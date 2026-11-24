package dev.caicompanion.app

import java.io.IOException
import java.io.InputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.LinkOption
import java.security.MessageDigest

/** Explicit local model import. A digest identifies bytes; it does not establish publisher trust. */
class NativeModelLibrary(private val root: Path, private val maxBytes: Long = 8L * 1024 * 1024 * 1024,
                         private val format: Format = Format.GGUF) {
    enum class Format(val suffix: String, val headerBytes: Int, val ceiling: Long) {
        GGUF(".gguf", 24, 8L * 1024 * 1024 * 1024),
        WHISPER(".bin", 48, 2L * 1024 * 1024 * 1024)
    }
    private val headerBytes = format.headerBytes
    data class Model(val sha256: String, val bytes: Long, val path: Path)
    init { require(maxBytes > headerBytes && maxBytes <= format.ceiling); Files.createDirectories(root) }
    private fun path(sha: String): Path {
        require(sha.matches(Regex("[0-9a-f]{64}")))
        return root.resolve(sha + format.suffix)
    }
    private fun checkHeader(bytes: ByteArray) {
        if (format == Format.WHISPER) {
            val b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            require(bytes.size == headerBytes && b.getInt(0) == 0x67676d6c) { "Not a Whisper GGML file" }
            require((1..10).all { b.getInt(it * 4) > 0 } && b.getInt(44) >= 0 &&
                b.getInt(12) == b.getInt(28)) { "Invalid Whisper header" }
            return
        }
        require(bytes.size == 24 && bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(71, 71, 85, 70))) { "Not a GGUF file" }
        val b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        require(b.getInt(4) in 2..3 && b.getLong(8) > 0 && b.getLong(16) >= 0) { "Invalid GGUF header" }
    }
    private fun inspect(input: InputStream, output: FileOutputStream?, expectedBytes: Long?, cancelled: () -> Boolean, progress: (Long) -> Unit): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val header = ByteArray(headerBytes)
        var headerSize = 0
        var total = 0L
        val buffer = ByteArray(128 * 1024)
        while (true) {
            if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Model operation cancelled")
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) throw IOException("Model stream made no progress")
            if (n.toLong() > maxBytes - total || (expectedBytes != null && n.toLong() > expectedBytes - total)) throw IOException("Model size exceeded")
            if (headerSize < headerBytes) {
                val copy = minOf(n, headerBytes - headerSize)
                buffer.copyInto(header, headerSize, 0, copy)
                headerSize += copy
                if (headerSize == headerBytes) checkHeader(header)
            }
            digest.update(buffer, 0, n)
            output?.write(buffer, 0, n)
            total += n
            progress(total)
        }
        if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Model operation cancelled")
        require(headerSize == headerBytes && total > headerBytes && (expectedBytes == null || total == expectedBytes)) { "Truncated model" }
        return digest.digest().joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 255) } to total
    }
    /** Caller owns input; all disk I/O must run on an explicitly requested worker. */
    @Synchronized fun import(input: InputStream, expectedBytes: Long? = null, expectedSha: String? = null,
                             cancelled: () -> Boolean = { false }, progress: (Long) -> Unit = {}): Model {
        require(expectedBytes == null || expectedBytes in (headerBytes + 1L)..maxBytes)
        if (expectedSha != null) path(expectedSha)
        if (expectedBytes != null && Files.getFileStore(root).usableSpace < expectedBytes) throw IOException("Insufficient storage")
        val temp = Files.createTempFile(root, ".model-", ".part")
        try {
            val (sha, size) = FileOutputStream(temp.toFile()).use { stream ->
                inspect(input, stream, expectedBytes, cancelled, progress).also { stream.fd.sync() }
            }
            require(expectedSha == null || expectedSha == sha) { "Model digest mismatch" }
            val target = path(sha)
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return verify(sha, cancelled).also { require(it.bytes == size) }
            }
            if (cancelled()) throw IOException("Model operation cancelled")
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
            return Model(sha, size, target)
        } finally { Files.deleteIfExists(temp) }
    }
    @Synchronized fun verify(sha: String, cancelled: () -> Boolean = { false }): Model {
        val target = path(sha)
        require(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) { "Model unavailable" }
        val size = Files.size(target)
        require(size in (headerBytes + 1L)..maxBytes)
        val (actual, count) = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS).use { inspect(it, null, size, cancelled) {} }
        require(actual == sha) { "Model digest mismatch" }
        return Model(sha, count, target)
    }
    /** Inventory only; verification is required before opening any entry in an engine. */
    @Synchronized fun list(): List<Model> = Files.newDirectoryStream(root, "*" + format.suffix).use { entries ->
        entries.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) && it.fileName.toString().removeSuffix(format.suffix).matches(Regex("[0-9a-f]{64}")) }
            .map { Model(it.fileName.toString().removeSuffix(format.suffix), Files.size(it), it) }.sortedBy { it.sha256 }
    }
}
