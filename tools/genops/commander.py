#!/usr/bin/env python3
"""Map a natural-language owner goal to a GenOps pipeline recommendation.

This helper is for the Commander Agent, not a required owner-facing interface.
The agent may use it to keep routing deterministic, then run the selected
pipeline itself.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tools.genops.pipeline_loader import load_mapping


ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "genops" / "commander.yaml"


def score_route(goal: str, route: dict[str, Any]) -> int:
    lowered = goal.lower()
    return sum(1 for cue in route.get("cues", []) if str(cue).lower() in lowered)


def classify_mode(goal: str, defaults: dict[str, str]) -> str:
    text = goal.lower()
    if any(cue in text for cue in ("验收", "回归", "build", "构建", "发布前")):
        return defaults.get("final_acceptance", "run_gates_then_handoff")
    if any(cue in text for cue in ("bug", "失败", "报错", "修复", "不通过")):
        return defaults.get("mechanical_bug_or_failing_gate", "implementation_first")
    if any(cue in text for cue in ("视觉", "审美", "好看", "难看", "剪影", "花园", "预览")):
        return defaults.get("visual_or_aesthetic_goal", "alignment_first")
    return defaults.get("unclear_scope", "planning_first")


def is_craft_required(goal: str) -> bool:
    text = goal.lower()
    cues = (
        "craft",
        "genops",
        "openspec",
        "proposal",
        "change",
        "apply",
        "subagent",
        "parallel",
        "jar",
        "item",
        "mod item",
        "build",
        "release",
        "changelog",
        "acceptance",
        "visual review",
        "提案",
        "变更",
        "立项",
        "实现",
        "视觉",
        "审美",
        "预览",
        "验收",
        "发布",
        "版本",
        "构建",
        "物品",
        "创造栏",
        "贴图",
        "模型",
        "配方",
        "子代理",
        "并行",
    )
    return any(cue in text for cue in cues)


def recommend(goal: str, config_path: Path = CONFIG) -> dict[str, Any]:
    config = load_mapping(config_path)
    routes = config.get("intent_routing", {})
    scored = sorted(
        (
            (score_route(goal, route), name, route.get("pipeline"))
            for name, route in routes.items()
        ),
        key=lambda item: (-item[0], item[1]),
    )
    best_score, intent, pipeline = scored[0] if scored else (0, "unknown", None)
    if best_score == 0:
        intent = "compound"
        pipeline = "genops/pipelines/compound-library.full.yaml"
    return {
        "goal": goal,
        "intent": intent,
        "pipeline": pipeline,
        "mode": classify_mode(goal, config.get("default_modes", {})),
        "craft_required": is_craft_required(goal),
        "frontdoor_summary_fields": config.get("craft_required_summary_fields", []),
        "audit_fields": config.get("craft_required_audit_fields", []),
        "owner_decision_interface": config.get("owner_decision_interface", {}),
        "visibility_policy": config.get("visibility_policy", {}),
        "subagent_execution_policy": config.get("subagent_execution_policy", {}),
        "auto_progression": config.get("auto_progression", {}),
        "human_verdict_policy": config.get("human_verdict_policy", {}),
        "archive_policy": config.get("archive_policy", {}),
        "owner_interface": "natural_language_conversation",
        "commander_note": "Owner decides need, depth, direction, and verdicts; Commander owns backend routing, evidence, and closeout.",
    }


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("goal", help="Natural-language owner goal")
    parser.add_argument("--config", type=Path, default=CONFIG)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    print(json.dumps(recommend(args.goal, args.config), indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
