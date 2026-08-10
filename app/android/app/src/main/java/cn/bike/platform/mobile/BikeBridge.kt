package cn.bike.platform.mobile

import android.webkit.JavascriptInterface

/**
 * 作用: 向受信任 H5 暴露最小化的原生能力白名单。
 * 输入: H5 生成的回调编号。
 * 输出: 由 MainActivity 通过统一 JSON 回调返回定位或扫码结果。
 */
class BikeBridge(private val host: MainActivity) {
    @JavascriptInterface
    fun requestLocation(callbackId: String) = host.requestLocation(callbackId)

    @JavascriptInterface
    fun scanVehicleCode(callbackId: String) = host.scanVehicleCode(callbackId)
}
