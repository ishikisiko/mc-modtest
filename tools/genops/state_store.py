#!/usr/bin/env python3
"""Maintain a rebuildable SQLite index for CRAFT/GenOps run evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tools.genops.artifact_writer import ensure_dir, write_json


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_DB = ".genops/state.sqlite"
SCHEMA_VERSION = "1"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def stable_id(*parts: str) -> str:
    raw = "\0".join(parts)
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:24]


def db_path(root: Path, value: str | None) -> Path:
    path = Path(value or DEFAULT_DB)
    return path if path.is_absolute() else root / path


def connect(root: Path, db_value: str | None = None) -> sqlite3.Connection:
    if db_value == ":memory:":
        conn = sqlite3.connect(":memory:")
        conn.row_factory = sqlite3.Row
        return conn
    path = db_path(root, db_value)
    ensure_dir(path.parent)
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA busy_timeout=5000")
    return conn


def init_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS schema_meta (
          key TEXT PRIMARY KEY,
          value TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS intents (
          intent_id TEXT PRIMARY KEY,
          title TEXT NOT NULL,
          status TEXT NOT NULL,
          phase TEXT NOT NULL,
          created_at TEXT NOT NULL,
          updated_at TEXT NOT NULL,
          current_run_id TEXT,
          current_change TEXT,
          next_action TEXT,
          summary TEXT
        );

        CREATE TABLE IF NOT EXISTS runs (
          run_id TEXT PRIMARY KEY,
          intent_id TEXT,
          pipeline TEXT NOT NULL,
          status TEXT NOT NULL,
          goal TEXT NOT NULL,
          manifest_path TEXT NOT NULL,
          human_verdict TEXT,
          created_at TEXT,
          updated_at TEXT
        );

        CREATE TABLE IF NOT EXISTS tasks (
          run_id TEXT NOT NULL,
          task_id TEXT NOT NULL,
          role TEXT NOT NULL,
          status TEXT NOT NULL,
          assigned_agent TEXT,
          self_executed INTEGER DEFAULT 0,
          blocker TEXT,
          retry_count INTEGER DEFAULT 0,
          PRIMARY KEY(run_id, task_id)
        );

        CREATE TABLE IF NOT EXISTS decisions (
          decision_id TEXT PRIMARY KEY,
          intent_id TEXT NOT NULL,
          run_id TEXT,
          kind TEXT NOT NULL,
          summary TEXT NOT NULL,
          created_at TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS artifacts (
          artifact_id TEXT PRIMARY KEY,
          run_id TEXT NOT NULL,
          task_id TEXT,
          path TEXT NOT NULL,
          kind TEXT,
          role TEXT,
          action TEXT
        );

        CREATE TABLE IF NOT EXISTS gates (
          gate_id TEXT PRIMARY KEY,
          run_id TEXT NOT NULL,
          task_id TEXT,
          name TEXT,
          status TEXT NOT NULL,
          command TEXT,
          stdout_log TEXT,
          stderr_log TEXT
        );

        CREATE TABLE IF NOT EXISTS closeout (
          change_name TEXT PRIMARY KEY,
          intent_id TEXT,
          run_id TEXT,
          archive_ready INTEGER NOT NULL,
          archived INTEGER NOT NULL,
          archive_path TEXT,
          blockers_json TEXT,
          updated_at TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS events (
          seq INTEGER PRIMARY KEY AUTOINCREMENT,
          intent_id TEXT,
          run_id TEXT,
          task_id TEXT,
          event_type TEXT NOT NULL,
          payload_json TEXT,
          created_at TEXT NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_runs_updated ON runs(updated_at);
        CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
        CREATE INDEX IF NOT EXISTS idx_artifacts_path ON artifacts(path);
        CREATE INDEX IF NOT EXISTS idx_closeout_ready ON closeout(archive_ready, archived);
        CREATE INDEX IF NOT EXISTS idx_events_run ON events(run_id);
        """
    )
    conn.execute(
        "INSERT OR REPLACE INTO schema_meta(key, value) VALUES (?, ?)",
        ("schema_version", SCHEMA_VERSION),
    )
    conn.commit()


def clear_index(conn: sqlite3.Connection) -> None:
    for table in (
        "intents",
        "runs",
        "tasks",
        "artifacts",
        "gates",
        "closeout",
        "events",
    ):
        conn.execute(f"DELETE FROM {table}")
    conn.commit()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def normalize_path(path: str | Path, root: Path) -> str:
    value = str(path).replace("\\", "/")
    path_obj = Path(value)
    if path_obj.is_absolute():
        try:
            value = path_obj.resolve().relative_to(root.resolve()).as_posix()
        except ValueError:
            value = path_obj.as_posix()
    while value.startswith("./"):
        value = value[2:]
    return value.rstrip("/")


def classify_artifact(path: str) -> str:
    if path.startswith("openspec/"):
        return "openspec"
    if path.startswith("docs/") or path in {"README.md", "AGENTS.md", "CRAFT.md"}:
        return "docs"
    if path.startswith("genops/") or path.startswith("tools/genops/"):
        return "genops"
    if path.startswith("reports/"):
        return "report"
    if path.startswith("out/preview/"):
        return "preview"
    if path.startswith("src/main/"):
        return "runtime"
    return "artifact"


def classify_action(path: str) -> str:
    if "/archive/" in path:
        return "archived"
    return "touched"


def iter_task_payloads(run_dir: Path, task_id: str) -> Iterable[dict[str, Any]]:
    task_dir = run_dir / "tasks" / task_id
    for filename in ("task_result.json", "evidence.json", "patch_guard.json"):
        path = task_dir / filename
        if path.exists():
            payload = read_json(path)
            if isinstance(payload, dict):
                yield payload


def task_self_executed(run_dir: Path, task_id: str) -> bool:
    return any(bool(payload.get("commander_self_executed")) for payload in iter_task_payloads(run_dir, task_id))


def task_blocker(run_dir: Path, task_id: str, status: str) -> str | None:
    if status not in {"blocked", "fail"}:
        return None
    for payload in iter_task_payloads(run_dir, task_id):
        for key in ("summary", "blocker", "error"):
            if payload.get(key):
                return str(payload[key])
    return status


def artifact_rows(manifest: dict[str, Any], root: Path) -> list[tuple[str, str | None, str, str, str | None, str]]:
    rows: list[tuple[str, str | None, str, str, str | None, str]] = []
    run_id = str(manifest["run_id"])
    task_role = {str(task["id"]): str(task.get("agent", "")) for task in manifest.get("tasks", [])}
    for item in manifest.get("artifact_index", []):
        if not isinstance(item, dict):
            continue
        task_id = str(item.get("task_id") or "")
        role = str(item.get("agent") or task_role.get(task_id) or "")
        for raw_path in item.get("artifacts", []):
            path = normalize_path(raw_path, root)
            artifact_id = stable_id(run_id, task_id, path)
            rows.append((artifact_id, task_id or None, path, classify_artifact(path), role or None, classify_action(path)))
    return rows


def gate_rows(manifest: dict[str, Any]) -> list[tuple[str, str | None, str | None, str, str | None, str | None, str | None]]:
    rows: list[tuple[str, str | None, str | None, str, str | None, str | None, str | None]] = []
    run_id = str(manifest["run_id"])
    for task in manifest.get("tasks", []):
        task_id = str(task.get("id", ""))
        for index, gate in enumerate(task.get("gates", []) or [], start=1):
            if not isinstance(gate, dict):
                continue
            command = gate.get("command")
            gate_id = stable_id(run_id, task_id, str(index), str(command))
            rows.append(
                (
                    gate_id,
                    task_id or None,
                    str(gate.get("name") or command or f"gate-{index}"),
                    str(gate.get("status") or "unknown"),
                    str(command) if command is not None else None,
                    str(gate.get("stdout_log")) if gate.get("stdout_log") else None,
                    str(gate.get("stderr_log")) if gate.get("stderr_log") else None,
                )
            )
    return rows


def upsert_intent_for_run(conn: sqlite3.Connection, manifest: dict[str, Any]) -> str:
    run_id = str(manifest["run_id"])
    goal = str(manifest.get("goal") or run_id)
    intent_id = stable_id("run", run_id)
    now = str(manifest.get("generated_at") or utc_now())
    run_status = str(manifest.get("status") or "unknown")
    human_verdict = str(manifest.get("human_verdict") or "pending")
    if run_status in {"failed", "rejected_by_human"}:
        status = "blocked"
        phase = "closeout"
    elif run_status in {"human_review_pending", "manual_ready", "accepted"} and human_verdict == "pending":
        status = "active"
        phase = "review" if run_status == "human_review_pending" else "closeout"
    elif run_status in {"pass", "success", "completed"}:
        status = "completed"
        phase = "closed"
    else:
        status = "active"
        phase = "closeout"
    conn.execute(
        """
        INSERT INTO intents(intent_id, title, status, phase, created_at, updated_at, current_run_id, summary)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(intent_id) DO UPDATE SET
          title=excluded.title,
          status=excluded.status,
          phase=excluded.phase,
          updated_at=excluded.updated_at,
          current_run_id=excluded.current_run_id,
          summary=excluded.summary
        """,
        (intent_id, goal, status, phase, now, now, run_id, goal),
    )
    return intent_id


def index_run(conn: sqlite3.Connection, root: Path, run_id: str) -> dict[str, Any]:
    run_dir = root / "reports" / "agent_runs" / run_id
    manifest_path = run_dir / "run_manifest.json"
    if not manifest_path.exists():
        raise FileNotFoundError(f"run manifest not found: {manifest_path}")
    manifest = read_json(manifest_path)
    intent_id = upsert_intent_for_run(conn, manifest)
    generated_at = str(manifest.get("generated_at") or utc_now())

    conn.execute("DELETE FROM tasks WHERE run_id = ?", (run_id,))
    conn.execute("DELETE FROM artifacts WHERE run_id = ?", (run_id,))
    conn.execute("DELETE FROM gates WHERE run_id = ?", (run_id,))
    conn.execute(
        """
        INSERT INTO runs(run_id, intent_id, pipeline, status, goal, manifest_path, human_verdict, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(run_id) DO UPDATE SET
          intent_id=excluded.intent_id,
          pipeline=excluded.pipeline,
          status=excluded.status,
          goal=excluded.goal,
          manifest_path=excluded.manifest_path,
          human_verdict=excluded.human_verdict,
          updated_at=excluded.updated_at
        """,
        (
            run_id,
            intent_id,
            str(manifest.get("pipeline") or ""),
            str(manifest.get("status") or "unknown"),
            str(manifest.get("goal") or ""),
            normalize_path(manifest_path, root),
            str(manifest.get("human_verdict") or "pending"),
            generated_at,
            generated_at,
        ),
    )

    for task in manifest.get("tasks", []):
        task_id = str(task.get("id", ""))
        if not task_id:
            continue
        status = str(task.get("status") or "unknown")
        conn.execute(
            """
            INSERT OR REPLACE INTO tasks(run_id, task_id, role, status, assigned_agent, self_executed, blocker, retry_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT retry_count FROM tasks WHERE run_id=? AND task_id=?), 0))
            """,
            (
                run_id,
                task_id,
                str(task.get("agent") or ""),
                status,
                None,
                1 if task_self_executed(run_dir, task_id) else 0,
                task_blocker(run_dir, task_id, status),
                run_id,
                task_id,
            ),
        )

    for artifact_id, task_id, path, kind, role, action in artifact_rows(manifest, root):
        conn.execute(
            """
            INSERT OR REPLACE INTO artifacts(artifact_id, run_id, task_id, path, kind, role, action)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (artifact_id, run_id, task_id, path, kind, role, action),
        )

    for gate_id, task_id, name, status, command, stdout_log, stderr_log in gate_rows(manifest):
        conn.execute(
            """
            INSERT OR REPLACE INTO gates(gate_id, run_id, task_id, name, status, command, stdout_log, stderr_log)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (gate_id, run_id, task_id, name, status, command, stdout_log, stderr_log),
        )

    index_decision_artifacts(conn, root, run_dir, intent_id)
    conn.commit()
    return {"run_id": run_id, "tasks": len(manifest.get("tasks", [])), "manifest_path": normalize_path(manifest_path, root)}


def index_decision_artifacts(conn: sqlite3.Connection, root: Path, run_dir: Path, fallback_intent_id: str) -> None:
    decisions_dir = run_dir / "artifacts" / "decisions"
    if not decisions_dir.exists():
        return
    for path in sorted(decisions_dir.glob("*.json")):
        payload = read_json(path)
        decision_id = str(payload.get("decision_id") or path.stem)
        intent_id = str(payload.get("intent_id") or fallback_intent_id)
        run_id = payload.get("run_id")
        created_at = str(payload.get("created_at") or utc_now())
        conn.execute(
            """
            INSERT OR REPLACE INTO decisions(decision_id, intent_id, run_id, kind, summary, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                decision_id,
                intent_id,
                str(run_id) if run_id else None,
                str(payload.get("kind") or "unknown"),
                str(payload.get("summary") or ""),
                created_at,
            ),
        )
        conn.execute(
            """
            INSERT INTO events(intent_id, run_id, task_id, event_type, payload_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (intent_id, str(run_id) if run_id else None, None, "decision_recorded", json.dumps(payload, ensure_ascii=False), created_at),
        )


def iter_run_ids(root: Path) -> Iterable[str]:
    base = root / "reports" / "agent_runs"
    if not base.exists():
        return []
    return sorted(path.name for path in base.iterdir() if (path / "run_manifest.json").exists())


def run_openspec_list(root: Path) -> list[dict[str, Any]]:
    proc = subprocess.run(["openspec", "list", "--json"], cwd=root, text=True, capture_output=True, check=False)
    if proc.returncode != 0:
        return []
    try:
        data = json.loads(proc.stdout)
    except json.JSONDecodeError:
        return []
    changes = data.get("changes", [])
    return changes if isinstance(changes, list) else []


def index_closeout(conn: sqlite3.Connection, root: Path) -> None:
    now = utc_now()
    for change in run_openspec_list(root):
        if not isinstance(change, dict):
            continue
        name = str(change.get("name") or "")
        if not name:
            continue
        complete = change.get("completedTasks") == change.get("totalTasks") and change.get("status") == "complete"
        blockers = [] if complete else ["tasks_incomplete"]
        if complete:
            blockers.append("needs_craft_closeout_evidence")
        conn.execute(
            """
            INSERT OR REPLACE INTO closeout(change_name, intent_id, run_id, archive_ready, archived, archive_path, blockers_json, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (name, None, None, 0, 0, None, json.dumps(blockers, ensure_ascii=False), now),
        )

    archive_root = root / "openspec" / "changes" / "archive"
    if archive_root.exists():
        for path in sorted(item for item in archive_root.iterdir() if item.is_dir()):
            name = path.name
            conn.execute(
                """
                INSERT OR REPLACE INTO closeout(change_name, intent_id, run_id, archive_ready, archived, archive_path, blockers_json, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (name, None, None, 0, 1, normalize_path(path, root), "[]", now),
            )
    conn.commit()


def rebuild(conn: sqlite3.Connection, root: Path) -> dict[str, int]:
    init_schema(conn)
    clear_index(conn)
    run_count = 0
    for run_id in iter_run_ids(root):
        index_run(conn, root, run_id)
        run_count += 1
    index_closeout(conn, root)
    counts = {
        "runs": run_count,
        "tasks": conn.execute("SELECT COUNT(*) FROM tasks").fetchone()[0],
        "artifacts": conn.execute("SELECT COUNT(*) FROM artifacts").fetchone()[0],
        "closeout": conn.execute("SELECT COUNT(*) FROM closeout").fetchone()[0],
    }
    return counts


def rows_to_dicts(rows: Iterable[sqlite3.Row]) -> list[dict[str, Any]]:
    return [dict(row) for row in rows]


def print_result(data: Any, as_json: bool = False) -> None:
    if as_json:
        print(json.dumps(data, indent=2, ensure_ascii=False))
    elif isinstance(data, list):
        for item in data:
            print(item if isinstance(item, str) else json.dumps(item, ensure_ascii=False))
    elif isinstance(data, dict):
        for key, value in data.items():
            print(f"{key}: {value}")
    else:
        print(data)


def cmd_current(conn: sqlite3.Connection, args: argparse.Namespace) -> None:
    row = conn.execute(
        """
        SELECT * FROM intents
        WHERE status IN ('active', 'blocked')
        ORDER BY updated_at DESC
        LIMIT 1
        """
    ).fetchone()
    print_result(dict(row) if row else {}, args.json)


def cmd_pending_decisions(conn: sqlite3.Connection, args: argparse.Namespace) -> None:
    rows = conn.execute(
        """
        SELECT r.run_id, r.intent_id, r.pipeline, r.status, r.goal, r.human_verdict
        FROM runs r
        WHERE r.status = 'human_review_pending'
           OR (r.human_verdict = 'pending' AND r.status IN ('manual_ready', 'accepted'))
        ORDER BY r.updated_at DESC
        """
    ).fetchall()
    print_result(rows_to_dicts(rows), args.json)


def cmd_closeout_ready(conn: sqlite3.Connection, args: argparse.Namespace) -> None:
    rows = conn.execute(
        """
        SELECT * FROM closeout
        WHERE archive_ready = 1 AND archived = 0
        ORDER BY updated_at DESC
        """
    ).fetchall()
    print_result(rows_to_dicts(rows), args.json)


def cmd_artifact_owner(conn: sqlite3.Connection, args: argparse.Namespace, root: Path) -> None:
    target = normalize_path(args.path, root)
    rows = conn.execute(
        """
        SELECT a.path, a.run_id, a.task_id, a.role, a.kind, a.action, r.updated_at
        FROM artifacts a
        LEFT JOIN runs r ON r.run_id = a.run_id
        WHERE a.path = ? OR a.path LIKE ?
        ORDER BY r.updated_at DESC
        LIMIT ?
        """,
        (target, f"%{target}", args.limit),
    ).fetchall()
    print_result(rows_to_dicts(rows), args.json)


def cmd_record_decision(conn: sqlite3.Connection, args: argparse.Namespace, root: Path) -> None:
    created_at = utc_now()
    decision_id = args.decision_id or stable_id(args.intent, args.kind, args.summary, created_at)
    payload = {
        "decision_id": decision_id,
        "intent_id": args.intent,
        "run_id": args.run_id,
        "kind": args.kind,
        "summary": args.summary,
        "created_at": created_at,
    }
    conn.execute(
        """
        INSERT OR REPLACE INTO decisions(decision_id, intent_id, run_id, kind, summary, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (decision_id, args.intent, args.run_id, args.kind, args.summary, created_at),
    )
    conn.execute(
        """
        INSERT INTO events(intent_id, run_id, task_id, event_type, payload_json, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (args.intent, args.run_id, None, "decision_recorded", json.dumps(payload, ensure_ascii=False), created_at),
    )
    if args.run_id:
        out_dir = ensure_dir(root / "reports" / "agent_runs" / args.run_id / "artifacts" / "decisions")
        write_json(out_dir / f"{decision_id}.json", payload)
    conn.commit()
    print_result(payload, args.json)


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--db", default=DEFAULT_DB)
    parser.add_argument("--json", action="store_true")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("init")
    subparsers.add_parser("rebuild")

    index_run_parser = subparsers.add_parser("index-run")
    index_run_parser.add_argument("run_id")

    subparsers.add_parser("current")
    subparsers.add_parser("pending-decisions")
    subparsers.add_parser("closeout-ready")

    record = subparsers.add_parser("record-decision")
    record.add_argument("--intent", required=True)
    record.add_argument("--run-id")
    record.add_argument("--kind", required=True, choices=["accept", "reject", "accept_with_changes", "reopen_discussion", "pause"])
    record.add_argument("--summary", required=True)
    record.add_argument("--decision-id")

    owner = subparsers.add_parser("artifact-owner")
    owner.add_argument("path")
    owner.add_argument("--limit", type=int, default=20)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    root = args.root.resolve()
    conn = connect(root, args.db)
    init_schema(conn)

    if args.command == "init":
        print_result({"db": normalize_path(db_path(root, args.db), root), "schema_version": SCHEMA_VERSION}, args.json)
    elif args.command == "rebuild":
        print_result(rebuild(conn, root), args.json)
    elif args.command == "index-run":
        print_result(index_run(conn, root, args.run_id), args.json)
    elif args.command == "current":
        cmd_current(conn, args)
    elif args.command == "pending-decisions":
        cmd_pending_decisions(conn, args)
    elif args.command == "closeout-ready":
        cmd_closeout_ready(conn, args)
    elif args.command == "record-decision":
        cmd_record_decision(conn, args, root)
    elif args.command == "artifact-owner":
        cmd_artifact_owner(conn, args, root)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
