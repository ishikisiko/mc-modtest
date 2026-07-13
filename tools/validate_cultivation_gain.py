#!/usr/bin/env python3
"""Validate ten-tick affinity gain, caps, and direct spirit-stone transactions."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
JAVA = Path("src/main/java/com/example/myvillage")
REALMS = Path("src/main/resources/data/myvillage/myvillage/realm")
LANG = Path("src/main/resources/assets/myvillage/lang")

EXPECTED_CAPS = {
    "myvillage:mortal_qi_sensed": 1000,
    "myvillage:qi_refining_1": 1100,
    "myvillage:qi_refining_2": 1200,
    "myvillage:qi_refining_3": 1300,
}
EXPECTED_SPIRIT_STONE_COSTS = {
    "myvillage:mortal_qi_sensed": 1,
    "myvillage:qi_refining_1": 1,
    "myvillage:qi_refining_2": 2,
    "myvillage:qi_refining_3": 3,
}


@dataclass(frozen=True)
class ValidationResult:
    errors: tuple[str, ...]
    checked_files: int


class CultivationGainValidator:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.errors: list[str] = []
        self.checked_files: set[Path] = set()

    def label(self, path: Path) -> str:
        try:
            return path.relative_to(self.root).as_posix()
        except ValueError:
            return str(path)

    def error(self, location: str | Path, message: str) -> None:
        label = self.label(location) if isinstance(location, Path) else location
        self.errors.append(f"{label}: {message}")

    def read(self, relative: Path, purpose: str = "required Java source") -> str | None:
        path = self.root / relative
        if not path.is_file():
            self.error(relative, f"missing {purpose}")
            return None
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as exc:
            self.error(relative, f"cannot read UTF-8 file: {exc}")
            return None
        self.checked_files.add(path)
        return text

    def read_tree(self, relative: Path) -> dict[Path, str]:
        base = self.root / relative
        if not base.is_dir():
            self.error(relative, "missing Java source directory")
            return {}
        result: dict[Path, str] = {}
        for path in sorted(base.rglob("*.java")):
            try:
                result[path] = path.read_text(encoding="utf-8")
                self.checked_files.add(path)
            except (OSError, UnicodeError) as exc:
                self.error(path, f"cannot read UTF-8 file: {exc}")
        return result

    def validate_caps(self) -> None:
        stage_path = JAVA / "cultivation/data/RealmStageDefinition.java"
        stage = self.read(stage_path)
        if stage is not None:
            if not re.search(r"Optional(?:<Long>|Long)\s+cultivationCap", stage):
                self.error(stage_path, "RealmStageDefinition must expose optional cultivationCap")
            if 'optionalFieldOf("cultivation_cap")' not in stage:
                self.error(stage_path, "cultivation_cap must be optional definition data")
            if not re.search(r"cultivationCap.*(?:<=\s*0|>\s*0|positive)", stage, re.DOTALL):
                self.error(stage_path, "present cultivation_cap must be validated as positive")
            if not re.search(r"Optional(?:<Integer>|Int)\s+spiritStoneCost", stage):
                self.error(stage_path, "RealmStageDefinition must expose optional spiritStoneCost")
            if 'optionalFieldOf("spirit_stone_cost")' not in stage:
                self.error(stage_path, "spirit_stone_cost must be optional definition data")
            if not re.search(r"spiritStoneCost.*(?:<=\s*0|>\s*0|positive)", stage, re.DOTALL):
                self.error(stage_path, "present spirit_stone_cost must be validated as positive")
            if "stabilityCapFor" not in stage or "cultivationCap / 2" not in stage:
                self.error(stage_path, "stability cap must derive as integer-floor half of cultivation_cap")

        seen: dict[str, tuple[Path, Any, Any]] = {}
        realm_root = self.root / REALMS
        if not realm_root.is_dir():
            self.error(REALMS, "missing realm definitions")
            return
        for path in sorted(realm_root.glob("*.json")):
            self.checked_files.add(path)
            try:
                realm = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, UnicodeError, json.JSONDecodeError) as exc:
                self.error(path, f"invalid realm JSON: {exc}")
                continue
            stages = realm.get("stages") if isinstance(realm, dict) else None
            if not isinstance(stages, list):
                continue
            for stage_value in stages:
                if isinstance(stage_value, dict) and isinstance(stage_value.get("id"), str):
                    seen[stage_value["id"]] = (
                        path,
                        stage_value.get("cultivation_cap"),
                        stage_value.get("spirit_stone_cost"),
                    )

        for stage_id, expected in EXPECTED_CAPS.items():
            if stage_id not in seen:
                self.error(REALMS, f"missing shipped stage {stage_id}")
                continue
            path, actual, _cost = seen[stage_id]
            if type(actual) is not int or actual != expected:
                self.error(path, f"{stage_id} cultivation_cap must be integer {expected}, got {actual!r}")
        for stage_id, expected in EXPECTED_SPIRIT_STONE_COSTS.items():
            if stage_id not in seen:
                continue
            path, cap, actual = seen[stage_id]
            if type(actual) is not int or actual != expected:
                self.error(path, f"{stage_id} spirit_stone_cost must be integer {expected}, got {actual!r}")
            if cap is not None and (type(actual) is not int or actual <= 0):
                self.error(path, f"cultivatable stage {stage_id} must declare positive spirit_stone_cost")
        for stage_id, (path, actual, _cost) in seen.items():
            if stage_id not in EXPECTED_CAPS and actual is not None:
                self.error(path, f"release-ceiling stage {stage_id} must omit cultivation_cap")

    def _gain_sources(self, sources: dict[Path, str]) -> dict[Path, str]:
        needles = (
            "SETTLEMENT_INTERVAL_TICKS",
            "SPIRIT_PROGRESS_PER_SETTLEMENT",
            "spiritStoneCost",
            "InventoryBatchRemoval",
            "RemovedSpiritStones",
            "LOW_GRADE_SPIRIT_STONE",
        )
        return {
            path: text
            for path, text in sources.items()
            if any(needle in text for needle in needles)
        }

    def validate_settlement(self) -> None:
        sources = self.read_tree(JAVA / "cultivation")
        gain_sources = self._gain_sources(sources)
        if not gain_sources:
            self.error(JAVA / "cultivation", "missing Basic Breathing settlement implementation")
            return
        combined = "\n".join(gain_sources.values())

        numeric_contracts = (
            (r"SETTLEMENT_INTERVAL_TICKS\s*=\s*10\b", "settlement interval must be exactly 10 eligible ticks"),
            (r"SPIRIT[A-Z_]*PROGRESS[A-Z_]*\s*=\s*50\b", "spirit progress must be exactly 50 per settlement"),
            (r"MASTERY[A-Z_]*\s*=\s*10\b", "Basic Breathing mastery rate must be 10 per year"),
        )
        for pattern, message in numeric_contracts:
            if re.search(pattern, combined, re.IGNORECASE) is None:
                self.error(JAVA / "cultivation", message)

        remainders = re.search(r"record\s+Remainders\s*\(([^)]*)\)", combined, re.DOTALL)
        remainder_fields = remainders.group(1) if remainders else ""
        if "stability" in remainder_fields:
            self.error(JAVA / "cultivation", "stability must not retain a year-scaled fixed-point remainder")
        if "mastery" not in remainder_fields:
            self.error(JAVA / "cultivation", "mastery fixed-point remainder is missing")
        if re.search(r"progress|bonus|reserve", remainder_fields, re.IGNORECASE):
            self.error(JAVA / "cultivation", "ten-tick progress must not retain a fixed-point or reserve remainder")
        if re.search(r"ticksPerYear\s*\(", combined, re.IGNORECASE) is None:
            self.error(JAVA / "cultivation", "mastery denominator must use the live cultivation-year scale")
        if not any(fragment in combined for fragment in ("Math.multiplyExact", "saturatingAdd", "safeMultiply")):
            self.error(JAVA / "cultivation", "settlement arithmetic must avoid silent overflow")

        for fragment, message in (
            ("BASIC_BREATHING_TECHNIQUE_ID", "settlement must execute only registered Basic Breathing"),
            ("cultivationCap", "settlement must resolve the current stage cultivation cap"),
            ("spiritStoneCost", "spirit settlement must use the source-stage spirit-stone cost"),
            ("LOW_GRADE_SPIRIT_STONE", "settlement must consume only the low-grade spirit stone"),
            ("getInventory()", "stone lookup must be limited to ordinary player inventory"),
            ("CultivationService", "settlement must commit through CultivationService"),
        ):
            if fragment not in combined:
                self.error(JAVA / "cultivation", message)
        if re.search(
            r"requestedProgress\s*=\s*spiritBatch\s*\?\s*"
            r"SPIRIT_PROGRESS_PER_SETTLEMENT\s*:\s*current\.spiritualAffinity\(\)",
            combined,
            re.DOTALL,
        ) is None:
            self.error(JAVA / "cultivation", "normal settlement must use the current server profile affinity")
        if re.search(r"resolved\.stage\(\)\.spiritStoneCost\(\)", combined) is None:
            self.error(JAVA / "cultivation", "stone cost must resolve directly from the authoritative source stage")
        if any(
            re.search(
                r"stageId\(\)[^;\n]{0,160}(?:getPath|split|substring|parseInt)|"
                r"(?:getPath|split|substring|parseInt)[^;\n]{0,160}spiritStoneCost",
                source,
                re.IGNORECASE,
            )
            for source in gain_sources.values()
        ):
            self.error(JAVA / "cultivation", "stone cost must not be inferred from a stage id")
        if "player.setData(" in combined:
            self.error(JAVA / "cultivation", "settlement must not write the attachment directly")

        if not any(fragment in combined for fragment in ("Math.min", "remainingCapacity", "remainingProgress")):
            self.error(JAVA / "cultivation", "progress gain must clamp to remaining stage capacity")
        for fragment, message in (
            ("RealmStageDefinition.stabilityCapFor", "settlement must derive the dynamic stability cap from progress cap"),
            ("current.stability() < stabilityCap", "stability gain must clamp to the dynamic stage cap"),
            ("stabilityApplied", "settlement must expose exact applied stability for behavioral verification"),
            ("withStability(finalStability)", "settlement must commit affinity-paced stability through the profile replacement"),
        ):
            if fragment not in combined:
                self.error(JAVA / "cultivation", message)
        if re.search(
            r"if\s*\(\s*current\.cultivationProgress\(\)\s*>=\s*cultivationCap\s*"
            r"&&\s*current\.stability\(\)\s*<\s*stabilityCap",
            combined,
            re.DOTALL,
        ) is None:
            self.error(JAVA / "cultivation", "stability must remain locked until progress was already full")
        if re.search(
            r"stabilityApplied\s*=\s*Math\.min\s*\(.*?current\.spiritualAffinity\(\)",
            combined,
            re.DOTALL,
        ) is None:
            self.error(JAVA / "cultivation", "post-cap stability must use current spiritual affinity")
        if "STABILITY_PER_YEAR" in combined:
            self.error(JAVA / "cultivation", "stability must not retain the old configured-year rate")
        if "meditationQiReserve" in combined:
            self.error(JAVA / "cultivation", "legacy meditation reserve must be inert in settlement code")
        for fragment, message in (
            ("InventoryBatchRemoval.has", "settlement must preflight the complete source-stage stone cost"),
            ("InventoryBatchRemoval.remove", "settlement must own complete multi-slot stone removal"),
            ("SlotSnapshot", "multi-slot removal must snapshot every touched inventory slot"),
            ("InventoryBatchRemoval.restore", "failed or partial removal must restore every touched slot"),
            ("remaining != 0", "partial multi-slot removal must be detected before commit"),
            ("current.cultivationProgress() < cap", "a capped spirit settlement must not charge at the cap"),
        ):
            if fragment not in combined:
                self.error(JAVA / "cultivation", message)
        manager = next(
            (
                source
                for path, source in gain_sources.items()
                if path.name == "MeditationManager.java"
            ),
            "",
        )
        inventory_transaction = next(
            (
                source
                for path, source in gain_sources.items()
                if path.name == "InventoryBatchRemoval.java"
            ),
            "",
        )
        helper_rollback = (
            "InventoryBatchRemoval.transact" in manager
            and re.search(
                r"removed\s*->\s*InventoryBatchRemoval\.restore\s*\(", manager
            )
            is not None
            and re.search(
                r"if\s*\(\s*install\.getAsBoolean\(\)\s*\).*?"
                r"restore\.accept\(removed\).*?INSTALL_REJECTED",
                inventory_transaction,
                re.DOTALL,
            )
            is not None
            and re.search(
                r"installedAfterFailure\.getAsBoolean\(\).*?"
                r"INSTALL_FAILED_AFTER_COMMIT.*?restore\.accept\(removed\).*?"
                r"INSTALL_FAILED_BEFORE_COMMIT",
                inventory_transaction,
                re.DOTALL,
            )
            is not None
        )
        legacy_rollback = re.search(
            r"if\s*\(\s*!result\.success\(\)\s*\).*?"
            r"(?:InventoryBatchRemoval\.)?restore(?:SpiritStones)?",
            combined,
            re.DOTALL,
        )
        if legacy_rollback is None and not helper_rollback:
            self.error(JAVA / "cultivation", "failed profile commit must restore the full spirit-stone cost")
        legacy_post_install = re.search(
            r"catch\s*\(\s*RuntimeException.*?getProfile\(player\).*?"
            r"equals\(plan\.replacement\(\)\).*?"
            r"(?:InventoryBatchRemoval\.)?restore(?:SpiritStones)?",
            combined,
            re.DOTALL,
        )
        helper_post_install = (
            helper_rollback
            and "INSTALL_FAILED_AFTER_COMMIT" in manager
            and re.search(
                r"installedAfterFailure\.getAsBoolean\(\)\s*\)\s*\{\s*"
                r"return\s+new\s+TransactionResult<.*?INSTALL_FAILED_AFTER_COMMIT",
                inventory_transaction,
                re.DOTALL,
            )
            is not None
        )
        if legacy_post_install is None and not helper_post_install:
            self.error(JAVA / "cultivation", "post-install snapshot failure must not refund an already committed cost")
        if "downgradeToNormal" not in combined:
            self.error(JAVA / "cultivation", "unfunded spirit mode must downgrade to normal meditation")
        if not re.search(
            r"spiritStonesAvailable\s*\?\s*SPIRIT_PROGRESS_PER_SETTLEMENT\s*:\s*current\.spiritualAffinity\(\)|"
            r"spiritBatch\s*\?\s*SPIRIT_PROGRESS_PER_SETTLEMENT\s*:\s*current\.spiritualAffinity\(\)",
            combined,
            re.DOTALL,
        ):
            self.error(JAVA / "cultivation", "unfunded spirit batches must use the normal affinity result")
        if not re.search(r"(?:settlement|activeMeditation|meditating)", combined, re.IGNORECASE):
            self.error(JAVA / "cultivation", "gain must be gated by an active meditation settlement")
        for forbidden in (
            "withCurrentSpiritualPower",
            "withLifespanConsumedTicks",
            "getEnderChestInventory",
            "EnderChest",
            "Random",
            "nextDouble(",
            "nextFloat(",
        ):
            if forbidden in combined:
                self.error(JAVA / "cultivation", f"gain implementation must not use {forbidden}")

    def validate_runtime_and_ui(self) -> None:
        meditation_sources = self.read_tree(JAVA / "cultivation/meditation")
        meditation = "\n".join(meditation_sources.values())
        if "SETTLEMENT_INTERVAL_TICKS" not in meditation and not re.search(
            r"(?:settle|settlement).*10|10.*(?:settle|settlement)", meditation, re.IGNORECASE | re.DOTALL
        ):
            self.error(JAVA / "cultivation/meditation", "active manager must evaluate settlement every 10 eligible meditation ticks")
        if "downgradeToNormal" not in meditation:
            self.error(JAVA / "cultivation/meditation", "manager must expose a one-way spirit-to-normal downgrade")
        if "meditationQiReserve" in meditation:
            self.error(JAVA / "cultivation/meditation", "legacy meditation reserve must remain inert at runtime")

        session_path = JAVA / "cultivation/meditation/MeditationSession.java"
        session = self.read(session_path)
        for fragment, message in (
            ("activeMeditationTicks", "session must own the eligible-tick settlement counter"),
            ("if (!state.meditating())", "only active meditation may advance the settlement counter"),
            ("activeMeditationTicks++", "each eligible server tick must advance the settlement counter once"),
            ("activeMeditationTicks >= BasicBreathingSettlement.SETTLEMENT_INTERVAL_TICKS", "settlement must become due from the ten-tick server constant"),
            ("activeMeditationTicks = 0", "a completed settlement must reset its transient tick counter"),
        ):
            if session is not None and fragment not in session:
                self.error(session_path, message)

        config_path = JAVA / "cultivation/time/CultivationServerConfig.java"
        config = self.read(config_path)
        if config is not None and "onConfigReloading" in config:
            all_cultivation = "\n".join(self.read_tree(JAVA / "cultivation").values())
            if not re.search(r"(?:onScaleReloaded|onConfigReloading).*Meditation|Meditation.*(?:onScaleReloaded|onConfigReloading)", all_cultivation, re.DOTALL):
                self.error(JAVA / "cultivation", "time-scale reload must interrupt sessions before reusing fixed-point remainders")

        screen_path = JAVA / "client/cultivation/CultivationProfileScreen.java"
        screen = self.read(screen_path)
        for fragment, message in (
            ("cultivationProgress()", "H screen must show current stage progress"),
            ("spiritualAffinity()", "H screen must show normal gain from spiritual affinity"),
            ("SPIRIT_PROGRESS_PER_BATCH", "H screen must show the fixed spirit result"),
            ("spiritStoneCost", "H screen must show the source-stage spirit-stone cost"),
            ("stabilityCap", "H screen must show the dynamic stability cap"),
            ("stabilityGainValue", "H screen must show locked, active, or capped stability gain"),
            ("spiritCostValue", "H screen must distinguish progress cost from no-cost stability consolidation"),
            ("masteryPoints()", "H screen must show Basic Breathing mastery"),
        ):
            if screen is not None and fragment not in screen:
                self.error(screen_path, message)
        if screen is not None and (
            "meditationQiReserve" in screen
            or "screen.myvillage.cultivation.reserve" in screen
        ):
            self.error(screen_path, "H screen must not present the inert legacy meditation reserve")
        if screen is not None and not re.search(r"cap|unsupported|unavailable", screen, re.IGNORECASE):
            self.error(screen_path, "H screen must distinguish current cap and unsupported stages")

        required_keys = (
            "screen.myvillage.cultivation.spiritual_affinity",
            "screen.myvillage.cultivation.normal_rate",
            "screen.myvillage.cultivation.spirit_rate",
            "screen.myvillage.cultivation.spirit_cost",
            "screen.myvillage.cultivation.rate_per_ten_ticks",
            "screen.myvillage.cultivation.cost_per_ten_ticks",
            "screen.myvillage.cultivation.stability_gain",
            "screen.myvillage.cultivation.stability_locked",
            "screen.myvillage.cultivation.stability_capped",
            "screen.myvillage.cultivation.stability_no_stone_cost",
        )
        for locale in ("en_us", "zh_cn"):
            path = self.root / LANG / f"{locale}.json"
            if not path.is_file():
                self.error(LANG / f"{locale}.json", "missing language file")
                continue
            self.checked_files.add(path)
            try:
                language = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, UnicodeError, json.JSONDecodeError) as exc:
                self.error(path, f"invalid language JSON: {exc}")
                continue
            for key in required_keys:
                if not isinstance(language.get(key), str) or not language[key].strip():
                    self.error(path, f"missing non-empty cultivation-gain UI key {key}")

    def validate(self) -> ValidationResult:
        self.validate_caps()
        self.validate_settlement()
        self.validate_runtime_and_ui()
        return ValidationResult(tuple(self.errors), len(self.checked_files))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = CultivationGainValidator(args.root.resolve()).validate()
    if result.errors:
        print(f"cultivation gain validation failed ({len(result.errors)} error(s)):", file=sys.stderr)
        for error in result.errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(
        "cultivation gain validation passed: "
        f"checked_files={result.checked_files}; settlement_ticks=10; normal=affinity; "
        "spirit=50; costs=1/1/2/3; caps=1000/1100/1200/1300; "
        "stability=post-cap-affinity@50pct"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
