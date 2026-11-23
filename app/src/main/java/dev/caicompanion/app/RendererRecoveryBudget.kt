package dev.caicompanion.app

import java.util.ArrayDeque

/** Limits automatic renderer restarts. Times are monotonic milliseconds, supplied by Android. */
class RendererRecoveryBudget(
    private val maximum: Int = 3,
    private val windowMillis: Long = 120_000L
) {
    init {
        require(maximum > 0)
        require(windowMillis > 0)
    }

    private val restarts = ArrayDeque<Long>()

    private fun prune(elapsedRealtime: Long) {
        require(elapsedRealtime >= 0)
        if (restarts.isNotEmpty() && elapsedRealtime < restarts.peekLast()) restarts.clear()
        while (restarts.isNotEmpty() && elapsedRealtime - restarts.peekFirst() >= windowMillis) {
            restarts.removeFirst()
        }
    }

    /** No URLs or crash-stack traces are stored, only timestamps for the current process. */
    @Synchronized fun recentRestartCount(elapsedRealtime: Long): Int {
        prune(elapsedRealtime)
        return restarts.size
    }

    /** Returns false when a crash loop requires explicit user intervention. */
    @Synchronized fun allowAutomaticRestart(elapsedRealtime: Long): Boolean {
        prune(elapsedRealtime)
        if (restarts.size >= maximum) return false
        restarts.addLast(elapsedRealtime)
        return true
    }
}
