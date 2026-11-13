package ToFA.Android.whmwws.watch.imports

import ToFA.Android.whmwws.watch.data.TotpAccount
import ToFA.Android.whmwws.watch.security.TofaCrypto
import ToFA.Android.whmwws.watch.ui.WatchI18n
import org.json.JSONObject

/** 手表端账号导入 / 导出格式处理（与手机端 PayloadCodec 格式完全一致）。 */
object PayloadCodec {

    fun encodePlain(accounts: List<TotpAccount>): String = TotpAccount.listToJson(accounts)

    fun encodeEncrypted(accounts: List<TotpAccount>, passphrase: CharArray): String =
        TofaCrypto.encrypt(encodePlain(accounts), passphrase)

    fun isEncrypted(text: String): Boolean = TofaCrypto.isEncrypted(text)

    fun decode(text: String): List<TotpAccount> {
        val raw = text.trim().removePrefix("\uFEFF").trimStart()
        require(raw.isNotEmpty()) { WatchI18n.pick("文件内容为空", "File is empty") }
        if (raw.startsWith("[")) {
            return TotpAccount.listFromJson(raw).also {
                if (it.isEmpty()) throw IllegalArgumentException(WatchI18n.pick("文件里没有可用账号", "No usable accounts in file"))
            }
        }
        require(raw.startsWith("{")) { WatchI18n.pick("无法识别的文件格式", "Unrecognized file format") }
        val obj = JSONObject(raw)
        val arr = obj.optJSONArray("accounts") ?: throw IllegalArgumentException(WatchI18n.pick("文件里没有账号字段", "File has no accounts field"))
        val accounts = (0 until arr.length()).mapNotNull { i ->
            val item = runCatching { arr.getJSONObject(i) }.getOrNull() ?: return@mapNotNull null
            runCatching {
                if (item.has("s") || item.has("k")) TotpAccount.fromWireJson(item) else TotpAccount.fromJson(item)
            }.getOrNull()
        }.filter { it.secret.isNotBlank() }
        if (accounts.isEmpty()) throw IllegalArgumentException(WatchI18n.pick("文件里没有可用账号", "No usable accounts in file"))
        return accounts
    }

    fun decryptToAccounts(text: String, passphrase: CharArray): List<TotpAccount> =
        decode(TofaCrypto.decrypt(text, passphrase))

    /** 明文导入：JSON 数组 / 旧版信封 / 每行一条 otpauth 链接。 */
    fun decodeFlexible(text: String): List<TotpAccount> {
        val raw = text.trim().removePrefix("\uFEFF").trimStart()
        return if (raw.startsWith("[") || raw.startsWith("{")) {
            decode(raw)
        } else {
            AccountImporter.parse(raw).also {
                if (it.isEmpty()) throw IllegalArgumentException(WatchI18n.pick("文件里没有可用账号", "No usable accounts in file"))
            }
        }
    }
}
