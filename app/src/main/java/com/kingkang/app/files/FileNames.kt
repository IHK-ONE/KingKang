package com.kingkang.app.files

/**
 * Safe display names for files the app hands to other apps or writes to disk. Everything
 * here is pure Kotlin so the rules can be unit-tested without an Android runtime.
 */
object FileNames {

    /**
     * Turns an untrusted display name into a single safe file name. Path separators,
     * control characters, and traversal segments are removed; the extension survives.
     */
    fun sanitize(name: String, fallback: String = "download"): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = buildString {
            for (character in base) {
                when {
                    character == '\u0000' -> Unit
                    character.code < 0x20 || character.code == 0x7f -> Unit
                    character in "/\\:*?\"<>|" -> append('_')
                    else -> append(character)
                }
            }
        }.trim().trim('.').take(120)
        val safe = cleaned.ifBlank { fallback }
        return if (safe == "." || safe == "..") fallback else safe
    }

    /** Keeps a name unique inside [directory] without overwriting an existing file. */
    fun unique(directory: java.io.File, name: String): java.io.File {
        var candidate = java.io.File(directory, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val suffix = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = java.io.File(directory, "$stem-$index$suffix")
            index++
        }
        return candidate
    }
}
