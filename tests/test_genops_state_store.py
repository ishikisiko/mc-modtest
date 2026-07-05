from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

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

            for run_id in ("run-1", "run-2"):
                closed_intent = state_store.stable_id("run", run_id)
                closed = conn.execute("SELECT status, phase FROM intents WHERE intent_id = ?", (closed_intent,)).fetchone()
                self.assertEqual(dict(closed), {"status": "completed", "phase": "closed"})

            current = conn.execute("SELECT current_run_id FROM intents WHERE status IN ('active', 'blocked')").fetchone()
            self.assertIsNone(current)


if __name__ == "__main__":
    unittest.main()
