"""Source-level lifecycle gates, not Android runtime tests."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/'app/src/main/java/dev/caicompanion/app'
class UpdateLifecycle(unittest.TestCase):
    def test_all_modes_cancel_when_hidden(self):
        for path in (JAVA/'MainActivity.kt', JAVA/'BrowserSessionActivity.kt',
                     ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt'):
            source = path.read_text()
            for method in ('onStop', 'onDestroy'):
                self.assertIn('ForegroundUpdateUi.cancel(this)', source.split('override fun '+method+'()', 1)[1].split('\n    }',1)[0])
    def test_dialog_ownership_and_cancellation(self):
        source = (JAVA/'ForegroundUpdateUi.kt').read_text()
        self.assertIn('dialogs.remove(activity)?.dismiss()', source)
        self.assertIn('dialogs[activity] === dialog', source)
        for file in ('ReleaseCenter.kt', 'UpdateDownload.kt'):
            self.assertIn('ForegroundUpdateUi.show', (JAVA/file).read_text())
    def test_staging_retention_and_worker_verification(self):
        source = (JAVA/'UpdateDownload.kt').read_text()
        self.assertIn('it.lastModified() < cutoff', source)
        self.assertIn('if (!handedOff) candidate?.delete()', source)
        self.assertIn('retained + asset.size > 1_073_741_824L', source)
        self.assertIn('elapsedRealtime() - startedAt >= 300_000L', source)
        install = source.split('private fun install(',1)[1]
        self.assertNotIn('verifyPackage(', install)
        self.assertIn('file.length() != expectedSize', install)
    def test_single_apk_migration_and_debug_gate(self):
        center = (JAVA/'ReleaseCenter.kt').read_text()
        download = (JAVA/'UpdateDownload.kt').read_text()
        for source in (center, download):
            self.assertIn('"arm64-v8a" in Build.SUPPORTED_ABIS', source)
            self.assertIn('BuildConfig.DEBUG', source)
        self.assertIn('ReleasePolicy.selectAsset(remote, BuildConfig.FLAVOR, assets, arm64)', center)
        self.assertIn('ReleasePolicy.acceptsArchive(BuildConfig.FLAVOR', download)
        self.assertIn('GET_SIGNING_CERTIFICATES', download)
        self.assertIn('FLAG_DEBUGGABLE', download)
        self.assertIn('apkContentsSigners', download)
    def test_install_permission_preflight_and_private_provider(self):
        source = (JAVA/'UpdateDownload.kt').read_text()
        start = source.split('fun start(', 1)[1].split('private fun fetch(', 1)[0]
        self.assertLess(start.index('if (!installationAllowed(activity))'), start.index('Thread {'))
        self.assertLess(start.index('if (!installationAllowed(activity))'), start.index('busy.compareAndSet(false, true)'))
        self.assertIn('verifyPackage(activity, target, tag) && !expired()', start)
        self.assertLess(start.index('it.lastModified() < cutoff'), start.index('dir.usableSpace'))
        self.assertIn('ClipData.newRawUri("Verified update", uri)', source)
        self.assertIn('FLAG_GRANT_READ_URI_PERMISSION', source)
        self.assertNotIn('FLAG_GRANT_WRITE_URI_PERMISSION', source)
        self.assertIn('ForegroundUpdateUi.show(activity, prompt)', source)
        manifest = (ROOT/'app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('android:name=".UpdateFileProvider"', manifest)
        self.assertIn('class UpdateFileProvider : FileProvider()', (JAVA/'UpdateFileProvider.kt').read_text())
        self.assertIn('path="verified-updates/"', (ROOT/'app/src/main/res/xml/update_paths.xml').read_text())
    def test_dialog_dismiss_callback_is_idempotent(self):
        source = (JAVA/'ForegroundUpdateUi.kt').read_text()
        self.assertIn('if (completed) return', source)
        self.assertIn('completed = true', source)
        self.assertIn('dialog.setOnDismissListener { complete() }', source)
if __name__ == '__main__': unittest.main()
