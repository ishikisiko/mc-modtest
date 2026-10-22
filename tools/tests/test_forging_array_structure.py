"""Cross-check the shipped ding mesh against the runtime's structural lattice.

Mesh coordinates become blueprint coordinates via (+.5, -1, +2.5), matching
ForgingArrayCollision and the renderer's controller-relative origin. Do not
silently clip collision outside non-air cells: those volumes disappear at runtime.
"""
import collections
import itertools
import json
import math
from pathlib import Path
import re
import unittest

from tools.forging_array.formed_model import build

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "src/main/resources"
OFFSET = (.5, -1, 2.5)
EPSILON = 1e-9


def translated_box(box):
    return tuple(tuple(point[i] + OFFSET[i] for i in range(3)) for point in box)


def cell_slices(box):
    """Exact positive-volume intersections; touching a boundary is not overlap."""
    lo, hi = translated_box(box)
    for cell in itertools.product(*(range(math.floor(lo[i]), math.ceil(hi[i])) for i in range(3))):
        lower = tuple(max(lo[i], cell[i]) for i in range(3))
        upper = tuple(min(hi[i], cell[i] + 1) for i in range(3))
        volume = math.prod(max(0, upper[i] - lower[i]) for i in range(3))
        if volume > EPSILON:
            yield cell, lower, upper, volume


class ForgingArrayStructureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rules = json.loads((RESOURCES / "data/myvillage/forging/array.json").read_text())
        cls.mesh = json.loads((RESOURCES / "assets/myvillage/forging_array/formed_mesh.json").read_text())
        cls.cells = {(c["x"], c["y"], c["z"]): c["role"] for c in cls.rules["cells"]}

    def test_structure_has_complete_unique_lattice_and_component_quantities(self):
        expected = set(itertools.product(range(-2, 3), range(-1, 4), range(5)))
        self.assertEqual(125, len(self.rules["cells"]))
        self.assertEqual(expected, set(self.cells))
        self.assertEqual({"foundation": 25, "controller": 1, "hearth": 1,
                          "pylon": 4, "casing": 24, "air": 70},
                         collections.Counter(self.cells.values()))
        self.assertEqual("controller", self.cells[(0, 0, 0)])
        self.assertEqual("hearth", self.cells[(0, 0, 2)])

    def test_generated_mesh_matches_authored_geometry(self):
        # JSON roundtrip normalizes tuples without rebuilding or writing assets.
        self.assertEqual(json.loads(json.dumps(build())), self.mesh)

    def test_collision_volume_is_fully_assigned_to_non_air_structure_cells(self):
        errors = []
        for index, box in enumerate(self.mesh["collision_boxes"]):
            lo, hi = box
            self.assertTrue(all(math.isfinite(v) for p in box for v in p), f"box {index}")
            self.assertTrue(all(lo[i] < hi[i] for i in range(3)), f"box {index}")
            total = math.prod(hi[i] - lo[i] for i in range(3))
            assigned = 0
            for cell, lower, upper, volume in cell_slices(box):
                role = self.cells.get(cell, "outside structure")
                if role == "air" or cell not in self.cells:
                    errors.append(f"box {index} {box} -> {cell} ({role}), "
                                  f"lost slice {lower}..{upper}, volume={volume:.9g}")
                else:
                    assigned += volume
            if not math.isclose(total, assigned, rel_tol=1e-8, abs_tol=EPSILON):
                errors.append(f"box {index}: source volume={total:.9g}, "
                              f"assigned={assigned:.9g}, lost={total-assigned:.9g}")
        if errors:
            self.fail("Collision clipped by runtime structure allocation:\n" + "\n".join(errors))

    def test_face_bounds_fit_the_same_structure_envelope(self):
        points = [p for f in self.mesh["faces"] for p in f["p"]]
        self.assertTrue(points)
        self.assertEqual([[min(p[i] for p in points) for i in range(3)],
                          [max(p[i] for p in points) for i in range(3)]], self.mesh["bounds"])
        lower = tuple(min(c[i] for c in self.cells) for i in range(3))
        upper = tuple(max(c[i] for c in self.cells) + 1 for i in range(3))
        for index, face in enumerate(self.mesh["faces"]):
            self.assertEqual(4, len(face["p"]), f"face {index}")
            for point in face["p"]:
                self.assertEqual(3, len(point), f"face {index}")
                mapped = tuple(point[i] + OFFSET[i] for i in range(3))
                self.assertTrue(all(math.isfinite(v) for v in mapped), f"face {index}: {point}")
                self.assertTrue(all(lower[i]-EPSILON <= mapped[i] <= upper[i]+EPSILON for i in range(3)),
                                f"face {index}: mesh {point} -> blueprint {mapped}, envelope {lower}..{upper}")

    def test_every_non_air_component_has_static_faces_in_its_cell(self):
        # Require positive overlap on two axes, so an unrelated edge cannot
        # masquerade as a structural component. Fire/ring are runtime effects.
        missing = []
        for cell, role in self.cells.items():
            if role == "air":
                continue
            visible = False
            for face in self.mesh["faces"]:
                if face["g"] != "body":
                    continue
                lo = [min(p[i] for p in face["p"]) + OFFSET[i] for i in range(3)]
                hi = [max(p[i] for p in face["p"]) + OFFSET[i] for i in range(3)]
                overlap = [min(hi[i], cell[i]+1)-max(lo[i], cell[i]) for i in range(3)]
                if all(n >= -EPSILON for n in overlap) and sum(n > EPSILON for n in overlap) >= 2:
                    visible = True
                    break
            if not visible:
                missing.append((cell, role))
        self.assertFalse(missing, f"Non-air components without static mesh faces: {missing}")

    def test_blueprint_quantities_and_world_tooltips_use_the_runtime_definition(self):
        # Source boundary guard: no second blueprint/count table may diverge
        # from the rules checked above. JUnit checks runtime coordinate behavior.
        java = ROOT / "src/main/java/com/example/myvillage"
        screen = (java / "client/forging/array/ForgingArrayScreen.java").read_text()
        compact = re.sub(r"\s+", "", screen)
        self.assertIn("for(varc:ForgingArrayRules.get().cells())", compact)
        self.assertIn("ForgingArrayRules.get().cells().stream().filter(c->c.role()==role).count()", compact)
        self.assertIn("ForgingArrayPattern.worldPos(menu.origin(),menu.facing(),c)", compact)
        self.assertIn("ForgingArrayRules.get().cells().indexOf(c)==menu.mismatchIndex()", compact)
        pattern = re.sub(r"\s+", "", (java / "forging/array/ForgingArrayPattern.java").read_text())
        self.assertIn("varcells=ForgingArrayRules.get().cells()", pattern)

    def test_collision_partition_handles_integer_edges_and_detects_air_overhangs(self):
        # Synthetic cases verify the coverage check itself, especially negative
        # coordinates and a tiny protrusion that a midpoint-only check misses.
        exact = [[-2.5, 0, -2.5], [-1.5, 1, -1.5]]
        slices = list(cell_slices(exact))
        self.assertEqual([(-2, -1, 0)], [s[0] for s in slices])
        self.assertAlmostEqual(1, sum(s[3] for s in slices))
        protruding = [[-.1, 1, -2.1], [.5001, 2, -1.9]]
        slices = list(cell_slices(protruding))
        self.assertEqual({(0, 0, 0), (1, 0, 0)}, {s[0] for s in slices})
        self.assertGreater(next(s[3] for s in slices if s[0][0] == 1), 0)


if __name__ == "__main__":
    unittest.main()
