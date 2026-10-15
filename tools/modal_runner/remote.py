"""Uploaded independently of the tested commit; standard library only."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import signal
import subprocess
import tarfile
import time
from threading import Event, Thread

PAL = "PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar"
CATALOG = "exmod/mod_block_catalog.json"
DEPENDENCIES = (PAL, CATALOG)
PRESETS = {
    "smoke": [["java", "-version"], ["python3", "-c", "import yaml, numpy, PIL; print('python dependencies OK')"],
              ["xvfb-run", "-a", "glxinfo", "-B"]],
    "python": [["python3", "-m", "unittest", "discover", "-s", "tools/tests"]],
    "jar": [["./gradlew", "jar", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "build": [["./gradlew", "test", "build", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "crops-build": [["./gradlew", "test", "--tests", "com.example.myvillage.block.SpiritCropsTest", "build", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "danxia-build": [["python3", "-m", "unittest", "tools.tests.test_danxia_ruin", "tools.tests.test_combat_capture", "tools.tests.test_modal_runner"],
                     ["python3", "tools/gen_danxia_ruin.py", "--check"],
                     ["./gradlew", "test", "--tests", "com.example.myvillage.ruins.*", "build", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "danxia-runtime": [["python3", "-m", "unittest", "tools.tests.test_danxia_ruin"],
                       ["python3", "tools/gen_danxia_ruin.py", "--check"],
                       ["./gradlew", "test", "--tests", "com.example.myvillage.ruins.*", "jar", "-x", "generateAllStructures", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"],
                       ["python3", "tools/danxia_ruin_evidence.py"]],
    "alchemy-build": [["./gradlew", "test", "--tests", "com.example.myvillage.alchemy.*", "build", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "fates": [["./gradlew", "test", "--tests", "com.example.myvillage.cultivation.fate.FateFoundationTest", "build", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx2g", "--max-workers=4"]],
    "release": [["python3", "tools/release_gate.py"]],
    "server": [["python3", "tools/run_chunky_acceptance.py", "--stage", "1"]],
    "client": [["python3", "-m", "tools.combat_capture", "sword-qi", "--trials", "tap_hit,charged_hit", "--out", "out/preview/modal_sword_qi"]],
    "alchemy-quality": [["python3", "tools/run_chunky_acceptance.py", "--stage", "1"],
                        ["python3", "tools/alchemy_quality_evidence.py"]],
}
PRESETS["release-alchemy"] = PRESETS["release"] + PRESETS["alchemy-quality"]
ALCHEMY_CASES = ("brewing", "multiplayer", "medicine", "persistence", "ui")


def select_cases(value=None):
    """Canonical bounded selection shared by local planning and game evidence."""
    if value is None or value == "all":
        return list(ALCHEMY_CASES)
    names = [name.strip() for name in value.split(",")]
    if not names or len(names) != len(set(names)) or any(name not in ALCHEMY_CASES for name in names):
        raise ValueError("Cases must be all or unique names from: " + ",".join(ALCHEMY_CASES))
    return [name for name in ALCHEMY_CASES if name in names]


def commands_for(preset, cases=None):
    if cases is not None and preset != "alchemy-quality":
        raise ValueError("--cases is only supported by alchemy-quality; release-alchemy always runs all cases")
    commands = [list(command) for command in PRESETS[preset]]
    if preset == "alchemy-quality":
        commands[-1] += ["--cases", ",".join(select_cases(cases))]
    return commands


ARTIFACT_ROOTS = ("build/libs", "build/test-results", "build/reports", "reports",
                  "out/preview", "run-acceptance/logs", "run-acceptance/acceptance-server-stdout.log",
                  "run-combat-capture/logs", "run-combat-capture/client/options.txt")
MAX_ARTIFACT_BYTES = 2 * 1024 ** 3


def safe_extract(archive: Path, destination: Path) -> None:
    """No links/devices/absolute paths or traversal, even in a trusted Git archive."""
    with tarfile.open(archive) as tf:
        members = tf.getmembers()
        if sum(m.size for m in members) > MAX_ARTIFACT_BYTES:
            raise ValueError("Archive uncompressed content exceeds 2 GiB")
        for m in members:
            p = Path(m.name)
            if p.is_absolute() or ".." in p.parts or not (m.isfile() or m.isdir()):
                raise ValueError(f"Unsafe archive member: {m.name}")
        tf.extractall(destination, members=members, filter="data")


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def initialize_archive_git(repo: Path) -> dict:
    """Make an explicitly synthetic baseline so release generator drift is checked.

    Git archives intentionally contain no original .git history. This local-only
    commit tracks exactly their extracted source, including ignored tracked files,
    before runtime dependencies or test outputs are added. The real tested commit
    remains request['commit']; this baseline must never replace that provenance.
    """
    if (repo / ".git").exists():
        raise ValueError("Extracted archive unexpectedly contains .git")
    commands = (["git", "init", "--quiet"], ["git", "add", "--all", "--force", "--", "."],
                ["git", "-c", "user.name=Modal Archive Baseline", "-c", "user.email=modal-baseline@example.invalid",
                 "commit", "--quiet", "--no-gpg-sign", "--no-verify", "-m", "Synthetic archive validation baseline"])
    for command in commands:
        subprocess.run(command, cwd=repo, check=True, capture_output=True, timeout=120)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True, timeout=10).strip()
    return {"synthetic": True, "head": commit,
            "purpose": "generator drift comparison only; tested commit is request.commit"}


def package(repo: Path, evidence: Path, output: Path) -> dict:
    paths = list(evidence.rglob("*"))
    for root in ARTIFACT_ROOTS:
        path = repo / root
        paths.extend([path] if path.is_file() else path.rglob("*"))
    selected, total = [], 0
    for p in sorted(set(paths)):
        if not p.is_file() or p.is_symlink():
            continue
        # A symlinked parent could otherwise export files outside the workspace.
        base = evidence if p.is_relative_to(evidence) else repo
        if not p.resolve().is_relative_to(base.resolve()):
            continue
        size = p.stat().st_size
        total += size
        if total > MAX_ARTIFACT_BYTES:
            raise ValueError("Artifacts exceed 2 GiB; narrow the preset/output before rerunning")
        name = ("evidence/" + str(p.relative_to(evidence))) if base == evidence else str(p.relative_to(repo))
        selected.append((p, name, size))
    with tarfile.open(output, "w:gz") as tf:
        for p, name, _ in selected:
            tf.add(p, arcname=name, recursive=False)
    return {"files": len(selected), "uncompressed_bytes": total,
            "archive_bytes": output.stat().st_size, "sha256": sha256(output)}


def run_step(command: list[str], repo: Path, log: Path, timeout: float) -> dict:
    start = time.monotonic()
    result = {"command": command, "started_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    with log.open("w") as stream:
        p = subprocess.Popen(command, cwd=repo, stdout=stream, stderr=subprocess.STDOUT,
                             start_new_session=True)
        try:
            result["exit_code"] = p.wait(timeout=max(0.1, timeout))
        except subprocess.TimeoutExpired:
            result["timed_out"] = True
            os.killpg(p.pid, signal.SIGKILL)
            p.wait()
            result["exit_code"] = 124
    result["wall_seconds"] = round(time.monotonic() - start, 3)
    return result


def sample_rss(stop: Event, measurements: dict) -> None:
    """All visible Sandbox processes, including detached Gradle JVMs; shared pages counted per process."""
    while not stop.is_set():
        total = count = 0
        for path in Path("/proc").glob("[0-9]*/status"):
            try:
                for line in path.read_text().splitlines():
                    if line.startswith("VmRSS:"):
                        total += int(line.split()[1])
                        count += 1
            except (OSError, ValueError):
                continue
        measurements["peak_rss_sum_kib"] = max(total, measurements.get("peak_rss_sum_kib", 0))
        measurements["peak_processes"] = max(count, measurements.get("peak_processes", 0))
        stop.wait(0.5)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--preset", choices=PRESETS, required=True)
    parser.add_argument("--timeout", type=int, required=True)
    parser.add_argument("--cases")
    args = parser.parse_args()
    repo, evidence = Path("/workspace/repo"), Path("/workspace/evidence")
    repo.mkdir(parents=True, exist_ok=True)
    evidence.mkdir(exist_ok=True)
    start = time.monotonic()
    result = {"preset": args.preset, "steps": [], "platform": platform.platform(),
              "python": platform.python_version(), "cpu_count": os.cpu_count(),
              "renderer": "software requested; see glxinfo for observed renderer",
              "cache": "persistent Gradle cache; workspace/worlds are ephemeral"}
    cache = Path(os.environ.get("GRADLE_USER_HOME", "/cache/gradle"))
    result["cache_before"] = {"exists": cache.exists(), "entries": sorted(p.name for p in cache.iterdir()) if cache.exists() else []}
    result["meminfo"] = Path("/proc/meminfo").read_text()
    result["java_version"] = subprocess.run(["java", "-version"], capture_output=True, text=True).stderr.strip()
    measurements = {"scope": "Sandbox /proc RSS sum; shared pages counted per process; sampled every 0.5s"}
    stop = Event()
    monitor = Thread(target=sample_rss, args=(stop, measurements), daemon=True)
    monitor.start()
    code = 1
    try:
        request = json.loads(Path("/workspace/request.json").read_text())
        result["request"] = request
        commands = commands_for(args.preset, args.cases)
        if request["commands"] != commands:
            raise ValueError("Requested commands differ from bounded preset selection")
        for name, digest in request["inputs"].items():
            if sha256(Path("/workspace") / name) != digest:
                raise ValueError(f"Input hash mismatch: {name}")
        safe_extract(Path("/workspace/source.tar"), repo)
        if args.preset in ("release", "release-alchemy"):
            result["archive_git_baseline"] = initialize_archive_git(repo)
        for name in DEPENDENCIES:
            target = repo / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((Path("/workspace") / name).read_bytes())
        os.chmod(repo / "gradlew", 0o755)
        for i, command in enumerate(commands):
            step = run_step(command, repo, evidence / f"step-{i}.log", args.timeout - (time.monotonic() - start))
            result["steps"].append(step)
            print(json.dumps(step), flush=True)
            code = step["exit_code"]
            if code:
                break
    except Exception as exc:
        code = 1
        result["error"] = f"{type(exc).__name__}: {exc}"
    finally:
        stop.set()
        monitor.join(timeout=2)
        classes = repo / "build/classes/java/main"
        if classes.is_dir():
            hashes = {path.relative_to(classes).as_posix(): sha256(path)
                      for path in sorted(classes.rglob("*.class"))}
            (evidence / "runtime_classes.json").write_text(json.dumps(hashes, indent=2) + "\n")
            result["compiled_main_classes"] = len(hashes)
        result["resources"] = measurements
        result["exit_code"] = code
        result["wall_seconds"] = round(time.monotonic() - start, 3)
        result["ended_utc"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        for name in ("memory.peak", "memory.max", "cpu.max", "cpu.stat"):
            p = Path("/sys/fs/cgroup") / name
            result["cgroup_" + name] = p.read_text().strip() if p.exists() else None
        (evidence / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        meta = package(repo, evidence, Path("/workspace/artifacts.tar.gz"))
        Path("/workspace/artifacts.json").write_text(json.dumps(meta))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
