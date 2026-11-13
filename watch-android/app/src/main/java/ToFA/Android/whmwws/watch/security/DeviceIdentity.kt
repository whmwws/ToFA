package ToFA.Android.whmwws.watch.security

import android.content.Context
import android.provider.Settings
import ToFA.Android.whmwws.watch.otp.Base32
import java.security.MessageDigest

/**
 * 设备唯一码（迁移身份）。
 *
 *   SHA-256("tofa-device-v2|<packageName>|<ANDROID_ID>") → Base32 前 16 位
 *   → XXXX-XXXX-XXXX-XXXX
 *
 * Android 8+ 的 ANDROID_ID 按「应用签名 + 用户 + 设备」稳定，卸载重装（同签名）不变，
 * 清除应用数据不变；只有恢复出厂或更换签名才会变化。
 * 生成与解析不绑定任何应用前缀，其他应用可复用同一套算法。
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

    /** 唯一码转口令：去掉分隔符，得到 16 位 Base32 本体；兼容旧版 TOFA- 前缀。 */
    fun keyOf(code: String): CharArray =
        normalize(code)?.replace("-", "")?.toCharArray() ?: CharArray(0)
}
