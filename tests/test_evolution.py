from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class EvolutionTests(unittest.TestCase):
    def test_review_and_explicit_save(self):
        ui = (JAVA / 'RoleplayToolkitUi.kt').read_text()
        for token in ('CompanionModules.Module.EVOLUTION -> evolution(activity)',
                      'EvolutionPolicy.state(entries, profile)', 'EvolutionPolicy.packet(entries, profile)',
                      'EvolutionPolicy.review(entries, profile, editor.text.toString())',
                      'EvolutionPolicy.entry(profile, field, editor.text.toString())',
                      'MemoryBankStore.put(context, entries, entry)', 'previewContext(activity, prompt)'):
            self.assertIn(token, ui)
        policy = (JAVA / 'EvolutionPolicy.kt').read_text()
        self.assertIn('MemoryBank.forProfile(entries, profile)', policy)
        self.assertIn('Do not force romance', policy)
        for token in ('openConnection', 'evaluateJavascript', 'WorkManager'):
            self.assertNotIn(token, policy)
    def test_localization(self):
        def keys(locale):
            return {x.get('name') for x in ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()
                    if x.get('name', '').startswith('evolution_')}
        self.assertEqual(keys('values'), keys('values-ru'))
        self.assertEqual(len(keys('values')), 12)
