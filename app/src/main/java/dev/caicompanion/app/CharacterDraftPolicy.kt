package dev.caicompanion.app

/** Local creator fields. Imported values remain intact; target limits are advisory. */
object CharacterDraftPolicy {
    const val MAX_FIELD = 100_000
    const val MAX_LIST = 64
    enum class Field(val targetMax: Int) {
        NAME(20), TAGLINE(50), DESCRIPTION(500), GREETING(4096), DEFINITION(32000)
    }
    data class Draft(val name: String = "", val tagline: String = "", val description: String = "",
                     val greeting: String = "", val definition: String = "",
                     val alternatives: List<String> = emptyList(), val tags: List<String> = emptyList(),
                     val sourceNotes: String = "", val conversionLoss: Boolean = false) {
        fun value(field: Field): String = when (field) {
            Field.NAME -> name
            Field.TAGLINE -> tagline
            Field.DESCRIPTION -> description
            Field.GREETING -> greeting
            Field.DEFINITION -> definition
        }
    }
    data class Card(val name: String, val description: String, val personality: String = "",
                    val scenario: String = "", val greeting: String = "", val examples: String = "",
                    val alternatives: List<String> = emptyList(), val tags: List<String> = emptyList(),
                    val sourceNotes: String = "", val conversionLoss: Boolean = false)

    fun valid(draft: Draft): Boolean {
        if (draft.alternatives.size > MAX_LIST || draft.tags.size > MAX_LIST) return false
        val fields = Field.entries.map(draft::value) + draft.alternatives + draft.tags + draft.sourceNotes
        return draft.name.length <= 512 && draft.tags.all { it.length <= 512 } &&
            fields.all { it.length <= MAX_FIELD } && fields.sumOf { it.length.toLong() } <= LorebookPolicy.MAX_CHARS
    }

    /** Keep first-person public description separate from the editable definition. */
    fun fromCard(card: Card): Draft? {
        val definition = listOf("Identity" to card.description, "Personality" to card.personality,
            "Scenario" to card.scenario, "Dialogue examples" to card.examples)
            .filter { it.second.isNotBlank() }.joinToString("\n\n") { (label, value) -> "$label:\n$value" }
        return Draft(card.name, "", card.description, card.greeting, definition,
            card.alternatives.toList(), card.tags.toList(), card.sourceNotes, card.conversionLoss)
            .takeIf(::valid)
    }

    fun edit(draft: Draft, field: Field, value: String): Draft? = when (field) {
        Field.NAME -> draft.copy(name = value)
        Field.TAGLINE -> draft.copy(tagline = value)
        Field.DESCRIPTION -> draft.copy(description = value)
        Field.GREETING -> draft.copy(greeting = value)
        Field.DEFINITION -> draft.copy(definition = value)
    }.takeIf(::valid)

    fun needsReview(field: Field, value: String): Boolean {
        if (value.length > field.targetMax) return true
        if (field != Field.NAME) return false
        return value.length < 3 || value.isBlank() || value != value.trim() ||
            !value.codePoints().allMatch { Character.isLetterOrDigit(it) || it == 32 || it == 45 || it == 95 ||
                Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
    }

    fun useAlternative(draft: Draft, index: Int): Draft? =
        draft.alternatives.getOrNull(index)?.let { edit(draft, Field.GREETING, it) }
}
