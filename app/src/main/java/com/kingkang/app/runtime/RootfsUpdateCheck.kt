package com.kingkang.app.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the SHA-256 Kali currently publishes for the rootfs image. That checksum changes
 * whenever Kali rotates the image, which is how an installed environment learns that a
 * newer build is available without downloading the archive first.
 */
class RootfsUpdateCheck {

    suspend fun latestChecksum(spec: RootfsSpec): String = withContext(Dispatchers.IO) {
        val connection = (URL(RootfsCatalog.checksumsUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "KingKang/0.1 (Android)")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("检查更新失败：HTTP $code")
            val body = connection.inputStream.bufferedReader().use { reader ->
                val text = StringBuilder()
                while (text.length < MAX_BYTES) {
                    val line = reader.readLine() ?: break
                    text.append(line).append('\n')
                }
                text.toString()
            }
            RootfsCatalog.parseChecksum(body, spec.fileName)
                ?: throw IOException("校验文件中没有 ${spec.fileName}")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_BYTES = 64 * 1024
    }
}
