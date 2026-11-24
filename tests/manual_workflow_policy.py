"""Enforce the user's one explicitly authorized, strictly manual Android build."""

from pathlib import Path
import re


MANUAL_FILE = "build-android-apk-manual.yml"


def manual_workflow_issues(root: Path) -> list[str]:
    """Reject absent, unexpected, or automatically triggered GitHub workflows.

    This function validates the top-level YAML event mapping without needing
    PyYAML on a stock GitHub Actions runner. Its allowlist intentionally does
    not authorize future CI events, scheduled jobs, or extra workflow files.
    """
    directory = root / ".github" / "workflows"
    if not directory.is_dir():
        return ["The explicitly approved manual workflow is missing"]
    names = sorted(path.name for path in directory.iterdir())
    if names != [MANUAL_FILE]:
        return [f"Unexpected workflow files: {names!r}"]
    text = (directory / MANUAL_FILE).read_text(encoding="utf-8")
    on_lines = list(re.finditer(r"(?m)^on:\s*$", text))
    if len(on_lines) != 1:
        return ["Exactly one top-level 'on:' declaration is required"]
    after_on = text[on_lines[0].end():]
    # The event mapping ends at the first next top-level YAML key.
    next_key = re.search(r"(?m)^[A-Za-z_][A-Za-z_0-9-]*:\s*", after_on)
    events_section = after_on[:next_key.start()] if next_key else after_on
    triggers = re.findall(r"(?m)^  ([A-Za-z_][A-Za-z_0-9-]*):\s*$", events_section)
    if triggers != ["workflow_dispatch"]:
        return [f"Only workflow_dispatch is authorized, found {triggers!r}"]
    return []
