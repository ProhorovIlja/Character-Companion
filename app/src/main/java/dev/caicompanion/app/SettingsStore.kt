package dev.caicompanion.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores only browser shell configuration and user bookmarks, never messages or credentials. */
class SettingsStore(context: Context) {
    // All instances share the same preferences file. Serialize history writes against opt-out/import.
    companion object { private val historyLock = Any() }
    private val prefs = context.getSharedPreferences("character_companion_v2", Context.MODE_PRIVATE)
    // Explicit local mode preference only; never contains account or session data.
    var browserSessionPreferred: Boolean
        get() = prefs.getBoolean("browser_session_preferred", false)
        set(v) = prefs.edit().putBoolean("browser_session_preferred", v).apply()
    // Shared APK: preserve the chosen embedded engine without transferring browser cookies.
    var preferWebView: Boolean
        get() = prefs.getBoolean("prefer_webview", false)
        set(v) = prefs.edit().putBoolean("prefer_webview", v).apply()
    var browserSessionProvider: String
        get() = prefs.getString("browser_session_provider", "").orEmpty()
        set(v) = prefs.edit().putString("browser_session_provider", v.take(255)).apply()
    var startInHub: Boolean
        get() = prefs.getBoolean("start_in_native_hub", false)
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
        set(v) {
            synchronized(historyLock) {
                val edit = prefs.edit().putBoolean("resume_page", v)
                if (!v) edit.remove("last_visited")
                edit.apply()
            }
        }
    var lastVisited: String
        get() = synchronized(historyLock) {
            if (!resumePage) "" else UrlPolicy.sanitizeBookmark(prefs.getString("last_visited", "")).orEmpty()
        }
        set(v) {
            val sanitized = UrlPolicy.sanitizeBookmark(v).orEmpty()
            synchronized(historyLock) {
                val edit = prefs.edit()
                if (!resumePage || sanitized.isEmpty()) edit.remove("last_visited")
                else edit.putString("last_visited", sanitized)
                edit.apply()
            }
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

    var libraryOrder: NativeLibrarySearch.Order
        get() = NativeLibrarySearch.order(prefs.getString("library_order", null))
        set(v) = prefs.edit().putString("library_order", v.name).apply()

    private fun bookmarks(): JSONArray = try {
        JSONArray(prefs.getString("bookmarks", "[]"))
    } catch (_: Exception) { JSONArray() }

    fun listBookmarks(): List<Pair<String, String>> = buildList {
        val data = bookmarks()
        for (i in 0 until data.length()) {
            val entry = data.optJSONObject(i) ?: continue
            val url = entry.optString("url")
            OfflineBookmarkEditor.normalize(entry.optString("title"), url)?.let { add(it) }
        }
    }
    fun addBookmark(title: String, url: String): Boolean {
        val normalized = OfflineBookmarkEditor.normalize(title, url) ?: return false
        synchronized(historyLock) {
            val data = NativeLibrarySearch.save(listBookmarks(), rawPins(), normalized) ?: return false
            writeBookmarks(data)
        }
        return true
    }
    fun removeBookmark(url: String) {
        val canonical = UrlPolicy.sanitizeBookmark(url) ?: return
        synchronized(historyLock) {
            writeBookmarks(listBookmarks().filterNot { it.second == canonical })
        }
    }
    private fun rawPins(): Set<String> = prefs.getStringSet("library_pins", emptySet()).orEmpty().toSet()

    fun pinnedBookmarks(): Set<String> = synchronized(historyLock) {
        NativeLibrarySearch.validPins(listBookmarks(), rawPins())
    }

    fun setBookmarkPinned(url: String, pinned: Boolean): Boolean = synchronized(historyLock) {
        val canonical = UrlPolicy.sanitizeBookmark(url) ?: return@synchronized false
        val items = listBookmarks()
        if (items.none { it.second == canonical }) return@synchronized false
        val pins = NativeLibrarySearch.validPins(items, rawPins()).toMutableSet()
        if (pinned) pins.add(canonical) else pins.remove(canonical)
        prefs.edit().putStringSet("library_pins", pins).apply()
        true
    }

    private fun writeBookmarks(items: List<Pair<String, String>>) {
        val array = JSONArray()
        items.takeLast(100).forEach { (title, url) ->
            array.put(JSONObject().put("title", title).put("url", url))
        }
        prefs.edit().putString("bookmarks", array.toString())
            .putStringSet("library_pins", NativeLibrarySearch.validPins(items.takeLast(100), rawPins()))
            .apply()
    }

    private fun sanitizedBookmarkArray(): JSONArray = JSONArray().also { sanitized ->
        listBookmarks().takeLast(100).forEach { (title, url) ->
            sanitized.put(JSONObject().put("title", title).put("url", url))
        }
    }

    /** Manual SAF export. No cookies, chat text, auth tokens or WebView localStorage. */
    fun exportJson(): String = synchronized(historyLock) { JSONObject().apply {
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
        put("libraryOrder", libraryOrder.name)
        put("bookmarks", sanitizedBookmarkArray())
        put("libraryPins", JSONArray(pinnedBookmarks().sorted()))
    }.toString(2) }

    fun importJson(contents: String): Boolean {
        if (contents.length > SettingsBackup.MAX_CHARS) return false
        val parsed = try { JSONObject(contents) } catch (_: Exception) { return false }
        if (parsed.optString("application") != "Character Companion for Android" ||
            parsed.optInt("schema") != 1) return false
        // Parse values before changing preferences, for consistent validation.
        val saved = mutableListOf<Pair<String,String>>()
        val array = parsed.optJSONArray("bookmarks") ?: JSONArray()
        for (i in 0 until minOf(array.length(), 100)) {
            val obj = array.optJSONObject(i) ?: continue
            val u = obj.optString("url")
            OfflineBookmarkEditor.normalize(obj.optString("title"), u)?.let { saved.add(it) }
        }
        // Publish one atomic preference transaction: a crash cannot leave a half-imported profile.
        val bookmarkArray = JSONArray()
        val unique = saved.asReversed().distinctBy { it.second }.asReversed().takeLast(100)
        unique.forEach { (title, url) ->
            bookmarkArray.put(JSONObject().put("title", title).put("url", url))
        }
        val pins = parsed.optJSONArray("libraryPins") ?: JSONArray()
        val importedPins = (0 until minOf(pins.length(), 100)).map { pins.optString(it) }
        synchronized(historyLock) {
            prefs.edit()
            .putStringSet("library_pins", NativeLibrarySearch.validPins(unique, importedPins))
            .remove("last_visited") // Settings backups contain no navigation history.
            .putBoolean("start_in_native_hub", parsed.optBoolean("startInHub", false))
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
            .putString("library_order", NativeLibrarySearch.order(parsed.optString("libraryOrder")).name)
            .putString("bookmarks", bookmarkArray.toString())
            .apply()
        }
        return true
    }
}
