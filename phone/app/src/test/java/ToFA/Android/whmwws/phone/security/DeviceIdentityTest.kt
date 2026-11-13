package ToFA.Android.whmwws.phone.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityTest {

    @Test
    fun codeIsStableForSameInputs() {
        val first = DeviceIdentity.codeFor("android-id-123", "ToFA.whmwws.watch")
        val second = DeviceIdentity.codeFor("android-id-123", "ToFA.whmwws.watch")
        assertEquals(first, second)
        assertTrue(Regex("[A-Z2-7]{4}-[A-Z2-7]{4}-[A-Z2-7]{4}-[A-Z2-7]{4}").matches(first))
    }

    @Test
    fun codeChangesWithDeviceOrPackage() {
        val base = DeviceIdentity.codeFor("android-id-123", "ToFA.whmwws.watch")
        assertNotEquals(base, DeviceIdentity.codeFor("android-id-456", "ToFA.whmwws.watch"))
        assertNotEquals(base, DeviceIdentity.codeFor("android-id-123", "ToFA.whmwws.phone"))
    }

    @Test
    fun normalizeAcceptsCommonForms() {
        val canonical = DeviceIdentity.codeFor("android-id-123", "ToFA.whmwws.watch")
        assertEquals(canonical, DeviceIdentity.normalize(canonical))
        assertEquals(canonical, DeviceIdentity.normalize(canonical.replace("-", "").lowercase()))
        assertEquals(canonical, DeviceIdentity.normalize("tofa-id:$canonical"))
        assertNull(DeviceIdentity.normalize("TOFA-ABC"))
        // 旧版 TOFA- 前缀仍可识别
        assertEquals(canonical, DeviceIdentity.normalize("TOFA-$canonical"))
    }

    @Test
    fun keyOfStripsSeparators() {
        val canonical = DeviceIdentity.codeFor("android-id-123", "ToFA.whmwws.watch")
        assertEquals(canonical.replace("-", ""), String(DeviceIdentity.keyOf(canonical)))
        assertEquals(canonical.replace("-", ""), String(DeviceIdentity.keyOf("TOFA-$canonical")))
    }
}
