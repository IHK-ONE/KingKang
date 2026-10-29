package com.kingkang.app.terminal

import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import com.kingkang.app.runtime.CommandSpec
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class TerminalSession(
    private val command: CommandSpec,
    private val rows: Int,
    private val cols: Int,
    private val onExit: (Int) -> Unit
) {
    private var fd: Int = -1
    private var pid: Int = -1
    private var pfd: ParcelFileDescriptor? = null
    private var input: FileInputStream? = null
    private var outStream: FileOutputStream? = null
    private var reader: Thread? = null

    @Volatile
    private var closing = false

    @Volatile
    private var lastRows: Int = rows

    @Volatile
    private var lastCols: Int = cols

    @Volatile
    private var sizeReported = false

    private val _output = MutableSharedFlow<ByteArray>(
        replay = 64,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val output: SharedFlow<ByteArray> = _output.asSharedFlow()

    val isRunning: Boolean
        get() = fd >= 0 && pid > 0

    fun start() {
        check(fd < 0) { "session already started" }
        val result = try {
            PtyBridge.create(
                command.executable,
                command.argv.toTypedArray(),
                command.envArray(),
                command.cwd,
                rows,
                cols
            )
        } catch (t: UnsatisfiedLinkError) {
            throw IOException("PTY 原生库加载失败：${t.message}", t)
        }

        if (result.size < 2 || result[0] < 0) {
            val errno = if (result.size > 1) -result[0].toInt() else -1
            val message = runCatching { Os.strerror(errno) }.getOrDefault("errno $errno")
            throw IOException("无法创建 PTY：$message")
        }

        fd = result[0].toInt()
        pid = result[1].toInt()
        pfd = ParcelFileDescriptor.adoptFd(fd)
        input = FileInputStream(pfd!!.fileDescriptor)
        outStream = FileOutputStream(pfd!!.fileDescriptor)

        reader = Thread({ readLoop() }, "kingkang-pty-$pid").apply {
            isDaemon = true
            start()
        }

        // PRoot's startup can overwrite the guest's terminal width with its own default
        // after the first xterm fit has already been applied. Re-assert the size a few
        // times once the runtime has settled; later resizes are harmless.
        Thread({ reassertSize() }, "kingkang-pty-size-$pid").apply {
            isDaemon = true
            start()
        }
    }

    private fun reassertSize() {
        val delays = longArrayOf(1_500L, 2_000L, 2_500L)
        for (delay in delays) {
            try {
                Thread.sleep(delay)
            } catch (_: InterruptedException) {
                return
            }
            if (closing) return
            if (sizeReported) resize(lastRows, lastCols)
        }
    }

    fun write(bytes: ByteArray) {
        if (bytes.isEmpty() || fd < 0) return
        try {
            outStream?.write(bytes)
            outStream?.flush()
        } catch (t: Throwable) {
            Log.w(TAG, "PTY write failed", t)
        }
    }

    fun resize(rows: Int, cols: Int) {
        if (fd < 0 || rows <= 0 || cols <= 0) return
        lastRows = rows
        lastCols = cols
        sizeReported = true
        runCatching { PtyBridge.resize(fd, rows, cols) }
            .onFailure { Log.w(TAG, "PTY resize failed", it) }
    }

    fun close() {
        if (closing) return
        closing = true
        val currentPid = pid
        pid = -1
        fd = -1

        if (currentPid > 0) {
            runCatching { PtyBridge.kill(currentPid, 15) }
            runCatching { PtyBridge.kill(currentPid, 9) }
            // Reap the child so repeated terminal restarts do not accumulate zombies.
            Thread({ runCatching { PtyBridge.waitFor(currentPid) } }, "kingkang-reaper")
                .apply { isDaemon = true }
                .start()
        }
        runCatching { input?.close() }
        runCatching { outStream?.close() }
        runCatching { pfd?.close() }
        reader?.interrupt()
    }

    private fun readLoop() {
        val buffer = ByteArray(8192)
        try {
            val stream = input ?: return
            while (!Thread.currentThread().isInterrupted && !closing) {
                val n = try {
                    stream.read(buffer)
                } catch (t: Throwable) {
                    if (!closing) Log.d(TAG, "PTY read ended: ${t.message}")
                    break
                }
                if (n < 0) break
                if (n == 0) continue
                _output.tryEmit(buffer.copyOf(n))
            }
        } finally {
            if (!closing) {
                val exitPid = pid
                val status = runCatching { PtyBridge.waitFor(exitPid) }.getOrDefault(-1)
                // The child has been reaped; marking the session dead prevents close()
                // from signalling a PID that the system may have already reused.
                pid = -1
                fd = -1
                onExit(status)
            }
        }
    }

    private companion object {
        const val TAG = "TerminalSession"
    }
}
