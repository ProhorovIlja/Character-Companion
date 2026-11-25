package dev.caicompanion.app

import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/** Platform-neutral native turn contract; text only, matching the portable v1 history. */
object NativeConversation {
    const val MAX_CLOCK = 9007199254740991L
    data class Event(val id: String, val device: String, val character: String, val chat: String,
                     val clock: Long, val role: String, val mode: String, val text: String) {
        init {
            listOf(id, device, character, chat).forEach { canonicalId(it) }
            require(clock in 1..MAX_CLOCK && role in setOf("user", "character") && mode in setOf("text", "dictation", "call"))
            validateText(text)
        }
    }
    data class Message(val role: String, val content: String)
    interface History {
        /** Atomically assign clock; return existing identical event on retry. */
        fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String): Event
        fun find(id: String): Event?
        /** Newest first, strictly scoped to character and chat. */
        fun recent(character: String, chat: String, limit: Int): List<Event>
        /** In one transaction, verify user is still latest and insert reply or reject. */
        fun commitReply(user: Event, id: String, text: String): Event
    }
    fun canonicalId(value: String): UUID = UUID.fromString(value).also { require(it.toString() == value) }
    fun validateText(value: String) {
        require(value.isNotBlank() && value.length <= 16000)
        require(value.none { it.code < 32 && it !in "\t\r\n" })
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (c.isHighSurrogate()) require(i < value.length && value[i++].isLowSurrogate())
            else require(!c.isLowSurrogate())
        }
    }
    /** RFC UUIDv5, identical to Python uuid5(message_id, 'companion-reply-v1'). */
    fun replyId(userId: String): String {
        val id = canonicalId(userId)
        val bytes = ByteBuffer.allocate(16).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).array()
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(bytes)
        val hash = digest.digest("companion-reply-v1".toByteArray(Charsets.UTF_8))
        hash[6] = ((hash[6].toInt() and 15) or 80).toByte()
        hash[8] = ((hash[8].toInt() and 63) or 128).toByte()
        val buffer = ByteBuffer.wrap(hash)
        return UUID(buffer.long, buffer.long).toString()
    }
    fun validateReply(user: Event, reply: Event): Event {
        require(reply.id == replyId(user.id) && reply.role == "character" && reply.device == user.device &&
                reply.character == user.character && reply.chat == user.chat && reply.mode == user.mode && reply.clock > user.clock)
        return reply
    }
    /** Synchronous foreground turn. Call on a user-initiated worker, never the UI thread. */
    fun send(history: History, id: String, device: String, character: String, chat: String, text: String,
             identity: String, mode: String = "text", contextUnits: Int = 12000,
             cancelled: () -> Boolean = { false }, onText: (String) -> Unit,
             generate: (List<Message>, () -> Boolean, (String) -> Unit) -> String): Event {
        fun checkActive() { if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Turn cancelled") }
        validateText(identity)
        require(identity.length <= 8000 && contextUnits in 256..64000)
        Event(id, device, character, chat, 1, "user", mode, text)
        checkActive()
        // A completed turn may need recovery after a crash between reply commit
        // and pending-state cleanup. A smaller preset must not block replay.
        if (identity.length + text.length > contextUnits) {
            val saved = history.find(replyId(id))
            require(saved != null) { "Message exceeds context allowance" }
            val original = checkNotNull(history.find(id)) { "Saved reply has no user event" }
            require(original == Event(id, device, character, chat, original.clock, "user", mode, text))
            validateReply(original, saved)
            checkActive()
            onText(saved.text)
            return saved
        }
        val user = history.appendUser(id, device, character, chat, mode, text)
        require(user == Event(id, device, character, chat, user.clock, "user", mode, text))
        val replyId = replyId(id)
        history.find(replyId)?.let { saved ->
            validateReply(user, saved)
            checkActive()
            onText(saved.text)
            return saved
        }
        val rows = history.recent(character, chat, 200)
        if (rows.firstOrNull() != user) throw IOException("Conversation advanced")
        require(rows.size <= 200 && rows.all { it.character == character && it.chat == chat })
        var remaining = contextUnits - identity.length
        val recent = mutableListOf<Message>()
        for (row in rows) {
            if (row.text.length > remaining) break
            remaining -= row.text.length
            recent.add(Message(if (row.role == "character") "assistant" else "user", row.text))
        }
        // A bounded recent suffix must not begin with an assistant reply whose
        // user turn was omitted. Keep the newest user message and core identity.
        if (recent.size > 1 && recent.last().role == "assistant") recent.removeAt(recent.lastIndex)
        val messages = listOf(Message("system", identity)) + recent.asReversed()
        val streamed = StringBuilder()
        checkActive()
        val answer = generate(messages, cancelled) { fragment ->
            checkActive()
            if (fragment.length > 16000 - streamed.length) throw IOException("Reply too large")
            streamed.append(fragment)
            if (fragment.isNotEmpty()) onText(fragment)
        }
        checkActive()
        require(answer == streamed.toString()) { "Provider output differs from displayed text" }
        validateText(answer)
        return validateReply(user, history.commitReply(user, replyId, answer))
    }
}
