"""Foreground-only tests of the 37.0 SDK installer, using a fake sdkmanager."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/install-android37-sdk.sh"

class Android37SdkInstallerTest(unittest.TestCase):
    def run_sdk(self, *, valid_sdk=True, valid_tools=True, sdk_set=True):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            sdk = root / "sdk"
            bin_dir = root / "bin"
            bin_dir.mkdir()
            fake = bin_dir / "sdkmanager"
            fake.write_text("""#!/usr/bin/env bash
printf '%s\n' "$@" >> "$SDK_ARGS_FILE"
""")
            fake.chmod(0o755)
            if valid_sdk:
                jar = sdk/'platforms/android-37.0/android.jar'
                jar.parent.mkdir(parents=True)
                jar.write_bytes(b'test-not-a-real-SDK')
            if valid_tools:
                for exe in ('apksigner','aapt'):
                    p = sdk/'build-tools/36.0.0'/exe
                    p.parent.mkdir(parents=True, exist_ok=True)
                    p.write_text('#!/bin/sh\nexit 0\n')
                    p.chmod(0o755)
            env = os.environ.copy()
            env['PATH'] = str(bin_dir) + os.pathsep + env.get('PATH','')
            env['SDK_ARGS_FILE'] = str(root/'args.txt')
            if sdk_set:
                env['ANDROID_HOME'] = str(sdk)
                env.pop('ANDROID_SDK_ROOT',None)
            else:
                env.pop('ANDROID_HOME',None)
                env.pop('ANDROID_SDK_ROOT',None)
            r = subprocess.run(['bash',str(SCRIPT)],env=env,text=True,capture_output=True)
            args = (root/'args.txt').read_text().splitlines() if (root/'args.txt').exists() else []
            return r, args

    def test_exact_minor_package_and_success(self):
        r,args = self.run_sdk()
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertEqual(args,['--install','platforms;android-37.0','build-tools;36.0.0'])
        self.assertIn('ANDROID_SDK_PLATFORM=37.0_INSTALLED',r.stdout)

    def test_missing_api_37_0_is_refused(self):
        r,args = self.run_sdk(valid_sdk=False)
        self.assertNotEqual(r.returncode,0)
        self.assertIn('Android SDK platform 37.0 missing',r.stderr)
        self.assertIn('platforms;android-37.0',args)

    def test_missing_build_tools_is_refused(self):
        r,_ = self.run_sdk(valid_tools=False)
        self.assertNotEqual(r.returncode,0)
        self.assertIn('build-tools/36.0.0/apksigner',r.stderr)

    def test_missing_sdk_root_is_refused(self):
        r,args = self.run_sdk(sdk_set=False)
        self.assertNotEqual(r.returncode,0)
        self.assertIn('ANDROID_HOME / ANDROID_SDK_ROOT',r.stderr)
        self.assertEqual(args,[])

if __name__ == '__main__': unittest.main()
