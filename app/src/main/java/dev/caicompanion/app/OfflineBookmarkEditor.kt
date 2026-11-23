package dev.caicompanion.app

/** Sanitizes user-created offline bookmarks; accepts public Character.AI links only. */
object OfflineBookmarkEditor {
    private fun isUnsafeTitleChar(c: Char): Boolean = c.isISOControl() ||
        c in '\u202a'..'\u202e' || c in '\u2066'..'\u2069' || c == '\u200e' || c == '\u200f'

    fun normalize(title: String?, url: String?): Pair<String, String>? {
        val canonical = UrlPolicy.sanitizeBookmark(url?.trim()) ?: return null
        val normalizedTitle = title.orEmpty().filterNot(::isUnsafeTitleChar)
            .trim().take(90).ifBlank { "Character.AI" }
        return normalizedTitle to canonical
    }
}
