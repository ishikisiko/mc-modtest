"""Preview sheets: the ledger sample as portraits, every living person, one person's life, the
sizes, and the per-layer part sheets."""
from __future__ import annotations

import json
import os
from dataclasses import replace

from PIL import Image, ImageDraw, ImageFont

from .person import ELEMENT_ZH, RANK_ZH, REALM_ZH, TRAIT_ZH, Person, assign
from .render import render

FONT_PATH = "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"
BG = (38, 38, 44, 255)
PANEL = (24, 22, 30, 255)
LAYERS = [("face", "脸型、嘴、老态"), ("eyes", "眼形、眼色、眉"), ("hair_front", "刘海"), ("hair_back", "后发、发髻、发色"),
          ("body", "道袍、头饰、伤与故")]


def font(size: int):
    try:
        return ImageFont.truetype(FONT_PATH, size)
    except OSError:
        return ImageFont.load_default()


def load_ledger(path: str):
    doc = json.load(open(path, encoding="utf-8"))
    state = doc["final_state"]
    dpy = doc["config"]["days_per_year"]
    day = state["day"]
    sects = {s["id"]: s for s in state["sects"]}
    out = []
    for p in state["persons"]:
        out.append(Person(p["id"], p["surname"] + p["given"], p["gender"], p["realm"], p["rank"], p["sect"],
                          (day - p["birth"]) / float(dpy), p["root"], p["traits"], p["injury"], True, p.get("dao_name", "")))
    for t in state["tombstones"]:
        out.append(Person(t["id"], t["name"], t["gender"], t["realm"], t["rank"], t["sect"],
                          (t["death"] - t["birth"]) / float(dpy), [2000] * 5, [50] * 5, 0, False, t.get("dao_name", "")))
    return out, sects


def caption(p: Person, sects: dict | None = None) -> str:
    sect = ""
    if sects and p.sect_id in sects:
        sect = sects[p.sect_id]["name"]
    bits = [p.name, "女" if p.female else "男", REALM_ZH.get(p.realm, p.realm), RANK_ZH.get(p.rank, p.rank)]
    if sect and p.rank != "rogue":
        bits.append(sect)
    if not p.alive:
        bits.append("已故")
    return " · ".join(bits)


def grid(tiles, scale: int = 3, cols: int = 8, cap_h: int = 34, pad: int = 8, bg=BG, size: int = 13):
    cell_w = 64 * scale + pad
    cell_h = 64 * scale + cap_h + pad
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w + pad, rows * cell_h + pad), bg)
    draw = ImageDraw.Draw(sheet)
    f = font(size)
    for i, (im, lines) in enumerate(tiles):
        x = pad + (i % cols) * cell_w
        y = pad + (i // cols) * cell_h
        big = im.resize((64 * scale, 64 * scale), Image.NEAREST)
        sheet.paste(big, (x, y), big)
        ty = y + 64 * scale + 2
        for line in lines:
            draw.text((x, ty), line, fill=(225, 225, 230, 255), font=f)
            ty += size + 2
    return sheet


def sample_people(people: list[Person], count: int) -> list[Person]:
    """A spread of the ledger by rank quota (masters, elders, inner, outer, rogue) plus a few dead."""
    alive = [p for p in people if p.alive]
    dead = [p for p in people if not p.alive]
    quota = {"sect_master": 5, "elder": 11, "inner": 11, "outer": 9, "rogue": 8}
    picked = []
    for rank, n in quota.items():
        picked += [p for p in alive if p.rank == rank][:n]
    picked = picked[: count - 4]
    picked += dead[:4]
    return picked


def lifecycle(p: Person):
    base = replace(p, alive=True, injury=0)
    return [
        (replace(base, realm="qi_refining", rank="outer", age_years=18), "炼气 · 外门 · 18 岁"),
        (replace(base, realm="foundation_establishment", rank="inner", age_years=45), "筑基 · 内门 · 45 岁"),
        (replace(base, realm="golden_core", rank="elder", age_years=160), "金丹 · 长老 · 160 岁"),
        (replace(base, realm="golden_core", rank="elder", age_years=420), "金丹 · 长老 · 420 岁（老）"),
        (replace(base, realm="nascent_soul", rank="sect_master", age_years=600), "元婴 · 宗主 · 600 岁"),
        (replace(base, realm="golden_core", rank="elder", age_years=160, injury=60), "重伤"),
        (replace(base, realm="golden_core", rank="elder", age_years=160, alive=False), "已故"),
    ]


def sizes_strip(p: Person) -> Image.Image:
    im = render(p)
    scales = (1, 2, 3, 4)
    w = sum(64 * k + 16 for k in scales) + 16
    h = 64 * 4 + 40
    out = Image.new("RGBA", (w, h), PANEL)
    draw = ImageDraw.Draw(out)
    f = font(13)
    x = 16
    for k in scales:
        big = im.resize((64 * k, 64 * k), Image.NEAREST)
        out.paste(big, (x, h - 24 - 64 * k), big)
        draw.text((x, h - 20), f"GUI 缩放 {k}×", fill=(200, 200, 210, 255), font=f)
        x += 64 * k + 16
    return out


def build(ledger: str, out_dir: str, count: int = 48) -> dict:
    from .page import write_pages
    from .preview_layer import sheet as layer_sheet
    os.makedirs(os.path.join(out_dir, "p"), exist_ok=True)
    people, sects = load_ledger(ledger)
    picked = sample_people(people, count)
    tiles = []
    entries = []
    for p in picked:
        ch = assign(p)
        im = render(p, ch)
        im.save(os.path.join(out_dir, "p", f"{p.id}.png"))
        tiles.append((im, [caption(p, sects), f"{TRAIT_ZH[p.mood]} · {ELEMENT_ZH[ch.eye_colour]}眼 · {round(p.age_years)}岁"]))
        entries.append({"id": p.id, "caption": caption(p, sects), "mood": TRAIT_ZH[p.mood], "age": round(p.age_years),
                        "choice": ch.summary(), "alive": p.alive})
    grid(tiles, scale=3, cols=8).save(os.path.join(out_dir, "sheet.png"))
    all_tiles = [(render(p), []) for p in people if p.alive]
    grid(all_tiles, scale=2, cols=13, cap_h=0, pad=4).save(os.path.join(out_dir, "everyone.png"))
    base_f = next(p for p in picked if p.female and p.alive)
    grid([(render(q), [c]) for q, c in lifecycle(base_f)], scale=3, cols=7, cap_h=20).save(os.path.join(out_dir, "life.png"))
    for layer, _ in LAYERS:
        layer_sheet(layer, 3).save(os.path.join(out_dir, f"parts_{layer}.png"))
    sizes_strip(base_f).save(os.path.join(out_dir, "sizes.png"))
    json.dump({"people": entries, "layers": LAYERS}, open(os.path.join(out_dir, "index.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    write_pages(out_dir, entries, LAYERS, len(all_tiles))
    return {"people": entries, "everyone": len(all_tiles)}
