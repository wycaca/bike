package cn.bike.platform.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedOriginPolicyTest {
    @Test
    fun `allows only the exact configured origin`() {
        val policy = TrustedOriginPolicy("https://ops.example.com/app", false)
        assertTrue(policy.isAllowed("https://ops.example.com/admin/overview"))
        assertTrue(policy.isAllowed("https://ops.example.com:443/operator/work"))
        assertFalse(policy.isAllowed("https://evil.example.com/"))
        assertFalse(policy.isAllowed("http://ops.example.com/"))
        assertFalse(policy.isAllowed("https://ops.example.com.evil.test/"))
    }

    @Test
    fun `permits the configured cleartext origin only in debug mode`() {
        val debugPolicy = TrustedOriginPolicy("http://192.168.50.204:8082", true)
        assertTrue(debugPolicy.isAllowed("http://192.168.50.204:8082/operator/pool"))
        assertFalse(debugPolicy.isAllowed("http://192.168.50.204:8080/operator/pool"))
    }
}
