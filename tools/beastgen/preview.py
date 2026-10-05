"""Offline renders of a beast: rest-pose views, the labelled atlas, contact sheets and GIFs per clip.

Needs numpy and Pillow; the rasteriser is tools/combat_preview's (`item_model.raster_tri`). The beast
is posed exactly as the game poses it: the schema-1 files are read back (cuboid.model_from_json,
anim.clips_from_json), clips are sampled by the KeyframeAnimations port, bones are chained with
translateAndRotate, and LivingEntityRenderer puts the model in the world:
    world = Ry(180 - bodyRot) * S(-1, -1, 1) * T(0, -1.501, 0) * part chain (model px / 16)
with bodyRot 180, so the beast faces world -Z and its left (+X in the model) is world -X. Lighting is
the level's two fixed diffuse lights (0.4 ambient + 0.6 * sum); the glow layer is added unlit on top,
like RenderType.eyes (additive). Previews show one clip at a time over the rest pose (no idle mixed
in, look angles 0).
"""
from __future__ import annotations

import io
import json
import math
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from ..combat_preview import item_model as pim
from ..combat_preview import sheets
from ..combat_preview.env import vanilla_jar
from . import anim, cuboid
from . import quadruped as quad
from .build import REPO, Built, paths, same_file

OUT = REPO / "out/preview"
LIGHT0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
LIGHT1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
TPS = 20.0
# Body yaw the renders are lit for. Entity faces get 0.4 + 0.6 * (both light terms), so a flank is at
# 50 % when the beast faces north or south, 74 % facing east or west, 57 % on the diagonal used here.
DEFAULT_BODY_ROT = 135.0

# camera directions (from the subject toward the camera) in the preview world (beast faces -Z, its left is -X)
CAMS = {
    "front": ((0.0, 0.10, -1.0), "正面"),
    "side": ((-1.0, 0.04, 0.0), "左侧"),
    "q_front": ((-1.0, 0.55, -1.05), "左前 3/4"),
    "q_back": ((1.0, 0.55, 1.05), "右后 3/4"),
    "top": ((0.0, 1.0, 0.0), "俯视"),
}


# ------------------------------------------------------------------------------------ scene
class Mesh:
    """A cuboid model with its textures, ready to pose."""

    def __init__(self, model, tex, glow=None, scale=1.0, offset=(0.0, 0.0, 0.0)):
        self.model = model
        self.tex = tex
        self.glow = glow
        self.scale = scale
        self.offset = np.asarray(offset, float)
        tw, th = model.texture_size
        self.polys = {}
        for b in model.bones:
            ps = []
            for c in b.cubes:
                for _, verts in cuboid.cube_polygons(c, tw, th):
                    ps.append((np.array([v[:3] for v in verts], float), [(v[3] * 16.0, v[4] * 16.0) for v in verts]))
            self.polys[b.name] = ps

    def world(self, mats, lift=0.0, travel=0.0):
        """[(world 4x3 points, uv list, tex, glow)] for the matrices from anim.posed_matrices; lift raises
        and travel carries forward (-Z) the whole entity (blocks), as the server moves it in a lunge."""
        out = []
        s = self.scale
        off = self.offset + np.array([0.0, lift, -travel])
        for name, ps in self.polys.items():
            m = np.array(mats[name], float)
            for pts, uv in ps:
                P = pts @ m[:3, :3].T + m[:3, 3]
                W = np.c_[-P[:, 0] / 16.0 * s, (1.501 - P[:, 1] / 16.0) * s, P[:, 2] / 16.0 * s] + off
                out.append((W, uv, self.tex, self.glow))
        return out


def rows_to_array(rows):
    return np.asarray(rows, np.uint8).astype(np.float32) / 255.0


class Frame:
    """Orthographic window: camera, pixels per block, centre (world), size."""

    def __init__(self, cdir, W, H, k, center, ss=3):
        self.cam = pim.Camera(cdir)
        self.W, self.H, self.k, self.ss = W, H, k, ss
        self.center = self.cam.project(np.asarray(center, float))[0, :2]

    def screen(self, P, ss=None):
        ss = self.ss if ss is None else ss
        pr = self.cam.project(np.atleast_2d(P))
        x = (pr[:, 0] - self.center[0]) * self.k * ss + self.W * ss / 2
        y = self.H * ss / 2 - (pr[:, 1] - self.center[1]) * self.k * ss
        return np.c_[x, y, pr[:, 2]]


def lights_for(body_rot):
    """The level's two fixed diffuse lights expressed in the preview frame for an entity whose body yaw
    is body_rot (the preview frame is the world at body_rot 180): world = Ry(180 - body_rot) * preview."""
    a = math.radians(body_rot - 180.0)
    c, s = math.cos(a), math.sin(a)
    R = np.array([[c, 0.0, s], [0.0, 1.0, 0.0], [-s, 0.0, c]])
    return R @ LIGHT0, R @ LIGHT1


def render(quads, fr, bg=(206, 210, 216), ground=True, body_rot=DEFAULT_BODY_ROT, light=1.0):
    """light scales the lit result like a dim lightmap; the glow layer stays full bright."""
    ss = fr.ss
    l0, l1 = lights_for(body_rot)
    W, H = fr.W * ss, fr.H * ss
    img = np.empty((H, W, 3), np.float32)
    grad = np.linspace(1.0, 0.88, H, dtype=np.float32)[:, None, None]
    img[:] = np.array(bg, np.float32)[None, None, :] / 255.0 * grad
    if ground:
        _ground(img, fr)
    zb = np.full((H, W), -np.inf, np.float32)
    glow_jobs = []
    for P, uv, tex, glow in quads:
        n = np.cross(P[1] - P[0], P[2] - P[0])
        nn = np.linalg.norm(n)
        if nn < 1e-12:
            continue
        n /= nn
        if n @ fr.cam.c <= 1e-9:
            continue
        sh = min(1.0, 0.4 + 0.6 * (max(0.0, n @ l0) + max(0.0, n @ l1))) * light
        S = fr.screen(P)
        for tri in ((0, 1, 2), (0, 2, 3)):
            pim.raster_tri(img, zb, S[list(tri)], [uv[i] for i in tri], tex, None, sh)
        if glow is not None:
            glow_jobs.append((S, uv, glow))
    if glow_jobs:  # additive, depth-tested against the finished base (RenderType.eyes)
        add = np.zeros_like(img)
        for S, uv, glow in glow_jobs:
            for tri in ((0, 1, 2), (0, 2, 3)):
                _raster_add(add, zb, S[list(tri)], [uv[i] for i in tri], glow)
        img = np.minimum(img + add, 1.0)
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    return im.resize((fr.W, fr.H), Image.BOX)


def _ground(img, fr):
    """Shade the ground plane (y = 0) slightly so feet read against it in tilted views."""
    H, W = img.shape[:2]
    c = fr.cam
    if abs(c.c[1]) < 0.2:
        return
    ys, xs = np.mgrid[0:H, 0:W]
    sx = (xs + 0.5 - W / 2) / (fr.k * fr.ss) + fr.center[0]
    sy = (H / 2 - ys - 0.5) / (fr.k * fr.ss) + fr.center[1]
    # point = r*sx + u*sy + c*t, solve y = 0 for t
    t = -(c.r[1] * sx + c.u[1] * sy) / c.c[1]
    px = c.r[0] * sx + c.u[0] * sy + c.c[0] * t
    pz = c.r[2] * sx + c.u[2] * sy + c.c[2] * t
    chk = ((np.floor(px) + np.floor(pz)) % 2 == 0)
    img[chk] *= 0.94


def _raster_add(add, zb, S, uv, tex):
    H, W = zb.shape
    x0 = max(int(math.floor(S[:, 0].min())), 0)
    x1 = min(int(math.ceil(S[:, 0].max())), W - 1)
    y0 = max(int(math.floor(S[:, 1].min())), 0)
    y1 = min(int(math.ceil(S[:, 1].max())), H - 1)
    if x0 > x1 or y0 > y1:
        return
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = S
    area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
    if abs(area) < 1e-12:
        return
    X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    w0 = ((bx - X) * (cy - Y) - (by - Y) * (cx - X)) / area
    w1 = ((cx - X) * (ay - Y) - (cy - Y) * (ax - X)) / area
    w2 = 1.0 - w0 - w1
    inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
    Z = w0 * az + w1 * bz + w2 * cz
    m = inside & (Z >= zb[y0:y1 + 1, x0:x1 + 1] - 1e-4)
    if not m.any():
        return
    U = w0 * uv[0][0] + w1 * uv[1][0] + w2 * uv[2][0]
    V = w0 * uv[0][1] + w1 * uv[1][1] + w2 * uv[2][1]
    th, tw = tex.shape[:2]
    tx = np.clip(np.floor(U / 16.0 * tw).astype(int), 0, tw - 1)
    ty = np.clip(np.floor(V / 16.0 * th).astype(int), 0, th - 1)
    texel = tex[ty, tx]
    m &= texel[..., 3] > 0.0
    sub = add[y0:y1 + 1, x0:x1 + 1]
    sub[m] += texel[..., :3][m] * texel[..., 3:4][m]


# ------------------------------------------------------------------------------------ overlays
def box_edges(lo, hi):
    xs, ys, zs = (lo[0], hi[0]), (lo[1], hi[1]), (lo[2], hi[2])
    c = [(x, y, z) for x in xs for y in ys for z in zs]
    edges = []
    for i in range(8):
        for j in range(i + 1, 8):
            if sum(a != b for a, b in zip(c[i], c[j])) == 1:
                edges.append((c[i], c[j]))
    return edges


def draw_box(im, fr, lo, hi, color, width=1, dash=0):
    d = ImageDraw.Draw(im)
    for a, b in box_edges(lo, hi):
        S = fr.screen(np.array([a, b], float), ss=1)
        if dash:
            n = max(2, int(np.hypot(*(S[1, :2] - S[0, :2])) / dash))
            for i in range(0, n, 2):
                p = S[0, :2] + (S[1, :2] - S[0, :2]) * i / n
                q = S[0, :2] + (S[1, :2] - S[0, :2]) * min(i + 1, n) / n
                d.line((p[0], p[1], q[0], q[1]), fill=color, width=width)
        else:
            d.line((S[0, 0], S[0, 1], S[1, 0], S[1, 1]), fill=color, width=width)


def draw_ground_line(im, fr, color=(60, 80, 50)):
    d = ImageDraw.Draw(im)
    S = fr.screen(np.array([[0.0, 0.0, -6.0], [0.0, 0.0, 6.0]]), ss=1)
    if abs(S[0, 1] - S[1, 1]) < 1.0:
        d.line((0, S[0, 1], im.width, S[0, 1]), fill=color, width=1)


# ------------------------------------------------------------------------------------ player and vanilla wolf
PLAYER_PARTS = [  # PlayerModel (wide arms): bone, pivot, [(uv, origin, size)]
    ("head", (0, 0, 0), [((0, 0), (-4, -8, -4), (8, 8, 8))]),
    ("body", (0, 0, 0), [((16, 16), (-4, 0, -2), (8, 12, 4))]),
    ("right_arm", (-5, 2, 0), [((40, 16), (-3, -2, -2), (4, 12, 4))]),
    ("left_arm", (5, 2, 0), [((32, 48), (-1, -2, -2), (4, 12, 4))]),
    ("right_leg", (-1.9, 12, 0), [((0, 16), (-2, 0, -2), (4, 12, 4))]),
    ("left_leg", (1.9, 12, 0), [((16, 48), (-2, 0, -2), (4, 12, 4))]),
]
WOLF_PARTS = [  # vanilla WolfModel (tame-less pose at rest): bone, parent, pivot, rot(rad), [(uv, origin, size)]
    ("head", None, (-1, 13.5, -7), (0, 0, 0), [((0, 0), (-2, -3, -2), (6, 6, 4)), ((16, 14), (-2, -5, 0), (2, 2, 1)),
                                               ((16, 14), (2, -5, 0), (2, 2, 1)), ((0, 10), (-0.5, -0.001, -5), (3, 3, 4))]),
    ("body", None, (0, 14, 2), (math.pi / 2, 0, 0), [((18, 14), (-3, -2, -3), (6, 9, 6))]),
    ("upper_body", None, (-1, 14, -3), (math.pi / 2, 0, 0), [((21, 0), (-3, -3, -3), (8, 6, 7))]),
    ("right_hind_leg", None, (-2.5, 16, 7), (0, 0, 0), [((0, 18), (0, 0, -1), (2, 8, 2))]),
    ("left_hind_leg", None, (0.5, 16, 7), (0, 0, 0), [((0, 18), (0, 0, -1), (2, 8, 2))]),
    ("right_front_leg", None, (-2.5, 16, -4), (0, 0, 0), [((0, 18), (0, 0, -1), (2, 8, 2))]),
    ("left_front_leg", None, (0.5, 16, -4), (0, 0, 0), [((0, 18), (0, 0, -1), (2, 8, 2))]),
    ("tail", None, (-1, 12, 8), (math.pi / 5, 0, 0), [((9, 18), (0, 0, -1), (2, 8, 2))]),
]


def _jar_texture(rel):
    jar = vanilla_jar()
    try:
        with zipfile.ZipFile(jar) as z:
            return np.asarray(Image.open(io.BytesIO(z.read(rel))).convert("RGBA"), np.float32) / 255.0
    except (OSError, KeyError):
        return None


def _simple_model(parts, tw, th, with_parent=False):
    bones = []
    for p in parts:
        if with_parent:
            name, parent, pivot, rot, cubes = p
        else:
            (name, pivot, cubes), parent, rot = p, None, (0, 0, 0)
        cs = [cuboid.Cube(f"{name}{i}", o, s, uv=uv) for i, (uv, o, s) in enumerate(cubes)]
        bones.append(cuboid.Bone(name, parent, pivot, tuple(math.degrees(r) for r in rot), cs))
    return cuboid.Model("ref", bones, None, 0.5, (tw, th))


def player_mesh(offset):
    tex = _jar_texture("assets/minecraft/textures/entity/player/wide/steve.png")
    if tex is None:
        tex = np.zeros((64, 64, 4), np.float32)
        tex[...] = (0.4, 0.55, 0.75, 1.0)
    m = _simple_model(PLAYER_PARTS, 64, 64)
    return Mesh(m, tex, scale=0.9375, offset=offset)


def vanilla_wolf_mesh(offset):
    tex = _jar_texture("assets/minecraft/textures/entity/wolf/wolf.png")
    if tex is None:
        return None
    m = _simple_model(WOLF_PARTS, 64, 32, with_parent=True)
    return Mesh(m, tex, offset=offset)


# ------------------------------------------------------------------------------------ beast posing
class Beast:
    def __init__(self, name):
        self.name = name
        self.built = Built(name)
        stale = [p for p, data in self.built.outputs().items() if not same_file(p, data)]
        self.stale = [str(p.relative_to(REPO)) for p in stale]
        self.model = cuboid.model_from_json(self.built.model_doc)
        self.clips = anim.clips_from_json(json.loads(json.dumps(self.built.anim_doc)))
        self.clip_objs = {c.name: c for c in self.built.clips}
        self.mesh = Mesh(self.model, rows_to_array(self.built.texture), rows_to_array(self.built.glow))
        self.server = self.built.server
        self._flights = {}

    def mats(self, clip=None, seconds=0.0):
        offs = None
        if clip:
            length, loop, chans = self.clips[clip]
            offs = anim.clip_offsets(chans, length, loop, seconds)
        return anim.posed_matrices(self.model, offs)

    def quads(self, clip=None, seconds=0.0):
        fwd, lift = self.place(clip, seconds)
        return self.mesh.world(self.mats(clip, seconds), lift, fwd)

    def flight(self, clip):
        """The server's lunge path for a move clip (quadruped.move_flight: aim at the middle of use_range),
        or None for a clip no move uses."""
        mv = self.move(clip) if clip else None
        if not mv:
            return None
        if clip not in self._flights:
            self._flights[clip] = quad.move_flight(mv)
        return self._flights[clip]

    def place(self, clip, seconds, client=False):
        """(forward travel, height) of the entity's feet in blocks at a clip time: the server's lunge path
        under vanilla motion (the clip itself carries posture only). client=True gives where the game
        would draw it with vanilla's lerp and clip clock instead (client_track; BeastEntity fixes both)."""
        f = self.flight(clip)
        if f is None:
            return 0.0, 0.0
        if client:
            return client_track(f, self.move(clip)["total_ticks"], seconds * TPS)
        return f.at(seconds * TPS)

    def lift(self, clip, seconds):
        return self.place(clip, seconds)[1]

    def landing(self, clip):
        mv = self.move(clip)
        if not mv or mv["lunge"]["up"] <= 0:
            return None
        return self.flight(clip).landing

    def feet_min_y(self, mats):
        """Lowest world y (blocks) of each paw cube's corners."""
        out = {}
        for b in self.model.bones:
            if not b.name.endswith("_paw"):
                continue
            m = np.array(mats[b.name], float)
            ys = []
            for c in b.cubes:
                lo, sz = np.array(c.origin), np.array(c.size)
                for i in range(8):
                    p = lo + sz * np.array([(i >> 0) & 1, (i >> 1) & 1, (i >> 2) & 1])
                    q = m[:3, :3] @ p + m[:3, 3]
                    ys.append(1.501 - q[1] / 16.0)
            out[b.name] = min(ys)
        return out

    def move(self, clip):
        for mv in self.server["moves"]:
            if mv["animation"] == clip:
                return mv
        return None


# Where the vanilla client would draw a lunging beast, kept as the reference for BeastEntity's fix (it now
# applies position packets in one step during a move and samples move clips on the drawn tick, so the
# game matches the top, server-aligned row of the landing sheet). Modelled from vanilla code:
# the server sends the position every tick of a move (the synced move tick makes the entity data dirty),
# ClientPacketListener hands it to LivingEntity.lerpTo with 3 steps, so each client tick closes a third
# of the gap to the newest server position, and the renderer interpolates between the last two client
# ticks. The move clip is anchored to the synced move tick before the client tick increments its clock,
# so while the client draws server tick N-1 -> N the clip plays N+1 -> N+2: the pose runs two ticks
# ahead of the drawn position.
CLIENT_LERP_STEPS = 3
CLIENT_CLIP_LEAD = 2
MIN_SENT_MOVE = math.sqrt(7.6293945e-6)  # ServerEntity sends a position once it moved this far


def client_track(flight, total_ticks, clip_tick, steps=CLIENT_LERP_STEPS, lead=CLIENT_CLIP_LEAD):
    """(forward, height) the client draws at clip time clip_tick: server ticks pass through the lerp
    (a new target every tick the server moved enough, resetting the step count) and the clip leads."""
    server_tick = clip_tick - lead
    n_last = math.floor(server_tick) + 1
    pos = np.zeros(2)
    hist = {}
    sent = np.zeros(2)
    left = 0
    target = np.zeros(2)
    for n in range(0, max(n_last, 0) + 1):
        p = np.array(flight.at(n)) if n <= total_ticks else np.array(flight.at(total_ticks))
        if np.linalg.norm(p - sent) >= MIN_SENT_MOVE:
            target, sent, left = p, p, steps
        if left > 0:
            pos = pos + (target - pos) / left
            left -= 1
        hist[n] = pos.copy()
    a = hist.get(n_last - 1, np.zeros(2))
    b = hist.get(n_last, a)
    f = server_tick - math.floor(server_tick)
    return tuple(float(x) for x in a + (b - a) * f)


def hit_box(move):
    h = move["hit"]
    return ((-h["half_width"], h["height"][0], -h["forward"][1]), (h["half_width"], h["height"][1], -h["forward"][0]))


# ------------------------------------------------------------------------------------ sheets
def model_views(beast, out, size):
    hb_w, hb_h = beast.built.d.HITBOX
    lo, hi = (-hb_w / 2, 0.0, -hb_w / 2), (hb_w / 2, hb_h, hb_w / 2)
    cells = []
    W, H = size, int(size * 0.72)
    for key in ("side", "front", "q_front", "q_back", "top"):
        cdir, title = CAMS[key]
        cam = pim.Camera(cdir)
        side_off = cam.r * 2.0
        side_off[1] = 0.0
        player = player_mesh(offset=tuple(side_off))
        quads = beast.quads() + player.world(anim.posed_matrices(player.model))
        k = W / 5.6 if key != "top" else W / 5.0
        center = side_off * 0.45 + np.array([0.0, 0.95, 0.0])
        if key == "top":
            center = side_off * 0.45 + np.array([0.0, 0.0, 0.2])
        fr = Frame(cdir, W, H, k, center)
        im = render(quads, fr)
        draw_box(im, fr, lo, hi, (200, 40, 40), width=1, dash=5)
        if key in ("side", "front"):
            draw_ground_line(im, fr)
        cells.append(sheets.labelled(im, [f"{title} · 静止姿态", f"红虚线 = 碰撞箱 {hb_w} x {hb_h} 格；人物 = 玩家（真实比例）"], W))
    # vanilla wolf next to it, same scale, for the silhouette comparison
    vw = vanilla_wolf_mesh(offset=(0.0, 0.0, 0.0))
    cdir = CAMS["side"][0]
    fr = Frame(cdir, W, H, W / 5.6, (0.0, 0.95, 1.1))
    quads = beast.quads()
    if vw is not None:
        quads += Mesh(vw.model, vw.tex, offset=(0.0, 0.0, 2.7)).world(anim.posed_matrices(vw.model))
    im = render(quads, fr)
    draw_ground_line(im, fr)
    cells.append(sheets.labelled(im, ["左侧 · 对照：原版狼（右，同比例）", "检查剪影：深胸、收腰、长腿、楔形长头、背脊鬃片"], W))
    rows = [cells[0:3], cells[3:6]]
    sheet = sheets.grid(rows, [f"{beast.name} · 模型视图", _stamp(beast)])
    sheet.save(out / "model_views.png")
    # big side view for the thumbnail-silhouette check
    _side_large(beast).save(out / "side_large.png")
    q = Frame(CAMS["q_front"][0], 900, 620, 900 / 3.6, (0.0, 0.8, -0.3))
    render(beast.quads(), q).save(out / "q_front_large.png")
    hd = Frame((-0.8, 0.25, -1.0), 640, 480, 640 / 1.2, (0.0, 1.15, -1.05))
    render(beast.quads(), hd).save(out / "head_closeup.png")


def texture_atlas(beast, out, scale=10):
    tw, th = beast.model.texture_size
    tex = Image.fromarray(np.asarray(beast.built.texture, np.uint8), "RGBA")
    bg = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    for y in range(th):  # checker under transparent texels
        for x in range(tw):
            bg.putpixel((x, y), (60, 60, 66, 255) if (x + y) % 2 else (44, 44, 50, 255))
    base = Image.alpha_composite(bg, tex).resize((tw * scale, th * scale), Image.NEAREST).convert("RGB")
    glow = Image.fromarray(np.asarray(beast.built.glow, np.uint8), "RGBA")
    gbase = Image.alpha_composite(Image.new("RGBA", (tw, th), (0, 0, 0, 255)), glow).resize(
        (tw * scale // 2, th * scale // 2), Image.NEAREST).convert("RGB")
    d = ImageDraw.Draw(base)
    fs = sheets.font(10)
    fn = sheets.font(13)
    users = {}
    for b, c in beast.built.model.cubes():
        users.setdefault(c.uv_from or c.name, []).append(c.name)
    legend = []
    owned = [(b, c) for b, c in beast.built.model.cubes() if not c.uv_from]
    for i, (b, c) in enumerate(owned, 1):
        for direction, (u, v, w, h) in cuboid.uv_regions(c).items():
            d.rectangle((u * scale, v * scale, (u + w) * scale - 1, (v + h) * scale - 1), outline=(250, 200, 90))
            if w >= 2 and h >= 2:
                d.text((u * scale + 2, v * scale + 1), cuboid.VISUAL[direction][:2], fill=(255, 255, 255), font=fs)
        u, v = c.uv
        d.text((u * scale + 2, v * scale + 1), str(i), fill=(140, 230, 255), font=fn)  # the unused corner
        twins = [n for n in users[c.name] if n != c.name]
        legend.append(f"{i:2d}  {b.name} / {c.name}" + (f"  (镜像共用: {', '.join(twins)})" if twins else ""))
    top = sheets.labelled(base, [f"贴图 {tw}x{th}（放大 {scale} 倍）",
                                 "编号见右侧；面标签 to=顶 bo=底 fr=前 ba=后 le=左 ri=右（以狼自身为准，左 = 模型 +X）"], base.width)
    lh = 17
    leg = Image.new("RGB", (430, max(top.height, lh * len(legend) + 40)), sheets.BG)
    dl = ImageDraw.Draw(leg)
    dl.text((8, 6), "岛编号 = 骨骼 / 立方体", fill=sheets.ACCENT, font=sheets.font(15))
    for k, line in enumerate(legend):
        dl.text((8, 30 + k * lh), line, fill=sheets.FG, font=sheets.font(12))
    bot = sheets.labelled(gbase, ["发光层 demon_wolf_eyes.png（只有眼睛，放大 5 倍）",
                                  "游戏中建议按 RenderType.eyes 叠加（加色、无光照）"], gbase.width)
    sheet = sheets.grid([[top, leg], [bot]], [f"{beast.name} · 贴图", _stamp(beast)])
    sheet.save(out / "texture_atlas.png")


PHASE_NAMES = {"start": "起始", "turn_lock": "锁定朝向", "lunge": "扑出", "active_first": "命中首帧",
               "active_last": "命中末帧", "recovery": "收招中", "end": "末帧", "landing": "落地（服务端）",
               "apex": "最高点"}


def move_ticks(move):
    a0, a1 = move["active_ticks"]
    total = move["total_ticks"]
    return [(0, "start"), (move["turn_lock_tick"], "turn_lock"), (move["lunge"]["tick"], "lunge"),
            (a0, "active_first"), (a1, "active_last"), ((a1 + total) // 2, "recovery"), (total - 1, "end")]


def _frames_for(beast, clip):
    """[(seconds, title, sub, hit box or None)]"""
    mv = beast.move(clip)
    length, loop, _ = beast.clips[clip]
    if mv:
        out = []
        a0, a1 = mv["active_ticks"]
        ticks = move_ticks(mv)
        land = beast.landing(clip)
        if land is not None:
            ticks += [(beast.flight(clip).apex_tick, "apex"), (land, "landing")]
            ticks = sorted(dict((t, p) for t, p in ticks).items())
        for t, ph in ticks:
            box = hit_box(mv) if a0 <= t <= a1 else None
            out.append((t / TPS, f"tick {t} · {PHASE_NAMES[ph]}", ph, box))
        return out
    n = 8 if clip in ("walk", "run") else 6
    if clip == "stagger":
        return [(t / TPS, f"tick {t}", "", None) for t in range(0, int(round(length * TPS)) + 1, 2)]
    return [(length * i / n, f"{i}/{n} 周期 · {length * i / n:.3f}s", "", None) for i in range(n)]


def _clip_frame(key, W, H, move, ss=3, jump=0.0, follow=0.0):
    """Side and 3/4-front windows; move clips are framed wider and further forward to hold the hit box,
    and taller by the jump's apex so the arc stays in frame. follow: the entity's forward travel
    (blocks), so the window moves with it."""
    grow = 1.0 + 0.45 * jump
    up = 0.5 * jump
    if key == "side":
        return Frame(CAMS[key][0], W, H, W / ((5.0 if move else 4.2) * grow),
                     (0.0, 0.95 + up, (-0.85 if move else -0.35) - follow), ss)
    return Frame(CAMS[key][0], W, H, W / ((4.4 if move else 3.6) * grow),
                 (0.0, 0.75 + up, (-0.9 if move else -0.45) - follow), ss)


def _apex(beast, clip):
    f = beast.flight(clip)
    return f.apex if f else 0.0


def _shift(box, fwd, lift):
    (x0, y0, z0), (x1, y1, z1) = box
    return (x0, y0 + lift, z0 - fwd), (x1, y1 + lift, z1 - fwd)


def draw_ground_ticks(im, fr, z0, z1, step=0.5, color=(60, 80, 50)):
    """Marks along the ground every `step` blocks of world Z (taller on whole blocks), so travel reads
    in a window that follows the beast."""
    d = ImageDraw.Draw(im)
    z = math.floor(min(z0, z1) / step) * step
    while z <= max(z0, z1):
        S = fr.screen(np.array([[0.0, 0.0, z]]), ss=1)[0]
        whole = abs(z - round(z)) < 1e-6
        d.line((S[0], S[1], S[0], S[1] + (7 if whole else 4)), fill=color, width=2 if whole else 1)
        z += step


def draw_path(im, fr, beast, clip, upto=None, color=(214, 120, 40)):
    """The server's path of the entity's feet (end of every tick from the lunge to rest), dotted, with
    the part already travelled at clip tick `upto` drawn solid."""
    f = beast.flight(clip)
    if f is None or (f.up <= 0 and f.forward_speed <= 0):
        return
    d = ImageDraw.Draw(im)
    ticks = [f.lunge_tick - 1] + sorted(f.positions)
    pts = [fr.screen(np.array([[0.0, f.at(t)[1], -f.at(t)[0]]]), ss=1)[0] for t in ticks]
    for t, p, q in zip(ticks, pts, pts[1:]):
        done = upto is not None and t + 1 <= upto
        d.line((p[0], p[1], q[0], q[1]), fill=color if done else (150, 150, 160), width=2 if done else 1)
    for t, p in zip(ticks, pts):
        if f.lunge_tick <= t <= f.landing:
            d.ellipse((p[0] - 2, p[1] - 2, p[0] + 2, p[1] + 2), outline=color)


def _feet_sub(beast, mats, fwd, lift):
    feet = beast.feet_min_y(mats)
    return min(feet.values()) + lift, max(feet.values()) + lift


def contact_sheet(beast, clip, out, size):
    frames = _frames_for(beast, clip)
    mv = beast.move(clip)
    rows = []
    views = ("side", "q_front")
    W = size
    H = int(size * 0.66)
    for key in views:
        cdir, vt = CAMS[key]
        row = []
        for sec, title, ph, box in frames:
            mats = beast.mats(clip, sec)
            fwd, lift = beast.place(clip, sec)
            quads = beast.mesh.world(mats, lift, fwd)
            fr = _clip_frame(key, W, H, bool(mv), jump=_apex(beast, clip), follow=fwd)
            im = render(quads, fr)
            if box:
                lo, hi = _shift(box, fwd, lift)
                draw_box(im, fr, lo, hi, (230, 30, 30), width=2)
            if key == "side":
                draw_ground_line(im, fr)
                if mv:
                    draw_ground_ticks(im, fr, 3.0 - fwd, -4.0 - fwd)
                    draw_path(im, fr, beast, clip, sec * TPS)
            low, high = _feet_sub(beast, mats, fwd, lift)
            sub = f"{vt} · 足底最低 {low:+.3f} 格 · 最高 {high:+.3f}"
            if lift > 0:
                sub += f" · 离地 {lift:.2f}"
            if fwd > 0:
                sub += f" · 前移 {fwd:.2f}"
            if box:
                sub += " · 红框 = 服务端命中盒"
            row.append(sheets.labelled(im, [title, sub], W))
        rows.append(row)
    length, loop, chans = beast.clips[clip]
    info = f"长度 {length}s · {'循环' if loop else '单次'} · {len(chans)} 通道"
    if mv:
        info += " · " + _flight_info(beast, clip)
        info += (f" · total {mv['total_ticks']} · turn_lock {mv['turn_lock_tick']} · lunge {mv['lunge']['tick']} · "
                 f"active {mv['active_ticks']} · 命中盒 forward {mv['hit']['forward']} half_width {mv['hit']['half_width']} "
                 f"height {mv['hit']['height']}（以脚底为原点）")
    sheet = sheets.grid(rows, [f"{beast.name} · {clip}", info, _stamp(beast)])
    sheet.save(out / f"clip_{clip}.png")
    return sheet


def _flight_info(beast, clip):
    f = beast.flight(clip)
    mv = beast.move(clip)
    s = (f"实体按服务端路径移动（BeastMotion：原版重力 0.08、竖直阻尼 0.98、地面 0.546 / 空中 0.91 水平保留；"
         f"瞄准 use_range 中点 {sum(mv['use_range']) / 2:g} 格 → 水平初速 {f.forward_speed:.2f} 格/tick，"
         f"停在 {f.rest_distance:.2f} 格")
    if f.up > 0:
        s += f"；最高 {f.apex:.3f} 格 @ tick {f.apex_tick}，平地落地 tick {f.landing}"
    return s + "）；橙线 = 服务端脚底轨迹，地面刻度每 0.5 格"


def arc_sheet(beast, clip, out, W=1500, H=520):
    """Strobe of the lunge in one fixed side window: the beast drawn at every tick from the coil to two
    ticks after touch-down on the server's path, ghosted, with the coil, apex and landing solid."""
    f = beast.flight(clip)
    land = f.landing
    span_back, span_front = 1.7, f.rest_distance + 1.9
    k = W / (span_back + span_front + 0.4)
    centre = (0.0, 0.75 + 0.35 * f.apex, (span_back - span_front) / 2.0)
    fr = Frame(CAMS["side"][0], W, H, k, centre, ss=2)
    bg = np.asarray(render([], fr), np.float32)
    acc = bg.copy()
    solid = {f.lunge_tick - 1: "蓄力", f.apex_tick: "最高点", land: "落地"}
    ticks = list(range(f.lunge_tick - 1, land + 3))
    ticks = [t for t in ticks if t not in solid] + [t for t in ticks if t in solid]  # solid ones on top
    labels = []
    for t in ticks:
        sec = t / TPS
        fwd, lift = beast.place(clip, sec)
        im = np.asarray(render(beast.mesh.world(beast.mats(clip, sec), lift, fwd), fr), np.float32)
        mask = (np.abs(im - bg).max(axis=2) > 6.0)[..., None]
        alpha = 1.0 if t in solid else 0.2
        acc = np.where(mask, acc * (1 - alpha) + im * alpha, acc)
        labels.append((t, fwd, lift))
    out_im = Image.fromarray(acc.clip(0, 255).astype(np.uint8))
    draw_ground_line(out_im, fr)
    draw_ground_ticks(out_im, fr, span_back, -span_front)
    draw_path(out_im, fr, beast, clip, land + 1)
    d = ImageDraw.Draw(out_im)
    for t, fwd, lift in labels:
        p = fr.screen(np.array([[0.0, lift + 1.75, -fwd]]), ss=1)[0]
        txt = f"{t}" + (f" {solid[t]}" if t in solid else "")
        d.text((p[0] - 6, p[1] - (14 if t % 2 else 0)), txt, fill=(30, 30, 30) if t in solid else (90, 90, 100),
               font=sheets.font(12))
    mv = beast.move(clip)
    sheets.grid([[out_im]], [f"{beast.name} · {clip} · 服务端弧线（每 tick 一帧，实心 = 蓄力 / 最高点 / 落地）",
                             _flight_info(beast, clip), _stamp(beast)]).save(out / f"clip_{clip}_arc.png")
    return f"clip_{clip}_arc.png"


def landing_sheet(beast, clip, out, W=300, H=300):
    """Close side views of the touch-down, tick by tick: the top row as the clip is authored (pose and
    server position on the same tick, as the game now draws it), the bottom row as the vanilla client drew it
    before BeastEntity's fix (client_track)."""
    f = beast.flight(clip)
    mv = beast.move(clip)
    land = f.landing
    rec = (mv["active_ticks"][1] + mv["total_ticks"]) // 2
    ticks = [land - 3, land - 2, land - 1, land - 0.5, land, land + 1, land + 2, land + 4, rec]
    rows = []
    for client in (False, True):
        row = []
        for t in ticks:
            sec = t / TPS
            mats = beast.mats(clip, sec)
            fwd, lift = beast.place(clip, sec, client=client)
            fr = Frame(CAMS["side"][0], W, H, W / 2.9, (0.0, 0.62 + 0.55 * min(lift, 1.0), -0.75 - fwd), ss=3)
            im = render(beast.mesh.world(mats, lift, fwd), fr)
            draw_ground_line(im, fr)
            draw_ground_ticks(im, fr, 1.5 - fwd, -3.0 - fwd)
            low, high = _feet_sub(beast, mats, fwd, lift)
            name = "落地" if t == land else f"落地{t - land:+g}" if t != rec else "收招中"
            row.append(sheets.labelled(im, [f"tick {t:g} · {name}",
                                            f"离地 {lift:.3f} · 足底 {low:+.3f}…{high:+.3f} 格"], W))
        rows.append(row)
    sheets.grid(rows, [f"{beast.name} · {clip} · 落地逐 tick（上：片段与服务端位置同 tick，按此编写，"
                       f"游戏现已如此；下：修复前的原版客户端推算，位置插值 {CLIENT_LERP_STEPS} 步 + 片段领先 {CLIENT_CLIP_LEAD} tick）",
                       "足底 = 四只爪子最低点的世界高度（最低…最高），0 = 地面；地面刻度每 0.5 格",
                       _stamp(beast)]).save(out / f"clip_{clip}_landing.png")
    return f"clip_{clip}_landing.png"


def footwork_sheet(beast, clip, out, W=330, H=250):
    """Close side views, tick by tick, of a ground lunge (the server slides the beast, the legs carry it):
    through the slide, then every step the definition records (its `<clip>_steps(move)` plan), with each
    paw's world drift from its planted point."""
    f = beast.flight(clip)
    mv = beast.move(clip)
    plan = getattr(beast.built.d, f"{clip}_steps", None)
    steps = plan(mv) if plan else {}
    lunge, a1 = mv["lunge"]["tick"], mv["active_ticks"][1]
    first = list(range(lunge - 1, a1 + 5))
    later = sorted({t for leg in steps.values() for t0, t1, *_ in leg if t0 >= a1 + 4 for t in (t0, (t0 + t1) / 2, t1)}
                   | {mv["total_ticks"]})
    rows = []
    for ticks in (first, later):
        row = []
        for t in ticks:
            sec = t / TPS
            mats = beast.mats(clip, sec)
            fwd, lift = beast.place(clip, sec)
            fr = Frame(CAMS["side"][0], W, H, W / 3.3, (0.0, 0.66, -0.3 - fwd), ss=3)
            im = render(beast.mesh.world(mats, lift, fwd), fr)
            draw_ground_line(im, fr)
            draw_ground_ticks(im, fr, 2.0 - fwd, -3.0 - fwd, step=0.25)
            swing = [n.replace("_", " ") for n, leg in steps.items() if any(t0 < t < t1 for t0, t1, *_ in leg)]
            row.append(sheets.labelled(im, [f"tick {t:g}" + (" · 命中" if mv["active_ticks"][0] <= t <= a1 else ""),
                                            f"前移 {fwd:.3f} 格 · 抬脚: {', '.join(swing) or '无'}"], W))
        rows.append(row)
    sheets.grid(rows, [f"{beast.name} · {clip} · 步法逐 tick（实体按服务端滑移，着地的爪子在世界里不动；地面刻度每 0.25 格）",
                       _flight_info(beast, clip), _stamp(beast)]).save(out / f"clip_{clip}_footwork.png")
    return f"clip_{clip}_footwork.png"


def gif(beast, clip, out, size, fps_per_tick=2):
    """Side window (fixed for a move, wide enough for the whole lunge, so the travel shows) and a 3/4
    front window that follows the beast."""
    length, loop, _ = beast.clips[clip]
    mv = beast.move(clip)
    f = beast.flight(clip)
    n = int(round(length * TPS * fps_per_tick))
    W = size
    H = int(size * 0.62)
    SW = W
    side_fixed = None
    if f is not None:
        span_back, span_front = 1.7, max(f.rest_distance + 2.4, 3.6)
        SW = int(round(W * 1.5))
        k = SW / (span_back + span_front + 0.3)
        side_fixed = Frame(CAMS["side"][0], SW, H, k, (0.0, 0.8 + 0.4 * f.apex, (span_back - span_front) / 2.0), 2)
    frames = []
    for i in range(n + (0 if loop else 1)):
        sec = i / (TPS * fps_per_tick)
        mats = beast.mats(clip, sec)
        fwd, lift = beast.place(clip, sec)
        quads = beast.mesh.world(mats, lift, fwd)
        ims = []
        for key in ("side", "q_front"):
            fr = side_fixed if (key == "side" and side_fixed) else _clip_frame(
                key, W, H, bool(mv), ss=2, jump=_apex(beast, clip), follow=fwd)
            im = render(quads, fr)
            tick = sec * TPS
            if mv and mv["active_ticks"][0] <= tick <= mv["active_ticks"][1] + 0.999:
                lo, hi = _shift(hit_box(mv), fwd, lift)
                draw_box(im, fr, lo, hi, (230, 30, 30), width=2)
            if key == "side":
                draw_ground_line(im, fr)
                if mv:
                    draw_ground_ticks(im, fr, 2.0, -(f.rest_distance + 2.0))
                    draw_path(im, fr, beast, clip, tick)
            ims.append(im)
        canvas = Image.new("RGB", (SW + W + 4, H + 22), (24, 24, 28))
        canvas.paste(ims[0], (0, 22))
        canvas.paste(ims[1], (SW + 4, 22))
        dd = ImageDraw.Draw(canvas)
        label = f"{clip}  tick {sec * TPS:5.1f}"
        if mv:
            label += "  " + _phase_at(mv, sec * TPS, f)
            if lift > 0:
                label += f"  离地 {lift:.2f}"
        elif clip in ("walk", "run"):
            label += "  （按片段时间播放；游戏里由 animateWalk 按走过的距离推进）"
        dd.text((6, 3), label, fill=(250, 200, 90), font=sheets.font(15))
        frames.append(canvas)
    pal = [fr_.convert("P", palette=Image.ADAPTIVE, colors=255) for fr_ in frames]
    # GIF delays are whole centiseconds: keep each tick's frames summing to exactly 50 ms
    per_tick = {1: [50], 2: [20, 30]}[fps_per_tick]
    dur = [per_tick[i % fps_per_tick] for i in range(len(pal))]
    pal[0].save(out / f"clip_{clip}.gif", save_all=True, append_images=pal[1:], duration=dur, loop=0, disposal=1)
    written = [f"clip_{clip}.gif"]
    if mv:
        pal[0].save(out / f"clip_{clip}_half.gif", save_all=True, append_images=pal[1:], duration=[2 * x for x in dur],
                    loop=0, disposal=1)
        written.append(f"clip_{clip}_half.gif")
    return written


def _phase_at(mv, tick, flight):
    a0, a1 = mv["active_ticks"]
    if mv["lunge"]["up"] > 0:
        land = flight.landing
        if mv["lunge"]["tick"] <= tick < land:
            return "腾空（命中判定）" if a0 <= tick < a1 + 1 else "腾空"
        if land <= tick < land + 1:
            return "落地" + ("（命中判定）" if a0 <= tick < a1 + 1 else "")
    if tick < mv["turn_lock_tick"]:
        return "前摇（跟踪目标）"
    if tick < mv["lunge"]["tick"]:
        return "前摇（朝向已锁定）"
    if tick < a0:
        return "扑出"
    if tick < a1 + 1:
        return "命中判定"
    return "收招（惩罚窗口）"


def _stamp(beast):
    s = "预览直接读取生成的 schema-1 模型与动画（与磁盘文件一致）"
    if beast.stale:
        s = "注意：磁盘上的文件已过期，先运行 build：" + ", ".join(beast.stale)
    return s


def lighting_sheet(beast, out):
    """How the coat reads under the game's entity lighting at different headings and in dim light."""
    W, H = 460, 300
    cells = []
    rows = []
    for title, key, rot, light in (
            ("朝向斜角（主预览所用）", "q_front", DEFAULT_BODY_ROT, 1.0),
            ("朝东/西：侧面最亮（74%）", "side", 90.0, 1.0),
            ("朝南/北：侧面最暗（50%）", "side", 180.0, 1.0)):
        fr = Frame(CAMS[key][0], W, H, W / (3.4 if key == "q_front" else 3.9), (0.0, 0.8, 0.0))
        cells.append(sheets.labelled(render(beast.quads(), fr, body_rot=rot, light=light), [title, "日光，游戏实体两灯光照"], W))
    rows.append(cells)
    cells = []
    for title, key, rot in (("弱光 · 斜角", "q_front", DEFAULT_BODY_ROT), ("弱光 · 侧面最暗", "side", 180.0)):
        fr = Frame(CAMS[key][0], W, H, W / (3.4 if key == "q_front" else 3.9), (0.0, 0.8, 0.0))
        im = render(beast.quads(), fr, bg=(70, 78, 70), body_rot=rot, light=0.30)
        cells.append(sheets.labelled(im, [title, "亮度 ×0.30（约光照等级 6，默认亮度设置；眼睛发光层不受影响）"], W))
    fr = Frame(CAMS["side"][0], W, H, W / 3.9, (0.0, 0.8, 0.0))
    im = render(beast.quads(), fr, bg=(34, 38, 36), body_rot=180.0, light=0.12)
    cells.append(sheets.labelled(im, ["很暗 · 侧面最暗", "亮度 ×0.12（约光照等级 2）"], W))
    rows.append(cells)
    sheets.grid(rows, [f"{beast.name} · 光照检查",
                       "实体面光照 = 0.4 + 0.6 ×（两盏固定方向光之和），与游戏实体着色一致；朝向决定侧面亮度"]).save(
        out / "lighting.png")


def _side_large(beast, body_rot=DEFAULT_BODY_ROT):
    hb_w, hb_h = beast.built.d.HITBOX
    fr = Frame(CAMS["side"][0], 900, 520, 900 / 4.4, (0.0, 0.85, 0.15))
    im = render(beast.quads(), fr, body_rot=body_rot)
    draw_box(im, fr, (-hb_w / 2, 0.0, -hb_w / 2), (hb_w / 2, hb_h, hb_w / 2), (200, 40, 40), width=1, dash=6)
    draw_ground_line(im, fr)
    return im


def revision_compare(beast, out):
    """The look revisions side by side: the saved renders of earlier versions (before/ = first version,
    rev1/ = first revision) against the same views now, all lit the way the first renders were
    (body yaw 180, flanks at 50 %)."""
    columns = [(out / "before", "第一版"), (out / "rev1", "第一次修订")]
    columns = [(d, t) for d, t in columns if d.is_dir()]
    if not columns:
        return False
    makers = (
        ("side_large.png", lambda: _side_large(beast, 180.0)),
        ("q_front_large.png", lambda: render(beast.quads(), Frame(CAMS["q_front"][0], 900, 620, 900 / 3.6,
                                                                  (0.0, 0.8, -0.3)), body_rot=180.0)),
        ("head_closeup.png", lambda: render(beast.quads(), Frame((-0.8, 0.25, -1.0), 640, 480, 640 / 1.2,
                                                                 (0.0, 1.15, -1.05)), body_rot=180.0)),
    )
    w = 560
    rows = []
    for name, make in makers:
        row = []
        for d, title in columns:
            if (d / name).is_file():
                row.append(sheets.labelled(Image.open(d / name).convert("RGB"), [title, name], w))
        row.append(sheets.labelled(make(), ["本版（第二次修订）", name], w))
        rows.append(row)
    sheets.grid(rows, [f"{beast.name} · 外观修订对比", "从左到右：" + " → ".join(t for _, t in columns) +
                       " → 本版；同视角，光照都按朝北（侧面 50%）渲染"]).save(out / "revision_compare.png")
    return True


def telegraph_compare(beast, out):
    """Rest against each move's wind-up pose, drawn as small as the wolf looks from about eight blocks
    (a 70-degree FOV at 854x480 puts a 1.45-block-tall beast about 60 pixels tall)."""
    cells = []
    picks = [("idle", 1.0, "待机 idle")]
    for mv in beast.server["moves"]:
        picks.append((mv["animation"], mv["turn_lock_tick"] / TPS, f"{mv['animation']} tick {mv['turn_lock_tick']}（锁定朝向）"))
    for key in ("side", "q_front"):
        row = []
        for clip, sec, title in picks:
            fr = Frame(CAMS[key][0], 150, 100, 42.0, (0.0, 0.8, -0.4))
            im = render(beast.quads(clip, sec), fr)
            big = im.resize((300, 200), Image.NEAREST)
            row.append(sheets.labelled(big, [title, CAMS[key][1] + " · 约 8 格外的像素量（放大 2 倍显示）"], 300))
        cells.append(row)
    sheets.grid(cells, [f"{beast.name} · 远看前摇", "左：待机；右：两招各自锁定朝向那一刻的姿势"]).save(
        out / "telegraph_compare.png")


def index_html(beast, out, gifs):
    clips = list(beast.clips)
    parts = [f"<!doctype html><meta charset=utf-8><title>{beast.name} preview</title>",
             "<style>body{background:#16181c;color:#ddd;font:14px sans-serif;margin:16px}img{max-width:100%;"
             "border:1px solid #333;margin:4px 0}h2{color:#fac85a;margin-top:28px}a{color:#8ad}</style>",
             f"<h1>{beast.name} · 离线预览</h1>",
             "<p>由 <code>python3 -m tools.beastgen preview demon_wolf</code> 生成。所有帧直接读取 schema-1 模型和动画文件，"
             "按原版 ModelPart / KeyframeAnimations / LivingEntityRenderer 规则摆放。招式片段只带姿态；预览里实体按服务端的扑击路径（原版重力与阻力）移动，橙线是服务端脚底轨迹。</p>",
             "<h2>模型</h2><img src=model_views.png><img src=side_large.png><img src=q_front_large.png>"
             "<img src=head_closeup.png>",
             "<h2>修订对比</h2><img src=revision_compare.png>",
             "<h2>光照</h2><img src=lighting.png>",
             "<h2>远看前摇</h2><img src=telegraph_compare.png>",
             "<h2>贴图</h2><img src=texture_atlas.png>"]
    for c in clips:
        parts.append(f"<h2>{c}</h2>")
        for g in gifs.get(c, []):
            parts.append(f"<div>{g}</div><img src={g}>")
        parts.append(f"<img src=clip_{c}.png>")
        for extra in (f"clip_{c}_arc.png", f"clip_{c}_landing.png", f"clip_{c}_footwork.png"):
            if (out / extra).is_file():
                parts.append(f"<img src={extra}>")
    (out / "index.html").write_text("\n".join(parts) + "\n", encoding="utf-8")


def run(name, only=None, size=0):
    beast = Beast(name)
    if beast.stale:
        print("WARN: files on disk differ from the generator; previewing the generator's output:", beast.stale)
    out = OUT / name
    out.mkdir(parents=True, exist_ok=True)
    parts = set((only or "views,atlas,sheets,gifs,index").split(","))
    if "views" in parts:
        model_views(beast, out, size or 520)
        telegraph_compare(beast, out)
        lighting_sheet(beast, out)
        extra = ", revision_compare.png" if revision_compare(beast, out) else ""
        print("wrote model_views.png, side_large.png, q_front_large.png, head_closeup.png, telegraph_compare.png, "
              "lighting.png" + extra)
    if "atlas" in parts:
        texture_atlas(beast, out)
        print("wrote texture_atlas.png")
    if "sheets" in parts:
        for c in beast.clips:
            contact_sheet(beast, c, out, size or 400)
            print(f"wrote clip_{c}.png")
            if beast.landing(c) is not None:
                print("wrote " + arc_sheet(beast, c, out) + ", " + landing_sheet(beast, c, out))
            elif beast.flight(c) is not None and beast.flight(c).forward_speed > 0:
                print("wrote " + footwork_sheet(beast, c, out))
    gifs = {}
    if "gifs" in parts or "index" in parts:
        for c in beast.clips:
            mv = beast.move(c)
            names = [f"clip_{c}.gif"] + ([f"clip_{c}_half.gif"] if mv else [])
            if "gifs" in parts:
                names = gif(beast, c, out, 360, 2 if (mv or c in ("stagger", "run", "walk")) else 1)
                print("wrote " + ", ".join(names))
            gifs[c] = names
    if "index" in parts:
        index_html(beast, out, gifs)
        print(f"wrote {out / 'index.html'}")
    return 0
