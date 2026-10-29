package com.kingkang.app.agent

/**
 * App-side view of a dsh session. The bridge extracts display fields so Compose never
 * has to understand dsh's projection storage.
 */
data class AgentSession(
    val id: String,
    val title: String = "",
    val updatedAt: Long = 0L,
    val running: Boolean = false,
    val blank: Boolean = false,
    val parentId: String? = null,
    val cwd: String? = null,
    /** Agent preset the session runs, as recorded by dsh's `agentPreset` projection. */
    val presetId: String? = null
) {
    val displayTitle: String
        get() = title.ifBlank { if (blank) "新对话" else "未命名对话" }
}

enum class AgentConnection {
    Stopped,
    Starting,
    Ready,
    Failed
}

sealed interface AgentItem {
    val key: String
    val seq: Long?
}

/**
 * One image carried by a message. `localData` holds the prompt-time data URL until dsh's
 * durable echo arrives; after that the bytes are fetched through `session.attachment`
 * and cached in [AgentState.imageData] under `sessionId:attachmentId`.
 */
data class AgentImage(
    val attachmentId: String,
    val mediaType: String = "image/png",
    val width: Int = 0,
    val height: Int = 0,
    val bytes: Long = 0L,
    val name: String? = null,
    val localData: String? = null
)

/**
 * One arbitrary file the user attached. dsh's prompt wire only carries text and images,
 * so the bytes are staged in the Kali-visible shared directory and the prompt names the
 * guest path; this row keeps the original name for display.
 */
data class AgentFile(
    val name: String,
    val mimeType: String = "",
    val size: Long = 0L,
    val guestPath: String,
    val hostPath: String? = null
)

data class UserItem(
    override val key: String,
    val text: String,
    override val seq: Long? = null,
    val pending: Boolean = false,
    val source: String? = null,
    val images: List<AgentImage> = emptyList(),
    val files: List<AgentFile> = emptyList(),
    val rpcId: String? = null
) : AgentItem

data class AssistantItem(
    override val key: String,
    val text: String = "",
    val reasoning: String = "",
    val streaming: Boolean = false,
    override val seq: Long? = null,
    val turn: Int? = null,
    val step: Int? = null,
    val images: List<AgentImage> = emptyList()
) : AgentItem

data class ToolItem(
    override val key: String,
    val callId: String,
    val name: String,
    val arguments: String = "",
    val resultText: String = "",
    val isError: Boolean = false,
    val running: Boolean = true,
    override val seq: Long? = null,
    val viewJson: String? = null
) : AgentItem

data class NoticeItem(
    override val key: String,
    val text: String,
    val isError: Boolean = false,
    override val seq: Long? = null
) : AgentItem

data class AgentApproval(
    val rpcId: String,
    val sessionId: String,
    val approvalId: String,
    val toolName: String,
    val reason: String? = null,
    val callId: String? = null
)

data class AgentQuestionOption(
    val label: String,
    val description: String? = null
)

data class AgentQuestionItem(
    val id: String,
    val question: String,
    val header: String? = null,
    val detail: String? = null,
    val options: List<AgentQuestionOption> = emptyList(),
    val multiSelect: Boolean = false
)

data class AgentQuestion(
    val rpcId: String,
    val sessionId: String,
    val questions: List<AgentQuestionItem>
)

data class AgentAnswer(
    val id: String,
    val selected: List<String> = emptyList(),
    val custom: String? = null
)

data class AgentTodo(
    val text: String,
    val status: String = "pending"
)

data class AgentPlan(
    val active: Boolean = false,
    val pending: Boolean = false,
    val text: String? = null
)

data class AgentQueueItem(
    val id: String,
    val placement: String = "queued",
    val text: String = "",
    val images: List<AgentImage> = emptyList(),
    val files: List<AgentFile> = emptyList()
) {
    val displayText: String
        get() = text.ifBlank {
            when {
                images.isNotEmpty() && files.isNotEmpty() ->
                    "[${images.size} 张图片 · ${files.size} 个文件]"
                images.isNotEmpty() -> if (images.size == 1) "[图片]" else "[${images.size} 张图片]"
                files.isNotEmpty() -> if (files.size == 1) "[文件] ${files.first().name}"
                    else "[${files.size} 个文件]"
                else -> ""
            }
        }
}

data class AgentEffort(
    val id: String,
    val name: String
)

data class AgentModel(
    val provider: String,
    val id: String,
    val name: String,
    val description: String? = null,
    val efforts: List<AgentEffort> = emptyList(),
    val defaultEffort: String? = null
)

data class AgentModelGroup(
    val id: String,
    val name: String,
    val models: List<AgentModel>
)

data class AgentModels(
    val provider: String,
    val model: String,
    val reasoningEffort: String? = null,
    val routable: Boolean = false,
    val groups: List<AgentModelGroup> = emptyList()
) {
    val current: AgentModel?
        get() = groups.asSequence()
            .flatMap { it.models.asSequence() }
            .firstOrNull { it.provider == provider && it.id == model }

    /** The effort dsh will actually use: an explicit pick, else the model's default. */
    val effectiveEffort: String?
        get() = reasoningEffort ?: current?.defaultEffort

    val currentEffort: AgentEffort?
        get() = effectiveEffort?.let { id ->
            current?.efforts?.firstOrNull { it.id == id }
        }
}

/** One image the composer is holding before the prompt is sent. */
class AgentDraftImage(
    val id: String,
    val name: String,
    val mediaType: String,
    val bytes: ByteArray,
    val width: Int,
    val height: Int
) {
    /** Same `data:` shape the transcript cache uses, so previews share one decoder. */
    val dataUrl: String
        get() = "data:$mediaType;base64," + android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.NO_WRAP
        )
}

/** One file the composer is holding before its path is sent to the model. */
data class AgentDraftFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val hostPath: String,
    val guestPath: String
)

/** One user-invocable skill advertised by dsh's per-session skill catalog. */
data class AgentSkill(
    val name: String,
    val description: String = "",
    val whenToUse: String? = null,
    val modelInvocable: Boolean = true,
    val path: String? = null
)

/** One slash command advertised by dsh's session command registry. */
data class AgentCommand(
    val name: String,
    val description: String = "",
    val hint: String? = null,
    val acceptsAttachments: Boolean = false
)

/** One selectable permission preset advertised by dsh's process catalog. */
data class AgentPermissionOption(
    val value: String,
    val name: String,
    val description: String? = null
)

/** The process-level permission catalog: per-session options and eligible defaults. */
data class AgentPermissionCatalog(
    val options: List<AgentPermissionOption> = emptyList(),
    val defaultOptions: List<AgentPermissionOption> = emptyList(),
    val defaultPreset: String? = null
)

/** The `permission` settings namespace: the default preset for new sessions. */
data class AgentPermissionSettings(
    val writable: Boolean = false,
    val revision: Int = 0,
    val defaultPreset: String? = null,
    val configured: Boolean = false
)

/** One composable Agent mode advertised by dsh's agent-preset roster. */
data class AgentPreset(
    val id: String,
    /** Name the preset published; blank for dsh's shipped presets. */
    val name: String = "",
    val description: String? = null,
    /** Why this preset cannot compose a session; null when it can. */
    val broken: String? = null,
    val isDefault: Boolean = false
)

/** The `agent-preset-registry` settings namespace: the mode new sessions compose. */
data class AgentPresetSettings(
    val loaded: Boolean = false,
    val writable: Boolean = false,
    val revision: Int = 0,
    /** The effective default, whether it comes from the user document or dsh's config. */
    val defaultId: String? = null,
    /** Only the value the user wrote; null when the user has never chosen a mode. */
    val userDefaultId: String? = null
)

/**
 * Display copy for dsh's shipped presets. They publish no name, so dsh resolves their
 * labels through its own locale dictionary; the app mirrors the same Chinese copy. A
 * preset that publishes a name owns its copy and is never translated here.
 */
object AgentPresetCopy {
    private val builtIn = mapOf(
        "standard" to (
            "标准模式" to
                "处理代码、文件和资料，适合大多数任务。Agent 会按需使用检索、编辑和终端等工具。"
            ),
        "ptc" to (
            "PTC 模式" to
                "包含标准模式的所有能力，更适合批量调用工具，并对结果进行筛选、整理、去重、统计或汇总的任务。"
            ),
        "minimal" to (
            "极简模式" to
                "Agent 使用终端和图片读取工具，并支持上下文压缩，适合测试和对比其基础表现。"
            ),
        "cordis" to (
            "创造模式" to
                "用对话定制 DSH：让 Agent 编写插件，添加新功能或界面；也能组合工具和提示词，创建自己的模式。"
            )
    )

    fun name(preset: AgentPreset): String =
        preset.name.ifBlank { builtIn[preset.id]?.first ?: preset.id }

    fun description(preset: AgentPreset): String? =
        if (preset.name.isBlank()) builtIn[preset.id]?.second ?: preset.description
        else preset.description
}

/**
 * One direct session-backed child of the active conversation. dsh owns the catalog;
 * `mode` decides whether the app may continue the child's conversation.
 */
data class AgentSubagent(
    val parentId: String,
    val id: String,
    val label: String? = null,
    val mode: String = "one-shot",
    val running: Boolean = false,
    val hasChildren: Boolean = false,
    /** Non-null when dsh reported the child as a broken catalog entry. */
    val diagnostic: String? = null
) {
    val continuable: Boolean
        get() = mode == "continuable"

    val displayLabel: String
        get() = label?.takeIf { it.isNotBlank() }
            ?: if (continuable) "可对话的子 Agent" else "只读子 Agent"
}

/**
 * One dsh Workspace: a named project directory and the sessions dsh accounts to it.
 * `sessionIds` is dsh's manual order; a session not listed in any workspace stays in
 * the drawer's ungrouped section.
 */
data class AgentWorkspace(
    val id: String,
    val path: String = "",
    val title: String = "",
    val sessionIds: List<String> = emptyList()
) {
    val displayTitle: String
        get() = title.ifBlank { path.substringAfterLast('/').ifBlank { path } }
            .ifBlank { "未命名工作区" }
}

/** One plugin bundle the user installed into the dsh profile. */
data class AgentPlugin(
    val name: String,
    val title: String = "",
    val description: String = "",
    val version: String? = null,
    val enabled: Boolean = true,
    val removable: Boolean = true,
    val official: Boolean = false
) {
    val displayTitle: String
        get() = title.ifBlank { name }
}

/** An official catalog bundle that is not installed yet, offered for one-tap install. */
data class AgentPluginOffer(
    val name: String,
    val title: String = "",
    val description: String = "",
    val version: String? = null,
    val spec: String = "",
    val official: Boolean = true
) {
    val displayTitle: String
        get() = title.ifBlank { name }
}

/** What dsh answered when asked what a package spec names, before anything installs. */
data class AgentPluginInspection(
    val accepted: Boolean,
    val name: String? = null,
    val version: String? = null,
    val description: String? = null,
    val problem: String? = null,
    val reason: String? = null
)

/** Outcome of installing one package into the dsh profile. */
data class AgentPluginInstall(
    val changed: Boolean = false,
    val restartRequired: Boolean = false,
    val bundle: String? = null,
    val version: String? = null,
    val error: String? = null
)

/** Outcome of switching one plugin bundle on or off in the dsh profile. */
data class AgentPluginChange(
    val changed: Boolean = false,
    val restartRequired: Boolean = false,
    val restartReason: String? = null,
    val enabled: Boolean = true,
    val error: String? = null
)

/** State of one dsh credential reference; the value itself never leaves dsh. */
data class AgentCredential(
    val ref: String,
    val configured: Boolean = false,
    val source: String? = null,
    val writable: Boolean = true
)

data class AgentState(
    val connection: AgentConnection = AgentConnection.Stopped,
    val status: String = "正在准备 Agent…",
    val error: String? = null,
    val bridgeVersion: String? = null,
    val dshVersion: String? = null,
    /**
     * Loopback port dsh web listens on inside Kali. The app's Harness screen loads that
     * origin in a WebView; `null` until the bridge has started dsh.
     */
    val webPort: Int? = null,
    val capabilities: Set<String> = emptySet(),
    val sessions: List<AgentSession> = emptyList(),
    val workspaces: List<AgentWorkspace> = emptyList(),
    /** Sessions dsh archived from Workspace navigation; the drawer hides them. */
    val archivedSessionIds: Set<String> = emptySet(),
    /** Pinned sessions, most recently pinned first. */
    val pinnedSessionIds: List<String> = emptyList(),
    /** Plugin bundles the user installed into the dsh profile. */
    val plugins: List<AgentPlugin> = emptyList(),
    /** Official catalog bundles that are not installed; one tap installs them. */
    val pluginOffers: List<AgentPluginOffer> = emptyList(),
    /**
     * True once the plugin inventory has been read from dsh. An empty [plugins] list means
     * "nothing installed" only after this flips; before that the page is still loading.
     */
    val pluginLoaded: Boolean = false,
    /**
     * Primary npm registry the dsh plugin manager asks first, as written into the profile's
     * `.npmrc`. Blank means the profile has no line yet; the bridge writes Aliyun's mirror
     * on startup, and the app pushes its stored choice (Alibaba by default) on every connect.
     * dsh installs use the same stored choice, so this is only the profile-side echo.
     */
    val pluginRegistry: String = "",
    val activeSessionId: String? = null,
    /**
     * Children of the conversation currently on screen: the root session's direct children,
     * or, while a child owns the transcript, that child's own children.
     */
    val subagents: List<AgentSubagent> = emptyList(),
    val subagentsParentAvailable: Boolean = true,
    /**
     * Root-to-open child chain. Empty while the root conversation owns the transcript;
     * otherwise the last entry is the child on screen and is also [activeSessionId], so the
     * existing renderer and event fold apply unchanged. Each entry's `parentId` addresses
     * the session that spawned it, which is what `subagent.*` calls need at every depth.
     */
    val subagentTrail: List<AgentSubagent> = emptyList(),
    val items: List<AgentItem> = emptyList(),
    val running: Boolean = false,
    /**
     * A stop request is in flight. dsh acknowledges cancel immediately but settles the turn
     * asynchronously, so the composer keeps its stop button disabled until the turn ends.
     */
    val turnStopping: Boolean = false,
    val approvals: List<AgentApproval> = emptyList(),
    val questions: List<AgentQuestion> = emptyList(),
    val todos: List<AgentTodo> = emptyList(),
    val plan: AgentPlan = AgentPlan(),
    val permissionMode: String? = null,
    val queue: List<AgentQueueItem> = emptyList(),
    val models: AgentModels? = null,
    val commands: List<AgentCommand> = emptyList(),
    val skills: List<AgentSkill> = emptyList(),
    val credentials: Map<String, AgentCredential> = emptyMap(),
    val permissionCatalog: AgentPermissionCatalog = AgentPermissionCatalog(),
    val permissionSettings: AgentPermissionSettings = AgentPermissionSettings(),
    /** Agent-preset roster dsh advertises; new sessions compose the default. */
    val agentPresets: List<AgentPreset> = emptyList(),
    val agentPresetSettings: AgentPresetSettings = AgentPresetSettings(),
    /** Loaded image bytes as `data:<mediaType>;base64,…`, keyed by `sessionId:attachmentId`. */
    val imageData: Map<String, String> = emptyMap(),
    val hasMoreHistory: Boolean = false,
    val oldestSeq: Long? = null,
    val logs: List<String> = emptyList(),
    val lastEventAt: Long = 0L
) {
    val activeSession: AgentSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }

    /** The open child, or null while the root conversation owns the transcript. */
    val subagentView: AgentSubagent?
        get() = subagentTrail.lastOrNull()

    /** Child levels below the root conversation; 0 at the root. */
    val subagentDepth: Int
        get() = subagentTrail.size

    val activeApprovals: List<AgentApproval>
        get() = approvals.filter { it.sessionId == activeSessionId }

    val activeQuestions: List<AgentQuestion>
        get() = questions.filter { it.sessionId == activeSessionId }
}
