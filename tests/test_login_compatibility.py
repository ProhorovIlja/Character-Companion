"""Source guards for temporary Character.AI login compatibility. JVM policy is executable."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / 'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
GECKO = (ROOT / 'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()
POLICY = (ROOT / 'app/src/main/java/dev/caicompanion/app/LoginCompatibility.kt').read_text()
ENGINE = (ROOT / 'app/src/main/java/dev/caicompanion/app/FilterEngine.kt').read_text()

class LoginCompatibilityTests(unittest.TestCase):
    def test_first_party_https_path_only(self):
        self.assertIn('HostBlocker.isServiceHost(parsed.host)', POLICY)
        self.assertIn('parsed.userInfo != null', POLICY)
        self.assertIn('parsed.port != 443', POLICY)
        self.assertIn('parsed.rawPath', POLICY)
        self.assertNotIn('rawQuery', POLICY)
        self.assertIn('pageKey', POLICY)
        self.assertIn('manualPage = null', POLICY)
        self.assertIn('manual && (page == null || page != manualPage)', POLICY)
        self.assertIn('@Volatile var bypass: Boolean', POLICY)
        self.assertNotIn('SharedPreferences', POLICY)
        self.assertNotIn('WorkManager', POLICY)

    def test_webview_subrequests_and_service_workers_share_state(self):
        self.assertIn('filter.loginBypass = loginProtection.bypass', MAIN)
        self.assertIn('if (!enabled || loginBypass) return false', ENGINE)
        self.assertIn('emptyBlockedResponse(currentFilter, request.url.toString())', MAIN)
        self.assertIn('observeLoginPage(url)', MAIN)
        self.assertIn('override fun doUpdateVisitedHistory(', MAIN)
        self.assertIn('setOnLongClickListener { toggleLoginProtection(); true }', MAIN)
        self.assertIn('loginProtection.bypass) getString(R.string.shield_login)', MAIN)
        self.assertIn('R.string.login_compatibility_button', MAIN)

    def test_gecko_tracking_scope_and_restoration(self):
        self.assertIn('session.settings.setUseTrackingProtection(settings.blocking && !loginProtection.bypass)', GECKO)
        self.assertIn('observeLoginPage(request.uri)', GECKO)
        self.assertIn('observeLoginPage(url)', GECKO)
        self.assertIn('onLocationChange', GECKO)
        self.assertIn('privacyButton.setOnLongClickListener', GECKO)
        self.assertIn('R.string.login_compatibility_button', GECKO)
        self.assertIn('settings.blocking = !settings.blocking', GECKO)
        self.assertNotIn('addJavascriptInterface', GECKO)
        self.assertNotIn('evaluateJavascript', GECKO)

    def test_manual_mode_is_explicit_and_translated(self):
        names = ['login_compatibility_button', 'login_compatibility_info',
                 'login_compatibility_on', 'login_compatibility_off',
                 'login_compatibility_unavailable', 'shield_login']
        for loc in ('values', 'values-ru'):
            xml = ET.parse(ROOT / f'app/src/main/res/{loc}/strings.xml').getroot()
            keys = {x.get('name') for x in xml.findall('string')}
            for name in names:
                with self.subTest(locale=loc, name=name): self.assertIn(name, keys)
        self.assertIn('fun verifyLoginCompatibility()', (ROOT / 'tests/PolicySmoke.kt').read_text())

if __name__ == '__main__': unittest.main()
