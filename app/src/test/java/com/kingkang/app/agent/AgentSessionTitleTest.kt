package com.kingkang.app.agent

import org.junit.Assert.assertEquals
import org.junit.Test

/** The capability note appended to a first prompt must not become part of the chat name. */
class AgentSessionTitleTest {

    @Test
    fun `strips the full capability marker`() {
        val session = AgentSession(
            id = "s1",
            title = "检查这台设备的 Kali 环境 [KingKang 环境]\n当前设备能力（仅在需要访问 Android 设备本身时使用）"
        )
        assertEquals("检查这台设备的 Kali 环境", session.displayTitle)
    }

    @Test
    fun `strips a marker truncated by dsh`() {
        val session = AgentSession(id = "s2", title = "检查这台设备的 Kali 环境 [Ki")
        assertEquals("检查这台设备的 Kali 环境", session.displayTitle)
    }

    @Test
    fun `keeps an ordinary title`() {
        val session = AgentSession(id = "s3", title = "Kali runtime status page")
        assertEquals("Kali runtime status page", session.displayTitle)
    }

    @Test
    fun `blank titles still fall back`() {
        assertEquals("新对话", AgentSession(id = "s4", title = "", blank = true).displayTitle)
        assertEquals("未命名对话", AgentSession(id = "s5", title = "").displayTitle)
    }
}
