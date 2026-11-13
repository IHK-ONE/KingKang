package com.kingkang.app.agent

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the wire shapes of dsh's session projections with fixtures copied from the
 * projection definitions in dsh (session-turn-outline, session-stats, subagent,
 * goal, model-selection, token-meter). A dsh upgrade that renames a field should
 * fail here with the projection name in the test, not silently blank the UI.
 */
class SessionProjectionsTest {

    private fun fixture(path: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$path")) {
            "missing fixture: $path"
        }.bufferedReader().use { it.readText() }

    private fun json(path: String): JSONObject = JSONObject(fixture(path))
    private fun array(path: String): JSONArray = JSONArray(fixture(path))

    @Test
    fun `goal projection keeps the wrapper roundsStarted`() {
        val goal = SessionProjections.goal(json("projections/goal.json"))

        checkNotNull(goal)
        assertEquals("goal_01J9ZK4T7M2Q", goal.id)
        assertEquals(3L, goal.revision)
        assertEquals("Add projection fixture coverage", goal.objective)
        assertEquals("active", goal.phase)
        assertEquals(12, goal.maxGoalRounds)
        assertEquals(4, goal.roundsStarted)
        assertNull(goal.blockedCode)
        assertTrue(goal.isActive)
    }

    @Test
    fun `goal projection exposes blocked reason`() {
        val goal = SessionProjections.goal(json("projections/goal-blocked.json"))

        checkNotNull(goal)
        assertEquals("blocked", goal.phase)
        assertEquals("awaiting-approval", goal.blockedCode)
        assertEquals(
            "The command needs user approval before it can run.",
            goal.blockedMessage
        )
        assertFalse(goal.isActive)
    }

    @Test
    fun `goal projection null and empty shapes decode to null`() {
        assertNull(SessionProjections.goal(null))
        assertNull(SessionProjections.goal(JSONObject.NULL))
        assertNull(SessionProjections.goal(JSONObject()))
        assertNull(SessionProjections.goal(JSONObject("""{"goal":null}""")))
    }

    @Test
    fun `goal view reads the flat service payload`() {
        val view = JSONObject(
            """
            {
              "id": "goal_01J9ZK4T7M2Q",
              "revision": 8,
              "objective": "Continue after restart",
              "phase": "paused",
              "maxGoalRounds": 12,
              "roundsStarted": 5,
              "activation": "armed"
            }
            """
        )

        val goal = SessionProjections.goalView(view)

        checkNotNull(goal)
        assertEquals(8L, goal.revision)
        assertEquals("paused", goal.phase)
        assertEquals(5, goal.roundsStarted)
        assertEquals("armed", goal.activation)
    }

    @Test
    fun `model selection prefers next and allows an omitted effort`() {
        val selection = SessionProjections.modelSelection(json("projections/model-selection.json"))

        assertEquals("deepseek", selection.provider)
        assertEquals("deepseek-reasoner", selection.model)
        assertNull(selection.reasoningEffort)
        assertTrue(selection.hasData)
    }

    @Test
    fun `model selection falls back to lastUsed when next is null`() {
        val selection = SessionProjections.modelSelection(
            JSONObject(
                """
                {
                  "lastUsed": { "provider": "deepseek", "model": "deepseek-chat" },
                  "next": null
                }
                """
            )
        )

        assertEquals("deepseek-chat", selection.model)
        assertEquals("deepseek / deepseek-chat", selection.label)
    }

    @Test
    fun `session stats keep optional timing fields distinct from zero`() {
        val stats = SessionProjections.sessionStats(json("projections/session-stats.json"))

        assertEquals(5, stats.turns)
        assertEquals(9, stats.steps)
        assertEquals(18_400L, stats.llmMs)
        assertEquals(2_600L, stats.toolMs)
        assertEquals(2_100L, stats.ttftMs)
        assertEquals(9, stats.ttftSteps)
        assertEquals(12_600L, stats.decodeMs)
        assertEquals(3_210L, stats.decodeTokens)
        assertTrue(stats.hasData)

        val empty = SessionProjections.sessionStats(JSONObject())
        assertNull(empty.llmMs)
        assertNull(empty.toolMs)
        assertFalse(empty.hasData)
    }

    @Test
    fun `turn outline preserves order and skips invalid rows`() {
        val entries = SessionProjections.turnOutline(array("projections/turn-outline.json"))

        assertEquals(2, entries.size)
        assertEquals(1, entries[0].turn)
        assertEquals(4L, entries[0].seq)
        assertEquals("Scan the loopback interface", entries[0].prompt)
        assertTrue(entries[1].response.startsWith("Wrote findings.md"))

        val filtered = SessionProjections.turnOutline(
            JSONArray("""[{"turn":0},{"turn":3,"prompt":"ok"},{"turn":-1}]""")
        )
        assertEquals(1, filtered.size)
        assertEquals(3, filtered[0].turn)
        assertNull(filtered[0].seq)
    }

    @Test
    fun `subagent timing reads the active interval and completion flag`() {
        val timing = SessionProjections.subagentTiming(json("projections/subagent-timing.json"))

        checkNotNull(timing)
        assertEquals(1_250L, timing.settledMs)
        assertEquals(1_730_000_010_000L, timing.activeSince)
        assertEquals(1_730_000_014_200L, timing.activeThrough)
        assertEquals(true, timing.lastTurnCompleted)

        val missingFlag = SessionProjections.subagentTiming(
            JSONObject("""{"settledMs":0}""")
        )
        checkNotNull(missingFlag)
        assertNull(missingFlag.lastTurnCompleted)
        assertNull(missingFlag.activeSince)
        assertNull(SessionProjections.subagentTiming(null))
    }

    @Test
    fun `token usage totals cached and uncached input`() {
        val usage = SessionProjections.tokenUsage(json("projections/token-usage.json"))

        assertEquals(1_200L, usage.uncachedInputTokens)
        assertEquals(480L, usage.outputTokens)
        assertEquals(32_000L, usage.cacheReadTokens)
        assertEquals(800L, usage.cacheWriteTokens)
        assertEquals(34_000L, usage.inputTokens)
        assertEquals(34_480L, usage.totalTokens)
        assertTrue(usage.hasData)
    }

    @Test
    fun `older token usage falls back to inputTokens`() {
        val usage = SessionProjections.tokenUsage(
            JSONObject("""{"inputTokens":900,"outputTokens":100}""")
        )

        assertEquals(900L, usage.uncachedInputTokens)
        assertEquals(1_000L, usage.totalTokens)
    }

    @Test
    fun `context pressure uses projected tokens and computes fill`() {
        val pressure = SessionProjections.contextPressure(json("projections/context-pressure.json"))

        assertEquals(41_000L, pressure.pressureTokens)
        assertEquals(43_600L, pressure.projectedTokens)
        assertEquals(128_000L, pressure.contextWindow)
        assertEquals(43_600L, pressure.usedTokens)
        assertEquals(43_600f / 128_000f, pressure.fraction ?: 0f, 0.0001f)
    }

    @Test
    fun `context breakdown sums the composition`() {
        val breakdown = SessionProjections.contextBreakdown(json("projections/context-breakdown.json"))

        assertEquals(6_100L, breakdown.systemTokens)
        assertEquals(2_200L, breakdown.toolsTokens)
        assertEquals(35_300L, breakdown.messageTokens)
        assertEquals(43_600L, breakdown.totalTokens)
    }

    @Test
    fun `assistant message usage decodes the event payload`() {
        val event = json("events/assistant-message.json")
        val usage = SessionProjections.usage(event.optJSONObject("usage"))

        checkNotNull(usage)
        assertEquals(53_000L, usage.inputTokens)
        assertEquals(640L, usage.outputTokens)
        assertEquals(32_000L, usage.cacheReadTokens)
        assertEquals(800L, usage.cacheWriteTokens)
        assertEquals(96L, usage.reasoningTokens)
        assertEquals(53_640L, usage.totalTokens)
        assertTrue(usage.hasData)
    }

    @Test
    fun `usage with no buckets is treated as absent`() {
        assertNull(SessionProjections.usage(null))
        assertNull(SessionProjections.usage(JSONObject()))
        assertNull(
            SessionProjections.usage(
                JSONObject("""{"inputTokens":0,"outputTokens":0}""")
            )
        )
    }

    @Test
    fun `unknown projections keep the newest entries and evict the oldest`() {
        var kept = emptyMap<String, String>()
        for (index in 0 until 30) {
            kept = SessionProjections.retainProjection(
                kept,
                "projection-$index",
                """{"index":$index}"""
            ) ?: kept
        }

        assertEquals(SessionProjections.MAX_EXTRA_PROJECTIONS, kept.size)
        assertEquals("""{"index":6}""", kept["projection-6"])
        assertEquals("""{"index":29}""", kept["projection-29"])
        assertFalse(kept.containsKey("projection-5"))
    }

    @Test
    fun `unchanged and empty projections do not churn state`() {
        val current = mapOf("futureProjection" to """{"a":1}""")

        assertNull(SessionProjections.retainProjection(current, "futureProjection", """{"a":1}"""))
        assertNull(SessionProjections.retainProjection(current, "", "value"))
        assertNull(SessionProjections.retainProjection(current, "blank", "   "))
        assertNull(SessionProjections.retainProjection(current, "nothing", null))
    }

    @Test
    fun `oversized projection values are truncated`() {
        val kept = SessionProjections.retainProjection(
            emptyMap(),
            "huge",
            "x".repeat(SessionProjections.MAX_EXTRA_PROJECTION_CHARS + 50)
        )

        checkNotNull(kept)
        assertEquals(
            SessionProjections.MAX_EXTRA_PROJECTION_CHARS,
            kept.getValue("huge").length
        )
    }

    @Test
    fun `unknown event kinds are deduplicated and bounded`() {
        var kinds = emptyList<String>()
        for (index in 0 until 45) {
            kinds = SessionProjections.recordUnknownEvent(kinds, "kind-$index") ?: kinds
        }

        assertEquals(SessionProjections.MAX_UNKNOWN_EVENT_TYPES, kinds.size)
        assertEquals("kind-44", kinds.last())
        assertFalse(kinds.contains("kind-4"))

        assertNull(SessionProjections.recordUnknownEvent(kinds, "kind-44"))
        assertNull(SessionProjections.recordUnknownEvent(kinds, " "))
    }

    @Test
    fun `agent team decodes members and tasks`() {
        val team = SessionProjections.agentTeam(
            JSONObject(
                """
                {
                  "members": [
                    { "id": "lead", "name": "Lead", "role": "lead", "phase": "active" },
                    { "id": "worker-1", "role": "teammate", "phase": "failed", "error": "timeout" }
                  ],
                  "tasks": [
                    { "id": "t1", "revision": 2, "subject": "Scan", "status": "completed", "ownerId": "worker-1" }
                  ]
                }
                """
            )
        )

        checkNotNull(team)
        assertEquals(2, team.members.size)
        assertEquals("worker-1", team.members[1].name)
        assertEquals("timeout", team.members[1].error)
        assertEquals("lead", team.lead?.id)
        assertEquals("completed", team.tasks.single().status)
        assertEquals("Scan", team.tasks.single().subject)
    }

    @Test
    fun `action cards join proposals with their decisions`() {
        val view = JSONObject(
            """
            {
              "proposals": {
                "call-a": {
                  "callId": "call-a",
                  "turn": 2,
                  "action": "create_personalized_page",
                  "title": "Build a log viewer",
                  "brief": "A page that follows the Kali log.",
                  "details": { "slot": "page" }
                },
                "call-b": {
                  "callId": "call-b",
                  "turn": 3,
                  "action": "create_personalized_page",
                  "title": "Build a port scanner",
                  "brief": "Scan the local ports.",
                  "details": { "slot": "sidebar-right" }
                }
              },
              "decisions": {
                "call-a": { "callId": "call-a", "action": "create", "phase": "installed" }
              }
            }
            """
        )

        val cards = SessionProjections.actionCards(view)

        assertEquals(2, cards.size)
        assertEquals("call-a", cards[0].callId)
        assertEquals("installed", cards[0].phase)
        assertEquals("page", cards[0].slot)
        assertEquals("call-b", cards[1].callId)
        assertEquals("unstarted", cards[1].phase)
        assertEquals("sidebar-right", cards[1].slot)
        assertFalse(cards[0].settled)
    }

    @Test
    fun `dismissed action cards settle and leave the transcript`() {
        val view = JSONObject(
            """
            {
              "proposals": {
                "call-a": {
                  "callId": "call-a",
                  "turn": 1,
                  "action": "other_kind",
                  "title": "Try something",
                  "brief": "Brief",
                  "details": {}
                }
              },
              "decisions": {
                "call-a": { "callId": "call-a", "action": "dismiss", "phase": "dismissed" }
              }
            }
            """
        )

        val card = SessionProjections.actionCards(view).single()
        assertEquals("dismissed", card.phase)
        assertTrue(card.settled)
        assertNull(card.slot)
    }
}
