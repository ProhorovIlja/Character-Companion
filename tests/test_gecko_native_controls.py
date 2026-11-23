"""Gecko native controls security and lifecycle regression checks (not a device build)."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT / 'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()
BASE = ROOT / 'app/src/main/res'

class GeckoNativeControlsTests(unittest.TestCase):
    def test_ui_has_a_native_bookmark_library_and_explicit_save(self):
        self.assertIn('private fun saveCurrentBookmark()', ACTIVITY)
        self.assertIn('private fun showBookmarks()', ACTIVITY)
        self.assertIn('UrlPolicy.sanitizeBookmark(activeUrl)', ACTIVITY)
        self.assertIn('settings.addBookmark(title.text.toString(), url)', ACTIVITY)
        self.assertIn('NativeLibrarySearch.filter(settings.listBookmarks()', ACTIVITY)
        self.assertIn('settings.removeBookmark(bookmark.second)', ACTIVITY)
        self.assertIn('setOnItemLongClickListener', ACTIVITY)
        self.assertNotIn('evaluateJavascript', ACTIVITY)

    def test_options_are_real_local_preferences_not_dead_switches(self):
        self.assertIn('private fun showGeckoSettings()', ACTIVITY)
        self.assertIn('settings.blocking = it', ACTIVITY)
        self.assertIn('session.settings.setUseTrackingProtection(it)', ACTIVITY)
        self.assertIn('settings.mediaAllowed = it', ACTIVITY)
        self.assertIn('settings.protectScreen = it', ACTIVITY)
        self.assertIn('settings.resumePage = it', ACTIVITY)
        self.assertIn('if (!it) settings.lastVisited = ""', ACTIVITY)
        self.assertIn('settings.compactChrome = it', ACTIVITY)
        self.assertIn('private fun updateCompactChrome()', ACTIVITY)
        self.assertIn('actionsRow.visibility', ACTIVITY)
        # Intentionally omit unsupported Gecko toggles instead of misleading the user.
        self.assertNotIn('settings.strictBlocking = it', ACTIVITY)
        self.assertNotIn('settings.cookies = it', ACTIVITY)
        self.assertNotIn('settings.reduceImages = it', ACTIVITY)

    def test_manual_crash_recovery_has_bounded_user_action(self):
        self.assertIn('override fun onCrash(session: GeckoSession)', ACTIVITY)
        self.assertIn('override fun onKill(session: GeckoSession)', ACTIVITY)
        self.assertIn('private fun showRecoveryPrompt()', ACTIVITY)
        self.assertIn('private fun recoverGecko()', ACTIVITY)
        self.assertIn('RendererRecoveryBudget(2, 120_000L)', ACTIVITY)
        self.assertIn('SystemClock.elapsedRealtime()', ACTIVITY)
        self.assertIn('.setPositiveButton(R.string.retry) { _, _ -> recoverGecko() }', ACTIVITY)
        self.assertIn('surface.releaseSession()', ACTIVITY)
        self.assertIn('session.open(engine(applicationContext))', ACTIVITY)
        self.assertIn('UrlPolicy.sanitizeBookmark(activeUrl) ?: HOME', ACTIVITY)
        self.assertIn('if (needsRecovery) showRecoveryPrompt()', ACTIVITY)
        self.assertNotIn('postDelayed(', ACTIVITY)
        self.assertNotIn('WorkManager', ACTIVITY)

    def test_outstanding_permissions_are_revoked_after_failure(self):
        crash = ACTIVITY.split('private fun geckoExited()', 1)[1].split('private fun showRecoveryPrompt()', 1)[0]
        self.assertIn('pendingMediaCallback?.reject()', crash)
        self.assertIn('permissionCallback?.reject()', crash)
        self.assertIn('finishFilePrompt(emptyList())', crash)
        self.assertIn('abandonDownload()', crash)
        result = ACTIVITY.split('override fun onRequestPermissionsResult(', 1)[1].split('private fun updateNavigation()', 1)[0]
        self.assertIn('started && settings.mediaAllowed && UrlPolicy.isTrustedMediaOrigin(activeUrl)', result)

    def test_gecko_preserves_layout_on_rotation(self):
        root = ET.parse(ROOT / 'app/src/gecko/AndroidManifest.xml').getroot()
        tag = '{http://schemas.android.com/apk/res/android}'
        activity = root.find('application/activity')
        self.assertIn('orientation', activity.get(tag + 'configChanges', ''))
        self.assertIn('screenSize', activity.get(tag + 'configChanges', ''))
        self.assertIn('smallestScreenSize', activity.get(tag + 'configChanges', ''))

    def test_localizations_are_complete_and_webview_is_not_used_for_gecko(self):
        en = {x.get('name') for x in ET.parse(BASE / 'values/strings.xml').getroot()}
        ru = {x.get('name') for x in ET.parse(BASE / 'values-ru/strings.xml').getroot()}
        for name in ('gecko_recovery_title', 'gecko_recovery_message',
                     'gecko_recovery_limit', 'hub_search_hint',
                     'hub_bookmark_confirm_delete', 'toggle_protect_screen', 'toggle_compact'):
            self.assertIn(name, en)
            self.assertIn(name, ru)
        self.assertNotIn('android.webkit.WebView', ACTIVITY)
        self.assertNotIn('WebView2', ACTIVITY)

if __name__ == '__main__': unittest.main()
