package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Static official guidance only. No account access, network probe, retry or automatic redemption. */
object FreeExperienceHelp {
    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity).setTitle(R.string.free_experience_title)
            .setMessage(R.string.free_experience_summary)
            .setPositiveButton(R.string.free_experience_charms) { _, _ ->
                open(activity, "https://support.character.ai/hc/en-us/articles/43610534413211-Charms-FAQ")
            }
            .setNeutralButton(R.string.free_experience_status) { _, _ ->
                open(activity, "https://status.character.ai/")
            }
            .setNegativeButton(R.string.close, null).show()
    }

    private fun open(activity: Activity, url: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: RuntimeException) {
            Toast.makeText(activity, R.string.error_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
