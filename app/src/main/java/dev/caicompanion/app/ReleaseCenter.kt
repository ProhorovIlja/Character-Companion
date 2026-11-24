package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit release lookup, never a scheduler or APK installer. No auth credentials. */
object ReleaseCenter {
    private const val RELEASES = "https://github.com/ProhorovIlja/Character-Companion/releases"
    private const val LATEST = "https://api.github.com/repos/ProhorovIlja/Character-Companion/releases/latest"
    private val safeTag = Regex("^[A-Za-z0-9._-]{1,80}$")

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity).setTitle(R.string.release_center)
            .setMessage(activity.getString(R.string.release_current,
                BuildConfig.VERSION_NAME, BuildConfig.FLAVOR) + "\n\n" +
                activity.getString(R.string.release_info))
            .setPositiveButton(R.string.release_check) { _, _ -> check(activity) }
            .setNeutralButton(R.string.release_open) { _, _ -> open(activity) }
            .setNegativeButton(R.string.close, null).show()
    }

    private fun open(activity: Activity) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES))
                .addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: RuntimeException) {
            android.widget.Toast.makeText(activity, R.string.release_browser_error,
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun check(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val cancelled = AtomicBoolean(false)
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.release_center)
            .setMessage(R.string.release_checking)
            .setNegativeButton(R.string.cancel, null).create()
        dialog.setOnDismissListener { cancelled.set(true) }
        dialog.show()
        // One bounded request following a button press. No retries, periodic checks, or token storage.
        Thread {
            var connection: HttpsURLConnection? = null
            var result = R.string.release_unavailable
            var tag: String? = null
            try {
                if (!cancelled.get()) {
                    val request = URL(LATEST).openConnection() as HttpsURLConnection
                    connection = request
                    request.instanceFollowRedirects = false
                    request.connectTimeout = 5000
                    request.readTimeout = 5000
                    request.setRequestProperty("Accept", "application/vnd.github+json")
                    request.setRequestProperty("User-Agent", "CharacterCompanion-Android")
                    when (request.responseCode) {
                        404 -> result = R.string.release_none
                        200 -> {
                            val body = request.inputStream.bufferedReader(Charsets.UTF_8).use {
                                BoundedText.read(it, 256_000)
                            }
                            if (body != null && !cancelled.get()) {
                                val json = JSONObject(body)
                                if (!json.optBoolean("draft", true) && !json.optBoolean("prerelease", true)) {
                                    tag = json.optString("tag_name").takeIf(safeTag::matches)
                                    if (tag != null) result = R.string.release_found
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                result = R.string.release_unavailable
            } finally {
                connection?.disconnect()
            }
            activity.runOnUiThread {
                if (cancelled.get() || activity.isFinishing || activity.isDestroyed || !dialog.isShowing)
                    return@runOnUiThread
                dialog.dismiss()
                AlertDialog.Builder(activity).setTitle(R.string.release_center)
                    .setMessage(if (result == R.string.release_found)
                        activity.getString(result, tag.orEmpty()) else activity.getString(result))
                    .setPositiveButton(R.string.release_open) { _, _ -> open(activity) }
                    .setNegativeButton(R.string.close, null).show()
            }
        }.start()
    }
}
