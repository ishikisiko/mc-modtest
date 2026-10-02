from __future__ import annotations

import io
import os
import sys
import tempfile
import time
import unittest
import zipfile
from pathlib import Path
from unittest import mock

from tools import release_gate as gate
from tools.tests.test_bump_version import CHANGELOG, README, make_tree

PASSING = (sys.executable, "-c", "pass")
FAILING = (sys.executable, "-c", "print('boom'); raise SystemExit(3)")


def write_jar(path: Path, names: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        for name in names:
            archive.writestr(name, "" if name.endswith("/") else "x")


class TempRoot(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def new_gate(self, **options) -> gate.Gate:
        options.setdefault("lock_path", self.root / "heavy.lock")
        options.setdefault("out", io.StringIO())
        return gate.Gate(self.root, **options)

    def run_main(self, steps, *argv: str) -> tuple[int, str]:
        out = io.StringIO()
        code = gate.main(list(argv), steps=steps, root=self.root, out=out,
                         gate_options={"lock_path": self.root / "heavy.lock"})
        return code, out.getvalue()


class ReadmePatternTest(unittest.TestCase):
    def test_extracts_distinct_patterns_in_order(self):
        text = (README + 'jar tf build/libs/myvillage-0.29.0-fix1.jar | grep "data/myvillage/structure"\n'
                'jar tf build/libs/myvillage-0.28.0.jar | grep "assets/old"\n'
                'jar tf some/other.jar | grep "ignored"\n')
        self.assertEqual(gate.readme_jar_patterns(text),
                         ["data/myvillage/structure", "assets/myvillage/combat/", "assets/old"])

    def test_grep_reads_patterns_as_basic_regex_substrings(self):
        names = ["assets/myvillage/combat/qingfeng_sword_geometry.json", "data/a+b(c).json"]
        self.assertEqual(gate.unmatched_patterns(["combat/qingfeng", "qingfeng.sword", "a+b(c)", "a\\+b"], names),
                         ["a\\+b"])  # '.' is any character; '+' and '(' are literal; '\+' is GNU "one or more"
        self.assertEqual(gate.unmatched_patterns(["geometry\\.json", "geometry\\.jsonx", "^assets/"], names),
                         ["geometry\\.jsonx"])

    def test_listing_reports_only_the_patterns_with_no_entry(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            make_tree(root)
            jar = root / "build/libs/myvillage-0.29.0-fix1.jar"
            write_jar(jar, ["data/myvillage/structure/house.nbt", "assets/myvillage/lang/en_us.json"])
            g = gate.Gate(root, lock_path=root / "lock", out=io.StringIO())
            g.built_jar = jar
            outcome = gate.check_readme_jar_listing(g)
            self.assertEqual(outcome.status, gate.FAIL)
            self.assertIn("1 of 2 patterns", outcome.detail)
            self.assertIn("assets/myvillage/combat/", outcome.detail)
            write_jar(jar, ["data/myvillage/structure/house.nbt", "assets/myvillage/combat/"])
            self.assertEqual(gate.check_readme_jar_listing(g).status, gate.PASS)


class VersionStepTest(TempRoot):
    def test_agreeing_versions_pass(self):
        make_tree(self.root)
        outcome = gate.check_versions(self.new_gate())
        self.assertEqual((outcome.status, outcome.detail), (gate.PASS, "all four say 0.29.0-fix1"))

    def test_a_readme_jar_name_on_another_version_fails(self):
        make_tree(self.root, README=README.replace("myvillage-0.29.0-fix1.jar | grep \"assets",
                                                   "myvillage-0.29.0.jar | grep \"assets"))
        outcome = gate.check_versions(self.new_gate())
        self.assertEqual(outcome.status, gate.FAIL)
        self.assertIn("README.md: jar names use several versions", outcome.detail)

    def test_an_empty_newest_changelog_entry_fails(self):
        make_tree(self.root, CHANGELOG=CHANGELOG.replace("## 0.29.0-fix1\n", "## 0.30.0\n\n## 0.29.0-fix1\n"),
                  GRADLE_PROPERTIES="mod_version=0.30.0\n",
                  MODS_TOML='[[mods]]\nversion = "0.30.0"\n',
                  README="build/libs/myvillage-0.30.0.jar\n")
        outcome = gate.check_versions(self.new_gate())
        self.assertEqual(outcome.status, gate.FAIL)
        self.assertEqual(outcome.detail, "CHANGELOG.md: the '## 0.30.0' entry has no body")


class SelectionTest(unittest.TestCase):
    STEPS = (
        gate.Step("alpha", PASSING),
        gate.Step("gen", PASSING),
        gate.Step("reads-gen", PASSING, after="gen"),
        gate.Step("build", PASSING, after="gen"),
        gate.Step("jar-a", PASSING, after="build"),
        gate.Step("jar-b", PASSING, after="build"),
    )

    def names(self, patterns):
        return [s.name for s in gate.select_steps(self.STEPS, patterns)[0]]

    def test_no_pattern_selects_everything_in_order(self):
        self.assertEqual(self.names([]), [s.name for s in self.STEPS])

    def test_globs_select_and_keep_list_order(self):
        self.assertEqual(self.names(["gen", "alpha"]), ["alpha", "gen"])

    def test_a_dependent_pulls_in_its_producer_transitively(self):
        selected, notes = gate.select_steps(self.STEPS, ["jar-*"])
        self.assertEqual([s.name for s in selected], ["gen", "build", "jar-a", "jar-b"])
        self.assertEqual(notes, ["added build: jar-a, jar-b need its output", "added gen: build need its output"])

    def test_an_unknown_pattern_is_an_error(self):
        with self.assertRaises(gate.SelectionError):
            gate.select_steps(self.STEPS, ["alpha", "nope*"])

    def test_real_step_names_are_unique_and_dependencies_exist(self):
        names = [s.name for s in gate.STEPS]
        self.assertEqual(len(names), len(set(names)))
        for step in gate.STEPS:
            if step.after:
                self.assertLess(names.index(step.after), names.index(step.name), step.name)

    def test_real_jar_readers_run_after_the_build(self):
        readers = {s.name for s in gate.STEPS if s.after == gate.BUILD}
        self.assertTrue({"validate-sword-combat-foundation", "readme-jar-listing"} <= readers)


class RunTest(TempRoot):
    def test_all_passing_exits_zero(self):
        code, out = self.run_main((gate.Step("one", PASSING), gate.Step("two", PASSING)))
        self.assertEqual(code, 0)
        self.assertIn("release gate: PASS (2 passed, 0 failed, 0 skipped)", out)

    def test_a_failure_exits_one_and_later_steps_still_run(self):
        code, out = self.run_main((gate.Step("bad", FAILING), gate.Step("good", PASSING)))
        self.assertEqual(code, 1)
        self.assertIn("bad", out)
        self.assertIn("FAIL", out)
        self.assertIn("| boom", out)  # the tail of the failing step's log
        self.assertIn("1 passed, 1 failed", out)
        self.assertIn("failed:  bad (log: reports/release_gate/bad.log)", out)

    def test_skips_are_listed_but_do_not_fail(self):
        steps = (gate.Step("tool", PASSING, requires=lambda root: "frobnicate CLI not on PATH"),)
        code, out = self.run_main(steps)
        self.assertEqual(code, 0)
        self.assertIn("skipped: tool: frobnicate CLI not on PATH", out)

    def test_a_dependent_of_a_failed_step_is_skipped(self):
        steps = (gate.Step("producer", FAILING), gate.Step("consumer", PASSING, after="producer"))
        code, out = self.run_main(steps)
        self.assertEqual(code, 1)
        self.assertIn("skipped: consumer: producer failed", out)

    def test_skip_build_skips_the_build_and_its_readers_only(self):
        steps = (gate.Step("cheap", PASSING), gate.Step(gate.BUILD, FAILING),
                 gate.Step("reader", PASSING, after=gate.BUILD))
        code, out = self.run_main(steps, "--skip-build")
        self.assertEqual(code, 0)
        self.assertIn("1 passed, 0 failed, 2 skipped", out)
        self.assertIn(f"skipped: reader: {gate.BUILD} was skipped", out)

    def test_bad_option_exits_two(self):
        with mock.patch("sys.stderr", io.StringIO()):
            self.assertEqual(self.run_main((gate.Step("one", PASSING),), "--only", "zzz")[0], 2)

    def test_an_interpreter_that_cannot_be_found_is_a_skip(self):
        step = gate.Step("parity", (gate.PY, "-c", "pass"),
                         python=lambda root: gate.find_python(None, root / ".venv-preview/bin/python"))
        code, out = self.run_main((step,))
        self.assertEqual(code, 0)
        self.assertIn("skipped: parity: no interpreter found (tried", out)

    def test_python_steps_fall_back_to_the_current_interpreter_with_a_note(self):
        found = gate.find_python(self.root / "missing-python", fallback=sys.executable)
        self.assertEqual(found.path, sys.executable)
        self.assertIn("missing-python missing; using", found.note)
        self.assertEqual(gate.find_python(sys.executable, fallback="/x").note, "")


class FreshJarTest(TempRoot):
    def setUp(self):
        super().setUp()
        (self.root / "gradle.properties").write_text("mod_version=1.2.3\n", encoding="utf-8")
        self.jar = self.root / "build/libs/myvillage-1.2.3.jar"

    def fake_gradle(self, script: str) -> tuple[str, ...]:
        return (sys.executable, "-c", script)

    def test_an_untouched_old_jar_is_never_taken_as_fresh(self):
        write_jar(self.jar, ["old"])
        # Gradle reporting success without rewriting the jar (its up-to-date case).
        outcome = gate.gradle_build(self.new_gate(gradle_argv=PASSING))
        self.assertEqual(outcome.status, gate.FAIL)
        self.assertIn("did not write build/libs/myvillage-1.2.3.jar", outcome.detail)
        self.assertFalse(self.jar.exists())

    def test_a_jar_written_by_the_build_passes(self):
        write_jar(self.jar, ["old"])
        old_time = time.time() - 3600
        os.utime(self.jar, (old_time, old_time))
        g = self.new_gate(gradle_argv=self.fake_gradle(
            "import zipfile; zipfile.ZipFile('build/libs/myvillage-1.2.3.jar', 'w').writestr('new', 'x')"))
        outcome = gate.gradle_build(g)
        self.assertEqual(outcome.status, gate.PASS, outcome.detail)
        self.assertEqual(g.built_jar, self.jar)
        self.assertGreater(self.jar.stat().st_mtime, old_time)
        with zipfile.ZipFile(self.jar) as archive:
            self.assertEqual(archive.namelist(), ["new"])

    def test_an_older_version_jar_with_a_newer_mtime_fails(self):
        other = self.root / "build/libs/myvillage-1.2.2.jar"
        write_jar(other, ["x"])
        future = time.time() + 3600
        os.utime(other, (future, future))
        g = self.new_gate(gradle_argv=self.fake_gradle(
            "import zipfile; zipfile.ZipFile('build/libs/myvillage-1.2.3.jar', 'w').writestr('new', 'x')"))
        outcome = gate.gradle_build(g)
        self.assertEqual(outcome.status, gate.FAIL)
        self.assertIn("myvillage-1.2.2.jar is at least as new", outcome.detail)

    def test_a_failed_build_fails_and_releases_the_lock(self):
        outcome = gate.gradle_build(self.new_gate(gradle_argv=FAILING))
        self.assertEqual(outcome.status, gate.FAIL)
        self.assertTrue(gate.procs.lock_is_free(self.root / "heavy.lock"))


class GeneratedDriftTest(unittest.TestCase):
    def test_drift_lists_new_changed_and_reverted_paths(self):
        before = {"a.nbt": "h1", "b.nbt": "h2", "c.nbt": "deleted"}
        after = {"a.nbt": "h1", "b.nbt": "h3", "d.nbt": "h4"}
        self.assertEqual(gate.snapshot_drift(before, after), ["b.nbt", "c.nbt", "d.nbt"])
        self.assertEqual(gate.snapshot_drift(before, dict(before)), [])


if __name__ == "__main__":
    unittest.main()
