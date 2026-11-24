"""Lifecycle and capability guards; pure Kotlin behavior exercised in PolicySmoke."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/dev/caicompanion/app'
MAIN=(JAVA/'MainActivity.kt').read_text()
GECKO=(ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()
POLICY=(JAVA/'LoginCompatibility.kt').read_text()
ENGINE=(JAVA/'FilterEngine.kt').read_text()
class FilterLifecycleTests(unittest.TestCase):
    def test_hidden_policy_rejects_late_callbacks(self):
        self.assertIn('private var visible = false',POLICY)
        self.assertIn('if (!value) reset()',POLICY)
        self.assertEqual(POLICY.count('if (!visible) return false'),2)
        self.assertIn('MessageDigest.getInstance("SHA-256")',POLICY)
        self.assertNotIn('.hashCode()',POLICY)
    def test_webview_resets_even_when_worker_outlives_activity(self):
        self.assertIn('loginProtection.setVisible(visible)',MAIN)
        self.assertIn('observeLoginPage(webView.url)',MAIN)
        self.assertIn('loginProtection.setVisible(false)\n        filter.loginBypass = false',MAIN)
        self.assertIn('if (::filter.isInitialized) filter.loginBypass = false',MAIN)
        self.assertIn('url != view?.url',MAIN)
        self.assertIn('view !== webView',MAIN)
    def test_gecko_pause_and_resume(self):
        self.assertIn('loginProtection.setVisible(sessionReady())',GECKO)
        self.assertIn('override fun onPause() {\n        loginProtection.setVisible(false)',GECKO)
        self.assertIn('needsRecovery = true\n        loginProtection.reset()',GECKO)
        self.assertIn('R.string.gecko_tracking_protection',GECKO)
        for loc in ('values','values-ru'):
            keys={e.get('name') for e in ET.parse(ROOT/f'app/src/main/res/{loc}/strings.xml').getroot()}
            self.assertTrue({'gecko_tracking_protection','gecko_tracking_explanation'} <= keys)
    def test_stats_reset_same_lock(self):
        self.assertIn('synchronized(statsLock) {\n            blockedRequestCount.incrementAndGet()',ENGINE)
        self.assertIn('synchronized(statsLock) {\n            counters.clear()\n            blockedRequestCount.set(0)',ENGINE)
if __name__=='__main__': unittest.main()
