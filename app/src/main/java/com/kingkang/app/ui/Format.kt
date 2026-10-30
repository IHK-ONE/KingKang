package com.kingkang.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Compact download-size label shared by the setup, settings and home screens. */
internal fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "未知大小"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
    }
}

/**
 * Token counts the way dsh-web abbreviates them. A missing bucket stays an em dash:
 * showing 0 would claim the provider reported a zero, which is a different fact.
 */
internal fun formatTokens(tokens: Long?): String {
    if (tokens == null) return "—"
    return when {
        tokens < 1_000L -> tokens.toString()
        tokens < 1_000_000L -> trimZero(String.format(Locale.US, "%.1f", tokens / 1_000.0)) + "k"
        else -> trimZero(String.format(Locale.US, "%.1f", tokens / 1_000_000.0)) + "M"
    }
}

internal fun formatPercent(fraction: Float?): String {
    if (fraction == null) return "—"
    val percent = fraction * 100f
    return if (percent < 10f) String.format(Locale.US, "%.1f%%", percent)
    else String.format(Locale.US, "%.0f%%", percent)
}

/** Durations in the compact form used by the stats panel and subagent rows. */
internal fun formatDuration(millis: Long?): String {
    if (millis == null || millis < 0L) return "—"
    return when {
        millis < 1_000L -> "${millis}ms"
        millis < 60_000L -> trimZero(String.format(Locale.US, "%.1f", millis / 1_000.0)) + "s"
        millis < 3_600_000L -> {
            val seconds = millis / 1_000L
            String.format(Locale.US, "%dm %02ds", seconds / 60L, seconds % 60L)
        }
        else -> {
            val minutes = millis / 60_000L
            String.format(Locale.US, "%dh %02dm", minutes / 60L, minutes % 60L)
        }
    }
}

/**
 * Byte counts for job output, where a few hundred bytes must not round down to "0 MB"
 * the way [formatBytes] does for downloads.
 */
internal fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

/** Wall-clock time of day, used by job rows for their start time. */
internal fun formatClock(millis: Long): String {
    if (millis <= 0L) return "—"
    return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(millis))
}

/** Short time-of-day for dense rows such as the turn outline. */
internal fun formatTimeOfDay(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))
}

/**
 * A scheduled instant in the device's zone. The year is only shown when it differs from
 * the current one, so the common "tomorrow 09:00" row stays scannable.
 */
internal fun formatScheduledAt(millis: Long?): String {
    if (millis == null || millis <= 0L) return "—"
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val pattern = if (now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)) {
        "MM-dd HH:mm"
    } else {
        "yyyy-MM-dd HH:mm"
    }
    return SimpleDateFormat(pattern, Locale.US).format(Date(millis))
}

private fun trimZero(value: String): String =
    if (value.endsWith(".0")) value.dropLast(2) else value
