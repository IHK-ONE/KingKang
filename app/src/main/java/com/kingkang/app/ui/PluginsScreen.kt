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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingkang.app.MainViewModel
import com.kingkang.app.PluginInstallNote
import com.kingkang.app.agent.AgentConnection
import com.kingkang.app.agent.AgentPlugin
import com.kingkang.app.agent.AgentPluginDetail
import com.kingkang.app.agent.AgentPluginInspection
import com.kingkang.app.agent.AgentPluginOffer
import com.kingkang.app.agent.AgentPluginMarketResult
import com.kingkang.app.agent.AgentPluginVersions
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.components.CompactInputPlatformStyle
import com.kingkang.app.ui.components.ScrollEdgeFade
import com.kingkang.app.ui.components.keyboardLift
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
    val pageScroll = rememberScrollState()

    var spec by remember { mutableStateOf("") }
    var detailPlugin by remember { mutableStateOf<AgentPlugin?>(null) }
    var confirmUninstall by remember { mutableStateOf<AgentPlugin?>(null) }
    var searchText by remember { mutableStateOf("") }
    var inspecting by remember { mutableStateOf(false) }
    var inspected by remember { mutableStateOf<AgentPluginInspection?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }
    // Dismissing the dialog or editing the spec must invalidate an inspection that is
    // still in flight; otherwise its result starts a real pnpm install after 取消.
    var inspectSeq by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(agent.connection) {
        if (ready) vm.refreshPlugins()
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

        ScrollEdgeFade(
            canScrollForward = pageScroll.canScrollForward,
            // Shrink the viewport while the keyboard is open so a focused field can be
            // scrolled above it instead of staying hidden behind the keys.
            modifier = Modifier.weight(1f).imePadding()
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(pageScroll)
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
                        if (agent.connection == AgentConnection.Starting) {
                            "Agent 正在重启，连接后自动读取插件。"
                        } else {
                            "Agent 未连接，连接后读取插件。"
                        },
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
                            dshVersion = agent.dshVersion,
                            updating = incompatibleUpdateVersion(plugin, agent.dshVersion)
                                ?.let { installing == "${plugin.name}@$it" } == true,
                            changing = changing == plugin.name,
                            removing = agent.pluginRemoving == plugin.name,
                            enabled = ready && changing == null && installing == null &&
                                agent.pluginRemoving == null,
                            onToggle = { vm.setPluginEnabled(plugin.name, it) },
                            onUpdate = { version -> vm.updatePlugin(plugin.name, version) },
                            onManage = { detailPlugin = plugin }
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
            SectionLabel("插件市场")
            Spacer(Modifier.height(8.dp))
            SettingsLikeCard {
                Text(
                    "从当前 NPM 源搜索 dsh 插件。第三方插件会以 dsh 的权限运行，" +
                        "安装前请确认来源。",
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceVariant)
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    ) {
                        BasicTextField(
                            value = searchText,
                            onValueChange = { searchText = it.take(120) },
                            modifier = Modifier.fillMaxWidth().keyboardLift(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                platformStyle = com.kingkang.app.ui.components.CompactInputPlatformStyle,
                                lineHeight = 11.5.sp,
                            ),
                            cursorBrush = SolidColor(Accent),
                            singleLine = true,
                            enabled = ready && !agent.pluginSearch.loading,
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchText.isEmpty()) {
                                        Text(
                                            "搜索插件，例如 web-search",
                                            color = TextMuted,
                                            fontSize = 11.5.sp,
                                            lineHeight = 11.5.sp,
                                            style = TextStyle(
                                                platformStyle = CompactInputPlatformStyle
                                            )
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { vm.searchPlugins(searchText.trim()) },
                        enabled = ready && !agent.pluginSearch.loading &&
                            agent.pluginRemoving == null,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Border),
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        Text("搜索", fontSize = 11.sp)
                    }
                }

                val market = agent.pluginSearch
                if (market.loading) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = TextMuted,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在搜索插件源…", color = TextMuted, fontSize = 10.5.sp)
                    }
                }
                market.error?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Text(message, color = ErrorText, fontSize = 10.5.sp, lineHeight = 15.sp)
                }
                if (market.searched && !market.loading && market.error == null) {
                    Spacer(Modifier.height(12.dp))
                    if (market.results.isEmpty()) {
                        Text(
                            "没有找到相关插件。可以换一个关键词，或直接输入包名安装。",
                            color = TextMuted,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                    } else {
                        Text(
                            "共 ${market.total} 个结果",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        market.results.forEachIndexed { index, result ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = Border.copy(alpha = 0.7f)
                                )
                            }
                            val alreadyInstalled = agent.plugins.any { it.name == result.name }
                            MarketResultRow(
                                result = result,
                                alreadyInstalled = alreadyInstalled,
                                installing = installing == marketInstallSpec(result),
                                enabled = ready && installing == null &&
                                    agent.pluginRemoving == null && !alreadyInstalled,
                                onInstall = { vm.installPlugin(marketInstallSpec(result)) }
                            )
                        }
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
                Spacer(Modifier.height(10.dp))
                // Same shape as the market search above: one field, one button. The spec
                // is checked with dsh before pnpm sees it, so a typo fails here, not
                // halfway through an install.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceVariant)
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    ) {
                        BasicTextField(
                            value = spec,
                            onValueChange = { value ->
                                inspectSeq++
                                spec = value.take(160)
                                inspecting = false
                                inspected = null
                                installError = null
                            },
                            modifier = Modifier.fillMaxWidth().keyboardLift(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                platformStyle = CompactInputPlatformStyle,
                                lineHeight = 11.5.sp,
                            ),
                            cursorBrush = SolidColor(Accent),
                            singleLine = true,
                            enabled = ready && installing == null && !inspecting,
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (spec.isEmpty()) {
                                        Text(
                                            "npm 包名，例如 dsh-plugin-xxx",
                                            color = TextMuted,
                                            fontSize = 11.5.sp,
                                            lineHeight = 11.5.sp,
                                            style = TextStyle(
                                                platformStyle = CompactInputPlatformStyle
                                            )
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val value = spec.trim()
                            if (value.isBlank() || inspecting) return@OutlinedButton
                            inspectSeq++
                            val seq = inspectSeq
                            inspecting = true
                            inspected = null
                            installError = null
                            scope.launch {
                                try {
                                    val result = vm.inspectPlugin(value)
                                    if (seq != inspectSeq) return@launch
                                    inspected = result
                                    if (result.accepted) {
                                        vm.installPlugin(value)
                                        spec = ""
                                        inspected = null
                                    }
                                } catch (t: Throwable) {
                                    if (seq == inspectSeq) {
                                        installError = t.message ?: "检查插件失败"
                                    }
                                } finally {
                                    if (seq == inspectSeq) inspecting = false
                                }
                            }
                        },
                        enabled = ready && spec.isNotBlank() && installing == null && !inspecting,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Border),
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        if (inspecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = TextMuted,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Text("安装", fontSize = 11.sp)
                        }
                    }
                }
                inspected?.takeIf { !it.accepted }?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    Text(refusalText(result), color = ErrorText, fontSize = 10.5.sp, lineHeight = 15.sp)
                }
                installError?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = ErrorText, fontSize = 10.5.sp, lineHeight = 15.sp)
                }
                Spacer(Modifier.height(2.dp))
                TextButton(
                    onClick = onOpenHarness,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Text("在 Harness 中管理", color = TextMuted, fontSize = 10.5.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "NPM 源在「设置 → Agent 核心」中配置，默认使用阿里源。",
                modifier = Modifier.padding(horizontal = 4.dp),
                color = TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )

            Spacer(Modifier.height(28.dp))
        }
        }
    }

    detailPlugin?.let { plugin ->
        LaunchedEffect(plugin.name) {
            vm.loadPluginVersions(plugin.name)
            vm.loadPluginDetail(plugin.name)
        }
        PluginDetailDialog(
            plugin = plugin,
            versions = agent.pluginVersions[plugin.name],
            detail = agent.pluginDetails[plugin.name],
            removing = agent.pluginRemoving == plugin.name,
            onUpdate = { version -> vm.updatePlugin(plugin.name, version) },
            onDismiss = { detailPlugin = null },
            onOpenHarness = {
                detailPlugin = null
                onOpenHarness()
            },
            onUninstall = {
                detailPlugin = null
                confirmUninstall = plugin
            }
        )
    }

    confirmUninstall?.let { plugin ->
        AlertDialog(
            onDismissRequest = { confirmUninstall = null },
            containerColor = Surface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            shape = RoundedCornerShape(20.dp),
            title = { Text("卸载 ${plugin.displayTitle}？", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "插件会从 dsh 的 profile 中移除，它的设置和相关数据可能一并删除。" +
                        "其他插件不受影响；如果 Agent 空闲会立即重启，否则在下次重启后生效。",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.uninstallPlugin(plugin.name)
                    confirmUninstall = null
                }) {
                    Text("卸载", color = ErrorText)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = null }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

}

@Composable
private fun PluginDetailDialog(
    plugin: AgentPlugin,
    versions: AgentPluginVersions?,
    detail: AgentPluginDetail?,
    removing: Boolean,
    onUpdate: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenHarness: () -> Unit,
    onUninstall: () -> Unit
) {
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plugin.displayTitle,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleMedium
                )
                plugin.version?.let { version ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        version,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
                if (plugin.official) {
                    Spacer(Modifier.width(6.dp))
                    MonoTag("官方")
                }
            }
        },
        text = {
            ScrollEdgeFade(
                canScrollForward = scroll.canScrollForward,
            ) {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(scroll)
            ) {
                Text(
                    plugin.name,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp
                )
                if (plugin.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        plugin.description,
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
                // Official plugins are not third-party npm packages, so dsh cannot list
                // their versions. Hiding the failed lookup is friendlier than reporting
                // that English protocol error for a plugin dsh ships itself.
                versions?.takeIf { !(plugin.official && it.problem != null) }?.let { list ->
                    Spacer(Modifier.height(12.dp))
                    Text("可用版本", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    when {
                        list.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                Modifier.size(12.dp),
                                color = TextMuted,
                                strokeWidth = 1.5.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("正在读取版本…", color = TextMuted, fontSize = 10.5.sp)
                        }
                        list.problem != null -> Text(
                            "读取版本失败：${list.reason ?: list.problem}",
                            color = Amber,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                        list.versions.isEmpty() -> Text(
                            "没有可用的版本信息。",
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                        else -> list.versions.take(12).forEach { version ->
                            val current = version.version == (list.installed ?: plugin.version)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !current) { onUpdate(version.version) }
                                    .padding(horizontal = 2.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    version.version,
                                    modifier = Modifier.weight(1f),
                                    color = if (current) TextMuted else TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                                if (version.deprecated != null) {
                                    Text("已弃用", color = Amber, fontSize = 9.sp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                if (version.prerelease) {
                                    Text("预览", color = TextMuted, fontSize = 9.sp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    if (current) "当前" else "更新",
                                    color = if (current) TextMuted else Accent,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }
                }
                detail?.let { readme ->
                    Spacer(Modifier.height(12.dp))
                    Text("说明", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    when {
                        readme.loading -> Text("正在读取说明…", color = TextMuted, fontSize = 10.5.sp)
                        readme.markdown.isNotBlank() -> {
                            Text(
                                plainReadme(readme.markdown).take(6000),
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp
                            )
                            if (readme.markdown.length > 6000) {
                                Text("（已截断）", color = TextMuted, fontSize = 9.5.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        "插件自己的设置页需要在 Harness 中打开。",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "路径：Harness → 插件 → ${plugin.displayTitle}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
                if (plugin.removable) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "卸载会移除插件及其配置，来源不可信的插件建议先停用。",
                        color = Amber,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenHarness) {
                Text("打开 Harness", color = Accent)
            }
        },
        dismissButton = {
            Row {
                if (plugin.removable) {
                    if (removing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 14.dp)
                                .size(14.dp),
                            color = TextMuted,
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = onUninstall) {
                            Text("卸载", color = ErrorText)
                        }
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = TextSecondary)
                }
            }
        }
    )
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

/**
 * dsh refuses a profile bundle built for another dsh release and disables it at startup,
 * but the profile keeps listing it as installed and switched on. Every official plugin
 * shares dsh's release version, so a mismatch is that incompatibility; the fix is
 * updating the package to the running dsh version.
 */
private fun incompatibleUpdateVersion(plugin: AgentPlugin, dshVersion: String?): String? {
    val installed = plugin.version ?: return null
    val running = dshVersion?.takeIf { it.isNotBlank() } ?: return null
    if (!plugin.official || !plugin.name.startsWith("@deepseek-ai/")) return null
    if (installed == running) return null
    return running
}

@Composable
private fun InstalledPluginRow(
    plugin: AgentPlugin,
    dshVersion: String?,
    updating: Boolean,
    changing: Boolean,
    removing: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onUpdate: (String) -> Unit,
    onManage: () -> Unit
) {
    val updateVersion = incompatibleUpdateVersion(plugin, dshVersion)
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
            if (updateVersion != null && dshVersion != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "与 dsh $dshVersion 版本不匹配，已被 dsh 停用。",
                        modifier = Modifier.weight(1f, fill = false),
                        color = ErrorText,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    if (updating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            color = TextMuted,
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Text(
                            "更新",
                            color = if (enabled) Accent else TextMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(enabled = enabled) {
                                onUpdate(updateVersion)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "详情与设置",
                color = Accent,
                fontSize = 10.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onManage)
                    .padding(vertical = 3.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        if (changing || removing) {
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

private fun marketInstallSpec(result: AgentPluginMarketResult): String =
    result.version?.takeIf { it.isNotBlank() }
        ?.let { "${result.name}@$it" }
        ?: result.name

@Composable
private fun MarketResultRow(
    result: AgentPluginMarketResult,
    alreadyInstalled: Boolean,
    installing: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    result.name,
                    modifier = Modifier.weight(1f, fill = false),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                result.version?.let { version ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        version,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
                if (result.official) {
                    Spacer(Modifier.width(6.dp))
                    MonoTag("官方")
                }
            }
            if (result.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    result.description,
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val meta = listOfNotNull(
                result.publisher?.takeIf { it.isNotBlank() }?.let { "发布者 $it" },
                result.date?.takeIf { it.isNotBlank() }?.take(10)
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(meta, color = TextMuted, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        when {
            alreadyInstalled -> Text(
                "已安装",
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            installing -> Box(
                Modifier.size(width = 52.dp, height = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    color = TextMuted,
                    strokeWidth = 2.dp
                )
            }
            else -> OutlinedButton(
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

/**
 * READMEs are written for GitHub, not for a text view: drop images and markdown link
 * wrappers so the说明 reads as sentences instead of `![智能体搜索历史会话](preview.svg)`.
 */
private fun plainReadme(markdown: String): String = markdown
    .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "")
    .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)")) { match -> match.groupValues[1] }
    .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

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
