package ToFA.Android.whmwws.phone.imports

import ToFA.Android.whmwws.phone.data.TotpAccount
import ToFA.Android.whmwws.phone.ui.I18n
import org.json.JSONObject

sealed class QrResult {
    data class Ok(val accounts: List<TotpAccount>) : QrResult()
    data class Error(val message: String) : QrResult()
}

/** 把扫到的原始文本路由到对应的解析器。 */
object QrRouter {

    enum class Mode { ANY, GOOGLE_EXPORT }

    fun parse(raw: String, mode: Mode = Mode.ANY): QrResult {
        val text = raw.trim()
        if (text.isEmpty()) return QrResult.Error(I18n.pick("二维码内容为空", "The QR code is empty"))

        if (GoogleMigration.looksLikeMigration(text)) {
            return runCatching { QrResult.Ok(GoogleMigration.parse(text)) }
                .getOrElse {
                    QrResult.Error(
                        it.message ?: I18n.pick(
                            "谷歌验证器二维码解析失败",
                            "Failed to parse the Google Authenticator QR code",
                        ),
                    )
                }
        }

        if (mode == Mode.GOOGLE_EXPORT) {
            return QrResult.Error(
                I18n.pick(
                    "这不是谷歌验证器的导出二维码，请打开 Google Authenticator → 转移账号 → 导出账号",
                    "This is not a Google Authenticator export QR code. Open Google Authenticator → " +
                        "Transfer accounts → Export accounts",
                ),
            )
        }

        if (text.lowercase().startsWith("otpauth://")) {
            return runCatching { QrResult.Ok(listOf(OtpauthUri.parse(text))) }
                .getOrElse {
                    QrResult.Error(
                        it.message ?: I18n.pick(
                            "otpauth 二维码解析失败",
                            "Failed to parse the otpauth QR code",
                        ),
                    )
                }
        }

        if (text.startsWith("{")) {
            val accounts = runCatching { parseExportJson(text) }.getOrNull()
            if (accounts != null && accounts.isNotEmpty()) {
                return QrResult.Ok(accounts)
            }
            return QrResult.Error(I18n.pick("JSON 里没有可用的账号", "No usable accounts in the JSON"))
        }

        return QrResult.Error(
            I18n.pick(
                "无法识别的二维码，仅支持 otpauth://、otpauth-migration:// 或本应用导出的 JSON",
                "Unrecognized QR code. Only otpauth://, otpauth-migration:// or JSON exported by this app " +
                    "is supported",
            ),
        )
    }

    /** 兼容本应用「导出 JSON」的文件内容 */
    private fun parseExportJson(text: String): List<TotpAccount> {
        val obj = JSONObject(text)
        val arr = obj.optJSONArray("accounts") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { TotpAccount.fromWireJson(arr.getJSONObject(i)) }.getOrNull()
        }.filter { it.secret.isNotBlank() }
    }
}
