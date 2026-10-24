#!/usr/bin/env python3
"""从可审阅的定点 authored patch 生成资源；无需 reports、Git、网络或历史模板。"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'blueprints/qianzhen_roof_repair/sample_pavilion.json'
RESOURCE = Path('data/myvillage/story/qianzhen_roof_repair.json')
STATE = re.compile(r'minecraft:[a-z0-9_]+(?:\[[a-z0-9_=,.-]+\])?\Z')


def validate(raw):
    if raw['schema'] != 1 or raw['id'] != 'qianzhen_sample_pavilion_supports_v1' or raw['scene_id'] != 'qianzhen_chapter_1' or raw['site_id'] != 'canal_gardens':
        raise ValueError('unknown bounded patch identity')
    lo, hi = raw['bounds']['min'], raw['bounds']['max']
    if lo != [31, 0, 7] or hi != [44, 10, 15] or len(raw['targets']) != 45:
        raise ValueError('patch extent/count differs from reviewed pavilion')
    cells = set()
    for kind in ('targets', 'guards'):
        for row in raw[kind]:
            p = tuple(row['pos'])
            if len(p) != 3 or any(type(v) is not int or not lo[i] <= v <= hi[i] for i, v in enumerate(p)) or p in cells:
                raise ValueError('duplicate/outside patch cell')
            cells.add(p)
            if kind == 'targets':
                if row['before'] != 'minecraft:air' or not re.fullmatch(r'minecraft:spruce_log\[axis=[xyz]\]', row['after']) or not row['reason'] or not (33 <= p[0] <= 43 and 6 <= p[1] <= 9 and 9 <= p[2] <= 13):
                    raise ValueError('patch must only add reviewed timber into air')
            elif not STATE.fullmatch(row['state']):
                raise ValueError('invalid guard state')
    if len(cells) != 14 * 11 * 9:
        raise ValueError('guard coverage incomplete')
    if {r['version'] for r in raw['supported']} != {'1-f795e60913419e05', '1-56da335c3dd1262f'}:
        raise ValueError('unreviewed source version')
    for row in raw['supported']:
        site = row['site']
        if site != {'id':'canal_gardens', 'template':'myvillage:qianzhen/canal_gardens', 'origin':[122,0,65], 'size':[48,22,47], 'sha256':'f9c90f21a201cec9da1f05617b2c7a2f2323c78ecc9d453fc18d80331bc4dcb8'} or not re.fullmatch('[0-9a-f]{64}', row['layout_sha256']):
            raise ValueError('unreviewed source site identity')
    if raw['provenance']['baseline_commit'] != '97b37a991350961290ab37bb5a254103da1a8c2d':
        raise ValueError('baseline provenance differs')
    return raw


def generate(source=SOURCE):
    data = Path(source).read_bytes()
    raw = validate(json.loads(data))
    raw['authored_patch_sha256'] = hashlib.sha256(data).hexdigest()
    return (json.dumps(raw, ensure_ascii=False, sort_keys=True, separators=(',', ':'))+'\n').encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=SOURCE)
    parser.add_argument('--out', type=Path, default=ROOT/'build/generated/qianzhen-roof-repair/resources')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    dest, expected = args.out/RESOURCE, generate(args.source)
    if args.check:
        if not dest.is_file() or dest.read_bytes() != expected:
            raise SystemExit('屋架补丁资源与 authored 输入不一致：'+str(dest))
    else:
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(expected)
    print('屋架补丁资源检查通过' if args.check else '已生成45格定点屋架补丁（1341格只读保护）')


if __name__ == '__main__':
    main()
