import json
import tempfile
import tomllib
import unittest
from argparse import Namespace
from pathlib import Path

import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from tools.genops import check_frontdoor, commander, patch_guard, pipeline_loader, report_writer, state_store, task_graph, validate_pipelines, visual_indexer  # noqa: E402
from tools.genops.models import PipelineSpec, TaskSpec  # noqa: E402
from tools.genops.run_pipeline import main as run_pipeline_main  # noqa: E402


class GenOpsTests(unittest.TestCase):
    def test_all_pipelines_load_and_sort(self) -> None:
        for path in sorted((ROOT / "genops" / "pipelines").glob("*.yaml")):
            pipeline = pipeline_loader.load_pipeline(path)
            ordered = task_graph.topological_tasks(pipeline)
            self.assertEqual(len(ordered), len(pipeline.tasks))
            self.assertEqual({task.id for task in ordered}, {task.id for task in pipeline.tasks})

    def test_pipeline_governance_validation_passes(self) -> None:
        self.assertEqual([finding.as_dict() for finding in validate_pipelines.validate(ROOT)], [])

    def test_pipeline_governance_validation_reports_errors(self) -> None:
        pipeline = PipelineSpec(
            id="bad.full",
            kind="generator-pipeline",
            version=1,
            path="genops/pipelines/bad.full.yaml",
            goal_summary="bad",
            human_review_required=False,
            tasks=[
                TaskSpec(
                    id="patch-bad",
                    agent="unknown-role",
                    outputs=["patch.diff"],
                    allowed_files=[],
                )
            ],
            raw={"id": "bad.full", "human_review": {"required": False}, "tasks": []},
        )
        rules = {finding.rule for finding in validate_pipelines.check_pipeline(pipeline, {"docs-steward"})}
        self.assertIn("unknown_role", rules)
        self.assertIn("write_task_missing_allowed_files", rules)
        self.assertIn("task_outputs_missing_contract", rules)

    def test_item_contract_schema_has_required_fields(self) -> None:
        schema = json.loads((ROOT / "genops" / "schemas" / "item_contract.schema.json").read_text(encoding="utf-8"))
        required = set(schema["required"])
        self.assertIn("item_id", required)
        self.assertIn("kind", required)
        self.assertIn("client_assets", required)
        self.assertIn("blockstate", schema["properties"]["client_assets"]["properties"])
        self.assertIn("block_model", schema["properties"]["client_assets"]["properties"])
        verdicts = set(schema["properties"]["acceptance"]["properties"]["human_verdict"]["enum"])
        self.assertEqual(
            verdicts,
            {"pending", "accept", "reject", "accept_with_changes", "not_required", "pause", "reopen_discussion"},
        )
        self.assertEqual(
            set(schema["properties"]["kind"]["enum"]),
            {"plain_item", "block_item", "decor_block_item", "functional_item"},
        )

    def test_patch_guard_blocks_out_of_scope_and_release_files(self) -> None:
        task = TaskSpec.from_dict(
            {
                "id": "patch-generator",
                "agent": "generator-engineer",
                "allowed_files": ["tools/buildgen/**"],
                "forbidden_files": ["CHANGELOG.md"],
            }
        )
        patch = "\n".join(
            [
                "diff --git a/tools/buildgen/sect.py b/tools/buildgen/sect.py",
                "+++ b/tools/buildgen/sect.py",
                "diff --git a/CHANGELOG.md b/CHANGELOG.md",
                "+++ b/CHANGELOG.md",
                "diff --git a/src/main/resources/data/myvillage/structure/x.nbt b/src/main/resources/data/myvillage/structure/x.nbt",
                "+++ b/src/main/resources/data/myvillage/structure/x.nbt",
            ]
        )
        result = patch_guard.check_patch_text(task, patch)
        self.assertEqual(result["status"], "fail")
        reasons = {item["reason"] for item in result["violations"]}
        self.assertIn("forbidden_file", reasons)
        self.assertIn("generated_output_not_allowed", reasons)
        self.assertIn("version_file_requires_release_steward", reasons)

    def test_commander_routes_natural_language_goal(self) -> None:
        recommendation = commander.recommend("用 GenOps 规划一下宗门远景剪影怎么改，先别动代码")
        self.assertEqual(recommendation["intent"], "sect")
        self.assertEqual(recommendation["pipeline"], "genops/pipelines/sect-worldgen.full.yaml")
        self.assertEqual(recommendation["owner_interface"], "natural_language_conversation")
        self.assertEqual(
            recommendation["owner_decision_interface"]["default_surface"],
            "decision_only",
        )

    def test_commander_routes_mod_item_goal(self) -> None:
        recommendation = commander.recommend("用 CRAFT 创建一个新的 myvillage 物品，包含创造栏、贴图和模型")
        self.assertEqual(recommendation["intent"], "mod_item")
        self.assertEqual(recommendation["pipeline"], "genops/pipelines/mod-item.full.yaml")
        self.assertTrue(recommendation["craft_required"])
        self.assertEqual(recommendation["owner_interface"], "natural_language_conversation")

    def test_commander_default_surface_hides_backend_routing(self) -> None:
        recommendation = commander.recommend("用 CRAFT 拆解 candidate_003 这个徽派参考建筑")
        owner_visible = set(recommendation["visibility_policy"]["owner_visible"])
        audit_available = set(recommendation["visibility_policy"]["audit_available"])
        summary_fields = recommendation["frontdoor_summary_fields"]
        self.assertEqual(
            summary_fields,
            [
                "goal_status",
                "scope_or_direction",
                "validation_state",
                "risk_or_blocker",
                "human_decision_needed",
                "next_decision",
            ],
        )
        self.assertEqual(
            recommendation["audit_fields"],
            [
                "run_id",
                "pipeline",
                "task_id",
                "worker_ownership",
                "artifacts",
                "gates",
                "raw_logs",
                "manifest_path",
            ],
        )
        self.assertNotIn("run_id", owner_visible)
        self.assertNotIn("pipeline", owner_visible)
        self.assertNotIn("task_id", owner_visible)
        self.assertIn("human_decision_needed", owner_visible)
        self.assertIn("risk_or_blocker", owner_visible)
        self.assertIn("run_id", audit_available)
        self.assertIn("pipeline", audit_available)
        self.assertIn("task_id", audit_available)
        self.assertIn("manifest_path", audit_available)
        self.assertIn("change names", recommendation["owner_decision_interface"]["commander_owns"])

    def test_commander_state_machine_blocks_for_verdict_then_closes_out(self) -> None:
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
                        "goal": "visual review",
                        "generated_at": "2026-07-06T00:00:00+00:00",
                        "human_verdict": "pending",
                        "tasks": [{"id": "visual-review", "agent": "visual-reviewer", "status": "pass", "gates": []}],
                        "artifact_index": [],
                        "visual": {},
                        "defect_index": {},
                    }
                ),
                encoding="utf-8",
            )
            db_path = str(root / ".genops" / "state.sqlite")
            ctx = commander.build_context(root, db_path, ROOT / "genops" / "commander.yaml")
            state_store.index_run(ctx.conn, root, "review-run")
            intent = commander.upsert_intent(
                ctx,
                intent_id="intent-review",
                title="visual review",
                phase="human_review_pending",
                run_id="review-run",
            )
            manifest = commander.load_manifest(root, "review-run")

            summary = commander.owner_summary(ctx, intent, manifest)
            self.assertTrue(summary["human_decision_needed"])
            self.assertEqual(summary["goal_status"], "human_review_pending")

            result = commander.command_record_verdict(
                Namespace(
                    root=root,
                    db=db_path,
                    config=ROOT / "genops" / "commander.yaml",
                    intent_id="intent-review",
                    run_id="review-run",
                    verdict="accept",
                    summary="视觉证据通过",
                    audit=False,
                )
            )
            self.assertEqual(result["state"], "accepted")
            decisions = list((run_dir / "artifacts" / "decisions").glob("*.json"))
            self.assertEqual(len(decisions), 1)

            intent = commander.intent_by_id(ctx.conn, "intent-review")
            moved = commander.transition_state(ctx, intent, manifest)
            self.assertEqual(moved["phase"], "closeout_ready")
            archived = commander.transition_state(ctx, moved, manifest, closeout=True)
            self.assertEqual(archived["phase"], "archived")

    def test_commander_stop_condition_detects_failing_gate(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            run_dir = root / "reports" / "agent_runs" / "failed-run"
            run_dir.mkdir(parents=True)
            (run_dir / "run_manifest.json").write_text(
                json.dumps(
                    {
                        "run_id": "failed-run",
                        "pipeline": "openspec-change.full",
                        "status": "failed",
                        "goal": "failed validation",
                        "generated_at": "2026-07-06T00:00:00+00:00",
                        "human_verdict": "pending",
                        "tasks": [
                            {
                                "id": "validate",
                                "agent": "regression-steward",
                                "status": "fail",
                                "gates": [{"status": "fail", "command": "pytest"}],
                            }
                        ],
                        "artifact_index": [],
                        "visual": {},
                        "defect_index": {},
                    }
                ),
                encoding="utf-8",
            )
            ctx = commander.build_context(root, ":memory:", ROOT / "genops" / "commander.yaml")
            state_store.index_run(ctx.conn, root, "failed-run")
            intent = commander.upsert_intent(
                ctx,
                intent_id="intent-failed",
                title="failed validation",
                phase="validation",
                run_id="failed-run",
            )
            stops = commander.collect_stop_conditions(ctx, intent, commander.load_manifest(root, "failed-run"))
            self.assertEqual(stops[0].id, "failing_gate")

    def test_codex_custom_agents_exist_for_genops_workers(self) -> None:
        subagents = pipeline_loader.load_mapping(ROOT / "genops" / "subagents.yaml")
        mapping = subagents["custom_agents"]
        self.assertIn("generator-engineer", mapping)
        for role, agent_name in mapping.items():
            path = ROOT / ".codex" / "agents" / f"{agent_name}.toml"
            self.assertTrue(path.exists(), f"missing custom agent for {role}: {path}")
            data = tomllib.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(data["name"], agent_name)
            self.assertTrue(data["description"])
            self.assertTrue(data["developer_instructions"])

    def test_no_op_run_writes_manifest(self) -> None:
        run_id = "genops-test-no-op"
        rc = run_pipeline_main(
            [
                "genops/pipelines/visual-acceptance.full.yaml",
                "--run-id",
                run_id,
                "--goal",
                "test no-op planning pass",
            ]
        )
        self.assertEqual(rc, 0)
        manifest_path = ROOT / "reports" / "agent_runs" / run_id / "run_manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.assertEqual(manifest["pipeline"], "visual-acceptance.full")
        self.assertEqual(manifest["status"], "planning_ready")
        self.assertEqual({task["status"] for task in manifest["tasks"]}, {"plan_materialized"})
        self.assertIn("artifact_index", manifest)
        self.assertEqual(manifest["frontdoor"]["status"], "pass")
        self.assertEqual(manifest["frontdoor"]["protected_paths"], [])
        self.assertIn("reports/agent_runs/genops-test-no-op/visual/reviews/visual_acceptance_review.json", manifest["visual"]["reviews"])
        review = json.loads((ROOT / "reports" / "agent_runs" / run_id / "visual" / "reviews" / "visual_acceptance_review.json").read_text(encoding="utf-8"))
        self.assertEqual(review["human_verdict_state"], "pending")
        self.assertIn("candidate", review)
        self.assertIsInstance(review["scores"], dict)
        self.assertTrue(manifest["tasks"])

    def test_manual_run_is_not_task_pass(self) -> None:
        run_id = "genops-test-manual"
        rc = run_pipeline_main(
            [
                "genops/pipelines/openspec-change.full.yaml",
                "--run-id",
                run_id,
                "--goal",
                "test manual planning pass",
                "--executor",
                "manual",
            ]
        )
        self.assertEqual(rc, 0)
        manifest = json.loads((ROOT / "reports" / "agent_runs" / run_id / "run_manifest.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["status"], "manual_ready")
        self.assertEqual({task["status"] for task in manifest["tasks"]}, {"manual_ready"})
        self.assertEqual(manifest["frontdoor"]["status"], "pass")

    def test_visual_indexer_reads_unified_verdict(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            run_dir = root / "reports" / "agent_runs" / "visual-run"
            verdict_dir = run_dir / "visual" / "human_verdicts"
            verdict_dir.mkdir(parents=True)
            (verdict_dir / "001.json").write_text(
                json.dumps({"verdict": "accepted", "summary": "legacy accepted spelling"}),
                encoding="utf-8",
            )
            visual = visual_indexer.collect(root, run_dir)
            self.assertEqual(visual["latest_human_verdict"], "accept")
            self.assertEqual(visual["human_verdicts"][0]["verdict"], "accept")

    def test_report_writer_uses_visual_verdict_when_cli_is_pending(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            run_dir = root / "reports" / "agent_runs" / "visual-verdict"
            pipeline = PipelineSpec(
                id="visual.full",
                kind="test",
                version=1,
                path="genops/pipelines/visual.full.yaml",
                goal_summary="visual",
                human_review_required=True,
                tasks=[],
                raw={},
            )
            manifest = report_writer.write_final_manifest(
                root=root,
                run_dir=run_dir,
                pipeline=pipeline,
                run_id="visual-verdict",
                goal="visual",
                repo={},
                task_records=[{"id": "review", "agent": "visual-reviewer", "status": "pass", "gates": []}],
                visual={"latest_human_verdict": "accept"},
                defects={},
                human_verdict="pending",
            )
            self.assertEqual(manifest["human_verdict"], "accept")
            self.assertEqual(manifest["status"], "accepted")

    def _write_frontdoor_manifest(self, root: Path, agent: str, artifacts: list[str]) -> Path:
        run_dir = root / "reports" / "agent_runs" / "frontdoor-test"
        run_dir.mkdir(parents=True)
        manifest = {
            "run_id": "frontdoor-test",
            "pipeline": "test.full",
            "status": "pass",
            "goal": "test",
            "tasks": [{"id": "task-a", "agent": agent, "status": "pass", "gates": []}],
            "artifact_index": [
                {
                    "task_id": "task-a",
                    "agent": agent,
                    "status": "pass",
                    "artifacts": artifacts,
                }
            ],
            "visual": {},
            "defect_index": {},
            "human_verdict": "pending",
        }
        path = run_dir / "run_manifest.json"
        path.write_text(json.dumps(manifest), encoding="utf-8")
        return path

    def test_frontdoor_accepts_matching_generator_provenance(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = self._write_frontdoor_manifest(root, "generator-engineer", ["tools/buildgen/sect.py"])
            protected = check_frontdoor.inspect_paths(root, ["tools/buildgen/sect.py"])
            owners = check_frontdoor.load_ownership(root, manifest)
            result = check_frontdoor.validate(protected, owners, allow_bootstrap=False, manifest=manifest)
            self.assertEqual(result["status"], "pass")
            self.assertEqual(result["accepted"][0]["task_id"], "task-a")

    def test_frontdoor_reports_missing_provenance(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            protected = check_frontdoor.inspect_paths(root, ["docs/ai-kb/19_genops.md"])
            result = check_frontdoor.validate(protected, [], allow_bootstrap=False, manifest=None)
            self.assertEqual(result["status"], "fail")
            self.assertEqual(result["findings"][0]["reason"], "missing_provenance")

    def test_frontdoor_rejects_mismatched_release_worker(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = self._write_frontdoor_manifest(root, "docs-steward", ["gradle.properties"])
            protected = check_frontdoor.inspect_paths(root, ["gradle.properties"])
            owners = check_frontdoor.load_ownership(root, manifest)
            result = check_frontdoor.validate(protected, owners, allow_bootstrap=False, manifest=manifest)
            self.assertEqual(result["status"], "fail")
            self.assertEqual(result["findings"][0]["reason"], "mismatched_worker_ownership")

    def test_frontdoor_accepts_release_steward_for_version_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = self._write_frontdoor_manifest(root, "release-steward", ["gradle.properties"])
            protected = check_frontdoor.inspect_paths(root, ["gradle.properties"])
            owners = check_frontdoor.load_ownership(root, manifest)
            result = check_frontdoor.validate(protected, owners, allow_bootstrap=False, manifest=manifest)
            self.assertEqual(result["status"], "pass")

    def test_frontdoor_accepts_item_runtime_and_skill_roles(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = self._write_frontdoor_manifest(
                root,
                "java-runtime-engineer",
                ["src/main/java/com/example/myvillage/item/ModItems.java"],
            )
            protected = check_frontdoor.inspect_paths(
                root,
                ["src/main/java/com/example/myvillage/item/ModItems.java"],
            )
            owners = check_frontdoor.load_ownership(root, manifest)
            result = check_frontdoor.validate(protected, owners, allow_bootstrap=False, manifest=manifest)
            self.assertEqual(result["status"], "pass")

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = self._write_frontdoor_manifest(
                root,
                "resource-asset-steward",
                ["src/main/resources/assets/myvillage/models/item/test.json"],
            )
            protected = check_frontdoor.inspect_paths(
                root,
                ["src/main/resources/assets/myvillage/models/item/test.json"],
            )
            self.assertEqual(protected[0].category, "client-resource")
            owners = check_frontdoor.load_ownership(root, manifest)
            result = check_frontdoor.validate(protected, owners, allow_bootstrap=False, manifest=manifest)
            self.assertEqual(result["status"], "pass")

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = self._write_frontdoor_manifest(
                root,
                "docs-steward",
                [".codex/skills/mod-item-creation/SKILL.md"],
            )
            protected = check_frontdoor.inspect_paths(root, [".codex/skills/mod-item-creation/SKILL.md"])
            owners = check_frontdoor.load_ownership(root, manifest)
            result = check_frontdoor.validate(protected, owners, allow_bootstrap=False, manifest=manifest)
            self.assertEqual(result["status"], "pass")

    def test_frontdoor_requires_manifest_and_task_change_consistency(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = self._write_frontdoor_manifest(root, "docs-steward", ["docs/ai-kb/19_genops.md"])
            task_dir = manifest.parent / "tasks" / "task-a"
            task_dir.mkdir(parents=True)
            (task_dir / "task_result.json").write_text(
                json.dumps(
                    {
                        "task_id": "task-a",
                        "agent": "docs-steward",
                        "status": "pass",
                        "changed_files": ["docs/ai-kb/other.md"],
                    }
                ),
                encoding="utf-8",
            )
            (task_dir / "patch_guard.json").write_text(
                json.dumps({"status": "pass", "changed_files": ["docs/ai-kb/19_genops.md"], "violations": []}),
                encoding="utf-8",
            )

            protected = check_frontdoor.inspect_paths(root, ["docs/ai-kb/19_genops.md"])
            owners = check_frontdoor.load_ownership(root, manifest)
            result = check_frontdoor.validate(protected, owners, allow_bootstrap=False, manifest=manifest)
            self.assertEqual(result["status"], "fail")
            self.assertEqual(result["findings"][0]["reason"], "missing_task_changed_file")

    def test_frontdoor_bootstrap_exception_is_narrow(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            protected = check_frontdoor.inspect_paths(
                root,
                [
                    "openspec/changes/enforce-craft-frontdoor-governance/proposal.md",
                    "openspec/changes/add-visual-reference-structure-pipeline/proposal.md",
                ],
            )
            result = check_frontdoor.validate(protected, [], allow_bootstrap=True, manifest=None)
            self.assertEqual(result["status"], "fail")
            self.assertEqual(result["accepted"][0]["reason"], "bootstrap_exception")
            self.assertEqual(result["findings"][0]["reason"], "missing_provenance")


if __name__ == "__main__":
    unittest.main()
