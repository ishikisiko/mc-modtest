"""Writes an NPC's three runtime files (model JSON, animations JSON, texture) or checks them.

Standard library only, like tools/beastgen/build.py, whose JSON layout and PNG encoder it shares.
"""
from __future__ import annotations

import importlib
import importlib.util

from .. import gen_qingfeng_sword_model as pngio
from ..beastgen import anim, cuboid, paint
from ..beastgen.build import REPO, RESOURCES, dumps, same_file

# One definition per look: `defs/<name>.py` declares ENTITY (the entity's texture directory), NAME
# (the files' name prefix) and LOOK (NpcEntity's look id), so several looks of one entity type share
# its texture directory.
DEFINITIONS = ("cultivator", "cultivator_f_novice", "cultivator_f_adept")


def definition(name):
    if name not in DEFINITIONS:
        raise SystemExit(f"unknown npc {name!r}; known: {', '.join(DEFINITIONS)}")
    module = f"{__package__}.defs.{name}"
    if importlib.util.find_spec(module) is None:
        raise SystemExit(f"definition tools/npcgen/defs/{name}.py is not written yet")
    return importlib.import_module(module)


def paths(name):
    d = definition(name)
    return {
        "model": RESOURCES / f"assets/myvillage/npc/{d.NAME}_model.json",
        "animations": RESOURCES / f"assets/myvillage/npc/{d.NAME}_animations.json",
        "texture": RESOURCES / f"assets/myvillage/textures/entity/{d.ENTITY}/{d.NAME}.png",
    }


class Built:
    """Everything a definition produces, in memory."""

    def __init__(self, name):
        self.name = name
        self.d = definition(name)
        self.model = self.d.build_model()
        problems = self.model.problems()
        if problems:
            raise SystemExit("model problems:\n  " + "\n  ".join(problems))
        cuboid.pack(self.model)
        # NPC painters shade from tools/npcgen/shade.py, so the crease distance is skipped for every cube.
        self.texels = paint.texels(self.model, {c.name for _, c in self.model.cubes()})
        self.texture = paint.image(self.model, self.d.painter(self.model), self.texels)
        self.clips = self.d.clips(self.model)
        self.model_doc = cuboid.model_json(self.model)
        self.anim_doc = anim.clips_json(self.model.id, self.clips)

    def outputs(self):
        p = paths(self.name)
        return {
            p["model"]: (dumps(self.model_doc) + "\n").encode("utf-8"),
            p["animations"]: (dumps(self.anim_doc) + "\n").encode("utf-8"),
            p["texture"]: pngio.encode_png(self.texture),
        }


def run(name, check=False):
    built = Built(name)
    outs = built.outputs()
    if check:
        stale = [p for p, data in outs.items() if not same_file(p, data)]
        for p in stale:
            print(f"STALE {p.relative_to(REPO)}")
        if stale:
            print(f"run: python3 -m tools.npcgen build {name}")
            return 1
        print(f"{name}: {len(outs)} files up to date")
        return 0
    for p, data in outs.items():
        p.parent.mkdir(parents=True, exist_ok=True)
        if not same_file(p, data):
            p.write_bytes(data)
        print(f"wrote {p.relative_to(REPO)}")
    w, h = built.model.texture_size
    print(f"{name}: {len(built.model.bones)} bones, {len(built.model.cubes())} cubes, atlas {w}x{h}, "
          f"scale {built.model.scale}, {len(built.clips)} clips")
    return 0
