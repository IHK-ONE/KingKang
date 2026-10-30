package com.kingkang.app.ui

import com.kingkang.app.agent.AgentCommand
import com.kingkang.app.agent.AgentSkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashMenuTest {

    private val commands = listOf(
        AgentCommand(name = "compact", description = "Compact the conversation"),
        AgentCommand(name = "permission", description = "Switch permission preset", hint = "<preset>"),
        AgentCommand(name = "export", description = "Download this session log"),
        AgentCommand(
            name = "goal",
            description = "Set a long-running goal",
            hint = "[<objective>|clear|edit <objective>|pause|resume]",
            acceptsAttachments = true
        ),
        AgentCommand(name = "plan", description = "Toggle plan mode", acceptsAttachments = true),
        AgentCommand(name = "feedback", description = "Send feedback", hint = "<text>")
    )

    private val skills = listOf(
        AgentSkill(
            name = "dsh-badge",
            description = "Add the powered-by-dsh badge",
            whenToUse = "When creating a pull request",
            path = "/root/.dsh/skills/dsh-badge/SKILL.md"
        ),
        AgentSkill(
            name = "office-report",
            description = "Write a DOCX report",
            modelInvocable = false,
            path = "/root/.dsh/skills/office-report/SKILL.md"
        )
    )

    @Test
    fun `an empty query uses web section order`() {
        val rows = slashSuggestions(commands, skills, typed = "")
        assertEquals(
            listOf("goal", "plan", "feedback", "compact", "permission", "export", "dsh-badge", "office-report"),
            rows.map { it.name }
        )
        assertEquals(SlashSection.Add, rows[0].section)
        assertEquals(SlashSection.Command, rows[3].section)
        assertEquals(SlashSection.Skill, rows[6].section)
    }

    @Test
    fun `recent commands lead their section without leaving it`() {
        val rows = slashSuggestions(commands, skills, typed = "", recent = listOf("export", "plan"))
        assertEquals("plan", rows[0].name)
        assertEquals(SlashSection.Add, rows[0].section)
        assertTrue(rows[0].recent)
        assertEquals("export", rows[3].name)
        assertEquals(SlashSection.Command, rows[3].section)
        assertTrue(rows[3].recent)
        assertFalse(rows[4].recent)
    }

    @Test
    fun `a typed query ranks names and labels and drops the rest`() {
        val rows = slashSuggestions(commands, skills, typed = "perm")
        assertEquals("permission", rows.first().name)
        assertEquals(listOf("permission"), rows.map { it.name })
        assertEquals(SlashSection.Command, rows.first().section)
    }

    @Test
    fun `the Chinese label is searchable but the description is not`() {
        assertEquals("export", slashSuggestions(commands, skills, typed = "日志").first().name)
        assertTrue(slashSuggestions(commands, skills, typed = "ZIP").isEmpty())
    }

    @Test
    fun `a subsequence still finds a command`() {
        assertEquals("compact", slashSuggestions(commands, skills, typed = "cp").first().name)
    }

    @Test
    fun `a user-only skill says so and keeps its source`() {
        val row = slashSuggestions(commands, skills, typed = "office").first()
        assertTrue(row.description.startsWith("仅用户"))
        assertEquals("/root/.dsh/skills/office-report/SKILL.md", row.source)
    }

    @Test
    fun `attachment support is explained per command`() {
        val without = slashSuggestions(commands, skills, typed = "compact", images = 1).first()
        assertEquals("/compact 不接受附件", without.blockedReason)

        val withImages = slashSuggestions(commands, skills, typed = "goal", images = 1).first()
        assertNull(withImages.blockedReason)

        val withFiles = slashSuggestions(commands, skills, typed = "goal", files = 1).first()
        assertEquals("/goal 暂不能携带文件附件", withFiles.blockedReason)
    }

    @Test
    fun `composer hint appears only while the argument slot is empty`() {
        assertEquals(
            "/goal [<objective>|clear|edit <objective>|pause|resume]",
            slashParamHint("/goal ", commands)
        )
        assertNull(slashParamHint("/goal ship it", commands))
        assertNull(slashParamHint("/goal", commands))
        assertNull(slashParamHint("/dsh-badge ", commands))
        assertNull(slashParamHint("hello", commands))
    }

    @Test
    fun `attachment validation keeps the draft until it passes`() {
        val goal = commands.first { it.name == "goal" }
        val compact = commands.first { it.name == "compact" }
        assertNull(slashAttachmentNotice(goal, images = 1, files = 0))
        assertEquals("/goal 暂不能携带文件附件，请先移除文件", slashAttachmentNotice(goal, 0, 1))
        assertEquals("/compact 不接受附件，请先移除附件", slashAttachmentNotice(compact, 1, 0))
        assertEquals("/compact 不接受附件，请先移除附件", slashAttachmentNotice(compact, 0, 1))
        assertNull(slashAttachmentNotice(compact, 0, 0))
    }

    @Test
    fun `slash parsing accepts case and ignores arguments`() {
        assertEquals("Goal", slashTokenName("/Goal ship it"))
        assertEquals("goal", slashCommandAt("/goAL ship", commands)?.name)
        assertNull(slashTokenName("goal ship"))
        assertNull(slashCommandAt("/nope", commands))
    }

    @Test
    fun `panel headings open each section once`() {
        val entries = slashPanelEntries(slashSuggestions(commands, skills, typed = ""))
        val headers = entries.filterIsInstance<SlashPanelEntry.Header>()
            .map { it.section }
        assertEquals(listOf(SlashSection.Add, SlashSection.Command, SlashSection.Skill), headers)
        val rows = entries.filterIsInstance<SlashPanelEntry.Row>().map { it.index }
        assertEquals((0 until slashSuggestions(commands, skills, typed = "").size).toList(), rows)
    }

    @Test
    fun `search results are bounded`() {
        val many = (1..20).map { AgentCommand(name = "task-$it", description = "Task $it") }
        val rows = slashSuggestions(many, emptyList(), typed = "task")
        assertEquals(8, rows.size)
    }
}
