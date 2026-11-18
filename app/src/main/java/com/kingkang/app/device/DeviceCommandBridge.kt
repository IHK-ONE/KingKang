package com.kingkang.app.device

import android.content.Context
import android.os.FileObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Serves the `kk-adb` helper: the guest writes a request into the shared directory, this
 * loop runs it through Shizuku's shell user, and the reply is written back atomically.
 * No sockets or ports are opened outside the app's private storage.
 */
object DeviceCommandBridge {

    private const val DIR_NAME = ".kk-device"
    private const val MAX_COMMAND_CHARS = 16_000
    private const val MAX_REPLY_CHARS = 200_000

    private var started = false
    private var observer: FileObserver? = null
    private val pollLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            val dir = File(app.filesDir, "shared/$DIR_NAME")
            runCatching { dir.mkdirs() }
            // Wake immediately when the guest writes a request; the slow loop below is
            // only a fallback for a missed event or a file created before watching.
            runCatching {
                @Suppress("DEPRECATION")
                observer = object : FileObserver(
                    dir.absolutePath,
                    CREATE or MOVED_TO
                ) {
                    override fun onEvent(event: Int, path: String?) {
                        scope.launch {
                            synchronized(pollLock) { runCatching { poll(dir, app) } }
                        }
                    }
                }.also { it.startWatching() }
            }
            while (isActive) {
                runCatching { synchronized(pollLock) { poll(dir, app) } }
                delay(2_000)
            }
        }
    }

    private fun poll(dir: File, context: Context) {
        // `req-*.tmp` is the helper's half-written file; treating it as a request would
        // run the same command twice (once as .tmp, once after the rename).
        val requests = dir.listFiles { file ->
            file.isFile && file.name.startsWith("req-") && !file.name.endsWith(".tmp")
        }?.sortedBy { it.lastModified() } ?: return
        if (requests.isEmpty()) return
        for (request in requests.take(2)) {
            val id = request.name.removePrefix("req-")
            val command = runCatching { request.readText().take(MAX_COMMAND_CHARS) }
                .getOrDefault("")
            // An older helper creates the request before writing into it. A blank file
            // younger than a second is still being filled, so leave it for the next poll
            // instead of answering "missing command" and deleting the request.
            if (command.isBlank() &&
                System.currentTimeMillis() - request.lastModified() < 1_000
            ) {
                continue
            }
            val result = File(dir, "res-$id")
            if (result.exists()) {
                // A previous poll already answered this request; never run it again.
                runCatching { request.delete() }
                continue
            }
            val reply = try {
                when {
                    !ShizukuManager.state.value.authorized ->
                        "kk-adb: Shizuku 未授权。请在 KingKang 设置 → 设备能力中授权。"
                    command.isBlank() -> "kk-adb: 缺少命令"
                    else -> ShizukuManager.exec(command.trim())
                }
            } catch (t: Throwable) {
                "kk-adb: ${t.message ?: "执行失败"}"
            }
            // The audit line is written before the reply so a very fast script cannot
            // observe a result that never appeared in the log.
            DeviceAudit.record(context, command.trim(), reply)
            if (!result.exists()) {
                val tmp = File(dir, "res-$id.tmp")
                runCatching {
                    tmp.writeText(reply.take(MAX_REPLY_CHARS))
                    tmp.renameTo(result)
                }
            }
            runCatching { request.delete() }
        }
        // Requests left behind by a killed app or a closed script are not useful later.
        val staleBefore = System.currentTimeMillis() - 60L * 60_000
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < staleBefore) runCatching { file.delete() }
        }
    }
}
