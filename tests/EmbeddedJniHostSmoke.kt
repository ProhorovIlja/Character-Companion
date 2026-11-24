package dev.caicompanion.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

/** Exercises actual JNI entry points with a deterministic, non-inference C++ test double. */
fun main() {
    val root = Files.createTempDirectory("mandy-jni-host")
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    fun messages(text: String, identity: String = "Identity") = listOf(
        NativeConversation.Message("system", identity), NativeConversation.Message("user", text))
    try {
        val library = NativeModelLibrary(root, 1024)
        val fixture = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf(71, 71, 85, 70)).putInt(3).putLong(1).putLong(0).array()
        val model = library.import(fixture.inputStream())
        bad { EmbeddedLlama.open(library, model.sha256, cancelled = { true }) }
        val engine = EmbeddedLlama.open(library, model.sha256)
        engine.use {
            val fragments = StringBuilder()
            verify(it.generate(messages("normal"), onText = { part -> fragments.append(part) }) == "Hello 🌙" && fragments.toString() == "Hello 🌙")
            verify(it.generate(messages("unicode-input", "Мэнди 🌙"), onText = {}) == "Hello 🌙")
            verify(it.generate(messages("profile-low"), contextTokens = 2048, outputTokens = 256, threads = 2, onText = {}) == "Hello 🌙")
            verify(it.generate(messages("profile-quality"), contextTokens = 6144, outputTokens = 768, threads = 6, onText = {}) == "Hello 🌙")
            bad { it.generate(messages("normal"), threads = 0, onText = {}) }
            bad { it.generate(messages("normal"), threads = 9, onText = {}) }
            val history = messages("earlier") + listOf(NativeConversation.Message("assistant", "reply"), NativeConversation.Message("user", "history"))
            verify(it.generate(history, onText = {}) == "Hello 🌙")
            fun fitting(last: String) = listOf(NativeConversation.Message("system", "Мэнди 🌙")) +
                (1..3).flatMap { n -> listOf(NativeConversation.Message("user", "old-$n"), NativeConversation.Message("assistant", "reply-$n")) } +
                listOf(NativeConversation.Message("user", "recent"), NativeConversation.Message("assistant", "recent-reply"), NativeConversation.Message("user", last))
            for (marker in listOf("fit-context", "fit-template", "fit-negative")) {
                verify(it.generate(fitting(marker), contextTokens = 512, outputTokens = 256, onText = {}) == "Hello 🌙")
            }
            val required = runCatching { it.generate(fitting("fit-required"), contextTokens = 512, outputTokens = 256, onText = { error("unexpected output") }) }.exceptionOrNull()
            verify(required is java.io.IOException && required.message == "Character context and latest message exceed model context")
            var probes = 0
            bad { it.generate(fitting("fit-context"), contextTokens = 512, outputTokens = 256, cancelled = { ++probes >= 4 }, onText = { error("unexpected output") }) }
            verify(probes >= 4)
            verify(it.generate(messages("normal"), onText = {}) == "Hello 🌙")
            for (input in listOf("bad-template", "overflow", "decode-error", "bad-utf8", "empty")) {
                bad { it.generate(messages(input), onText = {}) }
                verify(it.generate(messages("normal"), onText = {}) == "Hello 🌙")
            }
            verify(it.generate(messages("large-piece"), onText = {}) == "x".repeat(300))
            var emitted = ""
            bad { it.generate(messages("limit"), outputTokens = 2, onText = { part -> emitted += part }) }
            verify(emitted == "Hello Hello ")
            var stop = false
            bad { it.generate(messages("normal"), cancelled = { stop }, onText = { stop = true }) }
            verify(stop)
            val failure = IllegalStateException("callback fixture")
            verify(runCatching { it.generate(messages("normal"), onText = { throw failure }) }.exceptionOrNull() === failure)
            bad { it.generate(messages("normal"), onText = { _ -> it.close() }) }
            verify(it.generate(messages("normal"), onText = {}) == "Hello 🌙")
        }
        engine.close()
        bad { engine.generate(messages("normal"), onText = {}) }
        EmbeddedLlama.open(library, model.sha256).use { verify(it.generate(messages("normal"), onText = {}) == "Hello 🌙") }
        Files.write(model.path, fixture.copyOf().also { it[127] = 1 })
        bad { EmbeddedLlama.open(library, model.sha256) }
        println("Embedded JNI host: $checks checks passed (test double, no real inference)")
    } finally { root.toFile().deleteRecursively() }
}
