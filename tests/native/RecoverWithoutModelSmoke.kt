package dev.caicompanion.app

import java.nio.file.Files
import java.util.UUID

fun main() {
    val root = Files.createTempDirectory("mandy-recover-without-model-")
    fun id() = UUID.randomUUID().toString()
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun reject(block: () -> Unit) { check(runCatching(block).isFailure); checks++ }
    try {
        val account = id(); val device = id()
        val w = NativeChatWorkspace(root, account, device)
        val key = NativeChatWorkspace.Key(id(), id())
        val ticket = w.select(key)
        w.edit(ticket, "Привет 🌙")
        val pending = w.prepare(ticket, "dictation")
        w.edit(ticket, "New draft")
        val user = NativeConversation.Event(pending.id, device, key.character, key.chat, 1, "user", pending.mode, pending.text)
        val reply = NativeConversation.Event(NativeConversation.replyId(user.id), device, key.character, key.chat, 2, "character", user.mode, "Hello")
        val events = mutableMapOf<String, NativeConversation.Event>()
        val history = object : NativeConversation.History {
            override fun find(id: String) = events[id]
            override fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String): NativeConversation.Event = error("Unexpected append")
            override fun recent(character: String, chat: String, limit: Int): List<NativeConversation.Event> = error("Unexpected scan")
            override fun commitReply(user: NativeConversation.Event, id: String, text: String): NativeConversation.Event = error("Unexpected commit")
        }
        val original = w.state(key)
        verify(w.recoverCommitted(ticket, pending.id, history) == null)
        verify(w.state(key) == original)
        events[reply.id] = reply
        reject { w.recoverCommitted(ticket, pending.id, history) }
        events[user.id] = user.copy(text = "Wrong text")
        reject { w.recoverCommitted(ticket, pending.id, history) }
        events[user.id] = user
        events[reply.id] = reply.copy(chat = id())
        reject { w.recoverCommitted(ticket, pending.id, history) }
        events[reply.id] = reply.copy(clock = 1)
        reject { w.recoverCommitted(ticket, pending.id, history) }
        events[reply.id] = reply
        reject { w.recoverCommitted(ticket, id(), history) }
        verify(w.state(key) == original)
        val other = w.select(NativeChatWorkspace.Key(id(), id()))
        reject { w.recoverCommitted(ticket, pending.id, history) }
        reject { w.recoverCommitted(other, pending.id, history) }
        val selected = w.select(key)
        verify(w.recoverCommitted(selected, pending.id, history) == reply)
        verify(w.state(key) == NativeChatWorkspace.State("New draft"))
        verify(NativeChatWorkspace(root, account, device).state(key) == w.state(key))
        verify(events == mapOf(user.id to user, reply.id to reply))
        reject { w.recoverCommitted(selected, pending.id, history) }
        println("PASS $checks model-free recovery checks")
    } finally { root.toFile().deleteRecursively() }
}
