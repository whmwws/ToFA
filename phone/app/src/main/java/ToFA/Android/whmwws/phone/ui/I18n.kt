package ToFA.Android.whmwws.phone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** 手机端内置的离线语言；语言名固定用母语显示。 */
enum class AppLanguage(val id: String, val nativeLabel: String) {
    Chinese("zh", "简体中文"),
    English("en", "English"),
    TraditionalChinese("zh-Hant", "繁體中文"),
    Japanese("ja", "日本語");

    companion object {
        fun fromId(id: String?): AppLanguage = entries.firstOrNull { it.id == id } ?: Chinese
    }
}

/**
 * 带运行时参数的文案模板。source / target 中的 {0}、{1}… 对应同一个动态参数，
 * 例如“已导入 {0} 个密钥”可匹配“已导入 3 个密钥”，再把 3 填进目标语言。
 */
data class LanguagePattern(val source: String, val target: String) {
    private val token = Regex("\\{(\\d+)}")
    private val indexes = token.findAll(source).map { it.groupValues[1].toInt() }.toList()
    private val regex by lazy {
        val pattern = buildString {
            append('^')
            var cursor = 0
            token.findAll(source).forEach { match ->
                append(Regex.escape(source.substring(cursor, match.range.first)))
                append("(.*?)")
                cursor = match.range.last + 1
            }
            append(Regex.escape(source.substring(cursor)))
            append('$')
        }
        Regex(pattern, RegexOption.DOT_MATCHES_ALL)
    }

    fun translate(input: String): String? {
        val match = regex.matchEntire(input) ?: return null
        var result = target
        indexes.forEachIndexed { group, index ->
            result = result.replace("{$index}", match.groupValues[group + 1])
        }
        return result
    }
}

/** 一套可离线使用、也可通过 adb 导入手表的语言包。 */
data class LanguagePack(
    val id: String,
    val nativeLabel: String,
    val entries: Map<String, String>,
    val patterns: List<LanguagePattern> = emptyList(),
) {
    fun translate(zh: String, en: String): String =
        entries[zh] ?: patterns.firstNotNullOfOrNull { it.translate(zh) } ?: en

    fun toJson(): JSONObject {
        val entryJson = JSONObject()
        entries.forEach { (source, target) -> entryJson.put(source, target) }
        val patternJson = JSONArray()
        patterns.forEach { pattern ->
            patternJson.put(
                JSONObject()
                    .put("source", pattern.source)
                    .put("target", pattern.target),
            )
        }
        return JSONObject()
            .put("format", 1)
            .put("id", id)
            .put("name", nativeLabel)
            .put("entries", entryJson)
            .put("patterns", patternJson)
    }

    companion object {
        /** 解析单个语言包；手机导入文件与手表端解析共用同一份格式。 */
        fun fromJson(obj: JSONObject): LanguagePack? = runCatching {
            val id = obj.optString("id")
            if (id.isBlank() || id == "zh" || id == "en") return null
            val entries = mutableMapOf<String, String>()
            obj.optJSONObject("entries")?.let { json ->
                json.keys().forEach { key -> entries[key] = json.optString(key) }
            }
            val patterns = mutableListOf<LanguagePattern>()
            obj.optJSONArray("patterns")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val source = item.optString("source")
                    val target = item.optString("target")
                    if (source.isNotBlank() && target.isNotBlank()) {
                        patterns += LanguagePattern(source, target)
                    }
                }
            }
            LanguagePack(
                id = id,
                nativeLabel = obj.optString("name").ifBlank { id },
                entries = entries,
                patterns = patterns,
            )
        }.getOrNull()

        fun parseArray(json: String): List<LanguagePack> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                runCatching { fromJson(array.getJSONObject(index)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}

/** 语言选择项：内置语言与导入语言统一展示。 */
data class LanguageOption(val id: String, val label: String)

/** 应用内多语言：默认简体中文，切换后即时生效并持久化到 tfa_settings。 */
object I18n {

    var languageId by mutableStateOf(AppLanguage.Chinese.id)
        private set

    /** 用户从本地文件导入的额外语言包（离线，不含任何联网逻辑）。 */
    var importedPacks by mutableStateOf<List<LanguagePack>>(emptyList())
        private set

    fun apply(id: String) {
        languageId = id.ifBlank { AppLanguage.Chinese.id }
    }

    fun loadImported(json: String) {
        importedPacks = LanguagePack.parseArray(json)
    }

    fun exportImportedJson(): String {
        val array = JSONArray()
        importedPacks.forEach { array.put(it.toJson()) }
        return array.toString()
    }

    /** 导入或覆盖同 id 语言包；返回语言名。 */
    fun addImported(pack: LanguagePack): String {
        importedPacks = importedPacks.filterNot { it.id == pack.id } + pack
        return pack.nativeLabel
    }

    fun options(): List<LanguageOption> =
        AppLanguage.entries.map { LanguageOption(it.id, it.nativeLabel) } +
            importedPacks.map { LanguageOption(it.id, it.nativeLabel) }

    fun labelFor(id: String): String =
        options().firstOrNull { it.id == id }?.label ?: id

    /** 非组合环境使用；找不到语言包时回退英文，再回退中文。 */
    fun pick(zh: String, en: String): String = translate(languageId, importedPacks, zh, en)

    fun translate(id: String, packs: List<LanguagePack>, zh: String, en: String): String = when (id) {
        AppLanguage.Chinese.id -> zh
        AppLanguage.English.id -> en
        AppLanguage.TraditionalChinese.id -> PhoneTraditionalChinesePack.translate(zh, en)
        AppLanguage.Japanese.id -> PhoneJapanesePack.translate(zh, en)
        else -> packs.firstOrNull { it.id == id }?.translate(zh, en) ?: en
    }

    /** 中文 / 英文是手表原有能力；其它语言连同翻译表一起离线推送。 */
    fun watchPack(id: String): LanguagePack? = when (id) {
        AppLanguage.Chinese.id, AppLanguage.English.id -> null
        AppLanguage.TraditionalChinese.id -> WatchTraditionalChinesePack
        AppLanguage.Japanese.id -> WatchJapanesePack
        else -> importedPacks.firstOrNull { it.id == id }
    }
}

/** 组合内取文案：读取语言状态建立订阅，切换语言后自动刷新。 */
@Composable
fun tr(zh: String, en: String): String {
    val id = I18n.languageId
    val packs = I18n.importedPacks
    return I18n.translate(id, packs, zh, en)
}
