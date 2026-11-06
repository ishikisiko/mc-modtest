#!/usr/bin/env python3
"""检查当前数表、资源唯一归属和交付 jar；数值/事件行为另由 Java 与原生探针验证。"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DATA = Path('src/main/resources/data/myvillage')
PROGRESSION = DATA / 'stats/progression.json'

def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'重复字段：{key}')
        result[key] = value
    return result

def validate(root: Path = ROOT, jar: Path | None = None) -> dict:
    errors = []
    resources = [PROGRESSION, DATA / 'alchemy/foundation.json',
                 *[DATA / f'myvillage/realm/{name}.json' for name in ('mortal','qi_refining','foundation_establishment','golden_core')],
                 Path('src/main/resources/assets/myvillage/lang/zh_cn.json')]
    checked = {}
    for path in resources:
        try:
            payload = (root / path).read_bytes()
            checked[str(path)] = json.loads(payload, object_pairs_hook=unique)
        except (OSError, ValueError) as exc:
            errors.append(f'资源错误：{path}：{exc}')
    rules = checked.get(str(PROGRESSION), {})
    if rules.get('schema') != 1 or 'mock_tribulation' in rules:
        errors.append('数表版本非法或仍含 MOCK 开关')
    def vectors(value, count, label):
        if not isinstance(value, list) or len(value) != count:
            errors.append(f'{label}：数表长度错误'); return
        for row in value:
            if not isinstance(row, list) or len(row) != 4 or any(type(v) not in (float,int) or not math.isfinite(v) or v < 0 for v in row) or type(row[3]) is not int:
                errors.append(f'{label}：属性四元组错误')
    vectors(rules.get('mortal'), 2, '凡人')
    vectors(rules.get('qi_refining'), 9, '炼气')
    vectors(rules.get('foundation',{}).get('breakthrough'),4,'筑基')
    vectors(rules.get('golden_core',{}).get('breakthrough'),3,'金丹')
    for name in ('mortal','qi_refining','foundation_establishment','golden_core'):
        realm = checked.get(str(DATA / f'myvillage/realm/{name}.json'),{})
        if any('spiritual_power_cap' in stage for stage in realm.get('stages',[])):
            errors.append(f'{name}：灵力上限必须只由 progression.json 提供')
    foundation = checked.get(str(DATA / 'alchemy/foundation.json'),{})
    if foundation.get('version') != 2 or 'bonus_percent' in foundation:
        errors.append('筑基仍含旧根基百分比规则')
    defense = rules.get('defense',{})
    try:
        t, p = defense['transition'], defense['floor']
        if not 0 < t < 1 or not 0 < p < 1-t:
            errors.append('防御转折点或保底比例非法')
    except (KeyError, TypeError): errors.append('缺少防御规则')
    if jar is not None:
        try:
            with zipfile.ZipFile(jar) as archive:
                names = archive.namelist()
                for path in resources:
                    entry = path.relative_to('src/main/resources').as_posix()
                    if names.count(entry) != 1 or archive.read(entry) != (root/path).read_bytes():
                        errors.append(f'交付资源不一致：{entry}')
                for name in ('stats/BodyDefense','stats/CultivationStatsRules','stats/runtime/ModStatsAttributes',
                             'cultivation/tribulation/TribulationManager','cultivation/tribulation/TribulationKind','client/cultivation/panel/NumericalStatsPage'):
                    entry = f'com/example/myvillage/{name}.class'
                    if names.count(entry) != 1 or not archive.read(entry).startswith(b'\xca\xfe\xba\xbe'):
                        errors.append(f'缺少编译类：{entry}')
                if any('AdditiveStatsEvidence' in name or 'TribulationEvidence' in name or 'MockBreakthroughs' in name for name in names):
                    errors.append('原生探针不得进入交付 jar')
        except (OSError, ValueError, zipfile.BadZipFile, KeyError) as exc:
            errors.append(f'jar 读取失败：{exc}')
    return {'pass':not errors,'checked_resources':len(checked),'errors':errors,
            'progression_sha256':hashlib.sha256((root/PROGRESSION).read_bytes()).hexdigest()}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path)
    args=parser.parse_args();result=validate(jar=args.jar)
    print(json.dumps(result,ensure_ascii=False,indent=2));return 0 if result['pass'] else 1
if __name__=='__main__': raise SystemExit(main())
