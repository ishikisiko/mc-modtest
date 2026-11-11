#!/usr/bin/env python3
"""独立灰瓦庭院参考重建：原版方块 -> SCHEM/NBT/像素材质预览。"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import shutil
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'));sys.path.insert(0,str(ROOT))
from tools.player_estates.geometry import Estate, connectivity, AIR
from tools.player_estates.preview import bake, write_viewer, plan_svg
from buildgen.modset import load_modset
from buildgen.nbtread import read_gzipped_nbt,state_string
import json_to_nbt as nbt
import voxel_dump_to_structures as exports

SOURCE=ROOT/'blueprints/player_estates/gray_courtyard.json'


def canonical(s):
    name,props=nbt.parse_block_state(s)
    return name+('['+','.join(f'{k}={v}' for k,v in sorted(props.items()))+']' if props else '')


def check_exports(e,out):
    _,root=read_gzipped_nbt(str(out/'gray_courtyard.nbt'))
    palette=[state_string(p) for p in root['palette']]
    actual={tuple(b['pos']):palette[b['state']] for b in root['blocks'] if palette[b['state']]!=AIR}
    expected={p:canonical(s) for p,s in e.blocks.items()}
    if actual!=expected:raise ValueError('NBT 逐坐标回读与生成源不一致')
    _,schem=read_gzipped_nbt(str(out/'gray_courtyard.schem'))
    inv={v:canonical(k) for k,v in schem['Palette'].items()}
    values=exports.decode_varints(schem['BlockData']);W,H,D=e.c['size']
    if len(values)!=W*H*D:raise ValueError('SCHEM 体积不符')
    mismatches=[]
    for i,v in enumerate(values):
        x=i%W;z=(i//W)%D;y=i//(W*D)
        if inv[v]!=expected.get((x,y,z),AIR):
            mismatches.append([x,y,z])
            if len(mismatches)>10:break
    if mismatches:raise ValueError(f'SCHEM 逐坐标回读不同: {mismatches}')
    if root['size']!=e.c['size']:raise ValueError('NBT 尺寸与 authored 配置不符')
    return {'nbt_coordinate_cells':len(expected),'schem_coordinate_cells':len(values),'mismatches':0,'data_version':root['DataVersion']}


def package_source(out):
    core=('__init__.py','build.py','geometry.py','preview.py','test_geometry.py')
    files=[SOURCE,*[ROOT/'tools/player_estates'/name for name in core],ROOT/'tools/player_estates/viewer.html',ROOT/'tools/player_estates/README.md',ROOT/'tools/player_estates/capture.mjs',
           ROOT/'tools/voxel_dump_to_structures.py',ROOT/'tools/json_to_nbt.py',
           *[ROOT/'tools/buildgen'/name for name in ('__init__.py','modset.py','style.py','nbtread.py')],
           ROOT/'tools/web/three.min.js',ROOT/'tools/web/OrbitControls.js']
    with zipfile.ZipFile(out/'gray_courtyard_source.zip','w',zipfile.ZIP_DEFLATED) as z:
        for path in files:
            entry=zipfile.ZipInfo(str(path.relative_to(ROOT)),date_time=(2026,11,11,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(entry,path.read_bytes())


def finalize(out,manifest=None,base_url=None):
    """刷新源文件包及全部产物身份，可在离线截图完成后单独执行。"""
    if manifest is None:manifest=json.loads((out/'manifest.json').read_text())
    source_hash=hashlib.sha256(SOURCE.read_bytes()).hexdigest()
    if manifest['checks']['source_sha256']!=source_hash:
        raise ValueError('作者输入已变化，需重新生成蓝图后再整理产物。')
    if base_url is not None:manifest['preview_base_url']=base_url
    if 'preview_base_url' in manifest:write_viewer(None,out,manifest['preview_base_url'])
    package_source(out)
    manifest['files']={p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(out.iterdir()) if p.is_file() and p.name!='manifest.json'}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
    return manifest


def generate(out,jar,base_url='',reference=None):
    out.mkdir(parents=True,exist_ok=True)
    c=json.loads(SOURCE.read_text());e=Estate(c).build()
    errors=load_modset('vanilla').palette_block_errors(list(set(e.blocks.values())))
    if errors:raise ValueError(errors)
    native_names=zipfile.ZipFile(jar).namelist()
    missing=[s.split('[')[0] for s in set(e.blocks.values()) if 'assets/minecraft/blockstates/'+s.split('[')[0].split(':')[1]+'.json' not in native_names and s.split('[')[0]!='minecraft:water']
    if missing:raise ValueError(f'1.21.1 不含这些方块: {sorted(set(missing))}')
    # A final explicit air corner retains the authored bounding box in sparse NBT.
    blocks=dict(e.blocks);blocks[(c['size'][0]-1,c['size'][1]-1,c['size'][2]-1)]=AIR
    structure=exports.Structure(blocks)
    root=exports.build_structure_nbt(structure,exports.DEFAULT_DATA_VERSION,'MyVillage independent picture-reference study')
    nbt.write_gzipped_nbt(root,str(out/'gray_courtyard.nbt'))
    schem,_=exports.build_schem(structure,exports.DEFAULT_DATA_VERSION)
    exports.write_gzipped_nbt_ext(schem,out/'gray_courtyard.schem','Schematic')
    exports.write_blueprint(exports.build_blueprint(structure,c['id'],{'name':c['name'],'reference':c['reference'],'scope':c['scope']}),out/'gray_courtyard.blueprint.json')
    checks=check_exports(e,out)
    walk=connectivity(e)
    checks['walkability']=walk
    checks['palette_namespaces']=sorted({s.split(':')[0] for s in e.blocks.values()})
    checks['minecraft']= '1.21.1'
    checks['game_runtime']='not_verified'
    checks['owner_visual_acceptance']='not_verified'
    checks['source_sha256']=hashlib.sha256(SOURCE.read_bytes()).hexdigest()
    preview=bake(e,jar,out)
    checks['preview']=preview
    (out/'plan.svg').write_text(plan_svg(e))
    if reference:shutil.copyfile(reference,out/'reference.webp')
    counts=Counter(s.split('[')[0] for s in e.blocks.values())
    (out/'materials.json').write_text(json.dumps(dict(counts.most_common()),ensure_ascii=False,indent=2))
    (out/'rooms.json').write_text(json.dumps(e.rooms,ensure_ascii=False,indent=2))
    (out/'checks.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2))
    manifest={'name':c['name'],'size':c['size'],'blocks':len(e.blocks),'states':len(set(e.blocks.values())),
              'rooms':len(e.rooms),'reference':c['reference'],'scope':c['scope'],'preview':preview,'checks':checks,'preview_base_url':base_url}
    finalize(out,manifest)
    return e,manifest


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--out',type=Path,default=ROOT/'out/preview/gray-courtyard')
    ap.add_argument('--assets-jar',type=Path,default=Path('/home/ubuntu/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'))
    ap.add_argument('--base-url',default='')
    ap.add_argument('--reference',type=Path)
    ap.add_argument('--finalize',action='store_true',help='在截图后刷新源文件包与文件校验清单，不重新生成方块。')
    args=ap.parse_args()
    if args.finalize:
        m=finalize(args.out.resolve(),base_url=args.base_url or None)
        print(json.dumps({'files':len(m['files']),'source_sha256':m['checks']['source_sha256']},ensure_ascii=False))
        return
    e,m=generate(args.out.resolve(),args.assets_jar,args.base_url,args.reference)
    print(json.dumps({k:m[k] for k in ('name','size','blocks','states','rooms','preview')},ensure_ascii=False,indent=2))
    print(json.dumps(m['checks']['walkability'],ensure_ascii=False,indent=2))
    if not m['checks']['walkability']['all_reachable']:raise SystemExit('存在未连通的楼层目标，请保留本轮输出并修复。')


if __name__=='__main__':main()
