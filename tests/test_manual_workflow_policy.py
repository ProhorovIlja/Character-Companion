"""Regression tests for the exact one-time user approval of a manual build."""

from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from manual_workflow_policy import MANUAL_FILE, manual_workflow_issues


class ManualWorkflowPolicyTests(unittest.TestCase):
    def stage(self, root: Path, source: str, extra: bool = False) -> None:
        folder = root / ".github" / "workflows"
        folder.mkdir(parents=True, exist_ok=True)
        (folder / MANUAL_FILE).write_text(source, encoding="utf-8")
        if extra:
            (folder / "unexpected.yml").write_text("on: push\n", encoding="utf-8")

    def test_repository_has_only_explicitly_approved_manual_build(self):
        self.assertEqual(manual_workflow_issues(Path(__file__).resolve().parents[1]), [])

    def test_accept_exact_manual_dispatch(self):
        with TemporaryDirectory() as temp:
            root = Path(temp)
            self.stage(root, "name: build\non:\n  workflow_dispatch:\npermissions:\n  contents: read\n")
            self.assertEqual(manual_workflow_issues(root), [])

    def test_reject_automatic_push_or_pull_request_trigger(self):
        for trigger in ("push", "pull_request", "schedule", "workflow_run", "repository_dispatch"):
            with self.subTest(trigger=trigger), TemporaryDirectory() as temp:
                root = Path(temp)
                self.stage(root, f"on:\n  workflow_dispatch:\n  {trigger}:\njobs:\n")
                self.assertNotEqual(manual_workflow_issues(root), [])

    def test_reject_unauthorized_extra_workflow(self):
        with TemporaryDirectory() as temp:
            root = Path(temp)
            self.stage(root, "on:\n  workflow_dispatch:\njobs:\n", extra=True)
            self.assertNotEqual(manual_workflow_issues(root), [])

    def test_reject_absent_or_duplicate_dispatch_event(self):
        with TemporaryDirectory() as temp:
            root = Path(temp)
            self.assertNotEqual(manual_workflow_issues(root), [])
            self.stage(root, "on:\n  workflow_dispatch:\n  workflow_dispatch:\njobs:\n")
            self.assertNotEqual(manual_workflow_issues(root), [])


if __name__ == "__main__":
    unittest.main()
