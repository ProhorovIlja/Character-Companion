# Manual Android build from Windows 10/11 — Pixel 8a (`akita`), Android 17

This is a **one-time user-initiated command sequence**, not a CI workflow, scheduled task, unattended operation or background monitor. The current sources are an experimental client for Character.AI, not an official application.

## Prerequisites and boundaries

Install a trusted Java JDK 17 or newer, Android Studio/Android SDK with platform `android-37.0` and platform-tools, and Git for Windows. The repository pins Gradle **9.3.1** using a checksum-verified Gradle 8.13 bootstrap wrapper JAR, AGP **9.1.1**, Android compile API **37** and target API **36**. The device runtime target is Android 17 (API 37); **compiling with API 37 is not the same as targeting API 37**. The compile toolchain was migrated after a real GitHub Actions AAR metadata failure; compileSdk 37 does not mean targetSdk 37 or device validation.

Clone the full repository to use the SHA-256-verified Gradle Wrapper. Alternatively, extract a lightweight source ZIP and install a trusted **Gradle 9.3.1** into PATH: the manual Windows build script verifies that exact installed version and refuses others. The source ZIP intentionally excludes `gradle-wrapper.jar`. There is no need to install system WebView for the `gecko` flavor: its Firefox/Gecko libraries are bundled at packaging time. The `legacy` flavor uses the Android System WebView provider. The two flavors use the same package name and cannot safely be installed side by side without modifying the package ID.

Open PowerShell interactively from the repository root:

```powershell
# One-time interactive developer build; PowerShell command does not install an SDK.
.\scripts\build-apk-windows.ps1 gecko
# Alternative fallback: .\scripts\build-apk-windows.ps1 legacy
```

If PowerShell's script execution policy refuses local scripts, review the source and execute individual commands rather than disabling your machine's security policy globally. The build helper checks the official wrapper JAR SHA-256 (`81a82aae...c7ae45f`) and the Gradle distribution ZIP checksum, **or** verifies that locally installed Gradle reports exactly 9.3.1. It then checks Java and Android SDK platform 37, deletes any previous output APK, and invokes `:app:assembleGeckoDebug` or `:app:assembleLegacyDebug`. It prints the output path and SHA-256 only when the new debug APK exists and has non-empty `AndroidManifest.xml` and `classes.dex` entries. This limited ZIP inspection does **not** verify the APK installs or runs on Android 17.

For direct Gradle inspection from a full GitHub checkout with wrapper:

```powershell
.\gradlew.bat --version
.\gradlew.bat --no-daemon :app:assembleGeckoDebug
```

For the ZIP-based build, run the same PowerShell script after installing Gradle 9.3.1; do **not** invoke `gradlew.bat` without its verified wrapper JAR. Linux/macOS users can run `bash scripts/build-apk.sh gecko` or `legacy` with either a verified full wrapper or installed Gradle 9.3.1. Neither helper auto-installs an SDK or triggers scheduled jobs.

## Optional manual device verification

Enable developer options and USB debugging only if you choose to connect a trusted computer. Check the actual phone/ROM information before installing:

```powershell
adb devices
adb shell getprop ro.product.device
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abi
```

Expect Pixel 8a device name `akita`, ARM64 and Android 17/API 37 **only if those values are really reported by the device**. Never claim verification solely from an online specification. A debug build is not a signed production release, may overwrite a same-package installation and may trigger Play Protect. Back up user data before any installation, and do not weaken Android's integrity checks merely to silence warnings.

## Release gate

On the actual device, separately test launch, tab history, bookmarks/settings, login and Google-browser fallback, text chat, attached files, bounded downloads, microphone/camera permissions (including navigation during an outstanding prompt), app backgrounding, orientation, renderer recovery, performance, battery and security prompts. Compare `legacy` and `gecko` feature parity. Full CI, unattended builds and scheduled checks remain prohibited by the user's project requirements. No ready APK or verified Pixel 8a results are implied by this document.

**SDK Manager package naming (manual build #3):** Android 17's API 37.0 platform is
`platforms;android-37.0`, installed at `platforms/android-37.0/android.jar`.
The unsuffixed `platforms;android-37` identifier does not exist in the SDK package index.
With AGP 9.1.1 this project explicitly pins `release(37) { minorApiLevel = 0 }`
to match that SDK. The repository's manual workflow installs 37.0; users can
run `sdkmanager --install 'platforms;android-37.0' 'build-tools;36.0.0'`.
Do not rename platform directories or copy `android.jar` from another platform.
