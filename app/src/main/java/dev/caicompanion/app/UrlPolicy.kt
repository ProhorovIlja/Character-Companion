package dev.caicompanion.app

import java.net.URI
import java.util.Locale
import java.net.URLDecoder

/** HTTPS-only first party navigation with explicit separation of bookmarkable URLs. */
object UrlPolicy {
    private val identityHosts = listOf("accounts.google.com", "appleid.apple.com", "id.apple.com")
    // Only stable, content-oriented query parameters may be persisted with a bookmark.
    // Login and account routes must never become shareable, bookmarkable or resumable.
    private val sensitivePathParts = setOf("oauth", "authorize", "auth", "login", "signin",
        "sign-in", "callback", "verify", "token", "session", "logout", "reset-password",
        "account", "settings", "api", "password", "oauth2", "log-in", "signup",
        "sign-up", "register", "registration", "verification", "email-verification",
        "forgot-password", "recover-password", "reset", "magic-link", "sso")
    private val identitySubdomains = setOf("auth", "oauth", "oauth2", "account", "accounts",
        "login", "signin", "sso", "identity")
    private val bookmarkQueryKeys = setOf("char", "id")
    private val bookmarkValue = Regex("^[A-Za-z0-9_-]{1,128}$")

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

    /** Only the same first-party HTTPS origin may complete an in-flight media permission. */
    fun sameTrustedMediaOrigin(requestUrl: String?, visibleUrl: String?): Boolean {
        val request = parsedHttps(requestUrl) ?: return false
        val visible = parsedHttps(visibleUrl) ?: return false
        return HostBlocker.isServiceHost(request.host) &&
            HostBlocker.isServiceHost(visible.host) &&
            request.host.equals(visible.host, ignoreCase = true) &&
            (if (request.port == -1) 443 else request.port) ==
            (if (visible.port == -1) 443 else visible.port)
    }

    /** Do not persist OAuth redirects, fragments, account-provider URLs or unknown query fields. */
    fun sanitizeBookmark(url: String?): String? {
        val uri = parsedHttps(url) ?: return null
        if (!HostBlocker.isServiceHost(uri.host) || (url?.length ?: 0) > 4096) return null
        // Service-owned identity subdomains can contain credentials in otherwise ordinary paths.
        val host = uri.host.lowercase(Locale.ROOT)
        val serviceRoot = if (HostBlocker.isDomain(host, "character.ai")) "character.ai" else "c.ai"
        if (host != serviceRoot && host.removeSuffix(".$serviceRoot").split('.').any { it in identitySubdomains }) return null
        var decodedPath = uri.path.orEmpty()
        // Decode layered escapes: %252Fauth%252Fcallback must never bypass path checks.
        // URLDecoder treats '+' as a space; preserve literal '+' for path inspection.
        repeat(6) {
            decodedPath = try { URLDecoder.decode(decodedPath.replace("+", "%2B"), "UTF-8") }
                catch (_: IllegalArgumentException) { return null }
        }
        // Deeply nested percent-escapes can hide paths even after repeated decoding.
        if (Regex("%[0-9a-fA-F]{2}").containsMatchIn(decodedPath)) return null
        if (decodedPath.length > 1500 || decodedPath.any { it.isISOControl() || it == '\\' || it == ';' || Character.getType(it) == Character.FORMAT.toInt() } ||
            decodedPath.split('/').any {
                val segment = it.lowercase(Locale.ROOT)
                segment in sensitivePathParts || segment == "." || segment == ".."
            }) return null
        val query = uri.rawQuery?.takeIf { raw ->
            raw.length <= 1024 && raw.split('&').all { item ->
                val key = item.substringBefore('=')
                val value = item.substringAfter('=', "")
                key in bookmarkQueryKeys && bookmarkValue.matches(value)
            }
        }
        val port = if (uri.port == 443) ":443" else ""
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        return "https://$host$port$path" + if (query.isNullOrEmpty()) "" else "?$query"
    }

    fun safeBookmark(url: String?): Boolean = sanitizeBookmark(url) != null

    /** Share/copy only a sanitized first-party content link, never OAuth or session URLs. */
    fun safeShareUrl(url: String?): String? = sanitizeBookmark(url)
}
