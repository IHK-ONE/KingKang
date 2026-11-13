package com.kingkang.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingkang.app.MainViewModel
import com.kingkang.app.agent.AgentConnection
import com.kingkang.app.agent.AgentModelProviders
import com.kingkang.app.agent.AgentProvider
import com.kingkang.app.agent.AgentProviderCopy
import com.kingkang.app.agent.AgentProviderDraft
import com.kingkang.app.agent.AgentProviderModel
import com.kingkang.app.agent.AgentState
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.components.ScrollEdgeFade
import com.kingkang.app.ui.theme.Background
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.theme.OnAccent
import com.kingkang.app.ui.theme.Red
import com.kingkang.app.ui.theme.Success
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.SurfaceVariant
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextPrimary
import com.kingkang.app.ui.theme.TextSecondary

/**
 * Native counterpart of dsh-web's Models page. It writes provider profiles into the
 * `llm-pi-ai` settings namespace and keys into the credential store; neither the stored
 * secret nor any key typed here is ever read back into app state or logs.
 */
@Composable
fun ProviderSettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenHarness: () -> Unit
) {
    val agent by vm.agentState.collectAsState()
    val providers = agent.modelProviders
    var editingRoute by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(agent.connection) {
        if (agent.connection == AgentConnection.Ready) vm.refreshModelProviders()
    }

    val editorOpen = creating || editingRoute != null
    val editing = editingRoute?.let { route ->
        providers.providers.firstOrNull { it.provider == route }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (editorOpen) {
                        creating = false
                        editingRoute = null
                    } else {
                        onBack()
                    }
                }
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextSecondary
                )
            }
            Text(
                if (editorOpen) {
                    if (creating) "添加供应商" else "编辑供应商"
                } else {
                    "模型与供应商"
                },
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (editorOpen) {
            ProviderEditor(
                agent = agent,
                existing = editing,
                creating = creating,
                onCancel = {
                    creating = false
                    editingRoute = null
                },
                onSaved = {
                    creating = false
                    editingRoute = null
                },
                onDelete = { route, keyRef, deleteKey ->
                    vm.removeModelProvider(route, keyRef, deleteKey, confirmed = true)
                    creating = false
                    editingRoute = null
                },
                onDiscover = vm::discoverProviderModels,
                onSave = vm::saveModelProvider
            )
        } else {
            ProviderList(
                agent = agent,
                providers = providers,
                onRefresh = vm::refreshModelProviders,
                onAdd = { creating = true },
                onEdit = { route -> editingRoute = route },
                onOpenHarness = onOpenHarness
            )
        }
    }
}

@Composable
private fun ProviderList(
    agent: AgentState,
    providers: AgentModelProviders,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenHarness: () -> Unit
) {
    val ready = agent.connection == AgentConnection.Ready
    val pageScroll = rememberScrollState()
    ScrollEdgeFade(
        canScrollForward = pageScroll.canScrollForward,
        color = Background,
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(pageScroll)
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        when {
            !providers.supported && providers.loaded -> {
                ProviderCard {
                    Text(
                        "当前 dsh 没有多供应商设置",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "这个 dsh 版本不提供 llm-pi-ai 设置命名空间，新增供应商需要在 Harness 的 Models 页完成；" +
                            "保存后返回这里即可选择新模型。",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenHarness,
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, Border),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("在 Harness 中设置", fontSize = 11.5.sp)
                    }
                }
            }

            else -> {
                if (providers.error != null) {
                    ProviderNotice(providers.error, isError = true)
                    Spacer(Modifier.height(10.dp))
                }
                if (providers.loaded && !providers.writable) {
                    ProviderNotice(
                        "当前 profile 只读，供应商设置无法保存。请在 Kali 中检查 dsh 配置权限。",
                        isError = false
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    "已配置",
                    modifier = Modifier.padding(start = 4.dp),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                ProviderCard {
                    if (!providers.loaded) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = TextMuted
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (ready) "读取供应商…" else "等待 Agent 连接",
                                color = TextMuted,
                                fontSize = 11.5.sp
                            )
                        }
                    } else if (providers.configured.isEmpty()) {
                        Text(
                            "还没有配置其他供应商。DeepSeek 官方接口使用上一页的凭据卡。",
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                    } else {
                        providers.configured.forEachIndexed { index, provider ->
                            if (index > 0) {
                                HorizontalDivider(color = Border.copy(alpha = 0.7f))
                            }
                            ProviderRow(
                                provider = provider,
                                isDefault = providers.defaultProvider == provider.provider,
                                onClick = { onEdit(provider.provider) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onAdd,
                    enabled = ready && providers.loaded && providers.supported && providers.writable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent,
                        disabledContainerColor = SurfaceVariant,
                        disabledContentColor = TextMuted
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加供应商", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "供应商与模型写入 dsh 的 llm-pi-ai 设置，dsh-web 的 Models 页会同步显示。" +
                        "API Key 只写入凭据存储，界面和读取接口都不会返回明文。",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
                if (providers.defaultModel != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "新对话默认：${providers.defaultProvider ?: "—"} / ${providers.defaultModel}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun ProviderRow(
    provider: AgentProvider,
    isDefault: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    provider.displayName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (isDefault) {
                    Spacer(Modifier.width(6.dp))
                    Pill("默认", Success)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(provider.provider)
                    val profile = provider.profile
                    val count = profile?.models?.size ?: 0
                    if (count > 0) append(" · $count 个模型")
                    val api = profile?.api
                    if (!api.isNullOrBlank()) append(" · ${AgentProviderCopy.protocolName(api)}")
                },
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            if (provider.error != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    provider.error,
                    color = Amber,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                provider.keyRef.isBlank() -> "无需密钥"
                provider.keyConfigured -> "密钥已配置"
                else -> "密钥未配置"
            },
            color = if (provider.keyConfigured || provider.keyRef.isBlank()) TextMuted else Amber,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ProviderEditor(
    agent: AgentState,
    existing: AgentProvider?,
    creating: Boolean,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    onDelete: (String, String?, Boolean) -> Unit,
    onDiscover: (String, String, String, String) -> Unit,
    onSave: (AgentProviderDraft, String?) -> Unit
) {
    val profile = existing?.profile
    val providers = agent.modelProviders
    var route by remember(existing?.provider, creating) {
        mutableStateOf(existing?.provider ?: "")
    }
    var displayName by remember(existing?.provider, creating) {
        mutableStateOf(profile?.displayName ?: existing?.displayName?.takeIf {
            it != existing.provider
        } ?: "")
    }
    var api by remember(existing?.provider, creating) {
        mutableStateOf(
            profile?.api
                ?: if (creating || existing?.declared == true) "openai-completions" else ""
        )
    }
    var apiTouched by remember(existing?.provider, creating) { mutableStateOf(false) }
    var baseURL by remember(existing?.provider, creating) {
        mutableStateOf(profile?.baseURL ?: "")
    }
    var keyInput by remember(existing?.provider, creating) { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }
    var models by remember(existing?.provider, creating) {
        mutableStateOf(profile?.models ?: emptyList())
    }
    var modelInput by remember { mutableStateOf("") }
    var makeDefault by remember(existing?.provider, creating) {
        mutableStateOf(
            creating &&
                models.isNotEmpty() ||
                (existing != null && providers.defaultProvider == existing.provider)
        )
    }
    var defaultModelId by remember(existing?.provider, creating) {
        mutableStateOf(
            if (existing != null && providers.defaultProvider == existing.provider) {
                providers.defaultModel ?: models.firstOrNull()?.id ?: ""
            } else {
                models.firstOrNull()?.id ?: ""
            }
        )
    }
    var deleteKey by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val routeValid = route.matches(Regex("^[a-z][a-z0-9-]*$"))
    val catalogRoute = providers.providers.any { it.provider == route }
    val baseURLTrimmed = baseURL.trim()
    val endpointValid = baseURLTrimmed.isEmpty() ||
        baseURLTrimmed.startsWith("http://") ||
        baseURLTrimmed.startsWith("https://")
    val needsEndpoint = !catalogRoute && baseURLTrimmed.isEmpty()
    val needsModels = !catalogRoute && models.isEmpty()
    val canSave = routeValid && endpointValid && !needsEndpoint && !needsModels

    // A catalog route keeps its own protocol when the form has not overridden it;
    // switching the typed route back to a hand-declared one restores the default.
    LaunchedEffect(catalogRoute, creating) {
        if (creating && !apiTouched) {
            api = if (catalogRoute) "" else "openai-completions"
        }
    }
    val keyRef = profile?.apiKeyEnv?.takeIf { it.isNotBlank() }
        ?: derivedKeyRef(route)
    val save = agent.providerSave
    val savingThis = save.route == route && save.saving
    val saveError = if (save.route == route && !save.done && !save.saving) {
        save.error
    } else {
        null
    }

    // providerSave survives a completed save, so only close when this form saw its own
    // save begin; otherwise reopening the editor would close it immediately.
    var sawSaving by remember(existing?.provider, creating) { mutableStateOf(false) }
    LaunchedEffect(savingThis) {
        if (savingThis) sawSaving = true
    }
    LaunchedEffect(save.done, save.route) {
        if (sawSaving && save.done && save.route == route) onSaved()
    }

    val discovery = agent.modelDiscovery
    val discoveryMatches = discovery.provider == route.trim() &&
        discovery.baseURL == baseURL.trim() &&
        discovery.api == api
    val discoveryModels = if (discoveryMatches) discovery.models else emptyList()
    val discoveryLoading = discoveryMatches && discovery.loading
    val discoveryError = if (discoveryMatches) discovery.error else null

    val editorScroll = rememberScrollState()
    ScrollEdgeFade(
        canScrollForward = editorScroll.canScrollForward,
        color = Background,
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(editorScroll)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        if (existing != null && existing.error != null) {
            ProviderNotice(existing.error, isError = true)
            Spacer(Modifier.height(10.dp))
        }

        ProviderCard {
            ProviderField(
                label = "供应商 ID",
                value = route,
                onValueChange = { value ->
                    if (creating) {
                        route = value.lowercase().filter { it.isLetterOrDigit() || it == '-' }
                    }
                },
                placeholder = "例如 acme-gateway",
                enabled = creating,
                mono = true
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    !creating && route.isNotBlank() ->
                        "供应商 ID 是这条配置的键，保存后不可修改。"
                    !routeValid && route.isNotBlank() ->
                        "只能包含小写字母、数字和连字符，并以字母开头。"
                    catalogRoute -> "这是 dsh 内置的供应商，通常只需填写密钥。"
                    route.isNotBlank() ->
                        "自定义供应商需要 endpoint、协议和至少一个模型。"
                    else -> "供应商 ID 也是模型选择器里显示的导航键。"
                },
                color = if (!routeValid && route.isNotBlank()) Red else TextMuted,
                fontSize = 9.5.sp,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(14.dp))
            ProviderField(
                label = "显示名",
                value = displayName,
                onValueChange = { displayName = it },
                placeholder = route.ifBlank { "例如 Acme Gateway" }
            )

            Spacer(Modifier.height(14.dp))
            Text("协议", color = TextMuted, fontSize = 10.5.sp)
            Spacer(Modifier.height(6.dp))
            if (catalogRoute) {
                ProtocolRow(
                    name = "跟随内置目录",
                    selected = api.isBlank(),
                    onSelect = {
                        api = ""
                        apiTouched = true
                    }
                )
            }
            providers.protocols.forEach { option ->
                ProtocolRow(
                    name = AgentProviderCopy.protocolName(option),
                    selected = api == option,
                    onSelect = {
                        api = option
                        apiTouched = true
                    }
                )
            }
            if (catalogRoute) {
                Text(
                    "内置供应商留空时使用其目录里的协议与 endpoint。",
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp
                )
            }

            Spacer(Modifier.height(14.dp))
            ProviderField(
                label = "Endpoint",
                value = baseURL,
                onValueChange = { baseURL = it },
                placeholder = if (catalogRoute) "留空使用内置地址" else "https://api.example.com/v1",
                mono = true
            )
            if (!endpointValid) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "endpoint 必须是 http 或 https 地址。",
                    color = Red,
                    fontSize = 9.5.sp
                )
            }

            Spacer(Modifier.height(14.dp))
            ProviderField(
                label = "API Key",
                value = keyInput,
                onValueChange = { keyInput = it.take(400) },
                placeholder = when {
                    existing?.keyConfigured == true -> "已配置 · 输入新值可替换"
                    existing != null -> "未配置"
                    else -> "粘贴供应商提供的 Key"
                },
                mono = true,
                password = !revealKey,
                trailing = {
                    TextButton(
                        onClick = { revealKey = !revealKey },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            if (revealKey) "隐藏" else "显示",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (keyInput.isBlank() && existing?.keyConfigured == true) {
                    "Key 已保存在 dsh 凭据存储中，界面不会读回明文。"
                } else {
                    "密钥引用：$keyRef · 只写入，不读取"
                },
                color = TextMuted,
                fontSize = 9.5.sp,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("连接测试", color = TextMuted, fontSize = 10.5.sp)
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        onDiscover(route.trim(), baseURL.trim(), api, keyInput)
                    },
                    enabled = !discoveryLoading &&
                        (route.isNotBlank() || baseURL.isNotBlank()),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Border),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    if (discoveryLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = TextMuted
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (discoveryLoading) "连接中…" else "测试连接", fontSize = 11.sp)
                }
            }
            if (discoveryError != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    discoveryError,
                    color = Red.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
            if (discoveryModels.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "端点报告 ${discoveryModels.size} 个模型",
                    color = Success,
                    fontSize = 10.sp
                )
                TextButton(
                    onClick = {
                        val merged = (models + discoveryModels)
                            .distinctBy { it.id }
                        models = merged
                        if (defaultModelId.isBlank()) defaultModelId = merged.firstOrNull()?.id ?: ""
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("全部添加", color = Accent, fontSize = 10.5.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Border.copy(alpha = 0.7f))
            Spacer(Modifier.height(12.dp))
            Text("模型", color = TextMuted, fontSize = 10.5.sp)
            Spacer(Modifier.height(6.dp))
            if (models.isEmpty()) {
                Text(
                    if (catalogRoute) {
                        "留空使用内置模型目录；也可以在下面添加要单独声明的模型。"
                    } else {
                        "自定义供应商至少需要一个模型。"
                    },
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            } else {
                models.forEach { model ->
                    ModelRow(
                        model = model,
                        onRemove = {
                            models = models.filterNot { it.id == model.id }
                            if (defaultModelId == model.id) {
                                defaultModelId = models.firstOrNull()?.id ?: ""
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ProviderField(
                        label = "",
                        value = modelInput,
                        onValueChange = { modelInput = it.trim() },
                        placeholder = "模型 ID，例如 gpt-4.1-mini",
                        mono = true,
                        compact = true
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val id = modelInput.trim()
                        if (id.isNotEmpty() && models.none { it.id == id }) {
                            models = models + AgentProviderModel(id = id)
                            if (defaultModelId.isBlank()) defaultModelId = id
                        }
                        modelInput = ""
                    },
                    enabled = modelInput.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Border),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("添加", fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        ProviderCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "设为新对话默认模型",
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "保存后写入 dsh 的默认模型；当前对话不受影响。",
                        color = TextMuted,
                        fontSize = 9.5.sp,
                        lineHeight = 14.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                androidx.compose.material3.Switch(
                    checked = makeDefault,
                    onCheckedChange = { makeDefault = it },
                    enabled = models.isNotEmpty()
                )
            }
            if (makeDefault && models.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                models.forEach { model ->
                    ProtocolRow(
                        name = model.label,
                        selected = defaultModelId == model.id,
                        onSelect = { defaultModelId = model.id }
                    )
                }
            }
        }

        if (saveError != null) {
            Spacer(Modifier.height(10.dp))
            ProviderNotice(saveError, isError = true)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                onSave(
                    AgentProviderDraft(
                        previousRoute = existing?.provider,
                        route = route.trim(),
                        displayName = displayName.trim(),
                        api = api,
                        baseURL = baseURL.trim(),
                        apiKeyRef = keyRef,
                        apiKey = keyInput,
                        models = models
                    ),
                    if (makeDefault && defaultModelId.isNotBlank()) defaultModelId else null
                )
            },
            enabled = canSave && providers.writable && !savingThis,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = OnAccent,
                disabledContainerColor = SurfaceVariant,
                disabledContentColor = TextMuted
            )
        ) {
            if (savingThis) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 1.6.dp,
                    color = OnAccent
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (savingThis) "正在保存…" else "保存",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (existing != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { confirmDelete = true },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("删除供应商", color = Red.copy(alpha = 0.9f), fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { deleteKey = !deleteKey },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        if (deleteKey) "删除时清除密钥 ✓" else "删除时清除密钥",
                        color = if (deleteKey) Amber else TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(
            onClick = onCancel,
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Text("取消", color = TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(20.dp))
    }
    }

    if (confirmDelete && existing != null) {
        val inUse = agent.models?.provider == existing.provider ||
            providers.defaultProvider == existing.provider
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除 ${existing.displayName}？", fontSize = 16.sp) },
            text = {
                Text(
                    if (inUse) {
                        "这个供应商正在提供当前或默认模型。删除后 dsh 会保留一个无法解析的选择，" +
                            "需要重新选择模型才能继续对话。" +
                            if (deleteKey) "已保存的密钥也会被清除。" else ""
                    } else {
                        "供应商配置会从 dsh 中移除。模型列表和对话记录不受影响。" +
                            if (deleteKey) "已保存的密钥也会被清除。" else ""
                    },
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(
                            existing.provider,
                            existing.profile?.apiKeyEnv,
                            deleteKey
                        )
                    }
                ) {
                    Text("删除", color = Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun ProviderField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    mono: Boolean = false,
    password: Boolean = false,
    compact: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            Text(label, color = TextMuted, fontSize = 10.5.sp)
            Spacer(Modifier.height(5.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 38.dp else 42.dp)
                .background(SurfaceVariant, RoundedCornerShape(10.dp))
                .padding(start = 12.dp, end = if (trailing == null) 12.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = if (enabled) TextPrimary else TextMuted,
                    fontSize = 12.5.sp,
                    lineHeight = 12.5.sp,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
                ),
                cursorBrush = SolidColor(Accent),
                singleLine = true,
                enabled = enabled,
                visualTransformation = if (password) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = TextMuted, fontSize = 12.sp)
                    }
                    inner()
                }
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun ProtocolRow(
    name: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(16.dp)
                .border(
                    width = if (selected) 5.dp else 1.dp,
                    color = if (selected) Accent else Border,
                    shape = RoundedCornerShape(8.dp)
                )
        )
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 11.5.sp
        )
    }
}

@Composable
private fun ModelRow(
    model: AgentProviderModel,
    onRemove: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(SurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(9.dp))
            .padding(start = 10.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                model.label,
                color = TextPrimary,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace
            )
            val detail = buildList {
                if (model.name.isNotBlank() && model.name != model.id) add(model.name)
                model.contextWindow?.let { add("上下文 $it") }
                model.maxTokens?.let { add("输出 $it") }
                if (model.inputModalities.isNotEmpty()) {
                    add(
                        "输入 " + model.inputModalities.joinToString("+") {
                            when (it) {
                                "image" -> "图片"
                                "text" -> "文本"
                                else -> it
                            }
                        }
                    )
                }
            }.joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(detail, color = TextMuted, fontSize = 9.sp)
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "移除模型",
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ProviderCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun ProviderNotice(message: String, isError: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (isError) Red.copy(alpha = 0.07f) else SurfaceVariant,
                RoundedCornerShape(11.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            message,
            color = if (isError) Red else TextSecondary,
            fontSize = 10.5.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun Pill(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = color,
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Medium
    )
}

/** The credential reference dsh will store a new route's key under. */
private fun derivedKeyRef(route: String): String {
    val base = route.uppercase().filter { it.isLetterOrDigit() || it == '_' }
        .ifBlank { "PROVIDER" }
    return if (base.firstOrNull()?.isDigit() == true) {
        "${'_'}$base" + "_API_KEY"
    } else {
        "${base}_API_KEY"
    }
}
