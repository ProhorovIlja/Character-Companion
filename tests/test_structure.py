import pathlib
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]

class Structure(unittest.TestCase):
    def test_resource_xml(self):
        for path in (ROOT/'app/src/main/res').rglob('*.xml'):
            with self.subTest(path=path):
                ET.parse(path)
        ET.parse(ROOT/'app/src/main/AndroidManifest.xml')

    def test_android_only(self):
        self.assertTrue((ROOT/'app/build.gradle.kts').exists())
        self.assertFalse((ROOT/'src/windows').exists())

    def test_android_resource_references(self):
        # Catch unresolved string and ID names before a full Android SDK build.
        sources = '\n'.join(p.read_text() for p in (ROOT/'app/src/main/java').rglob('*.kt'))
        strings_xml = ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot()
        defined_strings = {node.attrib['name'] for node in strings_xml.findall('string')}
        missing_strings = set(re.findall(r'R\.string\.([A-Za-z0-9_]+)', sources)) - defined_strings
        self.assertEqual(missing_strings, set())
        layout = (ROOT/'app/src/main/res/layout/activity_main.xml').read_text()
        defined_ids = set(re.findall(r'@\+id/([A-Za-z0-9_]+)', layout))
        missing_ids = set(re.findall(r'R\.id\.([A-Za-z0-9_]+)', sources)) - defined_ids
        self.assertEqual(missing_ids, set())

    def test_privacy_defaults(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('android:allowBackup="false"',manifest)
        self.assertIn('android:usesCleartextTraffic="false"',manifest)
        self.assertIn('android:exported="true"',manifest)
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('handler.cancel()',activity)
        self.assertIn('onRenderProcessGone',activity)
        self.assertIn('ServiceWorkerControllerCompat',activity)
        self.assertNotIn('@JavascriptInterface',activity)

    def test_manual_profiles_and_no_api_proxy(self):
        profiles=(ROOT/'app/src/main/java/dev/caicompanion/app/BrowserProfile.kt').read_text()
        store=(ROOT/'app/src/main/java/dev/caicompanion/app/SettingsStore.kt').read_text()
        self.assertIn('BrowserProfile.DATA_SAVER', profiles)
        self.assertIn('BrowserProfile.COMPATIBILITY', profiles)
        self.assertIn('fun applyProfile(profile: BrowserProfile)', store)
        self.assertNotIn('retrofit2', ''.join(p.read_text() for p in ROOT.rglob('*.kt')))

    def test_navigation_media_and_link_safety(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('onShowCustomView', activity)
        self.assertIn('WindowManager.LayoutParams.FLAG_FULLSCREEN', activity)
        self.assertIn('download_confirm_title', (ROOT/'app/src/main/res/values/strings.xml').read_text())
        self.assertIn('enqueueDownload(safeUrl, agent, disposition, mime)', activity)
        self.assertIn('leaveFullscreen()', activity)
        self.assertIn('request.hasGesture()', activity)
        self.assertIn('UrlPolicy.safeShareUrl(webView.url)', activity)
        self.assertIn('denyPendingMedia()', activity)
        self.assertNotIn('webView.url?.takeIf(UrlPolicy::isHttps)', activity)

    def test_no_scheduled_actions(self):
        self.assertFalse((ROOT/'.github/workflows').exists())
        source=''.join(p.read_text(errors='ignore') for p in ROOT.rglob('*.kt'))
        self.assertNotIn('WorkManager',source)
        self.assertNotIn('AlarmManager',source)
        self.assertNotIn('JobScheduler',source)

    def test_manifest_has_minimum_permissions(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        permissions=set(re.findall(r'android.permission.[A-Z_]+',manifest))
        self.assertEqual(permissions, {'android.permission.INTERNET','android.permission.RECORD_AUDIO','android.permission.CAMERA'})

    def test_import_memory_bound_and_atomicity(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        store=(ROOT/'app/src/main/java/dev/caicompanion/app/SettingsStore.kt').read_text()
        self.assertIn('BoundedText.read(it, 200_000)', activity)
        self.assertNotIn('it.readText().take(200_001)', activity)
        self.assertIn('prefs.edit()\n            .putBoolean("blocking"', store)
        self.assertIn('UrlPolicy.sanitizeBookmark(u)', store)
        self.assertIn('put("bookmarks", sanitizedBookmarkArray())', store)
        self.assertIn('get() = UrlPolicy.sanitizeBookmark(prefs.getString("last_visited", "")).orEmpty()', store)

    def test_translations_cover_default_strings(self):
        en=ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot()
        ru=ET.parse(ROOT/'app/src/main/res/values-ru/strings.xml').getroot()
        self.assertEqual({n.attrib.get('name') for n in en.findall('string')},
                         {n.attrib.get('name') for n in ru.findall('string')})

    def test_strict_rules_do_not_block_service(self):
        lines=(ROOT/'app/src/main/assets/strict-hosts.txt').read_text().splitlines()
        rules=[s.strip() for s in lines if s.strip() and not s.startswith('#')]
        self.assertGreaterEqual(len(rules), 15)
        self.assertFalse(any('character.ai' in r or 'c.ai' in r for r in rules))

    def test_download_cookie_isolation_and_safe_filenames(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertNotIn('request.addRequestHeader("Cookie"', activity)
        self.assertNotIn('CookieManager.getInstance().getCookie(', activity)
        self.assertIn('SafeFilename.from(URLUtil.guessFileName', activity)
        self.assertIn('setDestinationInExternalPublicDir', activity)

    def test_service_worker_callback_no_activity_capture(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        start=activity.index('    private fun installServiceWorkerFilter()')
        end=activity.index('    private fun requestMediaPermission(', start)
        handler=activity[start:end]
        self.assertIn('val currentFilter = filter', handler)
        self.assertIn('emptyBlockedResponse(currentFilter,', handler)
        self.assertNotIn('filterResponse(request.url.toString())', handler)

    def test_oauth_browser_handoff_and_session_privacy(self):
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('if (UrlPolicy.isIdentityUrl(url))', activity)
        self.assertIn('confirmIdentityNavigation(url)', activity)
        self.assertIn('R.string.external_identity_message', activity)
        self.assertNotIn('@JavascriptInterface', activity)

    def test_local_stats_and_ui_accessibility(self):
        engine=(ROOT/'app/src/main/java/dev/caicompanion/app/FilterEngine.kt').read_text()
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('topBlockedRules', engine)
        self.assertIn('clearStats()', engine)
        self.assertIn('counters.size < 128', engine)
        self.assertIn('contentDescription = "$name. $description"', activity)
        self.assertTrue((ROOT/'app/src/main/res/drawable/settings_action.xml').exists())

    def test_conservative_host_rules(self):
        lines=(ROOT/'app/src/main/assets/blocked-hosts.txt').read_text().splitlines()
        rules=[s.strip() for s in lines if s.strip() and not s.startswith('#')]
        self.assertTrue(len(rules)>=20)
        self.assertFalse(any('character.ai' in r or 'c.ai' in r for r in rules))

if __name__=='__main__': unittest.main()
