package dev.caicompanion.app

import java.nio.file.Files
import java.util.UUID

private fun uid(n: Long) = UUID(0, n).toString()
private class History : NativeConversation.History {
    val rows = linkedMapOf<String, NativeConversation.Event>()
    override fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String): NativeConversation.Event {
        val e = NativeConversation.Event(id, device, character, chat, rows[id]?.clock ?: rows.size + 1L, "user", mode, text)
        require(rows[id] == null || rows[id] == e)
        rows[id] = e
        return e
    }
    override fun find(id: String) = rows[id]
    override fun recent(character: String, chat: String, limit: Int) = rows.values.filter { it.character == character && it.chat == chat }.sortedByDescending { it.clock }.take(limit)
    override fun commitReply(user: NativeConversation.Event, id: String, text: String): NativeConversation.Event {
        check(recent(user.character, user.chat, 1).first() == user)
        return NativeConversation.Event(id, user.device, user.character, user.chat, rows.size + 1L, "character", user.mode, text).also { rows[id] = it }
    }
}
fun main() {
    val root = Files.createTempDirectory("mandy-workspace-test")
    var count = 0
    fun verify(value: Boolean) { check(value); count++ }
    try {
        val a = NativeChatWorkspace.Key(uid(1), uid(2))
        val b = NativeChatWorkspace.Key(uid(3), uid(4))
        var w = NativeChatWorkspace(root, uid(90), uid(91))
        verify(w.selected() == null)
        var t = w.select(a)
        w.edit(t, "Привет 🌙")
        val p = w.prepare(t, "dictation")
        verify(w.state(a).draft.isEmpty() && w.state(a).pending == p)
        w.edit(t, "Next draft")
        verify(runCatching { w.prepare(t) }.isFailure)
        val old = t
        w = NativeChatWorkspace(root, uid(90), uid(91))
        t = checkNotNull(w.selected())
        verify(t.key == a && t != old && w.state(a).pending == p)
        verify(w.state(a).draft == "Next draft")
        verify(!w.visible(old) && runCatching { w.edit(old, "wrong") }.isFailure)
        verify(NativeChatWorkspace(root, uid(92), uid(91)).selected() == null)
        verify(NativeChatWorkspace(root, uid(90), uid(92)).state(a).pending == null)
        val history = History()
        verify(runCatching { w.sendPending(t, history, "Identity", onText = { _, _ -> }) { _, _, emit -> emit("partial"); error("offline") } }.isFailure)
        verify(w.state(a).pending == p && history.find(p.id) != null)
        val result = w.sendPending(t, history, "Identity", onText = { ticket, _ -> check(ticket == t) }) { _, _, emit -> emit("Hello 🌙"); "Hello 🌙" }
        verify(result.id == NativeConversation.replyId(p.id) && result.mode == "dictation")
        verify(w.state(a).pending == null && w.state(a).draft == "Next draft")
        val next = w.prepare(t)
        verify(runCatching { w.sendPending(t, history, "Identity", onText = { _, _ -> }) { _, stop, emit ->
            w.select(b); check(stop()); emit("stale"); "stale"
        } }.isFailure)
        verify(w.selected()?.key == b && w.state(a).pending == next && w.state(b) == NativeChatWorkspace.State())
        verify(history.find(NativeConversation.replyId(next.id)) == null)
        t = w.select(a)
        verify(runCatching { w.discardPending(t, p.id) }.isFailure)
        w.discardPending(t, next.id)
        verify(w.state(a).pending == null && history.find(next.id) != null)
        verify(runCatching { w.edit(t, "\uD800") }.isFailure)
        w.edit(t, "界".repeat(16000))
        val huge = w.prepare(t)
        w.edit(t, "界".repeat(16000))
        verify(w.state(a).pending == huge && w.state(a).draft.length == 16000)
        val record = root.resolve(uid(90)).resolve(uid(91)).resolve("${a.character}_${a.chat}.bin")
        Files.write(record, byteArrayOf(0, 1, 2))
        verify(runCatching { w.state(a) }.isFailure)
        println("NativeChatWorkspace: $count checks passed")
    } finally { root.toFile().deleteRecursively() }
}
