package com.kingkang.app.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.theme.Background
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.Red
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.SurfaceRaised
import com.kingkang.app.ui.theme.SurfaceVariant
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary
import java.io.ByteArrayInputStream
import java.util.Locale

private val subresourceSchemes = setOf("http", "https", "ws", "wss", "data", "blob", "about")

/**
 * The in-app browser. It runs in its own process with its own WebView data directory, so
 * nothing it stores (cookies, localStorage, cache) is visible to the Harness/dsh WebView —
 * and a local page the user opens here can never carry dsh's loopback session.
 *
 * Only loopback pages load without asking; remote pages must be HTTPS and are confirmed
 * first. File, content, intent and javascript URLs are refused outright.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    initialUrl: String?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var address by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var pageUrl by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    fun sync(view: WebView) {
        canBack = view.canGoBack()
        canForward = view.canGoForward()
        val current = view.url
        if (!current.isNullOrBlank() && current != "about:blank") {
            pageUrl = current
            address = current
        }
    }

    val web = remember(context) {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = true
                userAgentString = "$userAgentString KingKangBrowser"
            }
            // The browser's cookie jar is its own (separate process, separate data dir);
            // remote third-party cookies still have no reason to exist here.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val target = request.url.toString()
                    return when (BrowserPolicy.decide(target)) {
                        UrlDecision.Allow -> false
                        UrlDecision.Confirm -> {
                            pendingConfirm = target
                            true
                        }
                        UrlDecision.Block -> {
                            message = "已阻止不支持的链接（${request.url.scheme ?: "未知"}）"
                            true
                        }
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val scheme = request.url.scheme?.lowercase(Locale.US)
                    return if (scheme == null || scheme in subresourceSchemes) null else blockedResponse()
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    loading = true
                    sync(view)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    loading = false
                    sync(view)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        loading = false
                        message = "无法打开页面：${error.description}"
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (newProgress >= 100) loading = false
                }
            }
            setDownloadListener { url, _, _, _, _ ->
                message = if (BrowserPolicy.isLoopback(url)) {
                    "下载暂未接入：本地文件可用终端或「工作区文件」打开"
                } else {
                    "外部下载已阻止；如需下载请用系统浏览器打开该页面"
                }
            }
        }
    }

    LaunchedEffect(web) {
        if (address.isNotBlank() && web.url == null) {
            web.loadUrl(BrowserPolicy.normalize(address) ?: address)
        }
    }

    DisposableEffect(web) {
        onDispose {
            web.stopLoading()
            web.loadUrl("about:blank")
            web.destroy()
        }
    }

    fun load(raw: String) {
        val normalized = BrowserPolicy.normalize(raw)
        if (normalized == null) {
            message = "地址无效，请输入主机或 http(s) 链接"
            return
        }
        when (BrowserPolicy.decide(normalized)) {
            UrlDecision.Allow -> web.loadUrl(normalized)
            UrlDecision.Confirm -> pendingConfirm = normalized
            UrlDecision.Block -> message = "已阻止：这里只允许本地地址和 HTTPS 外部链接"
        }
    }

    fun openExternally(raw: String) {
        val normalized = BrowserPolicy.normalize(raw) ?: return
        if (BrowserPolicy.decide(normalized) == UrlDecision.Block) {
            message = "这个地址不能交给系统浏览器"
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
        runCatching { context.startActivity(intent) }
            .onFailure { message = "没有可用的浏览器" }
    }

    fun clearData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        runCatching { WebStorage.getInstance().deleteAllData() }
        web.clearCache(true)
        web.clearHistory()
        web.clearFormData()
        message = "浏览器数据已清除"
    }

    BackHandler {
        if (web.canGoBack()) web.goBack() else onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹",
                color = if (canBack) TextPrimary else TextMuted,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (web.canGoBack()) web.goBack() else onClose() }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
            Text(
                "›",
                color = if (canForward) TextPrimary else TextMuted,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (web.canGoForward()) web.goForward() }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            )
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceRaised)
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Accent,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier
                            .width(12.dp)
                            .height(12.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (address.isEmpty()) {
                        Text("127.0.0.1:3000", color = TextMuted, fontSize = 11.5.sp)
                    }
                    BasicTextField(
                        value = address,
                        onValueChange = { address = it.take(2048) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(Accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = { load(address) })
                    )
                }
            }
            Text(
                "前往",
                color = Accent,
                fontSize = 11.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { load(address) }
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            )
            Text(
                "⋯",
                color = TextSecondary,
                fontSize = 17.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showMenu = true }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (pageUrl.isBlank()) "本地预览：只放行 127.0.0.1 / localhost" else pageUrl,
                modifier = Modifier.weight(1f),
                color = TextMuted,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "刷新",
                color = TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { web.reload() }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        message?.let { text ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text,
                    modifier = Modifier.weight(1f),
                    color = if (text.startsWith("已阻止") || text.startsWith("无法")) Amber else TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
                Text(
                    "×",
                    color = TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { message = null }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        AndroidView(
            factory = { web },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }

    pendingConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            containerColor = Surface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            shape = RoundedCornerShape(20.dp),
            title = { Text("打开外部页面？") },
            text = {
                Column {
                    Text(
                        "这个地址不在本机，页面内容来自网络。",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        target,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!BrowserPolicy.isLoopback(target)) {
                        Text(
                            "主机：${BrowserPolicy.hostOf(target)}",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingConfirm = null
                    web.loadUrl(target)
                }) {
                    Text("在应用内打开", color = Accent, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingConfirm = null
                    openExternally(target)
                }) {
                    Text("系统浏览器", color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            containerColor = Surface,
            contentColor = TextPrimary,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
            ) {
                Text(
                    "浏览器",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "独立进程与数据目录：这里的 Cookie 不会与 Harness 或 dsh 共用。",
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                BrowserMenuRow("用系统浏览器打开当前页") {
                    showMenu = false
                    if (pageUrl.isNotBlank()) openExternally(pageUrl) else message = "还没有打开页面"
                }
                BrowserMenuRow("清除浏览器数据") {
                    showMenu = false
                    clearData()
                }
                BrowserMenuRow("关闭浏览器", color = Red) {
                    showMenu = false
                    onClose()
                }
            }
        }
    }
}

@Composable
private fun BrowserMenuRow(
    label: String,
    color: Color = TextPrimary,
    onClick: () -> Unit
) {
    Text(
        label,
        color = color,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp)
    )
}

/** Subresources with these schemes are refused; crossing processes is not a page load. */
private fun blockedResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

/** Shown on API 26–27, where a per-process WebView data directory is not available. */
@Composable
fun BrowserIsolationUnavailable(onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Text("内置浏览器需要 Android 9 或更高版本", color = TextPrimary, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "这个系统不支持为浏览器单独设置 WebView 数据目录，内置浏览器会和 dsh 页面共用 Cookie，存在串号风险，所以这里不提供。可用系统浏览器访问本地服务。",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "关闭",
            color = Accent,
            fontSize = 12.5.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClose)
                .padding(vertical = 8.dp)
        )
    }
}
