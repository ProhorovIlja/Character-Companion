package dev.caicompanion.app

/** Pure policy for Gecko top-level loads. Subresources do not pass through this gate. */
object GeckoNavigationPolicy {
    enum class Decision { ALLOW, CONFIRM_EXTERNAL, BLOCK }

    fun decide(url: String?, userGesture: Boolean, newWindow: Boolean): Decision {
        if (url == "about:blank") return Decision.ALLOW
        if (!UrlPolicy.isHttps(url)) return Decision.BLOCK
        // OAuth providers commonly reject embedded user agents. Never promise a cookie handoff.
        if (UrlPolicy.isIdentityUrl(url)) {
            return if (userGesture) Decision.CONFIRM_EXTERNAL else Decision.BLOCK
        }
        if (UrlPolicy.isInternal(url)) {
            return if (newWindow) {
                if (userGesture) Decision.CONFIRM_EXTERNAL else Decision.BLOCK
            } else Decision.ALLOW
        }
        return if (userGesture) Decision.CONFIRM_EXTERNAL else Decision.BLOCK
    }
}
