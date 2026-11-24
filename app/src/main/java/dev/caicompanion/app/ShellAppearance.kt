package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.util.TypedValue

/** Native shell only. Does not inject CSS, invert content, or alter the web renderer. */
object ShellAppearance {
    fun apply(activity: Activity) {
        if (SettingsStore(activity).amoledShell) activity.setTheme(R.style.AppTheme_Amoled)
    }

    fun color(context: Context, attr: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attr, value, true))
            if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
        else Color.BLACK
    }

    fun choose(activity: Activity) {
        val store = SettingsStore(activity)
        val labels = arrayOf(activity.getString(R.string.shell_theme_dark),
            activity.getString(R.string.shell_theme_amoled))
        AlertDialog.Builder(activity).setTitle(R.string.shell_theme_title)
            .setSingleChoiceItems(labels, if (store.amoledShell) 1 else 0) { dialog, choice ->
                dialog.dismiss()
                val amoled = choice == 1
                if (amoled != store.amoledShell) {
                    AlertDialog.Builder(activity).setMessage(R.string.shell_theme_restart)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.confirm) { _, _ ->
                            store.amoledShell = amoled
                            activity.recreate()
                        }.show()
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }
}
