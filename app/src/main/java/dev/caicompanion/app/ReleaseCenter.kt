package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit release lookup, never a scheduler or silent installer. No auth credentials. */
object ReleaseCenter {
    private const val RELEASES = "https://github.com/ProhorovIlja/Character-Companion/releases"
    private const val LATEST = "https://api.github.com/repos/ProhorovIlja/Character-Companion/releases/latest"


    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val menu = AlertDialog.Builder(activity).setTitle(R.string.release_center)
            .setMessage(activity.getString(R.string.release_current,
                BuildConfig.VERSION_NAME, BuildConfig.FLAVOR) + "\n\n" +
                activity.getString(R.string.release_info))
            .setPositiveButton(R.string.release_check) { _, _ -> check(activity) }
            .setNeutralButton(R.string.release_open) { _, _ -> open(activity) }
            .setNegativeButton(R.string.close, null).create()
        ForegroundUpdateUi.show(activity, menu)
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
        ForegroundUpdateUi.show(activity, dialog) { cancelled.set(true) }
        if (!dialog.isShowing) return
        val arm64 = "arm64-v8a" in Build.SUPPORTED_ABIS
        val startedAt = android.os.SystemClock.elapsedRealtime()
        fun expired(): Boolean = cancelled.get() ||
            android.os.SystemClock.elapsedRealtime() - startedAt >= 15_000L
        // One bounded request following a button press. No retries, periodic checks, or token storage.
        Thread {
            var connection: HttpsURLConnection? = null
            var result = R.string.release_unavailable
            var tag: String? = null
            var candidate: ReleasePolicy.Asset? = null
            try {
                if (!expired()) {
                    val request = URL(LATEST).openConnection() as HttpsURLConnection
                    connection = request
                    request.instanceFollowRedirects = false
                    request.connectTimeout = 5000
                    request.readTimeout = 5000
                    request.setRequestProperty("Accept", "application/vnd.github+json")
                    request.setRequestProperty("User-Agent", "CharacterCompanion-Android")
                    when (val code = request.responseCode) {
                        403, 429 -> if (!expired() && UpdateProgress.rateLimited(code,
                            request.getHeaderField("X-RateLimit-Remaining"), request.getHeaderField("Retry-After"))) {
                            result = R.string.release_rate_limited
                        }
                        404 -> if (!expired()) result = R.string.release_none
                        200 -> {
                            val body = request.inputStream.bufferedReader(Charsets.UTF_8).use {
                                BoundedText.read(it, 256_000, ::expired)
                            }
                            if (body != null && !expired()) {
                                val json = JSONObject(body)
                                if (!json.optBoolean("draft", true) && !json.optBoolean("prerelease", true)) {
                                    val remote = json.optString("tag_name")
                                    val entries = json.optJSONArray("assets")
                                    val assets = if (entries != null && entries.length() <= 100) {
                                        (0 until entries.length()).mapNotNull { i ->
                                            val item = entries.optJSONObject(i) ?: return@mapNotNull null
                                            ReleasePolicy.Asset(item.optString("name"),
                                                item.optString("browser_download_url"),
                                                item.optLong("size", -1), item.optString("digest"),
                                                item.optString("state"))
                                        }
                                    } else emptyList()
                                    tag = remote.takeIf { ReleasePolicy.version(it) != null }
                                    candidate = ReleasePolicy.selectAsset(remote, BuildConfig.FLAVOR, assets, arm64)
                                    result = when (ReleasePolicy.status(BuildConfig.VERSION_NAME,
                                        remote, BuildConfig.FLAVOR, assets, arm64)) {
                                        ReleasePolicy.Status.CURRENT -> R.string.release_current_match
                                        ReleasePolicy.Status.OLDER -> R.string.release_older
                                        ReleasePolicy.Status.NEWER_NO_APK -> R.string.release_no_compatible
                                        ReleasePolicy.Status.NEWER -> R.string.release_update_candidate
                                        ReleasePolicy.Status.UNKNOWN -> R.string.release_unavailable
                                    }
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
            if (expired()) {
                result = R.string.release_unavailable
                candidate = null
                tag = null
            }
            activity.runOnUiThread {
                if (cancelled.get() || activity.isFinishing || activity.isDestroyed || !dialog.isShowing)
                    return@runOnUiThread
                dialog.dismiss()
                val message = if (result in setOf(R.string.release_current_match, R.string.release_older,
                    R.string.release_no_compatible, R.string.release_update_candidate))
                    activity.getString(result, tag.orEmpty()) else activity.getString(result)
                val note = when {
                    result != R.string.release_update_candidate -> ""
                    BuildConfig.DEBUG -> "\n\n" + activity.getString(R.string.release_debug_migration)
                    BuildConfig.FLAVOR == "legacy" -> "\n\n" + activity.getString(R.string.release_combined_migration)
                    else -> ""
                }
                val details = AlertDialog.Builder(activity).setTitle(R.string.release_center)
                    .setMessage(message + note)
                    .setPositiveButton(R.string.release_open) { _, _ -> open(activity) }
                    .setNegativeButton(R.string.close, null)
                val selected = candidate
                val selectedTag = tag
                if (!BuildConfig.DEBUG && result == R.string.release_update_candidate && selected != null && selectedTag != null) {
                    details.setNeutralButton(R.string.release_download) { _, _ ->
                        UpdateDownload.start(activity, selectedTag, selected)
                    }
                }
                ForegroundUpdateUi.show(activity, details.create())
            }
        }.start()
    }
}
