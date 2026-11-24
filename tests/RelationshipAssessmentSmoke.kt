package dev.caicompanion.app

import java.nio.file.Files
import java.util.UUID

private typealias Event = NativeConversation.Event
private class AssessmentHistory : NativeConversation.History {
    val rows = linkedMapOf<String, Event>()
    override fun find(id: String) = rows[id]
    override fun recent(character: String, chat: String, limit: Int) = rows.values.filter { it.character == character && it.chat == chat }.sortedByDescending { it.clock }.take(limit)
    override fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String): Event =
        Event(id, device, character, chat, rows.size + 1L, "user", mode, text).also { rows[id] = it }
    override fun commitReply(user: Event, id: String, text: String): Event =
        Event(id, user.device, user.character, user.chat, rows.size + 1L, "character", user.mode, text).also { rows[id] = it }
}
fun main() {
    val root = Files.createTempDirectory("mandy-assessment")
    fun id(n: Long) = UUID(0, n).toString()
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    try {
        val m = NativeCharacterMemory(root, id(1))
        val key = NativeChatWorkspace.Key(id(2), id(3))
        val other = NativeChatWorkspace.Key(id(4), id(5))
        val h = AssessmentHistory()
        fun propose(raw: String) = RelationshipAssessment.propose(h, key, m.get(key.character)) { _, _ -> raw }
        verify(propose("FRIENDS\nShared music") == null)
        val u = h.appendUser(id(10), id(6), key.character, key.chat, "text", "Want to be friends?")
        verify(propose("FRIENDS\nShared music") == null)
        val r = h.commitReply(u, NativeConversation.replyId(u.id), "Yes, friends sounds good.")
        verify(RelationshipAssessment.latest(h, key) == RelationshipAssessment.Evidence(u, r))
        verify(propose("UNCHANGED") == null)
        bad { propose("ROMANTIC\nNote\nExtra") }
        bad { propose("UNKNOWN\nNote") }
        bad { propose("FRIENDS\n" + "a".repeat(801)) }
        bad { propose("FRIENDS\n\u0000") }
        bad { RelationshipAssessment.propose(h, key, m.get(key.character), { true }) { _, _ -> error("must not run") } }
        val p = checkNotNull(RelationshipAssessment.propose(h, key, m.get(key.character)) { messages, _ ->
            check(messages.first().role == "system" && messages.last().content.contains(u.text) && messages.last().content.contains(r.text))
            "FRIENDS\nAgreed to be friends."
        })
        verify(m.get(key.character).stage == NativeCharacterMemory.Stage.STRANGERS)
        bad { RelationshipAssessment.apply(h, m, key, p, false) }
        bad { RelationshipAssessment.apply(h, m, other, p, true) }
        verify(RelationshipAssessment.apply(h, m, key, p, true).stage == NativeCharacterMemory.Stage.FRIENDS)
        verify(NativeCharacterMemory(root, id(1)).get(key.character).relationship == p.note)
        bad { RelationshipAssessment.apply(h, m, key, p, true) }
        val next = checkNotNull(propose("ESTRANGED\nDisagreement"))
        h.appendUser(id(11), id(6), key.character, key.chat, "text", "New message")
        bad { RelationshipAssessment.apply(h, m, key, next, true) }
        verify(propose("ROMANTIC\nNot supported") == null)
        verify(m.get(other.character).revision == 0L)
        println("RelationshipAssessment: $checks checks passed")
    } finally { root.toFile().deleteRecursively() }
}
