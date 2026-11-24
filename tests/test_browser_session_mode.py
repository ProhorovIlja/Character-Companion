"""Regression gates for the explicitly chosen browser-owned Google/Apple mode."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/'app/src/main/java/dev/caicompanion/app'
MODE = (JAVA/'BrowserSessionActivity.kt').read_text()
MAIN = (JAVA/'MainActivity.kt').read_text()
GECKO = (ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()
STORE = (JAVA/'SettingsStore.kt').read_text()
class BrowserSessionModeTests(unittest.TestCase):
    def test_both_flavors_route_explicit_mode_before_renderer(self):
        for source, pref in [(MAIN, 'store'), (GECKO, 'settings')]:
            self.assertIn(f'if ({pref}.browserSessionPreferred)', source)
            self.assertIn('BrowserSessionActivity.enter(this, BrowserSessionPolicy.HOME, openNow = true)', source)
            self.assertIn('R.string.browser_mode_sign_in', source)
        self.assertLess(GECKO.index('if (settings.browserSessionPreferred)'), GECKO.index('session = GeckoSession()'))
        self.assertLess(MAIN.index('if (store.browserSessionPreferred)'), MAIN.index('else openFirstPartyPage(lastUrl)'))
    def test_mode_persists_only_boolean_and_has_embedded_exit(self):
        self.assertIn('getBoolean("browser_session_preferred", false)', STORE)
        self.assertIn('putBoolean("browser_session_preferred", v)', STORE)
        self.assertNotIn('put("browserSessionPreferred"', STORE)
        self.assertIn('store.browserSessionPreferred = true', MODE)
        self.assertIn('store.browserSessionPreferred = false', MODE)
        self.assertIn('getLaunchIntentForPackage(packageName)', MODE)
    def test_browser_provider_and_clean_url(self):
        self.assertIn('CustomTabsClient.getPackageName(this, null)', MODE)
        self.assertIn('tab.intent.setPackage(provider)', MODE)
        self.assertIn('Uri.parse(BrowserSessionPolicy.safeTabUrl(destination))', MODE)
        self.assertIn('putExtra(EXTRA_PAGE, BrowserSessionPolicy.safeTabUrl(page))', MODE)
        for forbidden in ('CookieManager', 'addJavascriptInterface', 'getCookie(', 'Authorization: Bearer', 'setUserAgentString', 'AuthTabIntent'):
            self.assertNotIn(forbidden, MODE)
    def test_no_loop_or_automatic_success_claim(self):
        self.assertIn('allowOpen = savedInstanceState == null', MODE)
        self.assertIn('setIntent(Intent(this, BrowserSessionActivity::class.java))', MODE)
        self.assertNotIn('override fun onResume', MODE)
        self.assertNotIn('override fun onStart', MODE)
    def test_private_activity_and_narrow_provider_discovery(self):
        manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
        ns = '{http://schemas.android.com/apk/res/android}'
        activity = next(a for a in manifest.findall('./application/activity') if a.get(ns+'name') == '.BrowserSessionActivity')
        self.assertEqual(activity.get(ns+'exported'), 'false')
        self.assertEqual(activity.findall('intent-filter'), [])
        self.assertIn('android.support.customtabs.action.CustomTabsService', ET.tostring(manifest, encoding='unicode'))
    def test_bilingual_controls_and_limitations(self):
        keys = ('browser_mode_title','browser_mode_sign_in','browser_mode_continue','browser_mode_home','browser_mode_embedded','browser_mode_explanation','browser_mode_no_provider','browser_mode_restart')
        for locale in ('values', 'values-ru'):
            doc = ET.parse(ROOT/f'app/src/main/res/{locale}/strings.xml').getroot()
            for key in keys: self.assertIsNotNone(doc.find(f"string[@name='{key}']"))
if __name__ == '__main__': unittest.main()
