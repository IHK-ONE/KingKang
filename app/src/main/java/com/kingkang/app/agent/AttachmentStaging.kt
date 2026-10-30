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

    /**
     * What one staging pass produced. [failed] keeps the source URIs so the composer can
     * offer a retry without reopening the picker; [cancelled] means the whole batch was
     * thrown away and nothing was added.
     */
    data class StagedBatch(
        val files: List<AgentDraftFile>,
        val failed: List<Uri>,
        val cancelled: Boolean = false
    )

    private class Cancelled : Exception()

    /**
     * Copies picked files into the shared directory. [onProgress] runs on the calling
     * thread (the composer launches staging on IO and hands progress back to the main
     * thread); [isCancelled] is polled between chunks so a large copy stops promptly.
     */
    fun stage(
        context: Context,
        uris: List<Uri>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): StagedBatch {
        if (uris.isEmpty()) return StagedBatch(emptyList(), emptyList())
        val resolver = context.contentResolver
        val uploads = File(RootfsManager(context).sharedDir, "uploads")
        if (!uploads.exists() && !uploads.mkdirs()) return StagedBatch(emptyList(), uris)
        // A crash or a cancelled copy can leave an empty batch directory behind; it holds
        // nothing the transcript can point at.
        runCatching {
            uploads.listFiles()?.forEach { dir ->
                if (dir.isDirectory && dir.listFiles()?.isEmpty() == true) dir.delete()
            }
        }
        val batch = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        val directory = File(uploads, batch)
        if (!directory.exists() && !directory.mkdirs()) return StagedBatch(emptyList(), uris)

        val chosen = uris.take(MAX_FILES)
        val staged = mutableListOf<AgentDraftFile>()
        val failed = mutableListOf<Uri>()
        var cancelled = false
        for ((index, uri) in chosen.withIndex()) {
            if (isCancelled()) {
                cancelled = true
                break
            }
            onProgress(index, chosen.size)
            val target = uniqueTarget(directory, safeName(displayName(resolver, uri)))
            val bytes = try {
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        copyLimited(input, output, MAX_FILE_BYTES, isCancelled)
                    }
                }
            } catch (_: Cancelled) {
                target.delete()
                cancelled = true
                break
            } catch (t: Throwable) {
                null
            }
            if (bytes == null || bytes <= 0L) {
                target.delete()
                failed += uri
            } else {
                staged += AgentDraftFile(
                    id = UUID.randomUUID().toString(),
                    name = target.name,
                    mimeType = resolver.getType(uri) ?: "application/octet-stream",
                    size = bytes,
                    hostPath = target.absolutePath,
                    guestPath = "/root/shared/uploads/$batch/${target.name}"
                )
            }
            onProgress(index + 1, chosen.size)
        }
        if (cancelled) {
            // Cancel means the batch never happened: remove the copies so nothing lingers
            // in the Kali-visible directory without a chip that could remove it later.
            staged.forEach(::discard)
            directory.delete()
            return StagedBatch(emptyList(), emptyList(), cancelled = true)
        }
        if (staged.isEmpty()) directory.delete()
        return StagedBatch(staged, failed)
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
        limit: Long,
        isCancelled: () -> Boolean
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            if (isCancelled()) throw Cancelled()
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IllegalStateException("file too large")
            output.write(buffer, 0, read)
        }
        return total
    }
}
