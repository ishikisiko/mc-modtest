#!/usr/bin/env python3
"""Build and review independent Qianzhen scene blueprints from authored DSL inputs."""
from __future__ import annotations

import argparse
from collections import Counter, deque
import hashlib
import html
import json
from pathlib import Path
import shutil
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import json_to_nbt as nbt
import voxel_dump_to_structures as export
from buildgen.modset import load_modset
from validate_structure_json import load_block_registry
from validate_blueprint import validate_blueprint
from yunshan.build_inn import attach_signs, verify_exports, check_roof, check_wall_roof_joins

SOURCE = ROOT / 'blueprints/qianzhen'
DIRECTIONS = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def block_at(blocks, pos):
    return nbt.parse_block_state(blocks.get(tuple(pos), 'minecraft:air'))


def check_walks(blocks, targets, size):
    """Conservative feet/head graph with opened doors and stair-only elevation changes."""
    def empty(p):
        name, _ = block_at(blocks, p)
        return name in ('minecraft:air', 'minecraft:cave_air') or name.endswith('_door')

    def support(p):
        name, props = block_at(blocks, p)
        if empty(p) or name in ('minecraft:water', 'minecraft:lava'):
            return False
        if any(word in name for word in ('fence', 'bed', 'lantern', 'sign', 'pressure_plate',
                                         'torch', 'ladder', 'chain', 'potted_', 'trapdoor')):
            return False
        if name.endswith('_slab') and props.get('type') != 'double':
            return False
        return True

    nodes = {(x, y, z) for x in range(size[0]) for z in range(size[2]) for y in range(1, size[1]-1)
             if empty((x, y, z)) and empty((x, y+1, z)) and support((x, y-1, z))}
    start = tuple(targets['entry'])
    if start not in nodes:
        raise ValueError('entry is obstructed or unsupported')
    seen, todo = {start}, deque([start])
    for name, p in targets.items():
        nbt.validate_pos(p, size, 'walk_targets.' + name)
    while todo:
        x, y, z = todo.popleft()
        for direction, (dx, dz) in DIRECTIONS.items():
            for dy in (0, 1, -1):
                p = (x+dx, y+dy, z+dz)
                if p not in nodes or p in seen:
                    continue
                if dy:
                    high = p if dy > 0 else (x, y, z)
                    block, props = block_at(blocks, (high[0], high[1]-1, high[2]))
                    ascent = direction if dy > 0 else next(k for k, v in DIRECTIONS.items() if v == (-dx, -dz))
                    if not block.endswith('_stairs') or props.get('facing') != ascent or props.get('half', 'bottom') != 'bottom':
                        continue
                    low = (x, y, z) if dy > 0 else p
                    if not empty((low[0], high[1]+1, low[2])):
                        continue
                seen.add(p)
                todo.append(p)
    missing = [name for name, pos in targets.items() if tuple(pos) not in seen]
    if missing:
        raise ValueError('unreachable walk targets: ' + ', '.join(missing))
    return {'targets': {name: True for name in targets}, 'reachable_cells': len(seen),
            'method': 'conservative block topology, doors opened, stair-only ascent; not native collision'}


def check_pairs_and_supports(blocks):
    counts = Counter()
    errors = []
    for (x, y, z), state in blocks.items():
        name, p = nbt.parse_block_state(state)
        if name.endswith('_door'):
            counts['door_halves'] += 1
            upper = p.get('half') == 'upper'
            other, op = block_at(blocks, (x, y-1 if upper else y+1, z))
            if other != name or op.get('half') != ('lower' if upper else 'upper') or any(op.get(k) != p.get(k) for k in ('facing', 'hinge', 'open', 'powered')):
                errors.append(f'orphan/mismatched door at {(x,y,z)}')
        if name.endswith('_bed'):
            counts['bed_halves'] += 1
            dx, dz = DIRECTIONS.get(p.get('facing'), (0, 0))
            head = p.get('part') == 'head'
            sign = -1 if head else 1
            other, op = block_at(blocks, (x+sign*dx, y, z+sign*dz))
            if other != name or op.get('part') != ('foot' if head else 'head') or op.get('facing') != p.get('facing'):
                errors.append(f'orphan/mismatched bed at {(x,y,z)}')
        if name.endswith('_wall_sign'):
            counts['wall_signs'] += 1
            dx, dz = DIRECTIONS.get(p.get('facing'), (0, 0))
            if block_at(blocks, (x-dx, y, z-dz))[0].endswith(':air'):
                errors.append(f'unsupported wall sign at {(x,y,z)}')
        if name in ('minecraft:lantern', 'minecraft:soul_lantern'):
            counts['lanterns'] += 1
            sy = y+1 if p.get('hanging') == 'true' else y-1
            if block_at(blocks, (x, sy, z))[0] in ('minecraft:air', 'minecraft:water'):
                errors.append(f'unsupported lantern at {(x,y,z)}')
        if name == 'minecraft:water':
            counts['water_cells'] += 1
            for dx, dy, dz in [(0,-1,0), (1,0,0), (-1,0,0), (0,0,1), (0,0,-1)]:
                if block_at(blocks, (x+dx, y+dy, z+dz))[0] in ('minecraft:air', 'minecraft:cave_air'):
                    errors.append(f'water opens into air at {(x,y,z)} toward {(dx,dy,dz)}')
    if errors:
        raise ValueError('; '.join(errors[:30]))
    return dict(counts)


def validate(data, maximum):
    size, _, blocks, _ = nbt.expand_structure(data)
    if any(size[i] > maximum[i] for i in range(3)):
        raise ValueError(f'size {size} exceeds independent blueprint boundary {maximum}')
    if not data.get('fill_air'):
        raise ValueError('fill_air must be explicit for independent exports')
    palette = sorted(set(blocks.values()))
    illegal = load_modset('vanilla').palette_block_errors(palette)
    if illegal:
        raise ValueError(str(illegal))
    vanilla = load_block_registry()
    models = {}
    for state in palette:
        name, props = nbt.parse_block_state(state)
        ns, bare = name.split(':', 1)
        if ns == 'minecraft' and bare not in vanilla:
            raise ValueError('unknown vanilla block: ' + name)
        if ns == 'myvillage':
            state_path = ROOT / 'src/main/resources/assets/myvillage/blockstates' / (bare+'.json')
            if not state_path.exists():
                raise ValueError('unknown mod block: ' + name)
            states = json.loads(state_path.read_text())
            if 'variants' in states:
                permitted = [dict(pair.split('=') for pair in key.split(',')) if key else {} for key in states['variants']]
                if props not in permitted:
                    raise ValueError('unknown mod blockstate: ' + state)
            model_path = ROOT / 'src/main/resources/assets/myvillage/models/block' / (bare+'.json')
            if model_path.exists():
                model = json.loads(model_path.read_text())
                if model.get('elements'):
                    models[name] = {'elements': model['elements'], 'textures': model.get('textures', {})}
    walks = check_walks(blocks, data['walk_targets'], size)
    anchors = data.get('story_anchors', [])
    ids = [a['id'] for a in anchors]
    if not ids or len(ids) != len(set(ids)):
        raise ValueError('story anchors must be present with unique ids')
    for a in anchors:
        if a['walk_target'] not in data['walk_targets']:
            raise ValueError('unknown story walk target: ' + a['id'])
        if 'pos' in a:
            nbt.validate_pos(a['pos'], size, 'anchor.' + a['id'])
    for s in data.get('signs', []):
        if not block_at(blocks, s['pos'])[0].endswith('_sign') or len(s['messages']) != 4:
            raise ValueError('invalid sign metadata: ' + str(s))
    pairs = check_pairs_and_supports(blocks)
    roofs = [check_roof(blocks, spec, size) for spec in data.get('roof_checks', [])]
    joins = check_wall_roof_joins(blocks, data.get('wall_roof_joins', []))
    return blocks, models, {'walks': walks, 'pairs_supports_water': pairs, 'story_anchors': len(anchors),
                           'roof_sections': roofs, 'wall_roof_contacts': joins,
                           'palette_ids': 'checked against repository 1.21.1 registry and modset',
                           'vanilla_properties': 'syntax checked; native state registry not exercised'}


def render_viewer(data, blocks, models, out):
    payload = {'id': data['id'].split(':')[1], 'size': data['size'],
               'blocks': [[*p, s] for p, s in blocks.items() if s != 'minecraft:air'],
               'models': models, 'signs': data.get('signs', []), 'site': data['site'],
               'presentation': data['presentation'], 'services': data.get('service_blocks', []),
               'anchors': data['story_anchors'], 'walkTargets': data['walk_targets']}
    page = (ROOT / 'tools/qianzhen/viewer.html').read_text()
    page = page.replace('/*DATA*/', json.dumps(payload, ensure_ascii=False))
    page = page.replace('/*THREE*/', (ROOT / 'tools/web/three.min.js').read_text())
    page = page.replace('/*ORBIT*/', (ROOT / 'tools/web/OrbitControls.js').read_text())
    (out / 'index.html').write_text(page)


def build_one(row, out):
    source = SOURCE / row['source']
    data = json.loads(source.read_text())
    expected = 'myvillage:qianzhen_' + row['id'] + '_v1'
    if data['id'] != expected:
        raise ValueError(f'expected {expected}')
    blocks, models, checks = validate(data, row['max_size'])
    folder = out / row['id']
    folder.mkdir(parents=True, exist_ok=True)
    stem = folder / data['id'].split(':')[1]
    structure = export.Structure(blocks)
    blueprint = export.build_blueprint(structure, data['id'], {'source': str(source.relative_to(ROOT)),
        'source_sha256': sha(source), 'story_anchors': data['story_anchors'], 'status': 'standalone_blueprint_candidate'})
    errors = validate_blueprint(blueprint)
    if errors:
        raise ValueError(str(errors))
    export.write_blueprint(blueprint, stem.with_suffix('.blueprint.json'))
    root = nbt.structure_json_to_root_nbt(data)
    schem, _ = export.build_schem(structure, export.DEFAULT_DATA_VERSION)
    attach_signs(root, schem, data.get('signs', []))
    nbt.write_gzipped_nbt(root, str(stem.with_suffix('.nbt')))
    export.write_gzipped_nbt_ext(schem, stem.with_suffix('.schem'), 'Schematic')
    verify_exports(blocks, stem.with_suffix('.nbt'), stem.with_suffix('.schem'), data.get('signs', []))
    (folder / 'authored.json').write_bytes(source.read_bytes())
    (folder / 'anchors.json').write_text(json.dumps({'story': data['story_anchors'], 'walk_targets': data['walk_targets']}, ensure_ascii=False, indent=2)+'\n')
    report = {**row, 'name': data['presentation']['name'], 'size': data['size'],
              'non_air_blocks': sum(s != 'minecraft:air' for s in blocks.values()),
              'palette_count': len(set(blocks.values())), 'source_sha256': sha(source), 'checks': checks,
              'export_roundtrip': 'every cell and sign matches NBT and Sponge v2',
              'not_verified': ['native_game_placement', 'native_collision', 'NPC_and_quest_runtime', 'owner_aesthetic_acceptance', 'FPS']}
    render_viewer(data, blocks, models, folder)
    report['export_sha256'] = {p.name: sha(p) for p in folder.iterdir() if p.suffix in ('.nbt', '.schem')}
    (folder / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    return report


def overview(manifest, reports, out):
    rects = []
    for r in reports:
        x, _, z = r['origin']; w, _, d = r['size']
        rects.append((r, x, z, w, d))
    for i, (r, x, z, w, d) in enumerate(rects):
        for s, a, b, c, e in rects[i+1:]:
            if not (x+w <= a or a+c <= x or z+d <= b or b+e <= z):
                raise ValueError('concept plot overlap: '+r['id']+' / '+s['id'])
    by_id = {r['id']: (x+w/2, z+d/2) for r, x, z, w, d in rects}
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="-12 -70 265 250"><rect x="-12" y="-70" width="265" height="250" fill="#ebe4d3"/>']
    for a, b in manifest['connections']:
        if a in by_id and b in by_id:
            x, z = by_id[a]; u, v = by_id[b]
            svg.append(f'<path d="M{x},{z} L{u},{v}" stroke="#b9a781" stroke-width="3" fill="none" stroke-dasharray="3 2"/>')
    for r, x, z, w, d in rects:
        svg.append(f'<rect x="{x}" y="{z}" width="{w}" height="{d}" rx="2" fill="{r["color"]}"/>')
        svg.append(f'<text x="{x+w/2}" y="{z+d/2}" fill="white" font-size="5" text-anchor="middle">{html.escape(r["name"])}</text>')
    svg.append('<text x="238" y="-58" font-size="6" text-anchor="end" fill="#445645">北 ↑</text></svg>')
    (out / 'layout.svg').write_text(''.join(svg))
    (out / 'layout.json').write_text(json.dumps({'scope': 'relative scene staging only; paths are diagrammatic, not built town roads',
         'locations': [{'id':r['id'],'origin':r['origin'],'size':r['size']} for r in reports],
         'connections':manifest['connections']},ensure_ascii=False,indent=2)+'\n')
    cards = []
    for r in reports:
        cards.append(f'<article><a href="{r["id"]}/index.html"><img src="{r["id"]}/full.png" alt="{html.escape(r["name"])}模型全貌"><h2>{html.escape(r["name"])}</h2></a><p>{html.escape(r["act"])} · {r["size"][0]} × {r["size"][2]} 格</p><p>{r["checks"]["story_anchors"]} 个剧情位置 · {r["non_air_blocks"]:,} 个非空气方块</p><a href="{r["id"]}/index.html">旋转模型 / 剖切 / 独栋下载 →</a></article>')
    page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>阡镇 · 六处故事地点蓝图</title><style>*{box-sizing:border-box}body{margin:0;background:#e9e3d4;color:#304236;font:15px/1.8 system-ui,sans-serif}main{max-width:1200px;margin:auto;padding:24px}h1{font-size:28px;margin:0 0 10px}p{margin:8px 0}a{color:#456e61}.cards{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin:24px 0}article{background:#faf7ec;border:1px solid #d1cbb8;border-radius:9px;padding:16px}article img{width:100%;border-radius:5px}article h2{margin:10px 0 3px;font-size:20px}article p,article a{font-size:13px}.plan{display:grid;grid-template-columns:1fr 1fr;gap:24px;align-items:center}.plan img{width:100%;max-height:460px}.note{padding:16px;border-left:3px solid #9c8658;background:#f4efdf;font-size:13px}.download{display:inline-block;padding:9px 15px;background:#416451;color:#fff;border-radius:5px;margin-top:10px;text-decoration:none}@media(max-width:650px){main{padding:15px}.cards,.plan{grid-template-columns:1fr}}</style><main><h1>阡镇 · 灯尽之前</h1><p>六处独立故事地点：生活院落、泉渠药圃、旧驿调查与下沉古阵。每处包含可导入的 NBT / SCHEM、authored 输入与剧情位置约定。</p><div class="note">画面来自真实蓝图方块的离线三维预览。材质为示意色，家具轮廓有简化；不代表原生游戏截图或玩家外观验收。城镇尚未接入自然生成、出生、商人或任务。</div><a class="download" href="qianzhen_blueprints.zip">下载六处蓝图包</a><div class="cards">'''+''.join(cards)+'''</div><div class="plan"><img src="layout.svg" alt="六处地点相对动线草图"><div><h2>从落脚到发现旧阵</h2><p>阡陌驿 → 长明药庐 → 青泉渠圃 → 沙埋旧驿 → 护泉古阵；问灵小院是可选入门支线。</p><p>这是相对地块与剧情顺序图。虚线是动线意图，尚不是可导入的整镇道路或自然世界坐标；独栋内部路线已单独检查。</p><p>导入应在独立世界或存档副本进行。每栋含显式空气，默认低 z 朝前，原点为地块最小角；没有改写现有存档。</p></div></div><p><a href="batch-report.json">蓝图检查记录</a> · <a href="browser-check.json">离线渲染记录</a> · <a href="SHA256SUMS.txt">下载哈希</a></p></main></html>'''
    page = page.replace('<style>', "<style>@font-face{font-family:QianBody;src:url(assets/panel_body.ttf) format('truetype');font-display:block}")
    page = page.replace('system-ui,sans-serif', 'QianBody,system-ui,sans-serif')
    (out / 'index.html').write_text(page)


def package(out):
    paths = sorted(p for p in out.rglob('*') if p.is_file()
                   and p.suffix in ('.nbt','.schem','.json','.png','.svg','.html','.ttf','.txt')
                   and p.name not in ('preview.json', 'SHA256SUMS.txt'))
    with zipfile.ZipFile(out / 'qianzhen_blueprints.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        for p in paths:
            z.write(p, p.relative_to(out))
        z.write(SOURCE / 'README.md', 'README.md')
        z.write(SOURCE / 'manifest.json', 'authored/manifest.json')
    sums = [f'{sha(p)}  {p.relative_to(out)}' for p in sorted(out.rglob('*')) if p.is_file() and p.suffix in ('.zip','.nbt','.schem')]
    (out / 'SHA256SUMS.txt').write_text('\n'.join(sums)+'\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT/'out/preview/qianzhen-blueprints')
    parser.add_argument('--only', choices=['relay_inn','apothecary','canal_gardens','old_relay','spring_array','initiation_court'])
    parser.add_argument('--source-id', default='working-tree')
    parser.add_argument('--package-only', action='store_true')
    args = parser.parse_args()
    if args.package_only:
        package(args.out);return
    manifest = json.loads((SOURCE / 'manifest.json').read_text())
    reports = []
    args.out.mkdir(parents=True, exist_ok=True)
    assets = args.out / 'assets'
    assets.mkdir(exist_ok=True)
    for name in ('panel_body.ttf', 'panel-body-notice.txt', 'panel-body-apache-2.0.txt'):
        shutil.copyfile(ROOT / 'src/main/resources/assets/myvillage/font' / name, assets / name)
    for row in manifest['locations']:
        if args.only and row['id'] != args.only:
            continue
        report = build_one(row, args.out)
        reports.append(report)
        print(json.dumps({'id':row['id'],'non_air':report['non_air_blocks'],'walks':len(report['checks']['walks']['targets'])},ensure_ascii=False),flush=True)
    overview(manifest, reports, args.out)
    result = {'source_id':args.source_id, 'status':'passed', 'selected':[r['id'] for r in reports],
              'completed':[r['id'] for r in reports], 'unrun':[], 'buildings':reports,
              'tool_hashes':{str(p.relative_to(ROOT)):sha(p) for p in [Path(__file__),ROOT/'tools/qianzhen/viewer.html',SOURCE/'manifest.json']},
              'scope':'offline blueprint validation and export; browser captures recorded separately'}
    (args.out / 'batch-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    package(args.out)


if __name__ == '__main__':
    main()
