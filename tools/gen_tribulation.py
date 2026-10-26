#!/usr/bin/env python3
"""金丹雷劫资源唯一生成入口。调参源在 tools/tribulation/rules.json。"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'tools/tribulation/rules.json'


def outputs():
    rules = json.loads(SOURCE.read_text())
    assert rules['schema_version'] == 1
    assert sum(s['main'] for s in rules['strikes']) == 6
    assert all(0 < s['warning_ticks'] < s['tick'] <= rules['duration_ticks'] for s in rules['strikes'])
    assert all(0 < s['damage_fraction'] <= 1 for s in rules['strikes'])
    assert [s['tick'] for s in rules['strikes']] == sorted(set(s['tick'] for s in rules['strikes']))
    assert rules['preparation_ticks'] > 0 and rules['arena_radius'] > rules['echo_radius'] > 0
    data = ROOT / 'src/main/resources/data'
    return {
        data / 'myvillage/tribulation/golden_core.json': rules,
        data / 'myvillage/damage_type/tribulation_main.json': {'message_id': 'myvillage.tribulation', 'scaling': 'never', 'exhaustion': 0.1},
        data / 'myvillage/damage_type/tribulation_echo.json': {'message_id': 'myvillage.tribulation', 'scaling': 'never', 'exhaustion': 0.1},
        data / 'minecraft/tags/damage_type/bypasses_cooldown.json': {'replace': False, 'values': ['myvillage:tribulation_main']},
        data / 'myvillage/tags/damage_type/tribulation.json': {'replace': False, 'values': ['myvillage:tribulation_main', 'myvillage:tribulation_echo']},
        data / 'myvillage/tags/damage_type/tribulation_unavoidable.json': {'replace': False, 'values': ['myvillage:tribulation_main']},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    failed = []
    for path, value in outputs().items():
        expected = json.dumps(value, ensure_ascii=False, indent=2) + '\n'
        if args.check:
            if not path.exists() or path.read_text() != expected:
                failed.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(expected)
    if failed:
        raise SystemExit('雷劫生成资源不一致：' + ', '.join(failed))
    print('雷劫资源一致' if args.check else '已生成雷劫资源')


if __name__ == '__main__':
    main()
