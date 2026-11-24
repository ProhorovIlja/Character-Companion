"""Authenticated ordered history snapshot descriptor; no transport or key storage."""
import hashlib
import hmac
import struct

from . import continuity as wire
from . import encrypted_history as crypto

MAGIC = b'MANDYM01'
MAX_CHUNKS = 4096
MAX_BYTES = 80 + 36 * MAX_CHUNKS


def _binding(key, account):
    if type(key) is not bytes or len(key) != 32:
        raise ValueError('expected separate random manifest key')
    wire._uuid(account)
    return account.encode('ascii')


def _descriptor(chunk):
    if type(chunk) is not bytes or not 37 <= len(chunk) <= crypto.MAX_ENVELOPE or not chunk.startswith(crypto.MAGIC):
        raise ValueError('invalid encrypted chunk')
    return struct.pack('>I', len(chunk)) + hashlib.sha256(chunk).digest()


def create(key, account, snapshot, chunks):
    binding = _binding(key, account)
    wire._uuid(snapshot)
    rows = []
    for chunk in chunks:
        if len(rows) >= MAX_CHUNKS:
            raise ValueError('snapshot capacity exceeded')
        rows.append(_descriptor(chunk))
    body = MAGIC + snapshot.encode('ascii') + struct.pack('>I', len(rows)) + b''.join(rows)
    return body + hmac.digest(key, body + binding, 'sha256')


def parse(key, account, manifest):
    binding = _binding(key, account)
    if type(manifest) is not bytes or not 80 <= len(manifest) <= MAX_BYTES or not manifest.startswith(MAGIC):
        raise ValueError('invalid manifest')
    body, tag = manifest[:-32], manifest[-32:]
    if not hmac.compare_digest(tag, hmac.digest(key, body + binding, 'sha256')):
        raise ValueError('manifest authentication failed')
    snapshot = body[8:44].decode('ascii')
    wire._uuid(snapshot)
    count, = struct.unpack('>I', body[44:48])
    if count > MAX_CHUNKS or len(body) != 48 + 36 * count:
        raise ValueError('invalid manifest count')
    rows = [body[48 + 36 * n:84 + 36 * n] for n in range(count)]
    if any(not 37 <= struct.unpack('>I', row[:4])[0] <= crypto.MAX_ENVELOPE for row in rows):
        raise ValueError('invalid chunk length')
    return snapshot, rows


def checked_chunks(rows, chunks):
    """Exhaust iterator to detect extra/missing pages. Do not commit before exhaustion."""
    source = iter(chunks)
    for expected in rows:
        chunk = next(source, None)
        if not hmac.compare_digest(expected, _descriptor(chunk)):
            raise ValueError('chunk does not match manifest')
        yield chunk
    sentinel = object()
    if next(source, sentinel) is not sentinel:
        raise ValueError('extra chunk')


def import_snapshot(store, history_key, manifest_key, manifest, chunks):
    """Local reference only: atomic merge of all manifest-listed history chunks."""
    account = crypto._account(store)
    crypto._check(history_key, account)
    if history_key == manifest_key:
        raise ValueError('use separate keys')
    snapshot, rows = parse(manifest_key, account, manifest)
    with store._write():
        count = 0
        for chunk in checked_chunks(rows, chunks):
            data = crypto.open_batch(history_key, account, chunk)
            for event in wire.decode(data):
                count += store._insert(event)
    return snapshot, count
