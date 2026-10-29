package com.kingkang.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.kingkang.app.HarnessUi
import com.kingkang.app.MainViewModel
import com.kingkang.app.RootProbe
import com.kingkang.app.agent.AgentConnection
import com.kingkang.app.agent.AgentPresetCopy
import com.kingkang.app.agent.AgentState
import com.kingkang.app.ui.components.Dot
import com.kingkang.app.ui.components.TerminalMark
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.AccentDim
import com.kingkang.app.ui.theme.AccentSoft
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.ErrorContainer
import com.kingkang.app.ui.theme.ErrorText
import com.kingkang.app.ui.theme.ErrorTextMuted
import com.kingkang.app.ui.theme.OnAccent
import com.kingkang.app.ui.theme.Red
import com.kingkang.app.ui.theme.Success
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.SurfaceRaised
import com.kingkang.app.ui.theme.SurfaceVariant
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenHarness: () -> Unit,
    onAbout: () -> Unit,
    focusSection: String? = null
) {
    val environment by vm.environment.collectAsState()
    val rootMode by vm.rootMode.collectAsState()
    val rootProbe by vm.rootProbe.collectAsState()
    val harness by vm.harness.collectAsState()
    val agent by vm.agentState.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val npmRegistry by vm.npmRegistry.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmUninstallHarness by remember { mutableStateOf(false) }
    var showNpmSource by remember { mutableStateOf(false) }
    val credentialsAnchor = remember { BringIntoViewRequester() }

    LaunchedEffect(Unit) {
        if (rootMode) vm.checkRoot()
        vm.refreshHarness()
        vm.refreshAgentCredentials()
        vm.refreshAgentPermissions()
        vm.refreshAgentPresets()
    }

    // Entering from the "configure API Key" prompt lands on the credentials card instead
    // of the top of a long page. A short wait lets the cards above finish first layout.
    LaunchedEffect(focusSection) {
        if (focusSection == "credentials") {
            delay(220)
            runCatching { credentialsAnchor.bringIntoView() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(com.kingkang.app.ui.theme.Background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
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
            Text(
                "设置",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            SectionLabel("外观")
            Spacer(Modifier.height(8.dp))
            AppearanceCard(
                themeMode = themeMode,
                onSelect = vm::setThemeMode
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("Kali 环境")
            Spacer(Modifier.height(8.dp))
            EnvironmentCard(
                environment = environment,
                rootfsPath = vm.rootfsPath,
                onInstall = { vm.installRootfs(startAfter = false) },
                onCancel = vm::cancelInstall,
                onDismissError = vm::dismissEnvironmentError,
                onCheckUpdate = vm::checkRootfsUpdate,
                onDelete = { confirmDelete = true },
                onOpenTerminal = onOpenTerminal
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("Agent 核心")
            Spacer(Modifier.height(8.dp))
            // The npm source leads the section because dsh itself is installed from it;
            // the same choice is later reused for plugin installs.
            NpmSourceCard(
                registry = npmRegistry,
                onSelect = vm::setNpmRegistry,
                onCustom = { showNpmSource = true }
            )

            Spacer(Modifier.height(10.dp))
            HarnessCard(
                harness = harness,
                liveVersion = agent.dshVersion,
                kaliInstalled = environment.installed,
                onInstall = vm::installHarness,
                onCheckUpdate = vm::checkHarnessUpdate,
                onUninstall = { confirmUninstallHarness = true },
                onCancel = vm::cancelHarnessTask,
                onDismissError = vm::dismissHarnessError
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("Agent 连接")
            Spacer(Modifier.height(8.dp))
            AgentConnectionCard(
                agent = agent,
                onRestart = vm::restartAgent,
                onStop = vm::stopAgent
            )

            Spacer(Modifier.height(10.dp))
            Box(Modifier.bringIntoViewRequester(credentialsAnchor)) {
                AgentCredentialsCard(
                    agent = agent,
                    onSave = vm::setAgentCredential,
                    onRemove = vm::removeAgentCredential,
                    onRefresh = vm::refreshAgentCredentials
                )
            }

            Spacer(Modifier.height(10.dp))
            AgentPermissionCard(
                agent = agent,
                onSelect = vm::setAgentDefaultPermission,
                onRefresh = vm::refreshAgentPermissions
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("dsh 完整设置")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "DeepSeek Harness",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "轨迹视图、自定义插件页和 dsh-web 的其余设置在 Harness 里；" +
                                "快捷设置会与它保持同步。",
                            color = TextMuted,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = onOpenHarness,
                        enabled = agent.connection == com.kingkang.app.agent.AgentConnection.Ready,
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, Border),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("打开", fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("运行模式")
            Spacer(Modifier.height(8.dp))
            AgentPresetCard(
                agent = agent,
                onSelect = vm::setAgentPreset,
                onRefresh = vm::refreshAgentPresets
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("运行方式")
            Spacer(Modifier.height(8.dp))
            RootSettingCard(
                rootMode = rootMode,
                probe = rootProbe,
                onToggle = vm::setRootMode,
                onCheck = vm::checkRoot
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("存储与隐私")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsInfoRow("运行位置", "应用私有目录")
                SettingsInfoRow("共享目录", "files/shared")
                SettingsInfoRow("网络", "仅使用设备本机网络")
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("关于")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TerminalMark(size = 38.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "KingKang 0.1.0",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "PRoot (GPLv2) · xterm.js (MIT)",
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onAbout,
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, Border),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("说明", fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmUninstallHarness) {
        ConfirmDialog(
            title = "卸载 Agent 核心？",
            text = "会从 Kali 内删除 dsh、Node.js 与 npm 依赖缓存（约 1.4 GB）。" +
                "Kali 环境本身和 /root 中的其他文件不受影响。",
            confirmLabel = "卸载",
            destructive = true,
            onDismiss = { confirmUninstallHarness = false },
            onConfirm = {
                confirmUninstallHarness = false
                vm.removeHarness()
            }
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除 Kali 环境？",
            text = "根文件系统会从应用私有目录中删除。再次使用终端时需要重新下载，共享目录不会被删除。",
            confirmLabel = "删除",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.removeRootfs()
            }
        )
    }

    if (showNpmSource) {
        NpmSourceDialog(
            initial = npmRegistry,
            onDismiss = { showNpmSource = false },
            onSave = { value ->
                showNpmSource = false
                vm.setNpmRegistry(value)
            }
        )
    }
}

@Composable
private fun AppearanceCard(
    themeMode: String,
    onSelect: (String) -> Unit
) {
    SettingsCard {
        Text(
            "主题",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "切换后同步到 DeepSeek Harness。",
            color = TextMuted,
            fontSize = 10.5.sp
        )
        Spacer(Modifier.height(12.dp))
        ChoiceSegmented(
            options = listOf(
                "system" to "跟随系统",
                "light" to "浅色",
                "dark" to "深色"
            ),
            selected = themeMode
        ) { onSelect(it) }
    }
}

@Composable
private fun AgentPresetCard(
    agent: AgentState,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val ready = agent.connection == AgentConnection.Ready
    val settings = agent.agentPresetSettings
    val presets = agent.agentPresets
    val currentId = settings.defaultId ?: presets.firstOrNull { it.isDefault }?.id
    val current = presets.firstOrNull { it.id == currentId }

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Agent 预设",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    !ready -> "等待连接"
                    !settings.loaded -> "读取中"
                    current != null -> AgentPresetCopy.name(current)
                    else -> "—"
                },
                color = if (ready && settings.loaded) Success else TextMuted,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "选择新对话使用的运行模式。已开始的对话会保留创建时的模式。",
            color = TextMuted,
            fontSize = 9.5.sp,
            lineHeight = 14.sp
        )

        if (ready && settings.loaded && presets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Border.copy(alpha = 0.7f))
            presets.forEach { preset ->
                val selected = preset.id == currentId
                val unusable = preset.broken != null || !settings.writable
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !unusable && !selected) { onSelect(preset.id) }
                        .background(
                            if (selected) AccentDim.copy(alpha = 0.6f) else Color.Transparent
                        )
                        .padding(horizontal = 9.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        Modifier
                            .padding(top = 5.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    preset.broken != null -> ErrorTextMuted
                                    selected -> Accent
                                    else -> SurfaceVariant
                                }
                            )
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                AgentPresetCopy.name(preset),
                                color = when {
                                    preset.broken != null -> TextMuted
                                    selected -> TextPrimary
                                    else -> TextSecondary
                                },
                                fontSize = 12.5.sp
                            )
                            if (selected) {
                                Spacer(Modifier.width(8.dp))
                                Text("新对话默认", color = Accent, fontSize = 9.5.sp)
                            }
                        }
                        val description = preset.broken?.let { "加载失败：$it" }
                            ?: AgentPresetCopy.description(preset)
                        if (description != null) {
                            Text(
                                description,
                                color = TextMuted,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        } else if (ready && settings.loaded) {
            Spacer(Modifier.height(8.dp))
            Text(
                "dsh 没有提供可选的运行模式。",
                color = TextMuted,
                fontSize = 9.5.sp
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onRefresh,
                enabled = ready,
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text("刷新状态", color = TextMuted, fontSize = 10.5.sp)
            }
            Spacer(Modifier.weight(1f))
            if (ready && settings.loaded && presets.isNotEmpty() && !settings.writable) {
                Text(
                    "当前配置不可写",
                    color = Amber.copy(alpha = 0.9f),
                    fontSize = 9.5.sp
                )
            }
        }
    }
}

@Composable
private fun ChoiceSegmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant.copy(alpha = 0.55f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (key, label) ->
            val active = key == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) Surface else Color.Transparent)
                    .clickable { if (!active) onSelect(key) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) TextPrimary else TextMuted,
                    fontSize = 11.5.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RootSettingCard(
    rootMode: Boolean,
    probe: RootProbe,
    onToggle: (Boolean) -> Unit,
    onCheck: () -> Unit
) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(
                        if (rootMode) Amber.copy(alpha = 0.12f) else SurfaceVariant,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (rootMode) Amber else TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Root 模式",
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "使用设备 su + chroot 启动 Kali",
                    color = TextMuted,
                    fontSize = 10.5.sp
                )
            }
            Switch(
                checked = rootMode,
                onCheckedChange = onToggle,
                enabled = probe !is RootProbe.Checking,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnAccent,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceVariant,
                    checkedBorderColor = Accent,
                    uncheckedBorderColor = Border
                )
            )
        }

        Spacer(Modifier.height(13.dp))
        HorizontalDivider(color = Border.copy(alpha = 0.7f))
        Spacer(Modifier.height(11.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            when (probe) {
                is RootProbe.Checking -> {
                    CircularProgressIndicator(
                        color = Amber,
                        strokeWidth = 1.6.dp,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "正在请求 su 权限…",
                        color = Amber,
                        fontSize = 10.5.sp
                    )
                }

                is RootProbe.Available -> {
                    Dot(color = Success, size = 5.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "已检测到 ${probe.suPath}",
                        color = Success,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                is RootProbe.Unavailable -> {
                    Dot(color = Amber, size = 5.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "未检测到 su · 不会尝试 root 设备",
                        color = Amber,
                        fontSize = 10.sp
                    )
                }

                RootProbe.Unknown -> {
                    Dot(color = TextMuted, size = 5.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("尚未检测 Root 状态", color = TextMuted, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onCheck,
                enabled = probe !is RootProbe.Checking,
                modifier = Modifier.height(30.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Border),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary,
                    disabledContentColor = TextMuted
                )
            ) {
                Text("检测", fontSize = 10.5.sp)
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "检测会执行 su -c id，设备可能弹出授权请求。切换运行方式会在下次启动终端时生效；" +
                "KingKang 不会尝试获取或提升 root 权限。",
            color = TextMuted,
            fontSize = 9.5.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun EnvironmentCard(
    environment: com.kingkang.app.EnvironmentUi,
    rootfsPath: String,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDelete: () -> Unit,
    onOpenTerminal: () -> Unit
) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(SurfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                TerminalMark(size = 26.dp, filled = false)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Kali NetHunter",
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            environment.deleting -> "删除中"
                            environment.installing -> "安装中"
                            environment.checkingUpdate -> "检查中"
                            environment.installed -> "已就绪"
                            else -> "未安装"
                        },
                        color = when {
                            environment.deleting || environment.installing ||
                                environment.checkingUpdate -> Amber
                            environment.installed -> Success
                            else -> TextMuted
                        },
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    environment.version ?: environment.label ?: "Kali rootfs",
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            rootfsPath,
            color = TextMuted.copy(alpha = 0.8f),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (environment.installing || environment.deleting) {
            Spacer(Modifier.height(13.dp))
            val progress = if (environment.deleting) null else environment.progress
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    environment.status
                        ?: if (environment.deleting) "正在删除 Kali 环境…" else "正在下载…",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (progress != null) "${(progress * 100).toInt()}%" else "",
                    color = AccentSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            if (progress != null) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Accent,
                    trackColor = AccentDim
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Accent,
                    trackColor = AccentDim
                )
            }
            if (environment.installing) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Border),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("取消", fontSize = 10.5.sp)
                }
            }
        }

        if (environment.error != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(ErrorContainer, RoundedCornerShape(11.dp))
                    .border(1.dp, Red.copy(alpha = 0.35f), RoundedCornerShape(11.dp))
                    .padding(10.dp)
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Red,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        environment.error ?: "",
                        color = ErrorTextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                    TextButton(
                        onClick = onDismissError,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("关闭", color = ErrorText, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        when {
            environment.installing || environment.deleting -> Unit

            environment.installed -> {
                Button(
                    onClick = onOpenTerminal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent
                    )
                ) {
                    Text("打开 Kali 终端", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCheckUpdate,
                        enabled = !environment.checkingUpdate,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        Text(
                            if (environment.checkingUpdate) "检查中…" else "检查更新",
                            fontSize = 11.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Red.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("卸载", fontSize = 11.sp)
                    }
                }
                environment.notice?.let { notice ->
                    Spacer(Modifier.height(7.dp))
                    Text(notice, color = Success, fontSize = 10.sp)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "检查更新会比对 kali.download 上当前的镜像校验值，发现新版本时自动更新。" +
                        "更新只替换系统文件，/root 与 /opt/kingkang 会保留。",
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp
                )
            }

            else -> {
                Button(
                    onClick = onInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent
                    )
                ) {
                    Text(
                        "下载并安装 · ${formatBytes(environment.approxBytes)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "安装会下载 Kali NetHunter 完整根文件系统并校验 SHA-256。" +
                        "解压后占用明显更多空间，请预留足够存储。",
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun HarnessCard(
    harness: HarnessUi,
    liveVersion: String?,
    kaliInstalled: Boolean,
    onInstall: () -> Unit,
    onCheckUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit
) {
    // A connected bridge proves dsh exists even when the status probe has not run yet
    // (for example when dsh was installed outside the in-app flow).
    val installed = harness.installed || !liveVersion.isNullOrBlank()
    val versionText = harness.version ?: liveVersion
    val statusLabel = when {
        harness.busy -> when {
            harness.step?.contains("卸载") == true -> "卸载中"
            harness.step?.contains("更新") == true -> "更新中"
            else -> "安装中"
        }
        harness.checkingUpdate -> "检查中"
        installed -> "已安装"
        else -> "未安装"
    }
    val statusColor = when {
        harness.busy || harness.checkingUpdate -> Amber
        installed -> Success
        else -> TextMuted
    }

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(AccentDim, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "dsh",
                    color = AccentSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "DeepSeek Harness",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(statusLabel, color = statusColor, fontSize = 11.sp)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    if (!kaliInstalled) {
                        "先安装 Kali 环境，再把 dsh 装进 Kali"
                    } else {
                        "Kali 内的 Agent 核心 · 使用所选 NPM 源"
                    },
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (installed && !harness.busy) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Border.copy(alpha = 0.7f))
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append("dsh ${versionText ?: "已安装"}")
                        harness.nodeVersion?.let { append(" · Node $it") }
                    },
                    modifier = Modifier.weight(1f),
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (harness.checkingUpdate) {
                    CircularProgressIndicator(
                        color = Accent,
                        strokeWidth = 1.6.dp,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            if (harness.updateAvailable) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "可更新到 ${harness.latestVersion}（当前 ${versionText ?: "未知"}）",
                    color = Amber,
                    fontSize = 10.sp
                )
            } else if (harness.latestVersion != null && !harness.checkingUpdate) {
                Spacer(Modifier.height(4.dp))
                Text("已是最新版本", color = Success, fontSize = 10.sp)
            }
        }

        if (harness.busy) {
            Spacer(Modifier.height(12.dp))
            Text(
                harness.step ?: "正在执行…",
                color = TextSecondary,
                fontSize = 10.5.sp
            )
            Spacer(Modifier.height(8.dp))
            val fraction = harness.progress
            if (fraction != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp),
                        color = Accent,
                        trackColor = AccentDim
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${(fraction * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Accent,
                    trackColor = AccentDim
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.height(30.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Border),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text("取消", fontSize = 10.5.sp)
            }
        }

        if (harness.error != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(ErrorContainer, RoundedCornerShape(11.dp))
                    .border(1.dp, Red.copy(alpha = 0.35f), RoundedCornerShape(11.dp))
                    .padding(10.dp)
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Red,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        harness.error ?: "",
                        color = ErrorTextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                    TextButton(
                        onClick = onDismissError,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("关闭", color = ErrorText, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        when {
            !kaliInstalled -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent,
                        disabledContainerColor = SurfaceVariant,
                        disabledContentColor = TextMuted
                    )
                ) {
                    Text("安装", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "需要先完成上方 Kali 环境安装。",
                    color = TextMuted,
                    fontSize = 9.5.sp
                )
            }

            harness.busy -> Unit

            !installed -> {
                Button(
                    onClick = onInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent
                    )
                ) {
                    Text("安装", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "会安装 Node.js 24 与 @deepseek-ai/dsh（约 1.4 GB，另用约 300 MB 依赖缓存），" +
                        "npm 包来自上方所选源；首次安装视网络需要 20–40 分钟，" +
                        "Node.js 来自 nodejs.org 并校验 SHA-256。",
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp
                )
            }

            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCheckUpdate,
                        enabled = !harness.checkingUpdate,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        Text(
                            if (harness.checkingUpdate) "检查中…" else "检查更新",
                            fontSize = 11.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onUninstall,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Red.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("卸载", fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "检查更新会比对所选 NPM 源上的最新版本，发现新版本时自动更新并重启本地桥接。",
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

/** Alibaba's public npm mirror; the app's default for dsh installs and plugin installs. */
private const val NPM_SOURCE_ALIYUN = "https://registry.npmmirror.com/"
private const val NPM_SOURCE_OFFICIAL = "https://registry.npmjs.org/"

/** Aliyun leads the list because it is what a new profile gets. */
private val NPM_SOURCE_OPTIONS = listOf(
    Triple(NPM_SOURCE_ALIYUN, "阿里源", "registry.npmmirror.com · 默认"),
    Triple(NPM_SOURCE_OFFICIAL, "npm 官方源", "registry.npmjs.org")
)

private fun npmSourceKey(value: String): String = value.trim().trimEnd('/')

/**
 * npm source shared by dsh installs/updates and plugin installs. It is stored app-side so
 * it can be chosen before dsh exists; the bridge mirrors it into the profile's `.npmrc`.
 */
@Composable
private fun NpmSourceCard(
    registry: String,
    onSelect: (String) -> Unit,
    onCustom: () -> Unit
) {
    val key = npmSourceKey(registry)
    // A blank value is the app's default; show Aliyun as selected without writing a line.
    val effectiveKey = key.ifEmpty { npmSourceKey(NPM_SOURCE_ALIYUN) }
    val custom = key.isNotEmpty() &&
        NPM_SOURCE_OPTIONS.none { npmSourceKey(it.first) == key }
    val label = when {
        key.isEmpty() -> "阿里源（默认）"
        key == npmSourceKey(NPM_SOURCE_ALIYUN) -> "阿里源"
        key == npmSourceKey(NPM_SOURCE_OFFICIAL) -> "npm 官方源"
        else -> "自定义"
    }

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NPM 源",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                label,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "安装与更新 dsh、检查与安装插件时使用的 npm 源。默认阿里源，下次安装或更新生效。",
            color = TextMuted,
            fontSize = 9.5.sp,
            lineHeight = 14.sp
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Border.copy(alpha = 0.7f))
        NPM_SOURCE_OPTIONS.forEach { (value, title, detail) ->
            NpmSourceRow(
                title = title,
                detail = detail,
                selected = npmSourceKey(value) == effectiveKey,
                // Picking the source already in use would only write the same file again.
                tappable = npmSourceKey(value) != effectiveKey,
                onClick = { onSelect(value) }
            )
        }
        NpmSourceRow(
            title = "自定义",
            detail = if (custom) registry else "填写私有或自建 npm 源地址",
            selected = custom,
            // The custom row opens its editor, so it stays tappable even when selected.
            tappable = true,
            onClick = onCustom
        )
    }
}

@Composable
private fun NpmSourceRow(
    title: String,
    detail: String,
    selected: Boolean,
    tappable: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = tappable, onClick = onClick)
            .background(if (selected) AccentDim.copy(alpha = 0.6f) else Color.Transparent)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (selected) Accent else Border)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 12.5.sp
            )
            Text(
                detail,
                color = TextMuted,
                fontSize = 9.5.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NpmSourceDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val known = NPM_SOURCE_OPTIONS.any { npmSourceKey(it.first) == npmSourceKey(initial) }
    var input by remember { mutableStateOf(if (known) "" else initial) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = { Text("自定义 NPM 源", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "填写 http(s) 地址，例如公司内网的 Verdaccio。",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = {
                            input = it.take(200)
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = TextPrimary,
                            fontSize = 12.sp
                        ),
                        cursorBrush = SolidColor(Accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        decorationBox = { inner ->
                            if (input.isEmpty()) {
                                Text(
                                    "https://npm.example.com/",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            inner()
                        }
                    )
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = ErrorText, fontSize = 10.5.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = input.trim()
                    if (!value.startsWith("http://") && !value.startsWith("https://")) {
                        error = "地址需要以 http:// 或 https:// 开头"
                    } else {
                        onSave(value)
                    }
                }
            ) {
                Text("保存", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun AgentConnectionCard(
    agent: AgentState,
    onRestart: () -> Unit,
    onStop: () -> Unit
) {
    var showLogs by remember { mutableStateOf(false) }
    val status = when (agent.connection) {
        AgentConnection.Ready -> "已连接"
        AgentConnection.Starting -> "启动中"
        AgentConnection.Failed -> "未连接"
        AgentConnection.Stopped -> "已停止"
    }
    val statusColor = when (agent.connection) {
        AgentConnection.Ready -> Success
        AgentConnection.Starting -> Amber
        AgentConnection.Failed -> Red
        AgentConnection.Stopped -> TextMuted
    }

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color = statusColor, size = 6.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                "DeepSeek Harness 桥接",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(status, color = statusColor, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "dsh 只在 Kali 回环地址监听，应用通过 stdio 桥接读取流式事件。",
            color = TextMuted,
            fontSize = 9.5.sp,
            lineHeight = 14.sp
        )

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Border.copy(alpha = 0.7f))
        SettingsInfoRow("dsh", agent.dshVersion ?: "—")
        SettingsInfoRow("桥接", agent.bridgeVersion?.let { "KK-Agent $it" } ?: "—")
        SettingsInfoRow(
            "模型",
            agent.models?.let { models ->
                val name = models.current?.name ?: models.model
                if (name.isBlank()) "—" else if (models.routable) name else "$name · 不可用"
            } ?: "—"
        )
        SettingsInfoRow("权限模式", agent.permissionMode ?: "跟随 dsh")
        SettingsInfoRow("状态", agent.status)

        if (!agent.error.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                agent.error ?: "",
                color = Red.copy(alpha = 0.9f),
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRestart,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Border),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text("重启桥接", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = onStop,
                enabled = agent.connection != AgentConnection.Stopped,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Border),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary,
                    disabledContentColor = TextMuted
                )
            ) {
                Text("停止", fontSize = 11.sp)
            }
            TextButton(
                onClick = { showLogs = !showLogs },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(if (showLogs) "收起日志" else "日志", color = TextMuted, fontSize = 10.5.sp)
            }
        }

        if (showLogs) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .background(SurfaceVariant, RoundedCornerShape(10.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text(
                    if (agent.logs.isEmpty()) {
                        "暂无日志"
                    } else {
                        agent.logs.takeLast(60).joinToString("\n")
                    },
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

/**
 * Model provider credentials live in dsh's own store. The app can describe and replace a
 * reference, but never reads the secret back — that is the whole point of the API.
 */
@Composable
private fun AgentCredentialsCard(
    agent: AgentState,
    onSave: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val ref = "DEEPSEEK_API_KEY"
    val credential = agent.credentials[ref]
    val ready = agent.connection == AgentConnection.Ready
    var keyInput by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "模型与凭据",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    !ready -> "等待连接"
                    credential == null -> "读取中"
                    credential.configured -> "已配置"
                    else -> "未配置"
                },
                color = when {
                    !ready -> TextMuted
                    credential?.configured == true -> Success
                    else -> Amber
                },
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "API Key 只写入 dsh 的凭据存储，读取接口不会返回明文。",
            color = TextMuted,
            fontSize = 9.5.sp,
            lineHeight = 14.sp
        )

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Border.copy(alpha = 0.7f))
        SettingsInfoRow("供应商", agent.models?.provider?.takeIf { it.isNotBlank() } ?: "—")
        SettingsInfoRow(
            "当前模型",
            agent.models?.let { models ->
                val name = models.current?.name ?: models.model
                when {
                    name.isBlank() -> "—"
                    models.routable -> name
                    else -> "$name · 不可用"
                }
            } ?: "—"
        )
        SettingsInfoRow(
            "DeepSeek API Key",
            when {
                credential == null -> if (ready) "读取中…" else "—"
                !credential.configured -> "未配置"
                credential.source.isNullOrBlank() -> "已配置"
                else -> "已配置 · ${credential.source}"
            }
        )

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceVariant)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it.take(200) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = TextPrimary,
                        fontSize = 12.sp
                    ),
                    cursorBrush = SolidColor(Accent),
                    singleLine = true,
                    enabled = ready,
                    visualTransformation = if (reveal) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    decorationBox = { inner ->
                        if (keyInput.isEmpty()) {
                            Text(
                                if (ready) "输入新的 API Key" else "Agent 尚未连接",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        inner()
                    }
                )
                // A plain tap target instead of a TextButton: a button's 48 dp minimum would
                // make the field taller than the 保存 button beside it.
                Box(
                    Modifier
                        .size(width = 38.dp, height = 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = ready) { reveal = !reveal },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (reveal) "隐藏" else "显示",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (keyInput.isNotBlank()) {
                        onSave(ref, keyInput)
                        keyInput = ""
                        reveal = false
                    }
                },
                enabled = ready && keyInput.isNotBlank() &&
                    (credential?.writable ?: true),
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent,
                    disabledContainerColor = SurfaceVariant,
                    disabledContentColor = TextMuted
                )
            ) {
                Text("保存", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onRefresh() },
                enabled = ready,
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text("刷新状态", color = TextMuted, fontSize = 10.5.sp)
            }
            Spacer(Modifier.weight(1f))
            if (credential?.configured == true) {
                TextButton(
                    onClick = { onRemove(ref) },
                    enabled = ready,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("清除密钥", color = Red.copy(alpha = 0.9f), fontSize = 10.5.sp)
                }
            }
        }
        if (credential?.writable == false) {
            Spacer(Modifier.height(4.dp))
            Text(
                "当前 profile 不允许写入凭据，请在 Kali 中配置。",
                color = Amber.copy(alpha = 0.9f),
                fontSize = 9.5.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun AgentPermissionCard(
    agent: AgentState,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val ready = agent.connection == AgentConnection.Ready
    val settings = agent.permissionSettings
    val catalog = agent.permissionCatalog
    val options = catalog.defaultOptions.ifEmpty { catalog.options }
    val current = settings.defaultPreset ?: catalog.defaultPreset
    val currentLabel = current?.let { permissionLabel(it, options) }

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "新会话权限",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    !ready -> "等待连接"
                    options.isEmpty() -> "读取中"
                    else -> currentLabel ?: "—"
                },
                color = if (ready && options.isNotEmpty()) Success else TextMuted,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "新建对话默认采用的 dsh 权限预设。已有对话可在输入框上方切换。",
            color = TextMuted,
            fontSize = 9.5.sp,
            lineHeight = 14.sp
        )

        if (ready && options.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Border.copy(alpha = 0.7f))
            options.forEach { option ->
                val selected = option.value == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = settings.writable && !selected) {
                            onSelect(option.value)
                        }
                        .background(if (selected) AccentDim.copy(alpha = 0.6f) else Color.Transparent)
                        .padding(horizontal = 9.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (selected) Accent else SurfaceVariant)
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                permissionLabel(option.value, options),
                                color = if (selected) TextPrimary else TextSecondary,
                                fontSize = 12.5.sp
                            )
                            if (selected) {
                                Spacer(Modifier.width(8.dp))
                                Text("当前", color = Accent, fontSize = 9.5.sp)
                            }
                        }
                        permissionDescription(option.value, option)?.let { description ->
                            Text(
                                description,
                                color = TextMuted,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        } else if (ready) {
            Spacer(Modifier.height(8.dp))
            Text(
                "dsh 没有提供可选的权限预设。",
                color = TextMuted,
                fontSize = 9.5.sp
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onRefresh,
                enabled = ready,
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text("刷新状态", color = TextMuted, fontSize = 10.5.sp)
            }
            Spacer(Modifier.weight(1f))
            if (ready && options.isNotEmpty() && !settings.writable) {
                Text(
                    "当前配置不可写",
                    color = Amber.copy(alpha = 0.9f),
                    fontSize = 9.5.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp),
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) Red else Accent,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}
