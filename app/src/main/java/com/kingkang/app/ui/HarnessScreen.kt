package com.kingkang.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kingkang.app.BuildConfig
import com.kingkang.app.MainViewModel
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.Background
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary

/**
 * The real dsh web surface, rendered in a WebView against the loopback origin the bridge
 * started inside Kali. The app keeps its native chat for day-to-day work; this screen is
 * where plugin-contributed pages, the complete settings tree and the plugin manager live
 * exactly as dsh-web ships them.
 */
@Composable
fun HarnessScreen(vm: MainViewModel, onBack: () -> Unit) {
    val activity = LocalActivity()
    var url by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooser = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
    }

    // The harness opens in portrait like the rest of the app; dsh-web is responsive and
    // collapses its sidebar into a menu at phone width.
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (activity != null && previous != null) {
                activity.requestedOrientation = previous
            }
        }
    }

    LaunchedEffect(attempt) {
        error = null
        val existing = webView
        if (existing != null && url != null) {
            // Retry reloads the live WebView; tearing it down would drop the auth cookie
            // exchange that just succeeded and flash the loading state for no reason.
            loading = true
            existing.reload()
            return@LaunchedEffect
        }
        loading = true
        url = vm.harnessUrl()
        if (url == null) {
            loading = false
            error = "无法连接 DeepSeek Harness。请确认 Agent 已连接后重试。"
        }
    }

    // The effect captures the view it owns: reading the state here instead would destroy
    // the WebView that the factory just created the moment the state flips to it.
    DisposableEffect(webView) {
        val owned = webView
        onDispose { owned?.destroy() }
    }

    BackHandler(enabled = true) {
        val view = webView
        if (view != null && view.canGoBack()) {
            view.goBack()
        } else {
            onBack()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(Surface)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "DeepSeek Harness",
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { attempt += 1 },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "重新加载",
                    tint = TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Border.copy(alpha = 0.6f))
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Background)
        ) {
            val pageColor = Background.toArgb()
            val target = url
            if (target != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                        WebView(context).apply {
                            setBackgroundColor(pageColor)
                            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                            isFocusable = true
                            isFocusableInTouchMode = true
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportZoom(false)
                                builtInZoomControls = false
                                displayZoomControls = false
                                mediaPlaybackRequiresUserGesture = false
                                // dsh-web is a responsive page; it sizes its shell with
                                // viewport units, so the WebView must honour its viewport
                                // meta tag instead of the legacy fixed-width mode.
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val target = request?.url ?: return true
                                    // The harness is this one loopback origin. Anything else
                                    // stays out of the WebView instead of becoming a second
                                    // browser the user did not ask for.
                                    return !isLoopback(target)
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    loading = true
                                    error = null
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    view?.evaluateJavascript(VIEWPORT_SHIM, null)
                                    if (BuildConfig.DEBUG) {
                                        // Enough to tell "dsh-web booted" from "blank shell"
                                        // without ever surfacing the launch token in a URL.
                                        view?.evaluateJavascript(
                                            "(function(){var r=document.getElementById('root');" +
                                                "return JSON.stringify({origin:location.origin," +
                                                "title:document.title,ready:document.readyState," +
                                                "text:((document.body&&document.body.innerText)||'').slice(0,200)," +
                                                "root:r?r.childElementCount:-1," +
                                                "html:r?r.innerHTML.length:-1," +
                                                "scripts:[].map.call(document.scripts,function(s){return (s.src||'').split('/').pop()})," +
                                                "resources:performance.getEntriesByType('resource').length})})()"
                                        ) { value -> Log.d("HarnessWeb", "page $value") }
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    err: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame != true) return
                                    loading = false
                                    error = err?.description?.toString()
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "Harness 加载失败"
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    response: WebResourceResponse?
                                ) {
                                    if (!BuildConfig.DEBUG) return
                                    Log.w(
                                        "HarnessWeb",
                                        "http ${response?.statusCode} ${request?.url?.path}"
                                    )
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                                    if (BuildConfig.DEBUG && message != null) {
                                        Log.d(
                                            "HarnessWeb",
                                            "${message.messageLevel()} ${message.message()} " +
                                                "@${message.sourceId()}:${message.lineNumber()}"
                                        )
                                    }
                                    return true
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    pendingFileCallback?.onReceiveValue(null)
                                    pendingFileCallback = filePathCallback
                                    val intent = try {
                                        fileChooserParams?.createIntent()
                                    } catch (_: ActivityNotFoundException) {
                                        null
                                    }
                                    if (intent == null) {
                                        pendingFileCallback = null
                                        filePathCallback?.onReceiveValue(null)
                                        return false
                                    }
                                    return try {
                                        fileChooser.launch(intent)
                                        true
                                    } catch (_: ActivityNotFoundException) {
                                        pendingFileCallback = null
                                        filePathCallback?.onReceiveValue(null)
                                        false
                                    }
                                }
                            }
                            webView = this
                            // Load after the first layout pass: a WebView that starts
                            // loading at 0×0 keeps a zero-height layout viewport, which
                            // makes dsh-web's viewport units collapse to 0px.
                            post { loadUrl(target) }
                        }
                    }
                )
            }

            if (loading && error == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Background)
                ) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "正在加载 DeepSeek Harness…",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            val message = error
            if (message != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Background)
                ) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Harness 未能加载",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                                .clickable { attempt += 1 }
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("重试", color = TextPrimary, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Some WebView builds (notably the API 37 preview) report a zero-height layout viewport:
 * `innerHeight` is real, but `vh`/`dvh` and percentage heights resolve to 0, so dsh-web's
 * shell collapses to nothing and any menu sized with `max-height: …100vh` is invisible.
 * Pin the shell to the visual viewport in pixels, and (only when that bug is detected)
 * rewrite height-based viewport units in the loaded stylesheets to matching pixels.
 * On a healthy WebView the detection fails and nothing is patched.
 */
private const val VIEWPORT_SHIM = """
(function(){
  var heightUnits = /(-?\d*\.?\d+)\s*(dvh|svh|lvh|vmin|vh)\b/g;
  var saved = window.__kkViewportRules || (window.__kkViewportRules = []);

  function rewrite(value, h, w) {
    return value.replace(heightUnits, function(match, n, unit) {
      var size = unit === 'vmin' ? Math.min(w, h) : h;
      return (parseFloat(n) * size / 100) + 'px';
    });
  }

  function walk(rules, h, w) {
    for (var i = 0; i < rules.length; i++) {
      var rule = rules[i];
      if (rule.cssRules) walk(rule.cssRules, h, w);
      var style = rule.style;
      if (!style) continue;
      for (var j = 0; j < style.length; j++) {
        var prop = style[j];
        var value = style.getPropertyValue(prop);
        if (!value || value.indexOf('v') < 0) continue;
        if (!/(dvh|svh|lvh|vmin|vh)\b/.test(value)) continue;
        var priority = style.getPropertyPriority(prop);
        saved.push({ s: style, p: prop, v: value, pri: priority });
        style.setProperty(prop, rewrite(value, h, w), priority);
      }
    }
  }

  function apply() {
    var h = window.innerHeight;
    var w = window.innerWidth;
    if (!(h > 0)) return;
    var px = h + 'px';
    document.documentElement.style.height = px;
    if (document.body) document.body.style.height = px;
    var root = document.getElementById('root');
    if (root) root.style.height = px;

    // Healthy engines answer this; the broken layout viewport makes every height query 0.
    if (matchMedia('(min-height: 100px)').matches || h <= 100) return;
    for (var i = 0; i < saved.length; i++) {
      try { saved[i].s.setProperty(saved[i].p, saved[i].v, saved[i].pri); } catch (e) {}
    }
    saved.length = 0;
    for (var s = 0; s < document.styleSheets.length; s++) {
      var rules;
      try { rules = document.styleSheets[s].cssRules; } catch (e) { continue; }
      if (rules) walk(rules, h, w);
    }
  }

  if (!window.__kkViewportShim) {
    window.__kkViewportShim = true;
    window.addEventListener('resize', apply);
    window.addEventListener('orientationchange', apply);
    setTimeout(apply, 400);
  }
  apply();
})()
"""

private fun isLoopback(uri: Uri): Boolean {
    if (uri.scheme != "http" && uri.scheme != "https") return false
    val host = uri.host ?: return false
    return host == "127.0.0.1" || host == "localhost" || host == "::1"
}

/** Unwraps the Activity a Compose WebView host is attached to. */
@Composable
private fun LocalActivity(): Activity? {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) break
            current = current.baseContext
        }
        current as? Activity
    }
}
