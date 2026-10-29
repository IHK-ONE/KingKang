package com.kingkang.app.agent

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kingkang.app.runtime.RootfsManager
import java.io.File
import java.util.UUID

/**
 * Stages arbitrary files where Kali can read them.
 *
 * dsh's prompt wire only accepts text and images, so the composer copies a picked file
 * into the app's shared directory (bind-mounted at `/root/shared`) and the prompt names
 * the guest path. The copy is streamed and size-limited; nothing is held in memory.
 */
object AttachmentStaging {

    const val MAX_FILES = 5
    const val MAX_FILE_BYTES = 64L * 1024L * 1024L

    fun stage(context: Context, uris: List<Uri>): List<AgentDraftFile> {
        if (uris.isEmpty()) return emptyList()
        val resolver = context.contentResolver
        val uploads = File(RootfsManager(context).sharedDir, "uploads")
        if (!uploads.exists() && !uploads.mkdirs()) return emptyList()
        val batch = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        val directory = File(uploads, batch)
        if (!directory.exists() && !directory.mkdirs()) return emptyList()

        val staged = mutableListOf<AgentDraftFile>()
        for (uri in uris.take(MAX_FILES)) {
            val name = safeName(displayName(resolver, uri))
            val target = uniqueTarget(directory, name)
            val bytes = try {
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        copyLimited(input, output, MAX_FILE_BYTES)
                    }
                }
            } catch (t: Throwable) {
                null
            }
            if (bytes == null || bytes <= 0L) {
                target.delete()
                continue
            }
            staged += AgentDraftFile(
                id = UUID.randomUUID().toString(),
                name = target.name,
                mimeType = resolver.getType(uri) ?: "application/octet-stream",
                size = bytes,
                hostPath = target.absolutePath,
                guestPath = "/root/shared/uploads/$batch/${target.name}"
            )
        }
        if (staged.isEmpty()) directory.delete()
        return staged
    }

    /** Best-effort cleanup when a draft is removed before it is sent. */
    fun discard(file: AgentDraftFile) {
        runCatching {
            val target = File(file.hostPath)
            target.delete()
            target.parentFile?.takeIf { it.listFiles()?.isEmpty() == true }?.delete()
        }
    }

    private fun displayName(
        resolver: android.content.ContentResolver,
        uri: Uri
    ): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    } catch (t: Throwable) {
        null
    } ?: uri.lastPathSegment

    private fun safeName(raw: String?): String {
        val leaf = raw
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.replace(Regex("[\\u0000-\\u001f]"), "")
            ?.trim()
            ?.take(96)
            .orEmpty()
        if (leaf.isEmpty()) return "file"
        return leaf.replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
    }

    private fun uniqueTarget(directory: File, name: String): File {
        var candidate = File(directory, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val suffix = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "$stem-$index$suffix")
            index++
        }
        return candidate
    }

    /** Copies until EOF, refusing the file as soon as it exceeds [limit]. */
    private fun copyLimited(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        limit: Long
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IllegalStateException("file too large")
            output.write(buffer, 0, read)
        }
        return total
    }
}
