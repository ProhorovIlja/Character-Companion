package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.atomic.AtomicBoolean

/** User-triggered download to private cache. Never silently install or schedule work. */
object UpdateDownload {
    private val busy = AtomicBoolean(false)
    private const val RENDERER = "dev.caicompanion.app.RENDERER"
    private const val MIME = "application/vnd.android.package-archive"

    fun start(activity: Activity, tag: String, asset: ReleasePolicy.Asset) {
        if (activity.isFinishing || activity.isDestroyed ||
            ReleasePolicy.status(BuildConfig.VERSION_NAME, tag, BuildConfig.FLAVOR, listOf(asset)) !=
                ReleasePolicy.Status.NEWER || !busy.compareAndSet(false, true)) return
        val cancelled = AtomicBoolean(false)
        val progress = AlertDialog.Builder(activity).setTitle(R.string.release_center)
            .setMessage(R.string.release_downloading).setNegativeButton(R.string.cancel, null).create()
        progress.setOnDismissListener { cancelled.set(true) }
        progress.show()
        Thread {
            var file: File? = null
            var verified = false
            try {
                val dir = File(activity.cacheDir, "verified-updates")
                if (!dir.exists() && !dir.mkdirs()) error("cache")
                if (dir.usableSpace < asset.size + 16_777_216L) error("space")
                // Keep at most one staged APK. Never reuse an existing unverified file.
                dir.listFiles()?.forEach { it.delete() }
                val target = File.createTempFile("candidate-", ".apk", dir)
                file = target
                verified = fetch(asset, target) { cancelled.get() || activity.isDestroyed || activity.isFinishing } &&
                    !cancelled.get() && verifyPackage(activity, target, tag)
            } catch (_: Exception) {
                verified = false
            }
            if (!verified || cancelled.get()) file?.delete()
            val candidate = file
            activity.runOnUiThread {
                if (cancelled.get() || activity.isDestroyed || activity.isFinishing || !progress.isShowing) {
                    candidate?.delete()
                    busy.set(false)
                    return@runOnUiThread
                }
                progress.dismiss()
                val result = AlertDialog.Builder(activity).setTitle(R.string.release_center)
                    .setMessage(if (verified) R.string.release_verified else R.string.release_verify_failed)
                    .setNegativeButton(R.string.close, null)
                if (verified && candidate != null) {
                    result.setPositiveButton(R.string.release_install) { _, _ ->
                        // Android controls install consent and verifies the APK again.
                        install(activity, candidate, tag)
                    }
                }
                val dialog = result.create()
                dialog.setOnDismissListener { busy.set(false) }
                dialog.show()
            }
        }.start()
    }

    private fun fetch(asset: ReleasePolicy.Asset, target: File, cancelled: () -> Boolean): Boolean {
        var address = asset.url
        repeat(4) {
            if (cancelled()) return false
            val connection = URL(address).openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("User-Agent", "CharacterCompanion-Android")
                when (connection.responseCode) {
                    301, 302, 303, 307, 308 -> {
                        val next = connection.getHeaderField("Location") ?: return false
                        if (!UpdateIntegrity.redirectAllowed(next)) return false
                        address = next
                    }
                    200 -> {
                        val length = connection.contentLengthLong
                        if (length >= 0 && length != asset.size) return false
                        return connection.inputStream.use { input ->
                            target.outputStream().use { output ->
                                UpdateIntegrity.copyVerified(input, output, asset.size, asset.digest, cancelled)
                            }
                        }
                    }
                    else -> return false
                }
            } finally {
                connection.disconnect()
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(activity: Activity, file: File, tag: String): Boolean {
        val pm = activity.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_META_DATA
        val installed = pm.getPackageInfo(activity.packageName, flags)
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        val info = archive.applicationInfo ?: return false
        fun signers(pkg: PackageInfo) = pkg.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        val currentSigners = signers(installed) ?: return false
        // Conservative same-signer policy; key rotation requires separate manual release review.
        return archive.packageName == activity.packageName &&
            archive.longVersionCode > installed.longVersionCode &&
            ReleasePolicy.version(archive.versionName.orEmpty()) == ReleasePolicy.version(tag) &&
            info.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0 &&
            info.metaData?.getString(RENDERER) == BuildConfig.FLAVOR &&
            currentSigners.isNotEmpty() && signers(archive) == currentSigners
    }

    private fun install(activity: Activity, file: File, tag: String) {
        try {
            if (!file.isFile || !verifyPackage(activity, file, tag)) error("archive")
            if (!activity.packageManager.canRequestPackageInstalls()) {
                // No automatic return or installation after changing this system setting.
                AlertDialog.Builder(activity).setMessage(R.string.release_install_permission)
                    .setPositiveButton(R.string.release_install_settings) { _, _ ->
                        try {
                            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.packageName)))
                        } catch (_: RuntimeException) {
                            android.widget.Toast.makeText(activity, R.string.release_verify_failed,
                                android.widget.Toast.LENGTH_LONG).show()
                        }
                    }.setNegativeButton(R.string.cancel, null).show()
                return
            }
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".updates", file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) {
            android.widget.Toast.makeText(activity, R.string.release_verify_failed,
                android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
