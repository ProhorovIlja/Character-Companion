package dev.caicompanion.app

import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import org.json.JSONTokener

/** One explicit public request. No cookies from the app's renderer, login tokens or retry. */
object WikipediaResearchClient {
    fun search(language: WikipediaResearch.Language, query: String): List<WikipediaResearch.Source> {
        val endpoint = WikipediaResearch.endpoint(language, query) ?: error("Invalid query")
        val connection = URL(endpoint).openConnection() as HttpsURLConnection
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "CharacterCompanion/" + BuildConfig.VERSION_NAME +
                " (https://github.com/ProhorovIlja/Character-Companion/issues)")
            require(connection.responseCode == 200) { "Wikipedia request failed" }
            require(connection.contentLength <= WikipediaResearch.MAX_RESPONSE)
            val json = connection.inputStream.use { input ->
                WikipediaResearch.readResponse(input) { android.os.SystemClock.elapsedRealtime() - started > 20000L }
            } ?: error("Invalid response")
            return parse(json)
        } finally {
            connection.disconnect()
        }
    }

    fun parse(json: String): List<WikipediaResearch.Source> {
        require(json.length <= WikipediaResearch.MAX_RESPONSE && LorebookPolicy.safeJsonDepth(json))
        val parser = JSONTokener(json)
        val root = parser.nextValue() as? JSONObject ?: error("Invalid response")
        require(parser.nextClean() == '\u0000' && !root.has("error"))
        val query = root.optJSONObject("query") ?: return emptyList()
        val pages = query.optJSONArray("pages") ?: return emptyList()
        require(pages.length() <= WikipediaResearch.MAX_RESULTS)
        return (0 until pages.length()).mapNotNull { i ->
            val page = pages.optJSONObject(i) ?: return@mapNotNull null
            val title = page.opt("title") as? String ?: return@mapNotNull null
            val extract = page.opt("extract") as? String ?: return@mapNotNull null
            val id = page.optLong("pageid", -1)
            val source = WikipediaResearch.Source(id, title, extract)
            if (page.optInt("ns", -1) != 0 || !WikipediaResearch.valid(source)) null
            else page.optInt("index", i + 1) to source
        }.sortedBy { it.first }.map { it.second }.distinctBy { it.id }
    }
}
