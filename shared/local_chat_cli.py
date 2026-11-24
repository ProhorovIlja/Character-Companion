"""Explicit local chat reference CLI (Windows/Linux); no server launch or remote fallback."""
import argparse
import getpass
import json
import sys
import uuid
import warnings
from contextlib import closing
from pathlib import Path

from . import continuity as wire
from .conversation import ChatError, LocalProvider, chat_turn, units
from .history_store import HistoryStore
from .relationships import Relationships


IDS = ('account', 'device', 'character', 'chat')
CONFIG = 'workspace.json'
DATABASE = 'history.db'


def read_bounded(path, limit):
    with path.open('rb') as source:
        data = source.read(limit + 1)
    if len(data) > limit:
        raise ValueError('file too large')
    return data.decode('utf-8')


def validate_identity(identity):
    if not isinstance(identity, str) or not identity.strip() or units(identity) > 8000:
        raise ValueError('invalid character identity')
    if any(ord(c) < 32 and c not in '\t\r\n' for c in identity):
        raise ValueError('invalid character identity')
    return identity


def initialize(root, identity_file):
    identity = validate_identity(read_bounded(identity_file, 32000))
    config = dict(version=1, identity=identity, **{key: str(uuid.uuid4()) for key in IDS})
    root.mkdir()  # New directory only; never replace an existing workspace.
    with (root / CONFIG).open('x', encoding='utf-8') as target:
        json.dump(config, target, ensure_ascii=False)
    with closing(HistoryStore(root / DATABASE, config['account'])):
        pass
    return config


def load(root):
    config = json.loads(read_bounded(root / CONFIG, 64000), object_pairs_hook=wire._unique)
    if type(config) is not dict or set(config) != {'version', 'identity', *IDS} or type(config['version']) is not int or config['version'] != 1:
        raise ValueError('unsupported workspace format')
    for key in IDS:
        wire._uuid(config[key])
    validate_identity(config['identity'])
    if not (root / DATABASE).is_file():
        raise ValueError('history missing; refusing to create replacement')
    return config


def run_turn(root, provider, text=None, *, retry=False):
    config = load(root)
    with closing(HistoryStore(root / DATABASE, config['account'])) as store:
        latest = store.page(config['character'], config['chat'], limit=1)
        pending = latest[0] if latest and latest[0]['role'] == 'user' else None
        if retry:
            if text is not None or pending is None or pending['device'] != config['device']:
                raise ChatError('no eligible pending turn')
            message_id, text, mode = pending['id'], pending['text'], pending['mode']
        else:
            if pending is not None:
                raise ChatError('retry the pending message before sending another')
            if not isinstance(text, str) or not text.strip():
                raise ValueError('empty message')
            message_id, mode = str(uuid.uuid4()), 'text'
        # Only publish the completed, persisted reply. Do not print partial model output.
        return ''.join(chat_turn(store, provider, character=config['character'], chat=config['chat'],
                                 device=config['device'], message_id=message_id, text=text,
                                 identity=config['identity'], mode=mode, relationships=Relationships(store)))


def history(root):
    config = load(root)
    with closing(HistoryStore(root / DATABASE, config['account'])) as store:
        return list(reversed(store.page(config['character'], config['chat'], limit=50)))


def display(text):
    # Avoid terminal escape/control sequences from model output or stored text.
    return ''.join(c if c.isprintable() or c in '\n\t' else ascii(c)[1:-1] for c in text)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    for name in ('init', 'send', 'retry', 'history'):
        command = commands.add_parser(name)
        command.add_argument('workspace', type=Path)
        if name == 'init':
            command.add_argument('--identity', required=True, type=Path)
        elif name in ('send', 'retry'):
            command.add_argument('--port', type=int, default=8080)
            command.add_argument('--model', default='mandy-local')
    args = parser.parse_args(argv)
    try:
        if args.command == 'init':
            initialize(args.workspace, args.identity)
            print('Workspace created. Local history is not encrypted at rest.')
        elif args.command == 'history':
            for row in history(args.workspace):
                print(row['role'] + ': ' + display(row['text']))
        else:
            load(args.workspace)
            with warnings.catch_warnings():
                warnings.simplefilter('error', getpass.GetPassWarning)
                key = getpass.getpass('Local server API key: ')
            if not key:
                raise ValueError('local API key required')
            provider = LocalProvider(args.model, args.port, timeout=120, api_key=key)
            text = input('You: ') if args.command == 'send' else None
            print('Character: ' + display(run_turn(args.workspace, provider, text, retry=args.command == 'retry')))
        return 0
    except (Exception, KeyboardInterrupt) as error:
        # Never echo request bodies, credentials, paths or exception payloads.
        print('Local operation failed (' + type(error).__name__ + '). Check workspace/history and use retry for a pending turn.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
