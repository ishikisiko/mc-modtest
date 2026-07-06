#!/usr/bin/env python3
"""Compile-time governance checks for GenOps pipeline YAML."""

from __future__ import annotations

import argparse
import fnmatch
import json
import sys
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tools.genops import pipeline_loader
from tools.genops.models import PipelineSpec, TaskSpec


ROOT = Path(__file__).resolve().parents[2]
PIPELINE_DIR = "genops/pipelines"

TASK_RESULT_OUTPUT = "task_result.json"
EVIDENCE_OUTPUT = "evidence.json"
PATCH_OUTPUT = "patch.diff"

RELEASE_PATTERNS = [
    "gradle.properties",
    "src/main/resources/META-INF/neoforge.mods.toml",
    "CHANGELOG.md",
    "openspec/config.yaml",
]

OPENSPEC_FORBIDDEN_ALLOWED_PATTERNS = [
    "src/main/**",
    "src/main/java/**",
    "src/main/resources/**",
    "src/main/resources/data/myvillage/structure/*.nbt",
    "gradle.properties",
    "src/main/resources/META-INF/neoforge.mods.toml",
    "CHANGELOG.md",
    "build.gradle",
]

GATE_REQUIRED_TASK_IDS = {
    "patch-validators",
    "preview-structures",
    "visual-evidence",
    "write-report",
    "regression",
}

VISUAL_AGENTS = {"visual-reviewer", "aesthetic-critic"}


@dataclass(frozen=True)
class Finding:
    pipeline: str
    rule: str
    message: str
    task_id: str | None = None

    def as_dict(self) -> dict[str, str]:
        data = {
            "pipeline": self.pipeline,
            "rule": self.rule,
            "message": self.message,
        }
        if self.task_id:
            data["task_id"] = self.task_id
        return data


def matches(path: str, pattern: str) -> bool:
    normalized = path.replace("\\", "/")
    pat = pattern.replace("\\", "/")
    return fnmatch.fnmatch(normalized, pat) or PurePosixPath(normalized).match(pat)


def any_match(path: str, patterns: list[str]) -> bool:
    return any(matches(path, pattern) for pattern in patterns)


def overlaps(path_or_pattern: str, protected_patterns: list[str]) -> bool:
    return any(matches(path_or_pattern, pattern) or matches(pattern, path_or_pattern) for pattern in protected_patterns)


def known_roles(root: Path) -> set[str]:
    mapping_path = root / "genops" / "subagents.yaml"
    mapping = pipeline_loader.load_mapping(mapping_path).get("custom_agents", {})
    return set(mapping) if isinstance(mapping, dict) else set()


def is_write_task(task: TaskSpec) -> bool:
    if PATCH_OUTPUT in task.outputs:
        return True
    return task.id.startswith("patch-") or task.id in {"docs-sync", "release-docs", "write-openspec-artifacts"}


def human_review_config(pipeline: PipelineSpec) -> dict[str, Any]:
    value = pipeline.raw.get("human_review", {})
    return value if isinstance(value, dict) else {"required": bool(value)}


def human_review_required(pipeline: PipelineSpec) -> bool:
    return bool(human_review_config(pipeline).get("required", False))


def is_visual_pipeline(pipeline: PipelineSpec) -> bool:
    text = " ".join(
        [
            pipeline.id,
            pipeline.kind,
            pipeline.goal_summary,
            json.dumps(pipeline.raw.get("constraints", {}), ensure_ascii=False),
        ]
    ).lower()
    if any(cue in text for cue in ("visual", "aesthetic", "视觉", "审美")):
        return True
    return any(task.agent in VISUAL_AGENTS for task in pipeline.tasks)


def check_pipeline(pipeline: PipelineSpec, roles: set[str]) -> list[Finding]:
    findings: list[Finding] = []
    review = human_review_config(pipeline)
    is_openspec = pipeline.id.startswith("openspec-") or bool(
        pipeline.raw.get("constraints", {}).get("blocks_runtime_and_release_edits")
    )

    if human_review_required(pipeline) and not review.get("artifact_index"):
        findings.append(
            Finding(
                pipeline=pipeline.id,
                rule="human_review_missing_artifact_index",
                message="human_review.required pipelines must declare owner review artifacts",
            )
        )

    if is_visual_pipeline(pipeline) and not human_review_required(pipeline):
        findings.append(
            Finding(
                pipeline=pipeline.id,
                rule="visual_pipeline_missing_human_review",
                message="visual or aesthetic pipelines must keep a pending human review gate",
            )
        )

    for task in pipeline.tasks:
        if task.agent not in roles:
            findings.append(
                Finding(
                    pipeline=pipeline.id,
                    task_id=task.id,
                    rule="unknown_role",
                    message=f"task uses unknown agent role: {task.agent}",
                )
            )

        missing_outputs = [item for item in (TASK_RESULT_OUTPUT, EVIDENCE_OUTPUT) if item not in task.outputs]
        if missing_outputs:
            findings.append(
                Finding(
                    pipeline=pipeline.id,
                    task_id=task.id,
                    rule="task_outputs_missing_contract",
                    message=f"task outputs must include: {', '.join(missing_outputs)}",
                )
            )

        if is_write_task(task) and not task.allowed_files:
            findings.append(
                Finding(
                    pipeline=pipeline.id,
                    task_id=task.id,
                    rule="write_task_missing_allowed_files",
                    message="write/patch tasks must declare allowed_files",
                )
            )

        if task.id in GATE_REQUIRED_TASK_IDS and not task.gates:
            findings.append(
                Finding(
                    pipeline=pipeline.id,
                    task_id=task.id,
                    rule="gate_command_missing",
                    message="this task id is required to declare executable gate commands",
                )
            )

        if is_openspec:
            for allowed in task.allowed_files:
                if overlaps(allowed, OPENSPEC_FORBIDDEN_ALLOWED_PATTERNS):
                    findings.append(
                        Finding(
                            pipeline=pipeline.id,
                            task_id=task.id,
                            rule="openspec_pipeline_allows_runtime_or_release",
                            message=f"OpenSpec governance pipeline must not allow runtime/release/generated path: {allowed}",
                        )
                    )

        for allowed in task.allowed_files:
            if overlaps(allowed, RELEASE_PATTERNS) and task.agent != "release-steward":
                findings.append(
                    Finding(
                        pipeline=pipeline.id,
                        task_id=task.id,
                        rule="release_files_not_owned_by_release_steward",
                        message=f"release-sensitive path is allowed by {task.agent}: {allowed}",
                    )
                )

        if task.id == "aesthetic-review":
            has_review_output = any(str(output).endswith("_review.json") or str(output).endswith("review.json") for output in task.outputs)
            if not has_review_output:
                findings.append(
                    Finding(
                        pipeline=pipeline.id,
                        task_id=task.id,
                        rule="aesthetic_review_missing_review_json",
                        message="aesthetic-review tasks must declare a review.json output",
                    )
                )

    return findings


def validate(root: Path) -> list[Finding]:
    roles = known_roles(root)
    findings: list[Finding] = []
    for path in sorted((root / PIPELINE_DIR).glob("*.yaml")):
        pipeline = pipeline_loader.load_pipeline(path)
        findings.extend(check_pipeline(pipeline, roles))
    return findings


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--json", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    root = args.root.resolve()
    findings = validate(root)
    if args.json:
        print(json.dumps([finding.as_dict() for finding in findings], indent=2, ensure_ascii=False))
    elif findings:
        for finding in findings:
            task = f":{finding.task_id}" if finding.task_id else ""
            print(f"{finding.pipeline}{task}: {finding.rule}: {finding.message}")
    else:
        print("pipeline governance validation: pass")
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
