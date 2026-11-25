package dev.caicompanion.app

/** Bounded utterances preserve exact input and never split a Unicode surrogate pair. */
internal object SpokenText {
    fun chunks(text: String, maxUnits: Int): List<String> {
        require(maxUnits in 2..16000)
        require(text.length in 1..16000 && text.isNotBlank())
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            require(c.code >= 32 || c in "\t\r\n")
            if (c.isHighSurrogate()) require(i < text.length && text[i++].isLowSurrogate())
            else require(!c.isLowSurrogate())
        }
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxUnits, text.length)
            if (end < text.length && text[end].isLowSurrogate()) end--
            // Prefer a nearby word boundary, but avoid very short chunks.
            if (end < text.length) {
                val floor = start + (end - start) / 2
                var split = end
                while (split > floor && !text[split - 1].isWhitespace()) split--
                if (split > floor) end = split
            }
            check(end > start)
            result.add(text.substring(start, end))
            start = end
        }
        return result
    }
}
