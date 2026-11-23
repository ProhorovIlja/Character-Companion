from pathlib import Path
import hashlib
import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('splitter',ROOT/'tools/split_xapk.py')
splitter=importlib.util.module_from_spec(spec);spec.loader.exec_module(splitter)

class SplitTest(unittest.TestCase):
    def test_exact_reassembly_and_manifest(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);file=root/'sample.xapk';file.write_bytes(os.urandom(2049))
            report=splitter.split_file(file,root/'parts',1024)
            self.assertEqual(len(report['parts']),3)
            self.assertEqual(b''.join((root/'parts'/p['name']).read_bytes() for p in report['parts']),file.read_bytes())
            self.assertEqual(report['source_sha256'],hashlib.sha256(file.read_bytes()).hexdigest())
            self.assertEqual(json.loads((root/'parts'/'sample.xapk.parts-manifest.json').read_text())['source_bytes'],2049)
            with self.assertRaises(FileExistsError):splitter.split_file(file,root/'parts',1024)

    def test_invalid_chunk_sizes(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/'a.xapk';p.write_bytes(b'hello')
            with self.assertRaises(ValueError):splitter.split_file(p,Path(t)/'out',0)
            with self.assertRaises(ValueError):splitter.split_file(p,Path(t)/'out',221*1024**2)

    def test_sha_precondition_on_reassembled_zip(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);file=root/'sample.xapk'
            with zipfile.ZipFile(file,'w') as f: f.writestr('manifest.json','{"name":"test"}')
            report=splitter.split_file(file,root/'parts',1024)
            names=[str(root/'parts'/p['name']) for p in report['parts']]
            base=[sys.executable,str(ROOT/'tools/inspect_xapk.py'),'--parts',*names,'--expect-sha256']
            ok=subprocess.run(base+[report['source_sha256']],capture_output=True,text=True)
            self.assertEqual(ok.returncode,0,ok.stderr)
            bad=subprocess.run(base+['0'*64],capture_output=True,text=True)
            self.assertEqual(bad.returncode,2)
            self.assertIn('source_sha256_mismatch',bad.stderr)

if __name__=='__main__':unittest.main()
