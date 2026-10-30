package com.kingkang.app.agent

/**
 * dsh's browser-safe `@path` token grammar, ported so the native composer cannot drift
 * from the web client. The rules live in dsh's own `file-reference/grammar` module.
 */
internal data class ActiveAtToken(
    /** Absolute index of the `@` that starts the token. */
    val start: Int,
    /** Cursor position where the token ends (the selection end when it was read). */
    val end: Int,
    /** Path text after `@` or `@"`. */
    val query: String,
    /** Whether the user opened a quoted path. */
    val quoted: Boolean
)

private val QUOTED_TOKEN = Regex("(?:^|\\s)(@\"([^\"]*))$")
private val PLAIN_TOKEN = Regex("(?:^|\\s)(@(\\S*))$")

/**
 * Extracts an `@path` or `@"path with spaces` token ending at [cursor]. The `@` must begin
 * a word, so an email address or a mid-word `@` never opens the picker.
 *
 * The grammar is line-scoped, matching dsh-web's editor; the returned offsets are
 * absolute so the caller can replace the token inside the full draft.
 */
internal fun activeAtToken(text: String, cursor: Int): ActiveAtToken? {
    val end = cursor.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (end - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    if (end < lineStart) return null
    val before = text.substring(lineStart, end)
    val quoted = QUOTED_TOKEN.find(before)
    if (quoted != null) {
        val group = quoted.groups[1] ?: return null
        val query = quoted.groups[2]?.value ?: return null
        return ActiveAtToken(
            start = lineStart + group.range.first,
            end = end,
            query = query,
            quoted = true
        )
    }
    val plain = PLAIN_TOKEN.find(before) ?: return null
    val group = plain.groups[1] ?: return null
    return ActiveAtToken(
        start = lineStart + group.range.first,
        end = end,
        query = plain.groups[2]?.value ?: "",
        quoted = false
    )
}

/**
 * Formats one selected path as prompt text. Whitespace uses the quoted `@"path"` spelling;
 * a quoted directory keeps the quote open after its trailing slash so completion can
 * descend another level. Returns null for a path the grammar cannot represent safely.
 */
internal fun formatFileMention(
    path: String,
    directory: Boolean,
    preserveQuote: Boolean
): String? {
    val formatted = if (directory) "$path/" else path
    if (formatted.any { it.code in 0..31 || it.code in 127..159 || it == '"' }) return null
    val quoted = preserveQuote || formatted.any { it.isWhitespace() }
    return when {
        !quoted -> "@$formatted"
        directory -> "@\"$formatted"
        else -> "@\"$formatted\""
    }
}
