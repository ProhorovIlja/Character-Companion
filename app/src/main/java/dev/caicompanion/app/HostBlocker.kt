package dev.caicompanion.app

import java.net.URI
import java.util.Locale

/** Stateless, exact host/suffix filter. O(number of labels), not O(number of rules). */
class HostBlocker(
    baseRules: Collection<String>,
    customRules: Collection<String> = emptyList(),
    excludedHosts: Collection<String> = emptyList()
) {
    val blockedDomains: Set<String> = (baseRules + customRules).mapNotNull(::normalizeRule).toSet()
    val allowDomains: Set<String> = excludedHosts.mapNotNull(::normalizeRule).toSet()

    /** Returns the matched rule for statistics. Never returns full URLs. */
    fun matchedBlockedDomain(rawUrl: String?): String? {
        val host = hostFromUrl(rawUrl) ?: return null
        if (isCriticalHost(host)) return null
        var suffix = host
        var matched: String? = null
        while (true) {
            if (suffix in allowDomains) return null // exceptions always override other matches
            // Attribute the block to the most specific matching domain.
            // We walk from hostname to parent suffix, so keep the first hit.
            if (matched == null && suffix in blockedDomains) matched = suffix
            val index = suffix.indexOf('.')
            if (index < 0) break
            suffix = suffix.substring(index + 1)
        }
        return matched
    }

    fun shouldBlock(rawUrl: String?): Boolean = matchedBlockedDomain(rawUrl) != null

    companion object {
        private val rule = Regex("^[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?$")
        fun normalizeRule(input: String): String? {
            val value = input.trim().trimEnd('.').lowercase(Locale.ROOT)
            return if (value.length <= 253 && value.contains('.') && rule.matches(value) &&
                !value.contains("..") && !value.startsWith('#') && !value.startsWith('-') &&
                value.split('.').all { it.isNotEmpty() && it.length <= 63 && !it.startsWith('-') && !it.endsWith('-') }
            ) value else null
        }
        fun hostFromUrl(rawUrl: String?): String? {
            val uri = try { URI(rawUrl ?: return null) } catch (_: Exception) { return null }
            if (!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true)) return null
            if (uri.userInfo != null || uri.host.isNullOrBlank()) return null
            return uri.host.trimEnd('.').lowercase(Locale.ROOT)
        }
        fun isDomain(host: String, domain: String): Boolean =
            host.equals(domain, true) || host.endsWith(".$domain", true)
        fun isServiceHost(host: String): Boolean =
            isDomain(host, "character.ai") || isDomain(host, "c.ai")
        /** Identity requests must not be broken even by imported third-party lists. */
        fun isCriticalHost(host: String): Boolean = isServiceHost(host) ||
            isDomain(host, "accounts.google.com") || isDomain(host, "appleid.apple.com") ||
            isDomain(host, "id.apple.com")
    }
}
