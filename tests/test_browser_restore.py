from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class BrowserRestoreTests(unittest.TestCase):
    def test_saved_destination(self):
        source = (JAVA / 'BrowserSessionActivity.kt').read_text()
        for token in ('restoredPage = savedInstanceState?.getString(STATE_PAGE)',
                      'BrowserSessionPolicy.resolveLaunch', 'override fun onSaveInstanceState',
                      'outState.putString(STATE_PAGE, BrowserSessionPolicy.safeTabUrl(destination))',
                      'if (launch.openTab) openTab()'):
            self.assertIn(token, source)

    def test_switch_rollback(self):
        source = (JAVA / 'BrowserSessionActivity.kt').read_text()
        self.assertEqual(source.count('val previousPreference = store.browserSessionPreferred'), 2)
        self.assertEqual(source.count('store.browserSessionPreferred = previousPreference'), 2)
