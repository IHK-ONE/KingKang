package com.kingkang.app.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure decoders for dsh's session projections and usage payloads.
 *
 * These are kept out of [AgentEngine] so the wire shapes dsh actually sends can be
 * pinned down by fixtures instead of only by whichever code path happened to run.
 * Every decoder is defensive: a projection that is missing, null, or malformed
 * yields the model's empty value rather than throwing into the event loop.
 */
internal object SessionProjections {
    const val MAX_EXTRA_PROJECTIONS = 24
    const val MAX_EXTRA_PROJECTION_CHARS = 4_000
    const val MAX_UNKNOWN_EVENT_TYPES = 40

    /**
     * Keeps a projection this build has no typed home for, in a bounded newest-N map.
     *
     * Returns null when nothing changed so the caller can keep the existing state
     * identity. When the map is full the *oldest* entry is evicted: a newer dsh
     * adding projections must not push out the ones it just sent.
     */
    fun retainProjection(
        current: Map<String, String>,
        key: String,
        value: Any?
    ): Map<String, String>? {
        if (key.isBlank() || value == null) return null
        val encoded = when (value) {
            is String -> value
            else -> value.toString()
        }.take(MAX_EXTRA_PROJECTION_CHARS)
        if (encoded.isBlank()) return null
        if (current[key] == encoded) return null
        val kept = LinkedHashMap<String, String>()
        current.entries
            .filter { it.key != key }
            .takeLast(MAX_EXTRA_PROJECTIONS - 1)
            .forEach { kept[it.key] = it.value }
        kept[key] = encoded
        return kept
    }

    /** Remembers a distinct unhandled event kind, bounded so a chatty host cannot grow it. */
    fun recordUnknownEvent(current: List<String>, type: String): List<String>? {
        if (type.isBlank() || type in current) return null
        return (current + type).takeLast(MAX_UNKNOWN_EVENT_TYPES)
    }

    fun usage(value: JSONObject?): AgentUsage? {
        if (value == null) return null
        val usage = AgentUsage(
            inputTokens = value.optLong("inputTokens"),
            outputTokens = value.optLong("outputTokens"),
            cacheReadTokens = value.optLong("cacheReadTokens"),
            cacheWriteTokens = value.optLong("cacheWriteTokens"),
            reasoningTokens = value.optLong("reasoningTokens")
        )
        return if (usage.hasData) usage else null
    }

    fun tokenUsage(value: Any?): AgentTokenUsage {
        val json = value as? JSONObject ?: return AgentTokenUsage()
        return AgentTokenUsage(
            uncachedInputTokens = json.optLong("uncachedInputTokens")
                .orIfZero { json.optLong("inputTokens") },
            outputTokens = json.optLong("outputTokens"),
            cacheReadTokens = json.optLong("cacheReadTokens"),
            cacheWriteTokens = json.optLong("cacheWriteTokens")
        )
    }

    fun contextPressure(value: Any?): AgentContextPressure {
        val json = value as? JSONObject ?: return AgentContextPressure()
        return AgentContextPressure(
            pressureTokens = json.longOrNull("pressureTokens"),
            projectedTokens = json.longOrNull("projectedTokens"),
            contextWindow = json.longOrNull("contextWindow")
        )
    }

    fun contextBreakdown(value: Any?): AgentContextBreakdown {
        val json = value as? JSONObject ?: return AgentContextBreakdown()
        return AgentContextBreakdown(
            systemTokens = json.optLong("systemTokens"),
            toolsTokens = json.optLong("toolsTokens"),
            messageTokens = json.optLong("messageTokens")
        )
    }

    /**
     * dsh 0.2.2's `conversationContinuation`: the seq of the latest failed turn that can
     * be retried with an empty prompt, or null when the conversation is not continuable.
     */
    fun continuation(value: Any?): Long? = when (value) {
        is Number -> value.toLong().takeIf { it > 0L }
        is String -> value.toLongOrNull()?.takeIf { it > 0L }
        else -> null
    }

    /** The `{goal: {...}, roundsStarted}` wrapper carried by the `goal` projection. */
    fun goal(value: Any?): AgentGoal? {
        val json = value as? JSONObject ?: return null
        val goal = json.optJSONObject("goal") ?: return null
        val blocked = goal.optJSONObject("blockedReason")
        val parsed = AgentGoal(
            id = goal.optString("id"),
            revision = goal.optLong("revision"),
            objective = goal.optString("objective"),
            phase = goal.optString("phase"),
            maxGoalRounds = goal.optInt("maxGoalRounds"),
            roundsStarted = json.optInt("roundsStarted", goal.optInt("roundsStarted")),
            blockedCode = blocked?.optString("code")?.takeIf { it.isNotBlank() },
            blockedMessage = blocked?.optString("message")?.takeIf { it.isNotBlank() }
        )
        return if (parsed.hasData) parsed else null
    }

    /**
     * Reads the flat GoalView the goal service returns (`id/revision/objective/...`),
     * as opposed to the projection's `{goal:{…}, roundsStarted}` wrapper.
     */
    fun goalView(value: JSONObject?): AgentGoal? {
        if (value == null) return null
        val blocked = value.optJSONObject("blockedReason")
        val parsed = AgentGoal(
            id = value.optString("id"),
            revision = value.optLong("revision"),
            objective = value.optString("objective"),
            phase = value.optString("phase"),
            maxGoalRounds = value.optInt("maxGoalRounds"),
            roundsStarted = value.optInt("roundsStarted"),
            blockedCode = blocked?.optString("code")?.takeIf { it.isNotBlank() },
            blockedMessage = blocked?.optString("message")?.takeIf { it.isNotBlank() },
            activation = value.optString("activation").takeIf { it.isNotBlank() }
        )
        return if (parsed.hasData) parsed else null
    }

    /** Wire view is `{lastUsed, next}`; `next` already falls back to `lastUsed` in dsh. */
    fun modelSelection(value: Any?): AgentModelSelection {
        val json = value as? JSONObject ?: return AgentModelSelection()
        val selected = json.optJSONObject("next") ?: json.optJSONObject("lastUsed")
            ?: return AgentModelSelection()
        return AgentModelSelection(
            provider = selected.optString("provider"),
            model = selected.optString("model"),
            reasoningEffort = selected.optString("reasoningEffort").takeIf { it.isNotBlank() }
        )
    }

    fun subagentTiming(value: Any?): AgentSubagentTiming? {
        val json = value as? JSONObject ?: return null
        val active = json.optJSONObject("active")
        return AgentSubagentTiming(
            settledMs = json.optLong("settledMs"),
            activeSince = active?.longOrNull("since"),
            activeThrough = active?.longOrNull("through"),
            lastTurnCompleted = if (json.has("lastTurnCompleted")) {
                json.optBoolean("lastTurnCompleted")
            } else {
                null
            }
        )
    }

    fun agentTeam(value: Any?): AgentTeam? {
        val json = value as? JSONObject ?: return null
        val members = mutableListOf<AgentTeamMember>()
        val memberArray = json.optJSONArray("members")
        if (memberArray != null) {
            for (index in 0 until memberArray.length()) {
                val item = memberArray.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                members += AgentTeamMember(
                    id = id,
                    name = item.optString("name").ifBlank { id },
                    role = item.optString("role").ifBlank { "teammate" },
                    phase = item.optString("phase").ifBlank { "active" },
                    error = item.optString("error").takeIf { it.isNotBlank() }
                )
            }
        }
        val tasks = mutableListOf<AgentTeamTask>()
        val taskArray = json.optJSONArray("tasks")
        if (taskArray != null) {
            for (index in 0 until taskArray.length()) {
                val item = taskArray.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                tasks += AgentTeamTask(
                    id = id,
                    revision = item.optLong("revision"),
                    subject = item.optString("subject"),
                    description = item.optString("description"),
                    status = item.optString("status").ifBlank { "pending" },
                    ownerId = item.optString("ownerId").takeIf { it.isNotBlank() }
                )
            }
        }
        val team = AgentTeam(
            members = members,
            tasks = tasks,
            failure = json.optString("failure").takeIf { it.isNotBlank() },
            enabled = if (json.has("enabled")) json.optBoolean("enabled") else null
        )
        return if (team.hasData) team else null
    }

    /**
     * `actionCards` is `{proposals: {callId: card}, decisions: {callId: decision}}`. The
     * two records are joined here so the transcript can render one card per proposal.
     */
    fun actionCards(value: Any?): List<AgentActionCard> {
        val json = value as? JSONObject ?: return emptyList()
        val proposals = json.optJSONObject("proposals") ?: return emptyList()
        val decisions = json.optJSONObject("decisions")
        val cards = mutableListOf<AgentActionCard>()
        val keys = proposals.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = proposals.optJSONObject(key) ?: continue
            val callId = item.optString("callId").ifBlank { key }
            if (callId.isBlank()) continue
            val decision = decisions?.optJSONObject(key)?.let { recorded ->
                AgentActionCardDecision(
                    action = recorded.optString("action"),
                    phase = recorded.optString("phase"),
                    data = recorded.optJSONObject("data")?.toString().orEmpty()
                )
            }
            cards += AgentActionCard(
                callId = callId,
                turn = item.optInt("turn"),
                action = item.optString("action"),
                title = item.optString("title"),
                brief = item.optString("brief"),
                details = item.optJSONObject("details")?.toString() ?: "{}",
                decision = decision
            )
        }
        return cards.sortedWith(compareBy({ it.turn }, { it.callId }))
    }

    /** `turnOutline` is an array of `{turn, seq, prompt, response}` entries. */
    fun turnOutline(value: Any?): List<AgentTurnOutlineEntry> {
        val array = value as? JSONArray ?: return emptyList()
        val entries = mutableListOf<AgentTurnOutlineEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val turn = item.optInt("turn", -1)
            if (turn <= 0) continue
            entries += AgentTurnOutlineEntry(
                turn = turn,
                seq = item.longOrNull("seq"),
                prompt = item.optString("prompt"),
                response = item.optString("response")
            )
        }
        return entries
    }

    fun sessionStats(value: Any?): AgentSessionStats {
        val json = value as? JSONObject ?: return AgentSessionStats()
        return AgentSessionStats(
            turns = json.optInt("turns"),
            steps = json.optInt("steps"),
            llmMs = json.longOrNull("llmMs"),
            toolMs = json.longOrNull("toolMs"),
            ttftMs = json.longOrNull("ttftMs"),
            ttftSteps = json.optInt("ttftSteps"),
            decodeMs = json.longOrNull("decodeMs"),
            decodeTokens = json.longOrNull("decodeTokens")
        )
    }

    private fun Long.orIfZero(alternative: () -> Long): Long =
        if (this != 0L) this else alternative()

    private fun JSONObject.longOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null
}
