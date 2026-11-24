package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import java.util.IdentityHashMap

/** Main-thread-only dialog ownership. Stop/destroy cancels explicit update work. */
object ForegroundUpdateUi {
    private val dialogs = IdentityHashMap<Activity, AlertDialog>()

    fun show(activity: Activity, dialog: AlertDialog, onDismiss: () -> Unit = {}) {
        cancel(activity)
        if (activity.isFinishing || activity.isDestroyed) {
            onDismiss()
            return
        }
        dialogs[activity] = dialog
        var completed = false
        fun complete() {
            if (completed) return
            completed = true
            if (dialogs[activity] === dialog) dialogs.remove(activity)
            onDismiss()
        }
        dialog.setOnDismissListener { complete() }
        try {
            dialog.show()
        } catch (_: RuntimeException) {
            runCatching { dialog.dismiss() }
            complete()
        }
    }

    fun cancel(activity: Activity) {
        dialogs.remove(activity)?.dismiss()
    }
}
