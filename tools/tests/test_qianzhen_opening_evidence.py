"""新开局采集的证据边界反例；均为离线模型，不能计入原生通过。"""
from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

from tools import qianzhen_opening_evidence as evidence
from tools.qianzhen_opening_collect import OpeningCollector, run_recorded, validate_script, validate_attachment
from tools.qianzhen_opening_environment import sanitize


class EvidenceBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.stage = evidence.fixture()["stages"][0]
        self.actions = [{"sequence": 1, "kind": "mapped_input", "player": "OpeningA", "display": ":111",
                         "window": "42", "pid": 123, "control": "w"}]
        self.checks = [{"id": key, "pass": True, "readback_after_sequence": 1, "readback_id": index,
                        "observation": {"fixture_model": True}}
                       for index, key in enumerate(self.stage["checks"], 1)]
        self.readbacks = [{"id": row["readback_id"], "after_sequence": 1, "player": "OpeningA", "state": row["observation"]}
                          for row in self.checks]
        self.artifacts = {}
        for key in ("actions", "readbacks", "video", "screenshot"):
            path = self.root / key
            path.write_bytes(b"offline model artifact only")
            self.artifacts[key] = {"path": key, "sha256": evidence.digest(path)}
        self.trace = {
            "evidence_tier": "native", "status": "captured", "run_id": "opening-test-001",
            "source": {"commit": "a" * 40, "tracked_status": "", "fixed": True, "request_sha256": "b" * 64,
                       "resource_sha256": {f"resource-{i}": "c" * 64 for i in range(7)},
                       "candidate_identity_sha256": "d" * 64, "scene_version": "model-v1", "dependency_sha256": {"PAL.jar": "e" * 64}},
            "fixture_sha256": evidence.digest(evidence.FIXTURE), "selected": [self.stage["id"]],
            "world": {"fresh": True, "isolation_root": "/content/opening-test-001", "directory": "/content/opening-test-001/run/world"},
            "stages": [{"id": self.stage["id"], "run_id": "opening-test-001", "source_commit": "a" * 40,
                        "request_sha256": "b" * 64, "artifacts": self.artifacts, "actions": self.actions, "checks": self.checks}],
        }
        self.sync_logs()

    def sync_logs(self):
        for key, rows in (("actions", self.actions), ("readbacks", self.readbacks)):
            path = self.root / key
            path.write_text("".join(json.dumps(row) + "\n" for row in rows))
            self.artifacts[key]["sha256"] = evidence.digest(path)

    def audit(self):
        return evidence.audit(self.trace, self.root)

    def assert_rejected(self):
        result = self.audit()
        self.assertEqual("invalid", result["status"])
        self.assertEqual([], result["completed"])
        self.assertEqual(self.trace["selected"], result["unrun"])

    def test_complete_model_remains_artifact_review_not_native_acceptance(self):
        result = self.audit()
        self.assertEqual("trace_valid", result["status"], result["errors"])
        self.assertEqual(["natural_spawn"], result["completed"])
        self.assertEqual("requires_raw_artifact_review", result["native_verdict"])
        self.assertEqual("not_verified", result["player_acceptance"])

    def test_plan_never_completes_native_stages(self):
        with patch.object(evidence, "source_snapshot", return_value={"commit": "a" * 40}):
            result = evidence.plan()
        self.assertEqual("ready_not_run", result["status"])
        self.assertFalse(result["fixed_source"])
        self.assertEqual([], result["completed"])
        self.assertEqual(result["selected"], result["unrun"])
        self.trace["status"] = "ready_not_run"
        self.assert_rejected()

    def test_mixed_commit_request_or_run_is_not_completed(self):
        for field in ("source_commit", "request_sha256", "run_id"):
            original = self.trace["stages"][0][field]
            self.trace["stages"][0][field] = "different"
            self.assert_rejected()
            self.trace["stages"][0][field] = original

    def test_global_source_failure_cannot_leave_stage_completed(self):
        self.trace["source"]["fixed"] = False
        self.assert_rejected()

    def test_failed_or_missing_assertion_stays_unrun(self):
        self.checks[0]["pass"] = False
        self.assert_rejected()
        self.checks[0]["pass"] = True
        self.checks.pop()
        self.assert_rejected()

    def test_failed_execution_cannot_be_repaired_by_complete_checks(self):
        self.trace['stages'][0]['status'] = 'failed'
        self.assert_rejected()

    def test_observation_must_equal_the_bound_raw_readback(self):
        self.checks[0]['observation'] = {'invented_success': True}
        self.assert_rejected()

    def test_no_teleport_grant_or_story_write_in_natural_sequence(self):
        for command in ("tp OpeningA 0 64 0", "give OpeningA minecraft:bread 6", "myvillage story accept OpeningA",
                        "myvillage cultivation setroot OpeningA wood", "execute as OpeningA run tp @s 0 64 0"):
            self.actions.append({"sequence": 2, "kind": "rcon", "command": command})
            self.sync_logs()
            self.assert_rejected()
            self.actions.pop()

    def test_fixture_cannot_stand_in_for_natural_route(self):
        self.actions.append({"sequence": 2, "kind": "fixture", "purpose": "load_legacy_world"})
        self.sync_logs()
        self.assert_rejected()

    def test_input_requires_actual_player_window_identity(self):
        del self.actions[0]["pid"]
        self.sync_logs()
        self.assert_rejected()

    def test_raw_action_log_must_match_summary(self):
        self.actions[0]["control"] = "right_mouse"
        self.assert_rejected()

    def test_stale_readback_cannot_assert_new_action(self):
        self.readbacks[0]["after_sequence"] = 0
        self.sync_logs()
        self.assert_rejected()

    def test_artifact_hash_tamper_and_escape_rejected(self):
        (self.root / "video").write_bytes(b"tampered")
        self.assert_rejected()
        self.artifacts["video"] = {"path": "../external", "sha256": "0" * 64}
        self.assert_rejected()

    def test_existing_world_is_rejected(self):
        self.trace["world"]["directory"] = "/content/qianzhen/run-acceptance/combat_capture_world"
        self.assert_rejected()

    def test_readonly_command_allowlist(self):
        for command in ("data get entity OpeningA Inventory", "data get entity OpeningA Rotation", "myvillage story status OpeningA"):
            evidence.guard_rcon(command)
        for command in ("data modify entity OpeningA Inventory set value []", "myvillage story status OpeningA\ngive OpeningA minecraft:bread"):
            with self.assertRaises(ValueError):
                evidence.guard_rcon(command)


class CollectorExecutionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        log = self.root / "client.log"
        log.write_text("")
        self.game = Mock(display=":111", client_log=log, size=(960, 540))
        self.reader = Mock(return_value='QIANZHEN_STORY_STATUS {"accepted": false, "revision": 0}')

    def collector(self):
        result = OpeningCollector(self.game, self.reader, "OpeningA", self.root / "capture")
        self.addCleanup(lambda: result.stream.close())
        self.addCleanup(lambda: result.readbacks.close())
        result.input.gate = Mock(window="42", pid="123")
        result.input.gate.remaining.return_value = 5
        return result

    def test_physical_key_dispatch_and_effect_are_separate(self):
        collector = self.collector()
        report = collector.run([{"op": "key", "key": "h"}, {"op": "expect_status", "path": ["accepted"], "equals": False}])
        collector.input.gate.ready.assert_called_once_with("opening_h", allow_escape=False)
        collector.input.gate.xdo.assert_called_once_with("key", "h")
        self.assertEqual("captured_not_accepted", report["status"])
        self.assertEqual([0, 1], report["completed"])
        self.assertEqual([], report["unrun"])
        self.assertTrue((collector.out / "readbacks.jsonl").read_text())

    def test_gate_failure_sends_no_key_and_preserves_unrun(self):
        collector = self.collector()
        collector.input.gate.ready.side_effect = TimeoutError("grab failed")
        with self.assertRaises(TimeoutError):
            collector.run([{"op": "key", "key": "h"}, {"op": "key", "key": "w"}])
        collector.input.gate.xdo.assert_not_called()
        report = json.loads((collector.out / "collector.json").read_text())
        self.assertEqual("fail", report["status"])
        self.assertEqual([], report["completed"])
        self.assertEqual([0, 1], report["unrun"])

    def test_held_key_released_even_if_emit_fails(self):
        collector = self.collector()
        collector.input.emit = Mock(side_effect=OSError("disk full"))
        with self.assertRaises(OSError):
            collector.input.hold("w", .01)
        collector.input.gate.xdo.assert_called_once_with("keydown", "w")
        self.game.xdo.assert_called_once_with("keyup", "w", timeout=1)

    def test_failed_menu_gate_does_not_emit_success(self):
        collector = self.collector()
        collector.input.gate.click_menu.side_effect = TimeoutError("menu grabbed")
        with self.assertRaises(TimeoutError):
            collector.input.click_menu("terms", 20, 20)
        self.assertEqual("", (collector.out / "actions.jsonl").read_text())

    def test_menu_escape_requires_menu_and_fresh_gate(self):
        collector = self.collector()
        collector.input.close_menu()
        collector.input.gate.menu.assert_called_once_with("opening_close_menu")
        collector.input.gate.require_menu.assert_called_once_with("pre_escape")
        collector.input.gate.xdo.assert_called_once_with("key", "Escape")

    def test_readback_timeout_is_preserved_with_attempt(self):
        collector = self.collector()
        self.reader.side_effect = TimeoutError("RCON deadline")
        with self.assertRaises(TimeoutError):
            collector.read("myvillage story status OpeningA")
        self.assertIn("RCON deadline", (collector.out / "readbacks.jsonl").read_text())
        self.assertIn("myvillage story status OpeningA", (collector.out / "actions.jsonl").read_text())

    def test_world_attack_uses_ingame_gate_and_physical_mouse(self):
        collector = self.collector()
        collector.run([{"op": "left_click"}])
        collector.input.gate.ready.assert_called_once_with("opening_left_click", allow_escape=False)
        collector.input.gate.xdo.assert_called_once_with("click", "1")
        self.assertIn("left_click", (collector.out / "actions.jsonl").read_text())

    def test_failed_recorder_start_keeps_all_actions_unrun(self):
        collector = self.collector()
        with patch("tools.qianzhen_opening_collect.subprocess.Popen", side_effect=OSError("ffmpeg missing")):
            with self.assertRaises(OSError):
                run_recorded(collector, [{"op": "key", "key": "h"}])
        collector.input.gate.ready.assert_not_called()
        report = json.loads((collector.out / "collector.json").read_text())
        self.assertEqual("fail", report["status"])
        self.assertEqual([0], report["unrun"])
        cleanup = json.loads((collector.out / "recording-exit.json").read_text())
        self.assertIsNone(cleanup["pid"])

    def test_early_recorder_exit_does_not_become_capture_success(self):
        collector = self.collector()
        recorder = Mock(pid=444)
        recorder.poll.side_effect = [None, 2, 2]
        with patch("tools.qianzhen_opening_collect.subprocess.Popen", return_value=recorder), \
                patch("tools.combat_capture.capture.stop_recording", return_value=2) as stop:
            with self.assertRaises(RuntimeError):
                run_recorded(collector, [{"op": "key", "key": "h"}])
        stop.assert_called_once_with(recorder, timeout=10)
        self.assertEqual("fail", json.loads((collector.out / "collector.json").read_text())["status"])

    def test_script_rejects_progress_command_and_unbounded_wait(self):
        for script in ([{"op": "shell", "command": "true"}], [{"op": "readback", "command": "give OpeningA minecraft:bread"}],
                       [{"op": "wait", "seconds": 600}], [{"op": "mouse", "dx": 9999, "dy": 0}]):
            with self.assertRaises(ValueError):
                validate_script(script)

    def test_local_native_attachment_has_no_fallback(self):
        with patch.dict("os.environ", {"MYVILLAGE_CLOUD_PROVIDER": "local"}):
            with self.assertRaises(ValueError):
                validate_attachment({}, self.root)

    def test_existing_output_is_preserved(self):
        (self.root / "capture").mkdir()
        (self.root / "capture/failure.json").write_text("retain")
        with self.assertRaises(FileExistsError):
            OpeningCollector(self.game, self.reader, "OpeningA", self.root / "capture")
        self.assertEqual("retain", (self.root / "capture/failure.json").read_text())

    def test_environment_report_redacts_credentials(self):
        self.assertEqual("java --password=<redacted> --token <redacted>", sanitize("java --password=abc --token def"))


if __name__ == "__main__":
    unittest.main()
