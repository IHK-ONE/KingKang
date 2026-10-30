package com.kingkang.app.agent

import java.util.Locale

/** How an attachment can be shown in place: inline image, text reader, PDF, or nothing. */
internal enum class AttachmentKind { Image, Text, Pdf, Other }

/**
 * Text formats are recognised by MIME type *and* extension: pickers often hand back
 * `application/octet-stream` for source files, and history entries may carry no type at all.
 */
private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "log", "json", "jsonl", "yaml", "yml", "xml", "html", "htm",
    "css", "js", "mjs", "cjs", "ts", "tsx", "jsx", "kt", "kts", "java", "py", "sh", "bash",
    "zsh", "fish", "rb", "go", "rs", "c", "h", "cpp", "hpp", "cc", "cs", "php", "sql",
    "toml", "ini", "conf", "cfg", "env", "csv", "tsv", "diff", "patch", "gradle",
    "properties", "plist", "tex", "lua", "pl", "swift", "dart", "scala", "r", "ps1", "bat",
    "cmd", "dockerfile", "makefile"
)

private val TEXT_MIMES = setOf(
    "application/json", "application/xml", "application/yaml", "application/x-yaml",
    "application/toml", "application/javascript", "application/x-sh",
    "application/x-ndjson", "application/sql", "application/x-httpd-php"
)

/**
 * Decides how the preview dialog renders [name]/[mimeType]. SVG is technically an image
 * but nothing on hand rasterises it, so its markup is shown as text instead.
 */
internal fun attachmentKind(mimeType: String, name: String): AttachmentKind {
    val mime = mimeType.trim().lowercase(Locale.US)
    val lowerName = name.lowercase(Locale.US)
    val extension = lowerName.substringAfterLast('.', "")
    val textual = lowerName == "dockerfile" || lowerName == "makefile" ||
        lowerName.startsWith(".env") || lowerName == ".gitignore" ||
        mime.startsWith("text/") ||
        mime in TEXT_MIMES ||
        mime.endsWith("+json") ||
        mime.endsWith("+xml") ||
        mime == "image/svg+xml" ||
        extension in TEXT_EXTENSIONS
    if (textual) return AttachmentKind.Text
    if (mime.startsWith("image/")) return AttachmentKind.Image
    if (mime == "application/pdf") return AttachmentKind.Pdf
    return AttachmentKind.Other
}
