#!/usr/bin/env python3
"""V11 只替换两类街景设施；V10 及更早的规则与资源继续冻结。"""
from __future__ import annotations
import argparse,base64,copy,gzip,json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools'))
from province_capitals.game.compile import canonical,sha,write
from province_capitals.game import compile_v10 as old

OUT=ROOT/'build/generated/province_capitals_v11/resources'
V10=ROOT/'build/generated/province_capitals_v10/resources'
V9=ROOT/'build/generated/province_capitals_v9/resources'
LEGACY=ROOT/'build/generated/province_capitals/resources'
RULES=ROOT/'blueprints/worldplan/rules_v11.json'


def aggregate(out:Path,source:Path):
    if out.resolve()==source.resolve():raise ValueError('V11 不能覆盖 V10 资源')
    spec=json.loads(RULES.read_text());path=source/'data/myvillage/worldplan/frozen_v10.json';original=old.read_bounded(path,4*1024*1024);base=json.loads(original)
    if (spec.get('schema'),spec.get('algorithm'),spec.get('base'),base['algorithm'])!=(1,'myvillage:worldplan_v11','frozen_v10.json','myvillage:worldplan_v10'):raise ValueError('V11 必须继承完整 V10')
    definitions=spec['assets']['cities'];regions={p['id'] for p in base['profiles']}
    if spec['assets']['accepted_versions']!=['v4'] or len(definitions)!=8 or {d['region_id'] for d in definitions}!=regions or len({d['city_id'] for d in definitions})!=8:raise ValueError('V11 必须八城齐全并只使用 v4')
    refs=[];summaries=[]
    for definition in sorted(definitions,key=lambda d:d['city_id']):
        ref,summary=old.inspect_city(out,definition,base['capitals']['life_places_limit'],accepted_versions=frozenset(('v4',)))
        refs.append(ref);summaries.append(summary)
    frozen=copy.deepcopy(base);frozen['algorithm']=spec['algorithm'];frozen['capitals']['assets']=spec['assets'];frozen['capitals']['bundles']=refs
    raw=canonical(frozen);transport='gzip:'+base64.b64encode(gzip.compress(raw,mtime=0)).decode()
    if len(raw)>4*1024*1024 or len(transport)>65000:raise ValueError('V11 冻结规则超出既有边界')
    preset=json.loads((source/'data/myvillage/worldgen/world_preset/cultivation.json').read_text());generator=preset['dimensions']['minecraft:overworld']['generator']
    if generator['type']!='myvillage:planned_v10':raise ValueError('前置默认预设不是 V10')
    generator.update(type='myvillage:planned_v11',rules=transport);generator.pop('plan_hash',None)
    write(out/'data/myvillage/worldplan/frozen_v11.json',frozen);write(out/'data/myvillage/worldgen/world_preset/cultivation.json',preset)
    report={'schema':1,'format':'capital_game_index','algorithm':spec['algorithm'],'cities':summaries,'total_tiles':sum(v['tiles'] for v in summaries),
            'total_bytes':sum(v['bytes'] for v in summaries),'frozen_rules_sha256':sha(raw),'legacy_v10_file_sha256':sha(original),
            'status':{'generation':'completed','native':'not_run','owner_appearance':'not_verified'}}
    write(out/'data/myvillage/capitals/v4/index.json',report)
    return report


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,default=OUT);parser.add_argument('--v10-out',type=Path,default=V10);parser.add_argument('--v9-out',type=Path,default=V9);parser.add_argument('--legacy-out',type=Path,default=LEGACY);parser.add_argument('--skip-base-export',action='store_true');parser.add_argument('--aggregate-only',action='store_true');a=parser.parse_args()
    if not a.aggregate_only:
        if not a.skip_base_export:subprocess.run([sys.executable,str(Path(__file__).with_name('compile_v10.py')),'--out',str(a.v10_out),'--v9-out',str(a.v9_out),'--legacy-out',str(a.legacy_out)],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/province_capitals/environment_v4/export_game.py'),'--out',str(a.out),'--source',str(a.v10_out),'--v9-source',str(a.v9_out),'--legacy-source',str(a.legacy_out)],check=True)
    result=aggregate(a.out,a.v10_out);print(json.dumps({'V11':'aggregated','cities':len(result['cities']),'tiles':result['total_tiles'],'bytes':result['total_bytes']},ensure_ascii=False))


if __name__=='__main__':main()
