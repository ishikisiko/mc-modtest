#!/usr/bin/env python3
"""Run the documented release checks in order; one result line per step.

    python3 tools/release_gate.py                     # every step, with a fresh Gradle build
    python3 tools/release_gate.py --list              # the steps, in order
    python3 tools/release_gate.py --skip-build        # no Gradle build and no jar steps
    python3 tools/release_gate.py --only 'validate-cultivation-*' --only version-consistency

The steps come from README.md, docs/ai-kb/09_validation_checklist.md, and the
generator ``--check`` modes. Python steps use /usr/bin/python3 (it has PyYAML;
the default python3 may not) and fall back to the current interpreter with a
note. The structure generator runs first (its reports feed the structure
validators), and a later step fails if regenerating changed any file under
src/main/resources. The Gradle build holds the machine's shared heavy-work lock
($MC_HEAVY_LOCK, else .mc-heavy.lock beside the main checkout) and deletes the
current version's jar first, so the jar that the later steps read was written
by this run. No step starts a Minecraft client or server. Per-step output goes
to reports/release_gate/<step>.log. Exit status: 0 when no step failed (skips
are listed), 1 when a step failed, 2 for a bad option. Standard library only.
"""
from __future__ import annotations

import argparse
import hashlib
import os
import re
import shlex
import shutil
import subprocess
import sys
import time
import zipfile
from dataclasses import dataclass
from fnmatch import fnmatchcase
from pathlib import Path
from typing import Callable, TextIO

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools import bump_version  # noqa: E402
from tools.combat_capture import procs  # noqa: E402

PY = "{python}"  # replaced by the step's interpreter
SUITE_PYTHON = "/usr/bin/python3"
LOG_DIR = "reports/release_gate"
GRADLE_ARGV = ("./gradlew", "build", "--console=plain")
GENERATED_ROOT = "src/main/resources"
# The same arguments build.gradle's generateAllStructures task passes.
GENERATE_ARGS = ("tools/generate_all_structures.py", "--mc-version", "1.21.1",
                 "--output", "src/main/resources/data/myvillage/structure")
GENERATE = "generate-all-structures"
BUILD = "gradle-build"
PASS, FAIL, SKIP = "PASS", "FAIL", "SKIP"


# ---------------------------------------------------------------------------------------------
# Interpreters and steps.
# ---------------------------------------------------------------------------------------------

@dataclass(frozen=True)
class Interpreter:
    path: str | None
    note: str = ""


def find_python(*candidates: str | Path | None, fallback: str | None = None) -> Interpreter:
    """The first existing executable candidate; else the fallback with a note; else none."""
    for candidate in candidates:
        if candidate and Path(candidate).is_file() and os.access(candidate, os.X_OK):
            return Interpreter(str(candidate))
    tried = ", ".join(str(c) for c in candidates if c) or "no candidate"
    if fallback:
        return Interpreter(fallback, f"{tried} missing; using {fallback}")
    return Interpreter(None, f"no interpreter found (tried {tried})")


def suite_python(root: Path) -> Interpreter:
    return find_python(SUITE_PYTHON, fallback=sys.executable)


@dataclass
class Outcome:
    status: str
    detail: str = ""
    seconds: float = 0.0
    log: Path | None = None


@dataclass(frozen=True)
class Step:
    name: str
    argv: tuple[str, ...] = ()                       # run from the repo root
    func: Callable[["Gate"], Outcome] | None = None  # or a check done in-process
    after: str | None = None                         # a step whose output this one reads; it must pass first
    python: Callable[[Path], Interpreter] = suite_python
    requires: Callable[[Path], str | None] | None = None  # returns a skip reason, or None to run

    def describe(self) -> str:
        what = shlex.join(self.argv).replace(shlex.quote(PY), "python") if self.argv else (self.func.__doc__ or "")
        return f"{self.name:<44} {f'[after {self.after}] ' if self.after else ''}{what.strip()}"


def tool(script: str, *args: str, name: str | None = None, after: str | None = None) -> Step:
    """A ``tools/<script>.py`` run with the suite interpreter."""
    return Step(name or script.replace("_", "-"), (PY, f"tools/{script}.py", *args), after=after)


def generator_check(script: str) -> Step:
    return tool(script, "--check", name=script.replace("_", "-") + "-check")


def needs_program(program: str) -> Callable[[Path], str | None]:
    return lambda root: None if shutil.which(program) else f"{program} CLI not on PATH"


# ---------------------------------------------------------------------------------------------
# In-process checks.
# ---------------------------------------------------------------------------------------------

def check_versions(gate: "Gate") -> Outcome:
    """gradle.properties, neoforge.mods.toml, README jar names, and the newest CHANGELOG heading agree."""
    sources = bump_version.read_sources(gate.root)
    problems = sources.problems()
    if sources.changelog and not sources.changelog_has_body:
        problems.append(f"{bump_version.CHANGELOG}: the '## {sources.changelog}' entry has no body")
    if problems:
        return Outcome(FAIL, "; ".join(problems))
    return Outcome(PASS, f"all four say {sources.gradle}")


JAR_TF_RE = re.compile(r'jar tf build/libs/myvillage-\d[^\s/]*\.jar \| grep "([^"]+)"')


def readme_jar_patterns(text: str) -> list[str]:
    """Distinct ``jar tf build/libs/myvillage-<v>.jar | grep "<pattern>"`` patterns, in README order."""
    return list(dict.fromkeys(JAR_TF_RE.findall(text)))


def grep_regex(pattern: str) -> re.Pattern[str]:
    """grep's basic-regex reading of ``pattern``: ``( ) { } | + ?`` are literal unless escaped."""
    out = []
    index = 0
    while index < len(pattern):
        char = pattern[index]
        if char == "\\" and index + 1 < len(pattern):
            following = pattern[index + 1]
            out.append(following if following in "(){}|+?" else "\\" + following)
            index += 2
            continue
        out.append("\\" + char if char in "(){}|+?" else char)
        index += 1
    try:
        return re.compile("".join(out))
    except re.error:
        return re.compile(re.escape(pattern))  # not a regex this reading understands; match it literally


def unmatched_patterns(patterns: list[str], names: list[str]) -> list[str]:
    return [p for p in patterns if not any(grep_regex(p).search(name) for name in names)]


def check_readme_jar_listing(gate: "Gate") -> Outcome:
    """Every README 'jar tf ... | grep' pattern matches an entry of the jar built by this run."""
    patterns = readme_jar_patterns((gate.root / bump_version.README).read_text(encoding="utf-8"))
    if not patterns:
        return Outcome(FAIL, "README.md has no 'jar tf build/libs/myvillage-<version>.jar | grep' lines")
    try:
        with zipfile.ZipFile(gate.built_jar) as archive:
            names = archive.namelist()
    except (OSError, zipfile.BadZipFile) as exc:
        return Outcome(FAIL, f"cannot read {gate.built_jar}: {exc}")
    missing = unmatched_patterns(patterns, names)
    if missing:
        return Outcome(FAIL, f"{len(missing)} of {len(patterns)} patterns match nothing in "
                             f"{gate.built_jar.name}: {', '.join(missing)}")
    return Outcome(PASS, f"{len(patterns)} patterns all match {gate.built_jar.name}")


def generated_snapshot(root: Path) -> dict[str, str] | None:
    """{path: content hash or 'deleted'} for every changed or untracked file under GENERATED_ROOT."""
    try:
        proc = subprocess.run(["git", "status", "--porcelain=v1", "-z", "--untracked-files=all", "--", GENERATED_ROOT],
                              cwd=root, capture_output=True, timeout=120)
    except (OSError, subprocess.SubprocessError):
        return None
    if proc.returncode != 0:
        return None
    entries = proc.stdout.decode("utf-8", "surrogateescape").split("\0")
    snapshot = {}
    index = 0
    while index < len(entries):
        entry = entries[index]
        index += 1
        if len(entry) < 4:
            continue
        if entry[0] in "RC":
            index += 1  # the rename source follows
        path = root / entry[3:]
        snapshot[entry[3:]] = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else "deleted"
    return snapshot


def snapshot_drift(before: dict[str, str], after: dict[str, str]) -> list[str]:
    return sorted(path for path in before.keys() | after.keys() if before.get(path) != after.get(path))


def generate_structures(gate: "Gate") -> Outcome:
    """Regenerate the structure resources and reports (what the build's generateAllStructures task runs)."""
    gate.generated_before = generated_snapshot(gate.root)
    interpreter = suite_python(gate.root)
    return gate.run_argv(GENERATE, [interpreter.path, *GENERATE_ARGS], interpreter.note)


def check_generated_resources(gate: "Gate") -> Outcome:
    """The generator runs (this gate's and the build's) left src/main/resources as committed."""
    after = generated_snapshot(gate.root)
    if gate.generated_before is None or after is None:
        return Outcome(SKIP, "git status unavailable, so generator drift in src/main/resources is unknown")
    drift = snapshot_drift(gate.generated_before, after)
    if drift:
        shown = ", ".join(drift[:5]) + (f", ... ({len(drift)} files)" if len(drift) > 5 else "")
        return Outcome(FAIL, f"regenerating changed {shown}; commit the regenerated files or fix the drift")
    return Outcome(PASS, f"regenerating changed nothing under {GENERATED_ROOT}")


def gradle_build(gate: "Gate") -> Outcome:
    """Delete the current version's jar, then ./gradlew build under the heavy-work lock."""
    version = bump_version.read_sources(gate.root).gradle
    if version is None:
        return Outcome(FAIL, "gradle.properties has no mod_version")
    jar = gate.root / "build/libs" / f"myvillage-{version}.jar"
    gate.say(f"  waiting for heavy-work lock {gate.lock_path}" if not procs.lock_is_free(gate.lock_path)
             else f"  heavy-work lock {gate.lock_path}")
    fd = procs.acquire_lock(gate.lock_path, None,
                            on_wait=lambda waited: gate.say(f"  still waiting for the lock ({waited:.0f}s)"))
    try:
        # A missing output forces Gradle's jar task to run, so the jar cannot be an untouched leftover
        # (Gradle keeps the old file when a rebuild produces identical inputs, e.g. a comment-only edit).
        jar.unlink(missing_ok=True)
        gate.say(f"  {shlex.join(gate.gradle_argv)} (log: {gate.log_path(BUILD).relative_to(gate.root)})")
        outcome = gate.run_argv(BUILD, list(gate.gradle_argv))
    finally:
        os.close(fd)
    if outcome.status != PASS:
        return outcome
    if not jar.is_file():
        return Outcome(FAIL, f"the build succeeded but did not write {jar.relative_to(gate.root)}", log=outcome.log)
    newer = sorted(p.name for p in jar.parent.glob("myvillage-*.jar")
                   if p != jar and p.stat().st_mtime >= jar.stat().st_mtime)
    if newer:
        return Outcome(FAIL, f"{', '.join(newer)} is at least as new as {jar.name}; the combat validator "
                             f"inspects the newest jar", log=outcome.log)
    gate.built_jar = jar
    return Outcome(PASS, f"wrote {jar.relative_to(gate.root)}", log=outcome.log)


# ---------------------------------------------------------------------------------------------
# The step list, in run order: cheap checks; the structure generator and the validators that read
# its output; the Python suites; the build; the steps that read the jar it wrote.
# To add a step, add one Step. One that needs another interpreter passes python=..., e.g.
#   Step("combat-preview-parity", (PY, "-m", "unittest", "tools.tests.test_combat_preview_parity"),
#        python=lambda root: find_python(os.environ.get("MC_PREVIEW_PYTHON"), root / ".venv-preview/bin/python")),
# which reports a skip when neither interpreter exists.
# ---------------------------------------------------------------------------------------------

STEPS: tuple[Step, ...] = (
    Step("version-consistency", func=check_versions),
    Step("openspec-specs", ("openspec", "validate", "--specs", "--strict"), requires=needs_program("openspec")),
    generator_check("gen_sword_pal_anims"),
    generator_check("gen_blade_cut_sprite"),
    generator_check("gen_qingfeng_sword_model"),
    generator_check("gen_lingxiao_spear_model"),
    tool("validate_mod_items"),
    tool("validate_custom_entities"),
    tool("validate_rideable_flying_sword"),
    tool("validate_cultivation_core"),
    tool("validate_cultivation_initiation"),
    tool("validate_cultivation_lifespan"),
    tool("validate_cultivation_meditation"),
    tool("validate_cultivation_gain"),
    tool("validate_cultivation_advancement"),
    tool("validate_region_topology"),
    Step(GENERATE, func=generate_structures),
    tool("validate_generated_structures", "src/main/resources/data/myvillage/structure", after=GENERATE),
    tool("validate_mod_block_fallbacks", after=GENERATE),
    tool("validate_plaque_bindings", after=GENERATE),
    tool("validate_compound_library", "--count", "6", after=GENERATE),
    tool("validate_compound_library", "--group", "cultivation_town", "--count", "6",
         name="validate-compound-library-cultivation-town", after=GENERATE),
    tool("validate_compound_library", "--group", "cultivation_sect", "--count", "2",
         name="validate-compound-library-cultivation-sect", after=GENERATE),
    tool("validate_compound_library", "--group", "chinese_huipai_mansion", "--count", "2",
         name="validate-compound-library-huipai", after=GENERATE),
    tool("validate_compound_library", "--group", "ganlan_stilted_house", "--count", "2",
         name="validate-compound-library-ganlan", after=GENERATE),
    tool("buildgen/tests/test_huipai_reference_slice", name="buildgen-test-huipai-reference-slice"),
    tool("buildgen/tests/test_ganlan_stilted_house", name="buildgen-test-ganlan-stilted-house"),
    tool("buildgen/tests/test_pagoda_landmark", name="buildgen-test-pagoda-landmark"),
    tool("validate_civic_library", after=GENERATE),
    tool("validate_town_generation", after=GENERATE),
    tool("validate_runtime_town_plan", after=GENERATE),
    tool("validate_sect_generation", after=GENERATE),
    tool("check_style_policy", after=GENERATE),
    tool("check_cultivation_forms", after=GENERATE),
    Step("python-tests", (PY, "-m", "unittest", "discover", "-s", "tools/tests")),
    Step(BUILD, func=gradle_build),
    Step("generated-resources-current", func=check_generated_resources, after=GENERATE),
    tool("validate_sword_combat_foundation", after=BUILD),
    tool("validate_spirit_stone_resources", "--require-current-jar", after=BUILD),
    tool("validate_guideme_cultivation_guide", "--require-current-jar", after=BUILD),
    Step("readme-jar-listing", func=check_readme_jar_listing, after=BUILD),
)


class SelectionError(ValueError):
    pass


def select_steps(steps: tuple[Step, ...], patterns: list[str]) -> tuple[list[Step], list[str]]:
    """Steps matching any glob in ``patterns`` (all when empty), in list order, plus notes.

    A selected step pulls in the step it runs after (e.g. a jar reader pulls in gradle-build), so it
    never reads output this run did not produce."""
    if not patterns:
        return list(steps), []
    unknown = [p for p in patterns if not any(fnmatchcase(s.name, p) for s in steps)]
    if unknown:
        raise SelectionError(f"no step matches {', '.join(unknown)} (see --list)")
    by_name = {s.name: s for s in steps}
    chosen = {s.name for s in steps if any(fnmatchcase(s.name, p) for p in patterns)}
    added = []
    pending = sorted(chosen)
    while pending:
        step = by_name[pending.pop()]
        if step.after and step.after not in chosen:
            if step.after not in by_name:
                raise SelectionError(f"{step.name} runs after unknown step {step.after}")
            chosen.add(step.after)
            added.append(step.after)
            pending.append(step.after)
    selected = [s for s in steps if s.name in chosen]
    notes = [f"added {dep}: {', '.join(s.name for s in selected if s.after == dep)} need its output"
             for dep in sorted(added)]
    return selected, notes


# ---------------------------------------------------------------------------------------------
# Running.
# ---------------------------------------------------------------------------------------------

class Gate:
    def __init__(self, root: Path = ROOT, *, skip_build: bool = False, out: TextIO = sys.stdout,
                 gradle_argv: tuple[str, ...] = GRADLE_ARGV, lock_path: Path | None = None):
        self.root = Path(root)
        self.skip_build = skip_build
        self.out = out
        self.gradle_argv = gradle_argv
        self.lock_path = lock_path or procs.default_lock_path(self.root)
        self.log_dir = self.root / LOG_DIR
        self.built_jar: Path | None = None
        self.generated_before: dict[str, str] | None = None
        self.outcomes: dict[str, Outcome] = {}
        self._line_open = False

    def say(self, text: str) -> None:
        if self._line_open:
            self.out.write("\n")
            self._line_open = False
        self.out.write(text + "\n")
        self.out.flush()

    def log_path(self, name: str) -> Path:
        return self.log_dir / f"{name}.log"

    def run_argv(self, name: str, argv: list[str], note: str = "") -> Outcome:
        log = self.log_path(name)
        log.parent.mkdir(parents=True, exist_ok=True)
        with open(log, "w", encoding="utf-8") as handle:
            handle.write(f"$ {shlex.join(argv)}\n")
            if note:
                handle.write(f"# {note}\n")
            handle.flush()
            try:
                code = subprocess.run(argv, cwd=self.root, stdin=subprocess.DEVNULL, stdout=handle,
                                      stderr=subprocess.STDOUT).returncode
            except OSError as exc:
                handle.write(f"{exc}\n")
                return Outcome(FAIL, f"cannot run {argv[0]}: {exc}", log=log)
        if code != 0:
            return Outcome(FAIL, "; ".join(filter(None, (f"exit {code}", note))), log=log)
        return Outcome(PASS, note, log=log)

    def run_step(self, step: Step) -> Outcome:
        if step.name == BUILD and self.skip_build:
            return Outcome(SKIP, "--skip-build")
        if step.after:
            needed = self.outcomes.get(step.after)
            if needed is None:
                return Outcome(SKIP, f"{step.after} did not run")
            if needed.status != PASS:
                return Outcome(SKIP, f"{step.after} {'was skipped' if needed.status == SKIP else 'failed'}")
        if step.requires is not None:
            reason = step.requires(self.root)
            if reason:
                return Outcome(SKIP, reason)
        if step.func is not None:
            return step.func(self)
        argv, note = list(step.argv), ""
        if PY in argv:
            interpreter = step.python(self.root)
            if interpreter.path is None:
                return Outcome(SKIP, interpreter.note)
            argv, note = [interpreter.path if a == PY else a for a in argv], interpreter.note
        return self.run_argv(step.name, argv, note)

    def run(self, steps: list[Step]) -> list[tuple[Step, Outcome]]:
        self.log_dir.mkdir(parents=True, exist_ok=True)
        for old in self.log_dir.glob("*.log"):
            old.unlink()
        results = []
        width = len(str(len(steps)))
        for number, step in enumerate(steps, 1):
            prefix = f"[{number:>{width}}/{len(steps)}] {step.name:<44}"
            self.out.write(prefix)
            self.out.flush()
            self._line_open = True
            start = time.monotonic()
            outcome = self.run_step(step)
            outcome.seconds = time.monotonic() - start
            self.outcomes[step.name] = outcome
            if not self._line_open:
                self.out.write(prefix)
            self._line_open = False
            self.out.write(f" {outcome.status} {outcome.seconds:7.1f}s  {outcome.detail}\n")
            if outcome.status == FAIL and outcome.log is not None and outcome.log.is_file():
                tail = outcome.log.read_text(encoding="utf-8", errors="replace").splitlines()[-15:]
                self.out.write("".join(f"      | {line}\n" for line in tail))
            self.out.flush()
            results.append((step, outcome))
        return results


def summarize(results: list[tuple[Step, Outcome]], seconds: float, root: Path) -> tuple[str, int]:
    counts = {status: sum(1 for _, o in results if o.status == status) for status in (PASS, FAIL, SKIP)}
    verdict = FAIL if counts[FAIL] else PASS
    minutes, secs = divmod(int(round(seconds)), 60)
    lines = [f"release gate: {verdict} ({counts[PASS]} passed, {counts[FAIL]} failed, {counts[SKIP]} skipped) "
             f"in {minutes}m{secs:02d}s"]
    for step, outcome in results:
        if outcome.status == FAIL:
            where = f" (log: {outcome.log.relative_to(root)})" if outcome.log else ""
            lines.append(f"  failed:  {step.name}{where}")
        elif outcome.status == SKIP:
            lines.append(f"  skipped: {step.name}: {outcome.detail}")
    return "\n".join(lines), 1 if counts[FAIL] else 0


def main(argv: list[str] | None = None, *, steps: tuple[Step, ...] = STEPS, root: Path = ROOT,
         out: TextIO = sys.stdout, gate_options: dict | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--list", action="store_true", help="print the steps in order and exit")
    parser.add_argument("--only", action="append", default=[], metavar="GLOB",
                        help="run only steps whose name matches (repeatable, comma-separated); "
                             "a step that reads the jar pulls in gradle-build")
    parser.add_argument("--skip-build", action="store_true",
                        help="skip gradle-build and the steps that read its jar")
    args = parser.parse_args(argv)
    if args.list:
        for step in steps:
            out.write(step.describe() + "\n")
        return 0
    patterns = [p.strip() for value in args.only for p in value.split(",") if p.strip()]
    try:
        selected, notes = select_steps(steps, patterns)
    except SelectionError as exc:
        parser.print_usage(sys.stderr)
        print(f"release_gate: {exc}", file=sys.stderr)
        return 2
    gate = Gate(root, skip_build=args.skip_build, out=out, **(gate_options or {}))
    interpreter = suite_python(root)
    out.write(f"release gate in {root}\npython steps: {interpreter.path}"
              f"{' (' + interpreter.note + ')' if interpreter.note else ''}\n")
    for note in notes:
        out.write(f"note: {note}\n")
    start = time.monotonic()
    results = gate.run(selected)
    text, code = summarize(results, time.monotonic() - start, gate.root)
    out.write(text + "\n")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
