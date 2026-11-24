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

    companion object {
        /** A real crash can repeat on the same page; hidden views must not load implicitly. */
        fun canRestoreAutomatically(didCrash: Boolean, activityResumed: Boolean,
                                    workspaceVisible: Boolean): Boolean =
            !didCrash && activityResumed && !workspaceVisible
    }

    private val restarts = ArrayDeque<Long>()

    private fun prune(elapsedRealtime: Long) {
        require(elapsedRealtime >= 0)
        val latest = restarts.peekLast()
        if (latest != null && elapsedRealtime < latest) restarts.clear()
        while (true) {
            val earliest = restarts.peekFirst() ?: break
            if (elapsedRealtime - earliest < windowMillis) break
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
