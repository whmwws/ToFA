package ToFA.Android.whmwws.phone.otp

import ToFA.Android.whmwws.phone.data.TotpAccount
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 4226 / RFC 6238 动态口令生成，逻辑与手表端 Totp.ets 保持一致。 */
object TotpEngine {

    /** 生成本窗口的验证码；HOTP 使用账号内的 counter */
    fun code(account: TotpAccount, atMillis: Long = System.currentTimeMillis()): String {
        val counter = if (account.type == "HOTP") {
            account.counter
        } else {
            atMillis / 1000L / account.period.coerceAtLeast(1)
        }
        return codeForCounter(account, counter)
    }

    fun codeForCounter(account: TotpAccount, counter: Long): String {
        val key = Base32.decode(account.secret)
        val mac = Mac.getInstance("Hmac" + normalizeAlgorithm(account.algorithm))
        mac.init(SecretKeySpec(key, "Hmac" + normalizeAlgorithm(account.algorithm)))
        val hash = mac.doFinal(counterToBytes(counter))
        return truncate(hash, account.digits)
    }

    /** 当前验证码剩余秒数，取值 1..period */
    fun secondsLeft(period: Int, atMillis: Long = System.currentTimeMillis()): Int {
        val p = period.coerceAtLeast(1)
        val elapsed = (atMillis / 1000L) % p
        return (p - elapsed).toInt().coerceIn(1, p)
    }

    private fun normalizeAlgorithm(algorithm: String): String = when (algorithm.uppercase()) {
        "SHA256" -> "SHA256"
        "SHA512" -> "SHA512"
        else -> "SHA1"
    }

    private fun counterToBytes(counter: Long): ByteArray =
        ByteArray(8) { i -> (counter shr (56 - 8 * i)).toByte() }

    private fun truncate(hash: ByteArray, digits: Int): String {
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val modulo = if (digits == 8) 100_000_000L else 1_000_000L
        val otp = binary.toLong() % modulo
        return otp.toString().padStart(digits, '0')
    }
}
