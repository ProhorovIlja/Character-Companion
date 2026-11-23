package dev.caicompanion.app

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Restrict URLs handed to Android's system DownloadManager. Its redirect chain cannot
 * be inspected by our WebView and it persists the source URL outside this process.
 * Query-bearing links may contain signed credentials and are always browser-only.
 */
object DownloadPolicy {
    enum class Kind { SAFE_SYSTEM, BROWSER_ONLY, REJECT }

    fun classify(raw: String?): Kind {
        if (raw.isNullOrBlank() || raw.length > 8192) return Kind.REJECT
        val uri = try { URI(raw) } catch (_: Exception) { return Kind.REJECT }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() ||
            uri.userInfo != null || (uri.port != -1 && uri.port != 443) ||
            uri.rawPath.orEmpty().any { it.isISOControl() || it == '\\' }) return Kind.REJECT
        if (UrlPolicy.isIdentityUrl(raw)) return Kind.BROWSER_ONLY
        var decodedPath = uri.rawPath.orEmpty()
        repeat(6) {
            decodedPath = try { URLDecoder.decode(decodedPath.replace("+", "%2B"), "UTF-8") }
                catch (_: IllegalArgumentException) { return Kind.BROWSER_ONLY }
        }
        val sensitiveSegments = setOf("auth", "oauth", "token", "login", "session", "callback", "password", "account")
        if (uri.rawQuery != null || uri.rawFragment != null ||
            Regex("%[0-9a-fA-F]{2}").containsMatchIn(decodedPath) ||
            decodedPath.any { it.isISOControl() || it == '\\' } ||
            decodedPath.split('/').any { it.lowercase(Locale.ROOT) in sensitiveSegments }
        ) return Kind.BROWSER_ONLY
        // Untrusted remote hosts are routed to the browser, where the user retains
        // their browser protections and can review any redirects or prompts.
        // Only clean first-party URLs are eligible for the system downloader.
        return if (HostBlocker.isServiceHost(uri.host)) Kind.SAFE_SYSTEM else Kind.BROWSER_ONLY
    }
}
