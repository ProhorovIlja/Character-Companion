import json
import tempfile
import threading
import unittest
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

from shared.conversation import ChatError, LocalProvider, chat_turn
from shared.history_store import HistoryStore


def uid(n):
    return str(uuid.UUID(int=n))


def frame(content=None, reason=None, **delta):
    return ('data: ' + json.dumps({'choices': [{'index': 0, 'delta': dict(content=content, **delta), 'finish_reason': reason}]}, ensure_ascii=False) + '\n\n').encode()


class ConversationTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.path = Path(tmp.name) / 'history.db'
        self.store = HistoryStore(self.path, uid(99))
        self.addCleanup(self.store.close)
        self.args = dict(character=uid(1), chat=uid(2), device=uid(3), message_id=uid(4), text='Привет 🌙', identity='You are a character.')
        self.requests = []

    def provider(self, payload, status=200, mime='text/event-stream'):
        requests = self.requests
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass
            def do_POST(self):
                requests.append((self.path, json.loads(self.rfile.read(int(self.headers['Content-Length'])))))
                self.send_response(status)
                self.send_header('Content-Type', mime)
                self.end_headers()
                self.wfile.write(payload)
        server = HTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        def close():
            server.shutdown()
            thread.join()
            server.server_close()
        self.addCleanup(close)
        return LocalProvider('test-model', server.server_port)

    def history(self):
        return self.store.page(uid(1), uid(2))

    def test_roundtrip_and_retry(self):
        p = self.provider(frame('Привет') + frame('! 🌙', 'stop') + b'data: [DONE]\n\n')
        self.assertEqual(list(chat_turn(self.store, p, **self.args)), ['Привет', '! 🌙'])
        self.assertEqual([r['text'] for r in self.history()], ['Привет! 🌙', self.args['text']])
        self.assertEqual(list(chat_turn(self.store, p, **self.args)), ['Привет! 🌙'])
        self.assertEqual(len(self.requests), 1)
        self.assertEqual(self.requests[0][0], '/v1/chat/completions')
        self.assertEqual(self.requests[0][1]['messages'], [{'role': 'system', 'content': self.args['identity']}, {'role': 'user', 'content': self.args['text']}])

    def test_truncated_and_retry(self):
        p = self.provider(frame('partial'))
        with self.assertRaises(ChatError):
            list(chat_turn(self.store, p, **self.args))
        self.assertEqual(len(self.history()), 1)
        p = self.provider(frame('complete', 'stop') + b'data: [DONE]\n\n')
        self.assertEqual(list(chat_turn(self.store, p, **self.args)), ['complete'])
        self.assertEqual(len(self.history()), 2)

    def test_cancel_does_not_commit(self):
        p = self.provider(frame('first') + frame('second', 'stop') + b'data: [DONE]\n\n')
        stream = chat_turn(self.store, p, **self.args)
        self.assertEqual(next(stream), 'first')
        stream.close()
        self.assertEqual(len(self.history()), 1)

    def test_errors_no_assistant(self):
        for payload, status, mime in [(b'', 302, 'text/event-stream'), (b'{}', 200, 'application/json'),
                                     (frame('x', 'length') + b'data: [DONE]\n\n', 200, 'text/event-stream'),
                                     (b'data: [DONE]\n\n', 200, 'text/event-stream'),
                                     (frame(tool_calls=[{}]), 200, 'text/event-stream')]:
            with self.subTest(status=status, payload=payload[:40]):
                with self.assertRaises(ChatError):
                    list(chat_turn(self.store, self.provider(payload, status, mime), **self.args))
        self.assertEqual(len(self.history()), 1)

    def test_reasoning_excluded(self):
        p = self.provider(frame(reasoning_content='private') + frame('visible', 'stop') + b'data: [DONE]\n\n')
        self.assertEqual(list(chat_turn(self.store, p, **self.args)), ['visible'])
        self.assertEqual(self.history()[0]['text'], 'visible')

    def test_history_scope_and_budget(self):
        self.store.append(id=uid(8), device=uid(3), character=uid(1), chat=uid(2), role='user', mode='text', text='x'*300)
        self.store.append(id=uid(9), device=uid(3), character=uid(7), chat=uid(6), role='user', mode='text', text='private other character')
        p = self.provider(frame('ok', 'stop') + b'data: [DONE]\n\n')
        list(chat_turn(self.store, p, **self.args, context_units=256))
        self.assertEqual(len(self.requests[0][1]['messages']), 2)

    def test_concurrent_advance_rejected(self):
        p = self.provider(frame('partial') + frame('end', 'stop') + b'data: [DONE]\n\n')
        stream = chat_turn(self.store, p, **self.args)
        self.assertEqual(next(stream), 'partial')
        other = HistoryStore(self.path, uid(99))
        try:
            other.append(id=uid(10), device=uid(11), character=uid(1), chat=uid(2), role='user', mode='text', text='newer')
        finally:
            other.close()
        with self.assertRaises(ChatError):
            list(stream)
        self.assertEqual(len(self.history()), 2)

    def test_oversized_reply(self):
        p = self.provider(frame('x'*16001, 'stop') + b'data: [DONE]\n\n')
        with self.assertRaises(ChatError):
            list(chat_turn(self.store, p, **self.args))
        self.assertEqual(len(self.history()), 1)


if __name__ == '__main__':
    unittest.main()
