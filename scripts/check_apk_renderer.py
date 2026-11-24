#!/usr/bin/env python3
"""Validate the application-level renderer marker from aapt dump xmltree output."""
import argparse
import re
import sys

KEY = 'dev.caicompanion.app.RENDERER'
ELEMENT = re.compile(r'^( +)E: ([\w.-]+)(?:\s|$)')
ATTRIBUTE = re.compile(r'^( +)A: android:(name|value)(?:\(0x[0-9a-fA-F]+\))?=(.*)$')
STRING = re.compile(r'^"([^"\r\n]*)"(?: \(Raw: "([^"\r\n]*)"\))?$')
MAX_TEXT = 1_048_576


def renderer(tree):
    """Fail closed on missing, duplicate, nested or nonliteral renderer metadata."""
    if len(tree) > MAX_TEXT:
        return None
    stack = []
    markers = []
    application_count = 0
    current = None

    def flush():
        if current is not None and KEY in current.get('name', []):
            names = current.get('name', [])
            values = current.get('value', [])
            markers.append(values[0] if names == [KEY] and len(values) == 1 else None)

    for line in tree.splitlines():
        element = ELEMENT.match(line)
        if element:
            flush()
            indent, name = len(element[1]), element[2]
            while stack and stack[-1][0] >= indent:
                stack.pop()
            parent = tuple(node for _, node in stack)
            if name == 'application' and parent == ('manifest',):
                application_count += 1
            current = {} if name == 'meta-data' and parent == ('manifest', 'application') else None
            stack.append((indent, name))
            continue
        attr = ATTRIBUTE.match(line)
        if current is not None and attr and len(attr[1]) > stack[-1][0]:
            value = STRING.fullmatch(attr[3])
            parsed = value[1] if value and value[2] in (None, value[1]) else None
            current.setdefault(attr[2], []).append(parsed)
    flush()
    return markers[0] if application_count == 1 and len(markers) == 1 and markers[0] in ('gecko', 'legacy') else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('flavor', choices=('gecko', 'legacy'))
    args = parser.parse_args()
    actual = renderer(sys.stdin.read(MAX_TEXT + 1))
    if actual != args.flavor:
        print('APK_RENDERER=UNVERIFIED_OR_MISMATCH', file=sys.stderr)
        return 1
    print('APK_RENDERER=' + actual + '_VERIFIED')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
