package com.kingkang.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one scroll affordance used across the app: while more content waits below, the
 * bottom edge fades into the surface instead of ending on a hard cut. It is drawn only
 * when forward scrolling is still possible, so a list already at its end stays crisp.
 *
 * Every scrollable surface (transcripts, sheets, settings pages, file lists and dialog
 * bodies) wraps its viewport in this box rather than inventing its own shadow or divider.
 */
@Composable
fun ScrollEdgeFade(
    canScrollForward: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    edgeHeight: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier) {
        content()
        if (canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(edgeHeight)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, color)))
            )
        }
    }
}
