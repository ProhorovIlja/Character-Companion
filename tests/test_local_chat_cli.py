import json
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

from shared.conversation import ChatError, LocalProvider
from shared.local_chat_cli import display, history, initialize, load, run_turn


class LocalChatTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name) / 'private'
        self.identity = Path(temp.name) / 'identity.txt'
        self.identity.write_text('Мэнди — bilingual character.', encoding='utf-8')
        self.config = initialize(self.root, self.identity)
        self.requests = []
        self.key = 'a' * 48

    def provider(self, complete=True, status=200):
        requests, key = self.requests, self.key
        payload = ('data: ' + json.dumps({'choices': [{'delta': {'content': 'Привет 🌙'}, 'finish_reason': 'stop'}]}, ensure_ascii=False) + '\n\n').encode()
        if complete:
            payload += b'data: [DONE]\n\n'
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass
            def do_POST(self):
                requests.append((self.headers.get('Authorization'), json.loads(self.rfile.read(int(self.headers['Content-Length'])))))
                self.send_response(status if self.headers.get('Authorization') == 'Bearer ' + key else 401)
                self.send_header('Content-Type', 'text/event-stream')
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
        return LocalProvider('mandy-local', server.server_port, api_key=key)

    def test_bilingual_history(self):
        provider = self.provider()
        self.assertEqual(run_turn(self.root, provider, 'Hello'), 'Привет 🌙')
        self.assertEqual(run_turn(self.root, provider, 'Как дела?'), 'Привет 🌙')
        self.assertEqual([row['text'] for row in history(self.root)], ['Hello', 'Привет 🌙', 'Как дела?', 'Привет 🌙'])
        auth, request = self.requests[-1]
        self.assertEqual(auth, 'Bearer ' + self.key)
        self.assertIn('Мэнди', request['messages'][0]['content'])
        self.assertIn('strangers', request['messages'][0]['content'])
        self.assertEqual([m['content'] for m in request['messages'][1:]], ['Hello', 'Привет 🌙', 'Как дела?'])
        self.assertNotIn(self.key, (self.root / 'workspace.json').read_text('utf-8'))

    def test_retry(self):
        with self.assertRaises(ChatError):
            run_turn(self.root, self.provider(complete=False), 'Retry me')
        pending = history(self.root)
        self.assertEqual(len(pending), 1)
        with self.assertRaises(ChatError):
            run_turn(self.root, self.provider(), 'New message')
        self.assertEqual(run_turn(self.root, self.provider(), retry=True), 'Привет 🌙')
        self.assertEqual(history(self.root)[0], pending[0])
        with self.assertRaises(ChatError):
            run_turn(self.root, self.provider(), retry=True)
        self.assertEqual(len(history(self.root)), 2)

    def test_auth_refusal(self):
        provider = self.provider(status=401)
        with self.assertRaises(ChatError):
            run_turn(self.root, provider, 'No reply')
        self.assertEqual([r['role'] for r in history(self.root)], ['user'])

    def test_invalid_key(self):
        for key in ('', 'a'*31, 'x'*129, 'x'*40+'\r\n', '🌙'*40):
            with self.subTest(key=key[:5]), self.assertRaises(ValueError):
                LocalProvider('mandy-local', api_key=key)

    def test_refuse_overwrite(self):
        with self.assertRaises(FileExistsError):
            initialize(self.root, self.identity)
        self.assertEqual(load(self.root), self.config)

    def test_missing_database(self):
        (self.root / 'history.db').unlink()
        with self.assertRaises(ValueError):
            history(self.root)
        self.assertFalse((self.root / 'history.db').exists())

    def test_bad_config(self):
        path = self.root / 'workspace.json'
        path.write_text('{"version":1,"version":1}', encoding='utf-8')
        with self.assertRaises(ValueError):
            load(self.root)

    def test_display_controls(self):
        self.assertEqual(display('Привет 🌙\x1b[2J\x9b\r'), 'Привет 🌙\\x1b[2J\\x9b\\r')


if __name__ == '__main__':
    unittest.main()
