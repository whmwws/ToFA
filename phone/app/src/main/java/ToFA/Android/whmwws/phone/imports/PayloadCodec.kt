package ToFA.Android.whmwws.phone.imports

import ToFA.Android.whmwws.phone.data.TotpAccount
import ToFA.Android.whmwws.phone.security.TofaCrypto
import ToFA.Android.whmwws.phone.ui.I18n
import org.json.JSONArray
import org.json.JSONObject

/**
 * 账号导入 / 导出的统一格式处理。
 *
 * 明文：`[ {id,issuer,account,secret,...}, ... ]`
 * 兼容旧版：`{ "v":1, "ty":"acct", "accounts":[ {i,s,a,k,al,d,p,t,c}, ... ] }`
 * 加密：TofaCrypto 信封，内部承载上面的明文数组。
 */
object PayloadCodec {

    fun encodePlain(accounts: List<TotpAccount>): String = TotpAccount.listToJson(accounts)

    /** TXT 明文导出：每行一条标准 otpauth:// 链接。 */
    fun encodeOtpauthLines(accounts: List<TotpAccount>): String =
        accounts.joinToString("\n") { it.toOtpauthUri() }

    fun encodeEncrypted(accounts: List<TotpAccount>, passphrase: CharArray): String =
        TofaCrypto.encrypt(encodePlain(accounts), passphrase)

    fun isEncrypted(text: String): Boolean = TofaCrypto.isEncrypted(text)

    fun decode(text: String): List<TotpAccount> {
        val raw = text.trim().removePrefix("\uFEFF").trimStart()
        require(raw.isNotEmpty()) { I18n.pick("文件内容为空", "The file is empty") }
        if (raw.startsWith("[")) {
            return TotpAccount.listFromJson(raw).also {
                if (it.isEmpty()) {
                    throw IllegalArgumentException(
                        I18n.pick("文件里没有可用账号", "No usable accounts in the file"),
                    )
                }
            }
        }
        require(raw.startsWith("{")) {
            I18n.pick("无法识别的文件格式", "Unrecognized file format")
        }
        val obj = JSONObject(raw)
        val arr = obj.optJSONArray("accounts")
            ?: throw IllegalArgumentException(I18n.pick("文件里没有账号字段", "No accounts field in the file"))
        val accounts = (0 until arr.length()).mapNotNull { i ->
            val item = runCatching { arr.getJSONObject(i) }.getOrNull() ?: return@mapNotNull null
            runCatching {
                if (item.has("s") || item.has("k")) TotpAccount.fromWireJson(item) else TotpAccount.fromJson(item)
            }.getOrNull()
        }.filter { it.secret.isNotBlank() }
        if (accounts.isEmpty()) {
            throw IllegalArgumentException(I18n.pick("文件里没有可用账号", "No usable accounts in the file"))
        }
        return accounts
    }

    fun decryptToAccounts(text: String, passphrase: CharArray): List<TotpAccount> =
        decode(TofaCrypto.decrypt(text, passphrase))

    fun toJsonArray(accounts: List<TotpAccount>): JSONArray = JSONArray().apply {
        accounts.forEach { put(it.toJson()) }
    }
}
