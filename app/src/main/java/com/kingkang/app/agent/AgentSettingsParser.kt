package com.kingkang.app.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns dsh's `settings/describe` wire view into the native form model.
 *
 * dsh ships a schemastery graph per profile entry. The graph is flattened here: only
 * top-level scalar fields of the curated namespaces become controls. Objects, arrays and
 * non-literal unions stay visible as rows that point at the Harness, because silently
 * hiding them would make the native page look complete when it is not.
 */
object AgentSettingsParser {

    private data class NamespaceCopy(
        val ns: String,
        val title: String,
        val subtitle: String? = null,
        val labels: Map<String, String> = emptyMap(),
        val hints: Map<String, String> = emptyMap()
    )

    /**
     * The profile entries with a native form. The order is deliberate: search and shell
     * are what most users open this page for. `ui-theme`, `permission`,
     * `agent-preset-registry`, `llm-pi-ai` and `locale` have dedicated native cards.
     */
    private val curated = listOf(
        NamespaceCopy(
            ns = "web-search-deepseek",
            title = "网页搜索",
            subtitle = "DeepSeek 搜索提供方",
            labels = mapOf(
                "apiKey" to "API Key",
                "apiKeyEnv" to "凭据引用",
                "baseURL" to "接口地址",
                "model" to "模型",
                "apiVersion" to "API 版本",
                "maxTokens" to "最大输出 Tokens",
                "maxUses" to "每次对话搜索上限"
            ),
            hints = mapOf(
                "apiKey" to "只写入 dsh 的凭据库，不会回显或备份",
                "apiKeyEnv" to "保存到环境变量或凭据库的名称",
                "maxUses" to "一条消息里最多调用搜索工具的次数"
            )
        ),
        NamespaceCopy(
            ns = "bash-sandbox",
            title = "终端与沙箱",
            subtitle = "Shell 工具的工作目录、超时与输出上限",
            labels = mapOf(
                "cwd" to "工作目录",
                "timeoutMs" to "默认超时（毫秒）",
                "maxTimeoutMs" to "最大超时（毫秒）",
                "maxOutputBytes" to "单次输出上限（字节）",
                "maxSpillBytes" to "溢出文件上限（字节）",
                "graceMs" to "终止宽限（毫秒）"
            ),
            hints = mapOf(
                "cwd" to "命令默认在这个 Kali 目录中运行"
            )
        ),
        NamespaceCopy(
            ns = "subagent",
            title = "子 Agent",
            subtitle = "嵌套深度与并发数量",
            labels = mapOf(
                "maxDepth" to "最大嵌套深度",
                "maxActiveSubagents" to "最大并发子 Agent"
            )
        ),
        NamespaceCopy(
            ns = "subagent-model-selection-settings",
            title = "子 Agent 模型",
            subtitle = "限制子 Agent 可以使用的模型",
            labels = mapOf(
                "enabled" to "启用模型限制",
                "allowedModels" to "允许的模型列表"
            )
        ),
        NamespaceCopy(
            ns = "agent-loop",
            title = "Agent 循环",
            subtitle = "并行工具调用与会话级 Agent 配置",
            labels = mapOf(
                "maxParallelToolCalls" to "并行工具调用上限",
                "agents" to "会话 Agent 配置"
            )
        ),
        NamespaceCopy(
            ns = "session-log-deepseek",
            title = "会话日志",
            subtitle = "日志开关与容量上限",
            labels = mapOf(
                "enabled" to "记录会话日志",
                "maxBytes" to "日志容量上限（字节）"
            )
        )
    )

    private val nativeNamespaces = setOf(
        "ui-theme",
        "permission",
        "agent-preset-registry",
        "llm-pi-ai",
        "locale"
    )

    private val optionLabels = mapOf(
        "light" to "浅色",
        "dark" to "深色",
        "system" to "跟随系统",
        "zh" to "中文",
        "en" to "English",
        "true" to "开启",
        "false" to "关闭"
    )

    fun parse(describe: JSONObject): AgentSettingsSnapshot {
        val globalWritable = describe.optBoolean("writable")
        val namespaces = describe.optJSONArray("namespaces") ?: JSONArray()
        val byName = mutableMapOf<String, JSONObject>()
        for (index in 0 until namespaces.length()) {
            val view = namespaces.optJSONObject(index) ?: continue
            val ns = view.optString("ns")
            if (ns.isNotBlank()) byName[ns] = view
        }
        val groups = curated.mapNotNull { copy ->
            val view = byName[copy.ns] ?: return@mapNotNull null
            parseGroup(view, globalWritable, copy)
        }
        val harnessOnly = byName.keys.count { it !in nativeNamespaces && curated.none { c -> c.ns == it } }
        return AgentSettingsSnapshot(
            loaded = true,
            writable = globalWritable,
            groups = groups,
            harnessOnlyCount = harnessOnly
        )
    }

    /** The `locale` namespace's preference; null means dsh follows its own default. */
    fun parseLanguage(describe: JSONObject): AgentLanguageSettings {
        val globalWritable = describe.optBoolean("writable")
        val view = findNamespace(describe, "locale")
            ?: return AgentLanguageSettings(loaded = true)
        val value = (view.optJSONObject("value")?.opt("preference") as? String)
            ?.takeIf { it.isNotBlank() }
        return AgentLanguageSettings(
            loaded = true,
            writable = globalWritable,
            revision = view.optInt("revision", 0),
            value = value
        )
    }

    /** The `ui-theme` preference dsh will render the Harness with. */
    fun parseTheme(describe: JSONObject): String? =
        (findNamespace(describe, "ui-theme")?.optJSONObject("value")?.opt("preference") as? String)
            ?.takeIf { it == "light" || it == "dark" || it == "system" }

    /** Parses the single namespace view a `settings/mutate` call returns. */
    fun parseMutated(view: JSONObject, globalWritable: Boolean): AgentSettingGroup? {
        val ns = view.optString("ns")
        val copy = curated.firstOrNull { it.ns == ns } ?: return null
        return parseGroup(view, globalWritable, copy)
    }

    fun findNamespace(describe: JSONObject, ns: String): JSONObject? {
        val namespaces = describe.optJSONArray("namespaces") ?: return null
        for (index in 0 until namespaces.length()) {
            val view = namespaces.optJSONObject(index) ?: continue
            if (view.optString("ns") == ns) return view
        }
        return null
    }

    private fun parseGroup(
        view: JSONObject,
        globalWritable: Boolean,
        copy: NamespaceCopy
    ): AgentSettingGroup {
        val writable = globalWritable
        val schema = view.optJSONObject("schema")
        if (schema == null) {
            return AgentSettingGroup(
                ns = copy.ns,
                title = copy.title,
                subtitle = copy.subtitle,
                writable = writable,
                revision = view.optInt("revision", 0),
                note = "这项设置没有可生成的表单，请在 Harness 中调整。"
            )
        }
        val refs = schema.optJSONObject("refs")
        val uid = schema.optString("uid")
        val root = refs?.optJSONObject(uid)
        if (refs == null || root == null || root.optString("type") != "object") {
            return AgentSettingGroup(
                ns = copy.ns,
                title = copy.title,
                subtitle = copy.subtitle,
                writable = writable,
                revision = view.optInt("revision", 0),
                note = "这项设置的表单结构无法在本页渲染，请在 Harness 中调整。"
            )
        }
        val dict = root.optJSONObject("dict") ?: JSONObject()
        val values = view.optJSONObject("value") ?: JSONObject()
        val secrets = view.optJSONArray("secrets") ?: JSONArray()
        val fields = mutableListOf<AgentSettingField>()
        for (key in dict.keys()) {
            val ref = dict.optInt(key, -1)
            if (ref < 0) continue
            val node = refs.optJSONObject(ref.toString()) ?: continue
            fields += parseField(
                key = key,
                path = listOf(key),
                node = node,
                refs = refs,
                value = values.opt(key),
                secretConfigured = secretConfigured(secrets, listOf(key)),
                copy = copy,
                editable = writable
            )
        }
        return AgentSettingGroup(
            ns = copy.ns,
            title = copy.title,
            subtitle = copy.subtitle,
            writable = writable,
            revision = view.optInt("revision", 0),
            fields = fields
        )
    }

    private fun parseField(
        key: String,
        path: List<String>,
        node: JSONObject,
        refs: JSONObject,
        value: Any?,
        secretConfigured: Boolean,
        copy: NamespaceCopy,
        editable: Boolean
    ): AgentSettingField {
        val meta = node.optJSONObject("meta") ?: JSONObject()
        val label = copy.labels[key] ?: key
        val hint = copy.hints[key] ?: (meta.opt("description") as? String)
            ?.takeIf { it.isNotBlank() }
        val required = meta.optBoolean("required", false)
        val unsupported = { reason: String ->
            AgentSettingField(
                path = path,
                label = label,
                kind = AgentSettingKind.Unsupported,
                hint = hint,
                unsupportedReason = reason
            )
        }
        return when (node.optString("type")) {
            "string" -> {
                val isSecret = meta.optString("role") == "secret"
                AgentSettingField(
                    path = path,
                    label = label,
                    kind = if (isSecret) AgentSettingKind.Secret else AgentSettingKind.Text,
                    text = if (isSecret) "" else (value as? String) ?: "",
                    hint = hint,
                    configured = isSecret && secretConfigured,
                    required = required,
                    pattern = (meta.opt("pattern") as? String)?.takeIf { it.isNotBlank() }
                )
            }

            "number" -> AgentSettingField(
                path = path,
                label = label,
                kind = AgentSettingKind.Number,
                text = formatNumber(value) ?: "",
                hint = hint,
                required = required,
                min = meta.optDoubleOrNull("min"),
                max = meta.optDoubleOrNull("max"),
                step = meta.optDoubleOrNull("step")
            )

            "boolean" -> AgentSettingField(
                path = path,
                label = label,
                kind = AgentSettingKind.Toggle,
                checked = value as? Boolean ?: meta.optBoolean("default", false),
                hint = hint
            )

            "const" -> unsupported(
                "固定为「${optionLabel(node.opt("value")?.toString() ?: "")}」，无需修改"
            )

            "union" -> {
                val list = node.optJSONArray("list") ?: JSONArray()
                val options = mutableListOf<AgentSettingOption>()
                var literalOnly = true
                for (index in 0 until list.length()) {
                    val branchRef = list.optInt(index, -1)
                    val branch = refs.optJSONObject(branchRef.toString())
                    val raw = branch?.opt("value")
                    if (branch?.optString("type") != "const" || raw == null) {
                        literalOnly = false
                        break
                    }
                    val encoded = raw.toString()
                    options += AgentSettingOption(encoded, optionLabel(encoded))
                }
                if (literalOnly && options.isNotEmpty()) {
                    AgentSettingField(
                        path = path,
                        label = label,
                        kind = AgentSettingKind.Choice,
                        text = (value as? String) ?: options.first().value,
                        hint = hint,
                        options = options
                    )
                } else {
                    unsupported("可选值结构需要在 Harness 中编辑")
                }
            }

            "object", "array", "dict", "intersect", "transform" -> unsupported(
                "结构化配置需要在 Harness 中编辑"
            )

            else -> unsupported("这一字段类型暂不支持原生编辑，请在 Harness 中调整")
        }.let { field ->
            if (field.kind != AgentSettingKind.Unsupported && !editable) {
                field.copy(kind = AgentSettingKind.Unsupported, unsupportedReason = "当前 dsh 配置不可写")
            } else {
                field
            }
        }
    }

    private fun secretConfigured(secrets: JSONArray, path: List<String>): Boolean {
        for (index in 0 until secrets.length()) {
            val secret = secrets.optJSONObject(index) ?: continue
            val secretPath = secret.optJSONArray("path") ?: continue
            if (secretPath.length() != path.size) continue
            var matches = true
            for (position in path.indices) {
                if (secretPath.optString(position) != path[position]) {
                    matches = false
                    break
                }
            }
            if (matches) return secret.optBoolean("set", false)
        }
        return false
    }

    private fun optionLabel(value: String): String =
        optionLabels[value] ?: value.ifBlank { "（空）" }

    private fun formatNumber(value: Any?): String? = when (value) {
        is Number -> {
            val double = value.toDouble()
            if (double == Math.floor(double) && !double.isInfinite()) double.toLong().toString()
            else double.toString()
        }
        is String -> value
        else -> null
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name)
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }
}
