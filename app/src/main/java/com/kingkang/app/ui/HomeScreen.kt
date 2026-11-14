package com.kingkang.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.Manifest
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kingkang.app.EnvironmentUi
import com.kingkang.app.MainViewModel
import com.kingkang.app.agent.AgentActionCard
import com.kingkang.app.agent.AgentAnswer
import com.kingkang.app.agent.AgentApproval
import com.kingkang.app.agent.AgentChangedFile
import com.kingkang.app.agent.AgentChanges
import com.kingkang.app.agent.AgentChangesDiff
import com.kingkang.app.agent.AgentCommand
import com.kingkang.app.agent.AgentConnection
import com.kingkang.app.agent.AgentContextBreakdown
import com.kingkang.app.agent.AgentContextPressure
import com.kingkang.app.agent.AgentDeliverable
import com.kingkang.app.agent.AgentDraftFile
import com.kingkang.app.agent.AgentDraftImage
import com.kingkang.app.agent.AgentEffort
import com.kingkang.app.agent.AgentExport
import com.kingkang.app.agent.AgentFile
import com.kingkang.app.agent.AgentFileReference
import com.kingkang.app.agent.AgentGoal
import com.kingkang.app.agent.AgentImage
import com.kingkang.app.agent.AgentItem
import com.kingkang.app.agent.AgentJob
import com.kingkang.app.agent.AgentJobOutput
import com.kingkang.app.agent.AgentMessageFeedback
import com.kingkang.app.agent.AgentModel
import com.kingkang.app.agent.AgentModels
import com.kingkang.app.agent.AgentModelSelection
import com.kingkang.app.files.FileActions
import com.kingkang.app.agent.AgentPermissionOption
import com.kingkang.app.agent.AgentQuestion
import com.kingkang.app.agent.AgentQuestionItem
import com.kingkang.app.agent.AgentQueueItem
import com.kingkang.app.agent.AgentSchedule
import com.kingkang.app.agent.AgentScheduleHistory
import com.kingkang.app.agent.AgentSessionReference
import com.kingkang.app.agent.AgentSessionStats
import com.kingkang.app.agent.AgentSkill
import com.kingkang.app.agent.AgentSpeechCatalog
import com.kingkang.app.agent.AgentSpeechProvider
import com.kingkang.app.agent.AgentState
import com.kingkang.app.agent.AgentSubagent
import com.kingkang.app.agent.AgentTeam
import com.kingkang.app.agent.AgentTodo
import com.kingkang.app.agent.AgentTokenUsage
import com.kingkang.app.agent.AgentTurnOutlineEntry
import com.kingkang.app.agent.AgentUsage
import com.kingkang.app.agent.AgentWorkflowMember
import com.kingkang.app.agent.AssistantItem
import com.kingkang.app.agent.AttachmentStaging
import com.kingkang.app.agent.NoticeItem
import com.kingkang.app.agent.ToolItem
import com.kingkang.app.agent.ToolView
import com.kingkang.app.agent.UserItem
import com.kingkang.app.agent.WorkflowItem
import com.kingkang.app.agent.activeAtToken
import com.kingkang.app.agent.formatFileMention
import com.kingkang.app.ui.components.KkDialog
import com.kingkang.app.ui.components.KkDialogActions
import com.kingkang.app.ui.components.KkDialogTitle
import com.kingkang.app.ui.components.ScrollEdgeFade
import com.kingkang.app.ui.components.TerminalMark
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.AccentDim
import com.kingkang.app.ui.theme.AccentSoft
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.theme.Background
import com.kingkang.app.ui.theme.Blue
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import org.json.JSONObject
import java.io.File
import java.util.TimeZone
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenDrawer: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHarness: () -> Unit,
    onConfigureKey: () -> Unit
) {
    val state by vm.agentState.collectAsState()
    val environment by vm.environment.collectAsState()
    val harness by vm.harness.collectAsState()
    val rootMode by vm.rootMode.collectAsState()
    // dsh reports the reference only after its credential store has been read; before
    // that the map is empty and prompting would be premature.
    val apiKeyMissing = state.connection == AgentConnection.Ready &&
        state.credentials["DEEPSEEK_API_KEY"]?.configured == false
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Saving a produced file uses SAF so the user picks the destination; the app never
    // writes into shared storage on its own.
    var pendingSavePath by remember { mutableStateOf<String?>(null) }
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val path = pendingSavePath
        pendingSavePath = null
        if (uri != null && path != null) {
            vm.notify(FileActions.copyTo(context, File(path), uri) ?: "已保存到所选位置")
        }
    }
    // dsh-web registers conversation views as tabs above the transcript and lets the
    // reader swipe between them.
    val tabs = listOf(HomeTab.Chat, HomeTab.Trajectory)
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    var showModelPicker by rememberSaveable { mutableStateOf(false) }
    var showPermission by rememberSaveable { mutableStateOf(false) }
    var showSubagents by rememberSaveable { mutableStateOf(false) }
    var confirmPermission by rememberSaveable { mutableStateOf<String?>(null) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var showOutline by rememberSaveable { mutableStateOf(false) }
    // A turn picked before its events were loaded: keep paging history until it lands,
    // then scroll to it. Null means no jump is pending.
    var pendingTurn by remember { mutableStateOf<TurnEntry?>(null) }
    var showWorkbench by rememberSaveable { mutableStateOf(false) }
    // An attachment chip opens this dialog instead of immediately leaving the app; the
    // system viewer stays one tap away from inside it.
    var filePreview by remember { mutableStateOf<AgentFile?>(null) }
    var jobOutputFor by rememberSaveable { mutableStateOf<String?>(null) }
    var diffIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    // A quoted reply is handed to the composer as a Markdown blockquote.
    var composerSeed by remember { mutableStateOf<String?>(null) }
    // The workbench badge counts the work that is still moving or already went wrong: live
    // jobs from dsh's projection plus workflow runs folded from this transcript. A run that
    // history replay has not reached yet simply is not counted rather than guessed at.
    val workflows = remember(state.items) { state.items.filterIsInstance<WorkflowItem>() }
    val workbenchRunning = workflows.count { it.running } +
        (if (state.jobsLoaded) state.jobs.count { it.running } else 0)
    val workbenchFailed = workflows.count { it.status == "failed" } +
        (if (state.jobsLoaded) state.jobs.count { it.failed } else 0)

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

    // rc.5 exposes turnOutline as a projection; when a host does not send it the outline is
    // derived from the loaded transcript instead. Unloaded turns have no row there, which is
    // honest — there is nothing to scroll to until history is loaded.
    val turns = remember(state.items, state.turnOutline) {
        outlineEntries(state.items, state.turnOutline)
    }
    val showTurns = state.connection == AgentConnection.Ready && state.subagentView == null &&
        turns.size > 1
    fun jumpToTurn(turn: TurnEntry) {
        if (!turn.loaded) return
        focusManager.clearFocus()
        keyboard?.hide()
        following = false
        scope.launch {
            // The outline can be opened while the Trajectory tab is showing; the jump is
            // always to a message, so the pager returns to Chat first.
            pagerState.animateScrollToPage(0)
            val target = turn.itemIndex + if (state.hasMoreHistory) 1 else 0
            listState.scrollToItem(target.coerceAtLeast(0))
        }
    }
    // A turn tapped before its events were loaded pages history until it lands, then jumps.
    // The engine ignores a second request while a page is in flight, so this effect waits
    // for `turns` to change and either jumps or asks for the next page.
    LaunchedEffect(pendingTurn, turns, state.hasMoreHistory) {
        val target = pendingTurn ?: return@LaunchedEffect
        val landed = turns.firstOrNull { it.number == target.number && it.loaded }
        when {
            landed != null -> {
                pendingTurn = null
                jumpToTurn(landed)
            }
            !state.hasMoreHistory -> {
                pendingTurn = null
                vm.notify("更早的消息已加载完，仍未找到第 ${target.number} 轮")
            }
            else -> vm.loadOlder()
        }
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
            outlineAvailable = showTurns,
            // Jobs belong to one conversation, and a child transcript has no workbench of
            // its own, so the entry only exists where its list can mean something.
            workbenchAvailable = state.connection == AgentConnection.Ready &&
                state.subagentView == null && state.activeSessionId != null,
            workbenchBadge = workbenchRunning + workbenchFailed,
            workbenchBadgeError = workbenchRunning == 0 && workbenchFailed > 0,
            onOpenOutline = { showOutline = true },
            onOpenWorkbench = {
                focusManager.clearFocus()
                keyboard?.hide()
                showWorkbench = true
            },
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
                installing = harness.busy,
                installingStep = harness.step,
                onRetry = vm::restartAgent,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
        } else if (state.connection == AgentConnection.Starting) {
            AgentLoadingState(
                state = state,
                modifier = Modifier.weight(1f)
            )
        } else if (state.connection == AgentConnection.Stopped) {
            AgentStoppedState(
                state = state,
                onRetry = vm::restartAgent,
                onOpenSettings = onOpenSettings,
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
                                apiKeyMissing = apiKeyMissing,
                                listState = listState,
                                onOpenTerminal = onOpenTerminal,
                                onOpenSettings = onOpenSettings,
                                onConfigureKey = onConfigureKey,
                                onPreviewFile = { filePreview = it },
                                onQuoteText = { composerSeed = quoteMessageText(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                            HomeTab.Trajectory -> TrajectoryPane(
                                state = state,
                                onJumpToItem = { item ->
                                    following = false
                                    scope.launch {
                                        pagerState.animateScrollToPage(0)
                                        val index = state.items.indexOfFirst { it.key == item.key }
                                        if (index >= 0) {
                                            listState.scrollToItem(
                                                index + if (state.hasMoreHistory) 1 else 0
                                            )
                                        }
                                    }
                                },
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
                        apiKeyMissing = apiKeyMissing,
                        listState = listState,
                        onOpenTerminal = onOpenTerminal,
                        onOpenSettings = onOpenSettings,
                        onConfigureKey = onConfigureKey,
                        onPreviewFile = { filePreview = it },
                        onQuoteText = { composerSeed = quoteMessageText(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (state.export.running || state.export.done) {
                ExportDock(
                    export = state.export,
                    onOpen = { vm.openExport()?.let(vm::notify) },
                    onShare = { vm.shareExport()?.let(vm::notify) },
                    onSave = {
                        state.export.hostPath?.let { path ->
                            pendingSavePath = path
                            saveFile.launch(
                                state.export.name.ifBlank { "session-log.zip" }
                            )
                        }
                    },
                    onRetry = { vm.exportSession(state.export.sessionId) },
                    onDismiss = vm::dismissExport
                )
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
                stopping = state.turnStopping,
                steerable = state.subagentView == null,
                hint = state.subagentView?.takeIf { !it.continuable }?.let {
                    "只读子 Agent 不接受后续消息，它的结果会回到主对话。"
                },
                models = state.models,
                permissionMode = state.permissionMode,
                permissionOptions = state.permissionCatalog.options,
                tokenUsage = state.tokenUsage,
                pressure = state.contextPressure,
                suggestionsEnabled = !(showModelPicker || showPermission ||
                    showSubagents || confirmPermission != null || showStats ||
                    showOutline || showWorkbench || jobOutputFor != null),
                seed = composerSeed,
                onSeedConsumed = { composerSeed = null },
                onPickModel = { showModelPicker = true },
                onPickPermission = { showPermission = true },
                onShowStats = { showStats = true },
                onSend = { text, images, files -> vm.sendChat(text, images, files) },
                onSteer = { text, images, files -> vm.steerChat(text, images, files) },
                onRefreshCatalogs = vm::refreshAgentCatalogs,
                onLoadFileReferences = vm::fileReferences,
                onLoadSessionReferences = vm::sessionReferences,
                onPreviewFile = { filePreview = it },
                onSpeechCatalog = vm::speechCatalog,
                onPrepareSpeech = vm::prepareSpeech,
                onCancelSpeechPreparation = vm::cancelSpeechPreparation,
                onTranscribeSpeech = vm::transcribeSpeech,
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

    if (showModelPicker) {
        ModelPickerDialog(
            models = state.models,
            onDismiss = { showModelPicker = false },
            onSelect = { model, effort ->
                vm.selectModel(model.provider, model.id, effort)
            }
        )
    }

    if (showStats) {
        ContextStatsDialog(
            usage = state.tokenUsage,
            pressure = state.contextPressure,
            breakdown = state.contextBreakdown,
            stats = state.sessionStats,
            selection = state.modelSelection,
            onRefresh = vm::refreshStats,
            onDismiss = { showStats = false }
        )
    }

    if (showOutline) {
        val firstVisible = listState.firstVisibleItemIndex -
            (if (state.hasMoreHistory) 1 else 0)
        TurnOutlineSheet(
            turns = turns,
            current = turns.lastOrNull { it.itemIndex <= firstVisible }?.number,
            runningTurn = if (state.running) turns.lastOrNull()?.number else null,
            onJump = { turn ->
                showOutline = false
                if (turn.loaded) {
                    jumpToTurn(turn)
                } else {
                    pendingTurn = turn
                }
            },
            onDismiss = { showOutline = false }
        )
    }

    // A workbench that froze on open would keep saying "运行中" after the job died, so the
    // list refreshes on a slow tick while it is the visible surface.
    LaunchedEffect(showWorkbench) {
        if (!showWorkbench) return@LaunchedEffect
        vm.loadJobs()
        vm.loadChanges()
        // The projection is live, but a reconnection may have missed events; one read
        // refreshes the revision the goal buttons will address.
        vm.loadGoal()
        // The catalog is Host-wide rather than a projection, so it has no live stream.
        vm.loadSchedules()
        while (true) {
            delay(3_000)
            vm.loadJobs()
        }
    }

    val clipboard = LocalClipboardManager.current
    val deliveries = state.deliverables
    if (showWorkbench) {
        WorkbenchSheet(
            jobs = state.jobs,
            loaded = state.jobsLoaded,
            error = state.jobsError,
            changes = state.changes,
            changesLoaded = state.changesLoaded,
            changesLoading = state.changesLoading,
            changesError = state.changesError,
            changesReleased = state.changesReleased,
            deliveries = deliveries,
            workflows = workflows,
            sessionStats = state.sessionStats,
            tokenUsage = state.tokenUsage,
            contextPressure = state.contextPressure,
            goal = state.goal,
            goalBusy = state.goalBusy,
            goalError = state.goalError,
            onRefresh = {
                vm.loadJobs()
                vm.loadChanges()
                vm.loadGoal()
                vm.loadSchedules()
            },
            onKill = vm::killJob,
            onOpenOutput = { jobOutputFor = it },
            onOpenChange = { diffIndex = it },
            onCopyPath = { path ->
                clipboard.setText(AnnotatedString(path))
                vm.notify("已复制")
            },
            onOpenDeliverable = { path -> vm.openGuestFile(path) },
            onShareDeliverable = { path -> vm.openGuestFile(path, share = true) },
            onOpenWorkflow = { key ->
                showWorkbench = false
                // The run is already in the loaded transcript, so the jump is a plain
                // scroll; the leading "older messages" row shifts every index by one.
                val index = state.items.indexOfFirst { it.key == key }
                if (index >= 0) {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    following = false
                    scope.launch {
                        pagerState.animateScrollToPage(0)
                        runCatching {
                            listState.scrollToItem(index + if (state.hasMoreHistory) 1 else 0)
                        }
                    }
                }
            },
            onShowStats = {
                showWorkbench = false
                showStats = true
            },
            onOpenHarness = {
                showWorkbench = false
                onOpenHarness()
            },
            onCreateGoal = vm::createGoal,
            onPauseGoal = vm::pauseGoal,
            onResumeGoal = vm::resumeGoal,
            onClearGoal = vm::clearGoal,
            onEditGoal = vm::editGoal,
            schedules = state.schedules,
            schedulesLoaded = state.schedulesLoaded,
            schedulesError = state.schedulesError,
            scheduleBusy = state.scheduleBusy,
            scheduleHistory = state.scheduleHistory,
            onUpdateSchedule = { id, title, prompt, change ->
                vm.updateSchedule(id, title, prompt, change)
            },
            onDeleteSchedule = vm::deleteSchedule,
            onOpenScheduleHistory = { id -> vm.loadScheduleHistory(id) },
            onLoadMoreScheduleHistory = { id, before ->
                vm.loadScheduleHistory(id, before)
            },
            onDismissScheduleHistory = vm::dismissScheduleHistory,
            onCreateSchedule = { title, prompt, change ->
                vm.createSchedule(title, prompt, change)
            },
            onRunSchedule = vm::runSchedule,
            onDismiss = { showWorkbench = false }
        )
    }

    val openDiffIndex = diffIndex
    LaunchedEffect(openDiffIndex, state.changes?.seq) {
        if (openDiffIndex != null) vm.loadChangesDiff(openDiffIndex)
    }
    if (openDiffIndex != null) {
        val changedFile = state.changes?.files?.getOrNull(openDiffIndex)
        ChangesDiffDialog(
            file = changedFile,
            diff = state.changeDiffs[openDiffIndex],
            onRefresh = { vm.loadChangesDiff(openDiffIndex) },
            onCopyPath = {
                changedFile?.path?.let { path ->
                    clipboard.setText(AnnotatedString(path))
                    vm.notify("已复制")
                }
            },
            onOpenFile = {
                changedFile?.path?.let { path -> vm.openGuestFile(path) }
            },
            onShareFile = {
                changedFile?.path?.let { path -> vm.openGuestFile(path, share = true) }
            },
            onDismiss = { diffIndex = null }
        )
    }

    val outputJobId = jobOutputFor
    LaunchedEffect(outputJobId) {
        if (outputJobId != null) vm.loadJobOutput(outputJobId)
    }
    val output = outputJobId?.let { state.jobOutput[it] }
    LaunchedEffect(outputJobId, output?.job?.running) {
        // Reading a live job's output is a follow, not a snapshot: keep appending while it
        // runs. The bridge returns after a short quiet window, so this is not a busy loop.
        if (outputJobId == null || output?.job?.running != true) return@LaunchedEffect
        while (true) {
            delay(3_000)
            vm.loadJobOutput(outputJobId, append = true)
        }
    }
    if (outputJobId != null) {
        JobOutputDialog(
            job = output?.job ?: state.jobs.firstOrNull { it.id == outputJobId },
            output = output,
            onRefresh = { vm.loadJobOutput(outputJobId) },
            onDismiss = { jobOutputFor = null }
        )
    }

    LaunchedEffect(showSubagents) {
        if (showSubagents) vm.refreshSubagents()
    }

    if (showSubagents) {
        SubagentPanelDialog(
            subagents = state.subagents,
            team = state.agentTeam,
            parentAvailable = state.subagentsParentAvailable,
            depth = state.subagentDepth,
            onDismiss = { showSubagents = false },
            onRefresh = vm::refreshSubagents,
            onToggleTeam = vm::setAgentTeamEnabled,
            onOpen = { subagent ->
                showSubagents = false
                vm.openSubagent(subagent.parentId, subagent)
            }
        )
    }

    // Reading an attachment stays in the app when it can: images, text and source files
    // render here, and the dialog offers the system viewer for everything else.
    filePreview?.let { file ->
        AttachmentPreviewDialog(
            file = file,
            resolveFile = vm::attachmentHostFile,
            onOpenExternal = { target ->
                filePreview = null
                vm.openAttachment(target)
            },
            onShare = { target ->
                filePreview = null
                vm.shareAttachment(target)
            },
            onDismiss = { filePreview = null }
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
    apiKeyMissing: Boolean,
    listState: LazyListState,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    onConfigureKey: () -> Unit,
    onPreviewFile: (AgentFile) -> Unit,
    onQuoteText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    Box(modifier) {
        if (state.items.isEmpty() && state.transcriptLoading) {
            // A session's history is on its way. Showing the first-run page here would be
            // wrong and visibly stuck; a quiet line is honest until the transcript lands.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在读取对话…", color = TextMuted, fontSize = 12.sp)
            }
        } else if (state.items.isEmpty()) {
            EmptyState(
                environment = environment,
                rootMode = rootMode,
                modelRoutable = state.models?.routable ?: true,
                apiKeyMissing = apiKeyMissing,
                onOpenTerminal = onOpenTerminal,
                onOpenSettings = onOpenSettings,
                onConfigureKey = onConfigureKey,
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
                onQuote = onQuoteText,
                onLoadImage = { image ->
                    state.activeSessionId?.let { vm.loadAgentImage(it, image) }
                },
                onBranch = { item -> item.seq?.let(vm::forkAtMessage) },
                onRate = { id, rating, category, note ->
                    vm.rateMessage(id, rating, category, note)
                },
                onClearFeedback = vm::clearMessageFeedback,
                onOpenFile = onPreviewFile,
                onActionCard = vm::actOnActionCard,
                onContinue = vm::continueConversation
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

/** One user prompt in the turn outline: where it starts, and how much work followed. */
private data class TurnEntry(
    val number: Int,
    val itemIndex: Int,
    val preview: String,
    val steps: Int,
    val tools: Int,
    val response: String = "",
    val time: Long? = null,
    val loaded: Boolean = itemIndex >= 0
)

/**
 * The navigator prefers dsh's `turnOutline` projection, which also covers turns whose
 * events have scrolled out of the loaded window: those rows stay visible but cannot be
 * jumped to until history is loaded. Without the projection it falls back to the loaded
 * transcript, which is what an older host can offer.
 */
private fun outlineEntries(
    items: List<AgentItem>,
    projection: List<AgentTurnOutlineEntry>
): List<TurnEntry> {
    if (projection.isEmpty()) return turnEntries(items)
    val sorted = projection.sortedBy { it.turn }
    return sorted.mapIndexed { position, entry ->
        val nextSeq = sorted.getOrNull(position + 1)?.seq
        val index = entry.seq?.let { seq -> startIndexFor(items, seq, nextSeq) } ?: -1
        val (steps, tools) = if (index >= 0) countTurnWork(items, index) else 0 to 0
        TurnEntry(
            number = entry.turn,
            itemIndex = index,
            preview = entry.prompt.lineSequence()
                .firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                .ifBlank { "第 ${entry.turn} 轮" }
                .take(80),
            steps = steps,
            tools = tools,
            response = entry.response,
            time = (items.getOrNull(index) as? UserItem)?.time,
            loaded = index >= 0
        )
    }
}

/**
 * Where one outline turn starts in the loaded transcript. Requiring a *user* prompt at or
 * after the turn's seq, and before the next turn's seq, stops a turn that fell out of the
 * loaded window from snapping to a later prompt that happens to share the range.
 */
private fun startIndexFor(items: List<AgentItem>, seq: Long, nextSeq: Long?): Int =
    items.indexOfFirst { item ->
        val itemSeq = item.seq
        item is UserItem && itemSeq != null && itemSeq >= seq &&
            (nextSeq == null || itemSeq < nextSeq)
    }

/** Steps and tool calls between one turn's prompt and the next turn's prompt. */
private fun countTurnWork(items: List<AgentItem>, start: Int): Pair<Int, Int> {
    var steps = 0
    var tools = 0
    var index = start
    while (index < items.size) {
        val item = items[index]
        if (index > start && item is UserItem) break
        if (item is AssistantItem && (item.text.isNotBlank() || item.reasoning.isNotBlank())) {
            steps++
        } else if (item is ToolItem) {
            tools++
        }
        index++
    }
    return steps to tools
}

private fun turnEntries(items: List<AgentItem>): List<TurnEntry> {
    val starts = items.withIndex().filter { (_, item) -> item is UserItem }
    if (starts.isEmpty()) return emptyList()
    return starts.mapIndexed { position, start ->
        val user = start.value as UserItem
        val end = starts.getOrNull(position + 1)?.index ?: items.size
        val slice = items.subList(start.index, end)
        TurnEntry(
            number = position + 1,
            itemIndex = start.index,
            preview = user.text.lineSequence()
                .firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                .ifBlank {
                    when {
                        user.images.isNotEmpty() -> "[${user.images.size} 张图片]"
                        user.files.isNotEmpty() -> user.files.joinToString("、") { it.name }
                        else -> "第 ${position + 1} 轮"
                    }
                }
                .take(80),
            steps = slice.count {
                it is AssistantItem && (it.text.isNotBlank() || it.reasoning.isNotBlank())
            },
            tools = slice.count { it is ToolItem },
            time = user.time
        )
    }
}

/**
 * Jump list for a long conversation. dsh 0.2.1 has no `turnOutline` projection, so the
 * rows are derived from loaded prompts; the entry is hidden until a second turn exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TurnOutlineSheet(
    turns: List<TurnEntry>,
    current: Int?,
    runningTurn: Int?,
    onJump: (TurnEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
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
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "轮次导航",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text("${turns.size} 轮", color = TextMuted, fontSize = 10.5.sp)
            }
            ScrollEdgeFade(
                canScrollForward = listState.canScrollForward,
                modifier = Modifier.heightIn(max = 460.dp)
            ) {
            LazyColumn(
                Modifier.fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(turns, key = { it.number }) { turn ->
                    val selected = turn.number == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // Unloaded turns are still tappable: the jump pages history
                            // until the turn lands, then scrolls to it.
                            .clickable { onJump(turn) }
                            .padding(horizontal = 20.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        selected -> Accent
                                        turn.loaded -> SurfaceVariant
                                        else -> SurfaceVariant.copy(alpha = 0.5f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                turn.number.toString(),
                                color = if (selected) OnAccent else TextSecondary,
                                fontSize = 9.5.sp
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                turn.preview,
                                color = when {
                                    !turn.loaded -> TextMuted
                                    selected -> TextPrimary
                                    else -> TextSecondary
                                },
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val meta = if (!turn.loaded) {
                                "未加载 · 点击加载并跳转"
                            } else {
                                buildString {
                                    when {
                                        turn.number == runningTurn -> append("进行中")
                                        turn.response.isNotBlank() -> append("已完成")
                                        else -> append("已记录")
                                    }
                                    turn.time?.let {
                                        formatTimeOfDay(it).takeIf { clock -> clock.isNotBlank() }?.let { clock ->
                                            append(" · $clock")
                                        }
                                    }
                                    if (turn.steps > 0) append(" · ${turn.steps} 步")
                                    if (turn.tools > 0) append(" · ${turn.tools} 个工具")
                                }
                            }
                            if (meta.isNotEmpty()) {
                                Text(meta, color = TextMuted, fontSize = 9.5.sp)
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

/**
 * The session's background work. Long-running shells are dsh jobs rather than transcript
 * rows, so the workbench reads the job projection: name, state, start time, elapsed time
 * and output size, with a stop action and a scrollback viewer. The same surface carries
 * the turn's recorded file changes and the files the agent declared with `present`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkbenchSheet(
    jobs: List<AgentJob>,
    loaded: Boolean,
    error: String?,
    changes: AgentChanges?,
    changesLoaded: Boolean,
    changesLoading: Boolean,
    changesError: String?,
    changesReleased: Boolean,
    deliveries: List<AgentDeliverable>,
    workflows: List<WorkflowItem>,
    sessionStats: AgentSessionStats,
    tokenUsage: AgentTokenUsage,
    contextPressure: AgentContextPressure,
    goal: AgentGoal?,
    goalBusy: Boolean,
    goalError: String?,
    onRefresh: () -> Unit,
    onKill: (String) -> Unit,
    onOpenOutput: (String) -> Unit,
    onOpenChange: (Int) -> Unit,
    onCopyPath: (String) -> Unit,
    onOpenDeliverable: (String) -> Unit,
    onShareDeliverable: (String) -> Unit,
    onOpenWorkflow: (String) -> Unit,
    onShowStats: () -> Unit,
    onOpenHarness: () -> Unit,
    onCreateGoal: (String) -> Unit,
    onPauseGoal: () -> Unit,
    onResumeGoal: () -> Unit,
    onClearGoal: () -> Unit,
    onEditGoal: (String, Int?) -> Unit,
    schedules: List<AgentSchedule>,
    schedulesLoaded: Boolean,
    schedulesError: String?,
    scheduleBusy: Boolean,
    scheduleHistory: Map<String, AgentScheduleHistory>,
    onUpdateSchedule: (String, String?, String?, JSONObject?) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onOpenScheduleHistory: (String) -> Unit,
    onLoadMoreScheduleHistory: (String, String?) -> Unit,
    onDismissScheduleHistory: (String) -> Unit,
    onCreateSchedule: (String, String, JSONObject?) -> Unit,
    onRunSchedule: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Elapsed time only moves on screen if something ticks; a running job earns that tick.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var creatingGoal by rememberSaveable { mutableStateOf(false) }
    var editingGoal by rememberSaveable { mutableStateOf(false) }
    var clearingGoal by rememberSaveable { mutableStateOf(false) }
    var creatingSchedule by rememberSaveable { mutableStateOf(false) }
    var editingScheduleId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingScheduleId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailScheduleId by rememberSaveable { mutableStateOf<String?>(null) }
    // Rotation while the sheet is open keeps the reader where they were.
    val listState = rememberLazyListState()
    val orderedSchedules = remember(schedules) {
        schedules.sortedWith(
            compareBy<AgentSchedule> { !it.active }
                .thenBy { it.scheduledAt ?: Long.MAX_VALUE }
        )
    }
    // Runs fold in start order; the workbench lists the newest run first, as a reader
    // opening it mid-task expects to see the live one.
    val orderedWorkflows = remember(workflows) { workflows.asReversed() }
    val anyRunning = jobs.any { it.running }
    // Live rows first in start order, then settled rows newest-first, as dsh-web orders them.
    val ordered = remember(jobs) {
        jobs.sortedWith { left, right ->
            when {
                left.running != right.running -> if (left.running) -1 else 1
                left.running -> left.startedAt.compareTo(right.startedAt)
                else -> {
                    val byFinish = (right.finishedAt ?: right.startedAt)
                        .compareTo(left.finishedAt ?: left.startedAt)
                    if (byFinish != 0) byFinish else left.startedAt.compareTo(right.startedAt)
                }
            }
        }
    }
    LaunchedEffect(anyRunning) {
        while (anyRunning) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
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
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "工作台",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    when {
                        jobs.any { it.running } -> buildString {
                            append(jobs.count { it.running }).append(" 个运行中")
                            val failed = jobs.count { it.failed }
                            if (failed > 0) append(" · ").append(failed).append(" 个失败")
                        }
                        jobs.any { it.failed } -> "${jobs.count { it.failed }} 个失败"
                        else -> "${jobs.size} 个任务"
                    },
                    color = TextMuted,
                    fontSize = 10.5.sp
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新工作台",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            ScrollEdgeFade(
                canScrollForward = listState.canScrollForward,
                modifier = Modifier.heightIn(max = 480.dp)
            ) {
            LazyColumn(
                Modifier.fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (goal == null) {
                    item(key = "goal-header") {
                        WorkbenchSectionAction(
                            title = "长期目标",
                            meta = null,
                            actionLabel = "新建",
                            onAction = { creatingGoal = true }
                        )
                    }
                    item(key = "goal-empty") {
                        SectionHint(
                            "还没有长期目标。点「新建」或发送 /goal 设置后，" +
                                "这里会显示进度，并可暂停或调整。"
                        )
                    }
                } else {
                    item(key = "goal-header") {
                        WorkbenchSection(
                            title = "长期目标",
                            meta = goalPhaseLabel(goal.phase)
                        )
                    }
                    item(key = "goal-card") {
                        GoalCard(
                            goal = goal,
                            busy = goalBusy,
                            onPause = onPauseGoal,
                            onResume = onResumeGoal,
                            onEdit = { editingGoal = true },
                            onClear = { clearingGoal = true }
                        )
                    }
                }
                goalError?.let { failure ->
                    item(key = "goal-error") {
                        SectionHint("目标同步失败：$failure", isError = true)
                    }
                }

                item(key = "schedules-header") {
                    WorkbenchSectionAction(
                        title = "定时任务",
                        meta = if (schedules.isEmpty()) null else {
                            val active = schedules.count { it.active }
                            if (active == schedules.size) "${schedules.size} 个"
                            else "$active 个进行中 · ${schedules.size} 个"
                        },
                        actionLabel = "新建",
                        onAction = { creatingSchedule = true }
                    )
                }
                when {
                    !schedulesLoaded && schedules.isEmpty() -> item(key = "schedules-loading") {
                        SectionLoading()
                    }
                    schedules.isEmpty() -> item(key = "schedules-empty") {
                        SectionHint(
                            "还没有定时任务。点「新建」填写时间和内容，保存后会直接写入 dsh；" +
                                "也可以直接在对话里说，例如「每天 9 点提醒我看日志」。"
                        )
                    }
                    else -> items(orderedSchedules, key = { "schedule-${it.id}" }) { schedule ->
                        ScheduleRow(
                            schedule = schedule,
                            onClick = {
                                detailScheduleId = schedule.id
                                onOpenScheduleHistory(schedule.id)
                            }
                        )
                        WorkbenchDivider()
                    }
                }
                schedulesError?.let { failure ->
                    item(key = "schedules-error") {
                        SectionHint("读取定时任务失败：$failure", isError = true)
                    }
                }

                item(key = "jobs-header") {
                    WorkbenchSection(
                        title = "后台任务",
                        meta = if (jobs.isEmpty()) null else "${jobs.size} 个"
                    )
                }
                when {
                    !loaded && jobs.isEmpty() -> item(key = "jobs-loading") {
                        SectionLoading()
                    }
                    jobs.isEmpty() -> item(key = "jobs-empty") {
                        SectionHint("没有后台任务。耗时较长的命令会在后台运行，可在这里查看输出或停止。")
                    }
                    else -> items(ordered, key = { "job-${it.id}" }) { job ->
                        JobRow(
                            job = job,
                            now = now,
                            onKill = { onKill(job.id) },
                            onOutput = { onOpenOutput(job.id) }
                        )
                        WorkbenchDivider()
                    }
                }
                error?.let {
                    item(key = "jobs-error") {
                        SectionHint("读取任务失败：$it", isError = true)
                    }
                }

                item(key = "workflows-header") {
                    WorkbenchSection(
                        title = "工作流",
                        meta = when {
                            workflows.isEmpty() -> null
                            workflows.any { it.running } ->
                                "${workflows.count { it.running }} 个运行中 · ${workflows.size} 个"
                            else -> "${workflows.size} 个"
                        }
                    )
                }
                if (workflows.isEmpty()) {
                    item(key = "workflows-empty") {
                        SectionHint(
                            "还没有工作流。助手把复杂任务拆分给多个子代理时，" +
                                "这里会汇总每个成员的状态；完整过程在对话中。"
                        )
                    }
                } else {
                    items(orderedWorkflows, key = { "workflow-${it.runId}" }) { run ->
                        WorkflowRow(item = run, onOpen = { onOpenWorkflow(run.key) })
                        WorkbenchDivider()
                    }
                }

                item(key = "changes-header") {
                    WorkbenchSection(
                        title = "本会话变更",
                        meta = when {
                            changesLoading -> "读取中…"
                            changes == null -> null
                            else -> buildString {
                                if (changes.added > 0) append("+").append(changes.added)
                                if (changes.deleted > 0) {
                                    if (isNotEmpty()) append(" ")
                                    append("−").append(changes.deleted)
                                }
                            }.ifEmpty { null }
                        }
                    )
                }
                val changedFiles = changes?.files.orEmpty()
                when {
                    changesError != null && changes == null -> item(key = "changes-error") {
                        SectionHint("读取变更失败：$changesError", isError = true)
                    }
                    changes == null && !changesLoaded -> item(key = "changes-loading") {
                        SectionLoading()
                    }
                    changesReleased && changedFiles.isEmpty() -> item(key = "changes-released") {
                        SectionHint("dsh 重启后，之前轮次的逐文件对比已不可用。")
                    }
                    changedFiles.isEmpty() -> item(key = "changes-empty") {
                        SectionHint("本会话还没有记录文件变更。")
                    }
                    else -> itemsIndexed(
                        changedFiles,
                        key = { index, file -> "change-$index-${file.path}" }
                    ) { index, file ->
                        ChangedFileRow(file = file, onOpen = { onOpenChange(index) })
                        WorkbenchDivider()
                    }
                }

                item(key = "deliveries-header") {
                    WorkbenchSection(
                        title = "交付物",
                        meta = if (deliveries.isEmpty()) null else "${deliveries.size} 个"
                    )
                }
                if (deliveries.isEmpty()) {
                    item(key = "deliveries-empty") {
                        SectionHint("还没有交付文件。")
                    }
                } else {
                    items(deliveries, key = { "delivery-${it.path}" }) { file ->
                        DeliverableRow(
                            file = file,
                            onOpen = { onOpenDeliverable(file.path) },
                            onShare = { onShareDeliverable(file.path) },
                            onCopy = { onCopyPath(file.path) }
                        )
                        WorkbenchDivider()
                    }
                }

                item(key = "stats-header") {
                    WorkbenchSectionAction(
                        title = "本会话统计",
                        meta = when {
                            sessionStats.hasData ->
                                "${sessionStats.turns} 轮 · ${sessionStats.steps} 步"
                            else -> null
                        },
                        actionLabel = "详细",
                        onAction = onShowStats
                    )
                }
                if (!sessionStats.hasData && !tokenUsage.hasData && !contextPressure.hasData) {
                    item(key = "stats-empty") {
                        SectionHint("完成一轮对话后，这里会显示轮次、上下文占用和 token 用量。")
                    }
                } else {
                    item(key = "stats-summary") {
                        WorkbenchStats(
                            stats = sessionStats,
                            usage = tokenUsage,
                            pressure = contextPressure
                        )
                    }
                }

                item(key = "workbench-advanced") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "终端、文件浏览与完整网页界面在 Harness 中。",
                            modifier = Modifier.weight(1f),
                            color = TextMuted,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                        TextButton(
                            onClick = onOpenHarness,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("打开 Harness", color = Accent, fontSize = 11.5.sp)
                        }
                    }
                }
            }
            }
            if (creatingGoal) {
                GoalCreateDialog(
                    onDismiss = { creatingGoal = false },
                    onCreate = { objective ->
                        creatingGoal = false
                        onCreateGoal(objective)
                    }
                )
            }
            if (editingGoal && goal != null) {
                GoalEditDialog(
                    goal = goal,
                    busy = goalBusy,
                    onDismiss = { editingGoal = false },
                    onSave = { objective, rounds ->
                        editingGoal = false
                        onEditGoal(objective, rounds)
                    }
                )
            }
            if (clearingGoal && goal != null) {
                GoalClearDialog(
                    goal = goal,
                    busy = goalBusy,
                    onDismiss = { clearingGoal = false },
                    onConfirm = {
                        clearingGoal = false
                        onClearGoal()
                    }
                )
            }
            if (creatingSchedule) {
                ScheduleFormDialog(
                    initial = null,
                    busy = false,
                    onDismiss = { creatingSchedule = false },
                    onSave = { draft ->
                        creatingSchedule = false
                        onCreateSchedule(draft.title, draft.prompt, scheduleChange(draft))
                    }
                )
            }
            schedules.firstOrNull { it.id == editingScheduleId }?.let { schedule ->
                ScheduleFormDialog(
                    initial = schedule,
                    busy = scheduleBusy,
                    onDismiss = { editingScheduleId = null },
                    onSave = { draft ->
                        editingScheduleId = null
                        onUpdateSchedule(
                            schedule.id,
                            draft.title,
                            draft.prompt,
                            scheduleChange(draft)
                        )
                    }
                )
            }
            schedules.firstOrNull { it.id == detailScheduleId }?.let { schedule ->
                ScheduleDetailDialog(
                    schedule = schedule,
                    history = scheduleHistory[schedule.id],
                    busy = scheduleBusy,
                    onDismiss = {
                        detailScheduleId = null
                        onDismissScheduleHistory(schedule.id)
                    },
                    onEdit = {
                        detailScheduleId = null
                        onDismissScheduleHistory(schedule.id)
                        editingScheduleId = schedule.id
                    },
                    onRun = { onRunSchedule(schedule.id) },
                    onDelete = {
                        detailScheduleId = null
                        onDismissScheduleHistory(schedule.id)
                        deletingScheduleId = schedule.id
                    },
                    onLoadMore = {
                        onLoadMoreScheduleHistory(
                            schedule.id,
                            scheduleHistory[schedule.id]?.nextBefore
                        )
                    }
                )
            }
            schedules.firstOrNull { it.id == deletingScheduleId }?.let { schedule ->
                ScheduleDeleteDialog(
                    schedule = schedule,
                    busy = scheduleBusy,
                    onDismiss = { deletingScheduleId = null },
                    onConfirm = {
                        deletingScheduleId = null
                        detailScheduleId = null
                        onDismissScheduleHistory(schedule.id)
                        onDeleteSchedule(schedule.id)
                    }
                )
            }
        }
    }
}

/**
 * dsh's own phase names, labelled in the workbench. Unknown phases from a newer dsh show
 * through unchanged rather than pretending to be one of the known ones.
 */
private fun goalPhaseLabel(phase: String): String = when (phase) {
    "active" -> "进行中"
    "paused" -> "已暂停"
    "blocked" -> "已阻塞"
    "complete" -> "已完成"
    else -> phase
}

/**
 * The long-running objective of this conversation. dsh owns the phase and revision; the
 * card reflects them and every action addresses the revision currently on screen, so a
 * stale tap is rejected by dsh instead of overwriting a newer change.
 */
@Composable
private fun GoalCard(
    goal: AgentGoal,
    busy: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEdit: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Text(
            goal.objective,
            color = TextPrimary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Row(
            Modifier.padding(top = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (goal.maxGoalRounds > 0) "${goal.roundsStarted} / ${goal.maxGoalRounds} 轮"
                else "未限制轮次",
                color = TextMuted,
                fontSize = 10.sp
            )
            if (goal.revision > 0) {
                Spacer(Modifier.width(10.dp))
                Text("第 ${goal.revision} 版", color = TextMuted, fontSize = 10.sp)
            }
        }
        if (goal.phase == "active" && goal.activation == "disarmed") {
            Text(
                "自动推进已停止，恢复后会继续",
                modifier = Modifier.padding(top = 4.dp),
                color = Amber,
                fontSize = 10.sp
            )
        }
        if (goal.phase == "blocked" && !goal.blockedMessage.isNullOrBlank()) {
            Text(
                goal.blockedMessage,
                modifier = Modifier.padding(top = 5.dp),
                color = Amber,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val canResume = goal.phase == "paused" || goal.phase == "blocked" ||
                (goal.phase == "active" && goal.activation == "disarmed")
            when {
                canResume -> GoalAction(
                    label = if (busy) "恢复中…" else "恢复",
                    enabled = !busy,
                    color = Accent,
                    onClick = onResume
                )
                goal.phase == "active" -> GoalAction(
                    label = if (busy) "暂停中…" else "暂停",
                    enabled = !busy,
                    color = Accent,
                    onClick = onPause
                )
            }
            if (goal.phase != "complete") {
                GoalAction(label = "编辑", enabled = !busy, onClick = onEdit)
            }
            GoalAction(label = "清除", enabled = !busy, color = Red, onClick = onClear)
        }
    }
}

@Composable
private fun GoalAction(
    label: String,
    enabled: Boolean = true,
    color: Color = TextSecondary,
    onClick: () -> Unit
) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = if (enabled) color else TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
    )
}

/**
 * Creates a goal through dsh's `/goal` command, since the goal domain has no create
 * RPC. The objective is collapsed to one line: the command receives the rest of the
 * composer line verbatim, so a pasted multi-line objective would otherwise be parsed
 * as several commands.
 */
@Composable
private fun GoalCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var objective by rememberSaveable { mutableStateOf("") }
    val canCreate = objective.isNotBlank()
    KkDialog(onDismiss = onDismiss) {
        KkDialogTitle("新建长期目标", onDismiss = onDismiss)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = objective,
            onValueChange = { if (it.length <= 4000) objective = it },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceRaised)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = TextPrimary,
                fontSize = 14.sp,
                platformStyle = com.kingkang.app.ui.components.CompactInputPlatformStyle,
                lineHeight = 20.sp
            ),
            cursorBrush = SolidColor(Accent),
            maxLines = 6,
            decorationBox = { inner ->
                if (objective.isEmpty()) {
                    Text(
                        "例如：完成本地服务的漏洞排查并给出修复建议",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
                inner()
            }
        )
        Text(
            "创建后可在目标卡片里调整轮次上限或暂停。",
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
            color = TextMuted,
            fontSize = 10.5.sp
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
            TextButton(
                onClick = { onCreate(objective.trim()) },
                enabled = canCreate
            ) {
                Text("创建", color = if (canCreate) Accent else TextMuted)
            }
        }
    }
}

/** Replaces the objective and/or the round cap; dsh increments the revision on save. */
@Composable
private fun GoalEditDialog(
    goal: AgentGoal,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int?) -> Unit
) {
    var objective by rememberSaveable(goal.id) { mutableStateOf(goal.objective) }
    var rounds by rememberSaveable(goal.id) {
        mutableStateOf(if (goal.maxGoalRounds > 0) goal.maxGoalRounds.toString() else "")
    }
    val parsedRounds = rounds.trim().toIntOrNull()
    val roundsValid = rounds.isBlank() || (parsedRounds != null && parsedRounds >= 1)
    val canSave = objective.isNotBlank() && roundsValid && !busy
    KkDialog(onDismiss = onDismiss) {
        KkDialogTitle("编辑目标", onDismiss = onDismiss)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = objective,
            onValueChange = { if (it.length <= 4000) objective = it },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceRaised)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = TextPrimary,
                fontSize = 14.sp,
                platformStyle = com.kingkang.app.ui.components.CompactInputPlatformStyle,
                lineHeight = 20.sp
            ),
            cursorBrush = SolidColor(Accent),
            maxLines = 6
        )
        Spacer(Modifier.height(10.dp))
        Text("最大轮次", color = TextMuted, fontSize = 10.5.sp)
        BasicTextField(
            value = rounds,
            onValueChange = { value ->
                rounds = value.filter(Char::isDigit).take(4)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceRaised)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = TextPrimary,
                fontSize = 13.sp,
                platformStyle = com.kingkang.app.ui.components.CompactInputPlatformStyle,
                lineHeight = 13.sp,
            ),
            cursorBrush = SolidColor(Accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            decorationBox = { inner ->
                if (rounds.isEmpty()) {
                    Text("留空则保持当前轮次上限", color = TextMuted, fontSize = 12.sp)
                }
                inner()
            }
        )
        if (!roundsValid) {
            Text(
                "轮次需为 1 以上的整数",
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                color = Red,
                fontSize = 10.sp
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
            TextButton(
                onClick = { onSave(objective.trim(), parsedRounds) },
                enabled = canSave
            ) {
                Text("保存", color = if (canSave) Accent else TextMuted)
            }
        }
    }
}

/** Clearing leaves a tombstone in dsh's log, but the card disappears; confirm first. */
@Composable
private fun GoalClearDialog(
    goal: AgentGoal,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "清除目标",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        },
        text = {
            Text(
                "清除后自动推进也会停止，这条目标不再出现在工作台。\n\n${goal.objective}",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text("清除", color = if (busy) TextMuted else Red)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

// ---------------------------------------------------------------------------------------
// Scheduled reminders
// ---------------------------------------------------------------------------------------

/** One reminder's weekly rule in the workbench's compact Chinese form. */
private fun scheduleWeekdays(days: List<Int>): String {
    if (days.isEmpty()) return "—"
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    return days.sorted().mapNotNull { day -> names.getOrNull(day - 1) }
        .joinToString("、") { "周$it" }
}

private fun scheduleZoneSuffix(schedule: AgentSchedule): String =
    schedule.timeZone?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""

/** dsh's frequency for one record, phrased the way its own task list phrases it. */
private fun scheduleFrequency(schedule: AgentSchedule): String = when (schedule.kind) {
    "after" -> "一次性 · ${formatDuration((schedule.afterSeconds ?: 0L) * 1_000L)}后"
    "at" -> "一次性 · ${formatScheduledAt(schedule.scheduledAt)}"
    "every" -> "每 ${formatDuration((schedule.everySeconds ?: 0L) * 1_000L)}"
    "daily" -> "每天 ${schedule.time ?: "—"}${scheduleZoneSuffix(schedule)}"
    "weekly" -> "每周 ${scheduleWeekdays(schedule.weekdays)} " +
        "${schedule.time ?: "—"}${scheduleZoneSuffix(schedule)}"
    "cron" -> "cron ${schedule.expression ?: "—"}${scheduleZoneSuffix(schedule)}"
    "rrule" -> {
        // dsh stores the whole RFC 5545 line; the rule after RRULE: is the readable part.
        val raw = schedule.expression
        val rule = if (raw == null) null else raw.substringAfter("RRULE:", raw)
        "重复规则 · ${rule?.takeIf { it.isNotBlank() } ?: "自定义"}"
    }
    else -> schedule.kind
}

/** The row label for a task that no longer runs; `null` while it is active. */
private fun scheduleStateLabel(schedule: AgentSchedule): String? = when (schedule.status) {
    "paused" -> "已暂停"
    "ended", "inactive" -> "已结束"
    else -> null
}

private fun scheduleRunMeta(schedule: AgentSchedule): String {
    if (!schedule.active) {
        val label = scheduleStateLabel(schedule) ?: "已结束"
        return schedule.lastDeliveredAt?.let { "$label · 上次投递 ${formatScheduledAt(it)}" }
            ?: label
    }
    val next = schedule.scheduledAt
        ?: return schedule.lastDeliveredAt?.let { "上次投递 ${formatScheduledAt(it)}" } ?: ""
    val prefix = if (schedule.kind == "after" || schedule.kind == "at") "计划" else "下次运行"
    return "$prefix ${formatScheduledAt(next)}"
}

/** One reminder row: title, its rule, and when it next runs. Tapping opens the detail. */
@Composable
private fun ScheduleRow(schedule: AgentSchedule, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                schedule.title.ifBlank {
                    schedule.prompt.lineSequence().firstOrNull().orEmpty()
                        .ifBlank { "定时任务" }
                },
                modifier = Modifier.weight(1f, fill = false),
                color = if (schedule.active) TextPrimary else TextMuted,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!schedule.active) {
                Spacer(Modifier.width(6.dp))
                Text(
                    scheduleStateLabel(schedule) ?: "已结束",
                    color = TextMuted,
                    fontSize = 9.5.sp
                )
            }
        }
        Text(
            scheduleFrequency(schedule),
            modifier = Modifier.padding(top = 2.dp),
            color = TextSecondary,
            fontSize = 10.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val meta = scheduleRunMeta(schedule)
        if (meta.isNotEmpty()) {
            Text(
                meta,
                modifier = Modifier.padding(top = 1.dp),
                color = TextMuted,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A section header that also carries one text action, used by the reminder list's 新建. */
@Composable
private fun WorkbenchSectionAction(
    title: String,
    meta: String?,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        if (!meta.isNullOrEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(meta, color = TextMuted, fontSize = 9.5.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(
            actionLabel,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            color = Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * The values a reminder form gathers. `keep` leaves the stored timing alone, which is the
 * only timing option dsh offers for an edit that does not restate the rule.
 */
private data class ScheduleDraft(
    val title: String,
    val prompt: String,
    /** keep | after | every | daily | weekly | cron */
    val kind: String,
    val time: String = "09:00",
    val timeZone: String = TimeZone.getDefault().id,
    val weekdays: List<Int> = listOf(1, 2, 3, 4, 5),
    val expression: String = "0 9 * * 1-5",
    val afterMinutes: Int = 60,
    val everyHours: Int = 1
)

private val SCHEDULE_TIME = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
private val SCHEDULE_CRON = Regex("^\\S+(\\s+\\S+){4}$")

/** The `schedule/update` timing object for [draft], or null when timing stays as stored. */
private fun scheduleChange(draft: ScheduleDraft): JSONObject? = when (draft.kind) {
    "after" -> JSONObject()
        .put("kind", "at")
        .put(
            "at",
            java.time.Instant.now()
                .plusSeconds(draft.afterMinutes.coerceAtLeast(1) * 60L)
                .toString()
        )
    "every" -> JSONObject()
        .put("kind", "every")
        .put("every_seconds", draft.everyHours * 3_600)
    "daily" -> JSONObject()
        .put("kind", "daily")
        .put(
            "daily",
            JSONObject().put("time", scheduleTimeId(draft.time))
                .put("time_zone", scheduleZoneId(draft.timeZone))
        )
    "weekly" -> JSONObject()
        .put("kind", "weekly")
        .put(
            "weekly",
            JSONObject()
                .put("time", scheduleTimeId(draft.time))
                .put("time_zone", scheduleZoneId(draft.timeZone))
                .put("weekdays", org.json.JSONArray(draft.weekdays.sorted()))
        )
    "cron" -> JSONObject()
        .put("kind", "cron")
        .put(
            "cron",
            JSONObject()
                .put("expression", draft.expression.trim())
                .put("time_zone", scheduleZoneId(draft.timeZone))
        )
    else -> null
}

/** dsh wants `HH:mm:ss`; the form's picker offers `HH:mm`. */
private fun scheduleTimeId(value: String): String {
    val time = value.trim()
    return when {
        Regex("^\\d{2}:\\d{2}$").matches(time) -> "$time:00"
        else -> time
    }
}

/**
 * dsh's schedule schema wants `UTC` or an IANA Area/Location zone. Android reports fixed
 * offsets as `GMT`, `GMT+08:00` or `Etc/GMT`, which the Host rejects; translate them to
 * an equivalent IANA zone (Etc/GMT signs are inverted by convention).
 */
private fun scheduleZoneId(value: String): String {
    val zone = value.trim()
    if (zone.isEmpty()) return "UTC"
    if (zone.equals("UTC", true) || zone.equals("Etc/UTC", true) ||
        zone.equals("GMT", true) || zone.equals("Etc/GMT", true)
    ) {
        return "UTC"
    }
    if (zone.contains('/')) return zone
    val match = Regex("^(?:GMT|UTC)([+-])(\\d{1,2})(?::?(\\d{2}))?$").find(zone)
        ?: return "UTC"
    val hours = match.groupValues[2].toIntOrNull() ?: 0
    val minutes = match.groupValues[3].toIntOrNull() ?: 0
    if (hours == 0 && minutes == 0) return "UTC"
    if (minutes != 0 || hours > 14) return "UTC"
    val sign = if (match.groupValues[1] == "+") "-" else "+"
    return "Etc/GMT$sign$hours"
}

/**
 * Title, instruction and timing. An edit can keep the stored rule; a new reminder has to
 * state one because there is no record to inherit it from.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleFormDialog(
    initial: AgentSchedule?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (ScheduleDraft) -> Unit
) {
    val edit = initial != null
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var prompt by rememberSaveable(initial?.id) { mutableStateOf(initial?.prompt.orEmpty()) }
    var kind by rememberSaveable(initial?.id) { mutableStateOf(if (edit) "keep" else "daily") }
    var time by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.time ?: "09:00")
    }
    var zone by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.timeZone ?: TimeZone.getDefault().id)
    }
    var weekdays by remember(initial?.id) {
        mutableStateOf(initial?.weekdays?.takeIf { it.isNotEmpty() } ?: listOf(1, 2, 3, 4, 5))
    }
    var expression by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.expression ?: "0 9 * * 1-5")
    }
    var afterMinutes by rememberSaveable(initial?.id) { mutableStateOf("60") }
    var everyHours by rememberSaveable(initial?.id) {
        mutableStateOf(
            ((initial?.everySeconds ?: 3_600L) / 3_600L).coerceAtLeast(1L).toString()
        )
    }
    val minutes = afterMinutes.toIntOrNull()
    val hours = everyHours.toIntOrNull()
    val timeValid = SCHEDULE_TIME.matches(time.trim())
    val zoneValid = zone.isNotBlank()
    val cronValid = SCHEDULE_CRON.matches(expression.trim())
    val timingValid = when (kind) {
        "after" -> minutes != null && minutes in 1..525_600
        "every" -> hours != null && hours in 1..168
        "daily" -> timeValid && zoneValid
        "weekly" -> timeValid && zoneValid && weekdays.isNotEmpty()
        "cron" -> cronValid && zoneValid
        else -> true
    }
    val canSave = !busy && title.isNotBlank() && prompt.isNotBlank() && timingValid
    val formScroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                if (edit) "编辑定时任务" else "新建定时任务",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        },
        text = {
            ScrollEdgeFade(canScrollForward = formScroll.canScrollForward) {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(formScroll)
            ) {
                ScheduleInput(
                    label = "标题",
                    value = title,
                    hint = "例如：查看日志",
                    onChange = { if (it.length <= 200) title = it }
                )
                Spacer(Modifier.height(10.dp))
                ScheduleInput(
                    label = "任务内容",
                    value = prompt,
                    hint = "提醒触发时交给助手执行的指令",
                    onChange = { if (it.length <= 4_000) prompt = it },
                    maxLines = 4
                )
                Spacer(Modifier.height(14.dp))
                Text("频率", color = TextMuted, fontSize = 10.5.sp)
                FlowRow(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val options = buildList {
                        if (edit) add("keep" to "保持当前")
                        add("after" to "一次")
                        add("every" to "每 N 小时")
                        add("daily" to "每天")
                        add("weekly" to "每周")
                        add("cron" to "cron")
                    }
                    options.forEach { (value, label) ->
                        ScheduleChip(
                            label = label,
                            selected = kind == value,
                            onClick = { kind = value }
                        )
                    }
                }
                when (kind) {
                    "after" -> {
                        Spacer(Modifier.height(10.dp))
                        ScheduleInput(
                            label = "多少分钟后",
                            value = afterMinutes,
                            hint = "1 – 525600",
                            onChange = { value ->
                                afterMinutes = value.filter(Char::isDigit).take(6)
                            },
                            numeric = true,
                            error = if (minutes == null || minutes !in 1..525_600) {
                                "请输入 1 到 525600 之间的分钟数"
                            } else null
                        )
                    }
                    "every" -> {
                        Spacer(Modifier.height(10.dp))
                        ScheduleInput(
                            label = "每隔多少小时",
                            value = everyHours,
                            hint = "1 – 168",
                            onChange = { value ->
                                everyHours = value.filter(Char::isDigit).take(3)
                            },
                            numeric = true,
                            error = if (hours == null || hours !in 1..168) {
                                "请输入 1 到 168 之间的小时数"
                            } else null
                        )
                    }
                    "daily", "weekly" -> {
                        Spacer(Modifier.height(10.dp))
                        ScheduleInput(
                            label = "时间（24 小时制）",
                            value = time,
                            hint = "09:00",
                            onChange = { value -> time = value.take(5) },
                            error = if (!timeValid) "格式应为 HH:mm，例如 09:00" else null
                        )
                        if (kind == "weekly") {
                            Spacer(Modifier.height(10.dp))
                            Text("星期", color = TextMuted, fontSize = 10.5.sp)
                            FlowRow(
                                Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("一", "二", "三", "四", "五", "六", "日")
                                    .forEachIndexed { index, label ->
                                        val day = index + 1
                                        ScheduleChip(
                                            label = "周$label",
                                            selected = day in weekdays,
                                            onClick = {
                                                weekdays = if (day in weekdays) {
                                                    weekdays - day
                                                } else {
                                                    (weekdays + day).sorted()
                                                }
                                            }
                                        )
                                    }
                            }
                            if (weekdays.isEmpty()) {
                                Text(
                                    "至少选择一天",
                                    modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                                    color = Red,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        ScheduleInput(
                            label = "时区",
                            value = zone,
                            hint = "Asia/Shanghai",
                            onChange = { zone = it.trim() },
                            error = if (!zoneValid) "时区不能为空" else null
                        )
                    }
                    "cron" -> {
                        Spacer(Modifier.height(10.dp))
                        ScheduleInput(
                            label = "cron 表达式（分 时 日 月 周）",
                            value = expression,
                            hint = "0 9 * * 1-5",
                            onChange = { expression = it.take(120) },
                            error = if (!cronValid) "需要 5 段，以空格分隔" else null
                        )
                        Spacer(Modifier.height(10.dp))
                        ScheduleInput(
                            label = "时区",
                            value = zone,
                            hint = "Asia/Shanghai",
                            onChange = { zone = it.trim() },
                            error = if (!zoneValid) "时区不能为空" else null
                        )
                    }
                }
                if (!edit) {
                    Text(
                        "新建会直接写入 dsh 并投递到当前对话，不再经过助手；到点后触发的内容会出现在这里。",
                        modifier = Modifier.padding(top = 12.dp),
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
            }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ScheduleDraft(
                            title = title.trim(),
                            prompt = prompt.trim(),
                            kind = kind,
                            time = time.trim(),
                            timeZone = zone.trim(),
                            weekdays = weekdays.sorted(),
                            expression = expression.trim(),
                            afterMinutes = minutes ?: 0,
                            everyHours = hours ?: 0
                        )
                    )
                },
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
private fun ScheduleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) AccentSoft else SurfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = if (selected) Accent else TextSecondary,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
    )
}

@Composable
private fun ScheduleInput(
    label: String,
    value: String,
    hint: String,
    onChange: (String) -> Unit,
    maxLines: Int = 1,
    numeric: Boolean = false,
    error: String? = null
) {
    Text(label, color = TextMuted, fontSize = 10.5.sp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceRaised)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = TextPrimary,
            fontSize = 13.sp,
            platformStyle = com.kingkang.app.ui.components.CompactInputPlatformStyle,
            lineHeight = 19.sp
        ),
        cursorBrush = SolidColor(Accent),
        maxLines = maxLines,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(hint, color = TextMuted, fontSize = 12.sp)
            }
            inner()
        }
    )
    if (error != null) {
        Text(
            error,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            color = Red,
            fontSize = 10.sp
        )
    }
}

/** Full reminder detail, including the deliveries dsh kept for it. */
@Composable
private fun ScheduleDetailDialog(
    schedule: AgentSchedule,
    history: AgentScheduleHistory?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    onLoadMore: () -> Unit
) {
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(
                    schedule.title.ifBlank { "定时任务" },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    scheduleFrequency(schedule),
                    modifier = Modifier.padding(top = 3.dp),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            ScrollEdgeFade(
                canScrollForward = scroll.canScrollForward,
            ) {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(scroll)
            ) {
                Text(
                    scheduleRunMeta(schedule),
                    color = if (schedule.active) Accent else TextMuted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    schedule.prompt,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "投递记录",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                when {
                    history == null || (history.loading && history.records.isEmpty()) -> {
                        Row(
                            Modifier.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                color = Accent,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("读取中…", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                    history.error != null -> Text(
                        "读取投递记录失败：${history.error}",
                        modifier = Modifier.padding(top = 8.dp),
                        color = Red,
                        fontSize = 11.sp
                    )
                    history.records.isEmpty() -> Text(
                        "还没有投递记录。到点后，触发的消息会保存在这里。",
                        modifier = Modifier.padding(top = 8.dp),
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    else -> {
                        history.records.forEach { record ->
                            Column(Modifier.padding(top = 8.dp)) {
                                Text(
                                    formatScheduledAt(record.deliveredAt),
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                record.prompt?.let { text ->
                                    Text(
                                        text,
                                        modifier = Modifier.padding(top = 2.dp),
                                        color = TextSecondary,
                                        fontSize = 10.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (history.earlierUnavailable) {
                            Text(
                                "更早的记录已不在本地。",
                                modifier = Modifier.padding(top = 8.dp),
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        } else if (history.earlierPruned) {
                            Text(
                                "更早的记录已按保留规则清理。",
                                modifier = Modifier.padding(top = 8.dp),
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        if (history.nextBefore != null) {
                            Text(
                                if (history.loading) "加载中…" else "加载更早",
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !history.loading, onClick = onLoadMore)
                                    .padding(horizontal = 2.dp, vertical = 4.dp),
                                color = if (history.loading) TextMuted else Accent,
                                fontSize = 11.sp
                            )
                        }
                        if (history.retentionDays > 0 || history.retentionRecords > 0) {
                            Text(
                                "保留规则：${history.retentionDays} 天 / " +
                                    "${history.retentionRecords} 条",
                                modifier = Modifier.padding(top = 8.dp),
                                color = TextMuted,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }
            }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit, enabled = !busy) {
                Text("编辑", color = if (busy) TextMuted else Accent)
            }
        },
        dismissButton = {
            Row {
                if (schedule.active) {
                    TextButton(onClick = onRun, enabled = !busy) {
                        Text("立即运行", color = if (busy) TextMuted else TextSecondary)
                    }
                }
                TextButton(onClick = onDelete, enabled = !busy) {
                    Text("删除", color = if (busy) TextMuted else Red)
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = TextSecondary)
                }
            }
        }
    )
}

/** Deletion is permanent in dsh; a queued delivery is not retracted, so say so. */
@Composable
private fun ScheduleDeleteDialog(
    schedule: AgentSchedule,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "删除定时任务",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        },
        text = {
            Text(
                "删除后不再触发，已排队的提醒消息也不会撤回。\n\n${schedule.title}",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text("删除", color = if (busy) TextMuted else Red)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

/**
 * A section name inside the workbench with its count or density beside it. Sections share
 * one scroll surface so a long job list never pushes the changes out of reach.
 */
@Composable
private fun WorkbenchSection(title: String, meta: String?) {    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        if (!meta.isNullOrEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(meta, color = TextMuted, fontSize = 9.5.sp)
        }
    }
}

@Composable
private fun SectionLoading() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun SectionHint(text: String, isError: Boolean = false) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp),
        color = if (isError) Red else TextMuted,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
}

@Composable
private fun WorkbenchDivider() {
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Border)
    )
}

/**
 * One file the turn changed. [AgentChangedFile.display] is dsh's own rendering, which is
 * usually workspace-relative; tapping opens the comparison.
 */
@Composable
private fun ChangedFileRow(file: AgentChangedFile, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                file.display.ifBlank { file.path },
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (file.binary) {
                Text(
                    "二进制",
                    modifier = Modifier.padding(start = 8.dp),
                    color = TextMuted,
                    fontSize = 9.5.sp
                )
            }
            if (file.oversized) {
                Text(
                    "过大",
                    modifier = Modifier.padding(start = 8.dp),
                    color = TextMuted,
                    fontSize = 9.5.sp
                )
            }
        }
        if (file.added > 0 || file.deleted > 0) {
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (file.added > 0) {
                    Text("+${file.added}", color = Success, fontSize = 9.5.sp)
                }
                if (file.added > 0 && file.deleted > 0) Spacer(Modifier.width(6.dp))
                if (file.deleted > 0) {
                    Text("−${file.deleted}", color = Red, fontSize = 9.5.sp)
                }
            }
        }
    }
}

/**
 * One file the agent declared with `present`. The file lives inside Kali, so opening or
 * sharing it first copies it into the shared outbox via the bridge; copying the path stays
 * available for the terminal and file manager.
 */
@Composable
private fun DeliverableRow(
    file: AgentDeliverable,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                file.path,
                color = TextPrimary,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (file.description.isNotBlank()) {
                Text(
                    file.description,
                    modifier = Modifier.padding(top = 2.dp),
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            "打开",
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onOpen)
                .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
            color = Accent,
            fontSize = 10.5.sp
        )
        Text(
            "分享",
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onShare)
                .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
            color = TextSecondary,
            fontSize = 10.5.sp
        )
        Text(
            "复制",
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onCopy)
                .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
            color = TextMuted,
            fontSize = 10.5.sp
        )
    }
}

/**
 * One workflow run from the transcript. The workbench is an index, not a second run view:
 * tapping scrolls back to the run card, which owns the member detail. A run whose turn
 * closed without a stop reason reads as interrupted so the index never claims it finished.
 */
@Composable
private fun WorkflowRow(item: WorkflowItem, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.name.ifBlank { "未命名工作流" },
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Text(
                workflowStatusLabel(item.status),
                color = workflowStatusColor(item.status),
                fontSize = 10.5.sp
            )
        }
        Text(
            buildString {
                append(if (item.members.isEmpty()) "尚无成员记录" else workflowSummary(item))
                if (item.status == "interrupted") append(" · 该轮结束时未收到完成事件")
                append(" · 点按定位")
            },
            modifier = Modifier.padding(top = 3.dp),
            color = TextMuted,
            fontSize = 9.5.sp,
            lineHeight = 14.sp
        )
    }
}

/**
 * A compact echo of the stats dialog: the three numbers a reader checks mid-task (turns,
 * context headroom, token totals). The dialog remains the full breakdown, so this section
 * only appears when dsh has reported something rather than padding the sheet with dashes.
 */
@Composable
private fun WorkbenchStats(
    stats: AgentSessionStats,
    usage: AgentTokenUsage,
    pressure: AgentContextPressure
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp)
    ) {
        if (stats.hasData) {
            Text(
                buildString {
                    append("轮次 ").append(stats.turns).append(" · 步骤 ").append(stats.steps)
                    stats.llmMs?.let { append(" · 模型耗时 ").append(formatDuration(it)) }
                    stats.toolMs?.let { append(" · 工具耗时 ").append(formatDuration(it)) }
                },
                color = TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
        if (pressure.hasData) {
            Text(
                buildString {
                    append("上下文 ")
                    append(formatTokens(pressure.usedTokens))
                    pressure.contextWindow?.let {
                        append(" / ")
                        append(formatTokens(it))
                    }
                    pressure.fraction?.let {
                        append(" · ")
                        append(formatPercent(it))
                    }
                },
                modifier = Modifier.padding(top = if (stats.hasData) 3.dp else 0.dp),
                color = TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
        if (usage.hasData) {
            Text(
                "Token 输入 ${formatTokens(usage.inputTokens)} · 输出 " +
                    "${formatTokens(usage.outputTokens)} · 合计 ${formatTokens(usage.totalTokens)}",
                modifier = Modifier.padding(
                    top = if (stats.hasData || pressure.hasData) 3.dp else 0.dp
                ),
                color = TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun JobRow(
    job: AgentJob,
    now: Long,
    onKill: () -> Unit,
    onOutput: () -> Unit
) {
    // Stopping kills work the agent may still need; like dsh-web, the first tap only arms
    // the button and a second tap confirms.
    var armed by remember(job.id) { mutableStateOf(false) }
    LaunchedEffect(armed, job.status) {
        if (armed && job.status == "running") {
            delay(3_000)
            armed = false
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                job.label,
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Text(job.statusLabel, color = jobStatusColor(job.status), fontSize = 10.5.sp)
        }
        Text(
            buildString {
                if (job.startedAt > 0L) append("开始 ").append(formatClock(job.startedAt)).append(" · ")
                append("耗时 ").append(formatDuration(job.elapsedMs(now)))
                append(" · 输出 ").append(formatSize(job.outputTotal))
            },
            modifier = Modifier.padding(top = 3.dp),
            color = TextMuted,
            fontSize = 9.5.sp
        )
        job.progress?.let {
            Text(it, modifier = Modifier.padding(top = 2.dp), color = TextSecondary, fontSize = 10.5.sp)
        }
        job.detail?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 2.dp),
                color = TextMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 1.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (job.running) {
                TextButton(
                    onClick = {
                        if (armed) {
                            armed = false
                            onKill()
                        } else {
                            armed = true
                        }
                    },
                    enabled = job.status != "stopping",
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        when {
                            job.status == "stopping" -> "停止中…"
                            armed -> "确认停止"
                            else -> "停止"
                        },
                        color = if (job.status == "stopping") TextMuted else Red,
                        fontSize = 11.5.sp
                    )
                }
            }
            // Without retained output there is nothing to open; a settled job whose ring
            // was empty still reads as finished in its status line.
            if (job.running || job.outputTotal > 0L) {
                TextButton(
                    onClick = onOutput,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("查看输出", color = Accent, fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
private fun JobOutputDialog(
    job: AgentJob?,
    output: AgentJobOutput?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val scroll = rememberScrollState()
    val text = output?.text.orEmpty()
    LaunchedEffect(text.length) {
        if (text.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }
    KkDialog(onDismiss = onDismiss) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            job?.label ?: "任务输出",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            job?.statusLabel ?: "读取中…",
                            color = job?.let { jobStatusColor(it.status) } ?: TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                    if (output?.loading == true) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            color = Accent,
                            strokeWidth = 2.dp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 380.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Background)
                        .border(1.dp, Border, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.TopStart
                ) {
                    when {
                        output == null || (output.loading && text.isEmpty()) -> Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = Accent,
                                strokeWidth = 2.dp
                            )
                        }
                        text.isEmpty() -> Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                output.error ?: "任务还没有输出。",
                                color = if (output.error != null) Red else TextMuted,
                                fontSize = 11.5.sp
                            )
                        }
                        else -> ScrollEdgeFade(
                            canScrollForward = scroll.canScrollForward,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            SelectionContainer {
                                Text(
                                    text,
                                    modifier = Modifier
                                        .verticalScroll(scroll)
                                        .padding(12.dp),
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
                if (output?.lossy == true) {
                    Text(
                        "较早的输出已被丢弃，这里只保留最近的部分。",
                        modifier = Modifier.padding(top = 6.dp),
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
                job?.spillPaths?.firstOrNull()?.let { path ->
                    Text(
                        "完整输出：$path",
                        modifier = Modifier.padding(top = 6.dp),
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
                output?.error?.takeIf { text.isNotEmpty() }?.let {
                    Text(
                        "刷新失败：$it",
                        modifier = Modifier.padding(top = 6.dp),
                        color = Red,
                        fontSize = 10.5.sp
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onRefresh) {
                        Text("刷新", color = TextSecondary)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = TextSecondary)
                    }
                }
    }
}

@Composable
private fun jobStatusColor(status: String): Color = when (status) {
    "running", "stopping" -> Amber
    "completed" -> Success
    "failed" -> Red
    else -> TextMuted
}

private enum class DiffRowKind { Header, Added, Removed, Context }

private data class DiffRow(val text: String, val kind: DiffRowKind)

/**
 * One changed file's turn comparison. dsh returns hunks rather than a whole-file diff, so
 * the viewer renders the list lazily; a very large comparison only pays for the rows on
 * screen. Binary and oversized files arrive without hunks and get an explanation instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChangesDiffDialog(
    file: AgentChangedFile?,
    diff: AgentChangesDiff?,
    onRefresh: () -> Unit,
    onCopyPath: () -> Unit,
    onOpenFile: () -> Unit,
    onShareFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val hunks = diff?.hunks.orEmpty()
    val diffListState = rememberLazyListState()
    val rows = remember(hunks) {
        buildList {
            hunks.forEach { hunk ->
                add(
                    DiffRow(
                        "@@ -${hunk.oldStart},${hunk.oldLines} +${hunk.newStart},${hunk.newLines} @@",
                        DiffRowKind.Header
                    )
                )
                for (line in hunk.lines) {
                    val kind = when (line.firstOrNull()) {
                        '+' -> DiffRowKind.Added
                        '-' -> DiffRowKind.Removed
                        else -> DiffRowKind.Context
                    }
                    add(DiffRow(line, kind))
                }
            }
        }
    }
    val heading = file?.display?.takeIf { it.isNotBlank() } ?: file?.path ?: "文件差异"
    val subtitle = when {
        diff?.kind == "binary" -> "二进制文件"
        diff?.kind == "oversized" -> "改动过大"
        file != null && (file.added > 0 || file.deleted > 0) -> buildString {
            if (file.added > 0) append("+").append(file.added)
            if (file.deleted > 0) {
                if (isNotEmpty()) append(" ")
                append("−").append(file.deleted)
            }
        }
        else -> "逐行对比"
    }
    KkDialog(onDismiss = onDismiss) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            heading,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(subtitle, color = TextMuted, fontSize = 10.5.sp)
                    }
                    if (diff?.loading == true) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            color = Accent,
                            strokeWidth = 2.dp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 380.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Background)
                        .border(1.dp, Border, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.TopStart
                ) {
                    when {
                        file == null -> CenteredHint("文件已不在变更列表里。")
                        diff == null || (diff.loading && rows.isEmpty()) -> Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = Accent,
                                strokeWidth = 2.dp
                            )
                        }
                        diff.error != null && rows.isEmpty() -> CenteredHint(diff.error, isError = true)
                        diff.kind == "binary" -> CenteredHint("二进制文件，无法显示逐行对比。")
                        diff.kind == "oversized" -> CenteredHint("文件改动过大，dsh 没有生成逐行对比。")
                        rows.isEmpty() -> CenteredHint("没有可显示的差异。")
                        else -> ScrollEdgeFade(
                            canScrollForward = diffListState.canScrollForward,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(Modifier.fillMaxWidth(), state = diffListState) {
                                itemsIndexed(rows, key = { index, _ -> index }) { _, row ->
                                    Text(
                                        row.text,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 1.dp),
                                        color = when (row.kind) {
                                            DiffRowKind.Added -> Success
                                            DiffRowKind.Removed -> Red
                                            DiffRowKind.Header -> TextMuted
                                            DiffRowKind.Context -> TextSecondary
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
                if (diff != null && !diff.loading && diff.error == null) {
                    if (diff.coarse) DiffNote("对比按时间窗口生成，可能包含本轮开始前的改动。")
                    if (!diff.before) DiffNote("该文件在本轮开始时不存在（新增）。")
                    if (!diff.after) DiffNote("该文件在本轮结束后不存在（已删除或移动）。")
                }
                diff?.error?.takeIf { rows.isNotEmpty() }?.let {
                    Text(
                        "刷新失败：$it",
                        modifier = Modifier.padding(top = 6.dp),
                        color = Red,
                        fontSize = 10.5.sp
                    )
                }
                FlowRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCopyPath, enabled = file != null) {
                        Text("复制路径", color = TextSecondary)
                    }
                    TextButton(onClick = onOpenFile, enabled = file != null) {
                        Text("打开", color = TextSecondary)
                    }
                    TextButton(onClick = onShareFile, enabled = file != null) {
                        Text("分享", color = TextSecondary)
                    }
                    TextButton(onClick = onRefresh, enabled = file != null) {
                        Text("刷新", color = TextSecondary)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = TextSecondary)
                    }
                }
    }
}

@Composable
private fun CenteredHint(text: String, isError: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (isError) Red else TextMuted,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DiffNote(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 6.dp),
        color = TextMuted,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
}

/**
 * A native reading of dsh's trajectory: every user prompt, model step and tool call in
 * order, grouped by turn. Plugin-contributed views cannot be native (they are React
 * components in dsh-web), so those remain available in the Harness screen.
 */
@Composable
private fun TrajectoryPane(
    state: AgentState,
    onJumpToItem: (AgentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(TrajectoryFilter.All) }
    val listState = rememberLazyListState()
    val allRows = remember(state.items) { trajectoryRows(state.items) }
    val needle = query.trim()
    val rows = remember(allRows, needle, filter) {
        if (needle.isEmpty() && filter == TrajectoryFilter.All) {
            allRows
        } else {
            val kept = allRows.filter { row ->
                row !is TrajectoryRow.Entry || (
                    filter.matches(row.item) &&
                        (needle.isEmpty() || row.item.searchText().contains(needle, ignoreCase = true))
                    )
            }
            // A turn heading with nothing under it would read as a broken divider.
            val result = mutableListOf<TrajectoryRow>()
            var heading: TrajectoryRow.Turn? = null
            kept.forEach { row ->
                when (row) {
                    is TrajectoryRow.Turn -> heading = row
                    is TrajectoryRow.Entry -> {
                        heading?.let { result += it }
                        heading = null
                        result += row
                    }
                }
            }
            result
        }
    }
    if (allRows.isEmpty()) {
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
    val visibleEntries = rows.count { it is TrajectoryRow.Entry }
    val totalEntries = state.items.size
    Column(modifier) {
        TrajectoryFilterBar(
            query = query,
            filter = filter,
            onQuery = { query = it },
            onFilter = { filter = it }
        )
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("没有匹配的轨迹", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "换个关键词，或把筛选切回「全部」。",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "清除筛选",
                        color = Accent,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                query = ""
                                filter = TrajectoryFilter.All
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            return@Column
        }
        ScrollEdgeFade(
            canScrollForward = listState.canScrollForward,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp)
        ) {
            item(key = "trajectory:summary") {
                Text(
                    when {
                        visibleEntries != totalEntries ->
                            "$visibleEntries / $totalEntries 条轨迹"
                        state.running -> "正在记录 · $totalEntries 条轨迹"
                        else -> "$totalEntries 条轨迹 · 按轮次排列"
                    },
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
                        TrajectoryEntry(row.item) { onJumpToItem(row.item) }
                    }
                }
            }
        }
        }
    }
}

private enum class TrajectoryFilter(val label: String) {
    All("全部"),
    User("用户"),
    Assistant("助手"),
    Tool("工具"),
    Notice("提示");

    fun matches(item: AgentItem): Boolean = when (this) {
        All -> true
        User -> item is UserItem
        Assistant -> item is AssistantItem
        Tool -> item is ToolItem || item is WorkflowItem
        Notice -> item is NoticeItem
    }
}

/** Everything a reader might type to find an entry: body text, arguments and output. */
private fun AgentItem.searchText(): String = when (this) {
    is UserItem -> buildString {
        append(text)
        if (files.isNotEmpty()) {
            append(' ')
            append(files.joinToString(" ") { it.name })
        }
    }
    is AssistantItem -> "$text\n$reasoning"
    is ToolItem -> "$name\n$arguments\n$resultText"
    is WorkflowItem -> buildString {
        append(name)
        members.forEach { member ->
            append('\n')
            append(member.label)
            append(' ')
            append(member.phase.orEmpty())
        }
    }
    is NoticeItem -> text
}

@Composable
private fun TrajectoryFilterBar(
    query: String,
    filter: TrajectoryFilter,
    onQuery: (String) -> Unit,
    onFilter: (TrajectoryFilter) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = { if (it.length <= 120) onQuery(it) },
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    platformStyle = com.kingkang.app.ui.components.CompactInputPlatformStyle,
                    lineHeight = 12.5.sp,
                ),
                cursorBrush = SolidColor(Accent),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("搜索轨迹", color = TextMuted, fontSize = 12.5.sp)
                    }
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                Text(
                    "×",
                    color = TextMuted,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onQuery("") }
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TrajectoryFilter.entries.forEach { option ->
                val selected = option == filter
                Text(
                    option.label,
                    color = if (selected) Accent else TextMuted,
                    fontSize = 10.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Accent.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { onFilter(option) }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
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
private fun TrajectoryEntry(item: AgentItem, onJump: () -> Unit) {
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
                onToggle = toggle,
                onJump = onJump
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
                onToggle = toggle,
                onJump = onJump
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
                meta = buildString {
                    append(toolSummary(item))
                    item.elapsedMs?.let { append(" · ").append(formatDuration(it)) }
                },
                text = "",
                detail = detail,
                monospaceDetail = true,
                expanded = expanded,
                onToggle = toggle,
                onJump = onJump
            )
        }
        is WorkflowItem -> {
            val detail = if (item.members.isEmpty()) null else buildString {
                item.members.groupBy { it.phase }.forEach { (phase, members) ->
                    append(workflowPhaseLabel(phase)).append('\n')
                    members.forEach { member ->
                        append("  ").append(member.label.ifBlank { "未命名" })
                        append(" · ").append(workflowMemberStatusLabel(member, item.status))
                        append('\n')
                    }
                }
            }.trimEnd()
            TrajectoryLine(
                dotColor = workflowStatusColor(item.status),
                label = "工作流",
                meta = buildString {
                    if (item.name.isNotBlank()) append(item.name).append(" · ")
                    append(workflowStatusLabel(item.status))
                    append(" · ").append(item.members.size).append(" 个成员")
                },
                text = "",
                detail = detail,
                expanded = expanded,
                onToggle = toggle,
                onJump = onJump
            )
        }
        is NoticeItem -> TrajectoryLine(
            dotColor = if (item.isError) Red else TextMuted,
            label = "提示",
            meta = null,
            text = item.text,
            detail = null,
            expanded = expanded,
            onToggle = toggle,
                onJump = onJump
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
    onJump: (() -> Unit)? = null,
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
            if (onJump != null) {
                Text(
                    "定位",
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onJump)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(2.dp))
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
    outlineAvailable: Boolean,
    workbenchAvailable: Boolean,
    workbenchBadge: Int,
    workbenchBadgeError: Boolean,
    onOpenOutline: () -> Unit,
    onOpenWorkbench: () -> Unit,
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
        if (workbenchAvailable) {
            // The count is the workbench's own activity, not an unread badge: live work
            // tints amber, and a settled failure stays red until the list is refreshed.
            Box(Modifier.size(48.dp)) {
                IconButton(onClick = onOpenWorkbench, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Build,
                        contentDescription = if (workbenchBadge > 0) {
                            "工作台，$workbenchBadge 项运行中或失败"
                        } else {
                            "工作台"
                        },
                        tint = TextSecondary
                    )
                }
                if (workbenchBadge > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 3.dp, end = 3.dp)
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(if (workbenchBadgeError) Red else Amber),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (workbenchBadge > 9) "9+" else workbenchBadge.toString(),
                            color = OnAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        if (outlineAvailable) {
            IconButton(onClick = onOpenOutline) {
                Icon(Icons.Filled.List, contentDescription = "轮次导航", tint = TextSecondary)
            }
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
    team: AgentTeam?,
    parentAvailable: Boolean,
    depth: Int,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onToggleTeam: (Boolean) -> Unit,
    onOpen: (AgentSubagent) -> Unit
) {
    val listState = rememberLazyListState()
    KkDialog(onDismiss = onDismiss) {
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
            if (team != null && team.hasData) {
                Spacer(Modifier.height(12.dp))
                TeamSection(team, onToggleTeam)
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
                ScrollEdgeFade(
                    canScrollForward = listState.canScrollForward,
                    modifier = Modifier.heightIn(max = 460.dp)
                ) {
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(subagents, key = { it.id }) { subagent ->
                            SubagentRow(subagent) { onOpen(subagent) }
                        }
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

/**
 * The team roster and its task board, as the `agentTeam` projection reports them. A
 * non-team session has no such projection, so this block simply does not appear.
 */
@Composable
private fun TeamSection(team: AgentTeam, onToggle: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceRaised)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (team.enabled != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "团队协作",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = 12.5.sp
                )
                Switch(
                    checked = team.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.height(28.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("团队", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                "${team.members.size} 人 · ${team.tasks.count { it.status != "deleted" }} 项任务",
                color = TextMuted,
                fontSize = 9.5.sp
            )
        }
        if (!team.failure.isNullOrBlank()) {
            Text(
                "团队数据不完整：${team.failure}",
                modifier = Modifier.padding(top = 4.dp),
                color = Amber,
                fontSize = 10.sp
            )
        }
        team.members.forEach { member ->
            Row(
                Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when (member.phase) {
                                "active" -> Success
                                "failed" -> Red
                                else -> Amber
                            }
                        )
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    member.name,
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(
                        if (member.role == "lead") "负责人" else "成员",
                        when (member.phase) {
                            "provisioning" -> "创建中"
                            "active" -> "活跃"
                            "failed" -> "失败"
                            else -> member.phase
                        }
                    ).joinToString(" · "),
                    color = TextMuted,
                    fontSize = 9.5.sp
                )
            }
        }
        val tasks = team.tasks.filter { it.status != "deleted" }
        if (tasks.isNotEmpty()) {
            Text(
                "任务板",
                modifier = Modifier.padding(top = 10.dp),
                color = TextSecondary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
            tasks.forEach { task ->
                Row(
                    Modifier.padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (task.status) {
                            "completed" -> "已完成"
                            "in_progress" -> "进行中"
                            else -> "待开始"
                        },
                        color = when (task.status) {
                            "completed" -> Success
                            "in_progress" -> Accent
                            else -> TextMuted
                        },
                        fontSize = 9.5.sp
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        task.subject.ifBlank { task.description },
                        modifier = Modifier.weight(1f),
                        color = if (task.status == "completed") TextMuted else TextPrimary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    team.members.firstOrNull { it.id == task.ownerId }?.let { owner ->
                        Spacer(Modifier.width(6.dp))
                        Text(owner.name, color = TextMuted, fontSize = 9.5.sp)
                    }
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
                        subagent.title?.let { append(it).append(" · ") }
                        append(if (subagent.continuable) "可对话" else "只读")
                        append(" · ")
                        append(
                            when {
                                subagent.running -> "运行中"
                                subagent.completed -> "已完成"
                                else -> "已结束"
                            }
                        )
                        if (subagent.hasChildren) append(" · 还有下级")
                    }
                },
                color = TextMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (diagnostic == null && (subagent.tokens != null || subagent.durationMs != null)) {
                Text(
                    listOfNotNull(
                        subagent.tokens?.let { "${formatTokens(it)} tok" },
                        subagent.durationMs?.let { formatDuration(it) }
                    ).joinToString(" · "),
                    modifier = Modifier.padding(top = 1.dp),
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
private fun AgentStoppedState(
    state: AgentState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val kaliRemoved = state.status.contains("Kali 环境已删除")
    val uninstalled = state.status.contains("卸载") || kaliRemoved
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 48.dp)
    ) {
        Text(
            when {
                kaliRemoved -> "Kali 环境已删除"
                uninstalled -> "Agent 核心已卸载"
                else -> "Agent 已停止"
            },
            color = TextPrimary,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                kaliRemoved -> "Kali 环境与其中的 dsh 都已删除。可在设置里重新安装 Kali 环境。"
                uninstalled -> "dsh 已从 Kali 中移除。可在设置里重新安装 Agent 核心。"
                else -> "Agent 连接已停止。dsh 仍在 Kali 中，重新连接即可继续之前的会话。"
            },
            color = TextSecondary,
            fontSize = 13.5.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (uninstalled) {
                // Retrying cannot bring dsh back; reinstalling is the only real path.
                PrimaryPill("打开设置", onOpenSettings)
            } else {
                PrimaryPill("重新连接", onRetry)
                OutlinePill("打开设置", onOpenSettings)
            }
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
    installing: Boolean,
    installingStep: String?,
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
        // A missing runtime and an install in progress both leave the agent disconnected,
        // but telling the reader "Node.js 尚未安装" while it downloads reads as an error.
        Text(
            if (installing) "正在安装 Agent 核心" else "Agent 还没有就绪",
            color = TextPrimary,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (installing) installingStep ?: "安装完成后会自动连接。" else state.error ?: state.status,
            color = TextSecondary,
            fontSize = 13.5.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(10.dp))
        if (!installing) {
            Text(
                "对话由 Kali 内的 dsh 提供。先在设置里安装 Kali 环境、Node.js 和 Agent 核心。",
                color = TextMuted,
                fontSize = 12.5.sp,
                lineHeight = 19.sp
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (installing) {
                PrimaryPill("查看安装进度", onOpenSettings)
            } else {
                PrimaryPill("重试连接", onRetry)
                OutlinePill("打开设置", onOpenSettings)
            }
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
    apiKeyMissing: Boolean,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    onConfigureKey: () -> Unit,
    onSend: (String) -> Unit
) {
    val scroll = rememberScrollState()
    ScrollEdgeFade(
        canScrollForward = scroll.canScrollForward,
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
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
        if (apiKeyMissing) {
            Spacer(Modifier.height(16.dp))
            ApiKeyPrompt(onConfigureKey)
        } else if (!modelRoutable) {
            Spacer(Modifier.height(10.dp))
            Text(
                "当前模型 provider 不可用，请在设置中检查配置。",
                color = Amber,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(if (apiKeyMissing) 16.dp else 26.dp))
        TerminalEntry(
            environment = environment,
            rootMode = rootMode,
            onOpen = onOpenTerminal,
            onSettings = onOpenSettings
        )

        Spacer(Modifier.height(20.dp))
        SuggestionRow("检查这台设备的 Kali 环境") { onSend(it) }
        Spacer(Modifier.height(8.dp))
        SuggestionRow("分析当前内网的机器拓扑") { onSend(it) }
        Spacer(Modifier.height(8.dp))
        SuggestionRow("探测当前内网中可能存在漏洞的服务") { onSend(it) }
    }
    }
}

/** The one setup step that has no terminal shortcut: dsh cannot call a model without it. */
@Composable
private fun ApiKeyPrompt(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(1.dp, Border.copy(alpha = 0.9f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Amber)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "还差一步：配置 DeepSeek API Key",
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Agent 需要 API Key 才能调用模型。密钥只写入 Kali 内的 dsh，" +
                    "可在「设置 → Agent 连接」中填写。",
                color = TextMuted,
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("去配置", color = Accent, fontSize = 11.5.sp)
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Filled.ArrowForward,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(15.dp)
        )
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

/** Result strip for the session-log archive: open, share, or retry the export. */
@Composable
private fun ExportDock(
    export: AgentExport,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    export.running -> "正在导出会话…"
                    export.error != null -> "导出失败"
                    else -> "会话已导出"
                },
                color = if (export.error != null) Red else TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
            if (!export.running) {
                Text(
                    export.error ?: "${export.name} · ${formatBytes(export.bytes)}",
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!export.running) {
            if (export.error != null) {
                ExportAction("重试", onRetry)
            } else {
                ExportAction("打开", onOpen)
                Spacer(Modifier.width(14.dp))
                ExportAction("分享", onShare)
                Spacer(Modifier.width(14.dp))
                ExportAction("保存", onSave)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "×",
                color = TextMuted,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun ExportAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = TextSecondary,
        fontSize = 11.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp)
    )
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
    KkDialog(onDismiss = onDismiss) {
        KkDialogTitle("排队消息", onDismiss = onDismiss)
        Text(
            item.displayText,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        CompactMenuRow("编辑内容", TextSecondary, onEdit)
        CompactMenuRow("转为立即引导", Amber, onSteer)
        CompactMenuRow("移除", Red, onRemove)
    }
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

/** A menu row with menu density: enough touch height, none of AlertDialog's extra air. */
@Composable
private fun CompactMenuRow(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
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
                        platformStyle = com.kingkang.app.ui.components.CompactInputPlatformStyle,
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
    onQuote: (String) -> Unit,
    onLoadImage: (AgentImage) -> Unit,
    onBranch: (AssistantItem) -> Unit,
    onRate: (String, String, String?, String?) -> Unit,
    onClearFeedback: (String) -> Unit,
    onOpenFile: (AgentFile) -> Unit,
    onActionCard: (String, String) -> Unit,
    onContinue: () -> Unit
) {
    val sessionId = state.activeSessionId
    fun imageData(image: AgentImage): String? =
        image.localData ?: sessionId?.let { state.imageData["$it:${image.attachmentId}"] }
    val branchable = remember(state.items, state.running) {
        branchableMessageKeys(state.items, state.running)
    }
    val cardsByHost = remember(state.items, state.actionCards) {
        actionCardsByHostKey(state.items, state.actionCards)
    }
    ScrollEdgeFade(
        canScrollForward = listState.canScrollForward,
        modifier = Modifier.fillMaxSize()
    ) {
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
                    is UserItem -> UserMessageRow(item, onCopy, ::imageData, onLoadImage, onOpenFile)
                    is AssistantItem -> {
                        AssistantMessageRow(
                            message = item,
                            onCopy = onCopy,
                            onQuote = onQuote,
                            imageData = ::imageData,
                            onLoadImage = onLoadImage,
                            feedback = item.messageId?.let { state.messageFeedback[it] },
                            branchable = item.key in branchable,
                            onBranch = { onBranch(item) },
                            onRate = onRate,
                            onClearFeedback = onClearFeedback
                        )
                        cardsByHost[item.key]?.forEach { card ->
                            Spacer(Modifier.height(10.dp))
                            ActionCardView(
                                card = card,
                                busy = state.actionCardBusy == card.callId,
                                onAct = onActionCard
                            )
                        }
                    }
                    is ToolItem -> ToolCard(
                        tool = item,
                        imageData = ::imageData,
                        onLoadImage = onLoadImage
                    )
                    is WorkflowItem -> WorkflowRunCard(item)
                    is NoticeItem -> NoticeRow(item)
                }
            }
            if (state.continuation != null && !state.running) {
                item(key = "continue") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = onContinue) {
                            Text("继续上次回答", color = Accent, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (state.running && state.items.none { it is AssistantItem && it.streaming }) {
                item(key = "typing") { TypingRow() }
            }
        }
    }
}

/**
 * dsh-message-quotes encodes a quote as ordinary Markdown blockquote lines: one `> `
 * prefix per line, then a blank line for the reply. The model sees nothing special.
 */
private fun quoteMessageText(text: String): String {
    val quoted = text.trim().lines().joinToString("\n") { line -> "> $line" }
    return "$quoted\n\n"
}

/** Picks the final, settled assistant reply of each turn that owns a visible card. */
private fun actionCardsByHostKey(
    items: List<AgentItem>,
    cards: List<AgentActionCard>
): Map<String, List<AgentActionCard>> {
    val visible = cards.filterNot { it.settled }
    if (visible.isEmpty()) return emptyMap()
    val hosts = mutableMapOf<String, List<AgentActionCard>>()
    visible.groupBy { it.turn }.forEach { (turn, list) ->
        val host = items.lastOrNull {
            it is AssistantItem && it.turn == turn && !it.streaming && it.text.isNotBlank()
        } ?: return@forEach
        hosts[host.key] = list
    }
    return hosts
}

private fun actionCardStatus(phase: String): String? = when (phase) {
    "creating" -> "正在创建…"
    "installing" -> "正在安装…"
    "installed" -> "已安装"
    "kept" -> "已保留"
    "removed" -> "已移除"
    "cancelled" -> "已取消"
    "failed" -> "创建失败"
    "interrupted" -> "已中断"
    "unconfirmed" -> "结果未确认"
    else -> null
}

/** The verbs a card offers in its current phase; only `create` needs kind details. */
private fun actionCardVerbs(card: AgentActionCard, creation: Boolean): List<Triple<String, String, Boolean>> =
    when (card.phase) {
        "unstarted" -> buildList {
            if (creation) add(Triple("create", "创建", true))
            add(Triple("dismiss", "暂不需要", false))
        }
        "creating", "installing" -> listOf(Triple("cancel", "取消", false))
        "installed" -> listOf(Triple("keep", "保留", false), Triple("undo", "撤销", false))
        "kept" -> listOf(Triple("undo", "撤销", false))
        "removed" -> listOf(Triple("restore", "恢复", true))
        "cancelled", "failed", "interrupted", "unconfirmed" -> buildList {
            if (creation) add(Triple("create", "重试创建", true))
            add(Triple("dismiss", "暂不需要", false))
        }
        else -> emptyList()
    }

/**
 * One optional action below a finished answer. Nothing runs until the user taps a verb;
 * the card then reports the decision dsh records, including background progress.
 */
@Composable
private fun ActionCardView(
    card: AgentActionCard,
    busy: Boolean,
    onAct: (String, String) -> Unit
) {
    val creation = card.action == "create_personalized_page"
    val status = actionCardStatus(card.phase)
    val statusColor = when (card.phase) {
        "installed", "kept" -> Success
        "failed", "interrupted", "unconfirmed" -> Amber
        else -> TextMuted
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                card.title.ifBlank { "建议操作" },
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(13.dp),
                    color = Accent,
                    strokeWidth = 1.5.dp
                )
            } else if (status != null) {
                Text(status, color = statusColor, fontSize = 10.sp)
            }
        }
        if (card.brief.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                card.brief,
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        }
        val verbs = actionCardVerbs(card, creation)
        if (verbs.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                verbs.forEach { (verb, label, primary) ->
                    TextButton(
                        onClick = { onAct(card.callId, verb) },
                        enabled = !busy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            label,
                            color = when {
                                busy -> TextMuted
                                primary -> Accent
                                else -> TextSecondary
                            },
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * dsh-web only branches from the final assistant message of a completed turn: anything
 * mid-turn would fork a partial conversation. Returns the item keys that qualify.
 */
private fun branchableMessageKeys(
    items: List<AgentItem>,
    running: Boolean
): Set<String> {
    // Forking while a turn streams would copy a half-written turn; dsh-web waits it out.
    if (running) return emptySet()
    val keys = mutableSetOf<String>()
    val turn = mutableListOf<AgentItem>()
    fun flushTurn() {
        turn.lastOrNull { it is AssistantItem && !it.streaming && it.seq != null }
            ?.let { keys += it.key }
        turn.clear()
    }
    items.forEach { item ->
        // Every user message opens a turn, whatever its source; the assistant reply that
        // settles it is the only fork point dsh-web accepts.
        if (item is UserItem) flushTurn()
        turn += item
    }
    flushTurn()
    return keys
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserMessageRow(
    message: UserItem,
    onCopy: (String) -> Unit,
    imageData: (AgentImage) -> String?,
    onLoadImage: (AgentImage) -> Unit,
    onOpenFile: (AgentFile) -> Unit
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
            MessageFiles(message.files, onOpenFile)
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
private fun MessageFiles(files: List<AgentFile>, onOpen: (AgentFile) -> Unit) {
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
                onRemove = null,
                onClick = { onOpen(file) }
            )
        }
    }
}

/** A file drafted in the composer or already sent; the guest path stays out of the bubble. */
@Composable
private fun FileChip(
    name: String,
    meta: String,
    onRemove: (() -> Unit)?,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .width(184.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Border.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
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

internal fun formatFileSize(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
    return String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0)
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
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { target ->
        if (target != null) {
            status = FileActions.saveDataUrl(context, dataUrl, target) ?: "图片已保存"
        }
    }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, dataUrl) {
        value = withContext(Dispatchers.IO) { decodeImage(dataUrl, 1600) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceRaised)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    status.orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = {
                        status = FileActions.copyImageToClipboard(context, dataUrl) ?: "图片已复制"
                    }
                ) {
                    Text("复制图片", color = TextSecondary, fontSize = 12.sp)
                }
                TextButton(onClick = { save.launch("kingkang-image.png") }) {
                    Text("保存", color = Accent, fontSize = 12.sp)
                }
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
    onQuote: (String) -> Unit,
    imageData: (AgentImage) -> String?,
    onLoadImage: (AgentImage) -> Unit,
    feedback: AgentMessageFeedback?,
    branchable: Boolean,
    onBranch: () -> Unit,
    onRate: (String, String, String?, String?) -> Unit,
    onClearFeedback: (String) -> Unit
) {
    LaunchedEffect(message.images) {
        message.images.forEach { if (imageData(it) == null) onLoadImage(it) }
    }
    var preview by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    // No avatar on the left: the reply starts at the same edge as the rest of the
    // transcript, which keeps long answers quiet and gives their text the full width.
    Row(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { menu = true })
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
            val usage = message.usage
            if (usage != null && usage.hasData && !message.streaming) {
                Spacer(Modifier.height(7.dp))
                Text(
                    usageSummary(usage),
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (feedback != null && !message.streaming) {
                Spacer(Modifier.height(if (usage != null && usage.hasData) 3.dp else 7.dp))
                Text(
                    feedbackSummary(feedback),
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (menu) {
        MessageActionsDialog(
            message = message,
            feedback = feedback,
            branchable = branchable,
            onCopy = {
                onCopy(message.text)
                menu = false
            },
            onQuote = {
                onQuote(message.text)
                menu = false
            },
            onBranch = {
                onBranch()
                menu = false
            },
            onRate = onRate,
            onClearFeedback = onClearFeedback,
            onDismiss = { menu = false }
        )
    }
    preview?.let { ImagePreviewDialog(it) { preview = null } }
}

@Composable
private fun MessageActionsDialog(
    message: AssistantItem,
    feedback: AgentMessageFeedback?,
    branchable: Boolean,
    onCopy: () -> Unit,
    onQuote: () -> Unit,
    onBranch: () -> Unit,
    onRate: (String, String, String?, String?) -> Unit,
    onClearFeedback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val messageId = message.messageId
    // A new rating opens the editor first, mirroring dsh-web: the judgment and its
    // explanation are one write. Retracting an existing rating stays a single tap.
    var composing by remember { mutableStateOf<String?>(null) }
    KkDialog(onDismiss = onDismiss) {
        KkDialogTitle("消息操作", onDismiss = onDismiss)
        CompactMenuRow("复制", TextSecondary, onCopy)
        CompactMenuRow("引用到输入框", TextSecondary, onQuote)
        CompactMenuRow(
            if (branchable) "从这里分支" else "从这里分支（不可用）",
            if (branchable) TextSecondary else TextMuted,
            if (branchable) onBranch else ({})
        )
        if (!branchable) {
            Text(
                branchHint(message),
                color = TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            )
        }
        if (messageId == null) {
            Text(
                "这条回复还没有最终消息 ID，暂时不能评价。",
                color = TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp)
            )
        } else {
            val positive = feedback?.rating == "positive"
            val negative = feedback?.rating == "negative"
            CompactMenuRow(if (positive) "取消好评" else "好评", TextSecondary) {
                if (positive) {
                    onClearFeedback(messageId)
                    onDismiss()
                } else {
                    composing = "positive"
                }
            }
            CompactMenuRow(if (negative) "取消差评" else "差评", TextSecondary) {
                if (negative) {
                    onClearFeedback(messageId)
                    onDismiss()
                } else {
                    composing = "negative"
                }
            }
            if (feedback != null) {
                CompactMenuRow("修改反馈原因", TextMuted) {
                    composing = feedback.rating
                }
            }
        }
    }
    val rating = composing
    if (messageId != null && rating != null) {
        FeedbackEditorDialog(
            rating = rating,
            existing = feedback?.takeIf { it.rating == rating },
            onSubmit = { category, note ->
                onRate(messageId, rating, category, note)
                composing = null
                onDismiss()
            },
            onDismiss = { composing = null }
        )
    }
}

/** Categories dsh's message feedback accepts, with the labels dsh-web shows. */
private val FeedbackCategories = listOf(
    "task-result" to "任务结果",
    "instruction-following" to "指令理解",
    "product-interaction" to "功能交互",
    "service-stability" to "稳定速度",
    "resource-cost" to "资源费用",
    "security-privacy-permission" to "安全隐私",
    "other" to "其他"
)

/**
 * The rating dialog dsh-web opens for either thumb: pick at most one category, add an
 * optional note, then submit. Both halves travel in the same `messageFeedback/put`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackEditorDialog(
    rating: String,
    existing: AgentMessageFeedback?,
    onSubmit: (String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf(existing?.category) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                if (rating == "negative") "哪里可以更好？" else "记录这次好评",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text("原因（可选）", color = TextMuted, fontSize = 10.5.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FeedbackCategories.forEach { (id, label) ->
                        val selected = category == id
                        Text(
                            label,
                            color = if (selected) Accent else TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Accent.copy(alpha = 0.12f) else SurfaceVariant)
                                .clickable { category = if (selected) null else id }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("补充说明（可选）", color = TextMuted, fontSize = 10.5.sp)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = note,
                    onValueChange = { if (it.length <= 600) note = it },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, lineHeight = 19.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (note.isBlank()) {
                            Text(
                                if (rating == "negative") {
                                    "写下具体哪里不好，或希望怎么改"
                                } else {
                                    "这次哪里做得好，可以补充说明"
                                },
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        inner()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(category, note.trim().ifBlank { null }) }
            ) {
                Text("提交", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

/** Why "从这里分支" is greyed out, mirroring dsh-web's rule against forking mid-turn. */
private fun branchHint(message: AssistantItem): String = when {
    message.streaming -> "这条回复还在生成，完成后才能分支。"
    message.seq == null -> "这条回复没有轮次位置，无法分支。"
    else -> "只能在已完成轮次的最后一条回复上分支。"
}

private fun feedbackSummary(feedback: AgentMessageFeedback): String =
    if (feedback.rating == "negative") "已标记为没帮助" else "已标记为有帮助"

/** One quiet line under a finished reply, mirroring dsh-web's per-turn accounting. */
private fun usageSummary(usage: AgentUsage): String = buildString {
    append("输入 ")
    append(formatTokens(usage.inputTokens))
    append(" · 输出 ")
    append(formatTokens(usage.outputTokens))
    if (usage.cacheReadTokens > 0L) {
        append(" · 缓存读 ")
        append(formatTokens(usage.cacheReadTokens))
    }
    if (usage.cacheWriteTokens > 0L) {
        append(" · 缓存写 ")
        append(formatTokens(usage.cacheWriteTokens))
    }
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
    when (language?.lowercase()) {
        "diff", "patch" -> {
            MarkdownDiffBlock(code)
            return
        }
        "math", "latex", "tex" -> {
            MarkdownMathBlock(code)
            return
        }
    }
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

/** A ```diff fence: additions, removals and hunk headers keep their diff colors. */
@Composable
private fun MarkdownDiffBlock(code: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 11.dp, vertical = 9.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        code.lines().take(200).forEach { line ->
            Text(
                line,
                color = when {
                    line.startsWith("+++") || line.startsWith("---") -> TextMuted
                    line.startsWith("+") -> Success
                    line.startsWith("-") -> Red
                    line.startsWith("@@") -> Accent
                    else -> TextSecondary
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                softWrap = false
            )
        }
        if (code.lines().size > 200) {
            Text("（已截断）", color = TextMuted, fontSize = 10.sp)
        }
    }
}

/**
 * ```math fence. Native Compose has no TeX layout engine, so the formula is shown as a
 * quiet centered block in the source notation rather than pretending to typeset it.
 */
@Composable
private fun MarkdownMathBlock(code: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text("公式", color = TextMuted, fontSize = 9.5.sp)
        Spacer(Modifier.height(5.dp))
        Text(
            code,
            modifier = Modifier.fillMaxWidth(),
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.5.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
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

/**
 * One workflow run. dsh records only the four `tool-workflow/` lifecycle events, so this
 * card is the whole workflow surface: the run, its phases, and each member's outcome.
 * It stays open while the run needs attention (running, failed, interrupted) and folds
 * away once a completed run settles, unless the reader opened it.
 */
@Composable
private fun WorkflowRunCard(item: WorkflowItem) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(item.status != "completed") }
    var touched by rememberSaveable(item.key) { mutableStateOf(false) }
    LaunchedEffect(item.status) {
        if (!touched) expanded = item.status != "completed"
    }
    val status = item.status
    val phases = remember(item.members) { item.members.groupBy { it.phase } }
    val memberLimit = 50
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Border.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .clickable {
                touched = true
                expanded = !expanded
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(workflowStatusColor(status))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "工作流",
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
            if (item.name.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    item.name,
                    modifier = Modifier.weight(1f),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(workflowStatusLabel(status), color = workflowStatusColor(status), fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                if (expanded) "收起" else "详情",
                color = TextMuted,
                fontSize = 10.sp
            )
        }
        Text(
            workflowSummary(item),
            modifier = Modifier.padding(top = 3.dp, start = 13.dp),
            color = TextMuted,
            fontSize = 9.5.sp
        )
        if (expanded) {
            var shown = 0
            phases.forEach { (phase, members) ->
                if (shown < memberLimit) {
                    Text(
                        workflowPhaseLabel(phase),
                        modifier = Modifier.padding(top = 9.dp),
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    members.forEach { member ->
                        if (shown < memberLimit) {
                            WorkflowMemberRow(member, status)
                            shown += 1
                        }
                    }
                }
            }
            if (item.members.isEmpty()) {
                Text(
                    "这个运行还没有委派成员。",
                    modifier = Modifier.padding(top = 9.dp),
                    color = TextMuted,
                    fontSize = 10.5.sp
                )
            } else if (item.members.size > memberLimit) {
                Text(
                    "还有 ${item.members.size - memberLimit} 个成员未显示",
                    modifier = Modifier.padding(top = 6.dp),
                    color = TextMuted,
                    fontSize = 9.5.sp
                )
            }
        }
    }
}

@Composable
private fun WorkflowMemberRow(member: AgentWorkflowMember, runStatus: String) {
    val memberStatus = member.outcome
        ?: if (runStatus == "interrupted") "interrupted" else "running"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(workflowStatusColor(memberStatus))
        )
        Spacer(Modifier.width(7.dp))
        Text(
            member.label.ifBlank { "未命名" },
            modifier = Modifier.weight(1f),
            color = if (memberStatus == "running") TextPrimary else TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(workflowStatusLabel(memberStatus), color = TextMuted, fontSize = 9.5.sp)
    }
}

private fun workflowSummary(item: WorkflowItem): String {
    val completed = item.members.count { it.outcome == "completed" }
    val failed = item.members.count { it.outcome == "failed" }
    val cancelled = item.members.count { it.outcome == "cancelled" }
    val phases = item.members.map { it.phase }.distinct().size
    return buildString {
        append("${item.members.size} 个成员")
        if (phases > 1) append(" · $phases 个阶段")
        if (completed > 0) append(" · $completed 已完成")
        if (failed > 0) append(" · $failed 失败")
        if (cancelled > 0) append(" · $cancelled 已取消")
    }
}

private fun workflowStatusLabel(status: String): String = when (status) {
    "running" -> "运行中"
    "completed" -> "已完成"
    "failed" -> "失败"
    "cancelled" -> "已取消"
    "interrupted" -> "已中断"
    else -> status
}

private fun workflowMemberStatusLabel(member: AgentWorkflowMember, runStatus: String): String =
    workflowStatusLabel(member.outcome ?: if (runStatus == "interrupted") "interrupted" else "running")

private fun workflowPhaseLabel(phase: String?): String = when (phase) {
    null -> "未分组"
    "" -> "未命名阶段"
    else -> phase
}

@Composable
private fun workflowStatusColor(status: String): Color = when (status) {
    "completed" -> Success
    "failed" -> Red
    else -> Amber
}

@Composable
private fun ToolCard(
    tool: ToolItem,
    imageData: (AgentImage) -> String? = { null },
    onLoadImage: (AgentImage) -> Unit = {}
) {
    var expanded by rememberSaveable(tool.key) { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tool.images) {
        tool.images.forEach { if (imageData(it) == null) onLoadImage(it) }
    }
    val dotColor = when {
        tool.running -> Amber
        tool.isError -> Red
        else -> Success
    }
    // The card shares the transcript's left edge. An indent here reads as a right-side
    // bubble, the same shape as the user's own messages.
    Column(
        Modifier
            .fillMaxWidth()
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
            toolTiming(tool)?.let { timing ->
                Spacer(Modifier.width(6.dp))
                Text(timing, color = TextMuted, fontSize = 9.5.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (expanded) "收起" else "详情",
                color = TextMuted,
                fontSize = 10.sp
            )
        }
        if (tool.images.isNotEmpty()) {
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tool.images.take(4).forEach { image ->
                    val url = imageData(image)
                    AgentThumb(
                        dataUrl = url,
                        size = 132.dp,
                        onClick = url?.let { { preview = it } }
                    )
                }
            }
        }
        if (expanded) {
            ToolDetailBody(tool)
        }
    }
    preview?.let { ImagePreviewDialog(it) { preview = null } }
}

/** Expanded tool content: structured cards where dsh's data supports them, else raw I/O. */
@Composable
private fun ToolDetailBody(tool: ToolItem) {
    val view = remember(
        tool.key, tool.arguments, tool.resultText, tool.metaJson, tool.running, tool.isError
    ) {
        ToolView.of(tool)
    }
    when (view) {
        is ToolView.Shell -> ShellDetail(tool, view)
        is ToolView.Diff -> DiffDetail(tool, view)
        is ToolView.ReadFile -> ReadDetail(tool, view)
        is ToolView.Grep -> GrepDetail(tool, view)
        is ToolView.Glob -> GlobDetail(tool, view)
        is ToolView.WebCall -> {
            view.url?.let { ToolSection("地址") { CodeText(it, TextSecondary) } }
            view.query?.let { ToolSection("查询") { CodeText(it, TextSecondary) } }
            RawToolIo(tool, argumentsOnly = view.url == null && view.query == null)
        }
        is ToolView.Generic -> RawToolIo(tool)
    }
}

@Composable
private fun ShellDetail(tool: ToolItem, view: ToolView.Shell) {
    ToolSection("命令") { CodeText(view.command, TextSecondary) }
    view.description?.let {
        Text(
            it,
            color = TextMuted,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    view.workdir?.let { MetaLine("工作目录 · $it") }
    val output = view.output ?: tool.resultText.takeIf { !tool.running }
    if (!output.isNullOrBlank()) {
        ToolSection("输出") {
            CodeText(output.take(12_000), if (tool.isError) Red else TextSecondary)
        }
    }
    if (tool.running) {
        MetaLine("运行中…")
    } else if (view.signal != null) {
        MetaLine("已终止 · ${view.signal}", Red)
    } else if (view.exitCode != null && view.exitCode != 0) {
        MetaLine("退出码 ${view.exitCode}", Red)
    } else if (view.exitCode != null) {
        MetaLine("退出码 0", TextMuted)
    }
}

@Composable
private fun DiffDetail(tool: ToolItem, view: ToolView.Diff) {
    view.hunks.take(4).forEach { hunk ->
        val removed = hunk.oldText?.lines().orEmpty().size
        val added = hunk.newText.lines().size
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                hunk.path,
                modifier = Modifier.weight(1f),
                color = TextSecondary,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "+$added  −$removed",
                color = TextMuted,
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(3.dp))
        DiffBlock(hunk)
    }
    if (view.hunks.size > 4) {
        MetaLine("另有 ${view.hunks.size - 4} 个文件未展开")
    }
    MetaLine(if (view.applied) "已应用修改" else if (tool.running) "等待执行" else "未应用")
}

/** Removed lines, then added lines: the whole-hunk form dsh's diff metadata describes. */
@Composable
private fun DiffBlock(hunk: ToolView.Diff.Hunk) {
    val old = hunk.oldText?.lines().orEmpty()
    val added = hunk.newText.lines()
    Column {
        if (old.isNotEmpty()) {
            Text(
                old.take(120).joinToString("\n") { "- $it" },
                color = Red.copy(alpha = 0.9f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
        Text(
            added.take(120).joinToString("\n") { "+ $it" },
            color = Success,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
        if (old.size > 120 || added.size > 120) {
            Text("（已截断）", color = TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ReadDetail(tool: ToolItem, view: ToolView.ReadFile) {
    MetaLine("文件 · ${view.path}")
    val lines = view.lines
    if (lines.isNullOrEmpty()) {
        RawToolIo(tool, argumentsOnly = false)
        return
    }
    val first = view.offset ?: lines.first().number
    val last = lines.last().number
    MetaLine("第 $first–$last 行" + (view.totalLines?.let { " · 共 $it 行" } ?: ""))
    Spacer(Modifier.height(6.dp))
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        lines.take(160).forEach { line ->
            Row {
                Text(
                    line.number.toString(),
                    modifier = Modifier.width(34.dp),
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    line.text,
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false
                )
            }
        }
    }
    if (lines.size > 160) MetaLine("仅显示前 160 行")
}

@Composable
private fun GrepDetail(tool: ToolItem, view: ToolView.Grep) {
    MetaLine(
        buildString {
            append("模式 · ")
            append(view.pattern)
            view.include?.let { append("  文件 · $it") }
            view.path?.let { append("  目录 · $it") }
        }
    )
    view.files?.let { files ->
        val total = view.total ?: files.sumOf { it.matches.size }
        MetaLine("$total 处匹配 · ${files.size} 个文件" + if (view.truncated) "（已截断）" else "")
        files.take(6).forEach { file ->
            Spacer(Modifier.height(7.dp))
            Text(
                file.path,
                color = TextSecondary,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            file.matches.take(5).forEach { match ->
                Row {
                    Text(
                        "${match.lineNumber}",
                        modifier = Modifier.width(30.dp),
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        match.line.trim(),
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (files.size > 6) MetaLine("另有 ${files.size - 6} 个文件未展开")
    } ?: RawToolIo(tool, argumentsOnly = false)
}

@Composable
private fun GlobDetail(tool: ToolItem, view: ToolView.Glob) {
    MetaLine(
        "模式 · ${view.pattern}" + (view.path?.let { "  目录 · $it" } ?: "")
    )
    view.paths?.let { paths ->
        MetaLine("${view.total ?: paths.size} 个结果" + if (view.truncated) "（已截断）" else "")
        Text(
            paths.take(40).joinToString("\n"),
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            lineHeight = 15.sp
        )
        if (paths.size > 40) MetaLine("另有 ${paths.size - 40} 个未显示")
    } ?: RawToolIo(tool, argumentsOnly = false)
}

/** The pre-card rendering, kept as the fallback for tools without a structured view. */
@Composable
private fun RawToolIo(tool: ToolItem, argumentsOnly: Boolean = false) {
    if (tool.arguments.isNotBlank() && tool.arguments != "{}" && tool.arguments != "[]") {
        ToolSection("参数") { CodeText(tool.arguments, TextSecondary) }
    }
    if (!argumentsOnly && tool.resultText.isNotBlank()) {
        ToolSection(if (tool.isError) "错误" else "输出") {
            CodeText(
                tool.resultText.take(12_000),
                if (tool.isError) Red else TextSecondary
            )
        }
    }
}

@Composable
private fun ToolSection(label: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(9.dp))
    Label(label)
    content()
}

@Composable
private fun CodeText(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.5.sp,
        lineHeight = 15.sp
    )
}

@Composable
private fun MetaLine(text: String, color: Color = TextMuted) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        modifier = Modifier.padding(top = 6.dp)
    )
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
    "off", "none" -> "不推理"
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
    "run_code" -> "执行代码"
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

/** Status word and elapsed time for a tool call, when dsh stamped both ends. */
private fun toolTiming(tool: ToolItem): String? {
    val status = when {
        tool.running -> "运行中"
        tool.isError -> "失败"
        else -> null
    }
    val duration = tool.elapsedMs?.let { formatDuration(it) }
    return listOfNotNull(status, duration).joinToString(" · ").ifBlank { null }
}

@Composable
private fun NoticeRow(item: NoticeItem) {
    var expanded by remember(item.key) { mutableStateOf(false) }
    val meta = buildString {
        item.foldedItems?.let {
            append("${it} 条记录")
        }
        item.foldedTokens?.let {
            if (isNotEmpty()) append(" · ")
            append("约 ${formatTokens(it)} tokens")
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = item.detail != null) { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.text,
                color = if (item.isError) Red.copy(alpha = 0.9f) else TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
            if (meta.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(meta, color = TextMuted.copy(alpha = 0.75f), fontSize = 9.5.sp)
            }
            if (item.detail != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    if (expanded) "收起" else "摘要",
                    color = TextMuted.copy(alpha = 0.75f),
                    fontSize = 9.5.sp
                )
            }
        }
        if (expanded && item.detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                item.detail,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceVariant)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun TypingRow() {
    Row(
        Modifier.fillMaxWidth(),
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
                        platformStyle = com.kingkang.app.ui.components.CompactInputPlatformStyle,
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

/**
 * The composer's context gauge. dsh reports projected/context-window tokens per turn;
 * tapping the chip opens the full stats dialog. Without a window the gauge is omitted
 * and the chip shows the raw count, since a fraction against an unknown denominator
 * would be made up.
 */
@Composable
private fun ContextChip(pressure: AgentContextPressure, onClick: () -> Unit) {
    val fraction = pressure.fraction
    // One sentence for TalkBack: the raw token pair reads as two bare numbers, and the
    // bar's threshold colour carries no text at all.
    val spoken = buildString {
        append("上下文已用 ")
        append(formatTokens(pressure.usedTokens))
        pressure.contextWindow?.let { window ->
            append("，共 ")
            append(formatTokens(window))
            fraction?.let { append("，占 ${formatPercent(it)}") }
        }
        append("。点按查看用量详情")
    }
    val open = onClick
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = "查看用量详情") {
                    open()
                    true
                }
            }
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            buildString {
                append(formatTokens(pressure.usedTokens))
                pressure.contextWindow?.let {
                    append(" / ")
                    append(formatTokens(it))
                }
            },
            color = TextMuted,
            fontSize = 10.5.sp,
            maxLines = 1
        )
        if (fraction != null) {
            Spacer(Modifier.width(6.dp))
            ContextGauge(fraction = fraction, width = 26.dp)
        }
    }
}

@Composable
private fun ContextGauge(fraction: Float, width: Dp? = null, height: Dp = 3.dp) {
    val fill = fraction.coerceIn(0f, 1f)
    val widthModifier = if (width == null) Modifier.fillMaxWidth() else Modifier.width(width)
    Box(
        widthModifier
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(SurfaceVariant)
    ) {
        if (fill > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .height(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            fill >= 0.9f -> Red
                            fill >= 0.7f -> Amber
                            else -> TextSecondary.copy(alpha = 0.75f)
                        }
                    )
            )
        }
    }
}

/**
 * Token accounting for the conversation on screen. Every count is a projection dsh last
 * reported; a bucket that has never been reported renders as an em dash rather than 0.
 */
@Composable
private fun ContextStatsDialog(
    usage: AgentTokenUsage,
    pressure: AgentContextPressure,
    breakdown: AgentContextBreakdown,
    stats: AgentSessionStats,
    selection: AgentModelSelection,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val fraction = pressure.fraction
    val scroll = rememberScrollState()
    KkDialog(onDismiss = onDismiss) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "上下文与用量",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "最近一次上报",
                        color = TextMuted,
                        fontSize = 10.5.sp
                    )
                }
                TextButton(onClick = onRefresh) {
                    Text("刷新", color = TextSecondary)
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = TextSecondary)
                }
            }
            Spacer(Modifier.height(4.dp))
            ScrollEdgeFade(
                canScrollForward = scroll.canScrollForward,
                modifier = Modifier.fillMaxWidth()
            ) {
            Column(
                Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(scroll)
            ) {
                StatSection("模型")
                StatWideRow("当前模型", modelSelectionLabel(selection))

                StatSection("上下文")
                StatRow(
                    "已用 / 窗口",
                    buildString {
                        append(formatTokens(pressure.usedTokens))
                        pressure.contextWindow?.let {
                            append(" / ")
                            append(formatTokens(it))
                        }
                    }
                )
                StatRow("占用", formatPercent(fraction))
                if (fraction != null) {
                    Spacer(Modifier.height(5.dp))
                    ContextGauge(fraction = fraction, height = 4.dp)
                }

                StatSection("Token 用量")
                val usageKnown = usage.hasData
                StatRow("未缓存输入", formatTokens(if (usageKnown) usage.uncachedInputTokens else null))
                StatRow("缓存读取", formatTokens(if (usageKnown) usage.cacheReadTokens else null))
                StatRow("缓存写入", formatTokens(if (usageKnown) usage.cacheWriteTokens else null))
                StatRow("输出", formatTokens(if (usageKnown) usage.outputTokens else null))
                StatRow(
                    "合计",
                    formatTokens(if (usageKnown) usage.totalTokens else null),
                    emphasis = true
                )

                if (breakdown.hasData) {
                    StatSection("上下文构成")
                    StatRow("系统提示", formatTokens(breakdown.systemTokens))
                    StatRow("工具定义", formatTokens(breakdown.toolsTokens))
                    StatRow("消息", formatTokens(breakdown.messageTokens))
                }

                if (stats.hasData) {
                    StatSection("本会话")
                    StatRow("轮次 / 步骤", "${stats.turns} / ${stats.steps}")
                    StatRow("模型耗时", formatDuration(stats.llmMs))
                    StatRow("工具耗时", formatDuration(stats.toolMs))
                    StatRow("首字延迟", formatDuration(stats.ttftMs))
                    StatRow(
                        "解码速度",
                        stats.decodeTps?.let {
                            String.format(java.util.Locale.US, "%.1f tok/s", it)
                        } ?: "—"
                    )
                }
            }
            }
    }
}

/** `provider / model · effort`, with dsh's effort ids spelled out in Chinese. */
private fun modelSelectionLabel(selection: AgentModelSelection): String {
    val base = selection.label.ifBlank { "—" }
    val effort = selection.reasoningEffort?.takeIf { it.isNotBlank() }?.let { id ->
        when (id.lowercase()) {
            "minimal" -> "最低"
            "low" -> "低"
            "medium" -> "中"
            "high" -> "高"
            "xhigh", "max" -> "极高"
            else -> id
        }
    }
    return if (effort == null) base else "$base · $effort"
}

@Composable
private fun StatSection(title: String) {
    Text(
        title,
        color = TextMuted,
        fontSize = 9.5.sp,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 3.dp)
    )
}

@Composable
private fun StatRow(label: String, value: String, emphasis: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.45f),
            color = TextSecondary,
            fontSize = 12.sp,
            maxLines = 1
        )
        Text(
            value,
            modifier = Modifier.weight(0.55f),
            color = if (emphasis) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A two-line stat used when the value is a full model name: the label stays on its own
 * line instead of being squeezed into one character per line by a long monospace value.
 */
@Composable
private fun StatWideRow(label: String, value: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, color = TextMuted, fontSize = 10.sp)
        Text(
            value,
            color = TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 2.dp)
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
    stopping: Boolean = false,
    steerable: Boolean = true,
    hint: String? = null,
    models: AgentModels? = null,
    permissionMode: String? = null,
    permissionOptions: List<AgentPermissionOption> = emptyList(),
    tokenUsage: AgentTokenUsage = AgentTokenUsage(),
    pressure: AgentContextPressure = AgentContextPressure(),
    suggestionsEnabled: Boolean = true,
    seed: String? = null,
    onSeedConsumed: () -> Unit = {},
    onPickModel: () -> Unit = {},
    onPickPermission: () -> Unit = {},
    onShowStats: () -> Unit = {},
    onSend: (String, List<AgentDraftImage>, List<AgentDraftFile>) -> Unit,
    onSteer: (String, List<AgentDraftImage>, List<AgentDraftFile>) -> Unit,
    onRefreshCatalogs: () -> Unit,
    onLoadFileReferences: suspend (String) -> List<AgentFileReference> = { emptyList() },
    onLoadSessionReferences: suspend (String) -> List<AgentSessionReference> = { emptyList() },
    onPreviewFile: (AgentFile) -> Unit = {},
    onSpeechCatalog: suspend () -> AgentSpeechCatalog = { AgentSpeechCatalog() },
    onPrepareSpeech: suspend (String) -> Unit = {},
    onCancelSpeechPreparation: suspend (String) -> Unit = {},
    onTranscribeSpeech: suspend (ByteArray, String?, String?) -> String = { _, _, _ -> "" },
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
    // Staging a large file can take a moment; the composer reports which file is being
    // copied, lets the user stop, and keeps failed URIs for a one-tap retry.
    var staging by remember(sessionId) { mutableStateOf(false) }
    var stagingDone by remember(sessionId) { mutableStateOf(0) }
    var stagingTotal by remember(sessionId) { mutableStateOf(0) }
    var stagingCancel by remember(sessionId) { mutableStateOf(false) }
    var retryUris by remember(sessionId) { mutableStateOf(emptyList<Uri>()) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The `/` menu's own state: the persisted recent-use ordering, the keyboard-selected
    // row, a per-draft dismissal, and the validation notice that keeps a draft in place.
    val slashHistory = remember(context) { SlashHistory.of(context) }
    var recentSlash by remember(context) { mutableStateOf(slashHistory.names()) }
    var slashIndex by remember(sessionId) { mutableStateOf(0) }
    var slashMutedFor by remember(sessionId) { mutableStateOf<String?>(null) }
    var slashNotice by remember(sessionId) { mutableStateOf<String?>(null) }

    fun startStaging(chosen: List<Uri>, dropped: Int = 0) {
        if (staging) return
        attachError = null
        retryUris = emptyList()
        staging = true
        stagingCancel = false
        stagingDone = 0
        stagingTotal = chosen.size
        scope.launch {
            try {
                val batch = withContext(Dispatchers.IO) {
                    AttachmentStaging.stage(
                        context = context,
                        uris = chosen,
                        onProgress = { done, total ->
                            scope.launch {
                                stagingDone = done
                                stagingTotal = total
                            }
                        },
                        // A cancelled composer scope stops the copy too; staging then
                        // removes the whole partial batch from the shared directory.
                        isCancelled = { stagingCancel || !isActive }
                    )
                }
                if (batch.cancelled) {
                    attachError = "已取消，没有添加文件"
                    return@launch
                }
                if (batch.files.isNotEmpty()) {
                    files = (files + batch.files).take(AttachmentStaging.MAX_FILES)
                }
                if (batch.failed.isNotEmpty()) retryUris = batch.failed
                attachError = when {
                    dropped > 0 ->
                        "最多附加 ${AttachmentStaging.MAX_FILES} 个文件，已保留前 ${chosen.size} 个"
                    batch.failed.isNotEmpty() ->
                        "有 ${batch.failed.size} 个文件未能添加：单个文件不超过 64 MB"
                    else -> null
                }
            } finally {
                staging = false
            }
        }
    }

    // Voice input: record into memory, let dsh's speech Remote transcribe, then insert the
    // text at the cursor. The audio never reaches disk and is dropped after the request.
    var recording by remember(sessionId) { mutableStateOf(false) }
    var voiceSeconds by remember(sessionId) { mutableStateOf(0f) }
    var voiceLevel by remember(sessionId) { mutableStateOf(0f) }
    var voiceStopping by remember(sessionId) { mutableStateOf(false) }
    var voiceTranscribing by remember(sessionId) { mutableStateOf(false) }
    var voiceError by remember(sessionId) { mutableStateOf<String?>(null) }
    var voiceStopRequested by remember(sessionId) { mutableStateOf(false) }
    var voiceCancelRequested by remember(sessionId) { mutableStateOf(false) }
    var voiceProviderId by remember(sessionId) { mutableStateOf<String?>(null) }
    var voiceLanguage by remember(sessionId) { mutableStateOf("") }
    var voiceMaxSeconds by remember(sessionId) { mutableStateOf(60) }
    var voiceMaxBytes by remember(sessionId) { mutableStateOf(10L * 1024 * 1024) }
    var voiceJob by remember(sessionId) { mutableStateOf<Job?>(null) }
    var speechSetup by remember(sessionId) { mutableStateOf<AgentSpeechProvider?>(null) }
    var voiceWantsRecord by remember(sessionId) { mutableStateOf(false) }

    fun insertVoiceText(text: String) {
        if (text.isBlank()) return
        val current = input
        val cursor = current.selection.end.coerceIn(0, current.text.length)
        val next = current.text.substring(0, cursor) + text + current.text.substring(cursor)
        input = TextFieldValue(next, selection = TextRange(cursor + text.length))
    }

    fun beginRecording(providerId: String?, language: String) {
        if (recording || voiceTranscribing) return
        voiceError = null
        voiceCancelRequested = false
        voiceStopRequested = false
        voiceStopping = false
        voiceSeconds = 0f
        voiceLevel = 0f
        voiceProviderId = providerId
        voiceLanguage = language
        recording = true
        voiceJob = scope.launch {
            var wav: ByteArray? = null
            var captureError: String? = null
            try {
                wav = captureSpeechWav(
                    maxSeconds = voiceMaxSeconds,
                    maxBytes = voiceMaxBytes,
                    isStopRequested = { voiceStopRequested || voiceCancelRequested || !isActive },
                    onTick = { seconds, level ->
                        scope.launch {
                            voiceSeconds = seconds
                            voiceLevel = level
                        }
                    }
                )
            } catch (_: CancellationException) {
                return@launch
            } catch (t: Throwable) {
                captureError = t.message ?: "录音失败"
            } finally {
                recording = false
                voiceStopping = false
            }
            if (voiceCancelRequested) {
                voiceCancelRequested = false
                return@launch
            }
            if (captureError != null) {
                voiceError = captureError
                return@launch
            }
            if (wav == null) {
                voiceError = "录音太短，没有可识别的内容"
                return@launch
            }
            voiceTranscribing = true
            try {
                val text = onTranscribeSpeech(wav, voiceProviderId, voiceLanguage)
                if (text.isBlank()) voiceError = "没有识别到语音内容"
                else insertVoiceText(text.trim())
            } catch (_: CancellationException) {
                return@launch
            } catch (t: Throwable) {
                voiceError = "语音识别失败：${t.message ?: "未知错误"}"
            } finally {
                voiceTranscribing = false
            }
        }
    }

    fun requestVoiceStart() {
        if (recording || voiceTranscribing) return
        voiceError = null
        voiceWantsRecord = true
        scope.launch {
            val catalog = try {
                onSpeechCatalog()
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                voiceWantsRecord = false
                voiceError = "语音识别不可用：${t.message ?: "dsh 没有响应"}"
                return@launch
            }
            val provider = catalog.selected
            if (provider == null) {
                voiceWantsRecord = false
                voiceError = "dsh 没有可用的语音识别提供方"
                return@launch
            }
            voiceMaxSeconds = catalog.maxDurationSeconds.coerceIn(5, 120)
            voiceMaxBytes = catalog.maxAudioBytes.takeIf { it > 0 } ?: (10L * 1024 * 1024)
            if (provider.ready) {
                voiceWantsRecord = false
                beginRecording(provider.id, catalog.language)
            } else {
                speechSetup = provider
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) requestVoiceStart()
        else voiceError = "需要麦克风权限才能使用语音输入"
    }

    fun requestMicrophone() {
        if (recording || voiceTranscribing) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) requestVoiceStart() else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // The microphone follows the composer, not the process: leaving the app, switching
    // conversations or losing the screen cancels the capture rather than recording on.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                voiceCancelRequested = true
                voiceStopRequested = true
                voiceStopping = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(sessionId) {
        onDispose {
            voiceJob?.cancel()
            recording = false
            voiceTranscribing = false
        }
    }

    // The setup gate follows the Host-owned task: the dialog updates while a model downloads
    // and closes itself the moment the provider is ready, if the mic tap is still pending.
    val setupProviderId = speechSetup?.id
    LaunchedEffect(setupProviderId) {
        if (setupProviderId == null) return@LaunchedEffect
        while (true) {
            delay(2_000)
            val catalog = try {
                onSpeechCatalog()
            } catch (_: CancellationException) {
                return@LaunchedEffect
            } catch (_: Throwable) {
                continue
            }
            val provider = catalog.providers.firstOrNull { it.id == setupProviderId }
            if (provider == null) {
                speechSetup = null
                voiceWantsRecord = false
                voiceError = "语音提供方已不可用"
                return@LaunchedEffect
            }
            voiceMaxSeconds = catalog.maxDurationSeconds.coerceIn(5, 120)
            voiceMaxBytes = catalog.maxAudioBytes.takeIf { it > 0 } ?: voiceMaxBytes
            speechSetup = provider
            if (provider.ready) {
                speechSetup = null
                if (voiceWantsRecord) {
                    voiceWantsRecord = false
                    beginRecording(provider.id, catalog.language)
                }
                return@LaunchedEffect
            }
        }
    }

    if (speechSetup != null) {
        SpeechSetupDialog(
            provider = speechSetup,
            onPrepare = { provider ->
                voiceError = null
                scope.launch {
                    try {
                        onPrepareSpeech(provider.id)
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        voiceError = "无法开始准备：${t.message ?: "未知错误"}"
                    }
                }
            },
            onCancelPreparation = { provider ->
                scope.launch {
                    try {
                        onCancelSpeechPreparation(provider.id)
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        voiceError = "无法取消准备：${t.message ?: "未知错误"}"
                    }
                }
            },
            onDismiss = {
                speechSetup = null
                voiceWantsRecord = false
            }
        )
    }

    // A quote lands here as a Markdown blockquote; keep whatever the user already typed
    // and append the quote below it, ready for the reply.
    LaunchedEffect(seed) {
        val text = seed ?: return@LaunchedEffect
        if (text.isBlank()) return@LaunchedEffect
        val existing = input.text.trimEnd()
        val combined = if (existing.isEmpty()) text else "$existing\n\n$text"
        input = TextFieldValue(combined, selection = TextRange(combined.length))
        onSeedConsumed()
        runCatching { focusRequester.requestFocus() }
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        attachError = null
        scope.launch {
            val room = (MAX_DRAFT_IMAGES - drafts.size).coerceAtLeast(0)
            if (room == 0) {
                attachError = "最多附加 ${MAX_DRAFT_IMAGES} 张图片"
                return@launch
            }
            val loaded = withContext(Dispatchers.IO) {
                uris.take(room).mapNotNull { readDraftImage(context, it) }
            }
            attachError = when {
                uris.size > room ->
                    "最多附加 ${MAX_DRAFT_IMAGES} 张图片，已保留前 $room 张"
                loaded.size < uris.take(room).size ->
                    "有图片无法使用：仅支持 PNG / JPEG / WEBP / GIF，单张不超过 8 MB"
                else -> null
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
        val room = (AttachmentStaging.MAX_FILES - files.size).coerceAtLeast(0)
        if (room == 0) {
            attachError = "最多附加 ${AttachmentStaging.MAX_FILES} 个文件"
            return@rememberLauncherForActivityResult
        }
        startStaging(uris.take(room), dropped = (uris.size - room).coerceAtLeast(0))
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
        if (staging || recording || voiceTranscribing) return
        if (!enabled || (text.isEmpty() && drafts.isEmpty() && files.isEmpty())) return
        // dsh refuses attachments on commands that do not declare them. Refuse here first
        // so the draft and its attachments survive for correction instead of vanishing.
        slashCommandAt(text, commands)?.let { command ->
            slashAttachmentNotice(command, drafts.size, files.size)?.let { notice ->
                slashNotice = notice
                return
            }
        }
        slashNotice = null
        val images = drafts
        val attached = files
        if (running && steer) onSteer(text, images, attached) else onSend(text, images, attached)
        // Remember what was actually sent (not merely highlighted), so the menu's recent
        // ordering reflects use rather than browsing.
        slashTokenName(text)?.let { name ->
            if (commands.any { it.name.equals(name, ignoreCase = true) } ||
                skills.any { it.name.equals(name, ignoreCase = true) }
            ) {
                slashHistory.record(name)
                recentSlash = slashHistory.names()
            }
        }
        slashMutedFor = null
        input = TextFieldValue("")
        drafts = emptyList()
        files = emptyList()
    }

    // dsh's `@` token grammar drives the picker: a word-initial @ opens it, an email does
    // not, and a quoted path may contain spaces. Failures are surfaced instead of looking
    // like an empty workspace.
    val atToken = if (suggestionsEnabled) activeAtToken(input.text, input.selection.end) else null
    var atFiles by remember(sessionId) { mutableStateOf(emptyList<AgentFileReference>()) }
    var atSessions by remember(sessionId) { mutableStateOf(emptyList<AgentSessionReference>()) }
    var atLoading by remember(sessionId) { mutableStateOf(false) }
    var atError by remember(sessionId) { mutableStateOf<String?>(null) }
    LaunchedEffect(atToken?.query, atToken?.quoted, sessionId) {
        val token = atToken
        if (token == null) {
            atFiles = emptyList()
            atSessions = emptyList()
            atLoading = false
            atError = null
            return@LaunchedEffect
        }
        atLoading = true
        atError = null
        // A short pause between keystrokes; dsh's index is cheap but the bridge should not
        // see one request per typed character.
        delay(180)
        try {
            val files = onLoadFileReferences(token.query)
            val sessions = if (token.quoted) emptyList() else onLoadSessionReferences(token.query)
            atFiles = files
            atSessions = sessions
            atLoading = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            atFiles = emptyList()
            atSessions = emptyList()
            atLoading = false
            atError = t.message?.takeIf { it.isNotBlank() } ?: "无法读取引用"
        }
    }

    fun insertAtToken(text: String) {
        val token = atToken ?: return
        val before = input.text.substring(0, token.start)
        val after = input.text.substring(token.end)
        val next = before + text + after
        input = TextFieldValue(next, selection = TextRange(before.length + text.length))
        focusRequester.requestFocus()
    }

    fun pickFile(file: AgentFileReference) {
        val token = atToken ?: return
        val mention = formatFileMention(file.path, file.directory, token.quoted) ?: return
        // A directory keeps the picker open: the mention ends in `/` (and may keep its
        // quote open), so the token is still active and the next query lists its children.
        insertAtToken(if (file.directory) mention else "$mention ")
    }

    fun pickSession(reference: AgentSessionReference) {
        insertAtToken("${reference.mention} ")
    }

    // Inserted mentions are ordinary prompt text, exactly as dsh-web serializes them. The
    // chips above the field are a view over that text, so editing the text and rotating
    // the screen can never disagree with what will be sent.
    val mentionChips = remember(input.text) { composerMentions(input.text) }
    val visibleMentions = if (atToken == null) mentionChips else emptyList()

    fun removeMention(range: IntRange) {
        val text = input.text
        if (range.first < 0 || range.last >= text.length) return
        val end = if (text.getOrNull(range.last + 1) == ' ') range.last + 2 else range.last + 1
        val next = text.removeRange(range.first, end)
        input = TextFieldValue(next, selection = TextRange(range.first.coerceIn(0, next.length)))
    }

    // Command discovery appears only while the first token is still being typed, so it
    // never covers the transcript once the user has moved on to arguments.
    val typedName = input.text.takeIf {
        suggestionsEnabled && it.startsWith("/") && it.none(Char::isWhitespace)
    }?.drop(1)
    // Escape dismisses the menu for this exact draft; the next edit opens it again.
    val slashOpen = typedName != null && slashMutedFor != input.text &&
        drafts.isEmpty() && files.isEmpty()
    val suggestions = remember(commands, skills, typedName, recentSlash, drafts.size, files.size) {
        if (typedName != null) {
            slashSuggestions(
                commands = commands,
                skills = skills,
                typed = typedName,
                recent = recentSlash,
                images = drafts.size,
                files = files.size
            )
        } else {
            emptyList()
        }
    }
    LaunchedEffect(typedName, suggestions) { slashIndex = 0 }
    LaunchedEffect(typedName != null) {
        if (typedName != null) onRefreshCatalogs()
    }

    fun pickSuggestion(suggestion: SlashSuggestion) {
        val inserted = "/${suggestion.name} "
        input = TextFieldValue(text = inserted, selection = TextRange(inserted.length))
        slashMutedFor = null
        focusRequester.requestFocus()
    }

    val modelName = models?.current?.name ?: models?.model
    val effortText = if (models?.current?.efforts.isNullOrEmpty()) {
        null
    } else {
        models?.currentEffort?.let(::effortLabel) ?: "默认"
    }
    val permissionText = permissionMode?.let { permissionLabel(it, permissionOptions) } ?: "权限"
    // The context gauge replaces the empty left gutter only when dsh has numbers to show;
    // a session that has not yet made a call keeps the ruler blank rather than a zero.
    val showContext = pressure.usedTokens != null || tokenUsage.hasData
    val showToolbar = !modelName.isNullOrBlank() || permissionOptions.isNotEmpty() || showContext

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
                    if (stopping) "正在停止当前任务…"
                    else if (steerable) "当前任务运行中" else "子 Agent 运行中",
                    color = TextMuted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.weight(1f))
                if (steerable && !stopping) {
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
        if (drafts.isNotEmpty() || files.isNotEmpty() || visibleMentions.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleMentions.forEach { mention ->
                    MentionChip(
                        label = mention.label,
                        session = mention.session,
                        onRemove = { removeMention(mention.range) }
                    )
                }
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
                        },
                        onClick = {
                            onPreviewFile(
                                AgentFile(
                                    name = file.name,
                                    mimeType = file.mimeType,
                                    size = file.size,
                                    guestPath = file.guestPath,
                                    hostPath = file.hostPath
                                )
                            )
                        }
                    )
                }
            }
        }
        if (staging) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 7.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = TextMuted,
                    strokeWidth = 1.4.dp,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (stagingTotal > 0) "正在准备文件 $stagingDone/$stagingTotal"
                    else "正在准备文件…",
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { stagingCancel = true }, enabled = !stagingCancel) {
                    Text(
                        if (stagingCancel) "取消中…" else "取消",
                        color = if (stagingCancel) TextMuted else TextSecondary,
                        fontSize = 10.5.sp
                    )
                }
            }
        }
        attachError?.let { message ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 7.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    message,
                    color = Amber,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                if (retryUris.isNotEmpty() && !staging) {
                    TextButton(
                        onClick = {
                            val room = (AttachmentStaging.MAX_FILES - files.size)
                                .coerceAtLeast(0)
                            val pending = retryUris
                            retryUris = emptyList()
                            if (room > 0) {
                                startStaging(pending.take(room), dropped = pending.size - room)
                            } else {
                                attachError = "最多附加 ${AttachmentStaging.MAX_FILES} 个文件"
                            }
                        }
                    ) {
                        Text("重试", color = TextSecondary, fontSize = 10.5.sp)
                    }
                }
            }
        }
        if (atToken != null) {
            AtReferencePanel(
                query = atToken.query,
                files = atFiles,
                sessions = atSessions,
                loading = atLoading,
                error = atError,
                onPickFile = ::pickFile,
                onPickSession = ::pickSession
            )
        }
        if (slashOpen) {
            SlashMenuPanel(
                typed = typedName.orEmpty(),
                suggestions = suggestions,
                selected = slashIndex,
                onPick = ::pickSuggestion
            )
        }
        // Once a command's token is claimed with a space, dsh's own argument syntax takes
        // the placeholder's place until the user starts typing arguments.
        if (!slashOpen) {
            slashParamHint(input.text, commands)?.let { hint ->
                Text(
                    hint,
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 12.dp, bottom = 4.dp)
                )
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
                if (showContext) {
                    ContextChip(pressure = pressure, onClick = onShowStats)
                }
                Spacer(Modifier.weight(1f))
                if (!modelName.isNullOrBlank()) {
                    // One control for both halves of the choice; the dialog shows the model
                    // with its reasoning level and lets either be changed without leaving it.
                    ComposerChip(
                        text = if (effortText != null) "$modelName · $effortText" else modelName,
                        enabled = enabled,
                        maxWidth = 220.dp,
                        onClick = onPickModel
                    )
                }
            }
        }
        if (recording) {
            VoiceRecordingDock(
                seconds = voiceSeconds,
                level = voiceLevel,
                stopping = voiceStopping,
                onStop = {
                    voiceStopping = true
                    voiceStopRequested = true
                },
                onCancel = {
                    voiceStopping = true
                    voiceCancelRequested = true
                    voiceStopRequested = true
                }
            )
        }
        if (voiceTranscribing) {
            VoiceTranscribingDock(onCancel = { voiceJob?.cancel() })
        }
        voiceError?.let { message ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 7.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    message,
                    color = Amber,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { voiceError = null }) {
                    Text("知道了", color = TextSecondary, fontSize = 10.5.sp)
                }
            }
        }
        slashNotice?.let { message ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 7.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    message,
                    color = Amber,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { slashNotice = null }) {
                    Text("知道了", color = TextSecondary, fontSize = 10.5.sp)
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
            val canAddFile = enabled && !staging && files.size < AttachmentStaging.MAX_FILES
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
                    // The composer row is taller than one line because of its round
                    // buttons. Let the text keep its own height and centre it in that
                    // row; otherwise the caret starts at the row's top edge.
                    .heightIn(min = 34.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    // Hardware up/down move the menu highlight; Enter/Tab settle it and
                    // Escape dismisses it for this draft, matching dsh-web's menu keys.
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || !slashOpen) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (suggestions.isEmpty()) false else {
                                    val size = suggestions.size
                                    slashIndex = (slashIndex.coerceIn(0, size - 1) + 1) % size
                                    true
                                }
                            }
                            Key.DirectionUp -> {
                                if (suggestions.isEmpty()) false else {
                                    val size = suggestions.size
                                    slashIndex = (slashIndex.coerceIn(0, size - 1) - 1 + size) % size
                                    true
                                }
                            }
                            Key.Enter, Key.NumPadEnter, Key.Tab -> {
                                val pick = suggestions.getOrNull(
                                    slashIndex.coerceIn(0, suggestions.lastIndex.coerceAtLeast(0))
                                )
                                if (pick == null) false else {
                                    pickSuggestion(pick)
                                    true
                                }
                            }
                            Key.Escape -> {
                                slashMutedFor = input.text
                                true
                            }
                            else -> false
                        }
                    }
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontSize = 15.sp,
                    platformStyle = com.kingkang.app.ui.components.CompactInputPlatformStyle,
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
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = com.kingkang.app.ui.components.CompactInputPlatformStyle
                            )
                        )
                    }
                    inner()
                }
            )
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(micBackground(recording))
                    .clickable(
                        enabled = enabled && !recording && !voiceTranscribing,
                        onClick = { requestMicrophone() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                MicGlyph(
                    tint = micTint(enabled, recording),
                    modifier = Modifier.size(17.dp)
                )
            }
            if (running) {
                Spacer(Modifier.width(5.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SurfaceVariant)
                        .clickable(enabled = !stopping, onClick = onStop),
                    contentAlignment = Alignment.Center
                ) {
                    if (stopping) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 1.5.dp,
                            color = TextSecondary
                        )
                    } else {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TextSecondary)
                        )
                    }
                }
            }
            Spacer(Modifier.width(7.dp))
            val canSend = enabled && !recording && !voiceTranscribing &&
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

/**
 * The `@` completion panel. Files come from dsh's session-scoped provider; conversations
 * come from the session-reference resolver and are inserted as their canonical mention,
 * never as a plain title. Both lists are bounded by dsh, so a plain Column inside one
 * scroll is enough and keeps the panel above the keyboard instead of a full-screen sheet.
 */
@Composable
private fun AtReferencePanel(
    query: String,
    files: List<AgentFileReference>,
    sessions: List<AgentSessionReference>,
    loading: Boolean,
    error: String?,
    onPickFile: (AgentFileReference) -> Unit,
    onPickSession: (AgentSessionReference) -> Unit
) {
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
        when {
            error != null -> Text(
                "无法读取引用：$error",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                color = Red,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
            loading && files.isEmpty() && sessions.isEmpty() -> Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    Modifier.size(14.dp),
                    color = Accent,
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("正在查找…", color = TextMuted, fontSize = 10.5.sp)
            }
            files.isEmpty() && sessions.isEmpty() -> Text(
                if (query.isBlank()) "输入文件名或对话标题来筛选" else "没有匹配的文件或对话",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                color = TextMuted,
                fontSize = 10.5.sp
            )
        }
        if (files.isNotEmpty()) {
            AtSectionLabel("文件与文件夹")
            files.forEach { file ->
                val name = file.path.substringAfterLast('/').ifBlank { file.path }
                val parent = file.path.substringBeforeLast('/', "")
                AtCandidateRow(
                    title = if (file.directory) "$name/" else name,
                    detail = parent.ifBlank { if (file.directory) "文件夹" else "文件" },
                    trailing = if (file.directory) "进入" else "引用",
                    onClick = { onPickFile(file) }
                )
            }
        }
        if (sessions.isNotEmpty()) {
            AtSectionLabel("对话")
            sessions.forEach { reference ->
                val location = when {
                    reference.sameWorkspace -> "同一工作区"
                    !reference.cwd.isNullOrBlank() -> reference.cwd
                    else -> "未记录工作区"
                }
                AtCandidateRow(
                    title = reference.title,
                    detail = location,
                    trailing = "引用",
                    onClick = { onPickSession(reference) }
                )
            }
        }
    }
}

@Composable
private fun AtSectionLabel(text: String) {
    Text(
        text,
        color = TextMuted,
        fontSize = 9.sp,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun AtCandidateRow(
    title: String,
    detail: String,
    trailing: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        Text(trailing, color = TextMuted, fontSize = 9.5.sp)
    }
}

/** One inserted `@` reference shown above the composer. Removing it edits the draft text. */
@Composable
private fun MentionChip(label: String, session: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier
            .widthIn(max = 210.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Border.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
            .padding(start = 9.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (session) "对话" else "文件",
            color = if (session) Blue else Accent,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            modifier = Modifier.widthIn(max = 150.dp),
            color = TextPrimary,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(4.dp))
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

private data class ComposerMention(
    val range: IntRange,
    val label: String,
    val session: Boolean
)

/** Canonical session mentions are escaped Markdown; a file mention is a word token. */
private val COMPOSER_SESSION_MENTION =
    Regex("@\\[((?:\\\\.|[^\\\\\\]])*)\\]\\(dsh-session:[^\\s)]*\\)")
private val COMPOSER_FILE_MENTION = Regex("(?:^|\\s)(@\"[^\"\\n]+\"|@[^\\s\"@]+)")
private val MENTION_ESCAPE = Regex("\\\\(.)")

/**
 * The mentions visible in [text], in draft order. Session mentions are matched first so
 * the file-token scan never mistakes their `@[` spelling for a path.
 */
private fun composerMentions(text: String): List<ComposerMention> {
    val sessions = COMPOSER_SESSION_MENTION.findAll(text).map { match ->
        ComposerMention(
            range = match.range,
            label = match.groupValues[1].replace(MENTION_ESCAPE, "$1").ifBlank { "对话" },
            session = true
        )
    }.toList()
    val files = COMPOSER_FILE_MENTION.findAll(text).mapNotNull { match ->
        val group = match.groups[1] ?: return@mapNotNull null
        if (text.startsWith("@[", group.range.first)) return@mapNotNull null
        if (sessions.any { it.range.first <= group.range.first && group.range.last <= it.range.last }) {
            return@mapNotNull null
        }
        val raw = group.value
        val label = if (raw.startsWith("@\"")) {
            raw.removePrefix("@\"").removeSuffix("\"")
        } else {
            raw.removePrefix("@")
        }
        ComposerMention(group.range, label.ifBlank { "文件" }, session = false)
    }
    return (sessions + files).sortedBy { it.range.first }
}

/**
 * One control for model and reasoning. The current model can be swapped inline and the
 * reasoning level is a slider over the model's own effort list, so both halves of the
 * choice are visible in one place and take effect immediately.
 */
@Composable
private fun ModelPickerDialog(
    models: AgentModels?,
    onDismiss: () -> Unit,
    onSelect: (AgentModel, String?) -> Unit
) {
    val currentModel = models?.current
    var changingModel by rememberSaveable { mutableStateOf(false) }
    val effortChoices = remember(currentModel) {
        currentModel?.let { model ->
            buildList {
                if (model.efforts.isNotEmpty() && model.defaultEffort == null) {
                    add("默认" to null)
                }
                model.efforts.forEach { effort -> add(effortLabel(effort) to effort.id) }
            }
        }.orEmpty()
    }
    val chosenEffort = models?.reasoningEffort
    val selectedIndex = effortChoices.indexOfFirst { it.second == chosenEffort }
        .takeIf { it >= 0 }
        ?: effortChoices.indexOfFirst { it.second == models?.effectiveEffort }
            .coerceAtLeast(0)
    // Reset only when the model changes. Syncing on every effort update made the thumb
    // snap back while a rapid left/right drag was still settling.
    var sliderIndex by remember(currentModel?.provider, currentModel?.id) {
        mutableStateOf(selectedIndex)
    }
    val headline = currentModel?.name ?: "选择模型"

    KkDialog(onDismiss = onDismiss) {
        KkDialogTitle(
            title = "模型与推理",
            onDismiss = onDismiss
        )

        if (models == null) {
            Text(
                "正在读取模型…",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
            return@KkDialog
        }
        if (!models.routable) {
            Text(
                "当前 provider 不可用，选择后本会话仍可能无法开始。",
                color = Amber,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Model: one tappable row, with the full list only while the user is switching.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { changingModel = !changingModel }
                .padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("模型", color = TextMuted, fontSize = 9.5.sp)
                Text(
                    headline,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                if (changingModel) "收起" else "更换",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (changingModel) {
            val modelListScroll = rememberScrollState()
            ScrollEdgeFade(canScrollForward = modelListScroll.canScrollForward) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(modelListScroll)
                    .padding(bottom = 4.dp)
            ) {
                models.groups.forEach { group ->
                    Text(
                        group.name,
                        color = TextMuted,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                    )
                    group.models.forEach { model ->
                        val selected = model.provider == models.provider && model.id == models.model
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) Accent.copy(alpha = 0.08f) else Color.Transparent
                                )
                                .clickable {
                                    val keepEffort = models.reasoningEffort?.takeIf { id ->
                                        model.efforts.any { it.id == id }
                                    }
                                    onSelect(model, keepEffort ?: model.defaultEffort)
                                    changingModel = false
                                }
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (selected) "✓" else " ",
                                color = Success,
                                fontSize = 11.sp,
                                modifier = Modifier.width(18.dp)
                            )
                            Text(
                                model.name,
                                color = if (selected) TextPrimary else TextSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (models.groups.isEmpty()) {
                    Text(
                        "没有可选择的模型",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
            }
        }

        HorizontalDivider(
            color = Border.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )

        // Reasoning: a slider over the model's declared levels. A model without levels
        // says so instead of showing a dead track.
        if (currentModel == null) {
            Text(
                "当前模型不在目录中，请先重新选择模型。",
                color = Amber,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        } else if (currentModel.efforts.isEmpty()) {
            Text(
                "当前模型不提供可调推理等级。",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("推理能力", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    effortChoices.getOrNull(sliderIndex)?.first ?: "默认",
                    color = Accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (effortChoices.size > 1) {
                Slider(
                    value = sliderIndex.toFloat(),
                    onValueChange = { value ->
                        sliderIndex = value.roundToInt()
                            .coerceIn(0, effortChoices.lastIndex.coerceAtLeast(0))
                    },
                    onValueChangeFinished = {
                        val effort = effortChoices.getOrNull(sliderIndex)?.second
                        if (effort != models.reasoningEffort) {
                            onSelect(currentModel, effort)
                        }
                    },
                    valueRange = 0f..effortChoices.lastIndex.toFloat(),
                    steps = effortChoices.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = SurfaceVariant,
                        activeTickColor = OnAccent.copy(alpha = 0.5f),
                        inactiveTickColor = TextMuted.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "向左更省、更快，向右推理更深入。",
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    "这个模型只有一个推理等级。",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        KkDialogActions {
            TextButton(onClick = onDismiss) {
                Text("完成", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

private fun compact(text: String, limit: Int): String {
    val normalized = text.replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= limit) normalized else normalized.take(limit) + "…"
}
