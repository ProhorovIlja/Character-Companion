package dev.caicompanion.app

import java.io.Closeable
import java.io.IOException

/** Preparation intervals only; no persistent telemetry. */
internal data class ModelPreparation(val reused: Boolean, val verifyNanos: Long,
                                     val admissionNanos: Long, val loadNanos: Long)

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
    fun <T> useModel(library: NativeModelLibrary, sha: String, cancelled: () -> Boolean,
                     admit: (NativeModelLibrary.Model) -> Unit, action: (EmbeddedLlama) -> T): T =
        useMeasured(library, sha, cancelled, admit, {}, action)

    @Synchronized fun <T> useMeasured(library: NativeModelLibrary, sha: String, cancelled: () -> Boolean,
                                      admit: (NativeModelLibrary.Model) -> Unit,
                                      onPreparation: (ModelPreparation) -> Unit, action: (EmbeddedLlama) -> T): T {
        check(!active) { "Reentrant inference" }
        active = true
        fun checkActive() {
            if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Inference cancelled")
        }
        try {
            checkActive()
            // Release a different model before verification or memory admission.
            if (loaded?.model?.sha256 != sha) clear()
            val verifyStart = System.nanoTime()
            val model = library.verify(sha, cancelled)
            val verifyNanos = System.nanoTime() - verifyStart
            if (loaded?.model != model) clear()
            checkActive()
            val admissionStart = System.nanoTime()
            try {
                admit(model)
            } catch (denial: ModelAdmission.Denied) {
                // Existing model residency may contribute to the low available-memory reading.
                // Release once and measure again; never weaken the admission threshold.
                if (loaded == null || denial.decision.reason != ModelAdmission.Reason.INSUFFICIENT_HEADROOM) throw denial
                clear()
                checkActive()
                admit(model)
            }
            val admissionNanos = System.nanoTime() - admissionStart
            checkActive()
            val reused = loaded != null
            val loadStart = System.nanoTime()
            val engine = loaded?.engine ?: EmbeddedLlama.openVerified(model, cancelled).also {
                loaded = Loaded(model, it)
            }
            val loadNanos = if (reused) 0L else System.nanoTime() - loadStart
            val result = action(engine)
            checkActive()
            onPreparation(ModelPreparation(reused, verifyNanos, admissionNanos, loadNanos))
            checkActive()
            return result
        } catch (failure: Throwable) {
            try { clear() } catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
            throw failure
        } finally { active = false }
    }
}
