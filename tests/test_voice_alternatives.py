from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

class VoiceAlternativesTests(unittest.TestCase):
    def test_activity_integration(self):
        source = (ROOT / 'app/src/main/java/dev/caicompanion/app/VoiceDraftActivity.kt').read_text()
        for token in ('VoiceAlternatives.Choice.build(original, alternatives, start, end)',
                      'lastChoice === before', 'before.choose(editor.text.toString(), index)',
                      'lastChoice = retained.choice?.takeIf', 'lastSpeechEdit = choice.undo()',
                      'if (lastChoice?.matches(current) != true) lastChoice = null'):
            self.assertIn(token, source)
    def test_localization(self):
        for locale in ('values', 'values-ru'):
            names = {x.get('name') for x in ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()}
            self.assertIn('voice_alternatives', names)
