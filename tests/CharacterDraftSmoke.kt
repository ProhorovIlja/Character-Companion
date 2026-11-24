import dev.caicompanion.app.CharacterDraftPolicy as P

fun verifyCharacterDraft() {
    val card = P.Card("Mandy", "Identity {{char}}", "Reserved", "A city", "Hi {{user}}", "{{char}}: Hello",
        listOf("Alternative one", "Alternative two"), listOf("Drama", "Mystery"), "Source instructions", true)
    val draft = P.fromCard(card)!!
    check(draft.name == card.name && draft.description == card.description)
    check(draft.tagline.isEmpty())
    check(draft.greeting == card.greeting)
    check(draft.definition == "Identity:\nIdentity {{char}}\n\nPersonality:\nReserved\n\nScenario:\nA city\n\nDialogue examples:\n{{char}}: Hello")
    check(draft.alternatives == card.alternatives && draft.tags == card.tags)
    check(draft.sourceNotes == card.sourceNotes && draft.conversionLoss)
    check(!draft.definition.contains("Source instructions"))
    check(P.useAlternative(draft, 1)?.greeting == "Alternative two")
    check(P.useAlternative(draft, -1) == null)
    check(P.useAlternative(draft, 2) == null)
    check(P.edit(draft, P.Field.NAME, "New name")?.name == "New name")
    check(P.edit(draft, P.Field.DEFINITION, "x".repeat(32001)) != null)
    check(P.edit(draft, P.Field.DEFINITION, "x".repeat(100001)) == null)
    check(P.edit(draft, P.Field.NAME, "x".repeat(513)) == null)
    check(P.fromCard(card.copy(alternatives = List(65) { "x" })) == null)
    check(P.fromCard(card.copy(tags = listOf("x".repeat(513)))) == null)
    check(P.fromCard(card.copy(alternatives = List(30) { "x".repeat(100000) })) == null)
    check(P.fromCard(P.Card("A", ""))?.definition == "")
    check(!P.needsReview(P.Field.NAME, "Café_123"))
    check(P.needsReview(P.Field.NAME, "AB"))
    check(P.needsReview(P.Field.NAME, "A!B"))
    check(P.needsReview(P.Field.NAME, " A B"))
    check(!P.needsReview(P.Field.NAME, "x".repeat(20)))
    check(P.needsReview(P.Field.NAME, "x".repeat(21)))
    for (field in P.Field.entries.filter { it != P.Field.NAME }) {
        check(!P.needsReview(field, "x".repeat(field.targetMax)))
        check(P.needsReview(field, "x".repeat(field.targetMax + 1)))
    }
    check(draft.greeting == card.greeting)
    println("CharacterDraftPolicy: 33 checks passed")
}
