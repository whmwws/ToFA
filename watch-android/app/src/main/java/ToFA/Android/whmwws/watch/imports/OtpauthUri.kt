package ToFA.Android.whmwws.watch.imports

import android.net.Uri
import ToFA.Android.whmwws.watch.data.TotpAccount
import ToFA.Android.whmwws.watch.otp.Base32
import ToFA.Android.whmwws.watch.ui.WatchI18n

/** 解析标准 otpauth:// 链接，手表端用于“粘贴链接即添加”。 */
object OtpauthUri {

    fun parse(raw: String): TotpAccount {
        val uri = Uri.parse(raw.trim())
        val type = (uri.host ?: "").uppercase()
        require(type == "TOTP" || type == "HOTP") {
            WatchI18n.pick("不支持的 otpauth 类型: ${uri.host}", "Unsupported otpauth type: ${uri.host}")
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
        if (account.contains(':') && account.startsWith("$issuer:")) {
            account = account.removePrefix("$issuer:")
        }

        val secretParam = uri.getQueryParameter("secret").orEmpty()
        require(secretParam.isNotBlank()) { WatchI18n.pick("链接缺少 secret 参数", "Link is missing the secret parameter") }
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

    fun looksLikeOtpauth(raw: String): Boolean =
        raw.trim().uppercase().startsWith("OTPAUTH://")

    /** 反向生成标准 otpauth:// 链接，供二维码扫描迁移到其它验证器 */
    fun build(account: TotpAccount): String {
        val label = if (account.issuer.isNotBlank()) {
            "${account.issuer}:${account.account}"
        } else {
            account.account.ifBlank { "account" }
        }
        val builder = Uri.Builder()
            .scheme("otpauth")
            .authority(account.type.lowercase())
            .appendPath(label)
            .appendQueryParameter("secret", account.secret)
            .appendQueryParameter("algorithm", account.algorithm)
            .appendQueryParameter("digits", account.digits.toString())
        if (account.issuer.isNotBlank()) {
            builder.appendQueryParameter("issuer", account.issuer)
        }
        if (account.type == "HOTP") {
            builder.appendQueryParameter("counter", account.counter.toString())
        } else {
            builder.appendQueryParameter("period", account.period.toString())
        }
        return builder.build().toString()
    }
}
