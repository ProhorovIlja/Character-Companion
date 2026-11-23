from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
SCRIPT=ROOT/'scripts/device-android17-preflight.sh'


class Android17PreflightTest(unittest.TestCase):
    def run_fake(self, api='37', lineage='22.2', installed=True):
        with tempfile.TemporaryDirectory() as tmp:
            fake=Path(tmp)/'adb'
            fake.write_text('''#!/usr/bin/env bash
case "$*" in
  *ro.build.version.sdk*) printf '%s\\n' "$MOCK_API";;
  *ro.lineage.version*) printf '%s\\n' "$MOCK_LINEAGE";;
  *"pm path dev.caicompanion.app"*) if [[ "$MOCK_INSTALLED" == 1 ]]; then echo 'package:/fake/path.apk'; fi;;
  *) exit 3;;
esac
''')
            fake.chmod(0o755)
            env=os.environ.copy();env.update({'PATH':tmp+os.pathsep+env['PATH'],
                'MOCK_API':api,'MOCK_LINEAGE':lineage,'MOCK_INSTALLED':'1' if installed else '0'})
            env.pop('ADB_SERIAL',None)
            return subprocess.run(['bash',str(SCRIPT)],env=env,capture_output=True,text=True)

    def test_android17_detected_no_false_validation(self):
        p=self.run_fake()
        self.assertEqual(p.returncode,0,p.stderr)
        self.assertIn('ANDROID_17_RUNTIME=DETECTED',p.stdout)
        self.assertIn('LINEAGE_OS_PRESENT=YES',p.stdout)
        self.assertIn('COMPANION_INSTALLED=YES',p.stdout)
        self.assertIn('LOGIN_CHAT_VOICE_PERFORMANCE=NOT_TESTED',p.stdout)
        self.assertNotIn('22.2',p.stdout) # don't leak ROM version in diagnostic output

    def test_android16_is_not_android17(self):
        p=self.run_fake('36','',False)
        self.assertEqual(p.returncode,0,p.stderr)
        self.assertIn('ANDROID_17_RUNTIME=NO',p.stdout)
        self.assertIn('COMPANION_INSTALLED=NO',p.stdout)

    def test_malformed_api_rejected(self):
        p=self.run_fake('not-a-number')
        self.assertEqual(p.returncode,2)
        self.assertIn('ANDROID_API=UNREADABLE',p.stderr)

if __name__=='__main__':unittest.main()
