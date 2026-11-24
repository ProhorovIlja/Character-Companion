import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from check_apk_renderer import renderer, MAX_TEXT

KEY = 'dev.caicompanion.app.RENDERER'


def node(flavor='gecko', key=KEY, indent='      '):
    return (f'{indent}E: meta-data (line=10)\n'
            f'{indent}  A: android:name(0x01010003)="{key}" (Raw: "{key}")\n'
            f'{indent}  A: android:value(0x01010024)="{flavor}" (Raw: "{flavor}")\n')


def manifest(body):
    return 'N: android=http://schemas.android.com/apk/res/android\n  E: manifest (line=2)\n    E: application (line=5)\n' + body


class RendererAudit(unittest.TestCase):
    def test_flavors(self):
        for flavor in ('gecko', 'legacy'):
            self.assertEqual(renderer(manifest(node(flavor))), flavor)

    def test_unrelated_metadata(self):
        self.assertEqual(renderer(manifest(node('anything', 'other') + node())), 'gecko')

    def test_missing_and_unknown(self):
        for tree in (manifest(''), manifest(node('unknown')), '', 'garbage'):
            self.assertIsNone(renderer(tree))

    def test_duplicate_marker(self):
        self.assertIsNone(renderer(manifest(node() + node())))
        self.assertIsNone(renderer(manifest(node() + node('legacy'))))

    def test_nested_marker_not_application_metadata(self):
        nested = '      E: activity (line=8)\n' + node(indent='        ')
        self.assertIsNone(renderer(manifest(nested)))
        self.assertEqual(renderer(manifest(nested + node('legacy'))), 'legacy')

    def test_nonliteral_or_inconsistent_value(self):
        tree = manifest(node())
        self.assertIsNone(renderer(tree.replace('="gecko" (Raw: "gecko")', '=@0x7f010001')))
        self.assertIsNone(renderer(tree.replace('(Raw: "gecko")', '(Raw: "legacy")')))

    def test_duplicate_attribute(self):
        self.assertIsNone(renderer(manifest(node() + '        A: android:value(0x01010024)="gecko"\n')))

    def test_duplicate_application(self):
        self.assertIsNone(renderer(manifest(node() + '    E: application (line=20)\n')))

    def test_bounded(self):
        self.assertIsNone(renderer(manifest(node()) + ' ' * MAX_TEXT))


if __name__ == '__main__':
    unittest.main()
