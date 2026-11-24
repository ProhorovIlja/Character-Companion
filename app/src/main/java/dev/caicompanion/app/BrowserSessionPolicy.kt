package dev.caicompanion.app

/**
 * A browser-backed session is separate from WebView's cookie store. Only public,
 * sanitized Character.AI content URLs may be opened in a Custom Tab. The home
 * URL is the correct entry point for browser-managed sign-in: forwarding an
 * in-progress OAuth redirect from WebView may leak transient state or fail CSRF.
 */
object BrowserSessionPolicy {
    const val HOME = "https://character.ai/"

    /** Always hand off a clean first-party link, not a token, login or identity URL. */
    fun safeTabUrl(current: String?): String = UrlPolicy.sanitizeBookmark(current) ?: HOME

    data class Launch(val destination: String, val openTab: Boolean)

    /** Restored activities ignore stale launch extras, including a previous open-now request. */
    fun resolveLaunch(restoring: Boolean, supplied: String?, saved: String?, retained: String?,
                      current: String?, requestedOpen: Boolean): Launch {
        val candidate = if (restoring) saved ?: retained ?: current else supplied ?: current
        return Launch(safeTabUrl(candidate), !restoring && requestedOpen)
    }
}
