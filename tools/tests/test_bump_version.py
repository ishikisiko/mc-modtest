from __future__ import annotations

import contextlib
import io
import tempfile
import unittest
from pathlib import Path

from tools import bump_version as bump

GRADLE = "org.gradle.jvmargs=-Xmx2G\n\nmod_id=myvillage\nmod_version=0.29.0-fix1\nminecraft_version=1.21.1\n"
TOML = '''modLoader = "javafml"
loaderVersion = "[4,)"

[[mods]]
modId = "myvillage"
version = "0.29.0-fix1"
displayName = "MyVillage"

[[dependencies.myvillage]]
modId = "neoforge"
versionRange = "[21.1.0,)"

[[mods]]
modId = "other"
version = "0.29.0-fix1"
'''
README = '''# myvillage

```bash
jar tf build/libs/myvillage-0.29.0-fix1.jar | grep "data/myvillage/structure"
jar tf build/libs/myvillage-0.29.0-fix1.jar | grep "assets/myvillage/combat/"
```

The expected jar is build/libs/myvillage-0.29.0-fix1.jar.

The main hand holds this sword, or since 0.29.0 the spear.
0.29.0-fix1 thickened the off arm.

### Lingxiao Spear (0.29.0)
'''
CHANGELOG = '''# Changelog

## Versioning Rules

See openspec/config.yaml.

## 0.29.0-fix1

### Fixed

- Thicker off arm; 0.29.0 had it thin.

## 0.29.0

- The spear. Built myvillage-0.29.0.jar.
'''


def make_tree(root: Path, **overrides: str) -> None:
    files = {bump.GRADLE_PROPERTIES: GRADLE, bump.MODS_TOML: TOML, bump.README: README, bump.CHANGELOG: CHANGELOG}
    files.update({getattr(bump, key): value for key, value in overrides.items()})
    for relative, text in files.items():
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")


def snapshot(root: Path) -> dict[str, str]:
    return {p.relative_to(root).as_posix(): p.read_text(encoding="utf-8") for p in root.rglob("*") if p.is_file()}


def run(root: Path, *argv: str) -> tuple[int, str, str]:
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        code = bump.main(list(argv), root=root)
    return code, out.getvalue(), err.getvalue()


class VersionShapeTest(unittest.TestCase):
    def test_accepts_the_rule_shapes(self):
        for text in ("0.30.0", "0.29.1", "0.29.0-fix1", "0.29.0-fix12", "1.0.0"):
            bump.parse_version(text)

    def test_rejects_other_shapes(self):
        for text in ("0.30", "v0.30.0", "0.30.0-fix0", "0.30.0-fix", "0.30.0-rc1", "01.2.3", "0.30.0 ", "0.30.0-FIX1"):
            with self.assertRaises(ValueError, msg=text):
                bump.parse_version(text)

    def test_a_fix_sorts_after_its_base_and_before_the_next_patch(self):
        self.assertLess(bump.parse_version("0.29.0"), bump.parse_version("0.29.0-fix1"))
        self.assertLess(bump.parse_version("0.29.0-fix2"), bump.parse_version("0.29.1"))
        self.assertLess(bump.parse_version("0.29.9"), bump.parse_version("0.30.0"))

    def test_rule_successors(self):
        self.assertEqual(bump.rule_successors("0.29.0-fix1"), ["0.30.0", "0.29.1", "0.29.0-fix2"])


class ReadSourcesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def test_agreeing_tree_has_no_problems(self):
        make_tree(self.root)
        sources = bump.read_sources(self.root)
        self.assertEqual((sources.gradle, sources.toml, sources.readme, sources.changelog),
                         ("0.29.0-fix1", "0.29.0-fix1", ("0.29.0-fix1",), "0.29.0-fix1"))
        self.assertTrue(sources.changelog_has_body)
        self.assertEqual(sources.problems(), [])

    def test_toml_version_comes_from_the_first_mods_table_only(self):
        make_tree(self.root, MODS_TOML=TOML.replace('version = "0.29.0-fix1"\ndisplayName', 'version = "0.28.0"\ndisplayName'))
        self.assertEqual(bump.read_sources(self.root).toml, "0.28.0")

    def test_each_kind_of_disagreement_is_named(self):
        make_tree(self.root, README=README + "\njar tf build/libs/myvillage-0.28.0.jar | grep \"x\"\n",
                  CHANGELOG=CHANGELOG.replace("## 0.29.0-fix1", "## 0.29.0-fix2"))
        problems = " | ".join(bump.read_sources(self.root).problems())
        self.assertIn("several versions: 0.29.0-fix1, 0.28.0", problems)
        self.assertIn("CHANGELOG.md newest heading says 0.29.0-fix2", problems)

    def test_newest_changelog_heading_skips_non_version_headings(self):
        version, index, has_body = bump.changelog_newest(CHANGELOG)
        self.assertEqual((version, CHANGELOG.splitlines()[index]), ("0.29.0-fix1", "## 0.29.0-fix1"))
        self.assertTrue(has_body)
        self.assertFalse(bump.changelog_newest("## 0.30.0\n\n## 0.29.0\n\n- x\n")[2])


class BumpTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        make_tree(self.root)

    def tearDown(self):
        self.temp.cleanup()

    def test_dry_run_prints_the_plan_and_writes_nothing(self):
        before = snapshot(self.root)
        code, out, _ = run(self.root, "0.30.0", "--dry-run")
        self.assertEqual(code, 0)
        self.assertEqual(snapshot(self.root), before)
        self.assertIn("+mod_version=0.30.0", out)
        self.assertIn('+version = "0.30.0"', out)
        self.assertIn("3 jar names myvillage-0.29.0-fix1.jar -> myvillage-0.30.0.jar", out)
        self.assertIn("lines 4-5, 8", out)
        self.assertIn("dry run: nothing written", out)

    def test_bump_moves_all_four_places_together(self):
        code, _, _ = run(self.root, "0.30.0")
        self.assertEqual(code, 0)
        sources = bump.read_sources(self.root)
        self.assertEqual((sources.gradle, sources.toml, sources.readme, sources.changelog),
                         ("0.30.0", "0.30.0", ("0.30.0",), "0.30.0"))
        self.assertFalse(sources.changelog_has_body)  # the body is left for a human

    def test_bump_leaves_historical_mentions_alone(self):
        run(self.root, "0.29.1")
        readme = (self.root / bump.README).read_text(encoding="utf-8")
        self.assertEqual(readme, README.replace("myvillage-0.29.0-fix1.jar", "myvillage-0.29.1.jar"))
        self.assertIn("since 0.29.0 the spear", readme)
        self.assertIn("0.29.0-fix1 thickened the off arm.", readme)
        toml = (self.root / bump.MODS_TOML).read_text(encoding="utf-8")
        self.assertIn('modId = "other"\nversion = "0.29.0-fix1"', toml)
        self.assertIn('versionRange = "[21.1.0,)"', toml)
        gradle = (self.root / bump.GRADLE_PROPERTIES).read_text(encoding="utf-8")
        self.assertEqual(gradle, GRADLE.replace("mod_version=0.29.0-fix1", "mod_version=0.29.1"))

    def test_changelog_stub_goes_above_the_newest_entry_and_below_the_preamble(self):
        run(self.root, "0.29.0-fix2")
        changelog = (self.root / bump.CHANGELOG).read_text(encoding="utf-8")
        self.assertEqual(changelog, CHANGELOG.replace("## 0.29.0-fix1\n", "## 0.29.0-fix2\n\n## 0.29.0-fix1\n"))

    def test_refuses_a_malformed_version_without_writing(self):
        before = snapshot(self.root)
        code, _, err = run(self.root, "0.30")
        self.assertEqual(code, 1)
        self.assertIn("malformed version", err)
        self.assertEqual(snapshot(self.root), before)

    def test_refuses_a_version_that_is_not_newer(self):
        for version in ("0.29.0-fix1", "0.29.0", "0.28.5"):
            code, _, err = run(self.root, version)
            self.assertEqual(code, 1, version)
            self.assertIn("is not newer", err)

    def test_refuses_when_the_four_places_disagree(self):
        make_tree(self.root, MODS_TOML=TOML.replace('version = "0.29.0-fix1"\ndisplayName', 'version = "0.29.0"\ndisplayName'))
        before = snapshot(self.root)
        code, _, err = run(self.root, "0.30.0")
        self.assertEqual(code, 1)
        self.assertIn("disagree before the bump", err)
        self.assertIn("neoforge.mods.toml says 0.29.0", err)
        self.assertEqual(snapshot(self.root), before)

    def test_a_version_that_skips_ahead_is_applied_with_a_note(self):
        code, _, err = run(self.root, "0.31.0", "--dry-run")
        self.assertEqual(code, 0)
        self.assertIn("0.30.0, 0.29.1, 0.29.0-fix2", err)

    def test_crlf_line_endings_survive(self):
        make_tree(self.root, CHANGELOG=CHANGELOG.replace("\n", "\r\n"), GRADLE_PROPERTIES=GRADLE.replace("\n", "\r\n"))
        run(self.root, "0.30.0")
        with open(self.root / bump.CHANGELOG, encoding="utf-8", newline="") as handle:
            changelog = handle.read()
        self.assertIn("## 0.30.0\r\n\r\n## 0.29.0-fix1\r\n", changelog)
        self.assertNotIn("\r\r", changelog)
        with open(self.root / bump.GRADLE_PROPERTIES, encoding="utf-8", newline="") as handle:
            self.assertIn("mod_version=0.30.0\r\n", handle.read())


if __name__ == "__main__":
    unittest.main()
