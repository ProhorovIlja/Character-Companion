package dev.caicompanion.app

import java.nio.file.Files
import java.util.UUID

fun main() {
    val root = Files.createTempDirectory("mandy-memory-context-")
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun id() = UUID.randomUUID().toString()
    try {
        val account = id(); val device = id()
        val profiles = NativeCharacterLibrary(root.resolve("profiles"), account)
        val profile = profiles.create("Mandy", "Core identity.", "Conversation")
        val memory = NativeCharacterMemory(root.resolve("memory"), account)
        val saved = memory.edit(profile.id, NativeCharacterMemory.State(stage = NativeCharacterMemory.Stage.FRIENDS,
            pinned = "Prefers tea.", relationship = "Mutual respect.", memories = listOf("Visited Киото", "Enjoys jazz")))
        val query = "КИОТО " + "x".repeat(15994)
        val narrow = memory.compose(profile.id, profile.identity, query)
        verify(narrow == profile.identity)
        val wide = memory.compose(profile.id, profile.identity, query, 24000)
        verify(wide.startsWith(profile.identity))
        verify(wide.contains("Pinned memory: Prefers tea."))
        verify(wide.contains("Relevant memory: Visited Киото"))
        verify(wide.length + query.length <= 24000)
        verify(memory.compose(profile.id, "I".repeat(8000), query, 24000) == "I".repeat(8000))
        verify(runCatching { memory.compose(profile.id, profile.identity, query, 255) }.isFailure)
        verify(runCatching { memory.compose(profile.id, profile.identity, query, 64001) }.isFailure)
        val w = NativeChatWorkspace(root.resolve("workspace"), account, device)
        val ticket = w.select(NativeChatWorkspace.Key(profile.id, profile.chats.single().id))
        w.edit(ticket, query)
        val pending = w.prepare(ticket, identity = profile.identity, contextUnits = 24000)
        val events = mutableListOf<NativeConversation.Event>()
        val history = object : NativeConversation.History {
            override fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String) =
                find(id) ?: NativeConversation.Event(id, device, character, chat, events.size.toLong()+1, "user", mode, text).also { events.add(it) }
            override fun find(id: String) = events.find { it.id == id }
            override fun recent(character: String, chat: String, limit: Int) = events.asReversed().filter { it.character == character && it.chat == chat }.take(limit)
            override fun commitReply(user: NativeConversation.Event, id: String, text: String) =
                NativeConversation.Event(id, user.device, user.character, user.chat, events.size.toLong()+1, "character", user.mode, text).also { events.add(it) }
        }
        verify(runCatching { w.sendPending(ticket, history, narrow, onText = { _, _ -> }) { _, _, _ -> error("unexpected inference") } }.isFailure)
        verify(events.isEmpty() && w.state(ticket.key).pending == pending)
        val reply = w.sendPendingWithBudget(ticket, history, wide, 24000, onText = { _, _ -> }) { messages, _, emit ->
            verify(messages.map { it.content } == listOf(wide, query))
            emit("Да"); "Да"
        }
        verify(reply.text == "Да" && events.size == 2)
        verify(w.state(ticket.key) == NativeChatWorkspace.State())
        verify(NativeCharacterMemory(root.resolve("memory"), account).get(profile.id) == saved)
        verify(memory.get(id()) == NativeCharacterMemory.State())
        println("PASS: $checks integrated memory/context assertions")
    } finally { root.toFile().deleteRecursively() }
}
