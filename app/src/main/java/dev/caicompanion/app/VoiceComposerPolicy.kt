package dev.caicompanion.app

import java.net.URI
import java.util.Locale

/** Pure guard for a user-initiated handoff to the currently focused message editor. */
object VoiceComposerPolicy {
    private val path = Regex("^/chat/[A-Za-z0-9_-]{1,128}/?$")
    private val query = Regex("^char=[A-Za-z0-9_-]{1,128}$")

    fun chatPage(raw: String?): String? {
        if (raw.isNullOrEmpty() || raw.length > 512) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host !in setOf("character.ai", "www.character.ai") ||
            !uri.scheme.equals("https", true) || uri.userInfo != null ||
            uri.port !in listOf(-1, 443) || uri.rawFragment != null) return null
        val route = uri.rawPath ?: return null
        val q = uri.rawQuery
        if (!(path.matches(route) && q == null) &&
            !(route == "/chat" && q != null && query.matches(q))) return null
        return raw
    }

    fun validDraft(text: String?): String? = text?.takeIf {
        it.isNotBlank() && it.length <= VoiceDraftPolicy.MAX_CHARS &&
            !it.any { c -> c == '\u0000' }
    }

    /** Process-local recovery slot. Never overwrites an unsent draft or retries insertion. */
    class RecoveryDraft {
        private var text: String? = null
        val hasDraft: Boolean get() = text != null
        fun preserve(draft: String?): Boolean {
            if (hasDraft) return false
            text = validDraft(draft)
            return hasDraft
        }
        fun open(launchEditor: (String) -> Boolean): Boolean {
            val draft = text ?: return false
            if (!launchEditor(draft)) return false
            text = null
            return true
        }
        fun clear() { text = null }
    }

    /** A navigation or renderer restart revokes permission even if the URL later repeats. */
    class Ticket(page: String?) {
        private var expected = chatPage(page)
        val eligible: Boolean get() = expected != null
        fun observe(page: String?) { if (expected != chatPage(page)) invalidate() }
        fun invalidate() { expected = null }
        fun consume(page: String?): Boolean {
            val accepted = expected != null && expected == chatPage(page)
            invalidate()
            return accepted
        }
    }
}
