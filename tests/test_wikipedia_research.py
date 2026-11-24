from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class WikipediaResearchTests(unittest.TestCase):
    def test_bounded_read_only_client(self):
        client = (JAVA / 'WikipediaResearchClient.kt').read_text()
        for token in ('instanceFollowRedirects = false', 'requestMethod = "GET"', 'connectTimeout = 8000',
                      'readTimeout = 8000', 'WikipediaResearch.readResponse', 'connection.disconnect()',
                      'LorebookPolicy.safeJsonDepth(json)', 'User-Agent'):
            self.assertIn(token, client)
        for token in ('CookieManager', 'Authorization', 'postDelayed', 'WorkManager', 'setDoOutput'):
            self.assertNotIn(token, client)
    def test_selection_and_review(self):
        ui = (JAVA / 'RoleplayToolkitUi.kt').read_text()
        for token in ('WikipediaResearchClient.search(language, raw)', 'BooleanArray(sources.size)',
                      'WikipediaResearch.packet(language, sources.filterIndexed', 'WikipediaResearch.append(editor.text.toString(), packet)',
                      'research(activity, emptyMap(), packet)'):
            self.assertIn(token, ui)
    def test_localization(self):
        def keys(locale):
            return {x.get('name') for x in ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()
                    if x.get('name', '').startswith('wiki_research_')}
        self.assertEqual(keys('values'), keys('values-ru'))
        self.assertEqual(len(keys('values')), 7)
