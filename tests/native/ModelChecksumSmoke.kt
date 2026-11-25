package dev.caicompanion.app

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest

fun main() {
    val root = Files.createTempDirectory("mandy-model-check-")
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    try {
        val lib = NativeModelLibrary(root, 1024 * 1024L)
        // Header-only synthetic fixtures, not actual inference models.
        val bytes = ByteBuffer.allocate(270000).order(ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf(71, 71, 85, 70)).putInt(3).putLong(1).putLong(0).array()
        val digest = sha(bytes)
        val progress = mutableListOf<Long>()
        val imported = lib.import(ByteArrayInputStream(bytes), bytes.size.toLong(), digest, progress = { progress.add(it) })
        verify(imported.sha256 == digest && imported.bytes == bytes.size.toLong())
        verify(progress.last() == bytes.size.toLong() && progress.zipWithNext().all { (a,b) -> a < b })
        verify(Files.readAllBytes(imported.path).contentEquals(bytes))
        verify(lib.verify(digest) == imported)
        verify(lib.import(ByteArrayInputStream(bytes), expectedSha = digest) == imported)
        verify(lib.list().size == 1)
        verify(runCatching { lib.import(ByteArrayInputStream(bytes), expectedSha = "0".repeat(64)) }.isFailure)
        verify(runCatching { lib.import(ByteArrayInputStream(bytes), expectedSha = "a".repeat(65)) }.isFailure)
        verify(runCatching { lib.import(ByteArrayInputStream(bytes), expectedBytes = bytes.size.toLong() - 1) }.isFailure)
        verify(runCatching { lib.import(ByteArrayInputStream(bytes), expectedBytes = bytes.size.toLong() + 1) }.isFailure)
        var stop = false
        verify(runCatching { lib.import(ByteArrayInputStream(bytes), cancelled = { stop }, progress = { stop = true }) }.isFailure)
        verify(Files.list(root).use { it.count() } == 1L)
        verify(lib.verify(digest) == imported)
        Files.write(imported.path, bytes.copyOf().also { it[it.lastIndex] = 1 })
        verify(runCatching { lib.verify(digest) }.isFailure)
        println("PASS: $checks model checksum/import assertions")
    } finally { root.toFile().deleteRecursively() }
}
