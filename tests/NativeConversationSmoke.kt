package dev.caicompanion.app

import java.util.UUID

private typealias E = NativeConversation.Event
private fun id(n: Long) = UUID(0, n).toString()
private class MemoryHistory : NativeConversation.History {
    private val rows = linkedMapOf<String, E>()
    override fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String): E {
        require(rows.values.none { it.chat == chat && it.character != character })
        val event = E(id, device, character, chat, rows[id]?.clock ?: (rows.size + 1L), "user", mode, text)
        require(rows[id] == null || rows[id] == event)
        rows[id] = event
        return event
    }
    override fun find(id: String) = rows[id]
    override fun recent(character: String, chat: String, limit: Int) = rows.values.filter { it.character == character && it.chat == chat }.sortedByDescending { it.clock }.take(limit)
    override fun commitReply(user: E, id: String, text: String): E {
        check(recent(user.character, user.chat, 1).first() == user)
        return E(id, user.device, user.character, user.chat, rows.size + 1L, "character", user.mode, text).also { rows[id] = it }
    }
}
fun main() {
    val h = MemoryHistory()
    var calls = 0
    var cancel = false
    var shown = ""
    fun send(n: Long, character: Long = 1, chat: Long = 2, text: String = "Привет 🌙", identity: String = "Identity", budget: Int = 256,
             generate: (List<NativeConversation.Message>, () -> Boolean, (String) -> Unit) -> String = { messages, _, emit ->
                 calls++; check(messages.first().content == identity); check(messages.last().content == text)
                 emit("Hello "); emit("🌙"); "Hello 🌙"
             }) = NativeConversation.send(h, id(n), id(3), id(character), id(chat), text, identity, contextUnits = budget,
                 cancelled = { cancel }, onText = { shown += it }, generate = generate)
    check(NativeConversation.replyId(id(4)) == "4950f54b-3761-5427-af14-0094d21cf77f")
    check(send(4).text == "Hello 🌙" && shown == "Hello 🌙")
    check(h.recent(id(1), id(2), 10).size == 2)
    check(send(4).id == NativeConversation.replyId(id(4)) && calls == 1)
    check(runCatching { send(4, text = "different") }.isFailure)
    check(runCatching { send(5) { _, _, emit -> emit("partial"); error("offline") } }.isFailure)
    check(h.find(id(5)) != null && h.find(NativeConversation.replyId(id(5))) == null)
    check(send(5).text == "Hello 🌙")
    check(runCatching { send(6) { _, _, emit -> emit("partial"); cancel = true; "partial" } }.isFailure)
    cancel = false
    check(h.find(NativeConversation.replyId(id(6))) == null)
    check(runCatching { send(7) { _, _, emit -> emit("different"); "answer" } }.isFailure)
    check(h.find(NativeConversation.replyId(id(7))) == null)
    check(runCatching { send(8) { _, _, emit -> h.appendUser(id(9), id(3), id(1), id(2), "text", "new turn"); emit("stale"); "stale" } }.isFailure)
    check(h.find(NativeConversation.replyId(id(8))) == null)
    check(send(10, 11, 12) { messages, _, emit -> check(messages.size == 2); emit("other"); "other" }.text == "other")
    check(runCatching { send(13, text = "x".repeat(300)) }.isFailure && h.find(id(13)) == null)
    check(runCatching { send(14, text = "\uD800") }.isFailure && h.find(id(14)) == null)
    check(runCatching { send(15, text = "a\u0000b") }.isFailure && h.find(id(15)) == null)
    println("NativeConversation: 18 checks passed")
}
