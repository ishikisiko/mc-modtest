#!/usr/bin/env python3
"""Run staged Chunky acceptance checks against an isolated dev server profile."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import secrets
import shutil
import socket
import struct
import subprocess
import sys
import tomllib
import time
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
PROFILE_DIR = ROOT / "run-acceptance"
REPORT_PATH = ROOT / "reports" / "chunky_acceptance_report.json"
METADATA_PATH = ROOT / "tools" / "chunky_acceptance_metadata.json"
EXMOD_ZIP_PATH = ROOT / "exmod" / "mod_jars.zip"
REQUIRED_FULL_MOD_IDS = {
    "ars_nouveau": "Ars Nouveau",
    "curios": "Curios",
    "farmersdelight": "Farmer's Delight",
    "fetzisdisplays": "Fetzi's Displays",
    "geckolib": "GeckoLib",
    "mcwfurnitures": "Macaw's Furniture",
    "mcwwindows": "Macaw's Windows",
    "moonlight": "Moonlight",
    "supplementaries": "Supplementaries",
}
SERVER_PROVIDED_MOD_IDS = {"java", "minecraft", "neoforge"}


class RconError(RuntimeError):
    pass


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def sha512_file(path: Path) -> str:
    digest = hashlib.sha512()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


class RconClient:
    def __init__(self, host: str, port: int, password: str, timeout: float = 10.0) -> None:
        self.host = host
        self.port = port
        self.password = password
        self.timeout = timeout
        self.sock: socket.socket | None = None
        self.request_id = 100

    def __enter__(self) -> "RconClient":
        self.sock = socket.create_connection((self.host, self.port), timeout=self.timeout)
        self.sock.settimeout(self.timeout)
        packet_id, _packet_type, _body = self._request(3, self.password)
        if packet_id == -1:
            raise RconError("RCON authentication failed")
        return self

    def __exit__(self, _exc_type: object, _exc: object, _tb: object) -> None:
        if self.sock is not None:
            self.sock.close()

    def command(self, command: str) -> str:
        _packet_id, _packet_type, body = self._request(2, command)
        return body

    def _request(self, packet_type: int, body: str) -> tuple[int, int, str]:
        self.request_id += 1
        self._send_packet(self.request_id, packet_type, body)
        return self._recv_packet()

    def _send_packet(self, packet_id: int, packet_type: int, body: str) -> None:
        if self.sock is None:
            raise RconError("RCON socket is not connected")
        payload = struct.pack("<ii", packet_id, packet_type) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _recv_packet(self) -> tuple[int, int, str]:
        if self.sock is None:
            raise RconError("RCON socket is not connected")
        length_raw = self._recv_exact(4)
        length = struct.unpack("<i", length_raw)[0]
        data = self._recv_exact(length)
        packet_id, packet_type = struct.unpack("<ii", data[:8])
        body = data[8:-2].decode("utf-8", errors="replace")
        return packet_id, packet_type, body

    def _recv_exact(self, size: int) -> bytes:
        if self.sock is None:
            raise RconError("RCON socket is not connected")
        chunks: list[bytes] = []
        remaining = size
        while remaining:
            chunk = self.sock.recv(remaining)
            if not chunk:
                raise RconError("RCON connection closed")
            chunks.append(chunk)
            remaining -= len(chunk)
        return b"".join(chunks)


def load_metadata() -> dict[str, Any]:
    return json.loads(METADATA_PATH.read_text(encoding="utf-8"))


def provision_chunky(profile_dir: Path) -> dict[str, Any]:
    metadata = load_metadata()
    cache_dir = profile_dir / "_cache"
    mods_dir = profile_dir / "mods"
    cache_dir.mkdir(parents=True, exist_ok=True)
    mods_dir.mkdir(parents=True, exist_ok=True)

    cached_jar = cache_dir / metadata["filename"]
    if not cached_jar.exists():
        with urllib.request.urlopen(metadata["url"], timeout=60) as response:
            cached_jar.write_bytes(response.read())

    actual_sha512 = sha512_file(cached_jar)
    expected_sha512 = metadata["sha512"]
    if actual_sha512 != expected_sha512:
        cached_jar.unlink(missing_ok=True)
        raise RuntimeError(
            f"Chunky jar checksum mismatch for {metadata['filename']}: "
            f"expected {expected_sha512}, got {actual_sha512}"
        )

    target_jar = mods_dir / metadata["filename"]
    shutil.copy2(cached_jar, target_jar)
    return {
        "source": metadata["source"],
        "project_id": metadata["project_id"],
        "version_id": metadata["version_id"],
        "version_number": metadata["version_number"],
        "filename": metadata["filename"],
        "sha512": actual_sha512,
        "path": str(target_jar.relative_to(ROOT)),
    }


def read_mod_metadata_from_text(metadata_text: str) -> tuple[set[str], list[dict[str, str]]]:
    try:
        parsed = tomllib.loads(metadata_text)
    except tomllib.TOMLDecodeError:
        return set(), []

    mod_ids: set[str] = set()
    mods = parsed.get("mods", [])
    if isinstance(mods, list):
        for mod in mods:
            if isinstance(mod, dict) and isinstance(mod.get("modId"), str):
                mod_ids.add(mod["modId"])

    mandatory_dependencies: list[dict[str, str]] = []
    dependencies = parsed.get("dependencies", {})
    if isinstance(dependencies, dict):
        for owner_mod_id, entries in dependencies.items():
            if not isinstance(entries, list):
                continue
            for entry in entries:
                if not isinstance(entry, dict) or not isinstance(entry.get("modId"), str):
                    continue
                dependency_mod_id = entry["modId"]
                side = str(entry.get("side", "BOTH")).upper()
                dependency_type = str(entry.get("type", "")).lower()
                mandatory = entry.get("mandatory")
                is_required = dependency_type == "required" or mandatory is True
                if not is_required or side == "CLIENT" or dependency_mod_id in SERVER_PROVIDED_MOD_IDS:
                    continue
                mandatory_dependencies.append(
                    {
                        "owner_mod_id": str(owner_mod_id),
                        "mod_id": dependency_mod_id,
                        "version_range": str(entry.get("versionRange", "")),
                    }
                )
    return mod_ids, mandatory_dependencies


def read_mod_metadata_from_zip(jar: zipfile.ZipFile) -> tuple[set[str], list[dict[str, str]]]:
    mod_ids: set[str] = set()
    mandatory_dependencies: list[dict[str, str]] = []
    for metadata_name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
        try:
            metadata_text = jar.read(metadata_name).decode("utf-8", errors="replace")
        except KeyError:
            continue
        metadata_mod_ids, metadata_dependencies = read_mod_metadata_from_text(metadata_text)
        mod_ids.update(metadata_mod_ids)
        mandatory_dependencies.extend(metadata_dependencies)
    return mod_ids, mandatory_dependencies


def read_mod_metadata_from_jar(jar_path: Path) -> dict[str, Any]:
    mod_ids: set[str] = set()
    embedded_mod_ids: set[str] = set()
    mandatory_dependencies: list[dict[str, str]] = []
    with zipfile.ZipFile(jar_path) as jar:
        top_level_mod_ids, top_level_dependencies = read_mod_metadata_from_zip(jar)
        mod_ids.update(top_level_mod_ids)
        mandatory_dependencies.extend(top_level_dependencies)

        for nested_name in jar.namelist():
            if not nested_name.lower().endswith(".jar"):
                continue
            try:
                with zipfile.ZipFile(io.BytesIO(jar.read(nested_name))) as nested_jar:
                    nested_mod_ids, nested_dependencies = read_mod_metadata_from_zip(nested_jar)
            except zipfile.BadZipFile:
                continue
            embedded_mod_ids.update(nested_mod_ids)
            mandatory_dependencies.extend(nested_dependencies)
    return {
        "mod_ids": sorted(mod_ids),
        "embedded_mod_ids": sorted(embedded_mod_ids),
        "mandatory_dependencies": mandatory_dependencies,
    }


def provision_full_modset(profile_dir: Path) -> dict[str, Any]:
    if not EXMOD_ZIP_PATH.exists():
        raise RuntimeError(f"Stage 3 requires staged optional mod jars at {EXMOD_ZIP_PATH.relative_to(ROOT)}")

    mods_dir = profile_dir / "mods"
    mods_dir.mkdir(parents=True, exist_ok=True)
    extracted_jars: list[dict[str, Any]] = []
    present_mod_ids: set[str] = set()
    mandatory_dependencies: list[dict[str, str]] = []

    with zipfile.ZipFile(EXMOD_ZIP_PATH) as zip_handle:
        jar_infos = [
            info for info in zip_handle.infolist()
            if not info.is_dir() and info.filename.lower().endswith(".jar")
        ]
        if not jar_infos:
            raise RuntimeError(f"Stage 3 optional mod archive has no jar entries: {EXMOD_ZIP_PATH.relative_to(ROOT)}")

        for info in jar_infos:
            filename = Path(info.filename).name
            target_jar = mods_dir / filename
            with zip_handle.open(info) as source, target_jar.open("wb") as target:
                shutil.copyfileobj(source, target)

            jar_metadata = read_mod_metadata_from_jar(target_jar)
            jar_mod_ids = jar_metadata["mod_ids"]
            embedded_mod_ids = jar_metadata["embedded_mod_ids"]
            present_mod_ids.update(jar_mod_ids)
            present_mod_ids.update(embedded_mod_ids)
            for dependency in jar_metadata["mandatory_dependencies"]:
                dependency = dict(dependency)
                dependency["source_jar"] = filename
                mandatory_dependencies.append(dependency)
            extracted_jars.append(
                {
                    "filename": filename,
                    "path": str(target_jar.relative_to(ROOT)),
                    "size_bytes": target_jar.stat().st_size,
                    "sha512": sha512_file(target_jar),
                    "mod_ids": jar_mod_ids,
                    "embedded_mod_ids": embedded_mod_ids,
                    "mandatory_dependencies": jar_metadata["mandatory_dependencies"],
                }
            )

    missing_mod_ids = sorted(mod_id for mod_id in REQUIRED_FULL_MOD_IDS if mod_id not in present_mod_ids)
    missing_mandatory_dependencies = sorted(
        (
            dependency
            for dependency in mandatory_dependencies
            if dependency["mod_id"] not in present_mod_ids and dependency["mod_id"] not in SERVER_PROVIDED_MOD_IDS
        ),
        key=lambda dependency: (dependency["mod_id"], dependency["owner_mod_id"], dependency["source_jar"]),
    )
    result = {
        "enabled": True,
        "source_zip": str(EXMOD_ZIP_PATH.relative_to(ROOT)),
        "source_zip_sha512": sha512_file(EXMOD_ZIP_PATH),
        "required_mod_ids": REQUIRED_FULL_MOD_IDS,
        "present_required_mod_ids": sorted(mod_id for mod_id in REQUIRED_FULL_MOD_IDS if mod_id in present_mod_ids),
        "missing_required_mod_ids": missing_mod_ids,
        "missing_mandatory_dependencies": missing_mandatory_dependencies,
        "all_detected_mod_ids": sorted(present_mod_ids),
        "extracted_jars": extracted_jars,
        "verification_passed": not missing_mod_ids and not missing_mandatory_dependencies,
        "registry_startup_completed": False,
    }
    if missing_mod_ids:
        missing_names = ", ".join(f"{REQUIRED_FULL_MOD_IDS[mod_id]} ({mod_id})" for mod_id in missing_mod_ids)
        result["error"] = f"Stage 3 optional mod archive is missing required mod ids: {missing_names}"
    if missing_mandatory_dependencies:
        missing_names = ", ".join(
            f"{dependency['mod_id']} required by {dependency['owner_mod_id']} ({dependency['source_jar']})"
            for dependency in missing_mandatory_dependencies
        )
        result["error"] = f"Stage 3 optional mod archive is missing mandatory dependencies: {missing_names}"
    return result


def write_server_properties(
    profile_dir: Path,
    *,
    world_name: str,
    world_seed: str,
    server_port: int,
    rcon_port: int,
    rcon_password: str,
) -> None:
    properties = {
        "allow-flight": "true",
        "broadcast-console-to-ops": "true",
        "broadcast-rcon-to-ops": "true",
        "difficulty": "peaceful",
        "enable-command-block": "false",
        "enable-query": "false",
        "enable-rcon": "true",
        "enable-status": "true",
        "enforce-secure-profile": "false",
        "function-permission-level": "4",
        "gamemode": "creative",
        "generate-structures": "true",
        "level-name": world_name,
        "level-seed": world_seed,
        "level-type": "minecraft:normal",
        "max-players": "4",
        "max-tick-time": "-1",
        "motd": "MyVillage Chunky Acceptance",
        "online-mode": "false",
        "op-permission-level": "4",
        "pvp": "false",
        "rcon.password": rcon_password,
        "rcon.port": str(rcon_port),
        "server-ip": "127.0.0.1",
        "server-port": str(server_port),
        "simulation-distance": "6",
        "spawn-protection": "0",
        "view-distance": "6",
        "white-list": "false",
    }
    lines = ["# Generated by tools/run_chunky_acceptance.py"]
    lines.extend(f"{key}={value}" for key, value in sorted(properties.items()))
    (profile_dir / "server.properties").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (profile_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")


def prepare_profile(args: argparse.Namespace, report: dict[str, Any]) -> tuple[dict[str, Any], str]:
    profile_dir = PROFILE_DIR
    if args.clean and profile_dir.exists():
        shutil.rmtree(profile_dir)
    profile_dir.mkdir(parents=True, exist_ok=True)
    (profile_dir / "mods").mkdir(exist_ok=True)
    (profile_dir / "logs").mkdir(exist_ok=True)

    server_port = args.server_port or find_free_port()
    rcon_port = args.rcon_port or find_free_port()
    rcon_password = secrets.token_urlsafe(24)
    write_server_properties(
        profile_dir,
        world_name=args.world_name,
        world_seed=args.world_seed,
        server_port=server_port,
        rcon_port=rcon_port,
        rcon_password=rcon_password,
    )
    chunky = provision_chunky(profile_dir)
    if int(args.stage) >= 3:
        try:
            report["full_modset"] = provision_full_modset(profile_dir)
            if (
                report["full_modset"].get("missing_required_mod_ids")
                or report["full_modset"].get("missing_mandatory_dependencies")
            ):
                raise RuntimeError(report["full_modset"]["error"])
        except Exception as exc:  # noqa: BLE001 - preserve report context before failing
            report.setdefault(
                "full_modset",
                {
                    "enabled": True,
                    "source_zip": str(EXMOD_ZIP_PATH.relative_to(ROOT)),
                    "registry_startup_completed": False,
                },
            )
            report["full_modset"]["error"] = str(exc)
            raise
    else:
        report["full_modset"] = {
            "enabled": False,
            "source_zip": str(EXMOD_ZIP_PATH.relative_to(ROOT)),
            "reason": "Stage 3 not requested",
            "registry_startup_completed": False,
        }
    profile = {
        "path": str(profile_dir.relative_to(ROOT)),
        "clean": args.clean,
        "world_name": args.world_name,
        "world_seed": args.world_seed,
        "server_port": server_port,
        "rcon_host": "127.0.0.1",
        "rcon_port": rcon_port,
        "normal_run_server_properties_untouched": True,
    }
    report["server_profile"] = profile
    report["chunky"] = chunky
    return profile, rcon_password


def write_report(report: dict[str, Any]) -> None:
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def wait_for_rcon(
    profile: dict[str, Any],
    password: str,
    timeout: float,
    process: subprocess.Popen[str] | None = None,
) -> None:
    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        if process is not None and process.poll() is not None:
            raise RuntimeError(f"Server exited before RCON became ready with code {process.returncode}: {last_error}")
        try:
            with RconClient(profile["rcon_host"], profile["rcon_port"], password, timeout=5.0) as rcon:
                response = rcon.command("list")
                if "players online" in response or "There are" in response:
                    return
        except Exception as exc:  # noqa: BLE001 - diagnostic retry loop
            last_error = exc
        time.sleep(2)
    raise RuntimeError(f"Server did not become RCON-ready within {timeout:.0f}s: {last_error}")


def send_command(stage: dict[str, Any], rcon: RconClient, command: str) -> str:
    started = time.monotonic()
    response = rcon.command(command)
    stage["commands"].append(
        {
            "command": command,
            "response": response,
            "duration_seconds": round(time.monotonic() - started, 3),
        }
    )
    return response


def chunky_progress_complete(response: str) -> bool:
    lower = response.lower()
    return (
        "no tasks running" in lower
        or "no tasks are currently running" in lower
        or "complete" in lower
        or "100.00%" in lower
        or "100%" in lower
    )


def parse_locate_response(response: str) -> tuple[int, int] | None:
    lower = response.lower()
    if "could not find" in lower or "not found" in lower:
        return None
    match = re.search(r"\[\s*(-?\d+)\s*,\s*(?:~|-?\d+)\s*,\s*(-?\d+)\s*\]", response)
    if match:
        return int(match.group(1)), int(match.group(2))
    match = re.search(r"\bat\s+(-?\d+)\s*,\s*(?:~|-?\d+)\s*,\s*(-?\d+)\b", response)
    if match:
        return int(match.group(1)), int(match.group(2))
    return None


def response_looks_like_help(response: str, command: str) -> bool:
    lower = response.lower()
    return lower.startswith(f"{command.lower()} - ") or "usage:" in lower


def command_response_failed(response: str) -> bool:
    lower = response.lower()
    failure_tokens = (
        "unknown or incomplete command",
        "incorrect argument",
        "requires a player",
        "only players",
        "no player",
        "exception",
        "failed to",
    )
    return any(token in lower for token in failure_tokens)


def expect_response(stage: dict[str, Any], command: str, response: str, expected_marker: str) -> None:
    stage.setdefault("expectations", []).append(
        {
            "command": command,
            "expected_marker": expected_marker,
            "matched": expected_marker in response,
        }
    )
    if expected_marker not in response:
        raise RuntimeError(f"Command response did not include {expected_marker!r} for {command!r}: {response}")
    if command_response_failed(response):
        raise RuntimeError(f"Command response looked like a failure for {command!r}: {response}")


def start_acceptance_server() -> tuple[subprocess.Popen[str], Any, Path]:
    stdout_path = PROFILE_DIR / "acceptance-server-stdout.log"
    stdout_handle = stdout_path.open("w", encoding="utf-8")
    # Windows cannot exec the POSIX `gradlew` shell script directly; use the
    # `.bat` wrapper there. On POSIX the `.bat` does not exist, so keep `./gradlew`.
    gradlew_cmd = "gradlew.bat" if sys.platform == "win32" else "./gradlew"
    process = subprocess.Popen(
        [gradlew_cmd, "--no-daemon", "runAcceptanceServer"],
        cwd=ROOT,
        stdout=stdout_handle,
        stderr=subprocess.STDOUT,
        text=True,
    )
    return process, stdout_handle, stdout_path


def stop_acceptance_server(
    args: argparse.Namespace,
    profile: dict[str, Any],
    password: str,
    process: subprocess.Popen[str],
    stage: dict[str, Any],
) -> None:
    try:
        with RconClient(profile["rcon_host"], profile["rcon_port"], password, timeout=args.command_timeout) as rcon:
            send_command(stage, rcon, "save-all")
            send_command(stage, rcon, "stop")
    except Exception as stop_exc:  # noqa: BLE001
        stage["stop_error"] = str(stop_exc)
    try:
        process.wait(timeout=args.stop_timeout)
    except subprocess.TimeoutExpired:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()


def run_stage1_commands(args: argparse.Namespace, report: dict[str, Any], rcon: RconClient) -> None:
    stage = {
        "id": 1,
        "name": "Chunky/RCON/server lifecycle smoke",
        "status": "running",
        "started_at": utc_now(),
        "commands": [],
        "chunky_region": {
            "world": args.chunky_world,
            "shape": "square",
            "center_x": 0,
            "center_z": 0,
            "radius_blocks": args.chunky_radius,
        },
    }
    report["stages"].append(stage)
    write_report(report)

    try:
        setup_commands = [
            f"chunky world {args.chunky_world}",
            "chunky shape square",
            "chunky center 0 0",
            f"chunky radius {args.chunky_radius}",
            "chunky selection",
        ]
        for setup_command in setup_commands:
            response = send_command(stage, rcon, setup_command)
            if "unknown" in response.lower() or "incorrect" in response.lower():
                raise RuntimeError(f"Chunky setup command failed ({setup_command}): {response}")

        response = send_command(stage, rcon, "chunky start")
        if response_looks_like_help(response, "chunky start"):
            raise RuntimeError(f"Chunky start returned help instead of starting a task: {response}")
        if "unknown" in response.lower() or "incorrect" in response.lower():
            raise RuntimeError(f"Chunky start command failed: {response}")

        deadline = time.monotonic() + args.chunky_timeout
        last_progress = ""
        while time.monotonic() < deadline:
            time.sleep(args.progress_interval)
            last_progress = send_command(stage, rcon, "chunky progress")
            if chunky_progress_complete(last_progress):
                stage["chunky_completion"] = {
                    "complete": True,
                    "last_progress_response": last_progress,
                }
                break
        else:
            stage["chunky_completion"] = {
                "complete": False,
                "timeout_seconds": args.chunky_timeout,
                "last_progress_response": last_progress,
            }
            raise RuntimeError("Chunky task timed out")

        stage["status"] = "passed"
    except Exception as exc:  # noqa: BLE001 - report must be written on failure
        stage["status"] = "failed"
        stage["error"] = str(exc)
        raise
    finally:
        stage["ended_at"] = utc_now()
        write_report(report)


def run_stage2_commands(args: argparse.Namespace, report: dict[str, Any], rcon: RconClient) -> None:
    stage = {
        "id": 2,
        "name": "MyVillage coordinate command RCON smoke",
        "status": "running",
        "started_at": utc_now(),
        "commands": [],
        "cases": [],
    }
    report["stages"].append(stage)
    write_report(report)

    command_cases = [
        ("myvillage list", "Loaded myvillage structures"),
        ("myvillage placeat small_house_001 0 80 192", "Placed myvillage:small_house_001"),
        ("myvillage galleryat cultivation 256 80 192", "Placed myvillage cultivation gallery"),
        (f"myvillage townat {args.myvillage_seed} 1024 80 192", "Generated living town"),
        (f"myvillage sectat {args.myvillage_seed} -1024 80 192", "Generated sect compound"),
        (f"myvillage sectat worldgen {args.myvillage_seed} none -1024 80 768", "Generated sect compound"),
    ]

    try:
        for command, expected_marker in command_cases:
            response = send_command(stage, rcon, command)
            expect_response(stage, command, response, expected_marker)
            stage["cases"].append(
                {
                    "command": command,
                    "expected_marker": expected_marker,
                    "status": "passed",
                }
            )
        stage["status"] = "passed"
    except Exception as exc:  # noqa: BLE001 - report must be written on failure
        stage["status"] = "failed"
        stage["error"] = str(exc)
        raise
    finally:
        stage["ended_at"] = utc_now()
        write_report(report)


def run_stage3_commands(args: argparse.Namespace, report: dict[str, Any], rcon: RconClient) -> None:
    full_modset = report.get("full_modset", {})
    stage = {
        "id": 3,
        "name": "Full optional-mod MyVillage acceptance",
        "status": "running",
        "started_at": utc_now(),
        "commands": [],
        "cases": [],
        "full_modset": {
            "enabled": bool(full_modset.get("enabled")),
            "source_zip": full_modset.get("source_zip"),
            "extracted_jar_count": len(full_modset.get("extracted_jars", [])),
            "present_required_mod_ids": full_modset.get("present_required_mod_ids", []),
            "missing_required_mod_ids": full_modset.get("missing_required_mod_ids", []),
            "registry_startup_completed": bool(full_modset.get("registry_startup_completed")),
        },
    }
    report["stages"].append(stage)
    write_report(report)

    if not full_modset.get("enabled"):
        stage["status"] = "failed"
        stage["error"] = "Stage 3 requested without full_modset mode"
        stage["ended_at"] = utc_now()
        write_report(report)
        raise RuntimeError(stage["error"])

    command_cases = [
        ("myvillage galleryat cultivation 1536 80 192", "Placed myvillage cultivation gallery"),
        (f"myvillage townat {args.myvillage_seed} 2304 80 192", "Generated living town"),
        (f"myvillage sectat worldgen {args.myvillage_seed} none 2304 80 768", "Generated sect compound"),
    ]

    try:
        for command, expected_marker in command_cases:
            response = send_command(stage, rcon, command)
            expect_response(stage, command, response, expected_marker)
            stage["cases"].append(
                {
                    "command": command,
                    "expected_marker": expected_marker,
                    "status": "passed",
                }
            )
        stage["full_modset"]["registry_startup_completed"] = bool(
            report.get("full_modset", {}).get("registry_startup_completed")
        )
        stage["status"] = "passed"
    except Exception as exc:  # noqa: BLE001 - report must be written on failure
        stage["status"] = "failed"
        stage["error"] = str(exc)
        raise
    finally:
        stage["ended_at"] = utc_now()
        write_report(report)


def run_stage4_commands(args: argparse.Namespace, report: dict[str, Any], rcon: RconClient) -> None:
    stage = {
        "id": 4,
        "name": "Natural sect worldgen Chunky acceptance",
        "status": "running",
        "started_at": utc_now(),
        "commands": [],
        "locate": {},
        "chunky_region": {
            "world": args.chunky_world,
            "shape": "square",
            "radius_blocks": args.sect_worldgen_radius,
        },
    }
    report["stages"].append(stage)
    write_report(report)

    try:
        locate_command = "locate structure myvillage:sect"
        locate_response = send_command(stage, rcon, locate_command)
        if command_response_failed(locate_response):
            raise RuntimeError(f"Locate command failed: {locate_response}")
        located = parse_locate_response(locate_response)
        stage["locate"] = {
            "command": locate_command,
            "response": locate_response,
            "status": "located" if located else "sect_not_located",
        }
        if located is None:
            stage["status"] = "skipped"
            stage["skip_reason"] = "sect_not_located"
            return

        center_x, center_z = located
        stage["locate"]["x"] = center_x
        stage["locate"]["z"] = center_z
        stage["chunky_region"]["center_x"] = center_x
        stage["chunky_region"]["center_z"] = center_z

        setup_commands = [
            f"chunky world {args.chunky_world}",
            "chunky shape square",
            f"chunky center {center_x} {center_z}",
            f"chunky radius {args.sect_worldgen_radius}",
            "chunky selection",
        ]
        for setup_command in setup_commands:
            response = send_command(stage, rcon, setup_command)
            if "unknown" in response.lower() or "incorrect" in response.lower():
                raise RuntimeError(f"Chunky setup command failed ({setup_command}): {response}")

        response = send_command(stage, rcon, "chunky start")
        if response_looks_like_help(response, "chunky start"):
            raise RuntimeError(f"Chunky start returned help instead of starting a task: {response}")
        if "unknown" in response.lower() or "incorrect" in response.lower():
            raise RuntimeError(f"Chunky start command failed: {response}")

        deadline = time.monotonic() + args.sect_worldgen_timeout
        last_progress = ""
        while time.monotonic() < deadline:
            time.sleep(args.progress_interval)
            last_progress = send_command(stage, rcon, "chunky progress")
            if chunky_progress_complete(last_progress):
                stage["chunky_completion"] = {
                    "complete": True,
                    "last_progress_response": last_progress,
                }
                break
        else:
            stage["chunky_completion"] = {
                "complete": False,
                "timeout_seconds": args.sect_worldgen_timeout,
                "last_progress_response": last_progress,
            }
            raise RuntimeError("Natural sect Chunky task timed out")

        stage["status"] = "passed"
    except Exception as exc:  # noqa: BLE001 - report must be written on failure
        stage["status"] = "failed"
        stage["error"] = str(exc)
        raise
    finally:
        stage["ended_at"] = utc_now()
        write_report(report)


def run_acceptance(args: argparse.Namespace, report: dict[str, Any], profile: dict[str, Any], password: str) -> None:
    process, stdout_handle, stdout_path = start_acceptance_server()
    report["server_process"] = {"pid": process.pid, "stdout": str(stdout_path.relative_to(ROOT))}
    write_report(report)
    stop_stage = {"id": "stop", "name": "save and stop", "commands": []}
    try:
        wait_for_rcon(profile, password, args.server_ready_timeout, process)
        report.setdefault("server_process", {})["rcon_ready_at"] = utc_now()
        if report.get("full_modset", {}).get("enabled"):
            report["full_modset"]["registry_startup_completed"] = True
        write_report(report)
        with RconClient(
            profile["rcon_host"],
            profile["rcon_port"],
            password,
            timeout=args.command_timeout,
        ) as rcon:
            run_stage1_commands(args, report, rcon)
            if int(args.stage) >= 2:
                run_stage2_commands(args, report, rcon)
            else:
                report["downstream_stages"].append(
                    {"id": 2, "status": "skipped", "reason": "Stage 2 not requested"}
                )
            if int(args.stage) >= 3:
                run_stage3_commands(args, report, rcon)
            if int(args.stage) >= 4:
                run_stage4_commands(args, report, rcon)
            send_command(stop_stage, rcon, "save-all")
            send_command(stop_stage, rcon, "stop")
        try:
            process.wait(timeout=args.stop_timeout)
        except subprocess.TimeoutExpired as exc:
            raise RuntimeError("Server did not stop cleanly after RCON stop") from exc
        if process.returncode != 0:
            raise RuntimeError(f"Server process exited with code {process.returncode}")
    except Exception:
        if not any(stage.get("id") == 2 for stage in report["stages"]) and int(args.stage) >= 2:
            report["downstream_stages"].append(
                {"id": 2, "status": "skipped", "reason": "Stage 1 failed before Stage 2"}
            )
        if not any(stage.get("id") == 3 for stage in report["stages"]) and int(args.stage) >= 3:
            report["downstream_stages"].append(
                {"id": 3, "status": "skipped", "reason": "Earlier stage failed before Stage 3"}
            )
        if not any(stage.get("id") == 4 for stage in report["stages"]) and int(args.stage) >= 4:
            report["downstream_stages"].append(
                {"id": 4, "status": "skipped", "reason": "Earlier stage failed before Stage 4"}
            )
        stop_acceptance_server(args, profile, password, process, stop_stage)
        raise
    finally:
        stdout_handle.close()
        report["stop"] = stop_stage
        if process.poll() is not None:
            report.setdefault("server_process", {})["returncode"] = process.returncode
        write_report(report)


def summarize_logs(report: dict[str, Any]) -> None:
    latest = PROFILE_DIR / "logs" / "latest.log"
    summary: dict[str, Any] = {"latest_log": str(latest.relative_to(ROOT)) if latest.exists() else None}
    markers: list[str] = []
    chunky_markers: list[str] = []
    dependency_markers: list[str] = []
    latest_text = ""
    if latest.exists():
        latest_text = latest.read_text(encoding="utf-8", errors="replace")
        for line in latest_text.splitlines():
            lowered = line.lower()
            if any(token in lowered for token in ("[error]", "exception", "crash", "failed")):
                markers.append(line[-500:])
            if "[chunky]" in lowered and any(token in lowered for token in ("task started", "task running", "task finished")):
                chunky_markers.append(line[-500:])
            if any(
                token in lowered
                for token in (
                    "dependency",
                    "incompatible",
                    "missing or unsupported",
                    "mod loading has failed",
                    "modloadingexception",
                    "requires",
                )
            ):
                dependency_markers.append(line[-500:])
    summary["error_markers"] = markers[-50:]
    summary["chunky_markers"] = chunky_markers[-20:]
    summary["dependency_markers"] = dependency_markers[-80:]
    full_modset = report.get("full_modset")
    if isinstance(full_modset, dict) and full_modset.get("enabled") and latest_text:
        lowered_log = latest_text.lower()
        seen = sorted(mod_id for mod_id in REQUIRED_FULL_MOD_IDS if mod_id.lower() in lowered_log)
        full_modset["log_required_mod_ids_seen"] = seen
    report["log_summary"] = summary


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", choices=["1", "2", "3", "4"], default="1", help="Highest acceptance stage to run.")
    parser.add_argument("--clean", action=argparse.BooleanOptionalAction, default=True)
    parser.add_argument("--world-name", default="chunky_stage1_world")
    parser.add_argument("--world-seed", default="20260630")
    parser.add_argument("--myvillage-seed", type=int, default=20260618)
    parser.add_argument("--chunky-world", default="minecraft:overworld", help="Chunky world/dimension selector.")
    parser.add_argument("--chunky-radius", type=int, default=64, help="Square radius in blocks.")
    parser.add_argument("--sect-worldgen-radius", type=int, default=96, help="Stage 4 square radius in blocks.")
    parser.add_argument("--server-port", type=int, default=0)
    parser.add_argument("--rcon-port", type=int, default=0)
    parser.add_argument("--server-ready-timeout", type=float, default=240)
    parser.add_argument("--chunky-timeout", type=float, default=180)
    parser.add_argument("--sect-worldgen-timeout", type=float, default=300)
    parser.add_argument("--progress-interval", type=float, default=3)
    parser.add_argument("--command-timeout", type=float, default=600)
    parser.add_argument("--stop-timeout", type=float, default=90)
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    report: dict[str, Any] = {
        "schema_version": 1,
        "workflow": "chunky_acceptance",
        "started_at": utc_now(),
        "launch_path": {
            "primary": "./gradlew --no-daemon runAcceptanceServer",
            "fallback": "standalone NeoForge server profile is deferred until external-jar loading requires it",
            "game_directory": str(PROFILE_DIR.relative_to(ROOT)),
        },
        "myvillage_artifact": {
            "mode": "ModDevGradle source-set run",
            "mod_id": "myvillage",
        },
        "stages": [],
        "downstream_stages": [],
    }
    if int(args.stage) < 3:
        report["downstream_stages"].append({"id": 3, "status": "skipped", "reason": "Stage 3 not requested"})
    if int(args.stage) < 4:
        report["downstream_stages"].append({"id": 4, "status": "skipped", "reason": "Stage 4 not requested"})
    try:
        profile, password = prepare_profile(args, report)
        run_acceptance(args, report, profile, password)
        report["status"] = "passed"
        return 0
    except Exception as exc:  # noqa: BLE001
        report["status"] = "failed"
        report["error"] = str(exc)
        return 1
    finally:
        report["ended_at"] = utc_now()
        summarize_logs(report)
        write_report(report)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
