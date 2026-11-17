package com.kingkang.app.device

import android.os.Process
import java.util.concurrent.TimeUnit

/**
 * The Shizuku user-service implementation. Shizuku instantiates this class inside its own
 * shell-uid process, so the commands it runs carry exactly the privileges of `adb shell`
 * — not root, and not the app sandbox.
 */
class DeviceShellService : IDeviceShell.Stub() {

    override fun destroy() {
        Process.killProcess(Process.myPid())
    }

    override fun exec(command: String, timeoutMs: Int): String = run(command, timeoutMs)

    private fun run(command: String, timeoutMs: Int): String {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return "kk-adb: 缺少命令"
        if (trimmed.length > MAX_COMMAND_CHARS) return "kk-adb: 命令过长"
        val timeout = timeoutMs.coerceIn(1_000, MAX_TIMEOUT_MS)
        val process = ProcessBuilder("/system/bin/sh", "-c", trimmed)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        var truncated = false
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) {
                            if (output.length + line.length + 1 <= MAX_OUTPUT_CHARS) {
                                output.append(line).append('\n')
                            } else {
                                truncated = true
                            }
                        }
                    }
                }
            }
        }
        reader.start()
        val finished = runCatching {
            process.waitFor(timeout.toLong(), TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        if (!finished) {
            process.destroyForcibly()
            reader.join(1_000)
            val partial = synchronized(output) { output.toString().trimEnd() }
            return buildString {
                append("kk-adb: 命令超过 ${timeout / 1000} 秒，已停止")
                if (partial.isNotBlank()) append('\n').append(partial)
            }
        }
        reader.join(1_000)
        val text = synchronized(output) { output.toString().trimEnd() }
        val suffix = if (truncated) {
            "\n[kk-adb: 输出已截断，只保留前 $MAX_OUTPUT_CHARS 个字符]"
        } else {
            ""
        }
        return (text.ifBlank { "（命令完成，无输出）" } + suffix)
    }

    private companion object {
        const val MAX_COMMAND_CHARS = 16_000
        const val MAX_OUTPUT_CHARS = 200_000
        const val MAX_TIMEOUT_MS = 120_000
    }
}
