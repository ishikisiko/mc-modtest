"""In-game evidence for a beast (default myvillage:demon_wolf) in the capture
session: idle beauty stills, a scale still beside the player, exact-tick move
stills (plus landing stills for a leaping move), walk/run footage, a recorded
fight against the Qingfeng sword with the server's stagger evidence, slowed
dodge/interrupt trials and slowed move footage. Re-runnable with one command:

    python3 -m tools.combat_capture beast            # starts and stops its own session

Stills are taken with the world frozen (/tick freeze, /tick step) from a
spectator camera teleported relative to the beast's current position, HUD
hidden (F1). The beast's client clips show the server's move tick exactly while
frozen (BeastEntity.clipTime), so a still at move tick k is the pose of tick k.
Output: out/preview/<beast>/ingame/ (index.html, manifest.json, stills/,
video/, fight_log.txt).
"""
from __future__ import annotations

import html
import json
import math
import re
import subprocess
import threading
import time
from pathlib import Path

from . import scene
from .capture import ffmpeg_record_argv, press_f5_to
from .session import LOG_DIR, REPO, Session

RESOURCES = REPO / "src/main/resources"
EYE_HEIGHT = 1.62
WOLF_POS = (0.5, scene.FLOOR_Y, 8.5)
TAG = "capture_beast"
SWORD = "myvillage:qingfeng_sword"
FIGHT_PITCH = 35.0

# view -> (forward, left) unit offset of the camera from the beast, in the beast's frame
VIEW_OFFSETS = {
    "front": (1.0, 0.0),
    "side": (0.0, 1.0),
    "q_front": (math.sqrt(0.5), math.sqrt(0.5)),
    "q_back": (-math.sqrt(0.5), math.sqrt(0.5)),
}
VIEW_TITLES = {"front": "front", "side": "left side", "q_front": "three-quarter front",
               "q_back": "three-quarter back"}
STATUS_FIELD = re.compile(r"(\w+)=(\([^)]*\)|\[[^\]]*\]|\S+)")
NUM = r"-?[\d.]+(?:[eE]-?\d+)?"
POS_REPLY = re.compile(rf"\[({NUM})d, ({NUM})d, ({NUM})d\]")
FLOAT_REPLY = re.compile(r"data: (-?[\d.]+)f")


# ------------------------------------------------------------------ pure helpers
def beast_data(beast: str) -> dict:
    ns, path = beast.split(":", 1)
    return json.loads((RESOURCES / f"data/{ns}/beast/{path}.json").read_text(encoding="utf-8"))


def key_ticks(move: dict) -> list[tuple[str, int]]:
    """The exact-tick stills of one move: start, aim lock, lunge, first and last
    active tick, mid recovery."""
    first, last = move["active_ticks"]
    recovery = (last + 1 + move["total_ticks"] - 1) // 2
    keys = [("start", 0), ("turn_lock", move["turn_lock_tick"]), ("lunge", move["lunge"]["tick"]),
            ("active_first", first), ("active_last", last), ("recovery_mid", recovery)]
    return keys


def camera_pose(target, beast_yaw: float, view: str, distance: float, eye_up: float, aim_up: float):
    """Spectator feet position and rotation that put the eye `distance` blocks
    from the beast on `view`'s side, `eye_up` above its feet, looking at the
    point `aim_up` above its feet. Returns (x, y, z, yaw, pitch)."""
    fwd_k, left_k = VIEW_OFFSETS[view]
    yaw = math.radians(beast_yaw)
    forward = (-math.sin(yaw), math.cos(yaw))
    left = (math.cos(yaw), math.sin(yaw))
    tx, ty, tz = target
    ex = tx + (forward[0] * fwd_k + left[0] * left_k) * distance
    ez = tz + (forward[1] * fwd_k + left[1] * left_k) * distance
    ey = ty + eye_up
    dx, dz, dy = tx - ex, tz - ez, (ty + aim_up) - ey
    look_yaw = math.degrees(math.atan2(dz, dx)) - 90.0
    pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    return ex, ey - EYE_HEIGHT, ez, look_yaw, pitch


def parse_status(line: str) -> dict:
    return dict(STATUS_FIELD.findall(line))


def parse_pos(reply: str):
    m = POS_REPLY.search(reply)
    return tuple(float(v) for v in m.groups()) if m else None


def parse_float(reply: str):
    m = FLOAT_REPLY.search(reply)
    return float(m.group(1)) if m else None


def summon(beast: str, pos, yaw: float, tag: str = TAG, extra: str = "") -> str:
    x, y, z = pos
    return (f"summon {beast} {x:g} {y:g} {z:g} {{Rotation:[{yaw:g}f,0f],PersistenceRequired:1b,"
            f"Tags:[\"{tag}\"]{extra}}}")


# ------------------------------------------------------------------ session steps
class BeastCapture:
    def __init__(self, session: Session, beast: str, out: Path, log=print):
        self.s = session
        self.g = session.game
        self.beast = beast
        self.data = beast_data(beast)
        self.out = out
        self.log = log
        self.user = session.username
        self.manifest = {"beast": beast, "stills": [], "videos": [], "notes": []}
        self.hud_hidden = False

    def run(self, *cmds, warn=True):
        return self.s.run(*cmds, warn=warn)

    def sel(self, tag: str = TAG) -> str:
        return f"@e[tag={tag},limit=1]"

    def hide_hud(self, hidden: bool):
        if self.hud_hidden != hidden:
            self.g.key("F1")
            self.hud_hidden = hidden
            time.sleep(0.4)

    def setup_world(self):
        self.s.view = "first"  # the client joins in first person
        self.run(*scene.world_rules(), "difficulty normal", "tick unfreeze", "tick rate 20",
                 "gamerule doMobLoot false", "myvillage beast debug on")
        self.run(f"op {self.user}", "kill @e[type=!minecraft:player]", f"clear {self.user}",
                 f"effect clear {self.user}", f"experience set {self.user} 0 levels",
                 f"effect give {self.user} minecraft:saturation infinite 255 true",
                 scene.wall_fill(scene.BACK_WALL_Z, "minecraft:air"), scene.wall_fill(scene.FRONT_WALL_Z, "minecraft:air"),
                 "forceload add -48 -48 48 48")
        time.sleep(1.0)

    def remove(self, tag: str):
        """A mob killed while the world is frozen lies in its death pose with its
        death particles in view, so move it far below the floor and retag it;
        `bury` kills those once the world runs again."""
        self.run(f"tag @e[tag={tag}] add capture_gone", f"tag @e[tag={tag}] remove {tag}",
                 "tp @e[tag=capture_gone] 0.5 -200 0.5", warn=False)

    def bury(self):
        self.run("kill @e[tag=capture_gone]", warn=False)

    def pos(self, tag: str | None = TAG):
        """Position of the tagged entity, or of the player when `tag` is None."""
        who = self.user if tag is None else self.sel(tag)
        return parse_pos(self.run(f"data get entity {who} Pos", warn=False)[0])

    def status(self, tag: str = TAG) -> dict:
        return parse_status(self.run(f"myvillage beast status {self.sel(tag)}", warn=False)[0])

    def spectate(self, target, yaw, view, distance=3.2, eye_up=1.1, aim_up=0.75):
        x, y, z, look_yaw, pitch = camera_pose(target, yaw, view, distance, eye_up, aim_up)
        self.run(f"tp {self.user} {x:.3f} {y:.3f} {z:.3f} {look_yaw:.2f} {pitch:.2f}")
        time.sleep(0.5)

    def still(self, name: str, title: str, **info):
        path = self.out / "stills" / f"{name}.png"
        shot = self.g.shot(path, max_wait=6.0)
        self.manifest["stills"].append({"file": f"stills/{name}.png", "title": title, "shot": shot, **info})
        self.log(f"  still {name}: {shot}")

    def fresh_beast(self, pos=WOLF_POS, yaw=0.0):
        self.remove(TAG)
        self.run("kill @e[type=minecraft:item]", "kill @e[type=minecraft:experience_orb]")
        self.run(summon(self.beast, pos, yaw))
        self.run("tick step 3")
        time.sleep(0.8)

    # -------------------------------------------------------------- parts
    def stills_idle(self):
        self.log("idle beauty stills")
        self.run("tick freeze", f"gamemode spectator {self.user}")
        press_f5_to(self.s, "first")
        self.fresh_beast()
        self.hide_hud(True)
        target = self.pos()
        for view in ("front", "side", "q_front", "q_back"):
            self.spectate(target, 0.0, view)
            self.still(f"idle_{view}", f"idle, {VIEW_TITLES[view]}", view=view, pos=target)
        # Scale: survival player beside the beast, F5 front camera looking back at both.
        self.hide_hud(False)
        px, py, pz = WOLF_POS[0], WOLF_POS[1], WOLF_POS[2] - 2.6
        self.run(f"gamemode survival {self.user}", f"tp {self.user} {px:g} {py:g} {pz:g} -90 8",
                 f"item replace entity {self.user} weapon.mainhand with {SWORD}")
        self.fresh_beast(yaw=180.0)
        time.sleep(0.6)
        press_f5_to(self.s, "front")
        self.hide_hud(True)
        self.still("scale_player", "beside the player for scale (F5 front, 4 blocks)", pos=self.pos())
        press_f5_to(self.s, "first")
        self.hide_hud(False)

    def stills_moves(self):
        self.run("tick freeze", f"gamemode spectator {self.user}")
        press_f5_to(self.s, "first")
        for move in self.data["moves"]:
            name = move["id"].split(":", 1)[1]
            self.log(f"move stills: {move['id']}")
            self.fresh_beast()
            self.hide_hud(True)
            reply = self.run(f"myvillage beast move {self.sel()} {move['id']}")[0]
            self.manifest["notes"].append(f"{move['id']}: {reply.strip()}")
            current = -1
            for key, tick in key_ticks(move):
                if tick > current:
                    self.run(f"tick step {tick - current}")
                    time.sleep(0.6 + 0.05 * (tick - current))
                    current = tick
                st = self.status()
                if st.get("tick") != str(tick):
                    raise RuntimeError(f"{move['id']}: expected move tick {tick}, status says {st}")
                self.move_stills(move, name, key, tick, st)
            if move["lunge"]["up"] > 0.0:
                self.landing_stills(move, name)
            self.hide_hud(False)

    def move_stills(self, move: dict, name: str, key: str, tick: int, st: dict):
        target = self.pos()
        for view in ("side", "q_front"):
            self.spectate(target, 0.0, view, distance=3.6)
            self.still(f"{name}_{tick:02d}_{key}_{view}", f"{move['id']} tick {tick} ({key}), "
                       f"{VIEW_TITLES[view]}", move=move["id"], tick=tick, key=key, view=view,
                       pos=target, status=st)

    def landing_stills(self, move: dict, name: str):
        """A leaping move (lunge up > 0) again on a fresh beast: step one tick at
        a time from the tick after the lunge until the server reports the beast
        on the ground, and take the landing stills there and two ticks later."""
        self.fresh_beast()
        self.run(f"myvillage beast move {self.sel()} {move['id']}")
        current = -1
        landing = None
        for tick in range(move["lunge"]["tick"] + 1, move["total_ticks"] - 2):
            self.run(f"tick step {tick - current}")
            time.sleep(0.6 + 0.05 * (tick - current))
            current = tick
            st = self.status()
            if st.get("tick") != str(tick):
                raise RuntimeError(f"{move['id']}: expected move tick {tick}, status says {st}")
            if st.get("ground") == "true":
                landing = tick
                self.move_stills(move, name, "landing", tick, st)
                break
        if landing is None:
            self.manifest["notes"].append(f"{move['id']}: never on the ground before tick {move['total_ticks'] - 2}")
            self.log(f"  {move['id']}: no landing tick found")
            return
        self.manifest["notes"].append(f"{move['id']}: first tick on the ground after the lunge: {landing}")
        self.run("tick step 2")
        time.sleep(0.7)
        st = self.status()
        if st.get("tick") != str(landing + 2):
            raise RuntimeError(f"{move['id']}: expected move tick {landing + 2}, status says {st}")
        self.move_stills(move, name, "landing_plus2", landing + 2, st)

    def slowmo(self, rate: int = 5):
        """Each move at /tick rate `rate` (client and server slow down
        together, so each tick lasts 20/rate frames' worth of time), filmed
        from a low side camera so ground contact is readable. The server's
        move tick, ground flag and height are polled through the recording
        and kept in the manifest against seconds since the recording began."""
        self.log(f"slowed move footage (/tick rate {rate})")
        self.remove(TAG)
        self.run("tick rate 20", "tick unfreeze", f"gamemode spectator {self.user}")
        self.bury()
        press_f5_to(self.s, "first")
        self.hide_hud(True)
        timelines = {}
        try:
            for move in self.data["moves"]:
                name = short_id(move["id"])
                # The world stays frozen until the move is forced, so the beast cannot look around
                # (and turn) before it; the camera follows its actual start and yaw.
                self.run("tick freeze")
                self.fresh_beast()
                start = self.pos() or WOLF_POS
                try:
                    beast_yaw = float(self.status().get("yaw", "0"))
                except ValueError:
                    beast_yaw = 0.0
                # Centre the frame between the start and a quarter of the move's range sum ahead.
                ahead = sum(move["use_range"]) / 4.0
                rad = math.radians(beast_yaw)
                centre = (start[0] - math.sin(rad) * ahead, start[1], start[2] + math.cos(rad) * ahead)
                x, cy, z, yaw, pitch = camera_pose(centre, beast_yaw, "side", 5.0, 0.7, 0.5)
                self.run(f"tp {self.user} {x:.3f} {cy:.3f} {z:.3f} {yaw:.2f} {pitch:.2f}")
                time.sleep(0.8)
                seconds = 2.0 + move["total_ticks"] / rate + 2.0
                timeline = []

                def act(total, move=move, timeline=timeline):
                    t0 = time.time()
                    time.sleep(1.0)
                    self.run(f"tick rate {rate}", f"myvillage beast move {self.sel()} {move['id']}", "tick unfreeze")
                    seen = False
                    while time.time() - t0 < total - 0.5:
                        st = self.status()
                        if st.get("move", "none") != "none":
                            seen = True
                            pos = [float(v) for v in re.findall(NUM, st.get("pos", ""))]
                            timeline.append({"s": round(time.time() - t0, 3), "tick": int(st.get("tick", "-1")),
                                             "ground": st.get("ground") == "true",
                                             "pos": pos if len(pos) == 3 else None, "yaw": st.get("yaw")})
                        elif seen:
                            break
                        time.sleep(0.03)
                    self.run("tick rate 20")

                self.run("tick rate 20")
                self.record(f"slow_{name}", seconds, f"{move['id']} at /tick rate {rate}, low side camera "
                            f"(each tick lasts {20 // rate} normal ticks)", act)
                timelines[name] = timeline
        finally:
            self.run("tick rate 20", warn=False)
            self.hide_hud(False)
        self.manifest["slowmo"] = {"rate": rate, "timelines": timelines}

    def record(self, name: str, seconds: float, title: str, action=None):
        video = self.out / "video" / f"{name}.mp4"
        video.parent.mkdir(parents=True, exist_ok=True)
        rec = subprocess.Popen(ffmpeg_record_argv(self.s.state["display"], video, seconds), stdin=subprocess.DEVNULL)
        try:
            if action:
                action(seconds)
            rc = rec.wait(timeout=seconds + 60)
        except BaseException:
            rec.terminate()
            rec.wait(timeout=10)
            raise
        if rc != 0:
            raise RuntimeError(f"ffmpeg failed for {name} (rc {rc})")
        self.manifest["videos"].append({"file": f"video/{name}.mp4", "title": title, "seconds": seconds})
        self.log(f"  video {name} ({seconds:g}s)")

    def locomotion(self):
        """Walk: two beasts stroll in a short barrier pen, filmed from 6.5 blocks.
        Run: two beasts chase a NoAI husk along a corridor; the husk jumps to the
        far end whenever the nearest beast is within 9 blocks, so they keep
        running (and stay out of pounce range)."""
        self.log("walk / run footage")
        y = scene.FLOOR_Y
        self.remove(TAG)
        self.run("tick unfreeze", f"gamemode spectator {self.user}")
        self.bury()
        press_f5_to(self.s, "first")
        self.hide_hud(True)
        # Walk pen: inside x -4..5, z 7..9.
        self.run(f"fill -5 {y} 6 6 {y + 1} 10 minecraft:barrier", f"fill -4 {y} 7 5 {y + 1} 9 minecraft:air")
        for i, x in enumerate((-2.5, 3.5)):
            self.run(summon(self.beast, (x, y, 8.5), 90.0 if i else -90.0, tag=f"{TAG}_{i}"))
        x, cy, z, yaw, pitch = camera_pose((0.5, y, 8.5), 0.0, "front", 6.5, 1.3, 0.6)
        self.run(f"tp {self.user} {x:.3f} {cy:.3f} {z:.3f} {yaw:.2f} {pitch:.2f}")
        time.sleep(1.0)
        self.record("walk", 45.0, "walk: two beasts strolling in a barrier pen (camera 6.5 blocks out)")
        self.run(f"fill -5 {y} 6 6 {y + 1} 10 minecraft:air")
        # Run corridor: inside x -13..14, z 7..9.
        self.run(f"fill -14 {y} 6 15 {y + 1} 10 minecraft:barrier", f"fill -13 {y} 7 14 {y + 1} 9 minecraft:air")
        ends = (-12.5, 13.5)
        self.run(f"summon minecraft:husk {ends[0]:g} {y} 8.5 {{NoAI:1b,Silent:1b,PersistenceRequired:1b,"
                 f"Tags:[\"capture_lure\"]}}",
                 "effect give @e[tag=capture_lure] minecraft:resistance infinite 255 true")
        time.sleep(0.3)
        for i in range(2):
            self.run(f"damage @e[tag={TAG}_{i},limit=1] 0.5 minecraft:mob_attack by @e[tag=capture_lure,limit=1]")
        x, cy, z, yaw, pitch = camera_pose((0.5, y, 8.5), 0.0, "front", 10.0, 1.4, 0.6)
        self.run(f"tp {self.user} {x:.3f} {cy:.3f} {z:.3f} {yaw:.2f} {pitch:.2f}")
        time.sleep(0.5)

        def lure(seconds):
            end_time = time.time() + seconds
            side = 0
            while time.time() < end_time:
                nearest = min((abs(p[0] - ends[side]) for p in (self.pos(f"{TAG}_{i}") for i in range(2)) if p),
                              default=99.0)
                if nearest < 9.0:
                    side = 1 - side
                    self.run(f"tp @e[tag=capture_lure,limit=1] {ends[side]:g} {y} 8.5")
                time.sleep(0.1)

        self.record("run", 30.0, "run: two beasts chasing a lure that jumps between the corridor ends "
                    "(camera 10 blocks out)", lure)
        self.run("kill @e[tag=capture_lure]", *(f"kill @e[tag={TAG}_{i}]" for i in range(2)),
                 f"fill -14 {y} 6 15 {y + 1} 10 minecraft:air")
        self.hide_hud(False)

    def fight(self, seconds: float = 60.0):
        """Survival player (no armour, no Resistance, natural regeneration off so
        every bite stays on the health bar) with the Qingfeng sword in
        cultivation combat mode stands still; one beast at a time chases, bites
        and pounces; the player swings whenever the beast is within reach. When
        a beast dies the player is healed and the next one is summoned, so each
        beast is one round; a player who dies respawns, is put back and the
        round goes on (counted as a death). A second rcon connection polls the
        beast's status and the player's health; the server's BEAST_DEBUG lines
        give hits both ways, staggers and moves (see fight_rounds)."""
        self.log("fight")
        y = scene.FLOOR_Y
        self.remove(TAG)
        self.bury()
        self.run("tick unfreeze", f"gamemode survival {self.user}", "gamerule naturalRegeneration false",
                 "scoreboard objectives remove capture_deaths", "scoreboard objectives add capture_deaths deathCount",
                 f"tp {self.user} 0.5 {y} 0.5 0 {FIGHT_PITCH:g}", f"item replace entity {self.user} weapon.mainhand with {SWORD}",
                 f"effect clear {self.user}", f"effect give {self.user} minecraft:instant_health 1 10 true",
                 warn=False)
        self.manifest["notes"].append("fight: no armour, no effects, naturalRegeneration false, healed between beasts")
        time.sleep(0.8)
        press_f5_to(self.s, "first")
        mode = scene.ensure_cultivation(self.s)
        self.manifest["notes"].append(f"combat mode: {mode}")
        press_f5_to(self.s, "back")
        self.run(summon(self.beast, (0.5, y, 9.5), 180.0))
        server_log = LOG_DIR / "server.log"
        log_start = server_log.stat().st_size if server_log.is_file() else 0
        samples = []
        stop = threading.Event()

        def poll():
            with self.s.rcon() as r:
                while not stop.is_set():
                    t = time.time()
                    st = r.cmd(f"myvillage beast status {self.sel()}").strip()
                    hp = parse_float(r.cmd(f"data get entity {self.user} Health"))
                    samples.append((round(t, 3), st, hp))
                    time.sleep(0.02)

        def deaths_so_far() -> int:
            reply = self.run(f"scoreboard players get {self.user} capture_deaths", warn=False)[0]
            m = re.search(r" has (\d+) ", reply)
            return int(m.group(1)) if m else 0

        def act(total):
            end = time.time() + total
            last_aim = 0.0
            last_death_check = 0.0
            missing = 0
            deaths = 0
            while time.time() < end:
                if time.time() - last_death_check > 0.5:
                    last_death_check = time.time()
                    now_deaths = deaths_so_far()
                    if now_deaths > deaths:  # respawned at the world spawn (immediate respawn): bring them back
                        deaths = now_deaths
                        self.manifest.setdefault("fight_deaths", []).append(round(time.time(), 3))
                        self.run(f"tp {self.user} 0.5 {y} 0.5 0 {FIGHT_PITCH:g}", warn=False)
                        time.sleep(0.3)
                if time.time() - last_aim > 0.25:
                    # Face the beast, but keep the pitch at FIGHT_PITCH so the F5 camera looks over the
                    # player's shoulder down onto it (looking at its feet hides it behind the player).
                    self.run(f"execute as {self.user} at @s facing entity {self.sel()} feet "
                             f"run tp @s ~ ~ ~ ~ {FIGHT_PITCH:g}", warn=False)
                    last_aim = time.time()
                if samples:
                    st = parse_status(samples[-1][1])
                    try:
                        dist = float(st.get("dist", "99"))
                    except ValueError:
                        dist = 99.0
                    if dist < 3.6:
                        self.g.click()
                        time.sleep(0.3)
                        continue
                reply = self.run(f"data get entity {self.sel()} Health", warn=False)[0]
                missing = missing + 1 if "No entity was found" in reply else 0
                if missing >= 2:  # killed and gone (twice, to be sure): heal, then bring the next one
                    self.run(f"effect give {self.user} minecraft:instant_health 1 10 true",
                             f"tp {self.user} 0.5 {y} 0.5 0 {FIGHT_PITCH:g}")
                    time.sleep(0.5)
                    self.run(summon(self.beast, (0.5, y, 9.5), 180.0))
                    missing = 0
                time.sleep(0.05)

        poller = threading.Thread(target=poll, daemon=True)
        poller.start()
        try:
            self.record("fight", seconds, "fight: survival player standing still, no armour or Resistance, Qingfeng "
                        "sword, cultivation combat mode (F5 back); one beast at a time", act)
        finally:
            stop.set()
            poller.join(timeout=5)
        press_f5_to(self.s, "first")
        lines = []
        if server_log.is_file():
            with open(server_log, "rb") as f:
                f.seek(log_start)
                lines = [l for l in f.read().decode("utf-8", "replace").splitlines() if "BEAST_DEBUG" in l]
        (self.out / "fight_log.txt").write_text(
            "# server BEAST_DEBUG lines\n" + "\n".join(lines) + "\n\n# polled status (wall time, status, player hp)\n"
            + "\n".join(f"{t} | hp={hp} | {st}" for t, st, hp in samples) + "\n", encoding="utf-8")
        self.manifest["fight"] = {"seconds": seconds, "debug_lines": len(lines), "samples": len(samples),
                                  "deaths": len(self.manifest.pop("fight_deaths", [])),
                                  "stagger": analyse_fight(lines, samples),
                                  "rounds": fight_rounds(lines, self.data["moves"], samples)}
        self.run(f"kill @e[tag={TAG}]", f"effect clear {self.user}", "gamerule naturalRegeneration true",
                 "scoreboard objectives remove capture_deaths", f"effect give {self.user} minecraft:instant_health 1 10 true",
                 warn=False)

    def dodge(self):
        """Trials at /tick rate 5 (the player moves at the same slowed rate)
        against a survival player 2.4 blocks ahead of a fresh beast, each move
        forced with /myvillage beast move:

        - the close-range move (smallest use_range maximum): standing still,
          strafing left (A held for 11 ticks) from its first tick, and strafing
          left from its turn-lock tick;
        - every move: one sword swing (cultivation combat mode) sent at move
          tick 2, to show whether a hit early in the wind-up staggers and
          cancels the move or is resisted (immune_ticks).

        Natural regeneration is off. Each trial keeps the server's start, hit,
        hurt, stagger and end lines, the player's health before and after, and
        how far the player moved."""
        self.log("dodge")
        y = scene.FLOOR_Y
        close = min(self.data["moves"], key=lambda m: m["use_range"][1])
        self.remove(TAG)
        self.bury()
        self.run("tick rate 20", "tick unfreeze", f"gamemode survival {self.user}", f"effect clear {self.user}",
                 "gamerule naturalRegeneration false", f"tp {self.user} 0.5 {y} 0.5 0 {FIGHT_PITCH:g}",
                 f"item replace entity {self.user} weapon.mainhand with {SWORD}", warn=False)
        time.sleep(0.8)
        press_f5_to(self.s, "first")
        mode = scene.ensure_cultivation(self.s)
        self.manifest["notes"].append(f"dodge combat mode: {mode}")
        press_f5_to(self.s, "back")
        server_log = LOG_DIR / "server.log"
        trials = []

        def trial(name: str, move: dict, strafe_from=None, swing_at=None):
            log_start = server_log.stat().st_size if server_log.is_file() else 0
            self.run("tick freeze")
            self.remove(TAG)
            self.run(f"tp {self.user} 0.5 {y} 0.5 0 {FIGHT_PITCH:g}",
                     f"effect give {self.user} minecraft:instant_health 1 10 true")
            time.sleep(0.6)
            self.run(summon(self.beast, (0.5, y, 2.9), 180.0), "tick step 2")
            time.sleep(0.5)
            hp_before = parse_float(self.run(f"data get entity {self.user} Health", warn=False)[0])
            start_pos = self.pos(None)
            self.run(f"damage {self.sel()} 0.5 minecraft:player_attack by {self.user}",
                     f"myvillage beast move {self.sel()} {move['id']}", "tick rate 5", "tick unfreeze")
            held = False
            released_at = 0.0
            swung_at = None
            deadline = time.time() + 30
            seen_move = False
            try:
                while time.time() < deadline:
                    st = self.status()
                    tick = int(st.get("tick", "-1"))
                    if st.get("move", "none") != "none":
                        seen_move = True
                    if strafe_from is not None and not held and seen_move and tick >= strafe_from:
                        self.g.require_ingame("strafe")
                        self.g.xdo("keydown", "a")
                        held = True
                        released_at = time.time() + 2.2  # 11 ticks at rate 5
                    if held and time.time() >= released_at:
                        self.g.xdo("keyup", "a")
                        held = False
                        strafe_from = None
                    if swing_at is not None and swung_at is None and seen_move and tick >= swing_at:
                        self.g.click()
                        swung_at = tick
                    if seen_move and st.get("move") == "none":
                        break
                    time.sleep(0.03)
            finally:
                if held:
                    self.g.xdo("keyup", "a")
                self.run("tick freeze", "tick rate 20")
            time.sleep(0.5)  # let the server log catch up
            hp_after = parse_float(self.run(f"data get entity {self.user} Health", warn=False)[0])
            end_pos = self.pos(None)
            moved = (round(math.hypot(end_pos[0] - start_pos[0], end_pos[2] - start_pos[2]), 2)
                     if start_pos and end_pos else None)
            lines = []
            if server_log.is_file():
                with open(server_log, "rb") as f:
                    f.seek(log_start)
                    lines = [l.split("BEAST_DEBUG ", 1)[1] for l in f.read().decode("utf-8", "replace").splitlines()
                             if "BEAST_DEBUG" in l and any(k in l for k in (" hit ", " end ", " start ", " staggered",
                                                                             "hurt by minecraft:player"))]
            entry = {"trial": name, "move": move["id"], "hp_before": hp_before, "hp_after": hp_after,
                     "player_moved": moved, "server": lines,
                     "bitten": any(" hit minecraft:player " in l and "accepted=true" in l for l in lines),
                     "cancelled": any("staggered: cancelled" in l for l in lines)}
            if swing_at is not None:
                entry["swing_sent_at_move_tick"] = swung_at
            trials.append(entry)
            self.log(f"  {name}: hp {hp_before} -> {hp_after}, moved {moved}; {lines}")
            self.remove(TAG)
            self.run("tick unfreeze")
            self.bury()
            time.sleep(0.6)

        def act(seconds):
            short = short_id(close["id"])
            trial(f"{short}: stand still", close)
            trial(f"{short}: strafe from move tick 0", close, strafe_from=0)
            trial(f"{short}: strafe from the turn lock (tick {close['turn_lock_tick']})", close,
                  strafe_from=close["turn_lock_tick"])
            for move in self.data["moves"]:
                trial(f"{short_id(move['id'])}: one swing at move tick 2 (immune from tick "
                      f"{move['immune_ticks'][0]})", move, swing_at=2)

        seconds = 20.0 + 12.0 * (3 + len(self.data["moves"]))
        self.record("dodge", seconds, "dodge: the close-range move against a player standing still, strafing from "
                    "tick 0 and from the turn lock; one swing early in each move's wind-up (/tick rate 5)", act)
        self.run("gamerule naturalRegeneration true", warn=False)
        self.manifest["dodge"] = trials
        press_f5_to(self.s, "first")

    def finish(self):
        """Merge into an earlier manifest so a partial re-run (--parts) keeps the other parts."""
        path = self.out / "manifest.json"
        if path.is_file():
            try:
                old = json.loads(path.read_text(encoding="utf-8"))
            except ValueError:
                old = {}
            for key in ("stills", "videos"):
                mine = {entry["file"] for entry in self.manifest[key]}
                self.manifest[key] = [e for e in old.get(key, []) if e["file"] not in mine] + self.manifest[key]
            for key in ("fight", "dodge", "slowmo"):
                if key not in self.manifest and key in old:
                    self.manifest[key] = old[key]
        path.write_text(json.dumps(self.manifest, indent=2), encoding="utf-8")
        write_page(self.out, self.manifest)


HURT_LINE = re.compile(r"BEAST_DEBUG #(\d+) t=(\d+) hurt by (\S+) \((\w+)\) amount=(\S+) accepted=(\w+) "
                       r"hp (\S+) -> (\S+) move=(\S+) tick=(-?\d+) resists=(\w+)")
STAGGER_LINE = re.compile(r"BEAST_DEBUG #(\d+) t=(\d+) staggered: (.*)$")
STATUS_ID = re.compile(r"#(\d+) ")
HIT_LINE = re.compile(r"BEAST_DEBUG #(\d+) t=(\d+) hit (\S+) with (\S+) at move tick (-?\d+): damage (\S+) "
                      r"accepted=(\w+) hp (\S+) -> (\S+)")
END_LINE = re.compile(r"BEAST_DEBUG #(\d+) t=(\d+) end (\S+) at move tick (-?\d+), cooldown (\d+)")
START_LINE = re.compile(r"BEAST_DEBUG #(\d+) t=(\d+) start (\S+)\s*$")
CANCEL_LINE = re.compile(r"BEAST_DEBUG #(\d+) t=(\d+) staggered: cancelled (\S+) at move tick (-?\d+)")


def short_id(move_id: str) -> str:
    return move_id.split(":", 1)[-1]


def fight_rounds(lines: list[str], moves: list[dict], samples=()) -> list[dict]:
    """One entry per beast, in beast ticks `t` (which a hit-stop freeze pauses):

    - first_exchange_t / killed_t / ticks_to_kill: first blow either way to death;
    - started / completed / cancelled per move (short id); a move that is
      neither completed (ended at its last tick) nor cancelled by a stagger was
      cut by the beast's death;
    - player_hits on the beast, split by whether it resisted (inside a move's
      immune window) and whether a stagger followed (see analyse_fight);
    - its hits on the player: landed per move, the ticks they landed on
      (landed_t), hits_taken (accepted) and damage_to_player (health lost);
    - first_in_range_t per move: the first polled tick at which the target was
      inside that move's use_range, and in_range_to_landed: ticks from there to
      that move's first landed hit (None when it never landed);
    - first_bite_after_ticks: first exchange to the first landed hit of a move
      whose id ends in "bite" (kept for older pages)."""
    total_ticks = {m["id"]: m["total_ticks"] for m in moves}
    rounds: dict[int, dict] = {}

    def entry(beast_id):
        return rounds.setdefault(beast_id, {
            "id": beast_id, "first_exchange_t": None, "killed_t": None, "player_hits": 0,
            "player_hits_resisted": 0, "staggered_inside_window": 0, "staggered_outside_window": 0,
            "started": {}, "completed": {}, "cancelled": {}, "landed": {}, "landed_t": {},
            "hits_taken": 0, "damage_to_player": 0.0, "first_in_range_t": {}, "in_range_to_landed": {}})

    def bump(table, key):
        table[key] = table.get(key, 0) + 1

    for line in lines:
        m = HURT_LINE.search(line)
        if m and m.group(3) == "minecraft:player" and m.group(6) == "true":
            r = entry(int(m.group(1)))
            t = int(m.group(2))
            r["player_hits"] += 1
            r["first_exchange_t"] = t if r["first_exchange_t"] is None else r["first_exchange_t"]
            if float(m.group(8)) <= 0.0:
                r["killed_t"] = t
            continue
        m = HIT_LINE.search(line)
        if m and m.group(3) == "minecraft:player":
            r = entry(int(m.group(1)))
            t = int(m.group(2))
            r["first_exchange_t"] = t if r["first_exchange_t"] is None else r["first_exchange_t"]
            if m.group(7) == "true":
                move = short_id(m.group(4))
                bump(r["landed"], move)
                r["landed_t"].setdefault(move, []).append(t)
                r["hits_taken"] += 1
                r["damage_to_player"] = round(r["damage_to_player"] + float(m.group(8)) - float(m.group(9)), 2)
            continue
        m = START_LINE.search(line)
        if m:
            bump(entry(int(m.group(1)))["started"], short_id(m.group(3)))
            continue
        m = CANCEL_LINE.search(line)
        if m:
            bump(entry(int(m.group(1)))["cancelled"], short_id(m.group(3)))
            continue
        m = END_LINE.search(line)
        if m and total_ticks.get(m.group(3)) == int(m.group(4)) + 1:
            bump(entry(int(m.group(1)))["completed"], short_id(m.group(3)))

    for hit in analyse_fight(lines)["hits"]:
        r = entry(hit["id"])
        if hit["resists"]:
            r["player_hits_resisted"] += 1
        if hit["staggered"]:
            r["staggered_inside_window" if hit["resists"] else "staggered_outside_window"] += 1

    for _, line, _ in samples:
        m = STATUS_ID.search(line)
        st = parse_status(line)
        if not m or int(m.group(1)) not in rounds or "t" not in st or "dist" not in st:
            continue
        r = rounds[int(m.group(1))]
        try:
            t, dist = int(st["t"]), float(st["dist"])
        except ValueError:
            continue
        for move in moves:
            low, high = move["use_range"]
            name = short_id(move["id"])
            if low <= dist <= high and (name not in r["first_in_range_t"] or t < r["first_in_range_t"][name]):
                r["first_in_range_t"][name] = t

    out = []
    for r in rounds.values():
        start = r["first_exchange_t"]
        r["ticks_to_kill"] = r["killed_t"] - start if r["killed_t"] is not None and start is not None else None
        for name, t in r["first_in_range_t"].items():
            later = [x for x in r["landed_t"].get(name, []) if x >= t]
            r["in_range_to_landed"][name] = later[0] - t if later else None
        bites = sorted(t for name, ts in r["landed_t"].items() if name.endswith("bite") for t in ts)
        r["first_bite_after_ticks"] = bites[0] - start if bites and start is not None else None
        out.append(r)
    return out


def analyse_fight(lines: list[str], samples=()) -> dict:
    """Pairs each player hit the server logged with what followed for the same
    beast: a stagger line before that beast's next hit, and the polled velocity
    just before and after the hit (beast ticks, which a hit-stop freeze pauses)."""
    hits, staggers = [], []
    for line in lines:
        m = HURT_LINE.search(line)
        if m and m.group(3) == "minecraft:player" and m.group(6) == "true":
            hits.append({"id": int(m.group(1)), "t": int(m.group(2)), "hp": [float(m.group(7)), float(m.group(8))],
                         "move": m.group(9), "tick": int(m.group(10)), "resists": m.group(11) == "true"})
        m = STAGGER_LINE.search(line)
        if m:
            staggers.append({"id": int(m.group(1)), "t": int(m.group(2)), "what": m.group(3)})
    polled = {}
    for _, line, _ in samples:
        m = STATUS_ID.search(line)
        st = parse_status(line)
        if m and "t" in st:
            polled.setdefault((int(m.group(1)), int(st["t"])), st)
    for hit in hits:
        later = [h["t"] for h in hits if h["id"] == hit["id"] and h["t"] > hit["t"]]
        until = min(later) if later else 10 ** 9
        hit["staggered"] = next((s["what"] for s in staggers
                                 if s["id"] == hit["id"] and hit["t"] <= s["t"] < until), None)
        hit["vel"] = {str(dt): polled[(hit["id"], hit["t"] + dt)].get("vel")
                      for dt in (-1, 0, 1, 2, 3) if (hit["id"], hit["t"] + dt) in polled}
    inside = [h for h in hits if h["resists"]]
    outside = [h for h in hits if not h["resists"]]
    return {"hits": hits, "staggers": staggers,
            "inside_window": {"count": len(inside), "staggered": sum(1 for h in inside if h["staggered"])},
            "outside_window": {"count": len(outside), "staggered": sum(1 for h in outside if h["staggered"]),
                               "cancelled_a_move": sum(1 for h in outside if h["staggered"]
                                                       and h["staggered"].startswith("cancelled"))}}


def write_page(out: Path, manifest: dict) -> None:
    rows = []
    for still in manifest["stills"]:
        rows.append(f'<figure><a href="{still["file"]}"><img src="{still["file"]}" loading="lazy"></a>'
                    f'<figcaption>{html.escape(still["title"])}</figcaption></figure>')
    vids = [f'<figure><video src="{v["file"]}" controls preload="metadata"></video>'
            f'<figcaption>{html.escape(v["title"])}</figcaption></figure>' for v in manifest["videos"]]
    fight = manifest.get("fight")
    fight_html = ""
    if fight:
        st = fight["stagger"]
        fight_html = (f"<h2>Fight evidence</h2><p>Player hits logged by the server: "
                      f"{len(st['hits'])}. Inside the immune window: {st['inside_window']['count']} "
                      f"(staggered {st['inside_window']['staggered']}). Outside: {st['outside_window']['count']} "
                      f"(staggered {st['outside_window']['staggered']}, of which cancelled a move "
                      f"{st['outside_window']['cancelled_a_move']}). Full log: "
                      f'<a href="fight_log.txt">fight_log.txt</a>.</p>')
    if fight and fight.get("rounds"):
        def per_move(r):
            names = sorted(set(r.get("started", {})) | set(r.get("completed", {})) | set(r.get("landed", {})))
            return "<br>".join(
                f"{html.escape(n)}: {r.get('started', {}).get(n, 0)}/{r.get('completed', {}).get(n, 0)}/"
                f"{r.get('cancelled', {}).get(n, 0)}, landed {r.get('landed', {}).get(n, 0)}" for n in names)

        def in_range(r):
            return "<br>".join(f"{html.escape(n)}: {v}" for n, v in sorted(r.get("in_range_to_landed", {}).items()))

        rows_html = "".join(
            f"<tr><td>#{r['id']}</td><td>{r['ticks_to_kill']}</td><td>{r['player_hits']} "
            f"({r.get('player_hits_resisted', 0)} resisted; staggered {r.get('staggered_inside_window', 0)} inside, "
            f"{r.get('staggered_outside_window', 0)} outside)</td><td>{per_move(r)}</td>"
            f"<td>{r.get('hits_taken', 0)}</td><td>{r['damage_to_player']}</td><td>{in_range(r)}</td></tr>"
            for r in fight["rounds"])
        fight_html += ("<table><tr><th>beast</th><th>ticks to kill</th><th>player hits on it</th><th>moves "
                       "started/completed/cancelled</th><th>its hits on the player</th><th>damage to player</th>"
                       "<th>ticks from in use_range to first landed hit</th></tr>"
                       f"{rows_html}</table><p>Beast ticks (a hit-stop freeze pauses them). Player deaths: "
                       f"{fight.get('deaths', 0)}.</p>")
    dodge = manifest.get("dodge")
    if dodge:
        fight_html += "<h2>Dodge trials</h2><ul>" + "".join(
            f"<li>{html.escape(t['trial'])}: hp {t['hp_before']} -> {t['hp_after']}, "
            f"{'hit' if t['bitten'] else 'not hit'}"
            f"{', move cancelled by a stagger' if t.get('cancelled') else ''}"
            f"{'' if t.get('player_moved') is None else ', player moved ' + str(t['player_moved']) + ' blocks'}</li>"
            for t in dodge) + "</ul>"
    notes = "".join(f"<li>{html.escape(n)}</li>" for n in manifest.get("notes", []))
    page = f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>{html.escape(manifest['beast'])} in game</title>
<style>body{{font:14px/1.4 sans-serif;margin:16px;background:#14161a;color:#ddd}}
.grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:12px}}
figure{{margin:0}}img,video{{width:100%;border:1px solid #333}}figcaption{{font-size:12px;color:#aaa}}
a{{color:#7cc}}</style></head><body>
<h1>{html.escape(manifest['beast'])}: in-game capture</h1>
<p>Developer evidence from the headless capture session (960x540, software GL). Not an owner verdict.</p>
<ul>{notes}</ul>{fight_html}
<h2>Videos</h2><div class="grid">{''.join(vids)}</div>
<h2>Stills</h2><div class="grid">{''.join(rows)}</div></body></html>"""
    (out / "index.html").write_text(page, encoding="utf-8")


def run_beast(session: Session, beast: str, out: Path, parts: list[str], log=print) -> dict:
    cap = BeastCapture(session, beast, out, log=log)
    try:
        cap.setup_world()
        if "idle" in parts:
            cap.stills_idle()
        if "moves" in parts:
            cap.stills_moves()
        if "locomotion" in parts:
            cap.locomotion()
        if "fight" in parts:
            cap.fight()
        if "dodge" in parts:
            cap.dodge()
        if "slowmo" in parts:
            cap.slowmo()
    finally:
        try:
            if cap.hud_hidden:
                cap.hide_hud(False)
            cap.run("tick unfreeze", "myvillage beast debug off", warn=False)
            cap.bury()
        finally:
            cap.finish()
    return cap.manifest
