import json
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.validate_cultivation_core import CultivationValidator


ROOT = Path(__file__).resolve().parents[2]
RESOURCE_ROOT = Path("src/main/resources/data/myvillage/myvillage")
LANG_ROOT = Path("src/main/resources/assets/myvillage/lang")


class CultivationCoreValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fixture_root = Path(self.temp.name)

        shutil.copytree(
            ROOT / RESOURCE_ROOT,
            self.fixture_root / RESOURCE_ROOT,
        )
        (self.fixture_root / LANG_ROOT).mkdir(parents=True)
        for locale in ("en_us", "zh_cn"):
            shutil.copy2(
                ROOT / LANG_ROOT / f"{locale}.json",
                self.fixture_root / LANG_ROOT / f"{locale}.json",
            )

    def validate(self):
        return CultivationValidator(self.fixture_root).validate()

    def load_json(self, relative_path: Path) -> dict:
        return json.loads(
            (self.fixture_root / relative_path).read_text(encoding="utf-8")
        )

    def write_json(self, relative_path: Path, value: dict) -> None:
        (self.fixture_root / relative_path).write_text(
            json.dumps(value, indent=2) + "\n",
            encoding="utf-8",
        )

    def assert_error_contains(self, result, expected: str) -> None:
        self.assertTrue(
            any(expected in error for error in result.errors),
            f"expected an error containing {expected!r}, got {result.errors!r}",
        )

    def test_complete_shipped_fixture_passes(self) -> None:
        result = self.validate()

        self.assertEqual((), result.errors)
        self.assertEqual(3, result.realm_count)
        self.assertGreaterEqual(result.stage_count, 12)
        self.assertEqual(5, result.element_count)
        self.assertEqual(1, result.technique_count)

    def test_missing_element_reference_is_rejected(self) -> None:
        path = RESOURCE_ROOT / "technique/basic_breathing.json"
        technique = self.load_json(path)
        technique["elements"] = ["myvillage:missing_element"]
        self.write_json(path, technique)

        result = self.validate()

        self.assert_error_contains(
            result,
            "references missing element myvillage:missing_element",
        )

    def test_mismatched_requirement_stage_is_rejected(self) -> None:
        path = RESOURCE_ROOT / "technique/basic_breathing.json"
        technique = self.load_json(path)
        technique["requirements"] = {
            "minimum_realm": "myvillage:mortal",
            "minimum_stage": "myvillage:qi_refining_1",
        }
        self.write_json(path, technique)

        result = self.validate()

        self.assert_error_contains(
            result,
            "requires mismatched realm/stage: myvillage:mortal does not own "
            "myvillage:qi_refining_1",
        )

    def test_invalid_number_is_rejected(self) -> None:
        path = RESOURCE_ROOT / "spiritual_element/fire.json"
        element = self.load_json(path)
        element["display_color"] = 0x1000000
        self.write_json(path, element)

        result = self.validate()

        self.assert_error_contains(
            result,
            "field 'display_color' must be 0..16777215",
        )

    def test_unordered_realm_stages_are_rejected(self) -> None:
        path = RESOURCE_ROOT / "realm/mortal.json"
        realm = self.load_json(path)
        realm["stages"] = list(reversed(realm["stages"]))
        self.write_json(path, realm)

        result = self.validate()

        self.assert_error_contains(
            result,
            "stage sort_order values must be strictly increasing",
        )

    def test_missing_required_id_is_rejected(self) -> None:
        (self.fixture_root / RESOURCE_ROOT / "spiritual_element/metal.json").unlink()

        result = self.validate()

        self.assert_error_contains(
            result,
            "missing required spiritual element myvillage:metal",
        )

    def test_missing_translation_is_rejected(self) -> None:
        path = LANG_ROOT / "zh_cn.json"
        language = self.load_json(path)
        del language["cultivation.technique.myvillage.basic_breathing"]
        self.write_json(path, language)

        result = self.validate()

        self.assert_error_contains(
            result,
            "missing declared translation key "
            "'cultivation.technique.myvillage.basic_breathing'",
        )


if __name__ == "__main__":
    unittest.main()
