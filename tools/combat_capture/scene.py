"""Scene setup over rcon. The command builders are pure (unit-tested); the
apply functions run them against a Session.

World: the capture session's fresh superflat (grass top at y=-61, stand at
y=-60). The player stands at (0.5, -60, 0.5) facing south (+Z, yaw 0).
"""
from __future__ import annotations

import re
import time

FLOOR_Y = -60
PLAYER_POS = (0.5, FLOOR_Y, 0.5)
# Barrier walls pull the vanilla F5 camera in (it clips against blocks; a
# barrier is a full block that renders invisible). Rows at z=-4 and z=4.
BACK_WALL_Z, FRONT_WALL_Z = -4, 4
WALL_X = (-4, 5)
WALL_H = 7

# (tag, dx, dz) from the player, who faces south (+z); +x is the player's left.
TARGET_LAYOUTS = {
    # t1 straight ahead; t2/t3 well to the sides (5 blocks out: only t1 is in reach of any move)
    "default": [("t1", 0.0, 2.5), ("t2", -3.0, 4.0), ("t3", 3.0, 4.0)],
    # an arc 2.5 blocks out: ahead, 45 degrees left, 45 degrees right (inside a wide sweep)
    "sweep": [("t1", 0.0, 2.5), ("t2", 1.768, 1.768), ("t3", -1.768, 1.768)],
    # three in a line straight ahead, a block apart (a piercing thrust)
    "line": [("t1", 0.0, 2.5), ("t2", 0.0, 3.5), ("t3", 0.0, 4.5)],
}
TARGET_LAYOUT = TARGET_LAYOUTS["default"]
TARGET_TAG = "capture_target"


class SceneError(RuntimeError):
    pass


def world_rules() -> list[str]:
    return ["gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
            "gamerule doPatrolSpawning false", "gamerule doTraderSpawning false", "gamerule doInsomnia false",
            "gamerule announceAdvancements false", "gamerule sendCommandFeedback false",
            "gamerule commandBlockOutput false", "gamerule keepInventory true", "gamerule doImmediateRespawn true",
            "gamerule spawnRadius 0", "time set 6000", "weather clear 1000000", "tick rate 20"]


def wall_fill(z: int, block: str) -> str:
    x0, x1 = WALL_X
    return f"fill {x0} {FLOOR_Y} {z} {x1} {FLOOR_Y + WALL_H - 1} {z} {block}"


def wall_commands(wall: str | None) -> list[str]:
    """Barrier row behind ('back') or in front ('front') of the player, the
    other row air; None clears both (vanilla F5 distance)."""
    if wall not in (None, "back", "front"):
        raise SceneError(f"unknown wall {wall!r}")
    return [wall_fill(BACK_WALL_Z, "minecraft:barrier" if wall == "back" else "minecraft:air"),
            wall_fill(FRONT_WALL_Z, "minecraft:barrier" if wall == "front" else "minecraft:air")]


def player_setup(user: str, item: str) -> list[str]:
    px, py, pz = PLAYER_POS
    return [f"op {user}", f"gamemode survival {user}",
            "kill @e[type=!minecraft:player]", f"clear {user}",
            f"effect clear {user}",
            # killed targets drop experience; a filling XP bar would differ between captures
            f"experience set {user} 0 levels", f"experience set {user} 0 points",
            f"effect give {user} minecraft:saturation infinite 255 true",
            # No regeneration: its effect makes the heart row bounce, so two grabs would rarely match.
            f"effect give {user} minecraft:instant_health 1 10 true",
            wall_fill(BACK_WALL_Z, "minecraft:air"), wall_fill(FRONT_WALL_Z, "minecraft:air"),
            f"tp {user} {px} {py} {pz} 0 0",
            f"item replace entity {user} weapon.mainhand with {item}"]


def cleanup_drops(user: str, item: str) -> list[str]:
    """Killed mobs drop loot a moment later; pickups pop toasts on screen."""
    return ["kill @e[type=minecraft:item]", "kill @e[type=minecraft:experience_orb]", f"clear {user}",
            f"item replace entity {user} weapon.mainhand with {item}"]


def layout(name: str) -> list:
    if name not in TARGET_LAYOUTS:
        raise SceneError(f"unknown target layout {name!r} ({', '.join(TARGET_LAYOUTS)})")
    return TARGET_LAYOUTS[name]


def target_summons(kind: str, layout_name: str = "default") -> list[str]:
    """Targets in front of the player. 'dummy': a husk with AI on (so knockback
    and hitstun can move it) that cannot walk or acquire a target, 80 HP.
    'golem': a NoAI iron golem (100 HP, knockback resistant)."""
    px, py, pz = PLAYER_POS
    cmds = []
    for tag, dx, dz in layout(layout_name):
        x, z = px + dx, pz + dz
        if kind == "dummy":
            cmds.append(
                f"summon minecraft:husk {x} {py} {z} "
                f"{{PersistenceRequired:1b,Silent:1b,CanPickUpLoot:0b,Health:80f,Rotation:[180f,0f],"
                f"Tags:[\"{TARGET_TAG}\",\"capture_{tag}\"],attributes:["
                f"{{id:\"minecraft:generic.max_health\",base:80d}},"
                f"{{id:\"minecraft:generic.movement_speed\",base:0d}},"
                f"{{id:\"minecraft:generic.follow_range\",base:0d}},"
                f"{{id:\"minecraft:generic.knockback_resistance\",base:0d}}]}}")
        elif kind == "golem":
            cmds.append(f"summon minecraft:iron_golem {x} {py} {z} "
                        f"{{NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],"
                        f"Tags:[\"{TARGET_TAG}\",\"capture_{tag}\"]}}")
        else:
            raise SceneError(f"unknown target kind {kind!r} (dummy or golem)")
    return cmds


INT_ARRAY = re.compile(r"\[I;\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\]")
FLOAT_DATA = re.compile(r"data:\s*(-?[\d.]+(?:[eE]-?\d+)?)[fd]?\s*$")


def ints_to_uuid(ints) -> str:
    raw = b"".join((int(i) & 0xFFFFFFFF).to_bytes(4, "big") for i in ints)
    h = raw.hex()
    return f"{h[0:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:32]}"


def parse_float_data(reply: str):
    m = FLOAT_DATA.search(reply.strip())
    return float(m.group(1)) if m else None


def parse_combat_mode(reply: str) -> str:
    """Combat mode from `data get entity <p> "neoforge:attachments"`. The
    preference attachment is only written once set; absent means vanilla."""
    m = re.search(r'combat_mode:\s*"(\w+)"', reply)
    return m.group(1) if m else "vanilla"


# ---------------------------------------------------------------- apply
def check_item(session, item: str) -> str:
    hand = session.run(f"data get entity {session.username} SelectedItem.id", warn=False)[0]
    if item not in hand:
        raise SceneError(f"{item} is not in the main hand: {hand.strip()}")
    return hand.strip()


def base_scene(session, item: str):
    user = session.username
    if user not in session.run("list", warn=False)[0]:
        raise SceneError(f"{user} is not online")
    session.run(*world_rules())
    session.run(*player_setup(user, item))
    time.sleep(1.5)  # chunks around the fixed position; loot of killed mobs
    session.run(*cleanup_drops(user, item), warn=False)
    time.sleep(0.8)
    return check_item(session, item)


def combat_mode(session) -> str:
    reply = session.run(f'data get entity {session.username} "neoforge:attachments"', warn=False)[0]
    return parse_combat_mode(reply)


def ensure_cultivation(session, timeout: float = 8.0) -> str:
    """Confirm cultivation combat mode through the player's combat preference;
    press the toggle key (R) once if it is not, and verify the server agrees."""
    mode = combat_mode(session)
    if mode == "cultivation":
        session.log("combat mode: cultivation (confirmed through combat_preference)")
        return "already"
    session.game.key("r")
    deadline = time.time() + timeout
    while time.time() < deadline:
        time.sleep(0.4)
        if combat_mode(session) == "cultivation":
            session.log("combat mode: vanilla -> cultivation (key R, confirmed through combat_preference)")
            time.sleep(3.5)  # the action-bar notice fades before any frame is taken
            return "toggled"
    raise SceneError("pressed R but the server still reports combat_mode != cultivation")


def place_targets(session, kind: str, layout_name: str = "default") -> list[dict]:
    session.run(*target_summons(kind, layout_name))
    time.sleep(0.6)
    targets = []
    px, py, pz = PLAYER_POS
    for tag, dx, dz in layout(layout_name):
        out = session.run(f"data get entity @e[tag=capture_{tag},limit=1] UUID", warn=False)[0]
        m = INT_ARRAY.search(out)
        if not m:
            raise SceneError(f"could not read the UUID of target {tag}: {out.strip()[:200]}")
        uuid = ints_to_uuid(m.groups())
        targets.append({"tag": tag, "uuid": uuid, "kind": kind, "pos": [px + dx, py, pz + dz],
                        "health": read_health(session, uuid)})
    return targets


def read_health(session, uuid: str):
    return parse_float_data(session.run(f"data get entity {uuid} Health", warn=False)[0])


def player_pos(session) -> str:
    return session.run(f"data get entity {session.username} Pos", warn=False)[0].strip()
