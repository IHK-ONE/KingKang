package com.kingkang.app.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class RootfsInstaller(
    private val context: Context,
    private val manager: RootfsManager
) {
    suspend fun install(
        spec: RootfsSpec,
        expectedSha256: String = spec.sha256,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        onStatus: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val archive = File(context.cacheDir, "kali-${spec.fileName}")
        archive.parentFile?.mkdirs()
        val part = File(archive.absolutePath + ".part")

        val free = context.filesDir.usableSpace
        if (free in 1 until spec.approxBytes + MIN_SPARE_BYTES) {
            throw IOException("存储空间不足：需要约 ${spec.approxBytes / 1024 / 1024} MB 下载空间")
        }

        // Reuse an archive left behind by an earlier install attempt (e.g. extraction
        // failed), so a retry does not download the rootfs again.
        if (archive.isFile && archive.length() > 0L &&
            (expectedSha256.isBlank() || manager.verifySha256(archive, expectedSha256))
        ) {
            onStatus("发现已下载的安装包，直接安装…")
            manager.installArchive(archive, expectedSha256, onStatus)
            archive.delete()
            return@withContext
        }
        if (archive.exists() && !manager.verifySha256(archive, expectedSha256)) {
            archive.delete()
            part.delete()
        }

        val resumeFrom = part.length().takeIf { it > 0L } ?: 0L
        onStatus(
            if (resumeFrom > 0L) "正在续传 ${spec.label}…"
            else "正在下载 ${spec.label}…"
        )

        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "KingKang/0.1 (Android)")
            setRequestProperty("Accept-Encoding", "identity")
            if (resumeFrom > 0L) setRequestProperty("Range", "bytes=$resumeFrom-")
        }

        try {
            val code = connection.responseCode
            if (code == HTTP_RANGE_NOT_SATISFIABLE && resumeFrom > 0L) {
                // A stale or oversized partial file would make every retry fail identically.
                part.delete()
                throw IOException("续传位置无效，已清理临时文件，请重新安装")
            }
            if (code !in 200..299) {
                throw IOException("下载失败：HTTP $code")
            }

            // Only append when the server actually honoured the Range request.
            val appending = code == HttpURLConnection.HTTP_PARTIAL && resumeFrom > 0L
            if (!appending && resumeFrom > 0L) part.delete()

            val bodyBytes = connection.contentLengthLong.takeIf { it > 0 }
            val total = when {
                appending -> resumeFrom + (bodyBytes ?: 0L)
                bodyBytes != null -> bodyBytes
                else -> spec.approxBytes
            }
            var downloaded = if (appending) resumeFrom else 0L
            if (downloaded > 0L) onProgress(downloaded, total)

            connection.inputStream.use { input ->
                FileOutputStream(part, appending).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                }
            }

            if (part.length() == 0L) throw IOException("下载结果为空")
            if (archive.exists()) archive.delete()
            if (!part.renameTo(archive)) {
                part.copyTo(archive, overwrite = true)
                part.delete()
            }
            onProgress(archive.length(), archive.length())
            manager.installArchive(archive, expectedSha256, onStatus)
            archive.delete()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val MIN_SPARE_BYTES = 512L * 1024 * 1024
    }
}
