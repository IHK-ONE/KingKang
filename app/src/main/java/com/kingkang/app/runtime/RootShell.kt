package com.kingkang.app.runtime

import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Probes for a working su binary. This never tries to root the device; it only checks
 * whether the user has already granted root to KingKang.
 */
class RootShell {
    @Volatile
    private var cachedSu: String? = null

    fun findSu(): String? {
        cachedSu?.let { return it }
        for (candidate in CANDIDATES) {
            if (probe(candidate)) {
                cachedSu = candidate
                return candidate
            }
        }
        return null
    }

    fun isAvailable(): Boolean = findSu() != null

    fun clearCache() {
        cachedSu = null
    }

    private fun probe(path: String): Boolean {
        return try {
            val process = ProcessBuilder(path, "-c", "id")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return false
            }
            val output = process.inputStream.bufferedReader().readText()
            output.contains("uid=0")
        } catch (t: Throwable) {
            Log.d(TAG, "su probe failed for $path: ${t.message}")
            false
        }
    }

    private companion object {
        const val TAG = "RootShell"
        val CANDIDATES = listOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su")
    }
}
