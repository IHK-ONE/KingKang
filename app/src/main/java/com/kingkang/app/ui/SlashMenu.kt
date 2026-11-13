package com.kingkang.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingkang.app.agent.AgentCommand
import com.kingkang.app.agent.AgentSkill
import com.kingkang.app.ui.theme.Accent
import com.kingkang.app.ui.theme.AccentDim
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.theme.Border
import com.kingkang.app.ui.components.ScrollEdgeFade
import com.kingkang.app.ui.theme.Surface
import com.kingkang.app.ui.theme.TextMuted
import java.util.Locale

/**
 * The `/` menu mirrors dsh-web: an empty query splits host commands into the same
 * "添加" and "指令" sections, closes them with unlisted commands in catalog order, and
 * keeps skills in their own section. A typed query ranks every row by name and label,
 * exactly like the web's name ranking.
 */
internal enum class SlashSection(val label: String) {
    Add("添加"),
    Command("指令"),
    Skill("技能")
}

/**
 * One row of the `/` menu. [hint] is dsh's argument syntax; built-in commands show it in
 * the composer after the token is claimed rather than in the menu, matching the web.
 * [source] is the skill's instruction file, which is what "where did this come from"
 * means for a skill. [blockedReason] explains why the current draft's attachments cannot
 * ride this invocation.
 */
internal data class SlashSuggestion(
    val name: String,
    val label: String,
    val description: String,
    val hint: String?,
    val section: SlashSection,
    val source: String? = null,
    val recent: Boolean = false,
    val blockedReason: String? = null
) {
    val skill: Boolean = section == SlashSection.Skill
    val token: String get() = "/$name"
}

/** Section rows shown before catalog order when the query is empty (dsh-web's usage order). */
private val addCommandNames = setOf("goal", "plan", "feedback")
private val commandOrder = listOf("compact", "permission", "export")

/**
 * dsh's built-in commands ship English host copy but native zh labels. The slash menu
 * keeps the invocable token (`/compact`) while showing the same Chinese wording as
 * dsh web; unknown or future commands fall back to the host's own text.
 */
private val builtinCommandCopy = mapOf(
    "compact" to ("压缩" to "压缩以上对话内容"),
    "export" to ("下载日志" to "将当前会话内容导出为 ZIP"),
    "feedback" to ("反馈" to "发送关于当前会话的反馈"),
    "goal" to ("目标" to "设置或查看长期任务目标"),
    "permission" to ("权限" to "切换权限预设（沙箱模式与审批策略）"),
    "plan" to ("计划" to "进入或退出计划模式")
)

/** Search results stay bounded so the panel keeps its calm height. */
private const val SEARCH_LIMIT = 8

internal fun slashSuggestions(
    commands: List<AgentCommand>,
    skills: List<AgentSkill>,
    typed: String,
    recent: List<String> = emptyList(),
    images: Int = 0,
    files: Int = 0
): List<SlashSuggestion> {
    val searching = typed.isNotBlank()
    val recentRank = recent.map { it.removePrefix("/").lowercase(Locale.US) }
        .withIndex()
        .associate { it.value to it.index }
    val attachmentCount = images + files

    val commandRows = commands.mapNotNull { command ->
        val name = command.name.removePrefix("/")
        if (name.isBlank()) return@mapNotNull null
        val key = name.lowercase(Locale.US)
        val copy = builtinCommandCopy[key]
        SlashSuggestion(
            name = name,
            label = copy?.first ?: name,
            description = copy?.second ?: command.description,
            hint = if (copy == null) command.hint else null,
            section = if (!searching && key in addCommandNames) {
                SlashSection.Add
            } else {
                SlashSection.Command
            },
            recent = key in recentRank,
            blockedReason = when {
                attachmentCount == 0 -> null
                !command.acceptsAttachments -> "/$name 不接受附件"
                files > 0 -> "/$name 暂不能携带文件附件"
                else -> null
            }
        )
    }

    val skillRows = skills.mapNotNull { skill ->
        val name = skill.name.removePrefix("/")
        if (name.isBlank()) return@mapNotNull null
        val key = name.lowercase(Locale.US)
        val summary = skill.description.ifBlank { skill.whenToUse ?: "" }
        SlashSuggestion(
            name = name,
            label = name,
            description = if (skill.modelInvocable) summary else listOf("仅用户", summary)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            hint = if (skill.description.isNotBlank()) skill.whenToUse else null,
            section = SlashSection.Skill,
            source = skill.path,
            recent = key in recentRank
        )
    }

    if (searching) {
        return rankRows(commandRows, typed) + rankRows(skillRows, typed)
    }
    return orderRows(
        commandRows.filter { it.section == SlashSection.Add },
        addCommandNames.toList(),
        recentRank
    ) + orderRows(
        commandRows.filter { it.section == SlashSection.Command },
        commandOrder,
        recentRank
    ) + orderRows(skillRows, emptyList(), recentRank)
}

/** Ranked rows with the best match first; the index keeps ties in catalog order. */
private fun rankRows(rows: List<SlashSuggestion>, typed: String): List<SlashSuggestion> =
    rows.mapIndexedNotNull { index, row ->
        slashRank(row, typed)?.let { Triple(it, index, row) }
    }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .take(SEARCH_LIMIT)
        .map { it.third }

/** Recently used rows lead each section, then dsh's usage order, then catalog order. */
private fun orderRows(
    rows: List<SlashSuggestion>,
    fixed: List<String>,
    recent: Map<String, Int>
): List<SlashSuggestion> {
    val catalog = rows.withIndex().associate { it.value.name.lowercase(Locale.US) to it.index }
    return rows.sortedWith(
        compareBy<SlashSuggestion> {
            recent[it.name.lowercase(Locale.US)] ?: Int.MAX_VALUE
        }.thenBy {
            val position = fixed.indexOf(it.name.lowercase(Locale.US))
            if (position >= 0) position else fixed.size + (catalog[it.name.lowercase(Locale.US)] ?: 0)
        }
    )
}

private fun slashRank(suggestion: SlashSuggestion, typed: String): Int? {
    if (typed.isBlank()) return 0
    val needle = typed.lowercase(Locale.US)
    val name = suggestion.name.lowercase(Locale.US)
    val label = suggestion.label.lowercase(Locale.US)
    return when {
        name.startsWith(needle) -> 0
        label.startsWith(needle) -> 1
        name.contains(needle) -> 2
        label.contains(needle) -> 3
        isSubsequence(name, needle) -> 4
        isSubsequence(label, needle) -> 5
        else -> null
    }
}

private fun isSubsequence(text: String, needle: String): Boolean {
    var at = 0
    for (char in text) {
        if (at < needle.length && char == needle[at]) at++
        if (at == needle.length) return true
    }
    return at == needle.length
}

/** One rendered panel slot: a sticky-looking heading or a selectable row. */
internal sealed interface SlashPanelEntry {
    data class Header(val section: SlashSection) : SlashPanelEntry
    data class Row(val suggestion: SlashSuggestion, val index: Int) : SlashPanelEntry
}

internal fun slashPanelEntries(rows: List<SlashSuggestion>): List<SlashPanelEntry> {
    val entries = mutableListOf<SlashPanelEntry>()
    var current: SlashSection? = null
    rows.forEachIndexed { index, row ->
        if (row.section != current) {
            entries += SlashPanelEntry.Header(row.section)
            current = row.section
        }
        entries += SlashPanelEntry.Row(row, index)
    }
    return entries
}

/** The argument syntax dsh would show in the composer once its token is claimed. */
internal fun slashParamHint(text: String, commands: List<AgentCommand>): String? {
    val body = text.removePrefix("/")
    val space = body.indexOfFirst { it.isWhitespace() }
    if (space <= 0) return null
    if (body.substring(space).isNotBlank()) return null
    val name = body.substring(0, space)
    val command = commands.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
    val hint = command.hint?.takeIf { it.isNotBlank() } ?: return null
    return "/${command.name} $hint"
}

/** The host command a draft line would execute, if any. */
internal fun slashCommandAt(text: String, commands: List<AgentCommand>): AgentCommand? {
    val name = slashTokenName(text) ?: return null
    return commands.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

/** The first token's name when the draft is a slash line, without its leading slash. */
internal fun slashTokenName(text: String): String? {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("/")) return null
    val name = trimmed.drop(1).takeWhile { !it.isWhitespace() }
    return name.takeIf { it.isNotBlank() }
}

/**
 * Why the current attachments cannot ride this command, or null when they can. dsh
 * itself refuses attachments for commands that do not declare them; the app refuses
 * before dispatch so the draft and its attachments stay in place for correction.
 */
internal fun slashAttachmentNotice(command: AgentCommand, images: Int, files: Int): String? = when {
    images + files == 0 -> null
    !command.acceptsAttachments -> "/${command.name} 不接受附件，请先移除附件"
    files > 0 -> "/${command.name} 暂不能携带文件附件，请先移除文件"
    else -> null
}

/**
 * The last few commands and skills the user actually sent, most recent first. dsh has no
 * usage feed, so this local list is the app's own ordering aid; names are the only thing
 * stored, in a private preference file.
 */
internal class SlashHistory private constructor(private val prefs: SharedPreferences) {

    fun names(): List<String> = prefs.getString(KEY, null)
        ?.split('\n')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    fun record(name: String) {
        val clean = name.removePrefix("/").trim()
        if (clean.isEmpty()) return
        val next = (listOf(clean) + names().filterNot { it.equals(clean, ignoreCase = true) })
            .take(MAX_ENTRIES)
        prefs.edit().putString(KEY, next.joinToString("\n")).apply()
    }

    companion object {
        private const val PREFS = "slash-recent"
        private const val KEY = "names"
        private const val MAX_ENTRIES = 8

        fun of(context: Context): SlashHistory =
            SlashHistory(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}

/**
 * The `/` panel: section headings, a highlighted keyboard-selected row, and a no-results
 * line that names what was searched. Rows scroll under a fixed height so the composer
 * stays above the keyboard.
 */
@Composable
internal fun SlashMenuPanel(
    typed: String,
    suggestions: List<SlashSuggestion>,
    selected: Int,
    onPick: (SlashSuggestion) -> Unit
) {
    val entries = remember(suggestions) { slashPanelEntries(suggestions) }
    val listState = rememberLazyListState()

    LaunchedEffect(selected, entries) {
        val index = entries.indexOfFirst { it is SlashPanelEntry.Row && it.index == selected }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Border.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
    ) {
        if (suggestions.isEmpty()) {
            Text(
                if (typed.isBlank()) "还没有可用的命令或技能" else "没有匹配「/$typed」的命令或技能",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
            return@Column
        }
        ScrollEdgeFade(
            canScrollForward = listState.canScrollForward,
            color = Surface,
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(
                entries,
                key = { entry ->
                    when (entry) {
                        is SlashPanelEntry.Header -> "h:${entry.section.name}"
                        is SlashPanelEntry.Row -> "r:${entry.suggestion.section.name}:${entry.suggestion.name}"
                    }
                }
            ) { entry ->
                when (entry) {
                    is SlashPanelEntry.Header -> Text(
                        entry.section.label,
                        color = TextMuted,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp)
                    )
                    is SlashPanelEntry.Row -> SlashMenuRow(
                        suggestion = entry.suggestion,
                        selected = entry.index == selected,
                        onPick = { onPick(entry.suggestion) }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun SlashMenuRow(suggestion: SlashSuggestion, selected: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentDim else Color.Transparent)
            .clickable(onClick = onPick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    suggestion.label,
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (suggestion.recent) {
                    Spacer(Modifier.width(6.dp))
                    Text("最近", color = TextMuted, fontSize = 9.sp)
                }
            }
            val detail = listOfNotNull(
                suggestion.description.takeIf { it.isNotBlank() },
                suggestion.hint?.takeIf { it.isNotBlank() && !suggestion.skill }
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            suggestion.source?.takeIf { it.isNotBlank() }?.let { path ->
                Text(
                    "来源 $path",
                    color = TextMuted.copy(alpha = 0.82f),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            suggestion.blockedReason?.let { reason ->
                Text(
                    reason,
                    color = Amber,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            suggestion.token,
            color = TextMuted,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}
