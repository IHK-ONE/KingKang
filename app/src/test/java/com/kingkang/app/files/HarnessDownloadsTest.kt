package com.kingkang.app.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessDownloadsTest {

    @Test
    fun `only loopback http and https are allowed`() {
        assertTrue(HarnessDownloads.isLoopbackUrl("http://127.0.0.1:8080/api/file"))
        assertTrue(HarnessDownloads.isLoopbackUrl("https://localhost/file"))
        assertTrue(HarnessDownloads.isLoopbackUrl("http://[::1]:9000/x"))
        assertFalse(HarnessDownloads.isLoopbackUrl("http://example.com/file"))
        assertFalse(HarnessDownloads.isLoopbackUrl("file:///etc/passwd"))
        assertFalse(HarnessDownloads.isLoopbackUrl("javascript:alert(1)"))
        assertFalse(HarnessDownloads.isLoopbackUrl("http://127.0.0.1.evil.com/x"))
    }

    @Test
    fun `prefers rfc 5987 filename`() {
        val name = HarnessDownloads.fileNameFor(
            "attachment; filename=\"plain.zip\"; filename*=UTF-8''%E6%8A%A5%E5%91%8A.zip",
            "http://127.0.0.1/x",
            "application/zip"
        )
        assertEquals("报告.zip", name)
    }

    @Test
    fun `uses quoted filename and sanitizes it`() {
        val name = HarnessDownloads.fileNameFor(
            "attachment; filename=\"../../etc/passwd\"",
            "http://127.0.0.1/x",
            "text/plain"
        )
        assertEquals("passwd.txt", name)
    }

    @Test
    fun `falls back to the url leaf`() {
        val name = HarnessDownloads.fileNameFor(null, "http://127.0.0.1/files/report.pdf", "application/pdf")
        assertEquals("report.pdf", name)
    }

    @Test
    fun `adds an extension from mime when missing`() {
        val name = HarnessDownloads.fileNameFor(
            "attachment; filename=session-log",
            "http://127.0.0.1/x",
            "application/zip"
        )
        assertEquals("session-log.zip", name)
    }

    @Test
    fun `unknown mime keeps the plain name`() {
        val name = HarnessDownloads.fileNameFor(
            "attachment; filename=data",
            "http://127.0.0.1/x",
            "application/octet-stream"
        )
        assertEquals("data", name)
    }
}
