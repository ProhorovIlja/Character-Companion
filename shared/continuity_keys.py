"""Recovery code and domain-separated keys. Never store or log a real code."""
import base64
import os
import re
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from .continuity import _uuid

PREFIX = 'MANDY1.'


def generate(account):
    _uuid(account)
    return os.urandom(32)


def encode(account, secret):
    _uuid(account)
    if type(secret) is not bytes or len(secret) != 32:
        raise ValueError('expected random 32-byte recovery secret')
    return PREFIX + account + '.' + base64.urlsafe_b64encode(secret).decode('ascii').rstrip('=')


def decode(code):
    if type(code) is not str or len(code) != 87 or not code.startswith(PREFIX) or code[43] != '.':
        raise ValueError('invalid recovery format')
    account, encoded = code[7:43], code[44:]
    _uuid(account)
    if not re.fullmatch(r'[A-Za-z0-9_-]{43}', encoded):
        raise ValueError('invalid recovery secret')
    secret = base64.urlsafe_b64decode(encoded + '=')
    if encode(account, secret) != code:
        raise ValueError('noncanonical recovery secret')
    return account, secret


def derive(account, secret):
    _uuid(account)
    if type(secret) is not bytes or len(secret) != 32:
        raise ValueError('expected random 32-byte recovery secret')
    salt = ('MandyAI continuity v1:' + account).encode('ascii')
    return tuple(HKDF(algorithm=hashes.SHA256(), length=32, salt=salt, info=label).derive(secret)
                 for label in (b'history-aes-256-gcm', b'manifest-hmac-sha256'))
