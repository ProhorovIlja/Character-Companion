package dev.caicompanion.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.random.Random

/** Free local roleplay aids. No account, renderer or network access. */
object RoleplayToolkit {
    const val MAX_CHARS = 16_000
    const val MAX_BYTES = MAX_CHARS * 3
    private const val MAGIC = 0x43435250 // CCRP
    private const val VERSION = 1
    enum class Slot(val key: String, val wireId: Int) {
        PERSONA("persona", 1), SCENE("scene", 2), MEMORY("memory", 3), PROMPT("prompt", 4)
    }

    fun valid(text: String): Boolean = text.length <= MAX_CHARS &&
        text.none { it.isISOControl() && it !in "\t\r\n" } && Charsets.UTF_8.newEncoder().canEncode(text)

    fun write(slot: Slot, text: String, output: OutputStream) {
        require(valid(text))
        val bytes = text.toByteArray(Charsets.UTF_8)
        val stream = DataOutputStream(output)
        stream.writeInt(MAGIC)
        stream.writeInt(VERSION)
        stream.writeInt(slot.wireId)
        stream.writeInt(bytes.size)
        stream.write(bytes)
        stream.flush()
    }

    fun read(slot: Slot, input: InputStream): String {
        val stream = DataInputStream(input)
        require(stream.readInt() == MAGIC && stream.readInt() == VERSION && stream.readInt() == slot.wireId)
        val count = stream.readInt()
        require(count in 0..MAX_BYTES)
        val bytes = ByteArray(count)
        stream.readFully(bytes)
        require(stream.read() == -1)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        require(valid(text))
        return text
    }

    val diceSides = listOf(4, 6, 8, 10, 12, 20, 100)
    fun roll(sides: Int, count: Int, random: Random = Random.Default): List<Int> {
        require(sides in diceSides && count in 1..10)
        return List(count) { random.nextInt(1, sides + 1) }
    }
}
