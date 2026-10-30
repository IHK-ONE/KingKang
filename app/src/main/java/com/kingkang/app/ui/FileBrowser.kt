package com.kingkang.app.ui

import com.kingkang.app.agent.AgentFileEntry
import java.nio.charset.Charset
import java.util.Locale

/** How the file sidebar orders one directory. Folders always stay above files. */
internal enum class FileSort(val label: String) {
    Name("名称"),
    Size("大小"),
    Time("时间")
}

/** One hop of the sidebar's breadcrumb, from the workspace root down to the open folder. */
internal data class FileCrumb(val label: String, val path: String)

internal fun fileCrumbs(root: String, path: String): List<FileCrumb> {
    val cleanRoot = root.trimEnd('/').ifBlank { "/" }
    val crumbs = mutableListOf(
        FileCrumb(cleanRoot.substringAfterLast('/').ifBlank { "/" }, cleanRoot)
    )
    if (path == cleanRoot) return crumbs
    val relative = path.removePrefix(if (cleanRoot == "/") "" else cleanRoot)
    var current = cleanRoot
    relative.split('/').filter { it.isNotBlank() }.forEach { segment ->
        current = if (current == "/") "/$segment" else "$current/$segment"
        crumbs += FileCrumb(segment, current)
    }
    return crumbs
}

/**
 * Filters and orders one listing. Hidden files are dot-names only, matching what a Kali
 * shell considers hidden; a chosen sort applies inside the folder and file groups rather
 * than mixing the two.
 */
internal fun sortFileEntries(
    entries: List<AgentFileEntry>,
    sort: FileSort,
    ascending: Boolean,
    showHidden: Boolean,
    query: String
): List<AgentFileEntry> {
    val needle = query.trim()
    val visible = entries.filter { entry ->
        (showHidden || !entry.name.startsWith(".")) &&
            (needle.isEmpty() || entry.name.contains(needle, ignoreCase = true))
    }
    val nameOrder = String.CASE_INSENSITIVE_ORDER
    val inside = when (sort) {
        FileSort.Name -> if (ascending) {
            compareBy(nameOrder) { it.name }
        } else {
            compareByDescending(nameOrder) { it.name }
        }
        FileSort.Size -> if (ascending) {
            compareBy<AgentFileEntry> { it.size }.thenBy(nameOrder) { it.name }
        } else {
            compareByDescending<AgentFileEntry> { it.size }.thenBy(nameOrder) { it.name }
        }
        FileSort.Time -> if (ascending) {
            compareBy<AgentFileEntry> { it.modified }.thenBy(nameOrder) { it.name }
        } else {
            compareByDescending<AgentFileEntry> { it.modified }.thenBy(nameOrder) { it.name }
        }
    }
    return visible.sortedWith(
        compareBy<AgentFileEntry> { !it.directory }.thenComparing(inside)
    )
}

/** A compact, human size for a row's trailing metadata. */
// formatFileSize lives in HomeScreen.kt because attachment chips use it too.

/** How the viewer should present one file. */
internal enum class FileKind {
    Text,
    Markdown,
    Image,
    Pdf,
    Binary
}

private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
private val markdownExtensions = setOf("md", "markdown")
private val textExtensions = setOf(
    "txt", "text", "log", "json", "jsonl", "xml", "yaml", "yml", "toml", "ini",
    "conf", "cfg", "sh", "bash", "zsh", "py", "js", "mjs", "cjs", "ts", "tsx", "jsx",
    "kt", "kts", "java", "c", "h", "cc", "cpp", "hpp", "cs", "go", "rs", "rb", "php",
    "pl", "lua", "sql", "html", "htm", "css", "scss", "less", "csv", "tsv", "properties",
    "env", "diff", "patch", "dockerfile", "gradle", "gitignore", "service", "desktop"
)

internal fun fileExtension(name: String): String =
    name.substringAfterLast('.', "").lowercase(Locale.US)

/**
 * Chooses a viewer. Extensions decide the obvious cases; everything else is text unless
 * the bytes contain a NUL, which is what makes a mislabeled binary unreadable rather
 * than merely ugly.
 */
internal fun fileKindOf(name: String, bytes: ByteArray?): FileKind {
    val extension = fileExtension(name)
    return when (extension) {
        in imageExtensions -> FileKind.Image
        "pdf" -> FileKind.Pdf
        in markdownExtensions -> FileKind.Markdown
        in textExtensions -> FileKind.Text
        else -> if (bytes != null && looksBinary(bytes)) FileKind.Binary else FileKind.Text
    }
}

/** NUL bytes are the cheap, reliable signal that a file is not meant to be read as text. */
internal fun looksBinary(bytes: ByteArray): Boolean {
    val window = minOf(bytes.size, 8192)
    for (index in 0 until window) {
        if (bytes[index] == 0.toByte()) return true
    }
    return false
}

/** A decoded preview plus the charset it actually used. */
internal data class DecodedText(val text: String, val charset: String, val garbled: Boolean)

/**
 * Decodes a text preview. With no explicit charset it tries UTF-8, then GB18030 — the
 * usual encoding of Chinese logs written by older Kali tools. Replacement characters
 * confined to the very end are treated as a cut multi-byte character, not as a wrong
 * charset, because preview bytes stop at a cap rather than a character boundary.
 */
internal fun decodePreview(bytes: ByteArray, charsetName: String? = null): DecodedText {
    if (charsetName != null) {
        val charset = runCatching { Charset.forName(charsetName) }.getOrNull()
            ?: return DecodedText(String(bytes, Charsets.UTF_8), "UTF-8", true)
        return decodedWith(bytes, charset, charset.name())
    }
    val utf8 = decodedWith(bytes, Charsets.UTF_8, "UTF-8")
    if (utf8.text.indexOf('�') < 0) return utf8
    if (onlyTrailingReplacement(utf8.text)) return utf8
    val gb = runCatching { decodedWith(bytes, Charset.forName("GB18030"), "GB18030") }.getOrNull()
    return if (gb != null && replacementCount(gb.text) < replacementCount(utf8.text)) gb else utf8
}

private fun decodedWith(bytes: ByteArray, charset: Charset, label: String): DecodedText {
    val text = String(bytes, charset)
    return DecodedText(text, label, replacementCount(text) > 0 && !onlyTrailingReplacement(text))
}

private fun replacementCount(text: String): Int = text.count { it == '�' }

private fun onlyTrailingReplacement(text: String): Boolean {
    var index = text.length - 1
    while (index >= 0 && text[index] == '�') index--
    val trailing = text.length - 1 - index
    return trailing in 1..3 && text.substring(0, index + 1).indexOf('�') < 0
}

/** One top-level Markdown piece the file viewer draws on its own. */
internal sealed interface FileMarkdownBlock {
    data class Heading(val level: Int, val text: String) : FileMarkdownBlock
    data class Paragraph(val text: String) : FileMarkdownBlock
    data class Bullet(val marker: String, val text: String) : FileMarkdownBlock
    data class Quote(val text: String) : FileMarkdownBlock
    data class Code(val language: String, val text: String) : FileMarkdownBlock
    data object Rule : FileMarkdownBlock
}

/**
 * A deliberately small Markdown reader: headings, lists, quotes, fenced code, rules and
 * paragraphs. It is a preview, not a renderer for arbitrary HTML, and it never executes
 * or fetches anything a document asks for.
 */
internal fun fileMarkdownBlocks(source: String, limit: Int = 1_500): List<FileMarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<FileMarkdownBlock>()
    val paragraph = StringBuilder()
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += FileMarkdownBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }
    var index = 0
    while (index < lines.size && blocks.size < limit) {
        val line = lines[index]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val language = trimmed.removePrefix("```").trim()
                val body = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[index])
                    index++
                }
                blocks += FileMarkdownBlock.Code(language, body.toString())
            }
            trimmed.isEmpty() -> flushParagraph()
            trimmed.matches(Regex("^(#{1,6})\\s+.*")) -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length
                blocks += FileMarkdownBlock.Heading(level, trimmed.drop(level).trim())
            }
            trimmed.matches(Regex("^([-*_])\\1{2,}$")) -> {
                flushParagraph()
                blocks += FileMarkdownBlock.Rule
            }
            trimmed.startsWith(">") -> {
                flushParagraph()
                blocks += FileMarkdownBlock.Quote(trimmed.removePrefix(">").trim())
            }
            Regex("^([-*+])\\s+").containsMatchIn(trimmed) -> {
                flushParagraph()
                blocks += FileMarkdownBlock.Bullet("•", trimmed.drop(2).trim())
            }
            Regex("^\\d+[.)]\\s+").containsMatchIn(trimmed) -> {
                flushParagraph()
                val marker = trimmed.takeWhile { !it.isWhitespace() }
                blocks += FileMarkdownBlock.Bullet(
                    marker,
                    trimmed.substringAfter(' ').trim()
                )
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
        index++
    }
    flushParagraph()
    return blocks
}
