"""Run real extracted bridge JS in a sandboxed Node VM, if Node is present."""
from pathlib import Path
import shutil
import subprocess
import unittest

HERE=Path(__file__).resolve().parent

class BrowserBridgeRuntimeSmoke(unittest.TestCase):
    @unittest.skipUnless(shutil.which('node'), 'Node.js not installed')
    def test_dom_scenarios(self):
        result=subprocess.run(['node', str(HERE/'bridge_runtime.js')], cwd=HERE,
                              capture_output=True, text=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stdout+'\n'+result.stderr)
        self.assertIn('PASS 11',result.stdout)

if __name__=='__main__': unittest.main()
