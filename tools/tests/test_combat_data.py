from __future__ import annotations

import copy
import json
import math
import shutil
import tempfile
import unittest
from pathlib import Path

from tools import combat_data

DATA = "src/main/resources/data/myvillage/combat"
STYLE = f"{DATA}/style/basic_sword.json"
WEAPON = f"{DATA}/weapon/qingfeng_sword.json"
SPEAR_STYLE = f"{DATA}/style/basic_spear.json"
SPEAR_MOVES = [
    "myvillage:basic_spear_01_mid_thrust",
    "myvillage:basic_spear_02_sweep",
    "myvillage:basic_spear_03_rising_flick",
    "myvillage:basic_spear_04_overhead_smash",
    "myvillage:basic_spear_05_dragon_lunge",
]


# A mob-sized target standing on the ground: 0.6 wide, 1.8 tall.
TARGET_HALF_WIDTH = 0.3
TARGET_HEIGHT = 1.8
COVERAGE_FROM = 1.0
COVERAGE_STEP = 0.05


# The move's hit capsules as explicit samples (a port of HitboxGenerators.java, same constants).
expand_samples = combat_data.expand_samples


def forward_reach(move: dict) -> float:
    """Farthest +Z the move's hit capsules reach (end plus horizontal radius)."""
    return max(sample["end"][2] + sample["horizontal_radius"] for sample in expand_samples(move))


def segment_touches_box(start, end, low, high) -> bool:
    """Slab test of the segment start->end against an axis-aligned box (CombatGeometry.segmentAabbContact)."""
    first, last = 0.0, 1.0
    for axis in range(3):
        delta = end[axis] - start[axis]
        if abs(delta) < 1e-9:
            if not low[axis] <= start[axis] <= high[axis]:
                return False
            continue
        a, b = (low[axis] - start[axis]) / delta, (high[axis] - start[axis]) / delta
        first, last = max(first, min(a, b)), min(last, max(a, b))
        if first > last:
            return False
    return True


def standing_target_ticks(move: dict, distance: float, lateral: float = 0.0) -> set[int]:
    """Ticks at which a sample touches a standing target whose near face is ``distance`` ahead of the
    attacker's feet (attacker-local frame; the target box is inflated by radius plus tolerance)."""
    hitbox = move["hitbox"]
    ticks = set()
    for sample in expand_samples(move):
        h = sample["horizontal_radius"] + hitbox["horizontal_tolerance"]
        v = sample["vertical_radius"] + hitbox["vertical_tolerance"]
        low = (lateral - TARGET_HALF_WIDTH - h, -v, distance - h)
        high = (lateral + TARGET_HALF_WIDTH + h, TARGET_HEIGHT + v, distance + 2 * TARGET_HALF_WIDTH + h)
        if segment_touches_box(sample["start"], sample["end"], low, high):
            ticks.add(sample["tick"])
    return ticks


class CombatDataTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        shutil.copytree(combat_data.ROOT / DATA, self.root / DATA)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def read(self, relative: str) -> dict:
        return json.loads((self.root / relative).read_text(encoding="utf-8"))

    def write(self, relative: str, document: dict) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(document, indent=2), encoding="utf-8")

    def edit_style(self, change) -> None:
        style = self.read(STYLE)
        change(style)
        self.write(STYLE, style)

    def issues(self) -> list[combat_data.Issue]:
        return list(combat_data.load(self.root).issues)

    def find(self, code: str, field: str | None = None) -> combat_data.Issue:
        issues = self.issues()
        for issue in issues:
            if issue.code == code and (field is None or issue.field == field):
                return issue
        self.fail(f"no {code} issue for {field}: {[str(i) for i in issues]}")

    def test_committed_data_is_valid(self) -> None:
        data = combat_data.load()
        self.assertEqual((), data.issues)
        self.assertEqual(["myvillage:basic_sword", "myvillage:basic_spear"], list(data.styles))
        self.assertEqual(["myvillage:qingfeng_sword", "myvillage:lingxiao_spear"], list(data.weapons))
        self.assertEqual(10, len(list(data.moves())))
        self.assertEqual(combat_data.ROOT / STYLE, data.files["myvillage:basic_sword"])
        self.assertEqual(combat_data.ROOT / SPEAR_STYLE, data.files["myvillage:basic_spear"])

    def test_spear_style_and_weapon(self) -> None:
        data = combat_data.load()
        spear = data.styles["myvillage:basic_spear"]
        self.assertEqual(SPEAR_MOVES, [move["id"] for move in spear["moves"]])
        self.assertEqual([f"combat.myvillage.move.{move_id.split(':')[1]}" for move_id in SPEAR_MOVES],
                         [move["display_key"] for move in spear["moves"]])
        weapon = data.weapons["myvillage:lingxiao_spear"]
        self.assertEqual("myvillage:lingxiao_spear", weapon["item"])
        self.assertEqual("myvillage:basic_spear", weapon["style"])
        # The finisher cannot chain.
        self.assertEqual(spear["moves"][-1]["total_ticks"], spear["moves"][-1]["chain_tick"])

    def test_samples_reach_the_move_range(self) -> None:
        # CombatStepService keeps a light step still when a target is within `range`, so the
        # hit volume must reach at least that far (end + radius + tolerance along +Z).
        for style_id, move in combat_data.load().moves():
            with self.subTest(move=move["id"]):
                self.assertGreaterEqual(forward_reach(move) + move["hitbox"]["horizontal_tolerance"], move["range"])

    def test_standing_target_straight_ahead_is_hit_out_to_range(self) -> None:
        # A mob straight ahead whose near face is anywhere from 1.0 block out to the move's range
        # (the distance CombatStepService compares with `range`) is touched by some sample.
        for style_id, move in combat_data.load().moves():
            steps = int(round((move["range"] - COVERAGE_FROM) / COVERAGE_STEP))
            holes = [round(COVERAGE_FROM + i * COVERAGE_STEP, 2) for i in range(steps + 1)
                     if not standing_target_ticks(move, COVERAGE_FROM + i * COVERAGE_STEP)]
            with self.subTest(move=move["id"]):
                self.assertEqual([], holes, f"{move['id']}: no sample touches a standing target at these distances")

    def test_spear_flick_connects_early_and_smash_late(self) -> None:
        moves = {move["id"]: move for _, move in combat_data.load().moves()}
        flick = moves["myvillage:basic_spear_03_rising_flick"]
        smash = moves["myvillage:basic_spear_04_overhead_smash"]
        # The flick lifts what is in front on its first active tick; the smash pins at range as the
        # head comes down, after its first active tick.
        self.assertIn(flick["active_ticks"][0], standing_target_ticks(flick, 2.0))
        self.assertGreater(min(standing_target_ticks(smash, 3.0)), smash["active_ticks"][0])

    def test_ids_resolve_to_paths(self) -> None:
        self.assertEqual(self.root / STYLE, combat_data.style_file(self.root, "myvillage:basic_sword"))
        self.assertEqual(self.root / WEAPON, combat_data.weapon_file(self.root, "myvillage:qingfeng_sword"))
        self.assertEqual(self.root / "src/main/resources/assets/myvillage/combat/rig.json",
                         combat_data.asset_file(self.root, "myvillage:combat/rig.json"))
        self.assertEqual(("myvillage", "a/b"), combat_data.split_id("myvillage:a/b"))
        with self.assertRaises(ValueError):
            combat_data.split_id("NoNamespace")

    # --- spec scenarios -------------------------------------------------------------------

    def test_misspelled_field_names_file_and_field(self) -> None:
        def misspell(style):
            style["moves"][0]["chain_tik"] = style["moves"][0].pop("chain_tick")
        self.edit_style(misspell)
        issue = self.find("UNKNOWN_FIELD", "moves[0].chain_tik")
        self.assertEqual(STYLE, issue.file)
        self.assertIn(STYLE, str(issue))
        self.assertIn("moves[0].chain_tik", str(issue))
        self.find("SCHEMA", "moves[0].chain_tick")  # and the real field is reported missing
        self.assertNotIn("myvillage:basic_sword", combat_data.load(self.root).styles)

    def test_unlisted_style_file_is_reported(self) -> None:
        self.write(f"{DATA}/style/spear.json", self.read(STYLE))
        issue = self.find("INDEX")
        self.assertEqual(f"{DATA}/style/spear.json", issue.file)
        self.assertIn("myvillage:spear", issue.message)

    def test_listed_but_missing_file_is_reported(self) -> None:
        index = self.read(f"{DATA}/index.json")
        index["weapons"].append("myvillage:missing_blade")
        self.write(f"{DATA}/index.json", index)
        issue = self.find("INDEX", f"weapons[{len(index['weapons']) - 1}]")
        self.assertIn("myvillage:missing_blade", issue.message)

    def test_chain_tick_inside_active_window_is_rejected(self) -> None:
        self.edit_style(lambda style: style["moves"][1].update(chain_tick=6))
        self.assertIn("active end (6)", self.find("INVARIANT", "moves[1].chain_tick").message)

    def test_duplicate_move_id_across_styles_names_both_styles(self) -> None:
        other = self.read(STYLE)
        other["id"] = "myvillage:other_sword"
        other["moves"] = [copy.deepcopy(other["moves"][0])]
        self.write(f"{DATA}/style/other_sword.json", other)
        index = self.read(f"{DATA}/index.json")
        index["styles"].append("myvillage:other_sword")
        self.write(f"{DATA}/index.json", index)
        issue = self.find("DUPLICATE", "moves[0].id")
        self.assertEqual(f"{DATA}/style/other_sword.json", issue.file)
        self.assertIn("myvillage:basic_sword", issue.message)

    # --- invariants -----------------------------------------------------------------------

    def test_timing_invariants(self) -> None:
        cases = (
            (lambda m: m.update(active_ticks=[4, 11]), "moves[0].active_ticks"),
            (lambda m: m.update(active_ticks=[4, 3]), "moves[0].active_ticks"),
            (lambda m: m.update(buffer_start_tick=2), "moves[0].buffer_start_tick"),
            (lambda m: m.update(buffer_start_tick=11), "moves[0].buffer_start_tick"),
            (lambda m: m.update(chain_tick=12), "moves[0].chain_tick"),
            (lambda m: m["step"].update(tick=11), "moves[0].step.tick"),
        )
        original = self.read(STYLE)
        for change, field in cases:
            with self.subTest(field=field):
                style = copy.deepcopy(original)
                change(style["moves"][0])
                self.write(STYLE, style)
                self.find("INVARIANT", field)

    def test_finisher_may_chain_on_its_last_tick(self) -> None:
        # chain_tick == total_ticks is the "cannot chain" finisher and is valid.
        self.assertEqual(20, self.read(STYLE)["moves"][4]["chain_tick"])
        self.assertEqual([], self.issues())

    def test_explicit_samples_are_accepted_and_checked(self) -> None:
        sample = {"tick": 3, "start": [0, 1.2, 0.5], "end": [0, 1.2, 2.9],
                  "horizontal_radius": 0.16, "vertical_radius": 0.16}
        self.edit_style(lambda style: style["moves"][0]["hitbox"].update(samples=[sample, dict(sample, tick=4)]))
        self.assertEqual([], self.issues())
        self.edit_style(lambda style: style["moves"][0]["hitbox"]["samples"][1].update(tick=6))
        self.find("INVARIANT", "moves[0].hitbox.samples[1].tick")
        self.edit_style(lambda style: style["moves"][0]["hitbox"]["samples"][1].update(radius=1))
        self.find("UNKNOWN_FIELD", "moves[0].hitbox.samples[1].radius")

    def test_explicit_samples_must_not_go_back_in_tick(self) -> None:
        sample = {"tick": 3, "start": [0, 1.2, 0.5], "end": [0, 1.2, 2.9],
                  "horizontal_radius": 0.16, "vertical_radius": 0.16}
        thrust = self.read(STYLE)["moves"][0]
        start, end = thrust["active_ticks"]
        self.assertLess(start, end)
        # Repeated ticks keep their list order and are allowed (two per tick, so the counts match).
        ordered = [dict(sample, tick=start), dict(sample, tick=start), dict(sample, tick=end), dict(sample, tick=end)]
        self.edit_style(lambda style: style["moves"][0]["hitbox"].update(samples=ordered))
        self.assertEqual([], self.issues())
        # [start, end, start]: valid ticks for gameplay, but the trail would jump back.
        backwards = [dict(sample, tick=start), dict(sample, tick=end), dict(sample, tick=start)]
        self.edit_style(lambda style: style["moves"][0]["hitbox"].update(samples=backwards))
        # (The style no longer loads, so its weapon also gets a REFERENCE issue.)
        issues = [issue for issue in self.issues() if issue.code == "SAMPLE_ORDER"]
        self.assertEqual(1, len(issues), [str(i) for i in self.issues()])
        issue = issues[0]
        self.assertEqual(STYLE, issue.file)
        self.assertEqual("moves[0].hitbox.samples[2].tick", issue.field)
        self.assertIn(thrust["id"], issue.message)
        self.assertIn("samples[1]", issue.message)
        self.assertIn(STYLE, str(issue))

    def spear_cut_index(self) -> int:
        spear = self.read(SPEAR_STYLE)
        return next(i for i, move in enumerate(spear["moves"])
                    if isinstance(move["hitbox"]["samples"], list)
                    and any(sum(1 for s in move["hitbox"]["samples"] if s["tick"] == t) > 1
                            for t in range(move["active_ticks"][0], move["active_ticks"][1] + 1)))

    def test_committed_shared_ticks_carry_equal_sample_counts(self) -> None:
        index = self.spear_cut_index()  # a committed move with several samples on one tick
        self.assertEqual([], [i for i in self.issues() if i.code == "SAMPLE_COUNT"])
        move = self.read(SPEAR_STYLE)["moves"][index]
        start, end = move["active_ticks"]
        counts = {t: sum(1 for s in move["hitbox"]["samples"] if s["tick"] == t) for t in range(start, end + 1)}
        self.assertEqual(1, len(set(counts.values())), counts)

    def test_uneven_samples_per_tick_are_reported(self) -> None:
        index = self.spear_cut_index()
        spear = self.read(SPEAR_STYLE)
        move = spear["moves"][index]
        start, end = move["active_ticks"]
        # Drop one sample of the last active tick: the counts become n, ..., n - 1.
        last = max(i for i, s in enumerate(move["hitbox"]["samples"]) if s["tick"] == end)
        del move["hitbox"]["samples"][last]
        self.write(SPEAR_STYLE, spear)
        issue = self.find("SAMPLE_COUNT", f"moves[{index}].hitbox.samples")
        self.assertEqual(SPEAR_STYLE, issue.file)
        self.assertIn(move["id"], issue.message)
        self.assertIn(f"tick {end}: ", issue.message)
        self.assertIn(f"{start}..{end}", issue.message)

    def test_shared_tick_with_an_empty_active_tick_is_reported(self) -> None:
        spear = self.read(SPEAR_STYLE)
        index = self.spear_cut_index()
        move = spear["moves"][index]
        start, end = move["active_ticks"]
        self.assertGreater(end - start, 1)
        middle = start + 1
        move["hitbox"]["samples"] = [s for s in move["hitbox"]["samples"] if s["tick"] != middle]
        self.write(SPEAR_STYLE, spear)
        self.assertIn(f"tick {middle}: 0", self.find("SAMPLE_COUNT", f"moves[{index}].hitbox.samples").message)

    def test_single_samples_per_tick_need_no_equal_count(self) -> None:
        # One sample per tick sits on its tick, so a gap between single samples is not uneven.
        sample = {"tick": 3, "start": [0, 1.2, 0.5], "end": [0, 1.2, 2.9],
                  "horizontal_radius": 0.16, "vertical_radius": 0.16}
        style = self.read(STYLE)
        move = next(m for m in style["moves"] if m["active_ticks"][1] - m["active_ticks"][0] >= 2)
        start, end = move["active_ticks"]
        move["hitbox"]["samples"] = [dict(sample, tick=start), dict(sample, tick=end)]
        self.write(STYLE, style)
        self.assertEqual([], [str(i) for i in self.issues()])

    def test_expand_samples_matches_the_generators(self) -> None:
        sword = self.read(STYLE)
        arc = next(m for m in sword["moves"] if m["hitbox"]["samples"].get("generator") == "arc")
        expanded = combat_data.expand_samples(arc)
        start, end = arc["active_ticks"]
        self.assertEqual(list(range(start, end + 1)), [s["tick"] for s in expanded])
        spec = arc["hitbox"]["samples"]
        first = math.radians(spec["start_angle"])
        self.assertAlmostEqual(math.sin(first) * spec["range"], expanded[0]["end"][0])
        self.assertAlmostEqual(math.cos(first) * spec["range"], expanded[0]["end"][2])
        explicit = self.read(SPEAR_STYLE)["moves"][self.spear_cut_index()]
        self.assertIs(explicit["hitbox"]["samples"], combat_data.expand_samples(explicit))

    def test_unknown_sample_generator_is_rejected(self) -> None:
        self.edit_style(lambda style: style["moves"][2]["hitbox"]["samples"].update(generator="spiral"))
        self.find("SCHEMA", "moves[2].hitbox.samples.generator")

    def test_generator_parameters_are_exact(self) -> None:
        self.edit_style(lambda style: style["moves"][1]["hitbox"]["samples"].pop("height"))
        self.find("SCHEMA", "moves[1].hitbox.samples.height")

    def test_types_are_checked(self) -> None:
        self.edit_style(lambda style: style["moves"][0].update(maximum_targets=True))
        self.find("SCHEMA", "moves[0].maximum_targets")
        self.edit_style(lambda style: style["moves"][0].update(maximum_targets=1, kind="slash"))
        self.find("SCHEMA", "moves[0].kind")

    def test_duplicate_move_id_within_a_style(self) -> None:
        self.edit_style(lambda style: style["moves"][3].update(id=style["moves"][1]["id"]))
        self.find("DUPLICATE", "moves[3].id")

    def test_style_id_must_match_the_index(self) -> None:
        self.edit_style(lambda style: style.update(id="myvillage:renamed"))
        self.find("INDEX", "id")

    def test_weapon_unknown_field_and_style_reference(self) -> None:
        weapon = self.read(WEAPON)
        weapon["styel"] = weapon.pop("style")
        self.write(WEAPON, weapon)
        self.find("UNKNOWN_FIELD", "styel")
        weapon["style"] = "myvillage:unlisted"
        del weapon["styel"]
        self.write(WEAPON, weapon)
        self.find("REFERENCE", "style")

    def test_duplicate_key_is_reported(self) -> None:
        path = self.root / STYLE
        text = path.read_text(encoding="utf-8")
        path.write_text(text.replace('"combo_timeout_ticks": 14,',
                                     '"combo_timeout_ticks": 14,\n  "combo_timeout_ticks": 20,', 1),
                        encoding="utf-8")
        self.assertIn("duplicate key 'combo_timeout_ticks'", self.find("JSON").message)

    def test_invalid_json_is_reported(self) -> None:
        (self.root / WEAPON).write_text("{", encoding="utf-8")
        self.assertEqual(WEAPON, self.find("JSON").file)

    def test_load_strict_raises_with_every_issue(self) -> None:
        self.edit_style(lambda style: style["moves"][1].update(chain_tick=6, unknown=1))
        with self.assertRaises(combat_data.CombatDataError) as raised:
            combat_data.load_strict(self.root)
        self.assertEqual(2, len(raised.exception.issues))
        self.assertIn("moves[1].unknown", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
