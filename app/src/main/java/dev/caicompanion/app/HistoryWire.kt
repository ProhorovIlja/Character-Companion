package dev.caicompanion.app

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Strict bounded v1 JSON history, independent of Android JSON coercion rules. */
object HistoryWire {
    const val MAX_EVENTS = 2048
    const val MAX_BYTES = 4 * 1024 * 1024
    fun merge(events: List<NativeConversation.Event>): List<NativeConversation.Event> {
        require(events.size <= MAX_EVENTS)
        val ids = mutableMapOf<String, NativeConversation.Event>()
        val owners = mutableMapOf<String, String>()
        for (event in events) {
            require(ids.putIfAbsent(event.id, event).let { it == null || it == event }) { "Conflicting message ID" }
            require(owners.putIfAbsent(event.chat, event.character).let { it == null || it == event.character }) { "Conflicting chat owner" }
        }
        return ids.values.sortedWith(compareBy<NativeConversation.Event> { it.clock }.thenBy { it.id })
    }
    private fun quote(value: String): String = buildString {
        append('"')
        for (c in value) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n")
            '\r' -> append("\\r"); '\t' -> append("\\t"); else -> append(c)
        }
        append('"')
    }
    fun encode(events: List<NativeConversation.Event>): ByteArray {
        val out = StringBuilder("{\"version\":1,\"events\":[")
        for ((index, e) in merge(events).withIndex()) {
            if (index > 0) out.append(',')
            out.append("{\"id\":").append(quote(e.id)).append(",\"device\":").append(quote(e.device))
                .append(",\"character\":").append(quote(e.character)).append(",\"chat\":").append(quote(e.chat))
                .append(",\"clock\":").append(e.clock).append(",\"role\":").append(quote(e.role))
                .append(",\"mode\":").append(quote(e.mode)).append(",\"text\":").append(quote(e.text)).append('}')
            require(out.length <= MAX_BYTES)
        }
        return out.append("]}").toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    }
    fun decode(bytes: ByteArray): List<NativeConversation.Event> {
        require(bytes.size in 1..MAX_BYTES)
        val source = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        return Parser(source).parse()
    }
    private class Parser(private val s: String) {
        private var i = 0
        private fun ws() { while (i < s.length && s[i] in " \t\r\n") i++ }
        private fun take(c: Char): Boolean { ws(); if (i < s.length && s[i] == c) { i++; return true }; return false }
        private fun need(c: Char) { require(take(c)) { "Invalid history JSON" } }
        private fun string(): String {
            need('"')
            val out = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                if (c == '"') return out.toString()
                if (c == '\\') {
                    require(i < s.length)
                    out.append(when (s[i++]) {
                        '"' -> '"'; '\\' -> '\\'; '/' -> '/'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; 'b' -> '\b'; 'f' -> '\u000c'
                        'u' -> {
                            require(i + 4 <= s.length)
                            val hex = s.substring(i, i + 4)
                            require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                            i += 4; hex.toInt(16).toChar()
                        }
                        else -> error("Invalid JSON escape")
                    })
                } else { require(c.code >= 32); out.append(c) }
                require(out.length <= 16000)
            }
            error("Unterminated JSON string")
        }
        private fun number(): Long {
            ws(); val start = i
            require(i < s.length && s[i] in '1'..'9')
            while (i < s.length && s[i] in '0'..'9') { i++; require(i - start <= 16) }
            return s.substring(start, i).toLong()
        }
        private fun event(): NativeConversation.Event {
            need('{'); val fields = mutableMapOf<String, String>(); var clock: Long? = null
            do {
                val name = string(); need(':')
                when (name) {
                    "clock" -> { require(clock == null); clock = number() }
                    "id", "device", "character", "chat", "role", "mode", "text" -> {
                        require(name !in fields); fields[name] = string()
                    }
                    else -> error("Unknown event field")
                }
            } while (take(','))
            need('}'); require(fields.size == 7 && clock != null)
            return NativeConversation.Event(fields.getValue("id"), fields.getValue("device"), fields.getValue("character"), fields.getValue("chat"), clock,
                fields.getValue("role"), fields.getValue("mode"), fields.getValue("text"))
        }
        private fun array(): List<NativeConversation.Event> {
            need('['); val rows = mutableListOf<NativeConversation.Event>()
            if (!take(']')) {
                do { require(rows.size < MAX_EVENTS); rows.add(event()) } while (take(','))
                need(']')
            }
            return rows
        }
        fun parse(): List<NativeConversation.Event> {
            need('{'); var version: Long? = null; var events: List<NativeConversation.Event>? = null
            do {
                val name = string(); need(':')
                when (name) {
                    "version" -> { require(version == null); version = number() }
                    "events" -> { require(events == null); events = array() }
                    else -> error("Unknown history field")
                }
            } while (take(','))
            need('}'); ws(); require(i == s.length && version == 1L && events != null)
            return merge(events)
        }
    }
}
