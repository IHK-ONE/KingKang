package com.kingkang.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessTargetTest {

    private val base = "http://127.0.0.1:4665"

    @Test
    fun `no path keeps the harness home`() {
        assertEquals(base, harnessTarget(base, null))
        assertEquals(base, harnessTarget(base, ""))
        assertEquals(base, harnessTarget(base, "   "))
    }

    @Test
    fun `rooted paths are appended to the loopback origin`() {
        assertEquals("$base/settings", harnessTarget(base, "/settings"))
        assertEquals("$base/settings?tab=plugins", harnessTarget("$base/", "/settings?tab=plugins"))
        assertEquals("$base/plugins", harnessTarget(base, "  /plugins  "))
    }

    @Test
    fun `origin-changing input falls back to home`() {
        assertEquals(base, harnessTarget(base, "//evil.example/settings"))
        assertEquals(base, harnessTarget(base, "https://evil.example"))
        assertEquals(base, harnessTarget(base, "settings"))
        assertEquals(base, harnessTarget(base, "/set\\tings"))
        assertEquals(base, harnessTarget(base, "/set\ntings"))
    }

    @Test
    fun `no harness url means nothing to load`() {
        assertNull(harnessTarget(null, "/settings"))
        assertNull(harnessTarget("", "/settings"))
    }

    @Test
    fun `media grants require the exact loopback harness origin`() {
        val harness = "http://127.0.0.1:4665"
        assertTrue(isHarnessOrigin("http://127.0.0.1:4665", harness))
        assertTrue(isHarnessOrigin("http://127.0.0.1:4665/", "$harness/settings"))
        assertEquals(false, isHarnessOrigin("http://127.0.0.1:4666", harness))
        assertEquals(false, isHarnessOrigin("http://localhost:4665", harness))
        assertEquals(false, isHarnessOrigin("https://127.0.0.1:4665", harness))
        assertEquals(false, isHarnessOrigin("http://127.0.0.1:4665.evil.example", harness))
        assertEquals(false, isHarnessOrigin("http://192.168.1.2:4665", harness))
        assertEquals(false, isHarnessOrigin(null, harness))
        assertEquals(false, isHarnessOrigin("http://127.0.0.1:4665", null))
    }

    @Test
    fun `default ports are compared explicitly`() {
        assertTrue(isHarnessOrigin("https://127.0.0.1", "https://127.0.0.1:443"))
        assertEquals(false, isHarnessOrigin("https://127.0.0.1", "http://127.0.0.1:80"))
    }

    @Test
    fun `chromium errors become actionable messages`() {
        assertTrue(
            harnessErrorText("net::ERR_CONNECTION_REFUSED", -6).contains("Agent 连接")
        )
        assertTrue(harnessErrorText("net::ERR_CERT_AUTHORITY_INVALID", -200).contains("证书"))
        assertTrue(harnessErrorText("net::ERR_TIMED_OUT", -7).contains("超时"))
        assertEquals("Harness 加载失败（错误码 -2）", harnessErrorText(null, -2))
        assertEquals("自定义错误", harnessErrorText(" 自定义错误 ", -2))
    }
}
