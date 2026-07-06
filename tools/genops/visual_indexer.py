"""Collect visual artifacts referenced by a GenOps run."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


VERDICTS = {
    "pending",
    "accept",
    "reject",
    "accept_with_changes",
    "not_required",
    "pause",
    "reopen_discussion",
}

VERDICT_ALIASES = {
    "accepted": "accept",
    "rejected": "reject",
    "not_required_nonvisual_auto_archive": "not_required",
}


def _rel(root: Path, path: Path) -> str:
    try:
        return str(path.relative_to(root))
    except ValueError:
        return str(path)


def _normalize_verdict(value: Any) -> str:
    if value is None:
        return "pending"
    normalized = VERDICT_ALIASES.get(str(value), str(value))
    return normalized if normalized in VERDICTS else "pending"


def _read_verdict(root: Path, path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # pragma: no cover - defensive for malformed handoff artifacts
        return {"path": _rel(root, path), "verdict": "pending", "error": str(exc)}
    if not isinstance(payload, dict):
        return {"path": _rel(root, path), "verdict": "pending"}
    verdict = _normalize_verdict(payload.get("verdict") or payload.get("human_verdict") or payload.get("kind"))
    return {
        "path": _rel(root, path),
        "verdict": verdict,
        "summary": str(payload.get("summary") or payload.get("notes") or ""),
    }


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
    verdict_records = [_read_verdict(root, path) for path in verdicts]
    latest_verdict = verdict_records[-1]["verdict"] if verdict_records else "pending"
    return {
        "preview_index": {"path": _rel(root, preview_index), "exists": preview_index.exists()},
        "visual_acceptance_report_md": {"path": _rel(root, visual_report_md), "exists": visual_report_md.exists()},
        "visual_acceptance_report_json": {"path": _rel(root, visual_report_json), "exists": visual_report_json.exists()},
        "reviews": [_rel(root, path) for path in reviews],
        "human_verdicts": verdict_records,
        "latest_human_verdict": latest_verdict,
    }
