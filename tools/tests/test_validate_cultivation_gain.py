import json
import re
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.validate_cultivation_gain import CultivationGainValidator


ROOT = Path(__file__).resolve().parents[2]


class CultivationGainValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fixture_root = Path(self.temp.name)
        self.build_fixture()

    def build_fixture(self) -> None:
        paths = (
            "src/main/java/com/example/myvillage/cultivation",
            "src/main/java/com/example/myvillage/alchemy",
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
        return CultivationGainValidator(self.fixture_root).validate()

    def assert_error_contains(self, result, expected: str) -> None:
        self.assertTrue(
            any(expected in error for error in result.errors),
            f"expected error containing {expected!r}, got {result.errors!r}",
        )

    def find_java(self, pattern: str) -> Path:
        regex = re.compile(pattern, re.IGNORECASE | re.DOTALL)
        root = self.fixture_root / "src/main/java/com/example/myvillage/cultivation"
        for path in sorted(root.rglob("*.java")):
            if regex.search(path.read_text(encoding="utf-8")):
                return path
        self.fail(f"fixture contains no Java source matching {pattern!r}")

    def test_complete_shipped_fixture_passes(self) -> None:
        self.assertEqual((), self.validate().errors)

    def test_stage_cap_drift_is_rejected(self) -> None:
        path = (
            self.fixture_root
            / "src/main/resources/data/myvillage/myvillage/realm/qi_refining.json"
        )
        realm = json.loads(path.read_text(encoding="utf-8"))
        realm["stages"][0]["cultivation_cap"] = 1101
        path.write_text(json.dumps(realm, indent=2) + "\n", encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "cultivation_cap must be integer 1100")

    def test_normal_affinity_source_drift_is_rejected(self) -> None:
        path = self.find_java(r"current\.spiritualAffinity\(\)")
        text = path.read_text(encoding="utf-8")
        text, count = re.subn(r"current\.spiritualAffinity\(\)", "current.stability()", text, count=1)
        self.assertEqual(1, count)
        path.write_text(text, encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "normal settlement must use the current server profile affinity")

    def test_ten_tick_interval_drift_is_rejected(self) -> None:
        path = self.find_java(r"SETTLEMENT_INTERVAL_TICKS\s*=\s*10\b")
        text = path.read_text(encoding="utf-8")
        path.write_text(
            text.replace("SETTLEMENT_INTERVAL_TICKS = 10", "SETTLEMENT_INTERVAL_TICKS = 11", 1),
            encoding="utf-8",
        )

        result = self.validate()

        self.assert_error_contains(result, "settlement interval must be exactly 10 eligible ticks")

    def test_fixed_spirit_result_drift_is_rejected(self) -> None:
        path = self.find_java(r"SPIRIT[A-Z_]*PROGRESS[A-Z_]*\s*=\s*50\b")
        text = path.read_text(encoding="utf-8")
        text, count = re.subn(
            r"(SPIRIT[A-Z_]*PROGRESS[A-Z_]*\s*=\s*)50\b",
            r"\g<1>49",
            text,
            count=1,
        )
        self.assertEqual(1, count)
        path.write_text(text, encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "spirit progress must be exactly 50 per settlement")

    def test_progress_remainder_reintroduction_is_rejected(self) -> None:
        path = self.find_java(r"record\s+Remainders\s*\(long\s+mastery\)")
        text = path.read_text(encoding="utf-8")
        text = text.replace(
            "record Remainders(long mastery)",
            "record Remainders(long mastery, long progressRemainder)",
            1,
        )
        path.write_text(text, encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "must not retain a fixed-point or reserve remainder")

    def test_stability_year_remainder_reintroduction_is_rejected(self) -> None:
        path = self.find_java(r"record\s+Remainders\s*\(long\s+mastery\)")
        text = path.read_text(encoding="utf-8")
        path.write_text(
            text.replace(
                "record Remainders(long mastery)",
                "record Remainders(long stability, long mastery)",
                1,
            ),
            encoding="utf-8",
        )

        result = self.validate()

        self.assert_error_contains(result, "stability must not retain a year-scaled")

    def test_stability_cap_ratio_drift_is_rejected(self) -> None:
        path = self.find_java(r"cultivationCap\s*/\s*2")
        text = path.read_text(encoding="utf-8")
        path.write_text(text.replace("cultivationCap / 2", "cultivationCap / 3", 1), encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "integer-floor half of cultivation_cap")

    def test_pre_cap_stability_lock_removal_is_rejected(self) -> None:
        path = self.find_java(r"current\.cultivationProgress\(\)\s*>=\s*cultivationCap")
        text = path.read_text(encoding="utf-8")
        path.write_text(
            text.replace(
                "if (current.cultivationProgress() >= cultivationCap",
                "if (current.cultivationProgress() > cultivationCap",
                1,
            ),
            encoding="utf-8",
        )

        result = self.validate()

        self.assert_error_contains(result, "stability must remain locked until progress was already full")

    def mutate_medicine_source(self, relative, before, after):
        path = self.fixture_root / "src/main/java/com/example/myvillage" / relative
        source = path.read_text(encoding="utf-8")
        self.assertIn(before, source)
        path.write_text(source.replace(before, after, 1), encoding="utf-8")
        return self.validate()

    def test_post_cap_stability_affinity_drift_is_rejected(self) -> None:
        path = self.find_java(r"MedicineEffects\.stabilityGain")
        text, count = re.subn(
            r"(MedicineEffects\.stabilityGain\(\s*)current\.spiritualAffinity\(\)",
            r"\g<1>SPIRIT_PROGRESS_PER_SETTLEMENT", path.read_text(encoding="utf-8"), count=1,
        )
        self.assertEqual(1, count)
        path.write_text(text, encoding="utf-8")
        self.assert_error_contains(self.validate(), "post-cap stability must use current spiritual affinity")

    def test_medicine_stability_requires_remaining_stage_capacity(self):
        result = self.mutate_medicine_source(
            "cultivation/meditation/BasicBreathingSettlement.java",
            "(long) stabilityCap - current.stability()", "(long) stabilityCap",
        )
        self.assert_error_contains(result, "medicine stability gain must receive remaining dynamic stage capacity")

    def test_medicine_helper_cannot_drop_remaining_capacity_clamp(self):
        result = self.mutate_medicine_source(
            "alchemy/MedicineEffects.java", "Math.min(remaining,", "Math.max(remaining,",
        )
        self.assert_error_contains(result, "medicine stability helper must clamp multiplied affinity")

    def test_medicine_multiplier_cannot_apply_to_study(self):
        result = self.mutate_medicine_source(
            "cultivation/meditation/BasicBreathingSettlement.java",
            "mode == MeditationMode.STUDY ? 1.0 : stabilityMultiplier", "stabilityMultiplier",
        )
        self.assert_error_contains(result, "medicine multiplier must apply only outside study settlement")

    def test_medicine_multiplier_cannot_leak_into_progress(self):
        result = self.mutate_medicine_source(
            "cultivation/meditation/BasicBreathingSettlement.java",
            "CoreTechniqueFactor.apply(requestedProgress, progressFactorBasisPoints)",
            "CoreTechniqueFactor.apply((long) (requestedProgress * stabilityMultiplier), progressFactorBasisPoints)",
        )
        self.assert_error_contains(result, "medicine multiplier must affect stability only, never progress or mastery")

    def test_unmedicated_planner_must_retain_one_multiplier(self):
        result = self.mutate_medicine_source(
            "cultivation/meditation/BasicBreathingSettlement.java",
            "progressFactorBasisPoints, 1.0);", "progressFactorBasisPoints, 1.5);",
        )
        self.assert_error_contains(result, "ordinary settlement overload must retain the medicine multiplier 1.0 baseline")

    def test_expired_medicine_must_retain_one_multiplier(self):
        result = self.mutate_medicine_source(
            "alchemy/MedicineRuntime.java", "stabilityMultiplier() : 1.0;", "stabilityMultiplier() : 1.5;",
        )
        self.assert_error_contains(result, "only active ningshen may supply the authored multiplier")

    def test_commit_rollback_removal_is_rejected(self) -> None:
        path = self.find_java(r"LOW_GRADE_SPIRIT_STONE.*CultivationService|CultivationService.*LOW_GRADE_SPIRIT_STONE")
        text = path.read_text(encoding="utf-8")
        original = text
        text = re.sub(r"restore", "discard", text, flags=re.IGNORECASE)
        text = text.replace("placeItemBackInInventory", "discardRemovedItem")
        text = text.replace(".add(", ".discardRemoved(")
        self.assertNotEqual(original, text)
        path.write_text(text, encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "restore every touched slot")

    def test_spirit_downgrade_removal_is_rejected(self) -> None:
        root = self.fixture_root / "src/main/java/com/example/myvillage/cultivation"
        changed = False
        for path in sorted(root.rglob("*.java")):
            text = path.read_text(encoding="utf-8")
            replaced = re.sub(r"downgrade", "stopAcceleration", text, flags=re.IGNORECASE)
            if replaced != text:
                path.write_text(replaced, encoding="utf-8")
                changed = True
        self.assertTrue(changed, "fixture must expose an explicit downgrade path")

        result = self.validate()

        self.assert_error_contains(result, "downgrade to normal meditation")

    def test_stage_spirit_cost_drift_is_rejected(self) -> None:
        path = (
            self.fixture_root
            / "src/main/resources/data/myvillage/myvillage/realm/qi_refining.json"
        )
        realm = json.loads(path.read_text(encoding="utf-8"))
        realm["stages"][1]["spirit_stone_cost"] = 1
        path.write_text(json.dumps(realm, indent=2) + "\n", encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(result, "PROGRESSION_BASELINE: stage myvillage:qi_refining_2 spirit_stone_cost must be 2")

    def test_legacy_reserve_use_is_rejected(self) -> None:
        path = self.find_java(r"class\s+BasicBreathingSettlement")
        text = path.read_text(encoding="utf-8")
        path.write_text(
            text.rsplit("}", 1)[0]
            + "\n    long forbiddenReserve(CultivationProfile profile) { return profile.meditationQiReserve(); }\n}\n",
            encoding="utf-8",
        )

        result = self.validate()

        self.assert_error_contains(result, "legacy meditation reserve must be inert")

    def test_full_progress_no_cost_ui_removal_is_rejected(self) -> None:
        screen = (
            self.fixture_root
            / "src/main/java/com/example/myvillage/client/cultivation/panel/MeditationPage.java"
        )
        text = screen.read_text(encoding="utf-8")
        self.assertIn("spiritCostValue", text)
        screen.write_text(text.replace("spiritCostValue", "legacySpiritCost", 2), encoding="utf-8")

        result = self.validate()

        self.assert_error_contains(
            result,
            "distinguish progress cost from no-cost stability consolidation",
        )


if __name__ == "__main__":
    unittest.main()
