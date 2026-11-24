package dev.caicompanion.app

/** Worker-thread-only progress gate. Monotonic elapsed time; no timers or polling. */
class UpdateProgress(private val size: Long, startedAtMs: Long) {
    init { require(size in 1..536_870_912L && startedAtMs >= 0) }
    private var lastPercent = 0
    private var lastAt = startedAtMs

    fun next(bytes: Long, nowMs: Long): Int? {
        if (bytes !in 0..size || nowMs < lastAt) return null
        val percent = (bytes * 100 / size).toInt()
        if (percent <= lastPercent || (percent < 100 && nowMs - lastAt < 250)) return null
        lastPercent = percent
        lastAt = nowMs
        return percent
    }

    companion object {
        fun rateLimited(code: Int, remaining: String?, retryAfter: String?): Boolean =
            code == 429 || (code == 403 && (remaining?.trim() == "0" || !retryAfter.isNullOrBlank()))
    }
}
