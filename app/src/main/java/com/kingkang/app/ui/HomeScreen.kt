package com.kingkang.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kingkang.app.EnvironmentUi
import com.kingkang.app.MainViewModel
import com.kingkang.app.agent.AgentAnswer
import com.kingkang.app.agent.AgentApproval
import com.kingkang.app.agent.AgentCommand
import com.kingkang.app.agent.AgentConnection
import com.kingkang.app.agent.AgentDraftFile
import com.kingkang.app.agent.AgentDraftImage
import com.kingkang.app.agent.AgentEffort
import com.kingkang.app.agent.AgentFile
import com.kingkang.app.agent.AgentImage
import com.kingkang.app.agent.AgentItem
import com.kingkang.app.agent.AgentModel
import com.kingkang.app.agent.AgentModels
import com.kingkang.app.agent.AgentPermissionOption
import com.kingkang.app.agent.AgentQuestion
import com.kingkang.app.agent.AgentQuestionItem
import com.kingkang.app.agent.AgentQueueItem
import com.kingkang.app.agent.AgentSkill
import com.kingkang.app.agent.AgentState
import com.kingkang.app.agent.AgentSubagent
import com.kingkang.app.agent.AgentTodo
import com.kingkang.app.agent.AssistantItem
import com.kingkang.app.agent.AttachmentStaging
import com.kingkang.app.agent.NoticeItem
import com.kingkang.app.agent.ToolItem
import com.kingkang.app.agent.UserItem
import com.kingkang.app.ui.components.TerminalMark
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.AccentDim
import com.kingkang.app.ui.theme.AccentSoft
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.theme.Background
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.OnAccent
import com.kingkang.app.ui.theme.Red
import com.kingkang.app.ui.theme.Success
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.SurfaceRaised
import com.kingkang.app.ui.theme.SurfaceVariant
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenDrawer: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by vm.agentState.collectAsState()
    val environment by vm.environment.collectAsState()
    val rootMode by vm.rootMode.collectAsState()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    // dsh-web registers conversation views as tabs above the transcript and lets the
    // reader swipe between them.
    val tabs = listOf(HomeTab.Chat, HomeTab.Trajectory)
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    var modelPicker by rememberSaveable { mutableStateOf<ModelPane?>(null) }
    var showPermission by rememberSaveable { mutableStateOf(false) }
    var showSubagents by rememberSaveable { mutableStateOf(false) }
    var confirmPermission by rememberSaveable { mutableStateOf<String?>(null) }

    // System Back inside a child walks the trail one level at a time, exactly like the
    // trail bar; without this, Back at any depth would leave the whole app.
    BackHandler(enabled = state.subagentView != null) { vm.closeSubagent() }

    val lastLength = when (val last = state.items.lastOrNull()) {
        is AssistantItem -> last.text.length + last.reasoning.length
        is ToolItem -> last.resultText.length
        else -> 0
    }
    // Following the newest message is a property of the reader, not of the list contents:
    // switching sessions or sending a prompt always jumps down, while a streaming answer is
    // followed only until the reader scrolls up to reread earlier history.
    var following by remember { mutableStateOf(true) }
    var jumpRequested by remember { mutableStateOf(true) }

    LaunchedEffect(state.activeSessionId) {
        following = true
        jumpRequested = true
    }
    LaunchedEffect(listState) {
        // `isScrollInProgress` covers both finger drags and our own `scrollToItem` calls. A
        // programmatic jump finishes at the bottom, so it re-enables following by itself.
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                following = false
            } else {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                following = info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
            }
        }
    }
    LaunchedEffect(state.activeSessionId, state.items.size, state.running, lastLength) {
        if (state.items.isEmpty()) return@LaunchedEffect
        val sentByUser = state.items.lastOrNull() is UserItem
        if (!jumpRequested && !sentByUser && !following) return@LaunchedEffect
        jumpRequested = false
        // The list can carry a leading "older messages" row and a trailing typing indicator,
        // so the last item is not always the last row.
        var target = state.items.lastIndex + if (state.hasMoreHistory) 1 else 0
        if (state.running && state.items.none { it is AssistantItem && it.streaming }) target += 1
        runCatching { listState.scrollToItem(target.coerceAtLeast(0)) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        HomeTopBar(
            title = state.activeSession?.displayTitle ?: "KingKang",
            connection = state.connection,
            onOpenDrawer = {
                focusManager.clearFocus()
                keyboard?.hide()
                onOpenDrawer()
            },
            onNewChat = vm::newChat
        )

        if (state.connection == AgentConnection.Failed) {
            AgentSetupState(
                state = state,
                onRetry = vm::restartAgent,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
        } else if (state.connection == AgentConnection.Starting ||
            state.connection == AgentConnection.Stopped
        ) {
            AgentLoadingState(
                state = state,
                modifier = Modifier.weight(1f)
            )
        } else {
            val childView = state.subagentView
            if (childView != null) {
                SubagentTrailBar(
                    view = childView,
                    depth = state.subagentDepth,
                    running = state.running,
                    onBack = vm::closeSubagent,
                    onRefresh = vm::refreshSubagents
                )
            }
            // At every depth this bar describes the children of the conversation on
            // screen: the root's, or the open child's own.
            if (state.subagents.isNotEmpty()) {
                SubagentSummaryBar(
                    subagents = state.subagents,
                    depth = state.subagentDepth,
                    onOpen = { showSubagents = true }
                )
            }

            if (state.plan.active || state.plan.text != null) {
                PlanDock(state)
            }
            if (state.todos.isNotEmpty()) {
                TodoDock(state.todos)
            }

            // dsh-web hides the view ring on blank sessions and while a child transcript
            // owns the pane, so tabs only appear once this conversation has content.
            val showTabs = state.subagentView == null && state.items.isNotEmpty()
            if (showTabs) {
                HomeTabRow(
                    tabs = tabs,
                    current = pagerState.currentPage.coerceIn(0, tabs.lastIndex),
                    onSelect = { index ->
                        focusManager.clearFocus()
                        keyboard?.hide()
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                )
            }

            Box(Modifier.weight(1f)) {
                if (showTabs) {
                    // Pages swipe horizontally, matching dsh-web's view tabs; the composer
                    // below stays put so a running turn can be steered from any view.
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (tabs.getOrNull(page) ?: HomeTab.Chat) {
                            HomeTab.Chat -> ConversationBody(
                                vm = vm,
                                state = state,
                                environment = environment,
                                rootMode = rootMode,
                                listState = listState,
                                onOpenTerminal = onOpenTerminal,
                                onOpenSettings = onOpenSettings,
                                modifier = Modifier.fillMaxSize()
                            )
                            HomeTab.Trajectory -> TrajectoryPane(
                                state = state,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    ConversationBody(
                        vm = vm,
                        state = state,
                        environment = environment,
                        rootMode = rootMode,
                        listState = listState,
                        onOpenTerminal = onOpenTerminal,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (state.queue.isNotEmpty()) {
                QueueDock(
                    items = state.queue,
                    onEdit = vm::editQueuedMessage,
                    onSteer = vm::steerQueuedMessage,
                    onRemove = vm::removeQueuedMessage
                )
            }

            val approval = state.activeApprovals.firstOrNull()
            val question = state.activeQuestions.firstOrNull()
            if (approval != null) {
                ApprovalDock(
                    approval = approval,
                    extraCount = state.activeApprovals.size - 1,
                    onAllow = { vm.respondApproval(approval, true) },
                    onDeny = { vm.respondApproval(approval, false) }
                )
            } else if (question != null) {
                QuestionDock(
                    question = question,
                    extraCount = state.activeQuestions.size - 1,
                    onSubmit = { answers -> vm.answerQuestion(question, answers) }
                )
            }

            Composer(
                sessionId = state.activeSessionId ?: "",
                commands = state.commands,
                skills = state.skills,
                enabled = state.connection == AgentConnection.Ready &&
                    state.models?.routable != false &&
                    state.subagentView?.continuable != false,
                running = state.running,
                steerable = state.subagentView == null,
                hint = state.subagentView?.takeIf { !it.continuable }?.let {
                    "只读子 Agent 不接受后续消息，它的结果会回到主对话。"
                },
                models = state.models,
                permissionMode = state.permissionMode,
                permissionOptions = state.permissionCatalog.options,
                suggestionsEnabled = !(modelPicker != null || showPermission ||
                    showSubagents || confirmPermission != null),
                onPickModel = { modelPicker = ModelPane.Models },
                onPickEffort = { modelPicker = ModelPane.Efforts },
                onPickPermission = { showPermission = true },
                onSend = { text, images, files -> vm.sendChat(text, images, files) },
                onSteer = { text, images, files -> vm.steerChat(text, images, files) },
                onRefreshCatalogs = vm::refreshAgentCatalogs,
                onStop = vm::cancelAgentTurn
            )
        }
    }

    if (showPermission) {
        PermissionDialog(
            state = state,
            onDismiss = { showPermission = false },
            onPick = { value ->
                showPermission = false
                if (value == "danger-full-access" || value == "auto") {
                    confirmPermission = value
                } else {
                    vm.selectAgentPermission(value)
                }
            }
        )
    }

    confirmPermission?.let { value ->
        val label = permissionLabel(value, state.permissionCatalog.options)
        AlertDialog(
            onDismissRequest = { confirmPermission = null },
            containerColor = Surface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            shape = RoundedCornerShape(20.dp),
            title = { Text("切换到「$label」？", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    if (value == "auto") {
                        "自动审核会让 agent 自行判断并放行工具调用，不再逐条等待你确认。" +
                            "只在你信任当前任务范围时使用。"
                    } else {
                        "完全权限会让 agent 在 Kali 中不受工作区限制地执行命令，" +
                            "不再逐条等待你确认。只在你信任当前任务范围时使用。"
                    },
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.selectAgentPermission(value)
                    confirmPermission = null
                }) {
                    Text("仍然切换", color = Amber, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPermission = null }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    modelPicker?.let { pane ->
        ModelPickerDialog(
            models = state.models,
            initialPane = pane,
            onDismiss = { modelPicker = null },
            onSelect = { model, effort ->
                vm.selectModel(model.provider, model.id, effort)
                modelPicker = null
            }
        )
    }

    LaunchedEffect(showSubagents) {
        if (showSubagents) vm.refreshSubagents()
    }

    if (showSubagents) {
        SubagentPanelDialog(
            subagents = state.subagents,
            parentAvailable = state.subagentsParentAvailable,
            depth = state.subagentDepth,
            onDismiss = { showSubagents = false },
            onRefresh = vm::refreshSubagents,
            onOpen = { subagent ->
                showSubagents = false
                vm.openSubagent(subagent.parentId, subagent)
            }
        )
    }
}

/** Views dsh-web offers above the transcript. */
private enum class HomeTab(val label: String) {
    Chat("对话"),
    Trajectory("轨迹")
}

/**
 * The transcript (or the empty state for a new session). It is hosted both directly and as
 * the first page of the view pager, so scroll-following state stays in [HomeScreen].
 */
@Composable
private fun ConversationBody(
    vm: MainViewModel,
    state: AgentState,
    environment: EnvironmentUi,
    rootMode: Boolean,
    listState: LazyListState,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    Box(modifier) {
        if (state.items.isEmpty()) {
            EmptyState(
                environment = environment,
                rootMode = rootMode,
                modelRoutable = state.models?.routable ?: true,
                onOpenTerminal = onOpenTerminal,
                onOpenSettings = onOpenSettings,
                onSend = { text -> vm.sendChat(text) }
            )
        } else {
            MessageList(
                state = state,
                listState = listState,
                onLoadOlder = vm::loadOlder,
                onCopy = { text ->
                    clipboard.setText(AnnotatedString(text))
                    vm.notify("已复制")
                },
                onLoadImage = { image ->
                    state.activeSessionId?.let { vm.loadAgentImage(it, image) }
                }
            )
            // A short fade into the composer hints that the transcript continues below
            // without drawing a divider between two surfaces that should read as one.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Background)
                        )
                    )
            )
        }
    }
}

@Composable
private fun HomeTabRow(
    tabs: List<HomeTab>,
    current: Int,
    onSelect: (Int) -> Unit
) {
    // Plain "对话 | 轨迹" text tabs: they read as a switch, not as pill buttons.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            if (index > 0) {
                Text(
                    "|",
                    color = Border,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            val selected = index == current
            Text(
                tab.label,
                color = if (selected) TextPrimary else TextMuted,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

private sealed interface TrajectoryRow {
    data class Turn(val turn: Int) : TrajectoryRow
    data class Entry(val item: AgentItem) : TrajectoryRow
}

/** One entry per rendered item, with a turn divider whenever a new user prompt begins. */
private fun trajectoryRows(items: List<AgentItem>): List<TrajectoryRow> {
    val rows = mutableListOf<TrajectoryRow>()
    var turn = 0
    var lastTurn = 0
    items.forEach { item ->
        if (item is UserItem && item.source == null) turn += 1
        val current = turn.coerceAtLeast(1)
        if (current != lastTurn) {
            rows += TrajectoryRow.Turn(current)
            lastTurn = current
        }
        rows += TrajectoryRow.Entry(item)
    }
    return rows
}

/**
 * A native reading of dsh's trajectory: every user prompt, model step and tool call in
 * order, grouped by turn. Plugin-contributed views cannot be native (they are React
 * components in dsh-web), so those remain available in the Harness screen.
 */
@Composable
private fun TrajectoryPane(state: AgentState, modifier: Modifier = Modifier) {
    val rows = remember(state.items) { trajectoryRows(state.items) }
    if (rows.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("还没有轨迹", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "开始对话后，每一轮的用户消息、模型思考与工具调用都会按顺序记录在这里。",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 34.dp)
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp)
    ) {
        item(key = "trajectory:summary") {
            Text(
                if (state.running) "正在记录 · ${state.items.size} 条轨迹"
                else "${state.items.size} 条轨迹 · 按轮次排列",
                color = TextMuted,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        rows.forEach { row ->
            when (row) {
                is TrajectoryRow.Turn -> item(key = "trajectory:turn:${row.turn}") {
                    TrajectoryTurnHeader(row.turn)
                }
                is TrajectoryRow.Entry -> item(key = "trajectory:${row.item.key}") {
                    TrajectoryEntry(row.item)
                }
            }
        }
    }
}

@Composable
private fun TrajectoryTurnHeader(turn: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("第 $turn 轮", color = TextMuted, fontSize = 10.sp, letterSpacing = 0.4.sp)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Border.copy(alpha = 0.6f))
        )
    }
}

@Composable
private fun TrajectoryEntry(item: AgentItem) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    val toggle = { expanded = !expanded }
    when (item) {
        is UserItem -> {
            val preview = item.text.ifBlank {
                when {
                    item.images.isNotEmpty() && item.files.isNotEmpty() ->
                        "[${item.images.size} 张图片 · ${item.files.size} 个文件]"
                    item.images.isNotEmpty() -> "[${item.images.size} 张图片]"
                    item.files.isNotEmpty() -> item.files.joinToString("、") { it.name }
                    else -> ""
                }
            }
            TrajectoryLine(
                dotColor = Accent,
                label = "用户",
                meta = null,
                text = preview,
                detail = item.text.takeIf { it.isNotBlank() },
                expanded = expanded,
                onToggle = toggle
            )
        }
        is AssistantItem -> {
            val detail = buildList {
                if (item.reasoning.isNotBlank()) add("思考\n${item.reasoning}")
                if (item.text.isNotBlank() && (item.reasoning.isNotBlank() || item.text.length > 140)) {
                    add("回复\n${item.text}")
                }
            }.joinToString("\n\n").ifBlank { null }
            val meta = buildString {
                if (item.reasoning.isNotBlank()) append("思考 ${item.reasoning.length} 字")
                item.step?.let { step ->
                    if (isNotEmpty()) append(" · ")
                    append("第 $step 步")
                }
                if (item.streaming) {
                    if (isNotEmpty()) append(" · ")
                    append("生成中")
                }
            }.ifBlank { null }
            TrajectoryLine(
                dotColor = if (item.streaming) Amber else TextMuted,
                label = "助手",
                meta = meta,
                text = item.text.ifBlank { if (item.reasoning.isNotBlank()) "（仅思考）" else "" },
                detail = detail,
                expanded = expanded,
                onToggle = toggle
            )
        }
        is ToolItem -> {
            val detail = buildString {
                if (item.arguments.isNotBlank() &&
                    item.arguments != "{}" && item.arguments != "[]"
                ) {
                    append("参数\n").append(item.arguments)
                }
                if (item.resultText.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append(if (item.isError) "错误\n" else "输出\n").append(item.resultText)
                }
            }.ifBlank { null }
            TrajectoryLine(
                dotColor = when {
                    item.running -> Amber
                    item.isError -> Red
                    else -> Success
                },
                label = toolName(item.name),
                meta = toolSummary(item),
                text = "",
                detail = detail,
                monospaceDetail = true,
                expanded = expanded,
                onToggle = toggle
            )
        }
        is NoticeItem -> TrajectoryLine(
            dotColor = if (item.isError) Red else TextMuted,
            label = "提示",
            meta = null,
            text = item.text,
            detail = null,
            expanded = expanded,
            onToggle = toggle
        )
    }
}

@Composable
private fun TrajectoryLine(
    dotColor: Color,
    label: String,
    meta: String?,
    text: String,
    detail: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    monospaceDetail: Boolean = false
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = detail != null, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                label,
                color = TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            if (!meta.isNullOrBlank()) {
                Spacer(Modifier.width(7.dp))
                Text(
                    meta,
                    modifier = Modifier.weight(1f),
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (detail != null) {
                Text(if (expanded) "收起" else "展开", color = TextMuted, fontSize = 10.sp)
            }
        }
        if (text.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text,
                modifier = Modifier.padding(start = 12.dp),
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (expanded && !detail.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                detail.take(12_000),
                modifier = Modifier.padding(start = 12.dp),
                color = TextMuted,
                fontFamily = if (monospaceDetail) FontFamily.Monospace else FontFamily.Default,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun HomeTopBar(
    title: String,
    connection: AgentConnection,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit
) {
    val dotColor = when (connection) {
        AgentConnection.Ready -> Success
        AgentConnection.Starting -> Amber
        AgentConnection.Failed -> Red
        AgentConnection.Stopped -> TextMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Filled.Menu, contentDescription = "打开侧边栏", tint = TextSecondary)
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = title,
                modifier = Modifier.widthIn(max = 210.dp),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onNewChat) {
            Icon(Icons.Filled.Add, contentDescription = "新对话", tint = TextSecondary)
        }
    }
}

/**
 * A quiet one-line entry to the child catalog. It stays out of the transcript so a
 * delegated task never looks like a message the user wrote.
 */
@Composable
private fun SubagentSummaryBar(
    subagents: List<AgentSubagent>,
    depth: Int,
    onOpen: () -> Unit
) {
    val running = subagents.count { it.running && it.diagnostic == null }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(SurfaceRaised)
            .border(1.dp, Border, RoundedCornerShape(11.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (running > 0) Amber else TextMuted)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            if (depth > 0) "下级子 Agent" else "子 Agent",
            color = TextSecondary,
            fontSize = 11.5.sp
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (running > 0) "${subagents.size} 个 · $running 运行中" else "${subagents.size} 个",
            color = TextMuted,
            fontSize = 10.5.sp
        )
        Spacer(Modifier.weight(1f))
        Text("查看", color = TextMuted, fontSize = 10.5.sp)
        Spacer(Modifier.width(3.dp))
        Icon(
            Icons.Filled.ArrowForward,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(13.dp)
        )
    }
}

/** The header line shown while a child transcript owns the screen. */
@Composable
private fun SubagentTrailBar(
    view: AgentSubagent,
    depth: Int,
    running: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = if (depth > 1) "返回上一级" else "返回主对话",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (running) Amber else TextMuted)
        )
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                view.displayLabel,
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(if (view.continuable) "可对话的子 Agent" else "只读子 Agent")
                    append(" · 第 ")
                    append(depth)
                    append(" 层")
                    append(" · ")
                    append(if (running) "运行中" else "已结束")
                },
                color = TextMuted,
                fontSize = 10.sp
            )
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "刷新",
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SubagentPanelDialog(
    subagents: List<AgentSubagent>,
    parentAvailable: Boolean,
    depth: Int,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (AgentSubagent) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "子 Agent",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        tint = TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Text(
                if (depth > 0) {
                    "此子 Agent 委派出去的会话。打开可以查看完整过程；可对话的子 Agent 还能继续发消息。"
                } else {
                    "主 Agent 委派出去的独立会话。可对话的子 Agent 能在这里继续聊天，只读子 Agent 仅展示过程。"
                },
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            if (!parentAvailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "上级会话当前不可用于继续，子 Agent 暂时接收不了新消息。",
                    color = Amber,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            if (subagents.isEmpty()) {
                Text(
                    if (depth > 0) "此子 Agent 还没有下级。" else "这条对话还没有子 Agent。",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(subagents, key = { it.id }) { subagent ->
                        SubagentRow(subagent) { onOpen(subagent) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun SubagentRow(subagent: AgentSubagent, onClick: () -> Unit) {
    val diagnostic = subagent.diagnostic
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceRaised)
            .clickable(enabled = diagnostic == null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    when {
                        diagnostic != null -> Red
                        subagent.running -> Amber
                        else -> TextMuted
                    }
                )
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (diagnostic != null) "无法读取的子 Agent" else subagent.displayLabel,
                color = if (diagnostic != null) TextMuted else TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (diagnostic != null) {
                    "dsh 报告：$diagnostic"
                } else {
                    buildString {
                        append(if (subagent.continuable) "可对话" else "只读")
                        append(" · ")
                        append(if (subagent.running) "运行中" else "已结束")
                        if (subagent.hasChildren) append(" · 还有下级")
                    }
                },
                color = TextMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (diagnostic == null) {
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AgentLoadingState(state: AgentState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = TextMuted,
            strokeWidth = 2.dp
        )
        Spacer(Modifier.height(16.dp))
        Text(state.status, color = TextSecondary, fontSize = 14.sp)
        if (!state.error.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                state.error,
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun AgentSetupState(
    state: AgentState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 48.dp)
    ) {
        Text(
            "Agent 还没有就绪",
            color = TextPrimary,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            state.error ?: state.status,
            color = TextSecondary,
            fontSize = 13.5.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "对话由 Kali 内的 dsh 提供。先在设置里安装 Kali 环境、Node.js 和 Agent 核心。",
            color = TextMuted,
            fontSize = 12.5.sp,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryPill("重试连接", onRetry)
            OutlinePill("打开设置", onOpenSettings)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "连接只发生在应用与 Kali 内的回环地址之间。",
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun PrimaryPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, color = OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OutlinePill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyState(
    environment: EnvironmentUi,
    rootMode: Boolean,
    modelRoutable: Boolean,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    onSend: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 40.dp, bottom = 24.dp)
    ) {
        Text(
            "有什么可以帮你的？",
            color = TextPrimary,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 32.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "本地 dsh · 工具调用在 Kali 内执行",
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
        if (!modelRoutable) {
            Spacer(Modifier.height(10.dp))
            Text(
                "当前模型 provider 不可用，请在设置中检查配置。",
                color = Amber,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(26.dp))
        TerminalEntry(
            environment = environment,
            rootMode = rootMode,
            onOpen = onOpenTerminal,
            onSettings = onOpenSettings
        )

        Spacer(Modifier.height(20.dp))
        SuggestionRow("检查这台设备的 Kali 环境") { onSend(it) }
        Spacer(Modifier.height(8.dp))
        SuggestionRow("列出 /root 下的文件并说明用途") { onSend(it) }
        Spacer(Modifier.height(8.dp))
        SuggestionRow("帮我在 Kali 里验证一个本地实验") { onSend(it) }
    }
}

@Composable
private fun TerminalEntry(
    environment: EnvironmentUi,
    rootMode: Boolean,
    onOpen: () -> Unit,
    onSettings: () -> Unit
) {
    val subtitle = when {
        environment.installing -> environment.status ?: "正在安装 Kali 环境…"
        environment.installed ->
            if (rootMode) "Root · chroot · 环境就绪" else "PRoot · 无需 root · 环境就绪"

        else -> "首次使用需下载 ${formatBytes(environment.approxBytes)}"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TerminalMark(size = 40.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Kali 终端",
                color = TextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!environment.installed && !environment.installing) {
            TextButton(
                onClick = onSettings,
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Text("设置", color = TextSecondary, fontSize = 12.sp)
            }
        } else {
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SuggestionRow(text: String, onClick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface.copy(alpha = 0.65f))
            .clickable { onClick(text) }
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            color = TextSecondary,
            fontSize = 13.5.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.ArrowForward,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun PlanDock(state: AgentState) {
    val plan = state.plan
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Amber.copy(alpha = 0.07f))
            .border(1.dp, Amber.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("计划", color = Amber, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                plan.text?.takeIf { it.isNotBlank() }?.let { compact(it, 400) }
                    ?: if (plan.pending) "等待你的确认" else "计划模式已开启",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TodoDock(todos: List<AgentTodo>) {
    val done = todos.count { it.status.equals("completed", ignoreCase = true) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("待办", color = TextMuted, fontSize = 10.5.sp)
            Spacer(Modifier.width(8.dp))
            Text("$done/${todos.size}", color = TextMuted, fontSize = 10.5.sp)
        }
        Spacer(Modifier.height(5.dp))
        todos.take(3).forEach { todo ->
            val (mark, color) = when {
                todo.status.equals("completed", true) -> "✓" to Success
                todo.status.contains("progress", true) -> "●" to Amber
                else -> "○" to TextMuted
            }
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                Text(mark, color = color, fontSize = 10.sp, modifier = Modifier.width(16.dp))
                Text(
                    todo.text,
                    color = if (todo.status.equals("completed", true)) TextMuted else TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (todos.size > 3) {
            Text("+${todos.size - 3} 项", color = TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun QueueDock(
    items: List<AgentQueueItem>,
    onEdit: (String, String) -> Unit,
    onSteer: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var active by remember { mutableStateOf<AgentQueueItem?>(null) }
    var editing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("待发送", color = TextMuted, fontSize = 10.5.sp)
            Spacer(Modifier.weight(1f))
            Text("${items.size} · 点按管理", color = TextMuted, fontSize = 10.sp)
        }
        items.take(3).forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        editing = false
                        active = item
                    }
                    .padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    if (item.placement == "steering") "引导" else "排队",
                    color = if (item.placement == "steering") Amber else TextMuted,
                    fontSize = 9.5.sp,
                    modifier = Modifier.width(32.dp)
                )
                Text(
                    item.displayText,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (items.size > 3) {
            Text("+${items.size - 3} 条", color = TextMuted, fontSize = 10.sp)
        }
    }

    active?.let { item ->
        if (editing) {
            QueueEditDialog(
                item = item,
                onDismiss = { active = null; editing = false },
                onSave = { text ->
                    onEdit(item.id, text)
                    active = null
                    editing = false
                }
            )
        } else {
            QueueActionsDialog(
                item = item,
                onDismiss = { active = null },
                onEdit = { editing = true },
                onSteer = {
                    onSteer(item.id)
                    active = null
                },
                onRemove = {
                    onRemove(item.id)
                    active = null
                }
            )
        }
    }
}

@Composable
private fun QueueActionsDialog(
    item: AgentQueueItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSteer: () -> Unit,
    onRemove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("排队消息", style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
        text = {
            Column {
                Text(
                    item.displayText,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(14.dp))
                QueueActionRow("编辑内容", TextSecondary, onEdit)
                QueueActionRow("转为立即引导", Amber, onSteer)
                QueueActionRow("移除", Red, onRemove)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun QueueActionRow(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 12.5.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp)
    )
}

@Composable
private fun QueueEditDialog(
    item: AgentQueueItem,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by rememberSaveable(item.id) { mutableStateOf(item.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("编辑排队消息", style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
        text = {
            Column {
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 4000) draft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceRaised)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(Accent),
                    maxLines = 6
                )
                if (item.images.isNotEmpty()) {
                    Text(
                        if (item.images.size == 1) "保存后保留原图片" else "保存后保留原 ${item.images.size} 张图片",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp)
                    )
                }
                if (item.files.isNotEmpty()) {
                    Text(
                        "保存后保留 ${item.files.size} 个文件的 Kali 路径",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            val canSave = draft.isNotBlank() || item.images.isNotEmpty() || item.files.isNotEmpty()
            TextButton(
                onClick = { onSave(draft) },
                enabled = canSave
            ) {
                Text("保存", color = if (canSave) Accent else TextMuted)
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
private fun MessageList(
    state: AgentState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLoadOlder: () -> Unit,
    onCopy: (String) -> Unit,
    onLoadImage: (AgentImage) -> Unit
) {
    val sessionId = state.activeSessionId
    fun imageData(image: AgentImage): String? =
        image.localData ?: sessionId?.let { state.imageData["$it:${image.attachmentId}"] }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (state.hasMoreHistory) {
            item(key = "older") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onLoadOlder) {
                        Text("加载更早的消息", color = TextMuted, fontSize = 11.5.sp)
                    }
                }
            }
        }
        items(state.items, key = { it.key }) { item ->
            when (item) {
                is UserItem -> UserMessageRow(item, onCopy, ::imageData, onLoadImage)
                is AssistantItem -> AssistantMessageRow(item, onCopy, ::imageData, onLoadImage)
                is ToolItem -> ToolCard(item)
                is NoticeItem -> NoticeRow(item)
            }
        }
        if (state.running && state.items.none { it is AssistantItem && it.streaming }) {
            item(key = "typing") { TypingRow() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserMessageRow(
    message: UserItem,
    onCopy: (String) -> Unit,
    imageData: (AgentImage) -> String?,
    onLoadImage: (AgentImage) -> Unit
) {
    LaunchedEffect(message.images) {
        message.images.forEach { if (imageData(it) == null) onLoadImage(it) }
    }
    var preview by remember { mutableStateOf<String?>(null) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(horizontalAlignment = Alignment.End) {
            MessageImages(
                images = message.images,
                data = imageData,
                alignEnd = true,
                onOpen = { preview = it }
            )
            MessageFiles(message.files)
            if (message.text.isNotBlank()) {
                Text(
                    text = message.text,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 20.dp,
                                topEnd = 20.dp,
                                bottomStart = 20.dp,
                                bottomEnd = 6.dp
                            )
                        )
                        .background(SurfaceVariant.copy(alpha = if (message.pending) 0.6f else 1f))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onCopy(message.text) }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 23.sp
                )
            }
            if (message.pending) {
                Text(
                    "发送中",
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(top = 3.dp, end = 4.dp)
                )
            }
        }
    }
    preview?.let { ImagePreviewDialog(it) { preview = null } }
}

@Composable
private fun MessageImages(
    images: List<AgentImage>,
    data: (AgentImage) -> String?,
    alignEnd: Boolean,
    onOpen: (String) -> Unit
) {
    if (images.isEmpty()) return
    Row(
        Modifier
            .padding(bottom = if (alignEnd) 6.dp else 0.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        images.take(4).forEach { image ->
            val url = data(image)
            AgentThumb(
                dataUrl = url,
                size = 148.dp,
                onClick = url?.let { { onOpen(it) } }
            )
        }
    }
}

@Composable
private fun MessageFiles(files: List<AgentFile>) {
    if (files.isEmpty()) return
    Row(
        Modifier
            .padding(bottom = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        files.forEach { file ->
            FileChip(
                name = file.name,
                meta = if (file.size > 0) formatFileSize(file.size) else "Kali 共享目录",
                onRemove = null
            )
        }
    }
}

/** A file drafted in the composer or already sent; the guest path stays out of the bubble. */
@Composable
private fun FileChip(
    name: String,
    meta: String,
    onRemove: (() -> Unit)?
) {
    Row(
        Modifier
            .width(184.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Border.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
            .padding(start = 8.dp, end = if (onRemove != null) 4.dp else 9.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(AccentDim),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "FILE",
                color = AccentSoft,
                fontSize = 6.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = TextPrimary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                meta,
                color = TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onRemove != null) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Text("×", color = TextMuted, fontSize = 13.sp)
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    return String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
}

@Composable
private fun AgentThumb(
    dataUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp = 16.dp,
    onClick: (() -> Unit)? = null
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, dataUrl) {
        value = dataUrl?.let { withContext(Dispatchers.IO) { decodeImage(it, 512) } }
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(SurfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(
                color = TextMuted,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ImagePreviewDialog(dataUrl: String, onDismiss: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, dataUrl) {
        value = withContext(Dispatchers.IO) { decodeImage(dataUrl, 1600) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceRaised)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            val current = bitmap
            if (current != null) {
                Image(
                    bitmap = current,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                CircularProgressIndicator(color = TextMuted, strokeWidth = 2.dp)
            }
        }
    }
}

/** Decodes a `data:` URL, downsampling so a multi-megabyte photo fits the requested box. */
private fun decodeImage(dataUrl: String, maxPixels: Int): ImageBitmap? {
    val comma = dataUrl.indexOf(',')
    if (comma <= 0) return null
    return try {
        val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / 2 >= maxPixels) {
            sample *= 2
            largest /= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    } catch (t: Throwable) {
        null
    }
}

private const val MAX_DRAFT_IMAGES = 4
private const val MAX_DRAFT_IMAGE_BYTES = 8 * 1024 * 1024
private val allowedImageTypes = setOf(
    "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif"
)

/**
 * Reads one picked image into the composer's draft. dsh accepts these four raster types
 * as prompt bytes, so anything else is refused here rather than failing at admission.
 */
private fun readDraftImage(context: Context, uri: Uri): AgentDraftImage? {
    val resolver = context.contentResolver
    val declared = (resolver.getType(uri) ?: "").lowercase()
    if (declared !in allowedImageTypes) return null
    val bytes = try {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (t: Throwable) {
        null
    } ?: return null
    if (bytes.isEmpty() || bytes.size > MAX_DRAFT_IMAGE_BYTES) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return AgentDraftImage(
        id = UUID.randomUUID().toString(),
        name = displayNameOf(context, uri) ?: "image",
        mediaType = if (declared == "image/jpg") "image/jpeg" else declared,
        bytes = bytes,
        width = bounds.outWidth,
        height = bounds.outHeight
    )
}

private fun displayNameOf(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
} catch (t: Throwable) {
    null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantMessageRow(
    message: AssistantItem,
    onCopy: (String) -> Unit,
    imageData: (AgentImage) -> String?,
    onLoadImage: (AgentImage) -> Unit
) {
    LaunchedEffect(message.images) {
        message.images.forEach { if (imageData(it) == null) onLoadImage(it) }
    }
    var preview by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth()) {
        TerminalMark(size = 26.dp, filled = false)
        Spacer(Modifier.width(11.dp))
        Column(
            Modifier
                .weight(1f)
                .combinedClickable(onClick = {}, onLongClick = { onCopy(message.text) })
        ) {
            if (message.reasoning.isNotBlank()) {
                ReasoningBlock(message)
                if (message.text.isNotBlank()) Spacer(Modifier.height(10.dp))
            }
            if (message.text.isNotBlank()) {
                MarkdownMessage(message.text, message.streaming)
            } else if (message.streaming && message.images.isEmpty()) {
                TypingDots()
            }
            if (message.images.isNotEmpty()) {
                if (message.text.isNotBlank()) Spacer(Modifier.height(10.dp))
                MessageImages(
                    images = message.images,
                    data = imageData,
                    alignEnd = false,
                    onOpen = { preview = it }
                )
            }
        }
    }
    preview?.let { ImagePreviewDialog(it) { preview = null } }
}

private sealed interface MarkdownBlock {
    data class Prose(val text: String) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
}

/**
 * Splits assistant output into prose, fenced code and pipe tables. The text is re-parsed from
 * the start on every frame, so a table that is still streaming simply joins once its header
 * separator arrives. Never interprets HTML.
 */
private fun parseMarkdownBlocks(source: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val prose = StringBuilder()
    val code = StringBuilder()
    val lines = source.lines()
    var language: String? = null
    var inCode = false
    var index = 0

    fun flushProse() {
        val value = prose.toString().trim('\n')
        if (value.isNotBlank()) blocks += MarkdownBlock.Prose(value)
        prose.setLength(0)
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trimStart()
        val fence = trimmed.startsWith("```") || trimmed.startsWith("~~~")
        if (fence) {
            if (inCode) {
                blocks += MarkdownBlock.Code(language, code.toString().trimEnd('\n'))
                code.setLength(0)
                language = null
                inCode = false
            } else {
                flushProse()
                inCode = true
                language = trimmed.drop(3).trim().takeIf { it.isNotBlank() }
            }
            index++
            continue
        }
        if (!inCode && index + 1 < lines.size &&
            isTableRow(line) && isTableSeparator(lines[index + 1])
        ) {
            flushProse()
            val header = tableCells(line)
            val rows = mutableListOf<List<String>>()
            index += 2
            while (index < lines.size && isTableRow(lines[index]) &&
                !isTableSeparator(lines[index])
            ) {
                rows += tableCells(lines[index])
                index++
            }
            blocks += MarkdownBlock.Table(header, rows)
            continue
        }
        if (inCode) code.appendLine(line) else prose.appendLine(line)
        index++
    }
    if (inCode) {
        // An unterminated fence is normal while streaming; show what has arrived.
        blocks += MarkdownBlock.Code(language, code.toString().trimEnd('\n'))
    }
    flushProse()
    return blocks
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.startsWith("|") && trimmed.count { it == '|' } >= 2
}

private fun isTableSeparator(line: String): Boolean {
    val cells = tableCells(line)
    return cells.isNotEmpty() && cells.all { Regex("^:?-{2,}:?$").matches(it) }
}

private fun tableCells(line: String): List<String> {
    val body = line.trim().removePrefix("|").removeSuffix("|")
    return body.split('|').map { it.trim() }
}

private val headingMarkdown = Regex("^(#{1,6})\\s+(.*)$")
private val inlineMarkdown = Regex(
    "(\\[[^\\]\\n]+]\\([^)\\s]+\\)|`[^`\\n]+`|\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__|~~[^~\\n]+~~|\\*[^*\\n]+\\*|_[^_\\n]+_)"
)

@Composable
private fun markdownAnnotated(source: String): AnnotatedString = buildAnnotatedString {
    source.lineSequence().forEachIndexed { index, line ->
        if (index > 0) append('\n')
        val heading = headingMarkdown.find(line)
        val bullet = line.trimStart()
        when {
            heading != null -> withStyle(
                SpanStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
            ) { appendInline(heading.groupValues[2]) }

            bullet.startsWith("- ") || bullet.startsWith("* ") || bullet.startsWith("+ ") -> {
                append("• ")
                appendInline(bullet.drop(2))
            }

            bullet.startsWith("> ") -> {
                append("▏ ")
                appendInline(bullet.drop(2))
            }

            else -> appendInline(line)
        }
    }
}

@Composable
private fun AnnotatedString.Builder.appendInline(line: String) {
    var cursor = 0
    inlineMarkdown.findAll(line).forEach { match ->
        append(line.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("[") -> {
                // Link text only; the target stays in the raw message so a long-press copy
                // still carries the full URL, and nothing is opened automatically.
                val split = token.indexOf("](")
                val label = token.substring(1, split)
                val url = token.substring(split + 2, token.length - 1)
                withStyle(
                    SpanStyle(color = Accent, textDecoration = TextDecoration.Underline)
                ) { append(label.ifBlank { url }) }
            }

            token.startsWith("`") -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    background = SurfaceVariant
                )
            ) { append(token.substring(1, token.length - 1)) }

            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(token.substring(2, token.length - 2))
            }

            token.startsWith("__") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(token.substring(2, token.length - 2))
            }

            token.startsWith("~~") -> withStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough)
            ) { append(token.substring(2, token.length - 2)) }

            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.substring(1, token.length - 1))
            }
        }
        cursor = match.range.last + 1
    }
    append(line.substring(cursor))
}

@Composable
private fun MarkdownMessage(text: String, streaming: Boolean) {
    val rendered = if (streaming) "$text ▍" else text
    val blocks = remember(rendered) { parseMarkdownBlocks(rendered) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Prose -> Text(
                    text = markdownAnnotated(block.text),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 24.sp
                )

                is MarkdownBlock.Code -> MarkdownCodeBlock(block.language, block.code)
                is MarkdownBlock.Table -> MarkdownTable(block)
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table) {
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
    ) {
        TableRow(table.header, columns, header = true)
        table.rows.forEachIndexed { index, cells ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Border.copy(alpha = 0.35f))
                )
            }
            TableRow(cells, columns, header = false)
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, columns: Int, header: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (header) SurfaceVariant.copy(alpha = 0.55f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        repeat(columns) { column ->
            Text(
                text = markdownAnnotated(cells.getOrElse(column) { "" }),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                color = if (header) TextPrimary else TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun MarkdownCodeBlock(language: String?, code: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        if (!language.isNullOrBlank()) {
            Text(
                language.lowercase(),
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp
            )
            Spacer(Modifier.height(5.dp))
        }
        Text(
            text = code,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun ReasoningBlock(message: AssistantItem) {
    var expanded by rememberSaveable(message.key) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Surface.copy(alpha = 0.7f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("思考过程", color = TextMuted, fontSize = 10.5.sp)
            Spacer(Modifier.weight(1f))
            Text(if (expanded) "收起" else "展开", color = TextMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = message.reasoning,
            color = TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToolCard(tool: ToolItem) {
    var expanded by rememberSaveable(tool.key) { mutableStateOf(false) }
    val dotColor = when {
        tool.running -> Amber
        tool.isError -> Red
        else -> Success
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 37.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Border.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                toolName(tool.name),
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(8.dp))
            Text(
                toolSummary(tool),
                modifier = Modifier.weight(1f),
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (expanded) "收起" else "详情",
                color = TextMuted,
                fontSize = 10.sp
            )
        }
        if (expanded) {
            if (tool.arguments.isNotBlank() && tool.arguments != "{}" && tool.arguments != "[]") {
                Spacer(Modifier.height(9.dp))
                Label("参数")
                Text(
                    tool.arguments,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
            }
            if (tool.resultText.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Label(if (tool.isError) "错误" else "输出")
                Text(
                    tool.resultText.take(12_000),
                    color = if (tool.isError) Red else TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        color = TextMuted,
        fontSize = 9.5.sp,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(bottom = 3.dp)
    )
}

/** dsh providers use stable English effort ids; the picker and top bar speak Chinese. */
private fun effortLabel(effort: AgentEffort): String = when (effort.id.lowercase()) {
    "minimal" -> "极简"
    "low" -> "低"
    "medium", "med" -> "中"
    "high" -> "高"
    "xhigh", "extra-high" -> "极高"
    "max", "maximum" -> "最高"
    "off", "none" -> "无"
    "auto", "automatic" -> "自动"
    "default" -> "默认"
    else -> effort.name.ifBlank { effort.id }
}

private fun toolName(name: String): String {
    if (name.startsWith("mcp__")) {
        val parts = name.split("__")
        if (parts.size >= 3) {
            return "MCP · ${parts[1]} / ${parts.drop(2).joinToString("__")}"
        }
    }
    return builtinToolName(name)
}

private fun builtinToolName(name: String): String = when (name) {
    "bash", "pwsh", "terminal" -> "终端"
    "read" -> "读取"
    "write", "edit" -> "写入"
    "grep" -> "搜索内容"
    "glob" -> "查找文件"
    "web" -> "网络"
    "todo" -> "待办"
    "question" -> "提问"
    "read_image" -> "读取图片"
    else -> name
}

private fun toolSummary(tool: ToolItem): String {
    if (tool.running) return "运行中"
    val raw = tool.arguments
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() && it != "{" && it != "}" }
        .orEmpty()
    if (raw.isNotBlank()) return compact(raw, 90)
    return if (tool.isError) "失败" else "已完成"
}

@Composable
private fun NoticeRow(item: NoticeItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            item.text,
            color = if (item.isError) Red.copy(alpha = 0.9f) else TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TypingRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 37.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TypingDots()
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 620,
                        delayMillis = index * 150,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(TextSecondary.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun ApprovalDock(
    approval: AgentApproval,
    extraCount: Int,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceRaised)
            .border(1.dp, Amber.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("需要审批", color = Amber, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            Text(toolName(approval.toolName), color = TextSecondary, fontSize = 11.5.sp)
            Spacer(Modifier.weight(1f))
            if (extraCount > 0) {
                Text("还有 $extraCount 个", color = TextMuted, fontSize = 10.sp)
            }
        }
        if (!approval.reason.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                approval.reason,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDeny)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("拒绝", color = TextSecondary, fontSize = 12.sp)
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Accent)
                    .clickable(onClick = onAllow)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("允许一次", color = OnAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun QuestionDock(
    question: AgentQuestion,
    extraCount: Int,
    onSubmit: (List<AgentAnswer>) -> Unit
) {
    var selected by remember(question.rpcId) {
        mutableStateOf<Map<String, Set<String>>>(emptyMap())
    }
    var custom by remember(question.rpcId) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }

    fun complete(item: AgentQuestionItem): Boolean {
        if (custom[item.id].orEmpty().isNotBlank()) return true
        val values = selected[item.id].orEmpty()
        return values.isNotEmpty() || (item.options.isEmpty() && custom[item.id].orEmpty().isNotBlank())
    }

    val ready = question.questions.all(::complete)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceRaised)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("需要你的回答", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (extraCount > 0) {
                Text("还有 $extraCount 组", color = TextMuted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            question.questions.forEach { item ->
                Spacer(Modifier.height(8.dp))
                if (!item.header.isNullOrBlank()) {
                    Text(item.header, color = TextMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(2.dp))
                }
                Text(item.question, color = TextSecondary, fontSize = 12.5.sp, lineHeight = 18.sp)
                if (!item.detail.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(item.detail, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }
                item.options.forEach { option ->
                    val isSelected = selected[item.id].orEmpty().contains(option.label)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) Accent.copy(alpha = 0.10f) else Surface)
                            .border(
                                1.dp,
                                if (isSelected) AccentSoft.copy(alpha = 0.35f) else Border,
                                RoundedCornerShape(9.dp)
                            )
                            .clickable {
                                selected = selected.toMutableMap().apply {
                                    val current = this[item.id].orEmpty()
                                    this[item.id] = if (item.multiSelect) {
                                        if (current.contains(option.label)) current - option.label
                                        else current + option.label
                                    } else {
                                        setOf(option.label)
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            if (isSelected) "✓" else "○",
                            color = if (isSelected) Success else TextMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.width(17.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(option.label, color = TextPrimary, fontSize = 12.sp)
                            if (!option.description.isNullOrBlank()) {
                                Text(
                                    option.description,
                                    color = TextMuted,
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                BasicTextField(
                    value = custom[item.id].orEmpty(),
                    onValueChange = { value ->
                        custom = custom.toMutableMap().apply { this[item.id] = value }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    ),
                    cursorBrush = SolidColor(Accent),
                    maxLines = 3,
                    decorationBox = { inner ->
                        if (custom[item.id].orEmpty().isEmpty()) {
                            Text("其他回答（可选）", color = TextMuted, fontSize = 12.sp)
                        }
                        inner()
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (ready) Accent else SurfaceVariant)
                .clickable(enabled = ready) {
                    onSubmit(
                        question.questions.map { item ->
                            AgentAnswer(
                                id = item.id,
                                selected = selected[item.id].orEmpty().toList(),
                                custom = custom[item.id]?.takeIf { it.isNotBlank() }
                            )
                        }
                    )
                }
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "提交回答",
                color = if (ready) OnAccent else TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * dsh ships preset names in English; well-known values get a Chinese label while any
 * preset a future dsh adds still renders with its own supplied name.
 */
internal fun permissionLabel(value: String, options: List<AgentPermissionOption>): String {
    val name = options.firstOrNull { it.value == value }?.name
    if (!name.isNullOrBlank() && name != value) return name
    return when (value) {
        "workspace-write" -> "工作区内修改"
        "read-only" -> "只读"
        "danger-full-access" -> "完全访问"
        "auto" -> "自动审查"
        "custom" -> "自定义"
        else -> name?.takeIf { it.isNotBlank() } ?: value
    }
}

internal fun permissionDescription(value: String, option: AgentPermissionOption): String? =
    when (value) {
        "workspace-write" -> "可在工作区与允许的临时目录写文件；越界操作需要审批"
        "read-only" -> "只能读取，不能改动文件"
        "danger-full-access" -> "文件与命令不再逐条审批，风险最高"
        "auto" -> "由自动审查逐次判断；被拒绝时仍会询问你"
        "custom" -> "当前沙箱与审批组合不对应任何预设"
        else -> option.description
    }

@Composable
private fun PermissionDialog(
    state: AgentState,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = { Text("对话权限", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "仅影响当前对话；每次切换都由 dsh 记录在会话里。",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                state.permissionCatalog.options.forEach { option ->
                    val selected = option.value == state.permissionMode
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !selected) { onPick(option.value) }
                            .background(
                                if (selected) AccentDim.copy(alpha = 0.6f) else Color.Transparent
                            )
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
                                    permissionLabel(option.value, state.permissionCatalog.options),
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TextSecondary)
            }
        }
    )
}

/** A quiet text control for the composer toolbar; it stays flat so the input owns the eye. */
@Composable
private fun ComposerChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    maxWidth: Dp = Dp.Unspecified
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            modifier = if (maxWidth == Dp.Unspecified) Modifier else Modifier.widthIn(max = maxWidth),
            color = if (enabled) TextMuted else TextMuted.copy(alpha = 0.55f),
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = TextMuted.copy(alpha = 0.8f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun Composer(
    sessionId: String,
    commands: List<AgentCommand>,
    skills: List<AgentSkill>,
    enabled: Boolean,
    running: Boolean,
    steerable: Boolean = true,
    hint: String? = null,
    models: AgentModels? = null,
    permissionMode: String? = null,
    permissionOptions: List<AgentPermissionOption> = emptyList(),
    suggestionsEnabled: Boolean = true,
    onPickModel: () -> Unit = {},
    onPickEffort: () -> Unit = {},
    onPickPermission: () -> Unit = {},
    onSend: (String, List<AgentDraftImage>, List<AgentDraftFile>) -> Unit,
    onSteer: (String, List<AgentDraftImage>, List<AgentDraftFile>) -> Unit,
    onRefreshCatalogs: () -> Unit,
    onStop: () -> Unit
) {
    var input by rememberSaveable(sessionId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var steer by rememberSaveable(sessionId) { mutableStateOf(false) }
    var drafts by remember(sessionId) { mutableStateOf(emptyList<AgentDraftImage>()) }
    var files by remember(sessionId) { mutableStateOf(emptyList<AgentDraftFile>()) }
    var attachError by remember(sessionId) { mutableStateOf<String?>(null) }
    var showAttachMenu by remember(sessionId) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        attachError = null
        scope.launch {
            val room = (MAX_DRAFT_IMAGES - drafts.size).coerceAtLeast(0)
            val loaded = withContext(Dispatchers.IO) {
                uris.take(room).mapNotNull { readDraftImage(context, it) }
            }
            when {
                loaded.size < uris.take(room).size ->
                    attachError = "有图片无法使用：仅支持 PNG / JPEG / WEBP / GIF，单张不超过 8 MB"
                else -> attachError = null
            }
            if (loaded.isNotEmpty()) drafts = (drafts + loaded).take(MAX_DRAFT_IMAGES)
        }
    }

    // dsh's prompt wire cannot carry arbitrary bytes, so picked files are staged in the
    // Kali-visible shared directory before the prompt text names their guest paths.
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        attachError = null
        scope.launch {
            val room = (AttachmentStaging.MAX_FILES - files.size).coerceAtLeast(0)
            if (room == 0) {
                attachError = "最多附加 ${AttachmentStaging.MAX_FILES} 个文件"
                return@launch
            }
            val chosen = uris.take(room)
            val staged = withContext(Dispatchers.IO) {
                AttachmentStaging.stage(context, chosen)
            }
            if (staged.isNotEmpty()) files = files + staged
            attachError = when {
                uris.size > room ->
                    "最多附加 ${AttachmentStaging.MAX_FILES} 个文件，已保留前 $room 个"
                staged.size < chosen.size -> "有文件未能添加：单个文件不超过 64 MB"
                else -> null
            }
        }
    }

    // Unsent staged files would otherwise sit in the Kali-visible shared directory after
    // the composer goes away. Reading through rememberUpdatedState keeps the cleanup from
    // deleting files that were already handed to dsh (submit clears the list first).
    val latestFiles by rememberUpdatedState(files)
    DisposableEffect(sessionId) {
        onDispose { latestFiles.forEach { AttachmentStaging.discard(it) } }
    }

    fun submit() {
        val text = input.text.trim()
        if (!enabled || (text.isEmpty() && drafts.isEmpty() && files.isEmpty())) return
        val images = drafts
        val attached = files
        if (running && steer) onSteer(text, images, attached) else onSend(text, images, attached)
        input = TextFieldValue("")
        drafts = emptyList()
        files = emptyList()
    }

    // Command discovery appears only while the first token is still being typed, so it
    // never covers the transcript once the user has moved on to arguments.
    val typedName = input.text.takeIf {
        suggestionsEnabled && it.startsWith("/") && it.none(Char::isWhitespace)
    }?.drop(1)
    val suggestions = if (typedName != null) {
        slashSuggestions(commands, skills, typedName)
    } else {
        emptyList()
    }
    // Re-read the catalogs each time the slash menu opens: MCP/plugin changes and newly
    // installed skills land through bridge events, but opening the menu is the moment a
    // stale list would actually be seen. LaunchedEffect keys on the open state, not each
    // keystroke, so typing never floods the bridge.
    LaunchedEffect(typedName != null) {
        if (typedName != null) onRefreshCatalogs()
    }

    val modelName = models?.current?.name ?: models?.model
    val effortText = if (models?.current?.efforts.isNullOrEmpty()) {
        null
    } else {
        models?.currentEffort?.let(::effortLabel) ?: "默认"
    }
    val permissionText = permissionMode?.let { permissionLabel(it, permissionOptions) } ?: "权限"
    val showToolbar = !modelName.isNullOrBlank() || permissionOptions.isNotEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .background(Background)
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
    ) {
        if (running) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (steerable) "当前任务运行中" else "子 Agent 运行中",
                    color = TextMuted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.weight(1f))
                if (steerable) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { steer = !steer }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (steer) "发送为引导" else "发送为排队",
                            color = if (steer) Amber else TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
        if (hint != null) {
            Text(
                hint,
                color = TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 7.dp, end = 7.dp, bottom = 6.dp)
            )
        }
        if (drafts.isNotEmpty() || files.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                drafts.forEach { draft ->
                    Box {
                        AgentThumb(dataUrl = draft.dataUrl, size = 62.dp)
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Background.copy(alpha = 0.86f))
                                .clickable {
                                    drafts = drafts.filterNot { it.id == draft.id }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("×", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
                files.forEach { file ->
                    FileChip(
                        name = file.name,
                        meta = formatFileSize(file.size),
                        onRemove = {
                            AttachmentStaging.discard(file)
                            files = files.filterNot { it.id == file.id }
                        }
                    )
                }
            }
        }
        attachError?.let { message ->
            Text(
                message,
                color = Amber,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(start = 7.dp, bottom = 4.dp)
            )
        }
        if (drafts.isEmpty() && files.isEmpty() && suggestions.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .border(1.dp, Border.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                val mixed = suggestions.any { it.skill } && suggestions.any { !it.skill }
                suggestions.forEachIndexed { index, suggestion ->
                    if (mixed && (index == 0 || suggestions[index - 1].skill != suggestion.skill)) {
                        Text(
                            if (suggestion.skill) "技能" else "命令",
                            color = TextMuted,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp)
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val inserted = "/${suggestion.name} "
                                input = TextFieldValue(
                                    text = inserted,
                                    selection = TextRange(inserted.length)
                                )
                                focusRequester.requestFocus()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                suggestion.label,
                                color = Accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val detail = listOfNotNull(
                                suggestion.description.takeIf { it.isNotBlank() },
                                suggestion.hint?.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            if (detail.isNotBlank()) {
                                Text(
                                    detail,
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "/${suggestion.name}",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        if (showToolbar) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 1.dp, end = 3.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (permissionOptions.isNotEmpty()) {
                    ComposerChip(
                        text = permissionText,
                        onClick = onPickPermission
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!modelName.isNullOrBlank()) {
                    ComposerChip(
                        text = modelName,
                        enabled = enabled,
                        maxWidth = 180.dp,
                        onClick = onPickModel
                    )
                }
                if (effortText != null) {
                    ComposerChip(
                        text = "推理 $effortText",
                        enabled = enabled,
                        onClick = onPickEffort
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(SurfaceRaised)
                .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val canAddImage = enabled && drafts.size < MAX_DRAFT_IMAGES
            val canAddFile = enabled && files.size < AttachmentStaging.MAX_FILES
            val canAttach = canAddImage || canAddFile
            Box {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canAttach) { showAttachMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "添加附件",
                        tint = if (canAttach) TextSecondary else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { showAttachMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("图片", color = if (canAddImage) TextPrimary else TextMuted, fontSize = 13.sp) },
                        enabled = canAddImage,
                        onClick = {
                            showAttachMenu = false
                            pickImages.launch("image/*")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("文件", color = if (canAddFile) TextPrimary else TextMuted, fontSize = 13.sp) },
                        enabled = canAddFile,
                        onClick = {
                            showAttachMenu = false
                            pickFiles.launch(arrayOf("*/*"))
                        }
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = input,
                onValueChange = { if (it.text.length <= 4000) input = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .padding(vertical = 7.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                ),
                cursorBrush = SolidColor(Accent),
                maxLines = 5,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                decorationBox = { inner ->
                    if (input.text.isEmpty()) {
                        Text(
                            when {
                                !enabled -> "Agent 尚未连接"
                                running && steer -> "立即引导当前任务…"
                                running -> "排队一条消息…"
                                else -> "给 KingKang 发送消息…"
                            },
                            color = TextMuted,
                            fontSize = 15.sp
                        )
                    }
                    inner()
                }
            )
            if (running) {
                Spacer(Modifier.width(5.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SurfaceVariant)
                        .clickable(onClick = onStop),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TextSecondary)
                    )
                }
            }
            Spacer(Modifier.width(7.dp))
            val canSend = enabled &&
                (input.text.isNotBlank() || drafts.isNotEmpty() || files.isNotEmpty())
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (canSend) Accent else SurfaceVariant)
                    .clickable(enabled = canSend) { submit() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "发送",
                    tint = if (canSend) OnAccent else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private data class SlashSuggestion(
    val name: String,
    val label: String,
    val description: String,
    val hint: String?,
    val skill: Boolean
)

/**
 * dsh's built-in commands ship English host copy but native zh labels. The slash menu
 * keeps the invocable token (`/compact`) while showing the same Chinese wording as
 * dsh web; unknown or future commands fall back to the host's own text.
 */
private val builtinCommandCopy = mapOf(
    "compact" to ("压缩" to "压缩以上对话内容"),
    "export" to ("导出" to "将当前会话内容导出为 ZIP"),
    "feedback" to ("反馈" to "发送关于当前会话的反馈"),
    "goal" to ("目标" to "设置或查看长期任务目标"),
    "permission" to ("权限" to "切换权限预设（沙箱模式与审批策略）"),
    "plan" to ("计划" to "进入或退出计划模式")
)

private fun slashSuggestions(
    commands: List<AgentCommand>,
    skills: List<AgentSkill>,
    typed: String
): List<SlashSuggestion> {
    val commandRows = commands.mapNotNull { command ->
        val name = command.name.removePrefix("/")
        if (name.isBlank()) return@mapNotNull null
        val copy = builtinCommandCopy[name.lowercase()]
        val suggestion = SlashSuggestion(
            name = name,
            label = copy?.first ?: name,
            description = copy?.second ?: command.description,
            // Built-in hints are English usage syntax; dsh web shows only the zh
            // description, so keep those rows calm and let unknown commands explain
            // themselves with whatever the host supplied.
            hint = if (copy == null) command.hint else null,
            skill = false
        )
        slashRank(suggestion, typed)?.let { it to suggestion }
    }.sortedBy { it.first }.take(6).map { it.second }

    val skillRows = skills.mapNotNull { skill ->
        if (skill.name.isBlank()) return@mapNotNull null
        val suggestion = SlashSuggestion(
            name = skill.name.removePrefix("/"),
            label = skill.name.removePrefix("/"),
            description = skill.description,
            hint = skill.whenToUse,
            skill = true
        )
        slashRank(suggestion, typed)?.let { it to suggestion }
    }.sortedBy { it.first }.take(6).map { it.second }

    return commandRows + skillRows
}

private fun slashRank(suggestion: SlashSuggestion, typed: String): Int? {
    if (typed.isBlank()) return 0
    val needle = typed.lowercase()
    val name = suggestion.name.lowercase()
    val label = suggestion.label.lowercase()
    return when {
        name.startsWith(needle) -> 0
        label.startsWith(needle) -> 1
        name.contains(needle) -> 2
        label.contains(needle) -> 3
        isSubsequence(name, needle) -> 4
        else -> null
    }
}

private fun isSubsequence(text: String, needle: String): Boolean {
    var at = 0
    for (char in text) {
        if (at < needle.length && char == needle[at]) at++
        if (at == needle.length) return true
    }
    return at == needle.length
}

private enum class ModelPane { Models, Efforts }

/**
 * The two composer chips each open their own list directly: the model chip picks a
 * model, the effort chip picks a reasoning level. There is no intermediate menu to
 * back out of, so a second tap always starts from the list the chip names.
 * Choosing a model installs it immediately (with the provider default effort when one
 * is declared); the effort pane offers the provider default row only for models
 * without a declared default, exactly as the native menu does.
 */
@Composable
private fun ModelPickerDialog(
    models: AgentModels?,
    onDismiss: () -> Unit,
    onSelect: (AgentModel, String?) -> Unit,
    initialPane: ModelPane
) {
    val pane = initialPane
    val currentModel = models?.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Surface)
                .padding(vertical = 12.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (pane) {
                            ModelPane.Models -> "选择模型"
                            ModelPane.Efforts -> currentModel?.name ?: "推理等级"
                        },
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when (pane) {
                            ModelPane.Models -> "切换后用于下一条消息"
                            ModelPane.Efforts -> "推理等级"
                        },
                        color = TextMuted,
                        fontSize = 10.5.sp
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = TextSecondary)
                }
            }
            Spacer(Modifier.height(6.dp))

            if (models == null) {
                Text(
                    "正在读取模型…",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
                return@Column
            }
            if (!models.routable) {
                Text(
                    "当前 provider 不可用，选择后本会话仍可能无法开始。",
                    color = Amber,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
            }

            when (pane) {
                ModelPane.Models -> Column(
                    Modifier
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp)
                ) {
                    models.groups.forEach { group ->
                        Text(
                            group.name,
                            color = TextMuted,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp)
                        )
                        group.models.forEach { model ->
                            val selected = model.provider == models.provider && model.id == models.model
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) Accent.copy(alpha = 0.08f) else Color.Transparent
                                    )
                                    .clickable {
                                        if (selected) {
                                            onDismiss()
                                        } else {
                                            onSelect(model, model.defaultEffort)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    if (selected) "✓" else " ",
                                    color = Success,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(18.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        model.name,
                                        color = if (selected) TextPrimary else TextSecondary,
                                        fontSize = 13.sp
                                    )
                                    if (!model.description.isNullOrBlank()) {
                                        Text(
                                            model.description,
                                            color = TextMuted,
                                            fontSize = 10.5.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (models.groups.isEmpty()) {
                        Text(
                            "没有可选择的模型",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                ModelPane.Efforts -> Column(
                    Modifier
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp)
                ) {
                    val model = currentModel
                    if (model == null) {
                        Text(
                            "当前模型不在目录中，请先重新选择模型。",
                            color = Amber,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    } else {
                        if (model.defaultEffort == null) {
                            EffortRow(
                                label = "默认",
                                description = "使用 provider 默认推理等级",
                                selected = models.reasoningEffort == null
                            ) {
                                onSelect(model, null)
                            }
                        }
                        if (model.efforts.isEmpty()) {
                            Text(
                                "当前模型未提供推理等级。",
                                color = TextMuted,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        } else {
                            model.efforts.forEach { effort ->
                                val label = effortLabel(effort)
                                EffortRow(
                                    label = label,
                                    description = effort.name.takeIf {
                                        !it.equals(label, ignoreCase = true) && it != effort.id
                                    },
                                    selected = models.effectiveEffort == effort.id
                                ) {
                                    onSelect(model, effort.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffortRow(
    label: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Accent.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (selected) "✓" else " ",
            color = Success,
            fontSize = 11.sp,
            modifier = Modifier.width(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (selected) TextPrimary else TextSecondary,
                fontSize = 13.sp
            )
            if (!description.isNullOrBlank()) {
                Text(description, color = TextMuted, fontSize = 10.5.sp)
            }
        }
    }
}

private fun compact(text: String, limit: Int): String {
    val normalized = text.replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= limit) normalized else normalized.take(limit) + "…"
}
