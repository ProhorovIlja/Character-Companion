package dev.caicompanion.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.UUID

private typealias Event = NativeConversation.Event
private class TurnHistory : NativeConversation.History {
    val events = linkedMapOf<String, Event>()
    override fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String): Event {
        val e = Event(id, device, character, chat, events[id]?.clock ?: events.size + 1L, "user", mode, text)
        require(events[id] == null || events[id] == e)
        events[id] = e
        return e
    }
    override fun find(id: String) = events[id]
    override fun recent(character: String, chat: String, limit: Int) = events.values.filter { it.character == character && it.chat == chat }.sortedByDescending { it.clock }.take(limit)
    override fun commitReply(user: Event, id: String, text: String): Event {
        check(recent(user.character, user.chat, 1).first() == user)
        return Event(id, user.device, user.character, user.chat, events.size + 1L, "character", user.mode, text).also { events[id] = it }
    }
}
fun main() {
    val root = Files.createTempDirectory("mandy-embedded-turn")
    var count = 0
    fun verify(ok: Boolean) { check(ok); count++ }
    try {
        val account = UUID.randomUUID().toString()
        val device = UUID.randomUUID().toString()
        val chars = NativeCharacterLibrary(root.resolve("characters"), account)
        val w = NativeChatWorkspace(root.resolve("workspace"), account, device)
        val models = NativeModelLibrary(root.resolve("models"), 1024)
        val fixture = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN).put(byteArrayOf(71,71,85,70)).putInt(3).putLong(1).putLong(0).array()
        val m = models.import(fixture.inputStream())
        val a = chars.create("Mandy", "Мэнди 🌙", "First chat")
        val b = chars.create("Other", "Other identity", "First chat")
        val memory = NativeCharacterMemory(root.resolve("memory"), account)
        memory.edit(b.id, NativeCharacterMemory.State(stage = NativeCharacterMemory.Stage.FRIENDS, pinned = "Shared music"))
        val h = TurnHistory()
        val t = w.select(NativeChatWorkspace.Key(a.id, a.chats.single().id))
        fun send(ticket: NativeChatWorkspace.Ticket = t, sha: String = m.sha256, stop: () -> Boolean = { false }, emit: (NativeChatWorkspace.Ticket, String) -> Unit = { _, _ -> }) =
            EmbeddedChatTurn.send(w, ticket, chars, h, models, sha, stop, emit)
        w.edit(t, "unicode-input")
        val first = w.prepare(t)
        var displayed = ""
        val response = send(emit = { ticket, part -> check(ticket == t); displayed += part })
        verify(response.text == "Hello 🌙" && displayed == response.text)
        verify(response.id == NativeConversation.replyId(first.id))
        verify(w.state(t.key).pending == null && h.events.size == 2)
        w.edit(t, "normal")
        val second = w.prepare(t)
        var cancelled = false
        verify(runCatching { send(stop = { cancelled }, emit = { _, _ -> cancelled = true }) }.isFailure)
        verify(h.find(second.id) != null && h.find(NativeConversation.replyId(second.id)) == null)
        verify(w.state(t.key).pending == second)
        w.edit(t, "new draft")
        verify(send().text == "Hello 🌙")
        verify(w.state(t.key).draft == "new draft" && w.state(t.key).pending == null)
        val bt = w.select(NativeChatWorkspace.Key(b.id, b.chats.single().id))
        verify(runCatching { send() }.isFailure)
        w.edit(bt, "normal")
        val third = w.prepare(bt)
        verify(runCatching { send(bt, "0".repeat(64)) }.isFailure)
        verify(w.state(bt.key).pending == third && h.find(NativeConversation.replyId(third.id)) == null)
        verify(EmbeddedChatTurn.sendWithMemory(w, bt, chars, h, models, m.sha256, memory, onText = { _, _ -> }).character == b.id)
        verify(h.recent(a.id, t.key.chat, 20).size == 4 && h.recent(b.id, bt.key.chat, 20).size == 2)
        w.edit(bt, "normal")
        val fourth = w.prepare(bt)
        // Simulate a completed commit before pending state was cleared.
        val u = h.appendUser(fourth.id, device, b.id, bt.key.chat, "text", fourth.text)
        h.commitReply(u, NativeConversation.replyId(fourth.id), "Saved reply")
        Files.delete(m.path)
        verify(send(bt).text == "Saved reply")
        verify(w.state(bt.key).pending == null)
        println("EmbeddedChatTurn: $count checks passed (JNI test double)")
    } finally { root.toFile().deleteRecursively() }
}
