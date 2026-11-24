"""SQLite regression for Android's keyset queries with string-bound parameters."""
import sqlite3
import unittest

COLS = 'id, device, character, chat, clock, role, mode, text'
PAGE = 'SELECT ' + COLS + ' FROM messages WHERE chat=? AND character=?'
EXPORT = 'SELECT ' + COLS + ' FROM messages'
OLD_PAGE = PAGE + ' AND (clock < ? OR (clock = ? AND id < ?)) ORDER BY clock DESC,id DESC LIMIT ?'
NEW_PAGE = PAGE + ' AND (clock,id) < (?,?) ORDER BY clock DESC,id DESC LIMIT ?'
OLD_EXPORT = EXPORT + ' WHERE (clock > ? OR (clock = ? AND id > ?)) ORDER BY clock ASC,id ASC LIMIT 64'
NEW_EXPORT = EXPORT + ' WHERE (clock,id) > (?,?) ORDER BY clock ASC,id ASC LIMIT 64'


class HistorySeek(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.db = sqlite3.connect(':memory:')
        cls.db.executescript('CREATE TABLE messages (id TEXT PRIMARY KEY, device TEXT NOT NULL, character TEXT NOT NULL, chat TEXT NOT NULL, clock INTEGER NOT NULL, role TEXT NOT NULL, mode TEXT NOT NULL, text TEXT NOT NULL); CREATE INDEX messages_chat_order ON messages(chat, character, clock, id); CREATE INDEX messages_export_order ON messages(clock, id);')
        cls.db.executemany('INSERT INTO messages VALUES (?,?,?,?,?,?,?,?)', ((f'{i:08x}', 'device', 'character', 'chat' if i % 5 else 'other', i // 3 + 1, 'user', 'text', 'Привет 🌙') for i in range(60000)))
        cls.db.commit()

    @classmethod
    def tearDownClass(cls):
        cls.db.close()

    def query(self, sql, args):
        return self.db.execute(sql, args).fetchall()

    def measure(self, sql, args):
        ticks = 0
        def progress():
            nonlocal ticks
            ticks += 1
            return 0
        self.db.set_progress_handler(progress, 100)
        try:
            rows = self.query(sql, args)
        finally:
            self.db.set_progress_handler(None, 0)
        return rows, ticks * 100

    def test_page_equivalence(self):
        for clock, key in [('1', '00000002'), ('100', '00000129'), ('10000', '0000752f'), ('20001', 'ffffffff')]:
            with self.subTest(clock=clock, key=key):
                a = self.query(OLD_PAGE, ('chat', 'character', clock, clock, key, '30'))
                b = self.query(NEW_PAGE, ('chat', 'character', clock, key, '30'))
                self.assertEqual(a, b)
                self.assertTrue(all(r[3] == 'chat' and r[2] == 'character' for r in b))

    def test_export_equivalence(self):
        for clock, key in [('1', '00000000'), ('10000', '0000752f'), ('19900', '0000e900'), ('20001', 'ffffffff')]:
            self.assertEqual(self.query(OLD_EXPORT, (clock, clock, key)), self.query(NEW_EXPORT, (clock, key)))

    def test_tied_clocks_no_duplicates(self):
        first = self.query(PAGE + ' ORDER BY clock DESC,id DESC LIMIT 30', ('chat', 'character'))
        found = first[:]
        for _ in range(8):
            last = found[-1]
            found.extend(self.query(NEW_PAGE, ('chat', 'character', str(last[4]), last[0], '30')))
        self.assertEqual(found, self.query(PAGE + ' ORDER BY clock DESC,id DESC LIMIT 270', ('chat', 'character')))
        self.assertEqual(len({r[0] for r in found}), 270)

    def test_deep_history_seek(self):
        a, old = self.measure(OLD_PAGE, ('chat', 'character', '100', '100', '00000129', '30'))
        b, new = self.measure(NEW_PAGE, ('chat', 'character', '100', '00000129', '30'))
        self.assertEqual(a, b)
        self.assertEqual(len(b), 30)
        self.assertLess(new, 5000)
        print(f'SQLite {sqlite3.sqlite_version}: deep history OR={old} tuple={new} approximate VM instructions')

    def test_deep_export_seek(self):
        a, old = self.measure(OLD_EXPORT, ('19900', '19900', '0000e900'))
        b, new = self.measure(NEW_EXPORT, ('19900', '0000e900'))
        self.assertEqual(a, b)
        self.assertEqual(len(b), 64)
        self.assertLess(new, 5000)
        print(f'SQLite {sqlite3.sqlite_version}: deep export OR={old} tuple={new} approximate VM instructions')


if __name__ == '__main__':
    unittest.main()
