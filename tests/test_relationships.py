import tempfile
import unittest
import uuid
from pathlib import Path
from shared.history_store import HistoryStore
from shared.relationships import Relationships
from shared.conversation import chat_turn


def uid(n):
    return str(uuid.UUID(int=n))


class Model:
    def stream(self, messages):
        self.messages = messages
        yield 'Hello there.'


class RelationshipTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.path = Path(tmp.name) / 'history.db'
        self.store = HistoryStore(self.path, uid(99))
        self.addCleanup(self.store.close)
        self.rel = Relationships(self.store)

    def pair(self, n, character=1, chat=2, mode='text'):
        for i, role in enumerate(('user', 'character')):
            self.store.append(id=uid(n+i), device=uid(3), character=uid(character), chat=uid(chat), role=role, mode=mode, text='Conversation evidence')
        return dict(user_message=uid(n), character_message=uid(n+1))

    def revise(self, n, stage='friends', revision=0, **kw):
        return self.rel.revise(uid(1), expected_revision=revision, stage=stage, note='Shared history', approved=True, **self.pair(n, **kw))

    def test_progression_and_persistence(self):
        self.assertEqual(self.rel.current(uid(1))['stage'], 'strangers')
        self.revise(10)
        self.revise(12, 'close_friends', 1, mode='call')
        self.revise(14, 'romantic', 2, mode='dictation')
        self.revise(16, 'former_partners', 3)
        other = HistoryStore(self.path, uid(99))
        try:
            self.assertEqual(Relationships(other).current(uid(1))['stage'], 'former_partners')
        finally:
            other.close()
        self.assertEqual([x['revision'] for x in self.rel.history(uid(1), limit=2)], [4, 3])
        self.assertEqual([x['revision'] for x in self.rel.history(uid(1), before=3)], [2, 1])

    def test_isolation_and_no_auto_escalation(self):
        self.pair(10)
        self.assertEqual(self.rel.current(uid(1))['revision'], 0)
        self.revise(12)
        self.assertEqual(self.rel.current(uid(7))['stage'], 'strangers')
        with self.assertRaises(ValueError):
            self.revise(14, revision=1, character=7, chat=8)
        self.assertEqual(self.rel.current(uid(1))['revision'], 1)

    def test_approval_and_stale(self):
        evidence = self.pair(10)
        with self.assertRaises(ValueError):
            self.rel.revise(uid(1), expected_revision=0, stage='romantic', note='note', **evidence)
        self.revise(12)
        with self.assertRaises(ValueError):
            self.revise(14, revision=0)
        with self.assertRaises(ValueError):
            self.rel.revise(uid(1), expected_revision=1, stage='romantic', note='note', approved=True, **evidence)
        self.assertEqual(self.rel.current(uid(1))['stage'], 'friends')

    def test_invalid_evidence(self):
        a = self.pair(10)
        b = self.pair(12, chat=8)
        for evidence in (dict(user_message=a['character_message'], character_message=a['user_message']), dict(user_message=a['user_message'], character_message=b['character_message']), dict(user_message=uid(80), character_message=uid(81))):
            with self.assertRaises(ValueError):
                self.rel.revise(uid(1), expected_revision=0, stage='friends', note='note', approved=True, **evidence)
        self.assertEqual(self.rel.history(uid(1)), [])

    def test_provider_chat_turn(self):
        self.revise(10)
        model = Model()
        result = list(chat_turn(self.store, model, character=uid(1), chat=uid(2), device=uid(3), message_id=uid(20), text='Hi', identity='Character identity', relationships=self.rel))
        self.assertEqual(result, ['Hello there.'])
        self.assertIn('friends', model.messages[0]['content'])
        self.assertEqual(self.rel.current(uid(1))['revision'], 1)
        other = Model()
        list(chat_turn(self.store, other, character=uid(7), chat=uid(8), device=uid(3), message_id=uid(22), text='Hi', identity='Other identity', relationships=self.rel))
        self.assertIn('strangers', other.messages[0]['content'])
        self.assertNotIn('Shared history', other.messages[0]['content'])


if __name__ == '__main__':
    unittest.main()
