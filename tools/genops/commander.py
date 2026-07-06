#!/usr/bin/env python3
"""CRAFT Commander state machine and routing helper.

The owner still talks to the Commander in natural language. This module keeps
the deterministic backend surface small: classify a goal, start/index a run,
advance the current state, record verdicts, and summarize owner-facing state
while preserving audit evidence in the GenOps state store.
"""

from __future__ import annotations

import argparse
import contextlib
import io
import json
import sqlite3
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tools.genops import pipeline_loader, state_store
from tools.genops.pipeline_loader import load_mapping
from tools.genops.run_pipeline import default_run_id, main as run_pipeline_main


ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "genops" / "commander.yaml"
DEFAULT_DB = state_store.DEFAULT_DB

COMMANDS = {
    "classify",
    "start-run",
    "continue-current",
    "status",
    "next-decision",
    "record-verdict",
    "closeout",
    "summary",
}

STATE_SEQUENCE = [
    "intake",
    "planning",
    "ready_for_direction",
    "implementation",
    "validation",
    "human_review_pending",
    "accepted",
    "rejected",
    "accepted_with_changes",
    "closeout_ready",
    "archived",
]

VERDICT_TO_STATE = {
    "pending": "human_review_pending",
    "accept": "accepted",
    "reject": "rejected",
    "accept_with_changes": "accepted_with_changes",
    "not_required": "closeout_ready",
    "pause": "ready_for_direction",
    "reopen_discussion": "planning",
}

OWNER_SUMMARY_FIELDS = [
    "goal_status",
    "scope_or_direction",
    "validation_state",
    "risk_or_blocker",
    "human_decision_needed",
    "next_decision",
]

AUDIT_FIELDS = [
    "run_id",
    "pipeline",
    "task_id",
    "worker_ownership",
    "artifacts",
    "gates",
    "raw_logs",
    "manifest_path",
]


@dataclass(frozen=True)
class StopCondition:
    id: str
    message: str
    next_decision: str

    def as_dict(self) -> dict[str, str]:
        return {
            "id": self.id,
            "message": self.message,
            "next_decision": self.next_decision,
        }


@dataclass
class MachineContext:
    root: Path
    conn: sqlite3.Connection
    config_path: Path
    db: str


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


def build_context(root: Path, db: str, config_path: Path) -> MachineContext:
    resolved = root.resolve()
    conn = state_store.connect(resolved, db)
    state_store.init_schema(conn)
    return MachineContext(root=resolved, conn=conn, config_path=config_path, db=db)


def load_manifest(root: Path, run_id: str | None) -> dict[str, Any] | None:
    if not run_id:
        return None
    path = root / "reports" / "agent_runs" / run_id / "run_manifest.json"
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def current_intent(conn: sqlite3.Connection) -> dict[str, Any] | None:
    row = conn.execute(
        """
        SELECT * FROM intents
        WHERE status IN ('active', 'blocked')
        ORDER BY updated_at DESC
        LIMIT 1
        """
    ).fetchone()
    return dict(row) if row else None


def intent_by_id(conn: sqlite3.Connection, intent_id: str) -> dict[str, Any] | None:
    row = conn.execute("SELECT * FROM intents WHERE intent_id = ?", (intent_id,)).fetchone()
    return dict(row) if row else None


def select_intent(conn: sqlite3.Connection, intent_id: str | None) -> dict[str, Any] | None:
    return intent_by_id(conn, intent_id) if intent_id else current_intent(conn)


def decisions_for_intent(conn: sqlite3.Connection, intent_id: str) -> list[dict[str, Any]]:
    rows = conn.execute(
        """
        SELECT * FROM decisions
        WHERE intent_id = ?
        ORDER BY created_at ASC
        """,
        (intent_id,),
    ).fetchall()
    return [dict(row) for row in rows]


def has_decision(conn: sqlite3.Connection, intent_id: str, kinds: set[str]) -> bool:
    row = conn.execute(
        """
        SELECT 1 FROM decisions
        WHERE intent_id = ? AND kind IN ({})
        LIMIT 1
        """.format(",".join("?" for _ in kinds)),
        (intent_id, *sorted(kinds)),
    ).fetchone()
    return row is not None


def task_payloads(root: Path, run_id: str | None) -> list[dict[str, Any]]:
    if not run_id:
        return []
    tasks_dir = root / "reports" / "agent_runs" / run_id / "tasks"
    payloads: list[dict[str, Any]] = []
    if not tasks_dir.exists():
        return payloads
    for path in sorted(tasks_dir.glob("*/*.json")):
        if path.name not in {"task_result.json", "evidence.json", "patch_guard.json"}:
            continue
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        if isinstance(payload, dict):
            payloads.append(payload)
    return payloads


def manifest_gates(manifest: dict[str, Any] | None, payloads: list[dict[str, Any]]) -> list[dict[str, Any]]:
    gates: list[dict[str, Any]] = []
    if manifest:
        for task in manifest.get("tasks", []):
            if isinstance(task, dict):
                for gate in task.get("gates", []) or []:
                    if isinstance(gate, dict):
                        gates.append(gate)
    for payload in payloads:
        for key in ("gates", "gate_results"):
            value = payload.get(key, [])
            if isinstance(value, list):
                gates.extend(gate for gate in value if isinstance(gate, dict))
    return gates


def payload_strings(payloads: list[dict[str, Any]], keys: set[str]) -> list[str]:
    values: list[str] = []

    def walk(value: Any, active_key: str | None = None) -> None:
        if isinstance(value, dict):
            for key, item in value.items():
                walk(item, key)
        elif isinstance(value, list):
            for item in value:
                walk(item, active_key)
        elif active_key in keys and value not in (None, ""):
            values.append(str(value))

    for payload in payloads:
        walk(payload)
    return values


def manifest_status(manifest: dict[str, Any] | None) -> str:
    return str(manifest.get("status") or "missing") if manifest else "missing"


def manifest_verdict(manifest: dict[str, Any] | None) -> str:
    return str(manifest.get("human_verdict") or "pending") if manifest else "pending"


def infer_initial_state(manifest: dict[str, Any] | None, recommendation: dict[str, Any]) -> str:
    status = manifest_status(manifest)
    verdict = manifest_verdict(manifest)
    if status == "rejected_by_human" or verdict == "reject":
        return "rejected"
    if status == "planning_ready":
        return "ready_for_direction"
    if status in {"failed", "blocked", "manual_ready"}:
        return "validation" if status == "failed" else "implementation"
    if verdict == "accept":
        return "accepted"
    if verdict == "accept_with_changes":
        return "accepted_with_changes"
    if verdict == "not_required":
        return "closeout_ready"
    if verdict == "pause":
        return "ready_for_direction"
    if verdict == "reopen_discussion":
        return "planning"
    if status == "human_review_pending":
        return "human_review_pending"
    if recommendation.get("mode") in {"implementation_first", "run_gates_then_handoff"}:
        return "implementation"
    return "ready_for_direction"


def status_for_state(state: str, blockers: list[StopCondition] | None = None) -> str:
    if state == "archived":
        return "completed"
    if state == "rejected" or blockers:
        return "blocked"
    return "active"


def upsert_intent(
    ctx: MachineContext,
    *,
    intent_id: str,
    title: str,
    phase: str,
    run_id: str | None,
    status: str | None = None,
    next_action: str | None = None,
    summary: str | None = None,
    change: str | None = None,
) -> dict[str, Any]:
    now = state_store.utc_now()
    current = intent_by_id(ctx.conn, intent_id)
    created_at = current["created_at"] if current else now
    ctx.conn.execute(
        """
        INSERT INTO intents(intent_id, title, status, phase, created_at, updated_at, current_run_id, current_change, next_action, summary)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(intent_id) DO UPDATE SET
          title=excluded.title,
          status=excluded.status,
          phase=excluded.phase,
          updated_at=excluded.updated_at,
          current_run_id=excluded.current_run_id,
          current_change=excluded.current_change,
          next_action=excluded.next_action,
          summary=excluded.summary
        """,
        (
            intent_id,
            title,
            status or status_for_state(phase),
            phase,
            created_at,
            now,
            run_id,
            change,
            next_action,
            summary or title,
        ),
    )
    ctx.conn.commit()
    return intent_by_id(ctx.conn, intent_id) or {}


def set_intent_state(
    ctx: MachineContext,
    intent: dict[str, Any],
    phase: str,
    blockers: list[StopCondition] | None = None,
    next_action: str | None = None,
) -> dict[str, Any]:
    return upsert_intent(
        ctx,
        intent_id=str(intent["intent_id"]),
        title=str(intent.get("title") or ""),
        phase=phase,
        run_id=str(intent.get("current_run_id") or "") or None,
        status=status_for_state(phase, blockers),
        next_action=next_action,
        summary=str(intent.get("summary") or intent.get("title") or ""),
        change=str(intent.get("current_change") or "") or None,
    )


def latest_run_for_intent(conn: sqlite3.Connection, intent: dict[str, Any]) -> dict[str, Any] | None:
    run_id = str(intent.get("current_run_id") or "")
    row = conn.execute("SELECT * FROM runs WHERE run_id = ?", (run_id,)).fetchone()
    return dict(row) if row else None


def stop_missing_evidence(ctx: MachineContext, intent: dict[str, Any], manifest: dict[str, Any] | None) -> StopCondition | None:
    if not intent.get("current_run_id") or manifest is None:
        return StopCondition(
            "missing_evidence",
            "No run manifest is indexed for the current Commander intent.",
            "Start or index a GenOps run before continuing.",
        )
    return None


def stop_failing_gate(ctx: MachineContext, intent: dict[str, Any], manifest: dict[str, Any] | None) -> StopCondition | None:
    payloads = task_payloads(ctx.root, str(intent.get("current_run_id") or "") or None)
    gates = manifest_gates(manifest, payloads)
    failed_gate = next((gate for gate in gates if gate.get("status") == "fail"), None)
    failed_task = False
    if manifest:
        failed_task = any(task.get("status") == "fail" for task in manifest.get("tasks", []) if isinstance(task, dict))
    if manifest_status(manifest) == "failed" or failed_gate or failed_task:
        detail = str(failed_gate.get("command") or failed_gate.get("name")) if failed_gate else "task or pipeline failed"
        return StopCondition(
            "failing_gate",
            f"Validation or gate evidence failed: {detail}.",
            "Fix the failing gate before continuing.",
        )
    return None


def stop_required_visual_verdict(ctx: MachineContext, intent: dict[str, Any], manifest: dict[str, Any] | None) -> StopCondition | None:
    state = str(intent.get("phase") or "")
    verdict = manifest_verdict(manifest)
    if state == "human_review_pending" or manifest_status(manifest) == "human_review_pending":
        if verdict == "pending" and not has_decision(ctx.conn, str(intent["intent_id"]), set(VERDICT_TO_STATE)):
            return StopCondition(
                "required_visual_verdict",
                "The run is waiting for an owner verdict.",
                "Record accept, reject, or accept_with_changes.",
            )
    return None


def stop_direction_required(ctx: MachineContext, intent: dict[str, Any], manifest: dict[str, Any] | None) -> StopCondition | None:
    if intent.get("phase") != "ready_for_direction":
        return None
    if has_decision(ctx.conn, str(intent["intent_id"]), {"accept", "accept_with_changes"}):
        return None
    return StopCondition(
        "direction_required",
        "Planning is ready, but the owner has not accepted a direction.",
        "Confirm the scope or implementation direction before entering implementation.",
    )


def stop_release_approval(ctx: MachineContext, intent: dict[str, Any], manifest: dict[str, Any] | None) -> StopCondition | None:
    pipeline = str(manifest.get("pipeline") if manifest else "")
    phase = str(intent.get("phase") or "")
    if "release" not in pipeline or phase not in {"validation", "closeout_ready"}:
        return None
    if has_decision(ctx.conn, str(intent["intent_id"]), {"release_approved", "accept"}):
        return None
    return StopCondition(
        "release_approval_required",
        "Release-sensitive closeout requires explicit owner approval.",
        "Approve the release-sensitive change or revise the scope.",
    )


def stop_payload_blocker(ctx: MachineContext, intent: dict[str, Any], manifest: dict[str, Any] | None) -> StopCondition | None:
    payloads = task_payloads(ctx.root, str(intent.get("current_run_id") or "") or None)
    blockers = payload_strings(payloads, {"blocker", "blocked_by", "stop_condition", "stop_conditions"})
    if blockers:
        return StopCondition(
            "reported_blocker",
            blockers[0],
            "Resolve the reported blocker before continuing.",
        )
    return None


STOP_RULES: list[Callable[[MachineContext, dict[str, Any], dict[str, Any] | None], StopCondition | None]] = [
    stop_missing_evidence,
    stop_failing_gate,
    stop_payload_blocker,
    stop_required_visual_verdict,
    stop_release_approval,
    stop_direction_required,
]


def collect_stop_conditions(ctx: MachineContext, intent: dict[str, Any], manifest: dict[str, Any] | None) -> list[StopCondition]:
    stops: list[StopCondition] = []
    for rule in STOP_RULES:
        condition = rule(ctx, intent, manifest)
        if condition is not None:
            stops.append(condition)
    return stops


def next_decision_for_state(state: str, stops: list[StopCondition]) -> str:
    if stops:
        return stops[0].next_decision
    if state == "intake":
        return "Classify the owner goal and start a governed run."
    if state == "planning":
        return "Finish planning and prepare the owner direction choice."
    if state == "ready_for_direction":
        return "Confirm the scope or implementation direction."
    if state == "implementation":
        return "Continue implementation, then run validation."
    if state == "validation":
        return "Run gates and decide whether human review or closeout is next."
    if state == "human_review_pending":
        return "Record accept, reject, or accept_with_changes."
    if state in {"accepted", "accepted_with_changes"}:
        return "Move to closeout readiness."
    if state == "rejected":
        return "Revise the direction before continuing."
    if state == "closeout_ready":
        return "Close out and archive the Commander intent."
    if state == "archived":
        return "No owner decision needed."
    return "Review Commander state."


def owner_summary(
    ctx: MachineContext,
    intent: dict[str, Any] | None,
    manifest: dict[str, Any] | None,
    audit: bool = False,
) -> dict[str, Any]:
    if intent is None:
        summary = {
            "goal_status": "no_current_intent",
            "scope_or_direction": "",
            "validation_state": "unknown",
            "risk_or_blocker": "missing current Commander intent",
            "human_decision_needed": False,
            "next_decision": "Start a run.",
        }
        return summary

    stops = collect_stop_conditions(ctx, intent, manifest)
    state = str(intent.get("phase") or "intake")
    summary = {
        "goal_status": state,
        "scope_or_direction": intent.get("summary") or intent.get("title") or "",
        "validation_state": manifest_status(manifest),
        "risk_or_blocker": stops[0].message if stops else "none",
        "human_decision_needed": bool(stops) and stops[0].id in {"direction_required", "required_visual_verdict", "release_approval_required"},
        "next_decision": next_decision_for_state(state, stops),
    }
    if audit:
        run_id = str(intent.get("current_run_id") or "")
        tasks = manifest.get("tasks", []) if manifest else []
        summary["audit"] = {
            "run_id": run_id,
            "pipeline": manifest.get("pipeline") if manifest else None,
            "task_id": [task.get("id") for task in tasks if isinstance(task, dict)],
            "worker_ownership": [
                {"task_id": task.get("id"), "worker": task.get("agent")}
                for task in tasks
                if isinstance(task, dict)
            ],
            "artifacts": manifest.get("artifact_index", []) if manifest else [],
            "gates": manifest_gates(manifest, task_payloads(ctx.root, run_id or None)),
            "raw_logs": f"reports/agent_runs/{run_id}/tasks/*/logs" if run_id else None,
            "manifest_path": f"reports/agent_runs/{run_id}/run_manifest.json" if run_id else None,
        }
    return summary


def transition_state(ctx: MachineContext, intent: dict[str, Any], manifest: dict[str, Any] | None, closeout: bool = False) -> dict[str, Any]:
    current = str(intent.get("phase") or "intake")
    stops = collect_stop_conditions(ctx, intent, manifest)
    visual_review_transition = current == "validation" and any(item.id == "required_visual_verdict" for item in stops)
    if stops and not visual_review_transition and current not in {"accepted", "accepted_with_changes", "closeout_ready"}:
        return set_intent_state(ctx, intent, current, blockers=stops, next_action=next_decision_for_state(current, stops))

    if current == "intake":
        next_state = "planning"
    elif current == "planning":
        next_state = "ready_for_direction"
    elif current == "ready_for_direction":
        next_state = "implementation"
    elif current == "implementation":
        next_state = "validation"
    elif current == "validation":
        next_state = "human_review_pending" if stop_required_visual_verdict(ctx, intent, manifest) else "closeout_ready"
    elif current == "human_review_pending":
        next_state = VERDICT_TO_STATE.get(manifest_verdict(manifest), current)
    elif current in {"accepted", "accepted_with_changes"}:
        next_state = "closeout_ready"
    elif current == "closeout_ready" and closeout:
        next_state = "archived"
    else:
        next_state = current

    next_stops = collect_stop_conditions(ctx, {**intent, "phase": next_state}, manifest)
    if next_state == "archived":
        next_stops = []
    return set_intent_state(
        ctx,
        intent,
        next_state,
        blockers=next_stops,
        next_action=next_decision_for_state(next_state, next_stops),
    )


def command_classify(args: argparse.Namespace) -> dict[str, Any]:
    return recommend(args.goal, args.config)


def command_start_run(args: argparse.Namespace) -> dict[str, Any]:
    ctx = build_context(args.root, args.db, args.config)
    recommendation = recommend(args.goal, args.config)
    pipeline = args.pipeline or recommendation["pipeline"]
    pipeline_path = (ctx.root / pipeline).resolve() if not Path(pipeline).is_absolute() else Path(pipeline)
    pipeline_spec = pipeline_loader.load_pipeline(pipeline_path)
    run_id = args.run_id or default_run_id(pipeline_spec.id)

    run_args = [
        str(Path(pipeline).as_posix()),
        "--goal",
        args.goal,
        "--run-id",
        run_id,
        "--executor",
        args.executor,
        "--human-verdict",
        args.human_verdict,
        "--root",
        str(ctx.root),
    ]
    if args.run_gates:
        run_args.append("--run-gates")
    stdout = io.StringIO()
    stderr = io.StringIO()
    with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
        rc = run_pipeline_main(run_args)
    indexed = state_store.index_run(ctx.conn, ctx.root, run_id)
    manifest = load_manifest(ctx.root, run_id)
    intent_id = args.intent_id or state_store.stable_id("commander", run_id)
    phase = args.phase or infer_initial_state(manifest, recommendation)
    intent = upsert_intent(
        ctx,
        intent_id=intent_id,
        title=args.goal,
        phase=phase,
        run_id=run_id,
        next_action=next_decision_for_state(phase, []),
        summary=args.goal,
        change=args.change,
    )
    stops = collect_stop_conditions(ctx, intent, manifest)
    if stops:
        intent = set_intent_state(ctx, intent, phase, blockers=stops, next_action=next_decision_for_state(phase, stops))
    return {
        "status": "started" if rc == 0 else "run_failed",
        "intent_id": intent["intent_id"],
        "run_id": run_id,
        "pipeline": pipeline_spec.id,
        "state": intent["phase"],
        "indexed": indexed,
        "backend_output": stdout.getvalue().splitlines(),
        "backend_errors": stderr.getvalue().splitlines(),
        "summary": owner_summary(ctx, intent, manifest, audit=args.audit),
    }


def command_status(args: argparse.Namespace) -> dict[str, Any]:
    ctx = build_context(args.root, args.db, args.config)
    intent = select_intent(ctx.conn, args.intent_id)
    manifest = load_manifest(ctx.root, str(intent.get("current_run_id") or "") if intent else None)
    return owner_summary(ctx, intent, manifest, audit=args.audit)


def command_next_decision(args: argparse.Namespace) -> dict[str, Any]:
    ctx = build_context(args.root, args.db, args.config)
    intent = select_intent(ctx.conn, args.intent_id)
    manifest = load_manifest(ctx.root, str(intent.get("current_run_id") or "") if intent else None)
    summary = owner_summary(ctx, intent, manifest, audit=False)
    return {
        "human_decision_needed": summary["human_decision_needed"],
        "next_decision": summary["next_decision"],
        "risk_or_blocker": summary["risk_or_blocker"],
    }


def command_continue_current(args: argparse.Namespace) -> dict[str, Any]:
    ctx = build_context(args.root, args.db, args.config)
    intent = select_intent(ctx.conn, args.intent_id)
    if intent is None:
        return owner_summary(ctx, None, None, audit=args.audit)
    manifest = load_manifest(ctx.root, str(intent.get("current_run_id") or "") or None)
    updated = transition_state(ctx, intent, manifest)
    return {
        "previous_state": intent["phase"],
        "state": updated["phase"],
        "summary": owner_summary(ctx, updated, manifest, audit=args.audit),
    }


def command_record_verdict(args: argparse.Namespace) -> dict[str, Any]:
    ctx = build_context(args.root, args.db, args.config)
    intent = select_intent(ctx.conn, args.intent_id)
    if intent is None:
        return owner_summary(ctx, None, None, audit=args.audit)
    run_id = args.run_id or str(intent.get("current_run_id") or "") or None
    payload = state_store.record_decision(
        conn=ctx.conn,
        root=ctx.root,
        intent_id=str(intent["intent_id"]),
        run_id=run_id,
        kind=args.verdict,
        summary=args.summary,
    )
    phase = VERDICT_TO_STATE[args.verdict]
    updated = set_intent_state(
        ctx,
        intent,
        phase,
        next_action=next_decision_for_state(phase, []),
    )
    manifest = load_manifest(ctx.root, run_id)
    return {
        "decision": payload,
        "state": updated["phase"],
        "summary": owner_summary(ctx, updated, manifest, audit=args.audit),
    }


def command_closeout(args: argparse.Namespace) -> dict[str, Any]:
    ctx = build_context(args.root, args.db, args.config)
    intent = select_intent(ctx.conn, args.intent_id)
    if intent is None:
        return owner_summary(ctx, None, None, audit=args.audit)
    manifest = load_manifest(ctx.root, str(intent.get("current_run_id") or "") or None)
    ready = intent
    if intent.get("phase") in {"accepted", "accepted_with_changes"}:
        ready = transition_state(ctx, intent, manifest)
    closeout_stops = collect_stop_conditions(ctx, {**ready, "phase": "closeout_ready"}, manifest)
    if closeout_stops:
        blocked = set_intent_state(ctx, ready, str(ready.get("phase") or "closeout_ready"), blockers=closeout_stops)
        return {
            "status": "blocked",
            "state": blocked["phase"],
            "stop_conditions": [item.as_dict() for item in closeout_stops],
            "summary": owner_summary(ctx, blocked, manifest, audit=args.audit),
        }
    archived = set_intent_state(ctx, ready, "archived", blockers=[], next_action="No owner decision needed.")
    return {
        "status": "archived",
        "state": archived["phase"],
        "summary": owner_summary(ctx, archived, manifest, audit=args.audit),
    }


def command_summary(args: argparse.Namespace) -> dict[str, Any]:
    return command_status(args)


def print_result(data: Any, as_json: bool) -> None:
    if as_json:
        print(json.dumps(data, indent=2, ensure_ascii=False))
    else:
        print(json.dumps(data, ensure_ascii=False))


def add_shared(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--db", default=DEFAULT_DB)
    parser.add_argument("--config", type=Path, default=CONFIG)
    parser.add_argument("--json", action="store_true")


def parse_command_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    add_shared(parser)
    subparsers = parser.add_subparsers(dest="command", required=True)

    classify = subparsers.add_parser("classify")
    classify.add_argument("goal")
    classify.set_defaults(handler=command_classify)

    start = subparsers.add_parser("start-run")
    start.add_argument("goal")
    start.add_argument("--pipeline")
    start.add_argument("--run-id")
    start.add_argument("--intent-id")
    start.add_argument("--change")
    start.add_argument("--phase", choices=STATE_SEQUENCE)
    start.add_argument("--executor", choices=["no_op", "manual", "real", "subagent"], default="no_op")
    start.add_argument("--run-gates", action="store_true")
    start.add_argument("--human-verdict", choices=sorted(state_store.VERDICTS), default="pending")
    start.add_argument("--audit", action="store_true")
    start.set_defaults(handler=command_start_run)

    for name, handler in (
        ("continue-current", command_continue_current),
        ("status", command_status),
        ("next-decision", command_next_decision),
        ("closeout", command_closeout),
        ("summary", command_summary),
    ):
        item = subparsers.add_parser(name)
        item.add_argument("--intent-id")
        item.add_argument("--audit", action="store_true")
        item.set_defaults(handler=handler)

    verdict = subparsers.add_parser("record-verdict")
    verdict.add_argument("verdict", choices=sorted(VERDICT_TO_STATE))
    verdict.add_argument("--summary", required=True)
    verdict.add_argument("--intent-id")
    verdict.add_argument("--run-id")
    verdict.add_argument("--audit", action="store_true")
    verdict.set_defaults(handler=command_record_verdict)
    return parser.parse_args(argv)


def parse_legacy_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    add_shared(parser)
    parser.add_argument("goal", help="Natural-language owner goal")
    parser.set_defaults(command="classify", handler=command_classify)
    return parser.parse_args(argv)


def normalize_global_args(argv: list[str]) -> list[str]:
    prefix: list[str] = []
    rest: list[str] = []
    value_options = {"--root", "--db", "--config"}
    value_prefixes = ("--root=", "--db=", "--config=")
    index = 0
    while index < len(argv):
        item = argv[index]
        if item == "--json":
            prefix.append(item)
            index += 1
        elif item in value_options and index + 1 < len(argv):
            prefix.extend([item, argv[index + 1]])
            index += 2
        elif item.startswith(value_prefixes):
            prefix.append(item)
            index += 1
        else:
            rest.append(item)
            index += 1
    return prefix + rest


def main(argv: list[str] | None = None) -> int:
    raw = normalize_global_args(list(argv or sys.argv[1:]))
    command_index = next((index for index, item in enumerate(raw) if item in COMMANDS), None)
    args = parse_command_args(raw) if command_index is not None else parse_legacy_args(raw)
    data = args.handler(args)
    print_result(data, args.json)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
