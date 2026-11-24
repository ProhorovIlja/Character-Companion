package dev.caicompanion.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("mandy-resident")
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    val session = ResidentModel()
    try {
        val library = NativeModelLibrary(root, 1024)
        fun fixture(marker: Byte) = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf(71,71,85,70)).putInt(3).putLong(1).putLong(0).put(marker).array()
        val a = library.import(fixture(1).inputStream())
        val b = library.import(fixture(2).inputStream())
        val messages = listOf(NativeConversation.Message("system", "Mandy"), NativeConversation.Message("user", "normal"))
        fun generate(engine: EmbeddedLlama) = engine.generate(messages, onText = {})
        var admissions = 0
        fun run(sha: String = a.sha256, stop: () -> Boolean = { false }, action: (EmbeddedLlama) -> Unit) =
            session.useModel(library, sha, stop, { admissions++ }, action)
        lateinit var first: EmbeddedLlama
        run { first = it; verify(generate(it) == "Hello 🌙") }
        run { verify(it === first); verify(generate(it) == "Hello 🌙") }
        verify(admissions == 2)
        lateinit var second: EmbeddedLlama
        run(b.sha256) { second = it; verify(it !== first); bad { generate(first) }; verify(generate(it) == "Hello 🌙") }
        session.close()
        bad { generate(second) }
        session.close()
        run { verify(it !== first); first = it }
        bad { session.useModel(library, a.sha256, { false }, { error("admission refused") }) { error("unexpected") } }
        bad { generate(first) }
        run { first = it }
        bad { run { error("generation failed") } }
        bad { generate(first) }
        run { first = it }
        bad { run(stop = { true }) { error("unexpected") } }
        bad { generate(first) }
        run { first = it; bad { session.close() }; bad { run {} }; verify(generate(it) == "Hello 🌙") }
        var cancelled = false
        bad { run(stop = { cancelled }) { cancelled = true } }
        bad { generate(first) }
        run { first = it }
        Files.write(a.path, fixture(3))
        bad { run { error("tampered model reached inference") } }
        bad { generate(first) }
        Files.write(a.path, fixture(1))
        run { first = it; verify(generate(it) == "Hello 🌙") }
        Files.delete(a.path)
        bad { run {} }
        bad { generate(first) }
        run(b.sha256) { verify(generate(it) == "Hello 🌙") }
        println("ResidentModel: $checks checks passed (JNI test double)")
    } finally { session.close(); root.toFile().deleteRecursively() }
}
