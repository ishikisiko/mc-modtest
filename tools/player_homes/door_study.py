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


def box(parts,name,bounds,material='timber'):
    assert len(bounds)==6 and all(bounds[i]<bounds[i+3]for i in range(3)),(name,bounds)
    parts.append({'name':name,'bounds':bounds,'material':material})


def door_parts(config):
    frame=[];leaf=[];o=config['opening'];s=config['leaf'];bottom,top=s['bottom'],s['top'];w=s['width'];d=s['thickness'];bar=s['stile_width']
    # 后侧退口容纳开启后的门板，正面仍保留三格净宽。
    for side in (0,1):
        def mirrored(b):return b if side==0 else[5-b[3],b[1],b[2],5-b[0],b[4],b[5]]
        box(frame,'粉墙前收边',mirrored([0,.125,0,.5,5,.5]),'plaster')
        box(frame,'粉墙后退口',mirrored([0,.125,.5,.25,5,1]),'plaster')
        box(frame,'门框石脚',mirrored([.375,.125,0,1,.375,.5625]),'stone')
        box(frame,'木门立框',mirrored([.5,.375,.125,1,4.625,.5]))
        box(frame,'止口',mirrored([.9375,.375,.5,1,4.125,.625]))
        for h in s['hinge_heights']:
            box(frame,'门轴座',mirrored([.375,h-.0625,.4375,.5625,h+.0625,.75]),'bronze')
    box(frame,'低门槛',[0,0,0,5,o[1],1],'stone')
    box(frame,'上门楣',[.5,o[3],.125,4.5,4.625,.875])
    box(frame,'楣上粉墙',[.5,4.625,0,4.5,5,1],'plaster')
    box(frame,'浅木收边',[.5,4.5,.0625,4.5,4.625,.125])
    for x in (0,w-bar):box(leaf,'门扇边梃',[x,bottom,0,x+bar,top,d])
    for y,height in ((bottom,.25),(2,.25),(top-.25,.25)):
        box(leaf,'门扇横档',[bar,y,0,w-bar,y+height,d])
    for i in range(4):
        x=round((bar+i*(w-2*bar)/4)*16)/16;end=round((bar+(i+1)*(w-2*bar)/4)*16)/16
        box(leaf,'下部实心木板',[x,bottom+.25,.0625,end,2,.1875],'panel')
    for x in (.5625,1.0,1.4375):box(leaf,'上部门棂竖杆',[x,2.25,.0625,x+.0625,top-.25,.125])
    for y in (2.75,3.25):box(leaf,'上部门棂横杆',[bar,y,.0625,w-bar,y+.0625,.125])
    for h in s['hinge_heights']:
        box(leaf,'门轴铜箍',[0,h-.0625,-.0625,.4375,h+.0625,0],'bronze')
    hx,hy=s['ring_center'];front=s['ring_front']
    box(leaf,'门环背板',[hx-.1875,hy-.1875,-.0625,hx+.1875,hy+.25,0],'bronze')
    box(leaf,'门环固定销',[hx-.0625,hy+.125,front+.0625,hx+.0625,hy+.25,-.0625],'bronze')
    # 八段硬边铜环，不使用光滑圆环或发光材质。
    ring=[[-2,2,2,3],[-3,1,-1,2],[1,1,3,2],[-3,-2,-2,1],[2,-2,3,1],[-3,-3,-1,-2],[1,-3,3,-2],[-2,-4,2,-3]]
    for a,b,c,e in ring:box(leaf,'铜门环',[hx+a/16,hy+b/16,front,hx+c/16,hy+e/16,front+.0625],'bronze')
    return frame,leaf


def transformed_bounds(part,pivot,angle,mirror=False):
    b=part['bounds'];co,si=math.cos(angle),math.sin(angle);points=[]
    for x in (b[0],b[3]):
        for y in (b[1],b[4]):
            for z in (b[2],b[5]):
                x=-x if mirror else x
                points.append([pivot[0]+x*co+z*si,pivot[1]+y,pivot[2]-x*si+z*co])
    return [*[min(p[i]for p in points)for i in range(3)],*[max(p[i]for p in points)for i in range(3)]]


def check_design(config,frame,leaf):
    o=config['opening'];s=config['leaf'];maximum=-1;central_intrusions=[];leaf_intersections=[]
    def intersects(a,b):return all(min(a[i+3],b[i+3])-max(a[i],b[i])>1e-7 for i in range(3))
    portal=[o[0],o[1],0,o[2],o[3],s['swing_reservation_depth']]
    frame_intrusions=[p['name']for p in frame if intersects(p['bounds'],portal)]
    assert not frame_intrusions,('门框侵入净通道',frame_intrusions)
    for degrees in range(0,91):
        a=math.radians(degrees)
        left=[transformed_bounds(p,s['left_pivot'],-a)for p in leaf]
        right=[transformed_bounds(p,s['right_pivot'],a,True)for p in leaf]
        maximum=max(maximum,max(b[5]for b in left+right))
        if any(intersects(l,r)for l in left for r in right):leaf_intersections.append(degrees)
        if degrees==90:
            central_intrusions=[b for b in left+right if intersects(b,portal)]
    assert not leaf_intersections,('门扇开合相交',leaf_intersections)
    assert not central_intrusions,('全开门扇侵入净通道',central_intrusions)
    assert maximum<=s['swing_reservation_depth'],('开合区不足',maximum)
    assert all(all(abs(v*16-round(v*16))<1e-7 for v in p['bounds'])for p in frame+leaf),'构件没有对齐1/16格'
    return {'status':'design_geometry_checked','opening_width':o[2]-o[0],'opening_height':o[3]-o[1],
            'sampled_angles':91,'left_right_leaf_intersections':0,'fully_open_intrusions':0,'frame_intrusions':0,
            'swing_maximum_z':maximum,'reserved_depth':s['swing_reservation_depth'],
            'minecraft_collision':'not_verified','game_interaction':'not_implemented'}


def context_scene(config,jar,out):
    catalog=json.loads((ROOT/'blueprints/player_homes/catalog.json').read_text());spec=next(t for t in catalog['templates']if t['id']==config['context']['template'])
    site=build_home(ROOT,catalog,spec);m=next(m for m in spec['modules']if m['id']==config['context']['module']);t=catalog['module_types'][m['type']]
    F=m['front_z'];rx=m['roof_x'];width=t['roof_width'];feet=catalog['room_feet_y'];cx=(rx+t['body_inset']+rx+width-t['body_inset']-1)//2;anchor=[cx-2,feet,F]
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
              'home_templates_modified':False,'game_registration':False,'minecraft':'not_run','files':{}}
    for p in sorted(out.rglob('*')):
        if p.is_file():manifest['files'][str(p.relative_to(out))]={'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');print(json.dumps(checks,ensure_ascii=False))


if __name__=='__main__':main()
