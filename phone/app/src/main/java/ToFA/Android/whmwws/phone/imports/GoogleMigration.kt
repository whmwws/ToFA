package ToFA.Android.whmwws.phone.imports

import android.util.Base64
import ToFA.Android.whmwws.phone.data.TotpAccount
import ToFA.Android.whmwws.phone.otp.Base32
import ToFA.Android.whmwws.phone.ui.I18n

/**
 * Google Authenticator「转移账号 / 导出账号」二维码解析。
 *
 * 二维码内容形如：
 *   otpauth-migration://offline?data=BASE64URL(protobuf)
 *
 * protobuf 结构（Google 官方 MigrationPayload）：
 *   message MigrationPayload {
 *     repeated OtpParameters otp_parameters = 1;
 *     int32 version      = 2;
 *     int32 batch_size   = 3;
 *     int32 batch_index  = 4;
 *     int32 batch_id     = 5;
 *   }
 *   message OtpParameters {
 *     bytes  secret    = 1;   // 原始字节，不是 Base32
 *     string name      = 2;
 *     string issuer    = 3;
 *     Algorithm algorithm = 4; // 0 UNSPECIFIED / 1 SHA1 / 2 SHA256 / 3 SHA512 / 4 MD5
 *     DigitCount digits = 5;   // 0 UNSPECIFIED / 1 SIX / 2 EIGHT
 *     OtpType type      = 6;   // 0 UNSPECIFIED / 1 HOTP / 2 TOTP
 *     uint64 counter    = 7;
 *   }
 */
object GoogleMigration {

    fun looksLikeMigration(raw: String): Boolean =
        raw.trim().lowercase().startsWith("otpauth-migration://")

    fun parse(raw: String): List<TotpAccount> {
        val query = raw.substringAfter('?', "")
        val data = UriQuery.value(query, "data")
            ?: throw IllegalArgumentException(
                I18n.pick(
                    "二维码里没有 data 参数，请重新导出",
                    "The QR code has no data parameter. Please export again",
                ),
            )
        val bytes = decodeBase64Url(data)
        val payload = ProtoReader(bytes)

        val accounts = mutableListOf<TotpAccount>()
        // Google 一次最多导出 10 条，可能分成多个批次二维码；这里只解析当前批次。
        while (true) {
            val (field, wire) = payload.readTag() ?: break
            if (field == 1 && wire == 2) {
                val chunk = payload.readBytes()
                accounts.add(parseOtpParameters(chunk))
            } else {
                payload.skip(wire, field)
            }
        }
        if (accounts.isEmpty()) {
            throw IllegalArgumentException(
                I18n.pick(
                    "未解析到任何账号，可能不是谷歌验证器导出的二维码",
                    "No accounts parsed; this may not be a QR code exported by Google Authenticator",
                ),
            )
        }
        return accounts
    }

    private fun parseOtpParameters(buf: ByteArray): TotpAccount {
        val reader = ProtoReader(buf)
        var secret = ByteArray(0)
        var name = ""
        var issuer = ""
        var algorithm = 1
        var digits = 1
        var type = 2
        var counter = 0L

        while (true) {
            val (field, wire) = reader.readTag() ?: break
            when {
                field == 1 && wire == 2 -> secret = reader.readBytes()
                field == 2 && wire == 2 -> name = reader.readString()
                field == 3 && wire == 2 -> issuer = reader.readString()
                field == 4 && wire == 0 -> algorithm = reader.readVarint().toInt()
                field == 5 && wire == 0 -> digits = reader.readVarint().toInt()
                field == 6 && wire == 0 -> type = reader.readVarint().toInt()
                field == 7 && wire == 0 -> counter = reader.readVarint()
                else -> reader.skip(wire, field)
            }
        }

        var finalIssuer = issuer.trim()
        var finalAccount = name.trim()
        // 部分导出工具会把 "Issuer:account" 塞进 name
        if (finalIssuer.isBlank() && finalAccount.contains(':')) {
            val idx = finalAccount.indexOf(':')
            finalIssuer = finalAccount.substring(0, idx).trim()
            finalAccount = finalAccount.substring(idx + 1).trim()
        }

        return TotpAccount(
            issuer = finalIssuer,
            account = finalAccount,
            secret = Base32.encode(secret),
            algorithm = when (algorithm) {
                2 -> "SHA256"
                3 -> "SHA512"
                else -> "SHA1"
            },
            digits = if (digits == 2) 8 else 6,
            period = 30,
            type = if (type == 1) "HOTP" else "TOTP",
            counter = counter,
        )
    }

    private fun decodeBase64Url(data: String): ByteArray {
        val normalized = data.replace(" ", "+").trimEnd('=')
        return try {
            Base64.decode(normalized, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                I18n.pick(
                    "迁移数据不是合法的 Base64，请重新扫码",
                    "The migration data is not valid Base64. Please scan the code again",
                ),
            )
        }
    }
}

/** 极简 protobuf wire 解析，只覆盖迁移数据用到的类型。 */
private class ProtoReader(private val buf: ByteArray) {
    private var pos = 0

    fun readTag(): Pair<Int, Int>? {
        if (pos >= buf.size) return null
        val tag = readVarint()
        if (tag == 0L) return null
        return Pair((tag ushr 3).toInt(), (tag and 0x07).toInt())
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            val b = buf[pos++].toInt() and 0xFF
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        return result
    }

    fun readBytes(): ByteArray {
        val len = readVarint().toInt()
        require(len >= 0 && pos + len <= buf.size) {
            I18n.pick("迁移数据被截断", "The migration data is truncated")
        }
        val out = buf.copyOfRange(pos, pos + len)
        pos += len
        return out
    }

    fun readString(): String = String(readBytes(), Charsets.UTF_8)

    fun skip(wireType: Int, field: Int) {
        when (wireType) {
            0 -> readVarint()
            1 -> pos += 8
            2 -> pos += readVarint().toInt()
            5 -> pos += 4
            else -> throw IllegalArgumentException(
                I18n.pick(
                    "不支持的 protobuf wire type $wireType (field $field)",
                    "Unsupported protobuf wire type $wireType (field $field)",
                ),
            )
        }
        if (pos > buf.size) {
            throw IllegalArgumentException(
                I18n.pick("迁移数据被截断", "The migration data is truncated"),
            )
        }
    }
}

private object UriQuery {
    /** 不依赖 android.net.Uri 的 query 解析，避免 base64url 里的 '+'、'=' 被改写 */
    fun value(query: String, key: String): String? {
        query.split('&').forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0 && pair.substring(0, idx) == key) {
                return pair.substring(idx + 1)
            }
        }
        return null
    }
}
