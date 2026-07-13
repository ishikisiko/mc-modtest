#!/usr/bin/env python3
"""Validate cultivation profile-v3, lifespan, and shared-calendar contracts."""

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


class DuplicateJsonKey(ValueError):
    pass


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJsonKey(f"duplicate JSON key {key!r}")
        result[key] = value
    return result


@dataclass(frozen=True)
class ValidationResult:
    errors: tuple[str, ...]
    checked_files: int


class CultivationLifespanValidator:
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
            value = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as exc:
            self.error(relative, f"cannot read UTF-8 file: {exc}")
            return None
        self.checked_files.add(path)
        return value

    def load_json(self, relative: Path) -> dict[str, Any] | None:
        text = self.read(relative, "realm JSON")
        if text is None:
            return None
        try:
            value = json.loads(text, object_pairs_hook=_reject_duplicate_keys)
        except (json.JSONDecodeError, DuplicateJsonKey) as exc:
            self.error(relative, f"invalid JSON: {exc}")
            return None
        if not isinstance(value, dict):
            self.error(relative, "realm definition must be an object")
            return None
        return value

    def require(self, relative: Path, text: str | None, fragment: str, message: str) -> None:
        if text is not None and fragment not in text:
            self.error(relative, message)

    def validate_profile(self) -> None:
        profile_path = JAVA / "cultivation/CultivationProfile.java"
        profile = self.read(profile_path)
        if profile is None:
            return
        for fragment, message in (
            ("long lifespanConsumedTicks", "profile must store lifespanConsumedTicks as long"),
            ("long meditationQiReserve", "profile must store meditationQiReserve as long"),
            ("int spiritualAffinity", "profile must store spiritualAffinity as an integer"),
            ("CURRENT_SCHEMA_VERSION = 3", "current profile schema must be version 3"),
            ("DEFAULT_SPIRITUAL_AFFINITY = 10", "default spiritual affinity must be 10"),
            ("Codec<SerializedV1Profile> V1_CODEC", "explicit v1 DTO codec is missing"),
            ("Codec<SerializedV2Profile> V2_CODEC", "explicit v2 DTO codec is missing"),
            ("Codec<SerializedV3Profile> V3_CODEC", "explicit v3 DTO codec is missing"),
            ("Codec.either(V3_CODEC, Codec.either(V2_CODEC, V1_CODEC))", "profile codec must dispatch among v3 and retained v1/v2"),
            ("SerializedV1Profile::migrate", "profile codec must invoke explicit v1 migration"),
            ("SerializedV2Profile::migrate", "profile codec must invoke explicit v2 migration"),
            ("Either.left(profile.serializeV3())", "profile encoder must emit only the v3 shape"),
            ('fieldOf("spiritual_affinity")', "v3 codec omits spiritual_affinity"),
            ('fieldOf("lifespan_consumed_ticks")', "v2/v3 codecs omit lifespan_consumed_ticks"),
            ('fieldOf("meditation_qi_reserve")', "v2/v3 codecs omit meditation_qi_reserve"),
            ("if (spiritualAffinity < 0)", "spiritual affinity must reject negative values"),
            ("if (lifespanConsumedTicks < 0)", "lifespan counter must reject negative values"),
            ("if (meditationQiReserve < 0)", "reserve counter must reject negative values"),
            ("withSpiritualAffinity", "profile needs an affinity-preserving copy helper"),
            ("withLifespanConsumedTicks", "profile needs a lifespan-preserving copy helper"),
            ("withMeditationQiReserve", "profile needs a reserve-preserving copy helper"),
        ):
            self.require(profile_path, profile, fragment, message)
        if re.search(
            r"DEFAULT\s*=\s*new\s+CultivationProfile\s*\(\s*"
            r"CURRENT_SCHEMA_VERSION\s*,\s*DEFAULT_REALM_ID\s*,\s*DEFAULT_STAGE_ID\s*,\s*"
            r"0\s*,\s*0\s*,\s*0\s*,\s*DEFAULT_SPIRITUAL_AFFINITY\s*,\s*0\s*,\s*0\s*,",
            profile,
            re.DOTALL,
        ) is None:
            self.error(profile_path, "new/reset profile must use spiritual affinity 10 and preserve prior numeric defaults")

        v1_codec_match = re.search(
            r"V1_CODEC\s*=.*?\)\);(?P<body>.*?)private\s+static\s+final\s+Codec<SerializedV2Profile>",
            profile,
            re.DOTALL,
        )
        if v1_codec_match and any(
            field in v1_codec_match.group("body")
            for field in ("lifespan_consumed_ticks", "meditation_qi_reserve", "spiritual_affinity")
        ):
            self.error(profile_path, "v1 codec must retain the exact legacy field shape")
        v2_codec_match = re.search(
            r"V2_CODEC\s*=.*?\)\);(?P<body>.*?)private\s+static\s+final\s+Codec<SerializedV3Profile>",
            profile,
            re.DOTALL,
        )
        if v2_codec_match and "spiritual_affinity" in v2_codec_match.group("body"):
            self.error(profile_path, "v2 codec must retain the exact legacy field shape")
        if re.search(
            r"new\s+SerializedV2Profile\s*\(\s*2\s*,\s*realmId\s*,\s*stageId\s*,\s*"
            r"cultivationProgress\s*,\s*stability\s*,\s*currentSpiritualPower\s*,\s*"
            r"0\s*,\s*0\s*,\s*spiritualRoot\s*,\s*learnedTechniques\s*\)\.migrate\(\)",
            profile,
            re.DOTALL,
        ) is None:
            self.error(profile_path, "v1 migration must preserve old fields, initialize v2 counters to zero, and continue through v2 migration")
        if re.search(
            r"CURRENT_SCHEMA_VERSION\s*,\s*realmId\s*,\s*stageId\s*,\s*"
            r"cultivationProgress\s*,\s*stability\s*,\s*currentSpiritualPower\s*,\s*"
            r"DEFAULT_SPIRITUAL_AFFINITY\s*,\s*lifespanConsumedTicks\s*,\s*"
            r"meditationQiReserve\s*,\s*spiritualRoot\s*,\s*learnedTechniques",
            profile,
            re.DOTALL,
        ) is None:
            self.error(profile_path, "v2 migration must preserve lifespan/reserve and default affinity to 10")

        attachments_path = JAVA / "cultivation/CultivationAttachments.java"
        attachments = self.read(attachments_path)
        self.require(attachments_path, attachments, ".copyOnDeath()", "profile attachment must retain copyOnDeath")

        cultivation_root = self.root / JAVA / "cultivation"
        if cultivation_root.is_dir():
            for path in sorted(cultivation_root.rglob("*.java")):
                text = path.read_text(encoding="utf-8")
                if "PlayerEvent.Clone" in text:
                    self.error(path, "cultivation profile must not add a PlayerEvent.Clone copy path")

    def validate_realms(self) -> None:
        definition_path = JAVA / "cultivation/data/RealmDefinition.java"
        definition = self.read(definition_path)
        for fragment, message in (
            ("int maximumLifespanYears", "RealmDefinition must expose maximum lifespan"),
            ('Codec.INT.fieldOf("maximum_lifespan_years")', "maximum_lifespan_years must be required codec data"),
            ("maximumLifespanYears <= 0", "realm codec must reject non-positive maximum lifespan"),
        ):
            self.require(definition_path, definition, fragment, message)

        for realm, expected in (
            ("mortal", 80),
            ("qi_refining", 120),
            ("foundation_establishment", 240),
        ):
            path = REALMS / f"{realm}.json"
            value = self.load_json(path)
            if value is None:
                continue
            actual = value.get("maximum_lifespan_years")
            if type(actual) is not int or actual != expected:
                self.error(path, f"maximum_lifespan_years must be integer {expected}, got {actual!r}")

    def validate_time_model(self) -> None:
        config_path = JAVA / "cultivation/time/CultivationServerConfig.java"
        config = self.read(config_path)
        for fragment, message in (
            ("DEFAULT_TICKS_PER_DAY = 24_000", "default ticks-per-day must be 24000"),
            ("DEFAULT_DAYS_PER_YEAR = 6", "default days-per-year must be 6"),
            ('.defineInRange("ticks_per_day", DEFAULT_TICKS_PER_DAY, 1, Integer.MAX_VALUE)', "ticks_per_day must be a positive server config"),
            ('.defineInRange("days_per_year", DEFAULT_DAYS_PER_YEAR, 1, Integer.MAX_VALUE)', "days_per_year must be a positive server config"),
            ("Stored calendar/lifespan values are raw ticks and are not rescaled", "config load/reload must warn about raw-tick reinterpretation"),
            ("onConfigReloading", "time-scale reload hook is missing"),
        ):
            self.require(config_path, config, fragment, message)

        mod_path = JAVA / "MyVillageMod.java"
        mod = self.read(mod_path)
        self.require(mod_path, mod, "registerConfig(ModConfig.Type.SERVER, CultivationServerConfig.SPEC)", "server config must be registered through ModContainer")

        math_path = JAVA / "cultivation/time/CultivationTimeMath.java"
        math = self.read(math_path)
        for fragment, message in (
            ("Math.multiplyExact", "time-scale products must use checked multiplication"),
            ("Long.MAX_VALUE - value < increment", "clock additions must saturate rather than wrap"),
            ("OptionalInt mostUrgentWarning", "relative lifespan warning calculation is missing"),
            ("10L", "ten-year warning threshold is missing"),
            ("5L", "five-year warning threshold is missing"),
            ("1L", "one-year warning threshold is missing"),
        ):
            self.require(math_path, math, fragment, message)

        calendar_path = JAVA / "cultivation/time/CultivationCalendarSavedData.java"
        calendar = self.read(calendar_path)
        for fragment, message in (
            ("extends SavedData", "calendar must use SavedData"),
            ('"elapsed_calendar_ticks"', "calendar must persist elapsedCalendarTicks"),
            ("computeIfAbsent", "calendar must use Overworld data storage factory loading"),
            ("incrementSaturated", "calendar increment must saturate"),
            ("setDirty()", "calendar checkpoints must mark SavedData dirty"),
        ):
            self.require(calendar_path, calendar, fragment, message)
        if calendar is not None and any(
            forbidden in calendar
            for forbidden in ("getDayTime(", "getGameTime(", "setDayTime(", "DAYLIGHT_CYCLE")
        ):
            self.error(calendar_path, "cultivation calendar must not derive from vanilla world/day time")

        status_path = JAVA / "cultivation/time/CultivationTimeStatus.java"
        status = self.read(status_path)
        self.require(status_path, status, "zeroBased + 1", "calendar year must be one-based")
        self.require(status_path, status, "dayIndex + 1", "calendar day must be one-based")

    def validate_runtime(self) -> None:
        runtime_path = JAVA / "cultivation/time/CultivationTimeRuntime.java"
        runtime = self.read(runtime_path)
        for fragment, message in (
            ("COMMIT_INTERVAL_TICKS = 600", "lifespan/calendar batching interval must be 600 ticks"),
            ("Map<UUID, PendingLifespan>", "pending personal lifespan must be keyed by UUID"),
            ("anyMatch(CultivationTimeRuntime::isSurvivalOrAdventure)", "calendar must require at least one survival/adventure player"),
            ("isEligibleForPersonalLifespan", "personal lifespan eligibility boundary is missing"),
            ("player.isAlive()", "personal lifespan must pause while dead"),
            ("!player.isRemoved()", "removed players must not accrue lifespan"),
            ("CultivationService.addLifespanConsumedTicks", "batched lifespan must commit through CultivationService"),
            ("retaining", "failed lifespan commits must retain pending ticks for retry"),
            ("saturatingAdd", "effective lifespan must use saturating addition"),
            ("remainingLifespanTicks", "runtime must derive remaining lifespan"),
            ("mostUrgentWarning", "runtime must emit relative lifespan warnings"),
            ("new int[]{10, 5, 1}", "runtime warning de-dup must cover 10/5/1 years"),
            ("maximumLifespanYears()", "maximum lifespan must resolve from current realm data"),
        ):
            self.require(runtime_path, runtime, fragment, message)
        if runtime is not None:
            if "player.setData(" in runtime:
                self.error(runtime_path, "time runtime must not write the attachment directly")
            for forbidden in ("getDayTime(", "getGameTime(", "System.currentTimeMillis", "Instant.now("):
                if forbidden in runtime:
                    self.error(runtime_path, f"effective clocks must not use {forbidden}")
            for forbidden in (".kill(", ".hurt(", "resetProfile(", "withoutSpiritualRoot("):
                if forbidden in runtime:
                    self.error(runtime_path, "lifespan exhaustion must not kill or reset the profile")

        events_path = JAVA / "cultivation/CultivationEvents.java"
        events = self.read(events_path)
        for fragment, message in (
            ("ServerTickEvent.Post", "clocks must advance from one server-post-tick hook"),
            ("CultivationTimeRuntime.tick(event.getServer())", "server tick must drive the time runtime once"),
            ("onPlayerLoggedOut(player)", "logout must force a lifespan flush"),
            ("onPlayerDeath(player)", "death must force a lifespan flush"),
            ("onPlayerChangedDimension(player)", "dimension change must force a lifespan flush"),
            ("LevelEvent.Save", "ordinary server saves must expose a lifespan flush hook"),
            ("onServerSave(level.getServer())", "ordinary server saves must flush pending lifespan"),
            ("onServerStopping(event.getServer())", "clean server stop must flush lifespan and calendar"),
        ):
            self.require(events_path, events, fragment, message)
        self.require(
            runtime_path,
            runtime,
            "public static void onServerSave(MinecraftServer server)",
            "time runtime must own the ordinary-save flush boundary",
        )

    def validate_sync_ui_commands(self) -> None:
        payload_path = JAVA / "cultivation/network/CultivationTimeSnapshotPayload.java"
        payload = self.read(payload_path)
        for fragment, message in (
            ("long elapsedCalendarTicks", "time snapshot must carry shared calendar ticks"),
            ("long lifespanConsumedTicks", "time snapshot must carry effective consumed lifespan"),
            ("long ticksPerDay", "time snapshot must carry active ticks-per-day"),
            ("int daysPerYear", "time snapshot must carry active days-per-year"),
            ("long remainingLifespanTicks", "time snapshot must carry server-derived remaining lifespan"),
            ("boolean exhausted", "time snapshot must carry server-derived exhaustion"),
        ):
            self.require(payload_path, payload, fragment, message)

        payloads_path = JAVA / "cultivation/network/CultivationPayloads.java"
        payloads = self.read(payloads_path)
        self.require(payloads_path, payloads, "playToClient(\n                CultivationTimeSnapshotPayload.TYPE", "time snapshot must register clientbound")

        screen_path = JAVA / "client/cultivation/CultivationProfileScreen.java"
        screen = self.read(screen_path)
        for fragment, message in (
            ("spiritualAffinity()", "H screen must show spiritual affinity"),
            ("calendarValue", "H screen must show cultivation calendar year/day"),
            ("lifespanConsumedTicks()", "H screen must show consumed lifespan"),
            ("remainingLifespanTicks()", "H screen must show remaining lifespan"),
            ("exhausted()", "H screen must show lifespan exhaustion"),
        ):
            self.require(screen_path, screen, fragment, message)
        if screen is not None and (
            "meditationQiReserve" in screen
            or "screen.myvillage.cultivation.reserve" in screen
        ):
            self.error(screen_path, "H screen must not present the inert legacy meditation reserve")

        for locale in ("en_us", "zh_cn"):
            language_path = LANG / f"{locale}.json"
            language = self.load_json(language_path)
            if language is None:
                continue
            for key in (
                "screen.myvillage.cultivation.spiritual_affinity",
                "screen.myvillage.cultivation.calendar",
                "screen.myvillage.cultivation.lifespan_consumed",
                "screen.myvillage.cultivation.lifespan_remaining",
                "screen.myvillage.cultivation.lifespan_exhausted",
            ):
                if not isinstance(language.get(key), str) or not language[key].strip():
                    self.error(language_path, f"missing non-empty bilingual UI key {key}")

        commands_path = JAVA / "cultivation/CultivationCommands.java"
        commands = self.read(commands_path)
        self.require(commands_path, commands, "profile.lifespanConsumedTicks()", "info command must report lifespan consumed")
        self.require(commands_path, commands, "profile.spiritualAffinity()", "info command must report spiritual affinity")

    def validate(self) -> ValidationResult:
        self.validate_profile()
        self.validate_realms()
        self.validate_time_model()
        self.validate_runtime()
        self.validate_sync_ui_commands()
        return ValidationResult(tuple(self.errors), len(self.checked_files))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = CultivationLifespanValidator(args.root.resolve()).validate()
    if result.errors:
        print(f"cultivation lifespan validation failed ({len(result.errors)} error(s)):", file=sys.stderr)
        for error in result.errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(
        "cultivation lifespan validation passed: "
        f"checked_files={result.checked_files}; schema=3; affinity_default=10; realm_years=80/120/240; batch_ticks=600"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
