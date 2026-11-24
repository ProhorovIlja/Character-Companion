package dev.caicompanion.app

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

fun main(args: Array<String>) {
    fun id(n: Long) = UUID(0, n).toString()
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    val e = NativeConversation.Event(id(1), id(2), id(3), id(4), 1, "user", "text", "Привет 🌙 \"quoted\" \\ \n\t\r")
    val next = e.copy(id = id(5), clock = 2, role = "character", mode = "call", text = "Hello")
    val data = HistoryWire.encode(listOf(next, e, e))
    verify(HistoryWire.decode(data) == listOf(e, next))
    verify(HistoryWire.decode(HistoryWire.encode(emptyList())).isEmpty())
    val json = data.toString(Charsets.UTF_8)
    fun reject(value: String) = bad { HistoryWire.decode(value.toByteArray()) }
    reject(json.replace("\"version\":1", "\"version\":1,\"version\":1"))
    reject(json.replace("\"clock\":1", "\"clock\":1,\"clock\":1"))
    reject(json.replace("\"clock\":1", "\"clock\":1.0"))
    reject(json.replace("\"clock\":1", "\"clock\":true"))
    reject(json.replace("\"clock\":1", "\"clock\":01"))
    reject(json.replace("\"clock\":1", "\"clock\":9007199254740992"))
    reject(json.replace("\"version\":1", "\"version\":2"))
    reject(json.replace("\"role\":\"user\"", "\"role\":\"assistant\""))
    reject(json.replace("Привет", "\\uD800"))
    reject(json.replace("Привет", "\\u0000"))
    reject(json.replace("Привет", "\\u+123"))
    reject(json + "{}")
    reject(json.replace("\"version\":1", "\"version\":1,\"extra\":{}"))
    reject(json.replace("\"mode\":\"text\",", ""))
    bad { HistoryWire.decode(byteArrayOf(0xc0.toByte(), 0xaf.toByte())) }
    bad { HistoryWire.encode(listOf(e, e.copy(text = "conflict"))) }
    bad { HistoryWire.encode(listOf(e, next.copy(character = id(8)))) }
    bad { HistoryWire.encode(List(2049) { e }) }
    bad { HistoryWire.decode(ByteArray(HistoryWire.MAX_BYTES + 1)) }
    bad { HistoryWire.encode(List(100) { e.copy(id = id(100L + it), text = "界".repeat(16000)) }) }
    verify(HistoryWire.decode(HistoryWire.encode(listOf(e.copy(clock = NativeConversation.MAX_CLOCK)))).single().clock == NativeConversation.MAX_CLOCK)
    verify(HistoryWire.decode(json.replace("🌙", "\\uD83C\\uDF19").toByteArray()) == listOf(e, next))
    val key = ByteArray(32) { it.toByte() }
    val account = id(90)
    val cipher = EncryptedHistoryEnvelope.seal(key, account, data)
    verify(HistoryWire.decode(EncryptedHistoryEnvelope.open(key, account, cipher)) == listOf(e, next))
    val python = Files.readAllBytes(Path.of(args[0]))
    verify(HistoryWire.decode(python) == listOf(e, next))
    Files.write(Path.of(args[1]), data)
    println("HistoryWire: $checks checks passed")
}
