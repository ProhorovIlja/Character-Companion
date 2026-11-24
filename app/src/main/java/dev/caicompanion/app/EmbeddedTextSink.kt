package dev.caicompanion.app

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Incremental strict UTF-8 decoding; native tokens may split a Unicode code point. */
class EmbeddedTextSink(private val stopped: () -> Boolean, private val emit: (String) -> Unit) {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    private var tail = ByteArray(0)
    private val text = StringBuilder()
    private var done = false
    fun isCancelled(): Boolean = stopped() || Thread.currentThread().isInterrupted
    fun onBytes(bytes: ByteArray) {
        check(!done)
        if (isCancelled()) throw IOException("Generation cancelled")
        require(bytes.size in 1..65536)
        decode(bytes, false)
    }
    private fun decode(bytes: ByteArray, end: Boolean) {
        val input = ByteBuffer.wrap(tail + bytes)
        val output = CharBuffer.allocate(input.remaining() + 2)
        val result = decoder.decode(input, output, end)
        if (result.isError) result.throwException()
        check(result.isUnderflow)
        if (end) {
            val flush = decoder.flush(output)
            if (flush.isError) flush.throwException()
            check(flush.isUnderflow && !input.hasRemaining())
        }
        tail = ByteArray(input.remaining()).also { input.get(it) }
        check(tail.size <= 3)
        output.flip()
        val fragment = output.toString()
        if (fragment.length > 16000 - text.length) throw IOException("Reply too large")
        if (fragment.any { it.code < 32 && it !in "\t\r\n" }) throw IOException("Invalid reply character")
        if (fragment.isNotEmpty()) { text.append(fragment); emit(fragment) }
    }
    fun finish(): String {
        check(!done)
        if (isCancelled()) throw IOException("Generation cancelled")
        decode(ByteArray(0), true)
        done = true
        return text.toString().also(NativeConversation::validateText)
    }
}
