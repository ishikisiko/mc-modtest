"""Load and lightly validate GenOps pipeline YAML."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover - the repo dev image ships PyYAML.
    yaml = None

from tools.genops.models import PipelineSpec


def load_mapping(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        if yaml is None:
            raise RuntimeError("PyYAML is required to read non-JSON pipeline files")
        data = yaml.safe_load(text)
    if not isinstance(data, dict):
        raise ValueError(f"{path} must contain a mapping")
    return data


def load_pipeline(path: Path) -> PipelineSpec:
    data = load_mapping(path)
    for key in ("id", "tasks"):
        if key not in data:
            raise ValueError(f"{path} is missing required key: {key}")
    if not isinstance(data["tasks"], list) or not data["tasks"]:
        raise ValueError(f"{path} must declare at least one task")
    pipeline = PipelineSpec.from_dict(data, str(path))
    seen: set[str] = set()
    for task in pipeline.tasks:
        if task.id in seen:
            raise ValueError(f"{path} declares duplicate task id: {task.id}")
        seen.add(task.id)
    for task in pipeline.tasks:
        missing = [dep for dep in task.depends_on if dep not in seen]
        if missing:
            raise ValueError(f"{path}:{task.id} depends on unknown tasks: {', '.join(missing)}")
    return pipeline

