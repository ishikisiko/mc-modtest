"""Git state inspection for GenOps reports."""

from __future__ import annotations

import subprocess
from pathlib import Path
from typing import Any


def _git(root: Path, args: list[str], check: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=root,
        text=True,
        capture_output=True,
        check=check,
    )


def git_text(root: Path, args: list[str], default: str = "") -> str:
    proc = _git(root, args)
    return proc.stdout.strip() if proc.returncode == 0 else default


def inspect_repo(root: Path) -> dict[str, Any]:
    status = git_text(root, ["status", "--short"])
    return {
        "branch": git_text(root, ["branch", "--show-current"], "unknown"),
        "head": git_text(root, ["rev-parse", "--short", "HEAD"], "unknown"),
        "remote_head": git_text(root, ["rev-parse", "--short", "@{u}"], ""),
        "status_short": status.splitlines() if status else [],
        "dirty": bool(status),
    }


def diff_name_only(root: Path) -> list[str]:
    names: set[str] = set()
    for args in (["diff", "--name-only"], ["diff", "--cached", "--name-only"]):
        output = git_text(root, args)
        names.update(line.strip() for line in output.splitlines() if line.strip())
    return sorted(names)

