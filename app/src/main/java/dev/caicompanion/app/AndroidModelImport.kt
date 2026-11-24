package dev.caicompanion.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/** Explicit SAF import; no storage permission or external model execution. */
object AndroidModelImport {
    fun picker(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/x-gguf", "*/*"))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    /** Invoke on a user-initiated worker. No persistent URI grant needed after copying. */
    fun copy(context: Context, uri: Uri, expectedSha: String? = null,
             cancelled: () -> Boolean = { false }, progress: (Long) -> Unit = {}): NativeModelLibrary.Model {
        require(uri.scheme == "content")
        val app = context.applicationContext
        val size = app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it >= 0 } else null
        }
        val library = NativeModelLibrary(app.noBackupFilesDir.toPath().resolve("mandy-models-v1"))
        val input = checkNotNull(app.contentResolver.openInputStream(uri)) { "Model file unavailable" }
        return input.use { library.import(it, size, expectedSha, cancelled, progress) }
    }
}
