package dev.caicompanion.app

import java.util.Locale

/** Pure bounded operations on the current local draft. Never changes the imported source file. */
object LorebookEditor {
    private fun key(value: String) = value.trim().lowercase(Locale.ROOT)
    fun review(entry: LorebookPolicy.Entry): Boolean = entry.title.isBlank() || entry.content.isBlank() ||
        entry.keys.isEmpty() || entry.keys.size > 8 || entry.keys.any { it.isBlank() } ||
        entry.keys.map(::key).distinct().size != entry.keys.size

    fun search(book: LorebookPolicy.Book, query: String, reviewOnly: Boolean): List<Int> {
        val terms = query.take(120).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(8)
        return book.entries.indices.filter { i ->
            val e = book.entries[i]
            (!reviewOnly || review(e)) && terms.all { term ->
                e.title.contains(term, ignoreCase = true) || e.keys.any { it.contains(term, ignoreCase = true) } ||
                    e.content.contains(term, ignoreCase = true)
            }
        }
    }

    fun replace(book: LorebookPolicy.Book, index: Int?, entry: LorebookPolicy.Entry): LorebookPolicy.Book? {
        if (index != null && index !in book.entries.indices) return null
        if (index == null && book.entries.size >= LorebookPolicy.MAX_ENTRIES) return null
        val next = book.entries.toMutableList()
        if (index == null) next.add(entry) else next[index] = entry
        return book.copy(entries = next).takeIf(LorebookPolicy::valid)
    }

    fun remove(book: LorebookPolicy.Book, index: Int): LorebookPolicy.Book? {
        if (book.entries.size <= 1 || index !in book.entries.indices) return null
        return book.copy(entries = book.entries.filterIndexed { i, _ -> i != index })
    }

    fun metadata(book: LorebookPolicy.Book, name: String, description: String): LorebookPolicy.Book? =
        book.copy(name = name, description = description).takeIf(LorebookPolicy::valid)

    /** One keyword per line; commas are preserved for JSON exports. */
    fun keywords(input: String): List<String>? {
        if (input.length > LorebookPolicy.MAX_KEYS * 513) return null
        val keys = input.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return keys.takeIf { it.size <= LorebookPolicy.MAX_KEYS && it.all { k -> k.length <= 512 } }
    }
}
