package com.kingkang.app.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.kingkang.app.MainViewModel
import com.kingkang.app.agent.AgentFileEntry
import com.kingkang.app.agent.AgentFileListing
import com.kingkang.app.agent.AgentFilePreview
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.AccentDim
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.theme.Background
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.Red
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.SurfaceRaised
import com.kingkang.app.ui.theme.SurfaceVariant
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary

/**
 * The open conversation's workspace as a file list. It reads one directory at a time
 * through the bridge, confines every path to the Session's cwd, and offers read-only
 * actions: preview, share, copy the path, or open a terminal there.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenTerminal: (String) -> Unit
) {
    val state by vm.agentState.collectAsState()
    val listing = state.fileListing
    val root = state.activeSession?.cwd?.takeIf { it.isNotBlank() }
    var query by rememberSaveable { mutableStateOf("") }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var sortName by rememberSaveable { mutableStateOf(FileSort.Name.name) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var previewPath by rememberSaveable { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<AgentFileEntry?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    val sort = FileSort.entries.firstOrNull { it.name == sortName } ?: FileSort.Name

    LaunchedEffect(state.activeSessionId) { vm.browseFiles(null) }
    // dsh announces file changes during a turn; reload what is on screen so agent edits
    // appear without a pull-to-refresh gesture.
    LaunchedEffect(state.fileRevision) {
        if (state.fileListing.path.isNotBlank()) vm.browseFiles(state.fileListing.path)
    }
    val clipboard = LocalClipboardManager.current

    val preview = previewPath
    if (preview != null) {
        FilePreviewPane(
            vm = vm,
            path = preview,
            onBack = {
                previewPath = null
                vm.closeFilePreview()
            }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        FilesTopBar(
            root = root ?: listing.root,
            path = listing.path,
            onBack = onBack,
            onRefresh = { vm.browseFiles(listing.path.ifBlank { null }) }
        )
        FilesSearchField(value = query, onValueChange = { query = it.take(60) })
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileSort.entries.forEach { option ->
                FilterChip(
                    label = option.label,
                    selected = option == sort,
                    onClick = {
                        if (option == sort) ascending = !ascending else {
                            sortName = option.name
                            ascending = true
                        }
                    }
                )
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                label = if (showHidden) "含隐藏" else "隐藏项",
                selected = showHidden,
                onClick = { showHidden = !showHidden }
            )
        }
        val crumbs = fileCrumbs(listing.root.ifBlank { root.orEmpty() }, listing.path)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            crumbs.forEachIndexed { index, crumb ->
                if (index > 0) {
                    Text("›", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 3.dp))
                }
                Text(
                    crumb.label,
                    color = if (index == crumbs.lastIndex) TextSecondary else Accent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = index != crumbs.lastIndex) {
                            vm.browseFiles(crumb.path)
                        }
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                )
            }
        }
        HorizontalDivider(color = Border.copy(alpha = 0.7f))

        val visible = remember(listing, sort, ascending, showHidden, query) {
            sortFileEntries(listing.entries, sort, ascending, showHidden, query)
        }
        Box(Modifier.weight(1f)) {
            when {
                root == null -> FileMessage("这个对话没有工作目录")
                listing.loading && listing.entries.isEmpty() -> Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在读取目录…", color = TextMuted, fontSize = 12.sp)
                }
                listing.error != null && listing.entries.isEmpty() -> Column {
                    Text(
                        listing.error,
                        color = Red,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    Text(
                        "重试",
                        color = Accent,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.browseFiles(listing.path) }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                visible.isEmpty() -> FileMessage(
                    if (query.isNotBlank()) "没有匹配「$query」的文件" else "这个目录是空的"
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    if (listing.truncated) {
                        item(key = "truncated") {
                            Text(
                                "目录较大，只列出前 500 项；在终端中查看全部。",
                                color = Amber,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                    items(visible, key = { it.path }) { entry ->
                        FileRow(
                            entry = entry,
                            onClick = {
                                when {
                                    entry.directory -> vm.browseFiles(entry.path)
                                    entry.type == "link" -> vm.notify("符号链接不展开，可在终端中查看")
                                    else -> {
                                        previewPath = entry.path
                                        vm.openFilePreview(entry.path)
                                    }
                                }
                            },
                            onLongClick = {
                                actionsFor = entry
                                showSheet = true
                            }
                        )
                    }
                }
            }
        }
    }

    val actionTarget = actionsFor
    if (showSheet && actionTarget != null) {
        FileActionsSheet(
            entry = actionTarget,
            onDismiss = { showSheet = false },
            onPreview = {
                showSheet = false
                if (!actionTarget.directory && actionTarget.type != "link") {
                    previewPath = actionTarget.path
                    vm.openFilePreview(actionTarget.path)
                }
            },
            onShare = {
                showSheet = false
                vm.openGuestFile(actionTarget.path, share = true)
            },
            onOpenExternally = {
                showSheet = false
                vm.openGuestFile(actionTarget.path, share = false)
            },
            onCopyPath = {
                showSheet = false
                clipboard.setText(AnnotatedString(actionTarget.path))
                vm.notify("已复制路径")
            },
            onOpenTerminal = {
                showSheet = false
                onOpenTerminal(if (actionTarget.directory) actionTarget.path else actionTarget.path.substringBeforeLast('/'))
            }
        )
    }
}

@Composable
private fun FilesTopBar(
    root: String,
    path: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = TextSecondary)
        }
        Column(Modifier.weight(1f)) {
            Text("文件", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                if (path.isBlank()) root else path,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "刷新",
            color = TextSecondary,
            fontSize = 11.5.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRefresh)
                .padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun FilesSearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceRaised)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("筛选当前目录", color = TextMuted, fontSize = 12.5.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = TextPrimary, fontSize = 12.5.sp),
                cursorBrush = SolidColor(Accent),
                singleLine = true
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "清除筛选",
                tint = TextMuted,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onValueChange("") }
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) TextPrimary else TextMuted,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AccentDim else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: AgentFileEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (entry.directory) "▸" else "·",
            color = if (entry.directory) Accent else TextMuted,
            fontSize = if (entry.directory) 13.sp else 15.sp,
            modifier = Modifier.width(18.dp)
        )
        Text(
            entry.name,
            modifier = Modifier.weight(1f),
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = if (entry.directory) FontFamily.Default else FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                entry.type == "link" -> "链接"
                entry.directory -> ""
                else -> formatFileSize(entry.size)
            },
            color = TextMuted,
            fontSize = 9.5.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun FileMessage(text: String) {
    Text(
        text,
        color = TextMuted,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileActionsSheet(
    entry: AgentFileEntry,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    onOpenExternally: () -> Unit,
    onCopyPath: () -> Unit,
    onOpenTerminal: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
        ) {
            Text(
                entry.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                entry.path,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            if (!entry.directory && entry.type != "link") {
                SheetRow("预览", onClick = onPreview)
                SheetRow("用其他应用打开", onClick = onOpenExternally)
                SheetRow("分享或保存", onClick = onShare)
            }
            SheetRow("复制路径", onClick = onCopyPath)
            SheetRow(
                if (entry.directory) "在终端中打开该目录" else "在终端中打开所在目录",
                onClick = onOpenTerminal
            )
        }
    }
}

@Composable
private fun SheetRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = TextPrimary,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp)
    )
}

/** Decodes the preview once per (bytes, charset) and keeps the viewer's own toggles. */
@Composable
private fun FilePreviewPane(vm: MainViewModel, path: String, onBack: () -> Unit) {
    val state by vm.agentState.collectAsState()
    val preview = state.filePreview
    val bytes = remember(preview.base64) {
        runCatching { Base64.decode(preview.base64, Base64.DEFAULT) }.getOrNull()
    }
    val kind = remember(preview.name, bytes) {
        fileKindOf(preview.name.ifBlank { path.substringAfterLast('/') }, bytes)
    }
    var wrap by rememberSaveable { mutableStateOf(true) }
    var charsetChoice by rememberSaveable { mutableStateOf("auto") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回文件列表", tint = TextSecondary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    preview.name.ifBlank { path.substringAfterLast('/') },
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatFileSize(preview.size),
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
            Text(
                "分享",
                color = TextSecondary,
                fontSize = 11.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.openGuestFile(path, share = true) }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
        when {
            preview.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
            preview.error != null -> Column(Modifier.padding(18.dp)) {
                Text(preview.error, color = Red, fontSize = 12.sp, lineHeight = 18.sp)
                Text(
                    "重试",
                    color = Accent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.openFilePreview(path) }
                        .padding(vertical = 8.dp)
                )
            }
            bytes == null -> FileMessage("无法读取这个文件")
            else -> {
                if (preview.truncated) {
                    TruncatedNotice(
                        size = preview.size,
                        onOpenExternally = { vm.openGuestFile(path, share = false) }
                    )
                }
                when (kind) {
                    FileKind.Image -> ZoomableImage(bytes, preview.name.ifBlank { path })
                    FileKind.Pdf, FileKind.Binary -> BinaryPreview(
                        kind = kind,
                        size = preview.size,
                        onOpenExternally = { vm.openGuestFile(path, share = false) }
                    )
                    FileKind.Markdown -> MarkdownPreview(bytes, charsetChoice, onCharset = { charsetChoice = it })
                    FileKind.Text -> TextPreview(
                        bytes = bytes,
                        wrap = wrap,
                        onToggleWrap = { wrap = !wrap },
                        charsetChoice = charsetChoice,
                        onCharset = { charsetChoice = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun TruncatedNotice(size: Long, onOpenExternally: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceRaised)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "文件 ${formatFileSize(size)}，仅显示前 4 MB",
            modifier = Modifier.weight(1f),
            color = Amber,
            fontSize = 10.5.sp,
            lineHeight = 15.sp
        )
        Text(
            "完整打开",
            color = Accent,
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onOpenExternally)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun CharsetRow(choice: String, onChange: (String) -> Unit) {
    val options = listOf("auto" to "自动", "UTF-8" to "UTF-8", "GB18030" to "GB18030")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("编码", color = TextMuted, fontSize = 10.5.sp)
        Spacer(Modifier.width(8.dp))
        options.forEach { (value, label) ->
            FilterChip(label = label, selected = choice == value, onClick = { onChange(value) })
            Spacer(Modifier.width(5.dp))
        }
    }
}

@Composable
private fun TextPreview(
    bytes: ByteArray,
    wrap: Boolean,
    onToggleWrap: () -> Unit,
    charsetChoice: String,
    onCharset: (String) -> Unit
) {
    val decoded = remember(bytes, charsetChoice) {
        decodePreview(bytes, if (charsetChoice == "auto") null else charsetChoice)
    }
    val lines = remember(decoded.text) { decoded.text.split('\n') }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            label = if (wrap) "自动换行" else "不换行",
            selected = wrap,
            onClick = onToggleWrap
        )
        Spacer(Modifier.width(6.dp))
        if (decoded.garbled) {
            Text("编码可能不对", color = Amber, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(1f))
        Text("${lines.size} 行", color = TextMuted, fontSize = 10.sp)
    }
    CharsetRow(choice = charsetChoice, onChange = onCharset)
    if (wrap) {
        val visible = if (lines.size > 5_000) lines.take(5_000) else lines
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (lines.size > visible.size) {
                item(key = "more-lines") {
                    Text(
                        "只显示前 5000 行，完整内容请用其他应用打开。",
                        color = Amber,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
            items(visible.size) { index ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "${index + 1}",
                        color = TextMuted.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .width(38.dp)
                            .padding(end = 8.dp),
                        maxLines = 1
                    )
                    Text(
                        visible[index],
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    } else {
        val text = remember(decoded.text) {
            if (decoded.text.length > 200_000) decoded.text.take(200_000) else decoded.text
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text,
                color = TextPrimary,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace,
                softWrap = false
            )
            if (decoded.text.length > text.length) {
                Text(
                    "只显示前 20 万字符，完整内容请用其他应用打开。",
                    color = Amber,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun MarkdownPreview(bytes: ByteArray, charsetChoice: String, onCharset: (String) -> Unit) {
    val decoded = remember(bytes, charsetChoice) {
        decodePreview(bytes, if (charsetChoice == "auto") null else charsetChoice)
    }
    val blocks = remember(decoded.text) { fileMarkdownBlocks(decoded.text) }
    CharsetRow(choice = charsetChoice, onChange = onCharset)
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(blocks) { block ->
            when (block) {
                is FileMarkdownBlock.Heading -> Text(
                    inlineMarkdown(block.text),
                    color = TextPrimary,
                    fontSize = when (block.level) {
                        1 -> 19.sp
                        2 -> 17.sp
                        3 -> 15.sp
                        else -> 14.sp
                    },
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 3.dp)
                )
                is FileMarkdownBlock.Paragraph -> Text(
                    inlineMarkdown(block.text),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
                is FileMarkdownBlock.Bullet -> Row(Modifier.padding(vertical = 2.dp)) {
                    Text(
                        block.marker,
                        color = TextMuted,
                        fontSize = 12.5.sp,
                        modifier = Modifier.width(22.dp)
                    )
                    Text(
                        inlineMarkdown(block.text),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
                is FileMarkdownBlock.Quote -> Text(
                    inlineMarkdown(block.text),
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(SurfaceRaised)
                        .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 8.dp)
                )
                is FileMarkdownBlock.Code -> Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .padding(10.dp)
                ) {
                    if (block.language.isNotBlank()) {
                        Text(block.language, color = TextMuted, fontSize = 9.5.sp)
                    }
                    Text(
                        block.text,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                FileMarkdownBlock.Rule -> HorizontalDivider(
                    color = Border,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

/** Bold and inline code are enough for a read-only preview; links stay plain text. */
private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append("**")
                    index += 2
                }
            }
            text.startsWith("`", index) -> {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x14808080)
                        )
                    ) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append('`')
                    index++
                }
            }
            text.startsWith("_", index) -> {
                val end = text.indexOf('_', index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append('_')
                    index++
                }
            }
            else -> {
                append(text[index])
                index++
            }
        }
    }
}

@Composable
private fun BinaryPreview(kind: FileKind, size: Long, onOpenExternally: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (kind == FileKind.Pdf) "PDF 文档" else "二进制文件",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatFileSize(size)} · 预览只读，不解析内容",
            color = TextMuted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "用其他应用打开",
            color = Accent,
            fontSize = 12.5.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceVariant)
                .clickable(onClick = onOpenExternally)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun ZoomableImage(bytes: ByteArray, name: String) {
    val bitmap = remember(bytes) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2_048 || bounds.outHeight / sample > 2_048) {
            sample *= 2
        }
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }
    if (bitmap == null) {
        FileMessage("无法解码这张图片，可尝试用其他应用打开")
        return
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
