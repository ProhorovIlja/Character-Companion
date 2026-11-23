"""Execute the CEF adapter JS envelope; Node is only a test dependency."""
from pathlib import Path
import subprocess, shutil, unittest
HERE=Path(__file__).resolve().parent
class CefScriptRuntime(unittest.TestCase):
    @unittest.skipUnless(shutil.which('node'), 'Node.js not installed')
    def test_script_envelope(self):
        p=subprocess.run(['node',str(HERE/'test_cef_script_runtime.js')],capture_output=True,text=True,timeout=15)
        self.assertEqual(p.returncode,0,p.stdout+'\n'+p.stderr)
        self.assertIn('PASS 2',p.stdout)
if __name__=='__main__': unittest.main()
