package cn.bike.platform.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val originPolicy by lazy { TrustedOriginPolicy(BuildConfig.WEB_APP_URL, BuildConfig.DEBUG) }
    private var pendingLocationCallback: String? = null
    private var pendingScanCallback: String? = null
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraOutputUri: Uri? = null

    // ---------- 系统能力结果 ----------

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locateNow() else finishCallback(pendingLocationCallback, false, "PERMISSION_DENIED", "定位权限被拒绝")
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val callbackId = pendingScanCallback.also { pendingScanCallback = null }
        if (result.contents.isNullOrBlank()) finishCallback(callbackId, false, "USER_CANCELLED", "已取消扫码")
        else finishCallback(callbackId, true, data = JSONObject().put("text", result.contents))
    }

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = pendingFileCallback.also { pendingFileCallback = null }
        val selected = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        val files = selected ?: if (result.resultCode == Activity.RESULT_OK) cameraOutputUri?.let { arrayOf(it) } else null
        callback?.onReceiveValue(files)
        cameraOutputUri = null
    }

    // ---------- 生命周期与 WebView ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWebView()
        configureBackNavigation()
        if (savedInstanceState == null) webView.loadUrl(BuildConfig.WEB_APP_URL) else webView.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        pendingFileCallback?.onReceiveValue(null)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME)
        }
        webView.destroy()
        super.onDestroy()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView = WebView(this)
        setContentView(webView)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString BikeOperationsAndroid/${BuildConfig.VERSION_NAME}"
        }
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            "系统 WebView 版本过低，无法建立安全消息通道"
        }
        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_NAME,
            setOf(originPolicy.allowedOriginRule())
        ) { _, message, sourceOrigin, isMainFrame, _ ->
            if (isMainFrame && originPolicy.isAllowed(sourceOrigin.toString())) {
                handleBridgeMessage(message.data)
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (originPolicy.isAllowed(url)) return false
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = callback
                openEvidenceChooser(params)
                return true
            }
        }
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    // ---------- JSBridge 能力 ----------

    /** 输入: 受信任页面发送的 JSON 消息; 输出: 分派到定位或扫码白名单能力。 */
    private fun handleBridgeMessage(payload: String?) {
        val message = runCatching { JSONObject(payload ?: "") }.getOrNull() ?: return
        val callbackId = message.optString("callbackId")
        when (message.optString("action")) {
            "requestLocation" -> requestLocation(callbackId)
            "scanVehicleCode" -> scanVehicleCode(callbackId)
            else -> finishCallback(callbackId, false, "UNSUPPORTED_ACTION", "不支持的原生能力")
        }
    }

    /** 输入: H5 回调编号; 输出: 授权后返回设备当前位置。 */
    fun requestLocation(callbackId: String) = runOnUiThread {
        if (!canUseBridge(callbackId)) return@runOnUiThread
        pendingLocationCallback = callbackId
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) locateNow()
        else locationPermission.launch(permissions)
    }

    /** 输入: H5 回调编号; 输出: 扫码内容或可区分的取消结果。 */
    fun scanVehicleCode(callbackId: String) = runOnUiThread {
        if (!canUseBridge(callbackId)) return@runOnUiThread
        pendingScanCallback = callbackId
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            .setPrompt("扫描车辆二维码或条码")
            .setBeepEnabled(false)
            .setOrientationLocked(true)
        scanLauncher.launch(options)
    }

    @Suppress("MissingPermission")
    private fun locateNow() {
        val manager = getSystemService(LocationManager::class.java)
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            finishCallback(pendingLocationCallback, false, "LOCATION_DISABLED", "系统定位服务未开启")
            return
        }

        // 1. 五分钟内的系统定位足以用于作业到场证明，可立即返回，减少现场等待。
        val recent = manager.getLastKnownLocation(provider)
        if (recent != null && System.currentTimeMillis() - recent.time <= RECENT_LOCATION_MILLIS) {
            finishLocation(recent)
            return
        }

        // 2. 没有近期定位时请求一次更新；12 秒后仍无结果则返回明确错误，避免 H5 一直等待。
        lateinit var listener: LocationListener
        listener = LocationListener { location ->
            manager.removeUpdates(listener)
            finishLocation(location)
        }
        manager.requestSingleUpdate(provider, listener, mainLooper)
        Handler(mainLooper).postDelayed({
            if (pendingLocationCallback != null) {
                manager.removeUpdates(listener)
                finishCallback(pendingLocationCallback, false, "LOCATION_UNAVAILABLE", "定位超时，请移至开阔区域重试")
            }
        }, LOCATION_TIMEOUT_MILLIS)
    }

    private fun finishLocation(location: Location) {
        val callbackId = pendingLocationCallback.also { pendingLocationCallback = null }
        finishCallback(callbackId, true, data = JSONObject().put("longitude", location.longitude).put("latitude", location.latitude))
    }

    // ---------- 相机与统一回调 ----------

    private fun openEvidenceChooser(params: WebChromeClient.FileChooserParams?) {
        val contentIntent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        val directory = File(cacheDir, "evidence").apply { mkdirs() }
        val imageFile = File.createTempFile("evidence_", ".jpg", directory)
        cameraOutputUri = FileProvider.getUriForFile(this, "$packageName.files", imageFile)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(contentIntent, getString(R.string.camera_chooser)).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
        }
        fileChooser.launch(chooser)
    }

    private fun canUseBridge(callbackId: String): Boolean {
        if (callbackId.length !in 1..100) return false
        if (originPolicy.isAllowed(webView.url)) return true
        finishCallback(callbackId, false, "UNTRUSTED_ORIGIN", "当前页面无权调用原生能力")
        return false
    }

    /** 输入: 回调编号、状态及数据; 输出: 转义后的 JSON 结果送回 H5 的一次性回调。 */
    private fun finishCallback(callbackId: String?, ok: Boolean, code: String? = null, message: String? = null, data: JSONObject? = null) {
        if (callbackId.isNullOrBlank()) return
        if (callbackId == pendingLocationCallback) pendingLocationCallback = null
        val payload = JSONObject().put("ok", ok).apply {
            if (code != null) put("code", code)
            if (message != null) put("message", message)
            if (data != null) put("data", data)
        }.toString()
        val script = "window.BikeNative&&window.BikeNative.onResult(${JSONObject.quote(callbackId)},${JSONObject.quote(payload)})"
        webView.evaluateJavascript(script, null)
    }

    companion object {
        private const val BRIDGE_NAME = "BikeBridge"
        private const val RECENT_LOCATION_MILLIS = 5 * 60 * 1000L
        private const val LOCATION_TIMEOUT_MILLIS = 12_000L
    }
}
