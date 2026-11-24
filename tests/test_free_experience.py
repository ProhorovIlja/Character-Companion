from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class FreeExperienceTests(unittest.TestCase):
    def test_all_modes_expose_help(self):
        for path in (JAVA / 'MainActivity.kt', JAVA / 'BrowserSessionActivity.kt',
                     ROOT / 'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt'):
            self.assertIn('FreeExperienceHelp.show(this)', path.read_text())
        help_text = (JAVA / 'FreeExperienceHelp.kt').read_text()
        self.assertIn('Intent.ACTION_VIEW', help_text)
        self.assertNotIn('openConnection(', help_text)

    def test_http_advice_preserves_page(self):
        main = (JAVA / 'MainActivity.kt').read_text()
        callback = main.split('override fun onReceivedHttpError', 1)[1].split('override fun onReceivedSslError', 1)[0]
        self.assertIn('view !== webView', callback)
        self.assertIn('request.isForMainFrame', callback)
        self.assertIn('UrlPolicy.isTrustedMediaOrigin', callback)
        for notice in ('RATE_LIMIT', 'UNAVAILABLE'):
            self.assertIn('Notice.' + notice + ' -> showToast', callback)
        self.assertNotIn('reload()', callback)
        self.assertIn('serviceLoadFeedback.beginNavigation()', main)

    def test_localization(self):
        for locale in ('values', 'values-ru'):
            keys = {e.get('name') for e in ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()}
            for suffix in ('title', 'summary', 'charms', 'status', 'http429', 'http503'):
                self.assertIn('free_experience_' + suffix, keys)
