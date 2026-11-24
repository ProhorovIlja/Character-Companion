package dev.caicompanion.app

import java.util.UUID

/** Main-thread session state shared by native speech providers. No audio retention or auto-send. */
class NativeDictation {
    private data class Session(val id: UUID, val ticket: NativeChatWorkspace.Ticket, val draft: String,
                               val start: Int, val end: Int, val sendAllowed: Boolean,
                               var finishing: Boolean = false, var sendRequested: Boolean = false)
    data class Result(val edit: VoiceDraftPolicy.Edit, val requestSend: Boolean)
    private var session: Session? = null
    val active: Boolean get() = session != null
    fun begin(ticket: NativeChatWorkspace.Ticket, draft: String, start: Int, end: Int, sendAllowed: Boolean): UUID {
        check(session == null)
        require(VoiceDraftPolicy.validText(draft) && start in 0..draft.length && end in 0..draft.length)
        return UUID.randomUUID().also { session = Session(it, ticket, draft, start, end, sendAllowed) }
    }
    /** First explicit Stop wins. Natural provider endpointing alone never requests a send. */
    fun stop(id: UUID, send: Boolean): Boolean {
        val s = session?.takeIf { it.id == id } ?: return false
        if (s.finishing || (send && !s.sendAllowed)) return false
        s.finishing = true; s.sendRequested = send
        return true
    }
    /** Terminal provider callback consumes the session even if its output is invalid. */
    fun complete(id: UUID, ticket: NativeChatWorkspace.Ticket?, draft: String?, candidates: List<String>?): Result? {
        val s = session?.takeIf { it.id == id } ?: return null
        session = null
        if (ticket != s.ticket || draft != s.draft) return null
        val edit = VoiceRecognitionOptions.edit(s.draft, candidates, s.start, s.end) ?: return null
        return Result(edit, s.sendRequested)
    }
    fun cancel(id: UUID): Boolean {
        if (session?.id != id) return false
        session = null
        return true
    }
}
