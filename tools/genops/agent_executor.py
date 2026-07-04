"""Local GenOps executor backends.

These backends do not spawn autonomous agents. They create the contract,
prompt, empty patch, and structured evidence that an external coding agent or
human can fill in later.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from tools.genops.artifact_writer import ensure_dir, write_json, write_text
from tools.genops.models import PipelineSpec, TaskSpec
from tools.genops.pipeline_loader import load_mapping


def load_agent_brief(root: Path, agent: str) -> str:
    path = root / "genops" / "agents" / f"{agent}.md"
    if not path.exists():
        return f"# {agent}\n\nNo role brief found.\n"
    return path.read_text(encoding="utf-8")


def custom_agent_name(root: Path, agent: str) -> str | None:
    path = root / "genops" / "subagents.yaml"
    if not path.exists():
        return None
    mapping = load_mapping(path).get("custom_agents", {})
    if not isinstance(mapping, dict):
        return None
    value = mapping.get(agent)
    return str(value) if value else None


def prepare_prompt(root: Path, run_dir: Path, pipeline: PipelineSpec, task: TaskSpec, context_bundle: Path) -> Path:
    task_dir = ensure_dir(run_dir / "tasks" / task.id)
    prompt_path = task_dir / "prompt.md"
    codex_agent = custom_agent_name(root, task.agent)
    codex_agent_line = f"Suggested Codex custom subagent: `{codex_agent}`\n\n" if codex_agent else ""
    prompt = (
        f"# GenOps Task Prompt: {task.id}\n\n"
        f"Pipeline: `{pipeline.id}`\n\n"
        f"{codex_agent_line}"
        f"## Role Brief\n\n{load_agent_brief(root, task.agent)}\n\n"
        f"## Context Bundle\n\nRead `{context_bundle.relative_to(run_dir)}` before acting.\n\n"
        "## Output Contract\n\n"
        "- Write a unified diff to `patch.diff`.\n"
        "- Write structured status to `task_result.json`.\n"
        "- Write command/file evidence to `evidence.json`.\n"
        "- Stay inside `allowed_files`; never touch `forbidden_files`.\n"
    )
    write_text(prompt_path, prompt)
    return prompt_path


def execute(
    root: Path,
    run_dir: Path,
    pipeline: PipelineSpec,
    task: TaskSpec,
    context_bundle: Path,
    executor: str,
) -> dict[str, Any]:
    task_dir = ensure_dir(run_dir / "tasks" / task.id)
    prompt_path = prepare_prompt(root, run_dir, pipeline, task, context_bundle)
    patch_path = task_dir / "patch.diff"
    write_text(patch_path, "")

    status = "pass" if executor == "no_op" else "manual_ready"
    summary = (
        "No-op executor prepared artifacts without changing files."
        if executor == "no_op"
        else "Manual executor prepared prompt and awaits an external patch/result."
    )
    result = {
        "task_id": task.id,
        "agent": task.agent,
        "status": status,
        "summary": summary,
        "changed_files": [],
        "declared_invariants_preserved": [],
        "commands_run": [],
        "new_defects_addressed": [],
        "risks": [],
    }
    evidence = {
        "executor": executor,
        "codex_custom_agent": custom_agent_name(root, task.agent),
        "prompt": str(prompt_path.relative_to(root)),
        "context_bundle": str(context_bundle.relative_to(root)),
        "patch": str(patch_path.relative_to(root)),
    }
    write_json(task_dir / "task_result.json", result)
    write_json(task_dir / "evidence.json", evidence)
    return {"result": result, "evidence": evidence, "patch_text": ""}
