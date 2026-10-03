from __future__ import annotations

import contextlib
import io
import json
import re
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools import combat_data
from tools import new_combat_weapon as tool

RESOURCES = "src/main/resources"
DATA = f"{RESOURCES}/data/myvillage/combat"
LANG = f"{RESOURCES}/assets/myvillage/lang"
SPEAR = "myvillage:lingxiao_spear"
SWORD = "myvillage:qingfeng_sword"
HALBERD = "myvillage:iron_halberd"
HALBERD_STYLE = "myvillage:basic_halberd"
HALBERD_MOVES = ["thrust", "sweep", "hook", "chop", "lunge"]
JADE = "myvillage:jade_sword"

# What the scaffold reads: the combat data, the assets it copies or checks for, the item contracts,
# the item registration, and the tag.
SCAFFOLD_FIXTURE = (
    DATA,
    f"{RESOURCES}/assets/myvillage/combat",
    LANG,
    f"{RESOURCES}/assets/myvillage/player_animations",
    f"{RESOURCES}/assets/myvillage/models/item",
    tool.ITEM_CONTRACTS,
    tool.MOD_ITEMS,
    tool.SWORD_TAG,
)
PLAYBOOK_HEADING = re.compile(r"^### (\d+)\. (.+) \(`([a-z-]+)`\)$")


def copy_into(root: Path, paths: tuple[str, ...]) -> None:
    for relative in paths:
        source = tool.ROOT / relative
        target = root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if source.is_dir():
            shutil.copytree(source, target, dirs_exist_ok=True)
        else:
            shutil.copy2(source, target)


def snapshot(root: Path) -> dict[str, bytes]:
    return {path.relative_to(root).as_posix(): path.read_bytes() for path in sorted(root.rglob("*")) if path.is_file()}


def run(*argv: str) -> tuple[int, str, str]:
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        code = tool.main(list(argv))
    return code, out.getvalue(), err.getvalue()


class PlaybookTest(unittest.TestCase):
    def test_playbook_steps_match_the_tool(self) -> None:
        text = (tool.ROOT / tool.PLAYBOOK).read_text(encoding="utf-8")
        headings = [match.groups() for match in map(PLAYBOOK_HEADING.match, text.splitlines()) if match]
        expected = [(str(number), step.title, step.id) for number, step in enumerate(tool.STEPS, start=1)]
        self.assertEqual(headings, expected)


class ScaffoldTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        copy_into(self.root, SCAFFOLD_FIXTURE)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def read_json(self, relative: str):
        return json.loads((self.root / relative).read_text(encoding="utf-8"))

    def scaffold(self, *extra: str) -> tuple[int, str, str]:
        return run("--root", str(self.root), "scaffold", *extra)

    def test_new_style_renames_every_id_and_keeps_every_value(self) -> None:
        code, out, err = self.scaffold("--from", SPEAR, "--id", HALBERD, "--style", HALBERD_STYLE,
                                       "--moves", ",".join(HALBERD_MOVES))
        self.assertEqual(code, 0, err)
        data = combat_data.load(self.root)
        self.assertEqual(data.issues, ())
        self.assertEqual(data.index["styles"][-1], HALBERD_STYLE)
        self.assertEqual(data.index["weapons"][-1], HALBERD)
        weapon = data.weapons[HALBERD]
        self.assertEqual(weapon["style"], HALBERD_STYLE)
        self.assertEqual(weapon["first_person_rig"], "myvillage:combat/iron_halberd_first_person.json")
        self.assertEqual(weapon["geometry"], "myvillage:combat/iron_halberd_geometry.json")

        style, template = data.styles[HALBERD_STYLE], data.styles["myvillage:basic_spear"]
        move_ids = [f"myvillage:basic_halberd_{n:02d}_{name}" for n, name in enumerate(HALBERD_MOVES, start=1)]
        self.assertEqual([move["id"] for move in style["moves"]], move_ids)
        self.assertEqual(tool.canonical_style(style), tool.canonical_style(template))
        self.assertEqual(set(style["animations"].values()),
                         {f"myvillage:basic_halberd_{role}" for role in template["animations"]})

        rig = self.read_json(f"{RESOURCES}/assets/myvillage/combat/iron_halberd_first_person.json")
        template_rig = self.read_json(f"{RESOURCES}/assets/myvillage/combat/lingxiao_spear_first_person.json")
        self.assertEqual(list(rig["moves"]), move_ids)
        self.assertEqual(tool.canonical_rig(rig, move_ids),
                         tool.canonical_rig(template_rig, [move["id"] for move in template["moves"]]))

        for path in sorted((self.root / LANG).glob("*.json")):
            language = json.loads(path.read_text(encoding="utf-8"))
            keys = ["item.myvillage.iron_halberd"] + [tool.display_key(move_id) for move_id in move_ids]
            for key in keys:
                self.assertTrue(language[key].startswith(tool.PLACEHOLDER), f"{path.name}: {key}")
        self.assertIn("Progress: python3 tools/new_combat_weapon.py progress myvillage:iron_halberd", out)

    def test_shared_style_writes_no_style(self) -> None:
        before_styles = sorted((self.root / DATA / "style").iterdir())
        code, out, err = self.scaffold("--from", SWORD, "--id", JADE)
        self.assertEqual(code, 0, err)
        self.assertEqual(sorted((self.root / DATA / "style").iterdir()), before_styles)
        data = combat_data.load(self.root)
        self.assertEqual(data.issues, ())
        self.assertEqual(data.weapons[JADE]["style"], data.weapons[SWORD]["style"])
        self.assertNotIn(HALBERD_STYLE, data.index["styles"])
        self.assertIn("(shared with the template)", out)

    def test_dry_run_writes_nothing(self) -> None:
        before = snapshot(self.root)
        code, out, err = self.scaffold("--from", SPEAR, "--id", HALBERD, "--style", HALBERD_STYLE, "--dry-run")
        self.assertEqual(code, 0, err)
        self.assertEqual(snapshot(self.root), before)
        self.assertIn("Would write", out)

    def test_refusals_write_nothing(self) -> None:
        code, _, err = self.scaffold("--from", SWORD, "--id", JADE)
        self.assertEqual(code, 0, err)
        before = snapshot(self.root)
        cases = {
            "taken": ("--from", SWORD, "--id", JADE),
            "taken style": ("--from", SPEAR, "--id", HALBERD, "--style", "myvillage:basic_sword"),
            "moves without style": ("--from", SWORD, "--id", HALBERD, "--moves", "a,b"),
            "move count": ("--from", SPEAR, "--id", HALBERD, "--style", HALBERD_STYLE, "--moves", "a,b"),
            "repeated move": ("--from", SPEAR, "--id", HALBERD, "--style", HALBERD_STYLE,
                              "--moves", "a,a,b,c,d"),
            "unknown template": ("--from", "myvillage:nope", "--id", HALBERD),
            "namespace": ("--from", SWORD, "--id", "minecraft:iron_halberd"),
            "taken item": ("--from", SWORD, "--id", SPEAR),
        }
        for name, argv in cases.items():
            with self.subTest(name):
                code, out, err = self.scaffold(*argv)
                self.assertEqual(code, 2, out)
                self.assertTrue(err.startswith("new_combat_weapon: "), err)
                self.assertEqual(snapshot(self.root), before)

    def test_manual_steps_follow_the_playbook_order(self) -> None:
        order = [step.id for step in tool.STEPS]
        for argv, expected in (
                ((SPEAR, HALBERD, HALBERD_STYLE), {"style-data", "third-person-poses"}),
                ((SWORD, JADE, None), {"item-tag", "third-person-poses"})):
            with self.subTest(argv[1]):
                plan = tool.plan_scaffold(self.root, *argv)
                ids = plan.manual_ids()
                self.assertEqual(ids, sorted(ids, key=order.index))
                self.assertTrue(set(ids) <= set(tool.CHECKED_STEPS))
                self.assertTrue(expected <= set(ids))
                self.assertEqual(set(tool.CHECKED_STEPS) - set(ids) - {"weapon-data"},
                                 set() if plan.new_style else {"style-data"})


class ProgressTest(unittest.TestCase):
    def test_shipped_weapons_have_no_open_step(self) -> None:
        for weapon_id in (SWORD, SPEAR):
            with self.subTest(weapon_id):
                result = tool.report(tool.ROOT, weapon_id, fast=True)
                self.assertEqual(result["missing"], [], json.dumps(result["steps"], indent=2))

    def test_scaffolded_copies_are_placeholders(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            copy_into(root, SCAFFOLD_FIXTURE + ("tools", "README.md", "src/main/java",
                                                tool.PARITY_GOLDEN, f"{RESOURCES}/assets/myvillage/sounds.json"))
            code, _, err = run("--root", str(root), "scaffold", "--from", SPEAR, "--id", HALBERD,
                               "--style", HALBERD_STYLE)
            self.assertEqual(code, 0, err)
            # The pinned-list tests run in a subprocess on the whole repository; they are not this test's subject.
            with mock.patch.object(tool.Progress, "_failing_pinned_tests", return_value=[]):
                result = tool.report(root, HALBERD, fast=True)
        status = {step["id"]: step["status"] for step in result["steps"]}
        self.assertEqual(status["weapon-data"], tool.DONE)
        for step_id in ("style-data", "names", "first-person-rig"):
            self.assertEqual(status[step_id], tool.PLACEHOLDER_STATE, step_id)
        for step_id in ("item-contract", "item-registration", "model-and-contract", "third-person-poses",
                        "parity-golden", "item-validator-pin", "readme"):
            self.assertEqual(status[step_id], tool.MISSING, step_id)
        self.assertEqual(status["hit-samples"], tool.BLOCKED)
        self.assertEqual(result["missing"], [step["id"] for step in result["steps"]
                                             if step["status"] in tool.OPEN_STATES])


if __name__ == "__main__":
    unittest.main()
