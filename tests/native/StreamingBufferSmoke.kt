package dev.caicompanion.app

import java.io.IOException
import java.nio.charset.CharacterCodingException
import kotlin.random.Random

fun main() {
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    fun reject(type: Class<out Throwable>, block: () -> Unit) {
        try { block() } catch (e: Throwable) { check(type.isInstance(e)) { "Unexpected failure: $e" }; checks++; return }
        error("Expected failure")
    }
    fun roundTrip(expected: String, chunks: List<ByteArray>) {
        val out = StringBuilder()
        val sink = EmbeddedTextSink({ false }) { out.append(it) }
        chunks.forEach(sink::onBytes)
        verify(sink.finish() == expected && out.toString() == expected)
    }
    val sample = "Hello, Привет, 日本語, café, 🌙🧑‍💻\n\t!"
    val bytes = sample.toByteArray()
    for (i in 1 until bytes.size) roundTrip(sample, listOf(bytes.copyOfRange(0, i), bytes.copyOfRange(i, bytes.size)))
    roundTrip(sample, bytes.map { byteArrayOf(it) })
    val random = Random(2026)
    repeat(100) {
        val expected = sample.repeat(random.nextInt(1, 30))
        val encoded = expected.toByteArray()
        val chunks = mutableListOf<ByteArray>()
        var pos = 0
        while (pos < encoded.size) {
            val end = minOf(encoded.size, pos + random.nextInt(1, 600))
            chunks.add(encoded.copyOfRange(pos, end)); pos = end
        }
        roundTrip(expected, chunks)
    }
    val boundary = "a".repeat(255) + "🌙" + "б".repeat(600) + "x"
    val b = boundary.toByteArray()
    roundTrip(boundary, listOf(b.copyOfRange(0, 256), b.copyOfRange(256, b.size)))
    roundTrip("a".repeat(16000), listOf(ByteArray(16000) { 97 }))
    roundTrip("a".repeat(16000), List(16000) { byteArrayOf(97) })
    reject(IOException::class.java) { EmbeddedTextSink({ false }) {}.onBytes(ByteArray(65536) { 97 }) }
    reject(IllegalArgumentException::class.java) { EmbeddedTextSink({ false }) {}.onBytes(ByteArray(65537)) }
    reject(IllegalArgumentException::class.java) { EmbeddedTextSink({ false }) {}.onBytes(byteArrayOf()) }
    reject(CharacterCodingException::class.java) { EmbeddedTextSink({ false }) {}.onBytes(byteArrayOf(0xc0.toByte(), 0xaf.toByte())) }
    reject(CharacterCodingException::class.java) { EmbeddedTextSink({ false }) {}.apply { onBytes(byteArrayOf(0xf0.toByte(), 0x9f.toByte())); finish() } }
    reject(IOException::class.java) { EmbeddedTextSink({ false }) {}.onBytes(byteArrayOf(0)) }
    reject(IllegalArgumentException::class.java) { EmbeddedTextSink({ false }) {}.finish() }
    var stop = false
    val stopped = EmbeddedTextSink({ stop }) {}
    stopped.onBytes(byteArrayOf(97)); stop = true
    reject(IOException::class.java) { stopped.onBytes(byteArrayOf(98)) }
    reject(IOException::class.java) { stopped.finish() }
    val done = EmbeddedTextSink({ false }) {}
    done.onBytes(byteArrayOf(97)); verify(done.finish() == "a")
    reject(IllegalStateException::class.java) { done.onBytes(byteArrayOf(98)) }
    reject(IllegalStateException::class.java) { done.finish() }
    println("PASS $checks streaming buffer checks")
}
