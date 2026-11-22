package dev.caicompanion.app

import java.util.concurrent.atomic.AtomicLong

/** Requests can be intercepted from multiple WebView threads. Rules are immutable snapshots. */
class FilterEngine(private val defaults: List<String>, private val strictRules: List<String> = emptyList()) {
    @Volatile var strict: Boolean = false
    @Volatile var enabled: Boolean = true
    @Volatile private var blocker: HostBlocker = HostBlocker(defaults)
    val blockedRequestCount = AtomicLong(0)

    // Store only matched rule domains, never paths, query strings or per-user chat URLs.
    // Bounded to 128 unique keys; ordinary counters are reset on process restart.
    private val statsLock = Any()
    private val counters: MutableMap<String, Long> = mutableMapOf()

    fun replace(customRules: String, exclusions: String) {
        val newBlocker = HostBlocker(defaults + if (strict) strictRules else emptyList(),
            customRules.lineSequence().take(500).toList(),
            exclusions.lineSequence().take(500).toList())
        blocker = newBlocker
    }

    fun shouldBlock(url: String): Boolean {
        if (!enabled) return false
        val matched = blocker.matchedBlockedDomain(url) ?: return false
        blockedRequestCount.incrementAndGet()
        synchronized(statsLock) {
            if (matched in counters || counters.size < 128) {
                counters[matched] = (counters[matched] ?: 0L) + 1
            }
        }
        return true
    }

    fun ruleCount(): Int = blocker.blockedDomains.size

    fun topBlockedRules(limit: Int = 8): List<Pair<String, Long>> = synchronized(statsLock) {
        counters.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }
            .thenBy { it.key }).take(limit.coerceIn(0, 20)).map { it.key to it.value }
    }

    fun clearStats() {
        synchronized(statsLock) {
            counters.clear()
            blockedRequestCount.set(0)
        }
    }
}
