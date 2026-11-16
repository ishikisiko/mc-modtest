"""独立三维草案：复用整城构件与查看器，不写入正式城市生成。"""
from pathlib import Path
import argparse, base64, copy, gc, gzip, hashlib, json, math, struct, subprocess, sys, time

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools'))
from province_capitals.blueprint_plan.generate import generate, preview_scene, html
from qianji.model import Model
from qianji.preview import boxes as qboxes, PALETTE

from province_capitals.blueprint_plan.study_surfaces import apply_surfaces, amend_viewer
viewer_source = (ROOT / 'tools/province_capitals/blueprint_plan/viewer.js').read_text()
template = (ROOT / 'tools/province_capitals/blueprint_plan/template.html').read_text()
def save():
    (OUT / 'build-status.json').write_text(json.dumps(progress, ensure_ascii=False, indent=2))

def inside(x, z, box, margin=0):
    return box[0]-margin <= x < box[2]+margin and box[1]-margin <= z < box[3]+margin

def build(p):
    started = time.monotonic()
    region = p['region']
    cached = CACHE / (region + '.json.gz')
    if CACHE_VALID and cached.exists():
        with gzip.open(cached, 'rt') as f:
            cached_data = json.load(f)
        data, scene = cached_data['data'], cached_data['scene']
    else:
        planner, data = generate(ROOT / 'blueprints/province_capitals/blueprint_plan' / (region + '.json'))
        scene = preview_scene(planner, data)
        with gzip.open(cached, 'wt') as f:
            json.dump({'data': data, 'scene': scene}, f, ensure_ascii=False, separators=(',', ':'))
        del planner
        gc.collect()
    width, _, depth = data['dimensions']
    ground = lambda x, z: data['terrain'][max(0, min(depth-1, int(z))) * width + max(0, min(width-1, int(x)))]
    by_id = {b['id']: b for b in data['plots']}
    before = {b['id']: b for b in p['before']}
    after = {b['id']: b for b in p['after']}
    moved = {}
    for m in p['house_moves']:
        b = by_id[m['id']]['building']
        dx, dz = m['to'][0]-m['from'][0], m['to'][1]-m['from'][1]
        new_box = m['to']
        old_box = m['from']
        new_ground = max(ground(x,z) for z in range(new_box[1],new_box[3]) for x in range(new_box[0],new_box[2]))
        old_ground = max(ground(x,z) for z in range(old_box[1],old_box[3]) for x in range(old_box[0],old_box[2]))
        dy = new_ground - old_ground
        moved[m['id']] = [dx, dy, dz]
        assert [m['to'][2]-m['to'][0], m['to'][3]-m['to'][1]] == [m['from'][2]-m['from'][0], m['from'][3]-m['from'][1]]
    retired = set(p['proposed_retired_houses'])
    removed_gardens = set(g['id'] for g in data['gardens'] if g.get('owner') in retired)
    affected_courts = set(c for q in p['qianji'] for c in q['courts'])
    garden_shifts = {g['id']: moved[g['owner']] for g in data['gardens'] if g.get('owner') in moved}
    shifts = {**moved, **garden_shifts}
    removed_tags = retired | removed_gardens | affected_courts
    transformed = []
    for b in scene['boxes']:
        if b['tag'] in removed_tags:
            continue
        if b['tag'] in shifts:
            b = {**b, 'p': [v+d for v, d in zip(b['p'], shifts[b['tag']])]}
        if b['layer'] in ('roads', 'road', 'road_edge') and any(inside(b['p'][0], b['p'][2], q['box']) for q in p['qianji']):
            continue
        transformed.append(b)
    scene['boxes'] = transformed
    for plot in data['plots']:
        if plot['id'] not in moved:
            continue
        delta = moved[plot['id']]
        b = plot['building']
        for key in ('position', 'entrance'):
            b[key] = [v+d for v, d in zip(b[key], delta)]
        for key in ('min', 'max'):
            b['bounds'][key] = [v+d for v, d in zip(b['bounds'][key], delta)]
        box = after[plot['id']]['box']
        plot['cell_runs'] = [[z, box[0], box[2]-1] for z in range(box[1], box[3])]
    data['plots'] = [b for b in data['plots'] if b['id'] not in retired]
    kept_gardens = []
    for g in data['gardens']:
        if g['id'] in removed_tags:
            continue
        if g['id'] in garden_shifts:
            dx, dy, dz = garden_shifts[g['id']]
            g['bounds'] = [v+d for v, d in zip(g['bounds'], [dx, dz, dx, dz])]
            g['y'] += dy
            for key in ('gate',):
                g[key] = [v+d for v, d in zip(g[key], [dx, dy, dz])]
            for key in ('path', 'internal_path'):
                g[key] = [[v+d for v, d in zip(point, [dx, dy, dz])] for point in g[key]]
            for f in g['fixtures']:
                f['position'] = [v+d for v, d in zip(f['position'], [dx, dy, dz])]
        kept_gardens.append(g)
    data['gardens'] = kept_gardens
    qspec = json.loads((ROOT / 'blueprints/qianji' / (p['qianji_variant']+'.json')).read_text())
    qcount = 0
    for q in p['qianji']:
        room = next(r for r in qspec['rooms'] if r['id'] == q['room'])
        model = Model(qspec)
        model.hall(room)
        model.illuminate()
        model.blocks = {pos: state for pos, state in model.blocks.items() if pos[1] >= room['floor_y']-2}
        x0, z0, w, d = room['box']
        turn = q['rotation_quarters']
        xb, zb, xe, ze = q['box']
        assert [xe-xb, ze-zb] == ([w, d] if turn % 2 == 0 else [d, w])
        floor = max(ground(x, z) for z in range(zb, ze) for x in range(xb, xe)) + 1
        def place(x, y, z):
            u, v = x-x0, z-z0
            u, v = [(u, v), (d-v, u), (w-u, d-v), (v, w-u)][turn]
            return [xb+u, floor+y-room['floor_y'], zb+v]
        room_boxes = []
        for box in qboxes(model):
            x, y, z, sw, sh, sd, mat, layer = box[:8]
            b = {'p': place(x, y, z), 's': [sw, sh, sd] if turn%2 == 0 else [sd, sh, sw],
                 'm': 'q_'+mat, 'tag': q['id'], 'layer': 'qroof' if layer == 'roof' else 'buildings'}
            scene['boxes'].append(b)
            room_boxes.append(b)
        lo = [min(b['p'][i]-b['s'][i]/2 for b in room_boxes) for i in range(3)]
        hi = [max(b['p'][i]+b['s'][i]/2 for b in room_boxes) for i in range(3)]
        entrance = place(x0+w/2, room['floor_y'], z0)
        data['plots'].append({'id': q['id'], 'name': q['name'], 'garden_ids': [],
            'cell_runs': [[z, xb, xe-1] for z in range(zb, ze)],
            'building': {'id': q['id'], 'name': q['name'], 'role': 'qianji', 'family': '千机阁', 'size': [xe-xb, hi[1]-lo[1], ze-zb],
                         'position': [(xb+xe)/2, floor, (zb+ze)/2], 'bounds': {'min': lo, 'max': hi},
                         'rotation_quarters': turn, 'entrance': entrance, 'volumes': [], 'road_id': '待细化院路'}})
        qcount += len(room_boxes)
    for key, (color, _) in PALETTE.items():
        scene['materials']['q_'+key] = {'color': color}
    surface_report=apply_surfaces(ROOT,data,scene,p,CONFIG['regions'][region],CONFIG['layout_rules'])
    (OUT/(region+'-ground.json')).write_text(json.dumps(surface_report,ensure_ascii=False,indent=2)+'\n')
    # 改线按真实地形显示为细线，保留旧路作为现状参考；不虚构已完成的道路标高。
    road_hints = []
    for road in p['roads_after']:
        if road['id'] not in p['road_changes']:
            continue
        if road['plan_status'] == 'needs_local_street_redesign':
            continue
        points = road['points']
        for a, b in zip(points, points[1:]):
            x,z = a;ex,ez = b
            while (x,z) != (ex,ez):
                road_hints.append([x+.5, ground(x,z)+1.15, z+.5])
                if x!=ex: x += (ex>x)-(ex<x)
                else: z += (ez>z)-(ez<z)
    data['study_road_hints'] = road_hints
    qb = [q['box'] for q in p['qianji']]
    data['study_bounds'] = [min(b[0] for b in qb)-18,min(b[1] for b in qb)-18,max(b[2] for b in qb)+18,max(b[3] for b in qb)+18]
    data['districts'] = [{'id':'qianji_study','name':'千机阁街区','bounds':data['study_bounds']}, *data['districts']]
    data['relayout'] = False
    data['study'] = p
    # 保留原查看器的数据契约，但删除其正式验收文案，明确这次只是设计几何。
    data['checks']['status'] = 'design_preview'
    data['checks']['owner_acceptance'] = 'pending'
    data['checks']['issues'] = [{'message':r['name']+'改线待细化'} for r in p['roads_after'] if r['plan_status']=='needs_local_street_redesign']
    data['checks']['connected_gardens'] = 'not_verified'
    data['checks']['new_doors'] = 'not_verified'
    data['checks']['houses'] = sum(v['building']['role']=='house' for v in data['plots'])
    data['checks']['gardens'] = len(data['gardens'])
    data['checks']['game_runtime'] = 'not_run'
    data['name'] = p['name']+' · 城镇地面重排第二稿'
    original = html(data, scene)
    packed = original.split("atob('",1)[1].split("')",1)[0]
    payload = json.loads(gzip.decompress(base64.b64decode(packed)))
    viewer = viewer_source
    viewer = viewer.replace("walls:'城防'", "walls:'城防',qroof:'千机阁屋顶'")
    viewer = viewer.replace("const knownLayers={", "const knownLayers={qroof:'qroof',garden:'gardens',")
    start = viewer.index("$('count').textContent=")
    end = viewer.index('function select(id)', start)
    viewer = viewer[:start]+"""
$('count').textContent=DATA.name+'；可拖动旋转、滚轮缩放。';
function general(){
 $('selectedName').textContent='千机阁入城草案';
 $('selectedText').textContent='建筑使用当前作者输入的实际构件，千机阁保留各栋原尺寸和楼层。迁排住宅按草案移动，街坊空地连成铺地院场，绿地集中在有边界的树院。';
 $('selectedType').textContent='设计预览，未接入世界生成';
 $('selectedConnection').textContent='新楼房标高暂取地块最高地面，院路、道路完整路幅与土方尚未核验。浅色改线标记只表示平面走向，旧路仍供对照。'+DATA.checks.issues.map(x=>x.message+'。').join('');
}
"""+viewer[end:]
    viewer = viewer.replace("general();reset();$('loading').hidden=true;", """
const sourceSelect=select;select=function(id){sourceSelect(id);if(selected){$('selectedType').textContent='草案位置与原尺寸构件';$('selectedConnection').textContent='本页展示布置方向；新院路、门口净空、道路标高及实际施工仍待核验。';}};
general();changeMode('space');focus(DATA.study_bounds);$('loading').hidden=true;
$('qfocus').onclick=()=>focus(DATA.study_bounds);
$('topview').onclick=()=>{const y=(terrainRange[0]+terrainRange[1])/2;orbit.target.set(W/2,y,D/2);camera.position.set(W/2,y+span*1.5,D/2+.01);orbit.update();};
$('streetview').onclick=()=>{const b=DATA.plots.find(p=>p.id.startsWith('qianji_')).building;const e=b.entrance,turn=b.rotation_quarters;const v=[[0,-1],[1,0],[0,1],[-1,0]][turn];orbit.target.set(e[0],e[1]+5,e[2]);camera.position.set(e[0]+v[0]*20,e[1]+2,e[2]+v[1]*20);orbit.update();};
$('citypick').onchange=e=>{location.href=e.target.value+'.html';};
""")
    viewer = viewer.replace('world.add(group);const mats={};', """world.add(group);
if(DATA.study_road_hints.length){const geo=new THREE.BufferGeometry().setFromPoints(DATA.study_road_hints.map(p=>new THREE.Vector3(...p)));const pts=new THREE.Points(geo,new THREE.PointsMaterial({color:0xe5c875,size:.65}));world.add(pts);}
const mats={};""")
    viewer=amend_viewer(viewer)
    payload['viewer'] = viewer
    packed = base64.b64encode(gzip.compress(json.dumps(payload, ensure_ascii=False, separators=(',', ':')).encode(), mtime=0, compresslevel=6)).decode()
    page = template.replace('__CITY_TITLE__', data['name']).replace('__PAYLOAD__', packed)
    page = page.replace(':root{', ':root{color-scheme:dark;--foreground:#e4e8df;--muted-foreground:#aab6a8;--border:#435046;--secondary:#27372e;--card:#1d2c24;--primary:#b9cfa4;--primary-foreground:#1f2a1c;--popover:#1f2f27;--popover-foreground:#e4e8df;')
    page = page.replace('__FOOT__','本页为入城草案的三维设计预览。建筑来自当前生成器；新标高、院路、铺装边界、庭院迁排和实际施工仍待细化。未修改正式城市输入。')
    page = page.replace('<div class="toolbar">', '<div class="toolbar"><select id="citypick" aria-label="选择省城">'+''.join('<option value="'+c['region']+'"'+(' selected' if c['region']==region else '')+'>'+c['name']+'</option>' for c in plans)+'</select>')
    page = page.replace('<button id="planMode"', '<button style="display:none" id="planMode"').replace('<button id="spaceMode"', '<button style="display:none" id="spaceMode"')
    page = page.replace('<button id="reset">复位</button>', '<button id="reset">整城</button><button id="qfocus">千机阁街区</button><button id="treeview">集中树院</button><button id="topview">俯视</button><button id="streetview">街道视角</button><button id="ground_old">上一版地面</button><button id="ground_new" aria-pressed="true">本次地面</button>')
    page = page.replace('<div class="count"', '<p style="margin:0 0 9px;color:var(--soft)">建成街坊以连续铺地为主；绿地与自然区域按明确地块保留。当前为设计草案。</p><div class="count"')
    page=page.replace('<div class="legend">','<div class="legend" style="display:none">')
    (OUT / (region+'.html')).write_text(page)
    result = {'region':region,'status':'design_preview','seconds':round(time.monotonic()-started,3),'boxes':len(scene['boxes']),'qianji_boxes':qcount,'houses_moved':len(moved),'ordinary_houses_proposed_retired':len(retired),'new_road_heights':'not_verified','native_runtime':'not_run','sha256':hashlib.sha256(page.encode()).hexdigest()}
    print(json.dumps(result, ensure_ascii=False), flush=True)
    return result

def main():
    global OUT,CACHE,CONFIG,plans,SOURCE_COMMIT,cache_marker,CACHE_VALID,progress
    args_parser=argparse.ArgumentParser(description=__doc__)
    args_parser.add_argument('--out',type=Path,required=True)
    args_parser.add_argument('--cache',type=Path,default=ROOT/'reports/qianji-ground-r2/cache')
    args_parser.add_argument('--input',type=Path,default=ROOT/'blueprints/province_capitals/blueprint_plan/studies/qianji_ground_r2.json')
    args=args_parser.parse_args()
    OUT=args.out.resolve();OUT.mkdir(parents=True,exist_ok=False)
    CACHE=args.cache.resolve();CACHE.mkdir(parents=True,exist_ok=True)
    CONFIG=json.loads(args.input.read_text())
    plans=json.loads((ROOT/CONFIG['base_plan']).read_text())
    SOURCE_COMMIT=subprocess.check_output(['git','-C',str(ROOT),'rev-parse','HEAD'],text=True).strip()
    cache_marker=CACHE/'.source_commit'
    CACHE_VALID=cache_marker.exists() and cache_marker.read_text().strip()==SOURCE_COMMIT and subprocess.run(['git','-C',str(ROOT),'diff','--quiet','HEAD','--','blueprints/province_capitals','blueprints/lord_manors','tools/province_capitals','tools/buildgen','tools/lord_manors']).returncode==0
    progress = {'status': 'running', 'selected': [p['region'] for p in plans], 'completed': [], 'failed': [], 'unrun': [p['region'] for p in plans]}
    save()
    try:
        for p in plans:
            progress['current'] = p['region'];save()
            result = build(p)
            progress['completed'].append(result)
            progress['unrun'].remove(p['region'])
            save();gc.collect()
        progress['status'] = 'design_preview_generated';save();cache_marker.write_text(SOURCE_COMMIT+'\n')
    except BaseException as error:
        progress['status'] = 'failed'
        if progress['current'] in progress['unrun']:
            progress['failed'].append({'region':progress['current'],'error':repr(error)})
            progress['unrun'].remove(progress['current'])
        else:
            progress['finalize_error']=repr(error)
        save();raise


if __name__ == "__main__":
    main()
