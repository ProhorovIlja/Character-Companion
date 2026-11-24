import tempfile
import unittest
import uuid
from pathlib import Path

from shared import continuity
from shared.history_store import HistoryStore
try:
    from cryptography.exceptions import InvalidTag
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from shared import encrypted_history as encrypted
except ModuleNotFoundError:
    encrypted = None


def uid(n):
    return str(uuid.UUID(int=n))


@unittest.skipIf(encrypted is None, 'optional cryptography dependency unavailable')
class EncryptedHistoryTests(unittest.TestCase):
    def setUp(self):
        self.key = bytes(range(32))  # Test fixture only.
        self.account = uid(1)
        self.event = dict(id=uid(2), device=uid(3), character=uid(4), chat=uid(5), clock=1, role='user', mode='text', text='Привет 🌙')
        self.plain = continuity.encode([self.event])
        self.blob = encrypted.seal(self.key, self.account, self.plain)

    def test_roundtrip(self):
        self.assertEqual(encrypted.open_batch(self.key, self.account, self.blob), self.plain)
        self.assertEqual(len(self.blob), len(self.plain) + 36)
        self.assertNotEqual(self.blob, encrypted.seal(self.key, self.account, self.plain))

    def test_tampering(self):
        for index in (0, 8, 20, len(self.blob) - 1):
            changed = bytearray(self.blob)
            changed[index] ^= 1
            with self.assertRaises((ValueError, InvalidTag)):
                encrypted.open_batch(self.key, self.account, bytes(changed))

    def test_wrong_account_or_key(self):
        for key, account in ((bytes(32), self.account), (self.key, uid(8))):
            with self.assertRaises(InvalidTag):
                encrypted.open_batch(key, account, self.blob)

    def test_invalid_lengths_and_arguments(self):
        for data in (b'', self.blob[:36], bytes(encrypted.MAX_ENVELOPE + 1)):
            with self.assertRaises(ValueError):
                encrypted.open_batch(self.key, self.account, data)
        with self.assertRaises(InvalidTag):
            encrypted.open_batch(self.key, self.account, self.blob[:-1])
        for key, account in ((bytes(31), self.account), (self.key, 'invalid')):
            with self.assertRaises(ValueError):
                encrypted.seal(key, account, self.plain)

    def test_authenticated_invalid_payload_rejected(self):
        nonce = bytes(12)  # Fixed nonce solely for an independently constructed invalid test vector.
        header = encrypted.MAGIC + nonce
        invalid = header + AESGCM(self.key).encrypt(nonce, b'{}', header + self.account.encode('ascii'))
        with self.assertRaises(ValueError):
            encrypted.open_batch(self.key, self.account, invalid)
        with self.assertRaises(ValueError):
            encrypted.seal(self.key, self.account, b'{}')

    def test_store_transfer_and_replay(self):
        with tempfile.TemporaryDirectory() as directory:
            first = HistoryStore(Path(directory) / 'first.db', self.account)
            second = HistoryStore(Path(directory) / 'second.db', self.account)
            try:
                first.import_batch(self.plain)
                [chunk] = list(encrypted.export_encrypted(first, self.key))
                self.assertEqual(encrypted.import_encrypted(second, self.key, chunk), 1)
                self.assertEqual(encrypted.import_encrypted(second, self.key, chunk), 0)
                self.assertEqual(second.page(uid(4), uid(5)), [self.event])
                new = second.append(id=uid(6), device=uid(7), character=uid(4), chat=uid(5), role='user', mode='text', text='next')
                self.assertEqual(new['clock'], 2)
            finally:
                first.close(); second.close()

    def test_conflict_atomic(self):
        with tempfile.TemporaryDirectory() as directory:
            store = HistoryStore(Path(directory) / 'history.db', self.account)
            try:
                store.import_batch(self.plain)
                conflict = dict(self.event, text='changed')
                new = dict(self.event, id=uid(1), clock=1)
                chunk = encrypted.seal(self.key, self.account, continuity.encode([new, conflict]))
                with self.assertRaises(ValueError):
                    encrypted.import_encrypted(store, self.key, chunk)
                self.assertEqual(store.page(uid(4), uid(5)), [self.event])
            finally:
                store.close()


if __name__ == '__main__':
    unittest.main()
