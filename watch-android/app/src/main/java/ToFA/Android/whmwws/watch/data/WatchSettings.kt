package ToFA.Android.whmwws.watch.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.io.File

/** 首页 / 列表显示名三档：只密钥名 → 只账户名 → 都显示，默认只密钥名。 */
enum class DisplayNameMode(val id: String, val label: String, val enLabel: String) {
    SecretOnly("secret", "只密钥名", "Secret"),
    AccountOnly("account", "只账户名", "Account"),
    Both("both", "都显示", "Both");

    /** 点击整行时循环切换，交互与「深浅色」一致。 */
    fun next(): DisplayNameMode = when (this) {
        SecretOnly -> AccountOnly
        AccountOnly -> Both
        Both -> SecretOnly
    }

    companion object {
        fun fromId(id: String?): DisplayNameMode? = entries.firstOrNull { it.id == id }
    }
}

/** 界面语言：中文 / English，默认中文，交互与「深浅色」一致（点整行切换）。 */
enum class WatchLanguage(val id: String) {
    Chinese("zh"),
    English("en");

    /** 语言名固定用母语，切换后仍能认出自己选的是哪一种。 */
    val nativeLabel: String
        get() = if (this == Chinese) "中文" else "English"

    fun next(): WatchLanguage = if (this == Chinese) English else Chinese

    companion object {
        fun fromId(id: String?): WatchLanguage = if (id == English.id) English else Chinese
    }
}

/** 手表端的轻量设置项（独立于账号保险库）。 */
object WatchSettings {

    private const val PREFS = "tfa_watch_settings"
    private const val KEY_QR_ENABLED = "qr_enabled"
    private const val KEY_COPY_ENABLED = "copy_enabled"
    private const val KEY_ACCENT = "accent"
    private const val KEY_ROUND_UI = "round_ui"
    private const val KEY_RECENTS_PRIVACY = "recents_privacy"
    private const val KEY_SHOW_CODES_ON_LAUNCH = "show_codes_on_launch"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DISPLAY_NAME_MODE = "display_name_mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_LANGUAGE_PACKS = "language_packs"
    private const val KEY_PRIVACY_AGREED = "privacy_agreed"
    /** 旧布尔开关，仅用于首次迁移，读取后不再写入。 */
    private const val KEY_LEGACY_SHOW_ACCOUNT_NAME = "show_account_name"

    data class Accent(val id: String, val label: String, val argb: Long) {
        val color: Color get() = Color(argb)
    }
    val accents: List<Accent> = listOf(
        Accent("green", "青绿", 0xFF7BD88F),
        Accent("blue", "天蓝", 0xFF8AB4F8),
        Accent("purple", "紫罗兰", 0xFFC792EA),
        Accent("orange", "暖橙", 0xFFFFB86C),
        Accent("cyan", "冰青", 0xFF67E8F9),
        Accent("pink", "樱粉", 0xFFFF9EC4),
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 是否允许轻点表盘显示当前账号密钥二维码，默认关闭，避免密钥被旁观拍摄。 */
    fun isQrEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_QR_ENABLED, false)

    fun setQrEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_QR_ENABLED, enabled).apply()
    }

    /** 是否允许轻点验证码复制到剪贴板（验证码更新后自动清空）。 */
    fun isCopyEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COPY_ENABLED, false)

    fun setCopyEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_COPY_ENABLED, enabled).apply()
    }

    /** “圆形页面”默认关闭，即默认使用方形布局；开启后圆屏设备使用贴合表盘的布局。 */
    fun isSquareUi(context: Context): Boolean =
        !prefs(context).getBoolean(KEY_ROUND_UI, false)

    fun setSquareUi(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ROUND_UI, !enabled).apply()
    }

    /** 最近任务隐私模式（默认开启）：后台时最近任务卡片不显示验证码。 */
    fun isRecentsPrivacy(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RECENTS_PRIVACY, true)

    fun setRecentsPrivacy(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RECENTS_PRIVACY, enabled).apply()
    }

    /** 启动时是否直接显示验证码（默认开启）；关闭后进入应用先显示遮挡页。 */
    fun isShowCodesOnLaunch(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_CODES_ON_LAUNCH, true)

    fun setShowCodesOnLaunch(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_CODES_ON_LAUNCH, enabled).apply()
    }

    /** 深浅色模式：light / dark / system（默认深色，保持原有观感）。 */
    fun themeModeId(context: Context): String =
        prefs(context).getString(KEY_THEME_MODE, "dark").orEmpty()

    fun setThemeModeId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_THEME_MODE, id).apply()
        writeThemeExport(context)
    }

    /**
     * 把当前主题写入手表私有外部目录，供手机端「从手表导入」读取。
     * 每次主题色 / 深浅色变化都会刷新，保证文件始终是当前值。
     */
    fun writeThemeExport(context: Context) {
        val json = JSONObject()
            .put("accent", accent(context).id)
            .put("mode", themeModeId(context))
        runCatching {
            File(rootDir(context), "theme_out.json").writeText(json.toString())
        }
    }

    /**
     * 显示名档位（默认只密钥名）。
     * 老数据迁移：旧布尔 true → 只账户名，false / 未设置 → 只密钥名；迁移后写入新键并清除旧键。
     */
    fun displayNameMode(context: Context): DisplayNameMode {
        val p = prefs(context)
        DisplayNameMode.fromId(p.getString(KEY_DISPLAY_NAME_MODE, null))?.let { return it }
        val legacy = p.getBoolean(KEY_LEGACY_SHOW_ACCOUNT_NAME, false)
        val migrated = if (legacy) DisplayNameMode.AccountOnly else DisplayNameMode.SecretOnly
        p.edit()
            .putString(KEY_DISPLAY_NAME_MODE, migrated.id)
            .remove(KEY_LEGACY_SHOW_ACCOUNT_NAME)
            .apply()
        return migrated
    }

    fun setDisplayNameMode(context: Context, mode: DisplayNameMode) {
        prefs(context).edit()
            .putString(KEY_DISPLAY_NAME_MODE, mode.id)
            .remove(KEY_LEGACY_SHOW_ACCOUNT_NAME)
            .apply()
    }

    /** 界面语言：zh / en（默认中文）。 */
    fun languageId(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, WatchLanguage.Chinese.id).orEmpty()

    fun setLanguageId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, id).apply()
    }

    /** 手机推送的离线语言包（JSON 数组），与当前语言选择分别持久化。 */
    fun languagePacksJson(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE_PACKS, "[]").orEmpty()

    fun setLanguagePacksJson(context: Context, json: String) {
        prefs(context).edit().putString(KEY_LANGUAGE_PACKS, json).apply()
    }

    /** 首启隐私协议是否已确认；确认后不再弹出。 */
    fun isPrivacyAgreed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PRIVACY_AGREED, false)

    fun setPrivacyAgreed(context: Context, agreed: Boolean) {
        prefs(context).edit().putBoolean(KEY_PRIVACY_AGREED, agreed).apply()
    }

    fun accent(context: Context): Accent {
        val id = prefs(context).getString(KEY_ACCENT, accents.first().id)
        return parseAccent(id) ?: accents.first()
    }

    fun setAccent(context: Context, id: String) {
        if (parseAccent(id) == null) return
        prefs(context).edit().putString(KEY_ACCENT, id).apply()
        writeThemeExport(context)
    }

    /** 预设色 id 或色轮自定义色（custom:#AARRGGBB）。 */
    fun parseAccent(id: String?): Accent? {
        if (id == null) return null
        accents.firstOrNull { it.id == id }?.let { return it }
        if (id.startsWith("custom:")) {
            val argb = id.removePrefix("custom:").toLongOrNull(16) ?: return null
            return Accent(id, "自定义", argb)
        }
        return null
    }

    /** 手机 adb 推送密钥的目录。 */
    fun importDir(context: Context): String = folder(context, "import")

    /** 手表导出密钥的目录，与导入目录分离，避免新旧文件互相覆盖。 */
    fun exportDir(context: Context): String = folder(context, "export")

    /** 应用外部私有目录根路径，激活文件放在这里。 */
    fun rootDir(context: Context): String =
        context.getExternalFilesDir(null)?.absolutePath
            ?: "/sdcard/Android/data/${context.packageName}/files"

    private fun folder(context: Context, name: String): String = "${rootDir(context)}/$name"
}
