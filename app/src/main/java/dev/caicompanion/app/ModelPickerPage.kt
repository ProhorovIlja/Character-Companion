package dev.caicompanion.app

import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Locale

/** Read only the visible page on a foreground request worker; identity stays bound to the digest. */
object ModelPickerPage {
    const val PAGE_SIZE = 16
    data class Entry(val model: NativeModelLibrary.Model, val label: String)
    data class Page(val entries: List<Entry>, val offset: Int, val total: Int) {
        val hasPrevious: Boolean get() = offset > 0
        val hasNext: Boolean get() = total - offset > entries.size
    }
    fun read(models: List<NativeModelLibrary.Model>, offset: Int = 0): Page {
        require(offset >= 0 && offset % PAGE_SIZE == 0 && (offset < models.size || offset == 0))
        val entries = models.drop(offset).take(PAGE_SIZE).map { model ->
            val summary = try {
                Files.newInputStream(model.path, LinkOption.NOFOLLOW_LINKS).use { GgufSummary.read(it) }
            } catch (_: java.io.IOException) { null }
              catch (_: SecurityException) { null }
            val label = buildString {
                append(summary?.name ?: "Unnamed GGUF model")
                append("\nArchitecture: ").append(summary?.architecture ?: "Unknown")
                append(" · ").append(String.format(Locale.ROOT, "%.2f GiB", model.bytes / 1073741824.0))
                append("\nSHA-256: ").append(model.sha256)
            }
            Entry(model, label)
        }
        return Page(entries, offset, models.size)
    }
}
