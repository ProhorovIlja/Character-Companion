package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.view.View

/** One operation per displayed dialog, explicitly initiated by the user.
 * No scheduler, polling, retry or persistent worker. A committed write can finish
 * if the Activity is destroyed, but no UI callback is delivered to that Activity. */
object ManualEditorWork {
    private fun active(activity: Activity, dialog: AlertDialog): Boolean =
        !activity.isFinishing && !activity.isDestroyed && dialog.isShowing

    fun <T> run(activity: Activity, dialog: AlertDialog, editors: List<View> = emptyList(),
                work: () -> T, done: (Result<T>) -> Unit) {
        if (!active(activity, dialog)) return
        val controls = listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL).mapNotNull { dialog.getButton(it) } + editors
        val states = controls.map { it.isEnabled }
        controls.forEach { it.isEnabled = false }
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        fun complete(result: Result<T>) {
            if (!active(activity, dialog)) return
            controls.forEachIndexed { index, view -> view.isEnabled = states[index] }
            dialog.setCancelable(true)
            done(result)
        }
        try {
            Thread({
                val result = runCatching(work)
                activity.runOnUiThread { complete(result) }
            }, "manual-editor-io").start()
        } catch (error: RuntimeException) {
            complete(Result.failure(error))
        }
    }

    fun <T> load(activity: Activity, work: () -> T, done: (Result<T>) -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = AlertDialog.Builder(activity).setMessage(R.string.editor_working)
            .setCancelable(false).create()
        if (SettingsStore(activity).protectScreen)
            dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
        run(activity, dialog, work = work) { result ->
            dialog.dismiss()
            done(result)
        }
    }
}
