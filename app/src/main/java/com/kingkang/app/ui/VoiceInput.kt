package com.kingkang.app.ui

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingkang.app.agent.AgentSpeechProvider
import com.kingkang.app.ui.theme.AccentDim
import com.kingkang.app.ui.theme.AccentSoft
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.Red
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.SurfaceVariant
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/** The only shape dsh's speech Remote accepts: 16 kHz, mono, PCM16. */
internal const val SPEECH_SAMPLE_RATE = 16_000

/** A recording failure worth telling the user about, in their terms. */
internal class SpeechCaptureException(message: String) : Exception(message)

/**
 * Records one utterance into memory and returns it as a WAV. Nothing touches disk, and a
 * cancelled run drops the PCM instead of transcribing a partial answer. [isStopRequested]
 * is polled between 100 ms chunks, so stop and screen-leave both release the microphone
 * promptly.
 */
internal suspend fun captureSpeechWav(
    maxSeconds: Int,
    maxBytes: Long,
    isStopRequested: () -> Boolean,
    onTick: (seconds: Float, level: Float) -> Unit
): ByteArray? = withContext(Dispatchers.IO) {
    val minimum = AudioRecord.getMinBufferSize(
        SPEECH_SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    if (minimum <= 0) throw SpeechCaptureException("设备不支持 16 kHz 录音")
    val record = try {
        AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SPEECH_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum * 2, 8192)
        )
    } catch (_: Throwable) {
        throw SpeechCaptureException("无法打开麦克风")
    }
    if (record.state != AudioRecord.STATE_INITIALIZED) {
        record.release()
        throw SpeechCaptureException("无法打开麦克风")
    }
    val pcm = ByteArrayOutputStream()
    try {
        record.startRecording()
        val buffer = ShortArray(SPEECH_SAMPLE_RATE / 10)
        val startedAt = SystemClock.elapsedRealtime()
        // The WAV header takes 44 bytes; dsh rejects a body beyond its intake limit.
        val dataLimit = (maxBytes - 44L).coerceAtLeast(0L)
        while (!isStopRequested()) {
            val read = record.read(buffer, 0, buffer.size)
            if (read <= 0) continue
            var peak = 0
            val chunk = ByteArray(read * 2)
            for (index in 0 until read) {
                val value = buffer[index].toInt()
                peak = maxOf(peak, abs(value))
                chunk[index * 2] = (value and 0xFF).toByte()
                chunk[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
            val room = (dataLimit - pcm.size()).toInt()
            if (room <= 0) break
            if (chunk.size <= room) pcm.write(chunk) else {
                pcm.write(chunk, 0, room)
                break
            }
            val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000f
            onTick(elapsed, peak / 32768f)
            if (elapsed >= maxSeconds) break
        }
    } finally {
        runCatching { record.stop() }
        record.release()
    }
    val data = pcm.toByteArray()
    pcm.reset()
    // Under ~100 ms is a stray tap, not speech.
    if (data.size < SPEECH_SAMPLE_RATE * 2 / 10) null else wavFile(data)
}

/** Wraps PCM16 mono samples in a canonical 44-byte WAV header. */
internal fun wavFile(pcm: ByteArray): ByteArray {
    val header = ByteArray(44)
    fun ascii(offset: Int, text: String) {
        for (index in text.indices) header[offset + index] = text[index].code.toByte()
    }
    fun int(offset: Int, value: Int) {
        header[offset] = value.toByte()
        header[offset + 1] = (value ushr 8).toByte()
        header[offset + 2] = (value ushr 16).toByte()
        header[offset + 3] = (value ushr 24).toByte()
    }
    fun short(offset: Int, value: Int) {
        header[offset] = value.toByte()
        header[offset + 1] = (value ushr 8).toByte()
    }
    val byteRate = SPEECH_SAMPLE_RATE * 2
    ascii(0, "RIFF")
    int(4, 36 + pcm.size)
    ascii(8, "WAVE")
    ascii(12, "fmt ")
    int(16, 16)
    short(20, 1)
    short(22, 1)
    int(24, SPEECH_SAMPLE_RATE)
    int(28, byteRate)
    short(32, 2)
    short(34, 16)
    ascii(36, "data")
    int(40, pcm.size)
    return header + pcm
}

/** A hollow microphone, drawn rather than pulling in the whole icon pack. */
@Composable
internal fun MicGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val stroke = height * 0.085f
        val capsuleWidth = width * 0.26f
        drawRoundRect(
            color = tint,
            topLeft = Offset((width - capsuleWidth) / 2f, height * 0.10f),
            size = Size(capsuleWidth, height * 0.44f),
            cornerRadius = CornerRadius(capsuleWidth / 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(width * 0.24f, height * 0.30f),
            size = Size(width * 0.52f, height * 0.46f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(
            color = tint,
            start = Offset(width / 2f, height * 0.78f),
            end = Offset(width / 2f, height * 0.92f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** The bar above the composer while the microphone is open. */
@Composable
internal fun VoiceRecordingDock(
    seconds: Float,
    level: Float,
    stopping: Boolean,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, Border.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Red)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "录音中 ${seconds.toInt()}s",
            color = TextPrimary,
            fontSize = 12.sp
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SurfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(level.coerceIn(0.03f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentSoft)
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onCancel, enabled = !stopping) {
            Text("取消", color = TextMuted, fontSize = 11.5.sp)
        }
        TextButton(onClick = onStop, enabled = !stopping) {
            Text("停止", color = AccentSoft, fontSize = 11.5.sp)
        }
    }
}

/** "转写中" shares the recording bar's place so the composer does not jump twice. */
@Composable
internal fun VoiceTranscribingDock(onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, Border.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(AccentSoft)
        )
        Spacer(Modifier.width(8.dp))
        Text("正在转写…", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel) {
            Text("取消", color = TextMuted, fontSize = 11.5.sp)
        }
    }
}

/**
 * The web's install gate, in one dialog: what will be downloaded, how big it is, and
 * whether a Host-owned preparation task is already moving.
 */
@Composable
internal fun SpeechSetupDialog(
    provider: AgentSpeechProvider?,
    onPrepare: (AgentSpeechProvider) -> Unit,
    onCancelPreparation: (AgentSpeechProvider) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = { Text("语音识别", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (provider == null) {
                    Text("dsh 没有可用的语音识别提供方。", fontSize = 13.sp)
                } else {
                    Text(provider.name, color = TextPrimary, fontSize = 13.sp)
                    Text(speechPhaseText(provider), fontSize = 12.5.sp, lineHeight = 18.sp)
                    speechEstimateText(provider)?.let {
                        Text(it, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    if (provider.ready) {
                        Text("已就绪，关闭后点麦克风开始录音。", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            val current = provider
            when {
                current == null -> Unit
                current.preparing -> TextButton(
                    onClick = { onCancelPreparation(current) }
                ) {
                    Text("取消准备", color = TextSecondary, fontSize = 12.sp)
                }
                current.needsSetup -> TextButton(
                    onClick = { onPrepare(current) }
                ) {
                    Text("准备语音模型", color = AccentSoft, fontSize = 12.sp)
                }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TextSecondary, fontSize = 12.sp)
            }
        }
    )
}

private fun speechPhaseText(provider: AgentSpeechProvider): String = when (provider.phase) {
    "unprepared" -> "识别模型还没有准备。准备会在 Kali 内下载模型，之后可以离线转写。"
    "cancelled" -> "上次准备已取消，可以重新准备。"
    "failed" -> provider.message?.takeIf { it.isNotBlank() } ?: "准备失败，可以重试。"
    "downloading" -> {
        val total = provider.totalBytes
        if (total != null && total > 0) {
            val percent = (provider.completedBytes * 100 / total).coerceIn(0, 100)
            "下载中 $percent% · ${formatFileSize(provider.completedBytes)} / ${formatFileSize(total)}"
        } else {
            "下载中 ${formatFileSize(provider.completedBytes)}"
        }
    }
    "checking" -> "正在检查模型缓存…"
    "loading" -> "正在加载模型…"
    "waking" -> "正在唤醒识别服务…"
    "cancelling" -> "正在取消准备…"
    else -> "识别模型已就绪。"
}

private fun speechEstimateText(provider: AgentSpeechProvider): String? {
    val parts = mutableListOf<String>()
    provider.diskBytes?.takeIf { it > 0 }?.let {
        parts += "磁盘约 ${formatFileSize(it)}"
    }
    provider.memoryBytes?.takeIf { it > 0 }?.let {
        parts += "内存约 ${formatFileSize(it)}"
    }
    val min = provider.minMinutes
    val max = provider.maxMinutes
    if (min != null && max != null && min > 0) {
        parts += if (min == max) "首次准备约 $min 分钟" else "首次准备约 $min–$max 分钟"
    }
    if (parts.isEmpty()) return null
    return parts.joinToString(" · ")
}

/** Keeps the microphone button's idle colour in one place. */
@Composable
internal fun micTint(enabled: Boolean, recording: Boolean): Color = when {
    recording -> Red
    enabled -> TextSecondary
    else -> TextMuted
}

/** Accent backdrop for the recording state; unused elsewhere but keeps the composer calm. */
@Composable
internal fun micBackground(recording: Boolean): Color =
    if (recording) AccentDim.copy(alpha = 0.35f) else SurfaceVariant
