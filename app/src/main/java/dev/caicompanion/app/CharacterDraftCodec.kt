package dev.caicompanion.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Private, versioned representation; preserves all editable fields and source metadata. */
object CharacterDraftCodec {
    const val MAX_FILE_BYTES = 8 * 1024 * 1024L
    private const val MAGIC = 0x43434452 // CCDR
    private const val VERSION = 1
    data class Header(val name: String)

    private class Reader(val stream: DataInputStream) {
        private var chars = 0L
        fun text(max: Int): String {
            val length = stream.readInt()
            require(length in 0..max * 3)
            val bytes = ByteArray(length)
            stream.readFully(bytes)
            val value = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            chars += value.length
            require(value.length <= max && chars <= LorebookPolicy.MAX_CHARS)
            return value
        }
        fun header(): Header {
            require(stream.readInt() == MAGIC && stream.readInt() == VERSION)
            return Header(text(512))
        }
        fun list(max: Int): List<String> {
            val count = stream.readInt()
            require(count in 0..CharacterDraftPolicy.MAX_LIST)
            return List(count) { text(max) }
        }
    }

    fun header(input: InputStream): Header = Reader(DataInputStream(input)).header()

    fun read(input: InputStream): CharacterDraftPolicy.Draft {
        val r = Reader(DataInputStream(input))
        val name = r.header().name
        val max = CharacterDraftPolicy.MAX_FIELD
        val draft = CharacterDraftPolicy.Draft(name, r.text(max), r.text(max), r.text(max),
            r.text(max), r.list(max), r.list(512), r.text(max), when (r.stream.readUnsignedByte()) {
                0 -> false
                1 -> true
                else -> error("Invalid flags")
            })
        require(r.stream.read() == -1 && CharacterDraftPolicy.valid(draft))
        return draft
    }

    fun write(draft: CharacterDraftPolicy.Draft, output: OutputStream) {
        require(CharacterDraftPolicy.valid(draft))
        val stream = DataOutputStream(output)
        fun text(value: String) {
            val bytes = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
            stream.writeInt(bytes.remaining())
            val copy = ByteArray(bytes.remaining())
            bytes.get(copy)
            stream.write(copy)
        }
        fun list(values: List<String>) {
            stream.writeInt(values.size)
            values.forEach(::text)
        }
        stream.writeInt(MAGIC)
        stream.writeInt(VERSION)
        for (field in CharacterDraftPolicy.Field.entries) text(draft.value(field))
        list(draft.alternatives)
        list(draft.tags)
        text(draft.sourceNotes)
        stream.writeByte(if (draft.conversionLoss) 1 else 0)
        stream.flush()
    }
}
