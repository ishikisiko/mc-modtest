import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "tools" / "validate_custom_entities.py"
SPEC = importlib.util.spec_from_file_location("validate_custom_entities", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class CustomEntityValidationTest(unittest.TestCase):
    def test_simple_fox_surface_is_complete(self) -> None:
        report = MODULE.validate(ROOT)
        self.assertEqual([], report["errors"])
        self.assertEqual("pass", report["status"])
        self.assertEqual([48, 32], report["texture"]["dimensions"])
        self.assertEqual(998, report["texture"]["used_texels"])


if __name__ == "__main__":
    unittest.main()
