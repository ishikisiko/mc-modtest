from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.genops import state_store


class StateStoreTests(unittest.TestCase):
    def test_rebuild_indexes_run_artifacts_and_decisions(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            run_dir = root / "reports" / "agent_runs" / "run-1"
            task_dir = run_dir / "tasks" / "task-a"
            task_dir.mkdir(parents=True)
            (run_dir / "run_manifest.json").write_text(
                json.dumps(
                    {
                        "run_id": "run-1",
                        "pipeline": "openspec-change.full",
                        "status": "pass",
                        "goal": "test goal",
                        "generated_at": "2026-07-05T00:00:00+00:00",
                        "human_verdict": "pending",
                        "tasks": [{"id": "task-a", "agent": "docs-steward", "status": "pass", "gates": []}],
                        "artifact_index": [
                            {
                                "task_id": "task-a",
                                "agent": "docs-steward",
                                "status": "pass",
                                "artifacts": ["docs/ai-kb/example.md"],
                            }
                        ],
                        "visual": {},
                        "defect_index": {},
                    }
                ),
                encoding="utf-8",
            )
            (task_dir / "task_result.json").write_text(
                json.dumps({"task_id": "task-a", "agent": "docs-steward", "commander_self_executed": True}),
                encoding="utf-8",
            )
            decision_dir = run_dir / "artifacts" / "decisions"
            decision_dir.mkdir(parents=True)
            (decision_dir / "decision-accept.json").write_text(
                json.dumps(
                    {
                        "decision_id": "decision-accept",
                        "run_id": "run-1",
                        "kind": "accept",
                        "summary": "accepted from evidence",
                        "created_at": "2026-07-05T00:02:00+00:00",
                    }
                ),
                encoding="utf-8",
            )
            closed_run_dir = root / "reports" / "agent_runs" / "run-2"
            closed_run_dir.mkdir(parents=True)
            (closed_run_dir / "run_manifest.json").write_text(
                json.dumps(
                    {
                        "run_id": "run-2",
                        "pipeline": "openspec-change.full",
                        "status": "pass",
                        "goal": "closed goal",
                        "generated_at": "2026-07-05T00:01:00+00:00",
                        "human_verdict": "not_required_nonvisual_auto_archive",
                        "tasks": [],
                        "artifact_index": [],
                        "visual": {},
                        "defect_index": {},
                    }
                ),
                encoding="utf-8",
            )

            conn = state_store.connect(root, ":memory:")
            counts = state_store.rebuild(conn, root)
            self.assertEqual(counts["runs"], 2)
            self.assertEqual(counts["tasks"], 1)
            self.assertEqual(counts["artifacts"], 1)

            owner = conn.execute("SELECT run_id, task_id, role FROM artifacts WHERE path = ?", ("docs/ai-kb/example.md",)).fetchone()
            self.assertEqual(dict(owner), {"run_id": "run-1", "task_id": "task-a", "role": "docs-steward"})

            task = conn.execute("SELECT self_executed FROM tasks WHERE run_id = ? AND task_id = ?", ("run-1", "task-a")).fetchone()
            self.assertEqual(task["self_executed"], 1)

            decided = conn.execute("SELECT status, human_verdict FROM runs WHERE run_id = ?", ("run-1",)).fetchone()
            self.assertEqual(dict(decided), {"status": "accepted", "human_verdict": "accept"})
            decision_count = conn.execute("SELECT COUNT(*) AS count FROM decisions").fetchone()
            self.assertEqual(decision_count["count"], 1)

            accepted_intent = state_store.stable_id("run", "run-1")
            accepted = conn.execute("SELECT status, phase FROM intents WHERE intent_id = ?", (accepted_intent,)).fetchone()
            self.assertEqual(dict(accepted), {"status": "active", "phase": "accepted"})

            closed_intent = state_store.stable_id("run", "run-2")
            closed = conn.execute("SELECT status, phase FROM intents WHERE intent_id = ?", (closed_intent,)).fetchone()
            self.assertEqual(dict(closed), {"status": "completed", "phase": "closed"})

            current = conn.execute("SELECT current_run_id FROM intents WHERE status IN ('active', 'blocked')").fetchone()
            self.assertEqual(current["current_run_id"], "run-1")

    def test_rebuild_marks_closeout_ready_from_evidence_verdict_and_validation(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            run_dir = root / "reports" / "agent_runs" / "closeout-run"
            run_dir.mkdir(parents=True)
            (run_dir / "run_manifest.json").write_text(
                json.dumps(
                    {
                        "run_id": "closeout-run",
                        "pipeline": "openspec-change.full",
                        "status": "pass",
                        "goal": "ready change",
                        "generated_at": "2026-07-05T00:00:00+00:00",
                        "human_verdict": "pending",
                        "tasks": [{"id": "closeout-readiness", "agent": "regression-steward", "status": "pass", "gates": []}],
                        "artifact_index": [
                            {
                                "task_id": "closeout-readiness",
                                "agent": "regression-steward",
                                "status": "pass",
                                "artifacts": [
                                    "openspec/changes/ready-change/proposal.md",
                                    "artifacts/closeout_readiness.json",
                                ],
                            }
                        ],
                        "frontdoor": {"status": "pass", "protected_paths": [], "accepted": [], "findings": []},
                        "visual": {},
                        "defect_index": {},
                    }
                ),
                encoding="utf-8",
            )
            decision_dir = run_dir / "artifacts" / "decisions"
            decision_dir.mkdir(parents=True)
            (decision_dir / "decision-not-required.json").write_text(
                json.dumps(
                    {
                        "decision_id": "decision-not-required",
                        "run_id": "closeout-run",
                        "kind": "not_required",
                        "summary": "nonvisual",
                        "created_at": "2026-07-05T00:01:00+00:00",
                    }
                ),
                encoding="utf-8",
            )

            conn = state_store.connect(root, ":memory:")
            with patch.object(
                state_store,
                "run_openspec_list",
                return_value=[{"name": "ready-change", "status": "complete", "completedTasks": 1, "totalTasks": 1}],
            ):
                state_store.rebuild(conn, root)

            row = conn.execute("SELECT archive_ready, archived, run_id, blockers_json FROM closeout WHERE change_name = ?", ("ready-change",)).fetchone()
            self.assertEqual(row["archive_ready"], 1)
            self.assertEqual(row["archived"], 0)
            self.assertEqual(row["run_id"], "closeout-run")
            self.assertEqual(json.loads(row["blockers_json"]), [])

    def test_record_decision_updates_run_and_pending_query(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            run_dir = root / "reports" / "agent_runs" / "review-run"
            run_dir.mkdir(parents=True)
            (run_dir / "run_manifest.json").write_text(
                json.dumps(
                    {
                        "run_id": "review-run",
                        "pipeline": "visual-acceptance.full",
                        "status": "human_review_pending",
                        "goal": "needs verdict",
                        "generated_at": "2026-07-05T00:00:00+00:00",
                        "human_verdict": "pending",
                        "tasks": [],
                        "artifact_index": [],
                        "visual": {},
                        "defect_index": {},
                    }
                ),
                encoding="utf-8",
            )
            conn = state_store.connect(root, ":memory:")
            state_store.init_schema(conn)
            state_store.index_run(conn, root, "review-run")

            pending = conn.execute(
                """
                SELECT run_id FROM runs
                WHERE status = 'human_review_pending' AND human_verdict = 'pending'
                """
            ).fetchall()
            self.assertEqual([row["run_id"] for row in pending], ["review-run"])

            intent_id = state_store.stable_id("run", "review-run")
            state_store.record_decision(
                conn=conn,
                root=root,
                intent_id=intent_id,
                run_id="review-run",
                kind="accept",
                summary="accepted",
            )
            run = conn.execute("SELECT status, human_verdict FROM runs WHERE run_id = ?", ("review-run",)).fetchone()
            self.assertEqual(dict(run), {"status": "accepted", "human_verdict": "accept"})
            pending = conn.execute(
                """
                SELECT run_id FROM runs
                WHERE status = 'human_review_pending' AND human_verdict = 'pending'
                """
            ).fetchall()
            self.assertEqual(pending, [])


if __name__ == "__main__":
    unittest.main()
