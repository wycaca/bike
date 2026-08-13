package cn.bike.platform.mobile

import android.webkit.WebViewClient

/** 原生错误页展示的数据, 故障代码用于现场反馈和日志定位. */
data class WebLoadError(val code: String, val title: String, val message: String)

/** 将 WebView 技术错误转换为面向现场人员的稳定提示. */
object WebLoadErrorPolicy {

    /** 输入: WebView 网络错误码; 输出: 可操作的中文错误提示. */
    fun fromNetworkError(errorCode: Int): WebLoadError = when (errorCode) {
        WebViewClient.ERROR_INTERNET_DISCONNECTED -> WebLoadError(
            "NETWORK_OFFLINE",
            "当前设备未连接网络",
            "请连接 Wi-Fi 或移动网络后重新连接."
        )
        WebViewClient.ERROR_HOST_LOOKUP,
        WebViewClient.ERROR_CONNECT,
        WebViewClient.ERROR_TIMEOUT -> WebLoadError(
            "SERVER_UNREACHABLE",
            "暂时无法连接运营服务",
            "请确认手机与服务器在同一网络, 且移动端服务已经启动."
        )
        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> securityError()
        else -> WebLoadError(
            "PAGE_LOAD_FAILED",
            "页面加载失败",
            "网络可能不稳定, 请稍后重新连接."
        )
    }

    /** 输入: 主页面 HTTP 状态码; 输出: 区分部署错误和服务异常的提示. */
    fun fromHttpStatus(statusCode: Int): WebLoadError = when {
        statusCode == 404 -> WebLoadError(
            "PAGE_NOT_FOUND",
            "移动端页面不存在",
            "服务器已连接, 但没有找到移动端页面, 请联系管理员检查发布地址."
        )
        statusCode >= 500 -> WebLoadError(
            "SERVER_ERROR",
            "运营服务暂时不可用",
            "服务器正在维护或发生异常, 请稍后重新连接."
        )
        else -> WebLoadError(
            "HTTP_$statusCode",
            "运营服务拒绝访问",
            "服务器已连接, 但当前请求无法完成, 请联系管理员."
        )
    }

    /** 输入: 无; 输出: 不允许绕过校验的安全连接错误提示. */
    fun securityError() = WebLoadError(
        "SECURE_CONNECTION_FAILED",
        "安全连接失败",
        "无法验证服务器身份, 为保护运营数据已停止连接, 请联系管理员."
    )
}
