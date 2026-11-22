package dev.caicompanion.app

import java.net.URI
import java.util.Locale

/** Strict host-based blocking. Never blocks Character.AI or a user allowlisted host. */
class HostBlocker(
    baseRules: Collection<String>,
    customRules: Collection<String> = emptyList(),
    excludedHosts: Collection<String> = emptyList()
) {
    val blockedDomains: Set<String> = (baseRules + customRules).mapNotNull(::normalizeRule).toSet()
    val allowDomains: Set<String> = excludedHosts.mapNotNull(::normalizeRule).toSet()

    fun shouldBlock(rawUrl: String?): Boolean {
        val host = hostFromUrl(rawUrl) ?: return false
        if (isServiceHost(host) || allowDomains.any { isDomain(host, it) }) return false
        return blockedDomains.any { isDomain(host, it) }
    }

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
            return uri.host?.trimEnd('.')?.lowercase(Locale.ROOT)
        }
        fun isDomain(host: String, domain: String): Boolean =
            host.equals(domain, true) || host.endsWith(".$domain", true)
        fun isServiceHost(host: String): Boolean =
            isDomain(host, "character.ai") || isDomain(host, "c.ai")
    }
}
