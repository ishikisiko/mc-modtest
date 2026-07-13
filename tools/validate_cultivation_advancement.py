#!/usr/bin/env python3
"""Validate deterministic Qi-sensed through Qi-IV advancement contracts."""

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

EXPECTED_RULES = {
    "myvillage:mortal_qi_sensed": {
        "target_realm": "myvillage:qi_refining",
        "target_stage": "myvillage:qi_refining_1",
        "kind": "ordinary",
        "duration_ticks": 100,
        "required_stability": 500,
        "stability_cost": 250,
        "interruption_stability_loss": 0,
    },
    "myvillage:qi_refining_1": {
        "target_realm": "myvillage:qi_refining",
        "target_stage": "myvillage:qi_refining_2",
        "kind": "ordinary",
        "duration_ticks": 100,
        "required_stability": 550,
        "stability_cost": 275,
        "interruption_stability_loss": 0,
    },
    "myvillage:qi_refining_2": {
        "target_realm": "myvillage:qi_refining",
        "target_stage": "myvillage:qi_refining_3",
        "kind": "ordinary",
        "duration_ticks": 120,
        "required_stability": 600,
        "stability_cost": 300,
        "interruption_stability_loss": 0,
    },
    "myvillage:qi_refining_3": {
        "target_realm": "myvillage:qi_refining",
        "target_stage": "myvillage:qi_refining_4",
        "kind": "bottleneck",
        "duration_ticks": 200,
        "required_stability": 650,
        "stability_cost": 325,
        "interruption_stability_loss": 5,
    },
}


@dataclass(frozen=True)
class ValidationResult:
    errors: tuple[str, ...]
    checked_files: int


class CultivationAdvancementValidator:
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

    def validate_definition_codec(self) -> None:
        stage_path = JAVA / "cultivation/data/RealmStageDefinition.java"
        stage = self.read(stage_path)
        if stage is None:
            return
        if not re.search(r"Optional<\w*(?:Advancement|Breakthrough)\w*>\s+advancement", stage):
            self.error(stage_path, "RealmStageDefinition must expose optional advancement metadata")
        if 'optionalFieldOf("advancement")' not in stage:
            self.error(stage_path, "advancement must be optional stage definition data")

        data_sources = self.read_tree(JAVA / "cultivation/data")
        combined = "\n".join(data_sources.values())
        for field in (
            "target_realm",
            "target_stage",
            "kind",
            "duration_ticks",
            "required_stability",
            "stability_cost",
            "interruption_stability_loss",
        ):
            if f'"{field}"' not in combined:
                self.error(JAVA / "cultivation/data", f"advancement codec omits {field}")
        for enum_value in ("ORDINARY", "BOTTLENECK"):
            if enum_value not in combined:
                self.error(JAVA / "cultivation/data", f"advancement kind omits {enum_value}")
        if not re.search(r"stabilityCost.*requiredStability|stability_cost.*required_stability", combined, re.IGNORECASE | re.DOTALL):
            self.error(JAVA / "cultivation/data", "advancement validation must compare success cost with required stability")
        for fragment, message in (
            ("stabilityCapFor", "stage definitions must derive stability cap from cultivation cap"),
            ("definition.requiredStability() != stabilityCap", "advancement requirement must equal the dynamic stability cap"),
            ("stabilityCap - stabilityCap / 2", "compatibility stability cost must describe half retention"),
        ):
            if fragment not in combined:
                self.error(JAVA / "cultivation/data", message)
        if not re.search(r"durationTicks.*(?:<=\s*0|positive)", combined, re.IGNORECASE | re.DOTALL):
            self.error(JAVA / "cultivation/data", "advancement duration must be validated as positive")

        registries_path = JAVA / "cultivation/data/ModCultivationRegistries.java"
        registries = self.read(registries_path)
        for fragment, message in (
            ("stage.advancement().ifPresent", "registry validation must inspect every declared advancement"),
            ("stage.cultivationCap().isEmpty()", "a stage with advancement must also declare a cap"),
            ("realms.get(advancement.targetRealm())", "registry validation must resolve the target realm"),
            ("targetRealm.containsStage(advancement.targetStage())", "registry validation must resolve target-stage ownership"),
        ):
            if registries is not None and fragment not in registries:
                self.error(registries_path, message)

    def validate_rules(self) -> None:
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
            for stage in stages:
                if isinstance(stage, dict) and isinstance(stage.get("id"), str):
                    seen[stage["id"]] = (
                        path,
                        stage.get("cultivation_cap"),
                        stage.get("advancement"),
                    )

        targets: set[tuple[str, str]] = set()
        for source_id, expected in EXPECTED_RULES.items():
            if source_id not in seen:
                self.error(REALMS, f"missing shipped advancement source {source_id}")
                continue
            path, actual_cap, actual = seen[source_id]
            expected_cap = EXPECTED_CAPS[source_id]
            if type(actual_cap) is not int or actual_cap != expected_cap:
                self.error(
                    path,
                    f"{source_id} cultivation_cap must be integer {expected_cap}, got {actual_cap!r}",
                )
            if isinstance(actual, dict) and type(actual_cap) is int:
                expected_stability = actual_cap // 2
                if actual.get("required_stability") != expected_stability:
                    self.error(
                        path,
                        f"{source_id} required_stability must be half of cultivation_cap",
                    )
                expected_cost = expected_stability - expected_stability // 2
                if actual.get("stability_cost") != expected_cost:
                    self.error(
                        path,
                        f"{source_id} stability_cost must retain half of required stability",
                    )
            if actual != expected:
                self.error(path, f"{source_id} advancement must be {expected!r}, got {actual!r}")
                continue
            target = (actual["target_realm"], actual["target_stage"])
            if target in targets:
                self.error(path, f"duplicate advancement target {target[0]}/{target[1]}")
            targets.add(target)
            if actual["target_stage"] == source_id:
                self.error(path, f"advancement source {source_id} must not target itself")

        for stage_id, (path, cap, advancement) in seen.items():
            if stage_id not in EXPECTED_RULES and advancement is not None:
                self.error(path, f"release-ceiling stage {stage_id} must omit advancement metadata")
            if stage_id not in EXPECTED_CAPS and cap is not None:
                self.error(path, f"release-ceiling stage {stage_id} must omit cultivation_cap")
        qi4 = seen.get("myvillage:qi_refining_4")
        if qi4 is None:
            self.error(REALMS, "missing Qi Refining IV release endpoint")
        elif qi4[1] is not None or qi4[2] is not None:
            self.error(qi4[0], "Qi Refining IV must be an advancement endpoint with no further rule")

    def validate_intent_and_keys(self) -> None:
        network_sources = self.read_tree(JAVA / "cultivation/network")
        action_text = "\n".join(network_sources.values())
        if "START_NORMAL" not in action_text or "START_SPIRIT" not in action_text:
            self.error(JAVA / "cultivation/network", "missing extended meditation intent action definition")
        else:
            required_starts = {"START_NORMAL", "START_SPIRIT", "START_BREAKTHROUGH"}
            starts = set(re.findall(r"\bSTART_[A-Z_]+\b", action_text))
            if starts != required_starts:
                self.error(
                    JAVA / "cultivation/network",
                    f"bounded intent start actions must be {sorted(required_starts)}, got {sorted(starts)}",
                )
            if "STOP" not in action_text:
                self.error(JAVA / "cultivation/network", "bounded intent must retain STOP")
            records = re.findall(r"record\s+\w*IntentPayload\s*\(([^)]*)\)", action_text, re.DOTALL)
            if not records or any("," in fields for fields in records):
                self.error(JAVA / "cultivation/network", "extended intent must still contain exactly one action field")
            for forbidden in ("target", "duration", "stability", "progress", "result", "success", "UUID", "BlockPos"):
                if records and forbidden.lower() in records[0].lower():
                    self.error(JAVA / "cultivation/network", f"breakthrough intent must not carry {forbidden}")

        client_sources = self.read_tree(JAVA / "client/cultivation")
        client = "\n".join(client_sources.values())
        for key, action in (
            ("GLFW_KEY_V", "START_NORMAL"),
            ("GLFW_KEY_B", "START_SPIRIT"),
            ("GLFW_KEY_X", "STOP"),
            ("GLFW_KEY_N", "START_BREAKTHROUGH"),
        ):
            if key not in client:
                self.error(JAVA / "client/cultivation", f"missing configurable advancement control {key}")
            if action not in client:
                self.error(JAVA / "client/cultivation", f"client controls omit {action}")
        screen_path = JAVA / "client/cultivation/CultivationProfileScreen.java"
        screen = client_sources.get(self.root / screen_path)
        if screen is not None:
            if "START_BREAKTHROUGH" not in screen:
                self.error(screen_path, "H meditation tab must expose the advancement action")
            if not re.search(
                r"(?:ClientCultivationIntentSender\.send|PacketDistributor\.sendToServer)\s*\(",
                screen,
            ):
                self.error(screen_path, "H advancement button must reuse the bounded intent sender")

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
            key = "screen.myvillage.cultivation.button.advancement"
            if not isinstance(language.get(key), str) or not language[key].strip():
                self.error(path, f"missing non-empty advancement UI key {key}")

    def _advancement_sources(self, sources: dict[Path, str]) -> dict[Path, str]:
        needles = (
            "START_BREAKTHROUGH",
            "interruptionStabilityLoss",
            "requiredStability",
            "stabilityCost",
            "ADVANCING_",
            "BREAKTHROUGH_",
        )
        return {
            path: text
            for path, text in sources.items()
            if any(needle in text for needle in needles)
        }

    def validate_runtime(self) -> None:
        sources = self.read_tree(JAVA / "cultivation")
        advancement_sources = self._advancement_sources(sources)
        if not advancement_sources:
            self.error(JAVA / "cultivation", "missing deterministic advancement runtime")
            return
        combined = "\n".join(advancement_sources.values())
        meditation = "\n".join(
            text for path, text in sources.items() if "/meditation/" in path.as_posix()
        )

        if not re.search(r"ADVANC(?:ING|EMENT)[A-Z_]*ORDINARY|ORDINARY[A-Z_]*ADVANC", meditation):
            self.error(JAVA / "cultivation/meditation", "session state must represent ordinary advancement")
        if not re.search(r"ADVANC(?:ING|EMENT)[A-Z_]*BOTTLENECK|BOTTLENECK[A-Z_]*ADVANC", meditation):
            self.error(JAVA / "cultivation/meditation", "session state must represent bottleneck advancement")
        for fragment, message in (
            ("Map<UUID, MeditationSession>", "one UUID-keyed manager must own meditation and advancement exclusion"),
            ("cultivationCap", "advancement start must resolve the current stage cap"),
            ("requiredStability", "advancement start must check definition-owned stability requirement"),
            ("durationTicks", "advancement duration must come from current definition data"),
            ("targetRealm", "advancement target realm must come from current definition data"),
            ("targetStage", "advancement target stage must come from current definition data"),
            ("BASIC_BREATHING_TECHNIQUE_ID", "advancement must require learned registered Basic Breathing"),
            ("exhausted()", "advancement must reject exhausted lifespan"),
            ("CultivationService", "advancement transitions and penalties must use CultivationService"),
            ("interruptionStabilityLoss", "bottleneck interruption must use definition-owned loss"),
            ("AdvancementKind.BOTTLENECK", "interruption loss must apply only to bottleneck advancement"),
            ("Math.max(0", "interruption stability loss must clamp at zero"),
        ):
            if fragment not in combined:
                self.error(JAVA / "cultivation", message)

        if not re.search(r"withRealmAndStage|targetRealm.*targetStage", combined, re.DOTALL):
            self.error(JAVA / "cultivation", "success must install the explicit target realm/stage")
        if not re.search(r"withCultivationProgress\(0\)|cultivationProgress[^\n]*0", combined):
            self.error(JAVA / "cultivation", "success must reset stage-local progress to zero")
        if re.search(
            r"withStability\s*\(\s*current\.stability\(\)\s*/\s*2\s*\)",
            combined,
        ) is None:
            self.error(JAVA / "cultivation", "successful advancement must retain integer-floor half of current stability")
        if re.search(
            r"withStability\s*\([^)]*stabilityCost|current\.stability\(\)\s*-\s*definition\.stabilityCost",
            combined,
            re.DOTALL,
        ):
            self.error(JAVA / "cultivation", "successful advancement must not deduct a fixed stability cost")
        if "player.setData(" in combined:
            self.error(JAVA / "cultivation", "advancement must not write the profile attachment directly")
        for forbidden in ("Random", "nextDouble(", "nextFloat(", "nextBoolean(", "successChance", "failureChance"):
            if forbidden in combined:
                self.error(JAVA / "cultivation", f"advancement must remain deterministic and not use {forbidden}")
        penalized = re.search(
            r"PENALIZED_INTERRUPTION_REASONS\s*=\s*EnumSet\.of\s*\((.*?)\)\s*;",
            meditation,
            re.DOTALL,
        )
        if penalized is None:
            self.error(
                JAVA / "cultivation/meditation",
                "advancement interruption penalties must use an explicit reason allow-list",
            )
        else:
            for clean_reason in (
                "SERVER_STOPPING",
                "CONFIG_RELOADED",
                "DEFINITION_RELOADED",
                "PROFILE_INVALIDATED",
                "ADVANCEMENT_INVALIDATED",
            ):
                if clean_reason in penalized.group(1):
                    self.error(
                        JAVA / "cultivation/meditation",
                        f"clean cancellation reason {clean_reason} must be non-penalizing",
                    )
        if not re.search(r"SESSIONS\.remove|requestStop", meditation):
            self.error(JAVA / "cultivation/meditation", "completion/interruption must remove one transient session")

    def validate(self) -> ValidationResult:
        self.validate_definition_codec()
        self.validate_rules()
        self.validate_intent_and_keys()
        self.validate_runtime()
        return ValidationResult(tuple(self.errors), len(self.checked_files))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = CultivationAdvancementValidator(args.root.resolve()).validate()
    if result.errors:
        print(f"cultivation advancement validation failed ({len(result.errors)} error(s)):", file=sys.stderr)
        for error in result.errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(
        "cultivation advancement validation passed: "
        f"checked_files={result.checked_files}; caps=1000/1100/1200/1300; "
        "stability=500/550/600/650; retention=50pct; transitions=4; "
        "endpoint=qi_refining_4; random=none"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
