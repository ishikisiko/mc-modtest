import math
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

import render_structure  # noqa: E402


class RenderStructureTests(unittest.TestCase):
    def test_solve_camera_forward_matches_target_vector(self) -> None:
        target = (10.0, 80.0, 20.0)
        for azimuth in (0.0, 90.0, 180.0, 270.0):
            cam = render_structure.solve_camera(
                target, azimuth_deg=azimuth, distance=20.0, height_above=8.0
            )
            px, py, pz = cam["position"]
            tx, ty, tz = target
            vx, vy, vz = tx - px, ty - py, tz - pz
            length = math.sqrt(vx * vx + vy * vy + vz * vz)
            expected = (vx / length, vy / length, vz / length)
            self.assertTupleAlmostEqual(tuple(cam["forward"]), expected)

            yaw = cam["orientation"]["yaw"]
            pitch = cam["orientation"]["pitch"]
            solved = (
                math.cos(yaw) * math.sin(pitch),
                -math.cos(pitch),
                -math.sin(yaw) * math.sin(pitch),
            )
            self.assertTupleAlmostEqual(solved, expected)
            self.assertAlmostEqual(cam["orientation"]["roll"], math.pi)
            self.assertAlmostEqual(cam["roll_deg"], 180.0)

    def test_clean_scene_cache_removes_stale_render_state(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            scene_dir = Path(tmp)
            for name in (
                "view_left.dump",
                "view_left.dump.backup",
                "view_left.octree2",
                "view_left.json.backup",
                "view_left.json",
            ):
                (scene_dir / name).write_text("x", encoding="utf-8")
            snapshots = scene_dir / "snapshots"
            snapshots.mkdir()
            (snapshots / "old.png").write_bytes(b"not real png")

            removed = render_structure.clean_scene_cache(scene_dir)

            self.assertIn("snapshots/", removed)
            self.assertFalse(snapshots.exists())
            self.assertFalse((scene_dir / "view_left.dump").exists())
            self.assertFalse((scene_dir / "view_left.dump.backup").exists())
            self.assertFalse((scene_dir / "view_left.octree2").exists())
            self.assertFalse((scene_dir / "view_left.json.backup").exists())
            self.assertTrue((scene_dir / "view_left.json").exists())

    def test_chunks_for_view_includes_camera_chunk(self) -> None:
        bbox = {"min": [-19, 63, 178], "max": [12, 91, 213]}
        chunks = render_structure.chunks_for_view(
            bbox, camera_pos=[44.5, 82.0, 195.5], pad=1
        )

        self.assertIn([2, 12], chunks)
        self.assertIn([-2, 11], chunks)

    def test_default_survey_plan_has_cardinal_and_high_diagonal_views(self) -> None:
        specs = render_structure.resolve_view_specs(
            view_plan="survey",
            views=None,
            base_distance=40.0,
            base_height=10.0,
        )

        self.assertEqual(len(specs), 8)
        self.assertIn("front_mid", [spec["name"] for spec in specs])
        self.assertIn("southeast_high", [spec["name"] for spec in specs])
        self.assertGreater(
            max(spec["height_above"] for spec in specs),
            min(spec["height_above"] for spec in specs),
        )

    def test_explicit_views_override_view_plan(self) -> None:
        specs = render_structure.resolve_view_specs(
            view_plan="height-sweep",
            views=["front", "left"],
            base_distance=40.0,
            base_height=10.0,
        )

        self.assertEqual([spec["name"] for spec in specs], ["front", "left"])
        self.assertEqual([spec["height_above"] for spec in specs], [10.0, 10.0])

    def test_assess_png_rejects_smooth_sky_gradient(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            png = Path(tmp) / "smooth_sky.png"
            self.write_rgb_png(
                png,
                64,
                48,
                lambda _x, y: (
                    110 + y,
                    145 + y // 2,
                    190 + y // 3,
                ),
            )

            assessment = render_structure.assess_png(png)

            self.assertFalse(assessment["framing_ok"])
            self.assertLess(assessment["edge_mean"], 3.0)

    def test_write_contact_sheet_for_height_sweep_views(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            entries = []
            for idx, view in enumerate(
                ("front_low", "front_mid", "front_high", "right_low")
            ):
                png = tmp_path / f"{view}.png"
                self.write_rgb_png(
                    png,
                    40,
                    20,
                    lambda x, y, n=idx: (
                        30 + n * 30 + x,
                        60 + y,
                        90 + n * 20,
                    ),
                )
                entries.append(
                    {
                        "view": view,
                        "render": {"png_path": str(png)},
                        "png_exists": True,
                    }
                )

            sheet = tmp_path / "contact_sheet.png"
            meta = render_structure.write_contact_sheet(
                entries,
                sheet,
                view_plan="height-sweep",
                cell_width=120,
            )
            assessment = render_structure.assess_png(sheet)

            self.assertTrue(sheet.is_file())
            self.assertTrue(meta["generated"])
            self.assertEqual(meta["columns"], 3)
            self.assertEqual(meta["rows"], 2)
            self.assertEqual(assessment["width"], 360)
            self.assertEqual(assessment["height"], 120)

    def assertTupleAlmostEqual(
        self, actual: tuple[float, ...], expected: tuple[float, ...]
    ) -> None:
        self.assertEqual(len(actual), len(expected))
        for a, e in zip(actual, expected):
            self.assertAlmostEqual(a, e, places=9)

    def write_rgb_png(self, path: Path, width: int, height: int, pixel_fn) -> None:
        def chunk(kind: bytes, payload: bytes) -> bytes:
            crc = zlib.crc32(kind)
            crc = zlib.crc32(payload, crc) & 0xFFFFFFFF
            return (
                struct.pack(">I", len(payload))
                + kind
                + payload
                + struct.pack(">I", crc)
            )

        raw = bytearray()
        for y in range(height):
            raw.append(0)
            for x in range(width):
                raw.extend(pixel_fn(x, y))
        ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
        path.write_bytes(
            b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw)))
            + chunk(b"IEND", b"")
        )


if __name__ == "__main__":
    unittest.main()
