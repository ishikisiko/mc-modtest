"""从正式 npcgen 组合生成中文衣装／发色对照页，不依赖历史 reports 脚本。"""
from __future__ import annotations

import argparse
from concurrent.futures import ProcessPoolExecutor
import hashlib
import html
import json
from pathlib import Path

from tools.beastgen.preview import Frame, Mesh, render, rows_to_array
from tools.combat_preview import sheets
from tools import gen_qingfeng_sword_model as pngio
from . import catalog, recolour, roles
from .humanoid import _rgb
from .preview import Npc

VIEWS = {'front': ('正面', (0, .08, -1)), 'q_front': ('左前', (-.85, .28, -1)),
         'side': ('左侧', (-1, .08, 0)), 'q_back': ('后侧', (.85, .28, 1))}
# 用户保留的四种发色；原配色是 painter 的 HAIR，不等同于运行时 ink_blue。
COLOURS = {'ink': {'title': '墨蓝 · 原配色', 'palette': None},
           'chestnut': {'title': '栗棕', 'palette': 'chestnut'},
           'silver': {'title': '银灰', 'palette': 'silver'},
           'violet': {'title': '灰紫', 'palette': 'grey_ink_violet'}}


def colour_texture(built, palette):
    """复用正式换色器，只比较头发；瞳色保留模型原值。"""
    eyes = [_rgb(getattr(built.d, name)) for name in ('IRIS_TOP', 'IRIS', 'IRIS_LOW')]
    ramp = recolour.hair_ramp7(recolour.hair_ramp5(palette)) if palette else built.d.HAIR
    pixels = recolour.compose_with(built.texture, built.roles, ramp, eyes)
    if pixels != roles.repaint(built, ramp, eyes):
        raise ValueError(f'{built.d.LOOK}: 换色与 painter 重绘不一致')
    if any(p != built.texture[y][x]
           for y, row in enumerate(pixels) for x, p in enumerate(row)
           if built.roles[y][x][0] != roles.MATERIAL_HAIR):
        raise ValueError(f'{built.d.LOOK}: 换色改动了非头发纹素')
    if palette is None and pixels != built.texture:
        raise ValueError(f'{built.d.LOOK}: 原配色未保持')
    return pixels


def render_variant(task):
    spec, out = task
    npc = Npc('cultivator_' + spec['look'])
    if npc.stale:
        raise RuntimeError(f'正式资源未生成：{npc.stale}')
    colours = {}
    for key, colour in COLOURS.items():
        # 保留原配色的旧文件路径，方便已有预览引用。
        folder = out / spec['look']
        if key != 'ink':
            folder /= key
        folder.mkdir(parents=True, exist_ok=True)
        pixels = colour_texture(npc.built, colour['palette'])
        npc.mesh = Mesh(npc.model, rows_to_array(pixels), None, scale=npc.model.scale)
        quads = npc.quads()
        for view, (_, direction) in VIEWS.items():
            render(quads, Frame(direction, 360, 580, 245, (0, 1, 0), ss=2),
                   cull=False, ground=False).save(folder / (view + '.png'))
        colours[key] = {'texture_sha256': hashlib.sha256(pngio.encode_png(pixels)).hexdigest(),
                        'repaint_parity': True, 'non_hair_unchanged': True}
    print(spec['look'], flush=True)
    return {**spec, 'colours': colours,
            'output_sha256': {p.name: hashlib.sha256(data).hexdigest()
                              for p, data in npc.built.outputs().items()}}


def page_html(outfits, asset_base):
    page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>女修发色与衣装对照</title>
<style>body{font:15px var(--font-sans,system-ui);color:var(--foreground,#ddd);margin:0}p{line-height:1.6}.controls{display:flex;gap:12px;flex-wrap:wrap;margin:14px 0}label{display:grid;gap:5px}select{font:inherit;padding:7px;border:1px solid var(--border,#555);border-radius:6px;background:var(--card,#222);color:inherit}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.outfits{grid-template-columns:repeat(3,minmax(0,1fr))}figure{margin:0;min-width:0}img{display:block;width:100%;height:330px;object-fit:contain;background:#bfc4ca;border-radius:8px}figcaption{padding:8px 0;color:var(--muted-foreground,#aaa)}.note{font-size:13px;color:var(--muted-foreground,#aaa)}[hidden]{display:none!important}@media(max-width:500px){img{height:260px}.outfits{grid-template-columns:repeat(2,minmax(0,1fr))}.controls{gap:8px}select{max-width:170px}}</style>
<p><strong>墨蓝、栗棕、银灰、灰紫</strong><br>四种保留发色 × 三张脸 × 四组发型与刘海 × 六套衣装。切换对照方式，可比较同一衣装的发色，或同一发色的六套衣装。</p>
<div class="controls"><label>对照方式<select id="compare"><option value="colours">比较发色</option><option value="outfits">比较衣装</option></select></label>
<label>脸型<select id="face"><option value="01">01 · 低位短脸</option><option value="02" selected>02 · 窄眼无嘴</option><option value="03">03 · 柔和带嘴</option></select></label>
<label>发型与刘海<select id="hair"><option value="a">低马尾 · 原刘海</option><option value="b">高马尾 · 偏分</option><option value="c">半束长发 · 轻中分</option><option value="d">低盘髻 · 短碎</option></select></label>
<label id="outfit-label">衣装<select id="outfit">OUTFIT_OPTIONS</select></label>
<label id="colour-label" hidden>发色<select id="colour">COLOUR_OPTIONS</select></label>
<label>角度<select id="view"><option value="q_front">左前</option><option value="front">正面</option><option value="side">左侧</option><option value="q_back">后侧</option></select></label></div>
<div id="cards" class="grid"></div>
<p class="note">换色时保留原瞳色、衣服和发饰，便于单独比较头发。墨蓝为模型原配色；其余三色使用人物换色调色板。此页为离线预览。</p>
<script>const base=ASSET_BASE;const outfits=OUTFITS;const colours=COLOURS;
const field=id=>document.getElementById(id);
function update(){const byColour=field('compare').value==='colours';field('outfit-label').hidden=!byColour;field('colour-label').hidden=byColour;const cards=field('cards');cards.classList.toggle('outfits',!byColour);cards.replaceChildren();
for(const [key,config] of Object.entries(byColour?colours:outfits)){const outfit=byColour?field('outfit').value:key;const colour=byColour?key:field('colour').value;const look=`f_novice_${field('face').value}_${field('hair').value}_${outfit}`;const figure=document.createElement('figure');const img=document.createElement('img');img.alt=outfits[outfit].title+' · '+colours[colour].title;img.dataset.look=look;img.dataset.colour=colour;img.src=base+look+'/'+(colour==='ink'?'':colour+'/')+field('view').value+'.png';img.onerror=()=>console.error('图片加载失败',img.src);const caption=document.createElement('figcaption');caption.textContent=config.title;figure.append(img,caption);cards.append(figure);}}
for(const el of document.querySelectorAll('select'))el.addEventListener('change',update);update();</script></html>'''
    def options(items):
        return ''.join(f'<option value="{html.escape(key)}">{html.escape(config["title"])}</option>'
                       for key, config in items.items())
    return (page.replace('OUTFIT_OPTIONS', options(outfits)).replace('COLOUR_OPTIONS', options(COLOURS))
            .replace('ASSET_BASE', json.dumps(asset_base)).replace('OUTFITS', json.dumps(outfits, ensure_ascii=False))
            .replace('COLOURS', json.dumps(COLOURS, ensure_ascii=False)))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=catalog.ROOT / 'out/preview/npc_variants')
    parser.add_argument('--asset-base', default='')
    parser.add_argument('--workers', type=int, default=1, help='离线渲染进程数')
    args = parser.parse_args()
    if args.workers < 1:
        parser.error('--workers 必须为正整数')
    args.out.mkdir(parents=True, exist_ok=True)
    with ProcessPoolExecutor(max_workers=args.workers) as pool:
        records = list(pool.map(render_variant, ((spec, args.out) for spec in catalog.variants())))
    outfits = catalog.load()['outfits']
    # 所有保留衣装都列出，不能由页面单独缩减目录。行走图保留原配色。
    for outfit, config in outfits.items():
        npc = Npc('cultivator_f_novice_02_a_' + outfit)
        frames = []
        for i in range(8):
            im = render(npc.quads('walk', i / 8), Frame((-.85, .28, -1), 240, 390, 170, (0, 1, 0)), cull=False)
            frames.append(sheets.labelled(im, [f'行走 {i + 1}/8'], 240))
        sheets.grid([frames[:4], frames[4:]], [config['title']]).save(args.out / (outfit + '-walk.png'))
    (args.out / 'index.html').write_text(page_html(outfits, args.asset_base), encoding='utf-8')
    (args.out / 'manifest.json').write_text(json.dumps(
        {'variants': records, 'colours': COLOURS, 'eyes': 'baked', 'evidence_tier': 'offline_render'},
        ensure_ascii=False, indent=2), encoding='utf-8')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
