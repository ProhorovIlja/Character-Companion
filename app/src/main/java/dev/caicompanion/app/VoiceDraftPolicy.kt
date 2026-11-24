package dev.caicompanion.app

/** Pure, in-memory text editing. Neither recognized speech nor draft state is persisted. */
object VoiceDraftPolicy {
    const val MAX_CHARS = 16_000

    data class Edit(val text: String, val caret: Int)

    /** Validate the transient language selector after a configuration change. */
    fun languageIndex(index: Int): Int = if (index in 0..2) index else 0

    /** Clamp selection indices without splitting a UTF-16 surrogate pair (e.g. an emoji). */
    private fun boundary(text: String, index: Int, right: Boolean): Int {
        var safe = index.coerceIn(0, text.length)
        if (safe > 0 && safe < text.length &&
            Character.isHighSurrogate(text[safe - 1]) && Character.isLowSurrogate(text[safe])) {
            safe += if (right) 1 else -1
        }
        return safe
    }

    /** Safe, non-persistent selection restoration after Activity recreation. */
    fun selection(text: String, start: Int, end: Int): IntRange {
        if (start == end) {
            val caret = boundary(text, start, right = true)
            return caret..caret
        }
        val low = boundary(text, minOf(start, end), right = false)
        val high = boundary(text, maxOf(start, end), right = true)
        return low..high
    }

    /**
     * Insert speech at the user's cursor or replace their selection. A null result means
     * the editor must remain untouched (empty speech, invalid index, or length overflow).
     * The returned caret points immediately after the inserted words, not the separator.
     */
    fun insert(current: String, candidate: String?, start: Int, end: Int): Edit? {
        val words = candidate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (current.length > MAX_CHARS || words.length > MAX_CHARS) return null
        if (start !in 0..current.length || end !in 0..current.length) return null
        val bounds = selection(current, start, end)
        val left = current.substring(0, bounds.first)
        val right = current.substring(bounds.last)
        val prefix = when {
            left.isEmpty() || left.last().isWhitespace() -> ""
            left.last() in "([\"«“" -> ""
            right.isEmpty() -> "\n" // Keep existing append-between-utterances behavior.
            else -> " "
        }
        val suffix = when {
            right.isEmpty() || right.first().isWhitespace() -> ""
            right.first() in ",.!?:;)]}»”" -> ""
            else -> " "
        }
        val caret = left.length + prefix.length + words.length
        val combined = left + prefix + words + suffix + right
        return combined.takeIf { it.length <= MAX_CHARS }?.let { Edit(it, caret) }
    }

    /** Historical append behavior, preserved for callers that do not provide a selection. */
    fun append(current: String, candidate: String?): String? =
        insert(current, candidate, current.length, current.length)?.text
}
