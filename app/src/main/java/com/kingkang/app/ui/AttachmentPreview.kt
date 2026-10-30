package com.kingkang.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kingkang.app.agent.AgentFile
import com.kingkang.app.agent.AttachmentKind
import com.kingkang.app.agent.attachmentKind
import com.kingkang.app.files.FileActions
import com.kingkang.app.ui.theme.AccentSoft
import com.kingkang.app.ui.theme.Background
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.SurfaceRaised
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Bytes of a text attachment read for the in-app viewer; the rest is reported as cut. */
private const val MAX_TEXT_PREVIEW_BYTES = 64 * 1024

/** Longest edge of the decoded preview bitmap; larger photos are downsampled first. */
private const val MAX_PREVIEW_EDGE = 2400

private sealed interface PreviewBody {
    data object Missing : PreviewBody
    data class Image(val bitmap: ImageBitmap?) : PreviewBody
    data class Text(val text: String, val truncated: Boolean) : PreviewBody
    data class Note(val message: String, val hint: String? = null) : PreviewBody
}

/**
 * In-place preview for an attachment chip. Images are shown full-size (pinch to zoom,
 * double-tap to reset), text and source files open read-only and selectable, and formats
 * the app cannot render — PDF among them — stay one tap away from the system viewer.
 * The dialog never copies the file: it reads the staged host file directly.
 */
@Composable
fun AttachmentPreviewDialog(
    file: AgentFile,
    resolveFile: (AgentFile) -> File?,
    onOpenExternal: (AgentFile) -> Unit,
    onShare: (AgentFile) -> Unit,
    onDismiss: () -> Unit
) {
    val body by produceState<PreviewBody?>(initialValue = null, file.hostPath, file.guestPath) {
        value = withContext(Dispatchers.IO) { loadPreview(file, resolveFile) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceRaised)
                .border(1.dp, Border.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        metaLine(file),
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = TextSecondary, fontSize = 18.sp)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .heightIn(max = 470.dp)
            ) {
                when (val current = body) {
                    null -> Box(
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = TextMuted,
                            strokeWidth = 1.6.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    PreviewBody.Missing -> PreviewNote(
                        "文件已不在共享目录中",
                        "可能已被清理，或这段会话来自另一台设备。"
                    )
                    is PreviewBody.Note -> PreviewNote(current.message, current.hint)
                    is PreviewBody.Text -> TextPreview(current)
                    is PreviewBody.Image -> ImagePreview(current, file.name)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onOpenExternal(file) }) {
                    Text("用其他应用打开", color = AccentSoft, fontSize = 12.sp)
                }
                TextButton(onClick = { onShare(file) }) {
                    Text("分享", color = TextSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PreviewNote(message: String, hint: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .padding(horizontal = 6.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = TextSecondary, fontSize = 13.sp)
        hint?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
    }
}

@Composable
private fun TextPreview(body: PreviewBody.Text) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Background)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            SelectionContainer {
                Text(
                    body.text,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (body.truncated) {
            Text(
                "仅显示前 ${MAX_TEXT_PREVIEW_BYTES / 1024} KB，完整内容请用其他应用打开。",
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }
    }
}

@Composable
private fun ImagePreview(body: PreviewBody.Image, name: String) {
    val bitmap = body.bitmap
    if (bitmap == null) {
        PreviewNote("无法解码这张图片", "可以交给其他应用打开。")
        return
    }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        val next = (scale * zoom).coerceIn(1f, 5f)
        scale = next
        offset = if (next > 1f) offset + pan else Offset.Zero
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Background)
            .transformable(transform)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

private fun loadPreview(file: AgentFile, resolve: (AgentFile) -> File?): PreviewBody {
    val target = resolve(file)
    if (target == null || !target.isFile) return PreviewBody.Missing
    return when (attachmentKind(file.mimeType, file.name)) {
        AttachmentKind.Image -> PreviewBody.Image(decodeImagePreview(target))
        AttachmentKind.Text -> readTextPreview(target)
        AttachmentKind.Pdf -> PreviewBody.Note(
            "PDF 需要系统应用来排版",
            "点下方「用其他应用打开」查看完整内容。"
        )
        AttachmentKind.Other -> PreviewBody.Note(
            "暂不支持在应用内预览这种文件",
            "可以交给其他应用打开，或分享出去。"
        )
    }
}

/** Downsamples to [MAX_PREVIEW_EDGE] before decoding so a camera photo does not exhaust the heap. */
private fun decodeImagePreview(file: File): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        var sample = 1
        var largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / 2 >= MAX_PREVIEW_EDGE) {
            sample *= 2
            largest /= 2
        }
        BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )?.asImageBitmap()
    }
} catch (_: Throwable) {
    null
}

private fun readTextPreview(file: File): PreviewBody {
    val length = file.length()
    val bytes = try {
        file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var remaining = MAX_TEXT_PREVIEW_BYTES.toLong()
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            output.toByteArray()
        }
    } catch (_: Throwable) {
        null
    }
    if (bytes == null) {
        return PreviewBody.Note("无法读取这个文件", "文件可能已被移动或删除。")
    }
    // A NUL in the head is the cheapest reliable signal of binary data; showing replacement
    // characters for a whole archive helps nobody.
    if (bytes.any { it == 0.toByte() }) {
        return PreviewBody.Note("这个文件包含二进制内容", "用其他应用打开才能正确查看。")
    }
    val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
    return PreviewBody.Text(text, truncated = length > MAX_TEXT_PREVIEW_BYTES)
}

private fun metaLine(file: AgentFile): String = buildString {
    if (file.size > 0) {
        append(formatFileSize(file.size))
        append(" · ")
    }
    append(typeLabel(file))
}

private fun typeLabel(file: AgentFile): String {
    val declared = file.mimeType.trim()
    if (declared.isNotEmpty() && declared != "application/octet-stream") return declared
    return FileActions.mimeTypeOf(File(file.name))
}
