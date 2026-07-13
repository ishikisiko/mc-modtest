import json
import re
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.validate_cultivation_advancement import CultivationAdvancementValidator


ROOT = Path(__file__).resolve().parents[2]


class CultivationAdvancementValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fixture_root = Path(self.temp.name)
        self.build_fixture()

    def build_fixture(self) -> None:
        paths = (
            "src/main/java/com/example/myvillage/cultivation",
            "src/main/java/com/example/myvillage/client/cultivation",
            "src/main/resources/data/myvillage/myvillage/realm",
            "src/main/resources/assets/myvillage/lang",
        )
        for relative in paths:
            source = ROOT / relative
            target = self.fixture_root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(source, target)

    def validate(self):
        return CultivationAdvancementValidator(self.fixture_root).validate()

    def assert_error_contains(self, result, expected: str) -> None:
        self.assertTrue(
            any(expected in error for error in result.errors),
            f"expected error containing {expected!r}, got {result.errors!r}",
        )

    def test_complete_shipped_fixture_passes(self) -> None:
        self.assertEqual((), self.validate().errors)

    def test_bottleneck_duration_drift_is_rejected(self) -> None:
        path = (
            self.fixture_root
            / "src/main/resources/data/myvillage/myvillage/realm/qi_refining.json"
        )
        realm = json.loads(path.read_text(encoding="utf-8"))
        realm["stages"][2]["advancement"]["duration_ticks"] = 199
        path.write_text(json.dumps(realm, indent=2) + "\n", encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "myvillage:qi_refining_3 advancement must be")

    def test_revised_cap_drift_is_rejected(self) -> None:
        path = (
            self.fixture_root
            / "src/main/resources/data/myvillage/myvillage/realm/qi_refining.json"
        )
        realm = json.loads(path.read_text(encoding="utf-8"))
        realm["stages"][0]["cultivation_cap"] = 1099
        path.write_text(json.dumps(realm, indent=2) + "\n", encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(
            result,
            "myvillage:qi_refining_1 cultivation_cap must be integer 1100",
        )

    def test_bottleneck_penalty_drift_is_rejected(self) -> None:
        path = (
            self.fixture_root
            / "src/main/resources/data/myvillage/myvillage/realm/qi_refining.json"
        )
        realm = json.loads(path.read_text(encoding="utf-8"))
        realm["stages"][2]["advancement"]["interruption_stability_loss"] = 4
        path.write_text(json.dumps(realm, indent=2) + "\n", encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "myvillage:qi_refining_3 advancement must be")

    def test_dynamic_stability_requirement_drift_is_rejected(self) -> None:
        path = (
            self.fixture_root
            / "src/main/resources/data/myvillage/myvillage/realm/qi_refining.json"
        )
        realm = json.loads(path.read_text(encoding="utf-8"))
        realm["stages"][0]["advancement"]["required_stability"] = 549
        path.write_text(json.dumps(realm, indent=2) + "\n", encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "required_stability must be half of cultivation_cap")

    def test_success_halving_removal_is_rejected(self) -> None:
        transition = (
            self.fixture_root
            / "src/main/java/com/example/myvillage/cultivation/meditation/AdvancementProfileTransition.java"
        )
        text = transition.read_text(encoding="utf-8")
        text, count = re.subn(
            r"current\.stability\(\)\s*/\s*2",
            "current.stability() - definition.stabilityCost()",
            text,
            count=1,
        )
        self.assertEqual(1, count)
        transition.write_text(text, encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "retain integer-floor half")
        self.assert_error_contains(result, "must not deduct a fixed stability cost")

    def test_breakthrough_key_removal_is_rejected(self) -> None:
        client_root = self.fixture_root / "src/main/java/com/example/myvillage/client/cultivation"
        changed = False
        for path in sorted(client_root.rglob("*.java")):
            text = path.read_text(encoding="utf-8")
            if "GLFW_KEY_N" in text:
                path.write_text(text.replace("GLFW_KEY_N", "GLFW_KEY_UNKNOWN", 1), encoding="utf-8")
                changed = True
                break
        self.assertTrue(changed, "fixture must register the advancement key")

        result = self.validate()

        self.assert_error_contains(result, "missing configurable advancement control GLFW_KEY_N")

    def test_client_authored_target_is_rejected(self) -> None:
        network_root = self.fixture_root / "src/main/java/com/example/myvillage/cultivation/network"
        payload = next(
            path
            for path in network_root.rglob("*.java")
            if re.search(r"record\s+\w*IntentPayload\s*\(", path.read_text(encoding="utf-8"))
        )
        text = payload.read_text(encoding="utf-8")
        text, count = re.subn(
            r"(record\s+\w*IntentPayload\s*\()([^)]*)(\))",
            r"\1\2, String targetStage\3",
            text,
            count=1,
        )
        self.assertEqual(1, count)
        payload.write_text(text, encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "exactly one action field")
        self.assert_error_contains(result, "must not carry target")

    def test_random_success_path_is_rejected(self) -> None:
        cultivation_root = self.fixture_root / "src/main/java/com/example/myvillage/cultivation"
        target = next(
            path
            for path in cultivation_root.rglob("*.java")
            if "interruptionStabilityLoss" in path.read_text(encoding="utf-8")
        )
        target.write_text(
            target.read_text(encoding="utf-8")
            + "\nfinal class ForbiddenRandomAdvancement { Random successChance; }\n",
            encoding="utf-8",
        )

        result = self.validate()

        self.assert_error_contains(result, "must remain deterministic")

    def test_clean_server_stop_penalty_is_rejected(self) -> None:
        manager = (
            self.fixture_root
            / "src/main/java/com/example/myvillage/cultivation/meditation/MeditationManager.java"
        )
        text = manager.read_text(encoding="utf-8")
        text, count = re.subn(
            r"(PENALIZED_INTERRUPTION_REASONS\s*=\s*EnumSet\.of\s*\()",
            r"\1\n            MeditationStopReason.SERVER_STOPPING,",
            text,
            count=1,
        )
        self.assertEqual(1, count)
        manager.write_text(text, encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "SERVER_STOPPING must be non-penalizing")


if __name__ == "__main__":
    unittest.main()
