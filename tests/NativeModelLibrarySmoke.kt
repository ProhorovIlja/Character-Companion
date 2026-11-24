package dev.caicompanion.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest

fun main() {
    val root = Files.createTempDirectory("mandy-model-test")
    var checks = 0
    fun verify(value: Boolean) { check(value); checks++ }
    fun bad(action: () -> Unit) = verify(runCatching(action).isFailure)
    try {
        val lib = NativeModelLibrary(root, 1024)
        val bytes = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN).put(byteArrayOf(71,71,85,70)).putInt(3).putLong(1).putLong(0).array()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        verify(lib.list().isEmpty())
        val m = lib.import(bytes.inputStream(), 128, sha)
        verify(m.sha256 == sha && m.bytes == 128L)
        verify(lib.verify(sha) == m)
        verify(lib.import(bytes.inputStream()) == m)
        verify(NativeModelLibrary(root, 1024).list() == listOf(m))
        bad { lib.import(ByteArray(128).inputStream()) }
        bad { lib.import(bytes.copyOf(20).inputStream()) }
        bad { lib.import(bytes.inputStream(), 129) }
        bad { lib.import(bytes.inputStream(), 127) }
        bad { lib.import(bytes.inputStream(), expectedSha = "0".repeat(64)) }
        bad { lib.import(bytes.inputStream(), cancelled = { true }) }
        bad { lib.import(bytes.copyOf(1025).inputStream()) }
        bad { lib.verify("../escape") }
        verify(Files.list(root).use { it.count() } == 1L)
        Files.write(m.path, bytes.copyOf().also { it[127] = 1 })
        bad { lib.verify(sha) }
        bad { lib.import(bytes.inputStream()) }
        verify(Files.list(root).use { it.count() } == 1L)
        println("NativeModelLibrary: $checks checks passed")
    } finally { root.toFile().deleteRecursively() }
}
