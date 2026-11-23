package dev.caicompanion.app

import kotlin.math.roundToInt

/**
 * A deliberately small, privacy-preserving runtime capability profile.
 * This is information about a device class and installed components, NOT
 * an attestation of the ROM, Google authentication, or actual app behavior.
 * No fingerprints, serials, Google accounts, installed-app lists or URLs.
 */
object DeviceCompatibility {
    data class Snapshot(
        val manufacturer: String?,
        val model: String?,
        val device: String?,
        val androidApi: Int,
        val androidRelease: String?,
        val supportedAbis: List<String>,
        val refreshRateHz: Float?,
        val memoryPageBytes: Long?,
        val webViewPackage: String?,
        val webViewVersion: String?,
        /** A package called com.google.android.gms; NOT evidence of genuine GMS or working OAuth. */
        val gmsCompatiblePackageVisible: Boolean?,
        val gmsPackageVersion: String? = null,
        /** Modern Morphe MicroG-RE application ID; unrelated to system Google Play Services. */
        val morpheMicrogCurrentVisible: Boolean? = null,
        val morpheMicrogCurrentVersion: String? = null,
        /** Historical Morphe ID; kept for older installations, not a GMS replacement. */
        val morpheMicrogVisible: Boolean? = null,
        val morpheMicrogVersion: String? = null
    )

    private val safeLabel = Regex("^[A-Za-z0-9._+ -]{1,64}$")
    private val safeVersion = Regex("^[A-Za-z0-9._+-]{1,80}$")
    private val allowedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    private fun label(value: String?): String =
        if (value != null && safeLabel.matches(value) && value.isNotBlank()) value else "unknown"

    private fun version(value: String?): String =
        if (value != null && safeVersion.matches(value)) value else "unknown"

    fun isPixel8a(snapshot: Snapshot): Boolean =
        snapshot.manufacturer.equals("Google", ignoreCase = true) &&
            (snapshot.device.equals("akita", ignoreCase = true) ||
                snapshot.model.equals("Pixel 8a", ignoreCase = true))

    fun format(snapshot: Snapshot): String = buildString {
        appendLine("Character Companion — device compatibility")
        appendLine("Hardware family: ${if (isPixel8a(snapshot)) "Pixel 8a (akita)" else "other / unverified"}")
        appendLine("Manufacturer: ${label(snapshot.manufacturer)}")
        appendLine("Model: ${label(snapshot.model)}")
        appendLine("Device codename: ${label(snapshot.device)}")
        appendLine("Android: ${version(snapshot.androidRelease)} (API ${snapshot.androidApi.coerceIn(0, 999)})")
        appendLine("Android 17: ${if (snapshot.androidApi >= 37) "detected by API" else "not detected"}")
        val abis = snapshot.supportedAbis.filter { it in allowedAbis }.distinct().take(4)
        appendLine("Supported ABI: ${abis.ifEmpty { listOf("unknown") }.joinToString(", ")}")
        val refresh = snapshot.refreshRateHz
        appendLine("Current display rate: ${if (refresh != null && refresh.isFinite() && refresh in 24f..240f) "${refresh.roundToInt()} Hz" else "unknown"}")
        appendLine("Kernel page size: ${when (snapshot.memoryPageBytes) {
            4096L -> "4 KiB"
            16384L -> "16 KiB"
            65536L -> "64 KiB"
            else -> "unknown"
        }}")
        appendLine("WebView provider: ${version(snapshot.webViewPackage)}")
        appendLine("WebView version: ${version(snapshot.webViewVersion)}")
        appendLine("GMS-compatible package visible: ${when (snapshot.gmsCompatiblePackageVisible) {
            true -> "yes (auth compatibility NOT verified)"
            false -> "no (browser-based sign-in may differ)"
            null -> "unknown"
        }}")
        appendLine("System GMS package version: ${if (snapshot.gmsCompatiblePackageVisible == true) version(snapshot.gmsPackageVersion) else "not available"}")
        appendLine("Morphe microG current (app.revanced.android.gms): ${when (snapshot.morpheMicrogCurrentVisible) {
            true -> "visible (package identity only; vendor unknown; does NOT prove Character.AI sign-in)"
            false -> "not visible"
            null -> "unknown"
        }}")
        appendLine("Morphe current package version: ${if (snapshot.morpheMicrogCurrentVisible == true) version(snapshot.morpheMicrogCurrentVersion) else "not available"}")
        appendLine("Morphe microG legacy (app.morphe.android.gms): ${when (snapshot.morpheMicrogVisible) {
            true -> "visible (older ID; does NOT prove Character.AI sign-in)"
            false -> "not visible"
            null -> "unknown"
        }}")
        appendLine("Morphe legacy package version: ${if (snapshot.morpheMicrogVisible == true) version(snapshot.morpheMicrogVersion) else "not available"}")
        appendLine("GMS and Morphe microG have different package IDs; do not substitute one for the other.")
        appendLine("LineageOS version: not attested by app; check System Settings or manual ADB diagnostic")
        appendLine("OAuth, voice, Play Protect and performance: NOT TESTED by this report")
        append("No device serial, build fingerprint, account, conversation, URL or auth token is collected.")
    }
}
