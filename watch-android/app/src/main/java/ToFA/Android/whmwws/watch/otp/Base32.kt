package ToFA.Android.whmwws.watch.otp

import ToFA.Android.whmwws.watch.ui.WatchI18n

/**
 * RFC 4648 Base32（TOTP 密钥的标准编码）。
 * 解析时忽略空格、连字符、大小写与补位 '='。
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(input: String): ByteArray {
        val clean = input
            .replace(" ", "")
            .replace("-", "")
            .replace("=", "")
            .uppercase()
        require(clean.isNotEmpty()) { WatchI18n.pick("密钥为空", "Secret is empty") }

        val out = ArrayList<Byte>(clean.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            require(v >= 0) { WatchI18n.pick("密钥包含非法字符: $c", "Secret contains an invalid character: $c") }
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }

    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                sb.append(ALPHABET[(buffer shr (bitsLeft - 5)) and 0x1F])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            sb.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return sb.toString()
    }
}
