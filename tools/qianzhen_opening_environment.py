#!/usr/bin/env python3
"""只读 Colab 复用清点；不启动/停止游戏、不安装依赖、不更改世界。"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time


def command(argv, cwd=None):
    try:
        result = subprocess.run(argv, cwd=cwd, text=True, capture_output=True,
                                timeout=5, env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"})
        return {"exit_code": result.returncode, "stdout": result.stdout[-16000:],
                "stderr": result.stderr[-2000:]}
    except (OSError, subprocess.TimeoutExpired) as error:
        return {"error": type(error).__name__}


def file_identity(path):
    return {"path": str(path), "bytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


def sanitize(value):
    return re.sub(r"(?i)((?:password|token|secret|api[_-]?key)[=\s]+)[^\s]+", r"\1<redacted>", value)


def processes(proc=Path("/proc")):
    rows = []
    for entry in proc.iterdir():
        if not entry.name.isdigit():
            continue
        try:
            argv = (entry / "cmdline").read_bytes().replace(b"\0", b" ").decode(errors="replace").strip()
            if not re.search(r"java|gradle|minecraft|Xvfb|ffmpeg|qianzhen|combat_capture", argv, re.I):
                continue
            status = (entry / "status").read_text()
            rows.append({"pid": int(entry.name), "ppid": int(re.search(r"PPid:\s+(\d+)", status)[1]),
                         "state": re.search(r"State:\s+([^\n]+)", status)[1],
                         "rss_kib": int(re.search(r"VmRSS:\s+(\d+)", status)[1]) if "VmRSS:" in status else 0,
                         "cwd": str((entry / "cwd").resolve()), "argv": sanitize(argv)[:2500]})
        except (OSError, ValueError, TypeError):
            continue
    return rows


def inspect_root(root):
    result = {"root": str(root), "exists": root.is_dir()}
    if not root.is_dir():
        return result
    result["head"] = command(["git", "rev-parse", "HEAD"], root)
    result["tracked_status"] = command(["git", "status", "--short", "--untracked-files=no"], root)
    result["libraries"] = [file_identity(path) for path in sorted({
        *(root / "libs").glob("*.jar"), *root.glob("PlayerAnimationLibNeoforge-*.jar")})]
    result["built_jars"] = [file_identity(path) for path in sorted((root / "build/libs").glob("*.jar"))]
    result["source_files"] = [file_identity(root / name) for name in (
        "tools/qianzhen/story_input.py", "tools/qianzhen/story_evidence.py",
        "src/main/resources/data/myvillage/story/qianzhen_chapter_1.json",
    ) if (root / name).is_file()]
    # Only inspect known runtime directories, at bounded depth. No recursive world reads.
    result["runtime_directories"] = []
    for name in ("run-acceptance", "run-combat-capture", "run", "out"):
        base = root / name
        if base.is_dir():
            result["runtime_directories"].append({"path": str(base), "entries": sorted(p.name for p in base.iterdir())[:80]})
    return result


def inspect(roots):
    content = Path("/content")
    found = [p for p in content.iterdir() if p.is_dir() and (p / "build.gradle").is_file()] if content.exists() else []
    roots = sorted(set([*roots, *found]))
    rows = processes()
    worlds = []
    for root in roots:
        for base in (root / "run-acceptance", root / "run-combat-capture/server", root / "run"):
            if not base.is_dir():
                continue
            for path in base.glob("*/level.dat"):
                worlds.append({"directory": str(path.parent), "level_dat": file_identity(path),
                               "playerdata_files": len(list((path.parent / "playerdata").glob("*.dat")))})
    displays = []
    for socket in sorted(Path("/tmp/.X11-unix").glob("X*")):
        display = ":" + socket.name[1:]
        argv = (["xwininfo", "-display", display, "-root", "-tree"] if shutil.which("xwininfo")
                else ["env", "DISPLAY=" + display, "xdotool", "search", "--onlyvisible", "--name", "Minecraft"])
        displays.append({"display": display, "windows": command(argv)})
    return {"schema": "qianzhen-opening-environment-v1", "observed_unix": time.time(),
            "read_only": True, "boot_id": Path("/proc/sys/kernel/random/boot_id").read_text().strip(),
            "memory": Path("/proc/meminfo").read_text().splitlines()[:4], "cpus": os.cpu_count(),
            "disk": dict(zip(("total", "used", "free"), shutil.disk_usage("/content" if content.exists() else "/"))),
            "commands": {key: shutil.which(key) for key in ("java", "python3", "Xvfb", "xdotool", "ffmpeg", "xwininfo")},
            "roots": [inspect_root(root) for root in roots], "processes": rows,
            "worlds": worlds, "displays": displays,
            "cache_directories": [str(p) for p in (Path("/root/.gradle"), Path("/content/qianzhen-gradle-cache")) if p.is_dir()],
            "reuse": "Existing processes/worlds are occupied; do not overwrite or terminate. New tests require a unique checkout/runtime/world and fixed source."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, action="append", default=[])
    args = parser.parse_args()
    print(json.dumps(inspect(args.root), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
