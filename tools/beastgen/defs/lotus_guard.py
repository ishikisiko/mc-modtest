"""Game projection of the approved stone beast. Mesh and pixel-paint ownership stay with author.cjs."""

from __future__ import annotations

import base64
import json
import math
import subprocess
from functools import lru_cache
from pathlib import Path

from .. import anim, cuboid
from ..builder import Builder

ID = "yuanxian:lotus_guard"
ROOT = Path(__file__).resolve().parents[3]
RULES = ROOT / "src/main/resources/data/yuanxian/beast/lotus_guard_boss.json"
SURFACE = "assets/yuanxian/beast/lotus_guard_surface.json"
TILE = 128
GUTTER = 2
LEGS = ("front_left", "front_right", "hind_left", "hind_right")


@lru_cache(maxsize=1)
def source():
    with RULES.open(encoding="utf-8") as stream:
        rules = json.load(stream)
    draw = Path(__file__).with_name("lotus_guard_extract.cjs")
    return rules, json.loads(subprocess.check_output(["node", str(draw)], cwd=ROOT))


def coordinates(p):
    return p[0] * 16, -p[1] * 16, -p[2] * 16


def build_model():
    rules, art = source()
    rig = Builder(ID, look={"bone": "head", "max_yaw": 12.0, "max_pitch": 8.0},
                  shadow_radius=rules["body"]["shadow_radius"])
    for name, parent, pivot in art["bones"]:
        rig.bone(name, parent, at=coordinates(pivot))
    # The shared gait sampler uses contact cubes; the surface replaces them in the renderer.
    for name in LEGS:
        vertices = [face[i:i+3] for bone, _, face in art["faces"] if bone == name for i in (0,5,10)]
        y = min(p[1] for p in vertices)
        soles = [p for p in vertices if p[1] < y + .06]
        x, z = (sum(p[i] for p in soles)/len(soles) for i in (0,2))
        rig.box(name, name + "_contact", (x*16-2,-y*16-2,-z*16-2), (4,2,4))
    return rig.model


# The shared builder's texture is replaced in finalize using the surface's real UV islands.
def paint(_texel):
    return None


glow = paint


def tile_pixels(raw):
    return [tuple(raw[i:i + 4]) for i in range(0, len(raw), 4)]


def build_surface(art):
    pivots = {name: pivot for name, _, pivot in art["bones"]}
    samples = {}
    for _, material, face in art["faces"]:
        samples.setdefault(material, []).extend(zip(face[3::5], face[4::5]))
    uv_extents = {}
    rectangles = []
    for material, uv in samples.items():
        us, vs = zip(*uv)
        u0, v0 = math.floor(min(us)), math.floor(min(vs))
        u1, v1 = math.ceil(max(us)), math.ceil(max(vs))
        uv_extents[material] = u0, v1
        rectangles.append((material, max(1, u1-u0)*TILE + 2*GUTTER, max(1, v1-v0)*TILE + 2*GUTTER))
    rectangles.sort(key=lambda r: (-r[2], -r[1], r[0]))
    candidates = []
    for w in (1024, 2048, 4096):
        packed = cuboid._skyline_pack(rectangles, w)
        if packed:
            offsets, used = packed
            h = 1 << (used-1).bit_length()
            candidates.append((w*h, abs(w-h), w, h, offsets))
    _, _, width, height, offsets = min(candidates)
    if width > 4096 or height > 4096:
        raise ValueError("伏莲兽图集超过 4096 像素")
    tex = [[(0,0,0,0)] * width for _ in range(height)]
    glow = [[(0,0,0,0)] * width for _ in range(height)]
    # Bright markings are limited to the original small eye and fissure triangles.
    decals = {"eye": ((27,45,39,255),(125,180,145,255)),
              "seep": ((43,62,58,255),(54,72,60,255))}
    for material, w, h in rectangles:
        x, y = offsets[material]
        if material in decals:
            base, light = decals[material]
            row, grow = [base]*w, [light]*w
            for yy in range(y, y+h):
                tex[yy][x:x+w] = row
                glow[yy][x:x+w] = grow
            continue
        tile = tile_pixels(base64.b64decode(art["paint"][material]))
        assert len(tile) == TILE*TILE
        indices = [(i-GUTTER)%TILE for i in range(w)]
        for yy in range(h):
            start = (yy-GUTTER)%TILE * TILE
            tex[y+yy][x:x+w] = [tile[start+j] for j in indices]
    parts = {}
    for bone, material, vertices in art["faces"]:
        image_x, image_y = offsets[material]
        u0, v1 = uv_extents[material]
        px,py,pz = pivots[bone]
        triangle = []
        for i in (0,5,10):
            x,y,z,u,v = vertices[i:i+5]
            p = coordinates((x-px,y-py,z-pz))
            U = (image_x+GUTTER+(u-u0)*TILE)/width
            V = (image_y+GUTTER+(v1-v)*TILE)/height
            triangle.extend(round(n,5) for n in (*p,U,V))
        parts.setdefault(bone, []).append(triangle)
    doc = {"schema":1,"id":ID,"parts":[{"bone":bone,"triangles":faces} for bone,faces in parts.items()]}
    return width,height,tex,glow,doc


def finalize(built):
    _, art = source()
    w,h,built.texture,built.glow,surface = build_surface(art)
    built.model.texture_size = (w,h)
    built.model.surface = surface
    built.model_doc = cuboid.model_json(built.model)
    built.model_doc["surface_mesh"] = "yuanxian:beast/lotus_guard_surface.json"


def extra_outputs(built):
    surface = built.model.surface
    rows = [json.dumps(part, separators=(",",":")) for part in surface["parts"]]
    return {ROOT/"src/main/resources"/SURFACE:
            ('{"schema":1,"id":"'+ID+'","parts":[\n'+',\n'.join(rows)+'\n]}\n').encode()}


def rotation(bone, x=0, y=0, z=0):
    return {bone: {"rotation": (x,y,z)}}


def clip(name, ticks, loop, *keys):
    return anim.Clip(name, ticks/20, loop, [anim.Key(tick/20, pose, interp) for tick,pose,interp in keys])


def gait(name, ticks, swing, lift):
    _, art = source()
    joints = {bone: p for bone, _, p in art["bones"]}
    soles = {}
    for name_in_rig in LEGS:
        vertices = [face[i:i+3] for bone, _, face in art["faces"] if bone == name_in_rig for i in (0,5,10)]
        lowest = min(p[1] for p in vertices)
        sols = [v for v in vertices if v[1] - lowest < .30]
        soles[name_in_rig] = [(v[1]-joints[name_in_rig][1],v[2]-joints[name_in_rig][2]) for v in sols]
    keys = []
    for i in range(9):
        pose = {}
        for j,bone in enumerate(LEGS):
            angle = i * math.pi / 4 + (0 if j in (0,3) else math.pi)
            roll = swing * math.cos(angle)
            pitch = math.radians(roll)
            y_joint = joints[bone][1]
            ground = y_joint + min(y*math.cos(pitch) - z*math.sin(pitch) for y,z in soles[bone])
            pose[bone] = {"rotation": (roll, 0, 0),
                          "position": (0, max(0.0, math.sin(angle))*lift - 16*ground, 0)}
        keys.append((i*ticks/8, pose, "catmullrom"))
    return clip(name, ticks, True, *keys)


def grounded_rotation(bone, art):
    joint = next(pivot for name, _, pivot in art["bones"] if name == bone)
    side = 1 if joint[0] > 0 else -1
    points = [(face[i]-joint[0],face[i+1]-joint[1])
              for mesh,_,face in art["faces"] if mesh==bone for i in (0,5,10)]
    low, high = 0, math.pi*.82
    for _ in range(24):
        m = (low+high)/2
        if min(joint[1] + y*math.cos(m) - side*x*math.sin(m) for x,y in points) > .035:
            low = m
        else:
            high = m
    return side * math.degrees(low)


def clips(_model, server):
    rules, art = source()
    linear = "linear"
    natural = [
        clip("idle", 80, True, (0,{},linear), (40,rotation("head", x=-1.0), "catmullrom"), (80,{}, "catmullrom")),
        gait("walk", 36, 7, 4.2), gait("run", 30, 10, 6.0),
        clip("stagger", 8, False, (0,{},linear), (3,rotation("head",x=-8),linear), (8,{},linear)),
        clip("hurt", 10, False, (0,{},linear), (2,rotation("head",x=-6,z=4),linear), (10,{},linear)),
        clip("death", 20, False, (0,{},linear),
             (9,{"body":{"position":(0,-4,0),"rotation":(1,0,-4)}},linear),
             (20,{"body":{"position":(0,-6,0),"rotation":(4,0,-5)}},linear)),
    ]
    for action in server["moves"]:
        name, ticks, impact = action["animation"], action["total_ticks"], action["active_ticks"][0]
        if name == "bite":
            ready = rotation("head", x=-6) | rotation("jaw", x=-16)
            snap = rotation("head", x=4) | rotation("jaw", x=5)
            natural.append(clip(name, ticks, False, (0,{},linear), (impact-3,ready,linear),
                                (impact,snap,linear), (impact+4,snap,linear), (ticks,{},linear)))
            continue
        bone = name.split("_")[0]+"_armor"
        drop = grounded_rotation(bone, art)
        raised = rotation(bone, z=-math.copysign(10, drop))
        open_side = rotation(bone, z=drop)
        close_start = ticks - rules["armor"]["return_ticks"]
        natural.append(clip(name, ticks, False, (0,{},linear),
                            (impact-9,raised,linear), (impact-7,raised,linear),
                            (impact,open_side,linear), (close_start,open_side,linear), (ticks,{},linear)))
    return natural
