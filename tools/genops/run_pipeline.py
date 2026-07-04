#!/usr/bin/env python3
"""Run a local, artifact-first GenOps pipeline."""

from __future__ import annotations

import argparse
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tools.genops import agent_executor, context_builder, defect_indexer, gate_runner, git_guard
from tools.genops import patch_guard, pipeline_loader, report_writer, task_graph, visual_indexer
from tools.genops.artifact_writer import ensure_dir, write_json


ROOT = Path(__file__).resolve().parents[2]


def default_run_id(pipeline_id: str) -> str:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    slug = re.sub(r"[^A-Za-z0-9_.-]+", "-", pipeline_id).strip("-")
    return f"{stamp}-{slug}"


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pipeline", type=Path, help="Path to genops/pipelines/*.yaml")
    parser.add_argument("--goal", default="", help="Run-specific goal text")
    parser.add_argument("--run-id", default="", help="Stable report directory id")
    parser.add_argument("--executor", choices=["no_op", "manual"], default="no_op")
    parser.add_argument("--run-gates", action="store_true", help="Actually run task gate commands")
    parser.add_argument("--require-clean", action="store_true", help="Fail before execution if git status is dirty")
    parser.add_argument("--human-verdict", choices=["accept", "reject", "pending"], default="pending")
    parser.add_argument("--root", type=Path, default=ROOT)
    return parser.parse_args(argv)


def task_blocked(task: Any, task_records: list[dict[str, Any]]) -> bool:
    by_id = {record["id"]: record for record in task_records}
    return any(by_id.get(dep, {}).get("status") in {"fail", "blocked"} for dep in task.depends_on)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    root = args.root.resolve()
    pipeline_path = (root / args.pipeline).resolve() if not args.pipeline.is_absolute() else args.pipeline
    pipeline = pipeline_loader.load_pipeline(pipeline_path)
    run_id = args.run_id or default_run_id(pipeline.id)
    goal = args.goal or pipeline.goal_summary

    repo = git_guard.inspect_repo(root)
    if args.require_clean and repo["dirty"]:
        print("GenOps refused to run because --require-clean was set and git status is dirty.", file=sys.stderr)
        for line in repo["status_short"]:
            print(line, file=sys.stderr)
        return 2

    run_dir = report_writer.create_run_dir(root, run_id)
    context_builder.build_shared_context(root, run_dir, repo, pipeline)
    write_json(run_dir / "task_graph.json", task_graph.serialize_task_graph(pipeline))

    task_records: list[dict[str, Any]] = []
    for task in task_graph.topological_tasks(pipeline):
        task_dir = ensure_dir(run_dir / "tasks" / task.id)
        contract = task.to_contract(goal)
        write_json(task_dir / "task_contract.json", contract)

        if task_blocked(task, task_records):
            result = {
                "task_id": task.id,
                "agent": task.agent,
                "status": "blocked",
                "summary": "Dependency failed or was blocked.",
                "changed_files": [],
            }
            write_json(task_dir / "task_result.json", result)
            task_records.append({"id": task.id, "agent": task.agent, "status": "blocked", "gates": []})
            continue

        bundle = context_builder.build_task_bundle(root, run_dir, pipeline, task)
        execution = agent_executor.execute(root, run_dir, pipeline, task, bundle, args.executor)
        patch_result = patch_guard.check_patch_text(task, execution["patch_text"])
        write_json(task_dir / "patch_guard.json", patch_result)

        gates = gate_runner.run_gates(task.gates, root, task_dir / "logs", args.run_gates)
        gate_failed = any(gate["status"] == "fail" for gate in gates)
        status = execution["result"]["status"]
        if patch_result["status"] == "fail" or gate_failed:
            status = "fail"

        result = dict(execution["result"])
        result["status"] = status
        result["commands_run"] = [gate["command"] for gate in gates if gate["status"] != "skipped"]
        result["gate_results"] = gates
        result["patch_guard"] = patch_result
        write_json(task_dir / "task_result.json", result)
        task_records.append({"id": task.id, "agent": task.agent, "status": status, "gates": gates})
        if status == "fail":
            break

    visual = visual_indexer.collect(root, run_dir)
    defects = defect_indexer.collect(root)
    human_verdict = None if args.human_verdict == "pending" else args.human_verdict
    manifest = report_writer.write_final_manifest(
        root=root,
        run_dir=run_dir,
        pipeline=pipeline,
        run_id=run_id,
        goal=goal,
        repo=repo,
        task_records=task_records,
        visual=visual,
        defects=defects,
        human_verdict=human_verdict,
    )
    report_writer.write_final_summary(run_dir, manifest)
    print(f"GenOps run {run_id}: {manifest['status']}")
    print(f"Manifest: {run_dir.relative_to(root) / 'run_manifest.json'}")
    return 1 if manifest["status"] in {"failed", "rejected_by_human"} else 0


if __name__ == "__main__":
    raise SystemExit(main())

