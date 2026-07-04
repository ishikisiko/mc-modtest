"""Patch scope checks for GenOps task outputs."""

from __future__ import annotations

import fnmatch
import re
from pathlib import PurePosixPath
from typing import Any

from tools.genops.models import TaskSpec


GENERATED_DENY_PATTERNS = [
    "src/main/resources/data/myvillage/structure/*.nbt",
    "reports/*.json",
    "reports/*.md",
    "out/preview/*",
    "build/libs/*",
]

VERSION_PROTECTED_PATTERNS = [
    "gradle.properties",
    "src/main/resources/META-INF/neoforge.mods.toml",
    "CHANGELOG.md",
]


def matches(path: str, pattern: str) -> bool:
    normalized = path.replace("\\", "/")
    pat = pattern.replace("\\", "/")
    return fnmatch.fnmatch(normalized, pat) or PurePosixPath(normalized).match(pat)


def any_match(path: str, patterns: list[str]) -> bool:
    return any(matches(path, pattern) for pattern in patterns)


def parse_patch_files(patch_text: str) -> list[str]:
    files: set[str] = set()
    for line in patch_text.splitlines():
        diff_match = re.match(r"^diff --git a/(.*?) b/(.*?)$", line)
        if diff_match:
            files.add(diff_match.group(2))
            continue
        file_match = re.match(r"^(?:---|\+\+\+) (?:a|b)/(.*?)$", line)
        if file_match:
            files.add(file_match.group(1))
    return sorted(path for path in files if path != "/dev/null")


def check_files(task: TaskSpec, changed_files: list[str]) -> dict[str, Any]:
    violations: list[dict[str, str]] = []
    for path in changed_files:
        if task.allowed_files and not any_match(path, task.allowed_files):
            violations.append({"file": path, "reason": "outside_allowed_files"})
        if any_match(path, task.forbidden_files):
            violations.append({"file": path, "reason": "forbidden_file"})
        if not task.generated_outputs_allowed and any_match(path, GENERATED_DENY_PATTERNS):
            violations.append({"file": path, "reason": "generated_output_not_allowed"})
        if task.agent != "release-steward" and any_match(path, VERSION_PROTECTED_PATTERNS):
            violations.append({"file": path, "reason": "version_file_requires_release_steward"})

    return {
        "status": "pass" if not violations else "fail",
        "changed_files": changed_files,
        "violations": violations,
    }


def check_patch_text(task: TaskSpec, patch_text: str) -> dict[str, Any]:
    changed_files = parse_patch_files(patch_text)
    if not changed_files:
        return {"status": "pass", "changed_files": [], "violations": []}
    return check_files(task, changed_files)

