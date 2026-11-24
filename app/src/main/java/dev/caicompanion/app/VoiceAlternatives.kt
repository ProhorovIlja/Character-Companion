package dev.caicompanion.app

/** Bounded, in-memory alternatives to the last speech edit. No audio, disk, or network. */
object VoiceAlternatives {
    data class Option(val words: String, val edit: VoiceDraftPolicy.Edit)
    class Choice private constructor(val before: String, val start: Int, val end: Int,
                                     val options: List<Option>, val selected: Int) {
        val edit: VoiceDraftPolicy.Edit get() = options[selected].edit
        fun matches(current: String): Boolean = current == edit.text
        fun choose(current: String, index: Int): Choice? =
            if (matches(current) && index in options.indices)
                Choice(before, start, end, options, index) else null
        fun undo(): VoiceDraftPolicy.SpeechUndo = VoiceDraftPolicy.SpeechUndo(before, edit.text, start, end)

        companion object {
            fun build(current: String, values: List<String>?, start: Int, end: Int): Choice? {
                val options = values.orEmpty().asSequence().take(5)
                    .mapNotNull { raw ->
                        if (raw.length > VoiceDraftPolicy.MAX_CHARS) return@mapNotNull null
                        val words = raw.trim()
                        VoiceDraftPolicy.insert(current, words, start, end)?.let { Option(words, it) }
                    }.distinctBy { it.edit }.toList()
                return if (options.isEmpty()) null else Choice(current, start, end, options, 0)
            }
        }
    }

    /** Compact label; selection itself uses the entire original candidate. */
    fun label(words: String): String {
        val count = words.codePointCount(0, words.length)
        return if (count <= 200) words else words.substring(0, words.offsetByCodePoints(0, 200)) + "…"
    }
}
