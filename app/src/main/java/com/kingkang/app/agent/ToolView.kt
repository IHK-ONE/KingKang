package com.kingkang.app.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured, client-derived view of one tool call — the Android counterpart of the cards
 * dsh-web builds from a tool's name, model-supplied arguments, and result `meta`.
 *
 * The derivation is deliberately read-only and defensive: arguments come from the model,
 * so anything malformed falls back to [Generic] rather than throwing or inventing detail.
 */
sealed interface ToolView {
    data class Shell(
        val command: String,
        val description: String?,
        val workdir: String?,
        /** dsh's persistent shell settles on the generic path, so no exit status is implied. */
        val oneShot: Boolean,
        val exitCode: Int?,
        val signal: String?,
        val output: String?
    ) : ToolView

    data class Diff(
        val hunks: List<Hunk>,
        /** True when dsh supplied the applied hunks in result meta. */
        val applied: Boolean
    ) : ToolView {
        data class Hunk(val path: String, val oldText: String?, val newText: String)
    }

    data class ReadFile(
        val path: String,
        val offset: Int?,
        val lines: List<Line>?,
        val totalLines: Int?,
        val lang: String?
    ) : ToolView {
        data class Line(val number: Int, val text: String)
    }

    data class Grep(
        val pattern: String,
        val path: String?,
        val include: String?,
        val files: List<FileMatches>?,
        val total: Int?,
        val truncated: Boolean
    ) : ToolView {
        data class FileMatches(val path: String, val matches: List<Match>)
        data class Match(val lineNumber: Int, val line: String)
    }

    data class Glob(
        val pattern: String,
        val path: String?,
        val paths: List<String>?,
        val total: Int?,
        val truncated: Boolean
    ) : ToolView

    data class WebCall(
        val action: String?,
        val url: String?,
        val query: String?
    ) : ToolView

    data class Generic(val summary: String?) : ToolView

    companion object {
        /** Builds the richest view the item's own data supports. */
        fun of(tool: ToolItem): ToolView {
            val args = tool.arguments.jsonObjectOrNull() ?: JSONObject()
            val meta = tool.metaJson.jsonObjectOrNull()
            return when (tool.name) {
                "bash", "pwsh", "terminal" -> shell(tool, args)
                "write", "edit", "str_replace_editor" -> diff(tool, args, meta)
                "read", "read_image" -> read(tool, args, meta)
                "grep" -> grep(args, meta)
                "glob" -> glob(args, meta)
                "web" -> WebCall(
                    action = args.optString("action").takeIf { it.isNotBlank() },
                    url = args.optString("url").takeIf { it.isNotBlank() },
                    query = args.optString("query").takeIf { it.isNotBlank() }
                )
                else -> Generic(args.firstStringValue())
            }
        }

        private fun shell(tool: ToolItem, args: JSONObject): ToolView {
            val command = args.optString("command")
            if (command.isBlank()) return Generic(args.firstStringValue())
            val oneShot = args.has("description")
            var output: String? = null
            var exitCode: Int? = null
            var signal: String? = null
            if (!tool.running && !tool.isError && oneShot) {
                val parsed = parseExit(tool.resultText)
                output = parsed.first
                exitCode = parsed.second
                signal = parsed.third
            }
            return Shell(
                command = command,
                description = args.optString("description").takeIf { it.isNotBlank() },
                workdir = args.optString("workdir").takeIf { it.isNotBlank() },
                oneShot = oneShot,
                exitCode = exitCode,
                signal = signal,
                output = output
            )
        }

        private fun diff(tool: ToolItem, args: JSONObject, meta: JSONObject?): ToolView {
            if (!tool.running && !tool.isError) {
                val applied = meta?.optJSONArray("diffs")?.let { parseHunks(it) }
                if (!applied.isNullOrEmpty()) return Diff(applied, applied = true)
            }
            val intended = intendedHunk(tool.name, args) ?: return Generic(args.firstStringValue())
            return Diff(listOf(intended), applied = false)
        }

        private fun intendedHunk(name: String, args: JSONObject): Diff.Hunk? {
            if (name == "str_replace_editor") {
                val path = args.optString("path")
                if (path.isBlank()) return null
                return when (args.optString("command")) {
                    "create" -> Diff.Hunk(path, null, args.optString("file_text"))
                    "str_replace" -> Diff.Hunk(
                        path,
                        args.optString("old_str").takeIf { it.isNotEmpty() },
                        args.optString("new_str")
                    )
                    else -> null
                }
            }
            val path = args.optString("file_path")
            if (path.isBlank()) return null
            return when (name) {
                "write" -> {
                    val content = args.optString("content")
                    if (!args.has("content")) null else Diff.Hunk(path, null, content)
                }
                "edit" -> {
                    if (!args.has("old_string") || !args.has("new_string")) return null
                    Diff.Hunk(
                        path,
                        args.optString("old_string").takeIf { it.isNotEmpty() },
                        args.optString("new_string")
                    )
                }
                else -> null
            }
        }

        private fun parseHunks(array: JSONArray): List<Diff.Hunk> {
            val hunks = mutableListOf<Diff.Hunk>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: return emptyList()
                val path = item.optString("path")
                val newText = item.opt("newText")
                val oldText = item.opt("oldText")
                if (path.isBlank() || newText !is String) return emptyList()
                if (oldText != null && oldText !is String) return emptyList()
                hunks += Diff.Hunk(path, oldText as? String, newText)
            }
            return hunks
        }

        private fun read(tool: ToolItem, args: JSONObject, meta: JSONObject?): ToolView {
            val path = args.optString("file_path").ifBlank { meta?.optString("path").orEmpty() }
            if (path.isBlank()) return Generic(args.firstStringValue())
            val lines = meta?.optJSONArray("lines")?.let { array ->
                val parsed = mutableListOf<ReadFile.Line>()
                for (index in 0 until array.length()) {
                    val line = array.optJSONObject(index) ?: continue
                    val number = line.optInt("number", -1)
                    val text = line.opt("text")
                    if (number < 1 || text !is String) continue
                    parsed += ReadFile.Line(number, text)
                }
                parsed.takeIf { it.isNotEmpty() }
            }
            return ReadFile(
                path = path,
                offset = args.optInt("offset", -1).takeIf { it >= 1 }
                    ?: (meta?.optInt("offset", -1) ?: -1).takeIf { it >= 1 },
                lines = lines,
                totalLines = meta?.optInt("totalLines", -1)?.takeIf { it >= 0 },
                lang = meta?.optString("lang")?.takeIf { it.isNotBlank() }
            )
        }

        private fun grep(args: JSONObject, meta: JSONObject?): ToolView {
            val pattern = args.optString("pattern")
            if (pattern.isBlank()) return Generic(args.firstStringValue())
            val shapeOk = meta?.optString("shape") == "matches"
            val files = if (shapeOk) meta?.optJSONArray("files")?.let { array ->
                val parsed = mutableListOf<Grep.FileMatches>()
                for (index in 0 until array.length()) {
                    val file = array.optJSONObject(index) ?: continue
                    val path = file.optString("path")
                    if (path.isBlank()) continue
                    val matches = mutableListOf<Grep.Match>()
                    val matchArray = file.optJSONArray("matches")
                    if (matchArray != null) {
                        for (matchIndex in 0 until matchArray.length()) {
                            val match = matchArray.optJSONObject(matchIndex) ?: continue
                            val lineNumber = match.optInt("lineNumber", -1)
                            val line = match.opt("line")
                            if (lineNumber >= 1 && line is String) {
                                matches += Grep.Match(lineNumber, line)
                            }
                        }
                    }
                    parsed += Grep.FileMatches(path, matches)
                }
                parsed
            } else {
                null
            }
            return Grep(
                pattern = pattern,
                path = args.optString("path").takeIf { it.isNotBlank() },
                include = args.optString("include").takeIf { it.isNotBlank() },
                files = files,
                total = meta?.optInt("total", -1)?.takeIf { it >= 0 },
                truncated = meta?.optBoolean("truncated", false) ?: false
            )
        }

        private fun glob(args: JSONObject, meta: JSONObject?): ToolView {
            val pattern = args.optString("pattern")
            if (pattern.isBlank()) return Generic(args.firstStringValue())
            val shapeOk = meta?.optString("shape") == "paths"
            val paths = if (shapeOk) meta?.optJSONArray("paths")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it) }
            } else {
                null
            }
            return Glob(
                pattern = pattern,
                path = args.optString("path").takeIf { it.isNotBlank() },
                paths = paths,
                total = meta?.optInt("total", -1)?.takeIf { it >= 0 },
                truncated = meta?.optBoolean("truncated", false) ?: false
            )
        }

        private val EXIT = Regex("\n\\[exit code: (\\d+)]$")
        private val SIGNAL = Regex("\n\\[killed by signal: ([^\\]\\n]+)]$")

        /** Output, exit code, and kill signal from dsh's shell result trailer. */
        private fun parseExit(text: String): Triple<String, Int?, String?> {
            SIGNAL.find(text)?.let { match ->
                return Triple(text.substring(0, match.range.first), null, match.groupValues[1])
            }
            EXIT.find(text)?.let { match ->
                return Triple(
                    text.substring(0, match.range.first),
                    match.groupValues[1].toIntOrNull(),
                    null
                )
            }
            return Triple(text, 0, null)
        }

        private fun String?.jsonObjectOrNull(): JSONObject? {
            if (this.isNullOrBlank()) return null
            return runCatching { JSONObject(this) }.getOrNull()
        }

        private fun JSONObject.firstStringValue(): String? {
            val keys = keys()
            while (keys.hasNext()) {
                val value = opt(keys.next())
                if (value is String && value.isNotBlank()) return value
            }
            return null
        }
    }
}
