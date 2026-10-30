package com.kingkang.app.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileNamesTest {

    @Test
    fun `strips separators and traversal`() {
        assertEquals("passwd", FileNames.sanitize("../../../etc/passwd"))
        assertEquals("b", FileNames.sanitize("a\\b"))
        assertEquals("foo.txt", FileNames.sanitize("C:\\tmp\\foo.txt"))
    }

    @Test
    fun `removes control characters and reserved characters`() {
        assertEquals("abc_", FileNames.sanitize("a\u0001b\u007fc?"))
        assertEquals("a_b", FileNames.sanitize("a?b"))
        assertEquals("report.pdf", FileNames.sanitize("report.pdf"))
    }

    @Test
    fun `falls back for empty or dot names`() {
        assertEquals("download", FileNames.sanitize(""))
        assertEquals("download", FileNames.sanitize(".."))
        assertEquals("download", FileNames.sanitize("   "))
    }

    @Test
    fun `unique does not overwrite`() {
        val folder = TemporaryFolder()
        folder.create()
        try {
            val first = folder.newFile("a.txt")
            val second = FileNames.unique(folder.root, "a.txt")
            assertEquals("a-1.txt", second.name)
            assertFalse(first.absolutePath == second.absolutePath)
            second.createNewFile()
            assertEquals("a-2.txt", FileNames.unique(folder.root, "a.txt").name)
        } finally {
            folder.delete()
        }
    }

    @Test
    fun `unique keeps plain name when free`() {
        val folder = TemporaryFolder()
        folder.create()
        try {
            assertEquals("free.txt", FileNames.unique(folder.root, "free.txt").name)
        } finally {
            folder.delete()
        }
    }
}
