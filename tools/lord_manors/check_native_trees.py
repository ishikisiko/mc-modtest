"""独立检查真实树的原木、叶距、树苗与泥土；不把资源检查称作游戏验收。"""
from __future__ import annotations
import argparse
from collections import deque
import json
from pathlib import Path
from tools.lord_manors.native_trees import ROOT,CONFIG,load,read

def parts(state):
    name,_,properties=state.partition('[')
    return name,dict(p.split('=',1) for p in properties.rstrip(']').split(',')) if properties else {}

def audit(root=ROOT):
    root=Path(root);data=load(root);cfg=read(root/CONFIG);material_cfg=read(root/'blueprints/lord_manors/config.json')
    forbidden=set(material_cfg['native_tree_only_roles']);catalog=read(root/'src/main/resources/data/myvillage/lord_manors/catalog.json');issues=[];cities={}
    for material in catalog['materials']:
        if material['role'] in forbidden:issues.append({'fake_tree_material':material['id']})
    for bank in catalog['banks']:
        if any(b[6] in forbidden for p in bank['profiles'] for b in p['boxes']):issues.append({'fake_tree_profile':bank['id']})
    maximum=data['leaf_distance_max']
    for city,source in data['cities'].items():
        states={tuple(p):parts(state) for p,state in source['blocks']};records=source['trees']
        selected={t['species'] for t in records};logs={cfg['species'][s]['log'] for s in selected};leaves={cfg['species'][s]['leaves'] for s in selected}
        unknown=[p for p,(name,_) in states.items() if name not in logs|leaves|{'minecraft:dirt'}]
        if unknown:issues.append({'city':city,'unexpected_native_blocks':unknown})
        distance={p:0 for p,(name,_) in states.items() if name in logs};queue=deque(distance)
        while queue:
            x,y,z=queue.popleft();next_distance=distance[x,y,z]+1
            if next_distance>=maximum:continue
            for pos in [(x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1)]:
                if pos in states and states[pos][0] in leaves and next_distance<distance.get(pos,maximum):distance[pos]=next_distance;queue.append(pos)
        leaf_count=0
        for pos,(name,properties) in states.items():
            if name not in leaves:continue
            leaf_count+=1
            if properties.get('persistent')!='false' or int(properties.get('distance',-1))!=distance.get(pos,maximum):
                issues.append({'city':city,'bad_leaf_state':pos,'properties':properties,'expected_distance':distance.get(pos,maximum)})
        for tree in records:
            spec=cfg['species'][tree['species']]
            if states.get(tuple(tree['root']),('',{}))[0]!=spec['log']:issues.append({'city':city,'tree_root_missing':tree['id']})
            if any(states.get(tuple(p),('',{}))[0]!='minecraft:dirt' for p in tree['soil']):issues.append({'city':city,'tree_soil_missing':tree['id']})
            if len(tree['soil'])!=spec['soil_size']**2:issues.append({'city':city,'wrong_planting_footprint':tree['id']})
        scene=read(root/'blueprints/lord_manors/cities'/f'{city}.json')
        if any(b[6] in forbidden for b in scene['boxes']):issues.append({'city':city,'fake_tree_source':True})
        manifest=read(root/'src/main/resources/data/myvillage/lord_manors'/f'{city}.json')
        if len(manifest['native_trees']['trees'])!=len(records):issues.append({'city':city,'manifest_tree_count':True})
        cities[city]={'trees':len(records),'native_blocks':len(states),'logs':sum(n in logs for n,_ in states.values()),'leaves':leaf_count}
    return {'status':'fail' if issues else 'pass','cities':cities,'issues':issues,'method':'Minecraft原生ConfiguredFeature输出、真实原木/叶距/种植土及无假树资源检查','player_lifecycle':'not_verified'}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--report',type=Path);args=parser.parse_args()
    result=audit(args.root);text=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
    if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(text)
    print(text,end='')
    if result['status']!='pass':raise SystemExit(1)

if __name__=='__main__':main()
