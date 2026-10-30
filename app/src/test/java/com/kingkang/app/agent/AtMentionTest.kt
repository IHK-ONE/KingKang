package com.kingkang.app.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Native coverage for dsh's browser-safe `@` grammar. */
class AtMentionTest {

    @Test
    fun `a plain token carries the path after the at sign`() {
        val token = activeAtToken("check @src/main.kt", "check @src/main.kt".length)
        assertEquals("src/main.kt", token?.query)
        assertEquals(6, token?.start)
        assertFalse(token!!.quoted)
    }

    @Test
    fun `an email address is not a mention`() {
        assertNull(activeAtToken("write to me@example.com", 23))
    }

    @Test
    fun `a mid-word at sign is not a mention`() {
        assertNull(activeAtToken("foo@bar", 7))
    }

    @Test
    fun `a quoted token keeps spaces and reports the open quote`() {
        val text = "open @\"my dir/"
        val token = activeAtToken(text, text.length)
        assertTrue(token!!.quoted)
        assertEquals("my dir/", token.query)
    }

    @Test
    fun `a token ends at the cursor rather than the line`() {
        val text = "see @a and more"
        val token = activeAtToken(text, 6)
        assertEquals("a", token?.query)
        assertEquals(4, token?.start)
    }

    @Test
    fun `paths with whitespace use the quoted spelling`() {
        assertEquals("@\"a b.txt\"", formatFileMention("a b.txt", false, false))
        assertEquals("@\"src dir/", formatFileMention("src dir", true, false))
        assertEquals("@src/", formatFileMention("src", true, false))
    }

    @Test
    fun `a quoted completion preserves the quote`() {
        assertEquals("@\"plain.txt\"", formatFileMention("plain.txt", false, true))
    }

    @Test
    fun `paths the grammar cannot represent are rejected`() {
        assertNull(formatFileMention("bad\"name", false, false))
        assertNull(formatFileMention("line\nbreak", false, false))
    }
}
