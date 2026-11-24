package dev.caicompanion.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("mandy-speech-models")
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(action: () -> Unit) = verify(runCatching(action).isFailure)
    try {
        val lib = NativeModelLibrary(root, 1024, NativeModelLibrary.Format.WHISPER)
        val text = NativeModelLibrary(root, 1024)
        val bytes = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN).putInt(0x67676d6c)
            .apply { repeat(10) { putInt(32) }; putInt(0) }.array()
        val m = lib.import(bytes.inputStream(), 128)
        verify(m.path.fileName.toString() == m.sha256 + ".bin")
        verify(m.bytes == 128L && lib.verify(m.sha256) == m)
        verify(lib.list() == listOf(m) && text.list().isEmpty())
        verify(lib.import(bytes.inputStream(), expectedSha = m.sha256) == m)
        bad { text.import(bytes.inputStream()) }
        val gguf = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN).put(byteArrayOf(71,71,85,70)).putInt(3).putLong(1).putLong(0).array()
        bad { lib.import(gguf.inputStream()) }
        bad { lib.import(bytes.copyOf(48).inputStream()) }
        bad { lib.import(bytes.copyOf(47).inputStream()) }
        bad { lib.import(bytes.copyOf(1025).inputStream()) }
        bad { lib.import(bytes.inputStream(), expectedBytes = 129) }
        bad { lib.import(bytes.inputStream(), expectedSha = "0".repeat(64)) }
        bad { lib.import(bytes.inputStream(), cancelled = { true }) }
        bad { lib.import(bytes.copyOf().also { it[4] = 0 }.inputStream()) }
        bad { lib.import(bytes.copyOf().also { it[12] = 31 }.inputStream()) }
        bad { NativeModelLibrary(root, 3L * 1024 * 1024 * 1024, NativeModelLibrary.Format.WHISPER) }
        verify(Files.list(root).use { it.count() } == 1L)
        Files.write(m.path, bytes.copyOf().also { it[127] = 1 })
        bad { lib.verify(m.sha256) }
        bad { lib.import(bytes.inputStream()) }
        verify(Files.list(root).use { it.count() } == 1L)
        println("SpeechModelLibrary: $checks checks passed")
    } finally { root.toFile().deleteRecursively() }
}
