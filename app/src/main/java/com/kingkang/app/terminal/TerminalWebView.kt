package com.kingkang.app.terminal

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalWebView(
    state: TerminalWebViewState,
    onInput: (ByteArray) -> Unit,
    onResize: (cols: Int, rows: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentInput = rememberUpdatedState(onInput)
    val currentResize = rememberUpdatedState(onResize)
    val bridge = remember {
        TerminalJsBridge().apply {
            this.onInput = { data -> currentInput.value(data.toByteArray(Charsets.UTF_8)) }
            this.onResize = { cols, rows -> currentResize.value(cols, rows) }
            this.onReady = { state.ready() }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = true
                isFocusableInTouchMode = true

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = false
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = false
                    useWideViewPort = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true
                }

                addJavascriptInterface(bridge, "KingKangBridge")
                state.attach(this)
                loadUrl("file:///android_asset/terminal/index.html")
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose { state.destroy() }
    }
}
