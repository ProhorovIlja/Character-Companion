package dev.caicompanion.app

/**
 * Pure, bounded Android Share Sheet link parser. An untrusted app can share text with us,
 * but only sanitized public Character.AI content URLs are ever opened in WebView.
 * No Android intents, Javascript evaluation, login URLs or third-party URLs are honored.
 */
object IncomingLinkParser {
    private const val MAX_SHARE_CHARACTERS = 16_384
    private val candidate = Regex("https://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    fun safeSharedLink(sharedText: String?): String? {
        if (sharedText.isNullOrBlank() || sharedText.length > MAX_SHARE_CHARACTERS) return null
        return candidate.findAll(sharedText).mapNotNull { hit ->
            val url = hit.value.trimEnd('.', ',', ';', '!', '?', ')', ']', '}')
            UrlPolicy.sanitizeBookmark(url)
        }.firstOrNull()
    }
}
