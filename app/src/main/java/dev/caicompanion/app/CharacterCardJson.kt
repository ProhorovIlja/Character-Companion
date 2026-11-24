package dev.caicompanion.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Read V1/V2/V3 character text; never evaluate prompt overrides or load remote assets. */
object CharacterCardJson {
    fun parse(text: String): CharacterDraftPolicy.Draft? = try {
        decode(text.removePrefix("\uFEFF"))
    } catch (_: Exception) { null }

    private fun decode(text: String): CharacterDraftPolicy.Draft? {
        if (!LorebookPolicy.safeJsonDepth(text)) return null
        val parser = JSONTokener(text)
        val root = parser.nextValue() as? JSONObject ?: return null
        if (parser.nextClean() != '\u0000') return null
        val spec = root.opt("spec")
        val data = when (spec) {
            "chara_card_v2", "chara_card_v3" -> root.optJSONObject("data") ?: return null
            null -> root.takeIf { listOf("first_mes", "personality", "scenario", "mes_example").any(it::has) }
                ?: return null
            else -> return null
        }
        fun field(key: String): String {
            if (!data.has(key) || data.isNull(key)) return ""
            return (data.get(key) as? String ?: error("invalid field")).also {
                require(it.length <= CharacterDraftPolicy.MAX_FIELD)
            }
        }
        fun list(key: String): List<String> {
            if (!data.has(key) || data.isNull(key)) return emptyList()
            val array = data.get(key) as? JSONArray ?: error("invalid array")
            require(array.length() <= CharacterDraftPolicy.MAX_LIST)
            return List(array.length()) { i -> array.get(i) as? String ?: error("invalid list item") }
        }
        val name = field("name")
        if (!data.has("name")) return null
        val notes = listOf("creator_notes", "system_prompt", "post_history_instructions", "nickname")
            .map { it to field(it) }.filter { it.second.isNotBlank() }
            .joinToString("\n\n") { (label, value) -> "$label:\n$value" }
        val supported = setOf("name", "description", "personality", "scenario", "first_mes", "mes_example",
            "alternate_greetings", "tags", "creator_notes", "system_prompt", "post_history_instructions", "nickname")
        val loss = data.keys().asSequence().any { it !in supported } ||
            (data !== root && root.keys().asSequence().any { it !in setOf("spec", "spec_version", "data") })
        return CharacterDraftPolicy.fromCard(CharacterDraftPolicy.Card(name, field("description"),
            field("personality"), field("scenario"), field("first_mes"), field("mes_example"),
            list("alternate_greetings"), list("tags"), notes, loss))
    }
}
