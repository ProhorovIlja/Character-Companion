#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 -m unittest discover -v -s tests -p 'test_*.py'
if command -v kotlinc >/dev/null 2>&1 && command -v kotlin >/dev/null 2>&1; then
  TEMP_DIR="$(mktemp -d)"; trap 'rm -rf "$TEMP_DIR"' EXIT
  kotlinc app/src/main/java/dev/caicompanion/app/{HostBlocker,LoginCompatibility,UrlPolicy,FilterEngine,BoundedText,BrowserProfile,SafeFilename,DownloadPolicy,RuleListParser,IncomingLinkParser,BrowserSessionPolicy,RendererRecoveryBudget,SupportReport,NativeLibrarySearch,NativeWorkspaceNavigation,NativeStartupPolicy,DeferredWebReload,OfflineBookmarkEditor,DeviceCompatibility,GeckoNavigationPolicy,GeckoTransferPolicy,GeckoTransferOriginPolicy,ExternalBrowserPolicy,VoiceDraftPolicy,VoiceRecognitionGuard,VoiceRecognitionFeedback}.kt \
    app/src/main/java/dev/caicompanion/app/VoiceComposerPolicy.kt app/src/main/java/dev/caicompanion/app/ReleasePolicy.kt tests/ReleasePolicySmoke.kt tests/PolicySmoke.kt -d "$TEMP_DIR/policy.jar"
  kotlin -classpath "$TEMP_DIR/policy.jar" PolicySmokeKt
else
  echo 'Kotlin toolchain unavailable: skipped pure Kotlin tests' >&2
fi
