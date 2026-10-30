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
    val rpcId: String? = null,
    /** Event wall-clock time from dsh, used by the turn navigator when the turn is loaded. */
    val time: Long? = null
) : AgentItem

data class AssistantItem(
    override val key: String,
    val text: String = "",
    val reasoning: String = "",
    val streaming: Boolean = false,
    override val seq: Long? = null,
    val turn: Int? = null,
    val step: Int? = null,
    val images: List<AgentImage> = emptyList(),
    /** Provider usage for this one completion, when dsh withheld no bucket. */
    val usage: AgentUsage? = null,
    /**
     * dsh's stable id for the finalized message. Feedback and any message-level action
     * address it by this id; a streaming or interrupted partial has none.
     */
    val messageId: String? = null
) : AgentItem

/** dsh's per-message feedback record; `version` is the compare-and-set token. */
data class AgentMessageFeedback(
    val messageId: String,
    /** positive | negative */
    val rating: String,
    val note: String? = null,
    val category: String? = null,
    val version: String? = null
)

data class ToolItem(
    override val key: String,
    val callId: String,
    val name: String,
    val arguments: String = "",
    val resultText: String = "",
    val isError: Boolean = false,
    val running: Boolean = true,
    override val seq: Long? = null,
    val viewJson: String? = null,
    /**
     * dsh's tool-result `meta` (diffs, match counts, truncation flags). The web client
     * renders its diff cards from this rather than from the result text.
     */
    val metaJson: String? = null,
    /** dsh event times for the call and its result, used to show elapsed time. */
    val startedAt: Long? = null,
    val finishedAt: Long? = null
) : AgentItem {
    /** Call duration, once both ends were stamped. */
    val elapsedMs: Long?
        get() {
            val start = startedAt ?: return null
            val end = finishedAt ?: return null
            return (end - start).takeIf { it >= 0 }
        }
}

/**
 * One subagent a workflow script fanned out. [phase] is the script's progress group;
 * null means the call passed no phase, empty string means it passed an empty one.
 */
data class AgentWorkflowMember(
    val seq: Int,
    val label: String = "",
    val phase: String? = null,
    val childId: String? = null,
    /** completed | cancelled | failed, or null while the member is still running. */
    val outcome: String? = null
)

/**
 * One durable workflow run, folded from `tool-workflow/run-start`, `agent-start`,
 * `agent-end` and `run-end`. dsh records these only in the session log, so the card is
 * rebuilt from history exactly like the live stream.
 */
data class WorkflowItem(
    override val key: String,
    val runId: String,
    val name: String = "",
    val members: List<AgentWorkflowMember> = emptyList(),
    /** completed | cancelled | error; null until the run settles. */
    val stopReason: String? = null,
    /** The enclosing turn closed without a run-end event (dsh-web's "interrupted"). */
    val interrupted: Boolean = false,
    override val seq: Long? = null
) : AgentItem {
    val status: String
        get() = when {
            stopReason == "completed" -> "completed"
            stopReason == "cancelled" -> "cancelled"
            stopReason == "error" -> "failed"
            stopReason != null -> stopReason
            interrupted -> "interrupted"
            else -> "running"
        }

    val running: Boolean
        get() = status == "running"
}

/**
 * Usage for a single assistant completion. The provider may omit any bucket; dsh's
 * `assistant/message` events carry the same optional shape.
 */
data class AgentUsage(
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWriteTokens: Long = 0L,
    val reasoningTokens: Long = 0L
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens

    val hasData: Boolean
        get() = inputTokens > 0L || outputTokens > 0L ||
            cacheReadTokens > 0L || cacheWriteTokens > 0L || reasoningTokens > 0L
}

data class NoticeItem(
    override val key: String,
    val text: String,
    val isError: Boolean = false,
    override val seq: Long? = null,
    /** Expandable body for a compaction divider: the model-facing summary. */
    val detail: String? = null,
    /** Tokens the compaction folded away, when dsh reported the count. */
    val foldedTokens: Long? = null,
    /** Number of history records the compaction folded away. */
    val foldedItems: Int? = null,
    /** True while the compaction is still running. */
    val compacting: Boolean = false
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

/**
 * Outcome of the last session-log export. The archive is copied out of Kali into the
 * app cache, so [hostPath] always names an app-private file the share sheet can expose.
 */
data class AgentExport(
    val sessionId: String = "",
    val running: Boolean = false,
    val name: String = "",
    val bytes: Long = 0L,
    val hostPath: String? = null,
    val error: String? = null
) {
    val done: Boolean
        get() = !running && (hostPath != null || error != null)

    val hasFile: Boolean
        get() = hostPath != null
}

/**
 * One background job from dsh's `job/list`. The list is per session, so a job belongs to
 * the conversation that started it; [startedAt] and [finishedAt] are epoch milliseconds.
 */
data class AgentJob(
    val id: String,
    val kind: String = "",
    val label: String = "",
    val status: String = "running",
    val progress: String? = null,
    val detail: String? = null,
    val startedAt: Long = 0L,
    val finishedAt: Long? = null,
    val outputTotal: Long = 0L,
    val outputLimitBytes: Long? = null,
    val spillPaths: List<String> = emptyList()
) {
    val running: Boolean
        get() = status == "running" || status == "stopping"

    val failed: Boolean
        get() = status == "failed"

    /** Wall-clock run time; keeps counting while the job has no finish stamp. */
    fun elapsedMs(now: Long = System.currentTimeMillis()): Long =
        ((finishedAt ?: now) - startedAt).coerceAtLeast(0L)

    val statusLabel: String
        get() = when (status) {
            "running" -> "运行中"
            "stopping" -> "停止中"
            "completed" -> "已完成"
            "failed" -> "失败"
            "killed" -> "已停止"
            else -> status
        }
}

data class AgentJobChunk(
    val at: Long = 0L,
    val text: String = "",
    /** stdout | stderr | log */
    val channel: String = "stdout",
    /** True when dsh dropped output before this chunk (spill or ring overflow). */
    val gapBefore: Boolean = false
)

/** One `job/follow` read: the job's latest status plus the scrollback collected. */
data class AgentJobOutput(
    val jobId: String,
    val job: AgentJob? = null,
    val chunks: List<AgentJobChunk> = emptyList(),
    /** Offset to pass as `from` for the next read. */
    val next: Long = 0L,
    val lossy: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
) {
    val text: String
        get() = buildString {
            chunks.forEach { chunk ->
                if (chunk.gapBefore && isNotEmpty()) append("\n…\n")
                append(chunk.text)
            }
        }
}

/**
 * One file the workspace-changes recorder listed for a turn. [display] is dsh's
 * workspace-relative rendering; [path] is the path behind it.
 */
data class AgentChangedFile(
    val path: String,
    val display: String = "",
    val added: Int = 0,
    val deleted: Int = 0,
    val binary: Boolean = false,
    val oversized: Boolean = false
)

/**
 * The change summary one `workspace/changes` event announced. [seq] is that event's
 * sequence, the coordinate the Host serves the summary and its comparisons under.
 */
data class AgentChanges(
    val seq: Long = 0L,
    val turn: Int = 0,
    val total: Int = 0,
    val added: Int = 0,
    val deleted: Int = 0,
    val files: List<AgentChangedFile> = emptyList()
) {
    val empty: Boolean
        get() = files.isEmpty()
}

data class AgentDiffHunk(
    val oldStart: Int = 0,
    val oldLines: Int = 0,
    val newStart: Int = 0,
    val newLines: Int = 0,
    val lines: List<String> = emptyList()
)

/**
 * One changed file's turn-start/turn-end comparison. [kind] is `text`, `binary` or
 * `oversized`; only a text comparison carries hunks.
 */
data class AgentChangesDiff(
    val path: String = "",
    val display: String = "",
    val kind: String = "text",
    val before: Boolean = false,
    val after: Boolean = false,
    val coarse: Boolean = false,
    val hunks: List<AgentDiffHunk> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

/** One file an agent declared with the `present` tool. */
data class AgentDeliverable(
    val path: String,
    val description: String = ""
)

/**
 * Cumulative usage for the whole conversation, as dsh's `tokenUsage` projection reports
 * it. `uncachedInputTokens` is the part of the prompt that missed the cache.
 */
data class AgentTokenUsage(
    val uncachedInputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWriteTokens: Long = 0L
) {
    val inputTokens: Long
        get() = uncachedInputTokens + cacheReadTokens + cacheWriteTokens

    val totalTokens: Long
        get() = inputTokens + outputTokens

    val hasData: Boolean
        get() = totalTokens > 0L
}

data class AgentContextPressure(
    val pressureTokens: Long? = null,
    val projectedTokens: Long? = null,
    val contextWindow: Long? = null
) {
    /** The token count to display: what the next call would project, else the pressure. */
    val usedTokens: Long?
        get() = projectedTokens ?: pressureTokens

    /** 0..1 fill for the composer's context gauge; null until dsh knows the window. */
    val fraction: Float?
        get() {
            val used = usedTokens
            val window = contextWindow?.takeIf { it > 0L } ?: return null
            if (used == null) return null
            return (used.toFloat() / window.toFloat()).coerceIn(0f, 1f)
        }

    val hasData: Boolean
        get() = usedTokens != null || contextWindow != null
}

data class AgentContextBreakdown(
    val systemTokens: Long = 0L,
    val toolsTokens: Long = 0L,
    val messageTokens: Long = 0L
) {
    val totalTokens: Long
        get() = systemTokens + toolsTokens + messageTokens

    val hasData: Boolean
        get() = totalTokens > 0L
}

/** dsh's `goal` projection: the long-running objective and its round budget. */
data class AgentGoal(
    val id: String = "",
    val revision: Long = 0L,
    val objective: String = "",
    /** active | paused | blocked | complete */
    val phase: String = "",
    val maxGoalRounds: Int = 0,
    val roundsStarted: Int = 0,
    val blockedCode: String? = null,
    val blockedMessage: String? = null,
    /**
     * Process-local continuation authority from the goal service: `armed` or `disarmed`.
     * The durable projection does not carry it, so it is known only after a `goal.get`
     * read (for example when the workbench opens). Null means "not read yet".
     */
    val activation: String? = null
) {
    val hasData: Boolean
        get() = objective.isNotBlank() || id.isNotBlank()

    val isActive: Boolean
        get() = phase == "active"
}

/**
 * One reminder from dsh's schedule catalog. dsh has no create RPC: the agent's
 * `schedule_create` tool is the only writer, so this app reads, edits and deletes.
 *
 * [status] is `active` until a one-shot fires or a recurrence has no next occurrence;
 * `inactive` therefore means ended, not paused. [raw] is the catalog record as dsh sent
 * it, kept so an edit can name the exact revision it expects.
 */
data class AgentSchedule(
    val id: String,
    val sessionId: String = "",
    /** after | at | every | daily | weekly | cron */
    val kind: String = "",
    val title: String = "",
    val prompt: String = "",
    /** Next occurrence, epoch milliseconds; null when dsh's stamp could not be parsed. */
    val scheduledAt: Long? = null,
    /** active | inactive */
    val status: String = "active",
    val afterSeconds: Long? = null,
    val everySeconds: Long? = null,
    /** HH:mm for daily/weekly records. */
    val time: String? = null,
    val timeZone: String? = null,
    /** ISO weekdays, Monday 1 through Sunday 7, for weekly records. */
    val weekdays: List<Int> = emptyList(),
    val expression: String? = null,
    val lastDeliveredAt: Long? = null,
    val raw: String = ""
) {
    val active: Boolean
        get() = status != "inactive"
}

/** One saved inbox delivery of a reminder. */
data class AgentScheduleDelivery(
    val scheduledAt: Long? = null,
    val deliveredAt: Long? = null,
    val prompt: String? = null,
    val messageId: String = ""
)

/** `schedule/history`: newest-first deliveries plus the retention rule that pruned them. */
data class AgentScheduleHistory(
    val id: String,
    val records: List<AgentScheduleDelivery> = emptyList(),
    val retentionDays: Int = 0,
    val retentionRecords: Int = 0,
    val earlierUnavailable: Boolean = false,
    val earlierPruned: Boolean = false,
    val nextBefore: String? = null,
    val loading: Boolean = false,
    val error: String? = null
)

/** Choice dsh would use for the next turn, or the one the last turn used. */
data class AgentModelSelection(
    val provider: String = "",
    val model: String = "",
    val reasoningEffort: String? = null
) {
    val hasData: Boolean
        get() = provider.isNotBlank() || model.isNotBlank()

    val label: String
        get() = listOf(provider, model).filter { it.isNotBlank() }.joinToString(" / ")
}

data class AgentSubagentTiming(
    val settledMs: Long = 0L,
    val activeSince: Long? = null,
    val activeThrough: Long? = null,
    val lastTurnCompleted: Boolean? = null
)

data class AgentTeamMember(
    val id: String,
    val name: String,
    /** lead | teammate */
    val role: String,
    /** provisioning | active | failed */
    val phase: String,
    val error: String? = null
)

data class AgentTeamTask(
    val id: String,
    val revision: Long = 0L,
    val subject: String = "",
    val description: String = "",
    /** pending | in_progress | completed | deleted */
    val status: String = "pending",
    val ownerId: String? = null
)

/** dsh's `agentTeam` projection — the live roster and task board of a team session. */
data class AgentTeam(
    val members: List<AgentTeamMember> = emptyList(),
    val tasks: List<AgentTeamTask> = emptyList(),
    val failure: String? = null
) {
    val hasData: Boolean
        get() = members.isNotEmpty() || tasks.isNotEmpty() || !failure.isNullOrBlank()

    val lead: AgentTeamMember?
        get() = members.firstOrNull { it.role == "lead" }
}

/** One row of dsh's `turnOutline` projection: a prompt and its reply, keyed by turn. */
data class AgentTurnOutlineEntry(
    val turn: Int,
    /** Source event seq of the turn; used to scroll the transcript to this turn. */
    val seq: Long? = null,
    val prompt: String = "",
    val response: String = ""
)

/** dsh's `sessionStats` projection: timing totals for the whole conversation. */
data class AgentSessionStats(
    val turns: Int = 0,
    val steps: Int = 0,
    val llmMs: Long? = null,
    val toolMs: Long? = null,
    val ttftMs: Long? = null,
    val ttftSteps: Int = 0,
    val decodeMs: Long? = null,
    val decodeTokens: Long? = null
) {
    val hasData: Boolean
        get() = turns > 0 || steps > 0 || llmMs != null || toolMs != null

    /** Decode speed in tokens per second, when both halves were reported. */
    val decodeTps: Float?
        get() {
            val ms = decodeMs ?: return null
            val tokens = decodeTokens ?: return null
            if (ms <= 0L || tokens <= 0L) return null
            return tokens * 1000f / ms
        }
}

data class AgentDirectoryEntry(
    val name: String,
    val path: String
)/** One directory listing from the workspace picker (Kali paths only). */
data class AgentDirectoryListing(
    val path: String = "",
    val parent: String? = null,
    val entries: List<AgentDirectoryEntry> = emptyList(),
    val truncated: Boolean = false,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null
)

/** One direct child of the open Session's workspace, as the file sidebar lists it. */
data class AgentFileEntry(
    val name: String,
    val path: String,
    /** `file`, `directory` or `link`; a link is shown but never followed. */
    val type: String = "file",
    val size: Long = 0L,
    /** Last modification instant in epoch milliseconds, or 0 when unknown. */
    val modified: Long = 0L
) {
    val directory: Boolean get() = type == "directory"
}

/** The workspace directory the file sidebar is showing. */
data class AgentFileListing(
    val root: String = "",
    val path: String = "",
    val parent: String? = null,
    val entries: List<AgentFileEntry> = emptyList(),
    val truncated: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

/**
 * One file the sidebar preview asked for. [base64] holds the raw prefix the bridge
 * returned; the viewer decides whether it is text (and in which charset), an image, or
 * binary. [truncated] means the file is larger than the preview cap.
 */
data class AgentFilePreview(
    val path: String = "",
    val name: String = "",
    val size: Long = 0L,
    val base64: String = "",
    val truncated: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
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

/**
 * One `@` path completion from dsh's file-reference provider. Paths are relative to the
 * session workspace when the provider reports them that way; the prompt grammar accepts
 * either spelling, so the app keeps the string dsh returned.
 */
data class AgentFileReference(
    val path: String,
    val directory: Boolean = false
)

/**
 * One `@` conversation completion. [mention] is dsh's canonical
 * `@[label](dsh-session:…)` spelling and is what the prompt must carry; the remaining
 * fields exist only for this picker's title and location hint.
 */
data class AgentSessionReference(
    val sessionId: String,
    val label: String,
    val displayTitle: String? = null,
    val cwd: String? = null,
    val sameWorkspace: Boolean = false,
    val createdAt: Long = 0L,
    val mention: String
) {
    val title: String
        get() = displayTitle?.takeIf { it.isNotBlank() } ?: label
}

/**
 * One recognizer dsh offers. [phase] is the Host-owned preparation state: `ready` and
 * `standby` can transcribe now, `unprepared`/`cancelled`/`failed` need preparing first,
 * and the progress phases describe a task that is already running.
 */
data class AgentSpeechProvider(
    val id: String,
    val name: String,
    val location: String = "",
    val languages: List<String> = emptyList(),
    val phase: String = "",
    val completedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val message: String? = null,
    val diskBytes: Long? = null,
    val memoryBytes: Long? = null,
    val minMinutes: Int? = null,
    val maxMinutes: Int? = null
) {
    val ready: Boolean
        get() = phase.isBlank() || phase == "ready" || phase == "standby"

    val preparing: Boolean
        get() = phase == "checking" || phase == "downloading" || phase == "loading" ||
            phase == "waking" || phase == "cancelling"

    val needsSetup: Boolean
        get() = phase == "unprepared" || phase == "cancelled" || phase == "failed"
}

/** Provider roster, persisted selection and the audio limits dsh will accept. */
data class AgentSpeechCatalog(
    val providers: List<AgentSpeechProvider> = emptyList(),
    val selectedProviderId: String = "",
    val language: String = "",
    val maxAudioBytes: Long = 10L * 1024L * 1024L,
    val maxDurationSeconds: Int = 60
) {
    val selected: AgentSpeechProvider?
        get() = providers.firstOrNull { it.id == selectedProviderId } ?: providers.firstOrNull()
}

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

/** One model a provider route advertises; only the id is required. */
data class AgentProviderModel(
    val id: String,
    val name: String = "",
    val contextWindow: Int? = null,
    val maxTokens: Int? = null,
    /** Accepted input types when the catalog or endpoint discloses them. */
    val inputModalities: List<String> = emptyList()
) {
    val label: String
        get() = name.ifBlank { id }
}

/** The editable part of one provider profile; absent fields keep dsh's catalog defaults. */
data class AgentProviderProfile(
    val displayName: String? = null,
    val api: String? = null,
    val baseURL: String? = null,
    /** Credential reference, never the secret itself. */
    val apiKeyEnv: String? = null,
    val models: List<AgentProviderModel> = emptyList()
)

/** One route in the provider directory, with the profile the Models page would edit. */
data class AgentProvider(
    val provider: String,
    val displayName: String,
    /** True when the route exists only because configuration declared it. */
    val declared: Boolean = false,
    /** Endpoint the catalog provider uses when the profile sets none. */
    val defaultBaseURL: String? = null,
    val hasProfile: Boolean = false,
    val profile: AgentProviderProfile? = null,
    val keyRef: String = "",
    val keyConfigured: Boolean = false,
    val keySource: String? = null,
    val keyWritable: Boolean = true,
    /** Configuration diagnostic; the route may still serve other models. */
    val error: String? = null
)

/** The `llm-pi-ai` settings namespace and the directory of routes it can activate. */
data class AgentModelProviders(
    val loaded: Boolean = false,
    /** False when this dsh has no multi-provider settings entry; the Harness owns them. */
    val supported: Boolean = true,
    val writable: Boolean = false,
    val revision: Int = 0,
    val protocols: List<String> = listOf(
        "openai-completions",
        "openai-responses",
        "anthropic-messages"
    ),
    val providers: List<AgentProvider> = emptyList(),
    /** The model new conversations start with, when dsh exposes it. */
    val defaultProvider: String? = null,
    val defaultModel: String? = null,
    val error: String? = null
) {
    val configured: List<AgentProvider>
        get() = providers.filter { it.hasProfile }

    val available: List<AgentProvider>
        get() = providers.filter { !it.hasProfile }
}

/** A draft provider interrogation, kept in state so the form can show the reply. */
data class AgentModelDiscovery(
    val stamp: Long = 0L,
    val provider: String = "",
    val baseURL: String = "",
    val api: String = "",
    val loading: Boolean = false,
    val models: List<AgentProviderModel> = emptyList(),
    val error: String? = null
)

/** Values one save carries; the key travels only into the write method. */
data class AgentProviderDraft(
    val previousRoute: String? = null,
    val route: String,
    val displayName: String,
    val api: String,
    val baseURL: String,
    val apiKeyRef: String,
    val apiKey: String,
    val models: List<AgentProviderModel>
)

/** Progress of the in-flight provider save, so the form closes only after dsh accepted it. */
data class AgentProviderSave(
    val route: String = "",
    val saving: Boolean = false,
    val done: Boolean = false,
    val error: String? = null
)

/** Display copy for the wire protocols a custom route may speak. */
object AgentProviderCopy {
    fun protocolName(api: String): String = when (api) {
        "openai-completions" -> "OpenAI Chat Completions"
        "openai-responses" -> "OpenAI Responses"
        "anthropic-messages" -> "Anthropic Messages"
        else -> api
    }
}

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
    /** The child session's own title, when dsh has one. */
    val title: String? = null,
    val mode: String = "one-shot",
    val running: Boolean = false,
    val hasChildren: Boolean = false,
    /** Total provider tokens the child has spent, when its projection reports them. */
    val tokens: Long? = null,
    /** Settled plus live activity, in milliseconds. */
    val durationMs: Long? = null,
    /** The child's last turn completed normally (rather than going inactive mid-turn). */
    val completed: Boolean = false,
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

/** Outcome of removing one plugin bundle from the dsh profile. */
data class AgentPluginUninstall(
    val changed: Boolean = false,
    val restartRequired: Boolean = false,
    val restartReason: String? = null,
    val bundle: String? = null,
    val error: String? = null
)

/** One package the configured npm registry returned for a marketplace search. */
data class AgentPluginMarketResult(
    val name: String,
    val version: String? = null,
    val description: String = "",
    val date: String? = null,
    val publisher: String? = null,
    val homepage: String? = null,
    val repository: String? = null,
    val keywords: List<String> = emptyList(),
    val official: Boolean = false
) {
    val displayTitle: String
        get() = name
}

/** Latest marketplace query, kept on screen so results survive a recomposition. */
data class AgentPluginSearch(
    val query: String = "",
    val registry: String = "",
    val total: Int = 0,
    val results: List<AgentPluginMarketResult> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null
)

/** Kind the native settings form renders for one dsh schema field. */
enum class AgentSettingKind {
    Text,
    Number,
    Toggle,
    Choice,
    Secret,
    Unsupported
}

/** One selectable value of a union-of-const dsh setting. */
data class AgentSettingOption(
    val value: String,
    val label: String
)

/**
 * One top-level field of a dsh settings namespace. [text] carries strings and numbers;
 * [checked] carries booleans; a [AgentSettingKind.Secret] field starts blank because dsh
 * never returns its value, and [configured] says whether a value is stored.
 */
data class AgentSettingField(
    val path: List<String>,
    val label: String,
    val kind: AgentSettingKind,
    val text: String = "",
    val checked: Boolean = false,
    val hint: String? = null,
    val configured: Boolean = false,
    val required: Boolean = false,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val pattern: String? = null,
    val options: List<AgentSettingOption> = emptyList(),
    val unsupportedReason: String? = null
) {
    val key: String
        get() = path.joinToString(".")

    /** Non-null when this field's current draft cannot be saved. */
    fun validate(): String? {
        if (kind == AgentSettingKind.Unsupported) return null
        if (kind == AgentSettingKind.Secret) return null
        if (kind == AgentSettingKind.Toggle || kind == AgentSettingKind.Choice) return null
        val value = text.trim()
        if (value.isEmpty()) return if (required) "必填" else null
        if (kind == AgentSettingKind.Number) {
            val number = value.toDoubleOrNull() ?: return "请输入数字"
            min?.let { if (number < it) return "不能小于 ${formatNumber(it)}" }
            max?.let { if (number > it) return "不能大于 ${formatNumber(it)}" }
            step?.let { stepValue ->
                if (stepValue > 0) {
                    val base = min ?: 0.0
                    val steps = (number - base) / stepValue
                    if (kotlin.math.abs(steps - Math.round(steps)) > 1e-6) {
                        return "需为 ${formatNumber(stepValue)} 的倍数"
                    }
                }
            }
        }
        pattern?.takeIf { it.isNotBlank() }?.let { expression ->
            val valid = runCatching { Regex(expression).matches(value) }.getOrNull()
            if (valid == false) return "格式不符合要求"
        }
        return null
    }

    private fun formatNumber(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
}

/** One editable dsh settings namespace, as the native form renders it. */
data class AgentSettingGroup(
    val ns: String,
    val title: String,
    val subtitle: String? = null,
    val writable: Boolean = false,
    val revision: Int = 0,
    val fields: List<AgentSettingField> = emptyList(),
    val note: String? = null
)

/** The curated schema-driven settings the app can edit without the Harness. */
data class AgentSettingsSnapshot(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val writable: Boolean = false,
    val groups: List<AgentSettingGroup> = emptyList(),
    /** Profile namespaces this page leaves to dsh's own Harness pages. */
    val harnessOnlyCount: Int = 0,
    val error: String? = null
)

/** One staged edit; a null [value] asks dsh to unset the path. */
data class AgentSettingEdit(
    val path: List<String>,
    val value: Any? = null
)

/** dsh's `ui-theme` / `locale` preference, read for native controls and sync. */
data class AgentLanguageSettings(
    val loaded: Boolean = false,
    val writable: Boolean = false,
    val revision: Int = 0,
    /** `zh`, `en`, or null when dsh follows its own default. */
    val value: String? = null,
    val saving: Boolean = false,
    val error: String? = null
)

/** The latest DeepSeek Account login attempt, without credentials or PKCE secrets. */
data class AgentAccountAttempt(
    val phase: String = "",
    val errorCode: String? = null
)

/** Display identity from dsh's Platform profile call. */
data class AgentAccountProfile(
    val id: String? = null,
    val name: String? = null,
    val contact: String? = null,
    val avatarUrl: String? = null
)

/** One Platform wallet balance; [balance] stays a decimal string. */
data class AgentAccountWallet(
    val currency: String,
    val balance: String
)

/**
 * Native view of dsh's DeepSeek Account. Login itself stays in the Harness: the OAuth
 * flow needs a browser callback origin, and the app must never collect a platform
 * password. Sign-out, profile and balances are safe to show natively.
 */
data class AgentAccountSnapshot(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    /** signed-out | credential-stored */
    val status: String = "signed-out",
    val usageUrl: String? = null,
    val topUpUrl: String? = null,
    val attempt: AgentAccountAttempt? = null,
    val profile: AgentAccountProfile? = null,
    val profileFailed: Boolean = false,
    val wallets: List<AgentAccountWallet> = emptyList(),
    val bonusWallets: List<AgentAccountWallet> = emptyList(),
    val balanceFailed: Boolean = false,
    val signingOut: Boolean = false,
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
    /** Cumulative token usage of the open conversation. */
    val tokenUsage: AgentTokenUsage = AgentTokenUsage(),
    /** How close the next call is to the model's context window. */
    val contextPressure: AgentContextPressure = AgentContextPressure(),
    /** Where the context tokens went (system, tool schemas, messages). */
    val contextBreakdown: AgentContextBreakdown = AgentContextBreakdown(),
    /** Long-running goal driving this conversation, when one is set. */
    val goal: AgentGoal? = null,
    /** A goal mutation is in flight; its buttons wait for dsh to commit. */
    val goalBusy: Boolean = false,
    /** Why the last goal read or mutation failed, shown beside the goal card. */
    val goalError: String? = null,
    /** Reminders bound to the open conversation, read from dsh's Host-wide catalog. */
    val schedules: List<AgentSchedule> = emptyList(),
    val schedulesLoaded: Boolean = false,
    val schedulesError: String? = null,
    /** A reminder edit or deletion is in flight; its buttons wait for dsh to commit. */
    val scheduleBusy: Boolean = false,
    /** Delivery history loaded on demand, keyed by reminder id. */
    val scheduleHistory: Map<String, AgentScheduleHistory> = emptyMap(),
    /** Provider/model dsh used last and would use next. */
    val modelSelection: AgentModelSelection = AgentModelSelection(),
    /** Durable elapsed time of the child session currently on screen, if any. */
    val subagentTiming: AgentSubagentTiming? = null,
    /** Live team roster and task board, when this is a team session. */
    val agentTeam: AgentTeam? = null,
    /** dsh's `turnOutline`: prompt/response per turn for the navigator. */
    val turnOutline: List<AgentTurnOutlineEntry> = emptyList(),
    /** dsh's `sessionStats`: timing totals for the stats panel. */
    val sessionStats: AgentSessionStats = AgentSessionStats(),
    /** Per-message like/dislike records, keyed by dsh's message id. */
    val messageFeedback: Map<String, AgentMessageFeedback> = emptyMap(),
    /**
     * Projection values this build has no typed home for, serialized as JSON and keyed
     * by projection name. A newer dsh's projections stay inspectable instead of vanishing.
     */
    val extraProjections: Map<String, String> = emptyMap(),
    /** Event/projection names received but not rendered by this build, newest last. */
    val unknownEvents: List<String> = emptyList(),
    /** Session-log export in flight or last completed. */
    val export: AgentExport = AgentExport(),
    /** Background jobs dsh reports for the open conversation. */
    val jobs: List<AgentJob> = emptyList(),
    val jobsLoaded: Boolean = false,
    val jobsError: String? = null,
    /** Scrollback snapshots loaded on demand, keyed by job id. */
    val jobOutput: Map<String, AgentJobOutput> = emptyMap(),
    /** The newest turn's recorded workspace changes, as the Host reports them. */
    val changes: AgentChanges? = null,
    val changesLoaded: Boolean = false,
    val changesLoading: Boolean = false,
    val changesError: String? = null,
    /** The newest announcement is still in history, but its comparison died with dsh. */
    val changesReleased: Boolean = false,
    /** Per-file comparisons loaded on demand, keyed by index in [changes].files. */
    val changeDiffs: Map<Int, AgentChangesDiff> = emptyMap(),
    /**
     * Files the agent declared with `present`, in announcement order and deduplicated by
     * path. The durable `deliverables/presented` event carries them, so history replay
     * rebuilds the list without another host read.
     */
    val deliverables: List<AgentDeliverable> = emptyList(),
    /** Directory the workspace picker is browsing, if it has been opened. */
    val directory: AgentDirectoryListing = AgentDirectoryListing(),
    /** Workspace directory the native file sidebar is showing. */
    val fileListing: AgentFileListing = AgentFileListing(),
    /** File the sidebar preview asked for, with the bytes the bridge returned. */
    val filePreview: AgentFilePreview = AgentFilePreview(),
    /**
     * Bumped when a turn ends or dsh announces workspace changes; the open sidebar
     * reloads the current directory so agent edits show up without a manual refresh.
     */
    val fileRevision: Long = 0L,
    val queue: List<AgentQueueItem> = emptyList(),
    val models: AgentModels? = null,
    val commands: List<AgentCommand> = emptyList(),
    val skills: List<AgentSkill> = emptyList(),
    val credentials: Map<String, AgentCredential> = emptyMap(),
    /** Multi-provider configuration (the web Models page's native counterpart). */
    val modelProviders: AgentModelProviders = AgentModelProviders(),
    /** Latest draft discovery reply, used by the provider form's 测试连接 action. */
    val modelDiscovery: AgentModelDiscovery = AgentModelDiscovery(),
    /** In-flight provider save, so the form can report the real outcome. */
    val providerSave: AgentProviderSave = AgentProviderSave(),
    val permissionCatalog: AgentPermissionCatalog = AgentPermissionCatalog(),
    val permissionSettings: AgentPermissionSettings = AgentPermissionSettings(),
    /** Agent-preset roster dsh advertises; new sessions compose the default. */
    val agentPresets: List<AgentPreset> = emptyList(),
    val agentPresetSettings: AgentPresetSettings = AgentPresetSettings(),
    /** Curated dsh settings rendered as native forms (DSH-502). */
    val agentSettings: AgentSettingsSnapshot = AgentSettingsSnapshot(),
    /** dsh's `locale` namespace, used by the Harness language card. */
    val languageSettings: AgentLanguageSettings = AgentLanguageSettings(),
    /** DeepSeek Account status, profile and balances. */
    val account: AgentAccountSnapshot = AgentAccountSnapshot(),
    /** Latest npm marketplace search from the plugins page. */
    val pluginSearch: AgentPluginSearch = AgentPluginSearch(),
    /** Plugin bundle currently being removed. */
    val pluginRemoving: String? = null,
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

