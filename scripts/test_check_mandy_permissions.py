import unittest
from check_mandy_permissions import check_permissions

VALID = "package: dev.caicompanion.app\nuses-permission: name='android.permission.RECORD_AUDIO'\nuses-permission: name='android.permission.INTERNET'\n"


class PermissionGate(unittest.TestCase):
    def test_native(self):
        check_permissions(VALID)

    def test_unrelated_permission(self):
        check_permissions(VALID + "uses-permission: name='dev.caicompanion.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'\n")

    def test_camera(self):
        with self.assertRaisesRegex(ValueError, 'Unexpected legacy permissions'):
            check_permissions(VALID + "uses-permission: name='android.permission.CAMERA'\n")

    def test_installer(self):
        with self.assertRaisesRegex(ValueError, 'Unexpected legacy permissions'):
            check_permissions(VALID + "uses-permission: name='android.permission.REQUEST_INSTALL_PACKAGES'\n")

    def test_sdk_qualified(self):
        with self.assertRaisesRegex(ValueError, 'Unexpected legacy permissions'):
            check_permissions(VALID + "uses-permission-sdk-23: name='android.permission.CAMERA'\n")

    def test_missing_audio(self):
        with self.assertRaisesRegex(ValueError, 'Missing required permissions'):
            check_permissions(VALID.replace("uses-permission: name='android.permission.RECORD_AUDIO'\n", ''))

    def test_wrong_package(self):
        with self.assertRaisesRegex(ValueError, 'package identity'):
            check_permissions(VALID.replace('dev.caicompanion.app', 'other.app'))

    def test_malformed_permission(self):
        with self.assertRaisesRegex(ValueError, 'Unrecognized permission'):
            check_permissions(VALID + 'uses-permission: broken\n')

    def test_duplicate_package(self):
        with self.assertRaisesRegex(ValueError, 'package identity'):
            check_permissions(VALID + 'package: dev.caicompanion.app\n')

    def test_empty(self):
        with self.assertRaises(ValueError):
            check_permissions('')


if __name__ == '__main__':
    unittest.main()
