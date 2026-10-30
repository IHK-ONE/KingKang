package com.kingkang.app.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kingkang.app.BuildConfig
import com.kingkang.app.MainViewModel
import com.kingkang.app.agent.AgentConnection
import com.kingkang.app.files.FileActions
import com.kingkang.app.files.HarnessDownload
import com.kingkang.app.files.HarnessDownloads
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.Background
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resolves an in-app route hint against the loopback Harness origin. Only a rooted path
 * is accepted; anything that could change origin (a scheme, `//host`, backslashes or
 * control characters) falls back to the Harness home instead of loading it.
 */
internal fun harnessTarget(base: String?, path: String?): String? {
    if (base.isNullOrBlank()) return null
    val clean = path?.trim().orEmpty()
    if (clean.isEmpty()) return base
    if (!clean.startsWith("/") || clean.startsWith("//")) return base
    if (clean.any { it == '\\' || it.isWhitespace() || it.code < 0x20 }) return base
    return base.trimEnd('/') + clean
}

/**
 * The real dsh web surface, rendered in a WebView against the loopback origin the bridge
 * started inside Kali. The app keeps its native chat for day-to-day work; this screen is
 * where plugin-contributed pages, the complete settings tree and the plugin manager live
 * exactly as dsh-web ships them.
 */
@Composable
fun HarnessScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    initialPath: String? = null
) {
    val activity = LocalActivity()
    var url by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    // A non-loopback http(s) link waits here until the user confirms the hand-off to a
    // real browser; loopback navigation never leaves the WebView.
    var pendingExternal by remember { mutableStateOf<String?>(null) }
    // Downloads the page requested. Nothing is fetched until the user confirms the name
    // and size; the fetch itself stays inside the app so the loopback cookie is kept.
    var pendingDownload by remember { mutableStateOf<HarnessDownload?>(null) }
    var activeDownload by remember { mutableStateOf<HarnessDownload?>(null) }
    var failedDownload by remember { mutableStateOf<Pair<HarnessDownload, String>?>(null) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val agentState by vm.agentState.collectAsState()

    val fileChooser = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
    }

    // A page's capture-enabled image input opens the camera app with a private cache
    // target. The file is exposed only through FileProvider and swept on the next capture.
    var pendingCaptureCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    val captureLauncher = rememberLauncherForActivityResult(CaptureImage()) { captured ->
        val callback = pendingCaptureCallback
        val uri = pendingCaptureUri
        pendingCaptureCallback = null
        pendingCaptureUri = null
        callback?.onReceiveValue(
            if (captured && uri != null) arrayOf(uri) else null
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val callback = pendingCaptureCallback
        pendingCaptureCallback = null
        if (!granted || callback == null) {
            callback?.onReceiveValue(null)
            vm.notify("没有相机权限，无法拍照")
            return@rememberLauncherForActivityResult
        }
        val target = createCaptureTarget(context)
        if (target == null) {
            callback.onReceiveValue(null)
            vm.notify("无法创建拍照文件")
            return@rememberLauncherForActivityResult
        }
        pendingCaptureCallback = callback
        pendingCaptureUri = target
        try {
            captureLauncher.launch(target)
        } catch (_: ActivityNotFoundException) {
            pendingCaptureCallback = null
            pendingCaptureUri = null
            callback.onReceiveValue(null)
            vm.notify("设备上没有可用的相机应用")
        }
    }
    val startCapture: (ValueCallback<Array<Uri>>) -> Unit = { callback ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val target = createCaptureTarget(context)
            if (target == null) {
                callback.onReceiveValue(null)
                vm.notify("无法创建拍照文件")
            } else {
                pendingCaptureCallback = callback
                pendingCaptureUri = target
                try {
                    captureLauncher.launch(target)
                } catch (_: ActivityNotFoundException) {
                    pendingCaptureCallback = null
                    pendingCaptureUri = null
                    callback.onReceiveValue(null)
                    vm.notify("设备上没有可用的相机应用")
                }
            }
        } else {
            pendingCaptureCallback = callback
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    // Media grants are handed to one loopback page at a time. The request is parked while
    // the system dialog runs, then granted only for the exact origin that asked and only
    // if the Harness is actually resumed.
    var pendingMediaRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingMediaResources by remember { mutableStateOf<List<String>>(emptyList()) }
    var mediaActive by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val request = pendingMediaRequest
        pendingMediaRequest = null
        val resources = pendingMediaResources
        pendingMediaResources = emptyList()
        if (request == null) return@rememberLauncherForActivityResult
        val resumed = lifecycleOwner.lifecycle.currentState
            .isAtLeast(Lifecycle.State.RESUMED)
        if (!resumed || !isHarnessOrigin(request.origin?.toString(), url)) {
            request.deny()
            return@rememberLauncherForActivityResult
        }
        val allowed = resources.filter { resource ->
            val permission = permissionForResource(resource) ?: return@filter false
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
        if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
        if (allowed.isNotEmpty()) mediaActive = true
    }

    // Fetches a confirmed download in the app process. CookieManager is read on the main
    // thread before the IO hop; the fetch re-validates loopback on every redirect.
    val startDownload: (HarnessDownload) -> Unit = { request ->
        if (activeDownload == null) {
            activeDownload = request
            failedDownload = null
            val cookie = runCatching {
                CookieManager.getInstance().getCookie(request.url)
            }.getOrNull()
            val directory = File(context.cacheDir, "harness-downloads")
            scope.launch {
                try {
                    downloadedFile = withContext(Dispatchers.IO) {
                        HarnessDownloads.download(
                            url = request.url,
                            userAgent = request.userAgent,
                            cookie = cookie,
                            directory = directory,
                            fileName = request.fileName
                        )
                    }
                } catch (t: Throwable) {
                    failedDownload = request to (
                        t.message?.takeIf { it.isNotBlank() } ?: "下载失败"
                        )
                } finally {
                    activeDownload = null
                }
            }
        }
    }

    var pendingSaveDownload by remember { mutableStateOf<File?>(null) }
    val saveDownload = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val file = pendingSaveDownload
        pendingSaveDownload = null
        if (uri != null && file != null) {
            vm.notify(FileActions.copyTo(context, file, uri) ?: "已保存到所选位置")
        }
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
        val base = vm.harnessUrl()
        url = harnessTarget(base, initialPath)
        if (url == null) {
            loading = false
            error = "无法连接 DeepSeek Harness。请确认 Agent 已连接后重试。"
        }
    }

    // If dsh starts after the first attempt failed, recover without making the user find
    // the retry button. One reload per connection transition avoids an error-page loop.
    LaunchedEffect(agentState.connection) {
        if (agentState.connection == AgentConnection.Ready && error != null) {
            attempt += 1
        }
    }

    // Leaving the foreground ends any microphone/camera track the page opened. The page
    // owns its MediaStream, so the shim records and stops what it handed out.
    DisposableEffect(lifecycleOwner, webView) {
        val owned = webView
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                pendingMediaRequest?.deny()
                pendingMediaRequest = null
                pendingMediaResources = emptyList()
                mediaActive = false
                owned?.evaluateJavascript(MEDIA_STOP_SHIM, null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pendingMediaRequest?.deny()
            pendingMediaRequest = null
            owned?.evaluateJavascript(MEDIA_STOP_SHIM, null)
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
            pendingCaptureCallback?.onReceiveValue(null)
            pendingCaptureCallback = null
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

        if (mediaActive) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Harness 正在使用麦克风或摄像头",
                    modifier = Modifier.weight(1f),
                    color = TextSecondary,
                    fontSize = 10.5.sp
                )
                TextButton(
                    onClick = {
                        webView?.evaluateJavascript(MEDIA_STOP_SHIM, null)
                        mediaActive = false
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 0.dp
                    )
                ) {
                    Text("停止", color = Accent, fontSize = 10.5.sp)
                }
            }
        }

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
                            setDownloadListener { downloadUrl, downloadUserAgent, contentDisposition, mimeType, contentLength ->
                                if (!HarnessDownloads.isLoopbackUrl(downloadUrl)) {
                                    vm.notify("已拦截非本机下载链接")
                                } else {
                                    pendingDownload = HarnessDownload(
                                        url = downloadUrl,
                                        fileName = HarnessDownloads.fileNameFor(
                                            contentDisposition,
                                            downloadUrl,
                                            mimeType
                                        ),
                                        mimeType = mimeType.orEmpty(),
                                        size = contentLength,
                                        userAgent = downloadUserAgent
                                    )
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val target = request?.url ?: return true
                                    // The harness is this one loopback origin. A real web
                                    // link is offered to the system browser behind an
                                    // explicit confirmation; everything else (file://,
                                    // intent://, javascript:) is refused outright.
                                    if (isLoopback(target)) return false
                                    if (target.scheme == "http" || target.scheme == "https") {
                                        pendingExternal = target.toString()
                                    } else {
                                        vm.notify("已拦截不支持的链接：${target.scheme ?: "未知"}")
                                    }
                                    return true
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    loading = true
                                    error = null
                                    // A navigation ends whatever media session the old page
                                    // had; do not keep showing "in use" or a stale grant.
                                    pendingMediaRequest?.deny()
                                    pendingMediaRequest = null
                                    pendingMediaResources = emptyList()
                                    mediaActive = false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    view?.evaluateJavascript(MEDIA_SHIM, null)
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
                                    error = harnessErrorText(
                                        err?.description?.toString(),
                                        err?.errorCode ?: 0
                                    )
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    response: WebResourceResponse?
                                ) {
                                    if (BuildConfig.DEBUG) {
                                        Log.w(
                                            "HarnessWeb",
                                            "http ${response?.statusCode} ${request?.url?.path}"
                                        )
                                    }
                                    val status = response?.statusCode ?: return
                                    if (request?.isForMainFrame == true && status >= 400) {
                                        loading = false
                                        error = "Harness 返回 HTTP $status。请稍后重试，或查看 Agent 日志。"
                                    }
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

                                // Media is granted only to the exact loopback Harness origin,
                                // while this screen is resumed, after the user approves the
                                // Android runtime permission. Everything else stays denied.
                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    if (request == null) return
                                    val resumed = lifecycleOwner.lifecycle.currentState
                                        .isAtLeast(Lifecycle.State.RESUMED)
                                    if (!resumed || !isHarnessOrigin(request.origin?.toString(), url)) {
                                        request.deny()
                                        return
                                    }
                                    val resources = request.resources
                                        .filter { permissionForResource(it) != null }
                                    if (resources.isEmpty()) {
                                        request.deny()
                                        return
                                    }
                                    val needed = resources
                                        .mapNotNull { permissionForResource(it) }
                                        .distinct()
                                        .filter {
                                            ContextCompat.checkSelfPermission(context, it) !=
                                                PackageManager.PERMISSION_GRANTED
                                        }
                                    if (needed.isEmpty()) {
                                        request.grant(resources.toTypedArray())
                                        mediaActive = true
                                        return
                                    }
                                    pendingMediaRequest?.deny()
                                    pendingMediaRequest = request
                                    pendingMediaResources = resources
                                    mediaPermissionLauncher.launch(needed.toTypedArray())
                                }

                                // `window.open` reaches here. A throwaway WebView captures
                                // the target URL (with network loads blocked, so the popup
                                // never leaves the device) and the URL is then routed the
                                // same way as an in-page link: loopback stays in the
                                // harness, a real web link asks before opening a browser.
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    val host = view ?: return false
                                    if (transport == null) return false
                                    var consumed = false
                                    val probe = WebView(host.context).apply {
                                        settings.javaScriptEnabled = false
                                        settings.blockNetworkLoads = true
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                inner: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                route(request?.url)
                                                return true
                                            }

                                            override fun onPageStarted(
                                                inner: WebView?,
                                                url: String?,
                                                favicon: Bitmap?
                                            ) {
                                                route(url?.let(Uri::parse))
                                                inner?.stopLoading()
                                            }

                                            private fun route(target: Uri?) {
                                                if (consumed) return
                                                consumed = true
                                                if (target != null) {
                                                    if (isLoopback(target)) {
                                                        host.loadUrl(target.toString())
                                                    } else if (target.scheme == "http" ||
                                                        target.scheme == "https"
                                                    ) {
                                                        pendingExternal = target.toString()
                                                    } else {
                                                        vm.notify(
                                                            "已拦截不支持的链接：${target.scheme ?: "未知"}"
                                                        )
                                                    }
                                                }
                                                this@apply.destroy()
                                            }
                                        }
                                    }
                                    transport.webView = probe
                                    resultMsg.sendToTarget()
                                    // If the popup never navigates, its probe must not linger.
                                    probe.postDelayed({ if (!consumed) probe.destroy() }, 3_000)
                                    return true
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    pendingFileCallback?.onReceiveValue(null)
                                    pendingFileCallback = null
                                    val callback = filePathCallback ?: return false
                                    // An explicit capture request takes the camera path;
                                    // a plain file picker stays a file picker.
                                    if (fileChooserParams?.isCaptureEnabled == true &&
                                        acceptsImage(fileChooserParams)
                                    ) {
                                        startCapture(callback)
                                        return true
                                    }
                                    pendingFileCallback = callback
                                    val intent = try {
                                        fileChooserParams?.createIntent()
                                    } catch (_: ActivityNotFoundException) {
                                        null
                                    }
                                    if (intent == null) {
                                        pendingFileCallback = null
                                        callback.onReceiveValue(null)
                                        return false
                                    }
                                    return try {
                                        fileChooser.launch(intent)
                                        true
                                    } catch (_: ActivityNotFoundException) {
                                        pendingFileCallback = null
                                        callback.onReceiveValue(null)
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

    val external = pendingExternal
    if (external != null) {
        val uri = Uri.parse(external)
        AlertDialog(
            onDismissRequest = { pendingExternal = null },
            containerColor = Surface,
            titleContentColor = TextPrimary,
            textContentColor = TextMuted,
            title = {
                Text("在浏览器中打开？", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            },
            text = {
                // Host and path only: query strings can carry launch tokens or other
                // credentials that must not be put on screen.
                Text(
                    externalLabel(uri),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingExternal = null
                    openExternal(context, uri) { vm.notify(it) }
                }) {
                    Text("打开", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternal = null }) {
                    Text("取消", color = TextMuted)
                }
            }
        )
    }

    pendingDownload?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingDownload = null },
            containerColor = Surface,
            titleContentColor = TextPrimary,
            textContentColor = TextMuted,
            title = {
                Text("下载文件？", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            },
            text = {
                Column {
                    Text(
                        request.fileName,
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                    Text(
                        if (request.size > 0) formatSize(request.size) else "大小未知",
                        modifier = Modifier.padding(top = 4.dp),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        "来自本机 Harness，确认后下载到应用缓存。",
                        modifier = Modifier.padding(top = 8.dp),
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDownload = null
                    startDownload(request)
                }) {
                    Text("下载", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownload = null }) {
                    Text("取消", color = TextMuted)
                }
            }
        )
    }

    activeDownload?.let { request ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = Surface,
            titleContentColor = TextPrimary,
            textContentColor = TextMuted,
            title = {
                Text("正在下载", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        request.fileName,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {}
        )
    }

    failedDownload?.let { (request, message) ->
        AlertDialog(
            onDismissRequest = { failedDownload = null },
            containerColor = Surface,
            titleContentColor = TextPrimary,
            textContentColor = TextMuted,
            title = {
                Text("下载失败", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            },
            text = {
                Text(message, fontSize = 12.sp, lineHeight = 17.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    failedDownload = null
                    startDownload(request)
                }) {
                    Text("重试", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { failedDownload = null }) {
                    Text("关闭", color = TextMuted)
                }
            }
        )
    }

    downloadedFile?.let { file ->
        AlertDialog(
            onDismissRequest = { downloadedFile = null },
            containerColor = Surface,
            titleContentColor = TextPrimary,
            textContentColor = TextMuted,
            title = {
                Text("下载完成", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            },
            text = {
                Column {
                    Text(
                        file.name,
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                    Text(
                        formatSize(file.length()),
                        modifier = Modifier.padding(top = 4.dp),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        downloadedFile = null
                        FileActions.open(context, file)?.let(vm::notify)
                    }) {
                        Text("打开", color = Accent)
                    }
                    TextButton(onClick = {
                        pendingSaveDownload = file
                        downloadedFile = null
                        saveDownload.launch(file.name)
                    }) {
                        Text("保存", color = Accent)
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        downloadedFile = null
                        FileActions.share(context, file)?.let(vm::notify)
                    }) {
                        Text("分享", color = Accent)
                    }
                    TextButton(onClick = { downloadedFile = null }) {
                        Text("完成", color = TextMuted)
                    }
                }
            }
        )
    }
}

/** Scheme, host, and a truncated path — never the query or fragment. */
private fun externalLabel(uri: Uri): String {
    val host = uri.host ?: return uri.toString().take(120)
    val path = uri.path.orEmpty()
    val shown = if (path.isBlank() || path == "/") "" else path.take(80)
    return "${uri.scheme}://$host$shown"
}

private fun openExternal(context: Context, uri: Uri, onError: (String) -> Unit) {
    val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onError("没有可打开此链接的应用")
    } catch (_: SecurityException) {
        onError("系统拒绝打开此链接")
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

private const val MEDIA_SHIM = """
(function(){
  if (window.__kkMediaHook) return;
  window.__kkMediaHook = true;
  var streams = [];
  var devices = navigator.mediaDevices;
  if (devices && devices.getUserMedia) {
    var original = devices.getUserMedia.bind(devices);
    devices.getUserMedia = function(constraints){
      return original(constraints).then(function(stream){
        streams.push(stream);
        return stream;
      });
    };
  }
  var legacy = navigator.getUserMedia || navigator.webkitGetUserMedia;
  if (legacy) {
    var wrapped = function(constraints, ok, fail){
      return legacy.call(navigator, constraints, function(stream){
        streams.push(stream);
        ok(stream);
      }, fail);
    };
    if (navigator.getUserMedia) navigator.getUserMedia = wrapped;
    if (navigator.webkitGetUserMedia) navigator.webkitGetUserMedia = wrapped;
  }
  window.__kkStopMedia = function(){
    for (var i = 0; i < streams.length; i++) {
      try {
        streams[i].getTracks().forEach(function(track){ track.stop(); });
      } catch (e) {}
    }
    streams.length = 0;
    try {
      var media = document.querySelectorAll('audio,video');
      for (var j = 0; j < media.length; j++) {
        var element = media[j];
        if (element.srcObject) {
          element.srcObject.getTracks().forEach(function(track){ track.stop(); });
          element.srcObject = null;
        }
      }
    } catch (e) {}
  };
})()
"""

private const val MEDIA_STOP_SHIM =
    "(function(){try{if(window.__kkStopMedia)window.__kkStopMedia()}catch(e){}})()"

/** Turns WebView's Chromium error strings into a cause the user can act on. */
internal fun harnessErrorText(description: String?, errorCode: Int): String {
    val raw = description.orEmpty().trim()
    val upper = raw.uppercase()
    return when {
        upper.contains("ERR_CONNECTION_REFUSED") ||
            upper.contains("ERR_CONNECTION_RESET") ||
            upper.contains("ERR_CONNECT") ||
            upper.contains("ERR_ADDRESS_UNREACHABLE") ->
            "dsh 还没有在这个端口上监听。等 Agent 连接后重试。"
        upper.contains("ERR_NAME_NOT_RESOLVED") -> "无法解析 Harness 的本机地址。"
        upper.contains("ERR_INTERNET_DISCONNECTED") -> "设备网络已断开。"
        upper.contains("ERR_CERT") || upper.contains("SSL") ->
            "Harness 的证书无效，未加载页面。"
        upper.contains("ERR_TIMED_OUT") -> "连接 Harness 超时。"
        raw.isNotBlank() -> raw
        else -> "Harness 加载失败（错误码 $errorCode）"
    }
}

/**
 * True only for the exact origin the Harness was loaded from. A loopback check alone is
 * not enough: another local port must not inherit a microphone or camera grant.
 */
internal fun isHarnessOrigin(origin: String?, harnessUrl: String?): Boolean {
    if (origin.isNullOrBlank() || harnessUrl.isNullOrBlank()) return false
    val asked = runCatching { java.net.URI(origin) }.getOrNull() ?: return false
    val base = runCatching { java.net.URI(harnessUrl) }.getOrNull() ?: return false
    if (asked.scheme !in setOf("http", "https")) return false
    if (base.scheme !in setOf("http", "https")) return false
    if (!isLoopbackHost(asked.host) || !isLoopbackHost(base.host)) return false
    if (!asked.scheme.equals(base.scheme, ignoreCase = true)) return false
    if (!asked.host.equals(base.host, ignoreCase = true)) return false
    return effectivePort(asked) == effectivePort(base)
}

private fun effectivePort(uri: java.net.URI): Int = when {
    uri.port > 0 -> uri.port
    uri.scheme.equals("https", ignoreCase = true) -> 443
    else -> 80
}

private fun isLoopbackHost(host: String?): Boolean =
    host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "[::1]"

private fun permissionForResource(resource: String): String? = when (resource) {
    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
    else -> null
}

/** Camera output for an image input; the file lives only in the app cache. */
private fun createCaptureTarget(context: Context): Uri? {
    val directory = File(context.cacheDir, "harness-captures")
    if (!directory.exists() && !directory.mkdirs()) return null
    val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    directory.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
    return runCatching {
        FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.files",
            file
        )
    }.getOrNull()
}

private fun acceptsImage(params: WebChromeClient.FileChooserParams): Boolean =
    params.acceptTypes.any { it == "image/*" || it.startsWith("image/") }

/**
 * `TakePicture` without URI grants fails on camera apps that respect the output flag,
 * so the intent carries both the flag and a ClipData entry.
 */
private class CaptureImage : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).apply {
            addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            clipData = ClipData.newRawUri("harness-capture", input)
        }
}

private fun isLoopback(uri: Uri): Boolean {
    if (uri.scheme != "http" && uri.scheme != "https") return false
    return isLoopbackHost(uri.host)
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
