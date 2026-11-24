package dev.caicompanion.app

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID

/** Explicitly saved, private drafts. All operations are serialized within the app process. */
object CharacterDraftStore {
    const val MAX_DRAFTS = 16
    private val idPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    data class Item(val id: String, val header: CharacterDraftCodec.Header?)

    private fun directory(context: Context): File = File(context.noBackupFilesDir, "character-drafts-v1").also {
        check(it.isDirectory || it.mkdirs())
    }
    private fun file(dir: File, id: String): AtomicFile {
        require(idPattern.matches(id))
        return AtomicFile(File(dir, "$id.bin"))
    }

    private fun ids(dir: File): Set<String> = (dir.list() ?: error("draft directory unavailable"))
        .mapNotNull { name ->
            val id = when {
                name.endsWith(".bin.bak") -> name.removeSuffix(".bin.bak")
                name.endsWith(".bin.new") -> name.removeSuffix(".bin.new")
                name.endsWith(".bin") -> name.removeSuffix(".bin")
                else -> return@mapNotNull null
            }
            id.takeIf(idPattern::matches)
        }.toSet()

    private fun <T> read(file: AtomicFile, decode: (java.io.InputStream) -> T): T =
        file.openRead().use { input ->
            require(file.baseFile.length() in 1..CharacterDraftCodec.MAX_FILE_BYTES)
            decode(input.buffered())
        }

    @Synchronized fun list(context: Context): List<Item> {
        val dir = directory(context)
        val existing = ids(dir)
        check(existing.size <= MAX_DRAFTS)
        return existing.map { id ->
            Item(id, runCatching { read(file(dir, id), CharacterDraftCodec::header) }.getOrNull())
        }.sortedWith(compareBy<Item> { it.header == null }
            .thenBy { it.header?.name.orEmpty().lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
    }

    @Synchronized fun load(context: Context, id: String): CharacterDraftPolicy.Draft =
        read(file(directory(context), id), CharacterDraftCodec::read)

    @Synchronized fun save(context: Context, existingId: String?, draft: CharacterDraftPolicy.Draft): String {
        val dir = directory(context)
        val existing = ids(dir)
        check(existing.size <= MAX_DRAFTS)
        val id = if (existingId != null) {
            require(existingId in existing)
            existingId
        } else {
            check(existing.size < MAX_DRAFTS)
            UUID.randomUUID().toString().also { check(it !in existing) }
        }
        val target = file(dir, id)
        val stream = target.startWrite()
        try {
            CharacterDraftCodec.write(draft, stream.buffered())
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
        check(read(target, CharacterDraftCodec::read) == draft)
        return id
    }

    @Synchronized fun delete(context: Context, id: String) {
        val dir = directory(context)
        file(dir, id).delete()
        check(id !in ids(dir))
    }
}
