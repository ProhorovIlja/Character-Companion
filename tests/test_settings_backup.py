from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class SettingsBackupTests(unittest.TestCase):
    def test_all_modes_share_backup_and_lifecycle(self):
        for path in (JAVA/'MainActivity.kt', JAVA/'BrowserSessionActivity.kt',
                     ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt'):
            src = path.read_text()
            for expected in ('SettingsBackup(this)', 'settingsBackup.receive(requestCode, resultCode, data)',
                             'settingsBackup.close()'):
                self.assertIn(expected, src)

    def test_manual_picker_and_confirmation(self):
        src = (JAVA/'SettingsBackup.kt').read_text()
        for expected in ('Intent.ACTION_CREATE_DOCUMENT', 'Intent.ACTION_OPEN_DOCUMENT',
                         'Intent.CATEGORY_OPENABLE', 'R.string.backup_restore_warning',
                         'pending != request', 'uri.scheme != "content"',
                         'busy.compareAndSet(false, true)', 'if (restored) activity.recreate()'):
            self.assertIn(expected, src)
        self.assertNotIn('takePersistableUriPermission', src)

    def test_bounds_cover_maximum_escaped_backup(self):
        src = (JAVA/'SettingsBackup.kt').read_text()
        store = (JAVA/'SettingsStore.kt').read_text()
        self.assertIn('MAX_CHARS = 6_000_000', src)
        # Worst-case JSON escaping of 100 URLs and pins, 100 titles, custom/exclude rules.
        self.assertLess(6 * (2 * 100 * 4096 + 100 * 90 + 24000) + 100000, 6000000)
        self.assertIn('contents.length > SettingsBackup.MAX_CHARS', store)
        self.assertIn('require(snapshot.length <= MAX_CHARS)', src)
        self.assertIn('BoundedText.read(it, MAX_CHARS) { closed.get() }', src)
        self.assertIn('fun exportJson(): String = synchronized(historyLock)', store)
        self.assertIn('.putBoolean("media_allowed", false)', store)

if __name__ == '__main__':
    unittest.main()
