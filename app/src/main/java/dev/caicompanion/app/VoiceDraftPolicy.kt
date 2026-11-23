package dev.caicompanion.app

/** Pure draft rules: no audio, credentials, or recognition results are persisted. */
object VoiceDraftPolicy {
    const val MAX_CHARS = 16_000

    /** Position only, in volatile memory. Never persist private speech preferences. */
    fun languageIndex(index: Int): Int = if (index in 0..2) index else 0

    /** Null rejects an empty/oversized candidate without destroying the user's draft. */
    fun append(current: String, candidate: String?): String? {
        val addition = candidate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (addition.length > MAX_CHARS || current.length > MAX_CHARS) return null
        val separator = if (current.isBlank() || current.lastOrNull()?.isWhitespace() == true) "" else "\n"
        val result = current + separator + addition
        return result.takeIf { it.length <= MAX_CHARS }
    }
}
