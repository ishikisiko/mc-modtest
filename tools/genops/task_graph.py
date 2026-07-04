"""Task graph helpers for GenOps pipelines."""

from __future__ import annotations

from collections import defaultdict, deque
from typing import Any

from tools.genops.models import PipelineSpec, TaskSpec


def topological_tasks(pipeline: PipelineSpec) -> list[TaskSpec]:
    by_id = {task.id: task for task in pipeline.tasks}
    incoming = {task.id: set(task.depends_on) for task in pipeline.tasks}
    outgoing: dict[str, list[str]] = defaultdict(list)
    for task in pipeline.tasks:
        for dep in task.depends_on:
            outgoing[dep].append(task.id)

    ready = deque(task.id for task in pipeline.tasks if not incoming[task.id])
    ordered: list[TaskSpec] = []
    while ready:
        task_id = ready.popleft()
        ordered.append(by_id[task_id])
        for child in outgoing.get(task_id, []):
            incoming[child].discard(task_id)
            if not incoming[child]:
                ready.append(child)

    if len(ordered) != len(pipeline.tasks):
        blocked = ", ".join(sorted(task_id for task_id, deps in incoming.items() if deps))
        raise ValueError(f"pipeline has a dependency cycle or blocked tasks: {blocked}")
    return ordered


def serialize_task_graph(pipeline: PipelineSpec) -> dict[str, Any]:
    return {
        "pipeline": pipeline.id,
        "tasks": [
            {
                "id": task.id,
                "agent": task.agent,
                "depends_on": task.depends_on,
                "allowed_files": task.allowed_files,
                "forbidden_files": task.forbidden_files,
                "gates": task.gates,
            }
            for task in pipeline.tasks
        ],
    }

