import json
import unittest
import uuid
from shared import continuity as c


def uid(n):
    return str(uuid.UUID(int=n))


def event(n=1, **changes):
    result = dict(id=uid(n), device=uid(100), character=uid(200), chat=uid(300),
                  clock=n, role="user", mode="text", text="Привет 🌙")
    result.update(changes)
    return result


class ContinuityTests(unittest.TestCase):
    def test_roundtrip(self):
        messages = [event(2, role="character", mode="call"), event(1, mode="dictation")]
        self.assertEqual(c.decode(c.encode(messages)), list(reversed(messages)))

    def test_offline_merge(self):
        a = [event(1), event(2)]
        b = [event(1), event(3, device=uid(101))]
        self.assertEqual(c.merge(a, b), c.merge(b, a))
        self.assertEqual(c.merge(a, a), a)
        self.assertEqual(c.merge(c.merge(a, b), a), c.merge(a, b))
        self.assertEqual(c.encode(c.merge(a, b)), c.encode(c.merge(b, a)))
        self.assertEqual(len(c.merge(a, b)), 3)

    def test_conflicts(self):
        with self.assertRaises(ValueError):
            c.merge([event()], [event(text="different")])
        with self.assertRaises(ValueError):
            c.merge([event()], [event(2, character=uid(201))])
        self.assertEqual(len(c.merge([event()], [event(2, character=uid(201), chat=uid(301))])), 2)

    def test_invalid_fields(self):
        for change in (dict(id="bad"), dict(clock=True), dict(clock=0), dict(clock=c.MAX_CLOCK+1),
                       dict(role="system"), dict(mode="audio"), dict(text=""), dict(text="\x00"),
                       dict(text="\ud800"), dict(text="😀"*8001), dict(path="secret")):
            with self.subTest(change=str(change)[:100]), self.assertRaises(ValueError):
                c.encode([event(**change)])
        self.assertEqual(c.decode(c.encode([event(text="😀"*8000)]))[0]["text"], "😀"*8000)

    def test_wire_validation(self):
        for raw in (b'{"version":1,"version":1,"events":[]}', b'{"version":true,"events":[]}',
                    b'{"version":2,"events":[]}', b'\xff', b'[]', b'{"version":1,"events":{}}',
                    b' '* (c.MAX_BYTES+1), b'['*2000+b']'*2000):
            with self.subTest(raw=raw[:60]), self.assertRaises(ValueError):
                c.decode(raw)
        self.assertEqual(c.decode(c.encode([])), [])

    def test_capacity(self):
        full = [event(i) for i in range(1, c.MAX_EVENTS+1)]
        with self.assertRaises(ValueError):
            c.merge(full, [event(c.MAX_EVENTS+1)])
        with self.assertRaises(ValueError):
            c.encode([event(i, text="x"*16000) for i in range(1, 300)])

    def test_no_input_mutation(self):
        original = event()
        result = c.merge([original])
        result[0]["text"] = "edit"
        self.assertEqual(original["text"], "Привет 🌙")

    def test_equal_clock_tie(self):
        first, second = event(1, clock=5), event(2, clock=5)
        self.assertEqual(c.merge([second], [first]), [first, second])


if __name__ == "__main__":
    unittest.main()
