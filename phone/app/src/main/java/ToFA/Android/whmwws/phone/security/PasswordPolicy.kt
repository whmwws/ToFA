package ToFA.Android.whmwws.phone.security

import ToFA.Android.whmwws.phone.ui.I18n

/**
 * 密码错误策略（纯逻辑，便于单元测试）。
 *
 * 开启「多次错误后删除」后：
 *   第 1、2 次：仅提示错误
 *   第 3 次：冷静期 3 分钟
 *   第 4 次：冷静期 5 分钟
 *   第 5 次：清空密钥
 * 关闭该开关时不触发冷静期与删除。
 */
object PasswordPolicy {

    const val COOLDOWN_3_MS = 3L * 60L * 1000L
    const val COOLDOWN_4_MS = 5L * 60L * 1000L
    const val WIPE_AFTER_FAILURES = 5

    data class State(val failures: Int = 0, val lockUntilMs: Long = 0L)

    sealed interface Verdict {
        data class Wrong(val state: State, val cooldownMs: Long = 0L) : Verdict
        data class Locked(val remainingMs: Long) : Verdict
        data object Wipe : Verdict
    }

    fun onWrong(state: State, nowMs: Long, wipeEnabled: Boolean): Verdict {
        val failures = state.failures + 1
        if (!wipeEnabled) {
            return Verdict.Wrong(State(failures, 0L))
        }
        return when {
            failures >= WIPE_AFTER_FAILURES -> Verdict.Wipe
            failures == 3 -> Verdict.Wrong(State(3, nowMs + COOLDOWN_3_MS), COOLDOWN_3_MS)
            failures == 4 -> Verdict.Wrong(State(4, nowMs + COOLDOWN_4_MS), COOLDOWN_4_MS)
            else -> Verdict.Wrong(State(failures, 0L))
        }
    }

    fun remainingMs(state: State, nowMs: Long): Long =
        (state.lockUntilMs - nowMs).coerceAtLeast(0L)

    fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            I18n.pick(
                "${minutes}分${seconds.toString().padStart(2, '0')}秒",
                "${minutes}m ${seconds.toString().padStart(2, '0')}s",
            )
        } else {
            I18n.pick("${seconds}秒", "${seconds}s")
        }
    }
}
