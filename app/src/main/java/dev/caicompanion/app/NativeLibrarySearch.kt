package dev.caicompanion.app

import java.util.Locale

/** Pure native, offline-only search for user-created bookmarks. No network or chat access. */
object NativeLibrarySearch {
    private val whitespace = Regex("\\s+")

    fun filter(
        bookmarks: List<Pair<String, String>>,
        input: String,
        limit: Int = 100
    ): List<Pair<String, String>> {
        if (limit <= 0) return emptyList()
        val terms = input.take(80).trim().lowercase(Locale.ROOT).split(whitespace)
            .filter { it.isNotEmpty() }.take(8)
        val matched = ArrayList<Pair<String, String>>(minOf(bookmarks.size, limit.coerceIn(0, 100)))
        val seen = HashSet<String>()
        for ((rawTitle, rawUrl) in bookmarks) {
            val url = UrlPolicy.sanitizeBookmark(rawUrl) ?: continue
            if (!seen.add(url)) continue
            val title = rawTitle.take(90).ifBlank { "Character.AI" }
            val searchable = (title + " " + url).lowercase(Locale.ROOT)
            if (terms.all { searchable.contains(it) }) {
                matched.add(title to url)
                if (matched.size >= limit.coerceIn(0, 100)) break
            }
        }
        return matched
    }
}
