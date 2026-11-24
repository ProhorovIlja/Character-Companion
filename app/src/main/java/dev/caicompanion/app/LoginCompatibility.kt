package dev.caicompanion.app

import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

/** Ephemeral first-party login exception. Never persists URLs, cookies, or credentials. */
object LoginCompatibilityPolicy {
    private val localeSegment = Regex("[a-z]{2}(?:-[a-z]{2})?")
    private val loginSegments = setOf(
        "login", "log-in", "signin", "sign-in", "signup", "sign-up",
        "register", "registration", "auth", "authorize", "oauth", "callback",
        "verify", "verification", "email-verification"
    )

    private fun firstPartyUri(url: String?): URI? {
        if (url.isNullOrBlank() || url.length > 8192) return null
        return try {
            val parsed = URI(url)
            if (!parsed.scheme.equals("https", ignoreCase = true) ||
                parsed.host.isNullOrBlank() || parsed.userInfo != null ||
                (parsed.port != -1 && parsed.port != 443) ||
                !HostBlocker.isServiceHost(parsed.host)) null else parsed
        } catch (_: Exception) { null }
    }

    /** Strict path matching. Host lookalikes and query text cannot disable the shield. */
    fun isLoginPage(url: String?): Boolean {
        val parsed = firstPartyUri(url) ?: return false
        val route = parsed.rawPath.orEmpty().lowercase(Locale.ROOT)
        // Only a leading auth route (optionally locale-prefixed) is a login page.
        // A chat path containing a segment named login must not pause protection.
        val parts = route.split('/').filter { it.isNotEmpty() }
        val first = parts.firstOrNull() ?: return false
        val auth = if (localeSegment.matches(first)) parts.getOrNull(1) else first
        return auth in loginSegments
    }

    /** Store only an in-memory path fingerprint, never a raw OAuth or chat URL. */
    fun pageKey(url: String?): String? = firstPartyUri(url)?.let {
        val key = it.host.lowercase(Locale.ROOT) + ":" + it.rawPath.orEmpty()
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8)))
    }
}

/** UI thread updates; worker-thread WebView interceptor reads only volatile bypass. */
class LoginCompatibilitySession {
    @Volatile var bypass: Boolean = false
        private set
    @Volatile var manual: Boolean = false
        private set
    private var manualPage: String? = null
    private var visible = false
    private var automatic = false

    /** Hidden, paused, or destroyed renderers may not retain or acquire bypass. */
    @Synchronized fun setVisible(value: Boolean) {
        visible = value
        if (!value) reset()
    }

    @Synchronized fun observePage(url: String?): Boolean {
        if (!visible) return false
        val page = if (manual) LoginCompatibilityPolicy.pageKey(url) else null
        if (manual && (page == null || page != manualPage)) {
            manual = false
            manualPage = null
        }
        automatic = LoginCompatibilityPolicy.isLoginPage(url)
        bypass = automatic || manual
        return bypass
    }

    /** User fallback for SPA login dialogs without a URL change. */
    @Synchronized fun toggleManual(url: String?): Boolean {
        if (!visible) return false
        if (manual) {
            manual = false
            manualPage = null
        } else {
            manualPage = LoginCompatibilityPolicy.pageKey(url)
            manual = manualPage != null
        }
        automatic = LoginCompatibilityPolicy.isLoginPage(url)
        bypass = automatic || manual
        return manual
    }

    @Synchronized fun reset() {
        automatic = false
        manual = false
        manualPage = null
        bypass = false
    }
}
