package com.kingkang.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingkang.app.MainViewModel
import com.kingkang.app.ui.components.ScrollEdgeFade
import com.kingkang.app.agent.AgentConnection
import com.kingkang.app.agent.AgentSettingEdit
import com.kingkang.app.agent.AgentSettingField
import com.kingkang.app.agent.AgentSettingGroup
import com.kingkang.app.agent.AgentSettingKind
import com.kingkang.app.agent.AgentSettingsSnapshot
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.Red
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.SurfaceVariant
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Native forms for dsh settings (DSH-502). Values come from `settings/describe`; only
 * fields the user actually changed are sent back, and structured fields stay visible with
 * a Harness pointer instead of disappearing. Secret values are write-only.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgentSettingsScreen(
    vm: MainViewModel,
    initialGroup: String?,
    onBack: () -> Unit,
    onOpenHarness: () -> Unit
) {
    val agent by vm.agentState.collectAsState()
    val settings = agent.agentSettings
    val ready = agent.connection == AgentConnection.Ready
    val pageScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val anchors = remember { mutableMapOf<String, BringIntoViewRequester>() }
    var savedNote by remember { mutableStateOf<String?>(null) }
    var savingNs by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ready) {
        if (ready) vm.refreshAgentSettings()
    }
    LaunchedEffect(initialGroup, settings.loaded) {
        val target = initialGroup ?: return@LaunchedEffect
        if (!settings.loaded) return@LaunchedEffect
        delay(180)
        anchors[target]?.let { runCatching { it.bringIntoView() } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(com.kingkang.app.ui.theme.Background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = TextSecondary)
            }
            Text(
                "dsh 设置",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { vm.refreshAgentSettings() },
                enabled = ready && !settings.loading
            ) {
                Text(
                    if (settings.loading) "读取中…" else "刷新",
                    color = if (ready) Accent else TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        ScrollEdgeFade(
            canScrollForward = pageScroll.canScrollForward,
            color = com.kingkang.app.ui.theme.Background,
            modifier = Modifier.weight(1f)
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(pageScroll)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                "这些设置直接写入 dsh 的配置，修改后立即生效。包含列表或嵌套结构的项仍由 Harness 编辑。",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            when {
                !ready -> MessageCard("Agent 尚未连接，连接后可在这里修改 dsh 设置。")
                settings.error != null && settings.groups.isEmpty() -> {
                    MessageCard("读取设置失败：${settings.error}")
                    TextButton(onClick = { vm.refreshAgentSettings() }) {
                        Text("重试", color = Accent, fontSize = 12.sp)
                    }
                }
                !settings.loaded -> LoadingCard("正在读取 dsh 设置…")
                else -> Unit
            }

            if (savedNote != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    savedNote ?: "",
                    color = Accent,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            settings.groups.forEach { group ->
                Spacer(Modifier.height(22.dp))
                AgentSettingsLabel(group.title)
                Spacer(Modifier.height(8.dp))
                val requester = remember(group.ns) { BringIntoViewRequester() }
                anchors[group.ns] = requester
                Box(Modifier.bringIntoViewRequester(requester)) {
                    SettingGroupCard(
                        group = group,
                        globalWritable = settings.writable,
                        saving = savingNs == group.ns,
                        onOpenHarness = onOpenHarness,
                        onSave = { edits ->
                            scope.launch {
                                savingNs = group.ns
                                savedNote = null
                                try {
                                    vm.saveAgentSettings(group.ns, group.revision, edits)
                                    savedNote = "已保存「${group.title}」"
                                } catch (t: Throwable) {
                                    savedNote = "保存失败：${t.message ?: "未知错误"}"
                                } finally {
                                    savingNs = null
                                }
                            }
                        }
                    )
                }
            }

            if (settings.loaded && settings.harnessOnlyCount > 0) {
                Spacer(Modifier.height(22.dp))
                SettingsCard {
                    Text(
                        "其余 ${settings.harnessOnlyCount} 项 dsh 设置在 Harness 中调整",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "例如模型路由、插件自身配置和结构化列表。原生页面只覆盖常用标量项。",
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenHarness, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("打开 Harness", color = Accent, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
        }
    }
}

@Composable
private fun SettingGroupCard(
    group: AgentSettingGroup,
    globalWritable: Boolean,
    saving: Boolean,
    onOpenHarness: () -> Unit,
    onSave: (List<AgentSettingEdit>) -> Unit
) {
    // Drafts reset whenever dsh's group view changes (a save returns a new revision), so
    // the form never keeps a value dsh has already replaced.
    val texts = remember(group) {
        mutableStateMapOf<String, String>().apply {
            group.fields.forEach { field -> put(field.key, field.text) }
        }
    }
    val toggles = remember(group) {
        mutableStateMapOf<String, Boolean>().apply {
            group.fields.forEach { field -> put(field.key, field.checked) }
        }
    }
    val cleared = remember(group) { mutableStateMapOf<String, Boolean>() }
    var errors by remember(group) { mutableStateOf<Map<String, String>>(emptyMap()) }

    SettingsCard {
        Text(
            group.subtitle?.takeIf { it.isNotBlank() } ?: group.title,
            color = TextMuted,
            fontSize = 10.5.sp,
            lineHeight = 15.sp
        )
        if (group.note != null) {
            Spacer(Modifier.height(6.dp))
            Text(group.note, color = Amber, fontSize = 11.sp, lineHeight = 16.sp)
        }
        group.fields.forEach { field ->
            Spacer(Modifier.height(14.dp))
            when (field.kind) {
                AgentSettingKind.Unsupported -> UnsupportedField(field, onOpenHarness)
                AgentSettingKind.Toggle -> {
                    val checked = toggles[field.key] ?: field.checked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = globalWritable) {
                                toggles[field.key] = !checked
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            FieldLabel(field)
                            field.hint?.let { HintText(it) }
                        }
                        Switch(
                            checked = checked,
                            onCheckedChange = { toggles[field.key] = it },
                            enabled = globalWritable,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Accent,
                                checkedTrackColor = Accent.copy(alpha = 0.35f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceVariant
                            )
                        )
                    }
                }
                AgentSettingKind.Choice -> {
                    FieldLabel(field)
                    field.hint?.let { HintText(it) }
                    Spacer(Modifier.height(7.dp))
                    ChoiceSegmented(
                        options = field.options.map { it.value to it.label },
                        selected = texts[field.key] ?: field.text
                    ) { value ->
                        if (globalWritable) texts[field.key] = value
                    }
                }
                AgentSettingKind.Secret -> SecretField(
                    field = field,
                    value = texts[field.key] ?: "",
                    cleared = cleared[field.key] == true,
                    enabled = globalWritable,
                    error = errors[field.key],
                    onChange = { value ->
                        texts[field.key] = value
                        if (value.isNotEmpty()) cleared[field.key] = false
                    },
                    onClear = {
                        texts[field.key] = ""
                        cleared[field.key] = true
                    }
                )
                AgentSettingKind.Text, AgentSettingKind.Number -> TextSettingField(
                    field = field,
                    value = texts[field.key] ?: "",
                    enabled = globalWritable,
                    error = errors[field.key],
                    onChange = { value -> texts[field.key] = value }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dirty = isDirty(group, texts, toggles, cleared)
            Box(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (globalWritable && dirty) Accent else SurfaceVariant)
                    .clickable(enabled = globalWritable && dirty && !saving) {
                        val found = mutableMapOf<String, String>()
                        group.fields.forEach { field ->
                            field.validateDraft(
                                text = texts[field.key] ?: field.text,
                                checked = toggles[field.key] ?: field.checked,
                                cleared = cleared[field.key] == true
                            )?.let { found[field.key] = it }
                        }
                        errors = found
                        if (found.isNotEmpty()) return@clickable
                        val edits = buildEdits(group, texts, toggles, cleared)
                        if (edits.isNotEmpty()) onSave(edits)
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                if (saving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = com.kingkang.app.ui.theme.OnAccent,
                            strokeWidth = 1.8.dp,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "保存中…",
                            color = com.kingkang.app.ui.theme.OnAccent,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        if (dirty) "保存" else "没有改动",
                        color = if (globalWritable && dirty) {
                            com.kingkang.app.ui.theme.OnAccent
                        } else {
                            TextMuted
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (!globalWritable) {
                Spacer(Modifier.width(10.dp))
                Text("当前 dsh 配置不可写", color = Amber, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun FieldLabel(field: AgentSettingField) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            field.label,
            color = TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
        if (field.required) {
            Spacer(Modifier.width(4.dp))
            Text("必填", color = Amber, fontSize = 9.sp)
        }
    }
}

@Composable
private fun HintText(text: String) {
    Spacer(Modifier.height(2.dp))
    Text(text, color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
}

@Composable
private fun UnsupportedField(field: AgentSettingField, onOpenHarness: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(field.label, color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                field.unsupportedReason ?: "这项设置需要在 Harness 中编辑",
                color = TextMuted,
                fontSize = 9.5.sp,
                lineHeight = 14.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Harness",
            color = Accent,
            fontSize = 10.5.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onOpenHarness)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TextSettingField(
    field: AgentSettingField,
    value: String,
    enabled: Boolean,
    error: String?,
    onChange: (String) -> Unit
) {
    FieldLabel(field)
    field.hint?.let { HintText(it) }
    Spacer(Modifier.height(7.dp))
    BasicTextField(
        value = value,
        onValueChange = { if (enabled) onChange(it.take(500)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (field.kind == AgentSettingKind.Number) {
                KeyboardType.Number
            } else {
                KeyboardType.Text
            },
            imeAction = ImeAction.Next
        ),
        textStyle = TextStyle(
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 12.5.sp,
            lineHeight = 12.5.sp,
            fontFamily = FontFamily.Monospace
        ),
        cursorBrush = SolidColor(Accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(SurfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    )
    field.min?.let { min ->
        val max = field.max
        Spacer(Modifier.height(3.dp))
        Text(
            if (max != null) "范围 $min – $max" else "最小值 $min",
            color = TextMuted,
            fontSize = 9.sp
        )
    }
    if (error != null) {
        Spacer(Modifier.height(3.dp))
        Text(error, color = Red, fontSize = 9.5.sp)
    }
}

@Composable
private fun SecretField(
    field: AgentSettingField,
    value: String,
    cleared: Boolean,
    enabled: Boolean,
    error: String?,
    onChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel(field)
        Spacer(Modifier.weight(1f))
        if (field.configured && !cleared && value.isEmpty()) {
            Text("已配置", color = Accent, fontSize = 9.5.sp)
        }
    }
    field.hint?.let { HintText(it) }
    Spacer(Modifier.height(7.dp))
    BasicTextField(
        value = value,
        onValueChange = { if (enabled) onChange(it.take(500)) },
        enabled = enabled,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        textStyle = TextStyle(
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 12.5.sp,
            lineHeight = 12.5.sp,
            fontFamily = FontFamily.Monospace
        ),
        cursorBrush = SolidColor(Accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(SurfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when {
                cleared -> "保存后清除已存密钥"
                field.configured -> "留空表示保持当前密钥"
                else -> "尚未配置"
            },
            color = if (cleared) Amber else TextMuted,
            fontSize = 9.sp
        )
        Spacer(Modifier.weight(1f))
        if (field.configured && !cleared) {
            Text(
                "清除",
                color = Amber,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled, onClick = onClear)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
    if (error != null) {
        Text(error, color = Red, fontSize = 9.5.sp)
    }
}

@Composable
private fun AgentSettingsLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp),
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun LoadingCard(text: String) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                color = Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(text, color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MessageCard(text: String) {
    SettingsCard {
        Text(text, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

private fun AgentSettingField.validateDraft(
    text: String,
    checked: Boolean,
    cleared: Boolean
): String? {
    if (kind == AgentSettingKind.Unsupported) return null
    if (kind == AgentSettingKind.Secret) return null
    if (kind == AgentSettingKind.Toggle) return null
    if (kind == AgentSettingKind.Choice) return null
    return copy(text = text, checked = checked).validate()
}

private fun isDirty(
    group: AgentSettingGroup,
    texts: Map<String, String>,
    toggles: Map<String, Boolean>,
    cleared: Map<String, Boolean>
): Boolean = group.fields.any { field ->
    when (field.kind) {
        AgentSettingKind.Unsupported -> false
        AgentSettingKind.Toggle -> (toggles[field.key] ?: field.checked) != field.checked
        AgentSettingKind.Secret -> cleared[field.key] == true ||
            !(texts[field.key].isNullOrEmpty())
        else -> (texts[field.key] ?: field.text) != field.text
    }
}

private fun buildEdits(
    group: AgentSettingGroup,
    texts: Map<String, String>,
    toggles: Map<String, Boolean>,
    cleared: Map<String, Boolean>
): List<AgentSettingEdit> {
    val edits = mutableListOf<AgentSettingEdit>()
    group.fields.forEach { field ->
        when (field.kind) {
            AgentSettingKind.Unsupported -> Unit
            AgentSettingKind.Toggle -> {
                val checked = toggles[field.key] ?: field.checked
                if (checked != field.checked) edits += AgentSettingEdit(field.path, checked)
            }
            AgentSettingKind.Secret -> {
                val value = texts[field.key].orEmpty()
                if (cleared[field.key] == true) {
                    edits += AgentSettingEdit(field.path, null)
                } else if (value.isNotEmpty()) {
                    edits += AgentSettingEdit(field.path, value)
                }
            }
            AgentSettingKind.Number -> {
                val value = (texts[field.key] ?: field.text).trim()
                if (value != field.text.trim() && value.isNotEmpty()) {
                    val number = value.toDoubleOrNull()
                    if (number != null) edits += AgentSettingEdit(field.path, number)
                }
            }
            AgentSettingKind.Text, AgentSettingKind.Choice -> {
                val value = texts[field.key] ?: field.text
                if (value != field.text) edits += AgentSettingEdit(field.path, value)
            }
        }
    }
    return edits
}
