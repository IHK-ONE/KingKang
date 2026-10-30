package com.kingkang.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPolicyTest {

    @Test
    fun bareHostAndPortBecomeLocalUrls() {
        assertEquals("http://127.0.0.1:3000", BrowserPolicy.normalize("127.0.0.1:3000"))
        assertEquals("http://localhost:8080/app", BrowserPolicy.normalize("localhost:8080/app"))
        assertEquals("http://localhost", BrowserPolicy.normalize("  localhost  "))
        assertEquals("https://example.com", BrowserPolicy.normalize("https://example.com"))
    }

    @Test
    fun invalidInputIsRejected() {
        assertNull(BrowserPolicy.normalize(""))
        assertNull(BrowserPolicy.normalize("   "))
        assertNull(BrowserPolicy.normalize("http://"))
    }

    @Test
    fun loopbackLoadsWithoutAsking() {
        assertEquals(UrlDecision.Allow, BrowserPolicy.decide("http://127.0.0.1:3000/"))
        assertEquals(UrlDecision.Allow, BrowserPolicy.decide("http://localhost:8080"))
        assertEquals(UrlDecision.Allow, BrowserPolicy.decide("http://lab.localhost/"))
        assertEquals(UrlDecision.Allow, BrowserPolicy.decide("http://[::1]:9000/"))
    }

    @Test
    fun remoteHttpsNeedsConfirmation() {
        assertEquals(UrlDecision.Confirm, BrowserPolicy.decide("https://example.com/docs"))
        assertEquals(UrlDecision.Confirm, BrowserPolicy.decide("https://127.0.0.1.evil.com/"))
    }

    @Test
    fun remoteCleartextAndOtherSchemesAreBlocked() {
        assertEquals(UrlDecision.Block, BrowserPolicy.decide("http://example.com"))
        assertEquals(UrlDecision.Block, BrowserPolicy.decide("file:///etc/passwd"))
        assertEquals(UrlDecision.Block, BrowserPolicy.decide("javascript:alert(1)"))
        assertEquals(UrlDecision.Block, BrowserPolicy.decide("intent://scan/#Intent;end"))
        assertEquals(UrlDecision.Block, BrowserPolicy.decide("content://com.example/x"))
        assertEquals(UrlDecision.Block, BrowserPolicy.decide("not a url"))
    }

    @Test
    fun userInfoCannotDisguiseTheHost() {
        // The real host is evil.com even though the text starts with a loopback address.
        assertEquals(UrlDecision.Confirm, BrowserPolicy.decide("https://127.0.0.1@evil.com/"))
        assertFalse(BrowserPolicy.isLoopback("https://127.0.0.1@evil.com/"))
    }

    @Test
    fun loopbackDetection() {
        assertTrue(BrowserPolicy.isLoopback("http://127.0.0.1"))
        assertTrue(BrowserPolicy.isLoopback("http://localhost:3000"))
        assertTrue(BrowserPolicy.isLoopback("http://[::1]"))
        assertFalse(BrowserPolicy.isLoopback("https://example.com"))
        assertFalse(BrowserPolicy.isLoopback("http://127.0.0.1.example.com"))
        assertFalse(BrowserPolicy.isLoopback("garbage"))
        assertEquals("example.com", BrowserPolicy.hostOf("https://example.com/a"))
    }
}
