package com.kingkang.app.files

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** A file the harness page asked the WebView to download, already reduced to safe parts. */
data class HarnessDownload(
    val url: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val userAgent: String?
)

/**
 * Downloads that originate inside the harness WebView. The page runs on loopback, so a
 * download is fetched by the app itself rather than handed to DownloadManager: the app
 * keeps the cookie, enforces the loopback-only rule on every redirect, and writes into
 * its own cache. Names from `Content-Disposition` are untrusted and always sanitized.
 */
object HarnessDownloads {

    /** A bigger single file is refused rather than risking the app's cache partition. */
    const val MAX_BYTES = 512L * 1024L * 1024L
    private const val MAX_REDIRECTS = 3
    private const val KEEP_FILES = 20

    fun isLoopbackUrl(raw: String): Boolean = try {
        val uri = URI(raw)
        (uri.scheme == "http" || uri.scheme == "https") && isLoopbackHost(uri.host)
    } catch (_: Exception) {
        false
    }

    fun isLoopbackHost(host: String?): Boolean =
        host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "[::1]"

    /**
     * Picks the name to save under: RFC 5987 `filename*`, then `filename`, then the URL
     * leaf. A name without an extension gets one from the MIME type when that is known.
     */
    fun fileNameFor(contentDisposition: String?, url: String, mimeType: String?): String {
        val header = contentDisposition.orEmpty()
        val encoded = Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)""")
            .find(header)?.groupValues?.get(1)
            ?.trim()?.trim('"')
            ?.let { value ->
                runCatching {
                    URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
                }.getOrDefault(value)
            }
        val plain = Regex("""filename\s*=\s*("([^"]*)"|([^;]+))""")
            .find(header)?.let { match ->
                match.groupValues[2].ifBlank { match.groupValues[3] }.trim()
            }
        val fromUrl = runCatching {
            val path = URI(url).path.orEmpty()
            URLDecoder.decode(path.substringAfterLast('/'), StandardCharsets.UTF_8.name())
        }.getOrDefault("")
        val raw = encoded?.takeIf { it.isNotBlank() }
            ?: plain?.takeIf { it.isNotBlank() }
            ?: fromUrl
        val sanitized = FileNames.sanitize(raw, "download")
        val extension = sanitized.substringAfterLast('.', "")
        if (extension.isNotBlank()) return sanitized
        val fromMime = extensionFor(mimeType)
        return if (fromMime == null) sanitized else "$sanitized.$fromMime"
    }

    /**
     * Streams [url] into [directory]. Redirects are followed manually so each hop is
     * re-checked against loopback; the cookie is supplied by the caller because only the
     * UI thread should touch the shared CookieManager.
     */
    fun download(
        url: String,
        userAgent: String?,
        cookie: String?,
        directory: File,
        fileName: String
    ): File {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("无法创建下载目录")
        var current = url
        var redirects = 0
        while (true) {
            if (!isLoopbackUrl(current)) throw IOException("已拦截非本机下载链接")
            val connection = try {
                URL(current).openConnection() as HttpURLConnection
            } catch (error: Exception) {
                throw IOException("下载地址无效", error)
            }
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: "KingKang")
            cookie?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("下载重定向缺少地址")
                    if (redirects >= MAX_REDIRECTS) throw IOException("下载重定向次数过多")
                    current = URL(URL(current), location).toString()
                    redirects++
                    continue
                }
                if (code !in 200..299) throw IOException("下载失败（HTTP $code）")
                val declared = connection.contentLengthLong
                if (declared > MAX_BYTES) throw IOException("文件超过 512 MB，未下载")
                val target = FileNames.unique(directory, fileName)
                val temp = File(directory, "${target.name}.part")
                var total = 0L
                try {
                    connection.inputStream.use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_BYTES) throw IOException("文件超过 512 MB，未下载")
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                } catch (error: Throwable) {
                    temp.delete()
                    throw error
                }
                prune(directory, target)
                return target
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Keeps the newest [KEEP_FILES] downloads; harness downloads are a convenience, not storage. */
    fun prune(directory: File, keep: File) {
        runCatching {
            val files = directory.listFiles { file -> file.isFile && !file.name.endsWith(".part") }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            for (old in files.drop(KEEP_FILES)) {
                if (old != keep) old.delete()
            }
        }
    }

    private fun extensionFor(mimeType: String?): String? = when (
        mimeType?.substringBefore(';')?.trim()?.lowercase()
    ) {
        "text/plain" -> "txt"
        "text/markdown" -> "md"
        "text/csv" -> "csv"
        "application/json" -> "json"
        "application/zip" -> "zip"
        "application/pdf" -> "pdf"
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "application/x-ndjson" -> "jsonl"
        else -> null
    }
}
