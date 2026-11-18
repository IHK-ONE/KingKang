package com.kingkang.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kingkang.app.BuildConfig
import com.kingkang.app.EnvironmentUi
import com.kingkang.app.HarnessUi
import com.kingkang.app.MainViewModel
import com.kingkang.app.agent.AgentConnection
import com.kingkang.app.agent.AgentState
import com.kingkang.app.device.ShizukuManager
import com.kingkang.app.device.UsbMonitor
import com.kingkang.app.ui.components.ScrollEdgeFade
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.Background
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.SurfaceVariant
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary

/**
 * The full diagnostic page: the same sanitized summary the About dialog shows, plus the
 * rolling bridge log. Everything here can be copied in one tap, and nothing that looks
 * like a credential is allowed through [sanitizeLogLine].
 */
@Composable
fun DiagnosticsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.agentState.collectAsState()
    val environment by vm.environment.collectAsState()
    val harness by vm.harness.collectAsState()
    val rootMode by vm.rootMode.collectAsState()
    val otg by UsbMonitor.state.collectAsState()
    val shizuku by ShizukuManager.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()

    val summary = buildDiagnosticsText(
        context = context,
        state = state,
        environment = environment,
        harness = harness,
        rootMode = rootMode,
        otg = otg,
        shizuku = shizuku
    )
    val logs = state.logs.reversed().joinToString("\n") { sanitizeLogLine(it) }

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
                .height(58.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = TextSecondary)
            }
            Column(Modifier.weight(1f)) {
                Text("诊断与日志", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                Text(
                    "可复制给支持人员；不含密钥或对话内容",
                    color = TextMuted,
                    fontSize = 10.5.sp
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "复制全部",
                color = Accent,
                fontSize = 11.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString("$summary\n\n日志：\n$logs"))
                        vm.notify("诊断信息已复制")
                    }
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            )
            Text(
                "清空日志",
                color = TextMuted,
                fontSize = 11.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.clearAgentLogs() }
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            )
        }
        ScrollEdgeFade(
            canScrollForward = scroll.canScrollForward,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    summary,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariant)
                        .padding(12.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("桥接日志", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "最近 ${state.logs.size} 条；启动、连接、dsh 与插件操作的原始行。",
                    color = TextMuted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(8.dp))
                if (logs.isBlank()) {
                    Text("还没有日志。", color = TextMuted, fontSize = 11.5.sp)
                } else {
                    Text(
                        logs,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

/** The sanitized one-screen summary shared by the page (and the About entry point). */
internal fun buildDiagnosticsText(
    context: Context,
    state: AgentState,
    environment: EnvironmentUi,
    harness: HarnessUi,
    rootMode: Boolean,
    otg: UsbMonitor.State,
    shizuku: ShizukuManager.State
): String = buildString {
    appendLine("KingKang ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("Agent：${state.connection}")
    appendLine("dsh：${state.dshVersion ?: "未知"}")
    appendLine("Node：${harness.nodeVersion ?: "未知"}")
    appendLine("Kali：${environment.version ?: environment.label ?: "未安装"}")
    appendLine("根模式：${if (rootMode) "开启" else "关闭"}")
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    appendLine("通知权限：${if (notifications) "已允许" else "未授予"}")
    appendLine(
        "OTG：${
            when {
                otg.interfaces.isNotEmpty() -> otg.interfaces.joinToString("、")
                otg.ethernet -> "以太网"
                else -> "未就绪"
            }
        }"
    )
    appendLine(
        "Shizuku：${
            when {
                shizuku.disabled -> "已停用"
                shizuku.connected -> "已授权"
                shizuku.authorized -> "连接中"
                shizuku.running -> "未授权"
                shizuku.installed -> "未运行"
                else -> "未安装"
            }
        }"
    )
    val wifi = state.wifiProbe
    appendLine(
        "Kali 无线：${
            when {
                !wifi.checked -> "未检测"
                wifi.interfaces.isEmpty() -> wifi.reason ?: "无接口"
                else -> wifi.interfaces.joinToString("、") {
                    "${it.name}(${it.phy}${if (it.monitor) ",monitor" else ""})"
                }
            }
        }"
    )
    append("Kali 安装中：${environment.installing} · dsh 安装中：${harness.busy}")
}

/**
 * Removes things that look like credentials before a log line leaves the device. The
 * bridge already masks the one-time dsh URL token; this covers pasted keys and headers
 * from tool output that reaches the app log.
 */
internal fun sanitizeLogLine(line: String): String = line
    .replace(Regex("(?i)(token|api[_-]?key|authorization|password)=([^\\s&,;]+)"), "$1=***")
    .replace(Regex("sk-[A-Za-z0-9_-]{8,}"), "sk-***")
    .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._-]{8,}"), "Bearer ***")
