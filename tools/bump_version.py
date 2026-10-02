#!/usr/bin/env python3
"""Move the mod version in the four places the version rule names.

The rule itself lives in ``openspec/config.yaml`` (``rules.tasks``): a version
bump updates ``gradle.properties``, ``neoforge.mods.toml``, the README jar-name
examples, and ``CHANGELOG.md`` together. This tool applies that mechanically:

    python3 tools/bump_version.py 0.30.0 --dry-run   # print the plan, write nothing
    python3 tools/bump_version.py 0.30.0             # apply it

It refuses a malformed version, a version that is not newer than the current
one, and a tree whose four places disagree before the bump. In README it
rewrites only ``myvillage-<old>.jar`` jar names, so prose such as "since
0.29.0" stays. In CHANGELOG it inserts an empty ``## <new>`` heading above the
newest entry; the body is left for a human (the release gate fails while it is
empty). Standard library only.
"""
from __future__ import annotations

import argparse
import difflib
import re
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE_PROPERTIES = "gradle.properties"
MODS_TOML = "src/main/resources/META-INF/neoforge.mods.toml"
README = "README.md"
CHANGELOG = "CHANGELOG.md"

# The shapes the rule describes: 0.x.y, and a validated fix appends -fix1, -fix2, ...
VERSION_PATTERN = r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:-fix[1-9]\d*)?"
VERSION_RE = re.compile(rf"^{VERSION_PATTERN}$")
GRADLE_VERSION_RE = re.compile(r"^mod_version\s*=\s*(\S+)\s*$", re.MULTILINE)
TOML_VERSION_RE = re.compile(r'^(\s*version\s*=\s*")([^"]*)("\s*)$')
# A jar name in README: myvillage-<version>.jar (the version starts with a digit).
README_JAR_RE = re.compile(r"\bmyvillage-(\d[0-9A-Za-z.\-]*?)\.jar\b")
CHANGELOG_HEADING_RE = re.compile(r"^## (\S+)[ \t]*$")


def parse_version(text: str) -> tuple[int, int, int, int]:
    """(major, minor, patch, fix) for an accepted version; ValueError otherwise."""
    if not VERSION_RE.match(text):
        raise ValueError(f"malformed version {text!r}: expected X.Y.Z or X.Y.Z-fixN (e.g. 0.30.0, 0.29.1, 0.29.0-fix1)")
    base, _, fix = text.partition("-fix")
    major, minor, patch = (int(part) for part in base.split("."))
    return major, minor, patch, int(fix) if fix else 0


def rule_successors(current: str) -> list[str]:
    """The next versions the rule allows: large feature, small feature, next fix."""
    major, minor, patch, fix = parse_version(current)
    return [f"{major}.{minor + 1}.0", f"{major}.{minor}.{patch + 1}", f"{major}.{minor}.{patch}-fix{fix + 1}"]


def read_text(path: Path) -> str:
    with open(path, encoding="utf-8", newline="") as handle:
        return handle.read()


def write_text(path: Path, content: str) -> None:
    with open(path, "w", encoding="utf-8", newline="") as handle:
        handle.write(content)


def toml_mods_version_line(lines: list[str]) -> int | None:
    """Index of the ``version = "..."`` line of the first ``[[mods]]`` table."""
    in_mods = False
    for index, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("["):
            if in_mods:
                return None
            in_mods = stripped == "[[mods]]"
            continue
        if in_mods and TOML_VERSION_RE.match(line.rstrip("\r\n")):
            return index
    return None


def changelog_newest(text: str) -> tuple[str | None, int | None, bool]:
    """(version, line index, has body) of the first ``## <version>`` heading."""
    lines = text.splitlines()
    for index, line in enumerate(lines):
        match = CHANGELOG_HEADING_RE.match(line)
        if match and VERSION_RE.match(match.group(1)):
            body = []
            for following in lines[index + 1:]:
                if following.startswith("## "):
                    break
                body.append(following)
            return match.group(1), index, any(part.strip() for part in body)
    return None, None, False


@dataclass(frozen=True)
class VersionSources:
    """The version as each of the four places states it (None when absent)."""
    gradle: str | None
    toml: str | None
    readme: tuple[str, ...]  # distinct jar-name versions, in order of first appearance
    changelog: str | None
    changelog_has_body: bool

    def problems(self) -> list[str]:
        found = []
        if self.gradle is None:
            found.append(f"{GRADLE_PROPERTIES}: no mod_version line")
        if self.toml is None:
            found.append(f"{MODS_TOML}: no version in the [[mods]] table")
        if not self.readme:
            found.append(f"{README}: no myvillage-<version>.jar name")
        elif len(self.readme) > 1:
            found.append(f"{README}: jar names use several versions: {', '.join(self.readme)}")
        if self.changelog is None:
            found.append(f"{CHANGELOG}: no '## <version>' heading")
        reference = self.gradle
        for label, value in ((MODS_TOML, self.toml), (CHANGELOG + " newest heading", self.changelog)):
            if reference is not None and value is not None and value != reference:
                found.append(f"{label} says {value}, {GRADLE_PROPERTIES} says {reference}")
        if reference is not None and len(self.readme) == 1 and self.readme[0] != reference:
            found.append(f"{README} jar names say {self.readme[0]}, {GRADLE_PROPERTIES} says {reference}")
        for label, value in ((GRADLE_PROPERTIES, self.gradle), (MODS_TOML, self.toml),
                             (CHANGELOG, self.changelog), *((README, v) for v in self.readme)):
            if value is not None and not VERSION_RE.match(value):
                found.append(f"{label}: malformed version {value!r}")
        return found


def read_sources(root: Path) -> VersionSources:
    def optional(relative: str) -> str:
        path = root / relative
        return read_text(path) if path.is_file() else ""

    gradle_match = GRADLE_VERSION_RE.search(optional(GRADLE_PROPERTIES))
    toml_lines = optional(MODS_TOML).splitlines()
    toml_index = toml_mods_version_line(toml_lines)
    toml = TOML_VERSION_RE.match(toml_lines[toml_index]).group(2) if toml_index is not None else None
    readme = tuple(dict.fromkeys(README_JAR_RE.findall(optional(README))))
    changelog, _, has_body = changelog_newest(optional(CHANGELOG))
    return VersionSources(gradle_match.group(1) if gradle_match else None, toml, readme, changelog, has_body)


@dataclass(frozen=True)
class FileEdit:
    relative: str
    before: str
    after: str
    summary: str


class BumpError(Exception):
    pass


def plan_bump(root: Path, new: str) -> tuple[str, list[FileEdit]]:
    """(old version, edits) for moving the tree to ``new``; BumpError when refused."""
    try:
        new_key = parse_version(new)
    except ValueError as exc:
        raise BumpError(str(exc)) from exc
    sources = read_sources(root)
    problems = sources.problems()
    if problems:
        raise BumpError("the four version places disagree before the bump:\n  " + "\n  ".join(problems))
    old = sources.gradle
    if new_key <= parse_version(old):
        raise BumpError(f"{new} is not newer than the current version {old}")

    edits = []
    gradle_before = read_text(root / GRADLE_PROPERTIES)
    match = GRADLE_VERSION_RE.search(gradle_before)
    gradle_after = gradle_before[:match.start(1)] + new + gradle_before[match.end(1):]
    edits.append(FileEdit(GRADLE_PROPERTIES, gradle_before, gradle_after, f"mod_version {old} -> {new}"))

    toml_before = read_text(root / MODS_TOML)
    toml_lines = toml_before.splitlines(keepends=True)
    index = toml_mods_version_line(toml_lines)
    content = toml_lines[index].rstrip("\r\n")
    match = TOML_VERSION_RE.match(content)
    toml_lines[index] = match.group(1) + new + match.group(3) + toml_lines[index][len(content):]
    edits.append(FileEdit(MODS_TOML, toml_before, "".join(toml_lines), f"[[mods]] version {old} -> {new}"))

    readme_before = read_text(root / README)
    old_jar = re.compile(rf"\bmyvillage-{re.escape(old)}\.jar\b")
    readme_after, count = old_jar.subn(f"myvillage-{new}.jar", readme_before)
    edits.append(FileEdit(README, readme_before, readme_after,
                          f"{count} jar names myvillage-{old}.jar -> myvillage-{new}.jar"))

    changelog_before = read_text(root / CHANGELOG)
    if f"## {new}" in (line.rstrip() for line in changelog_before.splitlines()):
        raise BumpError(f"{CHANGELOG} already has a '## {new}' heading")
    _, heading_index, _ = changelog_newest(changelog_before)
    lines = changelog_before.splitlines(keepends=True)
    newline = "\r\n" if lines[heading_index].endswith("\r\n") else "\n"
    lines[heading_index:heading_index] = [f"## {new}{newline}", newline]
    edits.append(FileEdit(CHANGELOG, changelog_before, "".join(lines),
                          f"empty '## {new}' heading above '## {old}' (write its body by hand)"))
    return old, edits


def describe(edit: FileEdit) -> str:
    """A short plan entry: the summary, then a zero-context diff (README: line numbers only)."""
    before, after = edit.before.splitlines(), edit.after.splitlines()
    if edit.relative == README:
        ranges: list[list[int]] = []
        for number in (i + 1 for i, (a, b) in enumerate(zip(before, after)) if a != b):
            if ranges and ranges[-1][1] == number - 1:
                ranges[-1][1] = number
            else:
                ranges.append([number, number])
        spans = ", ".join(str(a) if a == b else f"{a}-{b}" for a, b in ranges)
        return f"{edit.relative}: {edit.summary}\n    lines {spans}"
    diff = difflib.unified_diff(before, after, edit.relative, edit.relative, n=0, lineterm="")
    body = "\n".join("    " + line for line in list(diff)[2:])
    return f"{edit.relative}: {edit.summary}\n{body}"


def main(argv: list[str] | None = None, root: Path = ROOT) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("version", help="the new version, e.g. 0.30.0, 0.29.1, or 0.29.0-fix2")
    parser.add_argument("--dry-run", action="store_true", help="print what would change and write nothing")
    args = parser.parse_args(argv)
    try:
        old, edits = plan_bump(root, args.version)
    except BumpError as exc:
        print(f"bump_version: refused: {exc}", file=sys.stderr)
        return 1
    if args.version not in rule_successors(old):
        print(f"note: the rule's next versions after {old} are {', '.join(rule_successors(old))}; "
              f"{args.version} skips ahead", file=sys.stderr)
    print(f"{'plan' if args.dry_run else 'bump'}: {old} -> {args.version}")
    for edit in edits:
        print(describe(edit))
    if args.dry_run:
        print("dry run: nothing written")
        return 0
    for edit in edits:
        write_text(root / edit.relative, edit.after)
    print(f"written. Next: write the CHANGELOG.md body under '## {args.version}', "
          f"then run python3 tools/release_gate.py")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
