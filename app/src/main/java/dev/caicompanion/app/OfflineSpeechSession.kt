package dev.caicompanion.app

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One explicit foreground capture/recognize operation. No worker or timer is created here. */
internal class OfflineSpeechSession {
    private enum class Phase { RECORDING, STOP_REQUESTED, TRANSCRIBING, CANCELLED, FINISHED }
    private val phase = AtomicReference(Phase.RECORDING)
    private val started = AtomicBoolean(false)
    fun requestStop(): Boolean = phase.compareAndSet(Phase.RECORDING, Phase.STOP_REQUESTED)
    fun cancel() {
        while (true) {
            val old = phase.get()
            if (old == Phase.CANCELLED || old == Phase.FINISHED || phase.compareAndSet(old, Phase.CANCELLED)) return
        }
    }
    fun isCancelled(): Boolean = phase.get() == Phase.CANCELLED || Thread.currentThread().isInterrupted
    private fun checkActive() { if (isCancelled()) throw IOException("Dictation cancelled") }

    /** Capture transfers ownership of its PCM result. Clear that result on every terminal path. */
    fun run(capture: (() -> Boolean, () -> Boolean) -> ShortArray,
            onTranscribing: () -> Unit = {}, recognize: (ShortArray, () -> Boolean) -> String): String {
        check(started.compareAndSet(false, true)) { "Speech session already used" }
        var audio: ShortArray? = null
        try {
            checkActive()
            if (phase.get() != Phase.RECORDING) throw IOException("Recording stopped before start")
            val pcm = capture({ phase.get() == Phase.STOP_REQUESTED }, ::isCancelled)
            audio = pcm
            checkActive()
            require(pcm.size in 1..2_880_000)
            while (true) {
                val old = phase.get()
                if (old != Phase.RECORDING && old != Phase.STOP_REQUESTED) throw IOException("Dictation cancelled")
                if (phase.compareAndSet(old, Phase.TRANSCRIBING)) break
            }
            onTranscribing()
            checkActive()
            val text = recognize(pcm, ::isCancelled)
            NativeConversation.validateText(text)
            checkActive()
            if (!phase.compareAndSet(Phase.TRANSCRIBING, Phase.FINISHED)) throw IOException("Dictation cancelled")
            return text
        } finally {
            audio?.fill(0)
            phase.set(Phase.FINISHED)
        }
    }
}
