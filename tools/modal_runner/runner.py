"""Local controller. Imports Modal only for commands which contact the service."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import fcntl
import json
import os
from pathlib import Path
import signal
import shutil
import subprocess
import sys
import tarfile
import time
import uuid

from .remote import PAL, CATALOG, DEPENDENCIES, PRESETS, commands_for, select_cases, select_npc_life_cases, safe_extract, sha256

REPO = Path(__file__).resolve().parents[2]
APP = "myvillage-tests"
SANDBOX_NAME = "myvillage-test-runner"
SDK_VERSION = "1.6.1"
IMAGE = "eclipse-temurin:21-jdk-noble"


def inspect_source(archive: Path) -> None:
    """Reject common private paths and nonregular entries before any remote allocation."""
    forbidden = {".ssh", ".aws", ".gnupg", ".modal.toml", "credentials.json", "id_rsa", "id_ed25519"}
    with tarfile.open(archive) as tf:
        for m in tf.getmembers():
            p = Path(m.name)
            if (p.is_absolute() or ".." in p.parts or not (m.isfile() or m.isdir())
                    or any(part in forbidden or part == ".env" or part.startswith(".env.") for part in p.parts)
                    or p.suffix in (".pem", ".key", ".p12", ".pfx")):
                raise ValueError(f"Forbidden source archive path: {m.name}")


def write_json(path: Path, data: dict) -> None:
    temp = path.with_suffix(".tmp")
    temp.write_text(json.dumps(data, indent=2) + "\n")
    temp.replace(path)


@contextmanager
def lock(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w") as fd:
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise RuntimeError("Another local Modal job is active") from exc
        yield


def prepare(repo: Path, ref: str, preset: str, timeout: int, out: Path, cases=None) -> dict:
    started = time.monotonic()
    if ref.startswith("-") or not 180 <= timeout <= 7200 or preset not in PRESETS:
        raise ValueError("Invalid ref/preset or timeout (allowed 180..7200 seconds)")
    commands = commands_for(preset, cases)
    commit = subprocess.check_output(["git", "rev-parse", "--verify", ref + "^{commit}"], cwd=repo, text=True).strip()
    for name in DEPENDENCIES:
        if not (repo / name).is_file():
            raise FileNotFoundError(f"Required dependency missing: {repo / name}")
    out.mkdir(parents=True, exist_ok=False)
    archive = out / "source.tar"
    with archive.open("wb") as stream:
        subprocess.run(["git", "archive", "--format=tar", commit], cwd=repo, stdout=stream, check=True)
    inspect_source(archive)
    for name in DEPENDENCIES:
        target = out / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(repo / name, target)
    shutil.copyfile(Path(__file__).with_name("remote.py"), out / "remote.py")
    request = {"format": 1, "run_id": out.name, "commit": commit, "requested_ref": ref,
               "preset": preset, "timeout_seconds": timeout, "cpu": [4, 4], "memory_mib": [16384, 16384],
               "sdk_version": SDK_VERSION, "image_base": IMAGE, "automatic_retries": 0,
               "image_tools": {"python": "system", "preview_python": "/usr/bin/python3"},
               "controller_sha256": sha256(Path(__file__)),
               "source_policy": "git archive plus exactly the required PAL jar and block catalog",
               "inputs": {"source.tar": sha256(archive), **{name: sha256(out / name) for name in DEPENDENCIES},
                          "remote.py": sha256(out / "remote.py")},
               "commands": commands,
               "cases": (select_cases(cases) if preset == "alchemy-quality" else
                         select_npc_life_cases(cases) if preset == "npc-life-runtime" else None)}
    request["archive_seconds"] = round(time.monotonic() - started, 3)
    write_json(out / "request.json", request)
    return request


def sdk():
    import modal
    if modal.__version__ != SDK_VERSION:
        raise RuntimeError(f"Install tools/modal_runner/requirements.txt (need Modal {SDK_VERSION})")
    return modal


def image(modal):
    return (modal.Image.from_registry(IMAGE)
            .apt_install("python3", "python3-yaml", "python3-numpy", "python3-pil", "git", "curl", "unzip",
                         "xvfb", "xauth", "xdotool", "ffmpeg", "imagemagick", "fonts-dejavu-core",
                         "mesa-utils", "libgl1-mesa-dri", "libglx-mesa0", "libopenal1", "libxrandr2",
                         "libxinerama1", "libxcursor1", "libxi6", "libxxf86vm1", "procps")
            .env({"GRADLE_USER_HOME": "/cache/gradle", "LIBGL_ALWAYS_SOFTWARE": "1",
                  "MC_PREVIEW_PYTHON": "/usr/bin/python3",
                  "MC_HEAVY_LOCK": "/workspace/.mc-heavy.lock", "PYTHONUNBUFFERED": "1"}))


def execute(modal, request: dict, out: Path, repo: Path) -> int:
    """Always attempt collection and termination, including after a command failure."""
    state = {**request, "status": "creating", "started_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
             "phases_seconds": {}, "termination": "not_created"}
    write_json(out / "state.json", state)
    sb = None
    start = time.monotonic()
    code = 1
    try:
        app = modal.App.lookup(APP, create_if_missing=True)
        volume = modal.Volume.from_name("myvillage-gradle-cache-v1", create_if_missing=True)
        container_image = image(modal)
        with modal.enable_output():
            sb = modal.Sandbox.create("sleep", "infinity", app=app, name=SANDBOX_NAME,
                                     image=container_image, cpu=(4, 4), memory=(16384, 16384),
                                     timeout=request["timeout_seconds"], volumes={"/cache": volume},
                                     tags={"run_id": request["run_id"], "commit": request["commit"]})
        state.update(sandbox_id=sb.object_id, status="uploading", termination="pending")
        state["image_id"] = getattr(container_image, "object_id", None)
        state["phases_seconds"]["create_including_image"] = round(time.monotonic() - start, 3)
        write_json(out / "state.json", state)
        phase = time.monotonic()
        for name in ("source.tar", *DEPENDENCIES, "remote.py", "request.json"):
            sb.filesystem.copy_from_local(out / name, "/workspace/" + name)
        state["phases_seconds"]["upload"] = round(time.monotonic() - phase, 3)
        phase = time.monotonic()
        remaining = request["timeout_seconds"] - state["phases_seconds"]["upload"] - 120
        if remaining < 1:
            raise TimeoutError("Upload exhausted the job budget")
        state["status"] = "running"
        write_json(out / "state.json", state)
        selection = ["--cases", ",".join(request["cases"])] if request.get("cases") is not None else []
        process = sb.exec("python3", "/workspace/remote.py", "--preset", request["preset"],
                          "--timeout", str(int(remaining)), *selection, timeout=int(remaining) + 30)
        # Both streams are drained independently to avoid a full stderr pipe deadlock.
        from threading import Thread
        stream_errors = []
        def drain(stream, dest):
            try:
                with dest.open("w") as log:
                    for line in stream:
                        log.write(line)
                        log.flush()
                        print(line, end="", flush=True)
            except Exception as exc:
                stream_errors.append(exc)
        outputs = [Thread(target=drain, args=(process.stdout, out / "remote.stdout.log"), daemon=True),
                   Thread(target=drain, args=(process.stderr, out / "remote.stderr.log"), daemon=True)]
        for thread in outputs:
            thread.start()
        code = process.wait()
        for thread in outputs:
            thread.join(timeout=10)
        if stream_errors or any(thread.is_alive() for thread in outputs):
            raise RuntimeError("Remote console stream did not finish cleanly; inspect fetched artifacts")
        state["phases_seconds"]["remote_job"] = round(time.monotonic() - phase, 3)
        state["exit_code"] = code
    except (Exception, KeyboardInterrupt) as exc:
        state["error"] = f"{type(exc).__name__}: {exc}"
        print(state["error"], file=sys.stderr)
    finally:
        if sb is not None:
            phase = time.monotonic()
            try:
                sb.filesystem.copy_to_local("/workspace/artifacts.json", out / "artifacts.json")
                metadata = json.loads((out / "artifacts.json").read_text())
                if metadata["archive_bytes"] > 2 * 1024 ** 3:
                    raise ValueError("Remote artifact archive exceeds limit")
                sb.filesystem.copy_to_local("/workspace/artifacts.tar.gz", out / "artifacts.tar.gz")
                if sha256(out / "artifacts.tar.gz") != metadata["sha256"]:
                    raise ValueError("Artifact checksum mismatch")
                safe_extract(out / "artifacts.tar.gz", out / "artifacts")
                state["artifacts"] = "downloaded_and_verified"
            except (Exception, KeyboardInterrupt) as exc:
                state["artifact_error"] = f"{type(exc).__name__}: {exc}"
                code = 1
            state["phases_seconds"]["download"] = round(time.monotonic() - phase, 3)
            phase = time.monotonic()
            try:
                sb.terminate(wait=True)
                state["termination"] = "confirmed"
            except Exception as exc:
                state["termination"] = "unconfirmed_ttl_backstop"
                state["termination_error"] = f"{type(exc).__name__}: {exc}"
                code = 1
            state["phases_seconds"]["terminate"] = round(time.monotonic() - phase, 3)
        state["status"] = "passed" if code == 0 and "error" not in state else "failed"
        state["wall_seconds"] = round(time.monotonic() - start, 3)
        state["ended_utc"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        write_json(out / "state.json", state)
    return 0 if state["status"] == "passed" else (code or 1)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("doctor", "plan", "run", "status", "terminate"))
    parser.add_argument("--ref", default="HEAD")
    parser.add_argument("--preset", choices=PRESETS, default="smoke")
    parser.add_argument("--timeout", type=int, default=1800)
    parser.add_argument("--cases", help="alchemy-quality or npc-life-runtime: all or comma-separated scenario names")
    parser.add_argument("--run", type=Path, help="Existing local run directory for status/terminate")
    args = parser.parse_args(argv)
    try:
        if args.command == "doctor":
            modal = sdk()
            modal.Client.from_env()  # SDK verifies the active profile; never print its token.
            dependencies = {name: (REPO / name).is_file() for name in DEPENDENCIES}
            print(json.dumps({"sdk": modal.__version__, "authentication": "verified", "dependencies": dependencies}))
            return 0 if all(dependencies.values()) else 1
        if args.command in ("status", "terminate"):
            if not args.run:
                raise ValueError("--run is required")
            state = json.loads((args.run / "state.json").read_text())
            sb = sdk().Sandbox.from_id(state["sandbox_id"])
            if args.command == "terminate":
                if sb.get_tags().get("run_id") != state["run_id"]:
                    raise ValueError("Sandbox run identity mismatch")
                sb.terminate(wait=True)
                state["termination"] = "confirmed"
                write_json(args.run / "state.json", state)
            print(json.dumps({"sandbox_id": sb.object_id, "returncode": sb.poll()}))
            return 0
        out = REPO / "reports/modal-runs" / (time.strftime("%Y%m%dT%H%M%SZ", time.gmtime()) + "-" + uuid.uuid4().hex[:8])
        with lock(REPO / "reports/modal-runs/runner.lock"):
            request = prepare(REPO, args.ref, args.preset, args.timeout, out, args.cases)
            print(json.dumps({"run": str(out), **request}, indent=2), flush=True)
            if args.command == "plan":
                return 0
            # Ctrl-C and SIGTERM take the same cleanup route. SIGKILL is covered by Modal TTL.
            def interrupted(_signum, _frame):
                raise KeyboardInterrupt("Termination requested")
            signal.signal(signal.SIGTERM, interrupted)
            return execute(sdk(), request, out, REPO)
    except (Exception, KeyboardInterrupt) as exc:
        print(f"{type(exc).__name__}: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
