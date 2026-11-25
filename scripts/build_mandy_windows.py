"""Explicit foreground Windows preview build; no workflow, installation or upload."""
import argparse
import hashlib
import os
from pathlib import Path
import platform
import re
import shutil
import struct
import subprocess
import sys
import tempfile

SMOKES = (
    'windows/InferenceSmoke/CharacterCompanion.InferenceSmoke.csproj',
    'windows/HistorySmoke/CharacterCompanion.HistorySmoke.csproj',
    'windows/Mandy.MemorySmoke/Mandy.MemorySmoke.csproj',
    'windows/Mandy.AttachmentSmoke/Mandy.AttachmentSmoke.csproj',
)
PROJECT = 'windows/Mandy.NativePreview/Mandy.NativePreview.csproj'
REQUIRED = ('Mandy.NativePreview.exe', 'Mandy.NativePreview.dll',
            'Mandy.NativePreview.runtimeconfig.json', 'Mandy.NativePreview.deps.json',
            'coreclr.dll', 'hostfxr.dll', 'hostpolicy.dll',
            'PresentationFramework.dll', 'e_sqlite3.dll', 'System.Speech.dll')


def require(ok, message):
    if not ok:
        raise ValueError(message)


def preflight(root):
    require(os.name == 'nt' and platform.machine().lower() in ('amd64', 'x86_64'),
            'Requires a Windows x64 build machine')
    dotnet = shutil.which('dotnet')
    require(dotnet, 'Missing .NET 10 SDK')
    version = subprocess.check_output([dotnet, '--version'], cwd=root, text=True).strip()
    require(re.fullmatch(r'10\.0\.\d+', version), 'Requires selected stable .NET 10 SDK')
    for project in (*SMOKES, PROJECT):
        require((root / project).is_file(), 'Missing project: ' + project)
    return dotnet, version


def check_pe(path):
    size = path.stat().st_size
    with path.open('rb') as stream:
        header = stream.read(64)
        require(len(header) == 64 and header[:2] == b'MZ', 'Invalid Windows apphost')
        offset = struct.unpack_from('<I', header, 60)[0]
        require(64 <= offset <= min(size - 24, 1024 * 1024), 'Invalid PE offset')
        stream.seek(offset)
        pe = stream.read(24)
        require(len(pe) == 24 and pe[:4] == b'PE\x00\x00', 'Invalid PE signature')
        machine, sections, _, _, _, optional_size, flags = struct.unpack_from('<HHIIIHH', pe, 4)
        require(machine == 0x8664, 'Expected x64 PE32+ apphost')
        require(flags & 0x0002 and not flags & 0x2000, 'Expected executable, not DLL')
        table_end = offset + 24 + optional_size + 40 * sections
        require(1 <= sections <= 96 and optional_size >= 112 and table_end <= size,
                'Invalid PE optional header or section table')
        optional = stream.read(optional_size)
        require(len(optional) == optional_size and struct.unpack_from('<H', optional)[0] == 0x20b,
                'Expected x64 PE32+ apphost')
        entry = struct.unpack_from('<I', optional, 16)[0]
        image_size, headers_size = struct.unpack_from('<II', optional, 56)
        require(table_end <= headers_size <= size and headers_size <= image_size and 0 < entry < image_size,
                'Invalid PE image or entry point bounds')
        has_entry = False
        for _ in range(sections):
            section = stream.read(40)
            require(len(section) == 40, 'Truncated PE section')
            virtual_size, address, raw_size, raw_offset = struct.unpack_from('<IIII', section, 8)
            characteristics = struct.unpack_from('<I', section, 36)[0]
            require(address + max(virtual_size, raw_size) <= image_size, 'Invalid PE section image bounds')
            if raw_size:
                require(headers_size <= raw_offset and raw_offset + raw_size <= size,
                        'Invalid PE section file bounds')
                if characteristics & 0x20000000 and address <= entry < address + raw_size:
                    has_entry = True
        require(has_entry, 'PE entry point is not in a file-backed executable section')


def audit(directory):
    for name in REQUIRED:
        path = directory / name
        require(path.is_file() and not path.is_symlink() and path.stat().st_size > 0,
                'Missing or empty publish component: ' + name)
    check_pe(directory / REQUIRED[0])
    files = sorted(p for p in directory.rglob('*') if p.is_file())
    for path in files:
        require(not path.is_symlink() and '\n' not in path.name and '\r' not in path.name,
                'Unexpected linked file or unsafe filename')
        require(not path.name.lower().startswith(('cefsharp', 'libcef', 'chrome_')),
                'Legacy browser component in native preview')
    return files


def build(root, output, dotnet, version, run=subprocess.run):
    require(not output.exists(), 'Choose a new output directory; no existing output overwritten')
    require(output.parent.is_dir(), 'Output parent directory must exist')
    env = dict(os.environ, DOTNET_CLI_TELEMETRY_OPTOUT='1', DOTNET_NOLOGO='1')
    def invoke(args):
        run([dotnet, *args], cwd=root, env=env, check=True)
    for project in SMOKES:
        invoke(['run', '--project', project, '-c', 'Release', '-p:NuGetAudit=true'])
    with tempfile.TemporaryDirectory(prefix='mandy-preview-', dir=output.parent) as scratch:
        stage = Path(scratch) / 'publish'
        invoke(['publish', PROJECT, '-c', 'Release', '-r', 'win-x64', '--self-contained', 'true',
                '-p:PublishSingleFile=false', '-p:PublishTrimmed=false', '-p:PublishReadyToRun=false',
                '-p:NuGetAudit=true', '-o', str(stage)])
        files = audit(stage)
        info = stage / 'BUILD_INFO.txt'
        require(not info.exists() and not (stage / 'SHA256SUMS.txt').exists(), 'Unexpected reserved metadata file')
        info.write_text('SDK=' + version + '\nRID=win-x64\nSELF_CONTAINED=YES\n'
                        'SMOKE_PROJECTS=PASSED\nSIGNING=UNVERIFIED\nUI_DEVICE_TESTED=NO\n'
                        'REAL_MODEL_TESTED=NO\n' +
                        ''.join('SMOKE_PASSED=' + project + '\n' for project in SMOKES), encoding='utf-8')
        lines = []
        for path in sorted([*files, info]):
            with path.open('rb') as stream:
                sha = hashlib.file_digest(stream, 'sha256').hexdigest()
            lines.append(sha + '  ' + path.relative_to(stage).as_posix())
        (stage / 'SHA256SUMS.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
        require(not output.exists(), 'Output appeared during build')
        stage.rename(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--build', action='store_true', help='Run tests and publish; otherwise preflight only')
    args = parser.parse_args()
    if args.build and args.output is None:
        parser.error('--build requires an explicit new --output directory')
    root = Path(__file__).resolve().parents[1]
    try:
        dotnet, version = preflight(root)
        print('PREFLIGHT=PASSED SDK=' + version, flush=True)
        if args.build:
            build(root, args.output.resolve(), dotnet, version)
            print('NATIVE_PREVIEW_PUBLISHED=' + str(args.output.resolve()))
            print('UI_DEVICE_TESTED=NO REAL_MODEL_TESTED=NO SIGNING=UNVERIFIED')
        return 0
    except (OSError, ValueError, subprocess.CalledProcessError) as exc:
        print('NATIVE_PREVIEW_BUILD_FAILED: ' + str(exc), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
