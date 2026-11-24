"""Reference implementation of the portable text-message protocol, not cloud transport."""
import json
import uuid

MAX_BYTES = 4 * 1024 * 1024
MAX_EVENTS = 2048
MAX_TEXT = 16000
MAX_CLOCK = (1 << 53) - 1
FIELDS = {"id", "device", "character", "chat", "clock", "role", "mode", "text"}


def _uuid(value):
    if not isinstance(value, str) or len(value) != 36:
        raise ValueError("invalid UUID")
    if str(uuid.UUID(value)) != value:
        raise ValueError("noncanonical UUID")


def _event(event):
    if type(event) is not dict or event.keys() != FIELDS:
        raise ValueError("invalid event fields")
    for key in ("id", "device", "character", "chat"):
        _uuid(event[key])
    clock = event["clock"]
    if type(clock) is not int or not 1 <= clock <= MAX_CLOCK:
        raise ValueError("invalid logical clock")
    if event["role"] not in ("user", "character") or event["mode"] not in ("text", "dictation", "call"):
        raise ValueError("invalid role or mode")
    text = event["text"]
    if not isinstance(text, str) or not text.strip() or len(text.encode("utf-16-le")) // 2 > MAX_TEXT:
        raise ValueError("invalid text")
    if any(ord(c) < 32 and c not in "\t\r\n" for c in text):
        raise ValueError("invalid control character")
    return dict(event)


def merge(*batches):
    """Union immutable messages, reject conflicting IDs or chat ownership; never overwrite."""
    events, owners = {}, {}
    for batch in batches:
        if type(batch) is not list or len(batch) > MAX_EVENTS:
            raise ValueError("invalid batch size")
        for raw in batch:
            event = _event(raw)
            key = event["id"]
            if key in events and events[key] != event:
                raise ValueError("conflicting message ID")
            if owners.setdefault(event["chat"], event["character"]) != event["character"]:
                raise ValueError("conflicting chat owner")
            events[key] = event
            if len(events) > MAX_EVENTS:
                raise ValueError("batch capacity exceeded")
    return sorted(events.values(), key=lambda e: (e["clock"], e["id"]))


def _unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON key")
        result[key] = value
    return result


def encode(events):
    result = json.dumps({"version": 1, "events": merge(events)}, ensure_ascii=False,
                        sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")
    if len(result) > MAX_BYTES:
        raise ValueError("batch byte capacity exceeded")
    return result


def decode(data):
    if type(data) is not bytes or len(data) > MAX_BYTES:
        raise ValueError("invalid batch bytes")
    try:
        root = json.loads(data.decode("utf-8"), object_pairs_hook=_unique)
        if type(root) is not dict or root.keys() != {"version", "events"} or type(root["version"]) is not int or root["version"] != 1:
            raise ValueError("unsupported format")
        return merge(root["events"])
    except (RecursionError, UnicodeError) as exc:
        raise ValueError("invalid encoding or nesting") from exc
