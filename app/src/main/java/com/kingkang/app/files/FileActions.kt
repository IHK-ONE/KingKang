package com.kingkang.app.files

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * One place for handing a file the app produced — an export, an attachment, a deliverable —
 * to another app. Everything goes through the app's [FileProvider]; no `file://` URI is ever
 * exposed, so a receiver cannot trigger `FileUriExposedException`.
 */
object FileActions {

    /** Provider authority declared in AndroidManifest.xml. */
    private fun authority(context: Context): String = "${context.packageName}.files"

    fun mimeTypeOf(file: File): String {
        val extension = file.name.substringAfterLast('.', "").lowercase(Locale.US)
        if (extension.isNotBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }
        }
        return when (extension) {
            "md", "markdown" -> "text/markdown"
            "log" -> "text/plain"
            "yaml", "yml" -> "application/yaml"
            "jsonl" -> "application/x-ndjson"
            else -> "application/octet-stream"
        }
    }

    /** Opens the file with the user's default app. Returns an error message, or null. */
    fun open(context: Context, file: File, mime: String = mimeTypeOf(file)): String? {
        if (!file.isFile) return "文件不存在"
        val uri = contentUri(context, file) ?: return "无法共享此文件"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return launch(context, intent, file)
    }

    /** System share sheet for the file. Returns an error message, or null. */
    fun share(context: Context, file: File, mime: String = mimeTypeOf(file)): String? {
        if (!file.isFile) return "文件不存在"
        val uri = contentUri(context, file) ?: return "无法共享此文件"
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, "分享文件")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return launch(context, chooser, file)
    }

    /** Copies [file]'s bytes into the document the user picked with SAF. */
    fun copyTo(context: Context, file: File, target: Uri): String? = try {
        if (!file.isFile) {
            "文件不存在"
        } else {
            val output = context.contentResolver.openOutputStream(target)
            if (output == null) {
                "无法写入所选位置"
            } else {
                output.use { destination ->
                    file.inputStream().use { source -> source.copyTo(destination) }
                }
                null
            }
        }
    } catch (_: Exception) {
        "保存失败，请重试"
    }

    /** Opens a whole directory of exports (files application) instead of one file. */
    fun openFolder(context: Context, directory: File): String? {
        if (!directory.isDirectory) return "目录不存在"
        val uri = contentUri(context, directory) ?: return "无法共享此目录"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "resource/folder")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return launch(context, intent, directory)
    }

    /**
     * Puts an image the app is showing on the clipboard as a PNG, the form other apps
     * paste. The bytes come from the same `data:` URL the viewer already decoded; the
     * file lives in the cache until the next copy replaces it.
     */
    fun copyImageToClipboard(context: Context, dataUrl: String): String? {
        val decoded = decodeDataUrl(dataUrl) ?: return "图片数据无效"
        val dir = File(context.cacheDir, "clipboard").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val extension = when (decoded.first) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        val file = File(dir, "image.$extension")
        return try {
            file.writeBytes(decoded.second)
            val uri = contentUri(context, file) ?: return "无法共享此图片"
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "image", uri))
            null
        } catch (_: Exception) {
            "复制图片失败"
        }
    }

    /** Writes the bytes behind a `data:` URL into the document the user picked. */
    fun saveDataUrl(context: Context, dataUrl: String, target: Uri): String? {
        val decoded = decodeDataUrl(dataUrl) ?: return "图片数据无效"
        return try {
            val output = context.contentResolver.openOutputStream(target)
                ?: return "无法写入所选位置"
            output.use { it.write(decoded.second) }
            null
        } catch (_: Exception) {
            "保存失败，请重试"
        }
    }

    /** Media type and bytes of a `data:<type>;base64,<payload>` URL. */
    private fun decodeDataUrl(dataUrl: String): Pair<String, ByteArray>? {
        if (!dataUrl.startsWith("data:")) return null
        val comma = dataUrl.indexOf(',')
        if (comma <= 0) return null
        val header = dataUrl.substring(5, comma)
        if (!header.contains(";base64", ignoreCase = true)) return null
        val mediaType = header.substringBefore(';').ifBlank { "image/png" }
        return try {
            val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
            if (bytes.isEmpty()) null else mediaType to bytes
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** See [FileNames.sanitize]; kept here for callers that already import this object. */
    fun sanitizeFileName(name: String, fallback: String = "download"): String =
        FileNames.sanitize(name, fallback)

    private fun contentUri(context: Context, file: File): Uri? = try {
        FileProvider.getUriForFile(context, authority(context), file)
    } catch (_: IllegalArgumentException) {
        // The file sits outside every configured provider path; refusing is safer than
        // widening the provider to the whole filesystem.
        null
    }

    private fun launch(context: Context, intent: Intent, file: File): String? = try {
        // ClipData carries the grant to every receiver the chooser offers.
        val uri = intent.data ?: intent.clipData?.getItemAt(0)?.uri
        if (uri != null) {
            intent.clipData = ClipData.newRawUri(file.name, uri)
        }
        context.startActivity(intent)
        null
    } catch (_: ActivityNotFoundException) {
        "没有可打开此文件的应用"
    } catch (_: SecurityException) {
        "系统拒绝打开此文件"
    }
}
