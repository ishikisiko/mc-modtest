import json
import tempfile
import tomllib
import unittest
from pathlib import Path

import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from tools.genops import check_frontdoor, commander, patch_guard, pipeline_loader, task_graph  # noqa: E402
from tools.genops.models import TaskSpec  # noqa: E402
from tools.genops.run_pipeline import main as run_pipeline_main  # noqa: E402


class GenOpsTests(unittest.TestCase):
    def test_all_pipelines_load_and_sort(self) -> None:
        for path in sorted((ROOT / "genops" / "pipelines").glob("*.yaml")):
            pipeline = pipeline_loader.load_pipeline(path)
            ordered = task_graph.topological_tasks(pipeline)
            self.assertEqual(len(ordered), len(pipeline.tasks))
            self.assertEqual({task.id for task in ordered}, {task.id for task in pipeline.tasks})

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
        self.assertEqual(manifest["status"], "human_review_pending")
        self.assertIn("artifact_index", manifest)
        self.assertTrue(manifest["tasks"])

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
