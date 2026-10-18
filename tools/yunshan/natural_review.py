#!/usr/bin/env python3
"""Build a static review page from an actual natural-world evidence run."""
from __future__ import annotations

import argparse
import html
import json
from pathlib import Path


def esc(value):
    return html.escape(str(value), quote=True)


def build(directory: Path):
    evidence = json.loads((directory / 'evidence.json').read_text())
    candidates = evidence['candidates']
    selected = evidence['selected_id']
    completed = evidence.get('completed', [])
    fit_manifest = evidence.get('fit_manifest', {}).get('path', 'fit/fit.json')
    manifest_path = directory / fit_manifest
    manifest = json.loads(manifest_path.read_text()) if manifest_path.is_file() else {}
    flat = evidence.get('source_variant') == 'flat_fallback_v1'
    selection_path = directory / 'selection.json'
    selection = json.loads(selection_path.read_text()) if selection_path.is_file() else {}
    fallback_note = ''
    if flat:
        primary = selection.get('primary', {})
        fallback_note = (f'<p class="intro">原台地版在 {primary.get("expanded_surveys", "未记录")} 个扩展地形窗口、'
                         f'{primary.get("fit_attempts", "未记录")} 次完整融合尝试后未找到合格地块；'
                         f'平地后备在 {selection.get("fit_attempts", "未记录")} 次尝试内选中此地。'
                         f'街面与四栋功能建筑外部入口统一为 Y {manifest.get("flat_ground_y", "未记录")}；'
                         '建筑内部台阶和屋顶保留。</p>')
    frames = evidence.get('screenshots', [])
    comparison_note = ''
    comparison_path = directory / 'comparison.json'
    if comparison_path.is_file():
        comparison = json.loads(comparison_path.read_text())
        candidates = comparison['candidates']
        frames = frames + comparison.get('screenshots', [])
        comparison_note = '<p class="intro">候选原生地形图复用同种子的先前实测；最终场地在修正后单独完成实机复验。每项来源保存在比较记录中。</p>'
    pairs = []
    for after in frames:
        if after['stage'] != 'after':
            continue
        before = next((frame for frame in frames if frame['stage'] == 'before'
                       and frame['candidate'] == after['candidate']
                       and frame['camera']['name'] == after['camera']['name']), None)
        if before:
            if before['camera'] != after['camera']:
                raise ValueError('Before/after cameras differ')
            pairs.append((before, after))
    cards = []
    for candidate in candidates:
        metrics = candidate['metrics']
        picture = next((frame['file'] for frame in frames
                        if frame['candidate'] == candidate['id'] and frame['stage'] == 'before'), None)
        plan = candidate.get('preview_paths', {}).get('preview.svg')
        if plan and not (directory / plan).is_file():
            plan = None
        fitting = candidate.get('fit_validation', {})
        if not fitting and candidate['id'] == selected:
            fitting = manifest.get('validation', {})
        fit_label = '通过' if fitting.get('passed') else '未通过'
        if fitting.get('checks', {}).get('outgoing_reaches_untouched_native_ground') is False:
            fit_label = '出城路未接通'
        refinement = ''
        if candidate.get('refined_from'):
            previous = candidate['refined_from']['origin']
            dx, dz = [candidate['origin'][axis] - previous[axis] for axis in (0, 1)]
            refinement = f'<p>实测后微调：ΔX {dx}，ΔZ {dz}；已重新评分与检查。</p>'
        cards.append(f'''<article class="candidate {'selected' if candidate['id'] == selected else ''}">
        <div class="cardtitle"><h3>选址 {candidate['rank']}</h3><span>{'已实机落位' if candidate['id'] == selected and 'sparse_natural_fit' in completed else '备选地形'}</span></div>
        {f'<a href="{esc(picture)}"><img src="{esc(picture)}" alt="候选原生地形实机图"></a>' if picture else ''}
        <p>西北角 {esc(candidate['origin'])} · 整体旋转 {candidate['rotation_quarters'] * 90}° · 水面 Y {candidate['water_surface']}</p>
        <dl><div><dt>平均填挖</dt><dd>{metrics['mean_earthwork']:.1f} 格</dd></div>
        <div><dt>原地面高程</dt><dd>{metrics['height_min']}–{metrics['height_max']}</dd></div>
        <div><dt>融合检查</dt><dd>{esc(fit_label)}</dd></div></dl>{refinement}
        {f'<a class="plan" href="{esc(plan)}">查看地形融合规划图 ↗</a>' if plan else ''}
        </article>''')
    comparisons = []
    for index, (before, after) in enumerate(pairs):
        comparisons.append(f'''<section class="comparison" {'hidden' if index else ''} data-index="{index}">
        <div class="pair"><figure><a href="{esc(before['file'])}"><img src="{esc(before['file'])}" alt="相同相机的原生地形"></a><figcaption>原生地形</figcaption></figure>
        <figure><a href="{esc(after['file'])}"><img src="{esc(after['file'])}" alt="相同相机的融合后实机画面"></a><figcaption>城镇融合后</figcaption></figure></div></section>''')
    camera_names = {'natural_overview': '城镇与周边山势', 'shore_and_slope': '湖岸与街区坡地'}
    options = ''.join(f'<option value="{index}">{esc(camera_names.get(after["camera"]["name"], after["camera"]["name"]))}</option>'
                      for index, (_, after) in enumerate(pairs))
    missing = [name for name in evidence.get('selected', []) if name not in completed]
    world = evidence.get('world_export')
    download = f'<a class="button" href="{esc(world["file"])}">下载完整自然世界 ZIP</a>' if world else ''
    candidate_file = 'comparison.json' if comparison_path.is_file() else 'selection.json' if selection_path.is_file() else 'top3.json' if (directory / 'top3.json').is_file() else 'candidates.json'
    candidates_title = '三处真实选址' if len(candidates) == 3 else '实测选址'
    title = '云山 · 平地后备落位' if flat else '云山 · 自然山水落位'
    heading = '城内一层平街，城外接回山水' if flat else '把城内高差，接回自然山水'
    intro = ('原方案在预算内找不到合格地块后，自动尝试平地变体：清理城镇占用范围的地表，统一街道与院落高度，'
             '整栋平移建筑，再用不规则缓坡、湖岸和出城路接回原生地形。地形安全限制与填挖预算仍然有效。'
             if flat else '保留城内建筑、街道和台地的相对关系；重做裁切山体的外围过渡、湖岸与出城路。')
    world_name = '云山 · 平地后备预览' if flat else '云山 · 自然山水融合预览'
    text = f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>{title}</title><style>
    *{{box-sizing:border-box}}body{{margin:0;background:#111a1b;color:#e9ede6;font:16px/1.6 system-ui,sans-serif}}main{{max-width:1420px;margin:auto;padding:44px 36px}}h1{{font-size:40px;margin:4px 0 12px;letter-spacing:.04em}}h2{{font-size:24px;margin:40px 0 16px}}h3,p,figure{{margin:0}}.eyebrow,dt,small{{color:#a8b9ae}}.intro{{max-width:880px;color:#bfcac0}}.status{{margin:20px 0;padding:12px 18px;background:#1e2c2b;border-left:3px solid #b9ce9a}}.cards{{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}}.candidate{{background:#1a2525;border:1px solid #334440;border-radius:10px;overflow:hidden}}.candidate.selected{{border-color:#b9ce9a}}.candidate p,.candidate dl,.plan{{margin:12px 18px}}.cardtitle{{padding:14px 18px;display:flex;align-items:center;justify-content:space-between}}.cardtitle span{{font-size:13px;color:#b9ce9a}}img{{display:block;width:100%;height:auto}}.candidate img{{aspect-ratio:16/9;object-fit:cover}}.candidate p{{font-size:13px;color:#b9c5bc}}dl{{display:flex;gap:18px;justify-content:space-between}}dt{{font-size:12px}}dd{{margin:2px 0;font-size:17px}}a{{color:#c8dfa8;text-decoration:none}}a:hover{{text-decoration:underline}}.plan{{display:inline-block;font-size:14px}}.pair{{display:grid;grid-template-columns:1fr 1fr;gap:16px}}figure{{background:#1a2525;border-radius:8px;overflow:hidden}}figcaption{{padding:8px 14px;color:#bdcbbf}}select{{background:#23332e;color:#e9ede6;border:1px solid #526456;border-radius:6px;padding:9px 12px;max-width:100%;margin-bottom:16px}}.button{{display:inline-block;background:#c3d5a7;color:#17221a;border-radius:6px;padding:11px 18px;margin:16px 0}}footer{{margin-top:35px;border-top:1px solid #34423a;padding-top:18px;font-size:13px;color:#9faea3}}.comparison[hidden]{{display:none}}@media(max-width:900px){{.cards{{grid-template-columns:1fr}}.pair{{grid-template-columns:1fr}}main{{padding:25px 18px}}h1{{font-size:30px}}}}
    </style><main><div class="eyebrow">云山 D1 · 独立普通世界 · 种子 {evidence['world_seed']}</div>
    <h1>{heading}</h1><p class="intro">{intro}以下地形来自真实 Minecraft 普通世界，备选规划图与已放置的实机画面分别标注。</p>{fallback_note}
    <div class="status">云端验证：{'通过' if evidence['status'] == 'pass' else esc(evidence['status'])} · 已完成 {len(completed)}/{len(evidence.get('selected', []))} 个采集阶段{f' · 尚缺 {esc(missing)}' if missing else ''}</div>
    <h2>同位置、同角度的前后对比</h2><select id="camera" aria-label="切换对比视角">{options}</select>{''.join(comparisons)}
    <h2>{candidates_title}</h2>{comparison_note}<div class="cards">{''.join(cards)}</div>
    <h2>进入完整世界</h2><p class="intro">使用 Minecraft 1.21.1、NeoForge 21.1.233 和 MyVillage {esc(evidence.get('mod_version', '0.63.1'))}。解压后将含 level.dat 的独立世界文件夹放入该实例的 saves 目录；世界名为“{world_name}”。保留独立目录，原来的云山世界和平坦预览世界保持原样。</p>{download}
    <footer>这些画面为云端软件渲染，本次为自动工具生成的地形融合预览，尚未接入普通存档的运行时自然生成。玩家外观、手感、音效、FPS 与广泛多人验收仍为 not_verified。<br>源提交 {esc(evidence.get('source_commit', '未记录'))} · 模组 {esc(evidence.get('mod_version', '未记录'))} · <a href="evidence.json">完整采集记录</a> · <a href="{esc(candidate_file)}">选址指标</a> · <a href="{esc(fit_manifest)}">融合清单</a></footer>
    </main><script>document.querySelector('#camera').addEventListener('change',event=>{{document.querySelectorAll('.comparison').forEach(node=>node.hidden=node.dataset.index!==event.target.value)}});</script></html>'''
    (directory / 'index.html').write_text(text, encoding='utf-8')
    return directory / 'index.html'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    print(build(parser.parse_args().directory))
