"""Collect visual artifacts referenced by a GenOps run."""

from __future__ import annotations

from pathlib import Path
from typing import Any


def _rel(root: Path, path: Path) -> str:
    try:
        return str(path.relative_to(root))
    except ValueError:
        return str(path)


def collect(root: Path, run_dir: Path) -> dict[str, Any]:
    preview_index = root / "out" / "preview" / "index.html"
    visual_report_md = root / "reports" / "visual_acceptance_report.md"
    visual_report_json = root / "reports" / "visual_acceptance_report.json"
    reviews = sorted((run_dir / "visual" / "reviews").glob("*.json")) if (run_dir / "visual" / "reviews").exists() else []
    verdicts = (
        sorted((run_dir / "visual" / "human_verdicts").glob("*.json"))
        if (run_dir / "visual" / "human_verdicts").exists()
        else []
    )
    return {
        "preview_index": {"path": _rel(root, preview_index), "exists": preview_index.exists()},
        "visual_acceptance_report_md": {"path": _rel(root, visual_report_md), "exists": visual_report_md.exists()},
        "visual_acceptance_report_json": {"path": _rel(root, visual_report_json), "exists": visual_report_json.exists()},
        "reviews": [_rel(root, path) for path in reviews],
        "human_verdicts": [_rel(root, path) for path in verdicts],
    }

