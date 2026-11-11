#!/usr/bin/env python3
"""当前 V11 默认资源：七府正式重排与现有中州，统一聚合为 v4。"""
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


def apply_spawn_policy(frozen, policy):
    if not isinstance(policy,dict) or set(policy)!={'excluded_regions'}:
        raise ValueError('初始城规则必须显式声明排除的地区')
    values=policy['excluded_regions']
    profiles={p['id']:p for p in frozen['profiles']}
    if (not isinstance(values,list) or any(not isinstance(v,str) or v not in profiles for v in values)
            or len(set(values))!=len(values)):
        raise ValueError('初始城排除名单包含未知或重复地区')
    if not any(p['role']!='walled' and key not in values for key,p in profiles.items()):
        raise ValueError('初始城排除名单不能移除全部开放地区')
    frozen['spawn']['excluded_regions']=list(values)


def apply_region_progression(frozen, policy):
    if not isinstance(policy,dict) or set(policy)!={'order'}:
        raise ValueError('州序必须显式声明 order')
    order=policy['order'];profiles={p['id']:p for p in frozen['profiles']}
    if (not isinstance(order,list) or any(not isinstance(v,str) for v in order)
            or len(order)!=len(profiles) or len(set(order))!=len(order) or set(order)!=set(profiles)):
        raise ValueError('州序必须完整且不重复地覆盖地区清单')
    if profiles[order[-1]]['role']!='anchor':
        raise ValueError('州序必须以中枢地区结束')
    tiers=[frozen['tier_range'][1] if profiles[v]['role']=='anchor' else profiles[v]['tier'] for v in order]
    if any(not 0<b-a<=frozen['tier_step'][1] for a,b in zip(tiers,tiers[1:])):
        raise ValueError('州序阶位必须递增且满足最大阶差')
    frozen['region_progression']=copy.deepcopy(policy)


def aggregate(out:Path,source:Path,*,refresh_current=False):
    from player_estates.runtime_export import integrate
    integrate(out)
    from qianji.runtime_export import integrate as integrate_qianji
    integrate_qianji(out)
    from player_economy_sites import place_capital_boards
    place_capital_boards(out)
    if out.resolve()==source.resolve() and not refresh_current:raise ValueError('V11 不能覆盖 V10 资源')
    spec=json.loads(RULES.read_text());path=source/('data/myvillage/worldplan/frozen_v11.json' if refresh_current else 'data/myvillage/worldplan/frozen_v10.json');original=old.read_bounded(path,4*1024*1024);base=json.loads(original)
    if (spec.get('schema'),spec.get('algorithm'),spec.get('base'),base['algorithm'])!=(1,'myvillage:worldplan_v11','frozen_v10.json','myvillage:worldplan_v11' if refresh_current else 'myvillage:worldplan_v10'):raise ValueError('V11 必须继承完整 V10')
    definitions=spec['assets']['cities'];regions={p['id'] for p in base['profiles']}
    if spec['assets']['accepted_versions']!=['v4'] or len(definitions)!=8 or {d['region_id'] for d in definitions}!=regions or len({d['city_id'] for d in definitions})!=8:raise ValueError('V11 必须八城齐全并只使用 v4')
    refs=[];summaries=[]
    for definition in sorted(definitions,key=lambda d:d['city_id']):
        ref,summary=old.inspect_city(out,definition,base['capitals']['life_places_limit'],accepted_versions=frozenset(('v4',)))
        refs.append(ref);summaries.append(summary)
    frozen=copy.deepcopy(base);frozen['algorithm']=spec['algorithm'];frozen['capitals']['assets']=spec['assets'];frozen['capitals']['bundles']=refs
    policy=spec.get('topology',{})
    if set(policy)!={'walled_pass_chance'} or policy['walled_pass_chance']!=1.0:raise ValueError('当前八城路网必须开放州序规定的魔域关隘')
    frozen.update(policy)
    apply_region_progression(frozen,spec.get('region_progression'))
    apply_spawn_policy(frozen,spec.get('spawn'))
    sect_root=ROOT/'build/generated/sect_blueprints/resources'
    catalog=json.loads((sect_root/'data/myvillage/sect_blueprints/catalog.json').read_text())
    layouts=[];policy=None
    for sect_ref in catalog['layouts']:
        sect_file=sect_root/('data/'+sect_ref['resource'].replace(':','/',1))
        sect=json.loads(sect_file.read_text())
        if sha(sect_file.read_bytes())!=sect_ref['sha256']:raise ValueError('宗门正式清单摘要不一致')
        if policy is not None and policy!=sect['worldgen']:raise ValueError('宗门选址规则必须共用一份配置')
        policy=sect['worldgen']
        terrain=sect_root/('data/'+sect['terrain']['resource'].replace(':','/',1))
        if sha(terrain.read_bytes())!=sect['terrain']['sha256']:raise ValueError('宗门高度场摘要不一致')
        layouts.append({'id':sect['id'],'archetype':sect['archetype'],'scale':sect['scale'],
                        'width':sect['size'][0],'height':sect['size'][1],'depth':sect['size'][2],
                        'blend_margin':sect['blend_margin'],'max_y':sect['max_y'],'road_port':sect['road_port'],
                        **dict(zip(('entry_x','entry_y','entry_z'),sect['entry_anchor'])),
                        'blueprint_sha256':sect_ref['sha256'],'terrain_gzip':base64.b64encode(terrain.read_bytes()).decode()})
    frozen['sects']={**policy,'layouts':layouts}
    mine_root=ROOT/'build/generated/starter_mine/resources'
    mine_path=mine_root/'data/myvillage/starter_mine/layout.json'
    mine_bytes=old.read_bounded(mine_path,2*1024*1024)
    frozen['starter_mine']={'resource':'myvillage:starter_mine/layout.json','sha256':sha(mine_bytes)}
    raw=canonical(frozen);transport='gzip:'+base64.b64encode(gzip.compress(raw,mtime=0)).decode()
    if len(raw)>4*1024*1024 or len(transport)>65000:raise ValueError('V11 冻结规则超出既有边界')
    preset=json.loads((source/'data/myvillage/worldgen/world_preset/cultivation.json').read_text());generator=preset['dimensions']['minecraft:overworld']['generator']
    if generator['type']!=('myvillage:planned_v11' if refresh_current else 'myvillage:planned_v10'):raise ValueError('前置默认预设不是 V10')
    generator.update(type='myvillage:planned_v11',rules=transport);generator.pop('plan_hash',None)
    write(out/'data/myvillage/worldplan/frozen_v11.json',frozen);write(out/'data/myvillage/worldgen/world_preset/cultivation.json',preset)
    report={'schema':1,'format':'capital_game_index','algorithm':spec['algorithm'],'cities':summaries,'total_tiles':sum(v['tiles'] for v in summaries),
            'total_bytes':sum(v['bytes'] for v in summaries),'frozen_rules_sha256':sha(raw),'source_rules_sha256':sha(original),
            'status':{'generation':'completed','native':'not_run','owner_appearance':'not_verified'}}
    write(out/'data/myvillage/capitals/v4/index.json',report)
    return report


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,default=OUT);parser.add_argument('--v10-out',type=Path,default=V10);parser.add_argument('--v9-out',type=Path,default=V9);parser.add_argument('--legacy-out',type=Path,default=LEGACY);parser.add_argument('--skip-base-export',action='store_true');parser.add_argument('--aggregate-only',action='store_true');parser.add_argument('--refresh-current',action='store_true',help='从当前 V11 导出资源重建冻结规则，不重导出未变城市');a=parser.parse_args()
    if not a.aggregate_only and not a.refresh_current:
        if not a.skip_base_export:subprocess.run([sys.executable,str(Path(__file__).with_name('compile_v10.py')),'--out',str(a.v10_out),'--v9-out',str(a.v9_out),'--legacy-out',str(a.legacy_out)],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/province_capitals/environment_v4/export_game.py'),'--out',str(a.out),'--source',str(a.v10_out),'--v9-source',str(a.v9_out),'--legacy-source',str(a.legacy_out),'--regions','zhongzhou'],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/province_capitals/blueprint_plan/runtime.py'),'--out',str(a.out)],check=True)
    result=aggregate(a.out,a.out if a.refresh_current else a.v10_out,refresh_current=a.refresh_current);print(json.dumps({'V11':'aggregated','cities':len(result['cities']),'tiles':result['total_tiles'],'bytes':result['total_bytes']},ensure_ascii=False))


if __name__=='__main__':main()
