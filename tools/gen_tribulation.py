#!/usr/bin/env python3
"""突破雷劫资源唯一生成入口。调参源在 tools/tribulation/rules.json。"""
import argparse
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'tools/tribulation/rules.json'


def outputs():
    document=json.loads(SOURCE.read_text())
    assert document['schema_version']==2
    common=document['common'];trials=document['trials']
    kinds={'heaven':('qi_refining','qi_refining_9','foundation_establishment','foundation_early'),
           'golden_low':('foundation_establishment','foundation_late','golden_core','golden_early'),
           'golden_middle':('foundation_establishment','foundation_late','golden_core','golden_early'),
           'golden_high':('foundation_establishment','foundation_late','golden_core','golden_early')}
    assert len(trials)==len(kinds) and {t['kind'] for t in trials}==set(kinds)
    interval=common['strike_interval_ticks'];warning=common['warning_ticks']
    assert type(interval) is int and type(warning) is int and 0<warning<interval
    assert common['preparation_ticks']>0 and common['arena_radius']>common['warning_radius']>0
    data=ROOT/'src/main/resources/data';result={}
    for trial in trials:
        kind=trial['kind'];count=trial['required_hits'];fraction=trial['damage_fraction']
        assert type(count) is int and 1<=count<=32 and 0<fraction<=1
        values={k:v for k,v in common.items() if k not in ('strike_interval_ticks','warning_ticks')}
        values.update(schema_version=2,kind=kind,required_hits=count,duration_ticks=count*interval+1)
        values.update(dict(zip(('source_realm','source_stage','target_realm','target_stage'),('myvillage:'+v for v in kinds[kind]))))
        values['strikes']=[{'tick':interval*(i+1),'warning_ticks':warning,'main':True,'damage_fraction':fraction} for i in range(count)]
        result[data/f'myvillage/tribulation/{kind}.json']=values
    result.update({
        data / 'myvillage/damage_type/tribulation_main.json': {'message_id': 'myvillage.tribulation', 'scaling': 'never', 'exhaustion': 0.1},
        data / 'minecraft/tags/damage_type/bypasses_cooldown.json': {'replace': False, 'values': ['myvillage:tribulation_main']},
        data / 'myvillage/tags/damage_type/tribulation.json': {'replace': False, 'values': ['myvillage:tribulation_main']},
        data / 'myvillage/tags/damage_type/tribulation_unavoidable.json': {'replace': False, 'values': ['myvillage:tribulation_main']},
    })
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--jar',type=Path)
    args = parser.parse_args()
    failed = []
    for relative in ['myvillage/tribulation/golden_core.json','myvillage/damage_type/tribulation_echo.json']:
        obsolete=ROOT/'src/main/resources/data'/relative
        if obsolete.exists():
            if args.check:failed.append('已退役资源：'+relative)
            else:obsolete.unlink()
    for path, value in outputs().items():
        expected = json.dumps(value, ensure_ascii=False, indent=2) + '\n'
        if args.check:
            if not path.exists() or path.read_text() != expected:
                failed.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(expected)
    if args.jar:
        with zipfile.ZipFile(args.jar) as jar:
            names=jar.namelist()
            for path,value in outputs().items():
                name=path.relative_to(ROOT/'src/main/resources').as_posix()
                expected=json.dumps(value,ensure_ascii=False,indent=2)+'\n'
                if names.count(name)!=1 or jar.read(name).decode()!=expected:failed.append('jar:'+name)
            for retired in ['data/myvillage/tribulation/golden_core.json','data/myvillage/damage_type/tribulation_echo.json']:
                if retired in names:failed.append('jar包含退役资源：'+retired)
            if any('MockBreakthroughs' in name or 'TribulationEvidence' in name for name in names):failed.append('jar包含旧领奖入口或测试探针')
    if failed:
        raise SystemExit('雷劫生成资源不一致：' + ', '.join(failed))
    print('雷劫资源一致' if args.check else '已生成雷劫资源')


if __name__ == '__main__':
    main()
