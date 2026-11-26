#!/usr/bin/env python3
"""当前 V11 默认资源：七府正式重排与现有中州，统一聚合为 v4。"""
from __future__ import annotations
import argparse,base64,copy,gzip,json,shutil,subprocess,sys
from pathlib import Path
from tempfile import TemporaryDirectory
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
    if not isinstance(policy,dict) or set(policy)!={'levels'}:
        raise ValueError('州序必须显式声明 levels')
    levels=policy['levels'];profiles={p['id']:p for p in frozen['profiles']}
    if not isinstance(levels,list) or not levels or any(not isinstance(v,list) or not v for v in levels):
        raise ValueError('地区档位必须为非空分组')
    order=[v for level in levels for v in level]
    if (not isinstance(order,list) or any(not isinstance(v,str) for v in order)
            or len(order)!=len(profiles) or len(set(order))!=len(order) or set(order)!=set(profiles)):
        raise ValueError('州序必须完整且不重复地覆盖地区清单')
    if profiles[order[-1]]['role']!='anchor':
        raise ValueError('州序必须以中枢地区结束')
    if len(levels[-1])!=1:raise ValueError('中枢必须独占最终档位')
    tiers=[]
    for level in levels:
        values={frozen['tier_range'][1] if profiles[v]['role']=='anchor' else profiles[v]['tier'] for v in level}
        if len(values)!=1:raise ValueError('并列地区必须使用相同阶位')
        tiers.append(values.pop())
    if any(not 0<b-a<=frozen['tier_step'][1] for a,b in zip(tiers,tiers[1:])):
        raise ValueError('州序阶位必须递增且满足最大阶差')
    frozen['region_progression']=copy.deepcopy(policy)


def write_presets(out, preset, frozen, medium_map):
    medium = copy.deepcopy(frozen)
    medium['core_radius'] = medium_map['core_radius']
    medium['outer_radius'] = medium_map['outer_radius']
    medium['capitals'].update(ring_min=medium_map['ring_min'], ring_max=medium_map['ring_max'])

    generator = preset['dimensions']['minecraft:overworld']['generator']
    generator.pop('plan_hash', None)
    for name, rules in (('cultivation', frozen), ('cultivation_medium', medium)):
        raw = canonical(rules)
        transport = 'gzip:' + base64.b64encode(gzip.compress(raw, mtime=0)).decode()
        if len(raw) > 4 * 1024 * 1024 or len(transport) > 65000:
            raise ValueError(f'V11 世界预设 {name} 超出冻结规则容量')
        generator['rules'] = transport
        write(out/f'data/yuanxian/worldgen/world_preset/{name}.json', preset)


def aggregate(out:Path,source:Path,*,refresh_current=False):
    from player_economy_sites import place_capital_boards
    place_capital_boards(out)
    sys.path.insert(0,str(ROOT))
    from tools.tianheng.runtime import integrate as place_tianheng
    place_tianheng(out)
    if out.resolve()==source.resolve() and not refresh_current:raise ValueError('V11 不能覆盖 V10 资源')
    spec=json.loads(RULES.read_text());path=source/('data/yuanxian/worldplan/frozen_v11.json' if refresh_current else 'data/yuanxian/worldplan/frozen_v10.json');original=old.read_bounded(path,4*1024*1024);base=json.loads(original)
    if (spec.get('schema'),spec.get('algorithm'),spec.get('base'),base['algorithm'])!=(1,'yuanxian:worldplan_v11','frozen_v10.json','yuanxian:worldplan_v11' if refresh_current else 'yuanxian:worldplan_v10'):raise ValueError('V11 必须继承完整 V10')
    definitions=spec['assets']['cities'];regions={p['id'] for p in base['profiles']}
    if spec['assets']['accepted_versions']!=['v4'] or len(definitions)!=8 or {d['region_id'] for d in definitions}!=regions or len({d['city_id'] for d in definitions})!=8:raise ValueError('V11 必须八城齐全并只使用 v4')
    refs=[];summaries=[]
    for definition in sorted(definitions,key=lambda d:d['city_id']):
        ref,summary=old.inspect_city(out,definition,base['capitals']['life_places_limit'],accepted_versions=frozenset(('v4',)))
        refs.append(ref);summaries.append(summary)
    frozen=copy.deepcopy(base);frozen['algorithm']=spec['algorithm'];frozen['capitals']['assets']=spec['assets'];frozen['capitals']['bundles']=refs
    profiles={p.stem:json.loads(p.read_text()) for p in (ROOT/'src/main/resources/data/yuanxian/worldgen/region_profile').glob('*.json')}
    for profile in frozen['profiles']:
        for key in ('tier','qi','danger'):profile[key]=profiles[profile['id']][key]
    from wilderness_habitats.generate import generate as habitat_rules
    habitat=habitat_rules()
    resource=(json.dumps(habitat,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    frozen['habitats']={'resource_sha256':sha(resource)}
    lotus_resource='data/yuanxian/worldplan/lotus_mountain.bin.gz'
    lotus = gzip.compress(subprocess.check_output(
        ['node', str(ROOT/'tools/lotus_mountain/compile.cjs')], cwd=ROOT), mtime=0)
    (out/lotus_resource).parent.mkdir(parents=True, exist_ok=True)
    (out/lotus_resource).write_bytes(lotus)
    frozen['lotus_mountain']={**spec['lotus_mountain'], 'sha256':sha(lotus)}
    from tools.folded_ridge.compile import compile_assets as compile_folded_ridge
    frozen['folded_ridge'] = {**spec['folded_ridge'], 'sha256': compile_folded_ridge(out)}
    policy=spec.get('topology',{})
    if set(policy)!={'walled_pass_chance'} or policy['walled_pass_chance']!=1.0:raise ValueError('当前八城路网必须开放州序规定的魔域关隘')
    frozen.update(policy)
    apply_region_progression(frozen,spec.get('region_progression'))
    apply_spawn_policy(frozen,spec.get('spawn'))
    sect_root=ROOT/'build/generated/sect_blueprints/resources'
    catalog=json.loads((sect_root/'data/yuanxian/sect_blueprints/catalog.json').read_text())
    layouts=[];policy=None
    for sect_ref in catalog['layouts']:
        sect_file=sect_root/('data/'+sect_ref['resource'].replace(':','/',1))
        sect=json.loads(sect_file.read_text())
        if sha(sect_file.read_bytes())!=sect_ref['sha256']:raise ValueError('宗门正式清单摘要不一致')
        if policy is not None and policy!=sect['worldgen']:raise ValueError('宗门选址规则必须共用一份配置')
        policy=sect['worldgen']
        terrain=sect_root/('data/'+sect['terrain']['resource'].replace(':','/',1))
        if sha(terrain.read_bytes())!=sect['terrain']['sha256']:raise ValueError('宗门高度场摘要不一致')
        layouts.append({'id':sect['id'],'resource':sect_ref['resource'],'blueprint_sha256':sect_ref['sha256']})
    progression=json.loads((ROOT/'src/main/resources/data/yuanxian/world_sim/rules.json').read_text())['sect_progression']
    frozen['sects']={**policy,'layouts':layouts,**{key:progression[key] for key in ('entry_states','regional_hubs')}}
    mine_root=ROOT/'build/generated/starter_mine/resources'
    mine_path=mine_root/'data/yuanxian/starter_mine/layout.json'
    mine_bytes=old.read_bounded(mine_path,2*1024*1024)
    frozen['starter_mine']={'resource':'yuanxian:starter_mine/layout.json','sha256':sha(mine_bytes)}
    preset=json.loads((source/'data/yuanxian/worldgen/world_preset/cultivation.json').read_text());generator=preset['dimensions']['minecraft:overworld']['generator']
    if generator['type']!=('yuanxian:planned_v11' if refresh_current else 'yuanxian:planned_v10'):raise ValueError('前置默认预设不是 V10')
    generator.update(type='yuanxian:planned_v11',folded_ridge_biome='yuanxian:folded_ridge')
    write_presets(out, preset, frozen, spec['medium_map'])
    write(out/'data/yuanxian/worldplan/frozen_v11.json',frozen)
    report={'schema':1,'format':'capital_game_index','algorithm':spec['algorithm'],'cities':summaries,'total_tiles':sum(v['tiles'] for v in summaries),
            'total_bytes':sum(v['bytes'] for v in summaries),'frozen_rules_sha256':sha(canonical(frozen)),'source_rules_sha256':sha(original),
            'status':{'generation':'completed','native':'not_run','owner_appearance':'not_verified'}}
    write(out/'data/yuanxian/capitals/v4/index.json',report)
    return report


def refresh_habitat_reference(out:Path):
    """仅重生成冻结生态资源引用；供未改城市与宗门资源的局部构建复用。"""
    from wilderness_habitats.generate import generate
    path=out/'data/yuanxian/worldplan/frozen_v11.json'
    original=old.read_bounded(path,4*1024*1024);frozen=json.loads(original)
    if frozen['algorithm']!='yuanxian:worldplan_v11':raise ValueError('生态引用刷新需要当前V11冻结规则')
    resource=(json.dumps(generate(),ensure_ascii=False,indent=2)+'\n').encode()
    authored=ROOT/'src/main/resources/data/yuanxian/wilderness/habitats.json'
    if authored.read_bytes()!=resource:raise ValueError('生态资源未由当前作者输入生成')
    before=copy.deepcopy(frozen);frozen['habitats']={'resource_sha256':sha(resource)}
    raw=canonical(frozen)
    preset_path=out/'data/yuanxian/worldgen/world_preset/cultivation.json'
    preset=json.loads(preset_path.read_text());generator=preset['dimensions']['minecraft:overworld']['generator']
    if generator['type']!='yuanxian:planned_v11':raise ValueError('需要当前V11预设')
    write_presets(out, preset, frozen, json.loads(RULES.read_text())['medium_map'])
    write(path,frozen)
    index_path=out/'data/yuanxian/capitals/v4/index.json'
    if index_path.exists():
        index=json.loads(index_path.read_text());index['frozen_rules_sha256']=sha(raw);write(index_path,index)
    return {'operation':'refresh_habitat_reference','before':sha(original),'after':sha(raw),
            'old_habitats':before.get('habitats'),'habitats':frozen['habitats']}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,default=OUT);parser.add_argument('--v10-out',type=Path,default=V10);parser.add_argument('--v9-out',type=Path,default=V9);parser.add_argument('--legacy-out',type=Path,default=LEGACY);parser.add_argument('--skip-base-export',action='store_true');parser.add_argument('--aggregate-only',action='store_true');parser.add_argument('--refresh-current',action='store_true',help='从当前 V11 导出资源重建冻结规则，不重导出未变城市');parser.add_argument('--refresh-habitat-only',action='store_true',help='仅刷新当前冻结规则的生态资源引用');a=parser.parse_args()
    if a.refresh_habitat_only:print(json.dumps(refresh_habitat_reference(a.out),ensure_ascii=False));return
    if not a.aggregate_only and not a.refresh_current:
        if not a.skip_base_export:subprocess.run([sys.executable,str(Path(__file__).with_name('compile_v10.py')),'--out',str(a.v10_out),'--v9-out',str(a.v9_out),'--legacy-out',str(a.legacy_out)],check=True)
        export_cities(a)
    result=aggregate(a.out,a.out if a.refresh_current else a.v10_out,refresh_current=a.refresh_current);print(json.dumps({'V11':'aggregated','cities':len(result['cities']),'tiles':result['total_tiles'],'bytes':result['total_bytes']},ensure_ascii=False))


def export_cities(args):
    from capital_waystones.generate import generate as decorate_waystones
    from housing.town_expansion import integrate as build_pavilion_donors
    from player_estates.runtime_export import integrate as place_estates
    from province_capitals.inner_neighborhoods.runtime import integrate as place_neighborhoods
    from qianji.runtime_export import integrate as place_qianji

    args.out.parent.mkdir(parents=True,exist_ok=True)
    with TemporaryDirectory(prefix="capital-donors-",dir=args.out.parent) as temp:
        donors=Path(temp)/"resources"
        subprocess.run([sys.executable,str(ROOT/'tools/province_capitals/environment_v4/export_game.py'),
                        '--out',str(donors),'--source',str(args.v10_out),'--v9-source',str(args.v9_out),
                        '--legacy-source',str(args.legacy_out),'--regions','zhongzhou'],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/province_capitals/blueprint_plan/runtime.py'),
                        '--out',str(donors)],check=True)
        place_estates(donors)
        place_qianji(donors)
        # 有些传送亭需要原扩建地形才能施工。最终布局只搬运原设施和亭台。
        build_pavilion_donors(donors)
        decorate_waystones(donors)
        # 七城直接消费新街坊坐标。旧坐标只用于取得已批准的原楼体与独立亭台。
        for relative in ("capitals/v4", "structure/province_capitals/v4"):
            dest=args.out/"data/yuanxian"/relative
            if dest.exists():
                shutil.rmtree(dest)
            shutil.copytree(donors/"data/yuanxian"/relative/"capital_zhongzhou",dest/"capital_zhongzhou")
        place_neighborhoods(donors,args.out)


if __name__=='__main__':main()
