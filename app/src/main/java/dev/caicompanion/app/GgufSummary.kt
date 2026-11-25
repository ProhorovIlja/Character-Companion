package dev.caicompanion.app

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Bounded, informational GGUF v2/v3 little-endian metadata preview. Never loads weights. */
object GgufSummary {
    const val MAX_BYTES = 256 * 1024
    data class Info(val version: Int, val name: String?, val architecture: String?, val kind: String?, val fileType: Long?)
    private class Reader(input: InputStream) {
        private val stream = DataInputStream(input)
        private var remaining = MAX_BYTES
        private val scratch = ByteArray(4096)
        fun bytes(n: Int): ByteArray {
            require(n in 0..remaining)
            remaining -= n
            return ByteArray(n).also { stream.readFully(it) }
        }
        fun skip(n: Long) {
            require(n in 0..remaining.toLong())
            remaining -= n.toInt()
            var left = n.toInt()
            while (left > 0) {
                val chunk = minOf(left, scratch.size)
                stream.readFully(scratch, 0, chunk)
                left -= chunk
            }
        }
        fun u32(): Long = ByteBuffer.wrap(bytes(4)).order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        fun count(): Long = ByteBuffer.wrap(bytes(8)).order(java.nio.ByteOrder.LITTLE_ENDIAN).long.also { require(it >= 0) }
        fun string(limit: Int): String {
            val n = count()
            require(n <= limit)
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes(n.toInt()))).toString()
        }
        fun skipValue(type: Long) {
            val width = width(type)
            when {
                width != null -> skip(width)
                type == 8L -> skip(count())
                type == 9L -> {
                    val element = u32()
                    val count = count()
                    val w = width(element)
                    if (w != null) { require(count <= remaining / w); skip(count * w) }
                    else if (element == 8L) { require(count <= 8192); repeat(count.toInt()) { skip(this.count()) } }
                    else throw IllegalArgumentException("Unsupported array type")
                }
                else -> throw IllegalArgumentException("Unknown metadata type")
            }
        }
        private fun width(type: Long): Long? = when (type) {
            0L, 1L, 7L -> 1L
            2L, 3L -> 2L
            4L, 5L, 6L -> 4L
            10L, 11L, 12L -> 8L
            else -> null
        }
    }
    private fun label(value: String): String? = value.takeIf {
        it.isNotBlank() && it.length <= 160 && it.none { c -> c.isISOControl() || Character.getType(c) == Character.FORMAT.toInt() }
    }
    fun read(input: InputStream): Info? {
        val reader = Reader(input)
        return try {
            require(reader.bytes(4).contentEquals(byteArrayOf(71, 71, 85, 70)))
            val version = reader.u32().toInt()
            require(version in 2..3 && reader.count() > 0)
            val count = reader.count()
            var name: String? = null
            var arch: String? = null
            var kind: String? = null
            var fileType: Long? = null
            val seen = mutableSetOf<String>()
            repeat(minOf(count, 1024L).toInt()) {
                val key = reader.string(1024)
                require(seen.add(key))
                val type = reader.u32()
                when {
                    key == "general.name" && type == 8L -> name = label(reader.string(1024))
                    key == "general.architecture" && type == 8L -> arch = label(reader.string(1024))
                    key == "general.type" && type == 8L -> kind = label(reader.string(1024))
                    key == "general.file_type" && type == 4L -> fileType = reader.u32()
                    else -> reader.skipValue(type)
                }
                if (name != null && arch != null && fileType != null)
                    return Info(version, name, arch, kind, fileType)
            }
            Info(version, name, arch, kind, fileType)
        } catch (_: IOException) { null }
          catch (_: IllegalArgumentException) { null }
    }
}
