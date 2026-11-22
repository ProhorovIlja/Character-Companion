package dev.caicompanion.app

import java.util.concurrent.atomic.AtomicLong

/** Replaced atomically when the user changes filters, safe from WebView's IO threads. */
class FilterEngine(private val defaults: List<String>) {
    @Volatile var enabled: Boolean = true
    @Volatile private var blocker: HostBlocker = HostBlocker(defaults)
    val blockedRequestCount = AtomicLong(0)

    fun replace(customRules: String, exclusions: String) {
        val newBlocker = HostBlocker(defaults, customRules.lineSequence().take(500).toList(),
            exclusions.lineSequence().take(500).toList())
        blocker = newBlocker
    }

    fun shouldBlock(url: String): Boolean {
        if (!enabled || !blocker.shouldBlock(url)) return false
        blockedRequestCount.incrementAndGet()
        return true
    }
    fun ruleCount(): Int = blocker.blockedDomains.size
}
