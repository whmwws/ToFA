package ToFA.Android.whmwws.phone

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flyfishxu.kadb.exception.AdbAuthException
import com.flyfishxu.kadb.exception.AdbPairAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import ToFA.Android.whmwws.phone.data.AccountRepository
import ToFA.Android.whmwws.phone.data.DisplayNameMode
import ToFA.Android.whmwws.phone.data.PremiumStore
import ToFA.Android.whmwws.phone.data.SecretVault
import ToFA.Android.whmwws.phone.data.TotpAccount
import ToFA.Android.whmwws.phone.imports.PayloadCodec
import ToFA.Android.whmwws.phone.security.DeviceIdentity
import ToFA.Android.whmwws.phone.sync.AdbWatchSync
import ToFA.Android.whmwws.phone.sync.PushKey
import ToFA.Android.whmwws.phone.ui.AppLanguage
import ToFA.Android.whmwws.phone.ui.I18n
import ToFA.Android.whmwws.phone.ui.LanguagePack
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val vault = SecretVault(app)
    val repo = AccountRepository(vault)
    val premium = PremiumStore(app)
    val passwords = ToFA.Android.whmwws.phone.security.PasswordStore(app)
    private val settings = app.getSharedPreferences("tfa_settings", Context.MODE_PRIVATE)

    // ---------- 手机端显示设置 ----------
    var copyEnabled by mutableStateOf(settings.getBoolean(KEY_COPY_ENABLED, true))
        private set
    var qrEnabled by mutableStateOf(settings.getBoolean(KEY_QR_ENABLED, false))
        private set
    var accentId by mutableStateOf(settings.getString(KEY_ACCENT, "green").orEmpty())
        private set
    /** 最近任务隐私模式：开启后禁止截屏、最近任务缩略图显示空白。 */
    var recentsPrivacy by mutableStateOf(settings.getBoolean(KEY_RECENTS_PRIVACY, true))
        private set
    /** 启动时是否直接显示验证码；关闭后进入应用先显示遮挡页。 */
    var showCodesOnLaunch by mutableStateOf(settings.getBoolean(KEY_SHOW_CODES_ON_LAUNCH, true))
        private set
    /** 深浅色模式：light / dark / system（默认跟随系统，与原有行为一致）。 */
    var themeMode by mutableStateOf(settings.getString(KEY_THEME_MODE, "system").orEmpty())
        private set
    /** 账号卡片显示名档位（默认只密钥名）。 */
    var displayNameMode by mutableStateOf(loadDisplayNameMode())
        private set
    /** 界面语言：zh / en（默认中文），与手表端可互相同步。 */
    var languageId by mutableStateOf(settings.getString(KEY_LANGUAGE, "zh").orEmpty())
        private set
    /** 首启隐私协议是否已确认。 */
    var privacyAgreed by mutableStateOf(settings.getBoolean(KEY_PRIVACY_AGREED, false))
        private set

    // ---------- 安卓手表 ADB 直连 ----------
    var adbHost by mutableStateOf(settings.getString(KEY_ADB_HOST, DEFAULT_ADB_HOST).orEmpty())
        private set
    var adbPort by mutableStateOf(settings.getString(KEY_ADB_PORT, DEFAULT_ADB_PORT).orEmpty())
        private set
    var adbPairPort by mutableStateOf(settings.getString(KEY_ADB_PAIR_PORT, "").orEmpty())
        private set
    var adbLog by mutableStateOf("")
        private set
    var adbBusy by mutableStateOf(false)
        private set

    init {
        repo.ensureLoaded()
        I18n.apply(languageId)
        I18n.loadImported(settings.getString(KEY_LANGUAGE_PACKS, "[]").orEmpty())
    }

    fun upsert(account: TotpAccount) = repo.upsert(account)

    fun upsertAll(list: List<TotpAccount>) = repo.upsertAll(list)

    fun delete(id: String) = repo.delete(id)

    fun moveAccount(from: Int, to: Int) = repo.move(from, to)

    /** 紧急密码 / 连续错误触发：清空账号与密码状态。 */
    fun wipeVault() {
        repo.wipe()
        passwords.completeWipe()
    }

    fun updateCopyEnabled(enabled: Boolean) {
        copyEnabled = enabled
        settings.edit().putBoolean(KEY_COPY_ENABLED, enabled).apply()
    }

    fun updateQrEnabled(enabled: Boolean) {
        qrEnabled = enabled
        settings.edit().putBoolean(KEY_QR_ENABLED, enabled).apply()
    }

    fun setAccent(id: String) {
        accentId = id
        settings.edit().putString(KEY_ACCENT, id).apply()
    }

    fun updateRecentsPrivacy(enabled: Boolean) {
        recentsPrivacy = enabled
        settings.edit().putBoolean(KEY_RECENTS_PRIVACY, enabled).apply()
    }

    fun updateShowCodesOnLaunch(enabled: Boolean) {
        showCodesOnLaunch = enabled
        settings.edit().putBoolean(KEY_SHOW_CODES_ON_LAUNCH, enabled).apply()
    }

    fun updateThemeMode(id: String) {
        if (id != "light" && id != "dark" && id != "system") return
        themeMode = id
        settings.edit().putString(KEY_THEME_MODE, id).apply()
    }

    fun updateDisplayNameMode(mode: DisplayNameMode) {
        displayNameMode = mode
        settings.edit()
            .putString(KEY_DISPLAY_NAME_MODE, mode.id)
            .remove(KEY_LEGACY_SHOW_ACCOUNT_NAME)
            .apply()
    }

    /** 切换界面语言：内置语言或已导入语言，立即生效并持久化。 */
    fun setLanguage(id: String) {
        if (I18n.options().none { it.id == id }) return
        languageId = id
        settings.edit().putString(KEY_LANGUAGE, id).apply()
        I18n.apply(id)
    }

    /**
     * 从本地文件导入离线语言包（JSON 对象或数组）。
     * 返回语言名用于提示；解析失败返回 null。
     */
    fun importLanguagePack(text: String): String? {
        val packs = runCatching {
            val trimmed = text.trim()
            if (trimmed.startsWith("[")) {
                LanguagePack.parseArray(trimmed)
            } else {
                listOfNotNull(LanguagePack.fromJson(org.json.JSONObject(trimmed)))
            }
        }.getOrNull().orEmpty()
        if (packs.isEmpty()) return null
        packs.forEach { I18n.addImported(it) }
        settings.edit().putString(KEY_LANGUAGE_PACKS, I18n.exportImportedJson()).apply()
        val first = packs.first()
        setLanguage(first.id)
        return first.nativeLabel
    }

    /** 首启隐私协议确认：只记录状态，不上传任何信息。 */
    fun agreePrivacy() {
        privacyAgreed = true
        settings.edit().putBoolean(KEY_PRIVACY_AGREED, true).apply()
    }

    /** 读档位；老数据迁移：旧布尔 true → 只账户名，false / 未设置 → 只密钥名。 */
    private fun loadDisplayNameMode(): DisplayNameMode {
        DisplayNameMode.fromId(settings.getString(KEY_DISPLAY_NAME_MODE, null))?.let { return it }
        val legacy = settings.getBoolean(KEY_LEGACY_SHOW_ACCOUNT_NAME, false)
        val migrated = if (legacy) DisplayNameMode.AccountOnly else DisplayNameMode.SecretOnly
        settings.edit()
            .putString(KEY_DISPLAY_NAME_MODE, migrated.id)
            .remove(KEY_LEGACY_SHOW_ACCOUNT_NAME)
            .apply()
        return migrated
    }

    fun saveAdbAddress(host: String, port: String, pairPort: String) {
        adbHost = host.trim()
        adbPort = port.trim()
        adbPairPort = pairPort.trim()
        settings.edit()
            .putString(KEY_ADB_HOST, adbHost)
            .putString(KEY_ADB_PORT, adbPort)
            .putString(KEY_ADB_PAIR_PORT, adbPairPort)
            .apply()
    }

    fun adbPair(code: String) {
        val pairPort = adbPairPort.toIntOrNull()
        if (pairPort == null || pairPort !in 1..65535) {
            appendAdbLog(
                I18n.pick(
                    "配对端口不合法，请填写手表「配对码」弹窗里的端口",
                    "Invalid pairing port — enter the port shown in the watch's pairing-code dialog",
                ),
            )
            return
        }
        val host = adbHost
        launchAdb(I18n.pick("配对", "Pair")) {
            AdbWatchSync.pair(getApplication(), host, pairPort, code.trim())
        }
    }

    fun adbTest() {
        launchAdb(I18n.pick("连接测试", "Connection test")) {
            AdbWatchSync.testConnection(getApplication(), adbHost, requirePort())
        }
    }

    fun adbPushAccounts(key: PushKey) {
        val accounts = repo.accounts.value
        if (accounts.isEmpty()) {
            appendAdbLog(I18n.pick("还没有密钥，先添加一个吧", "No keys yet — add one first"))
            return
        }
        val count = accounts.size
        launchAdb(I18n.pick("推送密钥", "Push keys")) {
            val payload = when (key) {
                is PushKey.None -> PayloadCodec.encodePlain(accounts)
                is PushKey.Password ->
                    PayloadCodec.encodeEncrypted(accounts, key.value.toCharArray())
                is PushKey.UniqueCode ->
                    PayloadCodec.encodeEncrypted(accounts, DeviceIdentity.keyOf(key.code))
            }
            AdbWatchSync.pushAccounts(getApplication(), adbHost, requirePort(), payload)
            I18n.pick(
                "已推送 $count 个密钥（${
                    when (key) {
                        is PushKey.None -> "无密码"
                        is PushKey.Password -> "自定义密码"
                        is PushKey.UniqueCode -> "唯一码加密"
                    }
                }），手表端正在自动导入",
                "Pushed $count keys (${
                    when (key) {
                        is PushKey.None -> "no password"
                        is PushKey.Password -> "custom password"
                        is PushKey.UniqueCode -> "unique-code encryption"
                    }
                }); the watch is importing them automatically",
            )
        }
    }

    /** 手机端完成捐赠后，用唯一码加密的激活文件通过 adb 推送到手表。 */
    fun adbPushActivation(uniqueCode: String) {
        launchAdb(I18n.pick("激活手表高级功能", "Activate watch premium")) {
            AdbWatchSync.pushActivation(getApplication(), adbHost, requirePort(), uniqueCode)
            I18n.pick("手表高级功能已激活", "Watch premium is now activated")
        }
    }

    fun adbInstallWatch() {
        // 10 MB 安装包走无线调试，留足传输时间
        launchAdb(I18n.pick("安装手表端", "Install watch app"), INSTALL_TIMEOUT_MS) {
            AdbWatchSync.installWatchApp(getApplication(), adbHost, requirePort())
        }
    }

    fun adbLaunchWatch() {
        launchAdb(I18n.pick("启动手表端", "Launch watch app")) {
            AdbWatchSync.launchWatchApp(getApplication(), adbHost, requirePort())
        }
    }

    /** 把手机当前主题色推送到手表（复用无线调试通道，前台即时生效并持久化）。 */
    fun adbPushTheme() {
        launchAdb(I18n.pick("推送主题色", "Push accent color")) {
            AdbWatchSync.pushTheme(getApplication(), adbHost, requirePort(), accentId, themeMode)
        }
    }

    /** 从手表取回当前主题色并立即应用到手机。 */
    fun adbImportTheme() {
        launchAdb(I18n.pick("导入手表主题色", "Import watch accent color")) {
            val (accent, _) = AdbWatchSync.pullTheme(getApplication(), adbHost, requirePort())
            setAccent(accent)
            I18n.pick("已从手表导入主题色（$accent）", "Imported the accent color from the watch ($accent)")
        }
    }

    /** 把手机当前界面语言同步到手表；非中英文时连同离线翻译表一起推送。 */
    fun adbPushLanguage() {
        launchAdb(I18n.pick("同步语言设置", "Sync language")) {
            val packJson = I18n.watchPack(languageId)?.toJson()?.toString()
            AdbWatchSync.pushLanguage(getApplication(), adbHost, requirePort(), languageId, packJson)
        }
    }

    /** 把一条说明写入 ADB 运行日志（用于扫码取消等轻量提示）。 */
    fun pushKeyMessage(message: String) {
        appendAdbLog(message)
    }

    private fun requirePort(): Int =
        adbPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: error(
            I18n.pick(
                "连接端口不合法，请填写无线调试主页显示的端口",
                "Invalid connection port — enter the port shown on the wireless debugging screen",
            ),
        )

    /**
     * 执行一次 ADB 操作。Kadb 的握手是阻塞 socket、没有读超时，
     * 用 async + 超时竞速做看门狗：超时就给出明确提示并解除忙碌状态，
     * 避免界面一直停在「进行中」（阻塞线程会随后自行失败回收）。
     */
    private fun launchAdb(step: String, timeoutMs: Long = ADB_TIMEOUT_MS, block: suspend () -> String) {
        if (adbBusy) return
        adbBusy = true
        appendAdbLog("$step …")
        viewModelScope.launch {
            val task = async(Dispatchers.IO) { runCatching { block() } }
            val done = withTimeoutOrNull(timeoutMs) { task.await() }
            val message = when {
                done == null ->
                    I18n.pick(
                        "超时（${timeoutMs / 1000}s）：手表没有响应。请确认手表已开启无线调试并在授权弹窗点「允许」后重试",
                        "Timed out (${timeoutMs / 1000}s): the watch is not responding. Make sure wireless debugging is on and tap Allow in the authorization dialog, then retry",
                    )
                else -> done.getOrElse { describeAdbError(it) }
            }
            appendAdbLog(message)
            adbBusy = false
        }
    }

    private fun appendAdbLog(line: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())
        adbLog = (adbLog + "$time  $line\n").lines().takeLast(LOG_MAX_LINES).joinToString("\n")
    }

    private fun describeAdbError(t: Throwable): String = when (t) {
        is AdbAuthException -> I18n.pick(
            "手表拒绝或未确认授权：请解锁手表，在「允许 USB 调试」弹窗点允许后重试",
            "The watch refused or did not confirm authorization: unlock it, tap Allow in the USB debugging dialog, then retry",
        )
        is AdbPairAuthException -> I18n.pick(
            "配对失败：配对码错误或已过期，请重新点「使用配对码配对设备」获取",
            "Pairing failed: the code is wrong or expired. Tap Pair device with pairing code again to get a new one",
        )
        else -> I18n.pick("失败：${t.message ?: t.javaClass.simpleName}", "Failed: ${t.message ?: t.javaClass.simpleName}")
    }

    companion object {
        private const val KEY_COPY_ENABLED = "copy_enabled"
        private const val KEY_QR_ENABLED = "qr_enabled"
        private const val KEY_ACCENT = "accent"
        private const val KEY_SHOW_CODES_ON_LAUNCH = "show_codes_on_launch"
        private const val KEY_RECENTS_PRIVACY = "recents_privacy"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DISPLAY_NAME_MODE = "display_name_mode"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_LANGUAGE_PACKS = "language_packs"
        private const val KEY_PRIVACY_AGREED = "privacy_agreed"
        /** 旧布尔开关，仅用于首次迁移，读取后不再写入。 */
        private const val KEY_LEGACY_SHOW_ACCOUNT_NAME = "show_account_name"

        private const val DEFAULT_ADB_HOST = ""
        private const val DEFAULT_ADB_PORT = "5555"
        private const val KEY_ADB_HOST = "adb_host"
        private const val KEY_ADB_PORT = "adb_port"
        private const val KEY_ADB_PAIR_PORT = "adb_pair_port"
        /** 普通命令的超时：地址不可达时 Kadb 会一直挂起，需要主动打断并提示。 */
        private const val ADB_TIMEOUT_MS = 20_000L
        /** 安装手表端需要推送约 10 MB 安装包，单独放宽。 */
        private const val INSTALL_TIMEOUT_MS = 120_000L
        private const val LOG_MAX_LINES = 40
    }
}
