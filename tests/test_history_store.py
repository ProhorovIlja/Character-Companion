import tempfile
import unittest
import uuid
from pathlib import Path
from shared import continuity as wire
from shared.history_store import HistoryStore


def uid(n):
    return str(uuid.UUID(int=n))


def event(n, **changes):
    e = dict(id=uid(n), device=uid(10000), character=uid(20000), chat=uid(30000), clock=n, role="user", mode="text", text="Привет 🌙")
    e.update(changes)
    return e


class HistoryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "history.db"
        self.store = HistoryStore(self.path, uid(40000))
        self.addCleanup(lambda: self.store.close())

    def test_persistence_and_account(self):
        self.store.import_batch(wire.encode([event(1)]))
        self.store.close()
        self.store = HistoryStore(self.path, uid(40000))
        self.assertEqual(self.store.page(uid(20000), uid(30000)), [event(1)])
        with self.assertRaises(ValueError):
            HistoryStore(self.path, uid(40001))

    def test_idempotence_and_clock(self):
        self.assertEqual(self.store.import_batch(wire.encode([event(100)])), 1)
        self.assertEqual(self.store.import_batch(wire.encode([event(100)])), 0)
        new = event(2)
        del new["clock"]
        e = self.store.append(**new)
        self.assertEqual(e["clock"], 101)
        self.assertEqual(self.store.append(**new), e)
        other = HistoryStore(self.path, uid(40000))
        try:
            new["id"] = uid(3)
            self.assertEqual(other.append(**new)["clock"], 102)
        finally:
            other.close()

    def test_rollback_conflicts(self):
        self.store.import_batch(wire.encode([event(10)]))
        with self.assertRaises(ValueError):
            self.store.import_batch(wire.encode([event(1), event(10, text="conflict")]))
        with self.assertRaises(ValueError):
            self.store.import_batch(wire.encode([event(1, chat=uid(30001)), event(2, character=uid(20001))]))
        self.assertEqual(self.store.page(uid(20000), uid(30000)), [event(10)])
        self.assertEqual(self.store.page(uid(20000), uid(30001)), [])

    def test_pagination_and_isolation(self):
        events = [event(i, clock=5) for i in range(1, 8)]
        self.store.import_batch(wire.encode(events + [event(8, character=uid(20001), chat=uid(30001))]))
        got, cursor = [], None
        while True:
            page = self.store.page(uid(20000), uid(30000), before=cursor, limit=2)
            if not page:
                break
            got += page
            cursor = (page[-1]["clock"], page[-1]["id"])
        self.assertEqual(got, list(reversed(events)))
        self.assertEqual(self.store.page(uid(20001), uid(30000)), [])

    def test_unbounded_history_bounded_export(self):
        events = [event(i) for i in range(1, wire.MAX_EVENTS + 4)]
        for i in range(0, len(events), 1000):
            self.store.import_batch(wire.encode(events[i:i+1000]))
        batches = list(self.store.export_batches())
        self.assertGreater(len(batches), 1)
        self.assertEqual([e for b in batches for e in wire.decode(b)], events)

    def test_byte_budget_and_snapshot(self):
        events = [event(i, text="🌙"*8000) for i in range(1, 151)]
        for i in range(0, len(events), 50):
            self.store.import_batch(wire.encode(events[i:i+50]))
        batches = self.store.export_batches()
        first = next(batches)
        other = HistoryStore(self.path, uid(40000))
        try:
            other.import_batch(wire.encode([event(151)]))
            self.assertEqual([e for b in [first, *batches] for e in wire.decode(b)], events)
            self.assertEqual(len(self.store.page(uid(20000), uid(30000), limit=200)), 151)
        finally:
            other.close()

    def test_invalid_arguments(self):
        for value in (0, 201, True):
            with self.assertRaises(ValueError):
                self.store.page(uid(20000), uid(30000), limit=value)
        with self.assertRaises(ValueError):
            self.store.page(uid(20000), uid(30000), before=(0, uid(1)))
        with self.assertRaises(ValueError):
            self.store.import_batch(b"broken")
        self.assertEqual(list(self.store.export_batches()), [])

    def test_future_schema_rejected(self):
        self.store.db.execute("PRAGMA user_version=99")
        with self.assertRaises(ValueError):
            HistoryStore(self.path, uid(40000))


if __name__ == "__main__":
    unittest.main()
