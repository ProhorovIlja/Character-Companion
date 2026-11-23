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
        val compatiblePackage = try {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.packageManager.getPackageInfo("com.google.android.gms", PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                activity.packageManager.getPackageInfo("com.google.android.gms", 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: SecurityException) {
            null
        }
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
            gmsCompatiblePackageVisible = compatiblePackage
        )
    }
}
