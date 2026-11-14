package com.kingkang.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.kingkang.app.ui.components.CompactInputPlatformStyle
import com.kingkang.app.ui.components.KkDialog
import com.kingkang.app.ui.components.KkDialogActions
import com.kingkang.app.ui.components.KkDialogTitle
import com.kingkang.app.ui.components.ScrollEdgeFade
import com.kingkang.app.ui.components.keyboardLift
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.OnAccent
import com.kingkang.app.ui.theme.Red
import com.kingkang.app.ui.theme.SurfaceVariant
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary

/**
 * Read-only Kali directory browser used by the workspace form. It walks one level at a
 * time through the bridge's `workspace.browse`; files never appear because every caller
 * here picks a directory.
 *
 * The layout answers three questions in order: where am I (path card), how do I go
 * deeper (folder rows and 上一级), and how do I confirm (one filled button). Recent
 * directories are shortcuts that pick directly, so re-adding a familiar folder is one tap.
 */
@Composable
fun DirectoryPickerDialog(
    listing: AgentDirectoryListing,
    onBrowse: (String) -> Unit,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val current = listing.path.ifBlank { "/root" }
    val listState = rememberLazyListState()
    var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    val parent = listing.parent
    val canPick = listing.path.isNotBlank() && !listing.loading && listing.error == null

    KkDialog(onDismiss = onDismiss, padding = PaddingValues(20.dp)) {
        KkDialogTitle(
            title = "选择工作区目录",
            subtitle = "点目录进入下一层，选好后用底部按钮确认",
            onDismiss = onDismiss
        )
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text("当前目录", color = TextMuted, fontSize = 9.5.sp)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    current,
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (parent != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "↑ 上一级",
                        color = Accent,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !listing.loading) { onBrowse(parent) }
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
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
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "刷新",
                color = TextSecondary,
                fontSize = 11.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(enabled = !listing.loading) { onBrowse(current) }
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            )
        }
        if (newFolder) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(SurfaceVariant)
                        .border(1.dp, Border, RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = folderName,
                        onValueChange = { value -> folderName = value.take(60) },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            lineHeight = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            platformStyle = CompactInputPlatformStyle
                        ),
                        cursorBrush = SolidColor(Accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(17.dp)
                            .keyboardLift(),
                        decorationBox = { inner ->
                            if (folderName.isEmpty()) {
                                Text(
                                    "新文件夹名称",
                                    color = TextMuted,
                                    fontSize = 12.5.sp,
                                    lineHeight = 12.5.sp,
                                    style = TextStyle(platformStyle = CompactInputPlatformStyle)
                                )
                            }
                            inner()
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        onCreate(folderName.trim())
                        folderName = ""
                        newFolder = false
                    },
                    enabled = folderName.isNotBlank() && !listing.loading,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "创建",
                        color = if (folderName.isNotBlank() && !listing.loading) {
                            Accent
                        } else {
                            TextMuted
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("子目录", color = TextMuted, fontSize = 9.5.sp)
        Spacer(Modifier.height(2.dp))
        when {
            listing.loading -> Row(
                Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text("正在读取…", color = TextMuted, fontSize = 12.sp)
            }

            listing.error != null -> Column(Modifier.padding(vertical = 4.dp)) {
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
                        .clickable { onBrowse(current) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                )
            }

            listing.entries.isEmpty() -> Text(
                "这个目录下没有子目录。可以直接使用当前目录，或新建一个文件夹。",
                color = TextMuted,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            else -> ScrollEdgeFade(
                canScrollForward = listState.canScrollForward,
                modifier = Modifier.heightIn(max = 210.dp)
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
                            Text("▸", color = TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                entry.name,
                                modifier = Modifier.weight(1f),
                                color = TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("进入", color = TextMuted, fontSize = 10.5.sp)
                        }
                    }
                }
            }
        }
        if (listing.truncated) {
            Text(
                "只显示前 300 个目录，可以在上方路径框直接填写更深的目录。",
                color = TextMuted,
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        KkDialogActions {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = { onPick(current) },
                enabled = canPick,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent,
                    disabledContainerColor = SurfaceVariant,
                    disabledContentColor = TextMuted
                )
            ) {
                Text("使用此目录", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
