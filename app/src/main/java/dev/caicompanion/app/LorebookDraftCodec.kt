package dev.caicompanion.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Versioned private draft format, independent of lossy interchange formats. */
object LorebookDraftCodec {
    const val MAX_FILE_BYTES = 8 * 1024 * 1024L
    private const val MAGIC = 0x43434C42 // CCLB
    private const val VERSION = 1
    data class Header(val name: String, val entries: Int)

    private fun encode(text: String): ByteArray {
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text))
        return ByteArray(encoded.remaining()).also { encoded.get(it) }
    }

    private class Reader(val input: DataInputStream) {
        private var chars = 0L
        fun text(max: Int): String {
            val length = input.readInt()
            require(length in 0..max * 3)
            val bytes = ByteArray(length)
            input.readFully(bytes)
            val value = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            chars += value.length
            require(value.length <= max && chars <= LorebookPolicy.MAX_CHARS)
            return value
        }
        fun header(): Header {
            require(input.readInt() == MAGIC && input.readInt() == VERSION)
            val name = text(512)
            val count = input.readInt()
            require(count in 1..LorebookPolicy.MAX_ENTRIES)
            return Header(name, count)
        }
    }

    fun header(input: InputStream): Header = Reader(DataInputStream(input)).header()

    fun read(input: InputStream): LorebookPolicy.Book {
        val reader = Reader(DataInputStream(input))
        val head = reader.header()
        val description = reader.text(100_000)
        val loss = reader.input.readUnsignedByte()
        val skipped = reader.input.readInt()
        require(loss in 0..1 && skipped in 0..LorebookPolicy.MAX_ENTRIES)
        val entries = List(head.entries) {
            val title = reader.text(512)
            val count = reader.input.readInt()
            require(count in 0..LorebookPolicy.MAX_KEYS)
            val keys = List(count) { reader.text(512) }
            LorebookPolicy.Entry(title, keys, reader.text(100_000))
        }
        require(reader.input.read() == -1)
        return LorebookPolicy.Book(head.name, description, entries, loss == 1, skipped)
            .also { require(LorebookPolicy.valid(it)) }
    }

    fun write(book: LorebookPolicy.Book, output: OutputStream) {
        require(LorebookPolicy.valid(book) && book.skippedDisabled in 0..LorebookPolicy.MAX_ENTRIES)
        val stream = DataOutputStream(output)
        fun text(value: String) {
            val bytes = encode(value)
            stream.writeInt(bytes.size)
            stream.write(bytes)
        }
        stream.writeInt(MAGIC)
        stream.writeInt(VERSION)
        text(book.name)
        stream.writeInt(book.entries.size)
        text(book.description)
        stream.writeByte(if (book.conversionLoss) 1 else 0)
        stream.writeInt(book.skippedDisabled)
        for (entry in book.entries) {
            text(entry.title)
            stream.writeInt(entry.keys.size)
            entry.keys.forEach(::text)
            text(entry.content)
        }
        stream.flush()
    }
}
