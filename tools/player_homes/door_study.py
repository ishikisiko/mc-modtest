#!/usr/bin/env python3
"""独立大门设计：硬边构件、双扇开合和主屋局部试装，不写游戏资源。"""
from pathlib import Path
import argparse,base64,copy,gzip,hashlib,json,math,subprocess,sys,zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.player_homes.model import build_home
from tools.player_homes.preview import bake_instances,HomeModels

INPUT=ROOT/'blueprints/player_homes/door_study.json'
BASE='https://assets.ishiki333hhh.win/previews/player-house-door-study-20261117/'


from tools.player_homes.door_model import door_parts, transformed_bounds, check_design


def context_scene(config,jar,out):
    catalog=json.loads((ROOT/'blueprints/player_homes/catalog.json').read_text());spec=next(t for t in catalog['templates']if t['id']==config['context']['template'])
    site=build_home(ROOT,catalog,spec);m=next(m for m in spec['modules']if m['id']==config['context']['module']);t=catalog['module_types'][m['type']]
    F=m['front_z'];rx=m['roof_x'];width=t['roof_width'];feet=catalog['room_feet_y'];cx=(rx+t['body_inset']+rx+width-t['body_inset']-1)//2;anchor=[cx-2,feet,F]
    door_family=catalog['door']['family']
    for assembly in list(site.assemblies):
        if assembly['family']==door_family:
            for p in assembly['parts']:site.blocks.pop(tuple(p),None);site.owners.pop(tuple(p),None)
            site.assemblies.remove(assembly)
    keep={p for p in site.blocks if rx<=p[0]<rx+width and F-config['context']['front_margin']<=p[2]<F+config['context']['back_margin']}
    retained=[]
    for assembly in site.assemblies:
        parts={tuple(p)for p in assembly['parts']}
        if parts<=keep:retained.append(assembly)
        else:keep-=parts
    slot={p for p in keep if anchor[0]<=p[0]<anchor[0]+5 and feet<=p[1]<feet+5 and F<=p[2]<F+1}
    assert not any(p in site.owners for p in slot),'试装门洞会切割现有完整构件'
    keep-=slot;site.blocks={p:v for p,v in site.blocks.items()if p in keep};site.layers={p:v for p,v in site.layers.items()if p in keep};site.assemblies=retained
    site.c['views']={};site.rooms=[r for r in site.rooms if r['id']==m['id']]
    stats=bake_instances(site,jar,out)
    scene=json.loads((out/'scene.json').read_text());atlas='data:image/png;base64,'+base64.b64encode((out/'atlas.png').read_bytes()).decode()
    return {'scene':scene,'atlas':atlas,'anchor':anchor,'center':[cx+.5,feet+2.4,F+.5],
            'player':[cx+4.5,feet,F-.5],'source_template':spec['id'],'removed_scalar_cells_for_preview':len(slot),'stats':stats}


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--out',type=Path,required=True)
    ap.add_argument('--assets-jar',type=Path,default=Path('/home/ubuntu/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'));args=ap.parse_args()
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=False);config=json.loads(INPUT.read_text());frame,leaf=door_parts(config);checks=check_design(config,frame,leaf)
    context_dir=out/'context';context_dir.mkdir();context=context_scene(config,args.assets_jar,context_dir)
    textures={};texture_provenance={}
    with zipfile.ZipFile(args.assets_jar)as jar:
        for key,spec in config['materials'].items():
            data=(ROOT/spec['resource']).read_bytes()if'resource'in spec else jar.read(spec['vanilla']);textures[key]='data:image/png;base64,'+base64.b64encode(data).decode();texture_provenance[key]={'source':spec,'sha256':hashlib.sha256(data).hexdigest()}
    native=HomeModels(args.assets_jar);old=[]
    for half,y in [('lower',0),('upper',1)]:
        state=f'minecraft:dark_oak_door[facing=north,half={half},hinge=left,open=false,powered=false]'
        for points,normal,uvs,tex,tint,cull,transparent in native.faces(state):old.append({'p':[[x/16,py/16+y,z/16]for x,py,z in points],'n':normal,'uv':[[u/16,1-v/16]for u,v in uvs],'texture':tex})
    for key,image in native.textures.items():
        from io import BytesIO
        buffer=BytesIO();image.save(buffer,format='PNG');textures[key]='data:image/png;base64,'+base64.b64encode(buffer.getvalue()).decode()
    libraries=[base64.b64encode(gzip.compress((ROOT/name).read_bytes(),mtime=0)).decode()for name in('tools/web/three.min.js','tools/web/OrbitControls.js')]
    design={'config':config,'frame':frame,'leaf':leaf,'checks':checks,'textures':textures,'context':context,'old':old,'libraries':libraries}
    packed=base64.b64encode(gzip.compress(json.dumps(design,ensure_ascii=False,separators=(',',':')).encode(),mtime=0)).decode()
    page=(ROOT/'tools/player_homes/door_study.html').read_text().replace('/*DATA*/',json.dumps(packed));(out/'index.html').write_text(page)
    handoff='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><style>html,body{margin:0;background:transparent;color:var(--foreground);font:14px var(--font-sans,system-ui,sans-serif)}p{margin:0 0 10px}a{color:var(--primary)}iframe{display:block;width:100%;height:1130px;border:0}</style><p><a href="'''+BASE+'''index.html" target="_blank" rel="noopener">打开木格双开大门三维设计</a></p><iframe src="'''+BASE+'''index.html" title="木格双开大门独立设计"></iframe></html>'''
    (out/'handoff.html').write_text(handoff)
    (out/'door-design.json').write_text(json.dumps({'config':config,'frame':frame,'leaf':leaf,'checks':checks},ensure_ascii=False,indent=2)+'\n')
    (out/'checks.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2)+'\n')
    with zipfile.ZipFile(out/'source.zip','w',zipfile.ZIP_DEFLATED)as source:
        for path in [INPUT,ROOT/'tools/player_homes/door_study.py',ROOT/'tools/player_homes/door_study.html',ROOT/'tools/player_homes/door_capture.mjs',ROOT/'docs/features/player-house-door-study.md']:
            source.writestr(str(path.relative_to(ROOT)),path.read_bytes())
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip();dirty=bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True))
    manifest={'status':'design_preview','source_commit':commit,'source_dirty':dirty,'authored_input_sha256':hashlib.sha256(INPUT.read_bytes()).hexdigest(),'textures':texture_provenance,'checks':checks,
              'archive_scope':'本轮门体作者文件；复现需同提交的住宅及构件代码、原版客户端资源',
              'home_templates_modified':config['home_templates_modified'],'game_registration':config['game_registration'],'minecraft':'not_run','files':{}}
    for p in sorted(out.rglob('*')):
        if p.is_file():manifest['files'][str(p.relative_to(out))]={'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');print(json.dumps(checks,ensure_ascii=False))


if __name__=='__main__':main()
