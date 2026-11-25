"""Validate the final native Mandy APK permission dump, not just source XML."""
import re

APP_ID = 'dev.caicompanion.app'
REQUIRED = {'android.permission.RECORD_AUDIO', 'android.permission.INTERNET'}
FORBIDDEN = {'android.permission.CAMERA', 'android.permission.REQUEST_INSTALL_PACKAGES'}


def check_permissions(report: str) -> None:
    packages = []
    permissions = set()
    for line in report.splitlines():
        line = line.strip()
        if line.startswith('package:'):
            packages.append(line.partition(':')[2].strip())
        elif line.startswith('uses-permission'):
            match = re.fullmatch(r"uses-permission(?:-sdk-[0-9]+)?:\s*name='([^'\s]+)'(?:\s+.*)?", line)
            if not match:
                raise ValueError('Unrecognized permission dump entry')
            permissions.add(match.group(1))
    if packages != [APP_ID]:
        raise ValueError('Unexpected permission dump package identity')
    missing = REQUIRED - permissions
    forbidden = FORBIDDEN & permissions
    if missing:
        raise ValueError('Missing required permissions: ' + ', '.join(sorted(missing)))
    if forbidden:
        raise ValueError('Unexpected legacy permissions: ' + ', '.join(sorted(forbidden)))
