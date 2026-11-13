package ToFA.Android.whmwws.phone.imports

import ToFA.Android.whmwws.phone.data.TotpAccount
import ToFA.Android.whmwws.phone.security.TofaCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadCodecTest {

    private val accounts = listOf(
        TotpAccount(id = "1", issuer = "GitHub", account = "a@b.c", secret = "JBSWY3DPEHPK3PXP"),
        TotpAccount(id = "2", issuer = "Google", account = "x@y.z", secret = "GEZDGNBVGY3TQOJQ"),
    )

    @Test
    fun plainRoundTrip() {
        val text = PayloadCodec.encodePlain(accounts)
        assertEquals(2, PayloadCodec.decode(text).size)
        assertTrue(!PayloadCodec.isEncrypted(text))
    }

    @Test
    fun legacyWireJsonStillImports() {
        val legacy = """{"v":1,"ty":"acct","accounts":[{"i":"1","s":"GitHub","a":"a@b.c","k":"JBSWY3DPEHPK3PXP","al":"SHA1","d":6,"p":30,"t":"TOTP","c":0}]}"""
        val decoded = PayloadCodec.decode(legacy)
        assertEquals(1, decoded.size)
        assertEquals("GitHub", decoded[0].issuer)
        assertEquals("JBSWY3DPEHPK3PXP", decoded[0].secret)
    }

    @Test
    fun encryptedRoundTrip() {
        val sealed = PayloadCodec.encodeEncrypted(accounts, "pass".toCharArray())
        assertTrue(PayloadCodec.isEncrypted(sealed))
        val decoded = PayloadCodec.decryptToAccounts(sealed, "pass".toCharArray())
        assertEquals(accounts.map { it.secret }, decoded.map { it.secret })
    }

    @Test
    fun wrongKeyCannotDecrypt() {
        val sealed = PayloadCodec.encodeEncrypted(accounts, "pass".toCharArray())
        assertTrue(runCatching { PayloadCodec.decryptToAccounts(sealed, "nope".toCharArray()) }.isFailure)
    }
}
