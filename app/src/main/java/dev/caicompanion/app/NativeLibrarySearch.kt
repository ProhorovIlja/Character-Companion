package dev.caicompanion.app

import java.text.Normalizer
import java.util.Locale

/** Local bookmark search; build an immutable index once per library change. */
object NativeLibrarySearch {
    private val whitespace = Regex("\\s+")
    private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)

    enum class Order { SAVED, NEWEST, TITLE }

    fun order(value: String?): Order = Order.entries.firstOrNull { it.name == value } ?: Order.SAVED

    class Index(bookmarks: List<Pair<String, String>>) {
        private data class Entry(val title: String, val url: String, val searchable: String, val titleKey: String)
        private val entries = bookmarks.mapNotNull { (rawTitle, rawUrl) ->
            val url = UrlPolicy.sanitizeBookmark(rawUrl) ?: return@mapNotNull null
            val title = rawTitle.take(90).ifBlank { "Character.AI" }
            Entry(title, url, fold("$title $url"), fold(title))
        }

        private val newest = entries.asReversed()
        private val alphabetical = entries.sortedWith(compareBy<Entry> { it.titleKey }.thenBy { it.url })

        fun filter(input: String, limit: Int = 100, order: Order = Order.SAVED): List<Pair<String, String>> {
            val cap = limit.coerceIn(0, 100)
            if (cap == 0) return emptyList()
            val terms = fold(input.take(80)).trim().split(whitespace)
                .filter { it.isNotEmpty() }.take(8)
            val matched = ArrayList<Pair<String, String>>(minOf(entries.size, cap))
            val seen = HashSet<String>()
            val ordered = when (order) {
                Order.SAVED -> entries
                Order.NEWEST -> newest
                Order.TITLE -> alphabetical
            }
            for (entry in ordered) {
                // Keep aliases searchable; deduplicate only after matching.
                if (terms.all { entry.searchable.contains(it) } && seen.add(entry.url)) {
                    matched.add(entry.title to entry.url)
                    if (matched.size >= cap) break
                }
            }
            return matched
        }
    }

    fun filter(bookmarks: List<Pair<String, String>>, input: String, limit: Int = 100): List<Pair<String, String>> =
        Index(bookmarks).filter(input, limit)
}
