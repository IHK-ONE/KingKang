package com.kingkang.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingkang.app.agent.AgentAnswer
import com.kingkang.app.agent.AgentApproval
import com.kingkang.app.agent.AgentConnection
import com.kingkang.app.agent.AgentDraftFile
import com.kingkang.app.agent.AgentDraftImage
import com.kingkang.app.agent.AgentEngine
import com.kingkang.app.agent.AgentImage
import com.kingkang.app.agent.AgentPluginInspection
import com.kingkang.app.agent.AgentQuestion
import com.kingkang.app.agent.AgentSession
import com.kingkang.app.agent.AgentState
import com.kingkang.app.agent.AgentSubagent
import com.kingkang.app.data.SettingsStore
import com.kingkang.app.runtime.ChrootEngine
import com.kingkang.app.runtime.CommandSpec
import com.kingkang.app.runtime.HarnessManager
import com.kingkang.app.runtime.NetworkDns
import com.kingkang.app.runtime.ProotEngine
import com.kingkang.app.runtime.RootShell
import com.kingkang.app.runtime.RootfsCatalog
import com.kingkang.app.runtime.RootfsInstaller
import com.kingkang.app.runtime.RootfsManager
import com.kingkang.app.runtime.RootfsSpec
import com.kingkang.app.runtime.RootfsUpdateCheck
import com.kingkang.app.runtime.RuntimeAssets
import com.kingkang.app.terminal.TerminalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EnvironmentUi(
    val installed: Boolean = false,
    val version: String? = null,
    val label: String? = null,
    val approxBytes: Long? = null,
    val installing: Boolean = false,
    val progress: Float? = null,
    val status: String? = null,
    val error: String? = null,
    val checkingUpdate: Boolean = false,
    val updateAvailable: Boolean = false,
    val deleting: Boolean = false,
    val notice: String? = null
)

/** Last plugin install outcome, kept on screen because installs outlive a snackbar. */
data class PluginInstallNote(val text: String, val failed: Boolean = false)

data class HarnessUi(
    val installed: Boolean = false,
    val version: String? = null,
    val nodeVersion: String? = null,
    val latestVersion: String? = null,
    val updateAvailable: Boolean = false,
    val checkingUpdate: Boolean = false,
    val busy: Boolean = false,
    val step: String? = null,
    val progress: Float? = null,
    val error: String? = null
)

sealed interface RootProbe {
    data object Unknown : RootProbe
    data object Checking : RootProbe
    data class Available(val suPath: String) : RootProbe
    data object Unavailable : RootProbe
}

sealed interface TerminalUiState {
    data object Idle : TerminalUiState
    data object NeedsSetup : TerminalUiState
    data class Starting(val message: String = "正在启动 Kali…") : TerminalUiState
    data class Running(
        val engineLabel: String,
        val note: String? = null
    ) : TerminalUiState

    data class Exited(val code: Int, val engineLabel: String) : TerminalUiState
    data class Failed(val message: String) : TerminalUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val rootfsManager = RootfsManager(application)
    private val rootfsInstaller = RootfsInstaller(application, rootfsManager)
    private val rootfsUpdateCheck = RootfsUpdateCheck()
    private val runtimeAssets = RuntimeAssets(application)
    private val harnessManager = HarnessManager(application, rootfsManager, runtimeAssets)
    private val rootShell = RootShell()

    private val _rootMode = MutableStateFlow(settingsStore.rootMode)
    val rootMode: StateFlow<Boolean> = _rootMode.asStateFlow()

    /** Appearance choice: "system" | "light" | "dark". */
    private val _themeMode = MutableStateFlow(settingsStore.themeMode)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    /** npm registry for dsh installs and plugin installs; blank means the Aliyun default. */
    private val _npmRegistry = MutableStateFlow(settingsStore.npmRegistry)
    val npmRegistry: StateFlow<String> = _npmRegistry.asStateFlow()

    private val _rootProbe = MutableStateFlow<RootProbe>(RootProbe.Unknown)
    val rootProbe: StateFlow<RootProbe> = _rootProbe.asStateFlow()

    private val _environment = MutableStateFlow(EnvironmentUi())
    val environment: StateFlow<EnvironmentUi> = _environment.asStateFlow()

    private val _harness = MutableStateFlow(HarnessUi())
    val harness: StateFlow<HarnessUi> = _harness.asStateFlow()

    /** Name of the plugin whose switch is being applied; dsh can take several seconds. */
    private val _pluginChanging = MutableStateFlow<String?>(null)
    val pluginChanging: StateFlow<String?> = _pluginChanging.asStateFlow()

    /** Package spec currently being installed; installs run pnpm inside Kali and are slow. */
    private val _pluginInstalling = MutableStateFlow<String?>(null)
    val pluginInstalling: StateFlow<String?> = _pluginInstalling.asStateFlow()

    /** Outcome of the last install; dsh's own reason stays visible after the toast fades. */
    private val _pluginInstallNote = MutableStateFlow<PluginInstallNote?>(null)
    val pluginInstallNote: StateFlow<PluginInstallNote?> = _pluginInstallNote.asStateFlow()

    fun dismissPluginInstallNote() {
        _pluginInstallNote.value = null
    }

    private val _terminalState = MutableStateFlow<TerminalUiState>(TerminalUiState.Idle)
    val terminalState: StateFlow<TerminalUiState> = _terminalState.asStateFlow()

    private val _session = MutableStateFlow<TerminalSession?>(null)
    val session: StateFlow<TerminalSession?> = _session.asStateFlow()

    val agentState: StateFlow<AgentState> = AgentEngine.state

    val sessions: StateFlow<List<AgentSession>> = agentState
        .map { it.sessions }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeSession: StateFlow<AgentSession?> = agentState
        .map { it.activeSession }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()

    private var installJob: Job? = null
    private var startJob: Job? = null
    private var harnessJob: Job? = null
    private var sessionToken = 0L

    val sharedDir: File = rootfsManager.sharedDir
    val rootfsPath: String = rootfsManager.rootfsDir.absolutePath

    init {
        removeLegacyChatStore()
        refreshEnvironment()
        AgentEngine.init(application)
        AgentEngine.ensureStarted(application)
    }

    fun prepareTerminal() {
        val current = _session.value
        if (current != null) return
        when (_terminalState.value) {
            is TerminalUiState.Starting, is TerminalUiState.Running -> return
            else -> Unit
        }

        refreshEnvironment()
        val env = _environment.value
        if (env.installing || env.deleting) {
            _terminalState.value = TerminalUiState.Failed("Kali 环境正在安装或删除，请稍候")
            return
        }
        if (rootfsManager.isInstalled()) {
            startTerminal()
        } else {
            _terminalState.value = TerminalUiState.NeedsSetup
        }
    }

    fun startTerminal(debugShell: Boolean = false) {
        if (startJob?.isActive == true) return
        val env = _environment.value
        if (!debugShell && (env.installing || env.deleting)) {
            _terminalState.value = TerminalUiState.Failed("Kali 环境正在安装或删除，请稍候")
            return
        }
        if (!debugShell && !rootfsManager.isInstalled()) {
            _terminalState.value = TerminalUiState.NeedsSetup
            return
        }

        closeSession()
        val token = ++sessionToken
        startJob = viewModelScope.launch {
            _terminalState.value = TerminalUiState.Starting(
                if (debugShell) "正在启动设备 Shell…" else "正在启动 Kali…"
            )
            var created: TerminalSession? = null
            try {
                val command: CommandSpec
                val engineLabel: String
                var note: String? = null

                if (debugShell) {
                    command = debugShellCommand()
                    engineLabel = "设备 Shell · 自检"
                } else {
                    _terminalState.value = TerminalUiState.Starting("正在准备 Kali 环境…")
                    val paths = withContext(Dispatchers.IO) { runtimeAssets.ensureInstalled() }
                    val resolv = withContext(Dispatchers.IO) {
                        rootfsManager.ensureRuntimeLayout()
                        NetworkDns.writeResolvConf(getApplication<Application>())
                    }

                    var suPath: String? = null
                    if (_rootMode.value) {
                        suPath = withContext(Dispatchers.IO) { rootShell.findSu() }
                        if (suPath == null) {
                            disableRootBecauseUnavailable()
                            note = "未检测到 su，已回退到 PRoot 模式"
                        } else {
                            _rootProbe.value = RootProbe.Available(suPath)
                        }
                    }

                    val engine = if (suPath != null) ChrootEngine(suPath) else ProotEngine()
                    engineLabel = engine.label
                    // PRoot guests inherit Android's app sandbox, which rejects rename(2);
                    // build the shim first so the shell and every tool in it can move files.
                    if (engine is ProotEngine) {
                        withContext(Dispatchers.IO) { harnessManager.ensureRenameShim() }
                    }
                    command = engine.buildCommand(
                        rootfs = rootfsManager.rootfsDir,
                        paths = paths,
                        resolvConf = resolv,
                        sharedDir = rootfsManager.sharedDir,
                        cacheDir = getApplication<Application>().cacheDir
                    )
                    _terminalState.value = TerminalUiState.Starting("正在启动 ${engine.label}…")
                }

                val newSession = TerminalSession(
                    command = command,
                    rows = 30,
                    cols = 100,
                    onExit = { code -> onSessionExit(token, code, engineLabel) }
                )
                created = newSession
                _session.value = newSession
                withContext(Dispatchers.IO) { newSession.start() }

                if (token == sessionToken) {
                    _terminalState.value = TerminalUiState.Running(engineLabel, note)
                }
            } catch (c: CancellationException) {
                created?.close()
                if (token == sessionToken) {
                    _session.value = null
                }
                throw c
            } catch (t: Throwable) {
                created?.close()
                if (token == sessionToken) {
                    _session.value = null
                    _terminalState.value = TerminalUiState.Failed(
                        t.message?.takeIf { it.isNotBlank() } ?: "无法启动终端"
                    )
                }
            }
        }
    }

    fun restartTerminal() {
        closeSession()
        _terminalState.value = TerminalUiState.Idle
        startTerminal()
    }

    fun closeSession() {
        sessionToken++
        val current = _session.value
        _session.value = null
        current?.close()
    }

    fun installRootfs(startAfter: Boolean, expectedSha256: String? = null) {
        if (_environment.value.installing || _environment.value.deleting) return
        if (_harness.value.busy) {
            _environment.update {
                it.copy(error = "Agent 核心正在安装或卸载，请稍候")
            }
            return
        }
        val spec = currentSpec()
        if (spec == null) {
            _environment.update {
                it.copy(error = "当前设备架构暂不支持内置 Kali 环境")
            }
            return
        }
        val hadEnvironment = rootfsManager.isInstalled()
        closeSession()
        // Replacing a live rootfs under dsh/bridge leaves them running against deleted
        // files. Stop them first; success below reconnects if dsh was preserved.
        if (hadEnvironment) {
            AgentEngine.stop()
            cancelHarnessWork()
        }
        if (!startAfter) {
            _terminalState.value = TerminalUiState.NeedsSetup
        }

        installJob?.cancel()
        installJob = viewModelScope.launch {
            _environment.update {
                it.copy(
                    installing = true,
                    progress = 0f,
                    status = "准备下载…",
                    error = null,
                    notice = null
                )
            }
            try {
                // Prefer the checksum Kali publishes right now; the pinned one is only a
                // fallback for when the (tiny) SHA256SUMS request cannot be made.
                val checksum = expectedSha256 ?: withContext(Dispatchers.IO) {
                    runCatching { rootfsUpdateCheck.latestChecksum(spec) }.getOrNull()
                } ?: spec.sha256
                rootfsInstaller.install(
                    spec = spec,
                    expectedSha256 = checksum,
                    onProgress = { downloaded, total ->
                        val fraction = if (total > 0) {
                            (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        _environment.update { it.copy(progress = fraction) }
                    },
                    onStatus = { status ->
                        _environment.update {
                            val postDownload = status.contains("解压") ||
                                status.contains("校验") || status.contains("安装")
                            it.copy(
                                status = status,
                                progress = if (postDownload) null else it.progress
                            )
                        }
                    }
                )
                refreshEnvironment()
                refreshHarness()
                _environment.update {
                    it.copy(
                        installing = false,
                        progress = null,
                        status = "Kali 环境已安装",
                        error = null,
                        checkingUpdate = false,
                        updateAvailable = false
                    )
                }
                if (File(rootfsManager.rootfsDir, "opt/kingkang/dsh/bin/dsh").isFile) {
                    AgentEngine.restart()
                } else {
                    // The environment exists now but dsh does not. Re-evaluate readiness so
                    // Home stops showing the stale "Kali 环境尚未安装" from before.
                    AgentEngine.ensureStarted(getApplication())
                }
                if (startAfter) {
                    startTerminal()
                }
            } catch (c: CancellationException) {
                _environment.update {
                    it.copy(
                        installing = false,
                        progress = null,
                        status = "已取消下载",
                        error = null
                    )
                }
                if (startAfter && _terminalState.value !is TerminalUiState.Running) {
                    _terminalState.value = TerminalUiState.NeedsSetup
                }
                // A cancelled download/verify usually leaves the old rootfs untouched;
                // bring dsh back instead of leaving the app disconnected.
                reconnectAgentIfHarnessPresent(hadEnvironment)
                throw c
            } catch (t: Throwable) {
                val message = t.message?.takeIf { it.isNotBlank() } ?: "安装失败"
                _environment.update {
                    it.copy(
                        installing = false,
                        progress = null,
                        status = null,
                        error = message
                    )
                }
                if (startAfter) {
                    _terminalState.value = TerminalUiState.Failed(message)
                }
                reconnectAgentIfHarnessPresent(hadEnvironment)
            }
        }
    }

    fun cancelInstall() {
        val job = installJob ?: return
        if (!job.isActive) return
        // Leave `installing` set until the job actually exits: extraction is not
        // interruptible, and claiming the install stopped while it keeps writing the
        // rootfs was misleading.
        _environment.update { it.copy(status = "正在取消…") }
        job.cancel()
    }

    /** Restarts dsh after a failed environment update when the runtime is still there. */
    private fun reconnectAgentIfHarnessPresent(hadEnvironment: Boolean) {
        if (!hadEnvironment) return
        if (File(rootfsManager.rootfsDir, "opt/kingkang/dsh/bin/dsh").isFile) {
            AgentEngine.restart()
        }
    }

    /**
     * Deleting a full rootfs walks tens of thousands of files. It runs on an IO thread and
     * reports progress so the settings screen never blocks on the main thread.
     */
    fun removeRootfs() {
        if (_environment.value.deleting || _environment.value.installing) return
        if (_harness.value.busy) {
            _environment.update {
                it.copy(error = "Agent 核心正在安装或卸载，请稍候")
            }
            return
        }
        installJob?.cancel()
        installJob = null
        closeSession()
        AgentEngine.stop()
        cancelHarnessWork()
        _harness.value = HarnessUi()
        _terminalState.value = TerminalUiState.NeedsSetup
        _environment.update {
            it.copy(
                deleting = true,
                installing = false,
                progress = null,
                status = "正在删除 Kali 环境…",
                error = null,
                checkingUpdate = false,
                updateAvailable = false,
                notice = null
            )
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    rootfsManager.delete { removed ->
                        _environment.update { state ->
                            state.copy(status = "正在删除 Kali 环境…（已删除 $removed 个文件）")
                        }
                    }
                }
                refreshEnvironment()
                _environment.update { it.copy(deleting = false, status = null) }
                // Sessions, plugins and the dsh version all lived inside the deleted image.
                AgentEngine.forgetHarness("Kali 环境已删除")
                emitUiMessage("Kali 环境已删除")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "rootfs delete failed", t)
                refreshEnvironment()
                _environment.update {
                    it.copy(
                        deleting = false,
                        status = null,
                        error = t.readableMessage("删除失败，请重试")
                    )
                }
            }
        }
    }

    /**
     * Compares the installed image checksum with the one Kali publishes now. When they
     * differ the newer image is downloaded and installed automatically.
     */
    fun checkRootfsUpdate() {
        val state = _environment.value
        if (state.installing || state.deleting || state.checkingUpdate) return
        if (!rootfsManager.isInstalled()) {
            installRootfs(startAfter = false)
            return
        }
        val spec = currentSpec() ?: return

        _environment.update { it.copy(checkingUpdate = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                val latest = withContext(Dispatchers.IO) {
                    rootfsUpdateCheck.latestChecksum(spec).lowercase()
                }
                val installed = rootfsManager.installedSha256()?.lowercase()
                    ?.takeIf { it.length == 64 } ?: spec.sha256.lowercase()
                if (latest != installed) {
                    _environment.update {
                        it.copy(checkingUpdate = false, updateAvailable = true, notice = null)
                    }
                    emitUiMessage("发现新版本，开始更新")
                    installRootfs(startAfter = false, expectedSha256 = latest)
                } else {
                    _environment.update {
                        it.copy(
                            checkingUpdate = false,
                            updateAvailable = false,
                            notice = "已是最新版本"
                        )
                    }
                    emitUiMessage("Kali 环境已是最新版本")
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _environment.update {
                    it.copy(
                        checkingUpdate = false,
                        error = t.readableMessage("检查更新失败")
                    )
                }
            }
        }
    }

    fun dismissEnvironmentError() {
        _environment.update { it.copy(error = null) }
    }

    /**
     * Reads the dsh / Node versions from inside Kali. This is a local-only check and runs
     * through PRoot even when the terminal uses chroot, so it never triggers a su prompt.
     */
    fun refreshHarness() {
        if (!rootfsManager.isInstalled()) {
            _harness.value = HarnessUi()
            return
        }
        if (_environment.value.installing || _environment.value.deleting) return
        if (_harness.value.busy || _harness.value.checkingUpdate) return
        if (harnessJob?.isActive == true) return

        harnessJob = viewModelScope.launch {
            try {
                val output = withContext(Dispatchers.IO) {
                    harnessManager.runAsset(HARNESS_STATUS_SCRIPT, HARNESS_STATUS_TIMEOUT)
                }
                applyHarnessOutput(output)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _harness.update {
                    it.copy(error = t.readableMessage("无法读取 dsh 状态"))
                }
            }
        }
    }

    fun installHarness() = startHarnessInstall(update = false)

    fun updateHarness() = startHarnessInstall(update = true)

    private fun startHarnessInstall(update: Boolean) {
        if (_harness.value.busy) return
        if (_environment.value.installing || _environment.value.deleting) {
            _harness.update { it.copy(error = "Kali 环境正在安装或删除，请稍候") }
            return
        }
        if (!rootfsManager.isInstalled()) {
            _harness.update { it.copy(error = "请先安装 Kali 环境") }
            return
        }

        cancelHarnessWork()
        harnessJob = viewModelScope.launch {
            _harness.update {
                it.copy(
                    busy = true,
                    updateAvailable = false,
                    error = null,
                    progress = null,
                    step = if (update) "正在更新 DeepSeek Harness…" else "正在准备 Node.js…"
                )
            }
            try {
                val output = withContext(Dispatchers.IO) {
                    harnessManager.ensureNode(
                        onStep = { step ->
                            _harness.update { state -> state.copy(step = step, progress = null) }
                        },
                        onProgress = { fraction ->
                            _harness.update { state -> state.copy(progress = fraction) }
                        }
                    )
                    harnessManager.runAsset(
                        HARNESS_INSTALL_SCRIPT,
                        HARNESS_INSTALL_TIMEOUT,
                        onLine = { line ->
                            val step = line.trim()
                                .takeIf { it.startsWith("KK_STEP=") }
                                ?.substringAfter('=')
                                ?.takeIf { it.isNotBlank() }
                            if (step != null) {
                                _harness.update { state ->
                                    state.copy(step = step, progress = null)
                                }
                            }
                        },
                        resetCancel = false,
                        extraEnv = harnessNpmEnv()
                    )
                }
                applyHarnessOutput(output)
                val result = _harness.value
                if (result.error == null && result.installed) {
                    emitUiMessage(
                        if (update) "DeepSeek Harness 已更新" else "DeepSeek Harness 已安装"
                    )
                    // The bridge lives in the guest; restart it against the newly installed dsh
                    // instead of leaving the user to reconnect by hand.
                    _harness.update { it.copy(step = "正在重新连接 Agent…", progress = null) }
                    AgentEngine.restart()
                    _harness.update { it.copy(step = "安装完成", progress = null) }
                }
            } catch (c: CancellationException) {
                _harness.update { it.copy(busy = false, step = null, progress = null, error = null) }
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "dsh install failed", t)
                _harness.update {
                    it.copy(
                        busy = false,
                        step = null,
                        progress = null,
                        error = t.readableMessage("安装失败")
                    )
                }
            } finally {
                _harness.update { it.copy(busy = false, progress = null) }
            }
        }
    }

    fun checkHarnessUpdate() {
        if (_harness.value.busy || _harness.value.checkingUpdate) return
        if (_environment.value.installing || _environment.value.deleting) {
            _harness.update { it.copy(error = "Kali 环境正在安装或删除，请稍候") }
            return
        }
        if (!rootfsManager.isInstalled()) {
            _harness.update { it.copy(error = "请先安装 Kali 环境") }
            return
        }
        if (!_harness.value.installed) {
            _harness.update { it.copy(error = "请先安装 dsh") }
            return
        }

        cancelHarnessWork()
        harnessJob = viewModelScope.launch {
            _harness.update { it.copy(checkingUpdate = true, error = null) }
            var updateTo: String? = null
            try {
                val output = withContext(Dispatchers.IO) {
                    harnessManager.runAsset(
                        HARNESS_CHECK_SCRIPT,
                        HARNESS_CHECK_TIMEOUT,
                        extraEnv = harnessNpmEnv()
                    )
                }
                applyHarnessOutput(output)
                val state = _harness.value
                if (state.error == null) {
                    if (state.updateAvailable) {
                        // A newer dsh is published: update it without a second tap.
                        updateTo = state.latestVersion
                        emitUiMessage("发现新版本 ${updateTo}，开始更新")
                    } else {
                        emitUiMessage("已是最新版本")
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _harness.update {
                    it.copy(checkingUpdate = false, error = t.readableMessage("检查更新失败"))
                }
            } finally {
                _harness.update { it.copy(checkingUpdate = false) }
            }
            if (updateTo != null) startHarnessInstall(update = true)
        }
    }

    /** Removes dsh, the Node.js runtime and the npm cache from Kali. */
    fun removeHarness() {
        if (_harness.value.busy) return
        if (_environment.value.installing || _environment.value.deleting) {
            _harness.update { it.copy(error = "Kali 环境正在安装或删除，请稍候") }
            return
        }
        if (!rootfsManager.isInstalled()) {
            _harness.value = HarnessUi()
            return
        }
        cancelHarnessWork()
        harnessJob = viewModelScope.launch {
            _harness.update {
                it.copy(busy = true, step = "正在卸载 Agent 核心…", progress = null, error = null)
            }
            try {
                // Stop dsh before touching its files: a running dsh can keep writing to the
                // npm cache, and the app must not keep reporting a live connection to a
                // runtime that is about to disappear.
                AgentEngine.stop()
                val removed = withContext(Dispatchers.IO) {
                    // The script clears the PATH symlinks; the tree itself is deleted from
                    // here because `rm` inside PRoot cannot see all of npm's cache files.
                    try {
                        harnessManager.runAsset(HARNESS_UNINSTALL_SCRIPT, HARNESS_UNINSTALL_TIMEOUT)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        Log.w(TAG, "uninstall script failed, deleting the tree directly", t)
                    }
                    rootfsManager.removeHarnessRuntime()
                }
                if (!removed) {
                    _harness.update {
                        it.copy(busy = false, step = null, error = "卸载失败，请重试")
                    }
                    // Nothing was actually removed; bring the bridge back rather than
                    // leaving the app disconnected after a failed uninstall.
                    AgentEngine.restart()
                } else {
                    AgentEngine.forgetHarness()
                    _harness.value = HarnessUi()
                    emitUiMessage("Agent 核心已卸载")
                }
            } catch (c: CancellationException) {
                _harness.update { it.copy(busy = false, step = null) }
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "dsh uninstall failed", t)
                _harness.update {
                    it.copy(busy = false, step = null, error = t.readableMessage("卸载失败"))
                }
                AgentEngine.restart()
            }
        }
    }

    fun cancelHarnessTask() {
        cancelHarnessWork()
        _harness.update {
            it.copy(
                busy = false,
                checkingUpdate = false,
                step = null,
                progress = null,
                error = null
            )
        }
        emitUiMessage("已取消")
        // A cancelled install or uninstall can leave the guest half-changed; resync.
        refreshHarness()
    }

    fun dismissHarnessError() {
        _harness.update { it.copy(error = null) }
    }

    private fun cancelHarnessWork() {
        harnessJob?.cancel()
        harnessJob = null
        harnessManager.cancel()
    }

    private fun applyHarnessOutput(output: String) {
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val values = lines
            .filter { it.startsWith("KK_") && it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=').trim() }

        val errorCode = values["KK_ERROR"]
        if (!errorCode.isNullOrBlank()) {
            val detail = lines.lastOrNull { !it.startsWith("KK_") && it.length <= 160 }
            _harness.update {
                it.copy(
                    error = harnessErrorText(errorCode) + if (detail != null) "\n$detail" else "",
                    checkingUpdate = false
                )
            }
            return
        }

        val installed = values["KK_INSTALLED"] == "1"
        val version = values["KK_VERSION"]?.takeIf { it.isNotBlank() && it != "unknown" }
        val node = values["KK_NODE"]?.takeIf { it.isNotBlank() }
        val latest = values["KK_LATEST"]?.takeIf { it.isNotBlank() }
        val installedKey = versionKey(version)
        val latestKey = versionKey(latest)
        val hasUpdate = installed && installedKey != null && latestKey != null &&
            installedKey != latestKey

        _harness.update { state ->
            state.copy(
                installed = installed,
                version = if (installed) version else null,
                nodeVersion = if (installed) node else null,
                latestVersion = if (installed) latest else null,
                updateAvailable = hasUpdate,
                checkingUpdate = false
            )
        }
    }

    private fun versionKey(raw: String?): String? =
        raw?.let { Regex("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?").find(it)?.value }

    private fun harnessErrorText(code: String): String = when (code) {
        "NODE_MISSING" -> "Node.js 未准备好，请重试安装"
        "NPM_MISSING" -> "Node.js 安装不完整，缺少 npm，请重试安装"
        "DSH_INSTALL_FAILED" -> "dsh 安装失败，请检查网络后重试"
        "DSH_BIN_MISSING" -> "npm 已完成，但没有找到 dsh 可执行文件"
        "DSH_RUN_FAILED" -> "dsh 已安装，但无法读取版本，请在终端中运行 dsh --version 检查"
        "CHECK_FAILED" -> "无法读取 dsh 的最新版本"
        "UNINSTALL_FAILED" -> "无法完全删除 Agent 核心的文件"
        else -> "操作失败（$code）"
    }

    private fun Throwable.readableMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback

    fun checkRoot() {
        if (_rootProbe.value is RootProbe.Checking) return
        _rootProbe.value = RootProbe.Checking
        viewModelScope.launch {
            val suPath = withContext(Dispatchers.IO) { rootShell.findSu() }
            if (suPath != null) {
                _rootProbe.value = RootProbe.Available(suPath)
                if (_rootMode.value) {
                    emitUiMessage("Root 已就绪，重新打开终端后使用 chroot")
                }
            } else {
                _rootProbe.value = RootProbe.Unavailable
                if (_rootMode.value) {
                    disableRootBecauseUnavailable()
                }
                emitUiMessage("未检测到可用的 su")
            }
        }
    }

    /** The first-run guide is offered once; any dismissal records it as seen. */
    fun shouldShowOnboarding(): Boolean = !settingsStore.hasCompletedOnboarding

    fun completeOnboarding() {
        settingsStore.hasCompletedOnboarding = true
    }

    /** Switches the app palette and mirrors the choice into dsh's ui-theme setting. */
    fun setThemeMode(mode: String) {
        val value = when (mode) {
            "light", "dark" -> mode
            else -> "system"
        }
        if (value == _themeMode.value) return
        settingsStore.themeMode = value
        _themeMode.value = value
        AgentEngine.setThemePreference(value)
    }

    /** Makes [id] the agent preset new conversations compose. */
    fun setAgentPreset(id: String) = AgentEngine.setAgentPreset(id)

    fun refreshAgentPresets() = AgentEngine.refreshAgentPresets()

    suspend fun inspectPlugin(spec: String): AgentPluginInspection = AgentEngine.inspectPlugin(spec)

    /** Installs one package into the dsh profile and enables it; pnpm can take minutes. */
    fun installPlugin(spec: String) {
        if (_pluginInstalling.value != null) return
        viewModelScope.launch {
            _pluginInstalling.value = spec
            _pluginInstallNote.value = null
            try {
                val install = AgentEngine.installPlugin(spec)
                val note = when {
                    install.error != null -> PluginInstallNote(
                        text = "未安装：${pluginErrorText(install.error)}",
                        failed = true
                    )
                    install.restartRequired -> PluginInstallNote("已安装 ${install.bundle ?: spec}，重启 Agent 后生效")
                    install.bundle != null -> PluginInstallNote("已安装 ${install.bundle}")
                    else -> PluginInstallNote("已安装 $spec")
                }
                _pluginInstallNote.value = note
                emitUiMessage(note.text)
            } catch (t: Throwable) {
                val text = "安装插件失败：${t.message ?: "未知错误"}"
                _pluginInstallNote.value = PluginInstallNote(text, failed = true)
                emitUiMessage(text)
            } finally {
                _pluginInstalling.value = null
            }
        }
    }

    /**
     * Moves one installed plugin to another exact version. dsh loads plugin code only at
     * startup, so an idle agent restarts here; a running turn is never cut off for it.
     */
    fun updatePlugin(name: String, version: String) {
        if (_pluginInstalling.value != null) return
        viewModelScope.launch {
            _pluginInstalling.value = "$name@$version"
            _pluginInstallNote.value = null
            try {
                val update = AgentEngine.updatePlugin(name, version)
                val bundle = update.bundle ?: name
                val installed = update.version ?: version
                val busy = AgentEngine.state.value.running
                val note = if (busy) {
                    PluginInstallNote("已更新 $bundle 至 $installed，当前任务结束后重启 Agent 生效")
                } else {
                    PluginInstallNote("已更新 $bundle 至 $installed，正在重启 Agent")
                }
                _pluginInstallNote.value = note
                emitUiMessage(note.text)
                if (!busy) AgentEngine.restart()
            } catch (t: Throwable) {
                val text = "更新插件失败：${t.message ?: "未知错误"}"
                _pluginInstallNote.value = PluginInstallNote(text, failed = true)
                emitUiMessage(text)
            } finally {
                _pluginInstalling.value = null
            }
        }
    }

    fun setRootMode(enabled: Boolean) {
        if (!enabled) {
            settingsStore.rootMode = false
            _rootMode.value = false
            return
        }

        val probe = _rootProbe.value
        if (probe is RootProbe.Available) {
            settingsStore.rootMode = true
            _rootMode.value = true
            emitUiMessage("已启用 Root 模式，重新打开终端后生效")
            return
        }

        _rootProbe.value = RootProbe.Checking
        viewModelScope.launch {
            val suPath = withContext(Dispatchers.IO) { rootShell.findSu() }
            if (suPath != null) {
                _rootProbe.value = RootProbe.Available(suPath)
                settingsStore.rootMode = true
                _rootMode.value = true
                emitUiMessage("已启用 Root 模式，重新打开终端后生效")
            } else {
                _rootProbe.value = RootProbe.Unavailable
                settingsStore.rootMode = false
                _rootMode.value = false
                emitUiMessage("未检测到 su，无法启用 Root 模式")
            }
        }
    }

    fun sendChat(
        text: String,
        images: List<AgentDraftImage> = emptyList(),
        files: List<AgentDraftFile> = emptyList()
    ) = AgentEngine.prompt(text, images = images, files = files)

    fun steerChat(
        text: String,
        images: List<AgentDraftImage> = emptyList(),
        files: List<AgentDraftFile> = emptyList()
    ) = AgentEngine.prompt(text, mode = "steer", images = images, files = files)

    fun refreshAgentCatalogs() = AgentEngine.refreshCatalogs()

    fun loadAgentImage(sessionId: String, image: AgentImage) =
        AgentEngine.loadImage(sessionId, image)

    fun newChat() = AgentEngine.newSession()

    fun newChatInWorkspace(workspaceId: String) = AgentEngine.newSession(workspaceId)

    fun createWorkspace(path: String) = AgentEngine.createWorkspace(path)

    fun refreshWorkspaces() = AgentEngine.refreshWorkspaces()

    fun refreshPlugins() = AgentEngine.refreshPlugins()

    /**
     * Changes the npm source used both by Kali-side dsh installs/updates and by plugin
     * installs. Stored app-side first so the value is available before dsh exists; the
     * bridge applies it to the dsh profile whenever the agent connects.
     */
    fun setNpmRegistry(registry: String) {
        val value = registry.trim()
        if (value.isEmpty() ||
            (!value.startsWith("http://") && !value.startsWith("https://"))
        ) {
            emitUiMessage("NPM 源必须是 http(s) 地址")
            return
        }
        if (value == _npmRegistry.value) return
        settingsStore.npmRegistry = value
        _npmRegistry.value = value
        if (AgentEngine.state.value.connection == AgentConnection.Ready) {
            AgentEngine.setPluginRegistry(value)
        }
    }

    /**
     * Plugin bundles are enabled per dsh profile, not per chat, so a switch here changes
     * what every session started afterwards loads.
     */
    fun setPluginEnabled(name: String, enabled: Boolean) {
        if (_pluginChanging.value != null) return
        viewModelScope.launch {
            _pluginChanging.value = name
            try {
                val change = AgentEngine.setPluginEnabled(name, enabled)
                emitUiMessage(
                    when {
                        change.error != null -> "插件未切换：${pluginErrorText(change.error)}"
                        change.restartRequired -> "已保存，重启 Agent 后生效"
                        !change.changed -> "插件状态没有变化"
                        enabled -> "已启用 $name"
                        else -> "已停用 $name"
                    }
                )
            } catch (t: Throwable) {
                emitUiMessage("切换插件失败：${t.message ?: "未知错误"}")
            } finally {
                _pluginChanging.value = null
            }
        }
    }

    private fun pluginErrorText(raw: String): String = when {
        raw.startsWith("management-required") -> "这个插件由 dsh 管理，不能停用"
        raw.startsWith("incompatible-version") -> "插件与当前 dsh 版本不兼容"
        raw.startsWith("not-bundle") -> "插件内容不完整，需要重新安装"
        raw.startsWith("already-installed") -> "插件已经安装"
        raw.startsWith("invalid-spec") -> "插件名称格式不正确"
        raw.contains("build-blocked") || raw.contains("pendingBuilds") ->
            "插件包含需要批准的安装脚本，请到 DeepSeek Harness 中确认"
        raw.contains("pnpm-missing") -> "Kali 中缺少 pnpm，无法安装插件"
        raw.contains("not-found") -> "没有找到这个插件，请检查名称"
        raw.contains("network") || raw.contains("timeout") -> "网络超时，请重试"
        else -> raw
    }

    /** dsh web URL with its launch token; fetched on demand for the Harness WebView. */
    suspend fun harnessUrl(): String? = AgentEngine.webUrl()

    fun openSubagent(parentId: String, subagent: AgentSubagent) =
        AgentEngine.openSubagent(parentId, subagent)

    fun closeSubagent() = AgentEngine.closeSubagent()

    fun refreshSubagents() = AgentEngine.refreshSubagents()

    fun selectChat(id: String) = AgentEngine.openSession(id)

    fun archiveChat(id: String) = AgentEngine.archiveSession(id)

    fun unarchiveChat(id: String) = AgentEngine.unarchiveSession(id)

    fun setChatPinned(id: String, pinned: Boolean) = AgentEngine.setSessionPinned(id, pinned)

    fun renameChat(id: String, title: String) = AgentEngine.renameSession(id, title)

    fun forkChat(id: String) = AgentEngine.forkSession(id)

    fun restartAgent() = AgentEngine.restart()

    fun stopAgent() = AgentEngine.stop()

    fun cancelAgentTurn() = AgentEngine.cancel()

    fun editQueuedMessage(id: String, text: String) = AgentEngine.updateQueue(id, "edit", text)

    fun steerQueuedMessage(id: String) = AgentEngine.updateQueue(id, "steer")

    fun removeQueuedMessage(id: String) = AgentEngine.updateQueue(id, "remove")

    fun loadOlder() = AgentEngine.loadOlder()

    fun respondApproval(approval: AgentApproval, allow: Boolean) =
        AgentEngine.respondApproval(approval, allow)

    fun answerQuestion(question: AgentQuestion, answers: List<AgentAnswer>) =
        AgentEngine.answerQuestion(question, answers)

    fun selectModel(provider: String, model: String, effort: String? = null) =
        AgentEngine.selectModel(provider, model, effort)

    fun clearAgentError() = AgentEngine.clearTransientError()

    fun refreshAgentCredentials() = AgentEngine.refreshCredentials()

    fun setAgentCredential(ref: String, value: String) = AgentEngine.setCredential(ref, value)

    fun removeAgentCredential(ref: String) = AgentEngine.removeCredential(ref)

    fun selectAgentPermission(value: String) = AgentEngine.selectSessionPermission(value)

    fun setAgentDefaultPermission(value: String) = AgentEngine.setDefaultPermission(value)

    fun refreshAgentPermissions() = AgentEngine.refreshPermissions()

    fun notify(message: String) = emitUiMessage(message)

    private fun removeLegacyChatStore() {
        // The old local chat list was a placeholder with no AI connected. dsh now owns
        // sessions and history; the legacy file is intentionally discarded, no migration.
        val filesDir = getApplication<Application>().filesDir
        runCatching { File(filesDir, "chats.json").delete() }
        runCatching { File(filesDir, "chats.json.tmp").delete() }
    }

    private fun refreshEnvironment() {
        val spec = currentSpec()
        _environment.update {
            it.copy(
                installed = rootfsManager.isInstalled(),
                version = rootfsManager.versionLabel(),
                label = spec?.label,
                approxBytes = spec?.approxBytes,
                error = null
            )
        }
    }

    private fun currentSpec(): RootfsSpec? =
        RootfsCatalog.forAbi(RootfsCatalog.currentAbiName())

    private fun onSessionExit(token: Long, code: Int, engineLabel: String) {
        if (token != sessionToken) return
        _terminalState.value = TerminalUiState.Exited(code, engineLabel)
    }

    private fun disableRootBecauseUnavailable() {
        settingsStore.rootMode = false
        _rootMode.value = false
    }

    private fun debugShellCommand(): CommandSpec {
        val home = File(getApplication<Application>().filesDir, "shared").apply { mkdirs() }
        return CommandSpec(
            executable = "/system/bin/sh",
            argv = listOf("sh", "-l"),
            env = mapOf(
                "HOME" to home.absolutePath,
                "TERM" to "xterm-256color",
                "PATH" to "/sbin:/system/sbin:/system/bin:/system/xbin:/usr/bin:/usr/sbin"
            ),
            cwd = home.absolutePath
        )
    }

    private fun emitUiMessage(message: String) {
        _uiMessages.tryEmit(message)
    }

    /** The npm source handed to Kali maintenance scripts; blank means the app default. */
    private fun harnessNpmEnv(): Map<String, String> =
        mapOf("KINGKANG_NPM_REGISTRY" to _npmRegistry.value.ifBlank { NPM_REGISTRY_DEFAULT })

    override fun onCleared() {
        installJob?.cancel()
        startJob?.cancel()
        harnessJob?.cancel()
        harnessManager.cancel()
        closeSession()
        super.onCleared()
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val HARNESS_STATUS_SCRIPT = "harness/status.sh"
        const val HARNESS_CHECK_SCRIPT = "harness/check-update.sh"
        const val HARNESS_INSTALL_SCRIPT = "harness/install.sh"
        const val HARNESS_UNINSTALL_SCRIPT = "harness/uninstall.sh"
        const val HARNESS_STATUS_TIMEOUT = 180_000L
        const val HARNESS_CHECK_TIMEOUT = 600_000L
        const val HARNESS_INSTALL_TIMEOUT = 90 * 60_000L
        const val HARNESS_UNINSTALL_TIMEOUT = 5 * 60_000L
        const val NPM_REGISTRY_DEFAULT = "https://registry.npmmirror.com/"
    }
}
