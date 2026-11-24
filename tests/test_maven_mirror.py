"""The exact manual-run #4 Maven Central HTTP 429 build failure regression.

Tests inspect *both* Gradle repository scopes, so plugin classpath dependencies
cannot silently resolve via Maven Central before the Google-hosted mirror.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SETTINGS = ROOT / "settings.gradle.kts"
MIRROR = 'maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")'


class MavenMirrorTests(unittest.TestCase):
    @staticmethod
    def repo_bodies(source: str) -> list[str]:
        # Blocks are flat at repository declaration level; braces in nested DSL
        # would require an actual Kotlin parser, which is not necessary here.
        return re.findall(r"(?ms)repositories\s*\{(.*?)^    \}", source)

    def test_plugin_and_dependency_repositories_use_google_mirror_before_central(self):
        source = SETTINGS.read_text(encoding="utf-8")
        blocks = self.repo_bodies(source)
        self.assertEqual(len(blocks), 2, "Need mirror in plugin AND app dependency resolution")
        for body in blocks:
            self.assertEqual(body.count(MIRROR), 1)
            self.assertEqual(body.count("mavenCentral()"), 1)
            self.assertLess(body.index("google()"), body.index(MIRROR))
            self.assertLess(body.index(MIRROR), body.index("mavenCentral()"))
            self.assertNotIn("http://", body)

    def test_android_and_mozilla_repositories_preserved(self):
        source = SETTINGS.read_text(encoding="utf-8")
        self.assertIn("RepositoriesMode.FAIL_ON_PROJECT_REPOS", source)
        self.assertIn('maven(url = "https://maven.mozilla.org/maven2/")', source)
        self.assertIn("gradlePluginPortal()", source)
        self.assertIn('include(":app")', source)

    def test_manual_workflow_only_remains_user_initiated(self):
        source = (ROOT / ".github/workflows/build-android-apk-manual.yml").read_text(encoding="utf-8")
        self.assertIn("on:\n  workflow_dispatch:\n", source)
        self.assertFalse(re.search(r"(?m)^  (?:push|pull_request|schedule|workflow_run|repository_dispatch):", source))


if __name__ == "__main__":
    unittest.main()
