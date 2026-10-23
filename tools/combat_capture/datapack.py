"""Hash-pinned startup datapacks, installed under the supervisor's heavy lock."""
from __future__ import annotations

import hashlib
from pathlib import Path
import shutil


def file_hashes(folder):
    folder = Path(folder)
    if folder.is_symlink() or not folder.is_dir():
        raise ValueError('Startup datapack must be a real directory')
    files = {}
    for path in sorted(folder.rglob('*')):
        if path.is_symlink():
            raise ValueError('Symlink in startup datapack: '+str(path))
        if path.is_file():
            files[path.relative_to(folder).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    return files


def verify(folder, expected):
    if not isinstance(expected, dict) or 'pack.mcmeta' not in expected or file_hashes(folder) != expected:
        raise ValueError('Startup datapack fingerprint mismatch')


def install(source, expected, world, *, reuse=False):
    """Fresh worlds get the pack before level.dat exists; restarts must retain it."""
    verify(source, expected)
    target = Path(world)/'datapacks/qianzhen_candidate'
    if reuse:
        verify(target, expected)
    else:
        if target.exists():
            raise ValueError('Refuse to overwrite existing startup datapack')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(source, target)
        verify(target, expected)
    return target
