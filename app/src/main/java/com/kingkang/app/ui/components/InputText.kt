package com.kingkang.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import kotlinx.coroutines.delay

/**
 * Inputs drop the font's extra top/bottom padding. Compose otherwise draws the caret for
 * the whole padded line box, which makes it look higher than the text it sits next to;
 * without that padding the caret, hint and glyphs share the same vertical centre in every
 * single-line field.
 */
val CompactInputPlatformStyle = PlatformTextStyle(includeFontPadding = false)

/**
 * Keeps the focused field above the keyboard. The page still scrolls normally; when the
 * IME appears (or focus moves while it is open) the field asks its scroll parent to bring
 * it into view, which is what makes a card near the bottom of a long settings page usable
 * instead of sitting hidden under the keyboard. Fields that are not focused do nothing, so
 * one page can carry the modifier on several inputs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.keyboardLift(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom, focused) {
        if (imeBottom > 0 && focused) {
            // Let the inset padding settle before scrolling; doing it in the same frame
            // lands the field just under the keyboard edge.
            delay(120)
            runCatching { requester.bringIntoView() }
        }
    }
    return this
        .onFocusChanged { focused = it.isFocused }
        .bringIntoViewRequester(requester)
}
