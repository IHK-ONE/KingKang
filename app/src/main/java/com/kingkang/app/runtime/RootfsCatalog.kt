package com.kingkang.app.runtime

import android.os.Build

data class RootfsSpec(
    val label: String,
    val url: String,
    val sha256: String,
    val approxBytes: Long
) {
    val fileName: String get() = url.substringAfterLast('/')
}

/**
 * Pinned Kali NetHunter "full" rootless rootfs releases.
 *
 * The URL is the official NetHunter "current" rootfs directory; the checksum is the one
 * published in the same directory's SHA256SUMS. When Kali rotates the image the pinned
 * values here go stale, which is exactly what the update check reports: it compares the
 * checksum of the installed image with the checksum currently published upstream.
 */
object RootfsCatalog {
    private const val BASE = "https://kali.download/nethunter-images/current/rootfs/"

    val checksumsUrl: String get() = BASE + "SHA256SUMS"

    fun forAbi(abi: String?): RootfsSpec? = when (abi) {
        "arm64-v8a" -> RootfsSpec(
            label = "Kali NetHunter full · arm64",
            url = BASE + "kali-nethunter-rootfs-full-arm64.tar.xz",
            sha256 = "cc677eac68d6699873b1d617dda2957506665e71f9910923584248c80e1ec007",
            approxBytes = 1_837_393_488L
        )

        "x86_64" -> RootfsSpec(
            label = "Kali NetHunter full · amd64",
            url = BASE + "kali-nethunter-rootfs-full-amd64.tar.xz",
            sha256 = "9de2e5d4c69880edc8571314cce196e69161d7a1217850a59b4e856c920e4882",
            approxBytes = 2_356_544_572L
        )

        else -> null
    }

    fun currentAbiName(): String? = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "arm64-v8a"
        "x86_64" -> "x86_64"
        else -> null
    }

    /** Picks the checksum for [fileName] out of an official SHA256SUMS document. */
    fun parseChecksum(sumFile: String, fileName: String): String? =
        sumFile.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.endsWith("  $fileName") || it.endsWith(" *$fileName") }
            ?.substringBefore(' ')
            ?.takeIf { it.length == 64 && it.all { c -> c.isDigit() || c in 'a'..'f' } }
}
