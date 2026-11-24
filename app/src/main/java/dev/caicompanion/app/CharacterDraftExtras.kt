package dev.caicompanion.app

/** Local edits of ordered card metadata. All operations return a new validated draft. */
object CharacterDraftExtras {
    enum class Field(val maxChars: Int) {
        GREETINGS(CharacterDraftPolicy.MAX_FIELD), TAGS(512)
    }

    fun items(draft: CharacterDraftPolicy.Draft, field: Field): List<String> = when (field) {
        Field.GREETINGS -> draft.alternatives
        Field.TAGS -> draft.tags
    }

    private fun validText(value: String, max: Int): Boolean =
        value.length <= max && Charsets.UTF_8.newEncoder().canEncode(value)

    private fun replace(draft: CharacterDraftPolicy.Draft, field: Field, items: List<String>): CharacterDraftPolicy.Draft? =
        when (field) {
            Field.GREETINGS -> draft.copy(alternatives = items.toList())
            Field.TAGS -> draft.copy(tags = items.toList())
        }.takeIf(CharacterDraftPolicy::valid)

    fun edit(draft: CharacterDraftPolicy.Draft, field: Field, index: Int?, value: String): CharacterDraftPolicy.Draft? {
        if (value.isBlank() || !validText(value, field.maxChars)) return null
        val next = items(draft, field).toMutableList()
        if (index == null) {
            if (next.size >= CharacterDraftPolicy.MAX_LIST) return null
            next.add(value)
        } else {
            if (index !in next.indices) return null
            next[index] = value
        }
        return replace(draft, field, next)
    }

    fun remove(draft: CharacterDraftPolicy.Draft, field: Field, index: Int): CharacterDraftPolicy.Draft? {
        val next = items(draft, field).toMutableList()
        if (index !in next.indices) return null
        next.removeAt(index)
        return replace(draft, field, next)
    }

    fun move(draft: CharacterDraftPolicy.Draft, field: Field, index: Int, direction: Int): CharacterDraftPolicy.Draft? {
        val next = items(draft, field).toMutableList()
        if (direction !in listOf(-1, 1) || index !in next.indices || index + direction !in next.indices) return null
        val value = next.removeAt(index)
        next.add(index + direction, value)
        return replace(draft, field, next)
    }

    fun notes(draft: CharacterDraftPolicy.Draft, value: String): CharacterDraftPolicy.Draft? =
        if (!validText(value, CharacterDraftPolicy.MAX_FIELD)) null
        else draft.copy(sourceNotes = value).takeIf(CharacterDraftPolicy::valid)

    fun label(value: String): String {
        val end = value.offsetByCodePoints(0, minOf(80, value.codePointCount(0, value.length)))
        return value.substring(0, end).replace('\n', ' ').replace('\r', ' ') + if (end < value.length) "…" else ""
    }
}
