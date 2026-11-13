package ToFA.Android.whmwws.phone.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TofaCryptoTest {

    private val passphrase = "TOFA-ABCD-EFGH-IJKL".toCharArray()

    @Test
    fun encryptDecryptRoundTrip() {
        val plain = """[{"id":"1","issuer":"GitHub","secret":"JBSWY3DPEHPK3PXP"}]"""
        val sealed = TofaCrypto.encrypt(plain, passphrase)
        assertTrue(TofaCrypto.isEncrypted(sealed))
        assertEquals(plain, TofaCrypto.decrypt(sealed, passphrase))
    }

    @Test
    fun wrongPassphraseFails() {
        val sealed = TofaCrypto.encrypt("hello", "right".toCharArray())
        assertTrue(TofaCrypto.isEncrypted(sealed))
        val result = runCatching { TofaCrypto.decrypt(sealed, "wrong".toCharArray()) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TofaCrypto.WrongPassphraseException)
    }

    @Test
    fun plaintextIsNotEncrypted() {
        assertFalse(TofaCrypto.isEncrypted("[]"))
        assertFalse(TofaCrypto.isEncrypted("{\"accounts\":[]}"))
        assertFalse(TofaCrypto.isEncrypted("not json"))
    }

    @Test
    fun tamperedDataFailsClosed() {
        val sealed = TofaCrypto.encrypt("secret", passphrase)
        val obj = org.json.JSONObject(sealed)
        val data = obj.getString("data")
        obj.put("data", data.dropLast(2) + "AA")
        assertTrue(runCatching { TofaCrypto.decrypt(obj.toString(), passphrase) }.isFailure)
    }

    @Test
    fun unicodeContentSurvives() {
        val plain = "账户名称：微信\n密钥：JBSWY3DPEHPK3PXP"
        val sealed = TofaCrypto.encrypt(plain, passphrase)
        assertEquals(plain, TofaCrypto.decrypt(sealed, passphrase))
    }

    @Test
    fun normalizeKeyTrimsButKeepsSymbols() {
        val normalized = TofaCrypto.normalizeKey("  tofa-abcd-efgh-ijkl  ")
        assertEquals("tofa-abcd-efgh-ijkl", String(normalized))
    }
}
