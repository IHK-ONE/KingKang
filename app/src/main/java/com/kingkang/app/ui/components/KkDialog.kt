package com.kingkang.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary

/**
 * The shared dialog container. Every menu, editor and detail panel uses the same width,
 * corner radius, inner padding and maximum height, so a short menu looks short and a long
 * one scrolls inside the same frame instead of growing to a different shape.
 */
@Composable
fun KkDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.84f)
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(padding),
            content = content
        )
    }
}

/** Title row shared by dialogs: title, optional one-line hint, and a quiet close. */
@Composable
fun KkDialogTitle(
    title: String,
    subtitle: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = TextMuted, fontSize = 10.5.sp)
            }
        }
        if (onDismiss != null) {
            Text(
                "×",
                color = TextMuted,
                fontSize = 17.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/** Bottom action row shared by dialogs; text buttons align right in one consistent row. */
@Composable
fun KkDialogActions(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** A quiet secondary label used above grouped content inside a dialog. */
@Composable
fun KkDialogSectionLabel(text: String) {
    androidx.compose.material3.Text(
        text,
        color = TextSecondary,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}
