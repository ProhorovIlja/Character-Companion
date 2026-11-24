package dev.caicompanion.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Converts common lorebook schemas into the documented Character.AI interchange subset. */
object LorebookJson {
    private fun string(obj: JSONObject, key: String): String? =
        if (!obj.has(key) || obj.isNull(key)) null else obj.get(key) as? String
            ?: throw IllegalArgumentException("expected text")

    fun parse(raw: String): LorebookPolicy.Book? = try {
        decode(raw.removePrefix("\uFEFF"))
    } catch (_: Exception) { null }

    private fun decode(raw: String): LorebookPolicy.Book? {
        if (!LorebookPolicy.safeJsonDepth(raw)) return null
        val tokener = JSONTokener(raw)
        val root = tokener.nextValue()
        if (tokener.nextClean() != '\u0000') return null
        val outer = root as? JSONObject
        val data = outer?.optJSONObject("data")
        val embedded = data?.optJSONObject("character_book") ?: outer?.optJSONObject("character_book")
        val book = embedded ?: if (outer?.optString("spec") == "lorebook_v3") data ?: return null else outer
        val rows = if (root is JSONArray) root else book?.opt("entries") ?: return null
        val count = when (rows) {
            is JSONObject -> rows.length()
            is JSONArray -> rows.length()
            else -> return null
        }
        if (count !in 1..LorebookPolicy.MAX_ENTRIES) return null
        val entries = ArrayList<LorebookPolicy.Entry>(count)
        var skipped = 0
        var loss = embedded != null || book?.keys()?.asSequence()?.any {
            it !in setOf("name", "description", "entries")
        } == true
        val accepted = setOf("comment", "title", "name", "key", "keys", "keywords", "content")
        fun add(row: JSONObject) {
            if (row.keys().asSequence().any { it !in accepted }) loss = true
            if (row.optBoolean("disable", false) || (row.has("enabled") && !row.optBoolean("enabled", false))) {
                skipped++
                return
            }
            val title = string(row, "comment") ?: string(row, "title") ?: string(row, "name") ?: ""
            val content = string(row, "content") ?: throw IllegalArgumentException("missing content")
            val rawKeys = row.opt("key") ?: row.opt("keys") ?: row.opt("keywords")
            val keys = when (rawKeys) {
                null, JSONObject.NULL -> emptyList()
                is JSONArray -> {
                    require(rawKeys.length() <= LorebookPolicy.MAX_KEYS)
                    (0 until rawKeys.length()).map { rawKeys.get(it) as? String ?: error("invalid keyword") }
                }
                is String -> if (rawKeys.isBlank()) emptyList() else rawKeys.split(',').map { it.trim() }
                else -> error("invalid keywords")
            }
            if (content.lineSequence().any { it.startsWith("@@") }) loss = true
            entries.add(LorebookPolicy.Entry(title, keys, content))
        }
        when (rows) {
            is JSONArray -> for (i in 0 until count) add(rows.getJSONObject(i))
            is JSONObject -> for (key in rows.keys()) add(rows.getJSONObject(key))
        }
        return LorebookPolicy.Book(book?.let { string(it, "name") }.orEmpty(),
            book?.let { string(it, "description") }.orEmpty(), entries, loss, skipped)
            .takeIf(LorebookPolicy::valid)
    }

    fun encode(book: LorebookPolicy.Book): String? {
        if (!LorebookPolicy.valid(book)) return null
        val rows = JSONObject()
        book.entries.forEachIndexed { index, entry ->
            rows.put(index.toString(), JSONObject().put("comment", entry.title)
                .put("key", JSONArray(entry.keys)).put("content", entry.content))
        }
        return JSONObject().put("name", book.name).put("description", book.description)
            .put("entries", rows).toString().takeIf { it.length <= LorebookPolicy.MAX_CHARS }
    }
}
