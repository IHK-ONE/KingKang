package com.kingkang.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.AccentSoft
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.SurfaceVariant
import com.kingkang.app.ui.theme.SurfaceRaised
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary

@Composable
fun TerminalMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    filled: Boolean = true
) {
    val shape = RoundedCornerShape(size * 0.28f)
    val baseModifier = modifier
        .size(size)
        .then(
            if (filled) {
                Modifier
                    .background(SurfaceRaised, shape)
                    .border(1.dp, Border, shape)
            } else {
                Modifier.border(1.dp, Border, shape)
            }
        )
    Box(baseModifier, contentAlignment = Alignment.Center) {
        Text(
            text = ">_",
            color = if (filled) TextSecondary else TextMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.31f).sp,
            letterSpacing = (-0.5).sp
        )
    }
}

@Composable
fun ChatGlyph(
    modifier: Modifier = Modifier,
    color: Color = TextSecondary
) {
    Canvas(modifier.size(22.dp)) {
        val strokeWidth = 1.7.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        val radius = CornerRadius(5.dp.toPx())
        val bubbleHeight = size.height * 0.66f
        drawRoundRect(
            color = color,
            topLeft = Offset(strokeWidth, strokeWidth),
            size = Size(size.width - strokeWidth * 2f, bubbleHeight),
            cornerRadius = radius,
            style = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.28f, bubbleHeight),
            end = Offset(size.width * 0.24f, size.height - strokeWidth),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.24f, size.height - strokeWidth),
            end = Offset(size.width * 0.45f, bubbleHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun KeyboardGlyph(
    modifier: Modifier = Modifier,
    color: Color = TextSecondary
) {
    Canvas(modifier.size(22.dp)) {
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke, size.height * 0.27f),
            size = Size(size.width - stroke * 2f, size.height * 0.5f),
            cornerRadius = CornerRadius(3.5.dp.toPx()),
            style = Stroke(width = stroke)
        )
        val dot = 1.5.dp.toPx()
        val rowY = size.height * 0.44f
        for (i in 0..3) {
            drawCircle(
                color = color,
                radius = dot,
                center = Offset(size.width * (0.24f + i * 0.17f), rowY)
            )
        }
        drawLine(
            color = color,
            start = Offset(size.width * 0.28f, size.height * 0.62f),
            end = Offset(size.width * 0.72f, size.height * 0.62f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TerminalMark(size = if (compact) 30.dp else 36.dp)
        androidx.compose.foundation.layout.Column {
            Text(
                text = "KINGKANG",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 13.sp else 14.sp,
                letterSpacing = 2.2.sp
            )
            Text(
                text = "LOCAL SECURITY WORKSPACE",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = if (compact) 7.5.sp else 8.sp,
                letterSpacing = 1.1.sp
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Accent,
    dot: Boolean = true
) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (dot) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(color, RoundedCornerShape(50))
            )
        }
        Text(
            text = text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
fun MonoTag(
    text: String,
    color: Color = TextMuted,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .background(SurfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .border(1.dp, Border.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        letterSpacing = 0.7.sp
    )
}

@Composable
fun SectionEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = TextMuted,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.6.sp
    )
}

@Composable
fun Dot(color: Color = Accent, size: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(color, RoundedCornerShape(50))
    )
}

@Composable
fun InlineCode(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .background(SurfaceVariant.copy(alpha = 0.8f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = AccentSoft,
        fontFamily = FontFamily.Monospace,
        fontSize = MaterialTheme.typography.bodySmall.fontSize,
        letterSpacing = 0.sp
    )
}
