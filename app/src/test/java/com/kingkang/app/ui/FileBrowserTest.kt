package com.kingkang.app.ui

import com.kingkang.app.agent.AgentFileEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class FileBrowserTest {

    private val listing = listOf(
        AgentFileEntry(name = "beta", path = "/w/beta", type = "directory"),
        AgentFileEntry(name = "alpha", path = "/w/alpha", type = "directory"),
        AgentFileEntry(name = "notes.md", path = "/w/notes.md", size = 2048),
        AgentFileEntry(name = "scan.txt", path = "/w/scan.txt", size = 900),
        AgentFileEntry(name = ".config", path = "/w/.config", size = 12)
    )

    @Test
    fun crumbsWalkFromRoot() {
        val crumbs = fileCrumbs("/root/lab", "/root/lab/sub/dir")
        assertEquals(listOf("lab", "sub", "dir"), crumbs.map { it.label })
        assertEquals("/root/lab/sub/dir", crumbs.last().path)
        assertEquals(1, fileCrumbs("/root/lab", "/root/lab").size)
    }

    @Test
    fun crumbsTreatSlashRootAsRoot() {
        val crumbs = fileCrumbs("/", "/var/www")
        assertEquals(listOf("/", "var", "www"), crumbs.map { it.label })
        assertEquals("/var/www", crumbs.last().path)
    }

    @Test
    fun foldersStayAboveFiles() {
        val sorted = sortFileEntries(listing, FileSort.Name, ascending = true, showHidden = true, query = "")
        assertEquals("alpha", sorted[0].name)
        assertEquals("beta", sorted[1].name)
        assertTrue(sorted.drop(2).all { !it.directory })
    }

    @Test
    fun descendingReversesInsideGroupsOnly() {
        val sorted = sortFileEntries(listing, FileSort.Name, ascending = false, showHidden = true, query = "")
        assertEquals("beta", sorted[0].name)
        assertEquals("alpha", sorted[1].name)
        assertEquals("scan.txt", sorted[2].name)
        assertEquals("notes.md", sorted[3].name)
    }

    @Test
    fun hiddenAndQueryFilter() {
        val visible = sortFileEntries(listing, FileSort.Name, ascending = true, showHidden = false, query = "")
        assertFalse(visible.any { it.name.startsWith(".") })
        val matched = sortFileEntries(listing, FileSort.Name, ascending = true, showHidden = true, query = "SCAN")
        assertEquals(listOf("scan.txt"), matched.map { it.name })
    }

    @Test
    fun sizeSortKeepsFoldersFirst() {
        val sorted = sortFileEntries(listing, FileSort.Size, ascending = true, showHidden = false, query = "")
        assertTrue(sorted.take(2).all { it.directory })
        assertEquals("scan.txt", sorted[2].name)
    }

    @Test
    fun kindFollowsExtensionThenBytes() {
        assertEquals(FileKind.Image, fileKindOf("shot.PNG", byteArrayOf(1)))
        assertEquals(FileKind.Pdf, fileKindOf("report.pdf", null))
        assertEquals(FileKind.Markdown, fileKindOf("README.md", null))
        assertEquals(FileKind.Text, fileKindOf("notes.unknown", "plain".toByteArray()))
        val binary = byteArrayOf(0, 1, 2)
        assertEquals(FileKind.Binary, fileKindOf("blob.unknown", binary))
        assertTrue(looksBinary(binary))
        assertFalse(looksBinary("plain".toByteArray()))
    }

    @Test
    fun decodeUtf8() {
        val decoded = decodePreview("中文".toByteArray(Charsets.UTF_8))
        assertEquals("中文", decoded.text)
        assertEquals("UTF-8", decoded.charset)
        assertFalse(decoded.garbled)
    }

    @Test
    fun decodeFallsBackToGb18030() {
        val bytes = "中文日志".toByteArray(Charset.forName("GB18030"))
        val decoded = decodePreview(bytes)
        assertEquals("中文日志", decoded.text)
        assertEquals("GB18030", decoded.charset)
    }

    @Test
    fun cutMultiByteTailIsNotCalledGarbled() {
        // "中" is E4 B8 AD; the preview cap can land mid-character.
        val bytes = byteArrayOf(0xE4.toByte(), 0xB8.toByte())
        val decoded = decodePreview(bytes)
        assertEquals("UTF-8", decoded.charset)
        assertFalse(decoded.garbled)
    }

    @Test
    fun explicitCharsetIsHonoured() {
        val bytes = "中文".toByteArray(Charset.forName("GB18030"))
        val decoded = decodePreview(bytes, "GB18030")
        assertEquals("中文", decoded.text)
        assertEquals("GB18030", decoded.charset)
    }

    @Test
    fun markdownSplitsTopLevelBlocks() {
        val blocks = fileMarkdownBlocks(
            """
            # Title

            Intro text
            continues here.

            - one
            - two

            > quoted

            ```sh
            id
            ```

            ---
            """.trimIndent()
        )
        assertTrue(blocks[0] is FileMarkdownBlock.Heading)
        assertEquals("Title", (blocks[0] as FileMarkdownBlock.Heading).text)
        assertEquals("Intro text continues here.", (blocks[1] as FileMarkdownBlock.Paragraph).text)
        assertEquals(2, blocks.count { it is FileMarkdownBlock.Bullet })
        assertTrue(blocks.any { it is FileMarkdownBlock.Quote })
        val code = blocks.filterIsInstance<FileMarkdownBlock.Code>().single()
        assertEquals("sh", code.language)
        assertEquals("id", code.text)
        assertTrue(blocks.last() is FileMarkdownBlock.Rule)
    }

    @Test
    fun unclosedFenceStillShowsItsBody() {
        val blocks = fileMarkdownBlocks("```\nunfinished")
        assertEquals("unfinished", (blocks.single() as FileMarkdownBlock.Code).text)
    }

    @Test
    fun sizesAreCompact() {
        assertEquals("900 B", formatFileSize(900))
        assertEquals("1.0 KB", formatFileSize(1024))
        assertEquals("1.5 MB", formatFileSize(1024L * 1024L * 3 / 2))
        assertEquals("2.0 GB", formatFileSize(2L * 1024 * 1024 * 1024))
    }
}
