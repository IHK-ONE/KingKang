package com.kingkang.app.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kingkang.app.BuildConfig
import com.kingkang.app.MainActivity
import com.kingkang.app.R
import com.kingkang.app.data.SettingsStore
import com.kingkang.app.files.FileActions
import com.kingkang.app.runtime.RootfsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the single app-wide agent connection. Sessions and history live in dsh; this class
 * holds only the rendered projection and transient interaction state.
 */
object AgentEngine {

    private const val TAG = "AgentEngine"
    /** Intent extra naming the conversation a completion notification opens. */
    const val EXTRA_SESSION_ID = "com.kingkang.app.extra.SESSION_ID"
    private const val TASK_CHANNEL_ID = "kingkang_tasks"
    private const val HISTORY_LIMIT = 80
    private const val MAX_TOOL_RESULT = 100_000
    private const val MAX_TOOL_ARGS = 20_000
    /** Unknown projections retained for inspection: newest entries, each capped. */
    private const val MAX_EXTRA_PROJECTIONS = 24
    private const val MAX_EXTRA_PROJECTION = 4_000
    /** Distinct unrendered event kinds kept for diagnostics. */
    private const val MAX_UNKNOWN_EVENTS = 40
    /** Divider text dsh-web shows once history has been compacted. */
    private const val COMPACTION_NOTICE = "上下文已压缩，较早的内容已由摘要替代。"
    /** Feedback write refused because another client changed the record first. */
    private const val STALE_FEEDBACK = "反馈已被修改，正在同步最新状态"
    /** Categories dsh's `messageFeedback/put` accepts; anything else is dropped. */
    private val FEEDBACK_CATEGORIES = setOf(
        "task-result",
        "instruction-following",
        "product-interaction",
        "service-stability",
        "resource-cost",
        "security-privacy-permission",
        "other"
    )
    /** A child transcript is a snapshot; refetch it while the child reports running. */
    private const val SUBTAGENT_POLL_MS = 2_500L
    private const val SUBTAGENT_POLLS = 72
    /** Unexpected disconnects are retried this many times before the error stands. */
    private const val AUTO_RECONNECT_LIMIT = 10

    /** dsh's built-in preset for unrestricted file and command access. */
    private const val FULL_ACCESS_PRESET = "danger-full-access"
    /** Settings namespace that holds the agent preset new sessions compose. */
    private const val AGENT_PRESET_SETTINGS_NS = "agent-preset-registry"
    /** dsh ships `ptc` as its config default; the app's product default is 标准模式. */
    private const val STANDARD_PRESET_ID = "standard"
    /** npm mirror used when the user has not picked a source. */
    private const val DEFAULT_NPM_REGISTRY = "https://registry.npmmirror.com/"

    /**
     * Credential references dsh exposes for the DeepSeek provider. The name is the env var
     * dsh falls back to; the secret value itself never travels back over the bridge.
     */
    private val credentialRefs = listOf("DEEPSEEK_API_KEY")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var bridge: BridgeClient? = null
    private var collectJob: Job? = null
    private var startupTimeout: Job? = null
    private val connectMutex = Mutex()
    private val eventMutex = Mutex()

    private var historyLoadingFor: String? = null
    /** Session whose transcript failed to load, so a reconnect can retry it once. */
    private var historyLoadFailedFor: String? = null
    /** Recovery notice held back while the transcript reload wipes the transcript. */
    private var pendingRecoveryNotice = false
    private var lastUnknownType: String? = null
    private val bufferedLiveEvents = mutableListOf<JSONObject>()
    private var loadToken = 0L
    private val localIds = AtomicLong(1)
    /** Identifies the newest provider discovery so a stale reply never replaces it. */
    private val discoveryStamp = AtomicLong(1)
    private val loadingImages: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()
    private var stopping = false
    /** True after a user stop/uninstall, so returning from the background must not restart. */
    @Volatile
    private var userStopped = false
    /**
     * The foreground service was torn down by the system (not by the user). The next time
     * the app becomes visible the agent is restarted instead of showing a dead connection.
     */
    @Volatile
    private var resumeRecoveryPending = false
    private var cancelVerifyJob: Job? = null
    private var creatingSession = false
    private var createRetryPending = false

    /**
     * One attempt per bridge session to pin dsh's default permission. Set before the write
     * so a failing profile cannot trigger it again from the read that follows.
     */
    private var defaultPermissionEnsured = false

    private val liveBlocks = LinkedHashMap<String, LinkedHashMap<Int, LiveBlock>>()

    /**
     * Child transcripts have no follow stream — dsh serves them as persisted history —
     * so while a viewed child is running the engine refetches its page on a slow cadence
     * and stops as soon as the catalog reports it inactive.
     */
    private var subagentPoll: Job? = null

    /**
     * An unexpected bridge/dsh exit is retried a few times instead of leaving the app in a
     * dead state. Android can still kill the whole process, but a transient dsh crash or
     * bridge failure no longer requires the user to notice and restart by hand.
     */
    private var reconnectJob: Job? = null
    /** One connection notice per outage; the bridge reports every retry, not every drop. */
    private var streamOutageNoticed = false
    /** True once this bridge session reached ready, so a later fatal is worth retrying. */
    private var everReady = false
    /**
     * Set when the connection dropped during a turn. dsh closes interrupted tool calls
     * with a synthetic English result; the app explains that recovery once history loads.
     */
    private var pendingInterruptionNotice = false

    /**
     * Whether an activity is on screen. Completion notifications exist for the case where
     * the user is elsewhere; while the app is visible the transcript and workbench already
     * show the same outcome, so posting would only repeat it.
     */
    private val appVisible = AtomicBoolean(false)
    /** Posted task notification ids, so opening the app can clear them. */
    private val taskNotificationIds = ConcurrentHashMap.newKeySet<Int>()
    @Volatile
    private var taskChannelCreated = false
    /** A notification tap that arrived before the session list was loaded. */
    @Volatile
    private var pendingSessionOpen: String? = null
    /** What started the current turn, when it was not the user typing (e.g. a reminder). */
    private val sessionTrigger = ConcurrentHashMap<String, String>()

    private data class LiveBlock(val type: String, var text: String)

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
            themePreference = SettingsStore(appContext).themeMode
        }
    }

    /** Last appearance preference chosen in the app; mirrored into dsh's ui-theme setting. */
    @Volatile
    private var themePreference: String = "system"

    /**
     * Mirrors the app's appearance choice into dsh's own `ui-theme` setting, which is what
     * the Harness WebView renders with. The choice is stored with a pending flag first:
     * a stopped bridge keeps it, and the next ready connection writes it instead of
     * adopting whatever the Harness last wrote (last writer wins).
     */
    fun setThemePreference(preference: String) {
        val value = when (preference) {
            "dark", "light" -> preference
            else -> "system"
        }
        themePreference = value
        if (::appContext.isInitialized) {
            SettingsStore(appContext).themeSyncPending = true
        }
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) return
        scope.launch { writeThemePreference(client, value) }
    }

    /**
     * Reconciles the app palette with dsh's `ui-theme`. A pending local choice is pushed;
     * otherwise dsh's stored choice is adopted, so a theme changed in the Harness reaches
     * the native UI. Called on connecting, on app foreground and after leaving the
     * Harness. Returns the preference the app should now render with.
     */
    suspend fun reconcileThemePreference(): String? {
        val client = bridge ?: return null
        if (_state.value.connection != AgentConnection.Ready) return null
        val store = if (::appContext.isInitialized) SettingsStore(appContext) else null
        val described = try {
            client.callObject("settings.describe", timeoutMs = 20_000)
        } catch (t: Throwable) {
            appendLog("同步 Harness 主题失败：${readableError(t)}")
            return null
        }
        val dshTheme = AgentSettingsParser.parseTheme(described)
        if (store?.themeSyncPending == true) {
            writeThemePreference(client, themePreference)
            return themePreference
        }
        if (dshTheme != null && dshTheme != themePreference) {
            themePreference = dshTheme
            store?.themeMode = dshTheme
            return dshTheme
        }
        return themePreference
    }

    private suspend fun writeThemePreference(client: BridgeClient, preference: String) {
        try {
            client.callObject(
                "settings.mutate",
                JSONObject()
                    .put("ns", "ui-theme")
                    .put(
                        "ops",
                        JSONArray().put(
                            JSONObject()
                                .put("op", "set")
                                .put("path", JSONArray().put("preference"))
                                .put("value", preference)
                        )
                    ),
                20_000
            )
            if (::appContext.isInitialized) {
                SettingsStore(appContext).themeSyncPending = false
            }
        } catch (t: Throwable) {
            // The pending flag stays set, so the next connection retries this write
            // rather than silently adopting the Harness theme.
            appendLog("同步 Harness 主题失败：${readableError(t)}")
        }
    }

    /** Starts the foreground bridge only when the guest runtime is actually installed. */
    fun ensureStarted(context: Context) {
        init(context)
        val current = _state.value.connection
        if (current == AgentConnection.Starting || current == AgentConnection.Ready) return
        if (current == AgentConnection.Failed && bridge?.alive == true) return

        val rootfs = RootfsManager(appContext)
        val missing = when {
            !rootfs.isInstalled() -> "Kali 环境尚未安装"
            !File(rootfs.rootfsDir, "opt/kingkang/node/bin/node").isFile -> "Node.js 运行时尚未安装"
            !File(rootfs.rootfsDir, "opt/kingkang/dsh/bin/dsh").isFile -> "Agent 核心尚未安装"
            else -> null
        }
        if (missing != null) {
            _state.update {
                it.copy(
                    connection = AgentConnection.Failed,
                    status = "Agent 未就绪",
                    error = missing
                )
            }
            return
        }

        stopping = false
        userStopped = false
        resumeRecoveryPending = false
        _state.update {
            it.copy(
                connection = AgentConnection.Starting,
                status = "正在启动 Agent…",
                error = null
            )
        }
        runCatching {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, AgentService::class.java).setAction(AgentService.ACTION_START)
            )
        }.onFailure {
            _state.update { state ->
                state.copy(
                    connection = AgentConnection.Failed,
                    status = "Agent 启动失败",
                    error = it.message ?: "无法启动前台服务"
                )
            }
        }
    }

    fun connect() {
        if (!::appContext.isInitialized) return
        scope.launch {
            connectMutex.withLock {
                val existing = bridge
                if (existing?.alive == true &&
                    (_state.value.connection == AgentConnection.Ready ||
                        _state.value.connection == AgentConnection.Starting)
                ) {
                    return@withLock
                }
                stopping = false
                everReady = false
                startupTimeout?.cancel()
                collectJob?.cancel()
                existing?.destroyNow()
                bridge = null

                _state.update {
                    it.copy(
                        connection = AgentConnection.Starting,
                        status = "正在启动 Agent…",
                        error = null,
                        items = emptyList(),
                        running = false,
                        turnStopping = false,
                        approvals = emptyList(),
                        questions = emptyList(),
                        todos = emptyList(),
                        plan = AgentPlan(),
                        queue = emptyList(),
                        models = null,
                        subagents = emptyList(),
                        subagentsParentAvailable = true,
                        subagentTrail = emptyList(),
                        hasMoreHistory = false,
                        oldestSeq = null
                    )
                }
                subagentPoll?.cancel()
                subagentPoll = null
                liveBlocks.clear()
                historyLoadingFor = null
                bufferedLiveEvents.clear()
                creatingSession = false
                createRetryPending = false

                val client = BridgeClient(appContext)
                bridge = client
                collectJob = scope.launch {
                    client.events.collect { message ->
                        eventMutex.withLock { handleMessage(message) }
                    }
                }
                try {
                    client.start()
                    startupTimeout = scope.launch {
                        delay(150_000)
                        if (_state.value.connection == AgentConnection.Starting) {
                            appendLog("启动超时：dsh 未在 150 秒内就绪")
                            _state.update {
                                it.copy(
                                    connection = AgentConnection.Failed,
                                    status = "Agent 启动超时",
                                    error = "dsh 未能启动。请在终端里运行 dsh --version 检查安装。"
                                )
                            }
                            client.destroyNow()
                            // A retry loop owns the service lifetime; only a first start
                            // that never worked should leave the app stopped.
                            if (!isReconnecting()) stopServiceQuietly()
                        }
                    }
                } catch (t: Throwable) {
                    failConnection(readableError(t), t)
                    client.destroyNow()
                    bridge = null
                    if (!isReconnecting()) stopServiceQuietly()
                }
            }
        }
    }

    fun restart() {
        killBridge()
        _state.update {
            it.copy(
                connection = AgentConnection.Stopped,
                status = "正在重新启动 Agent…",
                error = null
            )
        }
        if (::appContext.isInitialized) ensureStarted(appContext)
    }

    fun stop() {
        subagentPoll?.cancel()
        subagentPoll = null
        userStopped = true
        resumeRecoveryPending = false
        killBridge()
        _state.update {
            it.copy(
                connection = AgentConnection.Stopped,
                status = "Agent 已停止",
                running = false,
                turnStopping = false
            )
        }
        stopServiceQuietly()
    }

    /**
     * Drops everything derived from the dsh install after the user removes the Agent core.
     * A plain [stop] only ends the connection; the harness card and connection panel would
     * otherwise keep showing the last known dsh version and the old session list.
     */
    fun forgetHarness(status: String = "Agent 核心已卸载") {
        subagentPoll?.cancel()
        subagentPoll = null
        userStopped = true
        resumeRecoveryPending = false
        _state.value = AgentState(
            connection = AgentConnection.Stopped,
            status = status
        )
    }

    fun onServiceDestroyed() {
        // Read this before killBridge() flips it: only a teardown the user did not ask for
        // is repaired automatically when the app comes back to the foreground.
        val systemTeardown = !stopping && !userStopped
        killBridge()
        if (_state.value.connection != AgentConnection.Failed) {
            _state.update {
                it.copy(
                    connection = AgentConnection.Stopped,
                    status = "Agent 已停止",
                    running = false,
                    turnStopping = false,
                    webPort = null
                )
            }
        }
        if (systemTeardown) resumeRecoveryPending = true
    }

    /**
     * Called whenever the app becomes visible. Android may stop a foreground service in the
     * background (battery savers on some ROMs); without this the user returns to a dead
     * agent and has to restart it by hand. A user-requested stop is respected.
     */
    fun onAppForegrounded(context: Context) {
        init(context)
        appVisible.set(true)
        clearTaskNotifications()
        if (!resumeRecoveryPending || userStopped) return
        if (_state.value.connection != AgentConnection.Stopped) {
            resumeRecoveryPending = false
            return
        }
        resumeRecoveryPending = false
        ensureStarted(context)
    }

    /** The last activity left the screen; completion notices become worth posting. */
    fun onAppBackgrounded() {
        appVisible.set(false)
    }

    /**
     * Opens the session named by a notification tap. A cold start can beat the session
     * list, so the id is held until the list arrives (see [tryOpenPendingSession]).
     */
    fun openSessionFromNotification(sessionId: String) {
        if (sessionId.isBlank()) return
        pendingSessionOpen = sessionId
        tryOpenPendingSession()
    }

    private fun tryOpenPendingSession() {
        val id = pendingSessionOpen ?: return
        if (_state.value.sessions.none { it.id == id }) return
        pendingSessionOpen = null
        openSession(id)
    }

    private fun stopServiceQuietly() {
        if (!::appContext.isInitialized) return
        runCatching { appContext.stopService(Intent(appContext, AgentService::class.java)) }
    }

    private fun killBridge() {
        stopping = true
        everReady = false
        reconnectJob?.cancel()
        reconnectJob = null
        cancelVerifyJob?.cancel()
        cancelVerifyJob = null
        defaultPermissionEnsured = false
        startupTimeout?.cancel()
        startupTimeout = null
        bridge?.stop()
        bridge = null
        collectJob?.cancel()
        collectJob = null
        streamOutageNoticed = false
        historyLoadingFor = null
        historyLoadFailedFor = null
        pendingRecoveryNotice = false
        bufferedLiveEvents.clear()
    }

    private suspend fun handleMessage(message: JSONObject) {
        when (message.optString("type")) {
            "log" -> appendLog(
                message.optString("message").take(2_000)
            )

            "bridge.exit" -> {
                if (!stopping) {
                    noteInterruptedTurn()
                    failConnection(
                        message.optString("message").ifBlank { "Agent 桥接已退出" },
                        null
                    )
                    scheduleAutoReconnect()
                }
            }

            "event" -> handleEvent(
                message.optString("event"),
                message.optJSONObject("data") ?: JSONObject()
            )

            else -> {
                val bridgeVersion = message.optJSONObject("data")?.optString("bridgeVersion")
                Log.d(TAG, "bridge frame: ${message.optString("type")} $bridgeVersion")
            }
        }
    }

    private suspend fun handleEvent(name: String, data: JSONObject) {
        when (name) {
            "hello" -> {
                _state.update {
                    it.copy(
                        bridgeVersion = data.optString("bridgeVersion")
                            .takeIf { value -> value.isNotBlank() }
                    )
                }
            }

            "ready" -> handleReady(data)
            "fatal" -> {
                val recoverable = everReady
                noteInterruptedTurn()
                failConnection(
                    data.optString("message").ifBlank { "Agent 启动失败" },
                    null
                )
                // A dsh crash after a working session is usually transient; retry it.
                // A fatal before the first ready belongs to a broken install, so leave
                // the error visible instead of looping.
                if (recoverable) scheduleAutoReconnect() else stopServiceQuietly()
            }

            "session.event" -> handleSessionEvent(data)
            "session.subscribed" -> Unit
            "session.added" -> scope.launch { refreshSessions(forceReloadActive = false) }
            "session.removed" -> handleSessionRemoved(data.optString("sessionId"))
            "session.status" -> handleSessionStatus(data)
            "session.error" -> {
                val sid = data.optString("sessionId")
                if (sid == _state.value.activeSessionId) {
                    addNotice(data.optString("message").ifBlank { "Agent 运行失败" }, isError = true)
                } else {
                    appendLog("${sid.substringBefore('-')}: ${data.optString("message")}")
                }
            }

            "approval.requested" -> {
                val approval = AgentApproval(
                    rpcId = data.optString("rpcId"),
                    sessionId = data.optString("sessionId"),
                    approvalId = data.optString("approvalId"),
                    toolName = data.optString("toolName").ifBlank { "工具" },
                    reason = data.optString("reason").takeIf { value -> value.isNotBlank() },
                    callId = data.optString("callId").takeIf { value -> value.isNotBlank() }
                )
                if (approval.rpcId.isNotBlank() && approval.approvalId.isNotBlank()) {
                    _state.update { state ->
                        if (state.approvals.any { it.approvalId == approval.approvalId }) {
                            state
                        } else {
                            state.copy(approvals = state.approvals + approval)
                        }
                    }
                }
            }

            "approval.resolved" -> {
                val approvalId = data.optString("approvalId")
                _state.update { state ->
                    state.copy(approvals = state.approvals.filterNot { it.approvalId == approvalId })
                }
            }

            "question.requested" -> {
                val questions = parseQuestions(data.optJSONArray("questions"))
                if (questions.isNotEmpty()) {
                    val question = AgentQuestion(
                        rpcId = data.optString("rpcId"),
                        sessionId = data.optString("sessionId"),
                        questions = questions
                    )
                    _state.update { state ->
                        if (state.questions.any { it.rpcId == question.rpcId }) {
                            state
                        } else {
                            state.copy(questions = state.questions + question)
                        }
                    }
                }
            }

            "question.resolved" -> {
                val rpcId = data.optString("rpcId")
                _state.update { state ->
                    state.copy(questions = state.questions.filterNot { it.rpcId == rpcId })
                }
            }

            "session.queue" -> {
                val sid = data.optString("sessionId")
                if (sid == _state.value.activeSessionId) {
                    _state.update { it.copy(queue = parseQueue(data.optJSONArray("items"))) }
                }
            }

            "session.projection" -> {
                applyProjection(
                    data.optString("sessionId"),
                    data.optString("key"),
                    data.opt("value")
                )
            }

            "models.changed" -> {
                _state.value.activeSessionId?.let { loadModels(it) }
                // A provider route added, changed, or removed through the web Models
                // page (or our own save) re-registers adapters; keep the form in sync.
                refreshModelProviders()
            }

            "stream.reconnecting" -> {
                appendLog("事件通道重连中（第 ${data.optInt("attempt")} 次）…")
                if (!streamOutageNoticed) {
                    streamOutageNoticed = true
                    // The bridge is already healing itself; this is status, not a failure.
                    addNotice("与 Agent 的连接中断，正在自动恢复…", isError = false)
                }
            }

            "stream.recovered" -> {
                val noticed = streamOutageNoticed
                streamOutageNoticed = false
                appendLog("事件通道已恢复")
                // A transcript load that died with the connection left the screen empty;
                // nothing else re-runs it, so do it here now that dsh answers again.
                val active = _state.value.activeSessionId
                val reload = active != null &&
                    historyLoadFailedFor == active &&
                    historyLoadingFor == null
                if (reload) historyLoadFailedFor = null
                if (noticed) {
                    // The reload replaces the transcript, taking notices with it, so the
                    // confirmation waits until the fresh transcript is in place.
                    if (reload) pendingRecoveryNotice = true
                    else addNotice("与 Agent 的连接已恢复", isError = false)
                }
                if (reload && active != null) {
                    val trail = _state.value.subagentTrail
                    activateSession(
                        active,
                        forceLoad = true,
                        trail = if (trail.lastOrNull()?.id == active) trail else emptyList()
                    )
                }
            }

            "stream.error" -> {
                val error = data.optJSONObject("error")
                val message = error?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: data.optString("message").ifBlank { "未知错误" }
                appendLog("stream: $message")
                if (data.optBoolean("retrying")) {
                    // The bridge keeps retrying in the background, so this is a heads-up
                    // rather than the dead end it used to be.
                    if (!streamOutageNoticed) {
                        streamOutageNoticed = true
                        addNotice(
                            "事件连接中断，正在后台自动重试。可继续等待，或在设置中重启桥接。",
                            isError = true
                        )
                    }
                } else {
                    addNotice("事件流中断：$message。可在设置中重启桥接。", isError = true)
                }
            }

            "dsh.restarting" -> {
                appendLog("dsh 已退出，正在自动重启（第 ${data.optInt("attempt")} 次）…")
                if (!streamOutageNoticed) {
                    streamOutageNoticed = true
                    // The bridge respawns dsh in place; only the event stream is down.
                    addNotice("Agent 进程已停止，正在自动重启…", isError = false)
                }
            }

            "stream.closed" -> {
                appendLog("事件流已关闭（${data.optString("path")}）")
                addNotice("事件流已关闭，请在设置中重启桥接。", isError = true)
            }
            "commands.changed" -> {
                _state.value.activeSessionId?.let { loadCommands(it) }
            }
            "skills.changed" -> {
                _state.value.activeSessionId?.let { loadSkills(it) }
            }
            "host.frame" -> {
                val type = data.optString("type")
                if (type.isNotBlank()) appendLog("host: $type")
            }

            else -> appendLog("未识别事件：$name")
        }
    }

    private fun handleReady(data: JSONObject) {
        startupTimeout?.cancel()
        startupTimeout = null
        everReady = true
        streamOutageNoticed = false
        // refreshSessions below reloads the active transcript, so the outage retry is moot.
        historyLoadFailedFor = null
        pendingRecoveryNotice = false
        val capabilities = data.optJSONArray("capabilities")
        val values = buildSet {
            if (capabilities != null) {
                for (index in 0 until capabilities.length()) {
                    capabilities.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        _state.update {
            it.copy(
                connection = AgentConnection.Ready,
                status = "Agent 已连接",
                error = null,
                dshVersion = data.optString("dshVersion").takeIf { value -> value.isNotBlank() },
                webPort = data.optInt("port").takeIf { port -> port > 0 },
                capabilities = values
            )
        }
        appendLog("Agent 已连接${data.optString("dshVersion").takeIf { it.isNotBlank() }?.let { " · dsh $it" } ?: ""}")
        scope.launch {
            refreshWorkspaces()
            refreshSessions(forceReloadActive = true)
            describeCredentials()
            describePermissions()
            describeAgentPresets()
            refreshModelProviders()
            loadPlugins()
            syncNpmRegistry()
            refreshPluginRegistry()
            reconcileThemePreference()
        }
    }

    private fun failConnection(message: String, throwable: Throwable?) {
        appendLog(message)
        throwable?.let { Log.w(TAG, "agent connection failed", it) }
        _state.update {
            it.copy(
                connection = AgentConnection.Failed,
                status = "Agent 连接失败",
                error = message,
                running = false,
                webPort = null
            )
        }
    }

    /** Remembers that the connection dropped during a running turn. */
    private fun noteInterruptedTurn() {
        if (_state.value.running) pendingInterruptionNotice = true
    }

    /**
     * dsh rejected the API session we hold. The bridge already re-traded its cookie once,
     * so if this surfaces the token itself is gone: re-trade from here and, when even that
     * fails, rebuild the bridge so a fresh dsh web process mints a new token.
     */
    private suspend fun recoverExpiredSession() {
        val client = bridge
        val refreshed = client != null && runCatching {
            client.callObject("connection.refresh", timeoutMs = 30_000)
        }.isSuccess
        eventMutex.withLock {
            if (refreshed) {
                addNotice("Agent 会话已刷新，请重新发送", isError = false)
            } else {
                addNotice("Agent 会话已失效，正在自动重启桥接…", isError = true)
            }
        }
        if (!refreshed) restart()
    }

    /** True while [scheduleAutoReconnect] is driving connect attempts. */
    private fun isReconnecting(): Boolean = reconnectJob?.isActive == true

    /**
     * Retries an unexpected disconnect: three quick attempts, then a patient 30 s retry
     * until [AUTO_RECONNECT_LIMIT]. At most one loop runs; a manual restart or stop
     * cancels it through [killBridge].
     */
    private fun scheduleAutoReconnect() {
        if (stopping) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var attempt = 0
            while (!stopping && _state.value.connection != AgentConnection.Ready) {
                if (attempt >= AUTO_RECONNECT_LIMIT) {
                    appendLog("自动重连 $attempt 次仍未成功，请手动重启 Agent")
                    addNotice(
                        "多次自动重连未成功。请确认 Kali 与 dsh 状态，然后在设置中重启桥接。",
                        isError = true
                    )
                    return@launch
                }
                val wait = when (attempt) {
                    0 -> 3_000L
                    1 -> 8_000L
                    2 -> 20_000L
                    else -> 30_000L
                }
                attempt++
                delay(wait)
                if (stopping || _state.value.connection == AgentConnection.Ready) return@launch
                appendLog("正在自动重连 Agent（第 $attempt 次）…")
                connect()
                // connect() starts its own coroutine; wait for this attempt to settle
                // before deciding whether another one is needed.
                withTimeoutOrNull(160_000) {
                    state.first {
                        it.connection == AgentConnection.Starting ||
                            it.connection == AgentConnection.Ready
                    }
                    state.first {
                        it.connection == AgentConnection.Ready ||
                            it.connection == AgentConnection.Failed
                    }
                }
            }
        }
    }

    private suspend fun refreshSessions(forceReloadActive: Boolean) {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) return
        val result = try {
            client.callObject("session.list", timeoutMs = 30_000)
        } catch (t: Throwable) {
            appendLog("读取会话失败：${readableError(t)}")
            return
        }

        val remote = mutableListOf<AgentSession>()
        // A locally derived title (first prompt text, or "图片" for an image-only prompt)
        // survives a refresh when dsh itself never titles the session: dsh's title
        // provider needs text, so an image-only session stays untitled forever.
        val knownTitles = _state.value.sessions.associate { it.id to it.title }
        val array = result.optJSONArray("sessions")
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                // dsh lists subagent sessions alongside roots and marks them with
                // `origin:"subagent"`. They belong to the subagent trail, never to the
                // drawer: a child cannot be followed as a normal session and must not
                // become the fallback conversation. `parentId` alone is not the marker —
                // a forked root also records its fork point there.
                if (item.optString("origin") == "subagent") continue
                // Archived sessions stay in the catalog so the drawer can list them under
                // "已归档" with their titles; every normal list filters them out, and the
                // fallback conversation below never picks one.
                remote += AgentSession(
                    id = id,
                    title = item.optString("title").ifBlank { knownTitles[id].orEmpty() },
                    updatedAt = item.optLong("updatedAt"),
                    running = item.optBoolean("running"),
                    blank = item.optBoolean("blank"),
                    cwd = item.optString("cwd").takeIf { it.isNotBlank() },
                    presetId = (item.opt("presetId") as? String)?.takeIf { it.isNotBlank() }
                )
            }
        }

        val current = _state.value
        val view = current.subagentView
        // dsh's session list excludes child sessions, so every level of the open trail is
        // kept locally as long as it can still be navigated back to.
        val trailIds = current.subagentTrail.mapTo(mutableSetOf()) { it.id }
        val synthetic = current.sessions.filter { session ->
            remote.none { it.id == session.id } &&
                (session.id in trailIds ||
                    (session.blank && session.id == current.activeSessionId))
        }
        val merged = (remote + synthetic).sortedByDescending { it.updatedAt }
        _state.update { it.copy(sessions = merged) }
        tryOpenPendingSession()

        // dsh's row is the authority on whether a turn is live. Keep the transcript's own
        // flag in step so a missed turn.end (for example after a background outage) cannot
        // leave the composer showing a turn that has already finished.
        if (view == null && current.connection == AgentConnection.Ready) {
            val active = merged.firstOrNull { it.id == current.activeSessionId }
            if (active != null && !active.running && (current.running || current.turnStopping)) {
                _state.update {
                    it.copy(
                        running = false,
                        turnStopping = false,
                        items = it.items.map { item ->
                            if (item is AssistantItem && item.streaming) {
                                item.copy(streaming = false)
                            } else {
                                item
                            }
                        }
                    )
                }
            }
        }

        if (view != null) {
            // While reading a child, a catalog refresh must never yank the user back to
            // some other conversation; only an empty transcript needs loading.
            if (current.items.isEmpty() && merged.any { it.id == view.id }) {
                activateSession(view.id, forceLoad = true, trail = current.subagentTrail)
            }
            return
        }

        val live = merged.filter { it.id !in current.archivedSessionIds }
        val target = live.firstOrNull { it.id == current.activeSessionId }
            ?: live.firstOrNull { !it.blank }
            ?: live.firstOrNull()
        if (target == null) {
            requestCreateSession()
        } else if (forceReloadActive || target.id != current.activeSessionId || current.items.isEmpty()) {
            activateSession(target.id, forceLoad = true)
        }
    }

    /**
     * dsh answers `session/list` with the sessions it has already summarized and adds cold
     * ones over a bounded background pass, so the first list after a restart can be empty
     * while conversations exist. Its progress arrives as `session.added`; waiting one short
     * beat before opening a brand-new conversation lets those rows land first.
     */
    private fun requestCreateSession() {
        if (creatingSession || createRetryPending) return
        createRetryPending = true
        scope.launch {
            delay(1_500)
            createRetryPending = false
            if (_state.value.sessions.any { it.id !in _state.value.archivedSessionIds }) {
                refreshSessions(forceReloadActive = false)
                return@launch
            }
            createSessionInternal()
        }
    }

    /**
     * Makes [id] the transcript on screen. [trail] is the root-to-child chain that owns the
     * screen; empty means a normal conversation. The trail's last entry is always [id].
     */
    private fun activateSession(
        id: String,
        forceLoad: Boolean,
        trail: List<AgentSubagent> = emptyList()
    ) {
        val view = trail.lastOrNull()
        val session = _state.value.sessions.firstOrNull { it.id == id }
        if (!forceLoad && id == _state.value.activeSessionId && _state.value.items.isNotEmpty()) return
        loadToken++
        val token = loadToken
        historyLoadingFor = id
        bufferedLiveEvents.clear()
        liveBlocks.clear()
        subagentPoll?.cancel()
        subagentPoll = null
        val previousTrail = _state.value.subagentTrail
        val keepIds = trail.mapTo(mutableSetOf()) { it.id }
        // Synthetic child rows exist only while their level is on the trail; dropping the
        // rest keeps a ghost chat out of the drawer after returning to the root.
        val dropIds = previousTrail.map { it.id }.filterNot { it in keepIds }.toSet()
        _state.update {
            it.copy(
                activeSessionId = id,
                sessions = if (dropIds.isEmpty()) it.sessions
                else it.sessions.filterNot { session -> session.id in dropIds },
                subagentTrail = trail,
                // The catalog belongs to the new screen: the root's children or this
                // child's own. Clear it so the previous level never flashes underneath.
                subagents = emptyList(),
                subagentsParentAvailable = true,
                items = emptyList(),
                running = session?.running ?: false,
                todos = emptyList(),
                plan = AgentPlan(),
                permissionMode = null,
                tokenUsage = AgentTokenUsage(),
                contextPressure = AgentContextPressure(),
                contextBreakdown = AgentContextBreakdown(),
                goal = null,
                goalBusy = false,
                goalError = null,
                schedules = emptyList(),
                schedulesLoaded = false,
                schedulesError = null,
                scheduleBusy = false,
                scheduleHistory = emptyMap(),
                modelSelection = AgentModelSelection(),
                subagentTiming = null,
                agentTeam = null,
                turnOutline = emptyList(),
                sessionStats = AgentSessionStats(),
                messageFeedback = emptyMap(),
                extraProjections = emptyMap(),
                unknownEvents = emptyList(),
                export = AgentExport(),
                jobs = emptyList(),
                jobsLoaded = false,
                jobsError = null,
                jobOutput = emptyMap(),
                changes = null,
                changesLoaded = false,
                changesLoading = false,
                changesError = null,
                changesReleased = false,
                changeDiffs = emptyMap(),
                deliverables = emptyList(),
                fileListing = AgentFileListing(),
                filePreview = AgentFilePreview(),
                queue = emptyList(),
                models = null,
                commands = emptyList(),
                skills = emptyList(),
                hasMoreHistory = false,
                oldestSeq = null
            )
        }
        scope.launch {
            loadHistory(id, beforeSeq = null, token = token)
            loadMessageFeedback(id)
            if (view == null) {
                loadModels(id)
                loadCommands(id)
                loadSkills(id)
                loadSubagents(id)
                // The workbench badge reads this list without the sheet being open, so a
                // background job left over from an earlier session is visible on arrival.
                loadJobs(id)
            } else {
                // Skills and commands need an attached ordinary session; a child view
                // lists its own children (drill-down) while its activity is read from
                // the parent's catalog.
                loadSubagents(view.id)
                refreshViewStatus(view)
            }
        }
    }

    fun openSession(id: String) {
        if (_state.value.sessions.none { it.id == id }) return
        if (id == _state.value.activeSessionId && _state.value.items.isNotEmpty()) return
        activateSession(id, forceLoad = true)
    }

    /**
     * Opens one child of the conversation on screen — the root's direct child, or, while
     * drilled in, the open child's own child. The child becomes the active session so the
     * transcript renderer applies unchanged; the trail remembers every parent address the
     * `subagent.*` calls need and lets back-navigation restore one level at a time.
     */
    fun openSubagent(parentId: String, subagent: AgentSubagent) {
        if (subagent.diagnostic != null) return
        val state = _state.value
        // Entries belong to the catalog they were listed from; a stale panel tap after the
        // screen moved to another level must not open a child of the wrong parent.
        val catalogParent = state.subagentView?.id ?: state.activeSessionId
        if (catalogParent.isNullOrBlank() || catalogParent != parentId) return
        // A child already on the trail is an ancestor; opening it would loop back up.
        if (state.subagentTrail.any { it.id == subagent.id }) return
        val addressed = if (subagent.parentId == parentId) subagent
        else subagent.copy(parentId = parentId)
        val child = AgentSession(
            id = addressed.id,
            title = addressed.displayLabel,
            updatedAt = now(),
            running = addressed.running,
            parentId = parentId
        )
        _state.update { current ->
            current.copy(
                sessions = listOf(child) + current.sessions.filterNot { it.id == child.id }
            )
        }
        activateSession(addressed.id, forceLoad = true, trail = state.subagentTrail + addressed)
    }

    /**
     * Steps back one level: from a grandchild to its child parent, or from a child to the
     * root conversation.
     */
    fun closeSubagent() {
        val trail = _state.value.subagentTrail
        if (trail.isEmpty()) return
        if (trail.size > 1) {
            val remaining = trail.dropLast(1)
            activateSession(remaining.last().id, forceLoad = false, trail = remaining)
            return
        }
        val rootId = trail.last().parentId
        if (_state.value.sessions.none { it.id == rootId }) {
            addNotice("无法返回主对话：它已不在会话列表中", isError = true)
            return
        }
        activateSession(rootId, forceLoad = false)
    }

    /**
     * Exports the open session (and its subagent descendants) as dsh's log archive.
     *
     * The bridge streams the ZIP into Kali; this method copies it into the app cache so
     * the FileProvider can expose it. The copy is the only reason the archive leaves the
     * rootfs, and the guest file is removed as soon as the copy exists.
     */
    fun exportSession(
        sessionId: String? = _state.value.activeSessionId,
        includeDescendants: Boolean = true
    ) {
        val id = sessionId?.takeIf { it.isNotBlank() } ?: return
        if (_state.value.export.running) return
        val client = bridge
        if (client == null) {
            addNotice("Agent 尚未连接，无法导出", isError = true)
            return
        }
        _state.update { it.copy(export = AgentExport(sessionId = id, running = true)) }
        scope.launch {
            try {
                val result = client.callObject(
                    "session.export",
                    JSONObject()
                        .put("sessionId", id)
                        .put("includeDescendants", includeDescendants),
                    240_000
                ) ?: throw IllegalStateException("导出未返回结果")
                val guestPath = result.optString("path")
                val source = guestToHost(guestPath)
                    ?: throw IllegalStateException("导出路径无效")
                if (!source.isFile) throw IllegalStateException("导出文件不存在")
                val directory = File(appContext.cacheDir, "exports").apply { mkdirs() }
                val name = FileActions.sanitizeFileName(
                    result.optString("name").ifBlank { source.name },
                    fallback = "session.zip"
                )
                val target = File(directory, name)
                withContext(Dispatchers.IO) {
                    source.inputStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                // The cache copy is the shareable artifact; the Kali original is redundant.
                runCatching { source.delete() }
                pruneExportCache(directory, target)
                _state.update {
                    it.copy(
                        export = AgentExport(
                            sessionId = id,
                            name = target.name,
                            bytes = target.length(),
                            hostPath = target.absolutePath
                        )
                    )
                }
                addNotice("会话已导出：${target.name}", isError = false)
            } catch (error: Exception) {
                val message = error.message?.takeIf { it.isNotBlank() } ?: "导出失败"
                _state.update { it.copy(export = AgentExport(sessionId = id, error = message)) }
                addNotice("导出失败：$message", isError = true)
            }
        }
    }

    /** Hides the export result strip; an in-flight export keeps running. */
    fun dismissExport() {
        if (_state.value.export.running) return
        _state.update { it.copy(export = AgentExport()) }
    }

    /**
     * Reads the open conversation's background jobs. dsh keeps them per session, so this
     * is also how the workbench recovers the list after the app was restarted.
     */
    fun loadJobs(sessionId: String? = null) {
        val client = bridge ?: return
        val id = sessionId ?: _state.value.activeSessionId ?: return
        if (id.isBlank()) return
        scope.launch {
            val result = try {
                client.callObject("job.list", JSONObject().put("sessionId", id), 20_000)
            } catch (t: Throwable) {
                _state.update { state ->
                    if (state.activeSessionId == id) {
                        state.copy(jobsLoaded = true, jobsError = readableError(t))
                    } else {
                        state
                    }
                }
                return@launch
            }
            val jobs = parseJobs(result)
            _state.update { state ->
                if (state.activeSessionId != id) state
                else state.copy(jobs = jobs, jobsLoaded = true, jobsError = null)
            }
        }
    }

    /** Asks dsh to stop one job; the row shows "停止中" until the next list refresh. */
    fun killJob(jobId: String) {
        val client = bridge ?: return
        val state = _state.value
        val sessionId = state.activeSessionId ?: return
        val target = state.jobs.firstOrNull { it.id == jobId } ?: return
        if (!target.running) return
        _state.update { current ->
            current.copy(jobs = current.jobs.map {
                if (it.id == jobId) it.copy(status = "stopping") else it
            })
        }
        scope.launch {
            val failure = try {
                client.callObject(
                    "job.kill",
                    JSONObject().put("sessionId", sessionId).put("jobId", jobId),
                    20_000
                )
                null
            } catch (t: Throwable) {
                readableError(t)
            }
            if (failure != null) {
                _state.update { current ->
                    current.copy(jobs = current.jobs.map {
                        if (it.id == jobId) it.copy(status = target.status) else it
                    })
                }
                eventMutex.withLock { addNotice("停止任务失败：$failure", isError = true) }
                return@launch
            }
            loadJobs(sessionId)
        }
    }

    /**
     * Loads a job's scrollback with `job/follow`. [append] continues from the offset the
     * previous read stopped at, so a refresh only adds what the job printed since.
     */
    fun loadJobOutput(jobId: String, append: Boolean = false) {
        val client = bridge ?: return
        val state = _state.value
        val sessionId = state.activeSessionId ?: return
        val previous = state.jobOutput[jobId]
        if (previous?.loading == true) return
        val from = if (append) previous?.next ?: 0L else 0L
        _state.update { current ->
            current.copy(
                jobOutput = current.jobOutput + (jobId to
                    (previous ?: AgentJobOutput(jobId)).copy(loading = true, error = null))
            )
        }
        scope.launch {
            val result = try {
                client.callObject(
                    "job.output",
                    JSONObject()
                        .put("sessionId", sessionId)
                        .put("jobId", jobId)
                        .put("from", from),
                    30_000
                )
            } catch (t: Throwable) {
                _state.update { current ->
                    if (current.activeSessionId != sessionId) current
                    else current.copy(
                        jobOutput = current.jobOutput + (jobId to
                            (current.jobOutput[jobId] ?: AgentJobOutput(jobId)).copy(
                                loading = false,
                                error = readableError(t)
                            ))
                    )
                }
                return@launch
            }
            val parsed = parseJobOutput(jobId, result)
            _state.update { current ->
                if (current.activeSessionId != sessionId) return@update current
                val prior = current.jobOutput[jobId]
                val merged = if (append) {
                    parsed.copy(chunks = (prior?.chunks ?: emptyList()) + parsed.chunks)
                } else {
                    parsed
                }
                val refreshed = parsed.job
                current.copy(
                    jobOutput = current.jobOutput + (jobId to merged),
                    jobs = if (refreshed == null) current.jobs
                    else current.jobs.map { if (it.id == refreshed.id) refreshed else it }
                )
            }
        }
    }

    /**
     * Reads the newest `workspace/changes` summary of the open conversation. The bridge
     * resolves the announcement's seq itself; a session with no announcement yet answers
     * with no summary, which is shown as an empty state rather than an error.
     */
    fun loadChanges() {
        val client = bridge ?: return
        val sessionId = _state.value.activeSessionId ?: return
        if (_state.value.changesLoading) return
        _state.update { it.copy(changesLoading = true, changesError = null) }
        scope.launch {
            val result = try {
                client.callObject(
                    "changes.summary",
                    JSONObject().put("sessionId", sessionId),
                    30_000
                )
            } catch (t: Throwable) {
                _state.update { current ->
                    if (current.activeSessionId != sessionId) current
                    else current.copy(changesLoading = false, changesError = readableError(t))
                }
                return@launch
            }
            val summary = result.optJSONObject("summary")
            val seq = result.longOrNull("seq") ?: 0L
            val released = result.optBoolean("released")
            val changes = summary?.let { parseChanges(it, seq) }
            _state.update { current ->
                if (current.activeSessionId != sessionId) current
                else current.copy(
                    changes = changes,
                    changesLoaded = true,
                    changesLoading = false,
                    changesError = null,
                    changesReleased = released,
                    // A new announcement replaces the previous turn's comparisons.
                    changeDiffs = emptyMap()
                )
            }
        }
    }

    /** Loads one changed file's comparison; [index] is its place in [AgentChanges.files]. */
    fun loadChangesDiff(index: Int) {
        val client = bridge ?: return
        val state = _state.value
        val sessionId = state.activeSessionId ?: return
        val changes = state.changes ?: return
        if (changes.seq <= 0L || index !in changes.files.indices) return
        if (state.changeDiffs[index]?.loading == true) return
        _state.update { current ->
            current.copy(
                changeDiffs = current.changeDiffs + (index to
                    (current.changeDiffs[index] ?: AgentChangesDiff()).copy(
                        loading = true,
                        error = null
                    ))
            )
        }
        scope.launch {
            val result = try {
                client.callObject(
                    "changes.diff",
                    JSONObject()
                        .put("sessionId", sessionId)
                        .put("seq", changes.seq)
                        .put("index", index),
                    45_000
                )
            } catch (t: Throwable) {
                _state.update { current ->
                    if (current.activeSessionId != sessionId) current
                    else current.copy(
                        changeDiffs = current.changeDiffs + (index to
                            (current.changeDiffs[index] ?: AgentChangesDiff()).copy(
                                loading = false,
                                error = readableError(t)
                            ))
                    )
                }
                return@launch
            }
            val parsed = parseChangesDiff(result)
            _state.update { current ->
                if (current.activeSessionId != sessionId) current
                else current.copy(changeDiffs = current.changeDiffs + (index to parsed))
            }
        }
    }

    /**
     * Reads the open conversation's current goal through the goal service. The `goal`
     * projection already keeps [AgentState.goal] live; this read refreshes the exact
     * revision the next mutation must address, including after dsh restarted or another
     * client changed the goal.
     */
    fun loadGoal() {
        val client = bridge ?: return
        val sessionId = _state.value.activeSessionId ?: return
        scope.launch {
            val result = try {
                client.callObject("goal.get", JSONObject().put("sessionId", sessionId), 20_000)
            } catch (t: Throwable) {
                _state.update { current ->
                    if (current.activeSessionId != sessionId) current
                    else current.copy(goalError = readableError(t))
                }
                return@launch
            }
            val parsed = parseGoalView(result.optJSONObject("goal"))
            _state.update { current ->
                if (current.activeSessionId != sessionId) current
                // A null answer is dsh's "no current goal"; a cleared goal must not linger.
                else current.copy(goal = parsed, goalError = null)
            }
        }
    }

    fun pauseGoal() = mutateGoal("goal.pause")

    fun resumeGoal() = mutateGoal("goal.resume")

    fun clearGoal() = mutateGoal("goal.clear")

    /**
     * Replaces the objective and/or the round cap. A blank objective means "keep the old
     * one", matching dsh's optional fields; the round cap is always sent when provided.
     */
    fun editGoal(objective: String, maxGoalRounds: Int?) {
        val edit = JSONObject()
        if (objective.isNotBlank()) edit.put("objective", objective.trim())
        if (maxGoalRounds != null && maxGoalRounds >= 1) {
            edit.put("maxGoalRounds", maxGoalRounds)
        }
        if (edit.length() == 0) return
        mutateGoal("goal.edit", edit)
    }

    /**
     * Runs one goal mutation against the revision on screen. dsh rejects a stale revision
     * rather than overwriting a newer change, so a failure refetches the card instead of
     * pretending the action landed.
     */
    private fun mutateGoal(method: String, edit: JSONObject? = null) {
        val client = bridge ?: return
        val state = _state.value
        val sessionId = state.activeSessionId ?: return
        val goal = state.goal ?: return
        if (state.goalBusy) return
        val request = JSONObject()
            .put("sessionId", sessionId)
            .put("id", goal.id)
            .put("revision", goal.revision)
        edit?.let { extra ->
            extra.keys().forEach { key -> request.put(key, extra.get(key)) }
        }
        _state.update { it.copy(goalBusy = true, goalError = null) }
        scope.launch {
            val result = try {
                client.callObject(method, request, 30_000)
            } catch (t: Throwable) {
                val failure = readableError(t)
                _state.update { current ->
                    if (current.activeSessionId != sessionId) current
                    else current.copy(goalBusy = false, goalError = failure)
                }
                eventMutex.withLock { addNotice("目标操作失败：$failure", isError = true) }
                loadGoal()
                return@launch
            }
            val parsed = parseGoalView(result.optJSONObject("goal"))
            _state.update { current ->
                if (current.activeSessionId != sessionId) current
                else current.copy(goal = parsed, goalBusy = false, goalError = null)
            }
        }
    }

    /**
     * Reads dsh's Host-wide reminder catalog and keeps the rows bound to the open
     * conversation. The catalog carries ended reminders too, so the workbench can show
     * what already fired instead of silently dropping it.
     */
    fun loadSchedules(sessionId: String? = null) {
        val client = bridge ?: return
        val id = sessionId ?: _state.value.activeSessionId ?: return
        if (id.isBlank()) return
        scope.launch {
            val result = try {
                client.callObject("schedule.catalog", JSONObject(), 20_000)
            } catch (t: Throwable) {
                _state.update { state ->
                    if (state.activeSessionId != id) state
                    else state.copy(schedulesLoaded = true, schedulesError = readableError(t))
                }
                return@launch
            }
            val schedules = parseSchedules(result, id)
            _state.update { state ->
                if (state.activeSessionId != id) state
                else state.copy(
                    schedules = schedules,
                    schedulesLoaded = true,
                    schedulesError = null
                )
            }
        }
    }

    /**
     * Edits a reminder's title, prompt and/or timing. dsh compares [AgentSchedule.raw]
     * with the stored record and refuses a stale write, so a concurrent edit from
     * dsh-web cannot be overwritten by a tap here.
     */
    fun updateSchedule(
        id: String,
        title: String? = null,
        prompt: String? = null,
        change: JSONObject? = null
    ) {
        val client = bridge ?: return
        val state = _state.value
        val sessionId = state.activeSessionId ?: return
        val schedule = state.schedules.firstOrNull { it.id == id } ?: return
        if (state.scheduleBusy) return
        val expected = runCatching { JSONObject(schedule.raw) }.getOrNull()
        if (expected == null) {
            scope.launch { eventMutex.withLock { addNotice("提醒记录不完整，请刷新后重试", isError = true) } }
            return
        }
        val request = JSONObject()
            .put("sessionId", sessionId)
            .put("id", id)
            .put("expected", expected)
        title?.let { request.put("title", it.trim()) }
        prompt?.let { request.put("prompt", it.trim()) }
        change?.let { request.put("change", it) }
        _state.update { it.copy(scheduleBusy = true) }
        scope.launch {
            val result = try {
                client.callObject("schedule.update", request, 30_000)
            } catch (t: Throwable) {
                val failure = readableError(t)
                _state.update { current ->
                    if (current.activeSessionId != sessionId) current
                    else current.copy(scheduleBusy = false)
                }
                eventMutex.withLock { addNotice("修改定时任务失败：$failure", isError = true) }
                loadSchedules(sessionId)
                return@launch
            }
            if (!result.optBoolean("updated", false)) {
                val code = result.optString("code")
                val failure = when (code) {
                    "schedule_not_found" -> "任务已不存在"
                    "schedule_ended" -> "任务已结束，无法再修改"
                    "schedule_conflict" -> "任务刚被其他客户端修改，请重试"
                    else -> code.ifBlank { "dsh 未更新任务" }
                }
                _state.update { current ->
                    if (current.activeSessionId != sessionId) current
                    else current.copy(scheduleBusy = false)
                }
                eventMutex.withLock { addNotice("修改定时任务失败：$failure", isError = true) }
                loadSchedules(sessionId)
                return@launch
            }
            val record = parseSchedule(result.optJSONObject("record"), sessionId, "active")
            _state.update { current ->
                if (current.activeSessionId != sessionId) current
                else current.copy(
                    schedules = if (record == null) current.schedules
                    else current.schedules.map { if (it.id == id) record else it },
                    scheduleBusy = false,
                    schedulesError = null
                )
            }
            eventMutex.withLock { addNotice("定时任务已更新", isError = false) }
            loadSchedules(sessionId)
        }
    }

    /** Deletes one reminder, ended or not. A queued delivery is not retracted. */
    fun deleteSchedule(id: String) {
        val client = bridge ?: return
        val state = _state.value
        val sessionId = state.activeSessionId ?: return
        if (state.scheduleBusy) return
        _state.update { it.copy(scheduleBusy = true) }
        scope.launch {
            val result = try {
                client.callObject(
                    "schedule.delete",
                    JSONObject().put("sessionId", sessionId).put("id", id),
                    30_000
                )
            } catch (t: Throwable) {
                val failure = readableError(t)
                _state.update { current ->
                    if (current.activeSessionId != sessionId) current
                    else current.copy(scheduleBusy = false)
                }
                eventMutex.withLock { addNotice("删除定时任务失败：$failure", isError = true) }
                loadSchedules(sessionId)
                return@launch
            }
            val deleted = result.optBoolean("deleted", false)
            _state.update { current ->
                if (current.activeSessionId != sessionId) current
                else current.copy(
                    schedules = if (deleted) current.schedules.filterNot { it.id == id }
                    else current.schedules,
                    scheduleHistory = current.scheduleHistory - id,
                    scheduleBusy = false
                )
            }
            if (deleted) {
                eventMutex.withLock { addNotice("定时任务已删除", isError = false) }
            } else {
                eventMutex.withLock { addNotice("任务已不存在，已刷新列表", isError = false) }
                loadSchedules(sessionId)
            }
        }
    }

    /**
     * Reads a reminder's saved deliveries. [before] pages older records; dsh answers
     * newest-first and names the next cursor.
     */
    fun loadScheduleHistory(id: String, before: String? = null) {
        val client = bridge ?: return
        val sessionId = _state.value.activeSessionId ?: return
        val previous = _state.value.scheduleHistory[id]
        if (previous?.loading == true) return
        _state.update { current ->
            current.copy(
                scheduleHistory = current.scheduleHistory + (id to
                    (previous ?: AgentScheduleHistory(id)).copy(
                        id = id,
                        loading = true,
                        error = null
                    ))
            )
        }
        val request = JSONObject()
            .put("sessionId", sessionId)
            .put("id", id)
            .put("limit", 20)
        if (!before.isNullOrBlank()) request.put("before", before)
        scope.launch {
            val result = try {
                client.callObject("schedule.history", request, 20_000)
            } catch (t: Throwable) {
                _state.update { current ->
                    if (current.activeSessionId != sessionId) current
                    else current.copy(
                        scheduleHistory = current.scheduleHistory + (id to
                            (current.scheduleHistory[id] ?: AgentScheduleHistory(id)).copy(
                                loading = false,
                                error = readableError(t)
                            ))
                    )
                }
                return@launch
            }
            val code = result.optString("code")
            if (code.isNotBlank()) {
                val failure = when (code) {
                    "schedule_not_found" -> "任务已不存在"
                    "delivery_cursor_not_found" -> "更早的记录已不可用"
                    else -> code
                }
                _state.update { current ->
                    if (current.activeSessionId != sessionId) current
                    else current.copy(
                        scheduleHistory = current.scheduleHistory + (id to
                            (current.scheduleHistory[id] ?: AgentScheduleHistory(id)).copy(
                                loading = false,
                                error = failure
                            ))
                    )
                }
                return@launch
            }
            val parsed = parseScheduleHistory(result, id)
            _state.update { current ->
                if (current.activeSessionId != sessionId) current
                else {
                    val older = if (before.isNullOrBlank()) emptyList()
                    else current.scheduleHistory[id]?.records.orEmpty()
                    current.copy(
                        scheduleHistory = current.scheduleHistory + (id to
                            parsed.copy(records = older + parsed.records))
                    )
                }
            }
        }
    }

    /** Drops one reminder's delivery history, closing its detail surface. */
    fun dismissScheduleHistory(id: String) {
        _state.update { it.copy(scheduleHistory = it.scheduleHistory - id) }
    }

    /**
     * Lists direct subdirectories of one Kali path for the workspace picker. The bridge
     * owns the read: it sees Kali only, so the picker can never wander into Android
     * storage even if a bound mount exists.
     */
    fun browseDirectory(path: String? = null) {        if (_state.value.directory.loading) return
        val client = bridge
        if (client == null) {
            _state.update {
                it.copy(
                    directory = it.directory.copy(
                        loading = false,
                        loaded = true,
                        error = "Agent 尚未连接"
                    )
                )
            }
            return
        }
        val target = path ?: _state.value.directory.path
        _state.update {
            it.copy(directory = it.directory.copy(loading = true, error = null))
        }
        scope.launch {
            try {
                val result = client.callObject(
                    "workspace.browse",
                    JSONObject().put("path", target),
                    15_000
                ) ?: throw IllegalStateException("目录列表为空")
                val entries = mutableListOf<AgentDirectoryEntry>()
                val array = result.optJSONArray("entries")
                if (array != null) {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val name = item.optString("name")
                        val child = item.optString("path")
                        if (name.isBlank() || child.isBlank()) continue
                        entries += AgentDirectoryEntry(name, child)
                    }
                }
                _state.update {
                    it.copy(
                        directory = AgentDirectoryListing(
                            path = result.optString("path"),
                            parent = if (result.isNull("parent")) {
                                null
                            } else {
                                result.optString("parent").takeIf { value -> value.isNotBlank() }
                            },
                            entries = entries,
                            truncated = result.optBoolean("truncated"),
                            loaded = true
                        )
                    )
                }
            } catch (error: Exception) {
                val message = error.message?.takeIf { it.isNotBlank() } ?: "无法读取目录"
                _state.update {
                    it.copy(
                        directory = it.directory.copy(
                            loading = false,
                            loaded = true,
                            error = message
                        )
                    )
                }
            }
        }
    }

    fun closeDirectoryBrowser() {
        _state.update { it.copy(directory = AgentDirectoryListing()) }
    }

    /** Creates a folder below the directory the picker is showing, then opens it. */
    fun createDirectory(name: String) {
        val client = bridge
        if (client == null) {
            notifyState("Agent 尚未连接")
            return
        }
        val listing = _state.value.directory
        if (listing.loading) return
        val parent = listing.path.ifBlank { "/root" }
        scope.launch {
            val result = try {
                client.callObject(
                    "workspace.mkdir",
                    JSONObject().put("parent", parent).put("name", name),
                    15_000
                )
            } catch (t: Throwable) {
                notifyState("无法创建文件夹：${readableError(t)}")
                return@launch
            }
            browseDirectory(result.optString("path").takeIf { it.isNotBlank() } ?: parent)
        }
    }

    /**
     * Hands a Kali file to another app. The bridge copies it into `/root/shared/outbox`
     * first — the one Kali location the phone can read — and the app opens or shares the
     * host file. Files already under the shared directory skip the copy.
     */
    fun openGuestFile(guestPath: String, share: Boolean = false) {
        val normalized = guestPath.trim()
        if (normalized.isBlank()) return
        if (normalized.startsWith("/root/shared/")) {
            val host = sharedGuestToHost(normalized)
            if (host == null) {
                notifyState("文件已不在共享目录中")
                return
            }
            val failure = if (share) {
                FileActions.share(appContext, host)
            } else {
                FileActions.open(appContext, host)
            }
            failure?.let(::notifyState)
            return
        }
        val client = bridge
        if (client == null) {
            notifyState("Agent 尚未连接")
            return
        }
        scope.launch {
            val result = try {
                client.callObject("file.share", JSONObject().put("path", normalized), 30_000)
            } catch (t: Throwable) {
                notifyState("无法打开文件：${readableError(t)}")
                return@launch
            }
            val host = sharedGuestToHost(result.optString("guestPath"))
            if (host == null) {
                notifyState("文件未能交给系统打开")
                return@launch
            }
            val failure = if (share) {
                FileActions.share(appContext, host)
            } else {
                FileActions.open(appContext, host)
            }
            failure?.let(::notifyState)
        }
    }

    /**
     * The host file behind an attachment, or null once it has been removed. A composer
     * draft knows its exact host path; a transcript attachment only carries the guest
     * path, which maps back into the shared directory.
     */
    fun attachmentHostFile(file: AgentFile): File? =
        file.hostPath
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::File)
            ?.takeIf { it.isFile }
            ?: sharedGuestToHost(file.guestPath)

    /** Opens a file the user attached earlier; its bytes live under the shared directory. */
    fun openAttachment(file: AgentFile) {
        val host = attachmentHostFile(file) ?: run {
            notifyState("文件已不在共享目录中")
            return
        }
        FileActions.open(appContext, host)?.let(::notifyState)
    }

    /** Hands an attachment to the system share sheet. */
    fun shareAttachment(file: AgentFile) {
        val host = attachmentHostFile(file) ?: run {
            notifyState("文件已不在共享目录中")
            return
        }
        FileActions.share(appContext, host)?.let(::notifyState)
    }

    /**
     * Lists one directory inside the open Session's workspace for the native sidebar.
     * A null [path] keeps the directory already on screen, or starts at the root.
     */
    fun browseFiles(path: String? = null) {
        val client = bridge ?: return
        val state = _state.value
        val sessionId = state.activeSessionId ?: run {
            notifyState("先打开一个对话")
            return
        }
        val root = state.activeSession?.cwd?.takeIf { it.isNotBlank() } ?: run {
            notifyState("这个对话没有工作目录")
            return
        }
        val target = path?.takeIf { it.isNotBlank() }
            ?: state.fileListing.path.takeIf { it.isNotBlank() }
            ?: root
        _state.update { it.copy(fileListing = it.fileListing.copy(loading = true, error = null)) }
        scope.launch {
            val result = try {
                client.callObject(
                    "workspace.files",
                    JSONObject().put("root", root).put("path", target),
                    25_000
                )
            } catch (t: Throwable) {
                if (_state.value.activeSessionId == sessionId) {
                    _state.update {
                        it.copy(fileListing = it.fileListing.copy(loading = false, error = readableError(t)))
                    }
                }
                return@launch
            }
            if (_state.value.activeSessionId != sessionId) return@launch
            val entries = result.optJSONArray("entries")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val name = item.optString("name")
                    val filePath = item.optString("path")
                    if (name.isBlank() || filePath.isBlank()) return@mapNotNull null
                    AgentFileEntry(
                        name = name,
                        path = filePath,
                        type = item.optString("type").ifBlank { "file" },
                        size = item.optLong("size"),
                        modified = item.optLong("mtime")
                    )
                }
            } ?: emptyList()
            _state.update {
                it.copy(
                    fileListing = AgentFileListing(
                        root = result.optString("root").ifBlank { root },
                        path = result.optString("path").ifBlank { target },
                        parent = result.optString("parent").takeIf { parent -> parent.isNotBlank() },
                        entries = entries,
                        truncated = result.optBoolean("truncated")
                    )
                )
            }
        }
    }

    /**
     * Reads one workspace file for the native preview. Bytes come back base64 so the
     * viewer can pick a charset or decode an image; the bridge caps the prefix.
     */
    fun openFilePreview(path: String) {
        val client = bridge ?: return
        val state = _state.value
        val sessionId = state.activeSessionId ?: return
        val root = state.activeSession?.cwd?.takeIf { it.isNotBlank() } ?: run {
            notifyState("这个对话没有工作目录")
            return
        }
        _state.update { it.copy(filePreview = AgentFilePreview(path = path, loading = true)) }
        scope.launch {
            val result = try {
                client.callObject(
                    "workspace.readFile",
                    JSONObject().put("root", root).put("path", path),
                    40_000
                )
            } catch (t: Throwable) {
                if (_state.value.activeSessionId == sessionId) {
                    _state.update {
                        it.copy(filePreview = AgentFilePreview(path = path, error = readableError(t)))
                    }
                }
                return@launch
            }
            if (_state.value.activeSessionId != sessionId) return@launch
            _state.update {
                it.copy(
                    filePreview = AgentFilePreview(
                        path = result.optString("path").ifBlank { path },
                        name = result.optString("name").ifBlank { path.substringAfterLast('/') },
                        size = result.optLong("size"),
                        base64 = result.optString("base64"),
                        truncated = result.optBoolean("truncated")
                    )
                )
            }
        }
    }

    /** Drops the loaded preview so its bytes do not linger for the next file. */
    fun closeFilePreview() {
        if (_state.value.filePreview == AgentFilePreview()) return
        _state.update { it.copy(filePreview = AgentFilePreview()) }
    }

    /** Maps `/root/shared/...` to the app's bind-mounted host directory. */
    private fun sharedGuestToHost(guestPath: String): File? {
        val prefix = "/root/shared/"
        if (!guestPath.startsWith(prefix)) return null
        val root = RootfsManager(appContext).sharedDir
        return try {
            val rootPath = root.canonicalFile.path + File.separator
            val candidate = File(root, guestPath.removePrefix(prefix)).canonicalFile
            if (candidate.path.startsWith(rootPath) && candidate.isFile) candidate else null
        } catch (_: Exception) {
            null
        }
    }

    /** Maps a guest path under Kali's export directory to its app-private host file. */
    private fun guestToHost(guestPath: String): File? {
        val normalized = guestPath.trim()
        if (!normalized.startsWith("/root/.dsh/exports/")) return null
        val root = RootfsManager(appContext).rootfsDir
        return try {
            val rootPath = root.canonicalFile.path + File.separator
            val candidate = File(root, normalized.removePrefix("/")).canonicalFile
            if (!candidate.path.startsWith(rootPath)) null else candidate
        } catch (_: Exception) {
            null
        }
    }

    /** Keeps the five newest archives in the cache; older exports are temporary by design. */
    private fun pruneExportCache(directory: File, keep: File) {
        runCatching {
            val entries = directory
                .listFiles { file -> file.isFile && file.name.endsWith(".zip", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            for (old in entries.drop(5)) {
                if (old != keep) old.delete()
            }
        }
    }

    fun newSession(workspaceId: String? = null) {
        if (_state.value.connection != AgentConnection.Ready) {
            notifyState("Agent 尚未连接")
            return
        }
        if (_state.value.subagentView != null) closeSubagent()
        val active = _state.value.activeSession
        // Reusing the open blank chat is only right for the ungrouped shelf; a workspace
        // tap must create a session that dsh files under that workspace.
        if (workspaceId == null && active?.blank == true && _state.value.items.isEmpty()) return
        scope.launch { createSessionInternal(workspaceId) }
    }

    private suspend fun createSessionInternal(workspaceId: String? = null) {
        if (creatingSession) return
        val client = bridge ?: return
        creatingSession = true
        try {
            val params = JSONObject()
            if (!workspaceId.isNullOrBlank()) params.put("workspaceId", workspaceId)
            val result = client.callObject("session.create", params, 30_000)
            val id = result.optString("sessionId")
            if (id.isBlank()) {
                appendLog("session.create 没有返回 sessionId")
                return
            }
            val now = System.currentTimeMillis()
            val session = AgentSession(id = id, blank = true, updatedAt = now)
            _state.update { state ->
                state.copy(
                    sessions = listOf(session) + state.sessions.filterNot { it.id == id }
                )
            }
            activateSession(id, forceLoad = false)
            if (!workspaceId.isNullOrBlank()) {
                _state.update { state ->
                    state.copy(
                        workspaces = state.workspaces.map { workspace ->
                            if (workspace.id == workspaceId && id !in workspace.sessionIds) {
                                workspace.copy(sessionIds = workspace.sessionIds + id)
                            } else {
                                workspace
                            }
                        }
                    )
                }
                refreshWorkspaces()
            }
        } catch (t: Throwable) {
            addNotice("无法创建对话：${readableError(t)}", isError = true)
        } finally {
            creatingSession = false
        }
    }

    private suspend fun loadHistory(id: String, beforeSeq: Long?, token: Long) {
        val client = bridge ?: return
        val view = _state.value.subagentView?.takeIf { it.id == id }
        val params = JSONObject()
        if (view == null) {
            params.put("sessionId", id)
        } else {
            params
                .put("parentSessionId", view.parentId)
                .put("childSessionId", view.id)
                .put("mode", view.mode)
        }
        params.put("maxMessages", HISTORY_LIMIT)
        if (beforeSeq != null) params.put("beforeSeq", beforeSeq)

        val result = try {
            client.callObject(
                if (view == null) "session.history" else "subagent.history",
                params,
                timeoutMs = 60_000
            )
        } catch (t: Throwable) {
            eventMutex.withLock {
                if (token == loadToken && historyLoadingFor == id) {
                    historyLoadingFor = null
                    historyLoadFailedFor = id
                    // A reconnect reloads the transcript on recovery (see stream.recovered),
                    // so an outage here is not something the user has to act on.
                    if (isConnectivityFailure(t)) {
                        appendLog("读取历史暂停：${readableError(t)}，连接恢复后自动重试")
                    } else {
                        addNotice("读取历史失败：${readableError(t)}", isError = true)
                    }
                }
            }
            return
        }

        val events = result.optJSONArray("events") ?: JSONArray()
        val entries = mutableListOf<JSONObject>()
        for (index in 0 until events.length()) {
            events.optJSONObject(index)?.let(entries::add)
        }

        eventMutex.withLock {
            if (token != loadToken || _state.value.activeSessionId != id) return@withLock
            historyLoadFailedFor = null
            val foldItems = if (beforeSeq == null) {
                liveBlocks.clear()
                mutableListOf()
            } else {
                _state.value.items.toMutableList()
            }
            val foldBlocks = if (beforeSeq == null) {
                liveBlocks
            } else {
                LinkedHashMap()
            }
            if (beforeSeq != null) {
                // Older pages never contain in-flight deltas in practice; keep the fold local
                // so they cannot overwrite the live tail's blocks.
            }
            val newItems = mutableListOf<AgentItem>()
            val replayedDeliverables = mutableListOf<AgentDeliverable>()
            for (entry in entries) {
                foldEvent(newItems, foldBlocks, entry)
                if (entry.optString("kind") == "deliverables") {
                    replayedDeliverables += parseDeliverableFiles(entry.optJSONArray("files"))
                }
            }
            // Deliverables live beside the transcript rather than in it; a full reload
            // rebuilds them from history, and an older page prepends its announcements.
            val mergedDeliverables = if (beforeSeq == null) {
                mergeDeliverables(emptyList(), replayedDeliverables)
            } else {
                mergeDeliverables(replayedDeliverables, _state.value.deliverables)
            }
            val merged = if (beforeSeq == null) {
                newItems
            } else {
                newItems + foldItems
            }
            val firstSeq = entries.asSequence()
                .mapNotNull { seqOf(it) }
                .minOrNull()
            val oldest = if (beforeSeq == null) firstSeq else {
                minOfNotNull(_state.value.oldestSeq, firstSeq)
            }
            if (beforeSeq == null && view == null) {
                result.optJSONObject("projections")?.let { applyProjectionsLocked(id, it) }
            }
            _state.update {
                it.copy(
                    items = merged,
                    deliverables = mergedDeliverables,
                    hasMoreHistory = result.optBoolean("hasMore"),
                    oldestSeq = oldest
                )
            }
            if (beforeSeq == null && pendingRecoveryNotice) {
                pendingRecoveryNotice = false
                addNotice("与 Agent 的连接已恢复", isError = false)
            }
            if (beforeSeq == null && view == null && pendingInterruptionNotice) {
                pendingInterruptionNotice = false
                addNotice(
                    "连接曾在工具调用中途断开，已自动重连。dsh 会为那次调用补一条恢复结果；" +
                        "请核对实际操作是否已经执行，再决定是否重试。",
                    isError = false
                )
            }
            // Both the initial page and older pages release the live-event buffer here;
            // leaving `historyLoadingFor` set on an older page would block every later
            // `loadOlder()` and strand the frames that arrived meanwhile.
            historyLoadingFor = null
            val buffered = bufferedLiveEvents.toList()
            bufferedLiveEvents.clear()
            buffered.forEach { handleSessionEventLocked(it) }
        }
    }

    private fun minOfNotNull(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> minOf(a, b)
    }

    private fun handleSessionEvent(data: JSONObject) {
        // Called with eventMutex held.
        handleSessionEventLocked(data)
    }

    private fun handleSessionEventLocked(data: JSONObject) {
        val sessionId = data.optString("sessionId")
        if (sessionId.isBlank()) return
        val kind = data.optString("kind")

        if (historyLoadingFor == sessionId && kind != "session.title") {
            if (bufferedLiveEvents.size < 2_000) bufferedLiveEvents += data
            return
        }

        val isActive = sessionId == _state.value.activeSessionId
        // Files usually change during a turn. The sidebar watches this counter and
        // reloads its directory, so an agent edit appears without a manual refresh.
        if (isActive && kind == "changes") {
            _state.update { it.copy(fileRevision = it.fileRevision + 1) }
        }
        when (kind) {
            "session.title" -> {
                updateSessionTitle(sessionId, data.optString("title"))
                return
            }
            "turn.start" -> {
                updateSessionRunning(sessionId, true)
                if (isActive) _state.update { it.copy(running = true, lastEventAt = now()) }
                return
            }
            "turn.end" -> {
                updateSessionRunning(sessionId, false)
                val reason = data.optJSONObject("reason")
                val reasonKind = reason?.optString("kind").orEmpty()
                val message = reason?.optString("message").orEmpty()
                val cancelled = reasonKind == "user" || reasonKind == "cancel" ||
                    reasonKind == "cancelled"
                val scheduled = sessionTrigger.remove(sessionId) == "schedule"
                if (isActive) {
                    cancelVerifyJob?.cancel()
                    _state.update { state ->
                        state.copy(
                            running = false,
                            turnStopping = false,
                            fileRevision = state.fileRevision + 1,
                            items = state.items.map {
                                when {
                                    it is AssistantItem && it.streaming ->
                                        it.copy(streaming = false)
                                    it is ToolItem && it.running -> it.copy(
                                        running = false,
                                        resultText = it.resultText.ifBlank {
                                            if (cancelled) "（已停止）" else "（没有返回结果）"
                                        }
                                    )
                                    it is WorkflowItem && it.stopReason == null ->
                                        it.copy(interrupted = true)
                                    else -> it
                                }
                            },
                            lastEventAt = now()
                        )
                    }
                    if (reasonKind == "error" || reasonKind == "failed") {
                        addNotice(
                            message.ifBlank { "本轮执行失败" },
                            isError = true,
                            seq = seqOf(data)
                        )
                    }
                }
                if (isActive && _state.value.subagentView == null) {
                    scope.launch { loadSubagents(sessionId) }
                }
                if (!cancelled) {
                    // A notification only leaves the app when no activity is visible; the
                    // timeboxed session title keeps unrelated conversations identifiable.
                    val failed = reasonKind == "error" || reasonKind == "failed"
                    notifyTask(
                        key = "turn:$sessionId",
                        title = when {
                            failed -> "对话失败"
                            scheduled -> "定时任务已完成"
                            else -> "对话已完成"
                        },
                        text = (if (failed) message else "").ifBlank { sessionTitle(sessionId) },
                        sessionId = sessionId,
                        isError = failed
                    )
                }
                // Background jobs often settle around the turn boundary; one read keeps
                // the workbench badge honest without waiting for the sheet to open.
                if (isActive) scope.launch { loadJobs(sessionId) }
                return
            }
            "context.message" -> {
                // Schedule reminders and job notices are usually for a session that is not
                // on screen; note the trigger and post the completion notice here. The
                // active transcript gets its compact line in foldEvent below.
                onContextMessage(sessionId, data)
            }
            "subagent.catalog" -> {
                // A child was established (or announced) while the turn is still running;
                // refresh the summary bar without waiting for the turn to end.
                if (isActive && _state.value.subagentView == null) {
                    scope.launch { loadSubagents(sessionId) }
                }
                return
            }
            "deliverables" -> {
                // The present tool appends one durable record per call; merging by path
                // keeps repeated presentations from growing the list.
                val files = parseDeliverableFiles(data.optJSONArray("files"))
                if (isActive && files.isNotEmpty()) {
                    _state.update {
                        it.copy(deliverables = mergeDeliverables(it.deliverables, files))
                    }
                }
                return
            }
            "todo.write" -> {
                if (isActive) {
                    _state.update { it.copy(todos = parseTodos(data.opt("todos"))) }
                }
                return
            }
            "command" -> {
                appendLog("command ${data.optString("phase")}: ${data.optString("text")}")
                if (!isActive) return
                val items = _state.value.items.toMutableList()
                foldEvent(items, liveBlocks, data)
                _state.update { it.copy(items = items, lastEventAt = now()) }
                // `/export` is dsh's own command; in the native client it should produce
                // a real archive, not only the handler's "download requested" line.
                if (data.optString("phase") == "done" &&
                    !data.optBoolean("isError") &&
                    (data.optString("name") == "export" || lastCommandWasExport())
                ) {
                    exportSession(sessionId)
                }
                return
            }
            "unknown" -> {
                // A dsh update can add event kinds at any time; one log line per distinct
                // kind keeps the in-app log readable without hiding what is unhandled.
                val dshType = data.optString("dshType")
                if (dshType.isNotBlank()) {
                    recordUnknownEvent(dshType)
                    if (dshType != lastUnknownType) {
                        lastUnknownType = dshType
                        appendLog("未知会话事件：$dshType")
                    }
                }
                return
            }
        }

        if (!isActive) return
        val items = _state.value.items.toMutableList()
        foldEvent(items, liveBlocks, data)
        _state.update { it.copy(items = items, lastEventAt = now()) }
    }

    /**
     * Folds one normalized KK-Agent event into a mutable surface. Used by both live events
     * and history replay, so the two paths cannot drift.
     */
    private fun foldEvent(
        items: MutableList<AgentItem>,
        blocks: MutableMap<String, LinkedHashMap<Int, LiveBlock>>,
        event: JSONObject
    ) {
        val kind = event.optString("kind")
        val seq = seqOf(event)
        when (kind) {
            "user.message" -> {
                val text = event.optString("text")
                val images = parseImages(event.optJSONArray("images"))
                if (text.isBlank() && images.isEmpty()) return
                val rpcId = event.optString("rpcId").takeIf { it.isNotBlank() }
                val pendingIndex = if (rpcId != null) {
                    items.indexOfLast { it is UserItem && it.rpcId == rpcId }
                } else {
                    items.indexOfLast { it is UserItem && it.pending && it.text == text }
                }
                if (pendingIndex >= 0) {
                    val item = items[pendingIndex] as UserItem
                    items[pendingIndex] = item.copy(
                        pending = false,
                        seq = seq ?: item.seq,
                        images = mergeImages(images, item.images),
                        files = item.files,
                        time = timeOf(event) ?: item.time
                    )
                    return
                }
                val key = "u:${seq ?: "local:${localIds.incrementAndGet()}"}"
                if (items.any { it.key == key }) return
                val (visibleText, files) = splitAttachmentText(text)
                items += UserItem(
                    key = key,
                    text = visibleText,
                    seq = seq,
                    source = event.optString("source").takeIf { it.isNotBlank() },
                    images = images,
                    files = files,
                    time = timeOf(event)
                )
            }

            "context.message" -> {
                // dsh injects schedule reminders and background-job completion notices as
                // user messages with a non-user source. They explain why a turn started or
                // what finished, so they become a compact line instead of a user bubble.
                val source = event.optString("source")
                val text = event.optString("text")
                val summary = event.optString("summary")
                val messageId = event.optString("messageId").takeIf { it.isNotBlank() }
                val key = "ctx:${messageId ?: seq ?: localIds.incrementAndGet()}"
                if (items.any { it.key == key }) return
                val (label, detail) = when (source) {
                    "schedule" -> "定时任务已触发" to text
                    "tool-jobs" -> "后台任务已结束" to summary.ifBlank { text }
                    else -> "已注入上下文" to text
                }
                items += NoticeItem(
                    key = key,
                    text = label,
                    seq = seq,
                    detail = detail.trim().takeIf { it.isNotBlank() }
                )
            }

            "assistant.delta" -> {
                val turn = event.optInt("turn", -1)
                val step = event.optInt("step", -1)
                val key = "a:$turn:$step"
                val chunk = event.optJSONObject("chunk") ?: return
                val index = chunk.optInt("index", 0)
                val type = chunk.optString("type")
                val map = blocks.getOrPut(key) { LinkedHashMap() }
                when (type) {
                    "block-start" -> {
                        map[index] = LiveBlock(chunk.optString("blockType", "text"), "")
                    }
                    "text-delta" -> {
                        val current = map.getOrPut(index) { LiveBlock("text", "") }
                        current.text += chunk.optString("text")
                    }
                    "reasoning-delta" -> {
                        val current = map.getOrPut(index) { LiveBlock("reasoning", "") }
                        current.text += chunk.optString("text")
                    }
                    "block-end" -> {
                        val block = chunk.optJSONObject("block")
                        val blockType = block?.optString("type")
                            ?.takeIf { it.isNotBlank() }
                            ?: map[index]?.type
                            ?: "text"
                        map[index] = LiveBlock(blockType, blockText(block))
                    }
                    "tool-call-delta", "usage" -> return
                    else -> return
                }
                val text = map.values
                    .filterNot { it.type.isReasoning() }
                    .joinToString("") { it.text }
                val reasoning = map.values
                    .filter { it.type.isReasoning() }
                    .joinToString("") { it.text }
                upsertAssistant(items, key, text, reasoning, true, seq, turn, step)
            }

            "assistant.message" -> {
                val turn = event.optInt("turn", -1)
                val step = event.optInt("step", -1)
                val key = "a:$turn:$step"
                val (text, reasoning) = assistantText(event.optJSONArray("blocks"))
                val images = parseImages(event.optJSONArray("images"))
                val index = items.indexOfFirst { it.key == key }
                if (text.isBlank() && reasoning.isBlank() && images.isEmpty() && index >= 0) {
                    val item = items[index] as AssistantItem
                    items[index] = item.copy(streaming = false, seq = seq ?: item.seq)
                } else if (text.isNotBlank() || reasoning.isNotBlank() || images.isNotEmpty()) {
                    upsertAssistant(
                        items, key, text, reasoning, false, seq, turn, step, images,
                        usage = parseUsage(event.optJSONObject("usage")),
                        messageId = event.optString("messageId").takeIf { it.isNotBlank() }
                    )
                }
                // Usage and the message id can arrive on a completion whose blocks were
                // already streamed and that therefore took the no-op branch above.
                val usage = parseUsage(event.optJSONObject("usage"))
                val messageId = event.optString("messageId").takeIf { it.isNotBlank() }
                if (usage != null || messageId != null) {
                    val at = items.indexOfFirst { it.key == key }
                    if (at >= 0) {
                        val item = items[at] as AssistantItem
                        val updated = item.copy(
                            usage = usage ?: item.usage,
                            messageId = messageId ?: item.messageId
                        )
                        if (updated != item) items[at] = updated
                    }
                }
                blocks.remove(key)
            }

            "tool.call" -> {
                val callId = event.optString("callId").takeIf { it.isNotBlank() }
                val key = "t:${callId ?: "seq:${seq ?: localIds.incrementAndGet()}"}"
                val arguments = jsonText(event.opt("arguments"), MAX_TOOL_ARGS)
                val index = items.indexOfFirst { it.key == key }
                if (index >= 0) {
                    val item = items[index] as ToolItem
                    items[index] = item.copy(
                        name = event.optString("name").ifBlank { item.name },
                        arguments = arguments.ifBlank { item.arguments },
                        seq = seq ?: item.seq,
                        startedAt = timeOf(event) ?: item.startedAt
                    )
                } else {
                    items += ToolItem(
                        key = key,
                        callId = callId ?: "",
                        name = event.optString("name").ifBlank { "tool" },
                        arguments = arguments,
                        running = true,
                        seq = seq,
                        viewJson = event.optJSONObject("view")?.toString(),
                        startedAt = timeOf(event)
                    )
                }
            }

            "tool.result" -> {
                val callId = event.optString("callId").takeIf { it.isNotBlank() }
                val key = "t:${callId ?: "seq:${seq ?: localIds.incrementAndGet()}"}"
                val result = event.optString("text").take(MAX_TOOL_RESULT)
                val isError = event.optBoolean("isError")
                val index = items.indexOfFirst { it.key == key }
                if (index >= 0) {
                    val item = items[index] as ToolItem
                    items[index] = item.copy(
                        resultText = result,
                        isError = isError,
                        running = false,
                        seq = seq ?: item.seq,
                        viewJson = event.optJSONObject("view")?.toString() ?: item.viewJson,
                        metaJson = event.optJSONObject("meta")?.toString() ?: item.metaJson,
                        finishedAt = timeOf(event) ?: item.finishedAt
                    )
                } else {
                    items += ToolItem(
                        key = key,
                        callId = callId ?: "",
                        name = "tool",
                        resultText = result,
                        isError = isError,
                        running = false,
                        seq = seq,
                        viewJson = event.optJSONObject("view")?.toString(),
                        metaJson = event.optJSONObject("meta")?.toString(),
                        finishedAt = timeOf(event)
                    )
                }
            }
            "workflow" -> {
                // Four log-only records fold into one card keyed by the run id. Members
                // are addressed by dsh's per-run seq, so a late `agent-end` settles the
                // right row even when events interleave.
                val runId = event.optString("runId").takeIf { it.isNotBlank() } ?: return
                val key = "wf:$runId"
                fun current(): WorkflowItem {
                    val at = items.indexOfFirst { it.key == key }
                    if (at >= 0) return items[at] as WorkflowItem
                    val created = WorkflowItem(key = key, runId = runId, seq = seq)
                    items += created
                    return created
                }
                fun replace(updated: WorkflowItem) {
                    val at = items.indexOfFirst { it.key == key }
                    if (at >= 0) items[at] = updated else items += updated
                }
                when (event.optString("phase")) {
                    "run-start" -> {
                        val item = current()
                        val name = event.optString("name")
                        replace(
                            item.copy(
                                name = name.ifBlank { item.name },
                                seq = seq ?: item.seq
                            )
                        )
                    }
                    "agent-start" -> {
                        val memberSeq = event.optInt("memberSeq", -1)
                        if (memberSeq < 0) return
                        val item = current()
                        val member = AgentWorkflowMember(
                            seq = memberSeq,
                            label = event.optString("label"),
                            phase = if (event.isNull("memberPhase")) null else event.optString("memberPhase"),
                            childId = event.optString("childId").takeIf { it.isNotBlank() }
                        )
                        replace(
                            item.copy(
                                members = item.members.filterNot { it.seq == memberSeq } + member,
                                seq = seq ?: item.seq
                            )
                        )
                    }
                    "agent-end" -> {
                        val memberSeq = event.optInt("memberSeq", -1)
                        val outcome = event.optString("outcome").takeIf { it.isNotBlank() }
                        if (memberSeq < 0 || outcome == null) return
                        val item = current()
                        replace(
                            item.copy(
                                members = item.members.map { member ->
                                    if (member.seq == memberSeq) member.copy(outcome = outcome) else member
                                },
                                seq = seq ?: item.seq
                            )
                        )
                    }
                    "run-end" -> {
                        val item = current()
                        replace(
                            item.copy(
                                stopReason = event.optString("stopReason").takeIf { it.isNotBlank() }
                                    ?: item.stopReason,
                                interrupted = false,
                                seq = seq ?: item.seq
                            )
                        )
                    }
                }
            }
            "compaction" -> {
                // A lifecycle bracket: `start` opens the divider, `summary` fills it with
                // what dsh folded away, `end` settles success or failure. Events with the
                // same compactionId replace each other, so live and replayed history agree.
                val phase = event.optString("phase")
                val compactionId = event.optString("compactionId").takeIf { it.isNotBlank() }
                val fallbackKey = "cmp:${seq ?: localIds.incrementAndGet()}"
                val key = compactionId?.let { "cmp:$it" } ?: fallbackKey
                val existing = items.indexOfLast {
                    if (compactionId != null) {
                        it.key == key
                    } else {
                        it is NoticeItem && it.compacting
                    }
                }
                val targetKey = if (existing >= 0) items[existing].key else key
                val foldedTokens = if (event.has("shadowedTokens") && !event.isNull("shadowedTokens")) {
                    event.optLong("shadowedTokens", -1L).takeIf { it >= 0 }
                } else {
                    null
                }
                val foldedItems = if (event.has("shadowedItems") && !event.isNull("shadowedItems")) {
                    event.optInt("shadowedItems", -1).takeIf { it >= 0 }
                } else {
                    null
                }
                when (phase) {
                    "start" -> if (existing < 0 && items.none { it.key == key }) {
                        items += NoticeItem(
                            key = targetKey,
                            text = "正在压缩上下文…",
                            seq = seq,
                            compacting = true
                        )
                    }
                    "summary" -> {
                        val notice = NoticeItem(
                            key = targetKey,
                            text = COMPACTION_NOTICE,
                            seq = seq,
                            detail = event.optString("summary").takeIf { it.isNotBlank() },
                            foldedTokens = foldedTokens,
                            foldedItems = foldedItems
                        )
                        if (existing >= 0) {
                            items[existing] = notice
                        } else {
                            val last = items.lastOrNull()
                            if (last !is NoticeItem || last.text != COMPACTION_NOTICE) items += notice
                        }
                    }
                    "end" -> {
                        val error = event.optString("error").takeIf { it.isNotBlank() }
                        if (error != null) {
                            val notice = NoticeItem(
                                key = targetKey,
                                text = "压缩未完成：$error",
                                isError = true,
                                seq = seq
                            )
                            if (existing >= 0) items[existing] = notice else items += notice
                        } else if (existing >= 0) {
                            val current = items[existing]
                            // A host that never sends a summary still gets a divider;
                            // one that did already replaced the running marker.
                            if (current is NoticeItem && current.compacting) {
                                items[existing] = NoticeItem(
                                    key = targetKey,
                                    text = COMPACTION_NOTICE,
                                    seq = seq
                                )
                            }
                        } else {
                            val last = items.lastOrNull()
                            if (last !is NoticeItem || last.text != COMPACTION_NOTICE) {
                                items += NoticeItem(
                                    key = targetKey,
                                    text = COMPACTION_NOTICE,
                                    seq = seq
                                )
                            }
                        }
                    }
                }
            }
            "command" -> {
                // Slash commands are log-only records. Rebuilding the typed line on `run`
                // and the handler's outcome on `done` keeps the transcript complete even
                // though the model never sees either record.
                val phase = event.optString("phase")
                val commandId = event.optString("commandId").takeIf { it.isNotBlank() }
                when (phase) {
                    "run" -> {
                        val name = event.optString("name")
                        if (name.isBlank()) return
                        val args = event.optString("args").trim()
                        val line = "/$name" + if (args.isBlank()) "" else " $args"
                        val key = "cmd:${commandId ?: seq ?: localIds.incrementAndGet()}"
                        if (items.none { it.key == key }) {
                            items += UserItem(key = key, text = line, seq = seq, source = "command")
                        }
                    }
                    "done" -> {
                        val isError = event.optBoolean("isError")
                        val text = event.optString("text")
                            .ifBlank { if (isError) "命令执行失败" else "" }
                        if (text.isBlank()) return
                        val key = "cmdr:${commandId ?: seq ?: localIds.incrementAndGet()}"
                        if (items.none { it.key == key }) {
                            items += NoticeItem(key = key, text = text, isError = isError, seq = seq)
                        }
                    }
                }
            }
            "turn.end" -> {
                // Live turn ends settle the transcript before this fold runs, so this
                // branch exists for history replay: a run whose turn closed without a
                // run-end is interrupted, not still running. A later run-end (background
                // runs outlive their turn) clears the flag.
                for (index in items.indices) {
                    val item = items[index]
                    if (item is WorkflowItem && item.stopReason == null && !item.interrupted) {
                        items[index] = item.copy(interrupted = true)
                    }
                }
            }
        }
    }

    private fun upsertAssistant(
        items: MutableList<AgentItem>,
        key: String,
        text: String,
        reasoning: String,
        streaming: Boolean,
        seq: Long?,
        turn: Int,
        step: Int,
        images: List<AgentImage> = emptyList(),
        usage: AgentUsage? = null,
        messageId: String? = null
    ) {
        val index = items.indexOfFirst { it.key == key }
        if (index >= 0) {
            val item = items[index] as AssistantItem
            items[index] = item.copy(
                text = text,
                reasoning = reasoning,
                streaming = streaming,
                seq = seq ?: item.seq,
                images = mergeImages(images, item.images),
                usage = usage ?: item.usage,
                messageId = messageId ?: item.messageId
            )
            return
        }
        items += AssistantItem(
            key = key,
            text = text,
            reasoning = reasoning,
            streaming = streaming,
            usage = usage,
            messageId = messageId,
            seq = seq,
            turn = turn.takeIf { it >= 0 },
            step = step.takeIf { it >= 0 },
            images = images
        )
    }

    /** Parses the bridge's durable image refs; prompt-time bytes never appear here. */
    private fun parseImages(array: JSONArray?): List<AgentImage> {
        if (array == null || array.length() == 0) return emptyList()
        val result = mutableListOf<AgentImage>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("attachmentId")
            if (id.isBlank()) continue
            result += AgentImage(
                attachmentId = id,
                mediaType = item.optString("mediaType").ifBlank { "image/png" },
                width = item.optInt("width"),
                height = item.optInt("height"),
                bytes = item.optLong("bytes"),
                name = item.optString("name").takeIf { it.isNotBlank() }
            )
        }
        return result
    }

    /**
     * dsh echoes images as durable refs, but the prompt-time bytes already rendered are
     * still the best preview. Pair them positionally while the counts agree.
     */
    private fun mergeImages(durable: List<AgentImage>, local: List<AgentImage>): List<AgentImage> {
        if (durable.isEmpty()) return local
        if (durable.size != local.size) return durable
        return durable.mapIndexed { index, image ->
            local[index].localData?.let { image.copy(localData = it) } ?: image
        }
    }

    private fun assistantText(blocks: JSONArray?): Pair<String, String> {
        if (blocks == null) return "" to ""
        val text = StringBuilder()
        val reasoning = StringBuilder()
        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: continue
            val value = blockText(block)
            if (value.isBlank()) continue
            if (block.optString("type").isReasoning()) {
                reasoning.append(value)
            } else {
                text.append(value)
            }
        }
        return text.toString() to reasoning.toString()
    }

    private fun String.isReasoning(): Boolean =
        contains("reason", ignoreCase = true) || contains("thinking", ignoreCase = true)

    private fun blockText(block: JSONObject?): String {
        if (block == null) return ""
        val direct = block.optString("text")
        if (direct.isNotBlank()) return direct
        val content = block.opt("content")
        return jsonText(content, MAX_TOOL_RESULT)
    }

    private fun addNotice(text: String, isError: Boolean, seq: Long? = null) {
        _state.update { addNoticeLocked(text, isError, seq, it) }
    }

    private fun addNoticeLocked(
        text: String,
        isError: Boolean,
        seq: Long? = null,
        state: AgentState = _state.value
    ): AgentState {
        val key = "n:${seq ?: "local:${localIds.incrementAndGet()}"}"
        if (state.items.any { it.key == key }) return state
        return state.copy(items = state.items + NoticeItem(key, text, isError, seq))
    }

    private fun notifyState(text: String) {
        addNotice(text, isError = true)
    }

    private fun sessionTitle(sessionId: String): String =
        _state.value.sessions.firstOrNull { it.id == sessionId }?.displayTitle ?: "对话"

    /** Non-user context injected by dsh: a reminder trigger or a settled background job. */
    private fun onContextMessage(sessionId: String, data: JSONObject) {
        when (data.optString("source")) {
            "schedule" -> sessionTrigger[sessionId] = "schedule"
            "tool-jobs" -> {
                val summary = data.optString("summary").ifBlank {
                    data.optString("text").trim().take(160)
                }
                val failed = summary.contains("failed", ignoreCase = true)
                val messageId = data.optString("messageId").ifBlank { data.optString("seq") }
                notifyTask(
                    key = "job:$sessionId:$messageId",
                    title = if (failed) "后台任务失败" else "后台任务完成",
                    text = summary.ifBlank { sessionTitle(sessionId) },
                    sessionId = sessionId,
                    isError = failed
                )
            }
        }
    }

    /**
     * Posts one completion notice. Notifications are deliberately quiet (low importance,
     * silent) and only leave the app while it is not on screen; tapping opens the session.
     * A user who disabled notifications keeps the in-app surface and gets no error here.
     */
    private fun notifyTask(
        key: String,
        title: String,
        text: String,
        sessionId: String?,
        isError: Boolean
    ) {
        if (appVisible.get() || !::appContext.isInitialized) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) {
            appendLog("通知已关闭，完成提醒仅在应用内显示")
            return
        }
        ensureTaskChannel(manager)
        val open = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            sessionId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SESSION_ID, it) }
        }
        val id = key.hashCode()
        val pending = PendingIntent.getActivity(
            appContext,
            id,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, TASK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(
                if (isError) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW
            )
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching {
            manager.notify(id, notification)
            taskNotificationIds += id
        }
    }

    private fun ensureTaskChannel(manager: NotificationManager) {
        if (taskChannelCreated) return
        val channel = NotificationChannel(
            TASK_CHANNEL_ID,
            "后台任务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "对话、定时任务与后台任务的完成提醒"
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
        taskChannelCreated = true
    }

    private fun clearTaskNotifications() {
        if (taskNotificationIds.isEmpty() || !::appContext.isInitialized) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        taskNotificationIds.forEach { runCatching { manager.cancel(it) } }
        taskNotificationIds.clear()
    }

    private fun handleSessionStatus(data: JSONObject) {
        val sid = data.optString("sessionId")
        val running = data.optBoolean("running")
        updateSessionRunning(sid, running)
        val state = _state.value
        val viewed = state.subagentView
        if (sid == state.activeSessionId) {
            _state.update { current ->
                current.copy(
                    running = running,
                    turnStopping = if (running) current.turnStopping else false,
                    items = if (running) current.items else current.items.map {
                        if (it is AssistantItem && it.streaming) {
                            it.copy(streaming = false)
                        } else {
                            it
                        }
                    }
                )
            }
        }
        // A settled conversation is when its children appear or stop running, so the
        // catalog on screen is refreshed at that boundary rather than polled continuously.
        val catalogParent = viewed?.id ?: state.activeSessionId
        if (!running && catalogParent != null && sid == catalogParent) {
            scope.launch { loadSubagents(catalogParent) }
        }
        // The open child has no follow stream, but its parent settling is the cheap hint
        // that the child's own activity moved.
        if (viewed != null && sid == viewed.parentId) {
            scope.launch { refreshViewStatus(viewed) }
        }
    }

    private fun handleSessionRemoved(id: String, keepRow: Boolean = false) {
        if (id.isBlank()) return
        // dsh disposes an idle child's activation while its durable session stays
        // readable. Every level of the open trail is still valid, so keep the synthetic
        // rows instead of yanking the reader back to some ancestor.
        if (_state.value.subagentTrail.any { it.id == id }) return
        val remaining = if (keepRow) {
            _state.value.sessions
        } else {
            _state.value.sessions.filterNot { it.id == id }
        }
        if (!keepRow) _state.update { it.copy(sessions = remaining) }
        if (id == _state.value.activeSessionId) {
            val archived = _state.value.archivedSessionIds
            val next = remaining.firstOrNull { it.id != id && it.id !in archived && !it.blank }
                ?: remaining.firstOrNull { it.id != id && it.id !in archived }
            if (next != null) activateSession(next.id, forceLoad = true) else scope.launch {
                createSessionInternal()
            }
        }
    }

    private fun updateSessionTitle(id: String, title: String) {
        if (id.isBlank() || title.isBlank()) return
        _state.update { state ->
            state.copy(sessions = state.sessions.map {
                if (it.id == id) it.copy(title = title, blank = false) else it
            })
        }
    }

    private fun updateSessionRunning(id: String, running: Boolean) {
        if (id.isBlank()) return
        _state.update { state ->
            val exists = state.sessions.any { it.id == id }
            val sessions = if (exists) {
                state.sessions.map {
                    if (it.id == id) {
                        it.copy(
                            running = running,
                            blank = if (running) false else it.blank,
                            updatedAt = if (running) now() else it.updatedAt
                        )
                    } else {
                        it
                    }
                }
            } else {
                state.sessions + AgentSession(id = id, running = running, blank = false)
            }
            state.copy(sessions = sessions)
        }
    }

    private suspend fun loadModels(id: String) {
        val client = bridge ?: return
        val result = try {
            client.callObject("session.models", JSONObject().put("sessionId", id), 45_000)
        } catch (t: Throwable) {
            appendLog("读取模型失败：${readableError(t)}")
            return
        }
        val models = parseModels(result)
        if (_state.value.activeSessionId == id) {
            _state.update { it.copy(models = models) }
        }
    }

    private fun parseModels(value: JSONObject): AgentModels {
        val current = value.optJSONObject("current") ?: JSONObject()
        val groups = mutableListOf<AgentModelGroup>()
        val array = value.optJSONArray("groups")
        if (array != null) {
            for (groupIndex in 0 until array.length()) {
                val groupJson = array.optJSONObject(groupIndex) ?: continue
                val providerId = groupJson.optString("id")
                val models = mutableListOf<AgentModel>()
                val modelArray = groupJson.optJSONArray("models") ?: continue
                for (modelIndex in 0 until modelArray.length()) {
                    val modelJson = modelArray.optJSONObject(modelIndex) ?: continue
                    val reasoning = modelJson.optJSONObject("reasoning")
                    val efforts = mutableListOf<AgentEffort>()
                    val effortArray = reasoning?.optJSONArray("efforts")
                    if (effortArray != null) {
                        for (effortIndex in 0 until effortArray.length()) {
                            val effort = effortArray.optJSONObject(effortIndex) ?: continue
                            val effortId = effort.optString("id")
                            if (effortId.isNotBlank()) {
                                efforts += AgentEffort(
                                    id = effortId,
                                    name = effort.optString("name").ifBlank { effortId }
                                )
                            }
                        }
                    }
                    models += AgentModel(
                        provider = providerId,
                        id = modelJson.optString("id"),
                        name = modelJson.optString("name").ifBlank { modelJson.optString("id") },
                        description = modelJson.optString("description")
                            .takeIf { it.isNotBlank() },
                        efforts = efforts,
                        defaultEffort = reasoning?.optString("defaultEffort")
                            ?.takeIf { it.isNotBlank() }
                    )
                }
                if (providerId.isNotBlank()) {
                    groups += AgentModelGroup(
                        id = providerId,
                        name = groupJson.optString("name").ifBlank { providerId },
                        models = models
                    )
                }
            }
        }
        return AgentModels(
            provider = current.optString("provider"),
            model = current.optString("model"),
            reasoningEffort = current.optString("reasoningEffort").takeIf { it.isNotBlank() },
            routable = value.optBoolean("routable"),
            groups = groups
        )
    }

    /**
     * Reads the session's slash commands. dsh resolves the list per agent, so a newly
     * created or switched session may advertise a different set; a failure only hides
     * autocomplete and never blocks the chat.
     */
    private suspend fun loadCommands(id: String) {
        val client = bridge ?: return
        val result = try {
            client.call("command.list", JSONObject().put("sessionId", id), 20_000)
        } catch (t: Throwable) {
            appendLog("读取命令列表失败：${readableError(t)}")
            return
        }
        val array = result as? JSONArray ?: return
        val commands = mutableListOf<AgentCommand>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name")
            if (name.isBlank()) continue
            val input = item.optJSONObject("input")
            commands += AgentCommand(
                name = name,
                description = item.optString("description"),
                hint = input?.optString("hint")?.takeIf { it.isNotBlank() },
                acceptsAttachments = input?.optBoolean("attachments") == true
            )
        }
        if (_state.value.activeSessionId == id) {
            _state.update { it.copy(commands = commands) }
        }
    }

    /**
     * Reads the skills visible to this session. A skill is invoked as `/name` just like
     * a command, so autocomplete shows both; the host still decides which spelling wins
     * when a skill and a command share a name.
     */
    private suspend fun loadSkills(id: String) {
        val client = bridge ?: return
        val result = try {
            client.call("skill.list", JSONObject().put("sessionId", id), 20_000)
        } catch (t: Throwable) {
            appendLog("读取技能列表失败：${readableError(t)}")
            return
        }
        val array = result as? JSONArray ?: return
        val skills = mutableListOf<AgentSkill>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name")
            if (name.isBlank()) continue
            skills += AgentSkill(
                name = name,
                description = item.optString("description"),
                whenToUse = item.optString("whenToUse").takeIf { it.isNotBlank() },
                modelInvocable = item.optBoolean("modelInvocable", true),
                path = item.optString("path").takeIf { it.isNotBlank() }
            )
        }
        if (_state.value.activeSessionId == id) {
            _state.update { it.copy(skills = skills) }
        }
    }

    /** Re-reads the catalogs a slash menu or model picker renders. */
    fun refreshCatalogs() {
        val id = _state.value.activeSessionId ?: return
        if (_state.value.connection != AgentConnection.Ready) return
        if (_state.value.subagentView != null) return
        scope.launch {
            loadModels(id)
            loadCommands(id)
            loadSkills(id)
        }
    }

    /**
     * `@` path completions for the open session's working directory. dsh reads no file
     * contents here, so a large workspace only costs a bounded index query. Failures
     * propagate so the picker can distinguish "no match" from "could not ask dsh".
     */
    suspend fun fileReferences(query: String): List<AgentFileReference> {
        val client = bridge ?: return emptyList()
        val sessionId = _state.value.activeSessionId ?: return emptyList()
        val result = client.call(
            "reference.files",
            JSONObject().put("sessionId", sessionId).put("query", query),
            20_000
        )
        return parseFileReferences(result)
    }

    /**
     * `@` conversation completions. The canonical mention dsh returns is the only part
     * that reaches the prompt; everything else labels the picker. The open conversation
     * is never offered as a reference to itself.
     */
    suspend fun sessionReferences(query: String): List<AgentSessionReference> {
        val client = bridge ?: return emptyList()
        val sessionId = _state.value.activeSessionId ?: return emptyList()
        val result = client.call(
            "reference.sessions",
            JSONObject().put("sessionId", sessionId).put("query", query),
            20_000
        )
        return parseSessionReferences(result).filterNot { it.sessionId == sessionId }
    }

    /**
     * dsh's speech Remote: provider roster, readiness and audio limits. Missing when the
     * experimental speech plugin is not installed; callers show that as "unavailable".
     */
    suspend fun speechCatalog(): AgentSpeechCatalog {
        val client = bridge ?: throw IllegalStateException("Agent 尚未连接")
        val value = client.call("speech.catalog", JSONObject(), 20_000)
        return parseSpeechCatalog(value)
    }

    /** Starts or joins the Host-owned model preparation task for [providerId]. */
    suspend fun prepareSpeech(providerId: String) {
        val client = bridge ?: throw IllegalStateException("Agent 尚未连接")
        client.call(
            "speech.prepare",
            JSONObject().put("providerId", providerId),
            30_000
        )
    }

    suspend fun cancelSpeechPreparation(providerId: String) {
        val client = bridge ?: throw IllegalStateException("Agent 尚未连接")
        client.call(
            "speech.cancelPreparation",
            JSONObject().put("providerId", providerId),
            60_000
        )
    }

    /**
     * Sends one 16 kHz mono PCM16 WAV recording for transcription. The bytes are held only
     * for this call: dsh keeps audio transient and records just the returned text when the
     * user later submits it as an ordinary message.
     */
    suspend fun transcribeSpeech(
        wav: ByteArray,
        providerId: String?,
        language: String?
    ): String {
        val client = bridge ?: throw IllegalStateException("Agent 尚未连接")
        val payload = JSONObject()
            .put("audioBase64", Base64.encodeToString(wav, Base64.NO_WRAP))
        providerId?.takeIf { it.isNotBlank() }?.let { payload.put("providerId", it) }
        language?.takeIf { it.isNotBlank() }?.let { payload.put("language", it) }
        val value = client.call("speech.transcribe", payload, 200_000)
        return (value as? JSONObject)?.optString("text").orEmpty()
    }

    private fun parseSpeechCatalog(value: Any?): AgentSpeechCatalog {
        val root = value as? JSONObject ?: return AgentSpeechCatalog()
        val providers = mutableListOf<AgentSpeechProvider>()
        val array = root.optJSONArray("providers")
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val preparation = item.optJSONObject("preparation")
                val estimate = item.optJSONObject("setupEstimate")
                val languages = mutableListOf<String>()
                item.optJSONArray("languages")?.let { list ->
                    for (position in 0 until list.length()) {
                        list.optString(position).takeIf { it.isNotBlank() }?.let(languages::add)
                    }
                }
                providers += AgentSpeechProvider(
                    id = id,
                    name = item.optString("name").ifBlank { id },
                    location = item.optString("location"),
                    languages = languages,
                    phase = preparation?.optString("phase").orEmpty(),
                    completedBytes = preparation?.optLong("completedBytes") ?: 0L,
                    totalBytes = preparation
                        ?.takeIf { it.has("totalBytes") }
                        ?.optLong("totalBytes"),
                    message = preparation
                        ?.optString("message")
                        ?.takeIf { it.isNotBlank() },
                    diskBytes = estimate
                        ?.takeIf { it.has("recommendedDiskBytes") }
                        ?.optLong("recommendedDiskBytes"),
                    memoryBytes = estimate
                        ?.takeIf { it.has("expectedMemoryBytes") }
                        ?.optLong("expectedMemoryBytes"),
                    minMinutes = estimate
                        ?.takeIf { it.has("minimumMinutes") }
                        ?.optInt("minimumMinutes"),
                    maxMinutes = estimate
                        ?.takeIf { it.has("maximumMinutes") }
                        ?.optInt("maximumMinutes")
                )
            }
        }
        val selection = root.optJSONObject("selection")
        return AgentSpeechCatalog(
            providers = providers,
            selectedProviderId = selection?.optString("providerId").orEmpty(),
            language = selection?.optString("language").orEmpty(),
            maxAudioBytes = root.optLong("maxAudioBytes").takeIf { it > 0 }
                ?: 10L * 1024L * 1024L,
            maxDurationSeconds = root.optInt("maxDurationSeconds").takeIf { it > 0 } ?: 60
        )
    }

    private fun parseFileReferences(value: Any?): List<AgentFileReference> {        val array = value as? JSONArray ?: return emptyList()
        val files = mutableListOf<AgentFileReference>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val path = item.optString("path")
            if (path.isBlank()) continue
            files += AgentFileReference(
                path = path,
                directory = item.optString("kind") == "directory"
            )
        }
        return files
    }

    private fun parseSessionReferences(value: Any?): List<AgentSessionReference> {
        val array = value as? JSONArray ?: return emptyList()
        val sessions = mutableListOf<AgentSessionReference>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val sessionId = item.optString("sessionId")
            val mention = item.optString("mention")
            if (sessionId.isBlank() || mention.isBlank()) continue
            sessions += AgentSessionReference(
                sessionId = sessionId,
                label = item.optString("label").ifBlank { sessionId },
                displayTitle = item.optString("displayTitle").takeIf { it.isNotBlank() },
                cwd = item.optString("cwd").takeIf { it.isNotBlank() },
                sameWorkspace = item.optBoolean("sameWorkspace", false),
                createdAt = item.optLong("createdAt", 0L),
                mention = mention
            )
        }
        return sessions
    }


    /** Which conversation's children the subagent panel currently shows. */
    private fun catalogParent(state: AgentState): String? =
        state.subagentView?.id ?: state.activeSessionId

    /**
     * Reads one conversation's direct-child catalog from dsh. The same call serves the root
     * and any open child, which is what makes drill-down possible. Child sessions are a
     * dsh-owned hierarchy, so the raw activity/mode values are kept instead of inferred.
     */
    private suspend fun fetchSubagents(
        parentId: String,
        logFailure: Boolean = true
    ): Pair<List<AgentSubagent>, Boolean>? {
        val client = bridge ?: return null
        if (_state.value.capabilities.isNotEmpty() &&
            "subagent.list" !in _state.value.capabilities
        ) {
            return null
        }
        val result = try {
            client.callObject(
                "subagent.list",
                JSONObject().put("parentSessionId", parentId),
                timeoutMs = 20_000
            )
        } catch (t: Throwable) {
            // An older dsh without the subagent domain simply has no panel to show.
            if (logFailure) appendLog("读取子 Agent 失败：${readableError(t)}")
            return null
        }
        val entries = result.optJSONArray("entries") ?: JSONArray()
        val subagents = mutableListOf<AgentSubagent>()
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            val kind = item.optString("kind")
            if (kind == "diagnostic") {
                subagents += AgentSubagent(
                    parentId = parentId,
                    id = id,
                    diagnostic = item.optString("reason").ifBlank { "unavailable" }
                )
                continue
            }
            subagents += AgentSubagent(
                parentId = parentId,
                id = id,
                label = item.optString("label").takeIf { it.isNotBlank() },
                title = item.optString("title").takeIf { it.isNotBlank() },
                mode = item.optString("mode").ifBlank { "one-shot" },
                running = item.optString("activity") == "running",
                hasChildren = item.optBoolean("hasChildren"),
                tokens = item.longOrNull("tokens"),
                durationMs = item.longOrNull("durationMs"),
                completed = item.optBoolean("completed")
            )
        }
        return subagents to result.optBoolean("parentAvailable", true)
    }

    /** Replaces the visible catalog, unless the screen moved to another level meanwhile. */
    private suspend fun loadSubagents(parentId: String) {
        val fetched = fetchSubagents(parentId) ?: return
        eventMutex.withLock {
            if (catalogParent(_state.value) != parentId) return@withLock
            _state.update {
                it.copy(
                    subagents = fetched.first,
                    subagentsParentAvailable = fetched.second
                )
            }
        }
    }

    /**
     * Reads the open child's own activity from its parent's catalog. A child transcript has
     * no follow stream, so this is the only place its running flag changes on its own.
     */
    private suspend fun refreshViewStatus(view: AgentSubagent) {
        val fetched = fetchSubagents(view.parentId, logFailure = false) ?: return
        val entry = fetched.first.firstOrNull { it.id == view.id && it.diagnostic == null }
            ?: return
        eventMutex.withLock {
            val current = _state.value
            val viewed = current.subagentView ?: return@withLock
            if (viewed.id != view.id) return@withLock
            if (current.running == entry.running && viewed.running == entry.running) {
                return@withLock
            }
            _state.update { state ->
                state.copy(
                    running = entry.running,
                    sessions = state.sessions.map {
                        if (it.id == view.id) it.copy(running = entry.running) else it
                    },
                    subagentTrail = state.subagentTrail.map {
                        if (it.id == view.id) it.copy(running = entry.running) else it
                    }
                )
            }
        }
    }

    /** Refreshes the catalog on screen; the panel calls this when it opens. */
    fun refreshSubagents() {
        val state = _state.value
        val view = state.subagentView
        val parentId = view?.id ?: state.activeSessionId
        if (parentId.isNullOrBlank()) return
        scope.launch {
            loadSubagents(parentId)
            if (view != null) refreshViewStatus(view)
        }
    }

    /**
     * Child history is a snapshot, not a stream: while the viewed child reports running the
     * engine refetches its transcript on a slow cadence and returns to event-driven behavior
     * (the normal session flow) once the catalog says it is inactive. The same pass keeps
     * the child's own children visible, so a delegation made while drilled in shows up.
     */
    private fun pollSubagent(view: AgentSubagent) {
        subagentPoll?.cancel()
        subagentPoll = scope.launch {
            repeat(SUBTAGENT_POLLS) {
                delay(SUBTAGENT_POLL_MS)
                if (_state.value.subagentView?.id != view.id) return@launch
                refreshViewStatus(view)
                loadSubagents(view.id)
                val running = _state.value.running
                loadHistory(view.id, beforeSeq = null, token = loadToken)
                if (!running) return@launch
            }
        }
    }

    /** Reads the configured state of the credential references this app surfaces. */
    fun refreshCredentials() {
        scope.launch { describeCredentials() }
    }

    private suspend fun describeCredentials(): Boolean {
        val client = bridge ?: return false
        if (_state.value.connection != AgentConnection.Ready) return false
        val result = try {
            client.callObject(
                "credentials.describe",
                JSONObject().put("refs", JSONArray(credentialRefs)),
                20_000
            )
        } catch (t: Throwable) {
            appendLog("读取凭据状态失败：${readableError(t)}")
            return false
        }
        applyCredentials(result)
        return true
    }

    private fun applyCredentials(result: JSONObject) {
        val refs = result.optJSONObject("refs") ?: JSONObject()
        val parsed = credentialRefs.associateWith { ref ->
            val view = refs.optJSONObject(ref) ?: JSONObject()
            AgentCredential(
                ref = ref,
                configured = view.optBoolean("configured"),
                source = view.optString("source").takeIf { it.isNotBlank() },
                writable = if (view.has("writable")) view.optBoolean("writable") else true
            )
        }
        _state.update { it.copy(credentials = parsed) }
    }

    fun setCredential(ref: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            notifyState("凭据内容不能为空")
            return
        }
        val client = bridge
        if (client == null || _state.value.connection != AgentConnection.Ready) {
            notifyState("Agent 尚未连接")
            return
        }
        scope.launch {
            try {
                client.callObject(
                    "credentials.set",
                    JSONObject().put("ref", ref).put("value", trimmed),
                    20_000
                )
            } catch (t: Throwable) {
                appendLog("保存凭据失败：${readableError(t)}")
                notifyState("保存凭据失败：${readableError(t)}")
                return@launch
            }
            appendLog("已保存凭据 $ref")
            describeCredentials()
            // The provider becomes routable as soon as the key exists; refresh the model
            // catalog so the composer unlocks without a manual reconnect.
            _state.value.activeSessionId?.let { loadModels(it) }
        }
    }

    fun removeCredential(ref: String) {
        val client = bridge
        if (client == null || _state.value.connection != AgentConnection.Ready) {
            notifyState("Agent 尚未连接")
            return
        }
        scope.launch {
            try {
                client.callObject("credentials.unset", JSONObject().put("ref", ref), 20_000)
            } catch (t: Throwable) {
                appendLog("清除凭据失败：${readableError(t)}")
                notifyState("清除凭据失败：${readableError(t)}")
                return@launch
            }
            appendLog("已清除凭据 $ref")
            describeCredentials()
            _state.value.activeSessionId?.let { loadModels(it) }
        }
    }

    // -----------------------------------------------------------------------
    // Model providers (DSH-501)
    // -----------------------------------------------------------------------

    /**
     * Reads the `llm-pi-ai` provider directory: routes the installed catalog ships plus
     * the profiles the user wrote, each with an editable (secret-free) profile and the
     * settings revision a write must carry. A dsh without that namespace reports
     * `supported = false` and the UI points at the Harness instead.
     */
    fun refreshModelProviders() {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) return
        scope.launch {
            val result = try {
                client.callObject("model.providers", JSONObject(), 30_000)
            } catch (t: Throwable) {
                appendLog("读取模型供应商失败：${readableError(t)}")
                _state.update {
                    it.copy(
                        modelProviders = it.modelProviders.copy(
                            loaded = true,
                            error = readableError(t)
                        )
                    )
                }
                return@launch
            }
            applyModelProviders(result)
        }
    }

    private fun applyModelProviders(result: JSONObject) {
        val array = result.optJSONArray("providers")
        val providers = mutableListOf<AgentProvider>()
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val route = item.optString("provider")
                if (route.isBlank()) continue
                val profileJson = item.optJSONObject("profile")
                val profile = profileJson?.let { value ->
                    AgentProviderProfile(
                        displayName = (value.opt("displayName") as? String)
                            ?.takeIf { it.isNotBlank() },
                        api = (value.opt("api") as? String)?.takeIf { it.isNotBlank() },
                        baseURL = (value.opt("baseURL") as? String)?.takeIf { it.isNotBlank() },
                        apiKeyEnv = (value.opt("apiKeyEnv") as? String)
                            ?.takeIf { it.isNotBlank() },
                        models = parseProviderModels(value.optJSONArray("models"))
                    )
                }
                providers += AgentProvider(
                    provider = route,
                    displayName = item.optString("displayName").ifBlank { route },
                    declared = item.optBoolean("declared"),
                    defaultBaseURL = (item.opt("defaultBaseURL") as? String)
                        ?.takeIf { it.isNotBlank() },
                    hasProfile = item.optBoolean("hasProfile"),
                    profile = profile,
                    keyRef = (item.opt("keyRef") as? String)
                        ?.takeIf { it.isNotBlank() } ?: "",
                    keyConfigured = item.optBoolean("keyConfigured"),
                    keySource = (item.opt("keySource") as? String)?.takeIf { it.isNotBlank() },
                    keyWritable = if (item.has("keyWritable")) {
                        item.optBoolean("keyWritable")
                    } else {
                        true
                    },
                    error = (item.opt("error") as? String)?.takeIf { it.isNotBlank() }
                )
            }
        }
        val protocolsArray = result.optJSONArray("protocols")
        val protocols = if (protocolsArray != null && protocolsArray.length() > 0) {
            buildList {
                for (index in 0 until protocolsArray.length()) {
                    protocolsArray.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        } else {
            listOf("openai-completions", "openai-responses", "anthropic-messages")
        }
        val default = result.optJSONObject("default")
        _state.update {
            it.copy(
                modelProviders = AgentModelProviders(
                    loaded = true,
                    supported = result.optBoolean("supported", true),
                    writable = result.optBoolean("writable"),
                    revision = result.optInt("revision"),
                    protocols = protocols,
                    providers = providers,
                    defaultProvider = (default?.opt("provider") as? String)
                        ?.takeIf { value -> value.isNotBlank() },
                    defaultModel = (default?.opt("model") as? String)
                        ?.takeIf { value -> value.isNotBlank() },
                    error = null
                )
            )
        }
    }

    private fun parseProviderModels(array: JSONArray?): List<AgentProviderModel> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                if (id.isEmpty()) continue
                add(
                    AgentProviderModel(
                        id = id,
                        name = (item.opt("name") as? String) ?: "",
                        contextWindow = item.positiveInt("contextWindow"),
                        maxTokens = item.positiveInt("maxTokens"),
                        inputModalities = item.optJSONArray("inputModalities")?.let { values ->
                            buildList {
                                for (valueIndex in 0 until values.length()) {
                                    values.optString(valueIndex)
                                        .takeIf { it == "text" || it == "image" }
                                        ?.let(::add)
                                }
                            }
                        } ?: emptyList()
                    )
                )
            }
        }
    }

    /**
     * Saves one provider profile and, when the form supplied one, its API key. The key
     * goes straight into dsh's credential store and never enters app state or logs; the
     * settings write happens first so a rejected profile leaves no stray credential.
     */
    fun saveModelProvider(draft: AgentProviderDraft, defaultModel: String?) {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) {
            notifyState("Agent 尚未连接")
            return
        }
        val route = draft.route.trim()
        if (!route.matches(Regex("^[a-z][a-z0-9-]*$"))) {
            notifyState("供应商 ID 只能包含小写字母、数字和连字符，并以字母开头")
            return
        }
        val state = _state.value
        if (!state.modelProviders.writable) {
            notifyState("当前 dsh 配置不可写")
            return
        }
        _state.update {
            it.copy(providerSave = AgentProviderSave(route = route, saving = true))
        }
        scope.launch {
            val models = JSONArray()
            draft.models.forEach { model ->
                models.put(
                    JSONObject().put("id", model.id).apply {
                        if (model.name.isNotBlank()) put("name", model.name)
                        model.contextWindow?.let { put("contextWindow", it) }
                        model.maxTokens?.let { put("maxTokens", it) }
                        if (model.inputModalities.isNotEmpty()) {
                            put(
                                "inputModalities",
                                org.json.JSONArray().apply {
                                    model.inputModalities.forEach { put(it) }
                                }
                            )
                        }
                    }
                )
            }
            val params = JSONObject()
                .put("route", route)
                .put("displayName", draft.displayName.trim())
                .put("api", draft.api.trim())
                .put("baseURL", draft.baseURL.trim())
                .put("apiKeyRef", draft.apiKeyRef.trim())
                .put("apiKey", draft.apiKey)
                .put("models", models)
            draft.previousRoute?.trim()?.takeIf { it.isNotEmpty() && it != route }?.let {
                params.put("previousRoute", it)
            }
            if (state.modelProviders.revision > 0) {
                params.put("expectedRevision", state.modelProviders.revision)
            }
            val result = try {
                client.callObject("model.saveProvider", params, 60_000)
            } catch (t: Throwable) {
                val message = readableError(t)
                appendLog("保存供应商失败：$message")
                notifyState("保存供应商失败：$message")
                _state.update {
                    it.copy(
                        providerSave = AgentProviderSave(
                            route = route,
                            error = message
                        )
                    )
                }
                refreshModelProviders()
                return@launch
            }
            val keyError = (result.opt("keyError") as? String)?.takeIf { it.isNotBlank() }
            if (keyError != null) {
                appendLog("供应商 $route 已保存，但密钥写入失败：$keyError")
                notifyState("供应商已保存，但密钥写入失败：$keyError")
            } else if (result.optBoolean("keyStored")) {
                appendLog("已保存供应商 $route，并写入密钥")
                notifyState("已保存 $route")
            } else {
                appendLog("已保存供应商 $route")
                notifyState("已保存 $route")
            }
            _state.update {
                it.copy(
                    providerSave = AgentProviderSave(
                        route = route,
                        done = keyError == null,
                        error = keyError
                    )
                )
            }
            refreshModelProviders()
            _state.value.activeSessionId?.let { loadModels(it) }
            // A route whose key failed cannot serve yet; making it the default would
            // break the next new conversation, so the default is only written on success.
            if (keyError == null && !defaultModel.isNullOrBlank()) {
                setDefaultModel(route, defaultModel, null)
            }
        }
    }

    /**
     * Removes one provider profile. Deleting the route that currently supplies the
     * session or the default model needs [confirmed], because dsh would otherwise keep a
     * selection that can no longer be resolved. The credential can be left in place.
     */
    fun removeModelProvider(
        route: String,
        apiKeyRef: String?,
        deleteKey: Boolean,
        confirmed: Boolean
    ) {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) {
            notifyState("Agent 尚未连接")
            return
        }
        if (!_state.value.modelProviders.writable) {
            notifyState("当前 dsh 配置不可写")
            return
        }
        val state = _state.value
        val inUse = state.models?.provider == route ||
            state.modelProviders.defaultProvider == route
        if (inUse && !confirmed) {
            notifyState("该供应商正在提供当前或默认模型，请先切换模型")
            return
        }
        scope.launch {
            val params = JSONObject()
                .put("route", route)
                .put("deleteKey", deleteKey)
            if (!apiKeyRef.isNullOrBlank()) params.put("apiKeyRef", apiKeyRef)
            if (state.modelProviders.revision > 0) {
                params.put("expectedRevision", state.modelProviders.revision)
            }
            val result = try {
                client.callObject("model.removeProvider", params, 30_000)
            } catch (t: Throwable) {
                appendLog("删除供应商失败：${readableError(t)}")
                notifyState("删除供应商失败：${readableError(t)}")
                refreshModelProviders()
                return@launch
            }
            val keyError = (result.opt("keyError") as? String)?.takeIf { it.isNotBlank() }
            if (keyError != null) {
                notifyState("已删除 $route，但密钥清除失败：$keyError")
            } else if (result.optBoolean("keyRemoved")) {
                notifyState("已删除 $route 及其密钥")
            } else {
                notifyState("已删除 $route")
            }
            appendLog("已删除供应商 $route")
            refreshModelProviders()
            _state.value.activeSessionId?.let { loadModels(it) }
        }
    }

    /**
     * Interrogates a draft endpoint for its model list. Nothing is stored, and the key
     * exists only in this call; the reply (or its failure) lands in [AgentState.modelDiscovery]
     * for the form to show.
     */
    fun discoverProviderModels(
        provider: String,
        baseURL: String,
        api: String,
        apiKey: String
    ) {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) {
            notifyState("Agent 尚未连接")
            return
        }
        val stamp = discoveryStamp.incrementAndGet()
        _state.update {
            it.copy(
                modelDiscovery = AgentModelDiscovery(
                    stamp = stamp,
                    provider = provider,
                    baseURL = baseURL,
                    api = api,
                    loading = true
                )
            )
        }
        scope.launch {
            val params = JSONObject()
                .put("provider", provider)
                .put("baseURL", baseURL)
                .put("api", api)
                .put("apiKey", apiKey)
            try {
                val result = client.callObject("model.discover", params, 90_000)
                val models = parseProviderModels(result.optJSONArray("models"))
                _state.update {
                    if (it.modelDiscovery.stamp != stamp) it
                    else it.copy(
                        modelDiscovery = it.modelDiscovery.copy(
                            loading = false,
                            models = models,
                            error = null
                        )
                    )
                }
            } catch (t: Throwable) {
                // dsh's error names the endpoint and reason; it never echoes the key.
                appendLog("测试连接失败：${readableError(t)}")
                _state.update {
                    if (it.modelDiscovery.stamp != stamp) it
                    else it.copy(
                        modelDiscovery = it.modelDiscovery.copy(
                            loading = false,
                            models = emptyList(),
                            error = readableError(t)
                        )
                    )
                }
            }
        }
    }

    /**
     * Makes a model the default for new conversations. With an open session dsh's own
     * selection path is used (it also applies the model immediately); without one the
     * stored default entry is written.
     */
    fun setDefaultModel(provider: String, model: String, effort: String?) {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) {
            notifyState("Agent 尚未连接")
            return
        }
        if (provider.isBlank() || model.isBlank()) return
        val sessionId = _state.value.activeSessionId
        scope.launch {
            val params = JSONObject()
                .put("provider", provider)
                .put("model", model)
            if (!sessionId.isNullOrBlank()) params.put("sessionId", sessionId)
            if (!effort.isNullOrBlank()) params.put("reasoningEffort", effort)
            try {
                client.callObject("model.setDefault", params, 60_000)
            } catch (t: Throwable) {
                appendLog("设置默认模型失败：${readableError(t)}")
                notifyState("设置默认模型失败：${readableError(t)}")
                refreshModelProviders()
                return@launch
            }
            appendLog("默认模型已设为 $provider/$model")
            notifyState("默认模型已设为 $model")
            refreshModelProviders()
            if (!sessionId.isNullOrBlank()) loadModels(sessionId)
        }
    }

    /**
     * Reads the process permission catalog and the `permission` settings namespace. Both are
     * display state until the user picks a value; a failure is only logged so a missing
     * permission service cannot block the rest of the connection.
     */
    fun describePermissions() {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) return
        scope.launch {
            try {
                applyPermissionCatalog(client.callObject("permission.catalog", timeoutMs = 20_000))
            } catch (t: Throwable) {
                appendLog("读取权限目录失败：${readableError(t)}")
            }
            try {
                applyPermissionSettings(
                    client.callObject("settings.describe", timeoutMs = 20_000)
                )
            } catch (t: Throwable) {
                appendLog("读取权限设置失败：${readableError(t)}")
            }
        }
    }

    fun refreshPermissions() = describePermissions()

    /**
     * Reads dsh's agent-preset roster and the settings namespace that picks the default
     * for sessions created later. Both are display state until the user chooses a mode.
     */
    fun describeAgentPresets() {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) return
        scope.launch {
            try {
                applyAgentPresetRoster(client.callObject("agentPreset.list", timeoutMs = 20_000))
            } catch (t: Throwable) {
                appendLog("读取运行模式失败：${readableError(t)}")
            }
            var settingsLoaded = false
            try {
                applyAgentPresetSettings(client.callObject("settings.describe", timeoutMs = 20_000))
                settingsLoaded = true
            } catch (t: Throwable) {
                appendLog("读取运行模式设置失败：${readableError(t)}")
            }
            if (settingsLoaded) ensureStandardDefault(client)
        }
    }

    fun refreshAgentPresets() = describeAgentPresets()

    /**
     * Reads the curated dsh settings namespaces and dsh's locale preference. One
     * `settings/describe` feeds both, so the native forms and the language card cannot
     * disagree about the profile they are looking at.
     */
    fun refreshAgentSettings() {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) return
        scope.launch { loadAgentSettingsNow(client) }
    }

    private suspend fun loadAgentSettingsNow(client: BridgeClient) {
        _state.update {
            it.copy(
                agentSettings = it.agentSettings.copy(loading = true, error = null),
                languageSettings = it.languageSettings.copy(error = null)
            )
        }
        val result = try {
            client.callObject("settings.describe", timeoutMs = 25_000)
        } catch (t: Throwable) {
            appendLog("读取 dsh 设置失败：${readableError(t)}")
            _state.update {
                it.copy(
                    agentSettings = it.agentSettings.copy(
                        loading = false,
                        error = readableError(t)
                    ),
                    languageSettings = it.languageSettings.copy(
                        loaded = true,
                        error = readableError(t)
                    )
                )
            }
            return
        }
        val snapshot = AgentSettingsParser.parse(result)
        val language = AgentSettingsParser.parseLanguage(result)
        _state.update {
            it.copy(
                agentSettings = snapshot.copy(saving = it.agentSettings.saving),
                languageSettings = language.copy(saving = it.languageSettings.saving)
            )
        }
    }

    /**
     * Writes the changed fields of one namespace. dsh answers with the updated namespace
     * view, which replaces the stale group so the next save uses the new revision. Secret
     * values travel in this call only and are never read back.
     */
    suspend fun saveAgentSettings(
        ns: String,
        revision: Int,
        edits: List<AgentSettingEdit>
    ): AgentSettingGroup? {
        if (edits.isEmpty()) return null
        val client = bridge ?: throw IllegalStateException("Agent 尚未连接")
        if (_state.value.connection != AgentConnection.Ready) {
            throw IllegalStateException("Agent 尚未连接")
        }
        _state.update {
            it.copy(agentSettings = it.agentSettings.copy(saving = true, error = null))
        }
        val ops = JSONArray()
        edits.forEach { edit ->
            val op = JSONObject()
                .put("op", if (edit.value == null) "unset" else "set")
                .put("path", JSONArray(edit.path))
            if (edit.value != null) op.put("value", edit.value)
            ops.put(op)
        }
        val params = JSONObject().put("ns", ns).put("ops", ops)
        if (revision > 0) params.put("expectedRevision", revision)
        val result = try {
            client.callObject("settings.mutate", params, 30_000)
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    agentSettings = it.agentSettings.copy(
                        saving = false,
                        error = readableError(t)
                    )
                )
            }
            throw t
        }
        val group = AgentSettingsParser.parseMutated(
            result,
            _state.value.agentSettings.writable
        )
        _state.update { current ->
            val groups = if (group == null) current.agentSettings.groups
            else current.agentSettings.groups.map { if (it.ns == group.ns) group else it }
            current.copy(
                agentSettings = current.agentSettings.copy(
                    saving = false,
                    groups = groups,
                    error = null
                )
            )
        }
        appendLog("已保存 dsh 设置：$ns（${edits.size} 项）")
        return group
    }

    /**
     * Sets dsh's Harness locale. The native UI is Chinese-only in this build, so this
     * deliberately affects the Harness page rather than the app's own strings.
     */
    fun setHarnessLocale(locale: String?) {
        val value = locale?.takeIf { it == "zh" || it == "en" }
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) {
            notifyState("Agent 尚未连接")
            return
        }
        val revision = _state.value.languageSettings.revision
        _state.update {
            it.copy(languageSettings = it.languageSettings.copy(saving = true, error = null))
        }
        scope.launch {
            val op = JSONObject()
                .put("op", if (value == null) "unset" else "set")
                .put("path", JSONArray().put("preference"))
            if (value != null) op.put("value", value)
            val params = JSONObject()
                .put("ns", "locale")
                .put("ops", JSONArray().put(op))
            if (revision > 0) params.put("expectedRevision", revision)
            try {
                val result = client.callObject("settings.mutate", params, 20_000)
                val applied = (result.optJSONObject("value")?.opt("preference") as? String)
                    ?.takeIf { it.isNotBlank() }
                _state.update {
                    it.copy(
                        languageSettings = it.languageSettings.copy(
                            loaded = true,
                            saving = false,
                            value = applied,
                            revision = result.optInt("revision", revision),
                            error = null
                        )
                    )
                }
                appendLog(if (applied == null) "Harness 语言已恢复默认" else "Harness 语言已设为 $applied")
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        languageSettings = it.languageSettings.copy(
                            saving = false,
                            error = readableError(t)
                        )
                    )
                }
                notifyState("设置 Harness 语言失败：${readableError(t)}")
            }
        }
    }

    /**
     * Reads the DeepSeek Account state, then the profile and balances when a credential is
     * stored. Profile and balance failures stay separate so one failing call does not hide
     * the other. No credential or token is ever requested here.
     */
    fun refreshAccount() {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) return
        scope.launch {
            _state.update {
                it.copy(account = it.account.copy(loading = true, error = null))
            }
            val state = try {
                client.callObject("account.state", JSONObject(), 30_000)
            } catch (t: Throwable) {
                appendLog("读取 DeepSeek 账号失败：${readableError(t)}")
                _state.update {
                    it.copy(
                        account = it.account.copy(
                            loading = false,
                            loaded = true,
                            error = readableError(t)
                        )
                    )
                }
                return@launch
            }
            val stored = state.optString("status") == "credential-stored"
            val attempt = state.optJSONObject("attempt")?.let { raw ->
                AgentAccountAttempt(
                    phase = raw.optString("phase"),
                    errorCode = raw.optString("errorCode").takeIf { it.isNotBlank() }
                )
            }
            var profile: AgentAccountProfile? = null
            var profileFailed = false
            var wallets = emptyList<AgentAccountWallet>()
            var bonusWallets = emptyList<AgentAccountWallet>()
            var balanceFailed = false
            if (stored) {
                try {
                    val result = client.callObject("account.profile", accountClient(), 45_000)
                    when (result.optString("status")) {
                        "ready" -> {
                            val value = result.optJSONObject("profile")
                            profile = AgentAccountProfile(
                                id = value?.optText("id"),
                                name = value?.optText("name"),
                                contact = value?.optText("contact"),
                                avatarUrl = value?.optText("avatarUrl")
                            )
                        }
                        "failed" -> profileFailed = true
                    }
                } catch (t: Throwable) {
                    profileFailed = true
                    appendLog("读取账号资料失败：${readableError(t)}")
                }
                try {
                    val result = client.callObject("account.balance", accountClient(), 45_000)
                    when (result.optString("status")) {
                        "ready" -> {
                            wallets = parseWallets(result.optJSONArray("wallets"))
                            bonusWallets = parseWallets(result.optJSONArray("bonusWallets"))
                        }
                        "failed" -> balanceFailed = true
                    }
                } catch (t: Throwable) {
                    balanceFailed = true
                    appendLog("读取账号余额失败：${readableError(t)}")
                }
            }
            _state.update {
                it.copy(
                    account = AgentAccountSnapshot(
                        loaded = true,
                        loading = false,
                        status = if (stored) "credential-stored" else "signed-out",
                        usageUrl = state.optText("usageUrl")
                            ?: state.optJSONObject("links")?.optText("usageUrl"),
                        topUpUrl = state.optText("topUpUrl")
                            ?: state.optJSONObject("links")?.optText("topUpUrl"),
                        attempt = attempt,
                        profile = profile,
                        profileFailed = profileFailed,
                        wallets = wallets,
                        bonusWallets = bonusWallets,
                        balanceFailed = balanceFailed
                    )
                )
            }
        }
    }

    /** Removes dsh's local account grant; remote revocation continues in the background. */
    fun signOutAccount() {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) {
            notifyState("Agent 尚未连接")
            return
        }
        if (_state.value.account.signingOut) return
        _state.update {
            it.copy(account = it.account.copy(signingOut = true, error = null))
        }
        scope.launch {
            try {
                client.callObject("account.signOut", accountClient(), 45_000)
                _state.update {
                    it.copy(
                        account = AgentAccountSnapshot(
                            loaded = true,
                            status = "signed-out"
                        )
                    )
                }
                appendLog("已退出 DeepSeek 账号（不会删除 API Key）")
                notifyState("已退出 DeepSeek 账号")
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        account = it.account.copy(
                            signingOut = false,
                            error = readableError(t)
                        )
                    )
                }
                notifyState("退出账号失败：${readableError(t)}")
            }
        }
    }

    private fun accountClient(): JSONObject = JSONObject()
        .put("version", runCatching { BuildConfig.VERSION_NAME }.getOrDefault("0.1.1"))
        .put("locale", java.util.Locale.getDefault().toLanguageTag())
        .put(
            "timezoneOffsetSeconds",
            java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000
        )

    private fun parseWallets(array: JSONArray?): List<AgentAccountWallet> {
        if (array == null) return emptyList()
        val result = mutableListOf<AgentAccountWallet>()
        for (index in 0 until array.length()) {
            val wallet = array.optJSONObject(index) ?: continue
            val currency = wallet.optText("currency") ?: continue
            val balance = wallet.opt("balance")?.toString() ?: continue
            result += AgentAccountWallet(currency = currency, balance = balance)
        }
        return result
    }

    /** Searches the configured npm registry; results are display data until inspected. */
    fun searchPlugins(query: String) {
        val text = query.trim()
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) {
            notifyState("Agent 尚未连接")
            return
        }
        _state.update {
            it.copy(
                pluginSearch = it.pluginSearch.copy(
                    query = text,
                    loading = true,
                    searched = true,
                    error = null
                )
            )
        }
        scope.launch {
            try {
                val result = client.callObject(
                    "plugin.search",
                    JSONObject().put("text", text).put("size", 30),
                    40_000
                )
                val rows = mutableListOf<AgentPluginMarketResult>()
                result.optJSONArray("results")?.let { array ->
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val name = item.optText("name") ?: continue
                        val keywords = mutableListOf<String>()
                        item.optJSONArray("keywords")?.let { words ->
                            for (position in 0 until words.length()) {
                                words.optString(position)
                                    .takeIf { it.isNotBlank() }
                                    ?.let(keywords::add)
                            }
                        }
                        rows += AgentPluginMarketResult(
                            name = name,
                            version = item.optText("version"),
                            description = item.optText("description") ?: "",
                            date = item.optText("date"),
                            publisher = item.optText("publisher"),
                            homepage = item.optText("homepage"),
                            repository = item.optText("repository"),
                            keywords = keywords,
                            official = item.optBoolean("official", false)
                        )
                    }
                }
                _state.update {
                    it.copy(
                        pluginSearch = AgentPluginSearch(
                            query = result.optText("query") ?: text,
                            registry = result.optText("registry") ?: "",
                            total = result.optInt("total", rows.size),
                            results = rows,
                            searched = true
                        )
                    )
                }
            } catch (t: Throwable) {
                appendLog("搜索插件失败：${readableError(t)}")
                _state.update {
                    it.copy(
                        pluginSearch = it.pluginSearch.copy(
                            loading = false,
                            searched = true,
                            error = readableError(t)
                        )
                    )
                }
            }
        }
    }

    fun clearPluginSearch() {
        _state.update { it.copy(pluginSearch = AgentPluginSearch()) }
    }

    /**
     * Removes one installed bundle. pnpm runs inside Kali, so the call gets the same long
     * budget as install; the inventory reloads even on failure because a failed removal
     * can still have changed the profile.
     */
    suspend fun uninstallPlugin(name: String): AgentPluginUninstall {
        val bundle = name.trim()
        if (bundle.isEmpty()) throw IllegalArgumentException("缺少插件名称")
        val client = bridge ?: throw IllegalStateException("Agent 尚未连接")
        if (_state.value.connection != AgentConnection.Ready) {
            throw IllegalStateException("Agent 尚未连接")
        }
        _state.update { it.copy(pluginRemoving = bundle) }
        try {
            val result = try {
                client.callObject(
                    "plugin.uninstall",
                    JSONObject().put("name", bundle),
                    16 * 60_000L
                )
            } catch (t: Throwable) {
                loadPluginsNow(client)
                throw t
            }
            loadPluginsNow(client)
            val removal = AgentPluginUninstall(
                changed = result.optBoolean("changed", false),
                restartRequired = result.optBoolean("restartRequired", false),
                restartReason = result.optText("restartReason"),
                bundle = result.optText("bundle") ?: bundle,
                error = result.optText("error")
            )
            appendLog(
                "插件卸载结果：${removal.bundle} changed=${removal.changed} " +
                    "error=${removal.error ?: "none"}"
            )
            return removal
        } finally {
            _state.update { it.copy(pluginRemoving = null) }
        }
    }

    private fun applyAgentPresetRoster(result: JSONObject) {
        val array = result.optJSONArray("presets")
        val presets = mutableListOf<AgentPreset>()
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                // JSON null stringifies to "null" through optString, so read the raw
                // values and keep only real strings.
                presets += AgentPreset(
                    id = id,
                    name = item.opt("name") as? String ?: "",
                    description = (item.opt("description") as? String)
                        ?.takeIf { it.isNotBlank() },
                    broken = (item.opt("broken") as? String)?.takeIf { it.isNotBlank() },
                    isDefault = item.optBoolean("isDefault")
                )
            }
        }
        _state.update { it.copy(agentPresets = presets) }
    }

    private fun applyAgentPresetSettings(result: JSONObject) {
        val namespaces = result.optJSONArray("namespaces")
        var view: JSONObject? = null
        if (namespaces != null) {
            for (index in 0 until namespaces.length()) {
                val item = namespaces.optJSONObject(index) ?: continue
                if (item.optString("ns") == AGENT_PRESET_SETTINGS_NS) {
                    view = item
                    break
                }
            }
        }
        val user = view?.optJSONObject("user")
        val value = view?.optJSONObject("value")
        val userDefault = user?.optString("selectedDefault")?.takeIf { it.isNotBlank() }
        _state.update { state ->
            state.copy(
                agentPresetSettings = AgentPresetSettings(
                    loaded = true,
                    writable = result.optBoolean("writable") && view != null,
                    revision = view?.optInt("revision") ?: 0,
                    defaultId = userDefault
                        ?: value?.optString("selectedDefault")?.takeIf { it.isNotBlank() }
                        ?: state.agentPresets.firstOrNull { it.isDefault }?.id,
                    userDefaultId = userDefault
                )
            )
        }
    }

    /**
     * dsh ships PTC as its configured default, but the app's own default is 标准模式. Only
     * a user who never picked a mode is moved, so an explicit choice is never overridden.
     */
    private suspend fun ensureStandardDefault(client: BridgeClient) {
        val state = _state.value
        val settings = state.agentPresetSettings
        if (!settings.loaded || !settings.writable) return
        if (settings.userDefaultId != null) return
        if (settings.defaultId == STANDARD_PRESET_ID) return
        if (state.agentPresets.none { it.id == STANDARD_PRESET_ID && it.broken == null }) return
        try {
            val params = JSONObject()
                .put("ns", AGENT_PRESET_SETTINGS_NS)
                .put(
                    "ops",
                    JSONArray().put(
                        JSONObject()
                            .put("op", "set")
                            .put("path", JSONArray().put("selectedDefault"))
                            .put("value", STANDARD_PRESET_ID)
                    )
                )
            if (settings.revision > 0) params.put("expectedRevision", settings.revision)
            val result = client.callObject("settings.mutate", params, 20_000)
            appendLog("已将新对话默认运行模式设为标准模式")
            _state.update { current ->
                current.copy(
                    agentPresetSettings = current.agentPresetSettings.copy(
                        defaultId = STANDARD_PRESET_ID,
                        userDefaultId = STANDARD_PRESET_ID,
                        revision = result.optInt(
                            "revision",
                            current.agentPresetSettings.revision
                        )
                    ),
                    agentPresets = current.agentPresets.map { preset ->
                        preset.copy(isDefault = preset.id == STANDARD_PRESET_ID)
                    }
                )
            }
        } catch (t: Throwable) {
            appendLog("设置默认运行模式失败：${readableError(t)}")
        }
    }

    /**
     * Makes [id] the mode new conversations compose, and applies it to the current
     * conversation while dsh still allows that (a blank session). dsh keeps a started
     * conversation on the preset it began with.
     */
    fun setAgentPreset(id: String) {
        val presetId = id.trim()
        if (presetId.isEmpty()) return
        val client = bridge ?: return
        val state = _state.value
        if (state.connection != AgentConnection.Ready) {
            notifyState("Agent 尚未连接")
            return
        }
        if (state.agentPresets.any { it.id == presetId && it.broken != null }) {
            notifyState("该运行模式当前不可用")
            return
        }
        if (!state.agentPresetSettings.writable) {
            notifyState("当前 dsh 配置不可写")
            return
        }
        scope.launch {
            val params = JSONObject()
                .put("ns", AGENT_PRESET_SETTINGS_NS)
                .put(
                    "ops",
                    JSONArray().put(
                        JSONObject()
                            .put("op", "set")
                            .put("path", JSONArray().put("selectedDefault"))
                            .put("value", presetId)
                    )
                )
            if (state.agentPresetSettings.revision > 0) {
                params.put("expectedRevision", state.agentPresetSettings.revision)
            }
            val result = try {
                client.callObject("settings.mutate", params, 20_000)
            } catch (t: Throwable) {
                eventMutex.withLock {
                    addNotice("保存运行模式失败：${readableError(t)}", isError = true)
                }
                // A revision conflict is the common failure; re-read so the next try matches.
                describeAgentPresets()
                return@launch
            }
            _state.update { current ->
                current.copy(
                    agentPresetSettings = current.agentPresetSettings.copy(
                        defaultId = presetId,
                        revision = result.optInt(
                            "revision",
                            current.agentPresetSettings.revision
                        )
                    ),
                    agentPresets = current.agentPresets.map { preset ->
                        preset.copy(isDefault = preset.id == presetId)
                    }
                )
            }

            val active = _state.value.activeSession
            var appliedNow = false
            if (active != null && active.blank) {
                try {
                    client.callObject(
                        "agentPreset.select",
                        JSONObject()
                            .put("sessionId", active.id)
                            .put("agentPreset", presetId),
                        20_000
                    )
                    appliedNow = true
                    _state.update { current ->
                        current.copy(
                            sessions = current.sessions.map { session ->
                                if (session.id == active.id) session.copy(presetId = presetId)
                                else session
                            }
                        )
                    }
                } catch (t: Throwable) {
                    appendLog("当前对话切换运行模式失败：${readableError(t)}")
                }
            }
            val label = presetLabel(presetId)
            eventMutex.withLock {
                addNotice(
                    if (appliedNow) "运行模式已切换为「$label」"
                    else "已将「$label」设为新对话的运行模式",
                    isError = false
                )
            }
        }
    }

    private fun presetLabel(id: String): String {
        val preset = _state.value.agentPresets.firstOrNull { it.id == id }
        return preset?.let(AgentPresetCopy::name) ?: id
    }

    private fun applyPermissionCatalog(result: JSONObject) {
        fun parse(array: JSONArray?): List<AgentPermissionOption> {
            val options = mutableListOf<AgentPermissionOption>()
            if (array == null) return options
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val value = item.optString("value")
                if (value.isBlank()) continue
                options += AgentPermissionOption(
                    value = value,
                    name = item.optString("name").ifBlank { value },
                    description = item.optString("description").takeIf { it.isNotBlank() }
                )
            }
            return options
        }
        _state.update {
            it.copy(
                permissionCatalog = AgentPermissionCatalog(
                    options = parse(result.optJSONArray("options")),
                    defaultOptions = parse(result.optJSONArray("defaultOptions")),
                    defaultPreset = result.optString("defaultPreset").takeIf { v -> v.isNotBlank() }
                )
            )
        }
    }

    private fun applyPermissionSettings(result: JSONObject) {
        val namespaces = result.optJSONArray("namespaces")
        var view: JSONObject? = null
        if (namespaces != null) {
            for (index in 0 until namespaces.length()) {
                val item = namespaces.optJSONObject(index) ?: continue
                if (item.optString("ns") == "permission") {
                    view = item
                    break
                }
            }
        }
        val value = view?.optJSONObject("value")
        val user = view?.optJSONObject("user")
        _state.update {
            it.copy(
                permissionSettings = AgentPermissionSettings(
                    writable = result.optBoolean("writable") && view != null,
                    revision = view?.optInt("revision") ?: 0,
                    defaultPreset = value?.optString("defaultPreset")
                        ?.takeIf { preset -> preset.isNotBlank() },
                    configured = user?.has("defaultPreset") == true
                )
            )
        }
        // KingKang's own default: a fresh profile starts every new chat with full access.
        // Only an unset profile is touched, so a preset the user picked in dsh or here wins.
        val applied = _state.value
        if (!applied.permissionSettings.configured &&
            applied.permissionSettings.writable &&
            !defaultPermissionEnsured &&
            applied.permissionCatalog.options.any { it.value == FULL_ACCESS_PRESET }
        ) {
            defaultPermissionEnsured = true
            appendLog("新会话权限未设置，改用完全访问")
            setDefaultPermission(FULL_ACCESS_PRESET)
        }
    }

    /** Switches the active session through dsh's own `/permission` command. */
    fun selectSessionPermission(value: String) {
        val sessionId = _state.value.activeSessionId ?: return
        val client = bridge ?: return
        scope.launch {
            val result = try {
                client.call(
                    "command.execute",
                    JSONObject()
                        .put("sessionId", sessionId)
                        .put("line", "/permission $value")
                        .put("submittedAttachments", JSONArray()),
                    30_000
                )
            } catch (t: Throwable) {
                eventMutex.withLock {
                    addNotice("切换权限失败：${readableError(t)}", isError = true)
                }
                return@launch
            }
            val execution = result as? JSONObject
            if (execution == null) {
                // dsh answers with no execution when no handler owns the line.
                eventMutex.withLock {
                    addNotice("dsh 没有提供 /permission 命令", isError = true)
                }
                return@launch
            }
            val outcome = execution.optJSONObject("result")
            if (outcome?.optString("kind") == "error") {
                eventMutex.withLock {
                    addNotice(
                        outcome.optString("text").ifBlank { "切换权限失败" },
                        isError = true
                    )
                }
                return@launch
            }
            // The permissions projection follows with the authoritative value; reflecting the
            // pick now keeps the chip responsive while that frame travels.
            _state.update { it.copy(permissionMode = value) }
        }
    }

    /** Runs one slash command through dsh; its `command/run`/`command/done` records render the line. */
    fun runCommand(line: String, images: List<AgentDraftImage> = emptyList()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        val state = _state.value
        val sessionId = state.activeSessionId
        val client = bridge
        if (state.connection != AgentConnection.Ready || sessionId == null || client == null) {
            notifyState("Agent 尚未连接")
            return
        }
        val submitted = JSONArray()
        images.forEach { image ->
            submitted.put(
                JSONObject()
                    .put("type", "image")
                    .put("mediaType", image.mediaType)
                    .put("data", Base64.encodeToString(image.bytes, Base64.NO_WRAP))
                    .put("name", image.name)
            )
        }
        val name = slashName(trimmed)
        scope.launch {
            val result = try {
                client.call(
                    "command.execute",
                    JSONObject()
                        .put("sessionId", sessionId)
                        .put("line", trimmed)
                        .put("submittedAttachments", submitted),
                    60_000
                )
            } catch (t: Throwable) {
                addNotice("命令执行失败：${readableError(t)}", isError = true)
                return@launch
            }
            if (result !is JSONObject) {
                // dsh answers with no execution when no handler owns the line.
                addNotice("dsh 没有执行 $trimmed", isError = true)
                return@launch
            }
            // A settled success or error is also recorded as a durable command/done event,
            // which folds into the transcript; adding it here would duplicate the notice.
            val outcome = result.optJSONObject("result")
            if (outcome?.optString("kind") == "error") {
                appendLog("命令失败：${outcome.optString("text")}")
                return@launch
            }
            // dsh-web starts its export download on the client side when `/export` settles.
            // The app's equivalent receipt is the export strip, which carries open/share/save.
            if (name.equals("export", ignoreCase = true) &&
                _state.value.capabilities.contains("session.export")
            ) {
                exportSession(sessionId)
            }
        }
    }

    /** Persists the default permission preset used by sessions created after this one. */
    fun setDefaultPermission(value: String) {
        val client = bridge ?: return
        val settings = _state.value.permissionSettings
        if (!settings.writable) {
            notifyState("当前 dsh 配置不可写")
            return
        }
        scope.launch {
            val params = JSONObject()
                .put("ns", "permission")
                .put(
                    "ops",
                    JSONArray().put(
                        JSONObject()
                            .put("op", "set")
                            .put("path", JSONArray().put("defaultPreset"))
                            .put("value", value)
                    )
                )
            if (settings.revision > 0) params.put("expectedRevision", settings.revision)
            val result = try {
                client.callObject("settings.mutate", params, 20_000)
            } catch (t: Throwable) {
                eventMutex.withLock {
                    addNotice("保存默认权限失败：${readableError(t)}", isError = true)
                }
                // A revision conflict is the common failure; re-read so the next try matches.
                describePermissions()
                return@launch
            }
            _state.update {
                it.copy(
                    permissionSettings = it.permissionSettings.copy(
                        defaultPreset = value,
                        configured = true,
                        revision = result.optInt("revision", it.permissionSettings.revision)
                    )
                )
            }
            appendLog("已设置新会话默认权限：$value")
        }
    }

    /**
     * dsh's prompt cannot carry arbitrary bytes, so the file paths are stated in the
     * text. The note is deliberately explicit: the model reads the real path instead of
     * guessing that an attachment follows, and the durable session log stays honest.
     */
    private fun attachmentText(text: String, files: List<AgentDraftFile>): String =
        attachNote(
            text,
            files.map {
                AgentFile(
                    name = it.name,
                    mimeType = it.mimeType,
                    size = it.size,
                    guestPath = it.guestPath,
                    hostPath = it.hostPath
                )
            }
        )

    private fun attachmentTextForFiles(text: String, files: List<AgentFile>): String =
        attachNote(text, files)

    private fun attachNote(text: String, files: List<AgentFile>): String {
        if (files.isEmpty()) return text
        return buildString {
            if (text.isNotBlank()) {
                append(text)
                append("\n\n")
            }
            append(ATTACHMENT_MARKER)
            append("\n已上传 ${files.size} 个文件到 Kali 共享目录：\n")
            files.forEach { file ->
                append("- ${file.guestPath}（${file.name}，${formatBytes(file.size)}）\n")
            }
            append("需要时请直接读取这些路径。")
        }
    }

    private const val ATTACHMENT_MARKER = "[KingKang 附件]"

    /**
     * Splits the path note back out of a durable history message. The note is the only
     * record of an arbitrary-file attachment after a reload, so it becomes file chips
     * instead of staying visible as prompt plumbing.
     */
    private fun splitAttachmentText(raw: String): Pair<String, List<AgentFile>> {
        val markerIndex = raw.indexOf(ATTACHMENT_MARKER)
        if (markerIndex < 0) return raw to emptyList()
        val body = raw.substring(markerIndex + ATTACHMENT_MARKER.length)
        val files = body.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- /root/shared/") }
            .mapNotNull { line ->
                val path = line.removePrefix("- ").substringBefore('（').trim()
                val details = line.substringAfter('（', "").substringBefore('）')
                val name = details.substringBefore('，').trim()
                if (path.isBlank()) return@mapNotNull null
                AgentFile(
                    name = name.ifBlank { path.substringAfterLast('/') },
                    guestPath = path
                )
            }
            .toList()
        if (files.isEmpty()) return raw to emptyList()
        val visible = raw.substring(0, markerIndex).trimEnd()
        return visible to files
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }

    fun prompt(
        text: String,
        mode: String = "queue",
        images: List<AgentDraftImage> = emptyList(),
        files: List<AgentDraftFile> = emptyList()
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && images.isEmpty() && files.isEmpty()) return
        _state.value.subagentView?.let { view ->
            promptSubagent(view, trimmed, images, files)
            return
        }
        // dsh resolves slash commands outside the model path: sending "/model …" as a
        // prompt would just show the text to the model. Route known commands through
        // commands/execute so the registry (and its durable records) owns the line.
        //
        // Attachments follow dsh's own admission rule: images travel as encoded inputs
        // only for a command that declares `input.attachments`; staged files cannot ride
        // a command yet (they need upload receipts), so the draft is refused instead of
        // silently becoming a model prompt.
        if (mode != "steer") {
            slashName(trimmed)?.let { name ->
                val command = _state.value.commands.firstOrNull {
                    it.name.equals(name, ignoreCase = true)
                }
                if (command != null) {
                    when {
                        !command.acceptsAttachments && (images.isNotEmpty() || files.isNotEmpty()) -> {
                            addNotice("/${command.name} 不接受附件，请先移除附件", isError = true)
                            return
                        }
                        files.isNotEmpty() -> {
                            addNotice("/${command.name} 暂不能携带文件附件，请先移除文件", isError = true)
                            return
                        }
                        else -> {
                            runCommand(trimmed, images)
                            return
                        }
                    }
                }
            }
        }
        val state = _state.value
        val sessionId = state.activeSessionId
        val client = bridge
        if (state.connection != AgentConnection.Ready || sessionId == null || client == null) {
            notifyState("Agent 尚未连接")
            return
        }

        // The app owns the prompt's rpcId so dsh's durable echo can be matched to this
        // bubble even when there is no text to compare (image-only prompts).
        val rpcId = "kk-${UUID.randomUUID()}"
        val key = "u:rpc:$rpcId"
        val pendingImages = images.map {
            AgentImage(
                attachmentId = "local:${it.id}",
                mediaType = it.mediaType,
                width = it.width,
                height = it.height,
                bytes = it.bytes.size.toLong(),
                name = it.name,
                localData = it.dataUrl
            )
        }
        val pendingFiles = files.map {
            AgentFile(
                name = it.name,
                mimeType = it.mimeType,
                size = it.size,
                guestPath = it.guestPath,
                hostPath = it.hostPath
            )
        }
        val outgoing = attachmentText(trimmed, files)
        val derivedTitle = trimmed.replace(Regex("\\s+"), " ").take(28)
            .ifBlank {
                when {
                    images.isNotEmpty() -> "图片"
                    files.isNotEmpty() -> "文件"
                    else -> ""
                }
            }
        // A message queued behind a running turn lives in dsh's inbox until the turn
        // settles, so the queue dock is its only surface for now; echoing it here would
        // show the same text twice and claim it is already being sent.
        val queuedBehindTurn = mode != "steer" && state.running
        _state.update { current ->
            current.copy(
                items = if (queuedBehindTurn) current.items
                else current.items + UserItem(
                    key = key,
                    text = trimmed,
                    pending = true,
                    images = pendingImages,
                    files = pendingFiles,
                    rpcId = rpcId
                ),
                running = true,
                sessions = current.sessions.map {
                    if (it.id == sessionId) {
                        it.copy(
                            title = it.title.ifBlank { derivedTitle },
                            blank = false,
                            running = true,
                            updatedAt = now()
                        )
                    } else {
                        it
                    }
                }
            )
        }

        val content = JSONArray()
        images.forEach { image ->
            content.put(
                JSONObject()
                    .put("type", "image")
                    .put("mediaType", image.mediaType)
                    .put("data", Base64.encodeToString(image.bytes, Base64.NO_WRAP))
                    .put("name", image.name)
            )
        }
        if (outgoing.isNotEmpty()) {
            content.put(JSONObject().put("type", "text").put("text", outgoing))
        }

        scope.launch {
            try {
                val result = client.callObject(
                    "session.prompt",
                    JSONObject()
                        .put("sessionId", sessionId)
                        .put("mode", if (mode == "steer") "steer" else "queue")
                        .put("rpcId", rpcId)
                        .put("content", content),
                    timeoutMs = 90_000
                )
                val command = result.optJSONObject("command")
                // The prompt is accepted; the durable user/message echo takes over the local
                // bubble. Keep the pending flags for nothing else, so clear them here.
                eventMutex.withLock {
                    _state.update { current ->
                        current.copy(
                            items = current.items.map {
                                if (it.key == key && it is UserItem) it.copy(pending = false) else it
                            }
                        )
                    }
                    if (command != null || trimmed.startsWith("/")) {
                        val commandText = command?.optString("text").orEmpty()
                        if (commandText.isNotBlank()) {
                            addNotice(commandText, isError = false)
                        }
                    }
                }
            } catch (t: Throwable) {
                val timedOut = t is TimeoutCancellationException
                eventMutex.withLock {
                    _state.update { current ->
                        current.copy(
                            items = current.items.map {
                                // A timed-out prompt may still be admitted by dsh; keep its
                                // bubble pending until the resync below decides.
                                if (it.key == key && it is UserItem && !timedOut) {
                                    it.copy(pending = false)
                                } else {
                                    it
                                }
                            },
                            running = if (timedOut) current.running else false
                        )
                    }
                }
                if ((t as? AgentBridgeException)?.code == "auth") {
                    recoverExpiredSession()
                } else if (timedOut) {
                    eventMutex.withLock {
                        addNotice("发送回执超时，正在确认这一条是否已送达…", isError = false)
                    }
                    scope.launch {
                        delay(2_500)
                        refreshSessions(forceReloadActive = false)
                        delay(2_500)
                        val current = _state.value
                        val delivered = current.items.any {
                            it is UserItem && it.rpcId == rpcId && !it.pending
                        }
                        val stillRunning = current.sessions
                            .firstOrNull { it.id == sessionId }?.running == true
                        if (!delivered && !stillRunning) {
                            eventMutex.withLock {
                                _state.update { state ->
                                    state.copy(
                                        items = state.items.map {
                                            if (it.key == key && it is UserItem) {
                                                it.copy(pending = false)
                                            } else {
                                                it
                                            }
                                        },
                                        running = false
                                    )
                                }
                                addNotice("这条消息可能没有送达，请重新发送。", isError = true)
                            }
                        }
                    }
                } else {
                    eventMutex.withLock {
                        addNotice("发送失败：${readableError(t)}", isError = true)
                    }
                }
            }
        }
    }

    /**
     * Continues a child conversation. Only a `continuable` child accepts human input; a
     * one-shot child reports back through the parent tool result instead, so the composer
     * stays read-only for it (the UI mirrors this, the engine enforces it).
     */
    private fun promptSubagent(
        view: AgentSubagent,
        text: String,
        images: List<AgentDraftImage>,
        files: List<AgentDraftFile>
    ) {
        if (!view.continuable) {
            notifyState("一次性子 Agent 不接受后续消息")
            return
        }
        val state = _state.value
        val client = bridge
        if (state.connection != AgentConnection.Ready || client == null ||
            state.subagentView?.id != view.id
        ) {
            notifyState("Agent 尚未连接")
            return
        }

        val outgoing = attachmentText(text, files)
        val pending = UserItem(
            key = "u:sub:${UUID.randomUUID()}",
            text = text,
            pending = true,
            images = images.map {
                AgentImage(
                    attachmentId = "local:${it.id}",
                    mediaType = it.mediaType,
                    width = it.width,
                    height = it.height,
                    bytes = it.bytes.size.toLong(),
                    name = it.name,
                    localData = it.dataUrl
                )
            },
            files = files.map {
                AgentFile(
                    name = it.name,
                    mimeType = it.mimeType,
                    size = it.size,
                    guestPath = it.guestPath,
                    hostPath = it.hostPath
                )
            }
        )
        _state.update { current ->
            current.copy(items = current.items + pending, running = true)
        }

        val content = JSONArray()
        images.forEach { image ->
            content.put(
                JSONObject()
                    .put("type", "image")
                    .put("mediaType", image.mediaType)
                    .put("data", Base64.encodeToString(image.bytes, Base64.NO_WRAP))
                    .put("name", image.name)
            )
        }
        if (outgoing.isNotEmpty()) {
            content.put(JSONObject().put("type", "text").put("text", outgoing))
        }

        scope.launch {
            try {
                val result = client.callObject(
                    "subagent.prompt",
                    JSONObject()
                        .put("parentSessionId", view.parentId)
                        .put("childSessionId", view.id)
                        .put("mode", view.mode)
                        .put("content", content),
                    timeoutMs = 90_000
                )
                val messageId = result.optString("messageId").takeIf { it.isNotBlank() }
                eventMutex.withLock {
                    _state.update { current ->
                        current.copy(
                            items = current.items.map {
                                if (it.key == pending.key && it is UserItem) {
                                    it.copy(pending = false)
                                } else {
                                    it
                                }
                            }
                        )
                    }
                    if (messageId == null) {
                        addNotice("子 Agent 没有确认这条消息", isError = true)
                    }
                }
                pollSubagent(view)
            } catch (t: Throwable) {
                eventMutex.withLock {
                    _state.update { current ->
                        current.copy(
                            items = current.items.map {
                                if (it.key == pending.key && it is UserItem) {
                                    it.copy(pending = false)
                                } else {
                                    it
                                }
                            },
                            running = false
                        )
                    }
                    addNotice("发送给子 Agent 失败：${subagentPromptError(t)}", isError = true)
                }
            }
        }
    }

    private fun slashName(line: String): String? {
        if (!line.startsWith("/")) return null
        val name = line.drop(1).takeWhile { !it.isWhitespace() }
        return name.takeIf { it.isNotBlank() }
    }

    /** Edits, steers, or removes one pending inbox message. */
    fun updateQueue(itemId: String, action: String, text: String? = null) {
        if (_state.value.subagentView != null) return
        val sessionId = _state.value.activeSessionId ?: return
        val client = bridge ?: return
        if (itemId.isBlank()) return
        val existing = _state.value.queue.firstOrNull { it.id == itemId }
        if (action == "edit" && text.isNullOrBlank() &&
            existing?.images.isNullOrEmpty() && existing?.files.isNullOrEmpty()
        ) return
        scope.launch {
            val actionJson = JSONObject().put("kind", action)
            if (action == "edit") {
                // The edit action replaces the whole content array, so the queued
                // message's durable image refs have to be carried over explicitly.
                val content = JSONArray()
                existing?.images?.forEach { image ->
                    content.put(
                        JSONObject()
                            .put("type", "image")
                            .put(
                                "attachment",
                                JSONObject()
                                    .put("attachmentId", image.attachmentId)
                                    .put("mediaType", image.mediaType)
                                    .put("bytes", image.bytes)
                                    .put("width", image.width)
                                    .put("height", image.height)
                                    .apply { image.name?.let { put("name", it) } }
                            )
                    )
                }
                val trimmedText = text?.trim().orEmpty()
                val outgoing = attachmentTextForFiles(trimmedText, existing?.files.orEmpty())
                if (outgoing.isNotEmpty()) {
                    content.put(JSONObject().put("type", "text").put("text", outgoing))
                }
                actionJson.put("content", content)
            }
            try {
                client.callObject(
                    "session.updateQueue",
                    JSONObject()
                        .put("sessionId", sessionId)
                        .put("itemId", itemId)
                        .put("action", actionJson),
                    20_000
                )
            } catch (t: Throwable) {
                eventMutex.withLock {
                    addNotice("更新排队消息失败：${readableError(t)}", isError = true)
                }
            }
        }
    }

    /**
     * Fetches one durable image once and caches it as a data URL. The transcript calls
     * this as rows reach composition; an in-flight duplicate is dropped.
     */
    fun loadImage(sessionId: String, image: AgentImage) {
        if (sessionId.isBlank() || image.attachmentId.isBlank()) return
        if (image.localData != null) return
        val cacheKey = "$sessionId:${image.attachmentId}"
        if (_state.value.imageData.containsKey(cacheKey)) return
        val client = bridge ?: return
        if (!loadingImages.add(cacheKey)) return
        scope.launch {
            try {
                val result = client.callObject(
                    "session.attachment",
                    JSONObject()
                        .put("sessionId", sessionId)
                        .put("attachmentId", image.attachmentId),
                    30_000
                )
                val data = result.optString("data")
                if (data.isBlank()) return@launch
                val mediaType = result.optJSONObject("attachment")
                    ?.optString("mediaType")
                    ?.takeIf { it.isNotBlank() }
                    ?: image.mediaType
                val dataUrl = "data:$mediaType;base64,$data"
                _state.update { current ->
                    val next = current.imageData + (cacheKey to dataUrl)
                    // Bound the cache: a long session should not pin every image forever.
                    val bounded = if (next.size > 24) {
                        next.entries.drop(next.size - 24).associate { it.key to it.value }
                    } else {
                        next
                    }
                    current.copy(imageData = bounded)
                }
            } catch (t: Throwable) {
                appendLog("读取图片失败：${readableError(t)}")
            } finally {
                loadingImages.remove(cacheKey)
            }
        }
    }

    fun cancel() {
        val sessionId = _state.value.activeSessionId ?: return
        val client = bridge ?: return
        val view = _state.value.subagentView
        if (_state.value.turnStopping) return
        if (view != null && !view.continuable) {
            notifyState("一次性子 Agent 无法中断")
            return
        }
        _state.update { it.copy(turnStopping = true) }
        scope.launch {
            try {
                if (view != null) {
                    // Subagent interrupts are fire-and-return: `accepted` is admitted, not
                    // quiescence, so the child may stay visibly running for a moment.
                    client.call(
                        "subagent.interrupt",
                        JSONObject()
                            .put("parentSessionId", view.parentId)
                            .put("childSessionId", view.id)
                            .put("mode", view.mode),
                        timeoutMs = 30_000
                    )
                    pollSubagent(view)
                } else {
                    client.call(
                        "session.cancel",
                        JSONObject().put("sessionId", sessionId),
                        timeoutMs = 30_000
                    )
                }
                verifyTurnStopped(sessionId, view)
            } catch (t: Throwable) {
                val dshCode = (t as? AgentBridgeException)
                    ?.details?.optString("dshCode").orEmpty()
                eventMutex.withLock {
                    when (dshCode) {
                        // dsh already settled or detached this turn; the local projection is
                        // the stale side, so adopt dsh's truth instead of leaving a spinner.
                        "session/not-found", "session/not-attached" -> {
                            _state.update { state ->
                                state.copy(
                                    running = false,
                                    turnStopping = false,
                                    sessions = state.sessions.map {
                                        if (it.id == sessionId) it.copy(running = false) else it
                                    }
                                )
                            }
                            addNotice("这一轮已经结束", isError = false)
                        }
                        else -> {
                            _state.update { it.copy(turnStopping = false) }
                            addNotice(
                                "停止失败：${readableError(t)}。可以在设置中重启 Agent。",
                                isError = true
                            )
                        }
                    }
                }
                if (view == null) {
                    // The request may have raced a dsh restart; reconcile what dsh reports.
                    scope.launch { refreshSessions(forceReloadActive = false) }
                }
            }
        }
    }

    /**
     * dsh acknowledges `session/cancel` before the turn actually unwinds. If the turn does
     * not settle shortly after, resync from dsh and give the stop button back so a second
     * tap is possible; otherwise the composer would sit in "stopping" with no way out.
     */
    private fun verifyTurnStopped(sessionId: String, view: AgentSubagent?) {
        cancelVerifyJob?.cancel()
        cancelVerifyJob = scope.launch {
            delay(8_000)
            if (!_state.value.turnStopping) return@launch
            if (view == null) {
                refreshSessions(forceReloadActive = false)
                delay(1_500)
            } else {
                pollSubagent(view)
                delay(1_500)
            }
            if (!_state.value.turnStopping) return@launch
            if (_state.value.running) {
                _state.update { it.copy(turnStopping = false) }
                eventMutex.withLock {
                    addNotice(
                        "已请求停止，但 dsh 仍显示本轮在运行。可以再点一次停止，" +
                            "或到设置中重启 Agent。",
                        isError = true
                    )
                }
            } else {
                _state.update { it.copy(turnStopping = false) }
            }
        }
    }

    fun respondApproval(approval: AgentApproval, allow: Boolean) {
        val client = bridge ?: return
        _state.update { state ->
            state.copy(approvals = state.approvals.filterNot { it.approvalId == approval.approvalId })
        }
        scope.launch {
            try {
                val value = JSONObject()
                    .put("sessionId", approval.sessionId)
                    .put("approvalId", approval.approvalId)
                    .put("outcome", if (allow) "allowed-once" else "rejected")
                client.callObject(
                    "respond",
                    JSONObject().put("rpcId", approval.rpcId).put("value", value),
                    timeoutMs = 30_000
                )
            } catch (t: Throwable) {
                eventMutex.withLock {
                    addNotice("审批提交失败：${readableError(t)}", isError = true)
                }
                _state.update { state ->
                    if (state.approvals.any { it.approvalId == approval.approvalId }) {
                        state
                    } else {
                        state.copy(approvals = state.approvals + approval)
                    }
                }
            }
        }
    }

    fun answerQuestion(question: AgentQuestion, answers: List<AgentAnswer>) {
        val client = bridge ?: return
        _state.update { state ->
            state.copy(questions = state.questions.filterNot { it.rpcId == question.rpcId })
        }
        scope.launch {
            try {
                val answerArray = JSONArray()
                answers.forEach { answer ->
                    answerArray.put(
                        JSONObject()
                            .put("id", answer.id)
                            .put(
                                "selected",
                                JSONArray().apply { answer.selected.forEach(::put) }
                            )
                            .apply {
                                answer.custom?.takeIf { it.isNotBlank() }?.let {
                                    put("custom", it)
                                }
                            }
                    )
                }
                val value = JSONObject()
                    .put("sessionId", question.sessionId)
                    .put("answer", JSONObject().put("answers", answerArray))
                client.callObject(
                    "respond",
                    JSONObject().put("rpcId", question.rpcId).put("value", value),
                    timeoutMs = 60_000
                )
            } catch (t: Throwable) {
                eventMutex.withLock {
                    addNotice("回答提交失败：${readableError(t)}", isError = true)
                }
                _state.update { state ->
                    if (state.questions.any { it.rpcId == question.rpcId }) {
                        state
                    } else {
                        state.copy(questions = state.questions + question)
                    }
                }
            }
        }
    }

    fun selectModel(provider: String, model: String, effort: String?) {
        val sessionId = _state.value.activeSessionId ?: return
        val client = bridge ?: return
        scope.launch {
            try {
                val params = JSONObject()
                    .put("sessionId", sessionId)
                    .put("provider", provider)
                    .put("model", model)
                if (!effort.isNullOrBlank()) params.put("reasoningEffort", effort)
                client.callObject("session.selectModel", params, 45_000)
                loadModels(sessionId)
            } catch (t: Throwable) {
                eventMutex.withLock {
                    addNotice("切换模型失败：${readableError(t)}", isError = true)
                }
            }
        }
    }

    fun loadOlder() {
        val state = _state.value
        val sessionId = state.activeSessionId ?: return
        val before = state.oldestSeq ?: return
        if (!state.hasMoreHistory || historyLoadingFor != null) return
        historyLoadingFor = sessionId
        val token = loadToken
        scope.launch { loadHistory(sessionId, before, token) }
    }

    fun archiveSession(id: String) {
        val client = bridge ?: return
        scope.launch {
            try {
                client.callObject(
                    "workspace.archiveSession",
                    JSONObject().put("sessionId", id),
                    30_000
                )
                // Archive is a Workspace-navigation fact: reflect it before the session
                // list is re-read so the archived chat cannot be merged back in. The row
                // itself stays in `sessions` so the drawer can offer "恢复".
                _state.update { state -> state.copy(archivedSessionIds = state.archivedSessionIds + id) }
                handleSessionRemoved(id, keepRow = true)
                refreshWorkspacesNow(client)
                refreshSessions(forceReloadActive = false)
            } catch (t: Throwable) {
                eventMutex.withLock {
                    addNotice("归档失败：${readableError(t)}", isError = true)
                }
            }
        }
    }

    fun unarchiveSession(id: String) {
        val client = bridge ?: return
        scope.launch {
            try {
                client.callObject(
                    "workspace.unarchiveSession",
                    JSONObject().put("sessionId", id),
                    30_000
                )
                _state.update { state -> state.copy(archivedSessionIds = state.archivedSessionIds - id) }
                refreshWorkspacesNow(client)
                refreshSessions(forceReloadActive = false)
            } catch (t: Throwable) {
                eventMutex.withLock {
                    addNotice("恢复对话失败：${readableError(t)}", isError = true)
                }
            }
        }
    }

    fun setSessionPinned(id: String, pinned: Boolean) {
        val client = bridge ?: return
        scope.launch {
            val method = if (pinned) "workspace.pinSession" else "workspace.unpinSession"
            try {
                val result = client.callObject(
                    method,
                    JSONObject().put("sessionId", id),
                    30_000
                )
                val ids = result.optJSONArray("pinnedSessionIds").stringList()
                if (ids.isNotEmpty() || !pinned) {
                    _state.update { state -> state.copy(pinnedSessionIds = ids) }
                } else {
                    // Some dsh builds omit the resulting set; apply the intent locally.
                    _state.update { state ->
                        state.copy(
                            pinnedSessionIds = if (pinned) listOf(id) + state.pinnedSessionIds
                            else state.pinnedSessionIds - id
                        )
                    }
                }
            } catch (t: Throwable) {
                eventMutex.withLock {
                    addNotice(
                        if (pinned) "置顶失败：${readableError(t)}" else "取消置顶失败：${readableError(t)}",
                        isError = true
                    )
                }
            }
        }
    }

    /**
     * Reads dsh's complete Workspace projection. The drawer's project sections and its
     * archived set both come from this one snapshot.
     */
    fun refreshWorkspaces() {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) return
        scope.launch { refreshWorkspacesNow(client) }
    }

    private suspend fun refreshWorkspacesNow(client: BridgeClient) {
        val result = try {
            client.callObject("workspace.list", timeoutMs = 25_000)
        } catch (t: Throwable) {
            appendLog("读取工作区失败：${readableError(t)}")
            return
        }
        val workspaces = mutableListOf<AgentWorkspace>()
        result.optJSONArray("workspaces")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                workspaces += AgentWorkspace(
                    id = id,
                    path = item.optString("path"),
                    title = item.optString("title"),
                    sessionIds = item.optJSONArray("sessionIds").stringList()
                )
            }
        }
        _state.update {
            it.copy(
                workspaces = workspaces,
                archivedSessionIds = result.optJSONArray("archivedSessionIds").stringList().toSet(),
                pinnedSessionIds = result.optJSONArray("pinnedSessionIds").stringList()
            )
        }
    }

    /**
     * Registers a Kali directory as a Workspace. The bridge creates the directory when it
     * is missing, so a path the user typed can become the workspace root directly.
     */
    fun createWorkspace(path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) {
            notifyState("请填写 Kali 中的目录")
            return
        }
        if (!trimmed.startsWith("/")) {
            notifyState("工作区需要 Kali 中的绝对路径（以 / 开头）")
            return
        }
        val client = bridge ?: return
        scope.launch {
            val result = try {
                client.callObject(
                    "workspace.create",
                    JSONObject().put("path", trimmed),
                    30_000
                )
            } catch (t: Throwable) {
                notifyState("创建工作区失败：${readableError(t)}")
                return@launch
            }
            refreshWorkspacesNow(client)
            addNotice(
                if (result.optBoolean("directoryCreated")) "已创建目录并添加工作区 $trimmed"
                else "已添加工作区 $trimmed",
                isError = false
            )
        }
    }

    /** Renames one Workspace in dsh's registry; dsh answers with the row it stored. */
    fun renameWorkspace(id: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            notifyState("工作区名称不能为空")
            return
        }
        val client = bridge ?: return
        scope.launch {
            val result = try {
                client.callObject(
                    "workspace.rename",
                    JSONObject().put("workspaceId", id).put("title", trimmed),
                    30_000
                )
            } catch (t: Throwable) {
                notifyState(workspaceFailure(t, "重命名工作区失败"))
                return@launch
            }
            val row = result.optJSONObject("workspace")
            if (row != null) {
                val renamed = AgentWorkspace(
                    id = row.optString("id").ifBlank { id },
                    path = row.optString("path"),
                    title = row.optString("title"),
                    sessionIds = row.optJSONArray("sessionIds").stringList()
                )
                _state.update { state ->
                    state.copy(
                        workspaces = state.workspaces.map { if (it.id == id) renamed else it }
                    )
                }
            }
            refreshWorkspacesNow(client)
            addNotice("工作区已重命名为「$trimmed」", isError = false)
        }
    }

    /**
     * Removes one Workspace registration. dsh keeps the directory and every Session; the
     * chats simply return to the drawer's ungrouped shelf.
     */
    fun deleteWorkspace(id: String) {
        val client = bridge ?: return
        val title = _state.value.workspaces.firstOrNull { it.id == id }?.displayTitle ?: "工作区"
        scope.launch {
            try {
                client.callObject(
                    "workspace.delete",
                    JSONObject().put("workspaceId", id),
                    30_000
                )
            } catch (t: Throwable) {
                notifyState(workspaceFailure(t, "移除工作区失败"))
                return@launch
            }
            refreshWorkspacesNow(client)
            addNotice("已移除工作区「$title」，目录与对话都保留", isError = false)
        }
    }

    /**
     * Moves one Workspace before [beforeId] in dsh's durable order; null appends it.
     * The returned id order is authoritative, so the drawer cannot drift from the host.
     */
    fun moveWorkspace(id: String, beforeId: String?) {
        val client = bridge ?: return
        scope.launch {
            val params = JSONObject().put("workspaceId", id)
            if (beforeId != null) params.put("beforeWorkspaceId", beforeId)
            val result = try {
                client.callObject("workspace.reorder", params, 30_000)
            } catch (t: Throwable) {
                notifyState(workspaceFailure(t, "调整工作区顺序失败"))
                return@launch
            }
            val order = result.optJSONArray("workspaceIds").stringList()
            if (order.isNotEmpty()) {
                _state.update { state ->
                    val byId = state.workspaces.associateBy { it.id }
                    val ordered = order.mapNotNull { byId[it] }
                    state.copy(
                        workspaces = ordered + state.workspaces.filterNot { it.id in order }
                    )
                }
            }
            refreshWorkspacesNow(client)
        }
    }

    /** dsh's stable code for a Workspace failure, with the raw text as the fallback. */
    private fun workspaceFailure(error: Throwable, action: String): String {
        val code = (error as? AgentBridgeException)?.details?.optString("dshCode").orEmpty()
        return when (code) {
            "workspace/name-conflict" -> "已有同名工作区，请换一个名称"
            "workspace/invalid-path" -> "这个目录不能作为工作区"
            else -> "$action：${readableError(error)}"
        }
    }

    /** Reads the plugin bundles the user installed into the dsh profile. */
    fun refreshPlugins() {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) return
        scope.launch { loadPluginsNow(client) }
    }

    private fun loadPlugins() {
        val client = bridge ?: return
        scope.launch { loadPluginsNow(client) }
    }

    /**
     * Switches one installed bundle in the dsh profile. dsh owns the enablement state and
     * applies it globally; the reloaded inventory reflects whatever actually took effect.
     */
    suspend fun setPluginEnabled(name: String, enabled: Boolean): AgentPluginChange {
        val client = bridge ?: throw IllegalStateException("Agent 尚未连接")
        if (_state.value.connection != AgentConnection.Ready) {
            throw IllegalStateException("Agent 尚未连接")
        }
        val result = client.callObject(
            "plugin.setEnabled",
            JSONObject().put("name", name).put("enabled", enabled),
            45_000
        )
        loadPluginsNow(client)
        return AgentPluginChange(
            changed = result.optBoolean("changed", false),
            restartRequired = result.optBoolean("restartRequired", false),
            restartReason = result.optString("restartReason").takeIf { it.isNotBlank() },
            enabled = result.optBoolean("enabled", enabled),
            error = result.optText("error")
        )
    }

    private suspend fun loadPluginsNow(client: BridgeClient) {
        val result = try {
            client.callObject("plugin.list", timeoutMs = 25_000)
        } catch (t: Throwable) {
            // A dsh without the plugin manager has no installed-bundle surface.
            appendLog("读取插件失败：${readableError(t)}")
            return
        }
        val plugins = mutableListOf<AgentPlugin>()
        result.optJSONArray("plugins")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name")
                if (name.isBlank()) continue
                plugins += AgentPlugin(
                    name = name,
                    title = item.optText("title") ?: "",
                    description = item.optText("description") ?: "",
                    version = item.optText("version"),
                    enabled = item.optBoolean("enabled", true),
                    removable = item.optBoolean("removable", true),
                    official = item.optBoolean("official", false)
                )
            }
        }
        val offers = mutableListOf<AgentPluginOffer>()
        result.optJSONArray("offers")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val spec = item.optString("spec")
                if (spec.isBlank()) continue
                offers += AgentPluginOffer(
                    name = item.optText("name") ?: "",
                    title = item.optText("title") ?: "",
                    description = item.optText("description") ?: "",
                    version = item.optText("version"),
                    spec = spec,
                    official = item.optBoolean("official", true)
                )
            }
        }
        _state.update { it.copy(plugins = plugins, pluginOffers = offers, pluginLoaded = true) }
    }

    /** Reads the npm registry the dsh plugin manager will ask first. */
    fun refreshPluginRegistry() {
        val client = bridge ?: return
        if (_state.value.connection != AgentConnection.Ready) return
        scope.launch {
            try {
                val result = client.callObject("plugin.registry.get", JSONObject(), 20_000)
                _state.update { it.copy(pluginRegistry = result.optText("registry") ?: "") }
            } catch (t: Throwable) {
                appendLog("读取 NPM 源失败：${readableError(t)}")
            }
        }
    }

    /**
     * Applies the app's stored npm source to the dsh profile after every connect. The app
     * owns this setting because the same value installs dsh before any bridge exists; a
     * blank stored value means the Aliyun default.
     */
    private suspend fun syncNpmRegistry() {
        val client = bridge ?: return
        val registry = SettingsStore(appContext).npmRegistry.trim()
            .ifBlank { DEFAULT_NPM_REGISTRY }
        try {
            val result = client.callObject(
                "plugin.registry.set",
                JSONObject().put("registry", registry),
                20_000
            )
            _state.update { it.copy(pluginRegistry = result.optText("registry") ?: registry) }
        } catch (t: Throwable) {
            appendLog("同步 npm 源失败：${readableError(t)}")
        }
    }

    /**
     * Sets the primary npm registry for plugin installs. dsh's manager runs pnpm in the
     * profile directory, so this is one `registry=` line in that profile's `.npmrc`. An
     * empty value removes the line; the next bridge start restores the Aliyun default.
     */
    fun setPluginRegistry(registry: String) {
        val value = registry.trim()
        val client = bridge
        if (client == null || _state.value.connection != AgentConnection.Ready) {
            notifyState("Agent 尚未连接")
            return
        }
        if (value.isNotEmpty() && (!value.startsWith("http://") && !value.startsWith("https://"))) {
            notifyState("NPM 源必须是 http(s) 地址")
            return
        }
        scope.launch {
            try {
                val result = client.callObject(
                    "plugin.registry.set",
                    JSONObject().put("registry", value),
                    20_000
                )
                val applied = result.optText("registry") ?: ""
                _state.update { it.copy(pluginRegistry = applied) }
                appendLog(
                    if (applied.isBlank()) "NPM 源已清除，下次连接恢复默认阿里源"
                    else "NPM 源已设为 $applied"
                )
            } catch (t: Throwable) {
                addNotice("设置 NPM 源失败：${readableError(t)}", isError = true)
            }
        }
    }

    /**
     * Asks dsh what a package spec names without installing anything. dsh checks the
     * registry (or the path/git target) first, so obvious mistakes are caught here.
     */
    suspend fun inspectPlugin(spec: String): AgentPluginInspection {
        val trimmed = spec.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("请输入插件名称")
        val client = bridge ?: throw IllegalStateException("Agent 尚未连接")
        if (_state.value.connection != AgentConnection.Ready) {
            throw IllegalStateException("Agent 尚未连接")
        }
        val result = client.callObject(
            "plugin.inspect",
            JSONObject().put("spec", trimmed),
            90_000
        )
        val accepted = result.optString("status") == "accepted"
        return AgentPluginInspection(
            accepted = accepted,
            name = result.optText("name"),
            version = result.optText("version"),
            description = result.optText("description"),
            problem = result.optText("problem"),
            reason = result.optText("reason")
        )
    }

    /**
     * Installs one package into the dsh profile and enables it. pnpm can take minutes on
     * this device, so the call has its own generous budget instead of the bridge default.
     */
    suspend fun installPlugin(spec: String): AgentPluginInstall {
        val trimmed = spec.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("请输入插件名称")
        val client = bridge ?: throw IllegalStateException("Agent 尚未连接")
        if (_state.value.connection != AgentConnection.Ready) {
            throw IllegalStateException("Agent 尚未连接")
        }
        val result = try {
            client.callObject(
                "plugin.install",
                JSONObject().put("spec", trimmed).put("enabled", true),
                16 * 60_000L
            )
        } catch (t: Throwable) {
            // Even a failed call may have changed the inventory; refresh before reporting.
            loadPluginsNow(client)
            throw t
        }
        loadPluginsNow(client)
        val install = AgentPluginInstall(
            changed = result.optBoolean("changed", false),
            restartRequired = result.optBoolean("restartRequired", false),
            bundle = result.optText("bundle"),
            version = result.optText("version"),
            error = result.optText("error")
        )
        // The manager answers a refusal as a normal ChangeResult, so keep its own words
        // in the app log: "plugin.install ok" alone cannot tell a refusal from a success.
        appendLog(
            "插件安装结果：changed=${install.changed} application=${result.optString("application")} " +
                "stage=${result.optString("stage")} target=${result.optString("target")} " +
                "error=${install.error ?: "none"}"
        )
        return install
    }

    /**
     * Replaces one recorded profile dependency with another exact version. dsh's plugin
     * manager refuses a package it already has installed, so this goes through dsh's own
     * CLI (`dsh plugin add`), which forwards to pnpm and rewrites the profile manifest.
     * The running dsh keeps the old code until it restarts.
     */
    suspend fun updatePlugin(name: String, version: String): AgentPluginInstall {
        val pluginName = name.trim()
        val targetVersion = version.trim()
        if (pluginName.isEmpty() || targetVersion.isEmpty()) {
            throw IllegalArgumentException("缺少插件名称或版本")
        }
        val client = bridge ?: throw IllegalStateException("Agent 尚未连接")
        if (_state.value.connection != AgentConnection.Ready) {
            throw IllegalStateException("Agent 尚未连接")
        }
        val result = try {
            client.callObject(
                "plugin.update",
                JSONObject().put("name", pluginName).put("version", targetVersion),
                20 * 60_000L
            )
        } catch (t: Throwable) {
            loadPluginsNow(client)
            throw t
        }
        loadPluginsNow(client)
        val update = AgentPluginInstall(
            changed = result.optBoolean("changed", false),
            restartRequired = result.optBoolean("restartRequired", false),
            bundle = result.optText("bundle"),
            version = result.optText("version"),
            error = result.optText("error")
        )
        appendLog(
            "插件更新结果：$pluginName $targetVersion " +
                "changed=${update.changed} version=${update.version ?: "unknown"}"
        )
        return update
    }

    /**
     * Resolves the live dsh web application URL, launch token included. The token is
     * ephemeral and only useful on this device's loopback, so it is never logged.
     */
    suspend fun webUrl(): String? {
        val client = bridge ?: return null
        if (_state.value.connection != AgentConnection.Ready) return null
        return try {
            client.callObject("web.url", timeoutMs = 15_000)
                .optString("url")
                .takeIf { value -> value.isNotBlank() }
        } catch (t: Throwable) {
            appendLog("读取 Harness 地址失败：${readableError(t)}")
            null
        }
    }

    /**
     * Reads an optional string from a bridge reply. org.json turns a JSON null into the
     * literal "null", which once made a successful install report "未安装：null".
     */
    private fun JSONObject.optText(name: String): String? =
        optString(name).takeIf { it.isNotBlank() && it != "null" && it != "undefined" }

    private fun JSONArray?.stringList(): List<String> {
        if (this == null) return emptyList()
        val values = mutableListOf<String>()
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) values += value
        }
        return values
    }

    fun renameSession(id: String, title: String) {
        val client = bridge ?: return
        scope.launch {
            try {
                client.callObject(
                    "session.rename",
                    JSONObject().put("sessionId", id).put("title", title),
                    30_000
                )
                updateSessionTitle(id, title)
            } catch (t: Throwable) {
                eventMutex.withLock {
                    addNotice("重命名失败：${readableError(t)}", isError = true)
                }
            }
        }
    }

    /** Forks the session at its latest completed turn and opens the copy. */
    fun forkSession(id: String) {
        forkSessionAt(id, null)
    }

    /**
     * Forks at an exact source event seq (`atSeq` is inclusive). dsh-web branches at the
     * last message of a completed turn; the app only offers that point, so the copy always
     * ends on a settled turn boundary.
     */
    fun forkSessionAt(id: String, atSeq: Long?) {
        val client = bridge ?: return
        scope.launch {
            val params = JSONObject().put("sessionId", id)
            if (atSeq != null) params.put("atSeq", atSeq)
            val result = try {
                client.callObject("session.fork", params, 30_000)
            } catch (t: Throwable) {
                eventMutex.withLock {
                    addNotice("分叉失败：${readableError(t)}", isError = true)
                }
                return@launch
            }
            val newId = result.optString("sessionId").takeIf { it.isNotBlank() }
            if (newId == null) {
                eventMutex.withLock {
                    addNotice("分叉失败：dsh 没有返回新会话", isError = true)
                }
                return@launch
            }
            refreshSessions(forceReloadActive = false)
            openSession(newId)
        }
    }

    fun clearTransientError() {
        _state.update { it.copy(error = null) }
    }

    /** Reads the like/dislike records of a conversation; failures stay silent and retryable. */
    private suspend fun loadMessageFeedback(sessionId: String) {
        val client = bridge ?: return
        val result = runCatching {
            client.callObject(
                "feedback.list",
                JSONObject().put("sessionId", sessionId),
                20_000
            )
        }.getOrNull() ?: return
        // The service answers with its own envelope inside the RPC value.
        if (result.optBoolean("ok") != true) return
        val value = result.optJSONObject("value") ?: return
        val array = value.optJSONArray("items") ?: return
        val records = LinkedHashMap<String, AgentMessageFeedback>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val messageId = item.optString("messageId")
            if (messageId.isBlank()) continue
            records[messageId] = AgentMessageFeedback(
                messageId = messageId,
                rating = item.optString("rating").ifBlank { "positive" },
                note = item.optString("note").takeIf { it.isNotBlank() },
                category = item.optString("category").takeIf { it.isNotBlank() },
                version = item.optString("version").takeIf { it.isNotBlank() }
            )
        }
        _state.update { state ->
            if (state.activeSessionId == sessionId) state.copy(messageFeedback = records) else state
        }
    }

    /**
     * Records a like/dislike for a finalized assistant message. The row updates at once
     * and rolls back on refusal, so the transcript keeps showing what dsh actually stored.
     */
    fun rateMessage(
        messageId: String,
        rating: String,
        category: String? = null,
        note: String? = null
    ) {
        val client = bridge ?: return
        val state = _state.value
        val sessionId = state.activeSessionId ?: return
        if (messageId.isBlank() || sessionId.isBlank()) return
        if (rating != "positive" && rating != "negative") return
        val previous = state.messageFeedback[messageId]
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        val cleanCategory = category?.takeIf { it in FEEDBACK_CATEGORIES }
        val optimistic = AgentMessageFeedback(
            messageId = messageId,
            rating = rating,
            note = cleanNote,
            category = cleanCategory,
            version = previous?.version
        )
        _state.update {
            it.copy(messageFeedback = it.messageFeedback + (messageId to optimistic))
        }
        scope.launch {
            val params = JSONObject()
                .put("sessionId", sessionId)
                .put("messageId", messageId)
                .put("rating", rating)
                .put("ifVersion", previous?.version ?: JSONObject.NULL)
            if (cleanCategory != null) params.put("category", cleanCategory)
            if (cleanNote != null) params.put("note", cleanNote)
            var stored: JSONObject? = null
            val failure = try {
                val result = client.callObject("feedback.put", params, 20_000)
                stored = result.optJSONObject("value")
                feedbackFailure(result)
            } catch (t: Throwable) {
                readableError(t)
            }
            if (failure != null) {
                eventMutex.withLock {
                    _state.update { current ->
                        if (current.activeSessionId != sessionId) return@update current
                        val map = current.messageFeedback.toMutableMap()
                        if (previous == null) map.remove(messageId) else map[messageId] = previous
                        current.copy(messageFeedback = map)
                    }
                    addNotice("反馈未保存：$failure", isError = true)
                }
                // A stale version can never succeed on retry; pull dsh's current records
                // so the next tap sends the right token.
                if (failure == STALE_FEEDBACK) loadMessageFeedback(sessionId)
                return@launch
            }
            // dsh hands back the stored record; its version is the token the next edit
            // must present, so keep it instead of the null we sent on creation.
            if (stored != null) {
                val version = stored.optString("version").takeIf { it.isNotBlank() }
                val storedCategory = stored.optString("category").takeIf { it.isNotBlank() }
                val storedNote = stored.optString("note").takeIf { it.isNotBlank() }
                _state.update { current ->
                    if (current.activeSessionId != sessionId) return@update current
                    val record = current.messageFeedback[messageId] ?: return@update current
                    current.copy(
                        messageFeedback = current.messageFeedback + (messageId to record.copy(
                            category = storedCategory,
                            note = storedNote,
                            version = version ?: record.version
                        ))
                    )
                }
            }
        }
    }

    /** Removes the feedback attached to one message, if any. */
    fun clearMessageFeedback(messageId: String) {
        val client = bridge ?: return
        val state = _state.value
        val sessionId = state.activeSessionId ?: return
        val previous = state.messageFeedback[messageId] ?: return
        _state.update {
            it.copy(messageFeedback = it.messageFeedback - messageId)
        }
        scope.launch {
            val params = JSONObject()
                .put("sessionId", sessionId)
                .put("messageId", messageId)
                .put("ifVersion", previous.version ?: JSONObject.NULL)
            val failure = try {
                val result = client.callObject("feedback.delete", params, 20_000)
                feedbackFailure(result)
            } catch (t: Throwable) {
                readableError(t)
            }
            if (failure != null) {
                eventMutex.withLock {
                    _state.update { current ->
                        if (current.activeSessionId != sessionId) return@update current
                        current.copy(
                            messageFeedback = current.messageFeedback + (messageId to previous)
                        )
                    }
                    addNotice("反馈未删除：$failure", isError = true)
                }
            }
        }
    }

    /** Null when the service envelope reports success; otherwise a readable reason. */
    private fun feedbackFailure(result: JSONObject): String? {
        if (result.optBoolean("ok")) return null
        val error = result.optJSONObject("error")
        val code = error?.optString("code").orEmpty()
        return when (code) {
            "session-not-found" -> "会话不存在"
            "target-not-found" -> "这条消息不再支持反馈"
            "version-conflict" -> STALE_FEEDBACK
            "note-blank" -> "说明不能为空"
            "note-too-large" -> "说明过长"
            else -> error?.optString("message")?.takeIf { it.isNotBlank() } ?: "dsh 拒绝了该操作"
        }
    }

    /**
     * Re-reads every projection for the active session. The stats panel calls this after
     * a reconnect or when the reader wants to confirm the live numbers.
     */
    fun refreshStats() {
        val client = bridge ?: return
        val sessionId = _state.value.activeSessionId ?: return
        scope.launch {
            val failure = try {
                val value = client.callObject(
                    "session.projections",
                    JSONObject().put("sessionId", sessionId),
                    20_000
                )
                eventMutex.withLock {
                    if (_state.value.activeSessionId == sessionId) {
                        applyProjectionsLocked(sessionId, value)
                    }
                }
                null
            } catch (t: Throwable) {
                readableError(t)
            }
            if (failure != null) {
                eventMutex.withLock { addNotice("统计刷新失败：$failure", isError = true) }
            }
        }
    }

    private fun applyProjectionsLocked(sessionId: String, block: JSONObject) {
        // History and follow snapshots wrap values as `{ values: {...}, asOfSeq }`; accept a
        // flat block too so a future wire shape cannot silently drop the permission mode.
        val values = block.optJSONObject("values") ?: block
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            applyProjectionLocked(sessionId, key, values.opt(key))
        }
    }

    private fun applyProjection(sessionId: String, key: String, value: Any?) {
        _state.update { state ->
            applyProjectionState(sessionId, key, value, state)
        }
    }

    private fun applyProjectionLocked(sessionId: String, key: String, value: Any?) {
        _state.update { state ->
            applyProjectionState(sessionId, key, value, state)
        }
    }

    private fun applyProjectionState(
        sessionId: String,
        key: String,
        value: Any?,
        state: AgentState
    ): AgentState {
        val active = sessionId == state.activeSessionId
        return when (key) {
            "title" -> {
                val title = titleText(value)
                if (title.isNullOrBlank()) {
                    state
                } else {
                    state.copy(sessions = state.sessions.map {
                        if (it.id == sessionId) it.copy(title = title, blank = false) else it
                    })
                }
            }
            "todos" -> if (active) state.copy(todos = parseTodos(value)) else state
            "plan" -> if (active) state.copy(plan = parsePlan(value)) else state
            "permissions" -> if (active) {
                state.copy(permissionMode = parsePermissionMode(value))
            } else {
                state
            }
            "tokenUsage" -> if (active) state.copy(tokenUsage = parseTokenUsage(value)) else state
            "contextPressure" -> if (active) {
                state.copy(contextPressure = parseContextPressure(value))
            } else {
                state
            }
            "contextBreakdown" -> if (active) {
                state.copy(contextBreakdown = parseContextBreakdown(value))
            } else {
                state
            }
            "goal" -> if (active) {
                // Activation is process-local and absent from the durable projection. A
                // projection update for the same revision (the echo of a mutation) keeps
                // what the goal service just told us instead of dropping it.
                val previous = state.goal
                val parsed = parseGoal(value)
                state.copy(goal = parsed?.let { next ->
                    if (previous != null && previous.id == next.id && previous.revision == next.revision) {
                        next.copy(activation = previous.activation)
                    } else {
                        next
                    }
                })
            } else {
                state
            }
            "modelSelection" -> if (active) {
                state.copy(modelSelection = parseModelSelection(value))
            } else {
                state
            }
            "subagentTiming" -> if (active) {
                state.copy(subagentTiming = parseSubagentTiming(value))
            } else {
                state
            }
            "agentTeam" -> if (active) {
                state.copy(agentTeam = parseAgentTeam(value))
            } else {
                state
            }
            "turnOutline" -> if (active) {
                state.copy(turnOutline = parseTurnOutline(value))
            } else {
                state
            }
            "sessionStats" -> if (active) {
                state.copy(sessionStats = parseSessionStats(value))
            } else {
                state
            }
            // Projections this build has no typed home for stay inspectable. A newer dsh
            // adding one must not mean the app silently drops half the session state.
            else -> if (active) retainProjection(state, key, value) else state
        }
    }

    /** Keeps unknown projections in a bounded, newest-24 map keyed by projection name. */
    private fun retainProjection(
        state: AgentState,
        key: String,
        value: Any?
    ): AgentState {
        if (key.isBlank() || value == null) return state
        val encoded = when (value) {
            is String -> value
            else -> value.toString()
        }.take(MAX_EXTRA_PROJECTION)
        if (encoded.isBlank()) return state
        if (state.extraProjections[key] == encoded) return state
        val kept = LinkedHashMap<String, String>()
        for ((name, json) in state.extraProjections) {
            if (name == key) continue
            kept[name] = json
            if (kept.size >= MAX_EXTRA_PROJECTIONS - 1) break
        }
        kept[key] = encoded
        return state.copy(extraProjections = kept)
    }

    /** Whether the most recent slash-command record was `/export`. */
    private fun lastCommandWasExport(): Boolean {
        val item = _state.value.items.lastOrNull {
            it is UserItem && it.source == "command"
        } as? UserItem ?: return false
        val command = item.text.trim().substringBefore(' ')
        return command == "/export"
    }

    /** Remembers an event kind the app does not render, so support can see the gap. */
    private fun recordUnknownEvent(type: String) {
        _state.update { state ->
            if (type in state.unknownEvents) {
                state
            } else {
                state.copy(
                    unknownEvents = (state.unknownEvents + type)
                        .takeLast(MAX_UNKNOWN_EVENTS)
                )
            }
        }
    }

    private fun parseUsage(value: JSONObject?): AgentUsage? {
        if (value == null) return null
        val usage = AgentUsage(
            inputTokens = value.optLong("inputTokens"),
            outputTokens = value.optLong("outputTokens"),
            cacheReadTokens = value.optLong("cacheReadTokens"),
            cacheWriteTokens = value.optLong("cacheWriteTokens"),
            reasoningTokens = value.optLong("reasoningTokens")
        )
        return if (usage.hasData) usage else null
    }

    private fun parseTokenUsage(value: Any?): AgentTokenUsage {
        val json = value as? JSONObject ?: return AgentTokenUsage()
        return AgentTokenUsage(
            uncachedInputTokens = json.optLong("uncachedInputTokens")
                .orIfZero { json.optLong("inputTokens") },
            outputTokens = json.optLong("outputTokens"),
            cacheReadTokens = json.optLong("cacheReadTokens"),
            cacheWriteTokens = json.optLong("cacheWriteTokens")
        )
    }

    private fun Long.orIfZero(alternative: () -> Long): Long =
        if (this != 0L) this else alternative()

    private fun parseContextPressure(value: Any?): AgentContextPressure {
        val json = value as? JSONObject ?: return AgentContextPressure()
        return AgentContextPressure(
            pressureTokens = json.longOrNull("pressureTokens"),
            projectedTokens = json.longOrNull("projectedTokens"),
            contextWindow = json.longOrNull("contextWindow")
        )
    }

    private fun parseContextBreakdown(value: Any?): AgentContextBreakdown {
        val json = value as? JSONObject ?: return AgentContextBreakdown()
        return AgentContextBreakdown(
            systemTokens = json.optLong("systemTokens"),
            toolsTokens = json.optLong("toolsTokens"),
            messageTokens = json.optLong("messageTokens")
        )
    }

    private fun parseGoal(value: Any?): AgentGoal? {
        val json = value as? JSONObject ?: return null
        val goal = json.optJSONObject("goal") ?: return null
        val blocked = goal.optJSONObject("blockedReason")
        val parsed = AgentGoal(
            id = goal.optString("id"),
            revision = goal.optLong("revision"),
            objective = goal.optString("objective"),
            phase = goal.optString("phase"),
            maxGoalRounds = goal.optInt("maxGoalRounds"),
            roundsStarted = json.optInt("roundsStarted", goal.optInt("roundsStarted")),
            blockedCode = blocked?.optString("code")?.takeIf { it.isNotBlank() },
            blockedMessage = blocked?.optString("message")?.takeIf { it.isNotBlank() }
        )
        return if (parsed.hasData) parsed else null
    }

    /**
     * Reads the flat GoalView the goal service returns (`id/revision/objective/...`),
     * as opposed to the projection's `{goal:{…}, roundsStarted}` wrapper.
     */
    private fun parseGoalView(value: JSONObject?): AgentGoal? {
        if (value == null) return null
        val blocked = value.optJSONObject("blockedReason")
        val parsed = AgentGoal(
            id = value.optString("id"),
            revision = value.optLong("revision"),
            objective = value.optString("objective"),
            phase = value.optString("phase"),
            maxGoalRounds = value.optInt("maxGoalRounds"),
            roundsStarted = value.optInt("roundsStarted"),
            blockedCode = blocked?.optString("code")?.takeIf { it.isNotBlank() },
            blockedMessage = blocked?.optString("message")?.takeIf { it.isNotBlank() },
            activation = value.optString("activation").takeIf { it.isNotBlank() }
        )
        return if (parsed.hasData) parsed else null
    }

    private fun parseModelSelection(value: Any?): AgentModelSelection {
        val json = value as? JSONObject ?: return AgentModelSelection()
        val selected = json.optJSONObject("next") ?: json.optJSONObject("lastUsed") ?: return AgentModelSelection()
        return AgentModelSelection(
            provider = selected.optString("provider"),
            model = selected.optString("model"),
            reasoningEffort = selected.optString("reasoningEffort").takeIf { it.isNotBlank() }
        )
    }

    private fun parseSubagentTiming(value: Any?): AgentSubagentTiming? {
        val json = value as? JSONObject ?: return null
        val active = json.optJSONObject("active")
        return AgentSubagentTiming(
            settledMs = json.optLong("settledMs"),
            activeSince = active?.longOrNull("since"),
            activeThrough = active?.longOrNull("through"),
            lastTurnCompleted = if (json.has("lastTurnCompleted")) {
                json.optBoolean("lastTurnCompleted")
            } else {
                null
            }
        )
    }

    private fun parseAgentTeam(value: Any?): AgentTeam? {
        val json = value as? JSONObject ?: return null
        val members = mutableListOf<AgentTeamMember>()
        val memberArray = json.optJSONArray("members")
        if (memberArray != null) {
            for (index in 0 until memberArray.length()) {
                val item = memberArray.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                members += AgentTeamMember(
                    id = id,
                    name = item.optString("name").ifBlank { id },
                    role = item.optString("role").ifBlank { "teammate" },
                    phase = item.optString("phase").ifBlank { "active" },
                    error = item.optString("error").takeIf { it.isNotBlank() }
                )
            }
        }
        val tasks = mutableListOf<AgentTeamTask>()
        val taskArray = json.optJSONArray("tasks")
        if (taskArray != null) {
            for (index in 0 until taskArray.length()) {
                val item = taskArray.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                tasks += AgentTeamTask(
                    id = id,
                    revision = item.optLong("revision"),
                    subject = item.optString("subject"),
                    description = item.optString("description"),
                    status = item.optString("status").ifBlank { "pending" },
                    ownerId = item.optString("ownerId").takeIf { it.isNotBlank() }
                )
            }
        }
        val team = AgentTeam(
            members = members,
            tasks = tasks,
            failure = json.optString("failure").takeIf { it.isNotBlank() }
        )
        return if (team.hasData) team else null
    }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    /** `turnOutline` is an array of `{turn, seq, prompt, response}` entries. */
    private fun parseTurnOutline(value: Any?): List<AgentTurnOutlineEntry> {
        val array = value as? JSONArray ?: return emptyList()
        val entries = mutableListOf<AgentTurnOutlineEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val turn = item.optInt("turn", -1)
            if (turn <= 0) continue
            entries += AgentTurnOutlineEntry(
                turn = turn,
                seq = item.longOrNull("seq"),
                prompt = item.optString("prompt"),
                response = item.optString("response")
            )
        }
        return entries
    }

    private fun parseSessionStats(value: Any?): AgentSessionStats {
        val json = value as? JSONObject ?: return AgentSessionStats()
        return AgentSessionStats(
            turns = json.optInt("turns"),
            steps = json.optInt("steps"),
            llmMs = json.longOrNull("llmMs"),
            toolMs = json.longOrNull("toolMs"),
            ttftMs = json.longOrNull("ttftMs"),
            ttftSteps = json.optInt("ttftSteps"),
            decodeMs = json.longOrNull("decodeMs"),
            decodeTokens = json.longOrNull("decodeTokens")
        )
    }

    /** Keeps only the catalog rows bound to [sessionId]; other sessions stay out of view. */
    private fun parseSchedules(value: Any?, sessionId: String): List<AgentSchedule> {
        val array = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("schedules")
            else -> null
        } ?: return emptyList()
        val schedules = mutableListOf<AgentSchedule>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val schedule = parseSchedule(item, sessionId, item.optString("status", "active"))
                ?: continue
            if (schedule.sessionId == sessionId) schedules += schedule
        }
        return schedules.sortedBy { it.scheduledAt ?: Long.MAX_VALUE }
    }

    private fun parseSchedule(
        item: JSONObject?,
        sessionId: String,
        status: String
    ): AgentSchedule? {
        if (item == null) return null
        val id = item.optString("id")
        if (id.isBlank()) return null
        val weekdays = mutableListOf<Int>()
        item.optJSONArray("weekdays")?.let { array ->
            for (index in 0 until array.length()) {
                val day = array.optInt(index)
                if (day in 1..7) weekdays += day
            }
        }
        val lastDelivery = item.optJSONObject("lastDelivery")
        // A catalog row names its own Session; an update result does not, so the caller's
        // binding is the fallback.
        val boundSession = item.optString("sessionId").takeIf { it.isNotBlank() } ?: sessionId
        return AgentSchedule(
            id = id,
            sessionId = boundSession,
            kind = item.optString("kind"),
            title = item.optString("title"),
            prompt = item.optString("prompt"),
            scheduledAt = parseInstant(item.optString("scheduledAt")),
            status = status.ifBlank { "active" },
            afterSeconds = item.longOrNull("afterSeconds"),
            everySeconds = item.longOrNull("everySeconds"),
            time = item.optString("time").takeIf { it.isNotBlank() },
            timeZone = item.optString("timeZone").takeIf { it.isNotBlank() },
            weekdays = weekdays,
            expression = item.optString("expression").takeIf { it.isNotBlank() },
            lastDeliveredAt = parseInstant(lastDelivery?.optString("deliveredAt")),
            raw = item.toString()
        )
    }

    private fun parseScheduleHistory(value: JSONObject, id: String): AgentScheduleHistory {
        val records = mutableListOf<AgentScheduleDelivery>()
        value.optJSONArray("records")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                records += AgentScheduleDelivery(
                    scheduledAt = parseInstant(item.optString("scheduledAt")),
                    deliveredAt = parseInstant(item.optString("deliveredAt")),
                    prompt = item.optString("prompt").takeIf { it.isNotBlank() },
                    messageId = item.optString("messageId")
                )
            }
        }
        val retention = value.optJSONObject("retention")
        return AgentScheduleHistory(
            id = id,
            records = records,
            retentionDays = retention?.optInt("days") ?: 0,
            retentionRecords = retention?.optInt("records") ?: 0,
            earlierUnavailable = value.optBoolean("earlierRecordsUnavailable", false),
            earlierPruned = value.optBoolean("earlierRecordsPruned", false),
            nextBefore = value.optString("nextBefore").takeIf { it.isNotBlank() },
            loading = false,
            error = null
        )
    }

    /** dsh stamps schedule times as ISO instants; an unparseable stamp stays unknown. */
    private fun parseInstant(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private fun parseJobs(value: Any?): List<AgentJob> {
        val array = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("jobs")
            else -> null
        } ?: return emptyList()
        val jobs = mutableListOf<AgentJob>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parseJob(item)?.let { jobs += it }
        }
        return jobs
    }

    private fun parseJob(item: JSONObject): AgentJob? {
        val id = item.optString("id")
        if (id.isBlank()) return null
        val output = item.optJSONObject("output")
        val spill = mutableListOf<String>()
        output?.optJSONArray("spillPaths")?.let { paths ->
            for (index in 0 until paths.length()) {
                paths.optString(index).takeIf { it.isNotBlank() }?.let { spill += it }
            }
        }
        return AgentJob(
            id = id,
            kind = item.optString("kind"),
            label = item.optString("label").ifBlank { id },
            status = item.optString("status").ifBlank { "running" },
            progress = item.optString("progress").takeIf { it.isNotBlank() },
            detail = item.optString("detail").takeIf { it.isNotBlank() },
            startedAt = item.optLong("startedAt", 0L),
            finishedAt = item.longOrNull("finishedAt"),
            outputTotal = output?.optLong("total", 0L) ?: 0L,
            outputLimitBytes = item.longOrNull("outputLimitBytes"),
            spillPaths = spill
        )
    }

    private fun parseJobOutput(jobId: String, value: Any?): AgentJobOutput {
        val json = value as? JSONObject ?: return AgentJobOutput(jobId)
        val chunks = mutableListOf<AgentJobChunk>()
        json.optJSONArray("chunks")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                chunks += AgentJobChunk(
                    at = item.optLong("at", 0L),
                    text = item.optString("text"),
                    channel = item.optString("channel").ifBlank { "stdout" },
                    gapBefore = item.optBoolean("gapBefore")
                )
            }
        }
        return AgentJobOutput(
            jobId = jobId,
            job = json.optJSONObject("job")?.let { parseJob(it) },
            chunks = chunks,
            next = json.optLong("next", 0L),
            lossy = json.optBoolean("lossy"),
            loading = false
        )
    }

    private fun parseChanges(value: JSONObject, seq: Long): AgentChanges {
        val files = mutableListOf<AgentChangedFile>()
        value.optJSONArray("files")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isBlank()) continue
                files += AgentChangedFile(
                    path = path,
                    display = item.optString("display").ifBlank { path },
                    added = item.optInt("added"),
                    deleted = item.optInt("deleted"),
                    binary = item.optBoolean("binary"),
                    oversized = item.optBoolean("oversized")
                )
            }
        }
        return AgentChanges(
            seq = seq,
            turn = value.optInt("turn"),
            total = value.optInt("total", files.size),
            added = value.optInt("added"),
            deleted = value.optInt("deleted"),
            files = files
        )
    }

    private fun parseChangesDiff(value: Any?): AgentChangesDiff {
        val json = value as? JSONObject
            ?: return AgentChangesDiff(error = "差异数据格式不正确")
        val hunks = mutableListOf<AgentDiffHunk>()
        json.optJSONArray("hunks")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val lines = mutableListOf<String>()
                item.optJSONArray("lines")?.let { list ->
                    for (line in 0 until list.length()) {
                        lines += list.optString(line)
                    }
                }
                hunks += AgentDiffHunk(
                    oldStart = item.optInt("oldStart"),
                    oldLines = item.optInt("oldLines"),
                    newStart = item.optInt("newStart"),
                    newLines = item.optInt("newLines"),
                    lines = lines
                )
            }
        }
        return AgentChangesDiff(
            path = json.optString("path"),
            display = json.optString("display").ifBlank { json.optString("path") },
            kind = json.optString("kind").ifBlank { "text" },
            before = json.optBoolean("before"),
            after = json.optBoolean("after"),
            coarse = json.optBoolean("coarse"),
            hunks = hunks
        )
    }

    private fun parseDeliverableFiles(value: Any?): List<AgentDeliverable> {
        val array = value as? JSONArray ?: return emptyList()
        val files = mutableListOf<AgentDeliverable>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val path = item.optString("path")
            if (path.isBlank()) continue
            files += AgentDeliverable(path, item.optString("description"))
        }
        return files
    }

    /** Appends [files] to [existing], replacing entries that share a path. */
    private fun mergeDeliverables(
        existing: List<AgentDeliverable>,
        files: List<AgentDeliverable>
    ): List<AgentDeliverable> {
        if (files.isEmpty()) return existing
        val merged = LinkedHashMap<String, AgentDeliverable>()
        existing.forEach { merged[it.path] = it }
        files.forEach { merged[it.path] = it }
        return merged.values.toList()
    }

    private fun parseTodos(value: Any?): List<AgentTodo> {
        val array = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("todos") ?: value.optJSONArray("items")
            else -> null
        } ?: return emptyList()
        val todos = mutableListOf<AgentTodo>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val text = item.optString("content")
                .ifBlank { item.optString("text") }
                .ifBlank { item.optString("title") }
            if (text.isBlank()) continue
            todos += AgentTodo(
                text = text,
                status = item.optString("status").ifBlank { "pending" }
            )
        }
        return todos.take(20)
    }

    private fun parsePlan(value: Any?): AgentPlan {
        if (value is String) {
            return if (value.isBlank()) AgentPlan() else AgentPlan(active = true, text = value)
        }
        if (value is JSONObject) {
            val text = listOf("markdown", "text", "summary", "content", "plan")
                .asSequence()
                .map { value.optString(it) }
                .firstOrNull { it.isNotBlank() }
            val active = value.optBoolean("active", false) ||
                value.optString("status").equals("active", ignoreCase = true)
            val pending = value.optBoolean("pending", false)
            return AgentPlan(active = active || pending, pending = pending, text = text)
        }
        return AgentPlan()
    }

    private fun parsePermissionMode(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.isNotBlank() }
        // dsh's `permissions` projection is `{ currentValue }` (see
        // @deepseek-ai/dsh-permission-presets types); older shapes carried `mode`.
        is JSONObject -> listOf("currentValue", "mode", "permissionMode")
            .asSequence()
            .map { value.optString(it) }
            .firstOrNull { it.isNotBlank() }
        else -> null
    }

    private fun titleText(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.isNotBlank() }
        is JSONObject -> value.optString("title")
            .ifBlank { value.optString("value") }
            .takeIf { it.isNotBlank() }
        else -> null
    }

    private fun parseQuestions(array: JSONArray?): List<AgentQuestionItem> {
        if (array == null) return emptyList()
        val result = mutableListOf<AgentQuestionItem>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val question = item.optString("question").ifBlank { item.optString("text") }
            if (id.isBlank() || question.isBlank()) continue
            val options = mutableListOf<AgentQuestionOption>()
            val optionArray = item.optJSONArray("options")
            if (optionArray != null) {
                for (optionIndex in 0 until optionArray.length()) {
                    val option = optionArray.optJSONObject(optionIndex) ?: continue
                    val label = option.optString("label").ifBlank { option.optString("value") }
                    if (label.isNotBlank()) {
                        options += AgentQuestionOption(
                            label = label,
                            description = option.optString("description").takeIf { it.isNotBlank() }
                        )
                    }
                }
            }
            result += AgentQuestionItem(
                id = id,
                question = question,
                header = item.optString("header").takeIf { it.isNotBlank() },
                detail = item.optString("detail").takeIf { it.isNotBlank() },
                options = options,
                multiSelect = item.optBoolean("multiSelect")
            )
        }
        return result
    }

    private fun parseQueue(array: JSONArray?): List<AgentQueueItem> {
        if (array == null) return emptyList()
        val result = mutableListOf<AgentQueueItem>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val placement = item.optString("placement").ifBlank { "queued" }
            if (placement == "context") continue
            val content = item.optJSONObject("message")?.opt("content")
            val (text, files) = splitAttachmentText(contentText(content))
            val images = parseImages(content as? JSONArray)
            if (text.isBlank() && images.isEmpty() && files.isEmpty()) continue
            result += AgentQueueItem(
                id = item.optString("id"),
                placement = placement,
                text = text,
                images = images,
                files = files
            )
        }
        return result
    }

    private fun contentText(content: Any?): String {
        if (content == null || content === JSONObject.NULL) return ""
        if (content is String) return content
        if (content is JSONArray) {
            val parts = mutableListOf<String>()
            for (index in 0 until content.length()) {
                val value = contentText(content.opt(index))
                if (value.isNotBlank()) parts += value
            }
            return parts.joinToString("\n")
        }
        if (content is JSONObject) {
            val text = content.optString("text")
            if (text.isNotBlank()) return text
            return contentText(content.opt("content"))
        }
        return content.toString()
    }

    private fun jsonText(value: Any?, limit: Int): String {
        if (value == null || value === JSONObject.NULL) return ""
        val raw = when (value) {
            is String -> value
            is JSONObject -> value.toString(2)
            is JSONArray -> value.toString(2)
            else -> value.toString()
        }
        return raw.take(limit)
    }

    private fun seqOf(value: JSONObject): Long? {
        if (!value.has("seq") || value.isNull("seq")) return null
        return value.optLong("seq", -1L).takeIf { it >= 0 }
    }

    /** dsh stamps every logged event with its wall-clock time; older hosts omit it. */
    private fun timeOf(value: JSONObject): Long? {
        if (!value.has("time") || value.isNull("time")) return null
        return value.optLong("time", -1L).takeIf { it > 0 }
    }

    private fun appendLog(line: String) {
        val cleaned = line.trim().takeIf { it.isNotBlank() } ?: return
        Log.d(TAG, "log: $cleaned")
        _state.update {
            it.copy(logs = (it.logs + cleaned).takeLast(200))
        }
    }

    /**
     * dsh only accepts `subagents/prompt` through the *exact live direct parent*, and its
     * remote session API refuses to resume a subagent-owned session on its own. After a
     * restart the parent chain is cold, so writing into a 2nd-level-or-deeper child fails
     * with `parent-unavailable` until that parent is woken by a message in its own view.
     * Say that in plain language instead of echoing the wire error.
     */
    private fun subagentPromptError(error: Throwable): String {
        val code = (error as? AgentBridgeException)?.details?.optString("dshCode").orEmpty()
        return when (code) {
            "subagent/parent-unavailable" ->
                "上一级子 Agent 不在线，dsh 只允许在直接上级在线时继续下一级。先回到上一级对话发一条消息，再回来发送（$code）。"
            "subagent/not-resumable" -> "这个子 Agent 无法恢复：会话记录缺失或已归档（$code）。"
            "subagent/delivery-unavailable" -> "子 Agent 并发已满，稍后再试（$code）。"
            else -> readableError(error)
        }
    }

    private fun readableError(error: Throwable): String {
        if (error is AgentBridgeException) {
            val code = error.details?.optString("dshCode").orEmpty()
            return if (code.isNotBlank()) "${error.message}（$code）" else error.message
        }
        return error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    }

    /**
     * Failures the reconnect machinery is already healing. They are worth a log line but
     * not a red notice: the outage notice and its recovery notice already say it better.
     */
    private fun isConnectivityFailure(error: Throwable): Boolean {
        if (error is AgentBridgeException) {
            if (error.code == "transport" || error.code == "auth") return true
        }
        val message = error.message.orEmpty()
        return message.contains("事件通道") || message.contains("桥接已退出") ||
            message.contains("尚未连接")
    }

    private fun now(): Long = System.currentTimeMillis()

    /** A model capacity, present only when dsh actually stated a positive number. */
    private fun JSONObject.positiveInt(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return optInt(name).takeIf { it > 0 }
    }
}
