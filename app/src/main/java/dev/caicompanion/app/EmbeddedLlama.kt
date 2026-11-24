package dev.caicompanion.app

import java.io.Closeable
import java.io.IOException

/** Per-successful-turn native wall-clock measurements; excludes file verification and model loading. */
data class GenerationMetrics internal constructor(val promptTokens: Int, val outputTokens: Int,
    val retainedMessages: Int, val fittingAttempts: Int, val fitNanos: Long, val contextNanos: Long,
    val prefillNanos: Long, val generationNanos: Long)

/** Optional JNI engine. One model per owner; every generation creates fresh native context. */
class EmbeddedLlama private constructor() : Closeable {
    private var handle = 0L
    private var generating = false
    private external fun nativeLoad(path: ByteArray): Long
    private external fun nativeGenerateV3(handle: Long, roles: Array<ByteArray>, contents: Array<ByteArray>,
                                        contextTokens: Int, outputTokens: Int, seed: Int, threads: Int, sink: EmbeddedTextSink): LongArray
    private external fun nativeClose(handle: Long)

    companion object {
        /** Caller explicitly trusts this model; hash validation is not proof of publisher identity. */
        fun open(library: NativeModelLibrary, sha: String, cancelled: () -> Boolean = { false },
                 admit: (NativeModelLibrary.Model) -> Unit = {}): EmbeddedLlama {
            val model = library.verify(sha, cancelled)
            if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Model load cancelled")
            admit(model)
            if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Model load cancelled")
            return openVerified(model, cancelled)
        }
        /** Internal callers must fully verify the private GGUF before invoking. */
        internal fun openVerified(model: NativeModelLibrary.Model, cancelled: () -> Boolean): EmbeddedLlama {
            if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Model load cancelled")
            System.loadLibrary("mandy_llama")
            val engine = EmbeddedLlama()
            engine.handle = engine.nativeLoad(model.path.toAbsolutePath().toString().toByteArray(Charsets.UTF_8))
            check(engine.handle != 0L)
            if (cancelled() || Thread.currentThread().isInterrupted) {
                engine.close(); throw IOException("Model load cancelled")
            }
            return engine
        }
    }
    @Synchronized fun generate(messages: List<NativeConversation.Message>, contextTokens: Int = 4096,
                              outputTokens: Int = 512, seed: Int = -1,
                              cancelled: () -> Boolean = { false }, threads: Int = 4, onMetrics: (GenerationMetrics) -> Unit = {}, onText: (String) -> Unit): String {
        check(handle != 0L && !generating)
        require(threads in 1..8)
        require(contextTokens in 512..8192 && outputTokens in 1..1024 && outputTokens < contextTokens)
        require(messages.size in 2..201 && messages.first().role == "system" && messages.last().role == "user")
        require(messages.drop(1).all { it.role == "user" || it.role == "assistant" })
        require(messages.sumOf { it.content.length.toLong() } <= 64000)
        messages.forEach { NativeConversation.validateText(it.content) }
        val sink = EmbeddedTextSink(cancelled, onText)
        if (sink.isCancelled()) throw IOException("Generation cancelled")
        generating = true
        try {
            val stats = nativeGenerateV3(handle, messages.map { it.role.toByteArray(Charsets.UTF_8) }.toTypedArray(),
                messages.map { it.content.toByteArray(Charsets.UTF_8) }.toTypedArray(), contextTokens, outputTokens, seed, threads, sink)
            val answer = sink.finish()
            require(stats.size == 8 && stats[0] in 1L..(contextTokens - outputTokens).toLong() &&
                stats[1] in 1L..outputTokens.toLong() && stats[2] in 2L..messages.size.toLong() &&
                stats[3] in 1L..9L && stats.drop(4).all { it >= 0L })
            onMetrics(GenerationMetrics(stats[0].toInt(), stats[1].toInt(), stats[2].toInt(), stats[3].toInt(),
                stats[4], stats[5], stats[6], stats[7]))
            return answer
        } finally { generating = false }
    }
    @Synchronized override fun close() {
        check(!generating) { "Cannot close engine from its generation callback" }
        if (handle != 0L) { nativeClose(handle); handle = 0 }
    }
}
