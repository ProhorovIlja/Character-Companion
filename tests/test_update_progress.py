from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class UpdateProgressTests(unittest.TestCase):
    def test_worker_progress(self):
        download = (JAVA / 'UpdateDownload.kt').read_text()
        for token in ('UpdateProgress(asset.size, startedAt)', 'gate.next(bytes,',
                      'bar.progress = percent', 'progress.isShowing',
                      'R.string.release_verifying_package', 'verifyPackage(activity, target, tag)'):
            self.assertIn(token, download)
        self.assertNotIn('ProgressDialog', download)

    def test_rate_limit_and_localization(self):
        center = (JAVA / 'ReleaseCenter.kt').read_text()
        for token in ('UpdateProgress.rateLimited', 'X-RateLimit-Remaining', 'Retry-After', 'release_rate_limited'):
            self.assertIn(token, center)
        for locale in ('values', 'values-ru'):
            names = {x.get('name') for x in ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()}
            for name in ('release_download_progress', 'release_verifying_package', 'release_rate_limited'):
                self.assertIn(name, names)
