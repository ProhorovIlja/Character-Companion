package dev.caicompanion.app

fun main() {
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    val original = "Привет 🌙 こんにちは café\n"
    val bytes = original.toByteArray()
    for (split in 1 until bytes.size) {
        val output = StringBuilder()
        val sink = EmbeddedTextSink({ false }) { output.append(it) }
        sink.onBytes(bytes.copyOfRange(0, split))
        sink.onBytes(bytes.copyOfRange(split, bytes.size))
        verify(sink.finish() == original && output.toString() == original)
    }
    val tiny = EmbeddedTextSink({ false }) {}
    bytes.forEach { tiny.onBytes(byteArrayOf(it)) }
    verify(tiny.finish() == original)
    bad { tiny.finish() }
    bad { tiny.onBytes(byteArrayOf(65)) }
    bad { EmbeddedTextSink({ false }) {}.onBytes(byteArrayOf(0xc0.toByte(), 0xaf.toByte())) }
    bad { EmbeddedTextSink({ false }) {}.apply { onBytes(byteArrayOf(0xf0.toByte())); finish() } }
    bad { EmbeddedTextSink({ false }) {}.apply { onBytes(byteArrayOf(0)); finish() } }
    bad { EmbeddedTextSink({ false }) {}.apply { onBytes(" ".toByteArray()); finish() } }
    bad { EmbeddedTextSink({ false }) {}.onBytes(ByteArray(65537)) }
    bad { EmbeddedTextSink({ false }) {}.onBytes("x".repeat(16001).toByteArray()) }
    bad { EmbeddedTextSink({ true }) {}.onBytes(byteArrayOf(65)) }
    var stop = false
    val cancelled = EmbeddedTextSink({ stop }) {}
    cancelled.onBytes("partial".toByteArray())
    stop = true
    bad { cancelled.finish() }
    bad { EmbeddedTextSink({ false }) { error("UI callback failed") }.onBytes(byteArrayOf(65)) }
    println("EmbeddedTextSink: $checks checks passed")
}
