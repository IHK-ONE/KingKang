package com.kingkang.app.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [ToolView.of] to representative dsh tool payloads: a settled shell call with
 * the exit trailer, an applied edit whose diff comes from result meta, the query
 * tools' `shape` meta, and a tool this build has no card for.
 */
class ToolViewTest {

    private fun fixture(path: String): JSONObject {
        val text = checkNotNull(javaClass.getResourceAsStream("/fixtures/$path")) {
            "missing fixture: $path"
        }.bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    private fun item(
        path: String,
        metaOverride: String? = null,
        running: Boolean? = null
    ): ToolItem {
        val json = fixture(path)
        val meta = metaOverride ?: json.optJSONObject("meta")?.toString()
        return ToolItem(
            key = "call-1",
            callId = "call-1",
            name = json.getString("name"),
            arguments = json.getJSONObject("arguments").toString(),
            resultText = json.optString("resultText"),
            isError = json.optBoolean("isError"),
            running = running ?: json.optBoolean("running"),
            metaJson = meta
        )
    }

    @Test
    fun `settled shell call separates output from exit code`() {
        val view = ToolView.of(item("tools/shell.json")) as ToolView.Shell

        assertEquals("nmap -sV 127.0.0.1", view.command)
        assertEquals("Scan loopback services", view.description)
        assertEquals("/root", view.workdir)
        assertTrue(view.oneShot)
        assertEquals(0, view.exitCode)
        assertNull(view.signal)
        assertEquals(
            "PORT     STATE SERVICE VERSION\n8080/tcp open  http    dsh harness\n",
            view.output
        )
    }

    @Test
    fun `failed shell call keeps no success output`() {
        val view = ToolView.of(item("tools/shell-error.json")) as ToolView.Shell

        assertEquals("cat /root/missing.txt", view.command)
        assertNull(view.output)
        assertNull(view.exitCode)
        assertNull(view.signal)
    }

    @Test
    fun `applied edit renders the meta diff`() {
        val view = ToolView.of(item("tools/diff-applied.json")) as ToolView.Diff

        assertTrue(view.applied)
        val hunk = view.hunks.single()
        assertEquals("/root/work/notes.md", hunk.path)
        assertEquals("sshd listens on port 22\n", hunk.oldText)
        assertEquals("sshd listens on port 2222\n", hunk.newText)
    }

    @Test
    fun `malformed meta falls back to the intended hunk`() {
        val view = ToolView.of(
            item("tools/diff-applied.json", metaOverride = "{not-json")
        ) as ToolView.Diff

        assertFalse(view.applied)
        val hunk = view.hunks.single()
        assertEquals("/root/work/notes.md", hunk.path)
        assertEquals("port 22", hunk.oldText)
        assertEquals("port 2222", hunk.newText)
    }

    @Test
    fun `running write shows the intended create diff`() {
        val view = ToolView.of(item("tools/diff-intended.json")) as ToolView.Diff

        assertFalse(view.applied)
        val hunk = view.hunks.single()
        assertEquals("/root/work/findings.md", hunk.path)
        assertNull(hunk.oldText)
        assertEquals("# Findings\n\nOnly loopback is exposed.\n", hunk.newText)
    }

    @Test
    fun `grep meta becomes file matches`() {
        val view = ToolView.of(item("tools/grep.json")) as ToolView.Grep

        assertEquals("listen", view.pattern)
        assertEquals("/etc", view.path)
        assertEquals("*.conf", view.include)
        assertEquals(2, view.total)
        assertFalse(view.truncated)
        assertEquals(2, view.files?.size)
        assertEquals("/etc/ssh/sshd_config", view.files?.get(0)?.path)
        assertEquals(14, view.files?.get(0)?.matches?.single()?.lineNumber)
        assertEquals(
            "ListenAddress 127.0.0.1",
            view.files?.get(0)?.matches?.single()?.line
        )
    }

    @Test
    fun `glob meta becomes paths`() {
        val view = ToolView.of(item("tools/glob.json")) as ToolView.Glob

        assertEquals("**/*.md", view.pattern)
        assertEquals("/root/work", view.path)
        assertEquals(
            listOf("/root/work/README.md", "/root/work/findings.md"),
            view.paths
        )
        assertEquals(2, view.total)
    }

    @Test
    fun `read meta becomes numbered lines`() {
        val view = ToolView.of(item("tools/read.json")) as ToolView.ReadFile

        assertEquals("/root/work/findings.md", view.path)
        assertEquals(2, view.offset)
        assertEquals(3, view.totalLines)
        assertEquals("markdown", view.lang)
        assertEquals(2, view.lines?.size)
        assertEquals(3, view.lines?.get(1)?.number)
        assertEquals("Only loopback is exposed.", view.lines?.get(1)?.text)
    }

    @Test
    fun `unknown tool falls back to a generic card`() {
        val view = ToolView.of(item("tools/unknown-view.json")) as ToolView.Generic

        assertEquals("show me the latest scan", view.summary)
    }

    @Test
    fun `running shell defers output until the call settles`() {
        val view = ToolView.of(
            item("tools/shell.json", running = true)
        ) as ToolView.Shell

        assertNull(view.output)
        assertNull(view.exitCode)
    }
}
