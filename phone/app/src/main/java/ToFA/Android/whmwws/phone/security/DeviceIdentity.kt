package ToFA.Android.whmwws.phone.security

import android.content.Context
import android.provider.Settings
import ToFA.Android.whmwws.phone.otp.Base32
import java.security.MessageDigest

/**
 * 设备唯一码（ToFA 迁移身份）。
 *
 * 生成机制刻意与账号、文件、应用数据无关：
 *   SHA-256("tofa-device-v2|<packageName>|<ANDROID_ID>") → Base32 前 16 位
 *   → XXXX-XXXX-XXXX-XXXX
 *
 * Android 8+ 的 ANDROID_ID 按「应用签名 + 用户 + 设备」稳定，卸载重装（同签名）不变，
 * 清除应用数据不变；只有恢复出厂或更换签名才会变化。生成与解析不绑定应用前缀，
 * 其他应用可复用同一套算法作为跨设备身份锚点。
 *
 * 注意：唯一码本身不是秘密，但可直接作为导出文件的加密口令，请妥善保管二维码。
 */
object DeviceIdentity {

    private const val SCHEME = "tofa-device-v2"
    private const val BODY_LENGTH = 16

    fun codeFor(androidId: String, packageName: String): String {
        val material = "$SCHEME|$packageName|${androidId.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        val body = Base32.encode(digest).take(BODY_LENGTH).padEnd(BODY_LENGTH, 'A')
        return body.chunked(4).joinToString("-")
    }

    fun current(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        return codeFor(androidId, context.packageName)
    }

    fun qrPayload(code: String): String = "tofa-id:$code"

    /** 接受二维码内容、纯字符串、带/不带连字符的输入，返回规范格式；非法返回 null。 */
    fun normalize(raw: String): String? {
        val upper = raw.trim().uppercase()
        val withoutScheme = if (upper.startsWith("TOFA-ID:")) upper.removePrefix("TOFA-ID:") else upper
        var body = withoutScheme.filter { it in 'A'..'Z' || it in '2'..'7' }
        // 只有带旧版 TOFA 前缀（全长 20 位）才去掉前缀，避免误伤以 TOFA 开头的新格式码
        if (body.length != BODY_LENGTH && body.length == BODY_LENGTH + 4 && body.startsWith("TOFA")) {
            body = body.drop(4)
        }
        if (body.length != BODY_LENGTH) return null
        return body.chunked(4).joinToString("-")
    }

    fun same(a: String, b: String): Boolean = MessageDigest.isEqual(
        a.uppercase().toByteArray(Charsets.UTF_8),
        b.uppercase().toByteArray(Charsets.UTF_8),
    )

    /** 用于加密的规范口令（去掉连字符后的 16 位 Base32，兼容旧版 TOFA- 前缀）。 */
    fun keyOf(code: String): CharArray =
        normalize(code)?.replace("-", "")?.toCharArray() ?: CharArray(0)
}
