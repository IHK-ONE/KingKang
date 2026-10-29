package com.kingkang.app.ui

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
