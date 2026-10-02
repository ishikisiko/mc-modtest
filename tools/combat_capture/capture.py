"""Stills (freeze probes) and the combo run (mapped clicks) against a ready
session, plus the capture-set manifest."""
from __future__ import annotations

import datetime
import json
import re
import subprocess
import time
from pathlib import Path

from . import scene
from .data import Weapon, asset_rel, fmt_tick, sha256_file
from .session import REPO, SCREEN

BUILD_RESOURCES = REPO / "build/resources/main"

MANIFEST = "manifest.json"
MANIFEST_SCHEMA = 1

F5_INDEX = {"first": 0, "back": 1, "front": 2}
# Camera framing for F5. The eye sits at the screen centre and the F5 camera
# looks along the player's look direction (back) or against it (front).
# - wall: a barrier wall row behind ("back") or in front ("front") of the
#   player stops the camera `wall_distance` blocks (horizontally) from the eye;
#   None leaves vanilla's 4-block F5 distance (the most a no-Java camera gets).
# - pitch: the player's pitch. It also tilts the PAL arm pose, so a quarter
#   view uses the same pitch as the straight view on its side and shows the
#   same pose.
# - look_yaw: the player's yaw. The body is first aligned to BODY_YAW (the
#   action facing, south); vanilla then keeps a standing player's body where it
#   is while the head turns up to HEAD_BODY_LIMIT degrees, so look_yaw +-50
#   sees the posed body from 50 degrees off its axis (see body_align_yaws).
#   Player facing south: its right side is -x, so a camera behind the right
#   shoulder needs look_yaw -50 and one in front on the right needs +50.
# These values are recorded in the manifest and must match between two
# captures that are compared.
TP_BACK_DIST, TP_BACK_PITCH = 2.4, 30.0
TP_FRONT_DIST, TP_FRONT_PITCH = 2.4, 15.0
VANILLA_F5_DISTANCE = 4.0
BODY_YAW = 0.0
HEAD_BODY_LIMIT = 50.0
QUARTER_YAW = 50.0
# Weapons drawn at least this long in a third-person hand get the quarter
# views by default (the straight views look along their shaft).
LONG_WEAPON_BLOCKS = 1.8


def _tp(title, f5, wall, dist, pitch, look_yaw):
    return {"title": title, "probe": "third_person", "f5": f5,
            "camera": {"wall": wall, "wall_distance": dist, "pitch": pitch, "look_yaw": look_yaw}}


VIEWS = {
    "fp": {"title": "first person", "probe": "first_person", "f5": "first"},
    "tp_back": _tp("third person, F5 back", "back", "back", TP_BACK_DIST, TP_BACK_PITCH, BODY_YAW),
    "tp_front": _tp("third person, F5 front", "front", "front", TP_FRONT_DIST, TP_FRONT_PITCH, BODY_YAW),
    "tp_back_right": _tp("third person, F5 back, 50 deg off behind the right shoulder", "back", None, None,
                         TP_BACK_PITCH, BODY_YAW - QUARTER_YAW),
    "tp_back_left": _tp("third person, F5 back, 50 deg off behind the left shoulder", "back", None, None,
                        TP_BACK_PITCH, BODY_YAW + QUARTER_YAW),
    "tp_front_right": _tp("third person, F5 front, 50 deg off on the player's right", "front", None, None,
                          TP_FRONT_PITCH, BODY_YAW + QUARTER_YAW),
    "tp_front_left": _tp("third person, F5 front, 50 deg off on the player's left", "front", None, None,
                         TP_FRONT_PITCH, BODY_YAW - QUARTER_YAW),
}
ALL_VIEWS = tuple(VIEWS)
SHORT_WEAPON_VIEWS = ("fp", "tp_back", "tp_front")
LONG_WEAPON_VIEWS = ("fp", "tp_back_right", "tp_front_left")
TICK_SECONDS = 0.05


def default_views(weapon: Weapon) -> list[str]:
    """Views a capture of this weapon takes unless --views says otherwise:
    the quarter views for a weapon drawn LONG_WEAPON_BLOCKS or longer in a
    third-person hand (from its geometry contract and item model), the
    straight views otherwise (and when the weapon has no geometry)."""
    long = weapon.held_length is not None and weapon.held_length >= LONG_WEAPON_BLOCKS
    return list(LONG_WEAPON_VIEWS if long else SHORT_WEAPON_VIEWS)


def body_align_yaws(look_yaw: float, body_yaw: float = BODY_YAW, limit: float = HEAD_BODY_LIMIT) -> list[float]:
    """Player yaws to teleport through, a client tick or more apart, so the
    body ends at exactly `body_yaw` and the look at `look_yaw`, whatever the
    body was before. Vanilla turns a standing, not swinging player's body only
    when the head is more than `limit` degrees off it, and then by exactly the
    excess. Step 1 (body - s*70) leaves the body 20..120 degrees on the far
    side; step 2 (body + s*limit) is then 70..170 degrees off, so the body is
    clamped to exactly body_yaw; step 3 turns the head only (within the limit)."""
    off = look_yaw - body_yaw
    if abs(off) > limit:
        raise ValueError(f"look yaw {look_yaw} is more than {limit} degrees off the body yaw {body_yaw}")
    s = -1.0 if off < 0 else 1.0
    out = [body_yaw - s * (limit + 20.0), body_yaw + s * limit]
    if look_yaw != out[-1]:
        out.append(look_yaw)
    return out


def stand_z(camera: dict) -> float:
    """Where the player stands (z) so a wall stops the camera at wall_distance."""
    if camera["wall"] == "back":
        return scene.BACK_WALL_Z + 1 + camera["wall_distance"]
    if camera["wall"] == "front":
        return scene.FRONT_WALL_Z - camera["wall_distance"]
    return scene.PLAYER_POS[2]


def camera_record(view: str) -> dict:
    """Manifest entry for a third-person view's camera."""
    c = VIEWS[view]["camera"]
    return {"view": view, "f5": VIEWS[view]["f5"], "wall_distance": c["wall_distance"],
            "camera_distance": c["wall_distance"] if c["wall"] else VANILLA_F5_DISTANCE,
            "pitch": c["pitch"], "look_yaw": c["look_yaw"], "body_yaw": BODY_YAW,
            "player_pos": [scene.PLAYER_POS[0], scene.PLAYER_POS[1], round(stand_z(c), 3)]}


def camera_key(view: str) -> str:
    return view[3:] if view.startswith("tp_") else view


def camera_mismatches(a: dict, b: dict, views) -> list[str]:
    """Camera fields that differ between two manifests for the given views.
    Only fields both record are compared (older captures lack look_yaw etc.);
    a missing look_yaw/body_yaw counts as BODY_YAW (old captures faced south)."""
    out = []
    ca = a.get("capture", {}).get("third_person_camera", {})
    cb = b.get("capture", {}).get("third_person_camera", {})
    for view in views:
        if VIEWS.get(view, {}).get("f5") in (None, "first"):
            continue
        x, y = ca.get(camera_key(view)), cb.get(camera_key(view))
        if x is None or y is None:
            continue
        for k in sorted(set(x) | set(y)):
            if k in x and k in y:
                va, vb = x[k], y[k]
            elif k in ("look_yaw", "body_yaw"):
                va, vb = x.get(k, BODY_YAW), y.get(k, BODY_YAW)
            else:
                continue
            if va != vb:
                out.append(f"{view}: {k} {va} vs {vb}")
    return out


def now_iso() -> str:
    return datetime.datetime.now().astimezone().isoformat(timespec="seconds")


def git_info(repo: Path = REPO) -> dict:
    def git(*args):
        try:
            return subprocess.run(["git", "-C", str(repo), *args], capture_output=True, text=True,
                                  timeout=20).stdout.strip()
        except (OSError, subprocess.SubprocessError):
            return ""
    dirty = git("status", "--porcelain", "--untracked-files=no")
    return {"head": git("rev-parse", "HEAD") or None, "describe": git("log", "-1", "--format=%s") or None,
            "dirty": bool(dirty)}


def mod_version(repo: Path = REPO):
    try:
        for line in (repo / "gradle.properties").read_text(encoding="utf-8").splitlines():
            if line.startswith("mod_version="):
                return line.split("=", 1)[1].strip()
    except OSError:
        pass
    return None


def new_manifest(label: str, weapon: Weapon, resources: Path, views: list[str], moves: list[int]) -> dict:
    return {
        "schema": MANIFEST_SCHEMA,
        "kind": "capture",
        "label": label,
        "created": now_iso(),
        "weapon": weapon.id,
        "item": weapon.item,
        "style": weapon.style_id,
        "rig": weapon.rig_location,
        "source": {**git_info(), "mod_version": mod_version(),
                   "files": {role: {"path": rel, "sha256": sha256_file(Path(resources) / rel)}
                             for role, rel in weapon.files.items()}},
        "capture": {"size": list(SCREEN), "fov": 70, "views": views,
                    "weapon_held_length_blocks": weapon.held_length,
                    "third_person_camera": {camera_key(v): camera_record(v)
                                            for v in ["tp_back", "tp_front"] + [v for v in views if v not in
                                                                                ("fp", "tp_back", "tp_front")]}},
        "moves": [{"index": m.index, "id": m.id, "short": m.short, "kind": m.kind, "total_ticks": m.total_ticks,
                   "keys": [{"key": k, "tick": t} for k, t in m.keys]}
                  for m in weapon.moves if m.index in moves],
        "frames": [],
        "stills_complete": False,
        "sheets": {},
        "combo": None,
        "notes": [],
    }


def write_manifest(out_dir: Path, manifest: dict) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    tmp = out_dir / (MANIFEST + ".tmp")
    tmp.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    tmp.replace(out_dir / MANIFEST)


def load_manifest(out_dir: Path) -> dict:
    p = Path(out_dir) / MANIFEST
    if not p.is_file():
        raise FileNotFoundError(f"{p} not found (not a capture directory)")
    return json.loads(p.read_text(encoding="utf-8"))


def frame_file(view: str, move: int, key: str) -> str:
    return f"frames/{view}/m{move}_{key}.png"


def f5_presses(current: str, target: str) -> int:
    return (F5_INDEX[target] - F5_INDEX[current]) % 3


def press_f5_to(session, target: str) -> None:
    n = f5_presses(session.view, target)
    for _ in range(n):
        session.game.key("F5")
        session.view = {0: "back", 1: "front", 2: "first"}[F5_INDEX[session.view]]
        time.sleep(0.35)
    if n:
        time.sleep(0.7)


def tp_command(user: str, z: float, yaw: float, pitch: float) -> str:
    px, py, _ = scene.PLAYER_POS
    return f"tp {user} {px:g} {py:g} {z:g} {yaw:g} {pitch:g}"


# A teleport reaches the client within a server tick; the client applies the
# head-turn clamp on its next tick. 0.4 s leaves several ticks for each step.
ALIGN_STEP_SECONDS = 0.4


def set_camera(session, view: str) -> bool:
    """Put the client camera into `view` (walls, stand, body alignment, F5)
    without any probe. Returns True if a barrier wall is up."""
    spec = VIEWS[view]
    if spec["f5"] == "first":
        session.run(scene.wall_fill(scene.BACK_WALL_Z, "minecraft:air"),
                    scene.wall_fill(scene.FRONT_WALL_Z, "minecraft:air"))
        session.run(tp_command(session.username, scene.PLAYER_POS[2], BODY_YAW, 0.0))
        time.sleep(0.6)
        press_f5_to(session, "first")
        time.sleep(0.8)
        return False
    cam = spec["camera"]
    session.run(*scene.wall_commands(cam["wall"]))
    z = stand_z(cam)
    # Stand first (a teleport is not movement for the body-turn logic, but keep
    # the alignment teleports at one position anyway), then turn body and head.
    session.run(tp_command(session.username, z, BODY_YAW, cam["pitch"]))
    time.sleep(ALIGN_STEP_SECONDS)
    for yaw in body_align_yaws(cam["look_yaw"]):
        session.run(tp_command(session.username, z, yaw, cam["pitch"]))
        time.sleep(ALIGN_STEP_SECONDS)
    press_f5_to(session, spec["f5"])
    time.sleep(0.8)
    return cam["wall"] is not None


# ====================================================================== stills
def probe_reject_pattern(probe: str) -> str:
    """Client log line of a refused probe (ClientPalSmokeEvents): group 1 is
    the reason, e.g. tick_out_of_range, no_weapon, index_out_of_range."""
    return rf"PAL_SMOKE {probe} rejected reason=(\S+)"


def rig_record(resources_root: Path, rel: str, source_root: Path | None = None) -> dict:
    """What a first-person rig file declares, read from the file the client
    loads (build/resources/main for a running session): its sha256 and
    whether it declares an off hand (rig.off_hand). With `source_root`, also
    whether it equals the source tree's copy (it differs after `reload --src`)."""
    p = Path(resources_root) / rel
    rec = {"path": rel, "read_from": "build/resources/main" if Path(resources_root) == BUILD_RESOURCES
           else "resources root", "present": p.is_file()}
    if not p.is_file():
        return rec
    try:
        rig = json.loads(p.read_text(encoding="utf-8")).get("rig", {})
    except ValueError:
        rig = {}
        rec["invalid_json"] = True
    rec.update({"sha256": sha256_file(p), "off_hand": isinstance(rig.get("off_hand"), dict)})
    if source_root is not None:
        s = Path(source_root) / rel
        rec["same_as_source"] = s.is_file() and sha256_file(s) == rec["sha256"]
    return rec


def record_loaded_rig(manifest: dict, weapon: Weapon) -> None:
    """manifest['rig_loaded'] from the copy the running client reads."""
    rel = weapon.files.get("rig") or asset_rel(weapon.rig_location)
    manifest["rig_loaded"] = rig_record(BUILD_RESOURCES, rel, REPO / "src/main/resources")


class Stills:
    def __init__(self, session, weapon: Weapon, out_dir: Path, manifest: dict, log=print):
        self.s = session
        self.g = session.game
        self.weapon = weapon
        self.out = Path(out_dir)
        self.m = manifest
        self.log = log
        self.active_probe = None
        self.walls = False

    def press_f5_to(self, target: str):
        press_f5_to(self.s, target)

    def place(self, z: float, pitch: float):
        self.s.run(tp_command(self.s.username, z, BODY_YAW, pitch))
        time.sleep(0.6)

    def set_view(self, view: str):
        self.walls = set_camera(self.s, view) or self.walls

    def shoot(self, view: str, move, key: str, tick: float):
        probe = VIEWS[view]["probe"]
        n = move.index
        m, line = self.g.chat(f"/myvillage_pal_smoke {probe} {n} {fmt_tick(tick)}",
                              confirm=rf"PAL_SMOKE {probe} move={n} tick=([-\d.Ee]+)",
                              reject=probe_reject_pattern(probe))
        self.active_probe = probe
        got = float(m.group(1))
        if abs(got - tick) > 1e-3:
            raise RuntimeError(f"probe tick mismatch: sent {tick}, client logged {got}")
        raw, info = self.g.stable_grab()
        rel = frame_file(view, n, key)
        self.g.save_png(raw, self.out / rel)
        rec = {"view": view, "move": n, "move_id": move.id, "move_short": move.short, "key": key, "tick": tick,
               "file": rel, **info}
        self.m["frames"] = [f for f in self.m["frames"]
                            if not (f["view"] == view and f["move"] == n and f["key"] == key)] + [rec]
        write_manifest(self.out, self.m)
        flag = ("" if info["stable"] else f"  NOT STABLE (diff {info.get('diff_fraction')}, "
                                          f"max level diff {info.get('max_level_diff')})")
        noise = f" within noise {info['max_level_diff']}" if info.get("match") == "noise" else ""
        self.log(f"  {view:<14} m{n} {move.short:<18} {key:<12} t={fmt_tick(tick):<5} grabs={info['grabs']}"
                 f"{noise}{flag}")

    def release(self):
        if self.active_probe:
            probe, self.active_probe = self.active_probe, None
            self.g.chat(f"/myvillage_pal_smoke {probe} release")
            time.sleep(0.4)

    def run(self, views: list[str], moves: list[int]):
        self.log(f"stills: {self.weapon.id}, views {views}, moves {moves}")
        scene.base_scene(self.s, self.weapon.item)
        record_loaded_rig(self.m, self.weapon)
        self.press_f5_to("first")
        self.m["combat_mode_before_stills"] = scene.ensure_cultivation(self.s)
        write_manifest(self.out, self.m)
        # The first still after scene setup and the mode switch kept changing slightly for
        # over 6 s; let the screen settle once (discarded) before the first probe.
        _, warm = self.g.stable_grab(max_wait=20.0)
        self.log(f"warm-up: screen {'settled' if warm['stable'] else 'still changing'} after {warm['seconds']}s")
        try:
            for view in views:
                self.log(f"== {VIEWS[view]['title']}")
                self.set_view(view)
                for mv in self.weapon.moves:
                    if mv.index not in moves:
                        continue
                    for key, tick in mv.keys:
                        self.shoot(view, mv, key, tick)
                self.release()
            self.m["stills_complete"] = True
        finally:
            self.restore()
            write_manifest(self.out, self.m)

    def restore(self):
        steps = [("release probe", self.release)]
        if self.walls:
            steps.append(("remove walls", lambda: self.s.run(
                scene.wall_fill(scene.BACK_WALL_Z, "minecraft:air"), scene.wall_fill(scene.FRONT_WALL_Z, "minecraft:air"))))
        steps.append(("player back to start", lambda: self.place(scene.PLAYER_POS[2], 0.0)))
        steps.append(("first person", lambda: self.press_f5_to("first")))
        for what, fn in steps:
            try:
                fn()
            except Exception as e:  # noqa: BLE001 - best effort; report and continue
                self.log(f"  restore step '{what}' failed: {e}")


# ====================================================================== combo
def click_schedule(moves, tick_seconds: float = TICK_SECONDS) -> tuple[list[dict], int]:
    """Clicks at chain timing through the whole combo. Move 1 is clicked at
    t=0; the click for move n+1 lands midway between move n's buffer start
    and its chain tick (the buffered click is then consumed at the chain tick,
    when move n+1 starts). `tick_seconds` is the length of a game tick (0.05
    at the normal tick rate; 0.2 at /tick rate 5). Returns
    ([{move, tick, seconds}], expected ticks until the last move ends)."""
    clicks = [{"move": moves[0].index, "tick": 0.0, "seconds": 0.0}]
    start = 0.0
    for cur, nxt in zip(moves, moves[1:]):
        t = start + (cur.buffer_start_tick + cur.chain_tick) / 2.0
        clicks.append({"move": nxt.index, "tick": t, "seconds": round(t * tick_seconds, 3)})
        start += cur.chain_tick
    return clicks, int(start + moves[-1].total_ticks)


FP_LINE = re.compile(r"PAL_SMOKE (fp_hit_stop|fp_resync) (.*)$")


def move_starts(timed_plays: list[tuple[float, str]], merge_seconds: float) -> list[dict]:
    """Distinct move starts from the client's accepted `PAL_SMOKE play` lines
    [(time, animation)]: a prediction and its confirmation (the same
    animation again within `merge_seconds`) count once."""
    out = []
    for t, anim in sorted(timed_plays):
        if out and out[-1]["animation"] == anim and t - out[-1]["t"] <= merge_seconds:
            continue
        out.append({"t": t, "animation": anim})
    return out


def health_drops(samples: list[tuple[float, str, float]]) -> list[dict]:
    """[(time, tag, health)] in time order -> one record per decrease."""
    last, out = {}, []
    for t, tag, h in samples:
        if h is None:
            continue
        prev = last.get(tag)
        if prev is not None and h < prev - 1e-6:
            out.append({"tag": tag, "t": t, "before": prev, "after": h, "damage": round(prev - h, 3)})
        last[tag] = h
    return out


def attribute_hits(drops: list[dict], starts: list[dict], moves=None) -> list[dict]:
    """Each health drop goes to the latest move start at or before it (the
    server applies damage during the move's active ticks, after the client
    started it). Adds 'animation' and, when it names a move of `moves`, its
    one-based 'move'."""
    ids = {m.id: m.index for m in (moves or [])}
    out = []
    for d in drops:
        owner = None
        for st in starts:
            if st["t"] <= d["t"]:
                owner = st
        rec = dict(d, animation=owner["animation"] if owner else None)
        if owner and owner["animation"] in ids:
            rec["move"] = ids[owner["animation"]]
        out.append(rec)
    return out


def hits_by_move(hits: list[dict]) -> dict:
    """{move label: {tag: total damage}} for the page and the report."""
    table: dict = {}
    for h in hits:
        key = f"m{h['move']}" if h.get("move") else (h.get("animation") or "before any move")
        table.setdefault(key, {})
        table[key][h["tag"]] = round(table[key].get(h["tag"], 0.0) + h["damage"], 3)
    return table


class HealthPoller:
    """Polls the targets' health over its own rcon connection while the combo
    runs, so each loss can be put on the move that caused it."""

    def __init__(self, session, targets: list[dict], interval: float = 0.05):
        import threading
        self.session, self.targets, self.interval = session, targets, interval
        self.samples: list[tuple[float, str, float]] = []
        self.error = None
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, daemon=True)

    def _run(self):
        try:
            with self.session.rcon() as r:
                while not self._stop.is_set():
                    for t in self.targets:
                        h = scene.parse_float_data(r.cmd(f"data get entity {t['uuid']} Health"))
                        self.samples.append((time.time(), t["tag"], h))
                    self._stop.wait(self.interval)
        except Exception as e:  # noqa: BLE001 - reported in the manifest, never fatal to the run
            self.error = f"{type(e).__name__}: {e}"

    def __enter__(self):
        self._thread.start()
        return self

    def __exit__(self, *exc):
        self._stop.set()
        self._thread.join(timeout=10)


def ffmpeg_record_argv(display: str, out: Path, seconds: float, fps: int = 30, size=SCREEN) -> list[str]:
    return ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "x11grab", "-draw_mouse", "0",
            "-video_size", f"{size[0]}x{size[1]}", "-framerate", str(fps), "-i", f"{display}.0+0,0",
            "-t", f"{seconds:g}", "-fps_mode", "cfr", "-r", str(fps), "-c:v", "libx264", "-preset", "veryfast",
            "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(out)]


PLAY_LINE = re.compile(r"PAL_SMOKE play .*animation=(\S+) elapsed_ticks=(\S+) accepted=(\w+)(.*)$")

# Cameras a combo can be recorded from. A real action snaps the body (and the
# head) to the look yaw it faces and vanilla turns the body to the look while
# swinging, so the F5 camera of a combo always sits on the body's axis: the
# quarter views cannot be held through a real combo (see `motion` for those).
# "back": F5 back at vanilla distance (no wall, the player stays at the combo
# start so the targets and hits are the first-person run's), look yaw 0.
COMBO_CAMERAS = ("first", "back")


def combo_camera_record(camera: str, pitch: float | None) -> dict:
    if camera not in COMBO_CAMERAS:
        raise ValueError(f"combo camera must be one of {COMBO_CAMERAS}, got {camera!r}")
    if camera == "first":
        return {"f5": "first", "pitch": 0.0, "look_yaw": BODY_YAW}
    return {"f5": "back", "camera_distance": VANILLA_F5_DISTANCE,
            "pitch": TP_BACK_PITCH if pitch is None else float(pitch), "look_yaw": BODY_YAW}


def run_combo(session, weapon: Weapon, out_dir: Path, manifest: dict, targets: str = "dummy",
              lead: float = 2.0, tail: float = 2.5, hold: float = 0.06, camera: str = "first",
              pitch: float | None = None, layout: str = "default", tick_rate: int = 20,
              video_name: str = "combo.mp4", log=print) -> dict:
    """Targets in front, mapped left clicks through the whole combo at chain
    timing while ffmpeg records, target health over rcon before and after and
    polled during the run (each loss is put on the move that caused it).
    `camera` "back" records the same run from F5 back (see COMBO_CAMERAS);
    `layout` places the targets (scene.TARGET_LAYOUTS); `tick_rate` below 20
    slows the game with /tick rate for the run (clicks scaled to match)."""
    from .xgame import LogTail, log_line_time
    if not 1 <= int(tick_rate) <= 20:
        raise ValueError("tick_rate must be 1-20")
    tick_seconds = 1.0 / int(tick_rate)
    g = session.game
    cam = combo_camera_record(camera, pitch)
    out_dir = Path(out_dir)
    (out_dir / "combo").mkdir(parents=True, exist_ok=True)
    scene.base_scene(session, weapon.item)
    if not manifest.get("rig_loaded"):
        record_loaded_rig(manifest, weapon)
    press_f5_to(session, "first")
    mode = scene.ensure_cultivation(session)
    if cam["f5"] != "first":
        session.run(tp_command(session.username, scene.PLAYER_POS[2], BODY_YAW, cam["pitch"]))
        time.sleep(0.6)
        press_f5_to(session, cam["f5"])
    placed = scene.place_targets(session, targets, layout)
    time.sleep(1.5)
    before = {t["tag"]: scene.read_health(session, t["uuid"]) for t in placed}
    pos_before = scene.player_pos(session)
    clicks, expected_ticks = click_schedule(weapon.moves, tick_seconds)
    seconds = lead + clicks[-1]["seconds"] + expected_ticks * tick_seconds + tail
    video = out_dir / "combo" / video_name
    if tick_rate != 20:
        session.run(f"tick rate {int(tick_rate)}")
        time.sleep(1.0)
    tail_log = LogTail(g.client_log)
    log(f"combo: {len(clicks)} clicks at {[c['seconds'] for c in clicks]} s, recording {seconds:.1f}s"
        + (f", tick rate {tick_rate}" if tick_rate != 20 else ""))
    try:
        with HealthPoller(session, placed) as poller:
            rec = subprocess.Popen(ffmpeg_record_argv(session.state["display"], video, seconds),
                                   stdin=subprocess.DEVNULL)
            try:
                time.sleep(lead)
                g.require_ingame("combo clicks")
                t0 = time.time()
                done = []
                for c in clicks:
                    delay = t0 + c["seconds"] - time.time()
                    if delay > 0:
                        time.sleep(delay)
                    g.click(hold)
                    done.append(round(time.time() - t0, 3))
                rc = rec.wait(timeout=seconds + 60)
            except BaseException:
                rec.terminate()
                rec.wait(timeout=10)
                raise
            time.sleep(0.5)
    finally:
        if tick_rate != 20:
            session.run("tick rate 20")
    if rc != 0 or not video.is_file():
        raise RuntimeError(f"ffmpeg failed (rc {rc})")
    after = {t["tag"]: scene.read_health(session, t["uuid"]) for t in placed}
    pos_after = scene.player_pos(session)
    tail_log.poll()
    plays, timed, fp_lines = [], [], []
    for line in tail_log.lines:
        m = PLAY_LINE.search(line)
        lt = log_line_time(line)
        if m:
            plays.append({"animation": m.group(1), "elapsed_ticks": m.group(2), "accepted": m.group(3) == "true",
                          "kept_prediction": "kept_prediction=true" in m.group(4),
                          "t": None if lt is None else round(lt - t0, 3)})
            if m.group(3) == "true" and lt is not None:
                timed.append((lt, m.group(1)))
        f = FP_LINE.search(line)
        if f:
            fp_lines.append({"t": None if lt is None else round(lt - t0, 3), "kind": f.group(1),
                             "text": f.group(2).strip()[:300]})
    starts = move_starts(timed, merge_seconds=12 * tick_seconds)
    hits = attribute_hits(health_drops(poller.samples), starts, weapon.moves)
    for h in hits:
        h["t"] = round(h["t"] - t0, 3)
    result = {
        "targets": [{"tag": t["tag"], "kind": t["kind"], "pos": t["pos"], "health_before": before[t["tag"]],
                     "health_after": after[t["tag"]],
                     "damage": None if before[t["tag"]] is None or after[t["tag"]] is None
                     else round(before[t["tag"]] - after[t["tag"]], 3)} for t in placed],
        "total_damage": round(sum((before[k] or 0) - (after[k] or 0) for k in before), 3),
        "clicks_planned": clicks,
        "clicks_sent_s": done,
        "expected_combo_ticks": expected_ticks,
        "video": f"combo/{video_name}",
        "video_seconds": round(seconds, 2),
        "lead_seconds": lead,
        "combat_mode": "cultivation" if mode in ("already", "toggled") else mode,
        "player_pos_before": _strip_name(pos_before),
        "player_pos_after": _strip_name(pos_after),
        "client_animation_starts": plays,
        "move_starts_s": [{"t": round(st["t"] - t0, 3), "animation": st["animation"]} for st in starts],
        "hits": hits,
        "hits_by_move": hits_by_move(hits),
        "health_poll": {"samples": len(poller.samples), "error": poller.error},
        "fp_log": fp_lines,
        "layout": layout,
        "layout_offsets": [[tag, dx, dz] for tag, dx, dz in scene.layout(layout)],
        "tick_rate": int(tick_rate),
        "tick_seconds": tick_seconds,
        "camera": cam,
        "recorded": now_iso(),
    }
    manifest["combo"] = result
    write_manifest(out_dir, manifest)
    for t in result["targets"]:
        log(f"  {t['tag']} ({t['kind']}) health {t['health_before']} -> {t['health_after']} (damage {t['damage']})")
    for mv, per in result["hits_by_move"].items():
        log(f"  {mv}: " + ", ".join(f"{tag} -{dmg}" for tag, dmg in sorted(per.items())))
    if poller.error:
        log(f"  WARNING health poll stopped: {poller.error}")
    # leave a clean field for whatever runs next
    session.run("kill @e[type=!minecraft:player]", warn=False)
    time.sleep(1.0)
    session.run(*scene.cleanup_drops(session.username, weapon.item), warn=False)
    if cam["f5"] != "first":
        press_f5_to(session, "first")
        session.run(tp_command(session.username, scene.PLAYER_POS[2], BODY_YAW, 0.0))
    return result


# ====================================================================== motion
def stop_recording(rec: subprocess.Popen, timeout: float = 30.0) -> int:
    """End an open-ended ffmpeg recording cleanly ('q' on its stdin)."""
    try:
        rec.stdin.write(b"q")
        rec.stdin.flush()
        rec.stdin.close()
    except (OSError, ValueError):
        pass
    try:
        return rec.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        rec.terminate()
        return rec.wait(timeout=10)


def wait_mode(session, want: str, timeout: float = 8.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        time.sleep(0.3)
        if scene.combat_mode(session) == want:
            return True
    return False


def run_motion(session, weapon: Weapon, out_dir: Path, manifest: dict, views: list[str], moves: list[int],
               enter: bool = False, idle: float = 0.0, gap: float = 1.0, log=print) -> list[dict]:
    """Real-time third-person motion from views a real combo cannot hold (the
    quarter views): per view one video of, optionally, the mode-enter
    animation through the real toggle key (R to vanilla, R back) and `idle`
    seconds of the ready idle, then each move's PAL animation played on the
    client at game speed with `/myvillage_pal_smoke move <n>` (typed in chat,
    so the chat line shows briefly). No server action: no hits, no lunge,
    no world trail, and the body does not turn, so the camera keeps its angle."""
    from .xgame import LogTail
    g = session.game
    out_dir = Path(out_dir)
    (out_dir / "motion").mkdir(parents=True, exist_ok=True)
    scene.base_scene(session, weapon.item)
    press_f5_to(session, "first")
    scene.ensure_cultivation(session)
    records = []
    try:
        for view in views:
            if VIEWS[view]["f5"] == "first":
                raise ValueError("motion is for third-person views")
            set_camera(session, view)
            video = out_dir / "motion" / f"{view}.mp4"
            argv = ffmpeg_record_argv(session.state["display"], video, 900)
            rec = subprocess.Popen(argv, stdin=subprocess.PIPE)
            t0 = time.time()
            events = []
            tail_log = LogTail(g.client_log)
            try:
                time.sleep(1.5)
                if enter:
                    g.key("r")
                    events.append({"event": "R (to vanilla)", "s": round(time.time() - t0, 2)})
                    if not wait_mode(session, "vanilla"):
                        log("  WARNING: server did not report vanilla after R")
                    time.sleep(1.5)
                    g.key("r")
                    events.append({"event": "R (to cultivation: mode enter)", "s": round(time.time() - t0, 2)})
                    if not wait_mode(session, "cultivation"):
                        raise RuntimeError("server did not report cultivation after the second R")
                    time.sleep(max(idle, 1.0))
                elif idle:
                    events.append({"event": "ready idle", "s": round(time.time() - t0, 2)})
                    time.sleep(idle)
                for mv in weapon.moves:
                    if mv.index not in moves:
                        continue
                    m, _ = g.chat(f"/myvillage_pal_smoke move {mv.index}", confirm=r"PAL_SMOKE play .*animation=(\S+)",
                                  reject=probe_reject_pattern("move"))
                    events.append({"event": f"move {mv.index} {mv.short}", "animation": m.group(1),
                                   "s": round(time.time() - t0, 2)})
                    time.sleep(mv.total_ticks * TICK_SECONDS + gap)
                time.sleep(1.0)
            finally:
                rc = stop_recording(rec)
            if not video.is_file():
                raise RuntimeError(f"ffmpeg wrote no video for {view} (rc {rc})")
            tail_log.poll()
            plays = [PLAY_LINE.search(l).group(1) for l in tail_log.lines if PLAY_LINE.search(l)]
            rec_ = {"view": view, "video": f"motion/{view}.mp4", "seconds": round(time.time() - t0, 1),
                    "camera": camera_record(view), "events": events, "client_animation_starts": plays}
            records.append(rec_)
            manifest["motion"] = [r for r in manifest.get("motion") or [] if r["view"] != view] + [rec_]
            write_manifest(out_dir, manifest)
            log(f"  {view}: {rec_['video']} {rec_['seconds']}s, {len(events)} events")
    finally:
        try:
            session.run(scene.wall_fill(scene.BACK_WALL_Z, "minecraft:air"),
                        scene.wall_fill(scene.FRONT_WALL_Z, "minecraft:air"))
            press_f5_to(session, "first")
            session.run(tp_command(session.username, scene.PLAYER_POS[2], BODY_YAW, 0.0))
        except Exception as e:  # noqa: BLE001 - best effort
            log(f"  restore failed: {e}")
    return records


def _strip_name(reply: str) -> str:
    m = re.search(r"(\[[^\]]*\])\s*$", reply)
    return m.group(1) if m else reply
