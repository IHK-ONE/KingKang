package com.kingkang.app.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The status vocabulary dsh-web derives from a workflow run's stop reason. */
class WorkflowItemTest {

    private fun item(stopReason: String? = null, interrupted: Boolean = false) =
        WorkflowItem(key = "wf:run-1", runId = "run-1", stopReason = stopReason, interrupted = interrupted)

    @Test
    fun `an unsettled run is running`() {
        assertEquals("running", item().status)
        assertTrue(item().running)
    }

    @Test
    fun `stop reasons map onto the web labels`() {
        assertEquals("completed", item("completed").status)
        assertEquals("cancelled", item("cancelled").status)
        assertEquals("failed", item("error").status)
        assertFalse(item("completed").running)
    }

    @Test
    fun `a closed turn interrupts a run that never settled`() {
        assertEquals("interrupted", item(interrupted = true).status)
    }

    @Test
    fun `a stop reason wins over the interrupted flag`() {
        val settled = item(stopReason = "completed", interrupted = true)
        assertEquals("completed", settled.status)
    }
}
