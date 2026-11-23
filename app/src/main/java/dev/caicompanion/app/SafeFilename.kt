package dev.caicompanion.app

/** Sanitize an untrusted filename before showing it and passing it to DownloadManager. */
object SafeFilename {
    fun from(raw: String?): String {
        val cleaned = (raw ?: "").map { ch ->
            if (ch.isISOControl() || ch in "\\/:*?\"<>|" || ch in '\u202A'..'\u202E' || ch in '\u2066'..'\u2069' || ch == '\u200E' || ch == '\u200F') '_' else ch
        }.joinToString("").trim().trim('.', ' ').take(120)
        return cleaned.ifBlank { "download" }
    }
}
