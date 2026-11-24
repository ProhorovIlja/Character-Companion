package dev.caicompanion.app

import java.io.Closeable
import java.io.IOException

/** Optional JNI engine. One model per owner; every generation creates fresh native context. */
class EmbeddedLlama private constructor() : Closeable {
    private var handle = 0L
    private var generating = false
    private external fun nativeLoad(path: ByteArray): Long
    private external fun nativeGenerateV2(handle: Long, roles: Array<ByteArray>, contents: Array<ByteArray>,
                                        contextTokens: Int, outputTokens: Int, seed: Int, threads: Int, sink: EmbeddedTextSink)
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
                              cancelled: () -> Boolean = { false }, threads: Int = 4, onText: (String) -> Unit): String {
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
            nativeGenerateV2(handle, messages.map { it.role.toByteArray(Charsets.UTF_8) }.toTypedArray(),
                messages.map { it.content.toByteArray(Charsets.UTF_8) }.toTypedArray(), contextTokens, outputTokens, seed, threads, sink)
            return sink.finish()
        } finally { generating = false }
    }
    @Synchronized override fun close() {
        check(!generating) { "Cannot close engine from its generation callback" }
        if (handle != 0L) { nativeClose(handle); handle = 0 }
    }
}
