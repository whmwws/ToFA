package ToFA.Android.whmwws.phone.imports

import android.net.Uri
import ToFA.Android.whmwws.phone.data.TotpAccount
import ToFA.Android.whmwws.phone.otp.Base32
import ToFA.Android.whmwws.phone.ui.I18n
import java.net.URLEncoder

/** 把账号编码成标准 otpauth:// 链接，供 TXT 导出与二维码迁移复用。 */
fun TotpAccount.toOtpauthUri(): String {
    val label = if (issuer.isBlank()) account else "$issuer:$account".trim(':')
    val path = URLEncoder.encode(label, "UTF-8").replace("+", "%20")
    val params = buildString {
        append("secret=").append(secret)
        if (issuer.isNotBlank()) append("&issuer=").append(URLEncoder.encode(issuer, "UTF-8").replace("+", "%20"))
        append("&algorithm=").append(algorithm)
        append("&digits=").append(digits)
        append("&period=").append(period)
        if (type == "HOTP") append("&counter=").append(counter)
    }
    return "otpauth://${type.lowercase()}/$path?$params"
}

/** 解析标准 otpauth:// 二维码 / 链接（Google Authenticator、各家网站通用格式）。 */
object OtpauthUri {

    fun parse(raw: String): TotpAccount {
        val uri = Uri.parse(raw.trim())
        val type = (uri.host ?: "").uppercase()
        require(type == "TOTP" || type == "HOTP") {
            I18n.pick(
                "不支持的 otpauth 类型: ${uri.host}",
                "Unsupported otpauth type: ${uri.host}",
            )
        }

        val label = uri.pathSegments.firstOrNull().orEmpty().trim()
        val decodedLabel = runCatching { Uri.decode(label) }.getOrDefault(label).trim()

        var issuer = uri.getQueryParameter("issuer").orEmpty().trim()
        var account = decodedLabel
        if (decodedLabel.contains(':')) {
            val idx = decodedLabel.indexOf(':')
            val prefix = decodedLabel.substring(0, idx).trim()
            val suffix = decodedLabel.substring(idx + 1).trim()
            if (issuer.isBlank()) issuer = prefix
            account = suffix
        }
        // 形如 "otpauth://totp/Example:alice@google.com"
        if (account.contains(':') && account.startsWith("$issuer:")) {
            account = account.removePrefix("$issuer:")
        }

        val secretParam = uri.getQueryParameter("secret").orEmpty()
        require(secretParam.isNotBlank()) {
            I18n.pick("二维码缺少 secret 参数", "The QR code is missing the secret parameter")
        }
        val secret = Base32.encode(Base32.decode(secretParam))

        val algorithm = uri.getQueryParameter("algorithm").orEmpty()
            .uppercase()
            .let { if (it == "SHA256" || it == "SHA512") it else "SHA1" }
        val digits = uri.getQueryParameter("digits")?.toIntOrNull()?.takeIf { it == 6 || it == 8 } ?: 6
        val period = uri.getQueryParameter("period")?.toIntOrNull()?.takeIf { it in 1..600 } ?: 30
        val counter = uri.getQueryParameter("counter")?.toLongOrNull() ?: 0L

        return TotpAccount(
            issuer = issuer,
            account = account,
            secret = secret,
            algorithm = algorithm,
            digits = digits,
            period = period,
            type = type,
            counter = counter,
        )
    }

    fun looksLikeOtpauth(raw: String): Boolean {
        val t = raw.trim().lowercase()
        return t.startsWith("otpauth://") || t.startsWith("otpauth-migration://")
    }
}
