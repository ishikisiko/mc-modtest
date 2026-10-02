"""tools/combat_preview/tuning.py without numpy: the sweep's --set grammar and JSON paths, the text
edit that writes candidate rigs, frame specs, and the diff's pairing of stills by file name.
Rendering is checked by test_combat_preview_sweep.py under the preview interpreter."""
from __future__ import annotations

import json
import re
import tempfile
import unittest
from pathlib import Path

from tools.combat_preview import cli
from tools.combat_preview import tuning as t

ROOT = Path(__file__).resolve().parents[2]
SPEAR_RIG = ROOT / "src/main/resources/assets/myvillage/combat/lingxiao_spear_first_person.json"
RIG = {"rig": {"shoulder": [0.3, -0.27, -0.05], "arm": {"thickness": 0.42},
               "off_hand": {"grip_diagonal": 30}}, "neutral": {"reach": 0.3}}


class PathTest(unittest.TestCase):
    def test_keys_and_indices(self):
        self.assertEqual(t.parse_path("rig.shoulder[1]"), ["rig", "shoulder", 1])
        self.assertEqual(t.parse_path("moves.myvillage:basic_spear_01_mid_thrust.keys[3].reach"),
                         ["moves", "myvillage:basic_spear_01_mid_thrust", "keys", 3, "reach"])
        self.assertEqual(t.format_path(["rig", "shoulder", 1]), "rig.shoulder[1]")

    def test_bad_paths(self):
        for bad in ("", "rig..arm", "rig.arm[x]", "rig.shoulder[-1]", " rig", "rig.[1]"):
            with self.subTest(bad=bad), self.assertRaises(t.TuningError):
                t.parse_path(bad)

    def test_lookup_existing_and_optional(self):
        self.assertEqual(t.lookup(RIG, t.parse_path("rig.shoulder[1]")), (True, -0.27))
        self.assertEqual(t.lookup(RIG, t.parse_path("rig.off_hand.thickness")), (False, None))

    def test_lookup_names_the_missing_parent(self):
        with self.assertRaisesRegex(t.TuningError, r"rig\.offhand\.thickness: rig\.offhand does not exist"):
            t.lookup(RIG, t.parse_path("rig.offhand.thickness"))
        with self.assertRaisesRegex(t.TuningError, "out of range"):
            t.lookup(RIG, t.parse_path("rig.shoulder[3]"))
        with self.assertRaisesRegex(t.TuningError, "not an object"):
            t.lookup(RIG, t.parse_path("rig.shoulder.x"))
        with self.assertRaisesRegex(t.TuningError, "not a list"):
            t.lookup(RIG, t.parse_path("rig.arm[0]"))

    def test_apply_path_copies_and_introduces_optional_fields(self):
        out = t.apply_path(RIG, t.parse_path("rig.off_hand.thickness"), 0.56)
        self.assertEqual(out["rig"]["off_hand"], {"grip_diagonal": 30, "thickness": 0.56})
        self.assertNotIn("thickness", RIG["rig"]["off_hand"])
        out = t.apply_path(RIG, t.parse_path("rig.shoulder[1]"), -0.3)
        self.assertEqual(out["rig"]["shoulder"], [0.3, -0.3, -0.05])
        self.assertEqual(RIG["rig"]["shoulder"], [0.3, -0.27, -0.05])
        with self.assertRaises(t.TuningError):
            t.apply_path(RIG, t.parse_path("rig.missing.thickness"), 1)


class EditTextTest(unittest.TestCase):
    def changed_lines(self, before, after):
        a, b = before.splitlines(), after.splitlines()
        self.assertEqual(len(a), len(b))
        return [(x, y) for x, y in zip(a, b) if x != y]

    def test_the_shipped_spear_rig_changes_by_one_token(self):
        text = SPEAR_RIG.read_text(encoding="utf-8")
        out = t.edit_text(text, t.parse_path("rig.off_hand.thickness"), 0.5)
        (old, new), = self.changed_lines(text, out)
        self.assertEqual(old.replace('"thickness": 0.56', '"thickness": 0.5'), new)
        self.assertEqual(json.loads(out), t.apply_path(json.loads(text), ["rig", "off_hand", "thickness"], 0.5))

    def test_list_items_and_whole_lists(self):
        text = SPEAR_RIG.read_text(encoding="utf-8")
        out = t.edit_text(text, t.parse_path("rig.shoulder[1]"), -0.3)
        self.assertEqual(self.changed_lines(text, out), [('    "shoulder": [0.3, -0.27, -0.05],',
                                                          '    "shoulder": [0.3, -0.3, -0.05],')])
        out = t.edit_text(text, ["rig", "shoulder"], [0.31, -0.27, -0.05])
        self.assertEqual(len(self.changed_lines(text, out)), 1)

    def test_a_missing_key_is_appended_to_its_object(self):
        self.assertEqual(t.edit_text('{"a": {"b": 1}}', ["a", "c"], 2), '{"a": {"b": 1, "c": 2}}')
        self.assertEqual(t.edit_text('{"a": {}}', ["a", "c"], [1, 2]), '{"a": {"c": [1, 2]}}')

    def test_strings_with_brackets_and_escapes_do_not_confuse_the_spans(self):
        text = '{"note": "a \\"}\\" ]", "x": {"y": [1, {"z": "w"}]}}'
        out = t.edit_text(text, t.parse_path("x.y[1].z"), "v")
        self.assertEqual(out, '{"note": "a \\"}\\" ]", "x": {"y": [1, {"z": "v"}]}}')

    def test_bad_path_is_refused(self):
        with self.assertRaises(t.TuningError):
            t.edit_text('{"a": 1}', ["b", "c"], 1)


class SetGrammarTest(unittest.TestCase):
    def test_values(self):
        path, steps, values = t.parse_set("rig.off_hand.thickness=0.42,0.5,0.56,0.62")
        self.assertEqual((path, steps, values), ("rig.off_hand.thickness", ["rig", "off_hand", "thickness"],
                                                 [0.42, 0.5, 0.56, 0.62]))
        self.assertEqual(t.parse_set("rig.shoulder=[0.3,-0.27,0],[0.32, -0.27, 0]")[2],
                         [[0.3, -0.27, 0], [0.32, -0.27, 0]])
        self.assertEqual(t.parse_set('k.ease=out_back,"a,b",true')[2], ["out_back", "a,b", True])

    def test_bad_specs(self):
        for bad in ("rig.arm.thickness", "rig.arm.thickness=", "rig.arm.thickness=0.4,,0.5",
                    "rig.arm.thickness=0.5,0.50", "rig.shoulder=[1,2", "rig..x=1"):
            with self.subTest(bad=bad), self.assertRaises(t.TuningError):
                t.parse_set(bad)

    def test_true_is_not_one(self):
        self.assertEqual(t.parse_set("a.b=1,true")[2], [1, True])
        self.assertTrue(t.same_value(1, 1.0))
        self.assertFalse(t.same_value(1, True))

    def test_several_sets_make_combinations_first_slowest(self):
        sets = [t.parse_set("a.x=1,2"), t.parse_set("a.y=3,4,5")]
        t.check_sets(sets)
        self.assertEqual(t.combinations(sets), [(1, 3), (1, 4), (1, 5), (2, 3), (2, 4), (2, 5)])

    def test_overlapping_or_too_many_sets_are_refused(self):
        with self.assertRaisesRegex(t.TuningError, "same value"):
            t.check_sets([t.parse_set("rig.shoulder=1"), t.parse_set("rig.shoulder[1]=2")])
        with self.assertRaisesRegex(t.TuningError, "same value"):
            t.check_sets([t.parse_set("a.x=1"), t.parse_set("a.x=2")])
        with self.assertRaisesRegex(t.TuningError, "at most 16"):
            t.check_sets([t.parse_set("a.x=1,2,3,4,5"), t.parse_set("a.y=1,2,3,4")])


class FramesTest(unittest.TestCase):
    def test_ticks_and_key_names(self):
        self.assertEqual(t.parse_frames("1:0, 3:7.5,2:contact"),
                         [(1, 0.0, None), (3, 7.5, None), (2, None, "contact")])
        self.assertEqual(t.frame_name(3, 7.5, None), "m3_t7.5")
        self.assertEqual(t.frame_name(2, 6.0, "contact"), "m2_contact")

    def test_bad_frames(self):
        for bad in ("", "1", "0:1", "x:1", "1:-2", "1:peak", "1:"):
            with self.subTest(bad=bad), self.assertRaises(t.TuningError):
                t.parse_frames(bad)


class PairingTest(unittest.TestCase):
    def test_pairs_by_name_and_lists_the_rest(self):
        paired, only_b, only_a = t.pair_names(["m10_idle", "m2_idle", "m1_idle", "extra"], ["m1_idle", "m2_idle",
                                                                                             "m10_idle", "new"])
        self.assertEqual(paired, ["m1_idle", "m2_idle", "m10_idle"])
        self.assertEqual((only_b, only_a), (["extra"], ["new"]))

    def test_manifest_order_wins(self):
        paired, _, _ = t.pair_names(["m1_contact", "m1_idle"], ["m1_idle", "m1_contact"], ["m1_idle", "m1_contact"])
        self.assertEqual(paired, ["m1_idle", "m1_contact"])

    def test_select(self):
        self.assertEqual(t.select_names(["a", "b", "c"], "c,a.png"), ["c", "a"])
        self.assertEqual(t.select_names(["a", "b"], None), ["a", "b"])
        with self.assertRaisesRegex(t.TuningError, "zz"):
            t.select_names(["a"], "a,zz")

    def test_capture_and_plain_directories(self):
        with tempfile.TemporaryDirectory() as tmp:
            cap = Path(tmp) / "cap"
            (cap / "frames/fp").mkdir(parents=True)
            (cap / "frames/tp_back").mkdir()
            for n in ("m1_idle", "m1_contact"):
                (cap / f"frames/fp/{n}.png").write_bytes(b"")
            (cap / "manifest.json").write_text(json.dumps({"frames": [
                {"view": "fp", "file": "frames/fp/m1_idle.png"}, {"view": "fp", "file": "frames/fp/m1_contact.png"},
                {"view": "tp_back", "file": "frames/tp_back/m1_idle.png"}]}))
            d, order = t.stills_dir(cap)
            self.assertEqual((d, order), (cap / "frames/fp", ["m1_idle", "m1_contact"]))
            self.assertEqual(t.png_names(d), ["m1_contact", "m1_idle"])
            with self.assertRaisesRegex(t.TuningError, r"no view 'side' \(views: fp, tp_back\)"):
                t.stills_dir(cap, "side")
            plain = Path(tmp) / "plain"
            plain.mkdir()
            with self.assertRaisesRegex(t.TuningError, "neither"):
                t.stills_dir(plain)
            (plain / "a.png").write_bytes(b"")
            self.assertEqual(t.stills_dir(plain), (plain, None))
            with self.assertRaises(t.TuningError):
                t.stills_dir(Path(tmp) / "absent")


class DefaultsTest(unittest.TestCase):
    def test_threshold_matches_fp_key_threshold(self):
        src = (ROOT / "tools/combat_preview/fp_rig.py").read_text(encoding="utf-8")
        m = re.search(r'"--key-threshold", type=float, default=([\d.]+)', src)
        self.assertIsNotNone(m, "fp_rig.py no longer declares --key-threshold as expected")
        self.assertEqual(float(m.group(1)), t.KEY_THRESHOLD)

    def test_cli_lists_the_new_commands(self):
        self.assertEqual(cli.TOOLS["sweep"][0], "sweep")
        self.assertEqual(cli.TOOLS["diff"][0], "diff")
        self.assertIn("sweep", cli.usage())


if __name__ == "__main__":
    unittest.main()
