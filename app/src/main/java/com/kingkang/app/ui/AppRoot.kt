package com.kingkang.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingkang.app.MainViewModel
import com.kingkang.app.agent.AgentConnection
import com.kingkang.app.agent.AgentSession
import com.kingkang.app.agent.AgentWorkspace
import com.kingkang.app.ui.components.CompactInputPlatformStyle
import com.kingkang.app.ui.components.KkDialog
import com.kingkang.app.ui.components.KkDialogActions
import com.kingkang.app.ui.components.KkDialogTitle
import com.kingkang.app.ui.components.ScrollEdgeFade
import com.kingkang.app.ui.components.TerminalMark
import com.kingkang.app.ui.components.keyboardLift
import com.kingkang.app.ui.theme.AccentDim
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.OnAccent
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
    Files,
    Harness,
    Plugins,
    Settings,
    AgentSettings,
    Providers
}

@Composable
fun KingKangApp(vm: MainViewModel = viewModel()) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screenName by rememberSaveable { mutableStateOf(AppScreen.Home.name) }
    val screen = AppScreen.valueOf(screenName)
    // Sub-screens opened from Settings/Plugins/Terminal return to their origin rather than
    // dumping the user on Home. Not saveable: after process death, Home is a safe root.
    var backStack by remember { mutableStateOf<List<String>>(emptyList()) }

    fun navigate(target: AppScreen) {
        val current = AppScreen.valueOf(screenName)
        if (target == AppScreen.Home) {
            backStack = emptyList()
        } else if (target != current) {
            backStack = backStack + current.name
        }
        screenName = target.name
    }

    fun goBack() {
        val previous = backStack.lastOrNull()
        backStack = if (previous == null) emptyList() else backStack.dropLast(1)
        screenName = previous ?: AppScreen.Home.name
    }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    // First launch only: the setup guide explains the three prerequisites, including the
    // API Key that dsh cannot call a model without. Dismissing it records it as seen.
    var showGuide by rememberSaveable { mutableStateOf(vm.shouldShowOnboarding()) }
    // "credentials" jumps Settings to the API Key card instead of the top of the page.
    var settingsFocus by rememberSaveable { mutableStateOf<String?>(null) }
    // A namespace passed from Settings search lands the native form on that group.
    var agentSettingsGroup by rememberSaveable { mutableStateOf<String?>(null) }
    // The session sheet lives at the window root, not inside the drawer: a sheet composed
    // in the drawer inherits its 300 dp width and stops looking like a system sheet.
    var pendingSession by remember { mutableStateOf<AgentSession?>(null) }
    // Captured with the long-press: reading the whole agent state at the app root would
    // recompose the whole navigation tree on every streamed token.
    var pendingPinned by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    // Workspace actions live at the window root for the same reason: a sheet composed in
    // the drawer would inherit its width and stop reading as a system sheet.
    var pendingWorkspace by remember { mutableStateOf<AgentWorkspace?>(null) }
    var workspaceRenaming by remember { mutableStateOf(false) }
    var workspaceRenameText by remember { mutableStateOf("") }
    var workspaceDeleting by remember { mutableStateOf(false) }
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
            goBack()
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
                            navigate(AppScreen.Terminal)
                            scope.launch { drawerState.close() }
                        },
                        onOpenFiles = {
                            navigate(AppScreen.Files)
                            scope.launch { drawerState.close() }
                        },
                        onOpenHarness = {
                            navigate(AppScreen.Harness)
                            scope.launch { drawerState.close() }
                        },
                        onOpenPlugins = {
                            navigate(AppScreen.Plugins)
                            scope.launch { drawerState.close() }
                        },
                        onOpenSettings = {
                            settingsFocus = null
                            navigate(AppScreen.Settings)
                            scope.launch { drawerState.close() }
                        },
                        onSessionLongPress = { session, pinned ->
                            pendingSession = session
                            pendingPinned = pinned
                            renaming = false
                            renameText = session.displayTitle
                        },
                        onWorkspaceLongPress = { workspace ->
                            pendingWorkspace = workspace
                            workspaceRenaming = false
                            workspaceRenameText = workspace.displayTitle
                            workspaceDeleting = false
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            ) {
                HomeScreen(
                    vm = vm,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenTerminal = { navigate(AppScreen.Terminal) },
                    onOpenSettings = {
                        settingsFocus = null
                        navigate(AppScreen.Settings)
                    },
                    onOpenHarness = { navigate(AppScreen.Harness) },
                    onConfigureKey = {
                        settingsFocus = "credentials"
                        navigate(AppScreen.Settings)
                    }
                )
            }

            AppScreen.Terminal -> TerminalScreen(
                vm = vm,
                onBack = { goBack() },
                onOpenSettings = {
                    settingsFocus = null
                    navigate(AppScreen.Settings)
                }
            )

            AppScreen.Files -> FilesScreen(
                vm = vm,
                onBack = { goBack() },
                onOpenTerminal = { path ->
                    vm.openTerminalAt(path)
                    navigate(AppScreen.Terminal)
                }
            )

            AppScreen.Harness -> HarnessScreen(
                vm = vm,
                onBack = {
                    goBack()
                    // Plugins and workspaces may have changed inside dsh-web.
                    vm.refreshPlugins()
                    vm.refreshWorkspaces()
                    // A theme picked in the Harness is the latest writer; adopt it.
                    vm.syncThemeFromDsh()
                }
            )

            AppScreen.Plugins -> PluginsScreen(
                vm = vm,
                onBack = { goBack() },
                onOpenHarness = { navigate(AppScreen.Harness) }
            )

            AppScreen.Settings -> SettingsScreen(
                vm = vm,
                focusSection = settingsFocus,
                onBack = {
                    settingsFocus = null
                    goBack()
                },
                onOpenTerminal = { navigate(AppScreen.Terminal) },
                onOpenHarness = { navigate(AppScreen.Harness) },
                onOpenProviders = { navigate(AppScreen.Providers) },
                onOpenPlugins = { navigate(AppScreen.Plugins) },
                onOpenAgentSettings = { group ->
                    agentSettingsGroup = group
                    navigate(AppScreen.AgentSettings)
                },
                onAbout = { showAbout = true }
            )

            AppScreen.AgentSettings -> AgentSettingsScreen(
                vm = vm,
                initialGroup = agentSettingsGroup,
                onBack = { goBack() },
                onOpenHarness = { navigate(AppScreen.Harness) }
            )

            AppScreen.Providers -> ProviderSettingsScreen(
                vm = vm,
                onBack = { goBack() },
                onOpenHarness = { navigate(AppScreen.Harness) }
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
            onExport = {
                vm.exportSession(session.id)
                pendingSession = null
            },
            onArchive = {
                vm.archiveChat(session.id)
                pendingSession = null
            },
            pinned = pendingPinned,
            onTogglePin = { pin ->
                vm.setChatPinned(session.id, pin)
                pendingSession = null
            },
            onDismiss = {
                pendingSession = null
                renaming = false
            }
        )
    }

    pendingWorkspace?.let { workspace ->
        // Read once per open: moving a workspace closes the sheet, so a stale list cannot
        // produce a second move against the old order.
        val all = vm.agentState.value.workspaces
        val index = all.indexOfFirst { it.id == workspace.id }
        WorkspaceActionsSheet(
            workspace = workspace,
            sessionCount = workspace.sessionIds.size,
            canMoveUp = index > 0,
            canMoveDown = index >= 0 && index < all.lastIndex,
            renaming = workspaceRenaming,
            renameText = workspaceRenameText,
            deleting = workspaceDeleting,
            onRenameTextChange = { workspaceRenameText = it.take(80) },
            onStartRename = { workspaceRenaming = true },
            onCancelRename = { workspaceRenaming = false },
            onSaveRename = {
                val title = workspaceRenameText.trim()
                if (title.isNotBlank() && title != workspace.displayTitle) {
                    vm.renameWorkspace(workspace.id, title)
                }
                pendingWorkspace = null
                workspaceRenaming = false
            },
            onMoveUp = {
                vm.moveWorkspace(workspace.id, all.getOrNull(index - 1)?.id)
                pendingWorkspace = null
            },
            onMoveDown = {
                vm.moveWorkspace(workspace.id, all.getOrNull(index + 2)?.id)
                pendingWorkspace = null
            },
            onDelete = {
                if (!workspaceDeleting) {
                    workspaceDeleting = true
                } else {
                    vm.deleteWorkspace(workspace.id)
                    pendingWorkspace = null
                    workspaceDeleting = false
                }
            },
            onDismiss = {
                pendingWorkspace = null
                workspaceRenaming = false
                workspaceDeleting = false
            }
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    if (showGuide) {
        OnboardingDialog(
            onOpenSettings = {
                showGuide = false
                vm.completeOnboarding()
                settingsFocus = null
                navigate(AppScreen.Settings)
            },
            onDismiss = {
                showGuide = false
                vm.completeOnboarding()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun KingKangDrawer(
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenHarness: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenSettings: () -> Unit,
    onSessionLongPress: (AgentSession, Boolean) -> Unit,
    onWorkspaceLongPress: (AgentWorkspace) -> Unit
) {
    val agent by vm.agentState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showWorkspaceDialog by rememberSaveable { mutableStateOf(false) }
    var showDirectoryPicker by rememberSaveable { mutableStateOf(false) }
    var workspacePath by rememberSaveable { mutableStateOf("/root") }
    // Folded headers survive both drawer closes and process restarts.
    val collapsedWorkspaces = remember { mutableStateOf(vm.collapsedWorkspaceIds()) }
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
    // Archived chats keep their rows in the catalog so this section can name them and
    // offer a way back; they never enter `history` or the fallback conversation.
    val archived = agent.sessions
        .filter { it.id in agent.archivedSessionIds && it.id !in trailIds }
        .sortedByDescending { it.updatedAt }
    val ordered = history.sortedWith(
        compareBy<AgentSession> { pinnedRank[it.id] ?: Int.MAX_VALUE }
            .thenByDescending { it.updatedAt }
    )
    // Pinned chats get their own section at the top of the drawer. Sorting them first
    // inside a workspace did not move them anywhere visible, so pinning a chat that sat
    // under a workspace looked like it had done nothing.
    val pinned = ordered.filter { it.id in pinnedRank }
    val unpinned = ordered.filter { it.id !in pinnedRank }
    val workspaceSessionIds = agent.workspaces.flatMapTo(mutableSetOf()) { it.sessionIds }
    val ungrouped = unpinned.filter { it.id !in workspaceSessionIds }
    val workspaceSections = agent.workspaces.map { workspace ->
        workspace to workspace.sessionIds.mapNotNull { id ->
            unpinned.firstOrNull { it.id == id }
        }
    }.filter { (workspace, sessions) ->
        // A workspace whose chats are all pinned keeps its header so "新对话" stays
        // reachable; one whose chats are all archived still disappears. A blank chat is
        // not in `history` and a fresh chat may not be in the catalog yet, so only treat
        // the workspace as gone when every one of its chats is known to be archived —
        // otherwise adding a workspace and opening an empty chat could hide it again.
        sessions.isNotEmpty() || workspace.sessionIds.isEmpty() ||
            workspace.sessionIds.any { id -> id !in agent.archivedSessionIds }
    }
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
            val longPressSession = { session: AgentSession ->
                onSessionLongPress(session, session.id in agent.pinnedSessionIds)
            }

            Box(Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 22.dp)
                ) {
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
                                onLongClick = { longPressSession(session) },
                                pinned = session.id in agent.pinnedSessionIds
                            )
                        }
                    }
                } else {
                    if (pinned.isNotEmpty()) {
                        item(key = "pinned:label") {
                            DrawerSectionLabel("置顶")
                        }
                        items(pinned, key = { "pinned:${it.id}" }) { session ->
                            HistoryRow(
                                session = session,
                                selected = session.id == agent.activeSessionId,
                                onClick = { openSession(session) },
                                onLongClick = { longPressSession(session) },
                                pinned = true
                            )
                        }
                    }
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
                                    running = workspace.sessionIds.any { id ->
                                        agent.sessions.firstOrNull { it.id == id }?.running == true
                                    },
                                    collapsed = collapsed,
                                    onToggle = {
                                        val next = if (collapsed) {
                                            collapsedWorkspaces.value - workspace.id
                                        } else {
                                            collapsedWorkspaces.value + workspace.id
                                        }
                                        collapsedWorkspaces.value = next
                                        vm.setWorkspaceCollapsed(workspace.id, !collapsed)
                                    },
                                    onNewChat = {
                                        vm.newChatInWorkspace(workspace.id)
                                        onDismiss()
                                    },
                                    onLongPress = { onWorkspaceLongPress(workspace) }
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
                                        indent = true,
                                        pinned = session.id in agent.pinnedSessionIds
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
                                onLongClick = { longPressSession(session) },
                                pinned = session.id in agent.pinnedSessionIds
                            )
                        }
                    } else if (agent.workspaces.isEmpty() && history.isEmpty() && archived.isEmpty()) {
                        item {
                            Text(
                                "还没有对话",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (archived.isNotEmpty()) {
                        item(key = "archived:label") {
                            DrawerSectionLabel(
                                "已归档",
                                trailing = "${archived.size} 个 · 点按恢复"
                            )
                        }
                        items(archived, key = { "archived:${it.id}" }) { session ->
                            ArchivedRow(
                                session = session,
                                onClick = { vm.unarchiveChat(session.id) }
                            )
                        }
                    }

                }
                }
                // The rail fades into the fixed section below instead of ending on a hard
                // edge, so the list reads as continuing under it.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(26.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Surface)))
                )
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
                title = "工作区文件",
                icon = { tint ->
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(19.dp)
                    )
                },
                enabled = harnessAvailable,
                onClick = onOpenFiles
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
        KkDialog(onDismiss = { showWorkspaceDialog = false }) {
            KkDialogTitle(
                title = "添加工作区",
                subtitle = "目录不存在时会自动创建",
                onDismiss = { showWorkspaceDialog = false }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "填写 Kali 中的绝对路径，或从下方浏览目录。",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = workspacePath,
                onValueChange = { workspacePath = it.take(200) },
                modifier = Modifier
                    .fillMaxWidth()
                    .keyboardLift()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    platformStyle = CompactInputPlatformStyle,
                    lineHeight = 13.5.sp,
                ),
                cursorBrush = SolidColor(Accent),
                singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "浏览 Kali 目录",
                    color = Accent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .clickable {
                            vm.browseDirectory(workspacePath.trim().ifBlank { "/root" })
                            showDirectoryPicker = true
                        }
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                )
                Spacer(Modifier.weight(1f))
            }
            KkDialogActions {
                TextButton(onClick = { showWorkspaceDialog = false }) {
                    Text("取消", color = TextMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = {
                        vm.createWorkspace(workspacePath)
                        showWorkspaceDialog = false
                    },
                    enabled = workspacePath.isNotBlank(),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent,
                        disabledContainerColor = SurfaceVariant,
                        disabledContentColor = TextMuted
                    )
                ) {
                    Text("添加", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showDirectoryPicker) {
        DirectoryPickerDialog(
            listing = agent.directory,
            onBrowse = vm::browseDirectory,
            onPick = { path ->
                workspacePath = path
                vm.rememberDirectory(path)
                showDirectoryPicker = false
                vm.closeDirectoryBrowser()
            },
            onCreate = vm::createDirectory,
            onDismiss = {
                showDirectoryPicker = false
                vm.closeDirectoryBrowser()
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
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    "搜索对话名称",
                    color = TextMuted,
                    fontSize = 12.5.sp,
                    lineHeight = 12.5.sp,
                    style = TextStyle(platformStyle = CompactInputPlatformStyle),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.CenterVertically),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    platformStyle = CompactInputPlatformStyle,
                    lineHeight = 12.5.sp,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceHeader(
    workspace: AgentWorkspace,
    sessionCount: Int,
    running: Boolean,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onNewChat: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
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
                        val meta = buildList {
                            add(workspace.path.ifBlank { "未命名目录" })
                            if (sessionCount > 0) add("$sessionCount 个对话")
                        }.joinToString(" · ")
                        append(meta)
                    }
                },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (running) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Accent)
            )
            Spacer(Modifier.width(6.dp))
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
private fun WorkspaceActionsSheet(
    workspace: AgentWorkspace,
    sessionCount: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    renaming: Boolean,
    renameText: String,
    deleting: Boolean,
    onRenameTextChange: (String) -> Unit,
    onStartRename: () -> Unit,
    onCancelRename: () -> Unit,
    onSaveRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
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
                workspace.displayTitle,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${workspace.path} · $sessionCount 个对话",
                color = TextMuted,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (renaming) {
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = renameText,
                    onValueChange = onRenameTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .keyboardLift()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        platformStyle = CompactInputPlatformStyle,
                        lineHeight = 14.sp,
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
                        enabled = renameText.isNotBlank() && renameText.trim() != workspace.displayTitle
                    ) {
                        Text("保存", color = Accent, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (deleting) {
                        "再次点按「确认移除」：只取消分组，Kali 目录和其中的对话都会保留。"
                    } else {
                        "移除工作区只取消分组，Kali 目录和其中的对话都会保留。"
                    },
                    color = if (deleting) Red else TextMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                SessionSheetAction("重命名", onClick = onStartRename)
                SessionSheetAction(
                    "上移",
                    color = if (canMoveUp) TextPrimary else TextMuted,
                    onClick = { if (canMoveUp) onMoveUp() }
                )
                SessionSheetAction(
                    "下移",
                    color = if (canMoveDown) TextPrimary else TextMuted,
                    onClick = { if (canMoveDown) onMoveDown() }
                )
                SessionSheetAction(
                    if (deleting) "确认移除" else "移除工作区",
                    color = Red,
                    onClick = onDelete
                )
            }
        }
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
    onExport: () -> Unit,
    onArchive: () -> Unit,
    pinned: Boolean,
    onTogglePin: (Boolean) -> Unit,
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
                        .keyboardLift()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        platformStyle = CompactInputPlatformStyle,
                        lineHeight = 14.sp,
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
                    "归档后仍保存在 dsh 中，不会删除 Kali 文件；可在抽屉的「已归档」中恢复。",
                    color = TextMuted,
                    fontSize = 10.5.sp
                )
                Spacer(Modifier.height(8.dp))
                SessionSheetAction("重命名", onClick = onStartRename)
                SessionSheetAction("分叉对话", onClick = onFork)
                SessionSheetAction("导出会话", onClick = onExport)
                SessionSheetAction(
                    if (pinned) "取消置顶" else "置顶对话",
                    onClick = { onTogglePin(!pinned) }
                )
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
    indent: Boolean = false,
    pinned: Boolean = false
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
        if (pinned) {
            Spacer(Modifier.width(8.dp))
            Text("置顶", color = TextMuted, fontSize = 9.5.sp)
        }
    }
}

@Composable
private fun ArchivedRow(
    session: AgentSession,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = session.displayTitle,
            modifier = Modifier.weight(1f),
            color = TextMuted,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "恢复",
            color = TextSecondary,
            fontSize = 11.sp
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

/**
 * First-run guide. It states the three setup steps in the order the app actually needs
 * them, and names the API Key step explicitly: without it dsh connects but cannot answer.
 */
@Composable
private fun OnboardingDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val scroll = rememberScrollState()
    KkDialog(
        onDismiss = onDismiss,
        modifier = Modifier.widthIn(max = 380.dp),
        padding = PaddingValues(22.dp)
    ) {
        ScrollEdgeFade(
            canScrollForward = scroll.canScrollForward,
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TerminalMark(size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "KINGKANG",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                    Text(
                        "本地安全测试工作台",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "开始之前",
                color = TextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "对话由安装在 Kali 里的 DeepSeek Harness 提供。按顺序完成三步，就能开始。",
                color = TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(16.dp))
            OnboardingStep(
                index = 1,
                title = "安装 Kali 环境",
                detail = "设置 → Kali 环境，下载 NetHunter 根文件系统。"
            )
            OnboardingStep(
                index = 2,
                title = "安装 Agent 核心",
                detail = "设置 → Agent 核心，安装 DeepSeek Harness（dsh）。"
            )
            OnboardingStep(
                index = 3,
                title = "配置 DeepSeek API Key",
                detail = "设置 → 模型与供应商（或 DeepSeek 官方凭据），填写 API Key。"
            )

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    "没有 API Key，Agent 能连上但无法调用模型，消息会一直失败。" +
                        "密钥只写入 Kali 内的 dsh，不会回读。",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "常用入口：左上角菜单管理终端、文件、插件和设置；标题栏的扳手图标是工作台，" +
                    "列表图标用于在长对话里按轮次跳转。",
                color = TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent
                    )
                ) {
                    Text("打开设置", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("稍后", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
        }
    }
}

@Composable
private fun OnboardingStep(index: Int, title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(21.dp)
                .clip(RoundedCornerShape(50))
                .background(SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$index",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(1.dp))
            Text(
                detail,
                color = TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "关于 KingKang",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        },
        text = {
            ScrollEdgeFade(
                canScrollForward = scroll.canScrollForward,
            ) {
            Column(
                Modifier
                    .verticalScroll(scroll)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "${com.kingkang.app.BuildConfig.VERSION_NAME} · 本地基座",
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = TextSecondary)
            }
        }
    )
}
