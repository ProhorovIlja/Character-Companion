package dev.caicompanion.app

import java.nio.file.Files
import java.util.UUID

private typealias E = NativeConversation.Event
private class MemoryHistory : NativeConversation.History {
    val rows = linkedMapOf<String, E>()
    override fun find(id: String) = rows[id]
    override fun recent(character: String, chat: String, limit: Int) = rows.values.filter { it.character == character && it.chat == chat }.sortedByDescending { it.clock }.take(limit)
    override fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String): E =
        E(id, device, character, chat, rows.size + 1L, "user", mode, text).also { rows[id] = it }
    override fun commitReply(user: E, id: String, text: String): E =
        E(id, user.device, user.character, user.chat, rows.size + 1L, "character", user.mode, text).also { rows[id] = it }
}
fun main() {
    val root = Files.createTempDirectory("mandy-memory-assessment")
    fun id(n: Long) = UUID(0, n).toString()
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    try {
        val memory = NativeCharacterMemory(root, id(1))
        val key = NativeChatWorkspace.Key(id(2), id(3))
        val other = NativeChatWorkspace.Key(id(4), id(5))
        val history = MemoryHistory()
        val initial = memory.edit(key.character, NativeCharacterMemory.State(stage = NativeCharacterMemory.Stage.FRIENDS, pinned = "Keep this"))
        fun propose(raw: String) = MemoryAssessment.propose(history, key, memory.get(key.character)) { _, _ -> raw }
        verify(propose("REMEMBER\nЛюблю гитару 🌙") == null)
        val user = history.appendUser(id(10), id(6), key.character, key.chat, "text", "Люблю гитару 🌙. I like music.")
        verify(propose("REMEMBER\nЛюблю гитару 🌙") == null)
        val reply = history.commitReply(user, NativeConversation.replyId(user.id), "I play piano.")
        verify(propose("SKIP") == null)
        bad { propose("REMEMBER\nI play piano.") }
        bad { propose("REMEMBER\nЛюблю пианино") }
        bad { propose("REMEMBER\nI like music.\nExtra") }
        bad { propose("UNKNOWN\nI like music.") }
        bad { propose("REMEMBER\n" + "x".repeat(390)) }
        bad { MemoryAssessment.propose(history, key, initial, { true }) { _, _ -> error("unexpected inference") } }
        val p = checkNotNull(MemoryAssessment.propose(history, key, initial) { messages, _ ->
            check(messages.last().content.contains(user.text) && messages.last().content.contains(reply.text))
            "REMEMBER\nЛюблю гитару 🌙"
        })
        verify(p.evidence.user == user && p.evidence.reply == reply)
        verify(memory.get(key.character) == initial)
        bad { MemoryAssessment.apply(history, memory, key, p, false) }
        bad { MemoryAssessment.apply(history, memory, other, p, true) }
        bad { MemoryAssessment.apply(history, memory, key, p.copy(quote = "invented"), true) }
        val saved = MemoryAssessment.apply(history, memory, key, p, true)
        verify(saved.memories == listOf("User said: Люблю гитару 🌙"))
        verify(saved.pinned == initial.pinned && saved.stage == initial.stage)
        verify(NativeCharacterMemory(root, id(1)).get(key.character) == saved)
        verify(memory.compose(key.character, "Identity", "гитару").contains(p.note))
        verify(propose("REMEMBER\nЛюблю гитару 🌙") == null)
        bad { MemoryAssessment.apply(history, memory, key, p, true) }
        val next = checkNotNull(propose("REMEMBER\nI like music."))
        memory.edit(key.character, saved.copy(pinned = "Updated"))
        bad { MemoryAssessment.apply(history, memory, key, next, true) }
        val fresh = checkNotNull(propose("REMEMBER\nI like music."))
        history.appendUser(id(11), id(6), key.character, key.chat, "text", "Next turn")
        bad { MemoryAssessment.apply(history, memory, key, fresh, true) }
        verify(memory.get(other.character).memories.isEmpty())
        val current = memory.get(key.character)
        val full = memory.edit(key.character, current.copy(memories = List(32) { "Memory $it" }))
        val u2 = history.appendUser(id(12), id(6), key.character, key.chat, "text", "Another preference")
        history.commitReply(u2, NativeConversation.replyId(u2.id), "Understood")
        verify(MemoryAssessment.propose(history, key, full) { _, _ -> error("full store must not infer") } == null)
        verify(memory.get(key.character) == full)
        println("MemoryAssessment: $checks checks passed")
    } finally { root.toFile().deleteRecursively() }
}
