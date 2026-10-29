package com.kingkang.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingkang.app.MainViewModel
import com.kingkang.app.PluginInstallNote
import com.kingkang.app.agent.AgentConnection
import com.kingkang.app.agent.AgentPlugin
import com.kingkang.app.agent.AgentPluginInspection
import com.kingkang.app.agent.AgentPluginOffer
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.Background
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.ErrorContainer
import com.kingkang.app.ui.theme.ErrorText
import com.kingkang.app.ui.theme.OnAccent
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.SurfaceVariant
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Native plugin shelf over dsh's own plugin manager. It mirrors what dsh-web offers —
 * installed bundles with their switches, the official catalog, and one spec field for
 * anything else — without embedding the web page. Installs run pnpm inside Kali and can
 * take minutes, so the screen keeps one install in flight and says so plainly.
 */
@Composable
fun PluginsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenHarness: () -> Unit
) {
    val agent by vm.agentState.collectAsState()
    val changing by vm.pluginChanging.collectAsState()
    val installing by vm.pluginInstalling.collectAsState()
    val installNote by vm.pluginInstallNote.collectAsState()
    val ready = agent.connection == AgentConnection.Ready

    var showInstall by remember { mutableStateOf(false) }
    var spec by remember { mutableStateOf("") }
    var inspecting by remember { mutableStateOf(false) }
    var inspected by remember { mutableStateOf<AgentPluginInspection?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }
    var showRegistry by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(agent.connection) {
        if (ready) {
            vm.refreshPlugins()
            vm.refreshPluginRegistry()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
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
                "插件",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = vm::refreshPlugins, enabled = ready) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    tint = if (ready) TextSecondary else TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                "插件由 Kali 内的 dsh 管理，开关与安装会影响之后开始的会话。",
                color = TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 12.dp)
            )

            val note = installNote
            if (note != null && installing == null) {
                PluginInstallNoteCard(note, onDismiss = vm::dismissPluginInstallNote)
                Spacer(Modifier.height(18.dp))
            }

            val busySpec = installing
            if (busySpec != null) {
                PluginRowCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            color = TextMuted,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "正在安装 $busySpec",
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "pnpm 在 Kali 内下载并写入 profile，可能需要几分钟。",
                                color = TextMuted,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            SectionLabel("已安装")
            Spacer(Modifier.height(8.dp))
            SettingsLikeCard {
                if (!ready) {
                    Text(
                        "连接 Agent 后读取插件。",
                        color = TextMuted,
                        fontSize = 10.5.sp
                    )
                } else if (!agent.pluginLoaded) {
                    Text(
                        "正在读取插件…",
                        color = TextMuted,
                        fontSize = 10.5.sp
                    )
                } else if (agent.plugins.isEmpty()) {
                    Text(
                        "还没有安装插件。",
                        color = TextMuted,
                        fontSize = 10.5.sp
                    )
                } else {
                    agent.plugins.forEachIndexed { index, plugin ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = Border.copy(alpha = 0.7f)
                            )
                        }
                        InstalledPluginRow(
                            plugin = plugin,
                            changing = changing == plugin.name,
                            enabled = ready && changing == null && installing == null,
                            onToggle = { vm.setPluginEnabled(plugin.name, it) }
                        )
                    }
                }
            }

            if (agent.pluginOffers.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionLabel("可安装")
                Spacer(Modifier.height(8.dp))
                SettingsLikeCard {
                    agent.pluginOffers.forEachIndexed { index, offer ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = Border.copy(alpha = 0.7f)
                            )
                        }
                        OfferPluginRow(
                            offer = offer,
                            installing = installing == offer.spec,
                            enabled = ready && installing == null && changing == null,
                            onInstall = { vm.installPlugin(offer.spec) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("安装其他插件")
            Spacer(Modifier.height(8.dp))
            SettingsLikeCard {
                Text(
                    "输入 npm 包名，或 dsh 接受的本地路径、git、tarball 地址。",
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            spec = ""
                            inspected = null
                            installError = null
                            showInstall = true
                        },
                        enabled = ready && installing == null,
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, Border),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        Text("安装…", fontSize = 11.5.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onOpenHarness) {
                        Text("在 Harness 中管理", color = TextMuted, fontSize = 10.5.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("插件源")
            Spacer(Modifier.height(8.dp))
            SettingsLikeCard {
                PluginRegistryCard(
                    registry = agent.pluginRegistry,
                    enabled = ready,
                    onSelect = vm::setPluginRegistry,
                    onCustom = { showRegistry = true }
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showInstall) {
        InstallPluginDialog(
            spec = spec,
            onSpecChange = {
                spec = it
                inspected = null
                installError = null
            },
            inspecting = inspecting,
            inspected = inspected,
            error = installError,
            enabled = ready && installing == null,
            onDismiss = { showInstall = false },
            onConfirm = {
                val value = spec.trim()
                if (value.isBlank() || inspecting) return@InstallPluginDialog
                inspecting = true
                inspected = null
                installError = null
                scope.launch {
                    try {
                        val result = vm.inspectPlugin(value)
                        inspected = result
                        if (result.accepted) {
                            showInstall = false
                            vm.installPlugin(value)
                        }
                    } catch (t: Throwable) {
                        installError = t.message ?: "检查插件失败"
                    } finally {
                        inspecting = false
                    }
                }
            }
        )
    }

    if (showRegistry) {
        RegistryDialog(
            initial = agent.pluginRegistry,
            onDismiss = { showRegistry = false },
            onSave = { value ->
                showRegistry = false
                vm.setPluginRegistry(value)
            }
        )
    }
}

/**
 * Install outcomes are worth keeping on screen: pnpm takes minutes, and dsh's refusal
 * reasons (incompatible, not a bundle, build approval) need more than a passing toast.
 */
@Composable
private fun PluginInstallNoteCard(note: PluginInstallNote, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (note.failed) ErrorContainer else Surface,
                RoundedCornerShape(16.dp)
            )
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                note.text,
                modifier = Modifier.weight(1f),
                color = if (note.failed) ErrorText else TextPrimary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDismiss) {
                Text("知道了", color = TextMuted, fontSize = 10.5.sp)
            }
        }
    }
}

@Composable
private fun InstalledPluginRow(
    plugin: AgentPlugin,
    changing: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plugin.displayTitle,
                    modifier = Modifier.weight(1f, fill = false),
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                plugin.version?.let { version ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        version,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
                if (plugin.official) {
                    Spacer(Modifier.width(6.dp))
                    MonoTag("官方")
                }
            }
            if (plugin.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    plugin.description,
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (changing) {
            Box(
                Modifier.size(width = 52.dp, height = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = TextMuted,
                    strokeWidth = 2.dp
                )
            }
        } else {
            Switch(
                checked = plugin.enabled,
                onCheckedChange = onToggle,
                enabled = enabled,
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
    }
}

@Composable
private fun OfferPluginRow(
    offer: AgentPluginOffer,
    installing: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    offer.displayTitle,
                    modifier = Modifier.weight(1f, fill = false),
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                offer.version?.let { version ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        version,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }
            if (offer.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    offer.description,
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (installing) {
            Box(
                Modifier.size(width = 52.dp, height = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = TextMuted,
                    strokeWidth = 2.dp
                )
            }
        } else {
            OutlinedButton(
                onClick = onInstall,
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Border),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary,
                    disabledContentColor = TextMuted
                )
            ) {
                Text("安装", fontSize = 10.5.sp)
            }
        }
    }
}

@Composable
private fun InstallPluginDialog(
    spec: String,
    onSpecChange: (String) -> Unit,
    inspecting: Boolean,
    inspected: AgentPluginInspection?,
    error: String?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = { Text("安装插件", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "先从 dsh 的 registry 检查，再交给 pnpm 安装并启用。",
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
                        value = spec,
                        onValueChange = onSpecChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = TextPrimary,
                            fontSize = 12.sp
                        ),
                        cursorBrush = SolidColor(Accent),
                        singleLine = true,
                        enabled = enabled && !inspecting,
                        decorationBox = { inner ->
                            if (spec.isEmpty()) {
                                Text(
                                    "例如 @deepseek-ai/dsh-plugin-market",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            inner()
                        }
                    )
                }
                if (inspecting) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = TextMuted,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在检查包名…", color = TextMuted, fontSize = 10.5.sp)
                    }
                }
                val result = inspected
                if (result != null) {
                    Spacer(Modifier.height(12.dp))
                    if (result.accepted) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(
                                result.name ?: spec,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            val detail = listOfNotNull(
                                result.version,
                                result.description?.take(80)
                            ).joinToString(" · ")
                            if (detail.isNotBlank()) {
                                Text(detail, color = TextMuted, fontSize = 10.sp)
                            }
                        }
                    } else {
                        Text(
                            refusalText(result),
                            color = ErrorText,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = ErrorText, fontSize = 10.5.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled && !inspecting) {
                Text("安装", color = if (enabled) Accent else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

private fun refusalText(result: AgentPluginInspection): String {
    val reason = result.reason?.takeIf { it.isNotBlank() }
    return when (result.problem) {
        "already-installed" -> "这个插件已经安装。"
        "not-found" -> "registry 中没有找到这个包。"
        "not-a-package", "not-a-bundle" -> "这个包不是 dsh 插件。"
        "invalid-spec" -> "插件名称格式不正确。"
        "network" -> "无法连接 registry，请检查网络后重试。"
        else -> reason?.let { "无法安装：$it" } ?: "无法安装这个插件。"
    }
}

/** Registry choices, in order. The empty value leaves dsh on its own plan. */
private val REGISTRY_OPTIONS = listOf(
    Triple("", "默认（跟随 dsh）", "先询问 npm 官方源，取不到时回退 npmmirror"),
    Triple("https://registry.npmjs.org/", "npm 官方源", "registry.npmjs.org"),
    Triple("https://registry.npmmirror.com/", "国内镜像", "registry.npmmirror.com")
)

private fun registryKey(value: String): String = value.trim().trimEnd('/')

/**
 * dsh's plugin manager runs pnpm inside the profile, so the primary registry is the
 * profile's own `.npmrc`. Inspections and installs both start there; bundles that are
 * already installed keep working whichever source is chosen.
 */
@Composable
private fun PluginRegistryCard(
    registry: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onCustom: () -> Unit
) {
    val key = registryKey(registry)
    val custom = key.isNotEmpty() && REGISTRY_OPTIONS.none { registryKey(it.first) == key }

    Text(
        "检查与安装插件时首先询问的 npm 源。已安装的插件不受影响。",
        color = TextMuted,
        fontSize = 10.5.sp,
        lineHeight = 15.sp
    )
    REGISTRY_OPTIONS.forEach { (value, title, detail) ->
        RegistryRow(
            title = title,
            detail = detail,
            selected = registryKey(value) == key,
            enabled = enabled,
            // Picking the source already in use would write the same file again.
            tappable = registryKey(value) != key,
            onClick = { onSelect(value) }
        )
    }
    RegistryRow(
        title = "自定义",
        detail = if (custom) registry else "填写私有或自建 npm 源地址",
        selected = custom,
        enabled = enabled,
        // The custom row opens its editor, so it stays tappable even when selected.
        tappable = true,
        onClick = onCustom
    )
}

@Composable
private fun RegistryRow(
    title: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    tappable: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled && tappable, onClick = onClick)
            .background(if (selected) SurfaceVariant.copy(alpha = 0.7f) else Color.Transparent)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) Accent else Border)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) TextPrimary else TextMuted,
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
private fun RegistryDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val known = REGISTRY_OPTIONS.any { registryKey(it.first) == registryKey(initial) }
    var input by remember { mutableStateOf(if (known) "" else initial) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = { Text("自定义插件源", style = MaterialTheme.typography.titleMedium) },
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
private fun MonoTag(text: String) {
    Text(
        text,
        modifier = Modifier
            .background(SurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = TextMuted,
        fontFamily = FontFamily.Monospace,
        fontSize = 8.5.sp
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
private fun SettingsLikeCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun PluginRowCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        content()
    }
}
