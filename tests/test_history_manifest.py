import tempfile
import unittest
import uuid
from pathlib import Path
from shared import continuity as wire
from shared.history_store import HistoryStore
from shared import encrypted_history as crypto, history_manifest as m


def uid(n):
    return str(uuid.UUID(int=n))


class ManifestTests(unittest.TestCase):
    def setUp(self):
        self.key = bytes(range(32))
        self.mac = bytes(range(32, 64))
        self.account = uid(1)
        self.snapshot = uid(99)
        self.event = dict(id=uid(2), device=uid(3), character=uid(4), chat=uid(5), clock=1, role='user', mode='text', text='Привет 🌙')
        self.second = dict(self.event, id=uid(6), clock=2, text='Second')
        self.chunks = [self.seal(self.event), self.seal(self.second)]
        self.manifest = m.create(self.mac, self.account, self.snapshot, self.chunks)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.store = HistoryStore(Path(self.tmp.name) / 'history.db', self.account)
        self.addCleanup(self.store.close)

    def seal(self, event):
        return crypto.seal(self.key, self.account, wire.encode([event]))

    def load(self, chunks, manifest=None):
        return m.import_snapshot(self.store, self.key, self.mac, self.manifest if manifest is None else manifest, chunks)

    def test_import_replay(self):
        self.assertEqual(self.load(iter(self.chunks)), (self.snapshot, 2))
        self.assertEqual(self.load(self.chunks), (self.snapshot, 0))
        self.assertEqual(self.store.page(uid(4), uid(5)), [self.second, self.event])

    def test_missing_extra_reordered_modified(self):
        changed = self.chunks[1][:-1] + bytes([self.chunks[1][-1] ^ 1])
        for pages in (self.chunks[:1], self.chunks + self.chunks[:1], self.chunks[::-1], [self.chunks[0], changed]):
            with self.assertRaises(ValueError):
                self.load(pages)
            self.assertEqual(self.store.page(uid(4), uid(5)), [])

    def test_manifest_authentication(self):
        for index in (0, 8, 44, 48, len(self.manifest)-1):
            changed = bytearray(self.manifest); changed[index] ^= 1
            with self.assertRaises(ValueError):
                self.load(self.chunks, bytes(changed))
        with self.assertRaises(ValueError):
            m.parse(self.mac, uid(7), self.manifest)
        with self.assertRaises(ValueError):
            m.parse(self.key, self.account, self.manifest)

    def test_cross_page_conflict_rollback(self):
        pages = [self.chunks[0], self.seal(dict(self.event, text='conflict'))]
        with self.assertRaises(ValueError):
            self.load(pages, m.create(self.mac, self.account, self.snapshot, pages))
        self.assertEqual(self.store.page(uid(4), uid(5)), [])
        self.assertEqual(self.store.db.execute('SELECT clock FROM vault').fetchone()[0], 0)

    def test_empty_snapshot(self):
        self.assertEqual(self.load([], m.create(self.mac, self.account, self.snapshot, [])), (self.snapshot, 0))

    def test_limits_and_separate_keys(self):
        with self.assertRaises(ValueError):
            m.create(self.mac, self.account, self.snapshot, [self.chunks[0]] * (m.MAX_CHUNKS + 1))
        with self.assertRaises(ValueError):
            m.import_snapshot(self.store, self.key, self.key, self.manifest, self.chunks)
        with self.assertRaises(ValueError):
            m.create(self.mac, self.account, self.snapshot, [b'invalid'])


if __name__ == '__main__':
    unittest.main()
