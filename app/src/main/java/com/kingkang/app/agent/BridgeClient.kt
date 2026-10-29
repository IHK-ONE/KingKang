package com.kingkang.app.agent

import android.content.Context
import android.util.Log
import com.kingkang.app.runtime.NetworkDns
import com.kingkang.app.runtime.ProotEngine
import com.kingkang.app.runtime.RootfsManager
import com.kingkang.app.runtime.RuntimeAssets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AgentBridgeException(
    val code: String,
    override val message: String,
    val details: JSONObject? = null
) : IOException(message)

/**
 * Owns the long-lived Node bridge process inside Kali.
 *
 * stdout is protocol only; stderr is forwarded as diagnostic log events. Each request is
 * correlated by id, so prompt/history calls can run concurrently with the event stream.
 */
class BridgeClient(private val context: Context) {

    private val rootfs = RootfsManager(context)
    private val runtimeAssets = RuntimeAssets(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messages = Channel<JSONObject>(Channel.UNLIMITED)
    val events: Flow<JSONObject> = messages.receiveAsFlow()

    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val writeLock = Any()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    var alive: Boolean = false
        private set

    private var stdoutJob: Job? = null
    private var stderrJob: Job? = null

    val dshInstalled: Boolean
        get() = File(rootfs.rootfsDir, "opt/kingkang/node/bin/node").isFile &&
            File(rootfs.rootfsDir, "opt/kingkang/dsh/bin/dsh").isFile

    suspend fun start(extraEnv: Map<String, String> = emptyMap()) {
        if (alive) return
        if (!rootfs.isInstalled()) {
            throw AgentBridgeException("startup", "Kali 环境尚未安装")
        }
        if (!dshInstalled) {
            throw AgentBridgeException("startup", "Agent 核心尚未安装")
        }

        withContext(Dispatchers.IO) {
            rootfs.ensureRuntimeLayout()
            installBridgeAsset()
            val paths = runtimeAssets.ensureInstalled()
            val resolv = NetworkDns.writeResolvConf(context)
            val command = ProotEngine().buildExec(
                rootfs = rootfs.rootfsDir,
                paths = paths,
                resolvConf = resolv,
                sharedDir = rootfs.sharedDir,
                cacheDir = context.cacheDir,
                executable = NODE_BIN,
                args = listOf(BRIDGE_PATH),
                extraEnv = mapOf(
                    "KK_NODE_BIN" to NODE_BIN,
                    "KK_DSH_BIN" to DSH_BIN,
                    "KK_WORKDIR" to "/root"
                ) + extraEnv
            )

            val builder = ProcessBuilder(listOf(command.executable) + command.argv.drop(1))
                .redirectErrorStream(false)
            command.cwd?.let { builder.directory(File(it)) }
            builder.environment().putAll(command.env)
            val started = builder.start()
            process = started
            writer = started.outputStream.bufferedWriter(Charsets.UTF_8)
            alive = true
            Log.i(TAG, "bridge process started")

            stdoutJob = scope.launch { readStdout(started) }
            stderrJob = scope.launch { readStderr(started) }
        }
    }

    private fun installBridgeAsset() {
        val target = File(rootfs.rootfsDir, BRIDGE_PATH.removePrefix("/"))
        target.parentFile?.mkdirs()
        context.assets.open("agent/bridge.mjs").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private suspend fun readStdout(target: Process) {
        try {
            target.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val json = runCatching { JSONObject(line) }.getOrNull()
                    if (json == null) {
                        Log.w(TAG, "bridge emitted non-JSON: ${line.take(300)}")
                        return@forEach
                    }
                    val id = json.optString("id").takeIf { it.isNotBlank() }
                    if (id != null && !json.has("type")) {
                        pending.remove(id)?.complete(json)
                    } else {
                        messages.trySend(json)
                    }
                }
            }
        } catch (t: Throwable) {
            if (alive) Log.w(TAG, "bridge stdout stopped", t)
        } finally {
            onProcessEnded(target)
        }
    }

    private suspend fun readStderr(target: Process) {
        try {
            target.errorStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        // dsh prints its one-time launch token in this URL. It is ephemeral and
                        // only useful inside the bridge, so keep it out of the user-visible log.
                        val safe = line.replace(TOKEN_PATTERN, "$1***").take(MAX_LOG_LINE)
                        Log.d(TAG, "stderr: $safe")
                        messages.trySend(
                            JSONObject()
                                .put("type", "log")
                                .put("level", "stderr")
                                .put("message", safe)
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "bridge stderr stopped", t)
        }
    }

    private fun onProcessEnded(target: Process) {
        if (!alive) return
        alive = false
        writer = null
        val error = AgentBridgeException(
            "transport",
            "Agent 桥接已退出（code=${runCatching { target.exitValue() }.getOrDefault(-1)}）"
        )
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        messages.trySend(
            JSONObject()
                .put("type", "bridge.exit")
                .put("message", error.message)
        )
    }

    suspend fun call(
        method: String,
        params: JSONObject = JSONObject(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Any? {
        if (!alive) {
            throw AgentBridgeException("transport", "Agent 尚未连接")
        }
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        try {
            val line = JSONObject()
                .put("v", 1)
                .put("id", id)
                .put("method", method)
                .put("params", params)
                .toString()
            synchronized(writeLock) {
                val out = writer ?: throw AgentBridgeException("transport", "Agent 桥接未就绪")
                out.write(line)
                out.newLine()
                out.flush()
            }
            val response = withTimeout(timeoutMs) { deferred.await() }
            if (!response.optBoolean("ok", false)) {
                val error = response.optJSONObject("error")
                throw AgentBridgeException(
                    error?.optString("code")?.takeIf { it.isNotBlank() } ?: "internal",
                    error?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "Agent 请求失败",
                    error?.optJSONObject("details")
                )
            }
            return response.opt("result")
        } finally {
            pending.remove(id)
        }
    }

    suspend fun callObject(
        method: String,
        params: JSONObject = JSONObject(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): JSONObject = call(method, params, timeoutMs) as? JSONObject
        ?: throw AgentBridgeException("internal", "$method 返回了意外的数据")

    fun stop() {
        val target = process ?: return
        runCatching {
            val id = UUID.randomUUID().toString()
            val line = JSONObject()
                .put("v", 1)
                .put("id", id)
                .put("method", "shutdown")
                .put("params", JSONObject())
                .toString()
            synchronized(writeLock) {
                writer?.write(line)
                writer?.newLine()
                writer?.flush()
            }
        }
        scope.launch {
            delay(500)
            runCatching { target.destroy() }
            delay(700)
            if (target.isAlive) runCatching { target.destroyForcibly() }
            process = null
            alive = false
        }
    }

    fun destroyNow() {
        alive = false
        runCatching { process?.destroyForcibly() }
        process = null
        writer = null
        pending.values.forEach {
            it.completeExceptionally(AgentBridgeException("transport", "Agent 已停止"))
        }
        pending.clear()
    }

    private companion object {
        const val TAG = "AgentBridge"
        const val NODE_BIN = "/opt/kingkang/node/bin/node"
        const val DSH_BIN = "/opt/kingkang/dsh/bin/dsh"
        const val BRIDGE_PATH = "/opt/kingkang/agent/bridge.mjs"
        const val DEFAULT_TIMEOUT_MS = 45_000L
        const val MAX_LOG_LINE = 2_000
        val TOKEN_PATTERN = Regex("([?&]token=)[^&\\s]+")
    }
}
