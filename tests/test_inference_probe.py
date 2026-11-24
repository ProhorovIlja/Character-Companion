import json
import unittest
from unittest.mock import patch
from contextlib import redirect_stdout, redirect_stderr
from io import StringIO

from shared.conversation import ChatError
from shared.inference_probe import main, probe


class Fixture:
    def __init__(self, responses):
        self.responses = iter(responses)
        self.requests = []
        self.closed = 0

    def stream(self, messages):
        self.requests.append(messages)
        try:
            for part in next(self.responses):
                if isinstance(part, Exception):
                    raise part
                yield part
        finally:
            self.closed += 1


class InferenceProbeTests(unittest.TestCase):
    def test_bilingual_timing(self):
        p = Fixture([['Hello', ' 🌙'], ['Привет']])
        ticks = iter([0, 5_000_000, 25_000_000, 30_000_000, 39_000_000, 50_000_000])
        report = probe(p, lambda: next(ticks))
        self.assertEqual(report['results'], [
            dict(language='en', first_text_ms=5.0, complete_ms=25.0, output_utf16_units=8),
            dict(language='ru', first_text_ms=9.0, complete_ms=20.0, output_utf16_units=6)])
        self.assertEqual(p.closed, 2)
        self.assertIn('Reply in English', p.requests[0][0]['content'])
        self.assertIn('Ответь по-русски', p.requests[1][0]['content'])
        self.assertNotIn('Привет', json.dumps(report, ensure_ascii=False))

    def test_reject_invalid(self):
        for parts in ([], ['   '], ['x'*16001], ['\x00'], [42], ['partial', ChatError('truncated')]):
            with self.subTest(parts=str(parts)[:16]):
                p = Fixture([parts])
                with self.assertRaises(ChatError):
                    probe(p)
                self.assertEqual(p.closed, 1)

    def test_second_failure(self):
        p = Fixture([['Hello'], [ChatError('no completion')]])
        with self.assertRaises(ChatError):
            probe(p)
        self.assertEqual(p.closed, 2)

    def test_cli_success(self):
        out = StringIO()
        with patch('shared.inference_probe.getpass.getpass', return_value='a'*48), \
             patch('shared.inference_probe.LocalProvider', return_value=Fixture([['Hello'], ['Привет']])) as factory, redirect_stdout(out):
            self.assertEqual(main(['--port', '8123']), 0)
        factory.assert_called_once_with('mandy-local', 8123, timeout=120, api_key='a'*48)
        self.assertEqual(len(json.loads(out.getvalue())['results']), 2)
        self.assertNotIn('a'*48, out.getvalue())

    def test_cli_failure_no_report(self):
        out, err = StringIO(), StringIO()
        with patch('shared.inference_probe.getpass.getpass', return_value='a'*48), \
             patch('shared.inference_probe.LocalProvider', return_value=Fixture([['Hello'], [ChatError('private text')]])), \
             redirect_stdout(out), redirect_stderr(err):
            self.assertEqual(main([]), 1)
        self.assertEqual(out.getvalue(), '')
        self.assertNotIn('private text', err.getvalue())

    def test_no_key(self):
        with patch('shared.inference_probe.getpass.getpass', return_value=''), \
             patch('shared.inference_probe.LocalProvider') as factory, redirect_stderr(StringIO()):
            self.assertEqual(main([]), 1)
            factory.assert_not_called()


if __name__ == '__main__':
    unittest.main()
