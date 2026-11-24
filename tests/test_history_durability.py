"""Real SQLite reference regression tests, not Android framework tests."""
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

from shared.history_store import HistoryStore
from test_history_store import uid, event
from shared import continuity as wire


class HistoryDurability(unittest.TestCase):
    def test_corrupt_file_preserved(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'history.db'
            damaged = b'not a SQLite database' * 256
            path.write_bytes(damaged)
            with self.assertRaises(sqlite3.DatabaseError):
                HistoryStore(path, uid(40000))
            self.assertEqual(path.read_bytes(), damaged)

    def test_durability_on_every_connection(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'history.db'
            with closing(HistoryStore(path, uid(40000))) as first, closing(HistoryStore(path, uid(40000))) as second:
                for store in (first, second):
                    self.assertEqual(store.db.execute('PRAGMA journal_mode').fetchone()[0], 'wal')
                    self.assertEqual(store.db.execute('PRAGMA synchronous').fetchone()[0], 2)
                    self.assertEqual(store.db.execute('PRAGMA foreign_keys').fetchone()[0], 1)
                first.import_batch(wire.encode([event(1)]))
                self.assertEqual(second.page(uid(20000), uid(30000)), [event(1)])
            with closing(HistoryStore(path, uid(40000))) as reopened:
                self.assertEqual(reopened.page(uid(20000), uid(30000)), [event(1)])

    def test_unknown_schema_not_reset(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'history.db'
            with closing(HistoryStore(path, uid(40000))) as store:
                store.import_batch(wire.encode([event(1)]))
                store.db.execute('PRAGMA user_version=99')
            with self.assertRaises(ValueError):
                HistoryStore(path, uid(40000))
            with closing(sqlite3.connect(path)) as db:
                self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0], 99)
                self.assertEqual(db.execute('SELECT text FROM messages').fetchone()[0], event(1)['text'])

    def test_foreign_unversioned_schema_preserved(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'history.db'
            with closing(sqlite3.connect(path)) as db:
                db.execute('CREATE TABLE unrelated (value TEXT)')
                db.execute('INSERT INTO unrelated VALUES (?)', ('preserve',))
                db.commit()
            with self.assertRaises(ValueError):
                HistoryStore(path, uid(40000))
            with closing(sqlite3.connect(path)) as db:
                self.assertEqual(db.execute('SELECT value FROM unrelated').fetchone()[0], 'preserve')
                self.assertIsNone(db.execute("SELECT 1 FROM sqlite_master WHERE name='vault'").fetchone())


if __name__ == '__main__':
    unittest.main()
