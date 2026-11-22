package dev.caicompanion.app

import java.io.Reader

/** Limits decoded characters during reading: protects JSON imports from huge or malformed files. */
object BoundedText {
    fun read(reader: Reader, maxChars: Int): String? {
        require(maxChars > 0)
        val result = StringBuilder(minOf(maxChars, 8192))
        val buffer = CharArray(4096)
        while (true) {
            val count = reader.read(buffer, 0, minOf(buffer.size, maxChars - result.length + 1))
            if (count < 0) return result.toString()
            if (count == 0) return null
            if (result.length + count > maxChars) return null
            result.append(buffer, 0, count)
        }
    }
}
