package ToFA.Android.whmwws.watch.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TofaCryptoTest {

    private val passphrase = "TOFAABCDEFGHIJKL".toCharArray()

    @Test
    fun roundTrip() {
        val plain = """[{"id":"1","issuer":"GitHub","secret":"JBSWY3DPEHPK3PXP"}]"""
        val sealed = TofaCrypto.encrypt(plain, passphrase)
        assertTrue(TofaCrypto.isEncrypted(sealed))
        assertEquals(plain, TofaCrypto.decrypt(sealed, passphrase))
    }

    @Test
    fun wrongKeyFails() {
        val sealed = TofaCrypto.encrypt("x", "right".toCharArray())
        assertTrue(runCatching { TofaCrypto.decrypt(sealed, "wrong".toCharArray()) }.isFailure)
    }

    @Test
    fun plaintextDetection() {
        assertFalse(TofaCrypto.isEncrypted("[]"))
    }

    @Test
    fun deviceIdentityIsStableAndDifferent() {
        val a = DeviceIdentity.codeFor("id-1", "pkg")
        assertEquals(a, DeviceIdentity.codeFor("id-1", "pkg"))
        assertNotEquals(a, DeviceIdentity.codeFor("id-2", "pkg"))
    }
}
