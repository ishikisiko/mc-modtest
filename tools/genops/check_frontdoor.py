#!/usr/bin/env python3
"""Validate protected-path changes against GenOps front-door evidence."""

from __future__ import annotations

import argparse
import fnmatch
import json
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[2]

BOOTSTRAP_CHANGE = "openspec/changes/enforce-craft-frontdoor-governance"

PROTECTED_PATTERNS: list[tuple[str, str]] = [
    (".codex/agents/**", "genops"),
    (".codex/skills/**", "skills"),
    ("openspec/changes/**", "openspec"),
    ("openspec/specs/**", "openspec"),
    ("docs/ai-kb/**", "docs"),
    ("genops/**", "genops"),
    ("tools/genops/**", "genops"),
    ("tools/buildgen/**", "generator"),
    ("src/main/resources/data/myvillage/structure/*.nbt", "generated-structure"),
    ("src/main/**", "runtime"),
    ("gradle.properties", "release"),
    ("src/main/resources/META-INF/neoforge.mods.toml", "release"),
    ("CHANGELOG.md", "release"),
    ("README.md", "docs"),
    ("CRAFT.md", "docs"),
    ("AGENTS.md", "docs"),
]

ROLE_ALLOWLIST: dict[str, set[str]] = {
    "openspec": {"docs-steward", "spec-guardian", "regression-steward"},
    "docs": {"docs-steward", "release-steward", "spec-guardian"},
    "genops": {"manager", "pipeline-architect", "docs-steward", "regression-steward"},
    "skills": {"pipeline-architect", "docs-steward", "spec-guardian"},
    "generator": {"generator-engineer"},
    "generated-structure": {"generator-engineer", "regression-steward"},
    "runtime": {"java-worldgen-engineer", "java-runtime-engineer", "resource-asset-steward"},
    "release": {"release-steward"},
}

RELEASE_PATTERNS = [
    "gradle.properties",
    "src/main/resources/META-INF/neoforge.mods.toml",
    "CHANGELOG.md",
]


@dataclass(frozen=True)
class ProtectedPath:
    path: str
    category: str
    pattern: str


@dataclass
class Ownership:
    task_id: str
    agent: str
    pipeline: str
    artifacts: set[str] = field(default_factory=set)


def normalize_path(path: str | Path, root: Path = ROOT) -> str:
    value = str(path).replace("\\", "/")
    path_obj = Path(value)
    if path_obj.is_absolute():
        try:
            value = path_obj.resolve().relative_to(root.resolve()).as_posix()
        except ValueError:
            value = path_obj.as_posix()
    while value.startswith("./"):
        value = value[2:]
    return value.rstrip("/")


def matches(path: str, pattern: str, root: Path = ROOT) -> bool:
    normalized = normalize_path(path, root)
    pat = normalize_path(pattern, root)
    return fnmatch.fnmatch(normalized, pat) or PurePosixPath(normalized).match(pat)


def protected_match(path: str, root: Path = ROOT) -> ProtectedPath | None:
    normalized = normalize_path(path, root)
    for pattern, category in PROTECTED_PATTERNS:
        if matches(normalized, pattern, root):
            return ProtectedPath(path=normalized, category=category, pattern=pattern)
    return None


def is_bootstrap_path(path: str) -> bool:
    normalized = normalize_path(path)
    return normalized == BOOTSTRAP_CHANGE or normalized.startswith(f"{BOOTSTRAP_CHANGE}/")


def git_status_paths(root: Path) -> list[str]:
    proc = subprocess.run(
        ["git", "status", "--porcelain", "--untracked-files=all"],
        cwd=root,
        text=True,
        capture_output=True,
        check=False,
    )
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.strip() or "git status failed")
    paths: set[str] = set()
    for line in proc.stdout.splitlines():
        if not line:
            continue
        raw = line[3:] if len(line) > 3 else line
        if " -> " in raw:
            raw = raw.split(" -> ", 1)[1]
        paths.add(normalize_path(raw))
    return sorted(paths)


def inspect_paths(root: Path, explicit_paths: list[str] | None) -> list[ProtectedPath]:
    paths = [normalize_path(path, root) for path in explicit_paths] if explicit_paths else git_status_paths(root)
    protected: list[ProtectedPath] = []
    for path in paths:
        match = protected_match(path, root)
        if match is not None:
            protected.append(match)
    return sorted(protected, key=lambda item: item.path)


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def manifest_path(root: Path, run_id: str, manifest_arg: Path | None) -> Path | None:
    if manifest_arg is not None:
        return manifest_arg if manifest_arg.is_absolute() else root / manifest_arg
    if not run_id:
        return None
    return root / "reports" / "agent_runs" / run_id / "run_manifest.json"


def strings_from(value: Any) -> Iterable[str]:
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for item in value:
            yield from strings_from(item)
    elif isinstance(value, dict):
        for item in value.values():
            yield from strings_from(item)


def artifact_strings(value: Any, root: Path = ROOT) -> set[str]:
    artifacts: set[str] = set()
    for string in strings_from(value):
        normalized = normalize_path(string, root)
        if protected_match(normalized, root) is not None:
            artifacts.add(normalized)
    return artifacts


def load_ownership(root: Path, manifest: Path) -> list[Ownership]:
    data = load_json(manifest)
    run_dir = manifest.parent
    pipeline = str(data.get("pipeline", ""))
    ownership: dict[tuple[str, str], Ownership] = {}

    def entry(task_id: str, agent: str) -> Ownership:
        key = (task_id, agent)
        if key not in ownership:
            ownership[key] = Ownership(task_id=task_id, agent=agent, pipeline=pipeline)
        return ownership[key]

    for task in data.get("tasks", []):
        if not isinstance(task, dict):
            continue
        task_id = str(task.get("id", ""))
        agent = str(task.get("agent", ""))
        if not task_id or not agent:
            continue
        owner = entry(task_id, agent)
        owner.artifacts.update(artifact_strings(task, root))

    for item in data.get("artifact_index", []):
        if not isinstance(item, dict):
            continue
        task_id = str(item.get("task_id") or item.get("id") or "")
        agent = str(item.get("agent", ""))
        if not task_id or not agent:
            continue
        owner = entry(task_id, agent)
        owner.artifacts.update(artifact_strings(item, root))

    for task_dir in sorted((run_dir / "tasks").glob("*")) if (run_dir / "tasks").exists() else []:
        if not task_dir.is_dir():
            continue
        for filename in ("task_result.json", "evidence.json", "patch_guard.json"):
            path = task_dir / filename
            if not path.exists():
                continue
            payload = load_json(path)
            task_id = str(payload.get("task_id") or task_dir.name)
            agent = str(payload.get("agent") or "")
            if not agent:
                for task in data.get("tasks", []):
                    if isinstance(task, dict) and task.get("id") == task_id:
                        agent = str(task.get("agent", ""))
                        break
            if not agent:
                continue
            owner = entry(task_id, agent)
            owner.artifacts.update(artifact_strings(payload, root))

    return list(ownership.values())


def artifact_matches_path(artifact: str, path: str, root: Path = ROOT) -> bool:
    art = normalize_path(artifact, root)
    target = normalize_path(path, root)
    if any(char in art for char in "*?[]"):
        return matches(target, art, root)
    return target == art or target.startswith(f"{art}/")


def matching_owners(path: ProtectedPath, ownership: list[Ownership]) -> list[Ownership]:
    return [owner for owner in ownership if any(artifact_matches_path(artifact, path.path) for artifact in owner.artifacts)]


def allowed_roles(path: ProtectedPath) -> set[str]:
    if any(matches(path.path, pattern) for pattern in RELEASE_PATTERNS):
        return {"release-steward"}
    return ROLE_ALLOWLIST.get(path.category, set())


def validate(
    protected_paths: list[ProtectedPath],
    ownership: list[Ownership],
    allow_bootstrap: bool,
    manifest: Path | None,
) -> dict[str, Any]:
    findings: list[dict[str, str]] = []
    accepted: list[dict[str, str]] = []

    for protected in protected_paths:
        if allow_bootstrap and is_bootstrap_path(protected.path):
            accepted.append(
                {
                    "path": protected.path,
                    "reason": "bootstrap_exception",
                    "category": protected.category,
                }
            )
            continue

        if manifest is None:
            findings.append(
                {
                    "path": protected.path,
                    "reason": "missing_provenance",
                    "category": protected.category,
                }
            )
            continue

        owners = matching_owners(protected, ownership)
        if not owners:
            findings.append(
                {
                    "path": protected.path,
                    "reason": "missing_matching_artifact",
                    "category": protected.category,
                }
            )
            continue

        permitted = allowed_roles(protected)
        valid = [owner for owner in owners if not permitted or owner.agent in permitted]
        if not valid:
            findings.append(
                {
                    "path": protected.path,
                    "reason": "mismatched_worker_ownership",
                    "category": protected.category,
                    "allowed_roles": ",".join(sorted(permitted)),
                    "actual_roles": ",".join(sorted({owner.agent for owner in owners})),
                }
            )
            continue

        owner = valid[0]
        accepted.append(
            {
                "path": protected.path,
                "reason": "matched_run_evidence",
                "category": protected.category,
                "pipeline": owner.pipeline,
                "task_id": owner.task_id,
                "agent": owner.agent,
            }
        )

    return {
        "status": "pass" if not findings else "fail",
        "protected_paths": [item.path for item in protected_paths],
        "accepted": accepted,
        "findings": findings,
    }


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--run-id", default="", help="GenOps run id under reports/agent_runs")
    parser.add_argument("--manifest", type=Path, help="Explicit run_manifest.json path")
    parser.add_argument("--allow-bootstrap", action="store_true", help="Allow this change's narrow bootstrap exception")
    parser.add_argument("--paths", nargs="*", help="Explicit paths to inspect instead of git status")
    parser.add_argument("--json", action="store_true", help="Print machine-readable JSON")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    root = args.root.resolve()
    protected = inspect_paths(root, args.paths)
    manifest = manifest_path(root, args.run_id, args.manifest)

    ownership: list[Ownership] = []
    manifest_for_validation: Path | None = None
    if manifest is not None:
        if not manifest.exists():
            result = {
                "status": "fail",
                "protected_paths": [item.path for item in protected],
                "accepted": [],
                "findings": [{"path": str(manifest), "reason": "manifest_not_found"}],
            }
            print(json.dumps(result, indent=2, ensure_ascii=False) if args.json else "front-door check failed: manifest not found")
            return 1
        ownership = load_ownership(root, manifest)
        manifest_for_validation = manifest

    result = validate(protected, ownership, args.allow_bootstrap, manifest_for_validation)
    if args.json:
        print(json.dumps(result, indent=2, ensure_ascii=False))
    else:
        print(f"front-door check: {result['status']}")
        for item in result["accepted"]:
            print(f"accepted: {item['path']} ({item['reason']})")
        for item in result["findings"]:
            detail = item.get("actual_roles") or item.get("category") or ""
            print(f"finding: {item['path']} ({item['reason']}{': ' + detail if detail else ''})")
    return 0 if result["status"] == "pass" else 1


if __name__ == "__main__":
    raise SystemExit(main())
