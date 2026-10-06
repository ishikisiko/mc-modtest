"""The offline preview's port of the first-person solver (tools/combat_preview/fp_rig.py) against
the Java golden src/test/resources/first_person_preview_parity.json, which the JUnit
FirstPersonPreviewParityTest writes and checks: pose sampling, arm lag, grip frame, the main arm
with lag and the off arm, for every shipped weapon, move, golden tick and both main arms.

Needs numpy and pillow, so it skips under an interpreter without them. Run it with the preview
interpreter (see tools/combat_preview/README.md):

    .venv-preview/bin/python -m unittest tools.tests.test_combat_preview_parity
"""
from __future__ import annotations

import importlib.util
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "src/main/resources"
GOLDEN = ROOT / "src/test/resources/first_person_preview_parity.json"
OFF_ARM_OVERRIDES = ROOT / "src/test/resources/first_person_off_arm_overrides.json"
MISSING = [m for m in ("numpy", "PIL") if importlib.util.find_spec(m) is None]
SKIP_REASON = (f"{sys.executable} has no {' or '.join(MISSING)}; run this test with the preview interpreter: "
               ".venv-preview/bin/python -m unittest tools.tests.test_combat_preview_parity "
               "(setup: python3 -m venv .venv-preview && "
               ".venv-preview/bin/pip install -r tools/combat_preview/requirements.txt)")
SIDES = (("right", 1.0), ("left", -1.0))
JOINTS = ("shoulder", "elbow", "wrist", "grip")
BONES = (("upper_arm", "upper_R"), ("forearm", "fore_R"), ("fist", "fist_R"))


def shipped_weapons() -> list[str]:
    return json.loads((RESOURCES / "data/myvillage/combat/index.json").read_text(encoding="utf-8"))["weapons"]


@unittest.skipIf(MISSING, SKIP_REASON)
class FirstPersonSolverParityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from tools.combat_preview import fp_rig

        cls.fp = fp_rig
        cls.np = fp_rig.np
        cls.golden = json.loads(GOLDEN.read_text(encoding="utf-8"))
        cls.tol = cls.golden["tolerance"]

    def load(self, entry):
        fp = self.fp
        data = fp.ModData([RESOURCES])
        weapon = fp.load_weapon(data, entry["weapon"])
        rig_ns, rig_path = entry["rig"].split(":", 1)
        geo_ns, geo_path = entry["geometry"].split(":", 1)
        self.assertEqual(weapon["rig_rel"], f"assets/{rig_ns}/{rig_path}", "the weapon file names another rig")
        self.assertEqual(weapon["geometry_rel"], f"assets/{geo_ns}/{geo_path}", "the weapon file names another contract")
        geo = fp.Geometry(data.json(weapon["geometry_rel"], "geometry")[0])
        rig_json, rig_file = data.json(weapon["rig_rel"], "rig")
        return fp.Rig(rig_json, weapon["style"], rig_file.name, geo), geo

    def near(self, expected, actual, tol, where):
        e = self.np.atleast_1d(self.np.asarray(expected, float))
        a = self.np.atleast_1d(self.np.asarray(actual, float)).ravel()
        self.assertEqual(e.shape, a.shape, where)
        worst = float(self.np.abs(e - a).max())
        self.assertLessEqual(worst, tol, f"{where}: off by {worst:.6g}\n  java   {e.tolist()}\n  python {a.tolist()}")

    def check_arm(self, expected, sol, where):
        t = self.tol
        for joint in JOINTS:
            self.near(expected[joint], sol[joint], t["position"], f"{where} {joint}")
        for name, key in BONES:
            java = self.fp._quat_rot(self.np.asarray(expected[name], float))
            worst = float(self.np.linalg.norm(java - sol[key], axis=0).max())  # per rotated axis
            self.assertLessEqual(worst, t["rotation"], f"{where} {name} rotation: an axis is off by {worst:.6g}")
        self.near(expected["flexion"], sol["flex"], t["degrees"], f"{where} flexion")
        self.near(expected["deviation"], sol["dev"], t["degrees"], f"{where} deviation")
        self.assertEqual(expected["clamped"], bool(sol["clamped"]), f"{where} clamped")

    def test_golden_covers_every_shipped_weapon(self):
        self.assertEqual([w["weapon"] for w in self.golden["weapons"]], shipped_weapons(),
                         "golden and combat index disagree; rewrite the golden: " + self.golden["regenerate"])

    def test_python_port_matches_the_java_golden(self):
        fp, t = self.fp, self.tol
        arms = off_arms = 0
        for entry in self.golden["weapons"]:
            rig, geo = self.load(entry)
            self.assertEqual([m.id for m in rig.moves], [m["id"] for m in entry["moves"]],
                             f"{entry['weapon']}: the rig's moves differ from the golden's")
            for move, expected_move in zip(rig.moves, entry["moves"]):
                self.assertTrue(expected_move["samples"], f"{move.id}: no samples")
                for sample in expected_move["samples"]:
                    tick = float(sample["tick"])
                    where = f"{move.id} t {tick:g}"
                    pose = move.sample(tick)
                    self.near(sample["pose"], list(pose), t["pose"], f"{where} pose")
                    lag = fp.arm_lag(rig, move, tick)
                    self.near(sample["lag"], lag, t["position"], f"{where} lag")
                    for name, side in SIDES:
                        expected = sample[name]
                        at = f"{where} {name}"
                        self.near(expected["grip_frame"], fp.grip_frame(side, rig, pose)[:3, :4],
                                  t["position"], f"{at} grip frame")
                        main = fp.solve_arm(side, rig, pose, lag)
                        self.check_arm(expected["main"], main, f"{at} main arm")
                        self.near(expected["main"]["lag_scale"], main["lag_scale"], t["scale"], f"{at} lag scale")
                        off = fp.solve_off_arm(side, rig, geo, pose)
                        self.assertEqual(expected["off"] is None, off is None, f"{at} off arm drawn")
                        if off is not None:
                            e = expected["off"]
                            self.check_arm(e, off, f"{at} off arm")
                            self.assertEqual(bool(e.get("free")), bool(off.get("free")), f"{at} free off hand")
                            if not e.get("free"):
                                self.near(e["grip_y"], off["grip_y"], t["model_px"], f"{at} off grip y")
                                self.near(e["wanted_grip_y"], off["wanted_y"], t["model_px"],
                                          f"{at} off wanted grip y")
                            self.near(e["hold"], off["hold"], t["scale"], f"{at} off hold")
                            off_arms += 1
                        arms += 1
        self.assertGreater(arms, 0)
        self.assertGreater(off_arms, 0, "no off-arm sample was compared")

    def test_off_arm_overrides_match_the_shared_fixture(self):
        # No shipped rig uses rig.off_hand upper_arm/forearm/rest_direction/rest_reach, so the golden
        # does not cover them; FirstPersonOffHandTest checks the same numbers on the Java side.
        fp = self.fp
        fixture = json.loads(OFF_ARM_OVERRIDES.read_text(encoding="utf-8"))
        data = fp.ModData([RESOURCES])
        rig_ns, rig_path = fixture["rig"].split(":", 1)
        geo_ns, geo_path = fixture["geometry"].split(":", 1)
        geo = fp.Geometry(data.json(f"assets/{geo_ns}/{geo_path}", "geometry")[0])
        rig_json, _ = data.json(f"assets/{rig_ns}/{rig_path}", "rig")
        rig_json["rig"]["off_hand"].update(fixture["off_hand_overrides"])
        style = fp.load_weapon(data, "myvillage:lingxiao_spear")["style"]
        rig = fp.Rig(rig_json, style, "overrides", geo)
        tol = fixture["tolerance"]
        for case in fixture["cases"]:
            pose = rig.moves[case["move"] - 1].sample(case["tick"]) if "move" in case else rig.neutral
            if "hold" in case:
                values = list(pose)
                values[fp.POSE_FIELDS.index("off_hand_hold")] = case["hold"]
                pose = fp.Pose(values)
            off = fp.solve_off_arm(1.0, rig, geo, pose)
            where = json.dumps({k: v for k, v in case.items() if k in ("pose", "move", "tick", "hold")})
            self.assertIsNotNone(off, where)
            self.near(case["grip_y"], off["grip_y"], tol, f"{where} grip y")
            for joint in JOINTS:
                self.near(case[joint], off[joint], tol, f"{where} {joint}")
            overrides = fixture["off_hand_overrides"]
            self.near(overrides["upper_arm"], self.np.linalg.norm(off["elbow"] - off["shoulder"]), 1e-6, f"{where} upper arm")
            self.near(overrides["forearm"], self.np.linalg.norm(off["wrist"] - off["elbow"]), 1e-6, f"{where} forearm")


if __name__ == "__main__":
    unittest.main()
