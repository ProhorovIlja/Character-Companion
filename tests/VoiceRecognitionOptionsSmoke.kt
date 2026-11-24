import dev.caicompanion.app.VoiceRecognitionOptions as O

fun verifyVoiceRecognitionOptions() {
    check(O.languages.size == 14)
    check(O.language(0) == null)
    check(O.language(1) == "ru-RU")
    check(O.language(2) == "en-US")
    check(O.language(3) == "sv-SE")
    check(O.language(13) == "zh-CN")
    check(O.index(-1) == 0)
    check(O.index(14) == 0)
    check(O.candidate(null) == null)
    check(O.candidate(listOf("", "  ", "  hello  ")) == "hello")
    check(O.candidate(listOf("\u0000bad", "valid")) == "valid")
    check(O.candidate(listOf("\uD800", "valid")) == "valid")
    check(O.candidate(List(5) { "" } + "sixth") == null)
    check(O.candidate(listOf("x".repeat(16001))) == null)
    check(O.preview(listOf("hello")) == "hello")
    check(O.preview(listOf("😀".repeat(200))) == "😀".repeat(200))
    check(O.preview(listOf("😀".repeat(201))) == "😀".repeat(200) + "…")
    check(O.preview(emptyList()) == null)
    println("VoiceRecognitionOptions: 18 checks passed")
}
