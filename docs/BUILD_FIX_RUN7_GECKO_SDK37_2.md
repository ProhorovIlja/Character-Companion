# Build #7: partial success and GeckoView API 37.2 repair

Run: https://github.com/ProhorovIlja/Character-Companion/actions/runs/45362926427

Legacy WebView compiled successfully and the workflow verified the debug APK, package/version and ZIP integrity. The artifact was uploaded successfully: https://github.com/ProhorovIlja/Character-Companion/actions/runs/45362926427/artifacts/14905217495 (expires 2026-12-01). Hardware QA is still required.

GeckoView 159.0.20261102144711 failed checkGeckoDebugAarMetadata because it requires compileSdk 37.2 and the project used 37.0. Keep the current GeckoView and Kotlin/AGP/Gradle pins. Select 37.2 for GeckoView, 37.0 for legacy, install 37.2 inside the GeckoView step so any failure cannot stop the WebView attempt. Direct Gradle defaults to 37.2. Both flavors retain targetSdk 36 and minSdk 29. No metadata-check bypass. The existing user-authorized workflow remains manual only.

Local tests use mocked SDK tools; they do not establish 37.2 package availability on the GitHub runner or prove GeckoView APK compilation. A new manual run is required.
