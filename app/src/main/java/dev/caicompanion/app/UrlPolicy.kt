package dev.caicompanion.app

import java.net.URI
import java.util.Locale

/** HTTPS-only first party navigation with explicit separation of bookmarkable URLs. */
object UrlPolicy {
    private val identityHosts = listOf("accounts.google.com", "appleid.apple.com", "id.apple.com")
    // Only stable, content-oriented query parameters may be persisted with a bookmark.
    private val bookmarkQueryKeys = setOf("char", "id", "q", "query", "search", "page", "tab", "sort", "view", "filter")

    private fun parsedHttps(url: String?): URI? {
        if (url == null) return null
        return try {
            val uri = URI(url)
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() ||
                uri.userInfo != null || (uri.port != -1 && uri.port != 443)) null else uri
        } catch (_: Exception) { null }
    }

    fun isHttps(url: String?): Boolean = parsedHttps(url) != null

    fun isInternal(url: String?): Boolean {
        val host = parsedHttps(url)?.host ?: return false
        return HostBlocker.isServiceHost(host) || identityHosts.any { HostBlocker.isDomain(host, it) }
    }

    fun isIdentityUrl(url: String?): Boolean {
        val host = parsedHttps(url)?.host ?: return false
        return identityHosts.any { HostBlocker.isDomain(host, it) }
    }

    fun isTrustedMediaOrigin(url: String?): Boolean =
        parsedHttps(url)?.host?.let(HostBlocker::isServiceHost) == true

    /** Do not persist OAuth redirects, fragments, account-provider URLs or unknown query fields. */
    fun sanitizeBookmark(url: String?): String? {
        val uri = parsedHttps(url) ?: return null
        if (!HostBlocker.isServiceHost(uri.host)) return null
        val query = uri.rawQuery?.takeIf { raw ->
            raw.length <= 1024 && raw.split('&').all { item ->
                val key = item.substringBefore('=')
                key in bookmarkQueryKeys
            }
        }
        val host = uri.host.lowercase(Locale.ROOT)
        val port = if (uri.port == 443) ":443" else ""
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        return "https://$host$port$path" + if (query.isNullOrEmpty()) "" else "?$query"
    }

    fun safeBookmark(url: String?): Boolean = sanitizeBookmark(url) != null
}
