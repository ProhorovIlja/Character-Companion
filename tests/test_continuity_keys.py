import unittest
import uuid
from pathlib import Path
from shared import continuity_keys as keys


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.account = str(uuid.UUID(int=1))
        self.secret = bytes(range(32))  # Fixture only.
        self.code = keys.encode(self.account, self.secret)

    def test_roundtrip(self):
        self.assertEqual(keys.decode(self.code), (self.account, self.secret))

    def test_generation(self):
        first, second = keys.generate(self.account), keys.generate(self.account)
        self.assertEqual(len(first), 32)
        self.assertNotEqual(first, second)

    def test_domains(self):
        history, manifest = keys.derive(self.account, self.secret)
        self.assertEqual(len(history), 32)
        self.assertEqual(len(manifest), 32)
        self.assertNotEqual(history, manifest)
        self.assertEqual(keys.derive(*keys.decode(self.code)), (history, manifest))
        self.assertNotEqual(keys.derive(str(uuid.UUID(int=2)), self.secret), (history, manifest))

    def test_invalid_codes(self):
        for code in (self.code + '=', ' ' + self.code, self.code.replace('MANDY1', 'MANDY2'), self.code.replace('.', '/'), self.code[:-1] + '9'):
            with self.assertRaises(ValueError):
                keys.decode(code)

    def test_invalid_arguments(self):
        for fn in (keys.encode, keys.derive):
            with self.assertRaises(ValueError):
                fn(self.account, bytes(31))
            with self.assertRaises(ValueError):
                fn('invalid', self.secret)


if __name__ == '__main__':
    unittest.main()
