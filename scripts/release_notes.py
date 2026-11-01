#!/usr/bin/env python3
"""Extract finalized release notes from the project changelog."""

import re
import sys
from pathlib import Path


def main() -> None:
    if len(sys.argv) != 2 or not sys.argv[1].startswith("v"):
        raise SystemExit("Expected a version tag, for example v0.3.0-alpha")
    version = sys.argv[1][1:]
    changelog = (Path(__file__).resolve().parent.parent / "CHANGELOG.md").read_text(encoding="utf-8")
    heading = rf"^## \[{re.escape(version)}\](?:\s+-\s+\d{{4}}-\d{{2}}-\d{{2}})?\s*$"
    match = re.search(heading, changelog, flags=re.MULTILINE)
    if match is None:
        raise SystemExit(f"No changelog section found for {version}")
    remainder = changelog[match.end():]
    next_heading = re.search(r"^## ", remainder, flags=re.MULTILINE)
    notes = remainder[:next_heading.start()] if next_heading else remainder
    notes = notes.strip()
    if not notes or not re.search(r"^\s*[-*] \S", notes, flags=re.MULTILINE):
        raise SystemExit(f"Changelog section for {version} has no release notes")
    print(notes)


if __name__ == "__main__":
    main()
