package com.kingkang.app.terminal

/**
 * Thin JNI surface over the POSIX PTY calls. The native side forks and execs the runtime
 * (proot or su) and returns {masterFd, pid}. A negative first element carries -errno.
 */
object PtyBridge {
    init {
        System.loadLibrary("kingkang_pty")
    }

    external fun create(
        executable: String,
        argv: Array<String>,
        env: Array<String>,
        cwd: String?,
        rows: Int,
        cols: Int
    ): LongArray

    external fun resize(fd: Int, rows: Int, cols: Int): Int
    external fun kill(pid: Int, signal: Int): Int
    external fun waitFor(pid: Int): Int
}
