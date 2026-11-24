package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ClipData
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
        val arm64 = "arm64-v8a" in Build.SUPPORTED_ABIS
        if (activity.isFinishing || activity.isDestroyed || BuildConfig.DEBUG ||
            ReleasePolicy.status(BuildConfig.VERSION_NAME, tag, BuildConfig.FLAVOR, listOf(asset), arm64) !=
                ReleasePolicy.Status.NEWER) return
        // Do not spend hundreds of MiB on an APK before the install-source permission is available.
        if (busy.get()) return
        if (!installationAllowed(activity)) {
            showInstallPermission(activity)
            return
        }
        if (!busy.compareAndSet(false, true)) return
        val cancelled = AtomicBoolean(false)
        val progress = AlertDialog.Builder(activity).setTitle(R.string.release_center)
            .setMessage(R.string.release_downloading).setNegativeButton(R.string.cancel, null).create()
        ForegroundUpdateUi.show(activity, progress) { cancelled.set(true) }
        if (!progress.isShowing) { busy.set(false); return }
        val startedAt = android.os.SystemClock.elapsedRealtime()
        Thread {
            var file: File? = null
            var verified = false
            try {
                val dir = File(activity.cacheDir, "verified-updates")
                if (!dir.exists() && !dir.mkdirs()) error("cache")
                // An installer may still be reading a previously shared APK.
                // Reclaim only old staging files during an explicit new request.
                val cutoff = System.currentTimeMillis() - 86_400_000L
                dir.listFiles()?.filter {
                    it.isFile && it.name.startsWith("candidate-") && it.name.endsWith(".apk") &&
                        it.lastModified() < cutoff
                }?.forEach { it.delete() }
                if (dir.usableSpace < asset.size + 16_777_216L) error("space")
                val retained = dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
                if (retained + asset.size > 1_073_741_824L) error("staging limit")
                val target = File.createTempFile("candidate-", ".apk", dir)
                file = target
                fun expired(): Boolean = cancelled.get() ||
                    android.os.SystemClock.elapsedRealtime() - startedAt >= 300_000L
                verified = fetch(asset, target, ::expired) && !expired() &&
                    verifyPackage(activity, target, tag) && !expired()
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
                var handedOff = false
                if (verified && candidate != null) {
                    result.setPositiveButton(R.string.release_install) { _, _ ->
                        // The archive was verified off the UI thread in private cache.
                        // The Android installer independently verifies signature and version.
                        handedOff = install(activity, candidate, asset.size)
                    }
                }
                ForegroundUpdateUi.show(activity, result.create()) {
                    if (!handedOff) candidate?.delete()
                    busy.set(false)
                }
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
        fun identity(pkg: PackageInfo): ReleasePolicy.ArchiveIdentity? {
            val info = pkg.applicationInfo ?: return null
            return ReleasePolicy.ArchiveIdentity(pkg.packageName, pkg.longVersionCode,
                pkg.versionName.orEmpty(), info.metaData?.getString(RENDERER),
                info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                pkg.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet())
        }
        val current = identity(installed) ?: return false
        val candidate = identity(archive) ?: return false
        // Same signer only, including the migration from the internal legacy build.
        return current.packageName == activity.packageName &&
            ReleasePolicy.acceptsArchive(BuildConfig.FLAVOR, "arm64-v8a" in Build.SUPPORTED_ABIS,
                current, tag, candidate)
    }

    private fun installationAllowed(activity: Activity): Boolean =
        runCatching { activity.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    private fun showInstallPermission(activity: Activity) {
        val prompt = AlertDialog.Builder(activity).setMessage(R.string.release_install_permission)
            .setPositiveButton(R.string.release_install_settings) { _, _ ->
                try {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.packageName)))
                } catch (_: RuntimeException) {
                    android.widget.Toast.makeText(activity, R.string.release_verify_failed,
                        android.widget.Toast.LENGTH_LONG).show()
                }
            }.setNegativeButton(R.string.cancel, null).create()
        ForegroundUpdateUi.show(activity, prompt)
    }

    private fun install(activity: Activity, file: File, expectedSize: Long): Boolean {
        try {
            if (!file.isFile || file.length() != expectedSize) error("archive")
            if (!installationAllowed(activity)) {
                showInstallPermission(activity)
                return false
            }
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".updates", file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, MIME).apply {
                clipData = ClipData.newRawUri("Verified update", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            return true
        } catch (_: Exception) {
            android.widget.Toast.makeText(activity, R.string.release_verify_failed,
                android.widget.Toast.LENGTH_LONG).show()
            return false
        }
    }
}
