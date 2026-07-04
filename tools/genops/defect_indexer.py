"""Load defect/rubric indexes for reports."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from tools.genops.pipeline_loader import load_mapping


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

