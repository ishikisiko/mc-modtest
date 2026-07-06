"""Run manifest and summary writers for GenOps."""

from __future__ import annotations

from datetime import datetime, timezone
import json
from pathlib import Path
from typing import Any

from tools.genops.artifact_writer import ensure_dir, write_json, write_text
from tools.genops.models import PipelineSpec


ARTIFACT_KEYS = {
    "artifacts",
    "artifact_paths",
    "changed_artifacts",
    "changed_files",
    "created_files",
    "modified_files",
    "outputs",
    "protected_artifacts",
}

VERDICTS = {
    "pending",
    "accept",
    "reject",
    "accept_with_changes",
    "not_required",
    "pause",
    "reopen_discussion",
}

VERDICT_ALIASES = {
    "accepted": "accept",
    "rejected": "reject",
    "not_required_nonvisual_auto_archive": "not_required",
}


def normalize_human_verdict(value: str | None) -> str:
    if not value:
        return "pending"
    normalized = VERDICT_ALIASES.get(str(value), str(value))
    return normalized if normalized in VERDICTS else "pending"


def create_run_dir(root: Path, run_id: str) -> Path:
    return ensure_dir(root / "reports" / "agent_runs" / run_id)


def _read_json_if_exists(path: Path) -> Any:
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def _strings(value: Any) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, list):
        result: list[str] = []
        for item in value:
            result.extend(_strings(item))
        return result
    if isinstance(value, dict):
        result: list[str] = []
        for item in value.values():
            result.extend(_strings(item))
        return result
    return []


def _artifact_paths(value: Any) -> list[str]:
    if not isinstance(value, dict):
        return []
    paths: list[str] = []
    for key, item in value.items():
        if key in ARTIFACT_KEYS:
            paths.extend(_strings(item))
        elif isinstance(item, dict):
            paths.extend(_artifact_paths(item))
        elif isinstance(item, list):
            for child in item:
                paths.extend(_artifact_paths(child))
    return sorted({path for path in paths if path})


def collect_artifact_index(run_dir: Path, task_records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    index: list[dict[str, Any]] = []
    for record in task_records:
        task_id = record["id"]
        task_dir = run_dir / "tasks" / task_id
        artifacts: set[str] = set()
        for filename in ("task_result.json", "evidence.json", "patch_guard.json"):
            payload = _read_json_if_exists(task_dir / filename)
            if payload is not None:
                artifacts.update(_artifact_paths(payload))
        index.append(
            {
                "task_id": task_id,
                "agent": record["agent"],
                "status": record["status"],
                "artifacts": sorted(artifacts),
            }
        )
    return index


def write_final_manifest(
    root: Path,
    run_dir: Path,
    pipeline: PipelineSpec,
    run_id: str,
    goal: str,
    repo: dict[str, Any],
    task_records: list[dict[str, Any]],
    visual: dict[str, Any],
    defects: dict[str, Any],
    human_verdict: str | None,
) -> dict[str, Any]:
    verdict = normalize_human_verdict(human_verdict)
    visual_verdict = normalize_human_verdict(visual.get("latest_human_verdict"))
    if verdict == "pending" and visual_verdict != "pending":
        verdict = visual_verdict
    failed = [task for task in task_records if task["status"] == "fail"]
    blocked = [task for task in task_records if task["status"] == "blocked"]
    manual = [task for task in task_records if task["status"] == "manual_ready"]
    planned = [task for task in task_records if task["status"] == "plan_materialized"]
    if failed:
        status = "failed"
    elif blocked:
        status = "blocked"
    elif manual:
        status = "manual_ready"
    elif planned:
        status = "planning_ready"
    elif verdict == "reject":
        status = "rejected_by_human"
    elif verdict == "pause":
        status = "paused"
    elif verdict == "reopen_discussion":
        status = "discussion_reopened"
    elif verdict == "accept":
        status = "accepted"
    elif verdict == "accept_with_changes":
        status = "accepted_with_changes"
    elif pipeline.human_review_required and verdict == "pending":
        status = "human_review_pending"
    else:
        status = "pass"

    artifact_index = collect_artifact_index(run_dir, task_records)
    manifest = {
        "run_id": run_id,
        "pipeline": pipeline.id,
        "pipeline_file": pipeline.path,
        "repo_ref": repo,
        "status": status,
        "goal": goal,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "tasks": task_records,
        "artifact_index": artifact_index,
        "visual": visual,
        "defect_index": defects,
        "human_verdict": verdict,
    }
    write_json(run_dir / "run_manifest.json", manifest)
    return manifest


def write_final_summary(run_dir: Path, manifest: dict[str, Any]) -> None:
    lines = [
        f"# GenOps Run {manifest['run_id']}",
        "",
        f"- Pipeline: `{manifest['pipeline']}`",
        f"- Status: `{manifest['status']}`",
        f"- Human verdict: `{manifest['human_verdict']}`",
        f"- Goal: {manifest['goal']}",
        "",
        "## Tasks",
        "",
        "| Task | Agent | Status | Artifacts |",
        "|---|---|---|---|",
    ]
    artifacts_by_task = {
        item["task_id"]: item.get("artifacts", []) for item in manifest.get("artifact_index", [])
    }
    for task in manifest["tasks"]:
        artifact_count = len(artifacts_by_task.get(task["id"], []))
        lines.append(
            f"| `{task['id']}` | `{task['agent']}` | `{task['status']}` | {artifact_count} |"
        )
    lines.extend(
        [
            "",
            "## Front Door",
            "",
            f"- Status: `{manifest.get('frontdoor', {}).get('status', 'not_run')}`",
            f"- Protected paths: {len(manifest.get('frontdoor', {}).get('protected_paths', []))}",
            f"- Findings: {len(manifest.get('frontdoor', {}).get('findings', []))}",
            "",
            "## Visual",
            "",
            f"- Preview index: `{manifest['visual']['preview_index']['path']}` "
            f"({manifest['visual']['preview_index']['exists']})",
            f"- Visual report: `{manifest['visual']['visual_acceptance_report_md']['path']}` "
            f"({manifest['visual']['visual_acceptance_report_md']['exists']})",
        ]
    )
    write_text(run_dir / "final_summary.md", "\n".join(lines) + "\n")
