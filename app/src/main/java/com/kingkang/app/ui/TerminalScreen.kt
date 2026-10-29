package com.kingkang.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingkang.app.MainViewModel
import com.kingkang.app.RootProbe
import com.kingkang.app.TerminalUiState
import com.kingkang.app.terminal.TerminalSession
import com.kingkang.app.terminal.TerminalWebView
import com.kingkang.app.terminal.TerminalWebViewState
import com.kingkang.app.ui.components.Dot
import com.kingkang.app.ui.components.KeyboardGlyph
import com.kingkang.app.ui.components.MonoTag
import com.kingkang.app.ui.components.SectionEyebrow
import com.kingkang.app.ui.components.TerminalMark
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.AccentDim
import com.kingkang.app.ui.theme.AccentSoft
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.Background
import com.kingkang.app.ui.theme.ErrorContainer
import com.kingkang.app.ui.theme.ErrorText
import com.kingkang.app.ui.theme.ErrorTextMuted
import com.kingkang.app.ui.theme.OnAccent
import com.kingkang.app.ui.theme.TerminalBackground
import com.kingkang.app.ui.theme.TerminalOverlay
import com.kingkang.app.ui.theme.Red
import com.kingkang.app.ui.theme.Success
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.SurfaceRaised
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val environment by vm.environment.collectAsState()
    val state by vm.terminalState.collectAsState()
    val session by vm.session.collectAsState()
    val rootMode by vm.rootMode.collectAsState()
    val rootProbe by vm.rootProbe.collectAsState()
    val viewState = remember { TerminalWebViewState() }
    // The web view is created fresh for the first session, so clearing it there can race
    // with the first prompt. Only reset when replacing an existing session.
    val lastSession = remember { arrayOfNulls<TerminalSession>(1) }
    val context = LocalContext.current

    // The WebView owns the IME session, so Compose's focus handling cannot dismiss it.
    // Back hides the keyboard while it is open; the bar button toggles it.
    val imeVisible = WindowInsets.isImeVisible
    BackHandler(enabled = imeVisible) { viewState.hideKeyboard() }

    val activeSession = session

    LaunchedEffect(Unit) { vm.prepareTerminal() }

    LaunchedEffect(activeSession) {
        val active = activeSession
        if (active != null) {
            if (lastSession[0] != null && lastSession[0] !== active) {
                viewState.reset()
            }
            lastSession[0] = active
            active.output.collect { chunk -> viewState.write(chunk) }
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
        TerminalTopBar(
            state = state,
            rootMode = rootMode,
            rootReady = rootProbe is RootProbe.Available,
            hasSession = activeSession != null,
            keyboardVisible = imeVisible,
            onBack = onBack,
            onRestart = { vm.restartTerminal() },
            onKeyboard = {
                if (imeVisible) viewState.hideKeyboard() else viewState.showKeyboard()
            }
        )

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                activeSession != null -> TerminalPane(
                    session = activeSession,
                    state = state,
                    viewState = viewState,
                    onRestart = { vm.restartTerminal() }
                )

                state is TerminalUiState.NeedsSetup -> SetupPane(
                    vm = vm,
                    rootMode = rootMode,
                    rootReady = rootProbe is RootProbe.Available,
                    onOpenSettings = onOpenSettings
                )

                state is TerminalUiState.Failed -> TerminalErrorPane(
                    message = (state as TerminalUiState.Failed).message,
                    environmentInstalled = environment.installed,
                    onRetry = { vm.startTerminal() },
                    onOpenSettings = onOpenSettings,
                    onInstall = { vm.installRootfs(startAfter = true) }
                )

                state is TerminalUiState.Starting -> CenteredStatus(
                    title = (state as TerminalUiState.Starting).message,
                    subtitle = "首次启动 PRoot 可能需要几秒"
                )

                else -> CenteredStatus(
                    title = "准备终端…",
                    subtitle = "正在检查本地 Kali 环境"
                )
            }
        }

        if (activeSession != null) {
            KeyBar(
                onKey = { bytes -> activeSession.write(bytes) },
                onPaste = {
                    val text = readClipboard(context)
                    if (text.isNotEmpty()) activeSession.write(text.toByteArray(Charsets.UTF_8))
                }
            )
        }
    }
}

@Composable
private fun TerminalTopBar(
    state: TerminalUiState,
    rootMode: Boolean,
    rootReady: Boolean,
    hasSession: Boolean,
    keyboardVisible: Boolean,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onKeyboard: () -> Unit
) {
    val engineLabel = when (state) {
        is TerminalUiState.Running -> state.engineLabel
        is TerminalUiState.Exited -> state.engineLabel
        else -> if (rootMode && rootReady) "ROOT · chroot" else "PRoot · 无 root"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "返回",
                tint = TextSecondary
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Kali 终端",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(
                    color = when (state) {
                        is TerminalUiState.Running -> Success
                        is TerminalUiState.Exited -> Amber
                        is TerminalUiState.Failed -> Red
                        else -> TextMuted
                    },
                    size = 5.dp
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    engineLabel,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.4.sp
                )
            }
        }
        if (hasSession) {
            IconButton(onClick = onKeyboard) {
                KeyboardGlyph(color = if (keyboardVisible) Accent else TextSecondary)
            }
            IconButton(onClick = onRestart) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "重新启动",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    HorizontalDivider(color = Border.copy(alpha = 0.6f))
}

@Composable
private fun TerminalPane(
    session: TerminalSession,
    state: TerminalUiState,
    viewState: TerminalWebViewState,
    onRestart: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(TerminalBackground)
    ) {
        TerminalWebView(
            state = viewState,
            onInput = { bytes -> session.write(bytes) },
            onResize = { cols, rows -> session.resize(rows, cols) },
            modifier = Modifier.fillMaxSize()
        )

        if (state is TerminalUiState.Running && state.note != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(10.dp),
                color = TerminalOverlay,
                contentColor = Amber,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Amber.copy(alpha = 0.3f))
            ) {
                Text(
                    text = state.note,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    fontSize = 10.5.sp
                )
            }
        }

        if (state is TerminalUiState.Starting) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(TerminalBackground.copy(alpha = 0.86f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(state.message, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        if (state is TerminalUiState.Exited) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth(),
                color = SurfaceRaised,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Border)
            ) {
                Row(
                    Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Dot(color = Amber, size = 6.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("会话已结束", color = TextPrimary, fontSize = 12.sp)
                        Text(
                            "exit code ${state.code}",
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp
                        )
                    }
                    TextButton(onClick = onRestart) {
                        Text("重新启动", color = Accent, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupPane(
    vm: MainViewModel,
    rootMode: Boolean,
    rootReady: Boolean,
    onOpenSettings: () -> Unit
) {
    val environment by vm.environment.collectAsState()
    val shape = RoundedCornerShape(22.dp)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TerminalMark(size = 54.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                SectionEyebrow("LOCAL KALI RUNTIME")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Kali，运行在本机",
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = 24.sp
                )
            }
        }

        Text(
            "KingKang 使用 PRoot 在应用私有目录中运行 Kali NetHunter 完整系统，" +
                "不需要解锁 Bootloader，也不会连接远程 Kali。首次使用需要下载并解压根文件系统。",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )

        Column(
            Modifier
                .fillMaxWidth()
                .background(Surface, shape)
                .border(1.dp, Border, shape)
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            SetupInfoRow("发行版", environment.label ?: "Kali NetHunter full")
            SetupInfoRow("架构", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            SetupInfoRow(
                "运行方式",
                if (rootMode && rootReady) "Root · chroot" else "PRoot · 无 root"
            )
            SetupInfoRow("下载体积", formatBytes(environment.approxBytes))
            SetupInfoRow("数据位置", "应用私有目录")
        }

        if (environment.installing) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceRaised, shape)
                    .border(1.dp, Border, shape)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "正在安装 Kali 环境",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    val progress = environment.progress
                    Text(
                        if (progress != null) "${(progress * 100).toInt()}%" else "…",
                        color = AccentSoft,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                val progress = environment.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Accent,
                        trackColor = AccentDim
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Accent,
                        trackColor = AccentDim
                    )
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    environment.status ?: "正在下载…",
                    color = TextMuted,
                    fontSize = 10.5.sp
                )
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { vm.cancelInstall() },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("取消下载", color = TextSecondary, fontSize = 11.sp)
                }
            }
        } else {
            Button(
                onClick = { vm.installRootfs(startAfter = true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent
                )
            ) {
                Text(
                    "下载并安装 Kali · ${formatBytes(environment.approxBytes)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }

        if (environment.error != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(ErrorContainer, RoundedCornerShape(14.dp))
                    .border(1.dp, Red.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(13.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Red,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "安装失败",
                        color = ErrorText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(3.dp))
                    SelectionContainer {
                        Text(
                            environment.error ?: "",
                            color = ErrorTextMuted,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    TextButton(
                        onClick = { vm.dismissEnvironmentError() },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("关闭", color = ErrorText, fontSize = 11.sp)
                    }
                }
            }
        }

        HorizontalDivider(color = Border.copy(alpha = 0.7f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.startTerminal(debugShell = true) }) {
                Text(
                    "终端自检 · 设备 Shell",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) {
                Text("环境设置", color = TextSecondary, fontSize = 11.sp)
            }
        }

        Text(
            "根文件系统来自 kali.download 的 Kali NetHunter full 构建，安装前会校验 SHA-256。" +
                "Kali 是 Offensive Security 的商标，本项目与其无隶属关系。",
            color = TextMuted,
            fontSize = 9.5.sp,
            lineHeight = 14.sp
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SetupInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(82.dp))
        Text(
            value,
            color = TextSecondary,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun TerminalErrorPane(
    message: String,
    environmentInstalled: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onInstall: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(54.dp)
                .background(Red.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = Red,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "终端启动失败",
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(
                message,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
        ) {
            Text("重试", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(9.dp))
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
        ) {
            Text("打开设置")
        }
        if (!environmentInstalled) {
            TextButton(onClick = onInstall) {
                Text("下载 Kali 环境", color = AccentSoft, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CenteredStatus(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = Accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(title, color = TextPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = TextMuted, fontSize = 10.5.sp)
    }
}

@Composable
private fun KeyBar(
    onKey: (ByteArray) -> Unit,
    onPaste: () -> Unit
) {
    val esc = byteArrayOf(0x1B)
    val tab = byteArrayOf(0x09)
    val ctrlC = byteArrayOf(0x03)
    val ctrlD = byteArrayOf(0x04)
    val ctrlL = byteArrayOf(0x0C)
    val up = byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte())
    val down = byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte())
    val right = byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte())
    val left = byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte())

    Row(
        Modifier
            .fillMaxWidth()
            .background(Surface.copy(alpha = 0.98f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KeyCap("ESC") { onKey(esc) }
        KeyCap("TAB") { onKey(tab) }
        KeyCap("CTRL-C") { onKey(ctrlC) }
        KeyCap("CTRL-D") { onKey(ctrlD) }
        KeyCap("CTRL-L") { onKey(ctrlL) }
        KeyCap("粘贴") { onPaste() }
        KeyCap("↑") { onKey(up) }
        KeyCap("↓") { onKey(down) }
        KeyCap("←") { onKey(left) }
        KeyCap("→") { onKey(right) }
    }
}

@Composable
private fun KeyCap(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = SurfaceRaised,
        border = BorderStroke(1.dp, Border)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.5.sp,
            letterSpacing = 0.3.sp
        )
    }
}

private fun readClipboard(context: Context): String {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
    } catch (t: Throwable) {
        ""
    }
}
