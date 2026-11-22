package dev.caicompanion.app

import java.net.URI

/** Pure and testable navigation policy. HTTPS-only, no javascript:, file: or intent: loads. */
object UrlPolicy {
    private val identityHosts = listOf("accounts.google.com", "appleid.apple.com", "id.apple.com")

    fun isHttps(url: String?): Boolean {
        if (url == null) return false
        return try {
            val uri = URI(url)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        } catch (_: Exception) { false }
    }

    fun isInternal(url: String?): Boolean {
        if (!isHttps(url)) return false
        val host = HostBlocker.hostFromUrl(url) ?: return false
        return HostBlocker.isServiceHost(host) || identityHosts.any { HostBlocker.isDomain(host, it) }
    }

    fun isTrustedMediaOrigin(url: String?): Boolean =
        isHttps(url) && HostBlocker.hostFromUrl(url)?.let(HostBlocker::isServiceHost) == true

    fun safeBookmark(url: String?): Boolean = isHttps(url) && isInternal(url)
}
