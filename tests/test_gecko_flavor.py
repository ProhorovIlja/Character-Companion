"""Offline GeckoView architecture/manifest checks; not equivalent to a device build."""
import pathlib
import subprocess
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'

class GeckoFlavorTests(unittest.TestCase):
    def test_gecko_flavor_has_own_launcher_and_share_sheet(self):
        base = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
        legacy = ET.parse(ROOT/'app/src/legacy/AndroidManifest.xml').getroot()
        gecko = ET.parse(ROOT/'app/src/gecko/AndroidManifest.xml').getroot()
        base_act = base.find('application/activity')
        self.assertEqual(base_act.attrib[ANDROID+'exported'], 'false')
        self.assertEqual(len(base_act.findall('intent-filter')), 0)
        for manifest, expected in [(legacy, '.MainActivity'), (gecko, '.GeckoActivity')]:
            activity = manifest.find('application/activity')
            self.assertEqual(activity.attrib[ANDROID+'name'], expected)
            self.assertEqual(activity.attrib[ANDROID+'exported'], 'true')
            categories = [n.attrib[ANDROID+'name'] for n in activity.iter('category')]
            actions = [n.attrib[ANDROID+'name'] for n in activity.iter('action')]
            self.assertIn('android.intent.category.LAUNCHER', categories)
            self.assertIn('android.intent.action.SEND', actions)
            for node in activity.iter('data'):
                self.assertEqual(node.attrib.get(ANDROID+'mimeType'), 'text/plain')

    def test_gecko_dependency_is_flavor_isolated(self):
        build = (ROOT/'app/build.gradle.kts').read_text()
        settings = (ROOT/'settings.gradle.kts').read_text()
        self.assertIn('geckoImplementation', build)
        self.assertIn('geckoview-arm64-v8a:156.0.20260921121718', build)
        self.assertIn('create("gecko")', build)
        self.assertIn('create("legacy")', build)
        self.assertIn('abiFilters.add("arm64-v8a")', build)
        self.assertIn('maven.mozilla.org/maven2/', settings)
        self.assertEqual(build.count('versionCode = 28'), 1)
        self.assertEqual(build.count('versionName = "0.28.0-rc1"'), 1)

    def test_gecko_navigation_is_first_party_and_fail_closed(self):
        src = (ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()
        policy = (ROOT/'app/src/main/java/dev/caicompanion/app/GeckoNavigationPolicy.kt').read_text()
        self.assertIn('GeckoNavigationPolicy.decide', src)
        self.assertIn('GeckoResult.fromValue(AllowOrDeny.DENY)', src)
        self.assertIn('onLoadRequest', src)
        self.assertIn('onNewSession', src)
        self.assertIn('UrlPolicy.isIdentityUrl', policy)
        self.assertIn('UrlPolicy.isHttps', policy)
        self.assertIn('UrlPolicy.sameTrustedMediaOrigin(uri, activeUrl)', src)
        self.assertNotIn('android.webkit.WebView', src)
        self.assertNotIn('CookieManager', src)
        self.assertNotIn('@JavascriptInterface', src)

    def test_gecko_permissions_require_consent_and_foreground(self):
        src = (ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()
        self.assertIn('onAndroidPermissionsRequest', src)
        self.assertIn('onMediaPermissionRequest', src)
        self.assertIn('checkSelfPermission(Manifest.permission.RECORD_AUDIO)', src)
        self.assertIn('checkSelfPermission(Manifest.permission.CAMERA)', src)
        self.assertIn('!settings.mediaAllowed', src)
        self.assertIn('AlertDialog.Builder(this@GeckoActivity)', src)
        self.assertIn('permissionCallback?.reject()', src)
        self.assertIn('pendingMediaCallback?.reject()', src)
        self.assertIn('override fun onStop()', src)
        self.assertIn('!started || !settings.mediaAllowed', src)
        self.assertIn('surface.releaseSession()', src)
        self.assertIn('session.close()', src)

    def test_no_unsigned_release_or_scheduled_ci(self):
        self.assertFalse((ROOT/'.github/workflows').exists())
        build = ROOT/'scripts/build-apk.sh'
        release = ROOT/'scripts/prepare-release.sh'
        self.assertIn('assembleGeckoDebug', build.read_text())
        self.assertIn('assembleLegacyDebug', build.read_text())
        self.assertIn('RENDERER_FLAVOR=$FLAVOR', release.read_text())
        for bad in [[], ['foo']]:
            result = subprocess.run(['bash', str(build), *bad], cwd=ROOT, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('Usage:', result.stderr)
        result = subprocess.run(['bash', str(release)], cwd=ROOT, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Explicit renderer flavor', result.stderr)

    def test_gecko_l10n_and_resource_references(self):
        vals = (ROOT/'app/src/main/res/values/strings.xml').read_text()
        ru = (ROOT/'app/src/main/res/values-ru/strings.xml').read_text()
        for text in ['gecko_preview_title', 'gecko_legacy', 'gecko_media', 'gecko_error']:
            self.assertIn(f'name="{text}"', vals)
            self.assertIn(f'name="{text}"', ru)

if __name__ == '__main__': unittest.main()
