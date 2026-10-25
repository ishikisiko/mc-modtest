"""从正式 npcgen 组合生成中文对照页，不依赖历史 reports 脚本。"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image
from tools.beastgen.preview import Frame, render
from tools.combat_preview import sheets
from . import catalog
from .preview import Npc

VIEWS = {'front': ('正面', (0, .08, -1)), 'q_front': ('左前', (-.85, .28, -1)),
         'side': ('左侧', (-1, .08, 0)), 'q_back': ('后侧', (.85, .28, 1))}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=catalog.ROOT / 'out/preview/npc_variants')
    parser.add_argument('--asset-base', default='')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    records = []
    for spec in catalog.variants():
        npc = Npc('cultivator_' + spec['look'])
        if npc.stale:
            raise RuntimeError(f'正式资源未生成：{npc.stale}')
        folder = args.out / spec['look']
        folder.mkdir(exist_ok=True)
        for view, (_, direction) in VIEWS.items():
            render(npc.quads(), Frame(direction, 360, 580, 245, (0, 1, 0), ss=2),
                   cull=False, ground=False).save(folder / (view + '.png'))
        records.append({**spec, 'output_sha256': {p.name: hashlib.sha256(data).hexdigest()
                                                for p, data in npc.built.outputs().items()}})
        print(spec['look'], flush=True)
    # 两种衣装的运动姿态接触表，供检查裙片与裤腿连续性。
    for outfit in ('pants', 'robe'):
        name = 'f_novice_02_a_' + outfit
        npc = Npc('cultivator_' + name)
        frames = []
        for i in range(8):
            im = render(npc.quads('walk', i / 8), Frame((-.85, .28, -1), 240, 390, 170, (0, 1, 0)), cull=False)
            frames.append(sheets.labelled(im, [f'行走 {i + 1}/8'], 240))
        sheets.grid([frames[:4], frames[4:]], ['A · 短打裤装' if outfit == 'pants' else 'B · 束袖长衣']).save(args.out / (outfit + '-walk.png'))
    page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font:15px var(--font-sans,system-ui);color:var(--foreground,#ddd);margin:0}p{line-height:1.6}.controls{display:flex;gap:12px;flex-wrap:wrap;margin:14px 0}label{display:grid;gap:5px}select{font:inherit;padding:7px;border:1px solid var(--border,#555);border-radius:6px;background:var(--card,#222);color:inherit}.pair{display:grid;grid-template-columns:1fr 1fr;gap:12px}figure{margin:0;min-width:0}img{display:block;width:100%;max-height:500px;object-fit:contain;background:#bfc4ca;border-radius:8px}figcaption{padding:8px 0;color:var(--muted-foreground,#aaa)}code{font-size:11px;overflow-wrap:anywhere}.note{font-size:13px;color:var(--muted-foreground,#aaa)}@media(max-width:500px){img{max-height:340px}.controls{gap:8px}select{max-width:170px}}</style>
<p><strong>女修组合已进入正式生成器</strong><br>三张脸、四组发型与配套刘海、两套衣装。下方读取本次正式生成资源的离线画面。</p>
<div class="controls"><label>脸型<select id="face"><option value="01">01 · 低位短脸</option><option value="02" selected>02 · 窄眼无嘴</option><option value="03">03 · 柔和带嘴</option></select></label>
<label>发型与刘海<select id="hair"><option value="a">低马尾 · 原刘海</option><option value="b">高马尾 · 偏分</option><option value="c">半束长发 · 轻中分</option><option value="d">低盘髻 · 短碎</option></select></label>
<label>角度<select id="view"><option value="q_front">左前</option><option value="front">正面</option><option value="side">左侧</option><option value="q_back">后侧</option></select></label></div>
<div class="pair"><figure><img id="pants" alt="A 短打裤装"><figcaption>A · 短打裤装<br><code id="pants-id"></code></figcaption></figure><figure><img id="robe" alt="B 束袖长衣"><figcaption>B · 束袖长衣<br><code id="robe-id"></code></figcaption></figure></div>
<p class="note">旧外观 ID 保留；命簿人物稳定选择组合。发色、瞳色沿用人物换色系统。此页为离线预览，原生验证结果另见交付说明。</p>
<script>const base=ASSET_BASE;function update(){for(const outfit of ['pants','robe']){const id=`f_novice_${document.querySelector('#face').value}_${document.querySelector('#hair').value}_${outfit}`;document.getElementById(outfit).src=base+id+'/'+document.querySelector('#view').value+'.png';document.getElementById(outfit+'-id').textContent=id;}}for(const el of document.querySelectorAll('select'))el.addEventListener('change',update);for(const im of document.images)im.onerror=()=>console.error('图片加载失败',im.src);update();</script></html>'''
    page = page.replace('ASSET_BASE', json.dumps(args.asset_base))
    (args.out / 'index.html').write_text(page)
    (args.out / 'manifest.json').write_text(json.dumps({'variants': records, 'evidence_tier': 'offline_render'}, ensure_ascii=False, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
