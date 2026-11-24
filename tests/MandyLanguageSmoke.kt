package dev.caicompanion.app

fun main() {
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    verify(MandyLanguage.entries.size == 2)
    verify(MandyLanguage.entries.map { it.tag } == listOf("en-US", "ru-RU"))
    verify(MandyLanguage.entries.map { it.modelCode } == listOf("en", "ru"))
    verify(MandyLanguage.fromTag("en-US") == MandyLanguage.ENGLISH)
    verify(MandyLanguage.fromTag("ru-RU") == MandyLanguage.RUSSIAN)
    verify(MandyLanguage.fromModelCode("en") == MandyLanguage.ENGLISH)
    verify(MandyLanguage.fromModelCode("ru") == MandyLanguage.RUSSIAN)
    for (invalid in listOf("", "auto", "de", "fr", "en_US", "RU", "ru-RU", " en")) {
        verify(MandyLanguage.fromModelCode(invalid) == null)
    }
    verify(MandyLanguage.fromTag("de-DE") == null)
    verify(MandyLanguage.RUSSIAN.displayName == "Русский")
    println("MandyLanguage: $checks checks passed")
}
