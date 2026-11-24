package dev.caicompanion.app

import java.io.Reader

/** Limits decoded characters during reading: protects JSON imports from huge or malformed files. */
object BoundedText {
    /** Cancellation is cooperative at read boundaries; the caller owns transport timeouts. */
    fun read(reader: Reader, maxChars: Int, cancelled: () -> Boolean = { false }): String? {
        require(maxChars > 0)
        val result = StringBuilder(minOf(maxChars, 8192))
        val buffer = CharArray(4096)
        while (!cancelled()) {
            val remaining = maxChars - result.length
            val requested = if (remaining >= buffer.size) buffer.size else remaining + 1
            val count = reader.read(buffer, 0, requested)
            if (cancelled()) return null
            if (count == -1) return result.toString()
            if (count <= 0 || count > requested || count > remaining) return null
            result.append(buffer, 0, count)
        }
        return null
    }
}
