#!/usr/bin/env python3
"""Export the exact pill PNGs and shared layers for human visual review."""
from pathlib import Path
import argparse
import html
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.gen_alchemy_assets import png
from tools.pill_art.render import catalogue, pill_icon, body_layer, support_layers, SUPPORTS


def export(out, url):
    out.mkdir(parents=True, exist_ok=True)
    entries = catalogue()
    names = dict(leaf="灵叶", dish="浅口瓷碟", casket="内衬丹匣")
    cards = []
    for kind, entry in entries.items():
        raw = png(pill_icon(kind))
        packaged = ROOT / f"src/main/resources/assets/myvillage/textures/item/pill_{kind}.png"
        if raw != packaged.read_bytes():
            raise ValueError(f"Regenerate resource owner first: {packaged}")
        (out / f"pill_{kind}.png").write_bytes(raw)
        (out / f"body_{kind}.png").write_bytes(png(body_layer(entry["body"])))
        src = html.escape(f"{url}/pill_{kind}.png", quote=True)
        name = html.escape(entry["name"])
        cards.append(f'<article><img class="big" src="{src}" alt="{name}"><h3>{name}</h3>'
                     f'<p class="muted">{names[entry["support"]]}</p><div class="small">'
                     f'<img src="{src}" width="16" height="16" alt="16像素"><img src="{src}" width="32" height="32" alt="32像素"></div></article>')
    for support in SUPPORTS:
        for part, rows in zip(("back", "front"), support_layers(support)):
            (out / f"{support}_{part}.png").write_bytes(png(rows))
    layers = "<span>＋</span>".join(f'<img src="{url}/{name}.png" alt="{label}">'
                                  for name, label in (("dish_back", "托座后层"), ("body_qingzhang", "独立丹体"), ("dish_front", "托座前沿")))
    page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>丹药托座与主体</title><style>
*{box-sizing:border-box}body{margin:0;color:var(--foreground,#ddd);font:14px/1.5 var(--font-sans,system-ui)}.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}article{padding:16px;border-radius:10px;background:var(--card,#202925);text-align:center}h3{font-size:16px;margin:4px 0}.big{display:block;width:128px;height:128px;max-width:100%;object-fit:contain;margin:auto}img{image-rendering:pixelated}.small{display:flex;align-items:center;justify-content:center;gap:14px;height:38px;background:#777;border-radius:4px}.muted{color:var(--muted-foreground,#aaa);font-size:12px}.layers{display:flex;gap:5px;align-items:center;justify-content:center;flex-wrap:wrap;background:var(--card,#202925);padding:16px;border-radius:10px}.layers img{width:80px;height:80px}a{color:var(--accent,#93c9ca)}@media(max-width:620px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.big{width:112px;height:112px}}
</style><body><p class="muted">丹药托座与主体 · 实际 32×32 资源预览（未作游戏内验收）</p><div class="grid">'''
    page += "".join(cards) + '</div><p>复用结构：托座后层 ＋ 独立丹体 ＋ 托座前沿 → 最终图标</p><div class="layers">'
    page += layers + f'<span>＝</span><img src="{url}/pill_qingzhang.png" alt="组合结果"></div>'
    page += '<p class="muted">托座区分种类的珍稀层级；下／中／上／极品继续使用现有品质提示，同种丹药共用本体。</p>'
    page += "<script>addEventListener('load',()=>console.log(JSON.stringify({loaded:[...document.images].every(i=>i.naturalWidth>0),overflow:document.documentElement.scrollWidth>innerWidth})))</script></body></html>"
    (out / "index.html").write_text(page)
    return out / "index.html"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=ROOT / "out/preview/pill-support-0843")
    parser.add_argument("--url", default="https://assets.ishiki333hhh.win/pill-support-0843")
    args = parser.parse_args()
    print(export(args.out, args.url.rstrip("/")))
