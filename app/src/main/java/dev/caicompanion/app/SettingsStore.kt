package dev.caicompanion.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores only browser shell configuration and user bookmarks, never messages or credentials. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("character_companion_v2", Context.MODE_PRIVATE)
    var startInHub: Boolean
        get() = prefs.getBoolean("start_in_native_hub", true)
        set(v) = prefs.edit().putBoolean("start_in_native_hub", v).apply()
    var protectScreen: Boolean
        get() = prefs.getBoolean("protect_screen", false)
        set(v) = prefs.edit().putBoolean("protect_screen", v).apply()
    var strictBlocking: Boolean
        get() = prefs.getBoolean("strict_blocking", false)
        set(v) = prefs.edit().putBoolean("strict_blocking", v).apply()
    var reduceImages: Boolean
        get() = prefs.getBoolean("reduce_images", false)
        set(v) = prefs.edit().putBoolean("reduce_images", v).apply()
    var blocking: Boolean
        get() = prefs.getBoolean("blocking", true)
        set(v) = prefs.edit().putBoolean("blocking", v).apply()
    var cookies: Boolean
        get() = prefs.getBoolean("third_party_cookies", true)
        set(v) = prefs.edit().putBoolean("third_party_cookies", v).apply()
    var mediaRequiresGesture: Boolean
        get() = prefs.getBoolean("media_gesture", true)
        set(v) = prefs.edit().putBoolean("media_gesture", v).apply()
    var mediaAllowed: Boolean
        get() = prefs.getBoolean("media_allowed", false)
        set(v) = prefs.edit().putBoolean("media_allowed", v).apply()
    var desktopMode: Boolean
        get() = prefs.getBoolean("desktop_mode", false)
        set(v) = prefs.edit().putBoolean("desktop_mode", v).apply()
    var resumePage: Boolean
        get() = prefs.getBoolean("resume_page", false)
        set(v) = prefs.edit().putBoolean("resume_page", v).apply()
    var lastVisited: String
        get() = UrlPolicy.sanitizeBookmark(prefs.getString("last_visited", "")).orEmpty()
        set(v) {
            val sanitized = UrlPolicy.sanitizeBookmark(v).orEmpty()
            prefs.edit().putString("last_visited", sanitized).apply()
        }
    var compactChrome: Boolean
        get() = prefs.getBoolean("compact_chrome", false)
        set(v) = prefs.edit().putBoolean("compact_chrome", v).apply()
    var darkCanvas: Boolean
        get() = prefs.getBoolean("dark_canvas", true)
        set(v) = prefs.edit().putBoolean("dark_canvas", v).apply()
    var textZoom: Int
        get() = prefs.getInt("text_zoom", 100).coerceIn(75, 175)
        set(v) = prefs.edit().putInt("text_zoom", v.coerceIn(75, 175)).apply()
    var customRules: String
        get() = prefs.getString("custom_rules", "").orEmpty()
        set(v) = prefs.edit().putString("custom_rules", v.take(12_000)).apply()
    var excludeRules: String
        get() = prefs.getString("exclude_rules", "").orEmpty()
        set(v) = prefs.edit().putString("exclude_rules", v.take(12_000)).apply()

    /** Atomically change a subset of options; preserve bookmarks, theme and media consent. */
    fun applyProfile(profile: BrowserProfile) {
        val p = BrowserProfiles.options(profile)
        prefs.edit()
            .putBoolean("blocking", p.blocking)
            .putBoolean("strict_blocking", p.strictBlocking)
            .putBoolean("third_party_cookies", p.thirdPartyCookies)
            .putBoolean("reduce_images", p.reduceImages)
            .putBoolean("media_gesture", p.requireMediaGesture)
            .apply()
    }

    private fun bookmarks(): JSONArray = try {
        JSONArray(prefs.getString("bookmarks", "[]"))
    } catch (_: Exception) { JSONArray() }

    fun listBookmarks(): List<Pair<String, String>> = buildList {
        val data = bookmarks()
        for (i in 0 until data.length()) {
            val entry = data.optJSONObject(i) ?: continue
            val url = entry.optString("url")
            UrlPolicy.sanitizeBookmark(url)?.let { add(entry.optString("title").take(90) to it) }
        }
    }
    fun addBookmark(title: String, url: String): Boolean {
        val safeUrl = UrlPolicy.sanitizeBookmark(url) ?: return false
        val data = listBookmarks().filterNot { it.second == safeUrl }.takeLast(99).toMutableList()
        data.add(title.take(90).ifBlank { "Character.AI" } to safeUrl)
        writeBookmarks(data)
        return true
    }
    fun removeBookmark(url: String) = writeBookmarks(listBookmarks().filterNot { it.second == UrlPolicy.sanitizeBookmark(url) })
    private fun writeBookmarks(items: List<Pair<String, String>>) {
        val array = JSONArray()
        items.takeLast(100).forEach { (title, url) ->
            array.put(JSONObject().put("title", title).put("url", url))
        }
        prefs.edit().putString("bookmarks", array.toString()).apply()
    }

    private fun sanitizedBookmarkArray(): JSONArray = JSONArray().also { sanitized ->
        listBookmarks().takeLast(100).forEach { (title, url) ->
            sanitized.put(JSONObject().put("title", title).put("url", url))
        }
    }

    /** Manual SAF export. No cookies, chat text, auth tokens or WebView localStorage. */
    fun exportJson(): String = JSONObject().apply {
        put("schema", 1)
        put("application", "Character Companion for Android")
        put("startInHub", startInHub)
        put("blocking", blocking)
        put("protectScreen", protectScreen)
        put("strictBlocking", strictBlocking)
        put("reduceImages", reduceImages)
        put("thirdPartyCookies", cookies)
        put("mediaRequiresGesture", mediaRequiresGesture)
        put("mediaAllowed", mediaAllowed)
        put("desktopMode", desktopMode)
        put("resumePage", resumePage)
        put("compactChrome", compactChrome)
        put("darkCanvas", darkCanvas)
        put("textZoom", textZoom)
        put("customRules", customRules)
        put("excludeRules", excludeRules)
        put("bookmarks", sanitizedBookmarkArray())
    }.toString(2)

    fun importJson(contents: String): Boolean {
        if (contents.length > 200_000) return false
        val parsed = try { JSONObject(contents) } catch (_: Exception) { return false }
        if (parsed.optString("application") != "Character Companion for Android" ||
            parsed.optInt("schema") != 1) return false
        // Parse values before changing preferences, for consistent validation.
        val saved = mutableListOf<Pair<String,String>>()
        val array = parsed.optJSONArray("bookmarks") ?: JSONArray()
        for (i in 0 until minOf(array.length(), 100)) {
            val obj = array.optJSONObject(i) ?: continue
            val u = obj.optString("url")
            UrlPolicy.sanitizeBookmark(u)?.let { saved.add(obj.optString("title").take(90) to it) }
        }
        // Publish one atomic preference transaction: a crash cannot leave a half-imported profile.
        val bookmarkArray = JSONArray()
        saved.takeLast(100).forEach { (title, url) ->
            bookmarkArray.put(JSONObject().put("title", title).put("url", url))
        }
        prefs.edit()
            .putBoolean("start_in_native_hub", parsed.optBoolean("startInHub", true))
            .putBoolean("blocking", parsed.optBoolean("blocking", true))
            .putBoolean("protect_screen", parsed.optBoolean("protectScreen", false))
            .putBoolean("strict_blocking", parsed.optBoolean("strictBlocking", false))
            .putBoolean("reduce_images", parsed.optBoolean("reduceImages", false))
            .putBoolean("third_party_cookies", parsed.optBoolean("thirdPartyCookies", true))
            .putBoolean("media_gesture", parsed.optBoolean("mediaRequiresGesture", true))
            .putBoolean("media_allowed", false) // Never import a privileged consent toggle
            .putBoolean("desktop_mode", parsed.optBoolean("desktopMode", false))
            .putBoolean("resume_page", parsed.optBoolean("resumePage", false))
            .putBoolean("compact_chrome", parsed.optBoolean("compactChrome", false))
            .putBoolean("dark_canvas", parsed.optBoolean("darkCanvas", true))
            .putInt("text_zoom", parsed.optInt("textZoom", 100).coerceIn(75, 175))
            .putString("custom_rules", parsed.optString("customRules").take(12_000))
            .putString("exclude_rules", parsed.optString("excludeRules").take(12_000))
            .putString("bookmarks", bookmarkArray.toString())
            .apply()
        return true
    }
}
