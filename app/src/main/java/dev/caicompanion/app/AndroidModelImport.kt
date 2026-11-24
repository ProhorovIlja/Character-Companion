package dev.caicompanion.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/** Explicit SAF import; no storage permission or external model execution. */
object AndroidModelImport {
    fun picker(speech: Boolean = false): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_MIME_TYPES, if (speech) arrayOf("application/octet-stream", "*/*") else arrayOf("application/octet-stream", "application/x-gguf", "*/*"))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    fun library(context: Context, speech: Boolean = false): NativeModelLibrary {
        val root = context.applicationContext.noBackupFilesDir.toPath()
        return if (speech) NativeModelLibrary(root.resolve("mandy-speech-models-v1"),
            NativeModelLibrary.Format.WHISPER.ceiling, NativeModelLibrary.Format.WHISPER)
        else NativeModelLibrary(root.resolve("mandy-models-v1"))
    }
    /** Invoke on a user-initiated worker. No persistent URI grant needed after copying. */
    fun copy(context: Context, uri: Uri, expectedSha: String? = null,
             cancelled: () -> Boolean = { false }, progress: (Long) -> Unit = {}, speech: Boolean = false): NativeModelLibrary.Model {
        require(uri.scheme == "content")
        val app = context.applicationContext
        val size = app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it >= 0 } else null
        }
        val library = library(app, speech)
        val input = checkNotNull(app.contentResolver.openInputStream(uri)) { "Model file unavailable" }
        return input.use { library.import(it, size, expectedSha, cancelled, progress) }
    }
}
