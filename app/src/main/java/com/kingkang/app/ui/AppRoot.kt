package com.kingkang.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingkang.app.MainViewModel
import com.kingkang.app.agent.AgentConnection
import com.kingkang.app.agent.AgentSession
import com.kingkang.app.agent.AgentWorkspace
import com.kingkang.app.ui.components.TerminalMark
import com.kingkang.app.ui.theme.AccentDim
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.Red
import com.kingkang.app.ui.theme.Success
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.SurfaceRaised
import com.kingkang.app.ui.theme.SurfaceVariant
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private enum class AppScreen {
    Home,
    Terminal,
    Harness,
    Plugins,
    Settings
}

@Composable
fun KingKangApp(vm: MainViewModel = viewModel()) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screenName by rememberSaveable { mutableStateOf(AppScreen.Home.name) }
    val screen = AppScreen.valueOf(screenName)
    var showAbout by rememberSaveable { mutableStateOf(false) }
    // The session sheet lives at the window root, not inside the drawer: a sheet composed
    // in the drawer inherits its 300 dp width and stops looking like a system sheet.
    var pendingSession by remember { mutableStateOf<AgentSession?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.uiMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = screen != AppScreen.Home || drawerState.isOpen) {
        if (screen == AppScreen.Home && drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            screenName = AppScreen.Home.name
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            AppScreen.Home -> ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                scrimColor = Color.Black.copy(alpha = 0.32f),
                drawerContent = {
                    KingKangDrawer(
                        vm = vm,
                        onDismiss = { scope.launch { drawerState.close() } },
                        onOpenTerminal = {
                            screenName = AppScreen.Terminal.name
                            scope.launch { drawerState.close() }
                        },
                        onOpenHarness = {
                            screenName = AppScreen.Harness.name
                            scope.launch { drawerState.close() }
                        },
                        onOpenPlugins = {
                            screenName = AppScreen.Plugins.name
                            scope.launch { drawerState.close() }
                        },
                        onOpenSettings = {
                            screenName = AppScreen.Settings.name
                            scope.launch { drawerState.close() }
                        },
                        onSessionLongPress = { session ->
                            pendingSession = session
                            renaming = false
                            renameText = session.displayTitle
                        }
                    )
                }
            ) {
                HomeScreen(
                    vm = vm,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenTerminal = { screenName = AppScreen.Terminal.name },
                    onOpenSettings = { screenName = AppScreen.Settings.name }
                )
            }

            AppScreen.Terminal -> TerminalScreen(
                vm = vm,
                onBack = { screenName = AppScreen.Home.name },
                onOpenSettings = { screenName = AppScreen.Settings.name }
            )

            AppScreen.Harness -> HarnessScreen(
                vm = vm,
                onBack = {
                    screenName = AppScreen.Home.name
                    // Plugins and workspaces may have changed inside dsh-web.
                    vm.refreshPlugins()
                    vm.refreshWorkspaces()
                }
            )

            AppScreen.Plugins -> PluginsScreen(
                vm = vm,
                onBack = { screenName = AppScreen.Home.name },
                onOpenHarness = { screenName = AppScreen.Harness.name }
            )

            AppScreen.Settings -> SettingsScreen(
                vm = vm,
                onBack = { screenName = AppScreen.Home.name },
                onOpenTerminal = { screenName = AppScreen.Terminal.name },
                onOpenHarness = { screenName = AppScreen.Harness.name },
                onAbout = { showAbout = true }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }

    pendingSession?.let { session ->
        SessionActionsSheet(
            session = session,
            renaming = renaming,
            renameText = renameText,
            onRenameTextChange = { renameText = it.take(80) },
            onStartRename = { renaming = true },
            onCancelRename = { renaming = false },
            onSaveRename = {
                val title = renameText.trim()
                if (title.isNotBlank() && title != session.displayTitle) {
                    vm.renameChat(session.id, title)
                }
                pendingSession = null
                renaming = false
            },
            onFork = {
                vm.forkChat(session.id)
                pendingSession = null
            },
            onArchive = {
                vm.archiveChat(session.id)
                pendingSession = null
            },
            onDismiss = {
                pendingSession = null
                renaming = false
            }
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun KingKangDrawer(
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenHarness: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenSettings: () -> Unit,
    onSessionLongPress: (AgentSession) -> Unit
) {
    val agent by vm.agentState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showWorkspaceDialog by rememberSaveable { mutableStateOf(false) }
    var workspacePath by rememberSaveable { mutableStateOf("/root") }
    val collapsedWorkspaces = remember { mutableStateOf(setOf<String>()) }
    // A child owning the transcript is kept in `sessions` so its title and status stay
    // addressable, but it is not a conversation the drawer can switch to: reopening it
    // needs its parent's address. The trail bar is the way back into that chain.
    val trailIds = agent.subagentTrail.mapTo(mutableSetOf()) { it.id }
    val history = agent.sessions.filter {
        it.id !in trailIds &&
            it.id !in agent.archivedSessionIds &&
            (!it.blank || it.id == agent.activeSessionId)
    }
    val pinnedRank = agent.pinnedSessionIds.withIndex().associate { it.value to it.index }
    val ordered = history.sortedWith(
        compareBy<AgentSession> { pinnedRank[it.id] ?: Int.MAX_VALUE }
            .thenByDescending { it.updatedAt }
    )
    val workspaceSessionIds = agent.workspaces.flatMapTo(mutableSetOf()) { it.sessionIds }
    val ungrouped = ordered.filter { it.id !in workspaceSessionIds }
    val workspaceSections = agent.workspaces.map { workspace ->
        workspace to workspace.sessionIds.mapNotNull { id ->
            history.firstOrNull { it.id == id }
        }
    }.filter { (workspace, sessions) -> sessions.isNotEmpty() || workspace.sessionIds.isEmpty() }
    val trimmedQuery = query.trim()
    val matches = if (trimmedQuery.isEmpty()) emptyList() else ordered.filter {
        it.displayTitle.contains(trimmedQuery, ignoreCase = true) ||
            agent.workspaces.firstOrNull { workspace -> it.id in workspace.sessionIds }
                ?.displayTitle?.contains(trimmedQuery, ignoreCase = true) == true
    }
    val harnessAvailable = agent.connection == AgentConnection.Ready
    // Split-screen and freeform windows can be narrower than the standard drawer; leave
    // the transcript a strip of scrim to tap instead of covering the whole window.
    val windowWidth = LocalConfiguration.current.screenWidthDp.dp
    val drawerWidth = minOf(304.dp, windowWidth - 56.dp).coerceAtLeast(248.dp)

    ModalDrawerSheet(
        modifier = Modifier
            .width(drawerWidth)
            .fillMaxHeight(),
        drawerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
        drawerContainerColor = Surface,
        drawerContentColor = TextPrimary,
        drawerTonalElevation = 0.dp
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Text(
                "KINGKANG",
                modifier = Modifier.padding(horizontal = 10.dp),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )
            Spacer(Modifier.height(18.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceRaised)
                    .clickable {
                        vm.newChat()
                        onDismiss()
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(11.dp))
                Text(
                    "新对话",
                    color = TextPrimary,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            DrawerSearchField(
                value = query,
                onValueChange = { query = it.take(40) }
            )
            Spacer(Modifier.height(10.dp))

            val openSession = { session: AgentSession ->
                vm.selectChat(session.id)
                onDismiss()
            }
            val longPressSession = { session: AgentSession -> onSessionLongPress(session) }

            LazyColumn(Modifier.weight(1f)) {
                if (trimmedQuery.isNotEmpty()) {
                    item {
                        DrawerSectionLabel(
                            "搜索「$trimmedQuery」",
                            trailing = if (matches.isEmpty()) null else "${matches.size} 个对话"
                        )
                    }
                    if (matches.isEmpty()) {
                        item {
                            Text(
                                "没有匹配的对话名称",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        items(matches, key = { "match:${it.id}" }) { session ->
                            HistoryRow(
                                session = session,
                                selected = session.id == agent.activeSessionId,
                                onClick = { openSession(session) },
                                onLongClick = { longPressSession(session) }
                            )
                        }
                    }
                } else {
                    // Workspaces are a dsh concept, so the section is always offered once
                    // dsh is reachable: without it a fresh profile has no way to add one.
                    item {
                        DrawerSectionLabel(
                            "工作区",
                            trailing = if (harnessAvailable) "添加" else null,
                            onTrailingClick = if (harnessAvailable) {
                                {
                                    workspacePath = "/root"
                                    showWorkspaceDialog = true
                                }
                            } else {
                                null
                            }
                        )
                    }
                    if (workspaceSections.isEmpty()) {
                        item(key = "workspace:empty") {
                            Text(
                                if (harnessAvailable) {
                                    "还没有工作区。添加 Kali 中的目录后，会话会按工作区分组。"
                                } else {
                                    "等待 Agent 连接后可以添加工作区。"
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                color = TextMuted,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    workspaceSections.forEach { (workspace, workspaceSessions) ->
                            val collapsed = workspace.id in collapsedWorkspaces.value
                            item(key = "workspace:${workspace.id}") {
                                WorkspaceHeader(
                                    workspace = workspace,
                                    sessionCount = workspaceSessions.size,
                                    collapsed = collapsed,
                                    onToggle = {
                                        collapsedWorkspaces.value = if (collapsed) {
                                            collapsedWorkspaces.value - workspace.id
                                        } else {
                                            collapsedWorkspaces.value + workspace.id
                                        }
                                    },
                                    onNewChat = {
                                        vm.newChatInWorkspace(workspace.id)
                                        onDismiss()
                                    }
                                )
                            }
                            if (!collapsed) {
                                items(
                                    workspaceSessions,
                                    key = { "workspace:${workspace.id}:${it.id}" }
                                ) { session ->
                                    HistoryRow(
                                        session = session,
                                        selected = session.id == agent.activeSessionId,
                                        onClick = { openSession(session) },
                                        onLongClick = { longPressSession(session) },
                                        indent = true
                                    )
                                }
                                if (workspaceSessions.isEmpty()) {
                                    item(key = "workspace-empty:${workspace.id}") {
                                        Text(
                                            "还没有对话",
                                            modifier = Modifier.padding(
                                                start = 24.dp,
                                                end = 10.dp,
                                                top = 2.dp,
                                                bottom = 8.dp
                                            ),
                                            color = TextMuted,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }
                        }

                    if (ungrouped.isNotEmpty()) {
                        item {
                            DrawerSectionLabel(
                                if (agent.workspaces.isEmpty()) "最近" else "未分组",
                                trailing = "长按可归档"
                            )
                        }
                        items(ungrouped, key = { "session:${it.id}" }) { session ->
                            HistoryRow(
                                session = session,
                                selected = session.id == agent.activeSessionId,
                                onClick = { openSession(session) },
                                onLongClick = { longPressSession(session) }
                            )
                        }
                    } else if (agent.workspaces.isEmpty() && history.isEmpty()) {
                        item {
                            Text(
                                "还没有对话",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                }
            }

            AgentStatusRow(agent.connection)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Border.copy(alpha = 0.7f))
            Spacer(Modifier.height(8.dp))
            DrawerAction(
                title = "Kali 终端",
                icon = { _ -> TerminalMark(size = 22.dp, filled = false) },
                onClick = onOpenTerminal
            )
            DrawerAction(
                title = "DeepSeek Harness",
                icon = { tint ->
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                },
                enabled = harnessAvailable,
                onClick = onOpenHarness
            )
            DrawerAction(
                title = "插件",
                icon = { tint ->
                    Icon(
                        Icons.Filled.Build,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                },
                enabled = harnessAvailable,
                onClick = onOpenPlugins
            )
            DrawerAction(
                title = "设置",
                icon = { tint ->
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = onOpenSettings
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showWorkspaceDialog) {
        AlertDialog(
            onDismissRequest = { showWorkspaceDialog = false },
            containerColor = Surface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            shape = RoundedCornerShape(20.dp),
            title = { Text("添加工作区", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(
                        "填写 Kali 中的绝对路径。目录不存在时会自动创建，然后注册为工作区。",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    BasicTextField(
                        value = workspacePath,
                        onValueChange = { workspacePath = it.take(200) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontSize = 13.5.sp
                        ),
                        cursorBrush = SolidColor(Accent),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.createWorkspace(workspacePath)
                        showWorkspaceDialog = false
                    },
                    enabled = workspacePath.isNotBlank()
                ) {
                    Text("添加", color = Accent, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWorkspaceDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun DrawerSearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceRaised)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("搜索对话名称", color = TextMuted, fontSize = 12.5.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontSize = 12.5.sp
                ),
                cursorBrush = SolidColor(Accent),
                singleLine = true
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "清除搜索",
                tint = TextMuted,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onValueChange("") }
            )
        }
    }
}

@Composable
private fun DrawerSectionLabel(
    text: String,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                color = if (onTrailingClick != null) TextSecondary else TextMuted,
                fontSize = 10.sp,
                modifier = if (onTrailingClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onTrailingClick)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                } else {
                    Modifier
                }
            )
        }
    }
}

@Composable
private fun WorkspaceHeader(
    workspace: AgentWorkspace,
    sessionCount: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onNewChat: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            // Name and path share one line so the rail stays scannable; the tail ellipsizes
            // instead of wrapping.
            Text(
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(color = TextPrimary, fontWeight = FontWeight.Medium)
                    ) {
                        append(workspace.displayTitle)
                    }
                    append("  ")
                    withStyle(SpanStyle(color = TextMuted)) {
                        append(workspace.path.ifBlank { "$sessionCount 个对话" })
                    }
                },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Filled.Add,
            contentDescription = "在此工作区新建对话",
            tint = TextMuted,
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onNewChat)
                .padding(2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionActionsSheet(
    session: AgentSession,
    renaming: Boolean,
    renameText: String,
    onRenameTextChange: (String) -> Unit,
    onStartRename: () -> Unit,
    onCancelRename: () -> Unit,
    onSaveRename: () -> Unit,
    onFork: () -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                session.displayTitle,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (renaming) {
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = renameText,
                    onValueChange = onRenameTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(Accent),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancelRename) {
                        Text("取消", color = TextSecondary, fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = onSaveRename,
                        enabled = renameText.isNotBlank() &&
                            renameText.trim() != session.displayTitle
                    ) {
                        Text("保存", color = Accent, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "归档后仍保存在 dsh 中，不会删除 Kali 文件。",
                    color = TextMuted,
                    fontSize = 10.5.sp
                )
                Spacer(Modifier.height(8.dp))
                SessionSheetAction("重命名", onClick = onStartRename)
                SessionSheetAction("分叉对话", onClick = onFork)
                SessionSheetAction("归档对话", color = Amber, onClick = onArchive)
            }
        }
    }
}

@Composable
private fun SessionSheetAction(
    label: String,
    color: Color = TextPrimary,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp)
    ) {
        Text(label, color = color, fontSize = 14.sp)
    }
}

@Composable
private fun AgentStatusRow(connection: AgentConnection) {
    // A healthy agent needs no label in the rail; only a problem is worth the pixels.
    if (connection == AgentConnection.Ready) return
    val color = when (connection) {
        AgentConnection.Ready -> Success
        AgentConnection.Starting -> Amber
        AgentConnection.Failed -> Red
        AgentConnection.Stopped -> TextMuted
    }
    val text = when (connection) {
        AgentConnection.Ready -> "Agent 已连接"
        AgentConnection.Starting -> "Agent 正在启动"
        AgentConnection.Failed -> "Agent 未连接"
        AgentConnection.Stopped -> "Agent 已停止"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextMuted, fontSize = 10.5.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    session: AgentSession,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    indent: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentDim else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(
                start = if (indent) 24.dp else 12.dp,
                end = 12.dp,
                top = 9.dp,
                bottom = 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = session.displayTitle,
            modifier = Modifier.weight(1f),
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DrawerAction(
    title: String,
    icon: @Composable (Color) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            // The icon follows the row: a disabled action fades as a whole.
            icon(if (enabled) TextSecondary else TextMuted)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) TextSecondary else TextMuted,
                fontSize = 13.5.sp
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "关于 KingKang",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "0.1.0 · 本地基座",
                    color = TextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 0.4.sp
                )
                Text(
                    "KingKang 把对话界面和 Kali 终端放在同一台 Android 设备上。" +
                        "Agent 通过应用内的 Node 桥接连接 Kali 中的 DeepSeek Harness（dsh），" +
                        "流式文本、工具调用、审批、提问、计划与模型选择都在原生界面中完成。"
                )
                Text(
                    "运行环境仅在本机执行，dsh 只监听 Kali 内的回环地址；终端不会自动连接远程 Kali 或云主机。"
                )
                Text(
                    "Kali Linux 是 Offensive Security 的商标。本应用与 Offensive Security " +
                        "没有隶属或背书关系。内置根文件系统来自 Kali NetHunter 官方 rootless 构建。"
                )
                Text(
                    "仅可将本应用用于你已经获得明确授权的目标与环境。用户对本地及网络中的所有操作负责。"
                )
                Text(
                    "本项目以 Apache License 2.0 开源：可自由使用、修改与再分发，" +
                        "商用与二次开发同样允许，但须保留版权与许可声明。" +
                        "「KingKang」名称与图标不在许可范围内。不得用于未经授权的测试目标。",
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "第三方组件：PRoot (GPLv2)、xterm.js (MIT)、libtalloc (LGPL-3.0)、" +
                        "Android NDK 运行库。相关许可证随应用发行说明提供。",
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = TextSecondary)
            }
        }
    )
}
