"""Offline preview of a Minecraft 1.21.1 JSON item model (numpy software rasteriser; light).

    python3 -m tools.combat_preview model myvillage:item/qingfeng_sword \
        --out out/preview/combat_preview/qingfeng_sword.png

The model is a resource id (namespace:path under assets/<ns>/models/) or a path to a
.json file. Assets are looked up in every --root (resources roots containing assets/;
the first hit wins, so put a dev pack first; default src/main/resources), then in the
vanilla client resources jar (default: the one a Gradle build leaves under
build/moddev/artifacts/).

What it understands (the subset vanilla / NeoForge 21.1 bakes for items):
  * parent chains, texture variables (#name), display merge (child context wins);
  * elements: from/to, faces (uv, default uv, rotation, texture), element rotation
    (origin/axis/angle/rescale), shade:false;
  * builtin/generated (item/generated, item/handheld): layer extrusion like
    ItemModelGenerator (1 model-px slab, a side face for every opaque pixel edge).
    As in vanilla, a chain whose root is builtin/generated ignores its elements;
  * neoforge:separate_transforms (base + perspectives per display context), and
    neoforge:item_layers (treated like builtin/generated);
  * display transforms (translation/16 clamped to +-5, rotation XYZ, scale clamped to +-4,
    NeoForge right_rotation) and the third-person hand chain of ItemInHandLayer on a
    wide-arm player model (arm pitched -18 deg as when holding an item).
It also reports what the game would reject (element rotation not in 0/+-22.5/+-45,
coordinates outside -16..32, missing textures).

Views (--views, comma list): front (look at the flat, +X side), side (look at the edge,
+Z side), iso, hilt (close-up of collar/handle/butt), tip (close-up of the head tip),
tp_right / tp_front / tp_iso (thirdperson_righthand on the schematic arm; the fist is
the darker bottom 4 px of the arm), gui. With a geometry contract file (--geometry, or
assets/myvillage/combat/qingfeng_sword_geometry.json found in the roots) the grip
centre, head base/tip and the handle/collar/butt ranges are marked, and the tp views
report where the grip centre lands relative to the fist box.
"""
import argparse
import io
import json
import math
import sys
import time
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from .. import combat_data
from . import sheets
from .env import DEFAULT_ROOT, OUT_ROOT, note_missing_jar, vanilla_jar

VANILLA_JAR = vanilla_jar()
DEFAULT_GEOMETRY = "assets/myvillage/combat/qingfeng_sword_geometry.json"
DIRS = ("down", "up", "north", "south", "west", "east")
ALLOWED_ANGLES = (0.0, 22.5, -22.5, 45.0, -45.0)


# ---------------------------------------------------------------- assets
class Assets:
    def __init__(self, roots, vanilla_jar):
        self.roots = [Path(r) for r in roots]
        self.jar = zipfile.ZipFile(vanilla_jar) if vanilla_jar and Path(vanilla_jar).is_file() else None
        self.origin = {}
        self.zips = {}

    def read(self, rel):
        for r in self.roots:
            if r.suffix in (".jar", ".zip") and r.is_file():  # a mod jar works as a root too
                if r not in self.zips:
                    self.zips[r] = zipfile.ZipFile(r)
                try:
                    data = self.zips[r].read(rel)
                    self.origin[rel] = f"{r}!{rel}"
                    return data
                except KeyError:
                    continue
            p = r / rel
            if p.is_file():
                self.origin[rel] = str(p)
                return p.read_bytes()
        if self.jar:
            try:
                data = self.jar.read(rel)
                self.origin[rel] = f"vanilla:{rel}"
                return data
            except KeyError:
                pass
        return None

    @staticmethod
    def split(ref, default_ns="minecraft"):
        ns, _, path = ref.partition(":") if ":" in ref else (default_ns, "", ref)
        return ns, path

    def model_json(self, ref):
        ns, path = self.split(ref)
        data = self.read(f"assets/{ns}/models/{path}.json")
        return None if data is None else json.loads(data)

    def texture(self, ref):
        ns, path = self.split(ref)
        data = self.read(f"assets/{ns}/textures/{path}.png")
        if data is None:
            return None
        im = Image.open(io.BytesIO(data)).convert("RGBA")
        a = np.asarray(im, dtype=np.float32) / 255.0
        h, w = a.shape[:2]
        if h > w and h % w == 0:  # animated strip: first frame
            a = a[:w]
        return a


# ---------------------------------------------------------------- model resolution
class Resolved:
    def __init__(self):
        self.elements = None
        self.textures = {}
        self.display = {}
        self.generated = False
        self.chain = []
        self.warnings = []
        self.errors = []


def resolve_model(assets, obj, name, depth=0, res=None):
    """Walk the parent chain of a model JSON object; child values win."""
    res = res or Resolved()
    if depth > 20:
        res.errors.append("parent chain deeper than 20 (cycle?)")
        return res
    res.chain.append(name)
    loader = obj.get("loader")
    if loader == "neoforge:item_layers":
        res.generated = True
    for k, v in obj.get("textures", {}).items():
        res.textures.setdefault(k, v)
    for ctx, t in obj.get("display", {}).items():
        res.display.setdefault(ctx, t)
    if res.elements is None and "elements" in obj:
        res.elements = obj["elements"]
    parent = obj.get("parent")
    if parent:
        ns, path = Assets.split(parent)
        if path == "builtin/generated":
            res.generated = True
            res.chain.append("builtin/generated")
        elif path == "builtin/entity":
            res.errors.append("builtin/entity (block entity renderer): not previewable")
        else:
            pj = assets.model_json(parent)
            if pj is None:
                res.errors.append(f"parent model not found: {parent}")
            else:
                resolve_model(assets, pj, f"{ns}:{path}", depth + 1, res)
    return res


def texture_ref(res, ref, outer=None):
    seen = 0
    while ref and ref.startswith("#") and seen < 10:
        key = ref[1:]
        ref = res.textures.get(key) or (outer.textures.get(key) if outer else None)
        seen += 1
    return ref


# ---------------------------------------------------------------- geometry
FACE_VERTS = {  # FaceInfo: (x, y, z) picks from (from, to) per axis; 0 = min, 1 = max
    "down": [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west": [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    "east": [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}


def default_uv(face, f, t):
    return {
        "down": [f[0], 16 - t[2], t[0], 16 - f[2]],
        "up": [f[0], f[2], t[0], t[2]],
        "north": [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
        "south": [f[0], 16 - t[1], t[0], 16 - f[1]],
        "west": [f[2], 16 - t[1], t[2], 16 - f[1]],
        "east": [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
    }[face]


def face_uvs(uv, rotation):
    """BlockFaceUV.getU/getV for the 4 vertices."""
    out = []
    for i in range(4):
        s = (i + rotation // 90) % 4
        u = uv[0] if s in (0, 1) else uv[2]
        v = uv[1] if s in (0, 3) else uv[3]
        out.append((u, v))
    return out


def axis_rot(axis, deg):
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class Quad:
    __slots__ = ("pos", "uv", "tex", "color", "shade", "tag")

    def __init__(self, pos, uv=None, tex=None, color=None, shade=True, tag="model"):
        self.pos = np.asarray(pos, dtype=np.float64)  # 4x3
        self.uv = uv
        self.tex = tex
        self.color = color
        self.shade = shade
        self.tag = tag


def element_quads(assets, res, el, idx, outer=None):
    quads = []
    f = [float(v) for v in el["from"]]
    t = [float(v) for v in el["to"]]
    for v in f + t:
        if v < -16 or v > 32:
            res.errors.append(f"element {idx}: coordinate {v} outside -16..32 (MC rejects the model)")
            break
    rot = el.get("rotation")
    R = None
    if rot:
        ang = float(rot.get("angle", 0))
        if ang not in ALLOWED_ANGLES:
            res.errors.append(f"element {idx}: rotation angle {ang} (MC only allows 0/+-22.5/+-45; model fails to load)")
        axis = rot.get("axis", "y")
        R = axis_rot(axis, ang)
        origin = np.array(rot.get("origin", [8, 8, 8]), dtype=np.float64)
        scale = np.ones(3)
        if rot.get("rescale") and ang != 0:
            k = 1.0 / math.cos(math.radians(abs(ang)))
            scale = np.array([1.0 if a == axis else k for a in "xyz"])
    shade = el.get("shade", True)
    for face, fd in el.get("faces", {}).items():
        if face not in FACE_VERTS:
            res.warnings.append(f"element {idx}: unknown face {face}")
            continue
        pts = []
        for sel in FACE_VERTS[face]:
            p = np.array([t[a] if sel[a] else f[a] for a in range(3)], dtype=np.float64)
            if R is not None:
                p = origin + (R @ (p - origin)) * scale
            pts.append(p)
        uv = fd.get("uv") or default_uv(face, f, t)
        uvs = face_uvs([float(x) for x in uv], int(fd.get("rotation", 0)))
        ref = texture_ref(res, fd.get("texture", ""), outer)
        tex = assets.texture(ref) if ref and not ref.startswith("#") else None
        if tex is None:
            res.warnings.append(f"element {idx} face {face}: texture {fd.get('texture')} -> {ref} not found (magenta)")
        quads.append(Quad(pts, uvs, tex, None if tex is not None else (1.0, 0.0, 1.0), shade))
    return quads


def generated_quads(assets, res, outer=None):
    quads = []
    layers = sorted((k for k in res.textures if k.startswith("layer")), key=lambda k: int(k[5:] or 0))
    if not layers:
        res.errors.append("builtin/generated model without layer0 texture: renders nothing")
    for li, key in enumerate(layers):
        ref = texture_ref(res, res.textures[key], outer)
        tex = assets.texture(ref)
        if tex is None:
            res.errors.append(f"{key} texture {ref} not found")
            continue
        H, W = tex.shape[:2]
        z0, z1 = 7.5, 8.5
        # front (south) and back (north) slabs, like ItemModelGenerator
        quads.append(Quad([(0, 16, z1), (0, 0, z1), (16, 0, z1), (16, 16, z1)],
                          face_uvs([0, 0, 16, 16], 0), tex))
        quads.append(Quad([(16, 16, z0), (16, 0, z0), (0, 0, z0), (0, 16, z0)],
                          face_uvs([16, 0, 0, 16], 0), tex))
        opaque = tex[..., 3] > 0
        sx, sy = 16.0 / W, 16.0 / H
        for y in range(H):
            for x in range(W):
                if not opaque[y, x]:
                    continue
                col = tuple(float(c) for c in tex[y, x, :3])
                xa, xb = x * sx, (x + 1) * sx
                ya, yb = 16 - (y + 1) * sy, 16 - y * sy
                if x == 0 or not opaque[y, x - 1]:
                    quads.append(Quad([(xa, yb, z0), (xa, ya, z0), (xa, ya, z1), (xa, yb, z1)], color=col, tag="edge"))
                if x == W - 1 or not opaque[y, x + 1]:
                    quads.append(Quad([(xb, yb, z1), (xb, ya, z1), (xb, ya, z0), (xb, yb, z0)], color=col, tag="edge"))
                if y == 0 or not opaque[y - 1, x]:
                    quads.append(Quad([(xa, yb, z0), (xa, yb, z1), (xb, yb, z1), (xb, yb, z0)], color=col, tag="edge"))
                if y == H - 1 or not opaque[y + 1, x]:
                    quads.append(Quad([(xa, ya, z1), (xa, ya, z0), (xb, ya, z0), (xb, ya, z1)], color=col, tag="edge"))
    return quads


class ModelVariant:
    """One bakeable model (for one display context)."""

    def __init__(self, assets, obj, name, outer=None):
        self.res = resolve_model(assets, obj, name)
        if self.res.generated and self.res.elements:
            self.res.warnings.append(
                "chain root is builtin/generated, so vanilla IGNORES this model's elements and extrudes layer0 "
                "(ModelBakery: getRootModel()==GENERATION_MARKER). Drop the item/handheld|generated parent for a 3D model")
        if self.res.generated:
            self.quads = generated_quads(assets, self.res, outer)
            self.kind = "generated (layer extrusion)"
        elif self.res.elements:
            self.quads = []
            for i, el in enumerate(self.res.elements):
                self.quads += element_quads(assets, self.res, el, i, outer)
            self.kind = f"elements ({len(self.res.elements)})"
        else:
            self.quads = []
            self.kind = "empty"
            self.res.errors.append("model has no elements and no builtin/generated root")

    def display(self, ctx):
        d = self.res.display.get(ctx)
        if d is None and ctx == "thirdperson_lefthand":
            d = self.res.display.get("thirdperson_righthand")
        if d is None and ctx == "firstperson_lefthand":
            d = self.res.display.get("firstperson_righthand")
        return d


class ItemModel:
    def __init__(self, assets, ref):
        self.assets = assets
        p = Path(ref)
        if p.suffix == ".json" and p.is_file():
            obj, self.name = json.loads(p.read_text(encoding="utf-8")), str(p)
        else:
            obj = assets.model_json(ref)
            self.name = ref
            if obj is None:
                raise SystemExit(f"ERROR: model {ref} not found in roots {[str(r) for r in assets.roots]} or the vanilla jar")
        self.loader = obj.get("loader")
        self.per = {}
        if self.loader == "neoforge:separate_transforms":
            self.base = ModelVariant(assets, obj["base"], f"{self.name}#base")
            for ctx, sub in obj.get("perspectives", {}).items():
                self.per[ctx] = ModelVariant(assets, sub, f"{self.name}#{ctx}", outer=self.base.res)
        elif self.loader and self.loader != "neoforge:item_layers":
            self.base = ModelVariant(assets, obj, self.name)
            self.base.res.warnings.append(f"loader {self.loader} not supported by the preview; rendered as plain model")
        else:
            self.base = ModelVariant(assets, obj, self.name)

    def variant(self, ctx=None):
        return self.per.get(ctx, self.base) if ctx else self.base

    def messages(self):
        out = []
        for tag, v in [("base", self.base)] + [(k, v) for k, v in self.per.items()]:
            out += [f"ERROR [{tag}] {e}" for e in v.res.errors]
            out += [f"WARN  [{tag}] {w}" for w in dict.fromkeys(v.res.warnings)]
        return out


# ---------------------------------------------------------------- transforms
def rot_xyz(rx, ry, rz):
    """JOML Quaternionf.rotationXYZ(rx, ry, rz) as a matrix: Rx * Ry * Rz (degrees in)."""
    return axis_rot("x", rx) @ axis_rot("y", ry) @ axis_rot("z", rz)


def affine(M=None, t=None):
    A = np.eye(4)
    if M is not None:
        A[:3, :3] = M
    if t is not None:
        A[:3, 3] = t
    return A


def display_matrix(d):
    """ItemTransform.apply + ItemRenderer's translate(-0.5): model px -> block units."""
    A = affine(np.eye(3) / 16.0)  # px -> blocks
    A = affine(t=(-0.5, -0.5, -0.5)) @ A
    if d:
        tr = np.clip(np.array(d.get("translation", [0, 0, 0]), dtype=float) / 16.0, -5, 5)
        rt = d.get("rotation", [0, 0, 0])
        sc = np.clip(np.array(d.get("scale", [1, 1, 1]), dtype=float), -4, 4)
        rr = d.get("right_rotation", [0, 0, 0])
        M = affine(t=tr) @ affine(rot_xyz(*rt)) @ affine(np.diag(sc)) @ affine(rot_xyz(*rr))
        A = M @ A
    return A


def paired_weapon(roots, model_ref) -> bool:
    """0.39.1: true when a combat weapon file under the roots (directories) names the item whose model
    is ``model_ref`` (``ns:item/name``) and sets ``paired``: the game then draws the same model a second
    time, mirrored, on an empty left hand (client/combat PairedWeaponLayer, FirstPersonArmRenderer)."""
    if not isinstance(model_ref, str) or ":" not in model_ref or Path(model_ref).suffix == ".json":
        return False
    ns, path = model_ref.split(":", 1)
    if not path.startswith("item/"):
        return False
    item = f"{ns}:{path[len('item/'):]}"
    for root in roots:
        base = Path(root)
        if not base.is_dir():
            continue
        for f in sorted(base.glob("data/*/combat/weapon/*.json")):
            try:
                weapon = json.loads(f.read_text(encoding="utf-8"))
            except (OSError, ValueError):
                continue
            if isinstance(weapon, dict) and weapon.get("item") == item:
                return weapon.get("paired") is True
    return False


ARM_PIVOT = np.array([-5.0, 2.0, 0.0]) / 16.0  # PlayerModel right arm (wide), entity space px -> blocks
ARM_XROT = -math.pi / 10  # HumanoidModel ArmPose.ITEM at rest
FIST_BOX = ((-3.0, 6.0, -2.0), (1.0, 10.0, 2.0))  # bottom 4 px of the arm box, arm-local px
ARM_BOX = ((-3.0, -2.0, -2.0), (1.0, 6.0, 2.0))
BODY_BOX = ((-4.0, 0.0, -2.0), (4.0, 12.0, 2.0))  # body part, entity px (pivot 0,0,0)


def arm_matrix():
    """Entity (y-down) space: ModelPart.translateAndRotate for the right arm."""
    return affine(t=ARM_PIVOT) @ affine(axis_rot("x", math.degrees(ARM_XROT)))


def hand_item_matrix():
    """ItemInHandLayer.renderArmWithItem for the right hand, in arm-local block units."""
    return affine(axis_rot("x", -90)) @ affine(axis_rot("y", 180)) @ affine(t=(1 / 16.0, 0.125, -0.625))


ENTITY_TO_WORLD = affine(np.diag([-1.0, -1.0, 1.0]), (0, 1.501, 0))  # LivingEntityRenderer scale(-1,-1,1)


def box_quads(lo, hi, color, tag):
    lo, hi = np.array(lo, float), np.array(hi, float)
    qs = []
    for face, sels in FACE_VERTS.items():
        pts = [np.array([hi[a] if s[a] else lo[a] for a in range(3)]) for s in sels]
        qs.append(Quad(pts, color=color, tag=tag))
    return qs


def transform_quads(quads, A):
    """Quads through the affine A; a mirroring A reverses each quad's vertices (as PairedWeapons does)
    so its front face stays outward for back-face culling."""
    flip = np.linalg.det(A[:3, :3]) < 0
    out = []
    for q in quads:
        P = (np.c_[q.pos, np.ones(4)] @ A.T)[:, :3]
        uv = q.uv
        if flip:
            P = P[::-1]
            uv = list(uv)[::-1] if uv is not None else None
        out.append(Quad(P, uv, q.tex, q.color, q.shade, q.tag))
    return out


def apply_point(A, p):
    return (A @ np.r_[np.asarray(p, float), 1.0])[:3]


# ---------------------------------------------------------------- rasteriser
class Camera:
    def __init__(self, cdir, up=(0, 1, 0)):
        c = np.asarray(cdir, float)
        self.c = c / np.linalg.norm(c)
        f = -self.c
        r = np.cross(f, np.asarray(up, float))
        if np.linalg.norm(r) < 1e-6:
            r = np.cross(f, np.array([0, 0, -1.0]))
        self.r = r / np.linalg.norm(r)
        self.u = np.cross(self.r, f)
        self.light = np.array([-0.45, 0.65, 1.0])
        self.light /= np.linalg.norm(self.light)

    def project(self, P):
        P = np.atleast_2d(P)
        return np.c_[P @ self.r, P @ self.u, P @ self.c]


def render(quads, cam, size, fit_points=None, margin=0.08, ss=2, scale=None, center=None, bg=(206, 210, 216),
           cull=True):
    """Orthographic render. Returns (PIL image, to_screen(points)->Nx2, px_per_unit)."""
    W = H = size * ss
    allp = np.vstack([q.pos for q in quads]) if quads else np.zeros((1, 3))
    fp = cam.project(allp if fit_points is None else fit_points)
    lo, hi = fp[:, :2].min(0), fp[:, :2].max(0)
    ctr = (lo + hi) / 2 if center is None else cam.project(np.asarray(center))[0, :2]
    span = max(hi[0] - lo[0], hi[1] - lo[1], 1e-6)
    k = (W * (1 - 2 * margin) / span) if scale is None else scale * ss

    def to_screen(P):
        pr = cam.project(P)
        return np.c_[(pr[:, 0] - ctr[0]) * k + W / 2, H / 2 - (pr[:, 1] - ctr[1]) * k, pr[:, 2]]

    img = np.empty((H, W, 3), np.float32)
    grad = np.linspace(1.0, 0.86, H, dtype=np.float32)[:, None, None]
    img[:] = np.array(bg, np.float32)[None, None, :] / 255.0 * grad
    zb = np.full((H, W), -np.inf, np.float32)
    for q in quads:
        S = to_screen(q.pos)
        n = np.cross(q.pos[1] - q.pos[0], q.pos[2] - q.pos[0])
        if np.linalg.norm(n) < 1e-12:
            n = np.cross(q.pos[2] - q.pos[0], q.pos[3] - q.pos[0])
        nn = np.linalg.norm(n)
        if nn < 1e-12:
            continue
        n = n / nn
        facing = float(n @ cam.c)
        if cull and facing <= 1e-9:
            continue
        ncam = np.array([n @ cam.r, n @ cam.u, n @ cam.c])
        if ncam[2] < 0:
            ncam = -ncam
        sh = 0.42 + 0.58 * max(0.0, float(ncam @ cam.light)) if q.shade else 1.0
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster_tri(img, zb, S[list(tri)], [q.uv[i] for i in tri] if q.uv else None, q.tex, q.color, sh)
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).resize((size, size), Image.LANCZOS)
    return im, (lambda P: to_screen(np.atleast_2d(P))[:, :2] / ss), k / ss


def raster_tri(img, zb, S, uv, tex, color, shade):
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
    xs = np.arange(x0, x1 + 1, dtype=np.float64) + 0.5
    ys = np.arange(y0, y1 + 1, dtype=np.float64) + 0.5
    X, Y = np.meshgrid(xs, ys)
    w0 = ((bx - X) * (cy - Y) - (by - Y) * (cx - X)) / area
    w1 = ((cx - X) * (ay - Y) - (cy - Y) * (ax - X)) / area
    w2 = 1.0 - w0 - w1
    eps = -1e-6
    inside = (w0 >= eps) & (w1 >= eps) & (w2 >= eps)
    if not inside.any():
        return
    Z = w0 * az + w1 * bz + w2 * cz
    zsub = zb[y0:y1 + 1, x0:x1 + 1]
    m = inside & (Z > zsub + 1e-7)
    if not m.any():
        return
    if tex is not None and uv is not None:
        U = w0 * uv[0][0] + w1 * uv[1][0] + w2 * uv[2][0]
        V = w0 * uv[0][1] + w1 * uv[1][1] + w2 * uv[2][1]
        th, tw = tex.shape[:2]
        tx = np.clip(np.floor(U / 16.0 * tw).astype(int), 0, tw - 1)
        ty = np.clip(np.floor(V / 16.0 * th).astype(int), 0, th - 1)
        texel = tex[ty, tx]
        m &= texel[..., 3] >= 0.1  # cutout
        rgb = texel[..., :3] * shade
    else:
        rgb = np.broadcast_to(np.array(color, np.float32) * shade, X.shape + (3,))
    sub = img[y0:y1 + 1, x0:x1 + 1]
    sub[m] = rgb[m]
    zsub[m] = Z[m]


# ---------------------------------------------------------------- views
def checked_geometry(geo, src):
    """The contract, or exit naming the format 2 fields when it is not format 2 (as the game rejects it)."""
    problems = combat_data.geometry_format_problems(geo)
    if problems:
        raise SystemExit(f"ERROR: geometry contract {src}: " + "; ".join(problems))
    return geo, src


def geometry_for(assets, arg):
    if arg == "none":
        return None, None
    if arg:
        p = Path(arg)
        return checked_geometry(json.loads(p.read_text(encoding="utf-8")), str(p))
    data = assets.read(DEFAULT_GEOMETRY)
    return checked_geometry(json.loads(data), assets.origin.get(DEFAULT_GEOMETRY)) if data else (None, None)


def model_space_points(geo):
    pts = {}
    if not geo:
        return pts
    for k in ("grip_center", "head_base", "head_tip"):
        if k in geo:
            pts[k] = np.array(geo[k], float)
    return pts


MARK_COLORS = {"grip_center": (230, 40, 40), "head_base": (20, 160, 200), "head_tip": (20, 160, 200)}


def draw_marks(im, to_screen, pts, labels=True):
    d = ImageDraw.Draw(im)
    f = sheets.font(12)
    for k, p in pts.items():
        x, y = to_screen(p)[0]
        c = MARK_COLORS.get(k, (230, 40, 40))
        d.ellipse((x - 4, y - 4, x + 4, y + 4), outline=c, width=2)
        if labels:
            d.text((x + 6, y - 7), k, fill=c, font=f)


def draw_ranges(im, to_screen, geo, axis_screen_x=8):
    """handle/collar/butt Y ranges as coloured bars along the left edge (model-space views)."""
    if not geo:
        return
    d = ImageDraw.Draw(im)
    f = sheets.font(11)
    cols = {"butt": (40, 150, 120), "handle": (120, 80, 40), "collar": (200, 160, 30)}
    for k, c in cols.items():
        if k in geo and "y" in geo[k]:
            y0, y1 = geo[k]["y"]
            a = to_screen(np.array([8.0, y0, 8.0]))[0][1]
            b = to_screen(np.array([8.0, y1, 8.0]))[0][1]
            d.line((axis_screen_x, a, axis_screen_x, b), fill=c, width=5)
            d.text((axis_screen_x + 6, (a + b) / 2 - 6), f"{k} {y0:g}-{y1:g}", fill=c, font=f)


def draw_ruler(im, px_per_unit, unit_name="模型像素"):
    d = ImageDraw.Draw(im)
    f = sheets.font(12)
    L = px_per_unit
    step = 1
    while L * step < 12:
        step *= 2
    n = 4
    x0, y0 = 10, im.height - 14
    d.line((x0, y0, x0 + L * step * n, y0), fill=(30, 30, 30), width=2)
    for i in range(n + 1):
        x = x0 + L * step * i
        d.line((x, y0 - 4, x, y0 + 4), fill=(30, 30, 30), width=1)
    d.text((x0, y0 - 18), f"{step * n:g} {unit_name}（1 {unit_name} = {L:.1f} 屏幕像素）", fill=(30, 30, 30), font=f)


def model_view(model, name, cdir, size, geo, ypick=None, scale=None, cull=True):
    v = model.variant(None)
    quads = v.quads
    if not quads:
        return None, {}
    cam = Camera(cdir)
    fit = None
    if ypick:
        pts = np.vstack([q.pos for q in quads])
        sel = pts[(pts[:, 1] >= ypick[0]) & (pts[:, 1] <= ypick[1])]
        if len(sel) >= 2:
            fit = np.vstack([sel, [[8, ypick[0], 8], [8, ypick[1], 8]]])
    im, to_s, k = render(quads, cam, size, fit_points=fit, scale=scale, cull=cull)
    marks = model_space_points(geo)
    draw_marks(im, to_s, marks)
    if name in ("front", "side"):
        draw_ranges(im, to_s, geo)
    draw_ruler(im, k)
    return im, {"px_per_model_px": round(k, 2)}


def tp_scene(model, geo, schematic=True):
    """World-space quads for thirdperson_righthand on the schematic player + grip analysis."""
    v = model.variant("thirdperson_righthand")
    D = display_matrix(v.display("thirdperson_righthand"))
    arm = arm_matrix()
    item_in_arm = hand_item_matrix() @ D  # model px -> arm-local blocks
    to_world = ENTITY_TO_WORLD @ arm @ item_in_arm
    quads = transform_quads(v.quads, to_world)
    if schematic:
        px = affine(np.eye(3) / 16.0)
        quads += transform_quads(box_quads(*ARM_BOX, (0.86, 0.68, 0.55), "arm"), ENTITY_TO_WORLD @ arm @ px)
        quads += transform_quads(box_quads(*FIST_BOX, (0.62, 0.42, 0.32), "fist"), ENTITY_TO_WORLD @ arm @ px)
        quads += transform_quads(box_quads(*BODY_BOX, (0.35, 0.55, 0.62), "body"), ENTITY_TO_WORLD @ px)
    info = {"display": v.display("thirdperson_righthand"), "variant": v.res.chain[0]}
    marks = {}
    if geo:
        for k, p in model_space_points(geo).items():
            marks[k] = apply_point(to_world, p)
        if "grip_center" in geo:
            g_arm = apply_point(item_in_arm, geo["grip_center"]) * 16.0  # arm-local px
            lo, hi = np.array(FIST_BOX[0]), np.array(FIST_BOX[1])
            fist_c = (lo + hi) / 2
            inside = bool(np.all(g_arm >= lo) and np.all(g_arm <= hi))
            info["grip_center_arm_px"] = [round(float(x), 2) for x in g_arm]
            info["fist_center_arm_px"] = [round(float(x), 2) for x in fist_c]
            info["grip_offset_from_fist_center_px"] = [round(float(x), 2) for x in g_arm - fist_c]
            info["grip_center_inside_fist"] = inside
        if "head_base" in geo and "head_tip" in geo:
            b = apply_point(item_in_arm, geo["head_base"])
            t = apply_point(item_in_arm, geo["head_tip"])
            dv = (t - b) / (np.linalg.norm(t - b) or 1)
            # arm-local: +y runs from shoulder to hand (down the arm), -z is the arm's front
            info["blade_dir_arm_local"] = [round(float(x), 3) for x in dv]
            info["blade_angle_to_forearm_deg"] = round(math.degrees(math.acos(max(-1, min(1, float(dv @ np.array([0, 1.0, 0])))))), 1)
    return quads, marks, info


def tp_pair_view(model, cdir, size, geo):
    """0.39.1: a paired weapon on both hands in the rest pose: the right-hand scene and its mirror image
    across the body's middle (PairedWeaponLayer draws the left one exactly so for a symmetric pose)."""
    quads, marks, info = tp_scene(model, geo)
    mirror = ENTITY_TO_WORLD @ affine(np.diag([-1.0, 1.0, 1.0])) @ np.linalg.inv(ENTITY_TO_WORLD)
    body = [q for q in quads if q.tag == "body"]
    quads = [q for q in quads if q.tag != "body"]
    quads = quads + transform_quads(quads, mirror) + body
    cam = Camera(cdir)
    im, to_s, k = render(quads, cam, size)
    draw_marks(im, to_s, marks, labels=False)
    draw_ruler(im, k / 16.0, "模型像素(玩家)")
    return im, info


def tp_view(model, cdir, size, geo):
    quads, marks, info = tp_scene(model, geo)
    cam = Camera(cdir)
    im, to_s, k = render(quads, cam, size)
    draw_marks(im, to_s, marks, labels=False)
    draw_ruler(im, k / 16.0, "模型像素(玩家)")
    return im, info


def gui_view(model, size):
    v = model.variant("gui")
    D = display_matrix(v.display("gui"))
    quads = transform_quads(v.quads, D)
    if not quads:
        return None, {}
    im, _, _ = render(quads, Camera((0, 0, 1)), size, fit_points=np.array([[-0.5, -0.5, 0], [0.5, 0.5, 0]]),
                      margin=0.04)
    return im, {"display": v.display("gui"), "variant": v.res.chain[0]}


DEFAULT_VIEWS = "front,side,iso,hilt,tip,tp_right,tp_front,tp_iso,gui"
VIEW_TITLES = {
    "front": "正面（看剑面，+X 侧）", "side": "侧面（看刃口，+Z 侧）", "iso": "45° 斜视",
    "hilt": "剑柄近景（剑格/柄/剑首）", "tip": "剑尖近景", "tp_right": "thirdperson_righthand · 右侧",
    "tp_front": "thirdperson_righthand · 正面", "tp_iso": "thirdperson_righthand · 斜前", "gui": "gui（物品栏）",
    "tp_pair": "成对（paired）· 左手镜像",
}


def build(args):
    t0 = time.time()
    assets = Assets(args.root, None if args.no_vanilla else args.vanilla_jar)
    model = ItemModel(assets, args.model)
    geo, geo_src = geometry_for(assets, args.geometry)
    views = args.views.split(",")
    if args.views == DEFAULT_VIEWS and paired_weapon(args.root, args.model):
        views.append("tp_pair")   # a paired weapon (0.39.1) shows both hands by default
    size = args.size
    ys = [q.pos[:, 1] for q in model.base.quads]
    ymin, ymax = (float(min(y.min() for y in ys)), float(max(y.max() for y in ys))) if ys else (0.0, 16.0)
    if geo and all(k in geo for k in ("butt", "collar")):
        hilt_rng = (geo["butt"]["y"][0] - 0.5, geo["collar"]["y"][1] + 1.5)
    else:
        hilt_rng = (ymin, ymin + 0.4 * (ymax - ymin))
    tip_y = geo["head_tip"][1] if geo and "head_tip" in geo else ymax
    tip_rng = (tip_y - 6.0, tip_y + 0.5)
    cells, report = [], {"model": model.name, "loader": model.loader, "views": {}}
    for vname in views:
        info = {}
        if vname == "front":
            im, info = model_view(model, vname, (1, 0, 0), size, geo, scale=args.scale)
        elif vname == "side":
            im, info = model_view(model, vname, (0, 0, 1), size, geo, scale=args.scale)
        elif vname == "iso":
            im, info = model_view(model, vname, (1, 0.6, 0.8), size, geo, scale=args.scale)
        elif vname == "hilt":
            im, info = model_view(model, vname, (1, 0.35, 0.6), size, geo, ypick=hilt_rng)
        elif vname == "tip":
            im, info = model_view(model, vname, (1, 0.25, 0.45), size, geo, ypick=tip_rng)
        elif vname == "tp_right":
            im, info = tp_view(model, (1, 0.25, 0.05), size, geo)
        elif vname == "tp_front":
            im, info = tp_view(model, (0.1, 0.25, -1), size, geo)
        elif vname == "tp_iso":
            im, info = tp_view(model, (0.8, 0.45, -0.7), size, geo)
        elif vname == "tp_pair":
            im, info = tp_pair_view(model, (0.1, 0.3, -1), size, geo)
        elif vname == "gui":
            im, info = gui_view(model, size)
        else:
            raise SystemExit(f"ERROR: unknown view {vname}; choose from {list(VIEW_TITLES)}")
        if im is None:
            im = sheets.placeholder(size, size, "nothing to draw")
        sub = ""
        if "grip_center_inside_fist" in info:
            sub = (f"握点{'在' if info['grip_center_inside_fist'] else '不在'}拳头盒内；偏移 "
                   f"{info['grip_offset_from_fist_center_px']} px；剑身与前臂夹角 {info.get('blade_angle_to_forearm_deg')}°")
        elif "px_per_model_px" in info:
            sub = f"1 模型像素 = {info['px_per_model_px']} 屏幕像素"
        cells.append(sheets.labelled(im, [VIEW_TITLES.get(vname, vname), sub], size))
        report["views"][vname] = info
        if args.each:
            out_each = Path(args.out).with_name(f"{Path(args.out).stem}_{vname}.png")
            im.save(out_each)
    msgs = model.messages()
    report["messages"] = msgs
    report["geometry"] = geo_src
    report["assets"] = assets.origin
    report["quads"] = {"base": len(model.base.quads), **{k: len(v.quads) for k, v in model.per.items()}}
    report["kind"] = model.base.kind
    cols = args.cols
    rows = [cells[i:i + cols] for i in range(0, len(cells), cols)]
    title = [f"模型预览 · {model.name} · {model.base.kind}" + (f" · loader {model.loader}" if model.loader else ""),
             f"父链 {' -> '.join(model.base.res.chain)} · 几何契约 {geo_src or '无'} · {time.strftime('%Y-%m-%d %H:%M:%S')}"]
    title += (msgs[:6] if msgs else ["无加载错误/警告"])
    sheet = sheets.grid(rows, title)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    out.with_suffix(".json").write_text(json.dumps(report, indent=2, ensure_ascii=False, default=str), encoding="utf-8")
    print(f"wrote {out} ({sheet.width}x{sheet.height}) + {out.with_suffix('.json').name} in {time.time() - t0:.1f}s")
    for m in msgs:
        print("  " + m)
    for vname, info in report["views"].items():
        if "grip_center_inside_fist" in info:
            print(f"  {vname}: grip_center in fist box = {info['grip_center_inside_fist']}, "
                  f"offset {info['grip_offset_from_fist_center_px']} px (arm-local: +y down the arm, -z forward), "
                  f"blade vs forearm {info.get('blade_angle_to_forearm_deg')} deg")
            break
    return 1 if any(m.startswith("ERROR") for m in msgs) and args.strict else 0


def main(argv=None):
    ap = argparse.ArgumentParser(prog="python3 -m tools.combat_preview model", description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("model", help="resource id (myvillage:item/qingfeng_sword) or path to a model .json")
    ap.add_argument("--root", action="append", default=[],
                    help="resources root containing assets/, or a mod .jar (repeatable, first wins). "
                         "Default: src/main/resources of this repository")
    ap.add_argument("--vanilla-jar", default=str(VANILLA_JAR))
    ap.add_argument("--no-vanilla", action="store_true")
    ap.add_argument("--geometry", help="geometry contract JSON; 'none' to disable (default: looked up in the roots)")
    ap.add_argument("--views", default=DEFAULT_VIEWS,
                    help="comma list; tp_pair (both hands) is added to the default for a paired weapon")
    ap.add_argument("--size", type=int, default=420, help="pixels per view (square)")
    ap.add_argument("--cols", type=int, default=3)
    ap.add_argument("--scale", type=float, help="fixed screen px per model px for front/side/iso (for A/B comparisons)")
    ap.add_argument("--out", default=str(OUT_ROOT / "model.png"))
    ap.add_argument("--each", action="store_true", help="also save every view as <out>_<view>.png")
    ap.add_argument("--strict", action="store_true", help="exit 1 when the model has load errors")
    a = ap.parse_args(argv)
    if not a.root:
        a.root = [str(DEFAULT_ROOT)]
    note_missing_jar(a.vanilla_jar, a.no_vanilla)
    return build(a)
