package ToFA.Android.whmwws.watch.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ToFA.Android.whmwws.watch.data.WatchLanguagePack
import ToFA.Android.whmwws.watch.data.WatchSettings

/**
 * 手表端多语言：内置中文 / English，并可接收手机端推送的离线语言包。
 * 与深浅色同一套机制——持久化在 WatchSettings，全局单例持有 Compose 状态，
 * 切换后所有页面即时重组。
 */
object WatchI18n {

    const val BUILTIN_ZH = "zh"
    const val BUILTIN_EN = "en"

    var languageId by mutableStateOf(BUILTIN_ZH)
        private set

    var packs by mutableStateOf<List<WatchLanguagePack>>(emptyList())
        private set

    /** 启动或收到推送后读取持久化状态。 */
    fun apply(context: Context) {
        languageId = WatchSettings.languageId(context)
        packs = WatchLanguagePack.parseArray(WatchSettings.languagePacksJson(context))
    }

    fun apply(id: String, packsJson: String? = null) {
        languageId = id
        if (packsJson != null) {
            packs = WatchLanguagePack.parseArray(packsJson)
        }
    }

    fun isEnglish(): Boolean = languageId == BUILTIN_EN

    /** 当前语言的母语名；语言包被移除时回退到中文。 */
    fun label(id: String = languageId): String = when (id) {
        BUILTIN_ZH -> "中文"
        BUILTIN_EN -> "English"
        else -> packs.firstOrNull { it.id == id }?.name ?: "中文"
    }

    /** 可切换的语言顺序：中文 → English → 手机推送的语言包。 */
    fun languageIds(): List<String> =
        listOf(BUILTIN_ZH, BUILTIN_EN) + packs.map { it.id }

    /** 点按整行循环切换，交互与「深浅色」一致；切换后立即持久化。 */
    fun cycle(context: Context) {
        val ids = languageIds()
        val nextIndex = (ids.indexOf(languageId) + 1) % ids.size
        val next = ids[nextIndex]
        languageId = next
        WatchSettings.setLanguageId(context, next)
    }

    fun translate(id: String, currentPacks: List<WatchLanguagePack>, zh: String, en: String): String =
        when (id) {
            BUILTIN_ZH -> zh
            BUILTIN_EN -> en
            else -> currentPacks.firstOrNull { it.id == id }?.translate(zh, en) ?: en
        }

    /** 非组合环境（协程、点击回调外的逻辑）按当前语言取值。 */
    fun pick(zh: String, en: String): String = translate(languageId, packs, zh, en)
}

/** 组合内取文案：读取语言与语言包状态建立订阅。 */
@Composable
fun tr(zh: String, en: String): String {
    val id = WatchI18n.languageId
    val packs = WatchI18n.packs
    return WatchI18n.translate(id, packs, zh, en)
}
