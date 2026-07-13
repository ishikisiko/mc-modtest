#!/usr/bin/env python3
"""Validate server-authoritative meditation state, input, and interruption wiring."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA = Path("src/main/java/com/example/myvillage")
MEDITATION = JAVA / "cultivation/meditation"
LANG = Path("src/main/resources/assets/myvillage/lang")


@dataclass(frozen=True)
class ValidationResult:
    errors: tuple[str, ...]
    checked_files: int


class CultivationMeditationValidator:
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
        values: dict[Path, str] = {}
        for path in sorted(base.rglob("*.java")):
            try:
                values[path] = path.read_text(encoding="utf-8")
                self.checked_files.add(path)
            except (OSError, UnicodeError) as exc:
                self.error(path, f"cannot read UTF-8 file: {exc}")
        return values

    def require(self, path: Path, text: str | None, fragment: str, message: str) -> None:
        if text is not None and fragment not in text:
            self.error(path, message)

    def validate_state_machine(self) -> None:
        manager_path = MEDITATION / "MeditationManager.java"
        manager = self.read(manager_path)
        state_path = MEDITATION / "MeditationState.java"
        state = self.read(state_path)
        session_path = MEDITATION / "MeditationSession.java"
        session = self.read(session_path)
        mode_path = MEDITATION / "MeditationMode.java"
        mode = self.read(mode_path)
        status_path = MEDITATION / "MeditationStatus.java"
        status = self.read(status_path)
        reason_path = MEDITATION / "MeditationStopReason.java"
        reasons = self.read(reason_path)

        for fragment, message in (
            ("PREPARATION_TICKS = 40", "preparation must require exactly 40 ticks"),
            ("RECENT_DAMAGE_TICKS = 100", "recent-damage exclusion must be 100 ticks"),
            ("MOVEMENT_TOLERANCE = 0.01D", "movement tolerance must be 0.01 block per axis"),
            ("Map<UUID, MeditationSession>", "sessions must be transient and keyed by player UUID"),
            ("requestStart(ServerPlayer player, MeditationMode mode)", "server manager must own start requests"),
            ("requestStop(ServerPlayer player", "server manager must own idempotent stop requests"),
            ("if (removed == null)", "duplicate stop must remain idempotent"),
            ("allowDuplicateFeedback", "duplicate starts must be rate-limited"),
            ("server.getPlayerList().getPlayer(playerId)", "session tick must resolve the authoritative server player"),
            ("recordPositiveDamage", "positive damage must update the recent-damage window"),
            ("onServerStarted", "server start must clear transient sessions"),
            ("onServerStopping", "server stop must clear transient sessions"),
        ):
            self.require(manager_path, manager, fragment, message)

        for enum_value in (
            "IDLE",
            "PREPARING_NORMAL",
            "PREPARING_SPIRIT",
            "MEDITATING_NORMAL",
            "MEDITATING_SPIRIT",
        ):
            self.require(state_path, state, enum_value, f"missing meditation state {enum_value}")
        for enum_value in ("NORMAL", "SPIRIT"):
            self.require(mode_path, mode, enum_value, f"missing meditation mode {enum_value}")
        self.require(session_path, session, "preparationTicks >= MeditationManager.PREPARATION_TICKS", "active transition must occur only after the 40th preparation tick")
        for fragment, message in (
            ("anchorX", "session must capture a server position anchor"),
            ("anchorY", "session must capture a server position anchor"),
            ("anchorZ", "session must capture a server position anchor"),
            ("ResourceKey<Level> dimension", "session must capture its authoritative dimension"),
            ("Math.abs(x - anchorX)", "movement must compare server X against the anchor"),
            ("Math.abs(y - anchorY)", "movement must compare server Y against the anchor"),
            ("Math.abs(z - anchorZ)", "movement must compare server Z against the anchor"),
        ):
            self.require(session_path, session, fragment, message)
        if session is not None and any(value in session for value in ("getYRot", "getXRot", "yRot", "xRot", "yaw", "pitch")):
            self.error(session_path, "camera yaw and pitch must not participate in movement interruption")

        for enum_value in (
            "RECENT_DAMAGE",
            "MOVED",
            "JUMPED",
            "DAMAGED",
            "ATTACKED",
            "MINING",
            "INTERACTED",
            "MOUNTED",
            "SWIMMING",
            "FLYING",
            "SLEEPING",
            "GAME_MODE_CHANGED",
            "DIMENSION_CHANGED",
            "DIED",
            "LOGGED_OUT",
            "SERVER_STOPPING",
        ):
            self.require(reason_path, reasons, enum_value, f"missing stable interruption reason {enum_value}")
        self.require(status_path, status, "preparationTicksRemaining", "client status must expose bounded preparation ticks")

        meditation_sources = self.read_tree(MEDITATION)
        combined = "\n".join(meditation_sources.values())
        for forbidden in ("extends SavedData", "AttachmentType<", "CompoundTag", ".serialize("):
            if forbidden in combined:
                self.error(MEDITATION, "meditation sessions must never be persisted or attached")
        if "player.setData(" in combined:
            self.error(MEDITATION, "meditation code must not write a player attachment directly")

    def validate_eligibility(self) -> None:
        manager_path = MEDITATION / "MeditationManager.java"
        manager = self.read(manager_path)
        for fragment, message in (
            ("!player.isAlive()", "eligibility must reject dead players"),
            ("player.isRemoved()", "eligibility must reject removed players"),
            ("player.gameMode.isSurvival()", "eligibility must require survival/adventure mode"),
            ("profile.spiritualRoot().isEmpty()", "eligibility must require an awakened root"),
            ("BASIC_BREATHING_TECHNIQUE_ID", "eligibility must require learned registered Basic Breathing"),
            ("registry(ModCultivationRegistries.TECHNIQUES)", "eligibility must resolve current technique registry data"),
            ("timeStatus.exhausted()", "eligibility must reject exhausted lifespan"),
            ("player.onGround()", "eligibility must require stable ground support"),
            ("player.isPassenger()", "eligibility must reject mounted players"),
            ("player.isSwimming()", "eligibility must reject swimming"),
            ("player.isFallFlying()", "eligibility must reject fall flight"),
            ("player.getAbilities().flying", "eligibility must reject ability flight"),
            ("player.isSleeping()", "eligibility must reject sleeping"),
            ("player.isUsingItem()", "eligibility must reject item use"),
            ("serverTick - lastDamage < RECENT_DAMAGE_TICKS", "eligibility must enforce the recent-damage window"),
            ("session.dimension().equals(player.level().dimension())", "continuation must revalidate dimension"),
            ("session.moved(player.getX(), player.getY(), player.getZ())", "continuation must revalidate authoritative position"),
            ("player.swinging", "server-observed attack swings must interrupt"),
        ):
            self.require(manager_path, manager, fragment, message)

    def _find_intent_payload(self, network_sources: dict[Path, str]) -> tuple[Path, str] | None:
        candidates = [
            (path, text)
            for path, text in network_sources.items()
            if "CustomPacketPayload" in text
            and re.search(r"record\s+\w*IntentPayload\s*\(", text)
        ]
        if len(candidates) != 1:
            self.error(
                JAVA / "cultivation/network",
                f"expected exactly one bounded meditation intent payload, found {len(candidates)}",
            )
            return None
        return candidates[0]

    def validate_network(self) -> None:
        network_sources = self.read_tree(JAVA / "cultivation/network")
        network_combined = "\n".join(network_sources.values())
        intent = self._find_intent_payload(network_sources)
        if intent is not None:
            path, text = intent
            record = re.search(r"record\s+\w*IntentPayload\s*\(([^)]*)\)", text, re.DOTALL)
            if record is None:
                self.error(path, "meditation intent must be an immutable record")
            else:
                fields = record.group(1).strip()
                if "," in fields or not re.search(r"\b(action|intent)\b", fields, re.IGNORECASE):
                    self.error(path, "meditation intent must contain exactly one action field")
                if not re.fullmatch(
                    r"\s*MeditationIntentAction\s+(?:action|intent)\s*",
                    fields,
                    re.IGNORECASE,
                ):
                    self.error(path, "meditation intent field must be the one bounded action enum")
                for forbidden in (
                    "UUID",
                    "ResourceLocation",
                    "BlockPos",
                    "Vec3",
                    "dimension",
                    "position",
                    "velocity",
                    "duration",
                    "progress",
                    "reserve",
                    "affinity",
                    "inventory",
                    "stone",
                    "cost",
                    "rate",
                    "cap",
                    "elapsed",
                    "ticks",
                    "stability",
                    "mastery",
                    "result",
                    "target",
                ):
                    if forbidden.lower() in fields.lower():
                        self.error(path, f"input-only intent must not carry {forbidden}")
            if not any(
                fragment in text
                for fragment in ("readByte()", "readUnsignedByte()", "ByteBufCodecs.BYTE", "readVarInt()")
            ):
                self.error(path, "intent codec must use a bounded numeric action encoding")
            if not any(
                fragment in text
                for fragment in ("IllegalArgumentException", "DecoderException", "fromCode", "knownAction", "isKnown")
            ):
                self.error(path, "intent codec must reject unknown action values")

        action_path = JAVA / "cultivation/network/MeditationIntentAction.java"
        action_source = self.read(action_path)
        expected_actions = {"START_NORMAL", "START_SPIRIT", "STOP", "START_BREAKTHROUGH"}
        actual_actions: set[str] = set()
        if action_source is not None:
            enum_match = re.search(
                r"enum\s+MeditationIntentAction\s*\{(?P<body>.*?)\}",
                action_source,
                re.DOTALL,
            )
            if enum_match is not None:
                actual_actions = set(re.findall(r"\b[A-Z][A-Z_]+\b", enum_match.group("body")))
        if actual_actions != expected_actions:
            self.error(
                action_path,
                f"bounded intent actions must be exactly {sorted(expected_actions)}, got {sorted(actual_actions)}",
            )
        for fragment, message in (
            ("playToServer", "meditation intent must register serverbound"),
            ("playToClient", "meditation runtime status must register clientbound"),
            ("context.player() instanceof ServerPlayer", "intent handler must derive identity from its sender context"),
            ("context.enqueueWork", "intent handling must execute on the logical server thread"),
            ("MeditationManager", "intent handler must delegate to the server-owned manager"),
            ("installStatusListener", "transition status must be sent through the manager listener"),
        ):
            if fragment not in network_combined:
                self.error(JAVA / "cultivation/network", message)
        if "player.setData(" in network_combined:
            self.error(JAVA / "cultivation/network", "intent handler must not write profile data directly")

    def validate_interruptions(self) -> None:
        events_path = JAVA / "cultivation/CultivationEvents.java"
        events = self.read(events_path)
        for fragment, message in (
            ("LivingEvent.LivingJumpEvent", "jump interruption hook is missing"),
            ("LivingDamageEvent.Post", "positive post-damage interruption hook is missing"),
            ("event.getNewDamage() > 0", "damage hook must require successful positive damage"),
            ("AttackEntityEvent", "active attack interruption hook is missing"),
            ("PlayerInteractEvent.LeftClickBlock", "mining-start interruption hook is missing"),
            ("LeftClickBlock.Action.START", "mining interruption must trigger at START"),
            ("PlayerInteractEvent.RightClickBlock", "block-use interruption hook is missing"),
            ("PlayerInteractEvent.RightClickItem", "item-use interruption hook is missing"),
            ("PlayerInteractEvent.EntityInteract", "entity interaction interruption hook is missing"),
            ("PlayerInteractEvent.EntityInteractSpecific", "specific entity interaction interruption hook is missing"),
            ("LivingEntityUseItemEvent.Start", "living item-use interruption hook is missing"),
            ("EntityMountEvent", "mount interruption hook is missing"),
            ("PlayerChangeGameModeEvent", "game-mode interruption hook is missing"),
            ("PlayerChangedDimensionEvent", "dimension-change interruption hook is missing"),
            ("LivingDeathEvent", "death interruption hook is missing"),
            ("PlayerLoggedOutEvent", "logout interruption hook is missing"),
            ("ServerStoppingEvent", "server-stop cleanup hook is missing"),
        ):
            self.require(events_path, events, fragment, message)

    def validate_client(self) -> None:
        client_sources = self.read_tree(JAVA / "client/cultivation")
        combined = "\n".join(client_sources.values())
        for key, action in (
            ("GLFW_KEY_V", "START_NORMAL"),
            ("GLFW_KEY_B", "START_SPIRIT"),
            ("GLFW_KEY_G", "STOP"),
        ):
            if key not in combined:
                self.error(JAVA / "client/cultivation", f"missing configurable meditation key {key}")
            if action not in combined:
                self.error(JAVA / "client/cultivation", f"client key path omits {action} intent")
        if "PacketDistributor.sendToServer" not in combined:
            self.error(JAVA / "client/cultivation", "client keys must send only the bounded intent")
        client_events_path = self.root / JAVA / "client/cultivation/ClientCultivationEvents.java"
        client_events = client_sources.get(client_events_path, "")
        logout_handler = re.search(
            r"onLoggingOut\s*\([^)]*\)\s*\{(?P<body>[^}]*)\}",
            client_events,
            re.DOTALL,
        )
        if (
            "ClientPlayerNetworkEvent.LoggingOut" not in client_events
            or logout_handler is None
            or not re.search(
                r"ClientCultivationState\s*\.\s*(?:clear|reset)\s*\(",
                logout_handler.group("body"),
                re.IGNORECASE,
            )
        ):
            self.error(JAVA / "client/cultivation", "client meditation status must clear on disconnect")

        screen_path = JAVA / "client/cultivation/CultivationProfileScreen.java"
        screen = client_sources.get(self.root / screen_path)
        if screen is not None:
            for view in ("PROFILE", "MEDITATION"):
                if view not in screen:
                    self.error(screen_path, f"H screen must expose the {view.title()} tab")
            for action in ("START_NORMAL", "START_SPIRIT", "STOP", "START_BREAKTHROUGH"):
                occurrences = len(re.findall(rf"\b{action}\b", screen))
                if occurrences != 1:
                    self.error(
                        screen_path,
                        f"H meditation controls must bind exactly one button to {action}, got {occurrences}",
                    )
            if not re.search(
                r"(?:ClientCultivationIntentSender\.send|PacketDistributor\.sendToServer)\s*\(",
                screen,
            ):
                self.error(screen_path, "H action buttons must reuse the bounded intent sender")
            if "meditationQiReserve" in screen or "screen.myvillage.cultivation.reserve" in screen:
                self.error(screen_path, "H screen must not present the inert legacy meditation reserve")
            if re.search(r"\b(?:player\s*\.\s*)?setData\s*\(", screen):
                self.error(screen_path, "H screen must not write profile data directly")
            for marker, message in (
                (
                    "screen.myvillage.cultivation.no_snapshot",
                    "H screen must render a missing-profile state",
                ),
                (
                    "screen.myvillage.cultivation.time_waiting",
                    "H screen must render a missing-time/status state",
                ),
                (
                    "screen.myvillage.cultivation.unavailable",
                    "H screen must render unresolved definition values as unavailable",
                ),
                ("normalButton.active", "H screen must expose advisory normal-button state"),
                ("spiritButton.active", "H screen must expose advisory spirit-button state"),
                ("stopButton.active", "H screen must expose advisory stop-button state"),
                (
                    "advancementButton.active",
                    "H screen must expose advisory advancement-button state",
                ),
            ):
                if marker not in screen:
                    self.error(screen_path, message)
            set_view = re.search(
                r"\bsetView\s*\([^)]*\)\s*\{(?P<body>[^}]*)\}",
                screen,
                re.DOTALL,
            )
            if set_view is not None and re.search(
                r"(?:sendToServer|IntentSender\.send|new\s+MeditationIntentPayload)",
                set_view.group("body"),
            ):
                self.error(screen_path, "switching H tabs must not send a meditation intent")

        required_keys = (
            "screen.myvillage.cultivation.tab.profile",
            "screen.myvillage.cultivation.tab.meditation",
            "screen.myvillage.cultivation.button.normal",
            "screen.myvillage.cultivation.button.spirit",
            "screen.myvillage.cultivation.button.stop",
            "screen.myvillage.cultivation.button.advancement",
        )
        for locale in ("en_us", "zh_cn"):
            language_path = self.root / LANG / f"{locale}.json"
            if not language_path.is_file():
                self.error(LANG / f"{locale}.json", "missing language file")
                continue
            self.checked_files.add(language_path)
            try:
                language = json.loads(language_path.read_text(encoding="utf-8"))
            except (OSError, UnicodeError, json.JSONDecodeError) as exc:
                self.error(language_path, f"invalid language JSON: {exc}")
                continue
            for key in required_keys:
                if not isinstance(language.get(key), str) or not language[key].strip():
                    self.error(language_path, f"missing non-empty meditation UI key {key}")

    def validate(self) -> ValidationResult:
        self.validate_state_machine()
        self.validate_eligibility()
        self.validate_network()
        self.validate_interruptions()
        self.validate_client()
        return ValidationResult(tuple(self.errors), len(self.checked_files))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = CultivationMeditationValidator(args.root.resolve()).validate()
    if result.errors:
        print(f"cultivation meditation validation failed ({len(result.errors)} error(s)):", file=sys.stderr)
        for error in result.errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(
        "cultivation meditation validation passed: "
        f"checked_files={result.checked_files}; preparation=40; damage_window=100; "
        "input=one-of-4-actions; h_tabs=2"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
