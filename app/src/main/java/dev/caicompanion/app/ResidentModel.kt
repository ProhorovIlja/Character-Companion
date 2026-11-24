package dev.caicompanion.app

import java.io.Closeable
import java.io.IOException

/** One verified model per foreground workspace. Fresh context per generation. */
internal class ResidentModel : Closeable {
    private data class Loaded(val model: NativeModelLibrary.Model, val engine: EmbeddedLlama)
    private var loaded: Loaded? = null
    private var active = false
    private fun clear() {
        val previous = loaded
        loaded = null
        previous?.engine?.close()
    }
    @Synchronized override fun close() {
        check(!active) { "Cannot release a model from its inference callback" }
        clear()
    }
    /** Keep the full digest check and admission on every turn. No engine may escape this callback. */
    @Synchronized fun <T> useModel(library: NativeModelLibrary, sha: String, cancelled: () -> Boolean,
                                  admit: (NativeModelLibrary.Model) -> Unit, action: (EmbeddedLlama) -> T): T {
        check(!active) { "Reentrant inference" }
        active = true
        fun checkActive() {
            if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Inference cancelled")
        }
        try {
            checkActive()
            // Release a different model before verification or memory admission.
            if (loaded?.model?.sha256 != sha) clear()
            val model = library.verify(sha, cancelled)
            if (loaded?.model != model) clear()
            checkActive()
            admit(model)
            checkActive()
            val engine = loaded?.engine ?: EmbeddedLlama.openVerified(model, cancelled).also {
                loaded = Loaded(model, it)
            }
            val result = action(engine)
            checkActive()
            return result
        } catch (failure: Throwable) {
            try { clear() } catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
            throw failure
        } finally { active = false }
    }
}
