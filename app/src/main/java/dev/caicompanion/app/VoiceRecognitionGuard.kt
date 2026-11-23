package dev.caicompanion.app

/**
 * Invalidates delayed callbacks from an earlier or cancelled recognizer.
 * All SpeechRecognizer listener callbacks are delivered on the Android main thread.
 * This class has no Android dependencies and does not retain recordings or text.
 */
class VoiceRecognitionGuard {
    private var generation = 0L
    private var current: Long? = null

    fun start(): Long {
        generation += 1
        current = generation
        return generation
    }

    fun isCurrent(token: Long): Boolean = current == token

    /** The final result/error may only be consumed once. */
    fun finish(token: Long): Boolean {
        if (!isCurrent(token)) return false
        current = null
        return true
    }

    fun cancel() { current = null }
}
