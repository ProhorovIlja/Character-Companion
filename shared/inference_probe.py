"""Manually invoked synthetic EN/RU local inference probe; no history or server launch."""
import argparse
import getpass
import json
import sys
import time
import warnings
from contextlib import closing

from .conversation import ChatError, LocalProvider, units

CASES = (
    ('en', 'Reply in English in two short sentences.', 'Tell me about a quiet walk in a park.'),
    ('ru', 'Ответь по-русски двумя короткими предложениями.', 'Расскажи о тихой прогулке в парке.'),
)


def probe(provider, clock=time.perf_counter_ns):
    results = []
    for language, identity, prompt in CASES:
        messages = [dict(role='system', content=identity), dict(role='user', content=prompt)]
        start = clock()
        first = None
        count = 0
        nonblank = False
        with closing(provider.stream(messages)) as stream:
            for fragment in stream:
                if not isinstance(fragment, str):
                    raise ChatError('invalid response fragment')
                if not fragment:
                    continue
                if first is None:
                    first = clock()
                count += units(fragment)
                if count > 16000 or any(ord(c) < 32 and c not in '\t\r\n' for c in fragment):
                    raise ChatError('invalid response text')
                nonblank = nonblank or bool(fragment.strip())
        end = clock()
        if first is None or not nonblank or not start <= first <= end:
            raise ChatError('invalid or empty completion')
        results.append(dict(language=language, first_text_ms=round((first-start)/1e6, 3),
                            complete_ms=round((end-start)/1e6, 3), output_utf16_units=count))
    return {'schema': 1, 'case_set': 'synthetic-en-ru-v1', 'results': results}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8080)
    parser.add_argument('--model', default='mandy-local')
    args = parser.parse_args(argv)
    try:
        with warnings.catch_warnings():
            warnings.simplefilter('error', getpass.GetPassWarning)
            key = getpass.getpass('Trusted local server API key: ')
        if not key:
            raise ValueError('local API key required')
        result = probe(LocalProvider(args.model, args.port, timeout=120, api_key=key))
        print(json.dumps(result, ensure_ascii=True, indent=2))
        return 0
    except (Exception, KeyboardInterrupt) as error:
        print('Probe failed (' + type(error).__name__ + '). No successful report emitted.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
