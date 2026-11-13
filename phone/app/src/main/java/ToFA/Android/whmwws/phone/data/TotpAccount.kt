package ToFA.Android.whmwws.phone.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 一条 2FA 账号。
 *
 * 字段与手表端账号模型一一对应，修改字段名时需同步检查双端导入导出格式。
 */
data class TotpAccount(
    val id: String = UUID.randomUUID().toString(),
    val issuer: String = "",
    val account: String = "",
    /** Base32 编码的共享密钥，解码后即为 HMAC 的原始密钥 */
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
    val label: String
        get() = when {
            issuer.isBlank() -> account
            account.isBlank() -> issuer
            else -> "$issuer · $account"
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

        /** 解析本地存储或导出文件中的账号数组，容忍单条数据损坏 */
        fun listFromJson(raw: String): List<TotpAccount> {
            val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()
            }.filter { it.secret.isNotBlank() }
        }
    }
}
