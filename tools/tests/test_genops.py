import json
import tomllib
import unittest
from pathlib import Path

import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from tools.genops import commander, patch_guard, pipeline_loader, task_graph  # noqa: E402
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
        self.assertTrue(manifest["tasks"])


if __name__ == "__main__":
    unittest.main()
