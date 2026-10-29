package com.kingkang.app.runtime

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File

data class RuntimePaths(
    val dir: File,
    val proot: File,
    val loader: File,
    val loader32: File
)

/**
 * Copies the proot runtime out of the APK into app-private storage. The executable bit is
 * set explicitly because assets do not carry Unix permissions.
 */
class RuntimeAssets(private val context: Context) {

    fun ensureInstalled(): RuntimePaths {
        val abi = RootfsCatalog.currentAbiName()
            ?: error("不支持的 CPU 架构：${android.os.Build.SUPPORTED_ABIS.joinToString()}")

        val dir = File(context.filesDir, "runtime/$abi").apply { mkdirs() }
        val proot = File(dir, "proot")
        val loader = File(dir, "loader")
        val loader32 = File(dir, "loader32")

        copyAsset("runtime/$abi/proot", proot)
        copyAsset("runtime/$abi/loader", loader)
        copyAsset("runtime/$abi/loader32", loader32)
        copyAsset("runtime/$abi/libtalloc.so.2", File(dir, "libtalloc.so.2"))
        copyAsset("runtime/$abi/libandroid-shmem.so", File(dir, "libandroid-shmem.so"))

        listOf(proot, loader, loader32).forEach { makeExecutable(it) }
        return RuntimePaths(dir, proot, loader, loader32)
    }

    private fun copyAsset(assetName: String, target: File) {
        if (target.isFile && target.length() > 0L) return
        target.parentFile?.mkdirs()
        context.assets.open(assetName).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        Log.d(TAG, "copied $assetName -> ${target.absolutePath} (${target.length()} bytes)")
    }

    private fun makeExecutable(file: File) {
        runCatching { Os.chmod(file.absolutePath, 0b111000000) } // 0700
            .onFailure { file.setExecutable(true, false) }
    }

    private companion object {
        const val TAG = "RuntimeAssets"
    }
}
