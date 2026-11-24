package dev.caicompanion.app

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PowerManager
import android.speech.SpeechRecognizer
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.webkit.WebView

/** Read-only, on-demand Android runtime inspection. Never requires device or account access. */
object DeviceRuntimeProbe {
    fun capture(activity: Activity): DeviceCompatibility.Snapshot {
        val provider = if (Build.VERSION.SDK_INT >= 26) {
            runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
        } else null
        // Morphe MicroG-RE changed its Android applicationId to
        // app.revanced.android.gms (upstream commit 2910276, August 2026).
        // Some older distributions use app.morphe.android.gms. These are NOT
        // the system com.google.android.gms service, and package presence alone
        // never attests OAuth, signature, or authentication compatibility.
        val googleServices = probePackage(activity.packageManager, "com.google.android.gms")
        val morpheCurrent = probePackage(activity.packageManager, "app.revanced.android.gms")
        val morpheLegacy = probePackage(activity.packageManager, "app.morphe.android.gms")
        val refresh = runCatching {
            if (Build.VERSION.SDK_INT >= 30) activity.display?.refreshRate
            else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.refreshRate
            }
        }.getOrNull()
        return DeviceCompatibility.Snapshot(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            androidApi = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            refreshRateHz = refresh,
            memoryPageBytes = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrNull(),
            webViewPackage = provider?.packageName,
            webViewVersion = provider?.versionName,
            gmsCompatiblePackageVisible = googleServices.first,
            gmsPackageVersion = googleServices.second,
            morpheMicrogCurrentVisible = morpheCurrent.first,
            morpheMicrogCurrentVersion = morpheCurrent.second,
            morpheMicrogVisible = morpheLegacy.first,
            morpheMicrogVersion = morpheLegacy.second
        )
    }
    /** No listeners, wake locks, package inventory or background checks. */
    fun capabilities(activity: Activity, renderer: String): DeviceRuntimeCapabilities.Snapshot {
        val manager = activity.getSystemService(ActivityManager::class.java)
        val power = activity.getSystemService(PowerManager::class.java)
        val memory = runCatching {
            manager?.let { service ->
                ActivityManager.MemoryInfo().also { service.getMemoryInfo(it) }.totalMem / 1_048_576L
            }
        }.getOrNull()
        return DeviceRuntimeCapabilities.Snapshot(
            renderer = renderer,
            totalRamMiB = memory,
            appHeapClassMiB = runCatching { manager?.memoryClass }.getOrNull(),
            lowRamDevice = runCatching { manager?.isLowRamDevice }.getOrNull(),
            powerSave = runCatching { power?.isPowerSaveMode }.getOrNull(),
            thermalStatus = runCatching { power?.currentThermalStatus }.getOrNull(),
            localSpeechAvailable = if (Build.VERSION.SDK_INT >= 31) runCatching {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)
            }.getOrNull() else false
        )
    }

    fun show(activity: Activity, renderer: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val snapshot = capture(activity)
        val report = DeviceCompatibility.format(snapshot) + "\n\n" +
            DeviceRuntimeCapabilities.format(capabilities(activity, renderer))
        AlertDialog.Builder(activity).setTitle(R.string.device_compat_title)
            .setMessage(report)
            .setNeutralButton(R.string.diagnostics_copy) { _, _ ->
                runCatching {
                    val clipboard = activity.getSystemService(ClipboardManager::class.java)
                        ?: error("clipboard unavailable")
                    clipboard.setPrimaryClip(ClipData.newPlainText("Device compatibility", report))
                }.onSuccess {
                    Toast.makeText(activity, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
                }
            }.setPositiveButton(R.string.confirm, null).show()
    }

    /** Returns null visibility only when Android explicitly denies inspection. */
    private fun probePackage(pm: PackageManager, name: String): Pair<Boolean?, String?> = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(name, 0)
        }
        true to info.versionName
    } catch (_: PackageManager.NameNotFoundException) {
        false to null
    } catch (_: SecurityException) {
        null to null
    }
}
