package ToFA.Android.whmwws.phone.security

import android.content.Context
import android.util.Base64
import ToFA.Android.whmwws.phone.ui.I18n
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 手机端本地密码 / 紧急密码存储。
 *
 * - 密码只保存 PBKDF2 派生后的哈希（随机盐 + 10 万次迭代），不保存明文。
 * - 错误次数与冷静期持久化，卸载/重启不会重置。
 * - 紧急密码验证通过后由调用方清空账号保险库。
 */
class PasswordStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    sealed interface UnlockResult {
        data object Ok : UnlockResult
        data class Wrong(val message: String, val cooldownMs: Long = 0L) : UnlockResult
        data class Locked(val remainingMs: Long) : UnlockResult
        data object Wipe : UnlockResult
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false) &&
        prefs.getString(KEY_HASH, null) != null

    fun isWipeAfterErrors(): Boolean = prefs.getBoolean(KEY_WIPE_AFTER_ERRORS, true)

    fun isEmergencyEnabled(): Boolean = prefs.getBoolean(KEY_EMERGENCY_ENABLED, false) &&
        prefs.getString(KEY_EMERGENCY_HASH, null) != null

    fun lockRemainingMs(nowMs: Long = System.currentTimeMillis()): Long =
        PasswordPolicy.remainingMs(readState(), nowMs)

    fun verify(input: CharArray, nowMs: Long = System.currentTimeMillis()): UnlockResult {
        if (!isEnabled()) return UnlockResult.Ok
        val remaining = lockRemainingMs(nowMs)
        if (remaining > 0L) return UnlockResult.Locked(remaining)

        if (isEmergencyEnabled() && matches(input, KEY_EMERGENCY_SALT, KEY_EMERGENCY_HASH, KEY_EMERGENCY_ITER)) {
            return UnlockResult.Wipe
        }

        if (matches(input, KEY_SALT, KEY_HASH, KEY_ITER)) {
            resetFailures()
            return UnlockResult.Ok
        }

        val verdict = PasswordPolicy.onWrong(readState(), nowMs, isWipeAfterErrors())
        return when (verdict) {
            is PasswordPolicy.Verdict.Wipe -> UnlockResult.Wipe
            is PasswordPolicy.Verdict.Wrong -> {
                writeState(verdict.state)
                val message = when {
                    verdict.cooldownMs > 0L ->
                        I18n.pick(
                            "错误次数过多，冷静 ${PasswordPolicy.formatRemaining(verdict.cooldownMs)}",
                            "Too many failed attempts. Please wait ${PasswordPolicy.formatRemaining(verdict.cooldownMs)}",
                        )
                    verdict.state.failures >= PasswordPolicy.WIPE_AFTER_FAILURES ->
                        I18n.pick("已达错误上限，密钥已删除", "Failure limit reached; the key has been deleted")
                    else -> I18n.pick(
                        "密码错误，请重试（第 ${verdict.state.failures} 次）",
                        "Wrong password. Please try again (attempt ${verdict.state.failures})",
                    )
                }
                UnlockResult.Wrong(message, verdict.cooldownMs)
            }
            is PasswordPolicy.Verdict.Locked ->
                UnlockResult.Locked(PasswordPolicy.remainingMs(readState(), nowMs))
        }
    }

    fun setPassword(password: CharArray) {
        require(password.size >= 4) { I18n.pick("密码至少 4 位", "The password must be at least 4 characters") }
        val (iter, salt, hash) = derive(password)
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_ITER, iter)
            .putString(KEY_SALT, salt)
            .putString(KEY_HASH, hash)
            .putInt(KEY_FAILURES, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    fun disablePassword() {
        prefs.edit()
            .putBoolean(KEY_ENABLED, false)
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .remove(KEY_ITER)
            .putInt(KEY_FAILURES, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    fun setWipeAfterErrors(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIPE_AFTER_ERRORS, enabled).apply()
    }

    fun setEmergencyPassword(password: CharArray) {
        require(password.size >= 4) { I18n.pick("紧急密码至少 4 位", "The emergency password must be at least 4 characters") }
        val (iter, salt, hash) = derive(password)
        prefs.edit()
            .putBoolean(KEY_EMERGENCY_ENABLED, true)
            .putInt(KEY_EMERGENCY_ITER, iter)
            .putString(KEY_EMERGENCY_SALT, salt)
            .putString(KEY_EMERGENCY_HASH, hash)
            .apply()
    }

    fun disableEmergencyPassword() {
        prefs.edit()
            .putBoolean(KEY_EMERGENCY_ENABLED, false)
            .remove(KEY_EMERGENCY_ITER)
            .remove(KEY_EMERGENCY_SALT)
            .remove(KEY_EMERGENCY_HASH)
            .apply()
    }

    /** 紧急密码或错误上限触发后调用：彻底清除密码与错误状态。 */
    fun completeWipe() {
        prefs.edit().clear().apply()
    }

    fun resetFailures() {
        prefs.edit()
            .putInt(KEY_FAILURES, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    private fun matches(input: CharArray, saltKey: String, hashKey: String, iterKey: String): Boolean {
        val salt = prefs.getString(saltKey, null) ?: return false
        val expected = prefs.getString(hashKey, null) ?: return false
        val iterations = prefs.getInt(iterKey, DEFAULT_ITERATIONS)
        val actual = runCatching { hash(input, Base64.decode(salt, Base64.NO_WRAP), iterations) }
            .getOrNull() ?: return false
        return MessageDigest.isEqual(actual, Base64.decode(expected, Base64.NO_WRAP))
    }

    private fun derive(password: CharArray): Triple<Int, String, String> {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = hash(password, salt, DEFAULT_ITERATIONS)
        return Triple(
            DEFAULT_ITERATIONS,
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(hash, Base64.NO_WRAP),
        )
    }

    private fun hash(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun readState(): PasswordPolicy.State = PasswordPolicy.State(
        failures = prefs.getInt(KEY_FAILURES, 0),
        lockUntilMs = prefs.getLong(KEY_LOCK_UNTIL, 0L),
    )

    private fun writeState(state: PasswordPolicy.State) {
        prefs.edit()
            .putInt(KEY_FAILURES, state.failures)
            .putLong(KEY_LOCK_UNTIL, state.lockUntilMs)
            .apply()
    }

    private companion object {
        const val PREFS = "tfa_phone_security"
        const val KEY_ENABLED = "password_enabled"
        const val KEY_ITER = "password_iter"
        const val KEY_SALT = "password_salt"
        const val KEY_HASH = "password_hash"
        const val KEY_FAILURES = "password_failures"
        const val KEY_LOCK_UNTIL = "password_lock_until"
        const val KEY_WIPE_AFTER_ERRORS = "wipe_after_errors"
        const val KEY_EMERGENCY_ENABLED = "emergency_enabled"
        const val KEY_EMERGENCY_ITER = "emergency_iter"
        const val KEY_EMERGENCY_SALT = "emergency_salt"
        const val KEY_EMERGENCY_HASH = "emergency_hash"
        const val DEFAULT_ITERATIONS = 100_000
        const val HASH_BITS = 256
        const val SALT_BYTES = 16
    }
}
