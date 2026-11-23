"""Offline test suite. Synthetic APKs only, no redistribution of proprietary assets."""
from pathlib import Path
import importlib.util
import io
import json
import os
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('xapk_inspector', ROOT/'tools/inspect_xapk.py')
xapk = importlib.util.module_from_spec(spec)
spec.loader.exec_module(xapk)


def binary_manifest():
    strings = ['manifest', 'package', 'dev.caicompanion.test', 'versionName', '1.2.3',
               'versionCode', 'uses-sdk', 'minSdkVersion', 'targetSdkVersion',
               'uses-permission', 'name', 'android.permission.INTERNET']
    raw = bytearray()
    offsets = []
    for text in strings:
        payload = text.encode('utf8')
        assert len(payload) < 128
        offsets.append(len(raw))
        raw.extend(bytes([len(text), len(payload)]))
        raw.extend(payload)
        raw.append(0)
    string_start = 28 + 4 * len(strings)
    pool_size = string_start + len(raw)
    pool_size += (-pool_size) % 4
    pool = (struct.pack('<HHIIIIII',1,28,pool_size,len(strings),0,0x100,string_start,0)
            + b''.join(struct.pack('<I',k) for k in offsets) + bytes(raw))
    pool += bytes(pool_size - len(pool))

    def elem(tag, attrs):
        ext = struct.pack('<IIHHHHHH',0xffffffff,strings.index(tag),20,20,len(attrs),0,0,0)
        attbytes = b''
        for key, value in attrs:
            if isinstance(value,int):
                type_, encoded, rawid = 0x10,value,0xffffffff
            else:
                type_, encoded, rawid = 0x03,strings.index(value),strings.index(value)
            attbytes += struct.pack('<IIIHBBI',0xffffffff,strings.index(key),rawid,8,0,type_,encoded)
        chunk_size = 16 + len(ext) + len(attbytes)
        return struct.pack('<HHIII',0x0102,16,chunk_size,1,0xffffffff)+ext+attbytes

    elements = (elem('manifest',[('package','dev.caicompanion.test'),('versionName','1.2.3'),('versionCode',123)])
                + elem('uses-sdk',[('minSdkVersion',29),('targetSdkVersion',37)])
                + elem('uses-permission',[('name','android.permission.INTERNET')]))
    return struct.pack('<HHI',3,8,8+len(pool)+len(elements))+pool+elements


def make_xapk(root: Path):
    buffer=io.BytesIO()
    with zipfile.ZipFile(buffer,'w',compression=zipfile.ZIP_DEFLATED) as apk:
        apk.writestr('AndroidManifest.xml', binary_manifest())
        apk.writestr('classes.dex',b'dex\n035\x00fake')
        apk.writestr('lib/arm64-v8a/libsample.so',b'fake elf content')
        apk.writestr('assets/texture.png',b'fake png')
    archive = root/'sample.xapk'
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_STORED) as outer:
        outer.writestr('manifest.json',json.dumps({'name':'Unit Sample','package_name':'dev.caicompanion.test',
           'version_name':'1.2.3','secret_field':'this should not be copied'}))
        outer.writestr('base.apk',buffer.getvalue())
        outer.writestr('split_config.arm64_v8a.apk',buffer.getvalue())
        outer.writestr('Android/obb/sample/main.obb',b'obbdemo')
        outer.writestr('../unsafe-path',b'never extract')
    return archive


class XapkInspectorTest(unittest.TestCase):
    def test_binary_manifest(self):
        meta=xapk.parse_axml_manifest(binary_manifest())
        self.assertEqual(meta['package_name'],'dev.caicompanion.test')
        self.assertEqual(meta['min_sdk'],'29')
        self.assertEqual(meta['target_sdk'],'37')
        self.assertEqual(meta['permissions'],['android.permission.INTERNET'])

    def test_manifest_corruption_rejected(self):
        with self.assertRaises(ValueError):
            xapk.parse_axml_manifest(b'\x00'*32)
        with self.assertRaises(ValueError):
            xapk.parse_axml_manifest(binary_manifest()[:30])

    def test_apk_inventory_and_archive_guard(self):
        with tempfile.TemporaryDirectory() as td:
            file=make_xapk(Path(td))
            result=xapk.inspect_xapk(file)
            self.assertEqual(result['manifest']['version_name'],'1.2.3')
            self.assertNotIn('secret_field',result['manifest'])
            self.assertEqual(len(result['apks']),2)
            self.assertEqual(result['apks'][0]['native_abis'],['arm64-v8a'])
            self.assertEqual(result['apks'][0]['dex_count'],1)
            self.assertEqual(result['apks'][0]['android_manifest']['target_sdk'],'37')
            self.assertEqual(result['zip_summary']['unsafe_entry_count'],1)
            self.assertEqual(result['other_payload'][0]['bytes'],7)

    def test_zip_symlink_and_paths(self):
        self.assertEqual(xapk.validate_info(zipfile.ZipInfo('../bad.apk')),'unsafe_path_or_symlink')
        self.assertEqual(xapk.validate_info(zipfile.ZipInfo('/abs.apk')),'unsafe_path_or_symlink')
        i=zipfile.ZipInfo('link.apk');i.create_system=3;i.external_attr=(0o120777 << 16)
        self.assertEqual(xapk.validate_info(i),'unsafe_path_or_symlink')

    def test_bad_zip_rejected_and_no_directory_traversal(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/'bad.xapk';p.write_text('not a ZIP')
            with self.assertRaises(zipfile.BadZipFile):
                xapk.inspect_xapk(p)

    def test_parts_and_json_output(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);original=make_xapk(root);data=original.read_bytes()
            one=root/'part-a';two=root/'part-b'
            one.write_bytes(data[:45]);two.write_bytes(data[45:])
            out=root/'report.json'
            result=subprocess.run([sys.executable,str(ROOT/'tools/inspect_xapk.py'),
                '--parts',str(one),str(two),'--json',str(out)],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            report=json.loads(out.read_text())
            self.assertEqual(report['sha256'],xapk.sha256_file(original))
            self.assertEqual(report['part_count'],2)
            self.assertFalse((root/'reassembled_xapk').exists())

    def test_no_input_exits_cleanly(self):
        p=subprocess.run([sys.executable,str(ROOT/'tools/inspect_xapk.py')],capture_output=True)
        self.assertNotEqual(p.returncode,0)


if __name__=='__main__':unittest.main()
