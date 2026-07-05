#!/usr/bin/env python3
"""Write a visual acceptance handoff report.

This report is intentionally a handoff and inspection checklist, not an image
classifier. It verifies that the offline preview entry point and representative
preview assets exist, then links those assets with the latest Chunky acceptance
coordinates so the reviewer and agent have one concrete visual review surface.
"""

from __future__ import annotations

import argparse
import glob
import json
import struct
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PREVIEW_ROOT = ROOT / "out" / "preview"
DEFAULT_CHUNKY_REPORT = ROOT / "reports" / "chunky_acceptance_report.json"
DEFAULT_OUT_JSON = ROOT / "reports" / "visual_acceptance_report.json"
DEFAULT_OUT_MD = ROOT / "reports" / "visual_acceptance_report.md"

STRUCTURE_SAMPLES = [
    ("small_house_001", "baseline medieval house"),
    ("chinese_courtyard_001", "one-entry courtyard compound"),
    ("chinese_mansion_001", "Jiangnan mansion compound"),
    ("chinese_huipai_mansion_001", "Hui-style Tianjing reference slice"),
    ("cultivation_town_001", "static cultivation town block"),
    ("cultivation_sect_001", "terraced sect compound template"),
    ("hero_rockery", "standalone rockery review specimen"),
    ("pagoda_001", "vertical landmark"),
    ("pavilion_001", "open civic/cultivation pavilion form"),
    ("town_shrine_001", "cultivation civic shrine"),
]

STRUCTURE_REQUIRED_FILES = [
    "isometric.png",
    "slices_contact.png",
    "viewer.html",
    "legend.txt",
]

PLAN_PREVIEW_GROUPS = [
    ("town_plan", "town_plan_s*", ["plan.png", "viewer.html", "plan.json"]),
    ("sect_plan", "sect_plan_s*", ["plan.png", "mountain.png", "viewer.html", "plan.json"]),
    ("region_topology", "region_topology_s*", ["layout.svg", "viewer.html", "graph.json"]),
]


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def png_dimensions(path: Path) -> dict[str, Any]:
    try:
        with path.open("rb") as handle:
            header = handle.read(24)
    except OSError as exc:
        return {"ok": False, "error": str(exc)}
    if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
        return {"ok": False, "error": "not_png"}
    width, height = struct.unpack(">II", header[16:24])
    return {"ok": True, "width": width, "height": height}


def file_record(path: Path) -> dict[str, Any]:
    record: dict[str, Any] = {
        "path": rel(path),
        "exists": path.exists(),
    }
    if not path.exists():
        record["status"] = "missing"
        return record
    record["size_bytes"] = path.stat().st_size
    record["status"] = "present" if record["size_bytes"] > 0 else "empty"
    if path.suffix.lower() == ".png":
        record["png"] = png_dimensions(path)
        if not record["png"].get("ok"):
            record["status"] = "invalid_png"
    return record


def collect_structure_samples(preview_root: Path) -> list[dict[str, Any]]:
    samples: list[dict[str, Any]] = []
    for stem, reason in STRUCTURE_SAMPLES:
        out_dir = preview_root / stem
        files = [file_record(out_dir / filename) for filename in STRUCTURE_REQUIRED_FILES]
        status = "passed" if out_dir.is_dir() and all(item["status"] == "present" for item in files) else "failed"
        samples.append(
            {
                "stem": stem,
                "reason": reason,
                "out_dir": rel(out_dir),
                "status": status,
                "files": files,
            }
        )
    return samples


def collect_plan_previews(preview_root: Path) -> list[dict[str, Any]]:
    groups: list[dict[str, Any]] = []
    for group_name, pattern, required_files in PLAN_PREVIEW_GROUPS:
        dirs = sorted(Path(path) for path in glob.glob(str(preview_root / pattern)) if Path(path).is_dir())
        previews: list[dict[str, Any]] = []
        for directory in dirs:
            files = [file_record(directory / filename) for filename in required_files]
            previews.append(
                {
                    "name": directory.name,
                    "out_dir": rel(directory),
                    "status": "passed" if all(item["status"] == "present" for item in files) else "failed",
                    "files": files,
                }
            )
        groups.append(
            {
                "group": group_name,
                "pattern": pattern,
                "count": len(previews),
                "status": "passed" if previews and all(item["status"] == "passed" for item in previews) else "failed",
                "previews": previews,
            }
        )
    return groups


def load_chunky_summary(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {
            "status": "missing",
            "path": rel(path),
            "manual_note": "Run tools/run_chunky_acceptance.py before in-game visual review if Chunky coverage is required.",
        }
    try:
        report = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return {"status": "invalid", "path": rel(path), "error": str(exc)}

    command_targets: list[dict[str, Any]] = []
    for stage in report.get("stages", []):
        for command in stage.get("commands", []):
            command_text = str(command.get("command", ""))
            if command_text.startswith("myvillage ") and command_text != "myvillage list":
                command_targets.append(
                    {
                        "stage": stage.get("id"),
                        "stage_name": stage.get("name"),
                        "command": command_text,
                        "response": command.get("response", ""),
                    }
                )

    stage_statuses = [
        {"stage": stage.get("id"), "name": stage.get("name"), "status": stage.get("status")}
        for stage in report.get("stages", [])
    ]
    natural_sect = None
    for stage in report.get("stages", []):
        if stage.get("id") == 4:
            natural_sect = {
                "locate": stage.get("locate"),
                "chunky_region": stage.get("chunky_region"),
                "chunky_completion": stage.get("chunky_completion"),
            }
            break

    return {
        "status": report.get("status", "unknown"),
        "path": rel(path),
        "stage_statuses": stage_statuses,
        "full_modset": report.get("full_modset", {}),
        "command_targets": command_targets,
        "natural_sect": natural_sect,
        "log_summary": report.get("log_summary", {}),
    }


def report_status(report: dict[str, Any]) -> str:
    failures: list[str] = []
    if report["preview_index"]["status"] != "present":
        failures.append("preview_index")
    failures.extend(sample["stem"] for sample in report["structure_samples"] if sample["status"] != "passed")
    failures.extend(group["group"] for group in report["plan_previews"] if group["status"] != "passed")
    return "failed" if failures else "passed"


def write_markdown(report: dict[str, Any], path: Path) -> None:
    def markdown_response(value: Any, limit: int = 500) -> str:
        response = str(value).strip().replace("\n", "<br>")
        if len(response) <= limit:
            return response
        return response[: limit - 3] + "..."

    lines = [
        "# Visual Acceptance Report",
        "",
        f"- Status: `{report['status']}`",
        f"- Generated: `{report['generated_at']}`",
        f"- Preview index: `{report['preview_index']['path']}` (`{report['preview_index']['status']}`)",
        f"- Chunky report: `{report['chunky_acceptance']['path']}` (`{report['chunky_acceptance']['status']}`)",
        "",
        "## Agent Inspection Targets",
        "",
        "The agent must open representative PNGs before claiming visual review was performed.",
        "This report verifies the files and records the review targets; it does not judge aesthetics.",
        "",
        "| Sample | Reason | Isometric | Contact Sheet | Viewer | Status |",
        "|---|---|---|---|---|---|",
    ]
    for sample in report["structure_samples"]:
        by_name = {Path(item["path"]).name: item for item in sample["files"]}
        lines.append(
            "| {stem} | {reason} | `{iso}` | `{contact}` | `{viewer}` | `{status}` |".format(
                stem=sample["stem"],
                reason=sample["reason"],
                iso=by_name.get("isometric.png", {}).get("path", ""),
                contact=by_name.get("slices_contact.png", {}).get("path", ""),
                viewer=by_name.get("viewer.html", {}).get("path", ""),
                status=sample["status"],
            )
        )

    lines.extend(["", "## Plan Preview Groups", "", "| Group | Count | Status |", "|---|---:|---|"])
    for group in report["plan_previews"]:
        lines.append(f"| {group['group']} | {group['count']} | `{group['status']}` |")

    lines.extend(["", "## In-Game Visual Targets From Chunky", ""])
    chunky = report["chunky_acceptance"]
    if chunky.get("command_targets"):
        lines.extend(["| Stage | Command | Response |", "|---:|---|---|"])
        for target in chunky["command_targets"]:
            response = markdown_response(target.get("response", ""))
            lines.append(f"| {target.get('stage')} | `{target.get('command')}` | {response} |")
    else:
        lines.append("No MyVillage command targets were available from the Chunky report.")

    if chunky.get("natural_sect"):
        lines.extend(["", "Natural sect worldgen target:", ""])
        lines.append(f"- Locate: `{chunky['natural_sect'].get('locate')}`")
        lines.append(f"- Chunky region: `{chunky['natural_sect'].get('chunky_region')}`")
        lines.append(f"- Completion: `{chunky['natural_sect'].get('chunky_completion')}`")

    lines.extend(
        [
            "",
            "## Manual Review Boundary",
            "",
            "- Offline previews cover massing, roof silhouettes, layer plans, coarse material palette, and plaque texture overlays.",
            "- Chunky coverage proves server-side placement/worldgen/chunk generation completed at concrete coordinates.",
            "- Final appearance-sensitive acceptance still needs human inspection in the preview viewer or Minecraft client.",
            "",
        ]
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def build_report(args: argparse.Namespace) -> dict[str, Any]:
    preview_root = Path(args.preview_root)
    chunky_report = Path(args.chunky_report)
    report: dict[str, Any] = {
        "workflow": "visual_acceptance",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "preview_root": rel(preview_root),
        "preview_index": file_record(preview_root / "index.html"),
        "structure_samples": collect_structure_samples(preview_root),
        "plan_previews": collect_plan_previews(preview_root),
        "chunky_acceptance": load_chunky_summary(chunky_report),
        "manual_review_required": True,
        "manual_review_boundary": [
            "Offline previews are not a full Minecraft renderer.",
            "Chunky generation is not a screenshot or visual classifier.",
            "The agent must inspect representative images before summarizing visual status.",
            "A reviewer still owns final in-game appearance acceptance.",
        ],
    }
    report["status"] = report_status(report)
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview-root", default=str(DEFAULT_PREVIEW_ROOT))
    parser.add_argument("--chunky-report", default=str(DEFAULT_CHUNKY_REPORT))
    parser.add_argument("--out-json", default=str(DEFAULT_OUT_JSON))
    parser.add_argument("--out-md", default=str(DEFAULT_OUT_MD))
    args = parser.parse_args()

    report = build_report(args)
    out_json = Path(args.out_json)
    out_md = Path(args.out_md)
    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    write_markdown(report, out_md)

    print(f"visual acceptance report: {rel(out_json)}")
    print(f"visual acceptance markdown: {rel(out_md)}")
    print(f"status: {report['status']}")
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
