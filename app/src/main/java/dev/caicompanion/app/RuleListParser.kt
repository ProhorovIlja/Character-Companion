package dev.caicompanion.app

/**
 * Intentionally small, auditable subset of hosts and Adblock Plus domain-only syntax.
 * Not a full EasyList parser: path, cosmetic, regex, options and redirect rules are skipped.
 * Input is selected manually by the user; no subscription URLs or update workers.
 */
object RuleListParser {
    const val MAX_CHARACTERS = 2_000_000
    const val MAX_LINES = 100_000
    const val MAX_DOMAINS = 40_000
    private val whitespace = Regex("\\s+")
    private val sinkAddresses = setOf("0.0.0.0", "127.0.0.1", "::1")

    data class Rules(
        val blocked: Set<String> = emptySet(),
        val allowed: Set<String> = emptySet(),
        val unsupported: Int = 0,
        val examined: Int = 0
    ) {
        val size: Int get() = blocked.size + allowed.size
        fun canonicalText(): String = buildString {
            blocked.sorted().forEach { append(it).append('\n') }
            allowed.sorted().forEach { append("@@||").append(it).append("^\n") }
        }
    }

    /** Return null for invalid, oversized or wholly unsupported lists, never partial success. */
    fun parse(contents: String): Rules? {
        if (contents.isBlank() || contents.length > MAX_CHARACTERS) return null
        val blocked = HashSet<String>()
        val allowed = HashSet<String>()
        var unsupported = 0
        var examined = 0
        var lines = 0
        for (rawLine in contents.lineSequence()) {
            if (++lines > MAX_LINES) return null
            val line = rawLine.trim().removePrefix("\uFEFF").trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("#") ||
                line.startsWith("[") || line.startsWith("//")) continue
            examined++
            if (line.length > 512) { unsupported++; continue }
            val isAllow = line.startsWith("@@")
            val entry = if (isAllow) line.removePrefix("@@") else line
            val domains = when {
                entry.startsWith("||") && entry.endsWith("^") -> listOf(entry.removePrefix("||").dropLast(1))
                !isAllow && entry.any { it.isWhitespace() } -> {
                    val fields = entry.substringBefore('#').trim().split(whitespace)
                    if (fields.size >= 2 && fields[0] in sinkAddresses) fields.drop(1) else emptyList()
                }
                !isAllow && !entry.any { it == '|' || it == '^' || it == '*' || it == '$' ||
                    it == '/' || it == '#' || it == '@' || it == ':' || it == ' ' } -> listOf(entry)
                else -> emptyList()
            }
            val normalized = domains.mapNotNull(HostBlocker::normalizeRule)
            // Reject malformed rows atomically; do not silently broaden unsupported syntax.
            if (normalized.isEmpty() || normalized.size != domains.size) { unsupported++; continue }
            val usable = normalized.filterNot(HostBlocker::isCriticalHost)
            if (usable.size != normalized.size) unsupported++
            if (isAllow) allowed.addAll(usable) else blocked.addAll(usable)
            if (blocked.size + allowed.size > MAX_DOMAINS) return null
        }
        if (blocked.isEmpty() && allowed.isEmpty()) return null
        // Punycode can expand imported Unicode domains. Reject lists that would exceed
        // the storage/reimport limit after canonicalization, before allocating the text.
        val canonicalLength = blocked.sumOf { it.length + 1 } + allowed.sumOf { it.length + 6 }
        if (canonicalLength > MAX_CHARACTERS) return null
        return Rules(blocked.toSet(), allowed.toSet(), unsupported, examined)
    }
}
