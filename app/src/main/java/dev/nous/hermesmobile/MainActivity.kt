package dev.nous.hermesmobile

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Thin native wrapper around the bundled Hermes mobile web client.
 *
 * The client is served from assets under a fixed `https://hermes.local/` origin
 * (via loadDataWithBaseURL) rather than `file://`, because some WebView versions
 * throw a SecurityError on localStorage for the `file:` scheme, and the client
 * persists its token / device id there. The WebSocket endpoint is the ngrok
 * tunnel, which forwards to the hermes-mobile gateway — no secret is baked into
 * the APK; the token is entered once on first launch and remembered locally.
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true   // client saves token/device id to localStorage
            allowFileAccess = true
            setSupportZoom(false)
        }
        // Keep any navigation inside the app instead of handing off to a browser.
        webView.webViewClient = WebViewClient()
        // Expose the keep-awake signal and reconnect backoff to the web client.
        webView.addJavascriptInterface(NativeBridge(this), "HermesNative")
        setContentView(webView)

        val html = assets.open("index.html").bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL("https://hermes.local/", html, "text/html", "utf-8", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

/**
 * Exposed to the client as `HermesNative`. `setBusy` keeps the phone awake while
 * a task runs; `nextReconnectDelay`/`resetReconnect` drive the reconnect backoff
 * so the schedule lives in one tested place instead of in the web client.
 */
class NativeBridge(private val activity: MainActivity) {

    private val backoff = ReconnectBackoff()

    @JavascriptInterface
    fun setBusy(busy: Boolean) {
        activity.runOnUiThread {
            if (busy) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                HermesService.startTask(activity, "Hermes is working\u2026")
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                HermesService.stopTask(activity)
            }
        }
    }

    @JavascriptInterface
    fun nextReconnectDelay(): Long = backoff.nextDelayMs()

    @JavascriptInterface
    fun resetReconnect() {
        backoff.reset()
    }
}
