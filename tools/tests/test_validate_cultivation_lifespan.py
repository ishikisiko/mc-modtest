import json
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.validate_cultivation_lifespan import CultivationLifespanValidator


ROOT = Path(__file__).resolve().parents[2]


class CultivationLifespanValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fixture_root = Path(self.temp.name)
        self.build_fixture()

    def build_fixture(self) -> None:
        paths = (
            "src/main/java/com/example/myvillage/MyVillageMod.java",
            "src/main/java/com/example/myvillage/cultivation/CultivationAttachments.java",
            "src/main/java/com/example/myvillage/cultivation/CultivationCommands.java",
            "src/main/java/com/example/myvillage/cultivation/CultivationEvents.java",
            "src/main/java/com/example/myvillage/cultivation/CultivationProfile.java",
            "src/main/java/com/example/myvillage/cultivation/CultivationService.java",
            "src/main/java/com/example/myvillage/cultivation/data/RealmDefinition.java",
            "src/main/java/com/example/myvillage/cultivation/network/CultivationPayloads.java",
            "src/main/java/com/example/myvillage/cultivation/network/CultivationTimeSnapshotPayload.java",
            "src/main/java/com/example/myvillage/cultivation/time",
            "src/main/java/com/example/myvillage/client/cultivation/CultivationProfileScreen.java",
            "src/main/resources/data/myvillage/myvillage/realm",
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
        return CultivationLifespanValidator(self.fixture_root).validate()

    def mutate_text(self, relative: str, old: str, new: str) -> None:
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

    def test_v1_migration_dispatch_removal_is_rejected(self) -> None:
        self.mutate_text(
            "src/main/java/com/example/myvillage/cultivation/CultivationProfile.java",
            "Codec.either(V3_CODEC, Codec.either(V2_CODEC, V1_CODEC))",
            "Codec.either(V3_CODEC, Codec.either(V2_CODEC, V2_CODEC))",
        )

        result = self.validate()

        self.assert_error_contains(result, "dispatch among v3 and retained v1/v2")

    def test_v2_migration_affinity_default_removal_is_rejected(self) -> None:
        relative = "src/main/java/com/example/myvillage/cultivation/CultivationProfile.java"
        path = self.fixture_root / relative
        text = path.read_text(encoding="utf-8")
        marker = "private record SerializedV2Profile("
        self.assertIn(marker, text)
        prefix, v2_and_after = text.split(marker, 1)
        changed = v2_and_after.replace(
            "DEFAULT_SPIRITUAL_AFFINITY,",
            "0,",
            1,
        )
        self.assertNotEqual(v2_and_after, changed)
        path.write_text(prefix + marker + changed, encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "v2 migration must preserve lifespan/reserve and default affinity to 10")

    def test_default_profile_affinity_drift_is_rejected(self) -> None:
        self.mutate_text(
            "src/main/java/com/example/myvillage/cultivation/CultivationProfile.java",
            "0,\n            DEFAULT_SPIRITUAL_AFFINITY,\n            0,",
            "0,\n            11,\n            0,",
        )

        result = self.validate()

        self.assert_error_contains(result, "new/reset profile must use spiritual affinity 10")

    def test_realm_lifespan_drift_is_rejected(self) -> None:
        relative = "src/main/resources/data/myvillage/myvillage/realm/mortal.json"
        path = self.fixture_root / relative
        value = json.loads(path.read_text(encoding="utf-8"))
        value["maximum_lifespan_years"] = 81
        path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "maximum_lifespan_years must be integer 80")

    def test_batch_interval_drift_is_rejected(self) -> None:
        self.mutate_text(
            "src/main/java/com/example/myvillage/cultivation/time/CultivationTimeRuntime.java",
            "COMMIT_INTERVAL_TICKS = 600",
            "COMMIT_INTERVAL_TICKS = 20",
        )

        result = self.validate()

        self.assert_error_contains(result, "batching interval must be 600 ticks")

    def test_server_save_flush_hook_removal_is_rejected(self) -> None:
        self.mutate_text(
            "src/main/java/com/example/myvillage/cultivation/CultivationEvents.java",
            "CultivationTimeRuntime.onServerSave(level.getServer());",
            "CultivationTimeRuntime.notifyStatus(null);",
        )

        result = self.validate()

        self.assert_error_contains(result, "ordinary server saves must flush pending lifespan")

    def test_vanilla_day_time_dependency_is_rejected(self) -> None:
        relative = (
            "src/main/java/com/example/myvillage/cultivation/time/"
            "CultivationCalendarSavedData.java"
        )
        path = self.fixture_root / relative
        text = path.read_text(encoding="utf-8")
        path.write_text(
            text.rsplit("}", 1)[0]
            + "\n    private long forbiddenVanillaClock() { return level.getDayTime(); }\n}\n",
            encoding="utf-8",
        )

        result = self.validate()

        self.assert_error_contains(result, "must not derive from vanilla world/day time")

    def test_h_screen_legacy_reserve_presentation_is_rejected(self) -> None:
        relative = "src/main/java/com/example/myvillage/client/cultivation/CultivationProfileScreen.java"
        path = self.fixture_root / relative
        text = path.read_text(encoding="utf-8")
        path.write_text(
            text.rsplit("}", 1)[0]
            + "\n    long forbiddenReserve(Profile profile) { return profile.meditationQiReserve(); }\n}\n",
            encoding="utf-8",
        )

        result = self.validate()

        self.assert_error_contains(result, "must not present the inert legacy meditation reserve")


if __name__ == "__main__":
    unittest.main()
