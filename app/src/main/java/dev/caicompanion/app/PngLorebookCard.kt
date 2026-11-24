package dev.caicompanion.app

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.zip.CRC32

/** Extract only standard PNG character-card text metadata; never decode image pixels. */
object PngLorebookCard {
    private val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private const val MAX_PNG = 32L * 1024 * 1024
    private const val MAX_CHUNKS = 4096
    private const val MAX_BASE64 = 8_000_000

    fun isPng(input: BufferedInputStream): Boolean {
        input.mark(signature.size)
        return try {
            val bytes = ByteArray(signature.size)
            DataInputStream(input).readFully(bytes)
            bytes.contentEquals(signature)
        } catch (_: EOFException) { false }
        finally { input.reset() }
    }

    fun extract(input: InputStream, cancelled: () -> Boolean = { false }): String? = try {
        decode(input, cancelled)
    } catch (_: Exception) { null }

    private fun decode(input: InputStream, cancelled: () -> Boolean): String? {
        val stream = DataInputStream(input)
        val magic = ByteArray(8).also(stream::readFully)
        require(magic.contentEquals(signature))
        val buffer = ByteArray(32768)
        var total = 8L
        var v2: ByteArray? = null
        var v3: ByteArray? = null
        var seenV2 = false
        var seenV3 = false
        var imageData = false
        repeat(MAX_CHUNKS) { index ->
            if (cancelled()) return null
            val length = stream.readInt()
            require(length >= 0 && total + 12L + length <= MAX_PNG)
            total += 12L + length
            val type = ByteArray(4).also(stream::readFully)
            require(type.all { it.toInt() in 65..90 || it.toInt() in 97..122 })
            val name = String(type, Charsets.US_ASCII)
            require(if (index == 0) name == "IHDR" && length == 13 else name != "IHDR")
            val crc = CRC32().apply { update(type) }
            var remaining = length
            fun read(bytes: ByteArray, size: Int) {
                stream.readFully(bytes, 0, size)
                crc.update(bytes, 0, size)
                remaining -= size
            }
            if (name == "tEXt") {
                val prefix = ByteArray(minOf(80, remaining))
                read(prefix, prefix.size)
                val separator = prefix.indexOf(0)
                val keyword = if (separator > 0) String(prefix, 0, separator, Charsets.ISO_8859_1) else ""
                if (keyword == "chara" || keyword == "ccv3") {
                    require(if (keyword == "chara") !seenV2 else !seenV3)
                    if (keyword == "chara") seenV2 = true else seenV3 = true
                    val prefixLength = prefix.size - separator - 1
                    val payloadLength = remaining + prefixLength
                    require(payloadLength in 1..MAX_BASE64)
                    val payload = ByteArray(payloadLength)
                    prefix.copyInto(payload, 0, separator + 1)
                    var offset = prefixLength
                    while (remaining > 0) {
                        if (cancelled()) return null
                        val count = minOf(buffer.size, remaining)
                        read(buffer, count)
                        buffer.copyInto(payload, offset, 0, count)
                        offset += count
                    }
                    if (keyword == "ccv3") { v3 = payload; v2 = null }
                    else if (!seenV3) v2 = payload
                }
            }
            while (remaining > 0) {
                if (cancelled()) return null
                read(buffer, minOf(buffer.size, remaining))
            }
            require(stream.readInt().toLong() and 0xffffffffL == crc.value)
            if (name == "IDAT") imageData = true
            if (name == "IEND") {
                require(length == 0 && imageData && stream.read() == -1)
                if (cancelled()) return null
                val payload = v3 ?: v2 ?: return null
                val bytes = Base64.getDecoder().decode(payload)
                val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                return text.takeIf { it.length <= LorebookPolicy.MAX_CHARS && it.trimStart().startsWith('{') }
            }
        }
        return null
    }
}
