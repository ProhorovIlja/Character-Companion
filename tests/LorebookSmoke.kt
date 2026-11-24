import dev.caicompanion.app.LorebookPolicy as P
fun verifyLorebook() {
    val source = "Lorebook Name: City\nLorebook Description: Night\n\nEntry Title: Gate\nKeywords: gate, guards\nContent: First line.\n\nSecond line {{user}}.\n\nEntry Title: Café\nKeywords: café\nContent: A place.\n"
    val b = P.parseText(source)!!
    check(b.name == "City" && b.description == "Night")
    check(b.entries.size == 2)
    check(b.entries[0].keys == listOf("gate", "guards"))
    check(b.entries[0].content == "First line.\n\nSecond line {{user}}.")
    check(P.parseText(P.toText(b)!!) == b)
    check(P.parseText("\uFEFF" + source) == b)
    check(P.parseText(source.replace("\n", "\r\n")) == b)
    check(P.parseText("just arbitrary text") == null)
    check(P.parseText("Entry Title: Bad\nContent: Missing keys") == null)
    check(P.parseText("Entry Title: Bad\nKeywords: one") == null)
    check(P.parseText("Lorebook Name: A\nLorebook Name: B\n" + source) == null)
    check(P.parseText("Entry Title: A\nKeywords:\nContent: test")!!.needsReview == 1)
    check(P.parseText("Entry Title: A\nKeywords: 1,2,3,4,5,6,7,8,9\nContent: test")!!.needsReview == 1)
    check(P.toText(b.copy(entries = listOf(P.Entry("X", listOf("a,b"), "text")))) == null)
    check(P.toText(b.copy(entries = listOf(P.Entry("X", listOf("a"), "Entry Title: literal")))) == null)
    check(P.toText(b.copy(name = "x\ny")) == null)
    check(P.parseText("x".repeat(P.MAX_CHARS+1)) == null)
    check(!P.valid(b.copy(entries = List(1001) { b.entries[0] })))
    check(P.safeJsonDepth("{\"entries\": [{\"content\": \"[}\\\"\"}]}"))
    check(!P.safeJsonDepth("[".repeat(33) + "]".repeat(33)))
    check(!P.safeJsonDepth("{'x':1}"))
    check(!P.safeJsonDepth("{/*comment*/}"))
    check(!P.safeJsonDepth("{\"x\":\"unfinished}"))
    println("LorebookPolicy: 23 checks passed")
}
