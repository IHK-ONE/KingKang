package com.kingkang.app.device

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * A bounded, local record of the commands the AI ran through `kk-adb`.
 *
 * It exists so the user can see exactly what the agent did with shell authority. It is
 * app-private, never uploaded, and keeps only the last [MAX_ENTRIES]; the command and the
 * first line of the reply are enough to audit without storing a full tool output.
 */
object DeviceAudit {

    private const val FILE_NAME = "device-audit.log"
    private const val MAX_ENTRIES = 200
    private const val MAX_COMMAND = 500
    private const val MAX_RESULT = 200

    data class Entry(
        val time: Long,
        val command: String,
        val result: String,
        val ok: Boolean
    )

    fun record(context: Context, command: String, reply: String) {
        val entry = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("c", command.replace(Regex("[\\r\\n\\t]+"), " ").take(MAX_COMMAND))
            .put(
                "r",
                reply.lineSequence().firstOrNull().orEmpty()
                    .replace(Regex("[\\t]+"), " ")
                    .take(MAX_RESULT)
            )
            .put("ok", !reply.startsWith("kk-adb:"))
        runCatching {
            val file = file(context)
            // The first entry has no existing file; readLines() would throw and the
            // runCatching would swallow the write, leaving an empty audit forever.
            val lines = if (file.isFile) file.readLines() else emptyList()
            file.writeText((lines.takeLast(MAX_ENTRIES - 1) + entry.toString()).joinToString("\n"))
        }
    }

    fun read(context: Context): List<Entry> {
        return runCatching {
            file(context).readLines().mapNotNull { line ->
                val json = runCatching { JSONObject(line) }.getOrNull() ?: return@mapNotNull null
                Entry(
                    time = json.optLong("t"),
                    command = json.optString("c"),
                    result = json.optString("r"),
                    ok = json.optBoolean("ok", true)
                )
            }.asReversed()
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
