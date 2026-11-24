package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

/** Shared user-initiated settings backup, without renderer or authentication data. */
class SettingsBackup(private val activity: Activity) {
    companion object {
        const val MAX_CHARS = 6_000_000
        private const val EXPORT = 781
        private const val IMPORT = 782
        private val busy = AtomicBoolean(false)
    }
    private val closed = AtomicBoolean(false)
    private var pending = 0

    fun show() {
        AlertDialog.Builder(activity).setTitle(R.string.section_data)
            .setItems(arrayOf(activity.getString(R.string.export_settings),
                activity.getString(R.string.import_settings))) { _, which ->
                if (which == 0) export() else restore()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    fun export() = pick(EXPORT)

    fun restore() {
        if (closed.get() || activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity).setTitle(R.string.import_settings)
            .setMessage(R.string.backup_restore_warning)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> pick(IMPORT) }.show()
    }

    private fun pick(request: Int) {
        if (closed.get() || activity.isFinishing || activity.isDestroyed || pending != 0 || busy.get()) return
        try {
            pending = request
            activity.startActivityForResult(Intent(if (request == EXPORT) Intent.ACTION_CREATE_DOCUMENT
                else Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                if (request == EXPORT) putExtra(Intent.EXTRA_TITLE, "character-companion-settings.json")
            }, request)
        } catch (_: RuntimeException) {
            pending = 0
            toast(if (request == EXPORT) R.string.export_failure else R.string.import_failure)
        }
    }

    /** Restoring a destroyed Activity drops its old document result instead of applying it implicitly. */
    fun receive(request: Int, result: Int, data: Intent?): Boolean {
        if (request != EXPORT && request != IMPORT) return false
        if (pending != request) return true
        pending = 0
        if (result != Activity.RESULT_OK || closed.get()) return true
        val uri = data?.data
        if (uri == null || uri.scheme != "content" || uri.authority.isNullOrBlank()) {
            toast(if (request == EXPORT) R.string.export_failure else R.string.import_failure)
            return true
        }
        if (!busy.compareAndSet(false, true)) return true
        val context = activity.applicationContext
        val store = SettingsStore(context)
        Thread({
            var message = if (request == EXPORT) R.string.export_failure else R.string.import_failure
            var restored = false
            try {
                if (!closed.get()) {
                    if (request == EXPORT) {
                        val snapshot = store.exportJson()
                        require(snapshot.length <= MAX_CHARS)
                        if (!closed.get()) {
                            val output = context.contentResolver.openOutputStream(uri, "w")
                                ?: error("missing output")
                            output.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
                            message = R.string.export_success
                        }
                    } else {
                        val contents = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)?.use {
                                BoundedText.read(it, MAX_CHARS) { closed.get() }
                            }
                        if (!closed.get()) {
                            restored = contents != null && store.importJson(contents)
                            message = if (restored) R.string.import_success else R.string.import_unsupported
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep the existing failure status.
            } finally {
                busy.set(false)
            }
            activity.runOnUiThread {
                if (closed.get() || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                toast(message)
                // Explicitly approved restore replaces shell settings; rebuild rather than leave stale controls.
                if (restored) activity.recreate()
            }
        }, "manual-settings-backup").start()
        return true
    }

    fun close() { closed.set(true); pending = 0 }

    private fun toast(message: Int) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
