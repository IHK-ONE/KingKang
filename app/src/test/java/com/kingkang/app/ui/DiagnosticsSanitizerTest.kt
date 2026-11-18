package com.kingkang.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The diagnostics page promises that copied logs carry no credentials. */
class DiagnosticsSanitizerTest {

    @Test
    fun masks_query_tokens_and_bearer_headers() {
        val line = "GET /api?token=abc123&x=1 Authorization: Bearer eyJhbGciOiJIUzI1NiJ9"
        val sanitized = sanitizeLogLine(line)
        assertFalse(sanitized.contains("abc123"))
        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertEquals("GET /api?token=***&x=1 Authorization: Bearer ***", sanitized)
    }

    @Test
    fun masks_sk_keys_anywhere_in_a_line() {
        val sanitized = sanitizeLogLine("credential stored: sk-test-1234567890abcdef")
        assertEquals("credential stored: sk-***", sanitized)
    }

    @Test
    fun leaves_ordinary_log_lines_alone() {
        val line = "[kk-agent] workspace.list ok (190ms)"
        assertEquals(line, sanitizeLogLine(line))
    }
}
