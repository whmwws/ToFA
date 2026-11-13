package ToFA.Android.whmwws.phone.sync

import android.content.Context
import android.os.Build
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.shell.AdbShellResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ToFA.Android.whmwws.phone.security.KeystoreBox
import ToFA.Android.whmwws.phone.ui.I18n
import java.io.File

/**
 * 手机端直连安卓手表的 ADB 通道。
 *
 * 手表需要先打开「开发者选项 → 无线调试」，手机进入该页面读取两项信息：
 * - 连接端口（无线调试主页显示，一般随 IP 一起给出）
 * - 配对端口 + 6 位配对码（点「使用配对码配对设备」弹出）
 *
 * 账号通过 adb push 落到手表 App 的私有外部目录，手表端启动时按文件修改时间自动导入。
 */
object AdbWatchSync {

    const val WATCH_PKG = "ToFA.Android.whmwws.watch"

    /** 手表端 Activity 的完全限定名（Kotlin 包名与 applicationId 不同，不能用 .MainActivity 简写） */
    private const val WATCH_ACTIVITY = "$WATCH_PKG/ToFA.Android.whmwws.watch.MainActivity"

    /** 与手表端 WatchSettings 的 import / export 目录保持一致 */
    private const val REMOTE_DIR = "/sdcard/Android/data/$WATCH_PKG/files"
    private const val REMOTE_IMPORT_DIR = "$REMOTE_DIR/import"
    private const val REMOTE_EXPORT_DIR = "$REMOTE_DIR/export"
    private const val REMOTE_FILE = "$REMOTE_IMPORT_DIR/accounts.json"
    private const val REMOTE_ACTIVATION_FILE = "$REMOTE_DIR/tofa-activation.json"
    /** 主题色命令 / 导出文件，与手表端 WatchSettings 的路径保持一致。 */
    private const val REMOTE_THEME_IN = "$REMOTE_DIR/theme_in.json"
    private const val REMOTE_THEME_OUT = "$REMOTE_DIR/theme_out.json"
    private const val THEME_ACTION = "ToFA.Android.whmwws.watch.THEME_COMMAND"
    private const val THEME_RECEIVER = "$WATCH_PKG/.data.ThemeCommandReceiver"

    /** 打包在 assets 里的手表安装包 */
    private const val APK_ASSET = "watch-app.apk"

    private const val PREFS = "tfa_adb_identity"
    private const val KEY_CERT = "cert"
    private const val KEY_PRIVATE = "private_key"
    private const val MAX_EXPORT_BYTES = 8L * 1024 * 1024

    /**
     * ADB 认证依赖本地密钥，Android 上没有 ~/.android/adbkey，
     * 因此首次使用时生成一对并持久化，避免每次启动都要求手表重新授权。
     * 私钥用 Android Keystore 密封后再落盘，SharedPreferences 中不会出现明文密钥。
     */
    fun ensureIdentity(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sealedCert = prefs.getString(KEY_CERT, null)
        val sealedKey = prefs.getString(KEY_PRIVATE, null)
        if (sealedCert != null && sealedKey != null) {
            val cert = KeystoreBox.open(sealedCert)
            val key = KeystoreBox.open(sealedKey)
            if (cert != null && key != null &&
                runCatching { KadbCert.set(cert, key) }.isSuccess
            ) {
                return
            }
        }
        val (newCert, newKey) = KadbCert.get()
        KadbCert.set(newCert, newKey)
        prefs.edit()
            .putString(KEY_CERT, KeystoreBox.seal(newCert))
            .putString(KEY_PRIVATE, KeystoreBox.seal(newKey))
            .apply()
    }

    /** 无线调试配对（Android 11+ 的 6 位配对码流程），成功后密钥被手表记住 */
    suspend fun pair(context: Context, host: String, pairPort: Int, code: String): String =
        withContext(Dispatchers.IO) {
            ensureIdentity(context)
            if (code.length != 6) {
                error(I18n.pick("配对码应为 6 位数字", "The pairing code must be 6 digits"))
            }
            Kadb.pair(host, pairPort, code, Build.MODEL.ifBlank { "Android" })
            I18n.pick(
                "配对成功，可以点「连接测试」确认连接",
                "Paired successfully. Tap \"Connection test\" to confirm the connection",
            )
        }

    /** 校验连接是否可用，同时取回手表型号便于确认连对了设备 */
    suspend fun testConnection(context: Context, host: String, port: Int): String =
        withContext(Dispatchers.IO) {
            ensureIdentity(context)
            ensureReachable(host, port)
            Kadb.create(host, port).use { adb ->
                val resp = adb.shell("getprop ro.product.model")
                resp.okOrThrow(I18n.pick("读取手表型号", "read watch model"))
                val model = resp.output.trim().ifBlank { I18n.pick("未知设备", "Unknown device") }
                I18n.pick("已连接：$model", "Connected: $model")
            }
        }

    /**
     * 先做一次带超时的 TCP 连接预检。
     * Kadb 的握手使用阻塞 socket 且没有读超时，地址/端口错误或手表未授权时会一直挂起，
     * 界面就停在「进行中」没有任何反馈；预检能立刻把「连不上」变成明确提示。
     */
    private fun ensureReachable(host: String, port: Int) {
        try {
            java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, port), 4000) }
        } catch (e: Exception) {
            error(
                I18n.pick(
                    "无法连接手表 $host:$port（${e.message ?: e.javaClass.simpleName}）：" +
                        "请确认手表已开启无线调试、地址与端口正确，且手机与手表在同一网络",
                    "Cannot connect to watch $host:$port (${e.message ?: e.javaClass.simpleName}): " +
                        "make sure wireless debugging is on, the address and port are correct, " +
                        "and both devices are on the same network",
                ),
            )
        }
    }

    /** 推送账号到手表，并重启手表端触发自动导入。payload 可以是明文或加密信封。 */
    suspend fun pushAccounts(
        context: Context,
        host: String,
        port: Int,
        payload: String,
    ): Int = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("tofa-watch-", ".tmp", context.cacheDir)
        try {
            temp.writeText(payload, Charsets.UTF_8)
            Kadb.create(host, port).use { adb ->
                adb.shell("mkdir -p $REMOTE_IMPORT_DIR")
                    .okOrThrow(I18n.pick("创建手表导入目录", "create watch import directory"))
                adb.push(temp, REMOTE_FILE)
                adb.shell("am force-stop $WATCH_PKG; am start -n $WATCH_ACTIVITY")
            }
            1
        } finally {
            temp.delete()
        }
    }

    /** 手表导出目录中的文件（按修改时间倒序） */
    suspend fun listExports(context: Context, host: String, port: Int): List<String> =
        withContext(Dispatchers.IO) {
            Kadb.create(host, port).use { adb ->
                val resp = adb.shell("ls -t $REMOTE_EXPORT_DIR 2>/dev/null")
                if (resp.exitCode != 0) {
                    emptyList()
                } else {
                    resp.output.lineSequence()
                        .map { it.trim() }
                        .filter { it.endsWith(".json") }
                        .toList()
                }
            }
        }

    /** 取回手表导出文件内容，供手机端按同一套规则解密导入 */
    suspend fun pullExport(
        context: Context,
        host: String,
        port: Int,
        name: String,
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        require(name.endsWith(".json") && !name.contains('/')) {
            I18n.pick("导出文件名不合法", "Invalid export file name")
        }
        val temp = File.createTempFile("tofa-watch-export-", ".json", context.cacheDir)
        try {
            Kadb.create(host, port).use { adb ->
                adb.pull(temp, "$REMOTE_EXPORT_DIR/$name")
            }
            require(temp.length() <= MAX_EXPORT_BYTES) {
                I18n.pick(
                    "导出文件过大，疑似不是 ToFA 导出文件",
                    "The export file is too large; it may not be a ToFA export file",
                )
            }
            name to temp.readText(Charsets.UTF_8)
        } finally {
            temp.delete()
        }
    }

    /** 推送高级功能激活文件。内容用手表唯一码加密，只有这台手表能解开。 */
    suspend fun pushActivation(
        context: Context,
        host: String,
        port: Int,
        uniqueCode: String,
    ): Unit = withContext(Dispatchers.IO) {
        val code = ToFA.Android.whmwws.phone.security.DeviceIdentity.normalize(uniqueCode)
            ?: error(I18n.pick("唯一码格式不正确", "Invalid unique code format"))
        val key = ToFA.Android.whmwws.phone.security.DeviceIdentity.keyOf(code)
        val plain = org.json.JSONObject()
            .put("magic", "TOFA-ACTIVATE")
            .put("v", 1)
            .put("activated", true)
            .put("at", System.currentTimeMillis())
            .toString()
        val payload = ToFA.Android.whmwws.phone.security.TofaCrypto.encrypt(plain, key)
        val temp = File.createTempFile("tofa-activation-", ".tmp", context.cacheDir)
        try {
            temp.writeText(payload, Charsets.UTF_8)
            Kadb.create(host, port).use { adb ->
                adb.shell("mkdir -p $REMOTE_DIR")
                    .okOrThrow(I18n.pick("创建手表目录", "create watch directory"))
                adb.push(temp, REMOTE_ACTIVATION_FILE)
                adb.shell("am force-stop $WATCH_PKG; am start -n $WATCH_ACTIVITY")
            }
        } finally {
            temp.delete()
        }
    }

    /** 安装手机内置的手表端安装包（覆盖升级）并启动 */
    suspend fun installWatchApp(context: Context, host: String, port: Int): String =
        withContext(Dispatchers.IO) {
            val apk = File(context.cacheDir, APK_ASSET)
            try {
                if (!apk.exists() || apk.length() == 0L) {
                    context.assets.open(APK_ASSET).use { input ->
                        apk.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                Kadb.create(host, port).use { adb ->
                    adb.install(apk, "-r")
                    adb.shell("am start -n $WATCH_ACTIVITY")
                }
                val sizeMb = String.format("%.1f", apk.length() / 1024.0 / 1024.0)
                I18n.pick(
                    "已安装并启动手表端（$sizeMb MB）",
                    "Watch app installed and launched ($sizeMb MB)",
                )
            } finally {
                apk.delete()
            }
        }

    /**
     * 把手机当前主题色推送到手表：复用现有无线调试通道。
     * 先写 files/theme_in.json，再广播手表端接收器；手表在前台会即时刷新，
     * 不在前台也会持久化，重启后仍生效。accentId 支持预设 id 与 custom:AARRGGBB。
     */
    suspend fun pushTheme(
        context: Context,
        host: String,
        port: Int,
        accentId: String,
        themeMode: String,
    ): String = withContext(Dispatchers.IO) {
        validateThemeId(accentId)
        require(themeMode == "light" || themeMode == "dark" || themeMode == "system") {
            I18n.pick("深浅色模式不合法", "Invalid light/dark mode")
        }
        val temp = File.createTempFile("tofa-theme-", ".json", context.cacheDir)
        try {
            val json = org.json.JSONObject()
                .put("accent", accentId)
                .put("mode", themeMode)
            temp.writeText(json.toString(), Charsets.UTF_8)
            ensureReachable(host, port)
            Kadb.create(host, port).use { adb ->
                adb.shell("mkdir -p $REMOTE_DIR")
                    .okOrThrow(I18n.pick("创建手表目录", "create watch directory"))
                adb.push(temp, REMOTE_THEME_IN)
                adb.shell("am broadcast -a $THEME_ACTION -n $THEME_RECEIVER")
                    .okOrThrow(I18n.pick("通知手表应用主题色", "notify the watch to apply the theme"))
            }
            I18n.pick(
                "已推送主题色到手表（前台即时生效，重启后保持）",
                "Theme pushed to the watch (applies immediately in the foreground and persists after restart)",
            )
        } finally {
            temp.delete()
        }
    }

    /**
     * 把手机当前界面语言同步到手表：写同一条主题命令文件并广播，
     * 手表端 ThemeSync 会读取其中的 lang 字段并即时应用、持久化。
     */
    suspend fun pushLanguage(
        context: Context,
        host: String,
        port: Int,
        languageId: String,
        packJson: String? = null,
    ): String = withContext(Dispatchers.IO) {
        require(languageId.isNotBlank()) {
            I18n.pick("语言设置不合法", "Invalid language setting")
        }
        val temp = File.createTempFile("tofa-lang-", ".json", context.cacheDir)
        try {
            val json = org.json.JSONObject().put("lang", languageId)
            // 非中英文时附带完整离线翻译表，手表端导入后即可切换
            if (!packJson.isNullOrBlank()) {
                json.put("packs", org.json.JSONArray("[$packJson]"))
            }
            temp.writeText(json.toString(), Charsets.UTF_8)
            ensureReachable(host, port)
            Kadb.create(host, port).use { adb ->
                adb.shell("mkdir -p $REMOTE_DIR")
                    .okOrThrow(I18n.pick("创建手表目录", "create watch directory"))
                adb.push(temp, REMOTE_THEME_IN)
                adb.shell("am broadcast -a $THEME_ACTION -n $THEME_RECEIVER")
                    .okOrThrow(I18n.pick("通知手表应用语言设置", "notify the watch to apply the language setting"))
            }
            I18n.pick("已同步语言设置到手表", "Language setting synced to the watch")
        } finally {
            temp.delete()
        }
    }

    /** 取回手表当前主题色：accentId to 深浅色模式 id。 */
    suspend fun pullTheme(
        context: Context,
        host: String,
        port: Int,
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("tofa-theme-out-", ".json", context.cacheDir)
        try {
            ensureReachable(host, port)
            Kadb.create(host, port).use { adb ->
                val check = adb.shell("ls $REMOTE_THEME_OUT 2>/dev/null")
                if (check.exitCode != 0 || check.output.trim().isEmpty()) {
                    error(
                        I18n.pick(
                            "手表还没有主题记录，请先在手表上打开一次 ToFA",
                            "The watch has no theme record yet; open ToFA on the watch once first",
                        ),
                    )
                }
                adb.pull(temp, REMOTE_THEME_OUT)
            }
            val json = org.json.JSONObject(temp.readText(Charsets.UTF_8))
            val accent = json.optString("accent")
            validateThemeId(accent)
            accent to json.optString("mode", "")
        } finally {
            temp.delete()
        }
    }

    /** 主题色 id 白名单校验，避免把非法字符串写进手表设置。 */
    private fun validateThemeId(id: String) {
        val presets = setOf("green", "blue", "purple", "orange", "cyan", "pink")
        if (id in presets) return
        require(id.startsWith("custom:")) {
            I18n.pick("主题色格式不合法", "Invalid theme color format")
        }
        val hex = id.removePrefix("custom:")
        require(hex.length == 8 && hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            I18n.pick("主题色格式不合法", "Invalid theme color format")
        }
    }

    /** 仅启动手表端（例如安装后没自动起来） */
    suspend fun launchWatchApp(context: Context, host: String, port: Int): String =
        withContext(Dispatchers.IO) {
            Kadb.create(host, port).use { adb ->
                adb.shell("am start -n $WATCH_ACTIVITY")
                    .okOrThrow(I18n.pick("启动手表端", "launch the watch app"))
            }
            I18n.pick("手表端已启动", "Watch app launched")
        }

    private fun AdbShellResponse.okOrThrow(step: String) {
        if (exitCode != 0) {
            val detail = allOutput.trim().ifBlank { errorOutput.trim() }
            error(
                I18n.pick(
                    "${step}失败（exit=$exitCode）${if (detail.isBlank()) "" else "：$detail"}",
                    "$step failed (exit=$exitCode)${if (detail.isBlank()) "" else ": $detail"}",
                ),
            )
        }
    }
}
