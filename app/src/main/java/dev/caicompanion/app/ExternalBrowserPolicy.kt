package dev.caicompanion.app

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Redacts authentication-bearing handoffs into the system browser.
 * The system browser does not share Gecko's session; forwarding a callback URL
 * would expose transient credentials without completing sign-in.
 * No cookies, account names, or tokens are inspected or persisted.
 */
object ExternalBrowserPolicy {
    const val HOME = "https://character.ai/"
    private const val MAX_URL = 8192
    private val sensitiveKeys = setOf(
        "code", "state", "access_token", "id_token", "refresh_token", "token",
        "session", "session_id", "auth", "authorization", "client_secret",
        "secret", "password", "jwt", "key", "api_key", "sig", "signature",
        "redirect_uri", "callback", "continue", "ticket", "request_uri",
        "id_token_hint", "code_verifier", "sso", "oauth_token",
        "redirect", "redirect_url", "return_url", "return_to", "next", "url"
    )
    private val sensitivePaths = setOf(
        "oauth", "authorize", "authorization", "auth", "login", "signin",
        "sign-in", "callback", "verify", "token", "session", "logout",
        "reset-password", "password", "account", "api"
    )

    private fun decodeLimited(raw: String): String? {
        var value = raw
        repeat(4) {
            value = try { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }
                catch (_: Exception) { return null }
        }
        return if (Regex("%[0-9a-fA-F]{2}").containsMatchIn(value)) null else value
    }

    /** Returns null for unsafe schemes or malformed addresses, HOME for sensitive URLs. */
    fun target(raw: String?): String? {
        if (raw.isNullOrEmpty() || raw.length > MAX_URL || !UrlPolicy.isHttps(raw)) return null
        if (raw.any { it.isISOControl() || it in "\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069" }) return null
        val uri = try { URI(raw) } catch (_: Exception) { return null }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        // Some Character.AI deployments use hosted identity providers rather than
        // accounts.google.com. Never transfer their transient login URLs.
        if (UrlPolicy.isIdentityUrl(raw) || HostBlocker.isDomain(host, "auth0.com")) return HOME
        val path = decodeLimited(uri.rawPath.orEmpty()) ?: return HOME
        if (path.split('/').any { it.lowercase(Locale.ROOT) in sensitivePaths }) return HOME
        val query = uri.rawQuery.orEmpty()
        for (entry in query.split('&')) {
            if (entry.isEmpty()) continue
            val key = decodeLimited(entry.substringBefore('=')) ?: return HOME
            if (key.lowercase(Locale.ROOT) in sensitiveKeys) return HOME
        }
        // Percent-encoded callback URLs can be hidden in an otherwise harmless
        // parameter such as ?payload=https%3A%2F%2Fhost%2F%3Fcode%3Dsecret.
        // Inspect a bounded, recursively decoded query without persisting its value.
        val expanded = decodeLimited(query) ?: return HOME
        // Encoded nested URLs can hide authentication endpoints even without a
        // code/state parameter, and their final redirects cannot be inspected.
        // Do not hand third-party URL wrappers to the system browser; users can
        // open a public destination directly instead.
        if (Regex("(?i)https?://").containsMatchIn(expanded)) return HOME
        val embeddedKey = Regex("(?:^|[?&#])([a-zA-Z_][a-zA-Z0-9_-]{0,63})=")
        if (embeddedKey.findAll(expanded).any {
            it.groupValues[1].lowercase(Locale.ROOT) in sensitiveKeys
        }) return HOME
        val fragment = decodeLimited(uri.rawFragment.orEmpty()) ?: return HOME
        // OAuth implicit-grant flows can carry tokens after '#', not just '?'.
        // A structured hash can contain implicit grant credentials, even when
        // nested after a route like #/callback?access_token=... . Simple document
        // anchors (#help) are harmless and remain usable.
        if (fragment.isNotEmpty() &&
            !Regex("^[a-zA-Z0-9_-]{1,80}$").matches(fragment)) return HOME
        return raw
    }
}
