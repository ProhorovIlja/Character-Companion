package dev.caicompanion.app

import java.net.URI
import java.util.Locale

/** A manual reload is bound to the visible address, not to a sanitized bookmark. */
object ChatRefreshPolicy {
    private val characterPath = Regex("^/chat/[A-Za-z0-9_-]{1,128}/?$")
    private val characterQuery = Regex("^[A-Za-z0-9_-]{1,128}$")

    fun isChat(raw: String?): Boolean {
        if (raw.isNullOrEmpty() || raw.length > 4096) return false
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        if (host != "character.ai" && host != "www.character.ai") return false
        if (!uri.scheme.equals("https", true) || uri.userInfo != null ||
            (uri.port != -1 && uri.port != 443)) return false
        val path = uri.rawPath.orEmpty()
        if (characterPath.matches(path)) return true
        if (path != "/chat" && path != "/chat/") return false
        // Legacy char query; reject ambiguous duplicate character identifiers.
        val values = uri.rawQuery.orEmpty().split('&').filter { it.substringBefore('=') == "char" }
        return values.size == 1 && characterQuery.matches(values[0].substringAfter('=', ""))
    }

    /** Preserve even unknown query parameters and fragments for an in-memory equality check only. */
    fun samePage(captured: String?, visible: String?): Boolean =
        !captured.isNullOrBlank() && captured.length <= 4096 && captured == visible
}
