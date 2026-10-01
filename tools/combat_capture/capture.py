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
from .data import Weapon, fmt_tick, sha256_file
from .session import REPO, SCREEN

MANIFEST = "manifest.json"
MANIFEST_SCHEMA = 1

# view -> (title, probe, F5 position, camera wall z or None, wall-to-eye distance, pitch)
VIEWS = {
    "fp": {"title": "first person", "probe": "first_person", "f5": "first"},
    "tp_back": {"title": "third person, F5 back", "probe": "third_person", "f5": "back"},
    "tp_front": {"title": "third person, F5 front", "probe": "third_person", "f5": "front"},
}
F5_INDEX = {"first": 0, "back": 1, "front": 2}
# Camera framing for F5: the barrier wall stops the camera this far
# (horizontally) from the eye; the eye sits at the screen centre. The back
# view looks down over the shoulder, the front view's camera sits low in front
# looking up, so the blade is not hidden behind the hotbar. The player's pitch
# also tilts the PAL arm pose, so these values are recorded in the manifest and
# must match between two captures that are compared.
TP_BACK_DIST, TP_BACK_PITCH = 2.4, 30.0
TP_FRONT_DIST, TP_FRONT_PITCH = 2.4, 15.0
TICK_SECONDS = 0.05


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
    return {"head": git("rev-parse", "HEAD") or None, "describe": git("log", "-1", "--format=%h %s") or None,
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
                    "third_person_camera": {"back": {"wall_distance": TP_BACK_DIST, "pitch": TP_BACK_PITCH},
                                            "front": {"wall_distance": TP_FRONT_DIST, "pitch": TP_FRONT_PITCH}}},
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


# ====================================================================== stills
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
        n = f5_presses(self.s.view, target)
        for _ in range(n):
            self.g.key("F5")
            self.s.view = {0: "back", 1: "front", 2: "first"}[F5_INDEX[self.s.view]]
            time.sleep(0.35)
        if n:
            time.sleep(0.7)

    def place(self, z: float, pitch: float):
        px, py, _ = scene.PLAYER_POS
        self.s.run(f"tp {self.s.username} {px} {py} {z} 0 {pitch}")
        time.sleep(0.6)

    def set_view(self, view: str):
        f5 = VIEWS[view]["f5"]
        if f5 == "first":
            self.s.run(scene.wall_fill(scene.BACK_WALL_Z, "minecraft:air"),
                       scene.wall_fill(scene.FRONT_WALL_Z, "minecraft:air"))
            self.walls = False
            self.place(scene.PLAYER_POS[2], 0.0)
        elif f5 == "back":
            self.s.run(scene.wall_fill(scene.BACK_WALL_Z, "minecraft:barrier"),
                       scene.wall_fill(scene.FRONT_WALL_Z, "minecraft:air"))
            self.walls = True
            self.place(scene.BACK_WALL_Z + 1 + TP_BACK_DIST, TP_BACK_PITCH)
        else:
            self.s.run(scene.wall_fill(scene.BACK_WALL_Z, "minecraft:air"),
                       scene.wall_fill(scene.FRONT_WALL_Z, "minecraft:barrier"))
            self.walls = True
            self.place(scene.FRONT_WALL_Z - TP_FRONT_DIST, TP_FRONT_PITCH)
        self.press_f5_to(f5)
        time.sleep(0.8)

    def shoot(self, view: str, move, key: str, tick: float):
        probe = VIEWS[view]["probe"]
        n = move.index
        m, line = self.g.chat(f"/myvillage_pal_smoke {probe} {n} {fmt_tick(tick)}",
                              confirm=rf"PAL_SMOKE {probe} move={n} tick=([-\d.Ee]+)")
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
        flag = "" if info["stable"] else f"  NOT STABLE (diff {info.get('diff_fraction')})"
        self.log(f"  {view:<8} m{n} {move.short:<18} {key:<12} t={fmt_tick(tick):<5} grabs={info['grabs']}{flag}")

    def release(self):
        if self.active_probe:
            probe, self.active_probe = self.active_probe, None
            self.g.chat(f"/myvillage_pal_smoke {probe} release")
            time.sleep(0.4)

    def run(self, views: list[str], moves: list[int]):
        self.log(f"stills: {self.weapon.id}, views {views}, moves {moves}")
        scene.base_scene(self.s, self.weapon.item)
        self.press_f5_to("first")
        self.m["combat_mode_before_stills"] = scene.ensure_cultivation(self.s)
        write_manifest(self.out, self.m)
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
def click_schedule(moves) -> tuple[list[dict], int]:
    """Clicks at chain timing through the whole combo. Move 1 is clicked at
    t=0; the click for move n+1 lands midway between move n's buffer start
    and its chain tick (the buffered click is then consumed at the chain tick,
    when move n+1 starts). Returns ([{move, tick, seconds}], expected ticks
    until the last move ends)."""
    clicks = [{"move": moves[0].index, "tick": 0.0, "seconds": 0.0}]
    start = 0.0
    for cur, nxt in zip(moves, moves[1:]):
        t = start + (cur.buffer_start_tick + cur.chain_tick) / 2.0
        clicks.append({"move": nxt.index, "tick": t, "seconds": round(t * TICK_SECONDS, 3)})
        start += cur.chain_tick
    return clicks, int(start + moves[-1].total_ticks)


def ffmpeg_record_argv(display: str, out: Path, seconds: float, fps: int = 30, size=SCREEN) -> list[str]:
    return ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "x11grab", "-draw_mouse", "0",
            "-video_size", f"{size[0]}x{size[1]}", "-framerate", str(fps), "-i", f"{display}.0+0,0",
            "-t", f"{seconds:g}", "-fps_mode", "cfr", "-r", str(fps), "-c:v", "libx264", "-preset", "veryfast",
            "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(out)]


PLAY_LINE = re.compile(r"PAL_SMOKE play .*animation=(\S+) elapsed_ticks=(\S+) accepted=(\w+)(.*)$")


def run_combo(session, weapon: Weapon, out_dir: Path, manifest: dict, targets: str = "dummy",
              lead: float = 2.0, tail: float = 2.5, hold: float = 0.06, log=print) -> dict:
    """Targets in front, mapped left clicks through the whole combo at chain
    timing while ffmpeg records, target health over rcon before and after."""
    from .xgame import LogTail
    g = session.game
    out_dir = Path(out_dir)
    (out_dir / "combo").mkdir(parents=True, exist_ok=True)
    scene.base_scene(session, weapon.item)
    if session.view != "first":
        n = f5_presses(session.view, "first")
        for _ in range(n):
            g.key("F5")
            time.sleep(0.35)
        session.view = "first"
    mode = scene.ensure_cultivation(session)
    placed = scene.place_targets(session, targets)
    time.sleep(1.5)
    before = {t["tag"]: scene.read_health(session, t["uuid"]) for t in placed}
    pos_before = scene.player_pos(session)
    clicks, expected_ticks = click_schedule(weapon.moves)
    seconds = lead + clicks[-1]["seconds"] + expected_ticks * TICK_SECONDS + tail
    video = out_dir / "combo" / "combo.mp4"
    tail_log = LogTail(g.client_log)
    log(f"combo: {len(clicks)} clicks at {[c['seconds'] for c in clicks]} s, recording {seconds:.1f}s")
    rec = subprocess.Popen(ffmpeg_record_argv(session.state["display"], video, seconds), stdin=subprocess.DEVNULL)
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
    if rc != 0 or not video.is_file():
        raise RuntimeError(f"ffmpeg failed (rc {rc})")
    time.sleep(0.5)
    after = {t["tag"]: scene.read_health(session, t["uuid"]) for t in placed}
    pos_after = scene.player_pos(session)
    tail_log.poll()
    plays = []
    for line in tail_log.lines:
        m = PLAY_LINE.search(line)
        if m:
            plays.append({"animation": m.group(1), "elapsed_ticks": m.group(2), "accepted": m.group(3) == "true",
                          "kept_prediction": "kept_prediction=true" in m.group(4)})
    result = {
        "targets": [{"tag": t["tag"], "kind": t["kind"], "pos": t["pos"], "health_before": before[t["tag"]],
                     "health_after": after[t["tag"]],
                     "damage": None if before[t["tag"]] is None or after[t["tag"]] is None
                     else round(before[t["tag"]] - after[t["tag"]], 3)} for t in placed],
        "total_damage": round(sum((before[k] or 0) - (after[k] or 0) for k in before), 3),
        "clicks_planned": clicks,
        "clicks_sent_s": done,
        "expected_combo_ticks": expected_ticks,
        "video": "combo/combo.mp4",
        "video_seconds": round(seconds, 2),
        "lead_seconds": lead,
        "combat_mode": "cultivation" if mode in ("already", "toggled") else mode,
        "player_pos_before": _strip_name(pos_before),
        "player_pos_after": _strip_name(pos_after),
        "client_animation_starts": plays,
        "recorded": now_iso(),
    }
    manifest["combo"] = result
    write_manifest(out_dir, manifest)
    for t in result["targets"]:
        log(f"  {t['tag']} ({t['kind']}) health {t['health_before']} -> {t['health_after']} (damage {t['damage']})")
    # leave a clean field for whatever runs next
    session.run("kill @e[type=!minecraft:player]", warn=False)
    time.sleep(1.0)
    session.run(*scene.cleanup_drops(session.username, weapon.item), warn=False)
    return result


def _strip_name(reply: str) -> str:
    m = re.search(r"(\[[^\]]*\])\s*$", reply)
    return m.group(1) if m else reply
