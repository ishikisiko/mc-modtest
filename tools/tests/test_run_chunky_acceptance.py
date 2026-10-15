"""Regression coverage for asynchronous Chunky completion; no Minecraft process."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from tools import run_chunky_acceptance as acceptance


WORLD = "minecraft:overworld"
FINISHED = f"[Chunky] Task finished for {WORLD}. Processed: 81 chunks (100.00%), Total time: 0:00:03"
RUNNING = f"[Chunky] Task running for {WORLD}. Processed: 50 chunks (61.73%)"
IDLE = "[Chunky] No tasks running."


class FakeClock:
    def __init__(self):
        self.now = 0.0

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class ChunkyStage1CompletionTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.profile = Path(temporary.name)
        self.log = self.profile / "logs/latest.log"
        self.log.parent.mkdir(parents=True)
        self.log.write_text("Server ready.\n", encoding="utf-8")
        self.args = SimpleNamespace(chunky_world=WORLD, chunky_radius=64, chunky_timeout=4,
                                    progress_interval=1)
        self.report = {"stages": []}
        self.clock = FakeClock()
        self.progress_calls = 0
        self.commands = []
        for mock in (patch.object(acceptance, "PROFILE_DIR", self.profile),
                     patch.object(acceptance, "write_report"),
                     patch.object(acceptance.time, "monotonic", self.clock.monotonic),
                     patch.object(acceptance.time, "sleep", self.clock.sleep)):
            mock.start()
            self.addCleanup(mock.stop)

    def run_stage(self, progress, on_start=None):
        test = self

        class Rcon:
            def command(self, command):
                test.commands.append(command)
                if command == "chunky start":
                    if on_start is not None:
                        on_start()
                    return f"[Chunky] Task started in {WORLD}."
                if command == "chunky progress":
                    test.progress_calls += 1
                    return progress(test.progress_calls)
                return "[Chunky] Selection changed."

        acceptance.run_stage1_commands(self.args, self.report, Rcon())
        return self.report["stages"][0]

    def append(self, line):
        with self.log.open("a", encoding="utf-8") as handle:
            handle.write(line + "\n")

    def test_initial_idle_then_running_then_finished_waits_for_current_finish_log(self):
        def progress(call):
            if call == 1:
                return IDLE
            if call == 2:
                self.append(RUNNING)
                return RUNNING
            self.append(FINISHED)
            return IDLE

        stage = self.run_stage(progress)
        self.assertEqual(3, self.progress_calls)
        self.assertEqual("passed", stage["status"])
        self.assertEqual(FINISHED, stage["chunky_completion"]["completion_log"])

    def test_stale_completion_before_start_does_not_accept_idle(self):
        self.append(FINISHED)
        with self.assertRaisesRegex(RuntimeError, "Chunky task timed out"):
            self.run_stage(lambda _call: IDLE)
        stage = self.report["stages"][0]
        self.assertEqual("failed", stage["status"])
        self.assertFalse(stage["chunky_completion"]["complete"])
        self.assertEqual(self.args.chunky_timeout, self.clock.now)

    def test_finish_between_start_and_first_poll_needs_no_running_observation(self):
        stage = self.run_stage(lambda _call: IDLE, on_start=lambda: self.append(FINISHED))
        self.assertEqual(1, self.progress_calls)
        self.assertTrue(stage["chunky_completion"]["complete"])

    def test_wrong_world_finish_and_rounded_100_percent_do_not_prove_completion(self):
        def progress(_call):
            self.append(FINISHED.replace(WORLD, "minecraft:the_nether"))
            return RUNNING.replace("61.73%", "100.00%")

        with self.assertRaisesRegex(RuntimeError, "Chunky task timed out"):
            self.run_stage(progress)
        self.assertFalse(self.report["stages"][0]["chunky_completion"]["complete"])

    def test_poll_interval_never_extends_chunky_deadline(self):
        self.args.progress_interval = 30
        with self.assertRaisesRegex(RuntimeError, "Chunky task timed out"):
            self.run_stage(lambda _call: IDLE)
        self.assertEqual(self.args.chunky_timeout, self.clock.now)


if __name__ == "__main__":
    unittest.main()
