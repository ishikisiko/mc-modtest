"""Run manifest and summary writers for GenOps."""

from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from tools.genops.artifact_writer import ensure_dir, write_json, write_text
from tools.genops.models import PipelineSpec


def create_run_dir(root: Path, run_id: str) -> Path:
    return ensure_dir(root / "reports" / "agent_runs" / run_id)


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
    failed = [task for task in task_records if task["status"] == "fail"]
    manual = [task for task in task_records if task["status"] == "manual_ready"]
    if failed:
        status = "failed"
    elif human_verdict == "reject":
        status = "rejected_by_human"
    elif human_verdict == "accept":
        status = "accepted"
    elif manual:
        status = "manual_ready"
    elif pipeline.human_review_required:
        status = "human_review_pending"
    else:
        status = "pass"

    manifest = {
        "run_id": run_id,
        "pipeline": pipeline.id,
        "pipeline_file": pipeline.path,
        "repo_ref": repo,
        "status": status,
        "goal": goal,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "tasks": task_records,
        "visual": visual,
        "defect_index": defects,
        "human_verdict": human_verdict or "pending",
    }
    write_json(run_dir / "run_manifest.json", manifest)
    return manifest


def write_final_summary(run_dir: Path, manifest: dict[str, Any]) -> None:
    lines = [
        f"# GenOps Run {manifest['run_id']}",
        "",
        f"- Pipeline: `{manifest['pipeline']}`",
        f"- Status: `{manifest['status']}`",
        f"- Goal: {manifest['goal']}",
        "",
        "## Tasks",
        "",
        "| Task | Agent | Status |",
        "|---|---|---|",
    ]
    for task in manifest["tasks"]:
        lines.append(f"| `{task['id']}` | `{task['agent']}` | `{task['status']}` |")
    lines.extend(
        [
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

