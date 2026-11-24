#!/usr/bin/env python3
"""Bind a manual device signoff to the exact combined APK and source revision."""
import argparse
import hashlib
import re
import sys
from pathlib import Path

KEYS = {'DEVICE_TESTS_PASSED', 'RENDERER_FLAVOR', 'COMMIT', 'VERSION_NAME',
        'VERSION_CODE', 'APK_SHA256', 'SIGNER_SHA256'}
HEX = re.compile(r'[0-9a-f]{64}')
SIGNER = re.compile(r'^Signer #([1-9][0-9]*) certificate SHA-256 digest: ([0-9a-fA-F]{64})$')
MAX_APK = 536_870_912


def parse_signoff(raw):
    if len(raw) > 8192:
        raise ValueError('signoff too large')
    fields = {}
    for line in raw.decode('utf-8').splitlines():
        if not line:
            continue
        key, sep, value = line.partition('=')
        if not sep or key not in KEYS or key in fields or not value or value != value.strip():
            raise ValueError('invalid or duplicate signoff field')
        fields[key] = value
    if fields.keys() != KEYS:
        raise ValueError('missing signoff field')
    return fields


def signer_digest(report):
    rows = [SIGNER.fullmatch(line) for line in report.splitlines()
            if re.match(r'^Signer #\S+ certificate SHA-256 digest:', line)]
    if len(rows) != 1 or rows[0] is None or rows[0][1] != '1':
        raise ValueError('expected exactly one verified signing certificate')
    return rows[0][2].lower()


def apk_digest(path):
    digest = hashlib.sha256()
    size = 0
    with open(path, 'rb') as stream:
        while chunk := stream.read(65536):
            size += len(chunk)
            if size > MAX_APK:
                raise ValueError('APK exceeds update size limit')
            digest.update(chunk)
    if size == 0:
        raise ValueError('empty APK')
    return digest.hexdigest()


def validate(raw, apk, commit, version, code, flavor, report):
    fields = parse_signoff(raw)
    signer = signer_digest(report)
    if (not re.fullmatch(r'[0-9a-f]{40}', commit) or
            not re.fullmatch(r'(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})(-rc[1-9][0-9]{0,8})?', version) or
            not re.fullmatch(r'[1-9][0-9]{0,9}', code) or int(code) > 2100000000 or flavor != 'gecko'):
        raise ValueError('invalid release identity')
    for key in ('APK_SHA256', 'SIGNER_SHA256'):
        if not HEX.fullmatch(fields[key]):
            raise ValueError('invalid SHA-256 field')
    digest = apk_digest(apk)
    expected = dict(DEVICE_TESTS_PASSED='YES', RENDERER_FLAVOR=flavor,
                    COMMIT=commit, VERSION_NAME=version, VERSION_CODE=code,
                    APK_SHA256=digest, SIGNER_SHA256=signer)
    if fields != expected:
        raise ValueError('device signoff does not match exact release identity')
    return digest, signer


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('signoff', 'apk', 'commit', 'version', 'code', 'flavor'):
        parser.add_argument(name)
    args = parser.parse_args()
    try:
        with Path(args.signoff).open('rb') as stream:
            raw = stream.read(8193)
        report = sys.stdin.read(65537)
        if len(report) > 65536:
            raise ValueError('signature report too large')
        digest, signer = validate(raw, args.apk, args.commit, args.version,
                                  args.code, args.flavor, report)
        print(digest + ' ' + signer)
        return 0
    except (OSError, ValueError) as exc:
        print('RELEASE_SIGNOFF=FAILED: ' + str(exc), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
