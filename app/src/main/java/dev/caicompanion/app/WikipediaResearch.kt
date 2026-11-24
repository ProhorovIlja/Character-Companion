package dev.caicompanion.app

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Fixed public encyclopedia endpoints and bounded, attributed research packets. */
object WikipediaResearch {
    const val MAX_RESULTS = 5
    const val MAX_RESPONSE = 65536
    const val MAX_EXTRACT = 1200
    const val MAX_RESEARCH = 8000
    enum class Language(val host: String) {
        EN("en.wikipedia.org"), RU("ru.wikipedia.org"), SV("sv.wikipedia.org")
    }
    data class Source(val id: Long, val title: String, val extract: String)

    fun endpoint(language: Language, raw: String): String? {
        if (CharacterContext.searchUrl(CharacterContext.Provider.WIKIPEDIA, raw) == null) return null
        return "https://" + language.host + "/w/api.php?action=query&generator=search&gsrnamespace=0" +
            "&gsrlimit=5&prop=extracts&exintro=1&explaintext=1&exchars=600&exlimit=5&format=json&formatversion=2" +
            "&gsrsearch=" + URLEncoder.encode(raw.trim(), "UTF-8")
    }

    fun valid(source: Source): Boolean = source.id > 0 && source.title.length in 1..256 &&
        source.title.isNotBlank() && source.title.none { it.isISOControl() } &&
        source.extract.length in 1..MAX_EXTRACT && source.extract.isNotBlank() &&
        RoleplayToolkit.valid(source.title) && RoleplayToolkit.valid(source.extract)

    fun packet(language: Language, sources: List<Source>): String? {
        if (sources.isEmpty() || sources.size > MAX_RESULTS || sources.any { !valid(it) } ||
            sources.map { it.id }.distinct().size != sources.size) return null
        val text = sources.joinToString("\n\n") { source ->
            "[Wikipedia: " + source.title + "]\nhttps://" + language.host + "/?curid=" + source.id +
                "\n" + source.extract
        } + "\n\nWikipedia excerpts; see linked article histories for attribution and licensing. " +
            "CC BY-SA 4.0 where applicable: https://creativecommons.org/licenses/by-sa/4.0/"
        return text.takeIf { it.length <= MAX_RESEARCH && RoleplayToolkit.valid(it) }
    }

    fun append(existing: String, research: String): String? {
        if (!RoleplayToolkit.valid(existing) || research.isBlank() || research.length > MAX_RESEARCH ||
            !RoleplayToolkit.valid(research)) return null
        val text = if (existing.isBlank()) research else existing + "\n\n" + research
        return text.takeIf { RoleplayToolkit.valid(it) }
    }

    fun readResponse(input: InputStream, expired: () -> Boolean = { false }): String? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!expired()) {
            val requested = minOf(buffer.size, MAX_RESPONSE - output.size() + 1)
            val count = input.read(buffer, 0, requested)
            if (expired()) return null
            if (count == -1) return try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(output.toByteArray())).toString()
            } catch (_: java.nio.charset.CharacterCodingException) { null }
            if (count <= 0 || count > requested || output.size() + count > MAX_RESPONSE) return null
            output.write(buffer, 0, count)
        }
        return null
    }
}
