package com.kingkang.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingkang.app.agent.AgentDirectoryListing
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.Red
import com.kingkang.app.ui.components.ScrollEdgeFade
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary

/**
 * Read-only Kali directory browser used by the workspace form. It walks one level at a
 * time through the bridge's `workspace.browse`; files never appear because every caller
 * here picks a directory.
 */
@Composable
fun DirectoryPickerDialog(
    listing: AgentDirectoryListing,
    onBrowse: (String) -> Unit,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    recentDirectories: List<String> = emptyList()
) {
    val current = listing.path.ifBlank { "/root" }
    val listState = rememberLazyListState()
    var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    // Recent picks are a shortcut, not a second browser: tapping one selects it directly,
    // so adding a workspace twice to the same folder takes one tap.
    val recents = remember(recentDirectories, current) {
        recentDirectories.filter { it != current }.take(3)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "选择目录",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val crumbs = pickerCrumbs(current)
                    crumbs.forEachIndexed { index, crumb ->
                        if (index > 0) {
                            Text("/", color = TextMuted, fontSize = 11.sp)
                        }
                        Text(
                            crumb.first,
                            color = if (index == crumbs.lastIndex) TextSecondary else Accent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = index != crumbs.lastIndex) {
                                    onBrowse(crumb.second)
                                }
                                .padding(horizontal = 2.dp, vertical = 3.dp)
                        )
                    }
                }
                if (recents.isNotEmpty()) {
                    Text(
                        "最近",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    recents.forEach { path ->
                        Text(
                            path,
                            color = Accent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(7.dp))
                                .clickable { onPick(path) }
                                .padding(vertical = 6.dp, horizontal = 2.dp)
                        )
                    }
                }
                listing.parent?.let { parent ->
                    Text(
                        "↑ 上级目录",
                        color = Accent,
                        fontSize = 11.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { onBrowse(parent) }
                            .padding(vertical = 7.dp, horizontal = 2.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (newFolder) "取消新建" else "＋ 新建文件夹",
                        color = Accent,
                        fontSize = 11.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .clickable {
                                newFolder = !newFolder
                                folderName = ""
                            }
                            .padding(vertical = 7.dp, horizontal = 2.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "刷新",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .clickable(enabled = !listing.loading) { onBrowse(current) }
                            .padding(vertical = 7.dp, horizontal = 2.dp)
                    )
                }
                if (newFolder) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = folderName,
                            onValueChange = { value -> folderName = value.take(60) },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                lineHeight = 12.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(Accent),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Surface)
                                .padding(horizontal = 9.dp, vertical = 8.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "创建",
                            color = if (folderName.isBlank() || listing.loading) {
                                TextMuted
                            } else {
                                Accent
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .clickable(enabled = folderName.isNotBlank() && !listing.loading) {
                                    onCreate(folderName.trim())
                                    folderName = ""
                                    newFolder = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.size(4.dp))
                when {
                    listing.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text("正在读取…", color = TextMuted, fontSize = 12.sp)
                    }

                    listing.error != null -> Column {
                        Text(
                            listing.error,
                            color = Red,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                        Text(
                            "重试",
                            color = Accent,
                            fontSize = 11.5.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .clickable { onBrowse(listing.path) }
                                .padding(vertical = 7.dp, horizontal = 2.dp)
                        )
                    }

                    listing.entries.isEmpty() -> Text(
                        "这个目录下没有子目录",
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    else -> ScrollEdgeFade(
                        canScrollForward = listState.canScrollForward,
                        color = Surface,
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                    LazyColumn(Modifier.fillMaxWidth(), state = listState) {
                        items(listing.entries, key = { it.path }) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onBrowse(entry.path) }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    entry.name,
                                    modifier = Modifier.weight(1f),
                                    color = TextPrimary,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("›", color = TextMuted, fontSize = 14.sp)
                            }
                        }
                    }
                    }
                }
                if (listing.truncated) {
                    Text(
                        "只显示前 300 个目录",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(current) },
                enabled = listing.path.isNotBlank() && !listing.loading && listing.error == null
            ) {
                Text(
                    "选择此目录",
                    color = if (listing.path.isNotBlank()) Accent else TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextMuted)
            }
        }
    )
}

/** `/root/lab` becomes `/`, `root`, `lab`, each with the absolute path it stands for. */
private fun pickerCrumbs(path: String): List<Pair<String, String>> {
    val clean = path.trimEnd('/').ifBlank { "/" }
    if (clean == "/") return listOf("/" to "/")
    val crumbs = mutableListOf("/" to "/")
    var accumulated = ""
    clean.split('/').filter { it.isNotBlank() }.forEach { segment ->
        accumulated += "/$segment"
        crumbs += segment to accumulated
    }
    return crumbs
}
