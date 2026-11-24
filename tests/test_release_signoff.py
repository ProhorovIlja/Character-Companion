import hashlib
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import check_release_signoff as gate


class ReleaseSignoffTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.apk = Path(directory.name) / 'fixture.apk'
        self.apk.write_bytes(b'not a real APK; digest fixture only')
        self.commit = 'a' * 40
        self.signer = 'b' * 64
        self.digest = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        self.report = 'Signer #1 certificate SHA-256 digest: ' + self.signer + '\n'
        self.fields = dict(DEVICE_TESTS_PASSED='YES', RENDERER_FLAVOR='gecko',
                           COMMIT=self.commit, VERSION_NAME='0.38.0-rc1', VERSION_CODE='38',
                           APK_SHA256=self.digest, SIGNER_SHA256=self.signer)

    def raw(self, **changes):
        return ('\n'.join(k + '=' + v for k, v in (self.fields | changes).items()) + '\n').encode()

    def verify(self, raw=None, report=None, **changes):
        args = dict(raw=self.raw() if raw is None else raw, apk=self.apk, commit=self.commit,
                    version='0.38.0-rc1', code='38', flavor='gecko',
                    report=self.report if report is None else report)
        return gate.validate(**(args | changes))

    def test_valid_exact_identity(self):
        self.assertEqual(self.verify(), (self.digest, self.signer))
        self.assertEqual(self.verify(report=self.report.upper().replace('SIGNER', 'Signer').replace('CERTIFICATE', 'certificate').replace('DIGEST', 'digest')), (self.digest, self.signer))

    def test_every_field_is_bound(self):
        for key, value in dict(DEVICE_TESTS_PASSED='NO', RENDERER_FLAVOR='legacy',
                               COMMIT='c'*40, VERSION_NAME='0.39.0', VERSION_CODE='39',
                               APK_SHA256='c'*64, SIGNER_SHA256='d'*64).items():
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.verify(self.raw(**{key: value}))

    def test_reject_duplicate_extra_and_missing(self):
        for raw in (self.raw()+b'COMMIT=x\n', self.raw()+b'EXTRA=1\n',
                    b'DEVICE_TESTS_PASSED=YES\n', self.raw()+b' '*8192, b'\xff'):
            with self.subTest(raw=raw[:60]), self.assertRaises(ValueError):
                self.verify(raw)

    def test_changed_apk_rejected(self):
        self.apk.write_bytes(b'changed')
        with self.assertRaises(ValueError):
            self.verify()

    def test_signer_report_must_be_unambiguous(self):
        for report in ('', self.report+self.report,
                       self.report+'Signer #2 certificate SHA-256 digest: '+'c'*64,
                       'Signer #1 certificate SHA-256 digest: invalid'):
            with self.subTest(report=report), self.assertRaises(ValueError):
                self.verify(report=report)

    def test_invalid_build_identity(self):
        for kwargs in (dict(flavor='legacy'), dict(version='00.38.0'),
                       dict(code='0'), dict(code='2100000001'), dict(commit='HEAD')):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                self.verify(**kwargs)

    def test_apk_size_bound(self):
        self.apk.write_bytes(b'')
        with self.assertRaises(ValueError):
            self.verify()
        with self.apk.open('wb') as stream:
            stream.truncate(gate.MAX_APK+1)
        with self.assertRaises(ValueError):
            self.verify()


if __name__ == '__main__':
    unittest.main()
