package com.kingkang.app.runtime

import android.content.Context
import android.util.Log
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Owns the on-device Kali rootfs. The archive is extracted with Android's toybox tar,
 * which supports xz and preserves symlinks and file modes.
 */
class RootfsManager(private val context: Context) {

    val rootfsDir: File = File(context.filesDir, "rootfs/kali")
    val sharedDir: File = File(context.filesDir, "shared").apply { mkdirs() }
    private val baseDir: File = File(context.filesDir, "rootfs")

    fun isInstalled(): Boolean =
        File(rootfsDir, "etc/os-release").isFile && File(rootfsDir, "bin/bash").isFile

    fun versionLabel(): String? {
        val osRelease = File(rootfsDir, "etc/os-release")
        if (!osRelease.isFile) return null
        return runCatching {
            osRelease.readLines()
                .firstOrNull { it.startsWith("PRETTY_NAME=") }
                ?.substringAfter('=')
                ?.trim('"')
        }.getOrNull()
    }

    /** Checksum recorded when the installed image was unpacked, if it was recorded. */
    fun installedSha256(): String? = markerValue("sha256")

    private fun markerValue(key: String): String? {
        val marker = File(rootfsDir, MARKER)
        if (!marker.isFile) return null
        return runCatching {
            marker.readLines()
                .firstOrNull { it.startsWith("$key=") }
                ?.substringAfter('=')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun installArchive(
        archive: File,
        expectedSha256: String?,
        onStatus: (String) -> Unit = {}
    ) {
        if (!archive.isFile || archive.length() == 0L) {
            throw IOException("rootfs 压缩包不存在")
        }
        if (!expectedSha256.isNullOrBlank()) {
            onStatus("正在校验 Kali 环境…")
            val actual = sha256(archive)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                throw IOException("SHA-256 校验失败\n期望: $expectedSha256\n实际: $actual")
            }
        }

        val tmp = File(baseDir, "kali.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        if (!tmp.isDirectory) throw IOException("无法创建临时目录")

        try {
            onStatus("正在解压 Kali 环境…")
            extract(archive, tmp)
            normalizeSingleTopLevelDirectory(tmp)
            if (!File(tmp, "etc/os-release").isFile) {
                throw IOException("解压完成，但没有找到 etc/os-release，rootfs 结构不正确")
            }

            onStatus("正在完成安装…")
            // Keep the user's home directory and the Node/dsh installation across a
            // reinstall or update: they are not part of the image and can take tens of
            // minutes to rebuild.
            val keep = preserveUserData()
            try {
                if (rootfsDir.exists() && !rootfsDir.deleteRecursively()) {
                    throw IOException("无法替换旧的 Kali 环境")
                }
                if (!tmp.renameTo(rootfsDir)) {
                    tmp.copyRecursively(rootfsDir, overwrite = true)
                    tmp.deleteRecursively()
                }
                restoreUserData(keep)
            } finally {
                keep?.deleteRecursively()
            }
            File(rootfsDir, MARKER).writeText(
                buildString {
                    append("installed_at=${System.currentTimeMillis()}\n")
                    append("label=${archive.name}\n")
                    append("sha256=${expectedSha256?.lowercase() ?: ""}\n")
                }
            )
            Log.i(TAG, "installed rootfs at ${rootfsDir.absolutePath}")
        } catch (t: Throwable) {
            tmp.deleteRecursively()
            throw t
        }
    }

    /**
     * Moves /root and /opt/kingkang out of the way before the old image is deleted.
     * The caller must delete the returned directory afterwards.
     */
    private fun preserveUserData(): File? {
        if (!isInstalled()) return null
        val keep = File(baseDir, "kali.keep")
        keep.deleteRecursively()
        if (!keep.mkdirs()) return null
        var moved = false
        File(rootfsDir, "root").takeIf { it.isDirectory }?.let {
            if (it.renameTo(File(keep, "root"))) moved = true
        }
        File(rootfsDir, "opt/kingkang").takeIf { it.isDirectory }?.let {
            if (it.renameTo(File(keep, "kingkang"))) moved = true
        }
        if (!moved) {
            keep.deleteRecursively()
            return null
        }
        return keep
    }

    private fun restoreUserData(keep: File?) {
        if (keep == null || !keep.isDirectory) return
        File(keep, "kingkang").takeIf { it.isDirectory }?.let { source ->
            val target = File(rootfsDir, "opt/kingkang")
            target.deleteRecursively()
            target.parentFile?.mkdirs()
            if (!source.renameTo(target)) source.copyRecursively(target, overwrite = true)
        }
        File(keep, "root").takeIf { it.isDirectory }?.let { source ->
            val target = File(rootfsDir, "root")
            target.mkdirs()
            // The fresh image ships a few dotfiles in /root; merge rather than replace
            // so a shell profile or .bash_history from the user survives.
            source.listFiles()?.forEach { child ->
                val dest = File(target, child.name)
                dest.deleteRecursively()
                if (!child.renameTo(dest)) child.copyRecursively(dest, overwrite = true)
            }
        }
    }

    fun ensureRuntimeLayout() {
        if (!isInstalled()) return
        RUNTIME_DIRS.forEach { relative ->
            File(rootfsDir, relative).mkdirs()
        }
        val resolv = File(rootfsDir, "etc/resolv.conf")
        if (!resolv.exists()) runCatching { resolv.writeText("") }
        // kali-motd prints "This is a minimal installation" unconditionally, even on the
        // full NetHunter image. Kali's own user-level switch hides that one section; the
        // file lives in /root, which survives image updates.
        val motdFlag = File(rootfsDir, "root/.local/share/kali-motd/disable-minimal-warning")
        if (!motdFlag.exists()) runCatching { motdFlag.parentFile?.mkdirs(); motdFlag.createNewFile() }
    }

    /**
     * Deletes the Kali tree. A full image is several gigabytes, so this reports progress
     * and must be called from a background thread.
     */
    fun delete(onProgress: (removed: Int) -> Unit = {}) {
        val removed = deleteTree(rootfsDir, onProgress)
        // Leftovers from an interrupted install are just as useless as the rootfs itself.
        listOf("kali.tmp", "kali.nested", "kali.keep").forEach { name ->
            deleteTree(File(baseDir, name), onProgress)
        }
        rootfsDir.delete()
        Log.i(TAG, "deleted rootfs, removed ${removed} entries")
    }

    /**
     * Deletes /opt/kingkang (Node.js, dsh, npm caches) from the app side and reports
     * whether it is really gone. Must be called from a background thread.
     */
    fun removeHarnessRuntime(): Boolean {
        val dir = File(rootfsDir, "opt/kingkang")
        if (!dir.exists()) return true
        deleteTree(dir, {})
        return !dir.exists()
    }

    private fun deleteTree(root: File, onProgress: (Int) -> Unit): Int {
        if (!root.exists()) return 0
        var removed = 0
        val pending = ArrayDeque<File>()
        // Parents are added before their children, so deleting this list in reverse
        // removes every directory after everything inside it.
        val dirs = ArrayList<File>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            val dir = pending.removeLast()
            dirs.add(dir)
            for (child in dir.listFiles() ?: continue) {
                if (child.isDirectory && !isSymlink(child)) {
                    pending.addLast(child)
                } else if (child.delete()) {
                    removed++
                    if (removed % 200 == 0) onProgress(removed)
                }
            }
        }
        for (dir in dirs.asReversed()) {
            if (dir.delete()) removed++
        }
        onProgress(removed)
        return removed
    }

    private fun isSymlink(file: File): Boolean =
        runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    fun verifySha256(archive: File, expected: String): Boolean =
        archive.isFile && sha256(archive).equals(expected, ignoreCase = true)

    private fun extract(archive: File, destination: File) {
        val compression = sniffCompression(archive)
        val log = File(context.cacheDir, "rootfs-extract.log")

        // Android's toybox tar only supports xz when an external `xz` binary exists, which
        // stock Android does not ship. Decompress in-process and stream a plain tar into
        // toybox instead; tar still restores symlinks and permission bits.
        val process = ProcessBuilder(
            TAR,
            "-xof",
            "-",
            "-C",
            destination.absolutePath
        )
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()

        try {
            archive.inputStream().use { fileInput ->
                val decoded: InputStream = when (compression) {
                    Compression.XZ -> XZInputStream(fileInput)
                    Compression.GZIP -> GZIPInputStream(fileInput)
                    Compression.NONE -> fileInput
                }
                decoded.use { input ->
                    process.outputStream.use { output ->
                        // A full NetHunter image expands to many gigabytes; stop early
                        // instead of filling the device's data partition.
                        val buffer = ByteArray(128 * 1024)
                        var sinceCheck = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                            sinceCheck += n
                            if (sinceCheck >= 64L * 1024 * 1024) {
                                sinceCheck = 0
                                if (destination.usableSpace in 1 until MIN_FREE_BYTES) {
                                    throw IOException("存储空间不足，解压已停止。请清理空间后重新安装")
                                }
                            }
                        }
                    }
                }
            }

            val exit = process.waitFor()
            // A tarball may contain device nodes (dev/null, dev/random) that an app
            // cannot create. tar reports those and still exits non-zero, so only treat
            // the exit code as fatal when no usable rootfs came out of it.
            if (exit != 0 && !looksLikeRootfs(destination)) {
                val tail = runCatching { log.readText().takeLast(4000) }.getOrDefault("")
                throw IOException("解压失败（tar exit=$exit）\n$tail")
            }
        } catch (t: Throwable) {
            process.destroyForcibly()
            val tail = runCatching { log.readText().takeLast(4000) }.getOrDefault("")
            val detail = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            throw IOException(if (tail.isBlank()) "解压失败：$detail" else "解压失败：$detail\n$tail", t)
        }
    }

    private fun looksLikeRootfs(root: File): Boolean {
        if (File(root, "etc/os-release").isFile) return true
        val only = root.listFiles()?.singleOrNull() ?: return false
        return only.isDirectory && File(only, "etc/os-release").isFile
    }

    private fun normalizeSingleTopLevelDirectory(root: File) {
        if (File(root, "etc/os-release").isFile) return
        val only = root.listFiles()?.singleOrNull() ?: return
        if (!only.isDirectory) return
        if (!File(only, "etc/os-release").isFile) return

        // Rename the nested root into place, then move it back up one level.
        val nested = File(root.parentFile, "kali.nested")
        nested.deleteRecursively()
        if (!only.renameTo(nested)) return
        root.deleteRecursively()
        root.mkdirs()
        nested.listFiles()?.forEach { child ->
            val target = File(root, child.name)
            if (!child.renameTo(target)) {
                child.copyRecursively(target, overwrite = true)
                child.deleteRecursively()
            }
        }
        nested.deleteRecursively()
    }

    private fun sniffCompression(file: File): Compression {
        val header = ByteArray(6)
        val read = file.inputStream().use { it.read(header) }
        if (read >= 6 && header[0] == 0xFD.toByte() && header[1] == 0x37.toByte() &&
            header[2] == 0x7A.toByte() && header[3] == 0x58.toByte() &&
            header[4] == 0x5A.toByte() && header[5] == 0x00.toByte()
        ) return Compression.XZ
        if (read >= 2 && header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte()) {
            return Compression.GZIP
        }
        return Compression.NONE
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private enum class Compression { XZ, GZIP, NONE }

    private companion object {
        const val TAG = "RootfsManager"
        const val TAR = "/system/bin/tar"
        const val MARKER = ".kingkang-rootfs"
        const val MIN_FREE_BYTES = 512L * 1024 * 1024
        val RUNTIME_DIRS = listOf("dev", "proc", "sys", "root/shared", "mnt/sdcard")
    }
}
