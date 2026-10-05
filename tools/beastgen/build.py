"""Writes a beast's four runtime files (model JSON, animations JSON, texture, glow layer) or checks them.

Standard library only. The PNG encoder is the stdlib one the item generators share
(tools/gen_qingfeng_sword_model.py), so the bytes are deterministic for a given zlib.
"""
from __future__ import annotations

import importlib
import json
from pathlib import Path

from .. import gen_qingfeng_sword_model as pngio
from . import anim, cuboid, paint

REPO = Path(__file__).resolve().parents[2]
RESOURCES = REPO / "src/main/resources"
DEFINITIONS = ("demon_wolf",)


def definition(name):
    if name not in DEFINITIONS:
        raise SystemExit(f"unknown beast {name!r}; known: {', '.join(DEFINITIONS)}")
    return importlib.import_module(f"{__package__}.defs.{name}")


def server_data(name):
    return json.loads((RESOURCES / f"data/myvillage/beast/{name}.json").read_text(encoding="utf-8"))


def paths(name):
    return {
        "model": RESOURCES / f"assets/myvillage/beast/{name}_model.json",
        "animations": RESOURCES / f"assets/myvillage/beast/{name}_animations.json",
        "texture": RESOURCES / f"assets/myvillage/textures/entity/{name}/{name}.png",
        "glow": RESOURCES / f"assets/myvillage/textures/entity/{name}/{name}_eyes.png",
    }


def dumps(obj, indent=0):
    """JSON with numbers and short objects kept on one line (stable, diff-friendly)."""
    flat = json.dumps(obj, ensure_ascii=False, separators=(", ", ": "))
    if not isinstance(obj, (dict, list)) or (len(flat) <= 96 and not _has_container_list(obj)):
        return flat
    pad = "  " * (indent + 1)
    end = "  " * indent
    if isinstance(obj, list):
        if all(not isinstance(v, (dict, list)) for v in obj):
            return flat
        return "[\n" + ",\n".join(pad + dumps(v, indent + 1) for v in obj) + "\n" + end + "]"
    items = [pad + json.dumps(k, ensure_ascii=False) + ": " + dumps(v, indent + 1) for k, v in obj.items()]
    return "{\n" + ",\n".join(items) + "\n" + end + "}"


def _has_container_list(obj):
    vals = obj.values() if isinstance(obj, dict) else obj
    return any(isinstance(v, list) and any(isinstance(x, (dict, list)) for x in v) for v in vals)


class Built:
    """Everything a definition produces, in memory."""

    def __init__(self, name):
        self.name = name
        self.d = definition(name)
        self.server = server_data(name)
        self.model = self.d.build_model()
        problems = self.model.problems()
        if problems:
            raise SystemExit("model problems:\n  " + "\n  ".join(problems))
        cuboid.pack(self.model)
        self.texels = paint.texels(self.model, getattr(self.d, "SKIP_CREASE", ()))
        self.texture = paint.image(self.model, self.d.paint, self.texels)
        self.glow = paint.image(self.model, self.d.glow, self.texels)
        self.clips = self.d.clips(self.model, self.server)
        self.model_doc = cuboid.model_json(self.model)
        self.anim_doc = anim.clips_json(self.model.id, self.clips)

    def outputs(self):
        p = paths(self.name)
        return {
            p["model"]: (dumps(self.model_doc) + "\n").encode("utf-8"),
            p["animations"]: (dumps(self.anim_doc) + "\n").encode("utf-8"),
            p["texture"]: pngio.encode_png(self.texture),
            p["glow"]: pngio.encode_png(self.glow),
        }


def same_file(path, data):
    if not path.is_file():
        return False
    current = path.read_bytes()
    if path.suffix == ".png":
        try:
            return pngio.decode_png_rgba(current) == pngio.decode_png_rgba(data)
        except Exception:  # noqa: BLE001 - an unreadable PNG simply differs
            return False
    return current == data


def run(name, check=False):
    built = Built(name)
    outs = built.outputs()
    if check:
        stale = [p for p, data in outs.items() if not same_file(p, data)]
        for p in stale:
            print(f"STALE {p.relative_to(REPO)}")
        if stale:
            print(f"run: python3 -m tools.beastgen build {name}")
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
          f"{len(built.clips)} clips")
    return 0
