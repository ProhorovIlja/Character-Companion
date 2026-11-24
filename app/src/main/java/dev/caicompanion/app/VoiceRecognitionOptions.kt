package dev.caicompanion.app

/** Stable language indices; provider support is checked separately. No audio or text persistence. */
object VoiceRecognitionOptions {
    val languages = listOf(null, "ru-RU", "en-US", "sv-SE", "fi-FI", "de-DE", "fr-FR",
        "es-ES", "it-IT", "pl-PL", "uk-UA", "ja-JP", "ko-KR", "zh-CN")
    fun index(value: Int): Int = if (value in languages.indices) value else 0
    fun language(value: Int): String? = languages[index(value)]

    /** Use the first valid provider alternative; malformed or blank candidates cannot overwrite a draft. */
    fun candidate(values: List<String>?): String? = values?.asSequence()?.take(5)
        ?.map { it.trim() }?.firstOrNull { it.isNotEmpty() && VoiceDraftPolicy.validText(it) }

    /** Preview only, bounded by code points so truncation cannot split an emoji. */
    fun preview(values: List<String>?): String? = candidate(values)?.let { text ->
        val count = text.codePointCount(0, text.length)
        if (count <= 200) text else text.substring(0, text.offsetByCodePoints(0, 200)) + "…"
    }
}
