import dev.caicompanion.app.VoiceAlternatives as A

fun verifyVoiceAlternatives() {
    fun build(text: String = "Hello world", values: List<String>? = listOf("quiet", "brave"), start: Int = 6, end: Int = 11) =
        A.Choice.build(text, values, start, end)
    val first = build()!!
    check(first.edit.text == "Hello quiet")
    check(first.options.size == 2)
    val second = first.choose(first.edit.text, 1)!!
    check(second.edit.text == "Hello brave")
    check(second.edit.caret == 11)
    check(second.undo().restore(second.edit.text)?.first == "Hello world")
    check(second.undo().restore(second.edit.text)?.second == 6..11)
    check(second.choose(second.edit.text, 0)?.edit == first.edit)
    check(first.choose("manual change", 1) == null)
    check(first.choose(first.edit.text, -1) == null)
    check(first.choose(first.edit.text, 2) == null)
    check(build(values = listOf("", "\u0000bad", "  quiet  ", "quiet", "brave"))!!.options.size == 2)
    check(build(values = List(5) { "" } + "sixth") == null)
    check(build(values = null) == null)
    check(build(values = listOf("\uD800")) == null)
    check(build(values = listOf("x".repeat(16001), "valid"))!!.edit.text == "Hello valid")
    val full = "x".repeat(15998)
    check(build(full, listOf("long", "a"), full.length, full.length)!!.edit.text == full + "\na")
    check(build("😀", listOf("hi", "bye"), 1, 1)!!.edit.text == "😀\nhi")
    check(build(start = -1) == null)
    check(A.label("😀".repeat(201)) == "😀".repeat(200) + "…")
    check(A.label("short") == "short")
    println("VoiceAlternatives: 20 checks passed")
}
