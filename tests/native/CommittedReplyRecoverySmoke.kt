package dev.caicompanion.app

import java.util.UUID

fun main() {
    fun id() = UUID.randomUUID().toString()
    val user = NativeConversation.Event(id(), id(), id(), id(), 1, "user", "text", "x".repeat(16000))
    val reply = user.copy(id = NativeConversation.replyId(user.id), clock = 2, role = "character", text = "Готово")
    val events = mutableMapOf(user.id to user, reply.id to reply)
    val history = object : NativeConversation.History {
        override fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String): NativeConversation.Event = error("Unexpected append")
        override fun find(id: String) = events[id]
        override fun recent(character: String, chat: String, limit: Int): List<NativeConversation.Event> = error("Unexpected history scan")
        override fun commitReply(user: NativeConversation.Event, id: String, text: String): NativeConversation.Event = error("Unexpected commit")
    }
    val emitted = mutableListOf<String>()
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    fun replay(candidate: NativeConversation.Event = user, cancelled: () -> Boolean = { false }) =
        NativeConversation.send(history, candidate.id, candidate.device, candidate.character, candidate.chat,
            candidate.text, "Core identity", candidate.mode, contextUnits = 12000, cancelled = cancelled,
            onText = { emitted.add(it) }) { _, _, _ -> error("Unexpected generation") }
    verify(replay() == reply)
    verify(emitted == listOf(reply.text))
    verify(events.size == 2)
    emitted.clear()
    verify(runCatching { replay(user.copy(chat = id())) }.isFailure)
    verify(runCatching { replay(user.copy(text = "y".repeat(16000))) }.isFailure)
    verify(runCatching { replay(cancelled = { true }) }.isFailure)
    events[reply.id] = reply.copy(character = id())
    verify(runCatching { replay() }.isFailure)
    events[reply.id] = reply
    events.remove(user.id)
    verify(runCatching { replay() }.isFailure)
    events[user.id] = user
    events.remove(reply.id)
    verify(runCatching { replay() }.isFailure)
    verify(emitted.isEmpty())
    verify(events == mapOf(user.id to user))
    println("PASS: $checks committed reply recovery assertions")
}
