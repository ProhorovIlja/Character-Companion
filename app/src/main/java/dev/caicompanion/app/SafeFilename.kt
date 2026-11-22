package dev.caicompanion.app

/** Sanitize an untrusted filename before showing it and passing it to DownloadManager. */
object SafeFilename {
    fun from(raw: String?): String {
        val cleaned = (raw ?: "").map { ch ->
            if (ch.isISOControl() || ch in "\\/:*?\"<>|" || ch == '\u202E' || ch == '\u202D') '_' else ch
        }.joinToString("").trim().trim('.', ' ').take(120)
        return cleaned.ifBlank { "download" }
    }
}
