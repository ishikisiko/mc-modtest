"""Focused archive boundary checks for the owner-facing world importer."""
import hashlib
import json
from pathlib import Path
import stat
import tempfile
import unittest
import zipfile

from tools.yunshan.prepare_delivery import inspect_archive, prepare


class DeliveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def archive(self, entries):
        path = self.root / "source.zip"
        with zipfile.ZipFile(path, "w") as z:
            for name, data in entries:
                z.writestr(name, data)
        return path

    def test_prepares_nested_world_and_preserves_source(self):
        archive = self.archive([("city/level.dat", b"\x1f\x8bNBT"), ("city/region/r.0.0.mca", b"region"),
                                ("README.txt", b"delivery notes")])
        before = archive.read_bytes()
        out = self.root / "delivery"
        manifest = prepare(archive, out, "yunshan-v2", r"E:\MC\instance", {"minecraft": "1.21.1"})
        self.assertEqual(manifest["world_root"], "city")
        self.assertEqual(manifest["ignored_files_outside_world"], 1)
        self.assertEqual(manifest["archive_sha256"], hashlib.sha256(before).hexdigest())
        self.assertEqual((out / "world.zip").read_bytes(), before)
        self.assertEqual(archive.read_bytes(), before)
        self.assertEqual(json.loads((out / "delivery.json").read_text())["requirements"]["minecraft"], "1.21.1")
        script = (out / "install-yunshan.ps1").read_text(encoding="utf-8-sig")
        self.assertIn("[System.IO.Directory]::Move($source, $destination)", script)
        self.assertNotIn("Move-Item", script)
        self.assertNotIn("Stop-Process", script)

    def test_flat_world(self):
        archive = self.archive([("level.dat", b"\x1f\x8bNBT"), ("region/r.0.0.mca", b"region")])
        self.assertEqual(inspect_archive(archive)["world_root"], "")

    def test_rejects_windows_path_attacks_and_case_collisions(self):
        for name in ("../evil", "/evil", "city/../evil", "city\\evil", "city/a:stream", "city/NUL.txt", "city/evil. "):
            with self.subTest(name=name):
                archive = self.archive([("city/level.dat", b"\x1f\x8bNBT"), (name, b"bad")])
                with self.assertRaises(ValueError):
                    inspect_archive(archive)
        archive = self.archive([("city/level.dat", b"\x1f\x8bNBT"), ("city/A", b"x"), ("city/a", b"y")])
        with self.assertRaisesRegex(ValueError, "duplicate"):
            inspect_archive(archive)

    def test_rejects_symlink_and_file_parent(self):
        link = zipfile.ZipInfo("city/link")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        archive = self.archive([("city/level.dat", b"\x1f\x8bNBT"), (link, b"../../evil")])
        with self.assertRaisesRegex(ValueError, "non-regular"):
            inspect_archive(archive)
        archive = self.archive([("city/level.dat", b"\x1f\x8bNBT"), ("city/region", b"x"), ("city/region/a", b"y")])
        with self.assertRaisesRegex(ValueError, "file as parent"):
            inspect_archive(archive)

    def test_rejects_missing_ambiguous_or_non_world_root(self):
        for entries in ([('level.dat', b'not-NBT')], [('region/a', b'x')],
                        [('a/level.dat', b'\x1f\x8b'), ('b/level.dat', b'\x1f\x8b')]):
            with self.subTest(entries=entries), self.assertRaises(ValueError):
                inspect_archive(self.archive(entries))

    def test_never_overwrites_output_or_accepts_dangerous_save_name(self):
        archive = self.archive([("level.dat", b"\x1f\x8bNBT")])
        out = self.root / "existing"
        out.mkdir()
        marker = out / "keep"
        marker.write_text("untouched")
        with self.assertRaises(FileExistsError):
            prepare(archive, out, "city", "instance")
        self.assertEqual(marker.read_text(), "untouched")
        for name in ("../dev1", "dev1/evil", "CON", ".hidden"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                prepare(archive, self.root / "unused", name, "instance")


if __name__ == "__main__":
    unittest.main()
