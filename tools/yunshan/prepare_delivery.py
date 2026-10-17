#!/usr/bin/env python3
"""Prepare a checked Minecraft world ZIP and a non-overwriting Windows importer.

This only writes the local delivery directory. It never contacts the owner PC.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import zipfile

WINDOWS_RESERVED = re.compile(r"^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)", re.I)


def safe_component(value: str) -> bool:
    return bool(value and value not in (".", "..") and not value.endswith((".", " "))
                and not re.search(r'[<>:"/\\|?*\x00-\x1f]', value)
                and not WINDOWS_RESERVED.match(value))


def inspect_archive(archive: Path) -> dict:
    """Reject ambiguous/unsafe Windows paths and find one complete world root."""
    paths: set[str] = set()
    files: set[str] = set()
    size = 0
    with zipfile.ZipFile(archive) as z:
        members = z.infolist()
        if len(members) > 100_000:
            raise ValueError("archive has too many entries")
        for member in members:
            raw = member.filename.rstrip("/")
            parts = raw.split("/")
            if not all(safe_component(part) for part in parts):
                raise ValueError(f"unsafe archive path: {member.filename!r}")
            folded = raw.casefold()
            if folded in paths:
                raise ValueError(f"duplicate Windows archive path: {member.filename!r}")
            paths.add(folded)
            mode = member.external_attr >> 16
            if stat.S_ISLNK(mode) or (stat.S_IFMT(mode) not in (0, stat.S_IFREG, stat.S_IFDIR)):
                raise ValueError(f"non-regular archive entry: {member.filename!r}")
            if member.flag_bits & 1:
                raise ValueError("encrypted ZIP entries are unsupported")
            size += member.file_size
            if member.file_size > 2 * 1024**3 or size > 8 * 1024**3:
                raise ValueError("archive exceeds the world delivery size limit")
            if not member.is_dir():
                files.add(raw)
        folded_files = {name.casefold() for name in files}
        for name in files:
            for parent in PurePosixPath(name).parents:
                if str(parent) != "." and str(parent).casefold() in folded_files:
                    raise ValueError(f"archive path has a file as parent: {name!r}")
        roots = [str(PurePosixPath(name).parent) for name in files if PurePosixPath(name).name == "level.dat"]
        if len(roots) != 1:
            raise ValueError("archive must contain exactly one level.dat")
        root = "" if roots[0] == "." else roots[0]
        ignored = sum(1 for name in files if root and not name.startswith(root + "/"))
        level = f"{root}/level.dat" if root else "level.dat"
        with z.open(level) as f:
            if f.read(2) != b"\x1f\x8b":
                raise ValueError("level.dat is not gzipped Minecraft NBT")
        if z.testzip() is not None:
            raise ValueError("archive has a corrupt ZIP member")
    digest = hashlib.sha256()
    with archive.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return {"world_root": root, "files": len(files), "ignored_files_outside_world": ignored,
            "uncompressed_bytes": size,
            "archive_sha256": digest.hexdigest(),
            "archive_bytes": archive.stat().st_size}


IMPORT_SCRIPT = r'''# Run in Windows PowerShell: powershell -ExecutionPolicy Bypass -File .\install-yunshan.ps1
param([string]$GameDir = __GAME_DIR__, [string]$ArchivePath = '')
$ErrorActionPreference = 'Stop'
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'delivery.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not $ArchivePath) { $ArchivePath = Join-Path $PSScriptRoot 'world.zip' }
if (-not (Test-Path -LiteralPath $GameDir -PathType Container)) { throw 'Minecraft instance directory does not exist.' }
if ((Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.archive_sha256) {
    throw 'World archive SHA256 mismatch. Nothing was imported.'
}
$saves = Join-Path $GameDir 'saves'
[System.IO.Directory]::CreateDirectory($saves) | Out-Null
$destination = Join-Path $saves $manifest.world_folder
if (Test-Path -LiteralPath $destination) { throw 'The new save already exists. No existing save will be replaced.' }
$staging = Join-Path $saves ('.yunshan-import-' + [guid]::NewGuid().ToString('N'))
try {
    [System.IO.Directory]::CreateDirectory($staging) | Out-Null
    Expand-Archive -LiteralPath $ArchivePath -DestinationPath $staging
    $source = $staging
    if ($manifest.world_root) { $source = Join-Path $staging $manifest.world_root }
    if (-not (Test-Path -LiteralPath (Join-Path $source 'level.dat') -PathType Leaf)) { throw 'World level.dat is missing.' }
    # Directory.Move refuses an existing destination, including a race after the earlier check.
    [System.IO.Directory]::Move($source, $destination)
    Write-Host ('Imported new save: ' + $destination)
    Write-Host 'Start the matching Minecraft instance and select this new save. Existing worlds were preserved.'
} finally {
    if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
}
'''


def prepare(archive: Path, out: Path, world_folder: str, game_dir: str, requirements: dict | None = None) -> dict:
    if not safe_component(world_folder) or world_folder.startswith("."):
        raise ValueError("world_folder must be a visible, plain Windows directory name")
    manifest = {**inspect_archive(archive), "world_folder": world_folder,
                "requirements": requirements or {}, "owner_preview": "not_verified"}
    out.mkdir(parents=True, exist_ok=False)
    shutil.copyfile(archive, out / "world.zip")
    (out / "delivery.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    quoted_dir = "'" + game_dir.replace("'", "''") + "'"
    (out / "install-yunshan.ps1").write_text(IMPORT_SCRIPT.replace("__GAME_DIR__", quoted_dir), encoding="utf-8-sig")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--world-folder", required=True)
    parser.add_argument("--game-dir", required=True)
    parser.add_argument("--requirements", type=Path, help="JSON with the actual tested runtime/jar identities")
    args = parser.parse_args()
    requirements = json.loads(args.requirements.read_text()) if args.requirements else {}
    print(json.dumps(prepare(args.archive, args.out, args.world_folder, args.game_dir, requirements), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
