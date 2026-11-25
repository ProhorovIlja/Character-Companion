package dev.caicompanion.app

import java.nio.file.Files
import java.util.UUID

fun main() {
    val root = Files.createTempDirectory("mandy-workspace-budget-")
    fun id() = UUID.randomUUID().toString()
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    try {
        val account = id(); val device = id()
        val w = NativeChatWorkspace(root, account, device)
        val key = NativeChatWorkspace.Key(id(), id())
        val ticket = w.select(key)
        val identity = "I".repeat(8000)
        val draft = "x".repeat(16000)
        verify(InferencePreset.LOW_MEMORY.contextUnits == 12000)
        verify(InferencePreset.BALANCED.contextUnits == 12000)
        verify(InferencePreset.QUALITY.contextUnits == 24000)
        w.edit(ticket, draft)
        verify(runCatching { w.prepare(ticket, identity = identity) }.isFailure)
        verify(w.state(key) == NativeChatWorkspace.State(draft))
        verify(runCatching { w.prepare(ticket, identity = identity, contextUnits = 24001 - 2) }.isFailure)
        verify(w.state(key).draft == draft)
        val pending = w.prepare(ticket, identity = identity, contextUnits = InferencePreset.QUALITY.contextUnits)
        verify(w.state(key).pending == pending && pending.text == draft)
        verify(NativeChatWorkspace(root, account, device).state(key).pending == pending)
        val events = mutableListOf<NativeConversation.Event>()
        val history = object : NativeConversation.History {
            override fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String) =
                find(id) ?: NativeConversation.Event(id, device, character, chat, events.size.toLong()+1, "user", mode, text).also { events.add(it) }
            override fun find(id: String) = events.find { it.id == id }
            override fun recent(character: String, chat: String, limit: Int) = events.asReversed().filter { it.character == character && it.chat == chat }.take(limit)
            override fun commitReply(user: NativeConversation.Event, id: String, text: String) =
                NativeConversation.Event(id, user.device, user.character, user.chat, events.size.toLong()+1, "character", user.mode, text).also { events.add(it) }
        }
        verify(runCatching { w.sendPending(ticket, history, identity, onText = { _, _ -> }) { _, _, _ -> error("unexpected generation") } }.isFailure)
        verify(events.isEmpty() && w.state(key).pending == pending)
        val reply = w.sendPendingWithBudget(ticket, history, identity, 24000, onText = { _, _ -> }) { messages, _, emit ->
            verify(messages.map { it.content } == listOf(identity, draft))
            emit("OK"); "OK"
        }
        verify(reply.text == "OK" && events.size == 2)
        verify(w.state(key) == NativeChatWorkspace.State())
        w.edit(ticket, "hello")
        verify(w.prepare(ticket).text == "hello")
        println("PASS: $checks workspace budget assertions")
    } finally { root.toFile().deleteRecursively() }
}
