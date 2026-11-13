"""将两关作者模型编译为正式方块、光源和有界遭遇；不启动 Minecraft。"""
from __future__ import annotations

import argparse
from array import array
import gzip
import hashlib
import json
import math
from pathlib import Path

from .model import ROOT, SOURCE, build, read
from .combat import SCENES


def encode(value):
    return (json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n').encode()


class Voxels:
    def __init__(self,size):
        self.w,self.h,self.d=size
        self.palette=['minecraft:air'];self.ids={'minecraft:air':0}
        self.data=array('H',[0])*(self.w*self.h*self.d)

    def material(self,state):
        if state not in self.ids:
            self.ids[state]=len(self.palette);self.palette.append(state)
        return self.ids[state]

    def put(self,x,y,z,state):
        if 0<=x<self.w and 0<=y<self.h and 0<=z<self.d:
            self.data[(y*self.d+z)*self.w+x]=self.material(state)

    def get(self,x,y,z):
        if not(0<=x<self.w and 0<=y<self.h and 0<=z<self.d):return 'minecraft:air'
        return self.palette[self.data[(y*self.d+z)*self.w+x]]

    def box(self,b,state):
        x,y,z,w,h,d=b[:6]
        lo=[max(0,math.floor(v+.001)) for v in (x,y,z)]
        hi=[min(bound,math.ceil(v+length-.001)) for bound,v,length in zip((self.w,self.h,self.d),(x,y,z),(w,h,d))]
        material=self.material(state)
        row=array('H',[material])*max(0,hi[0]-lo[0])
        for yy in range(lo[1],hi[1]):
            for zz in range(lo[2],hi[2]):
                start=(yy*self.d+zz)*self.w+lo[0];self.data[start:start+len(row)]=row

    def runs(self):
        result=[]
        for z in range(self.d):
            for x in range(self.w):
                start=0;last=0
                for y in range(self.h+1):
                    value=0 if y==self.h else self.data[(y*self.d+z)*self.w+x]
                    if value==last:continue
                    if last:result.append([x,start,z,y-start,last])
                    start=y;last=value
        return result


def compile_map(ident,rules):
    model=build(ident);spec=model['spec'];catalog=read('catalog');scene=SCENES[spec['construction']](spec,catalog)
    vox=Voxels(spec['size']);order={key:i for i,key in enumerate(rules['geometry_layers'])}
    for b in sorted(model['boxes'],key=lambda box:order.get(box[7],-1)):
        if b[7] in rules['ignored_layers'] or b[8]:continue
        if b[7] not in order:raise ValueError('未登记的正式构件层：'+b[7])
        key=model['materials'][b[6]]
        if key not in rules['materials']:raise ValueError('缺少正式材质：'+key)
        vox.box(b,rules['materials'][key])
    # 路径在方块网格中使用真实楼梯或半砖，头部空间由同一条作者通路保证。
    route_cells=set();probes=set();lights=set()
    for edge in model['routes']:
        for a,b in zip(edge['points'],edge['points'][1:]):
            for x in range(math.floor(min(a[0],b[0])-3),math.ceil(max(a[0],b[0])+3)):
                for z in range(math.floor(min(a[2],b[2])-3),math.ceil(max(a[2],b[2])+3)):
                    from .model import distance_segment
                    if distance_segment(x+.5,z+.5,a,b)[0]<2.7:route_cells.add((x,z))
            steps=max(1,math.ceil(math.hypot(b[0]-a[0],b[2]-a[2])))
            for j in range(steps+1):
                x,z=round(a[0]+(b[0]-a[0])*j/steps),round(a[2]+(b[2]-a[2])*j/steps)
                feet=scene.walk_level(x+.5,z+.5)[1]
                probes.add((x,math.ceil(feet),z))
                if j%rules['lighting']['route_spacing']==0:lights.add((x,math.ceil(feet)+rules['lighting']['height'],z))
    heights={cell:math.ceil(scene.walk_level(cell[0]+.5,cell[1]+.5)[1]) for cell in route_cells}
    for x,z in sorted(route_cells):
        yy=heights[x,z]
        for y in range(yy,yy+4):vox.put(x,y,z,'minecraft:air')
        lower=[(heights[xx,zz],xx,zz) for xx,zz in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)) if (xx,zz) in heights and heights[xx,zz]==yy-1]
        if lower:
            low,xx,zz=min(lower)
            facing={(1,0):'east',(-1,0):'west',(0,1):'south',(0,-1):'north'}[x-xx,z-zz]
            vox.put(x,yy-2,z,'minecraft:stone_bricks')
            vox.put(x,yy-1,z,f'minecraft:stone_brick_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]')
        else:
            inside=any(s['kind']=='combat_house' and abs(x+.5-s['pos'][0])<s['size'][0]/2-1 and abs(z+.5-s['pos'][2])<s['size'][2]/2-1 for s in spec['structures'])
            vox.put(x,yy-1,z,'minecraft:spruce_planks' if inside else 'minecraft:andesite' if ident=='beast_den' else 'minecraft:coarse_dirt')
    # 每个命名节点和实际出生点保留明确的平整落脚处。
    anchors={n['id']:n['pos'] for n in spec['nodes']}
    actors=[]
    required={a['id']:i for i,a in enumerate(spec['actions'])}
    encounters=[]
    for encounter in spec['encounters']:
        group=[]
        for source in encounter['actors']:
            actor=dict(rules['actors'][source['kind']],kind=source['kind'],pos=source['pos'])
            group.append(actor);actors.append(actor)
        encounters.append(dict(id=encounter['id'],name=encounter['name'],required=encounter['id'] in required,
                               stage=required.get(encounter['id'],-1),actors=group))
    for pos in [*anchors.values(),*(a['pos'] for a in actors)]:
        x,y,z=map(round,pos)
        for xx in range(x-1,x+2):
            for zz in range(z-1,z+2):
                vox.put(xx,y-1,zz,'minecraft:andesite' if ident=='beast_den' else 'minecraft:spruce_planks')
                for yy in range(y,y+4):vox.put(xx,yy,zz,'minecraft:air')
        probes.add((x,y,z));lights.add((x,y+rules['lighting']['height'],z))
    # 大交战面补光，光源无碰撞；可见灯笼仍保留在模型的照明构件处。
    rooms=spec['terrain'].get('rooms',[])
    if not rooms:
        rooms=[dict(center=n['pos'],radius=[13,11]) for n in spec['nodes'] if n['role'] in ('encounter','objective')]
    grid=rules['lighting']['room_grid']
    for room in rooms:
        cx,y,cz=room['center'];rx,rz=room['radius']
        for x in range(round(cx-rx+3),round(cx+rx-2),grid):
            for z in range(round(cz-rz+3),round(cz+rz-2),grid):
                if ((x-cx)/rx)**2+((z-cz)/rz)**2>.8:continue
                if vox.get(x,y,z)=='minecraft:air' and vox.get(x,y-1,z)!='minecraft:air':
                    lights.add((x,y+rules['lighting']['height'],z));probes.add((x,y,z))
    for x,y,z in sorted(lights):
        if vox.get(x,y,z)=='minecraft:air':vox.put(x,y,z,rules['lighting']['fill_block'])
    # 检查点覆盖通路、节点和交战面；柱梁遮挡高位补光时，在玩家头部的空格中补光。
    for x,y,z in sorted(probes):
        for yy in (y+1,y+2,y):
            if vox.get(x,yy,z) in ('minecraft:air',rules['lighting']['fill_block']):
                vox.put(x,yy,z,rules['lighting']['fill_block']);break
        else:raise ValueError(f'光照检查点被实体方块占用：{ident} {(x,y,z)}')
    # 闭合边界保护临时世界，避免离开已制作地形跌入虚空。
    for x in range(vox.w):
        for y in range(vox.h):
            vox.put(x,y,0,'minecraft:barrier');vox.put(x,y,vox.d-1,'minecraft:barrier')
    for z in range(vox.d):
        for y in range(vox.h):
            vox.put(0,y,z,'minecraft:barrier');vox.put(vox.w-1,y,z,'minecraft:barrier')
    for x in range(vox.w):
        for z in range(vox.d):vox.put(x,vox.h-1,z,'minecraft:barrier')
    exit_structure=next(s for s in spec['structures'] if s['kind']=='combat_exit')
    exit_pos=[round(exit_structure['pos'][0]),round(exit_structure['pos'][1])+1,round(exit_structure['pos'][2])]
    vox.put(*exit_pos,'minecraft:lodestone')
    result=dict(schema=1,id=ident,name=spec['name'],size=spec['size'],origin=rules['origin'],entry=anchors[spec['entry']],
                exit=exit_pos,anchors=anchors,routes=model['routes'],encounters=encounters,palette=vox.palette,runs=vox.runs(),
                lighting_probes=[list(p) for p in sorted(probes)],lighting_sources=len(lights),**rules['map_rules'][ident])
    return result


def export(out):
    out=Path(out);data=out/'data/myvillage';folder=data/'expedition';folder.mkdir(parents=True,exist_ok=True)
    rules=read('runtime');manifest={}
    for ident in rules['maps']:
        model=compile_map(ident,rules);payload=gzip.compress(encode(model),mtime=0)
        (folder/(ident+'.json.gz')).write_bytes(payload)
        manifest[ident]=dict(name=model['name'],sha256=hashlib.sha256(payload).hexdigest(),runs=len(model['runs']),blocks=sum(r[3] for r in model['runs']),light_probes=len(model['lighting_probes']))
        print(ident,manifest[ident],flush=True)
    (folder/'rules.json').write_bytes(encode(dict(rules,manifest=manifest)))
    for relative,value in {
        'dimension_type/expedition.json':rules['dimension_type'],
        'dimension/expedition_template.json':{'type':'myvillage:expedition','generator':{'type':'minecraft:flat','settings':{'biome':'minecraft:the_void','features':False,'lakes':False,'structure_overrides':[],'layers':[{'height':1,'block':'minecraft:air'}]}}}
    }.items():
        path=data/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(encode(value))
    return manifest


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();export(a.out)
