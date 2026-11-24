package dev.caicompanion.app

import java.net.URI
import java.util.Locale

/** Ephemeral first-party login exception. Never persists URLs, cookies, or credentials. */
object LoginCompatibilityPolicy {
    private val loginSegments = setOf(
        "login", "log-in", "signin", "sign-in", "signup", "sign-up",
        "register", "registration", "auth", "authorize", "oauth", "callback",
        "verify", "verification", "email-verification"
    )

    private fun firstPartyUri(url: String?): URI? {
        return try {
        val parsed = URI(url ?: return null)
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
        return route.split('/').any { it in loginSegments }
    }

    /** Store only an in-memory path fingerprint, never a raw OAuth or chat URL. */
    fun pageKey(url: String?): Int? = firstPartyUri(url)?.let {
        (it.host.lowercase(Locale.ROOT) + ":" + it.rawPath.orEmpty()).hashCode()
    }
}

/** UI thread updates; worker-thread WebView interceptor reads only volatile bypass. */
class LoginCompatibilitySession {
    @Volatile var bypass: Boolean = false
        private set
    @Volatile var manual: Boolean = false
        private set
    private var manualPage: Int? = null
    private var automatic = false

    @Synchronized fun observePage(url: String?): Boolean {
        val page = LoginCompatibilityPolicy.pageKey(url)
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
