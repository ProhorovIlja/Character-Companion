package dev.caicompanion.app

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/** Text-only CHARX reader. Never extracts archive paths or executes embedded assets. */
object CharxCard {
    const val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 64L * 1024 * 1024
    const val MAX_ENTRIES = 512
    private const val MAX_CARD_BYTES = LorebookPolicy.MAX_CHARS * 3

    fun isZip(input: BufferedInputStream): Boolean {
        input.mark(4)
        return try { input.read() == 0x50 && input.read() == 0x4b && input.read() == 3 && input.read() == 4 }
        finally { input.reset() }
    }

    private class Limited(input: InputStream, private val cancelled: () -> Boolean) : FilterInputStream(input) {
        private var total = 0L
        private fun checkBudget(n: Int): Int {
            if (n > 0) total += n
            require(total <= MAX_ARCHIVE_BYTES && !cancelled())
            return n
        }
        override fun read(): Int {
            require(!cancelled())
            val n = super.read()
            if (n >= 0) checkBudget(1)
            return n
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            require(!cancelled())
            return checkBudget(super.read(b, off, len))
        }
    }

    fun extract(input: InputStream, cancelled: () -> Boolean = { false }): String? = try {
        ZipInputStream(Limited(input, cancelled)).use { zip ->
            val buffer = ByteArray(32 * 1024)
            var result: String? = null
            var entries = 0
            var total = 0L
            while (true) {
                require(!cancelled())
                val entry = zip.nextEntry ?: break
                require(++entries <= MAX_ENTRIES)
                val card = entry.name == "card.json"
                require(!card || (!entry.isDirectory && result == null))
                if (entry.size >= 0) require(entry.size <= MAX_EXPANDED_BYTES - total)
                if (card && entry.size >= 0) require(entry.size <= MAX_CARD_BYTES)
                val output = if (card) ByteArrayOutputStream() else null
                while (true) {
                    require(!cancelled())
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_EXPANDED_BYTES)
                    if (output != null) {
                        require(output.size() + n <= MAX_CARD_BYTES)
                        output.write(buffer, 0, n)
                    }
                }
                zip.closeEntry()
                if (output != null) {
                    val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(output.toByteArray())).toString().removePrefix("\uFEFF")
                    require(text.length <= LorebookPolicy.MAX_CHARS && text.isNotBlank())
                    result = text
                }
            }
            require(!cancelled())
            result
        }
    } catch (_: Exception) { null }
}
