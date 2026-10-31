#!/usr/bin/env python3
"""从原始屋面合同追到实际 NBT，检查所有屋瓦/收口、挂幡与承点体积。"""
from __future__ import annotations

import argparse
from functools import lru_cache
import json
from pathlib import Path
import sys

from tools.lord_manors.construction import prepare,source_identity,bounds,title_box
from tools.lord_manors.check_models import volume_bits
from tools.buildgen.nbtread import read_gzipped_nbt,state_string

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))


def required_cells(boxes):
    required={}
    for box,kind in boxes:
        q=bounds(box)
        for x in range(q[0]//16,(q[3]+15)//16):
            for y in range(q[1]//16,(q[4]+15)//16):
                for z in range(q[2]//16,(q[5]+15)//16):
                    pos=x,y,z;offset=tuple(v*16 for v in pos)
                    part=tuple(max(0,q[i]-offset[i]) for i in range(3))+tuple(min(16,q[i+3]-offset[i]) for i in range(3))
                    key=pos,kind;required[key]=required.get(key,0)|volume_bits(part)
    return required


def audit(root=ROOT,runtime=None):
    root=Path(root);data=root/'src/main/resources/data/myvillage';auth=root/'blueprints/lord_manors'
    rules=json.loads((auth/'structure_rules.json').read_text());catalog=json.loads((data/'lord_manors/catalog.json').read_text())
    banks={b['id']:b for b in catalog['banks']};materials={m['id']:m for m in catalog['materials']}

    @lru_cache(maxsize=32768)
    def state_bits(state,kind):
        name,_,props=state.partition('[');name=name.split(':',1)[1]
        if name in materials:
            m=materials[name]
            return volume_bits((0,0,0,16,16,16)) if (kind=='opaque' and m['opaque']) or kind==m['role'] else 0
        if name not in banks:raise ValueError('屋面或挂幡读到未知构件：'+state)
        properties=dict(p.split('=') for p in props.rstrip(']').split(','));p=banks[name]['profiles'][int(properties['shape'])]
        boxes=p['occlusion'] if kind=='opaque' else [b[:6] for b in p['boxes'] if b[6]==kind]
        turn=('north','east','south','west').index(properties['facing']);result=0
        for box in boxes:
            b=tuple(box[:6])
            for _ in range(turn):b=(16-b[5],b[1],b[0],16-b[2],b[4],b[3])
            result|=volume_bits(b)
        return result

    cities={};issues=[]
    for city,cfg in rules['cities'].items():
        source_path=auth/f'cities/{city}.json';source_identity(source_path,cfg)
        source=json.loads(source_path.read_text());scene,construction=prepare(source,cfg,rules)
        manifest=json.loads((data/f'lord_manors/{city}.json').read_text())
        if manifest['construction']['checks']!='pass':raise ValueError('施工合同未通过')
        if manifest['construction']['roof_joints']!=construction['roof_joints']:raise ValueError('施工记录不是当前输入')
        requested=[(b,'opaque') for b in scene['boxes'] if b[6]=='roof']
        for row in construction['hanging_panels']:
            requested.extend((scene['boxes'][row[k]],'banner' if k=='panel' else 'opaque') for k in ('panel','support','peg'))
        requested.extend((title_box(sign,i),'plaque_'+str(i)) for i,sign in enumerate(scene.get('signs',[])))
        required=required_cells(requested);positions={p for p,_ in required};found={}
        for tile in manifest['tiles']:
            path=data/'structure'/(tile['template'].split(':',1)[1]+'.nbt')
            _,doc=read_gzipped_nbt(str(path));palette=[state_string(p) for p in doc['palette']]
            for b in doc['blocks']:
                p=tuple(b['pos'][i]+tile['offset'][i]+manifest['source_min'][i] for i in range(3))
                if p in positions:found[p]=palette[b['state']]
        failures=[]
        for (pos,kind),expected in required.items():
            state=found.get(pos);actual=state_bits(state,kind) if state else 0
            missing=(expected&~actual).bit_count()
            if missing:failures.append({'position':pos,'kind':kind,'missing_16th_voxels':missing,'actual':state})
        runtime_checked=0
        if runtime is not None:
            from province_capitals.regional.v2.common import rotated_state
            from province_capitals.blueprint_plan.manor_relayout import rotate_cell
            runtime=Path(runtime);bundle=json.loads((runtime/f'data/myvillage/capitals/v4/capital_{city}/city.json').read_text())
            placement=bundle['blueprint']['manor'];turn=placement['rotation_quarters'];w,_,d=manifest['size'];minimum=manifest['source_min'];origin=placement['origin']
            wanted={}
            for pos in positions:
                if pos not in found:continue
                x,z=rotate_cell(pos[0]-minimum[0],pos[2]-minimum[2],w,d,turn)
                target=(origin[0]+x,origin[1]+pos[1]-minimum[1],origin[2]+z)
                wanted[target]=rotated_state(found[pos],turn)
            needed_tiles={(x//16*16,z//16*16) for x,y,z in wanted};actual={}
            for tile in bundle['tiles']:
                if (tile['x'],tile['z']) not in needed_tiles:continue
                path=runtime/'data/myvillage/structure'/(tile['template'].split(':',1)[1]+'.nbt')
                _,doc=read_gzipped_nbt(str(path));palette=[state_string(p) for p in doc['palette']]
                for b in doc['blocks']:
                    p=(tile['x']+b['pos'][0],b['pos'][1],tile['z']+b['pos'][2])
                    if p in wanted:actual[p]=palette[b['state']]
            for pos,expected in wanted.items():
                if actual.get(pos)!=expected:failures.append({'default_city_position':pos,'expected':expected,'actual':actual.get(pos)})
            runtime_checked=len(wanted)
        if failures:issues.append({'city':city,'failures':failures})
        cities[city]={'roof_sheets':construction['roof_sheets'],'roof_joints':len(construction['roof_joints']),
                      'hanging_panels':len(construction['hanging_panels']),'title_boards':len(scene.get('signs',[])),'required_volume_blocks':len(required),
                      'default_city_blocks_checked':runtime_checked,
                      'status':'fail' if failures else 'pass'}
    return {'status':'fail' if issues else 'pass','cities':cities,'issues':issues,'default_city':'checked' if runtime is not None else 'not_run','method':'整数体积/邻接/锚固/实际NBT回读；未使用画面判定'}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--runtime',type=Path);parser.add_argument('--report',type=Path)
    args=parser.parse_args();result=audit(args.root,args.runtime);text=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
    if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(text)
    print(text,end='')
    if result['status']!='pass':raise SystemExit(1)


if __name__=='__main__':main()
