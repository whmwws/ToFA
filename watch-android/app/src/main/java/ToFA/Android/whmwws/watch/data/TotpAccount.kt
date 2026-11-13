package ToFA.Android.whmwws.watch.data

import org.json.JSONArray
import org.json.JSONObject
import ToFA.Android.whmwws.watch.ui.WatchI18n
import java.util.UUID

/**
 * 一条 2FA 账号，字段与手机端 data/TotpAccount.kt 一一对应，
 * 因此手机导出的 accounts.json 可以被手表直接导入。
 */
data class TotpAccount(
    val id: String = UUID.randomUUID().toString(),
    val issuer: String = "",
    val account: String = "",
    /** Base32 编码的共享密钥 */
    val secret: String = "",
    /** SHA1 / SHA256 / SHA512 */
    val algorithm: String = "SHA1",
    /** 6 或 8 */
    val digits: Int = 6,
    /** 秒，常见 30 / 60 */
    val period: Int = 30,
    /** TOTP / HOTP */
    val type: String = "TOTP",
    /** 仅 HOTP 使用 */
    val counter: Long = 0L,
) {
    val title: String
        get() = when {
            issuer.isNotBlank() -> issuer
            account.isNotBlank() -> account
            else -> WatchI18n.pick("未命名", "Unnamed")
        }

    /**
     * 列表 / 首页显示名。
     * 单行布局下「都显示」用「密钥名 · 账户名」合并展示，仍受 maxLines=1 + 省略号保护，
     * 避免为副标题新增第二行导致验证码区域位移。
     */
    fun displayTitle(mode: DisplayNameMode): String = when (mode) {
        DisplayNameMode.AccountOnly -> account.ifBlank { issuer.ifBlank { WatchI18n.pick("未命名", "Unnamed") } }
        DisplayNameMode.SecretOnly -> issuer.ifBlank { account.ifBlank { WatchI18n.pick("未命名", "Unnamed") } }
        DisplayNameMode.Both -> when {
            issuer.isNotBlank() && account.isNotBlank() && account != issuer -> "$issuer · $account"
            issuer.isNotBlank() -> issuer
            account.isNotBlank() -> account
            else -> WatchI18n.pick("未命名", "Unnamed")
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("issuer", issuer)
        put("account", account)
        put("secret", secret)
        put("algorithm", algorithm)
        put("digits", digits)
        put("period", period)
        put("type", type)
        put("counter", counter)
    }

    companion object {
        fun fromJson(o: JSONObject): TotpAccount = TotpAccount(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            issuer = o.optString("issuer"),
            account = o.optString("account"),
            secret = o.optString("secret"),
            algorithm = o.optString("algorithm", "SHA1").uppercase(),
            digits = o.optInt("digits", 6),
            period = o.optInt("period", 30).let { if (it <= 0) 30 else it },
            type = o.optString("type", "TOTP").uppercase(),
            counter = o.optLong("counter", 0L),
        )

        fun fromWireJson(o: JSONObject): TotpAccount = TotpAccount(
            id = o.optString("i").ifBlank { UUID.randomUUID().toString() },
            issuer = o.optString("s"),
            account = o.optString("a"),
            secret = o.optString("k"),
            algorithm = o.optString("al", "SHA1").uppercase(),
            digits = o.optInt("d", 6),
            period = o.optInt("p", 30).let { if (it <= 0) 30 else it },
            type = o.optString("t", "TOTP").uppercase(),
            counter = o.optLong("c", 0L),
        )

        fun listToJson(list: List<TotpAccount>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        /** 解析本地存储或导入文件中的账号数组，容忍单条数据损坏 */
        fun listFromJson(raw: String): List<TotpAccount> {
            val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()
            }.filter { it.secret.isNotBlank() }
        }
    }
}
