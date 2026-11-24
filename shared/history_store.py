"""Local SQLite history reference; no network, background jobs or runtime SDK."""
import sqlite3
from contextlib import contextmanager
from . import continuity as wire

SCHEMA = (
    "CREATE TABLE IF NOT EXISTS vault (singleton INTEGER PRIMARY KEY CHECK(singleton=1), account TEXT NOT NULL, clock INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS chats (id TEXT PRIMARY KEY, character TEXT NOT NULL, UNIQUE(id, character))",
    "CREATE TABLE IF NOT EXISTS messages (id TEXT PRIMARY KEY, device TEXT NOT NULL, character TEXT NOT NULL, chat TEXT NOT NULL, clock INTEGER NOT NULL, role TEXT NOT NULL, mode TEXT NOT NULL, text TEXT NOT NULL, FOREIGN KEY(chat, character) REFERENCES chats(id, character))",
    "CREATE INDEX IF NOT EXISTS messages_chat_order ON messages(chat, character, clock, id)",
    "CREATE INDEX IF NOT EXISTS messages_export_order ON messages(clock, id)",
)
COLS = "id, device, character, chat, clock, role, mode, text"
KEYS = COLS.split(", ")


class HistoryStore:
    """One account per database; use a local filesystem and one instance per thread."""
    def __init__(self, path, account):
        wire._uuid(account)
        self.db = sqlite3.connect(path, timeout=5, isolation_level=None)
        self.db.row_factory = sqlite3.Row
        try:
            self.db.execute("PRAGMA foreign_keys=ON")
            self.db.execute("PRAGMA journal_mode=WAL")
            self.db.execute("PRAGMA synchronous=FULL")
            with self._write():
                version = self.db.execute("PRAGMA user_version").fetchone()[0]
                if version not in (0, 1):
                    raise ValueError("unsupported history schema")
                if version == 0 and self.db.execute("SELECT 1 FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' LIMIT 1").fetchone():
                    raise ValueError("not an empty history database")
                if version == 0:
                    for sql in SCHEMA:
                        self.db.execute(sql)
                    self.db.execute("INSERT INTO vault VALUES (1, ?, 0)", (account,))
                    self.db.execute("PRAGMA user_version=1")
                row = self.db.execute("SELECT account FROM vault WHERE singleton=1").fetchone()
                if row is None or row[0] != account:
                    raise ValueError("account mismatch")
        except BaseException:
            self.db.close()
            raise

    @contextmanager
    def _write(self):
        self.db.execute("BEGIN IMMEDIATE")
        try:
            yield
            self.db.commit()
        except BaseException:
            self.db.rollback()
            raise

    def close(self):
        self.db.close()

    def _insert(self, event):
        existing = self.db.execute("SELECT " + COLS + " FROM messages WHERE id=?", (event["id"],)).fetchone()
        if existing is not None:
            if dict(existing) != event:
                raise ValueError("conflicting message ID")
            return 0
        owner = self.db.execute("SELECT character FROM chats WHERE id=?", (event["chat"],)).fetchone()
        if owner is None:
            self.db.execute("INSERT INTO chats VALUES (?, ?)", (event["chat"], event["character"]))
        elif owner[0] != event["character"]:
            raise ValueError("conflicting chat owner")
        self.db.execute("INSERT INTO messages (" + COLS + ") VALUES (?,?,?,?,?,?,?,?)", tuple(event[k] for k in KEYS))
        self.db.execute("UPDATE vault SET clock=max(clock, ?) WHERE singleton=1", (event["clock"],))
        return 1

    def import_batch(self, data):
        """Validate the whole bounded wire batch; reject conflicts without partial writes."""
        events = wire.decode(data)
        with self._write():
            return sum(self._insert(event) for event in events)

    def append(self, *, id, device, character, chat, role, mode, text):
        """Assign the next logical clock atomically, preserving retry idempotence."""
        with self._write():
            existing = self.db.execute("SELECT " + COLS + " FROM messages WHERE id=?", (id,)).fetchone()
            clock = existing["clock"] if existing else self.db.execute("SELECT clock FROM vault WHERE singleton=1").fetchone()[0] + 1
            event = wire.merge([dict(id=id, device=device, character=character, chat=chat, clock=clock, role=role, mode=mode, text=text)])[0]
            self._insert(event)
            return event

    @staticmethod
    def _cursor(cursor):
        if type(cursor) is not tuple or len(cursor) != 2 or type(cursor[0]) is not int or not 1 <= cursor[0] <= wire.MAX_CLOCK:
            raise ValueError("invalid cursor")
        wire._uuid(cursor[1])
        return cursor

    def page(self, character, chat, *, before=None, limit=50):
        """Newest-first page; pass (clock,id) of final row for older messages."""
        wire._uuid(character)
        wire._uuid(chat)
        if type(limit) is not int or not 1 <= limit <= 200:
            raise ValueError("invalid page limit")
        sql = "SELECT " + COLS + " FROM messages WHERE chat=? AND character=?"
        args = [chat, character]
        if before is not None:
            sql += " AND (clock,id) < (?,?)"
            args.extend(self._cursor(before))
        return [dict(r) for r in self.db.execute(sql + " ORDER BY clock DESC,id DESC LIMIT ?", (*args, limit))]

    def export_batches(self):
        """Bounded consistent snapshot of all history. Exhaust or close generator promptly."""
        self.db.execute("BEGIN")
        try:
            rows = self.db.execute("SELECT " + COLS + " FROM messages ORDER BY clock,id")
            batch, size = [], len(b'{"events":[],"version":1}')
            for row in rows:
                event = dict(row)
                # Single-event envelope minus empty envelope gives exact event bytes.
                extra = len(wire.encode([event])) - len(b'{"events":[],"version":1}')
                if batch and (len(batch) == wire.MAX_EVENTS or size + extra + 1 > wire.MAX_BYTES):
                    yield wire.encode(batch)
                    batch, size = [], len(b'{"events":[],"version":1}')
                size += extra + bool(batch)
                batch.append(event)
            if batch:
                yield wire.encode(batch)
        finally:
            self.db.rollback()
