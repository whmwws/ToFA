package ToFA.Android.whmwws.watch.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 手机端离线推送的语言包：静态词条 + 带 {0}/{1} 占位符的动态模板。
 * 手表端只做解析与翻译，不联网、不依赖任何后端。
 */
data class WatchLanguagePack(
    val id: String,
    val name: String,
    val entries: Map<String, String>,
    val patterns: List<WatchLanguagePattern>,
) {
    fun translate(zh: String, en: String): String =
        entries[zh] ?: patterns.firstNotNullOfOrNull { it.translate(zh) } ?: en

    companion object {

        /** 解析手机推送的 JSON 数组；坏数据整体跳过，不影响手表原有中英文。 */
        fun parseArray(json: String): List<WatchLanguagePack> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                runCatching { parse(array.getJSONObject(index)) }.getOrNull()
            }
        }.getOrDefault(emptyList())

        private fun parse(obj: JSONObject): WatchLanguagePack {
            val id = obj.optString("id")
            if (id.isBlank() || id == "zh" || id == "en") {
                throw IllegalArgumentException("reserved language id")
            }
            val entries = mutableMapOf<String, String>()
            val entryJson = obj.optJSONObject("entries")
            if (entryJson != null) {
                entryJson.keys().forEach { key ->
                    entries[key] = entryJson.optString(key)
                }
            }
            val patterns = mutableListOf<WatchLanguagePattern>()
            val patternJson = obj.optJSONArray("patterns")
            if (patternJson != null) {
                for (i in 0 until patternJson.length()) {
                    val item = patternJson.optJSONObject(i) ?: continue
                    val source = item.optString("source")
                    val target = item.optString("target")
                    if (source.isNotBlank() && target.isNotBlank()) {
                        patterns += WatchLanguagePattern(source, target)
                    }
                }
            }
            return WatchLanguagePack(
                id = id,
                name = obj.optString("name").ifBlank { id },
                entries = entries,
                patterns = patterns,
            )
        }
    }
}

/** 动态文案模板：把源文案里的占位片段按顺序捕获，再填回目标语言。 */
data class WatchLanguagePattern(val source: String, val target: String) {

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
