"""Context bundle creation for GenOps tasks."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from tools.genops.artifact_writer import ensure_dir, write_json, write_text
from tools.genops.models import PipelineSpec, TaskSpec


def build_shared_context(root: Path, run_dir: Path, repo: dict[str, Any], pipeline: PipelineSpec) -> None:
    context_dir = ensure_dir(run_dir / "context")
    write_json(context_dir / "repo_snapshot.json", repo)
    write_json(
        context_dir / "relevant_files.json",
        {
            "entrypoints": [
                "docs/ai-kb/INDEX.md",
                "README.md",
                "AGENTS.md",
                "openspec/specs/",
                "tools/",
                "src/main/resources/data/myvillage/",
            ],
            "pipeline": pipeline.path,
        },
    )
    write_text(
        context_dir / "spec_map.md",
        "# Spec Map\n\n"
        "- Start at `docs/ai-kb/INDEX.md` for narrative docs and capability specs.\n"
        "- Use `openspec/specs/validation/spec.md` for validation gates.\n"
        "- Use `openspec/specs/resource-export/spec.md` for generated resource paths.\n",
    )
    write_text(
        context_dir / "code_map.md",
        "# Code Map\n\n"
        "- `tools/generate_all_structures.py` is the batch generation entry point.\n"
        "- `tools/buildgen/` contains Python generator primitives and validators.\n"
        "- `src/main/java/com/example/myvillage/` contains runtime command/worldgen code.\n",
    )


def build_task_bundle(root: Path, run_dir: Path, pipeline: PipelineSpec, task: TaskSpec) -> Path:
    task_dir = ensure_dir(run_dir / "tasks" / task.id)
    bundle_path = task_dir / "context_bundle.md"
    lines = [
        f"# Context Bundle: {task.id}",
        "",
        f"- Pipeline: `{pipeline.id}`",
        f"- Agent: `{task.agent}`",
        f"- Goal: {task.raw.get('goal', pipeline.goal_summary)}",
        f"- Depends on: {', '.join(task.depends_on) if task.depends_on else 'none'}",
        "",
        "## Scope",
        "",
        f"- Allowed files: {', '.join(task.allowed_files) if task.allowed_files else 'not declared'}",
        f"- Forbidden files: {', '.join(task.forbidden_files) if task.forbidden_files else 'not declared'}",
        "",
        "## Required Outputs",
        "",
    ]
    for output in task.outputs or ["patch.diff", "task_result.json", "evidence.json"]:
        lines.append(f"- `{output}`")
    lines.extend(
        [
            "",
            "## Shared Context",
            "",
            "- `context/repo_snapshot.json`",
            "- `context/relevant_files.json`",
            "- `context/spec_map.md`",
            "- `context/code_map.md`",
        ]
    )
    write_text(bundle_path, "\n".join(lines) + "\n")
    return bundle_path

