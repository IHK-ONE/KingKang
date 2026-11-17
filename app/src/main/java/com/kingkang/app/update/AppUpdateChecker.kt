package com.kingkang.app.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val version: String,
    val url: String,
    val notes: String?
)

/**
 * Reads the newest stable GitHub release. The app is sideloaded, so this only offers a
 * verified release page; installing stays an explicit user action in the browser.
 */
object AppUpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/IHK-ONE/KingKang/releases/latest"

    fun latest(): AppRelease {
        val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "KingKang-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("GitHub 返回 HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim().removePrefix("v")
            val url = json.optString("html_url").trim()
            if (tag.isBlank() || !url.startsWith("https://github.com/IHK-ONE/KingKang/")) {
                throw IllegalStateException("发布信息不完整")
            }
            return AppRelease(
                version = tag,
                url = url,
                notes = json.optString("body").takeIf { it.isNotBlank() }
            )
        } finally {
            connection.disconnect()
        }
    }

    /** True when [latest] is a newer stable version than [current]. */
    fun isNewer(latest: String, current: String): Boolean {
        fun core(value: String): List<Int> =
            value.trim().removePrefix("v").substringBefore('-').substringBefore('+')
                .split('.').map { it.toIntOrNull() ?: 0 }
        val a = core(latest)
        val b = core(current)
        for (index in 0 until maxOf(a.size, b.size)) {
            val difference = (a.getOrNull(index) ?: 0) - (b.getOrNull(index) ?: 0)
            if (difference != 0) return difference > 0
        }
        // Same core: a stable release outranks a locally installed prerelease.
        val latestPre = latest.contains('-')
        val currentPre = current.contains('-')
        return !latestPre && currentPre
    }
}
