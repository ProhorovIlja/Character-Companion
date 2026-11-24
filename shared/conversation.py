"""Foreground reference chat: local streaming provider and immutable SQLite history."""
import http.client
import json
import uuid

from . import continuity as wire

MAX_STREAM = 2 * 1024 * 1024
MAX_LINE = 128 * 1024


class ChatError(RuntimeError):
    pass


def units(text):
    return len(text.encode('utf-16-le')) // 2


class LocalProvider:
    """Only numeric IPv4 loopback; no redirects, proxies, credentials or cloud fallback."""
    def __init__(self, model, port=8080, timeout=30):
        if not isinstance(model, str) or not model.strip() or len(model) > 256:
            raise ValueError('invalid model')
        if type(port) is not int or not 1 <= port <= 65535 or not 1 <= timeout <= 120:
            raise ValueError('invalid connection settings')
        self.model, self.port, self.timeout = model, port, timeout

    def stream(self, messages):
        body = json.dumps(dict(model=self.model, messages=messages, stream=True,
                               max_tokens=1024), ensure_ascii=False).encode('utf-8')
        conn = http.client.HTTPConnection('127.0.0.1', self.port, timeout=self.timeout)
        try:
            conn.request('POST', '/v1/chat/completions', body,
                         {'Content-Type': 'application/json', 'Accept': 'text/event-stream'})
            with conn.getresponse() as response:
                if response.status != 200 or response.getheader('Content-Type', '').split(';')[0].strip().lower() != 'text/event-stream':
                    raise ChatError('local provider rejected streaming request')
                total, finished, parts = 0, False, []
                while True:
                    line = response.readline(MAX_LINE + 1)
                    total += len(line)
                    if not line or len(line) > MAX_LINE or total > MAX_STREAM:
                        raise ChatError('incomplete or oversized stream')
                    line = line.rstrip(b'\r\n')
                    if line.startswith(b'data:'):
                        parts.append(line[5:].removeprefix(b' '))
                    if line or not parts:
                        continue
                    data = b'\n'.join(parts)
                    parts.clear()
                    if data == b'[DONE]':
                        if not finished:
                            raise ChatError('missing completion status')
                        return
                    try:
                        obj = json.loads(data.decode('utf-8'), object_pairs_hook=wire._unique)
                        choices = obj['choices']
                        if obj.get('error') or not isinstance(choices, list):
                            raise ValueError('invalid choices')
                        if not choices:  # optional usage frame
                            continue
                        if finished or len(choices) != 1 or choices[0].get('index', 0) != 0:
                            raise ValueError('unexpected choice')
                        choice = choices[0]
                        delta = choice['delta']
                        if not isinstance(delta, dict) or delta.get('tool_calls') or delta.get('function_call'):
                            raise ValueError('unsupported delta')
                        content = delta.get('content')
                        if content is not None and not isinstance(content, str):
                            raise ValueError('invalid content')
                        reason = choice.get('finish_reason')
                        if reason is not None and reason != 'stop':
                            raise ValueError('generation did not finish normally')
                        finished = reason == 'stop'
                    except (ValueError, KeyError, TypeError, AttributeError, RecursionError) as exc:
                        raise ChatError('invalid or unfinished completion') from exc
                    if content:
                        yield content
        except (OSError, http.client.HTTPException) as exc:
            raise ChatError('local provider connection failed') from exc
        finally:
            conn.close()


def _latest(store, character, chat):
    rows = store.page(character, chat, limit=1)
    return rows[0]['id'] if rows else None


def chat_turn(store, provider, *, character, chat, device, message_id, text,
              identity, mode='text', context_units=12000, relationships=None):
    """Yield display fragments; persist only a normally completed text reply.

    Consume on one thread. Close iterator to cancel; user text remains recoverable.
    A concurrent history advance rejects the final reply rather than attaching it
    to the wrong turn. This reference uses HistoryStore's transaction boundary.
    """
    if not isinstance(identity, str) or not identity.strip() or units(identity) > 8000:
        raise ValueError('invalid character identity')
    if type(context_units) is not int or not 256 <= context_units <= 64000:
        raise ValueError('invalid context budget')
    if relationships is not None:
        if relationships.store is not store:
            raise ValueError('relationship store mismatch')
        identity += '\n' + relationships.context(character)
    if units(identity) + units(text) > context_units:
        raise ValueError('latest message exceeds context budget')
    user = store.append(id=message_id, device=device, character=character, chat=chat,
                        role='user', mode=mode, text=text)
    reply_id = str(uuid.uuid5(uuid.UUID(message_id), 'companion-reply-v1'))
    old = store.db.execute('SELECT text, character, chat, role, device, mode FROM messages WHERE id=?', (reply_id,)).fetchone()
    if old:
        if (old['character'], old['chat'], old['role'], old['device'], old['mode']) != (character, chat, 'character', device, mode):
            raise ChatError('reply ID conflict')
        yield old['text']
        return
    rows = store.page(character, chat, limit=200)
    if not rows or rows[0]['id'] != user['id']:
        raise ChatError('conversation advanced; start a new turn')
    remaining = context_units - units(identity)
    recent = []
    for row in rows:
        size = units(row['text'])
        if size > remaining:
            break
        remaining -= size
        recent.append(dict(role='assistant' if row['role'] == 'character' else 'user', content=row['text']))
    messages = [dict(role='system', content=identity), *reversed(recent)]
    fragments, size = [], 0
    stream = provider.stream(messages)
    try:
        for fragment in stream:
            if not isinstance(fragment, str):
                raise ChatError('invalid provider fragment')
            size += units(fragment)
            if size > wire.MAX_TEXT or len(fragments) >= 16000:
                raise ChatError('reply exceeds history capacity')
            if fragment:
                fragments.append(fragment)
                yield fragment
    finally:
        stream.close()
    reply = ''.join(fragments)
    if not reply.strip():
        raise ChatError('empty reply')
    with store._write():
        if _latest(store, character, chat) != message_id:
            raise ChatError('conversation advanced during generation')
        clock = store.db.execute('SELECT clock FROM vault WHERE singleton=1').fetchone()[0] + 1
        event = wire.merge([dict(id=reply_id, device=device, character=character, chat=chat,
                                 clock=clock, role='character', mode=mode, text=reply)])[0]
        store._insert(event)
