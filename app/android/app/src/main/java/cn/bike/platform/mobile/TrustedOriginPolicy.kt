package cn.bike.platform.mobile

import java.net.URI

/**
 * 作用: 限制 WebView 页面和 JSBridge 只能工作在配置的同源地址。
 * 输入: 受信任首页地址、是否允许 HTTP 调试地址。
 * 输出: 候选 URL 是否与首页协议、主机和端口完全一致。
 */
class TrustedOriginPolicy(homeUrl: String, private val allowCleartext: Boolean) {
    private val home = URI(homeUrl)

    fun isAllowed(candidateUrl: String?): Boolean {
        if (candidateUrl.isNullOrBlank()) return false
        return runCatching {
            val candidate = URI(candidateUrl)
            val schemeAllowed = candidate.scheme == "https" || (allowCleartext && candidate.scheme == "http")
            schemeAllowed && candidate.scheme == home.scheme && candidate.host == home.host && effectivePort(candidate) == effectivePort(home)
        }.getOrDefault(false)
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme == "https" -> 443
        else -> 80
    }
}
