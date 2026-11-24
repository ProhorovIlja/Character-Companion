package dev.caicompanion.app

/** Bounded, local-only lorebook interchange. No trigger execution or network access. */
object LorebookPolicy {
    const val MAX_CHARS = 2_000_000
    const val MAX_ENTRIES = 1000
    const val MAX_KEYS = 256
    data class Entry(val title: String, val keys: List<String>, val content: String)
    data class Book(val name: String, val description: String, val entries: List<Entry>,
                    val conversionLoss: Boolean = false, val skippedDisabled: Int = 0) {
        val needsReview: Int get() = entries.count { it.title.isBlank() || it.content.isBlank() ||
            it.keys.isEmpty() || it.keys.size > 8 || it.keys.any { key -> key.isBlank() } }
    }

    fun valid(book: Book): Boolean {
        if (book.entries.size !in 1..MAX_ENTRIES || book.name.length > 512 || book.description.length > 100_000) return false
        var total = book.name.length.toLong() + book.description.length
        for (entry in book.entries) {
            if (entry.title.length > 512 || entry.content.length > 100_000 || entry.keys.size > MAX_KEYS ||
                entry.keys.any { it.length > 512 }) return false
            total += entry.title.length + entry.content.length + entry.keys.sumOf { it.length }
            if (total > MAX_CHARS) return false
        }
        return true
    }

    /** Character.AI's labelled text grammar. Reject incomplete or ambiguous headers. */
    fun parseText(raw: String): Book? {
        if (raw.length > MAX_CHARS) return null
        var name = ""
        var description = ""
        var title: String? = null
        var keys: List<String>? = null
        var content: StringBuilder? = null
        var hasName = false
        var hasDescription = false
        val entries = ArrayList<Entry>()
        fun finish(): Boolean {
            val heading = title ?: return true
            val words = keys ?: return false
            val body = content ?: return false
            if (entries.size >= MAX_ENTRIES) return false
            entries.add(Entry(heading, words, body.toString().trimEnd('\n')))
            return true
        }
        for (line in raw.removePrefix("\uFEFF").lineSequence()) {
            when {
                line.startsWith("Entry Title:") -> {
                    if (!finish()) return null
                    title = line.substringAfter(':').trim()
                    keys = null
                    content = null
                }
                content != null -> content.append('\n').append(line)
                title != null && line.startsWith("Keywords:") && keys == null -> {
                    val value = line.substringAfter(':').trim()
                    keys = if (value.isEmpty()) emptyList() else value.split(',').map { it.trim() }
                    if (keys.size > MAX_KEYS) return null
                }
                title != null && line.startsWith("Content:") && keys != null ->
                    content = StringBuilder(line.substringAfter(':').removePrefix(" "))
                title == null && line.startsWith("Lorebook Name:") && !hasName -> {
                    name = line.substringAfter(':').trim(); hasName = true
                }
                title == null && line.startsWith("Lorebook Description:") && !hasDescription -> {
                    description = line.substringAfter(':').trim(); hasDescription = true
                }
                line.isBlank() -> Unit
                else -> return null
            }
        }
        if (!finish()) return null
        return Book(name, description, entries).takeIf(::valid)
    }

    /** Only produce text when the format can round-trip every field without ambiguity. */
    fun toText(book: Book): String? {
        if (!valid(book)) return null
        fun singleLine(value: String) = '\n' !in value && '\r' !in value && value == value.trim()
        if (!singleLine(book.name) || !singleLine(book.description)) return null
        for (entry in book.entries) {
            if (!singleLine(entry.title) || entry.keys.any { !singleLine(it) || ',' in it || it.isEmpty() } ||
                '\r' in entry.content || entry.content.endsWith('\n') ||
                entry.content.lineSequence().any { it.startsWith("Entry Title:") }) return null
        }
        val output = buildString {
            append("Lorebook Name: ").append(book.name).append('\n')
            append("Lorebook Description: ").append(book.description).append("\n\n")
            for (entry in book.entries) {
                append("Entry Title: ").append(entry.title).append('\n')
                append("Keywords: ").append(entry.keys.joinToString(", ")).append('\n')
                append("Content: ").append(entry.content).append("\n\n")
            }
        }
        return output.takeIf { it.length <= MAX_CHARS }
    }

    /** Bound nesting before the platform JSON parser allocates recursive objects. */
    fun safeJsonDepth(raw: String): Boolean {
        if (raw.length > MAX_CHARS) return false
        var depth = 0
        var quoted = false
        var escaped = false
        for (ch in raw) {
            if (quoted) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') quoted = false
            } else when (ch) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > 32) return false
                '}', ']' -> if (--depth < 0) return false
                // Android JSONTokener accepts single quotes and comments; reject those extensions.
                '\'', '#', '/' -> return false
            }
        }
        return depth == 0 && !quoted
    }
}
