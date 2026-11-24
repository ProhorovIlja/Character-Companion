"""Authenticated history chunks; no key persistence, networking or automatic sync."""
import os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from . import continuity as wire

MAGIC = b'MANDYH01'
NONCE_BYTES = 12
TAG_BYTES = 16
MAX_ENVELOPE = len(MAGIC) + NONCE_BYTES + wire.MAX_BYTES + TAG_BYTES


def _check(key, account):
    if type(key) is not bytes or len(key) != 32:
        raise ValueError('expected a dedicated random 256-bit account key')
    wire._uuid(account)
    return account.encode('ascii')


def seal(key, account, data):
    """Validate an existing v1 history batch before encrypting it."""
    binding = _check(key, account)
    wire.decode(data)
    nonce = os.urandom(NONCE_BYTES)
    header = MAGIC + nonce
    return header + AESGCM(key).encrypt(nonce, data, header + binding)


def open_batch(key, account, envelope):
    """Authenticate all bytes before parsing; return validated original batch."""
    binding = _check(key, account)
    if (type(envelope) is not bytes or not 37 <= len(envelope) <= MAX_ENVELOPE
            or not envelope.startswith(MAGIC)):
        raise ValueError('invalid encrypted history envelope')
    header = envelope[:20]
    data = AESGCM(key).decrypt(header[8:], envelope[20:], header + binding)
    wire.decode(data)
    return data


def _account(store):
    return store.db.execute('SELECT account FROM vault WHERE singleton=1').fetchone()[0]


def export_encrypted(store, key):
    """Encrypt each bounded history batch; caller must exhaust or close iterator."""
    account = _account(store)
    _check(key, account)
    batches = store.export_batches()
    try:
        for batch in batches:
            yield seal(key, account, batch)
    finally:
        batches.close()


def import_encrypted(store, key, envelope):
    """Authenticate, validate, then import one batch atomically via HistoryStore."""
    return store.import_batch(open_batch(key, _account(store), envelope))
