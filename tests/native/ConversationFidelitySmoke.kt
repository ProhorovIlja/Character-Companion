package dev.caicompanion.app

import java.util.UUID

/** Text transport fidelity, not a test of the model's willingness to answer. */
fun main() {
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    val identity = "Speak naturally in English or Russian. Stay in character; avoid a corporate tone."
    val examples = listOf(
        "What the hell? Can we talk openly about sex and relationships?",
        "Чёрт, давай честно поговорим об интимных отношениях. 🌙",
        "I want a direct answer, not a canned office response."
    )
    for (mode in listOf("text", "dictation", "call")) {
        for (message in examples) {
            val rows = mutableListOf<NativeConversation.Event>()
            val history = object : NativeConversation.History {
                override fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String) =
                    NativeConversation.Event(id, device, character, chat, 1, "user", mode, text).also(rows::add)
                override fun find(id: String) = rows.firstOrNull { it.id == id }
                override fun recent(character: String, chat: String, limit: Int) = rows.asReversed().take(limit)
                override fun commitReply(user: NativeConversation.Event, id: String, text: String) =
                    user.copy(id = id, role = "character", clock = 2, text = text).also(rows::add)
            }
            val ids = List(4) { UUID.randomUUID().toString() }
            val preview = StringBuilder()
            val result = NativeConversation.send(history, ids[0], ids[1], ids[2], ids[3], message,
                identity, mode, onText = { preview.append(it) }) { prompt, cancelled, emit ->
                verify(prompt == listOf(NativeConversation.Message("system", identity), NativeConversation.Message("user", message)))
                val sink = EmbeddedTextSink(cancelled, emit)
                message.toByteArray(Charsets.UTF_8).forEach { sink.onBytes(byteArrayOf(it)) }
                sink.finish()
            }
            verify(preview.toString() == message)
            verify(rows.first().text == message && rows.first().mode == mode)
            verify(result.text == message && result.mode == mode && rows.last() == result)
            verify(SpokenText.chunks(result.text, 12).joinToString("") == message)
        }
    }
    verify(runCatching { NativeConversation.validateText("bad\u0000text") }.isFailure)
    verify(runCatching { NativeConversation.validateText("x".repeat(16001)) }.isFailure)
    println("PASS $checks conversation-fidelity assertions (simulated text provider)")
}
