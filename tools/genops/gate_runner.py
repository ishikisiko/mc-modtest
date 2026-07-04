"""Run pipeline gates and persist stdout/stderr evidence."""

from __future__ import annotations

import re
import subprocess
import time
from pathlib import Path
from typing import Any

from tools.genops.artifact_writer import ensure_dir


def slugify(value: str, fallback: str) -> str:
    slug = re.sub(r"[^A-Za-z0-9_.-]+", "-", value).strip("-")
    return slug[:80] or fallback


def run_command(command: str, root: Path, log_dir: Path, index: int) -> dict[str, Any]:
    ensure_dir(log_dir)
    label = slugify(command, f"gate-{index}")
    stdout_path = log_dir / f"{index:02d}-{label}.stdout.log"
    stderr_path = log_dir / f"{index:02d}-{label}.stderr.log"
    started = time.perf_counter()
    proc = subprocess.run(command, cwd=root, shell=True, text=True, capture_output=True)
    duration = time.perf_counter() - started
    stdout_path.write_text(proc.stdout, encoding="utf-8")
    stderr_path.write_text(proc.stderr, encoding="utf-8")
    return {
        "command": command,
        "cwd": str(root),
        "returncode": proc.returncode,
        "duration_seconds": round(duration, 3),
        "stdout_log": str(stdout_path.relative_to(root)),
        "stderr_log": str(stderr_path.relative_to(root)),
        "status": "pass" if proc.returncode == 0 else "fail",
    }


def run_gates(commands: list[str], root: Path, log_dir: Path, enabled: bool) -> list[dict[str, Any]]:
    if not commands:
        return []
    if not enabled:
        return [
            {
                "command": command,
                "cwd": str(root),
                "returncode": None,
                "duration_seconds": 0,
                "stdout_log": None,
                "stderr_log": None,
                "status": "skipped",
            }
            for command in commands
        ]
    return [run_command(command, root, log_dir, index) for index, command in enumerate(commands, start=1)]

