package dev.caicompanion.app

import java.util.UUID
import java.io.IOException

private fun uid() = UUID.randomUUID().toString()
private class MemoryHistory(val device: String, val character: String, val chat: String) : NativeConversation.History {
    val events = mutableListOf<NativeConversation.Event>()
    var writes = 0
    fun seed(text: String, role: String) {
        events.add(NativeConversation.Event(uid(), device, character, chat, events.size.toLong() + 1, role, "text", text))
    }
    override fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String): NativeConversation.Event {
        find(id)?.let { return it }
        return NativeConversation.Event(id,device,character,chat,events.size.toLong()+1,"user",mode,text).also { events.add(it); writes++ }
    }
    override fun find(id: String) = events.find { it.id == id }
    override fun recent(character: String, chat: String, limit: Int) = events.asReversed().filter { it.character == character && it.chat == chat }.take(limit)
    override fun commitReply(user: NativeConversation.Event, id: String, text: String): NativeConversation.Event =
        NativeConversation.Event(id,user.device,user.character,user.chat,events.size.toLong()+1,"character",user.mode,text).also { events.add(it); writes++ }
}

fun main() {
    var count = 0
    fun verify(condition: Boolean) { check(condition); count++ }
    fun newHistory() = MemoryHistory(uid(),uid(),uid())
    fun run(h: MemoryHistory, text: String = "Newest", identity: String = "Core identity", budget: Int = 256, id: String = uid(), stopped: () -> Boolean = {false}, generate: (List<NativeConversation.Message>, (String)->Unit)->String): NativeConversation.Event =
        NativeConversation.send(h,id,h.device,h.character,h.chat,text,identity,contextUnits=budget,cancelled=stopped,onText={}) { messages,_,emit -> generate(messages,emit) }
    val h = newHistory()
    h.seed("x".repeat(250), "user"); h.seed("Old reply", "character")
    run(h) { messages, emit ->
        verify(messages.map { it.role } == listOf("system","user"))
        verify(messages.first().content == "Core identity")
        verify(messages.last().content == "Newest")
        emit("Answer"); "Answer"
    }
    verify(h.events.size == 4 && h.events.first().text.length == 250)
    val h2 = newHistory()
    h2.seed("Earlier", "user"); h2.seed("Earlier reply", "character")
    run(h2) { messages, emit ->
        verify(messages.map { it.role } == listOf("system","user","assistant","user"))
        emit("OK"); "OK"
    }
    val h3 = newHistory()
    for (invalid in listOf("", "bad\u0000identity", "\uD800", "\uDC00", "x".repeat(8001))) {
        verify(runCatching { run(h3, identity=invalid) { _, _ -> error("generation should not start") } }.exceptionOrNull() is IllegalArgumentException)
    }
    verify(h3.writes == 0)
    run(h3, identity="Привет, Mandy 🌙") { messages, emit ->
        verify(messages.first().content == "Привет, Mandy 🌙")
        emit("Да"); "Да"
    }
    val h4 = newHistory()
    verify(runCatching { run(h4, text="x".repeat(250)) { _,_ -> error("generation should not start") } }.exceptionOrNull() is IllegalArgumentException)
    verify(h4.writes == 0)
    verify(runCatching { run(h4, stopped={true}) { _,_ -> error("generation should not start") } }.exceptionOrNull() is IOException)
    verify(h4.writes == 0)
    val h5 = newHistory(); val id = uid()
    val saved = run(h5,id=id) { _,emit -> emit("Saved"); "Saved" }
    val retried = run(h5,id=id) { _,_ -> error("retry should not regenerate") }
    verify(saved == retried && h5.writes == 2)
    val h6 = newHistory()
    verify(runCatching { run(h6) { _,emit -> emit("preview"); "different" } }.exceptionOrNull() is IllegalArgumentException)
    verify(h6.events.size == 1 && h6.events.single().role == "user")
    println("PASS: $count assertions on exact committed NativeConversation.kt with in-memory history and fake inference")
}
