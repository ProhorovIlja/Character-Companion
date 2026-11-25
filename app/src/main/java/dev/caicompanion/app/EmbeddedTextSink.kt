package dev.caicompanion.app

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Incremental strict UTF-8 decoding; native tokens may split a Unicode code point. */
class EmbeddedTextSink(private val stopped: () -> Boolean, private val emit: (String) -> Unit) {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    // Per-turn scratch buffers: grow only for an unusually large native fragment.
    // Input stays in write mode between callbacks, retaining at most 3 UTF-8 bytes.
    private var input = ByteBuffer.allocate(256)
    private var output = CharBuffer.allocate(258)
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
        val needed = input.position() + bytes.size
        if (needed > input.capacity()) {
            val larger = ByteBuffer.allocate(minOf(65539, maxOf(needed, input.capacity() * 2)))
            input.flip()
            larger.put(input)
            input = larger
        }
        input.put(bytes)
        input.flip()
        if (needed + 2 > output.capacity()) {
            output = CharBuffer.allocate(minOf(65541, maxOf(needed + 2, output.capacity() * 2)))
        }
        output.clear()
        val result = decoder.decode(input, output, end)
        if (result.isError) result.throwException()
        check(result.isUnderflow)
        if (end) {
            val flush = decoder.flush(output)
            if (flush.isError) flush.throwException()
            check(flush.isUnderflow && !input.hasRemaining())
        }
        check(input.remaining() <= 3)
        input.compact()
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
