import shutil
import tempfile
import unittest
from pathlib import Path

from tools.validate_cultivation_meditation import CultivationMeditationValidator


ROOT = Path(__file__).resolve().parents[2]


class CultivationMeditationValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fixture_root = Path(self.temp.name)
        self.build_fixture()

    def build_fixture(self) -> None:
        paths = (
            "src/main/java/com/example/myvillage/cultivation/CultivationEvents.java",
            "src/main/java/com/example/myvillage/cultivation/meditation",
            "src/main/java/com/example/myvillage/cultivation/network",
            "src/main/java/com/example/myvillage/client/cultivation",
            "src/main/resources/assets/myvillage/lang/en_us.json",
            "src/main/resources/assets/myvillage/lang/zh_cn.json",
        )
        for relative in paths:
            source = ROOT / relative
            target = self.fixture_root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():
                shutil.copytree(source, target)
            else:
                shutil.copy2(source, target)

    def validate(self):
        return CultivationMeditationValidator(self.fixture_root).validate()

    def mutate(self, relative: str, old: str, new: str) -> None:
        path = self.fixture_root / relative
        text = path.read_text(encoding="utf-8")
        self.assertIn(old, text)
        path.write_text(text.replace(old, new, 1), encoding="utf-8")

    def assert_error_contains(self, result, expected: str) -> None:
        self.assertTrue(
            any(expected in error for error in result.errors),
            f"expected error containing {expected!r}, got {result.errors!r}",
        )

    def test_complete_shipped_fixture_passes(self) -> None:
        self.assertEqual((), self.validate().errors)

    def test_legacy_g_stop_binding_is_rejected(self) -> None:
        self.mutate(
            "src/main/java/com/example/myvillage/client/cultivation/ClientCultivationKeyMappings.java",
            "GLFW.GLFW_KEY_X",
            "GLFW.GLFW_KEY_G",
        )

        result = self.validate()

        self.assert_error_contains(result, "missing configurable meditation key GLFW_KEY_X")
        self.assert_error_contains(result, "leave GuideME's default G hotkey unreserved")

    def test_client_authored_coordinate_is_rejected(self) -> None:
        self.mutate(
            "src/main/java/com/example/myvillage/cultivation/network/MeditationIntentPayload.java",
            "MeditationIntentPayload(MeditationIntentAction action)",
            "MeditationIntentPayload(MeditationIntentAction action, double positionX)",
        )

        result = self.validate()

        self.assert_error_contains(result, "exactly one action field")
        self.assert_error_contains(result, "must not carry position")

    def test_extra_numeric_intent_field_is_rejected(self) -> None:
        self.mutate(
            "src/main/java/com/example/myvillage/cultivation/network/MeditationIntentPayload.java",
            "MeditationIntentPayload(MeditationIntentAction action)",
            "MeditationIntentPayload(MeditationIntentAction action, int affinity)",
        )

        result = self.validate()

        self.assert_error_contains(result, "exactly one action field")
        self.assert_error_contains(result, "must not carry affinity")

    def test_extra_intent_action_is_rejected(self) -> None:
        self.mutate(
            "src/main/java/com/example/myvillage/cultivation/network/MeditationIntentAction.java",
            "START_BREAKTHROUGH\n}",
            "START_BREAKTHROUGH,\n    SET_RATE\n}",
        )

        result = self.validate()

        self.assert_error_contains(result, "bounded intent actions must be exactly")

    def test_preparation_timing_drift_is_rejected(self) -> None:
        self.mutate(
            "src/main/java/com/example/myvillage/cultivation/meditation/MeditationManager.java",
            "PREPARATION_TICKS = 40",
            "PREPARATION_TICKS = 39",
        )

        result = self.validate()

        self.assert_error_contains(result, "exactly 40 ticks")

    def test_positive_damage_hook_removal_is_rejected(self) -> None:
        self.mutate(
            "src/main/java/com/example/myvillage/cultivation/CultivationEvents.java",
            "LivingDamageEvent.Post",
            "LivingDamageEvent.Pre",
        )

        result = self.validate()

        self.assert_error_contains(result, "positive post-damage interruption hook")

    def test_persisted_session_state_is_rejected(self) -> None:
        path = (
            self.fixture_root
            / "src/main/java/com/example/myvillage/cultivation/meditation/MeditationSession.java"
        )
        path.write_text(
            path.read_text(encoding="utf-8")
            + "\n// contract probe\nfinal class PersistedMeditation extends SavedData {}\n",
            encoding="utf-8",
        )

        result = self.validate()

        self.assert_error_contains(result, "must never be persisted or attached")

    def test_tab_switch_sending_intent_is_rejected(self) -> None:
        relative = "src/main/java/com/example/myvillage/client/cultivation/CultivationProfileScreen.java"
        path = self.fixture_root / relative
        text = path.read_text(encoding="utf-8")
        text, count = text.replace(
            "private void setView(View newView) {",
            "private void setView(View newView) { ClientCultivationIntentSender.send(MeditationIntentAction.STOP);",
            1,
        ), text.count("private void setView(View newView) {")
        self.assertEqual(1, count)
        path.write_text(text, encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "switching H tabs must not send a meditation intent")

    def test_missing_data_fallback_removal_is_rejected(self) -> None:
        self.mutate(
            "src/main/java/com/example/myvillage/client/cultivation/CultivationProfileScreen.java",
            '"screen.myvillage.cultivation.no_snapshot"',
            '"screen.myvillage.cultivation.waiting"',
        )

        result = self.validate()

        self.assert_error_contains(result, "missing-profile state")

    def test_disconnect_cache_cleanup_removal_is_rejected(self) -> None:
        self.mutate(
            "src/main/java/com/example/myvillage/client/cultivation/ClientCultivationEvents.java",
            "ClientCultivationState.clear();",
            "// cache cleanup removed",
        )

        result = self.validate()

        self.assert_error_contains(result, "client meditation status must clear on disconnect")


if __name__ == "__main__":
    unittest.main()
