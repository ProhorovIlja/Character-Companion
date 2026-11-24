package dev.caicompanion.app

import java.util.concurrent.atomic.AtomicLong

/** Requests can be intercepted from multiple WebView threads. Rules are immutable snapshots. */
class FilterEngine(private val defaults: List<String>, private val strictRules: List<String> = emptyList()) {
    @Volatile var strict: Boolean = false
    @Volatile var enabled: Boolean = true
    /** Only in-memory login flow; never persisted to a profile or filter export. */
    @Volatile var loginBypass: Boolean = false
    @Volatile private var blocker: HostBlocker = HostBlocker(defaults)
    val blockedRequestCount = AtomicLong(0)

    // Store only matched rule domains, never paths, query strings or per-user chat URLs.
    // Bounded to 128 unique keys; ordinary counters are reset on process restart.
    private val statsLock = Any()
    private val counters: MutableMap<String, Long> = mutableMapOf()

    private data class RuleConfiguration(
        val strict: Boolean,
        val custom: String,
        val excluded: String,
        val importedBlocked: Set<String>,
        val importedAllowed: Set<String>
    )
    private var appliedConfiguration: RuleConfiguration? = null

    /** The settings screen calls this frequently; unchanged rules cost no rebuild. */
    @Synchronized fun replace(
        customRules: String, exclusions: String,
        imported: RuleListParser.Rules = RuleListParser.Rules()
    ) {
        val configuration = RuleConfiguration(strict, customRules, exclusions,
            imported.blocked, imported.allowed)
        if (configuration == appliedConfiguration) return
        val newBlocker = HostBlocker(
            defaults + (if (strict) strictRules else emptyList()) + imported.blocked,
            customRules.lineSequence().take(500).toList(),
            exclusions.lineSequence().take(500).toList() + imported.allowed
        )
        blocker = newBlocker
        appliedConfiguration = configuration
    }

    fun shouldBlock(url: String): Boolean {
        if (!enabled || loginBypass) return false
        val matched = blocker.matchedBlockedDomain(url) ?: return false
        synchronized(statsLock) {
            blockedRequestCount.incrementAndGet()
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
