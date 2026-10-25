"""男女修组合原生加载、Look/Colours 存档重进和男修四衣装画面。"""
from __future__ import annotations

import json
from pathlib import Path
import re
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from tools.combat_capture import npc, procs, scene, session

OUT = ROOT / 'out/preview/npc_variants_native'
APPEARANCES = (('male_a', 'm_01_01_a', -1), ('male_b', 'm_02_04_b', 50),
               ('male_c', 'm_03_02_c', 66), ('male_legacy', 'm_05_03_legacy', 82))
CASES = ('look_load', 'save_reload') + tuple('appearance_'+a[0] for a in APPEARANCES)


def fixture_colours(index):
    return (-1,50,66,82)[index % 4]


def parse_colours(reply):
    match = re.search(r'(?:^|[,{}])\s*Colours:\s*(-?\d+)', reply)
    return int(match.group(1)) if match else -1


def parse_look(reply):
    match = re.search(r'(?:^|[,{}])\s*Look:\s*"([a-z0-9_]+)"', reply)
    return match.group(1) if match else 'default'


def main():
    # 固定源由 Modal runner 传入；禁止把此入口误当本机游戏测试。
    if not Path('/workspace/request.json').is_file():
        raise SystemExit('此采集仅允许通过 Modal npc-variants-runtime 运行')
    OUT.mkdir(parents=True, exist_ok=True)
    request = json.loads(Path('/workspace/request.json').read_text())
    report = {'source_commit': request['commit'], 'selected': list(CASES), 'completed': [],
              'unrun': list(CASES), 'checks': [], 'evidence_tier': 'native_developer_capture',
              'player_visual_acceptance': 'not_verified', 'mapped_player_actions': 'not_tested'}

    def save():
        (OUT / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')

    def complete(name):
        report['completed'].append(name)
        report['unrun'].remove(name)
        save()

    def start(reuse=False):
        session.start(session.SessionConfig(session_id='npc-variants-' + ('reload' if reuse else 'initial'),
                      language='zh_cn', reuse_world=reuse, settle=5, lock_timeout=60,
                      prebuilt_resources=True,
                      lock=str(procs.default_lock_path(ROOT))), wait_timeout=900)
        s = session.Session.attach()
        s.run(f'execute in minecraft:overworld run tp {s.username} 0.5 {scene.FLOOR_Y} 0.5')
        return s

    def read_all(s, stage):
        rows = []
        for index, expected in enumerate(npc.LOOKS):
            selector = f'@e[tag=npc_variant_{index},limit=1]'
            raw, uuid = s.run(f'data get entity {selector}', f'data get entity {selector} UUID')
            exists = 'UUID:' in raw
            actual = parse_look(raw)
            colour = parse_colours(raw)
            rows.append({'look': expected, 'actual': actual, 'uuid': uuid, 'raw': raw,
                         'colours': colour, 'expected_colours': fixture_colours(index),
                         'pass': exists and actual == expected and colour == fixture_colours(index)})
        (OUT / (stage + '.json')).write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n')
        report['checks'].append({'name': stage, 'pass': all(row['pass'] for row in rows), 'count': len(rows)})
        save()
        if not all(row['pass'] for row in rows):
            raise AssertionError(f'{stage}: Look 读取不匹配')
        return rows

    save()
    try:
        s = start()
        s.run(*scene.world_rules(), f'op {s.username}', f'gamemode creative {s.username}',
              'forceload add -48 -48 48 48')
        for i, look in enumerate(npc.LOOKS):
            x, z = (i % 15) * 3 - 21, (i // 15) * 3 + 3
            extra = npc.look_nbt(look) + ',NoAI:1b,Invulnerable:1b'
            if fixture_colours(i) >= 0:
                extra += f',Colours:{fixture_colours(i)}'
            command = npc.summon('myvillage:cultivator', (x, scene.FLOOR_Y, z), 0,
                                 tag=f'npc_variant_{i}', extra=extra)
            s.run(command)
        time.sleep(1)
        before = read_all(s, 'before_restart')
        complete('look_load')
        s.run('save-all flush')
        session.stop()
        s = start(reuse=True)
        s.run('forceload add -48 -48 48 48')
        time.sleep(1)
        after = read_all(s, 'after_restart')
        same = [row['uuid'] for row in before] == [row['uuid'] for row in after]
        report['checks'].append({'name': 'same_entities_after_restart', 'pass': same})
        if not same:
            raise AssertionError('保存重进后的实体 UUID 改变')
        complete('save_reload')
        for label, look, colours in APPEARANCES:
            dest = OUT / label
            dest.mkdir(exist_ok=True)
            evidence = npc.run_npc(s, 'myvillage:cultivator', dest, ['idle', 'walk'], look=look,
                                   colours=None if colours < 0 else colours)
            passed = len(evidence['stills']) == 13 and len(evidence['videos']) == 2
            report['checks'].append({'name': 'appearance_' + label, 'pass': passed, 'look': look, 'colours': colours,
                                     'stills': len(evidence['stills']), 'videos': len(evidence['videos'])})
            if not passed:
                raise AssertionError('原生画面采集缺项')
            complete('appearance_' + label)
        report['status'] = 'pass'
    except Exception as error:
        report['status'] = 'fail'
        report['error'] = repr(error)
        raise
    finally:
        save()
        session.stop()
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
