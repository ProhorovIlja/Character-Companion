package dev.caicompanion.app

import java.nio.file.Files
import java.security.MessageDigest

fun main() {
    val root = Files.createTempDirectory("mandy-whisper")
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    try {
        val bytes = byteArrayOf(1, 2, 3)
        val path = Files.write(root.resolve("model.bin"), bytes)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val pcm = shortArrayOf(-32768, 32767)
        fun run(audio: ShortArray = pcm, lang: MandyLanguage = MandyLanguage.ENGLISH, threads: Int = 4,
                cancelled: () -> Boolean = { false }) = EmbeddedWhisper.transcribe(path, sha, audio, lang, threads, cancelled)
        verify(run() == "Hello 🌙")
        verify(run(lang = MandyLanguage.RUSSIAN, threads = 2) == "Привет 🌙")
        verify(pcm.contentEquals(shortArrayOf(-32768, 32767)))
        bad { run(threads = 0) }
        bad { run(threads = 9) }
        bad { run(shortArrayOf()) }
        bad { run(ShortArray(2880001)) }
        bad { run(cancelled = { true }) }
        bad { EmbeddedWhisper.transcribe(path, "0".repeat(64), pcm, MandyLanguage.ENGLISH) }
        bad { EmbeddedWhisper.transcribe(path, "x", pcm, MandyLanguage.ENGLISH) }
        val en = Files.write(root.resolve("english-only.bin"), bytes)
        bad { EmbeddedWhisper.transcribe(en, sha, pcm, MandyLanguage.ENGLISH) }
        for (n in 1..5) if (n != 2) {
            bad { run(pcm.copyOf(n)) }
            verify(run() == "Hello 🌙")
        }
        Files.write(path, byteArrayOf(4,5,6))
        bad { run() }
        Files.delete(path)
        bad { run() }
        Files.write(path, bytes)
        var calls = 0
        bad { run(cancelled = { ++calls >= 5 }) }
        verify(run() == "Hello 🌙")
        println("EmbeddedWhisper: $checks checks passed (native fixture only)")
    } finally { root.toFile().deleteRecursively() }
}
