# Mandy AI — manual embedded build gate (2026-11-24)

scripts/build_mandy_embedded.py is a foreground, opt-in helper for Windows and Linux (Python 3.11+, Java, Git). Default is a read-only preflight; --build explicitly invokes the existing Gradle wrapper with the native engine property. No new workflows, scheduling, dispatch, device installation, signing-key creation by this script, upload, or publishing. Gradle may download its pinned distribution and declared build dependencies and create the usual debug key during a user-requested build. Prepare a trusted Android SDK and a clean llama.cpp checkout manually.

Required: SDK 37.2, NDK 29.0.13113456, CMake 3.31.6, build-tools 36.0.0, pinned Gradle 9.7.0 wrapper JAR and distribution checksum, llama.cpp dbaf582af41941ebbfc4384fb9fa1b40dff7a510. Paths are explicit, no assumed drive letter.

Example in PowerShell from repository root (replace paths):
python scripts/build_mandy_embedded.py --sdk "D:/Android/Sdk" --llama "D:/src/llama.cpp"
python scripts/build_mandy_embedded.py --sdk "D:/Android/Sdk" --llama "D:/src/llama.cpp" --build

Only the second command runs Gradle. The script removes the expected old build output immediately before that invocation; other data is untouched. It requires a fresh nonempty APK containing AndroidManifest.xml, classes.dex and exactly one lib/arm64-v8a/libmandy_llama.so; checks ZIP integrity, all native library ELF64 PT_LOAD 16 KiB alignment via existing auditor, debug APK signature, application ID and zipalign -c -P 16 4. Only then prints EMBEDDED_DEBUG_APK and its SHA-256. Static checks do not establish runtime compatibility, JNI correctness or model quality. Preflight checks presence of tools and repository pins, not their full supply-chain authenticity; assumes a trusted local SDK and source tree. Windows-specific paths, wrapper invocation, signing and zipalign steps have not been exercised against actual Android toolchains. No actual APK or model generated.

Verification: eight Python unittest cases passed on synthetic ZIP/ELF fixtures and mocked Git revision/status output; existing check_apk_native.py used byte-for-byte (blob eedba0c6d52ef765af5c6366eb67fc5726ad6725). Existing six local Kotlin smoke jars were rerun (136 checks passed), and the 28 shared Python tests reran successfully. The complete repository regression suite was not run. Actual preflight in this environment failed as intended on missing checkout; Android SDK/NDK absent and git ls-remote failed with DNS error. No real inference, Android APK, Windows EXE or device tests. Do not interpret synthetic package tests as build success.

Next release gate: execute this build on a provisioned machine, run JNI with an explicitly selected trusted small GGUF, confirm completed reply and isolation, then wire the native Send UI. Keep the legacy build and existing manual workflow unchanged.
