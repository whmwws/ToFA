package ToFA.Android.whmwws.watch.security

import ToFA.Android.whmwws.watch.ui.WatchI18n
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 与手机端完全一致的导出信封（AES-256-GCM + PBKDF2-HMAC-SHA256）。
 * 手表端只依赖标准 JDK / Android API，不引入额外库，方便后续复用。
 */
object TofaCrypto {

    const val MAGIC = "TOFA-EXPORT"
    const val VERSION = 2
    const val DEFAULT_ITERATIONS = 120_000
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"

    class WrongPassphraseException : Exception(
        WatchI18n.pick("密钥不正确或文件已损坏", "Incorrect passphrase or corrupted file"),
    )

    fun isEncrypted(text: String): Boolean {
        val trimmed = text.trim().removePrefix("\uFEFF").trimStart()
        if (!trimmed.startsWith("{")) return false
        return runCatching {
            val obj = JSONObject(trimmed)
            obj.optString("magic") == MAGIC && obj.optInt("enc", 0) == 1
        }.getOrDefault(false)
    }

    fun encrypt(plain: String, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): String {
        require(passphrase.isNotEmpty()) { WatchI18n.pick("密钥不能为空", "Passphrase cannot be empty") }
        val safeIterations = iterations.coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)

        val key = deriveKey(passphrase, salt, safeIterations)
        try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            return JSONObject()
                .put("magic", MAGIC)
                .put("v", VERSION)
                .put("enc", 1)
                .put("kdf", "PBKDF2-HMAC-SHA256")
                .put("iter", safeIterations)
                .put("cipher", "AES-256-GCM")
                .put("salt", b64(salt))
                .put("iv", b64(iv))
                .put("data", b64(ciphertext))
                .toString(2)
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(text: String, passphrase: CharArray): String {
        val obj = runCatching { JSONObject(text.trim().removePrefix("\uFEFF").trimStart()) }
            .getOrElse { throw WrongPassphraseException() }
        if (obj.optString("magic") != MAGIC || obj.optInt("enc", 0) != 1) {
            throw WrongPassphraseException()
        }
        val salt = runCatching { Base64.getDecoder().decode(obj.getString("salt")) }
            .getOrElse { throw WrongPassphraseException() }
        val iv = runCatching { Base64.getDecoder().decode(obj.getString("iv")) }
            .getOrElse { throw WrongPassphraseException() }
        val ciphertext = runCatching { Base64.getDecoder().decode(obj.getString("data")) }
            .getOrElse { throw WrongPassphraseException() }
        if (salt.size < 8 || iv.size != IV_BYTES || ciphertext.isEmpty()) {
            throw WrongPassphraseException()
        }
        val iterations = obj.optInt("iter", DEFAULT_ITERATIONS)
            .coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)

        val key = deriveKey(passphrase, salt, iterations)
        try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            return runCatching { String(cipher.doFinal(ciphertext), Charsets.UTF_8) }
                .getOrElse { throw WrongPassphraseException() }
        } finally {
            key.fill(0)
        }
    }

    /**
     * 把用户输入统一成可参与 KDF 的形式：仅去掉首尾空白，保留大小写与符号。
     * 密码键盘支持 @ ? ! 等符号，不能为了「方便」把它们过滤掉而降低熵。
     * 唯一码请使用 DeviceIdentity.keyOf()。
     */
    fun normalizeKey(raw: String): CharArray = raw.trim().toCharArray()

    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray): String =
        Base64.getEncoder().withoutPadding().encodeToString(bytes)
}
