"""Offline renders of an NPC: turnaround, close-ups, the atlas, clip sheets, GIFs and an index page.

Needs numpy and Pillow. Posing and rasterising are tools/beastgen/preview.py's: the written schema-1
files are read back, clips are sampled by the KeyframeAnimations port, and the figure is lit with the
game's two fixed entity lights, so a frame here is what the renderer draws for the same pose (at the
model file's scale). NpcModel draws with entityCutoutNoCull, so every frame here is rendered with
cull=False: back faces are drawn too, and the inside of a cut-out shell shows through its holes as it
does in game.
"""
from __future__ import annotations

import json
import math

import numpy as np
from PIL import Image

from ..beastgen import anim, cuboid
from ..beastgen.preview import Frame, Mesh, draw_box, draw_ground_line, player_mesh, render, rows_to_array
from ..combat_preview import sheets
from .build import REPO, Built, same_file

OUT = REPO / "out/preview"
PARTS = ("views", "closeups", "face", "atlas", "sheets", "gifs", "index")

# camera directions, from the figure toward the camera (the figure faces -Z, its left is -X)
VIEWS = (
    ("front", (0.0, 0.08, -1.0), "正面"),
    ("q_front", (-0.85, 0.28, -1.0), "左前 3/4"),
    ("side", (-1.0, 0.05, 0.0), "左侧"),
    ("q_back", (0.85, 0.28, 1.0), "右后 3/4"),
    ("back", (0.0, 0.08, 1.0), "背面"),
    ("q_front_right", (0.85, 0.28, -1.0), "右前 3/4"),
)
CLOSEUPS = (
    # title, camera, centre (blocks), pixels per block, size
    ("头部 · 正面", (0.0, 0.05, -1.0), (0.0, 1.70, 0.0), 900, (460, 460)),
    ("头部 · 左前", (-0.9, 0.25, -1.0), (0.0, 1.70, 0.0), 820, (460, 460)),
    ("头部 · 右后", (0.9, 0.3, 1.0), (0.0, 1.66, 0.0), 760, (460, 460)),
    ("头部 · 侧面", (-1.0, 0.05, 0.0), (0.0, 1.70, 0.0), 820, (460, 460)),
    ("胸口 · 交领与对襟", (-0.35, 0.2, -1.0), (0.0, 1.22, 0.0), 900, (460, 460)),
    ("腰间 · 腰封、绦带、玉佩", (-0.5, 0.25, -1.0), (0.0, 0.80, 0.0), 800, (460, 460)),
    ("袖口 · 侧面", (-1.0, 0.15, -0.35), (0.0, 0.95, 0.0), 760, (460, 460)),
    ("下摆 · 左前", (-0.8, 0.35, -1.0), (0.0, 0.32, 0.0), 760, (460, 460)),
    ("背后 · 披发与后摆", (0.3, 0.2, 1.0), (0.0, 1.05, 0.0), 520, (460, 460)),
)
# the face sheet: the same six views large, then small at about in-game size
FACE_VIEWS = (
    ("正面", (0.0, 0.05, -1.0)),
    ("正面 · 略仰视", (0.0, -0.25, -1.0)),
    ("左前 3/4", (-0.9, 0.25, -1.0)),
    ("右前 3/4", (0.9, 0.25, -1.0)),
    ("左前 · 浅角度", (-0.45, 0.1, -1.0)),
    ("左侧", (-1.0, 0.05, 0.0)),
)
FACE_CENTER = (0.0, 1.70, 0.0)
FACE_BIG = (1100, (600, 600))     # pixels per block, cell size: the whole head, neck to crown
FACE_SMALL = (170, (160, 160))    # the head about 70 px wide, as it reads a few blocks away


class Npc:
    def __init__(self, name):
        self.name = name
        self.built = Built(name)
        stale = [p for p, data in self.built.outputs().items() if not same_file(p, data)]
        self.stale = [str(p.relative_to(REPO)) for p in stale]
        self.model = cuboid.model_from_json(self.built.model_doc)
        self.clips = anim.clips_from_json(json.loads(json.dumps(self.built.anim_doc)))
        self.mesh = Mesh(self.model, rows_to_array(self.built.texture), None, scale=self.model.scale)

    def quads(self, clip=None, seconds=0.0, look=None, clip2=None, seconds2=0.0):
        offsets = {}
        for name, at in ((clip, seconds), (clip2, seconds2)):
            if name:
                length, loop, chans = self.clips[name]
                anim.clip_offsets(chans, length, loop, at, into=offsets)
        return self.mesh.world(anim.posed_matrices(self.model, offsets, look))

    def stamp(self):
        w, h = self.model.texture_size
        text = (f"{len(self.model.bones)} 骨骼 · {len(self.model.cubes())} 方块 · 图集 {w}x{h} · "
                f"模型比例 {self.model.scale}（1 像素 = 1/{int(round(16 / self.model.scale))} 格）")
        if self.stale:
            text += " · 注意：磁盘上的文件与当前定义不一致，先运行 build"
        return text


def _figure_frame(cdir, W, H, k=250.0, center=(0.0, 1.0, 0.0)):
    return Frame(cdir, W, H, k, center)


def turnaround(npc, out):
    hb_w, hb_h = npc.built.d.HITBOX
    lo, hi = (-hb_w / 2, 0.0, -hb_w / 2), (hb_w / 2, hb_h, hb_w / 2)
    W, H = 380, 600
    cells = []
    for key, cdir, title in VIEWS:
        fr = _figure_frame(cdir, W, H)
        im = render(npc.quads(), fr, cull=False)
        if key in ("front", "side"):
            draw_box(im, fr, lo, hi, (200, 40, 40), width=1, dash=5)
            draw_ground_line(im, fr)
        cells.append(sheets.labelled(im, [title], W))
    sheet = sheets.grid([cells[0:3], cells[3:6]], [f"{npc.name} · 六视图（静止姿态）", npc.stamp(),
                                                   f"红虚线 = 碰撞箱 {hb_w} x {hb_h} 格"])
    sheet.save(out / "turnaround.png")
    # beside the player, true scale
    fr = Frame((-0.6, 0.2, -1.0), 760, 620, 250.0, (-0.55, 1.0, 0.0))
    player = player_mesh(offset=(-1.1, 0.0, 0.0))
    im = render(npc.quads() + player.world(anim.posed_matrices(player.model)), fr, cull=False)
    sheets.labelled(im, ["与玩家并排（真实比例）", "左：修仙者；右：原版玩家模型"], 760).save(out / "scale.png")
    hero = Frame((-0.7, 0.22, -1.0), 760, 1100, 500.0, (0.0, 1.02, 0.0))
    render(npc.quads(), hero, cull=False).save(out / "hero.png")
    hero_back = Frame((0.7, 0.22, 1.0), 760, 1100, 500.0, (0.0, 1.02, 0.0))
    render(npc.quads(), hero_back, cull=False).save(out / "hero_back.png")


def closeups(npc, out):
    cells = []
    for title, cdir, center, k, (W, H) in CLOSEUPS:
        fr = Frame(cdir, W, H, k, center)
        cells.append(sheets.labelled(render(npc.quads(), fr, ground=False, cull=False), [title], W))
    rows = [cells[i:i + 3] for i in range(0, len(cells), 3)]
    sheets.grid(rows, [f"{npc.name} · 细节特写", npc.stamp()]).save(out / "closeups.png")


def _hstack(images, gap=6, bg=(12, 12, 14)):
    out = Image.new("RGB", (sum(im.width for im in images) + gap * (len(images) - 1),
                            max(im.height for im in images)), bg)
    x = 0
    for im in images:
        out.paste(im, (x, 0))
        x += im.width + gap
    return out


def face_sheet(npc, out):
    big, small = [], []
    for title, cdir in FACE_VIEWS:
        for (k, (W, H)), cells in ((FACE_BIG, big), (FACE_SMALL, small)):
            im = render(npc.quads(), Frame(cdir, W, H, k, FACE_CENTER), ground=False, cull=False)
            cells.append(sheets.labelled(im, [title], W))
    rows = [[_hstack(big[0:3])], [_hstack(big[3:6])], [_hstack(small)]]
    sheets.grid(rows, [f"{npc.name} · 脸部", npc.stamp(),
                       f"上两行 {FACE_BIG[0]} 像素/格；下一行 {FACE_SMALL[0]} 像素/格（约为几格外看到的大小）"]).save(
        out / "face.png")


def atlas(npc, out, scale=6):
    tw, th = npc.model.texture_size
    tex = Image.fromarray(np.asarray(npc.built.texture, np.uint8), "RGBA")
    bg = Image.new("RGBA", (tw, th))
    px = bg.load()
    for y in range(th):
        for x in range(tw):
            px[x, y] = (58, 60, 66, 255) if (x // 4 + y // 4) % 2 else (70, 72, 80, 255)
    bg.alpha_composite(tex)
    big = bg.resize((tw * scale, th * scale), Image.NEAREST).convert("RGB")
    sheets.labelled(big, [f"{npc.name} · 贴图图集 {tw}x{th}（放大 {scale} 倍；棋盘格 = 透明/镂空）"], big.width).save(
        out / "atlas.png")


def _clip_cells(npc, clip, cdir, count, W, H, k, title):
    length, _, _ = npc.clips[clip]
    cells = []
    for i in range(count):
        seconds = length * i / count
        fr = _figure_frame(cdir, W, H, k)
        im = render(npc.quads(clip, seconds), fr, cull=False)
        draw_ground_line(im, fr)
        cells.append(sheets.labelled(im, [f"{title} {i}/{count}"], W))
    return cells


def clip_sheets(npc, out):
    W, H, k = 250, 420, 190.0
    side = _clip_cells(npc, "walk", (-1.0, 0.05, 0.0), 8, W, H, k, "侧面")
    front = _clip_cells(npc, "walk", (-0.7, 0.25, -1.0), 8, W, H, k, "左前")
    back = _clip_cells(npc, "walk", (0.7, 0.25, 1.0), 8, W, H, k, "右后")
    sheets.grid([side, front, back], [f"{npc.name} · walk（一个步态周期，8 等分）",
                                     "支撑脚贴地匀速后移，摆动脚抬起；前后摆与绦带跟随腿"]).save(out / "walk.png")
    idle = _clip_cells(npc, "idle", (-0.7, 0.25, -1.0), 4, W, H, k, "左前")
    sheets.grid([idle], [f"{npc.name} · idle（4 等分，幅度很小）"]).save(out / "idle.png")


def _gif(frames, path, ms):
    pal = [f.convert("P", palette=Image.ADAPTIVE, colors=255) for f in frames]
    pal[0].save(path, save_all=True, append_images=pal[1:], duration=ms, loop=0, disposal=2)


def gifs(npc, out):
    W, H, k = 420, 660, 290.0
    frames = []
    for i in range(48):
        a = 2.0 * math.pi * i / 48
        cdir = (-math.sin(a), 0.22, -math.cos(a))
        idle_at = npc.clips["idle"][0] * i / 48
        frames.append(render(npc.quads("idle", idle_at), Frame(cdir, W, H, k, (0.0, 1.0, 0.0), ss=2), cull=False))
    _gif(frames, out / "turntable.gif", 90)
    length = npc.clips["walk"][0]
    for name, cdir in (("walk_front", (-0.7, 0.25, -1.0)), ("walk_side", (-1.0, 0.05, 0.0)),
                       ("walk_back", (0.7, 0.25, 1.0))):
        frames = []
        for i in range(24):
            fr = Frame(cdir, W, H, k, (0.0, 1.0, 0.0), ss=2)
            im = render(npc.quads("walk", length * i / 24, clip2="idle", seconds2=length * i / 24), fr, cull=False)
            draw_ground_line(im, fr)
            frames.append(im)
        _gif(frames, out / f"{name}.gif", 45)


def index_html(npc, out):
    def img(name, note):
        return f'<figure><img src="{name}" alt="{note}"><figcaption>{note}</figcaption></figure>' \
            if (out / name).is_file() else ""

    body = "\n".join([
        img("hero.png", "左前 3/4"), img("hero_back.png", "右后 3/4"), img("turntable.gif", "转台（带 idle）"),
        img("turnaround.png", "六视图"), img("closeups.png", "细节特写"), img("face.png", "脸部"),
        img("scale.png", "与玩家并排"),
        img("walk_front.gif", "walk · 左前"), img("walk_side.gif", "walk · 侧面"), img("walk_back.gif", "walk · 右后"),
        img("walk.png", "walk 分解"), img("idle.png", "idle 分解"), img("atlas.png", "贴图图集"),
    ])
    html = f"""<!doctype html>
<html lang="zh"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{npc.name} 预览</title>
<style>
body {{ margin: 0; padding: 16px; background: #1c1e24; color: #dfe3ea; font: 15px/1.5 system-ui, sans-serif; }}
h1 {{ font-size: 20px; margin: 0 0 4px; }} p {{ margin: 0 0 16px; color: #9aa3b2; }}
main {{ display: flex; flex-wrap: wrap; gap: 14px; align-items: flex-start; }}
figure {{ margin: 0; background: #262931; border-radius: 6px; padding: 8px; max-width: 100%; }}
img {{ display: block; max-width: 100%; height: auto; image-rendering: pixelated; }}
figcaption {{ padding-top: 6px; color: #9aa3b2; font-size: 13px; }}
</style></head><body>
<h1>{npc.name} · 离线预览</h1>
<p>{npc.stamp()}。离线渲染使用游戏的两盏固定实体光，不是游戏内截图。</p>
<main>
{body}
</main></body></html>
"""
    (out / "index.html").write_text(html, encoding="utf-8")


def run(name, only=None):
    parts = PARTS if not only else tuple(p.strip() for p in only.split(","))
    unknown = [p for p in parts if p not in PARTS]
    if unknown:
        raise SystemExit(f"unknown part {unknown[0]!r}; known: {', '.join(PARTS)}")
    npc = Npc(name)
    out = OUT / name
    out.mkdir(parents=True, exist_ok=True)
    steps = {"views": turnaround, "closeups": closeups, "face": face_sheet, "atlas": atlas, "sheets": clip_sheets,
             "gifs": gifs, "index": index_html}
    for part in PARTS:
        if part in parts:
            steps[part](npc, out)
            print(f"{part}: done")
    print(f"preview: {out.relative_to(REPO)}/index.html")
    return 0
