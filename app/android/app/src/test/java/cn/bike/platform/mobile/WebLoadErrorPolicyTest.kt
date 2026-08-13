package cn.bike.platform.mobile

import android.webkit.WebViewClient
import org.junit.Assert.assertEquals
import org.junit.Test

class WebLoadErrorPolicyTest {

    @Test
    fun `断网应提示用户检查网络`() {
        val error = WebLoadErrorPolicy.fromNetworkError(WebViewClient.ERROR_INTERNET_DISCONNECTED)

        assertEquals("NETWORK_OFFLINE", error.code)
        assertEquals("当前设备未连接网络", error.title)
    }

    @Test
    fun `服务器连接失败应提示检查服务和局域网`() {
        listOf(WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT).forEach { errorCode ->
            val error = WebLoadErrorPolicy.fromNetworkError(errorCode)

            assertEquals("SERVER_UNREACHABLE", error.code)
            assertEquals("暂时无法连接运营服务", error.title)
        }
    }

    @Test
    fun `服务端异常和页面不存在应使用不同提示`() {
        assertEquals("SERVER_ERROR", WebLoadErrorPolicy.fromHttpStatus(503).code)
        assertEquals("PAGE_NOT_FOUND", WebLoadErrorPolicy.fromHttpStatus(404).code)
    }

    @Test
    fun `证书错误应阻止继续连接`() {
        val error = WebLoadErrorPolicy.securityError()

        assertEquals("SECURE_CONNECTION_FAILED", error.code)
        assertEquals("安全连接失败", error.title)
    }
}
