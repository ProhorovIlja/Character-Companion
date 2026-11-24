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
        dialog.setOnDismissListener {
            if (dialogs[activity] === dialog) dialogs.remove(activity)
            onDismiss()
        }
        try {
            dialog.show()
        } catch (_: RuntimeException) {
            if (dialogs[activity] === dialog) dialogs.remove(activity)
            onDismiss()
        }
    }

    fun cancel(activity: Activity) {
        dialogs.remove(activity)?.dismiss()
    }
}
