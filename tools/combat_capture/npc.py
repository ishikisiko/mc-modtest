"""In-game evidence for a humanoid NPC (``myvillage:cultivator``): stills and walk footage.

Reuses the beast capture's session steps (spectator camera, frozen world, stills,
ffmpeg) for an entity that has no moves and no server data file.

``idle``: full-figure stills from four sides, close-ups of the layered parts
(face, collar, belt, sleeve, hem, back), and a scale still beside the player.
``walk``: video of two NPCs strolling in a barrier pen, front and side cameras.

``look`` picks the outfit (``NpcEntity``'s ``Look`` save tag): ``default`` summons
exactly as before and writes ``ingame/``; any other look adds ``Look:"<look>"`` to
every summon and writes ``ingame_<look>/``.

Developer evidence only; the look and the motion remain the owner's verdict.
"""
from __future__ import annotations

import time
from pathlib import Path

from . import scene
from .beast import TAG, VIEW_TITLES, WOLF_POS, BeastCapture, camera_pose, summon, write_page
from .capture import press_f5_to
from .session import Session

NPC_PARTS = ("idle", "walk")
# CultivatorEntity.LOOKS; the entity ignores an unknown Look tag, so the CLI refuses one
LOOKS = ("default", "f_novice", "f_adept")
DEFAULT_LOOK = "default"
# name, title, view, distance, eye height, aim height (blocks above the feet)
CLOSEUPS = (
    ("face_front", "face, front", "front", 1.0, 1.72, 1.68),
    ("head_q_front", "head, three-quarter front", "q_front", 1.3, 1.85, 1.66),
    ("head_q_back", "head and back hair, three-quarter back", "q_back", 1.6, 1.8, 1.5),
    ("chest", "crossed collar and vest borders", "q_front", 1.5, 1.5, 1.2),
    ("waist", "belt, sash and pendant", "q_front", 1.5, 1.1, 0.8),
    ("sleeve", "sleeve and cuff, side", "side", 1.7, 1.1, 0.95),
    ("hem", "skirt hem and boots", "q_front", 1.6, 0.7, 0.3),
    ("back_panel", "back panel", "q_back", 1.8, 1.0, 0.7),
)


def look_nbt(look: str = DEFAULT_LOOK) -> str:
    """Extra summon NBT for a look; empty for the default so its summons stay as they were."""
    return "" if look == DEFAULT_LOOK else f',Look:"{look}"'


def summon_npc(npc: str, pos, yaw: float, tag: str = TAG, look: str = DEFAULT_LOOK) -> str:
    return summon(npc, pos, yaw, tag=tag, extra=look_nbt(look))


def ingame_dir_name(look: str = DEFAULT_LOOK) -> str:
    """Output folder under out/preview/<npc>/: ``ingame`` for the default look, else ``ingame_<look>``."""
    return "ingame" if look == DEFAULT_LOOK else f"ingame_{look}"


class NpcCapture(BeastCapture):
    def __init__(self, session: Session, npc: str, out: Path, log=print, look: str = DEFAULT_LOOK):
        self.s = session
        self.g = session.game
        self.beast = npc
        self.look = look
        self.data = {}
        self.out = out
        self.log = log
        self.user = session.username
        self.manifest = {"beast": npc, "look": look, "stills": [], "videos": [], "notes": []}
        if look != DEFAULT_LOOK:
            self.manifest["notes"].append(f'look: {look} (summoned with {{Look:"{look}"}})')
        self.hud_hidden = False

    def fresh_beast(self, pos=WOLF_POS, yaw=0.0):
        self.remove(TAG)
        self.run("kill @e[type=minecraft:item]", "kill @e[type=minecraft:experience_orb]")
        self.run(summon_npc(self.beast, pos, yaw, look=self.look))
        self.run("tick step 3")
        time.sleep(0.8)

    def finish(self):
        super().finish()
        if self.look != DEFAULT_LOOK:  # the page title carries the look
            write_page(self.out, {**self.manifest, "beast": f"{self.beast} · {self.look}"})

    def setup_world(self):
        self.s.view = "first"  # the client joins in first person
        self.run(*scene.world_rules(), "difficulty normal", "tick unfreeze", "tick rate 20", "gamerule doMobLoot false")
        self.run(f"op {self.user}", "kill @e[type=!minecraft:player]", f"clear {self.user}",
                 f"effect clear {self.user}", f"experience set {self.user} 0 levels",
                 f"effect give {self.user} minecraft:saturation infinite 255 true",
                 scene.wall_fill(scene.BACK_WALL_Z, "minecraft:air"), scene.wall_fill(scene.FRONT_WALL_Z, "minecraft:air"),
                 "forceload add -48 -48 48 48")
        time.sleep(1.0)

    def stills_idle(self):
        self.log("idle stills")
        self.run("tick freeze", f"gamemode spectator {self.user}")
        press_f5_to(self.s, "first")
        self.fresh_beast()
        self.hide_hud(True)
        target = self.pos()
        for view in ("front", "side", "q_front", "q_back"):
            self.spectate(target, 0.0, view, distance=2.6, eye_up=1.3, aim_up=1.0)
            self.still(f"idle_{view}", f"idle, {VIEW_TITLES[view]}", view=view, pos=target)
        for name, title, view, distance, eye_up, aim_up in CLOSEUPS:
            self.spectate(target, 0.0, view, distance=distance, eye_up=eye_up, aim_up=aim_up)
            self.still(f"close_{name}", f"close-up: {title}", view=view, pos=target)
        # Scale: survival player beside the NPC, F5 front camera looking back at both.
        self.hide_hud(False)
        px, py, pz = WOLF_POS[0] - 1.2, WOLF_POS[1], WOLF_POS[2]
        self.run(f"gamemode survival {self.user}", f"tp {self.user} {px:g} {py:g} {pz:g} 180 8")
        self.fresh_beast(yaw=180.0)
        time.sleep(0.6)
        press_f5_to(self.s, "front")
        self.hide_hud(True)
        self.still("scale_player", "beside the player for scale (F5 front)", pos=self.pos())
        press_f5_to(self.s, "first")
        self.hide_hud(False)

    def walk(self):
        """Two NPCs stroll in a short barrier pen; one take from the front, one from the end."""
        self.log("walk footage")
        y = scene.FLOOR_Y
        self.remove(TAG)
        self.run("tick unfreeze", f"gamemode spectator {self.user}")
        self.bury()
        press_f5_to(self.s, "first")
        self.hide_hud(True)
        # Pen: inside x -4..5, z 7..9.
        self.run(f"fill -5 {y} 6 6 {y + 2} 10 minecraft:barrier", f"fill -4 {y} 7 5 {y + 2} 9 minecraft:air")
        for i, x in enumerate((-2.5, 3.5)):
            self.run(summon_npc(self.beast, (x, y, 8.5), 90.0 if i else -90.0, tag=f"{TAG}_{i}", look=self.look))
        for name, view, distance, title in (("walk_front", "front", 5.5, "strolling in a barrier pen, front camera"),
                                            ("walk_end", "side", 7.5, "the same pen from its end")):
            x, cy, z, yaw, pitch = camera_pose((0.5, y, 8.5), 0.0, view, distance, 1.4, 0.9)
            self.run(f"tp {self.user} {x:.3f} {cy:.3f} {z:.3f} {yaw:.2f} {pitch:.2f}")
            time.sleep(1.0)
            self.record(name, 40.0, f"walk: {title}")
        self.run(*(f"kill @e[tag={TAG}_{i}]" for i in range(2)), f"fill -5 {y} 6 6 {y + 2} 10 minecraft:air")
        self.hide_hud(False)


def run_npc(session: Session, npc: str, out: Path, parts: list[str], log=print,
            look: str = DEFAULT_LOOK) -> dict:
    cap = NpcCapture(session, npc, out, log=log, look=look)
    try:
        cap.setup_world()
        if "idle" in parts:
            cap.stills_idle()
        if "walk" in parts:
            cap.walk()
    finally:
        try:
            if cap.hud_hidden:
                cap.hide_hud(False)
            cap.run("tick unfreeze", warn=False)
            cap.bury()
        finally:
            cap.finish()
    return cap.manifest
