"""Load defect/rubric indexes and write deterministic aesthetic reviews."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from tools.genops.artifact_writer import ensure_dir, write_json
from tools.genops.models import PipelineSpec, TaskSpec
from tools.genops.pipeline_loader import load_mapping


def _rel(root: Path, path: Path) -> str:
    try:
        return str(path.relative_to(root))
    except ValueError:
        return str(path)


def collect(root: Path) -> dict[str, Any]:
    defect_path = root / "genops" / "defects" / "defect_dictionary.yaml"
    rubric_dir = root / "genops" / "rubrics"
    style_dir = root / "genops" / "style_bibles"
    data: dict[str, Any] = {
        "defect_dictionary": str(defect_path) if defect_path.exists() else None,
        "defect_count": 0,
        "rubrics": [],
        "style_bibles": [],
    }
    if defect_path.exists():
        defects = load_mapping(defect_path).get("defects", {})
        data["defect_count"] = len(defects) if isinstance(defects, dict) else 0
    if rubric_dir.exists():
        data["rubrics"] = sorted(path.name for path in rubric_dir.glob("*.yaml"))
    if style_dir.exists():
        data["style_bibles"] = sorted(path.name for path in style_dir.glob("*.yaml"))
    return data


def _load_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return payload if isinstance(payload, dict) else {}


def _load_rubrics(root: Path, task: TaskSpec) -> list[dict[str, Any]]:
    rubrics: list[dict[str, Any]] = []
    for item in task.inputs:
        path = root / item
        if path.name.endswith(".rubric.yaml") and path.exists():
            rubric = load_mapping(path)
            rubric["_path"] = _rel(root, path)
            rubrics.append(rubric)
    return rubrics


def _load_defects(root: Path) -> dict[str, Any]:
    path = root / "genops" / "defects" / "defect_dictionary.yaml"
    if not path.exists():
        return {}
    defects = load_mapping(path).get("defects", {})
    return defects if isinstance(defects, dict) else {}


def _review_output_path(root: Path, run_dir: Path, task: TaskSpec) -> Path:
    run_id = run_dir.name
    for output in task.outputs:
        value = str(output).replace("${run_id}", run_id)
        if value.endswith(".json") and ("review" in Path(value).name):
            return root / value if value.startswith(("reports/", "out/")) else run_dir / value
    return run_dir / "visual" / "reviews" / f"{task.id}.json"


def _visual_report(root: Path, task: TaskSpec) -> tuple[Path, dict[str, Any]]:
    for item in task.inputs:
        path = root / item
        if path.name == "visual_acceptance_report.json":
            return path, _load_json(path)
    path = root / "reports" / "visual_acceptance_report.json"
    return path, _load_json(path)


def _report_failed(report: dict[str, Any]) -> bool:
    if not report:
        return True
    if str(report.get("status") or "").lower() == "failed":
        return True
    for sample in report.get("structure_samples", []) or []:
        if isinstance(sample, dict) and sample.get("status") == "failed":
            return True
    for group in report.get("plan_previews", []) or []:
        if isinstance(group, dict) and group.get("status") == "failed":
            return True
    return False


def _candidate_id(pipeline: PipelineSpec, task: TaskSpec, report: dict[str, Any], rubrics: list[dict[str, Any]]) -> str:
    if task.raw.get("candidate"):
        return str(task.raw["candidate"])
    for sample in report.get("structure_samples", []) or []:
        if isinstance(sample, dict) and sample.get("status") == "failed":
            return str(sample.get("stem") or pipeline.id)
    if len(rubrics) > 1:
        return f"{pipeline.id.replace('.full', '')}_review"
    if rubrics:
        return f"{rubrics[0].get('id', pipeline.id)}_review"
    return pipeline.id.replace(".full", "")


def _select_defects(rubrics: list[dict[str, Any]], defects: dict[str, Any], failed: bool) -> list[str]:
    if not failed:
        return []
    selected: list[str] = []
    for rubric in rubrics:
        rubric_id = str(rubric.get("id") or "")
        preferred = "terrace_parking_lot" if rubric_id == "sect" else ""
        if preferred and preferred in defects:
            selected.append(preferred)
            continue
        for defect_id in rubric.get("blocking_defects", []) or []:
            if defect_id in defects:
                selected.append(str(defect_id))
                break
    return sorted(dict.fromkeys(selected))


def _scores(rubrics: list[dict[str, Any]], blocking_defects: list[str]) -> dict[str, int]:
    scores: dict[str, int] = {}
    blocked = bool(blocking_defects)
    for rubric in rubrics:
        rubric_id = str(rubric.get("id") or "rubric")
        dimensions = rubric.get("dimensions", {})
        if not isinstance(dimensions, dict):
            continue
        for name in dimensions:
            key = str(name)
            if key in scores:
                key = f"{rubric_id}.{key}"
            scores[key] = 2 if blocked else 3
    return scores


def _fix_rules(blocking_defects: list[str], defects: dict[str, Any]) -> list[str]:
    rules: list[str] = []
    for defect_id in blocking_defects:
        defect = defects.get(defect_id, {})
        if isinstance(defect, dict):
            rules.extend(str(item) for item in defect.get("fix_patterns", []) or [])
    return sorted(dict.fromkeys(rules))


def build_aesthetic_review(root: Path, run_dir: Path, pipeline: PipelineSpec, task: TaskSpec) -> dict[str, Any]:
    rubrics = _load_rubrics(root, task)
    defects = _load_defects(root)
    report_path, report = _visual_report(root, task)
    failed = _report_failed(report)
    blocking_defects = _select_defects(rubrics, defects, failed)
    return {
        "candidate": _candidate_id(pipeline, task, report, rubrics),
        "scores": _scores(rubrics, blocking_defects),
        "blocking_defects": blocking_defects,
        "fix_rules": _fix_rules(blocking_defects, defects),
        "human_verdict_state": "pending",
        "rubrics": [str(rubric.get("_path") or rubric.get("id") or "") for rubric in rubrics],
        "source_visual_report": {
            "path": _rel(root, report_path),
            "exists": report_path.exists(),
            "status": str(report.get("status") or "missing"),
        },
    }


def _merge_artifact_list(payload: dict[str, Any], path: str) -> None:
    artifacts = payload.get("artifacts")
    if not isinstance(artifacts, list):
        artifacts = []
    if path not in artifacts:
        artifacts.append(path)
    payload["artifacts"] = artifacts


def write_aesthetic_reviews(root: Path, run_dir: Path, pipeline: PipelineSpec) -> list[str]:
    written: list[str] = []
    for task in pipeline.tasks:
        if task.id != "aesthetic-review" and task.agent != "aesthetic-critic":
            continue
        task_dir = run_dir / "tasks" / task.id
        task_result_path = task_dir / "task_result.json"
        task_result = _load_json(task_result_path)
        if not task_result or task_result.get("status") in {"blocked", "fail"}:
            continue
        output_path = _review_output_path(root, run_dir, task)
        review = build_aesthetic_review(root, run_dir, pipeline, task)
        write_json(output_path, review)
        rel_path = _rel(root, output_path)
        written.append(rel_path)

        ensure_dir(task_dir)
        _merge_artifact_list(task_result, rel_path)
        task_result["aesthetic_review"] = rel_path
        task_result["human_verdict_state"] = review["human_verdict_state"]
        write_json(task_result_path, task_result)

        evidence_path = task_dir / "evidence.json"
        evidence = _load_json(evidence_path)
        evidence["aesthetic_review"] = rel_path
        evidence["rubrics"] = review["rubrics"]
        evidence["source_visual_report"] = review["source_visual_report"]
        write_json(evidence_path, evidence)

    return written
