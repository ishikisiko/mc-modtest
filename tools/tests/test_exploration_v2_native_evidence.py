import copy
import tempfile
import unittest
from pathlib import Path
import zipfile

from tools import exploration_v2_native_evidence as evidence


class NativeEvidenceTests(unittest.TestCase):
    def test_incomplete_native_phase_is_not_a_pass(self):
        valid = {"phase": "initial", "passed": True, "selected": ["boss", "loot"],
                 "completed": ["boss", "loot"], "unrun": [],
                 "checks": [{"passed": True}]}
        evidence.validate_phase(valid, "initial")
        for updates in ({"completed": ["boss"]}, {"unrun": ["loot"]},
                        {"checks": []}, {"checks": [{"passed": False}]},
                        {"phase": "restart"}, {"passed": False, "error": "native failure"}):
            with self.subTest(updates=updates), self.assertRaises(RuntimeError):
                evidence.validate_phase({**copy.deepcopy(valid), **updates}, "initial")

    def test_test_only_subscriber_is_rejected_from_release(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jars = root / "build/libs"
            jars.mkdir(parents=True)
            jar = jars / "myvillage-test.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("com/example/MyVillage.class", b"release")
            self.assertEqual(list(evidence.verify_release_jar(root)), [jar.name])
            with zipfile.ZipFile(jar, "a") as archive:
                archive.writestr("com/example/ExplorationV2Evidence$ImpactSkull.class", b"fixture")
            with self.assertRaisesRegex(RuntimeError, "leaked"):
                evidence.verify_release_jar(root)


if __name__ == "__main__":
    unittest.main()
