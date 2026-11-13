package com.kingkang.app.ui.components

import androidx.compose.ui.text.PlatformTextStyle

/**
 * Inputs drop the font's extra top/bottom padding. Compose otherwise draws the caret for
 * the whole padded line box, which makes it look higher than the text it sits next to;
 * without that padding the caret, hint and glyphs share the same vertical centre in every
 * single-line field.
 */
val CompactInputPlatformStyle = PlatformTextStyle(includeFontPadding = false)
