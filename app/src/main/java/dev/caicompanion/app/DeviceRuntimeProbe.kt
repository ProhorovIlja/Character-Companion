package dev.caicompanion.app

import android.app.Activity
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
        // Morphe's MicroG-RE uses an independent application ID. It can be installed
        // alongside MindTheGapps without replacing com.google.android.gms.
        // Package presence is informational only; neither package proves OAuth.
        val googleServices = probePackage(activity.packageManager, "com.google.android.gms")
        val morpheServices = probePackage(activity.packageManager, "app.morphe.android.gms")
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
            morpheMicrogVisible = morpheServices.first,
            morpheMicrogVersion = morpheServices.second
        )
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
