package com.kingkang.app.runtime

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Prepares the DeepSeek Harness runtime and runs its maintenance scripts inside Kali.
 *
 * Node.js is downloaded by the app (not by apt/curl) and unpacked into the rootfs; the
 * scripts under assets/harness then only need npm. Progress is reported with `KK_*` lines.
 */
class HarnessManager(
    private val context: Context,
    private val rootfsManager: RootfsManager,
    private val runtimeAssets: RuntimeAssets
) {

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var activeDownload: HttpURLConnection? = null

    @Volatile
    private var activeProcess: Process? = null

    @Volatile
    private var activeExtract: Process? = null

    private val shimLock = Any()

    /**
     * Builds the rename(2) shim inside Kali the first time a PRoot session needs it.
     * Android's app sandbox answers rename(2) with ENOSYS, which breaks npm and any tool
     * that moves files, so the launcher preloads this shim whenever it exists. Needs gcc
     * from the image; when the build fails the session still starts, without the shim.
     */
    fun ensureRenameShim() {
        if (!rootfsManager.isInstalled()) return
        val shim = ProotEngine().shimFile(rootfsManager.rootfsDir)
        if (shim.length() > 0) return
        synchronized(shimLock) {
            if (shim.length() > 0) return
            try {
                val output = runAsset(COMPAT_SCRIPT, COMPAT_TIMEOUT, ensureShim = false)
                if (shim.length() == 0L) {
                    Log.w(TAG, "rename shim unavailable: ${output.takeLast(400)}")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "rename shim build failed", t)
            }
            Unit
        }
    }

    fun cancel() {
        cancelRequested = true
        activeDownload?.let { runCatching { it.disconnect() } }
        activeExtract?.let { runCatching { it.destroy() } }
        val process = activeProcess ?: return
        runCatching { process.destroy() }
        if (process.isAlive) {
            runCatching { process.destroyForcibly() }
        }
    }

    /**
     * Downloads the official Node.js 24 tarball for this ABI, verifies its SHA-256 and
     * unpacks it to /opt/kingkang/node. Skips the download when a suitable runtime is
     * already present. Returns the installed version (for example `v24.22.1`).
     */
    fun ensureNode(
        onStep: (String) -> Unit,
        onProgress: (Float) -> Unit = {}
    ): String {
        cancelRequested = false
        if (!rootfsManager.isInstalled()) {
            throw IOException("Kali 环境尚未安装")
        }

        val base = File(rootfsManager.rootfsDir, "opt/kingkang")
        val nodeDir = File(base, "node")
        val marker = File(nodeDir, ".kingkang-node-version")
        val existing = runCatching { marker.readText().trim() }.getOrNull()
        if (File(nodeDir, "bin/node").isFile && majorOf(existing) >= 24) {
            Log.i(TAG, "ensureNode: reusing $existing")
            return existing.orEmpty()
        }
        Log.i(TAG, "ensureNode: preparing Node for ${RootfsCatalog.currentAbiName()}")

        val arch = when (RootfsCatalog.currentAbiName()) {
            "arm64-v8a" -> "arm64"
            "x86_64" -> "x64"
            else -> throw IOException("当前架构不支持安装 Node.js")
        }

        onStep("正在获取 Node.js 24 版本…")
        val shasums = downloadText(SHASUMS_URL, MAX_SHASUMS_BYTES)
        val pattern = Regex(
            """^([0-9a-fA-F]{64})\s+(node-v([0-9][^\s]*)-linux-$arch\.tar\.gz)$""",
            RegexOption.MULTILINE
        )
        val match = pattern.find(shasums) ?: throw IOException("未找到匹配的 Node.js 安装包")
        val expectedSha = match.groupValues[1]
        val fileName = match.groupValues[2]
        val version = match.groupValues[3]

        onStep("正在下载 Node.js $version（约 58 MB）…")
        val archive = File(rootfsManager.rootfsDir, "tmp/kingkang-node.tar.gz")
        archive.parentFile?.mkdirs()
        Log.i(TAG, "ensureNode: downloading $fileName expectedSha=$expectedSha")

        var attempt = 0
        while (true) {
            attempt++
            if (attempt > 1) {
                onStep("下载中断，正在重试…")
                onProgress(0f)
            }
            try {
                downloadFile("$DIST_URL/$fileName", archive, onProgress)
            } catch (e: IOException) {
                archive.delete()
                if (cancelRequested || attempt >= 3) throw e
                Log.w(TAG, "ensureNode: download attempt $attempt failed", e)
                continue
            }
            Log.i(TAG, "ensureNode: downloaded ${archive.length()} bytes")

            onStep("正在校验 Node.js…")
            val actualSha = sha256(archive)
            if (actualSha.equals(expectedSha, ignoreCase = true)) break
            Log.w(TAG, "ensureNode: checksum mismatch $actualSha")
            archive.delete()
            if (attempt >= 3 || cancelRequested) {
                throw IOException("Node.js 安装包 SHA-256 校验失败")
            }
        }

        onStep("正在解压 Node.js…")
        base.mkdirs()
        val extractDir = File(base, "node.extract")
        val newNode = File(base, "node.new")
        try {
            extractTarGz(archive, extractDir)
            if (cancelRequested) throw IOException("已取消")
            val top = extractDir.listFiles()?.singleOrNull()
                ?: throw IOException("Node.js 压缩包结构不正确")
            if (!File(top, "bin/node").isFile) {
                throw IOException("Node.js 压缩包缺少 bin/node")
            }

            newNode.deleteRecursively()
            if (!top.renameTo(newNode)) {
                top.copyRecursively(newNode, overwrite = true)
                top.deleteRecursively()
            }

            nodeDir.deleteRecursively()
            if (!newNode.renameTo(nodeDir)) {
                newNode.copyRecursively(nodeDir, overwrite = true)
                newNode.deleteRecursively()
            }
            File(nodeDir, ".kingkang-node-version").writeText("v$version")
            return "v$version"
        } finally {
            extractDir.deleteRecursively()
            newNode.deleteRecursively()
            archive.delete()
        }
    }

    /**
     * Copies [assetName] into the rootfs and runs it with bash. Returns the combined output;
     * a non-zero exit is only an error when the script did not emit its own `KK_ERROR` marker.
     */
    fun runAsset(
        assetName: String,
        timeoutMillis: Long,
        onLine: (String) -> Unit = {},
        resetCancel: Boolean = true,
        ensureShim: Boolean = true
    ): String {
        if (resetCancel) cancelRequested = false
        if (cancelRequested) throw IOException("已取消")
        if (!rootfsManager.isInstalled()) {
            throw IOException("Kali 环境尚未安装")
        }
        if (ensureShim) ensureRenameShim()

        val paths = runtimeAssets.ensureInstalled()
        rootfsManager.ensureRuntimeLayout()
        val resolvConf = NetworkDns.writeResolvConf(context)

        val scriptFile = File(rootfsManager.rootfsDir, "tmp/${assetName.substringAfterLast('/')}")
        scriptFile.parentFile?.mkdirs()
        context.assets.open(assetName).use { input ->
            scriptFile.outputStream().use { output -> input.copyTo(output) }
        }
        val guestScript = "/tmp/${scriptFile.name}"

        val command = ProotEngine().buildCommand(
            rootfs = rootfsManager.rootfsDir,
            paths = paths,
            resolvConf = resolvConf,
            sharedDir = rootfsManager.sharedDir,
            cacheDir = context.cacheDir,
            scriptPath = guestScript
        )

        // ProcessBuilder uses argv[0] as the program path. ProotEngine's argv[0] is the
        // literal "proot" (matching the JNI PTY bridge), so drop it and let the executable
        // path take that slot.
        val builder = ProcessBuilder(listOf(command.executable) + command.argv.drop(1))
            .redirectErrorStream(true)
        command.cwd?.let { builder.directory(File(it)) }
        builder.environment().putAll(command.env)

        val process = builder.start()
        activeProcess = process
        runCatching { process.outputStream.close() }

        val output = StringBuffer()
        val reader = Thread({
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        output.append(line).append('\n')
                        onLine(line)
                    }
                }
            }.onFailure { Log.w(TAG, "harness output reader stopped", it) }
        }, "kingkang-harness")
        reader.isDaemon = true
        reader.start()

        try {
            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                cancel()
                reader.join(1_500)
                throw IOException("操作超时，已停止安装进程")
            }
            reader.join(2_000)
            val exit = process.exitValue()
            val text = output.toString()
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            // A script that printed its own success marker finished its work. PRoot can still
            // report a non-zero status for the session (the guest process tree shares one
            // exit path), so the marker is authoritative there instead of the exit code.
            val succeeded = lines.any { it == "KK_OK=1" }
            val failedByScript = lines.any { it.startsWith("KK_ERROR=") }
            if (exit != 0 && succeeded && !failedByScript) {
                Log.w(TAG, "runAsset: $assetName exited $exit after KK_OK=1; reporting success")
            }
            if (exit != 0 && !succeeded && !failedByScript) {
                // Progress markers are for the app, not for the user; leave only real output.
                val tail = lines.filterNot { it.startsWith("KK_") }
                    .joinToString("\n")
                    .takeLast(600)
                throw IOException(
                    if (tail.isBlank()) "命令执行失败（exit=$exit）" else "命令执行失败（exit=$exit）\n$tail"
                )
            }
            return text
        } finally {
            activeProcess = null
        }
    }

    private fun downloadText(url: String, maxBytes: Long): String {
        val connection = openConnection(url)
        activeDownload = connection
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    if (cancelRequested) throw IOException("已取消")
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) throw IOException("响应过大")
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } finally {
            activeDownload = null
            connection.disconnect()
        }
    }

    private fun downloadFile(url: String, target: File, onProgress: (Float) -> Unit) {
        val connection = openConnection(url)
        activeDownload = connection
        val partial = File(target.parentFile, "${target.name}.part")
        partial.delete()
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("下载失败（HTTP $code）")
            val total = connection.contentLengthLong
            var done = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        if (cancelRequested) throw IOException("已取消")
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(done.toFloat() / total.toFloat())
                            }
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("Node.js 下载不完整")
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
        } catch (t: Throwable) {
            partial.delete()
            throw t
        } finally {
            activeDownload = null
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "KingKang/0.1")
        }

    private fun extractTarGz(archive: File, destination: File) {
        destination.deleteRecursively()
        destination.mkdirs()
        val log = File(rootfsManager.rootfsDir, "tmp/kingkang-node-extract.log")
        val process = ProcessBuilder(
            "/system/bin/tar",
            "-xof",
            "-",
            "-C",
            destination.absolutePath
        )
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()
        activeExtract = process

        try {
            GZIPInputStream(archive.inputStream().buffered(128 * 1024)).use { input ->
                process.outputStream.use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        if (cancelRequested) throw IOException("已取消")
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            val exit = process.waitFor()
            if (cancelRequested) throw IOException("已取消")
            if (exit != 0) {
                val tail = runCatching { log.readText().takeLast(800) }.getOrDefault("")
                throw IOException(
                    if (tail.isBlank()) {
                        "Node.js 解压失败（tar exit=$exit）"
                    } else {
                        "Node.js 解压失败（tar exit=$exit）\n$tail"
                    }
                )
            }
        } catch (t: Throwable) {
            process.destroyForcibly()
            throw t
        } finally {
            activeExtract = null
            log.delete()
        }
    }

    private fun majorOf(version: String?): Int =
        version?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() } ?: 0

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                if (cancelRequested) throw IOException("已取消")
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private companion object {
        const val TAG = "HarnessManager"
        const val DIST_URL = "https://nodejs.org/dist/latest-v24.x"
        const val SHASUMS_URL = "$DIST_URL/SHASUMS256.txt"
        const val MAX_SHASUMS_BYTES = 256L * 1024L
        const val COMPAT_SCRIPT = "harness/compat.sh"
        const val COMPAT_TIMEOUT = 5 * 60_000L
    }
}
