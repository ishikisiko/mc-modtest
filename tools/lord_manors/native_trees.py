"""真实原版树：生成输入、原生树形回读和建筑保护，不再把树体做成雕构。"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.lord_manors.construction import prepare,bounds,source_identity

CONFIG=Path('blueprints/lord_manors/native_trees.json')
GENERATED=Path('tools/lord_manors/generated/native_trees.json')
JAVA=Path('tools/lord_manors/treegen/NativeTreeCapture.java')

def read(path):return json.loads(path.read_text())
def digest(data):return hashlib.sha256(data).hexdigest()

def engine_versions(root=ROOT):
    values=dict(line.split('=',1) for line in (root/'gradle.properties').read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))
    return {key:values[key].strip() for key in ('minecraft_version','neo_version')}

def source_hashes(root=ROOT):
    config=read(root/'blueprints/lord_manors/config.json')
    paths=[CONFIG,JAVA,Path('blueprints/lord_manors/config.json'),Path('blueprints/lord_manors/structure_rules.json'),
           Path('tools/lord_manors/native_trees.py'),Path('tools/lord_manors/construction.py'),Path('tools/lord_manors/geometry.py')]
    paths += [Path('blueprints/lord_manors/cities')/(city+'.json') for city in config['cities']]
    return {p.as_posix():digest((root/p).read_bytes()) for p in paths}

def floor_covered(boxes,x,y,z):
    # 原土体在新土块底面的完整投影必须存在，不能把一块泥土悬在空中。
    plane=y*16;covered=set()
    for lo,hi,role in boxes:
        if not(lo[1]<plane<=hi[1]):continue
        for xx in range(max(x*16,lo[0]),min((x+1)*16,hi[0])):
            for zz in range(max(z*16,lo[2]),min((z+1)*16,hi[2])):covered.add((xx,zz))
    return len(covered)==256

def prepare_sites(root=ROOT):
    cfg=read(root/CONFIG);materials=read(root/'blueprints/lord_manors/config.json');rules=read(root/'blueprints/lord_manors/structure_rules.json')
    result={'schema':1,'minecraft_version':cfg['minecraft_version'],'source_hashes':source_hashes(root),
            'engine_versions':engine_versions(root),'capture':cfg['capture'],'species':cfg['species'],'cities':{}}
    if cfg['minecraft_version']!=result['engine_versions']['minecraft_version']:raise ValueError('真实树配置与目标Minecraft版本不同')
    for city,city_cfg in cfg['cities'].items():
        source=root/'blueprints/lord_manors/cities'/f'{city}.json';scene=read(source);source_identity(source,rules['cities'][city])
        if any(b[6] in materials['native_tree_only_roles'] for b in scene['boxes']):raise ValueError('树体只能由真实树种生成，不能重新添加假树模型')
        scene,_=prepare(scene,rules['cities'][city],rules)
        boxes=[(q[:3],q[3:],b[6]) for b in scene['boxes'] for q in [bounds(b)]]
        trees=[];regions=[]
        for tree in city_cfg['trees']:
            radius=cfg['capture']['horizontal_margin'];height=cfg['capture']['height'];x,y,z=tree['preferred_root']
            regions.append(([x-radius,y-2,z-radius],[x+radius+1,y+height,z+radius+1]))
            candidates=[];size=cfg['species'][tree['species']]['soil_size']
            for dx,dz in cfg['capture']['root_offsets']:
                base=[x+dx,y,z+dz];soil=[[base[0]+i,y-1,base[2]+j] for i in range(size) for j in range(size)]
                valid=True
                for xx,yy,zz in soil:
                    if not floor_covered(boxes,xx,yy,zz):valid=False;break
                    for lo,hi,role in boxes:
                        if all(lo[n]<(v+1)*16 and hi[n]>v*16 for n,v in enumerate((xx,yy,zz))) and role not in cfg['plantable_roles']:
                            valid=False;break
                    if not valid:break
                if valid:candidates.append({'root':base,'soil':soil})
            if not candidates:raise ValueError('树池缺少有支撑的真实种植土位置：'+tree['id'])
            trees.append({**tree,'candidates':candidates})
        obstacles=set()
        for lo,hi,_ in boxes:
            for low,high in regions:
                start=[max(lo[i]//16,low[i]) for i in range(3)];end=[min((hi[i]+15)//16,high[i]) for i in range(3)]
                for y in range(start[1],end[1]):
                    for z in range(start[2],end[2]):
                        for x in range(start[0],end[0]):obstacles.add((x,y,z))
        result['cities'][city]={'bounds':city_cfg['bounds'],'trees':trees,'obstacles':sorted(map(list,obstacles))}
    return result

def load(root=ROOT):
    path=root/GENERATED
    if not path.is_file():raise ValueError('须先运行 generateManorNativeTrees 生成真实原版树形')
    data=read(path)
    if data['source_hashes']!=source_hashes(root):raise ValueError('原版树生成输入已变，须重新运行 generateManorNativeTrees；不能复用旧树形')
    if data['engine_versions']!=engine_versions(root):raise ValueError('原版树引擎版本已变，须重新生成')
    encoded=json.dumps(data['cities'],ensure_ascii=False,separators=(',',':')).encode()
    if digest(encoded)!=data['content_sha256']:raise ValueError('真实树形输出内容摘要不符；不能手改生成树形')
    cfg=read(root/CONFIG)
    if set(data['cities'])!=set(cfg['cities']):raise ValueError('真实树形缺城或包含未知城')
    for city,config in cfg['cities'].items():
        expected={t['id']:t for t in config['trees']};records=data['cities'][city]['trees']
        if len(records)!=len(expected) or {t['id'] for t in records}!=set(expected):raise ValueError('原树未被逐棵替换')
        for tree in records:
            spec=cfg['species'][expected[tree['id']]['species']]
            if tree['feature']!=spec['feature'] or tree['sapling']!=spec['sapling']:raise ValueError('原树与原版树种不同源')
    return data

def attach(scene,city,root=ROOT):
    cfg=read(root/CONFIG);data=load(root)['cities'][city]
    result=copy.deepcopy(scene)
    result['native_tree_cells']={tuple(p):state for p,state in data['blocks']}
    result['native_tree_soil']={tuple(p) for tree in data['trees'] for p in tree['soil']}
    result['native_tree_records']=data['trees'];result['native_tree_bounds']=cfg['cities'][city]['bounds']
    return result

def place(blocks,scene):
    soil=scene['native_tree_soil']
    for position,state in scene['native_tree_cells'].items():
        if position in blocks and position not in soil:raise ValueError('原版树覆盖建筑：'+str(position))
        blocks[position]=state

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--prepare',type=Path,required=True);args=parser.parse_args()
    data=prepare_sites();args.prepare.parent.mkdir(parents=True,exist_ok=True)
    args.prepare.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n')
    print(json.dumps({'cities':len(data['cities']),'trees':sum(len(c['trees']) for c in data['cities'].values()),'obstacles':sum(len(c['obstacles']) for c in data['cities'].values())}))

if __name__=='__main__':main()
