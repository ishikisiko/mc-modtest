"""The `sweep` and `diff` commands of tools/combat_preview with real rendering: a swept rig value
changes pixels where its arm is drawn, a path the renderer ignores is reported, a value the loader
rejects names the candidate, and `diff` counts known changes in synthetic stills.

Needs numpy and pillow, so it skips under an interpreter without them. Run it with the preview
interpreter (see tools/combat_preview/README.md):

    .venv-preview/bin/python -m unittest tools.tests.test_combat_preview_sweep
"""
from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

MISSING = [m for m in ("numpy", "PIL") if importlib.util.find_spec(m) is None]
SKIP_REASON = (f"{sys.executable} has no {' or '.join(MISSING)}; run this test with the preview interpreter: "
               ".venv-preview/bin/python -m unittest tools.tests.test_combat_preview_sweep "
               "(setup: python3 -m venv .venv-preview && "
               ".venv-preview/bin/pip install -r tools/combat_preview/requirements.txt)")
SPEAR = "myvillage:lingxiao_spear"
OFF_ARM_TAGS = (5, 6, 7)  # fp_rig.TAGS off_upper, off_forearm, off_fist


def quiet(fn, *args):
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        return fn(*args)


@unittest.skipIf(MISSING, SKIP_REASON)
class SweepTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from tools.combat_preview import fp_rig, sweep

        cls.fp, cls.sweep = fp_rig, sweep
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = Path(cls.tmp.name) / "thickness"
        cls.rc = quiet(sweep.main, ["--weapon", SPEAR, "--set", "rig.off_hand.thickness=0.42,0.62",
                                    "--frames", "1:idle", "--out", str(cls.out)])
        cls.summary = json.loads((cls.out / "summary.json").read_text(encoding="utf-8"))

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def run_sweep(self, *args):
        return quiet(self.sweep.main, ["--weapon", SPEAR, "--frames", "1:idle", *args])

    def test_writes_the_sheets_rigs_and_summary(self):
        self.assertEqual(self.rc, 0)
        for name in ("grid.png", "zoom.png", "zoom/m1_idle.png", "summary.txt",
                     "rigs/c1_thickness-0.42.json", "rigs/c2_thickness-0.62.json"):
            self.assertTrue((self.out / name).is_file(), name)

    def test_the_shipped_value_is_added_as_the_reference_column(self):
        cands = self.summary["candidates"]
        self.assertEqual([c["column"] for c in cands], [0, 1, 2])
        self.assertTrue(cands[0]["reference"])
        self.assertEqual(self.summary["sets"][0]["base_value"], 0.56)

    def test_candidate_rigs_differ_from_the_shipped_file_by_one_value(self):
        shipped = Path(self.fp.__file__).resolve().parents[2] / self.summary["base_rig"]
        a = shipped.read_text(encoding="utf-8").splitlines()
        b = (self.out / "rigs/c1_thickness-0.42.json").read_text(encoding="utf-8").splitlines()
        diff = [(x, y) for x, y in zip(a, b) if x != y]
        self.assertEqual(len(a), len(b))
        self.assertEqual(len(diff), 1)
        self.assertIn('"thickness": 0.42', diff[0][1])

    def test_thickness_changes_pixels_only_where_the_off_arm_is(self):
        frame = self.summary["frames"][0]
        for cand in self.summary["candidates"][1:]:
            self.assertGreater(cand["frames"][0]["changed_px"], 1000, cand["label"])
        # the off arm's own pixels (1x render, tag buffer) for the shipped rig and both candidates
        a = self.sweep.build_parser().parse_args(["--weapon", SPEAR, "--set", "x=1", "--out", "-"])
        off = None
        for rig in (None, self.out / "rigs/c1_thickness-0.42.json", self.out / "rigs/c2_thickness-0.62.json"):
            scene, _ = quiet(self.sweep.load_scene, self.sweep.fp_namespace(a, rig), "test")
            fr = quiet(self.fp.render_image, scene, scene.rig.moves[0], 0.0, self.fp.GAME_W, self.fp.GAME_H, 1)[1]
            m = self.fp.np.isin(fr.id, OFF_ARM_TAGS)
            off = m if off is None else off | m
        ys, xs = self.fp.np.nonzero(off)
        arm = (xs.min(), ys.min(), xs.max(), ys.max())
        slack = 3  # the sheet frames are 2x supersampled: anti-aliased edges reach a pixel or two further
        for cand in self.summary["candidates"][1:]:
            x0, y0, x1, y1 = cand["frames"][0]["bbox"]
            self.assertTrue(arm[0] - slack <= x0 and arm[1] - slack <= y0 and x1 <= arm[2] + slack
                            and y1 <= arm[3] + slack, f"{cand['label']}: changed {cand['frames'][0]['bbox']}, "
                                                      f"off arm {arm}")
        zx0, zy0, zx1, zy1 = frame["zoom_box"]
        self.assertTrue(zx0 <= arm[0] + slack and zy0 <= arm[1] + slack, frame["zoom_box"])
        self.assertLess((zx1 - zx0) * (zy1 - zy0), self.fp.GAME_W * self.fp.GAME_H / 4, "zoom is not a close-up")

    def test_an_ignored_path_is_reported_and_exits_one(self):
        with tempfile.TemporaryDirectory() as tmp:
            rc = self.run_sweep("--set", "rig.off_hand.unused=1,2", "--out", tmp)
            s = json.loads((Path(tmp) / "summary.json").read_text(encoding="utf-8"))
            text = (Path(tmp) / "summary.txt").read_text(encoding="utf-8")
        self.assertEqual(rc, 1)
        self.assertTrue(s["nothing_differs"])
        self.assertTrue(all(c["identical_to_reference"] for c in s["candidates"] if not c["reference"]))
        self.assertIn("NOTHING DIFFERS", text)
        self.assertTrue(any("is not read by FirstPersonSwing" in m for m in s["loud"]))

    def test_a_value_the_loader_rejects_names_the_candidate(self):
        with tempfile.TemporaryDirectory() as tmp, self.assertRaises(SystemExit) as cm:
            self.run_sweep("--set", "rig.off_hand.thickness=0.5,1.5", "--out", tmp)
        msg = str(cm.exception.code)
        self.assertIn("candidate c2 (rig.off_hand.thickness = 1.5", msg)
        self.assertIn("rig.off_hand.thickness must be within 0.2..1.2", msg)

    def test_a_path_without_a_parent_fails_naming_it(self):
        with tempfile.TemporaryDirectory() as tmp, self.assertRaises(SystemExit) as cm:
            self.run_sweep("--set", "rig.offhand.thickness=0.5", "--out", tmp)
        self.assertIn("rig.offhand.thickness: rig.offhand does not exist", str(cm.exception.code))

    def test_same_command_writes_the_same_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            outs = []
            for n in ("a", "b"):
                self.run_sweep("--set", "rig.arm.thickness=0.42,0.5", "--cell", "240", "--out", f"{tmp}/{n}")
                outs.append({p.relative_to(f"{tmp}/{n}").as_posix(): p.read_bytes()
                             for p in Path(f"{tmp}/{n}").rglob("*") if p.is_file() and p.suffix == ".png"})
        self.assertEqual(outs[0].keys(), outs[1].keys())
        self.assertEqual(outs[0], outs[1])


@unittest.skipIf(MISSING, SKIP_REASON)
class DiffTest(unittest.TestCase):
    def setUp(self):
        from PIL import Image

        from tools.combat_preview import diff

        self.Image, self.diff = Image, diff
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def stills(self, name, frames):
        d = self.root / name
        d.mkdir()
        for stem, (size, edits) in frames.items():
            im = self.Image.new("RGB", size, (100, 120, 140))
            for xy, rgb in edits:
                im.putpixel(xy, rgb)
            im.save(d / f"{stem}.png")
        return d

    def run_diff(self, before, after, *args):
        out = self.root / "out"
        rc = quiet(self.diff.main, [str(before), str(after), "--out", str(out), *args])
        return rc, json.loads((out / "summary.json").read_text(encoding="utf-8")), out

    def test_counts_bbox_threshold_and_unpaired(self):
        block = [((x, y), (200, 120, 140)) for x in range(10, 20) for y in range(5, 8)]  # 30 px, diff 100
        edge = [((30, 25), (112, 132, 152)), ((31, 25), (113, 132, 152))]  # sums 36 (not changed) and 37 (changed)
        before = self.stills("before", {"m1_idle": ((40, 30), []), "same": ((40, 30), []),
                                        "gone": ((40, 30), []), "big": ((40, 30), [])})
        after = self.stills("after", {"m1_idle": ((40, 30), block + edge), "same": ((40, 30), []),
                                      "new": ((40, 30), []), "big": ((50, 30), [])})
        rc, s, out = self.run_diff(before, after)
        self.assertEqual(rc, 0)
        frames = {f["name"]: f for f in s["frames"]}
        self.assertEqual(frames["m1_idle"]["changed_px"], 31)
        self.assertEqual(frames["m1_idle"]["bbox"], [10, 5, 31, 25])
        self.assertEqual((frames["same"]["changed_px"], frames["same"]["bbox"]), (0, None))
        self.assertEqual(frames["big"]["error"], "size mismatch")
        self.assertEqual((s["only_in_before"], s["only_in_after"]), (["gone"], ["new"]))
        self.assertTrue((out / "sheet.png").is_file() and (out / "zoom.png").is_file())
        # a higher threshold keeps the block (100 > 99) and drops the edge pixel; --frames selects
        _, s, _ = self.run_diff(before, after, "--threshold", "99", "--frames", "m1_idle")
        self.assertEqual([(f["name"], f["changed_px"], f["bbox"]) for f in s["frames"]], [("m1_idle", 30, [10, 5, 19, 7])])

    def test_capture_directories_use_the_view_and_manifest_order(self):
        for side, rgb in (("b", (100, 120, 140)), ("a", (100, 120, 240))):
            d = self.root / side / "frames/fp"
            d.mkdir(parents=True)
            for stem in ("m1_idle", "m1_contact"):
                im = self.Image.new("RGB", (8, 8), (100, 120, 140))
                if stem == "m1_contact":
                    im.putpixel((2, 3), rgb)
                im.save(d / f"{stem}.png")
            (self.root / side / "manifest.json").write_text(json.dumps({"frames": [
                {"view": "fp", "file": "frames/fp/m1_idle.png"}, {"view": "fp", "file": "frames/fp/m1_contact.png"}]}))
        _, s, _ = self.run_diff(self.root / "b", self.root / "a")
        self.assertEqual([(f["name"], f["changed_px"], f["bbox"]) for f in s["frames"]],
                         [("m1_idle", 0, None), ("m1_contact", 1, [2, 3, 2, 3])])
        with self.assertRaises(SystemExit):
            quiet(self.diff.main, [str(self.root / "b"), str(self.root / "a"), "--view", "tp_back",
                                   "--out", str(self.root / "x")])


if __name__ == "__main__":
    unittest.main()
