package ToFA.Android.whmwws.phone.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {

    @Test
    fun firstTwoFailuresOnlyWarn() {
        val first = PasswordPolicy.onWrong(PasswordPolicy.State(), 1000L, true)
        assertTrue(first is PasswordPolicy.Verdict.Wrong)
        assertEquals(1, (first as PasswordPolicy.Verdict.Wrong).state.failures)
        assertEquals(0L, first.cooldownMs)

        val second = PasswordPolicy.onWrong(first.state, 1000L, true)
        assertEquals(2, (second as PasswordPolicy.Verdict.Wrong).state.failures)
        assertEquals(0L, second.cooldownMs)
    }

    @Test
    fun thirdFailureLocksForThreeMinutes() {
        val third = PasswordPolicy.onWrong(PasswordPolicy.State(2, 0), 1000L, true)
        third as PasswordPolicy.Verdict.Wrong
        assertEquals(3, third.state.failures)
        assertEquals(PasswordPolicy.COOLDOWN_3_MS, third.cooldownMs)
        assertEquals(1000L + PasswordPolicy.COOLDOWN_3_MS, third.state.lockUntilMs)
    }

    @Test
    fun fourthFailureLocksForFiveMinutes() {
        val fourth = PasswordPolicy.onWrong(PasswordPolicy.State(3, 0), 1000L, true)
        fourth as PasswordPolicy.Verdict.Wrong
        assertEquals(4, fourth.state.failures)
        assertEquals(PasswordPolicy.COOLDOWN_4_MS, fourth.cooldownMs)
    }

    @Test
    fun fifthFailureWipes() {
        assertEquals(PasswordPolicy.Verdict.Wipe, PasswordPolicy.onWrong(PasswordPolicy.State(4, 0), 1000L, true))
    }

    @Test
    fun disabledWipeNeverLocks() {
        val verdict = PasswordPolicy.onWrong(PasswordPolicy.State(4, 0), 1000L, false)
        verdict as PasswordPolicy.Verdict.Wrong
        assertEquals(5, verdict.state.failures)
        assertEquals(0L, verdict.cooldownMs)
    }

    @Test
    fun remainingNeverNegative() {
        assertEquals(0L, PasswordPolicy.remainingMs(PasswordPolicy.State(3, 1000L), 2000L))
        assertEquals(500L, PasswordPolicy.remainingMs(PasswordPolicy.State(3, 1000L), 500L))
    }
}
