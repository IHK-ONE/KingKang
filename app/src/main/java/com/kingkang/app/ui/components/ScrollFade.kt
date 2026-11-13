package com.kingkang.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one scroll affordance used across the app: while more content waits below, the
 * bottom edge fades out instead of ending on a hard cut. It is applied only when forward
 * scrolling is still possible, so a list already at its end stays crisp.
 *
 * The fade is a mask on the content itself rather than a coloured stripe painted on top,
 * so it works on any background: white pages, grey dialog bodies, cards and images all
 * fade into whatever is actually behind them. Callers therefore never pick a colour.
 */
@Composable
fun ScrollEdgeFade(
    canScrollForward: Boolean,
    modifier: Modifier = Modifier,
    edgeHeight: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        if (canScrollForward) modifier.edgeFadeMask(edgeHeight) else modifier
    ) {
        content()
    }
}

@Composable
private fun Modifier.edgeFadeMask(edgeHeight: Dp): Modifier {
    val edgePx = with(LocalDensity.current) { edgeHeight.toPx() }
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (size.height > 0f) {
                val edge = edgePx.coerceIn(0f, size.height)
                val solidStop = ((size.height - edge) / size.height).coerceIn(0f, 1f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        solidStop to Color.Black,
                        1f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
        }
}
