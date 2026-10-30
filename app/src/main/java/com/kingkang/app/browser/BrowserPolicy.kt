package com.kingkang.app.browser

import java.net.URI
import java.util.Locale

/** What the in-app browser should do with one URL. */
internal enum class UrlDecision {
    /** Load it: a loopback address, the only kind allowed without asking. */
    Allow,

    /** Ask first: a remote HTTPS page. */
    Confirm,

    /** Never load: another scheme, or cleartext to a remote host. */
    Block
}

/**
 * The browser's URL rules. The app's network security config already refuses cleartext
 * outside loopback, so an external `http://` page could only fail after a confirmation;
 * blocking it up front is clearer than a blank page.
 */
internal object BrowserPolicy {

    private val allowedSchemes = setOf("http", "https")

    /**
     * Turns what the user typed into a URL, or null when it cannot name a web page.
     * A bare loopback host such as `127.0.0.1:3000` or `localhost` gets `http://`, which
     * is what a local tool expects; a bare remote host gets `https://`, since cleartext
     * to the internet is refused anyway. Strings with their own scheme are returned
     * untouched, so [decide] can refuse `file:`/`javascript:` and friends.
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (hasScheme(trimmed)) {
            val uri = parse(trimmed) ?: return null
            val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
            // Non-web schemes are returned as typed so decide() can name the refusal.
            if (scheme != "http" && scheme != "https") return trimmed
            return if (uri.host.isNullOrBlank()) null else trimmed
        }
        val candidate = if (trimmed.startsWith("//")) trimmed else "//$trimmed"
        val host = parse("https:$candidate")?.host ?: return null
        if (host.isBlank()) return null
        // Loopback tools conventionally speak HTTP; remote pages must be HTTPS.
        val scheme = if (isLoopbackHost(host)) "http" else "https"
        return "$scheme:$candidate"
    }

    fun decide(url: String): UrlDecision {
        val uri = parse(url) ?: return UrlDecision.Block
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return UrlDecision.Block
        if (scheme !in allowedSchemes) return UrlDecision.Block
        if (isLoopbackHost(uri.host)) return UrlDecision.Allow
        return if (scheme == "https") UrlDecision.Confirm else UrlDecision.Block
    }

    /** Whether [url] points at this device; used for labels and download handling. */
    fun isLoopback(url: String): Boolean = isLoopbackHost(parse(url)?.host)

    fun hostOf(url: String): String = parse(url)?.host.orEmpty()

    private fun isLoopbackHost(host: String?): Boolean {
        val value = host?.trim()?.lowercase(Locale.US) ?: return false
        return value == "localhost" ||
            value.endsWith(".localhost") ||
            value == "127.0.0.1" ||
            value == "::1" ||
            value == "[::1]"
    }

    /**
     * A colon only means a scheme when the remainder is not a port number, so
     * `localhost:3000` is read as a host. Anything with `://` is always a scheme.
     */
    private fun hasScheme(value: String): Boolean {
        if (value.contains("://")) return true
        val match = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:(.*)$").find(value) ?: return false
        val rest = match.groupValues[1]
        return rest.isNotEmpty() && !rest.first().isDigit()
    }

    private fun parse(value: String): URI? = runCatching { URI(value) }.getOrNull()
}
