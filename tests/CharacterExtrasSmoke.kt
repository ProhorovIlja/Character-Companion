import dev.caicompanion.app.CharacterDraftExtras as E
import dev.caicompanion.app.CharacterDraftPolicy as P
import dev.caicompanion.app.CharacterDraftCodec as C
import java.io.ByteArrayOutputStream

fun verifyCharacterExtras() {
    val g = E.Field.GREETINGS
    val t = E.Field.TAGS
    val original = P.Draft(name = "Mira", greeting = "Hi", alternatives = listOf("One", "Two"), tags = listOf("Drama"), conversionLoss = true)
    val added = E.edit(original, g, null, "Three")!!
    check(added.alternatives == listOf("One", "Two", "Three"))
    check(original.alternatives == listOf("One", "Two"))
    check(E.edit(added, g, 1, "New\nline")!!.alternatives[1] == "New\nline")
    check(E.move(added, g, 2, -1)!!.alternatives == listOf("One", "Three", "Two"))
    check(E.move(added, g, 0, 1)!!.alternatives == listOf("Two", "One", "Three"))
    check(E.move(added, g, 0, -1) == null)
    check(E.move(added, g, 2, 1) == null)
    check(E.move(added, g, 0, 2) == null)
    check(E.remove(added, g, 1)!!.alternatives == listOf("One", "Three"))
    check(E.remove(added, g, 99) == null)
    check(E.edit(added, g, -1, "X") == null)
    check(E.edit(added, g, null, "  ") == null)
    check(E.edit(added, g, null, "\uD800") == null)
    check(E.edit(original, t, null, "x".repeat(513)) == null)
    check(E.edit(original.copy(tags = List(64) { "x" }), t, null, "new") == null)
    val tagged = E.edit(added, t, 0, "Русский 🌙")!!
    check(tagged.tags == listOf("Русский 🌙"))
    val edited = E.notes(tagged, "Creator notes\nText")!!
    check(edited.sourceNotes == "Creator notes\nText" && edited.conversionLoss)
    check(E.notes(edited, "")!!.sourceNotes.isEmpty())
    check(E.notes(edited, "x".repeat(100001)) == null)
    check(E.notes(edited, "\uDC00") == null)
    check(E.label("😀".repeat(81)) == "😀".repeat(80) + "…")
    check(E.label("a\nb\rc") == "a b c")
    val packed = original.copy(alternatives = List(20) { "x".repeat(100000) }, tags = emptyList(), name = "", greeting = "")
    check(E.notes(packed, "x") == null)
    val output = ByteArrayOutputStream()
    C.write(edited, output)
    check(C.read(output.toByteArray().inputStream()) == edited)
    println("CharacterExtras: 24 checks passed")
}
