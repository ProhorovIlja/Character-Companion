package dev.caicompanion.app

/** Official Mandy AI language scope; legacy browser voice options remain independent. */
enum class MandyLanguage(val tag: String, val modelCode: String, val displayName: String) {
    ENGLISH("en-US", "en", "English"),
    RUSSIAN("ru-RU", "ru", "Русский");

    companion object {
        /** Unknown or device-default language is not silently mapped to English. */
        fun fromModelCode(code: String): MandyLanguage? = entries.firstOrNull { it.modelCode == code }
        fun fromTag(tag: String): MandyLanguage? = entries.firstOrNull { it.tag == tag }
    }
}
