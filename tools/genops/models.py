"""Typed accessors for GenOps pipeline data."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


def as_list(value: Any) -> list[Any]:
    if value is None:
        return []
    if isinstance(value, list):
        return value
    return [value]


@dataclass(frozen=True)
class TaskSpec:
    id: str
    agent: str
    depends_on: list[str] = field(default_factory=list)
    allowed_files: list[str] = field(default_factory=list)
    forbidden_files: list[str] = field(default_factory=list)
    inputs: list[str] = field(default_factory=list)
    outputs: list[str] = field(default_factory=list)
    gates: list[str] = field(default_factory=list)
    generated_outputs_allowed: bool = False
    raw: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "TaskSpec":
        return cls(
            id=str(data["id"]),
            agent=str(data["agent"]),
            depends_on=[str(item) for item in as_list(data.get("depends_on"))],
            allowed_files=[str(item) for item in as_list(data.get("allowed_files"))],
            forbidden_files=[str(item) for item in as_list(data.get("forbidden_files"))],
            inputs=[str(item) for item in as_list(data.get("inputs"))],
            outputs=[str(item) for item in as_list(data.get("outputs"))],
            gates=[str(item) for item in as_list(data.get("gates"))],
            generated_outputs_allowed=bool(data.get("generated_outputs_allowed", False)),
            raw=data,
        )

    def to_contract(self, pipeline_goal: str) -> dict[str, Any]:
        return {
            "task_id": self.id,
            "agent": self.agent,
            "goal": self.raw.get("goal", pipeline_goal),
            "depends_on": self.depends_on,
            "allowed_files": self.allowed_files,
            "forbidden_files": self.forbidden_files,
            "inputs": self.inputs,
            "required_outputs": self.outputs or ["patch.diff", "task_result.json", "evidence.json"],
            "generated_outputs_allowed": self.generated_outputs_allowed,
            "success_gates": self.gates,
        }


@dataclass(frozen=True)
class PipelineSpec:
    id: str
    kind: str
    version: int
    path: str
    goal_summary: str
    human_review_required: bool
    tasks: list[TaskSpec]
    raw: dict[str, Any]

    @classmethod
    def from_dict(cls, data: dict[str, Any], path: str) -> "PipelineSpec":
        goal = data.get("goal", {})
        if isinstance(goal, dict):
            goal_summary = str(goal.get("summary", ""))
        else:
            goal_summary = str(goal)
        human_review = data.get("human_review", {})
        return cls(
            id=str(data["id"]),
            kind=str(data.get("kind", "generator-pipeline")),
            version=int(data.get("version", 1)),
            path=path,
            goal_summary=goal_summary,
            human_review_required=bool(
                human_review.get("required", False) if isinstance(human_review, dict) else human_review
            ),
            tasks=[TaskSpec.from_dict(item) for item in data.get("tasks", [])],
            raw=data,
        )

