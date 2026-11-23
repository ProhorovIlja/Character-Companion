package dev.caicompanion.app

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Pure, platform-independent checks for user-initiated Gecko file transfers. */
object GeckoTransferPolicy {
    const val MAX_SELECTED_FILES = 8
    const val MAX_DOWNLOAD_BYTES: Long = 256L * 1024L * 1024L
    private val mime = Regex("^[a-z0-9][a-z0-9!#.+_-]{0,60}/(?:[a-z0-9][a-z0-9!#.+_-]{0,60}|\\*)$")

    fun isDownloadSourceAllowed(url: String?, secure: Boolean, statusCode: Int): Boolean =
        secure && statusCode in 200..299 && !url.isNullOrBlank() &&
            url.length <= 8192 && UrlPolicy.isTrustedMediaOrigin(url)

    /** Keep signed URL queries, server headers and login credentials out of SAF filenames. */
    fun filename(url: String?): String {
        val segment = try {
            URI(url ?: "").rawPath.orEmpty().substringAfterLast('/')
        } catch (_: Exception) { "" }
        val decoded = try {
            URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) { "" }
        val sanitized = SafeFilename.from(decoded)
        return if (sanitized == "download" || sanitized == "_" || sanitized.isBlank())
            "character-companion-download.bin" else sanitized
    }

    fun mimeType(raw: String?): String {
        val part = raw.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        return part.takeIf { it.length <= 125 && mime.matches(it) } ?: "application/octet-stream"
    }

    fun acceptedMimeTypes(raw: Array<String>?): List<String> =
        raw.orEmpty().asSequence().take(32).map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.length <= 125 && mime.matches(it) }.distinct().take(12)
            .toList().ifEmpty { listOf("*/*") }

    /** Only Android's document picker content URIs may be handed to Gecko as uploads. */
    fun isSelectedDocumentUri(raw: String?): Boolean = try {
        val parsed = URI(raw ?: "")
        parsed.scheme.equals("content", ignoreCase = true) &&
            !parsed.authority.isNullOrBlank() && parsed.userInfo == null &&
            parsed.rawFragment == null && (raw?.length ?: 0) <= 8192
    } catch (_: Exception) { false }

    /** Bounded, streaming copy. No buffering of remote files in RAM. */
    @Throws(IOException::class)
    fun copy(input: InputStream, output: OutputStream, maxBytes: Long = MAX_DOWNLOAD_BYTES): Long {
        require(maxBytes in 1..MAX_DOWNLOAD_BYTES)
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        var emptyReads = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) {
                // Malformed provider streams must not spin forever at 100% CPU.
                // A blocking InputStream should return data or EOF for a nonempty buffer.
                if (++emptyReads >= 8) throw IOException("download_stream_stalled")
                continue
            }
            emptyReads = 0
            if (n.toLong() > maxBytes - total) throw IOException("download_exceeds_limit")
            output.write(buffer, 0, n)
            total += n
        }
        output.flush()
        return total
    }
}
