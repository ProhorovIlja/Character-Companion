package dev.caicompanion.app

import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.MessageDigest

/** Bounded streaming verification; no Android or network dependencies. */
object UpdateIntegrity {
    private val digestPattern = Regex("^sha256:[0-9a-fA-F]{64}$")
    private const val MAX_BYTES = 536_870_912L

    fun redirectAllowed(raw: String): Boolean {
        if (raw.length > 8192) return false
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host in setOf("release-assets.githubusercontent.com",
            "objects.githubusercontent.com") && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            uri.rawFragment == null && !uri.rawPath.isNullOrEmpty()
    }

    /** The caller must discard output on any failure; never publish a partial file. */
    fun copyVerified(input: InputStream, output: OutputStream, size: Long, digest: String,
                     cancelled: () -> Boolean): Boolean {
        if (size !in 1..MAX_BYTES || !digestPattern.matches(digest)) return false
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (!cancelled()) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0 || total + count > size) return false
            output.write(buffer, 0, count)
            hash.update(buffer, 0, count)
            total += count
        }
        val actual = hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        return !cancelled() && total == size && actual.equals(digest.substringAfter(':'), true)
    }
}
