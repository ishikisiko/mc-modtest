"""Colab 单客户端报幕检查。RCON 准备位置；实际移动、F1 和菜单使用映射输入。"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / 'tools'))
from tools.quest_realms.native_evidence import heavy_lock
from tools.worldplan import acceptance as support
from tools.qianzhen.realm_evidence import Client, wait
from tools.alchemy_quality_evidence import read_data

CASES = ['dedicated_start', 'province_A', 'town_C', 'mapped_movement', 'same_town_no_replay',
         'hud_hidden_waits', 'dimension_clear']


class Evidence:
    def __init__(self, out: Path, previous: Path):
        self.out = out.resolve()
        self.out.mkdir(parents=True, exist_ok=False)
        old = json.loads(previous.read_text())
        if old.get('kind') != 'combat_quest_native' or old.get('status') != 'passed' or old.get('unrun'):
            raise RuntimeError('只能复用已通过的隔离主世界生成，不能恢复暂停的小BOSS场景')
        world = old.get('world', '')
        if not re.fullmatch(r'quest_runtime_[a-f0-9]{8}', world):
            raise RuntimeError('不是采集器创建的临时主世界')
        self.world = 'arrival_' + secrets.token_hex(4)
        self.name = 'Arrival' + secrets.token_hex(3)
        shutil.copytree(previous.parents[2] / 'run-acceptance' / world, ROOT / 'run-acceptance' / self.world,
                        ignore=shutil.ignore_patterns('session.lock'))
        jar = ROOT / 'build/libs/myvillage-0.131.1.jar'
        self.report = {'kind': 'region_entry_native', 'status': 'running', 'selected': CASES, 'completed': [],
                       'failed': [], 'unrun': CASES.copy(), 'checks': [], 'actions': [], 'commands': [],
                       'screenshots': [], 'cleanup': [], 'world': self.world,
                       'product_commit': json.loads((ROOT / 'candidate.json').read_text())['commit'],
                       'collector_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                       'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                       'world_copy_from': str(previous), 'owner_appearance': 'not_verified',
                       'owner_fps': 'not_verified', 'multiplayer': 'not_selected',
                       'boundary': '复用隔离世界的地形；RCON 仅准备位置、天气和维度。自动位置报幕与真实按键不代替玩家观感验收。'}
        self.save()

    def save(self): support.write(self.out / 'report.json', self.report)
    def action(self, kind, **detail):
        self.report['actions'].append({'kind': kind, 'time': time.monotonic(), **detail}); self.save()
    def check(self, name, passed, detail):
        self.report['checks'].append({'name': name, 'passed': bool(passed), 'detail': detail}); self.save()
        if not passed: raise AssertionError(name + ': ' + repr(detail))
    def done(self, name):
        self.report['completed'].append(name)
        self.report['unrun'] = [v for v in CASES if v not in self.report['completed']]; self.save()
    def rc(self, command):
        reply = self.server.cmd(command)
        self.report['commands'].append({'command': command, 'reply': reply}); self.save(); return reply
    def starts(self, client): return [line for line in client.tail(0).splitlines() if 'ARRIVAL_UI kind=' in line]
    def started(self, client, offset, kind):
        return wait(lambda: client.tail(offset), lambda text: 'ARRIVAL_UI kind=' + kind in text, 45, '自动报幕 ' + kind)
    def teleport(self, client, city, outside=False):
        entry = city['entry']; x, y, z = entry['x'], entry['y'], entry['z']
        if outside: x = city['x'] - 20; z = city['z'] - 20; y = 220
        return self.rc(f'execute in minecraft:overworld run tp {client.name} {x + .5} {y} {z + .5} 180 0')
    def pos(self, client): return read_data(self.rc('data get entity ' + client.name + ' Pos'))
    def key(self, client, key):
        client.guard(); client.game.xdo('key', key); self.action('mapped_key', player=client.name, key=key)

    def run(self):
        support.OUT = self.out
        try:
            with heavy_lock(), support.Server(self.world, 'arrival', planned=True, view_distance=3,
                                               prebuilt_resources=True, startup_timeout=1200) as self.server:
                self.done('dedicated_start')
                with Client(self, self.server, self.name, 'arrival', prebuilt_resources=True, heap='4g') as c:
                    self.rc('effect give ' + c.name + ' minecraft:resistance 99999 4 true')
                    cities = self.server.query('info')['cities']
                    current = self.server.query('at ' + str(int(self.pos(c)[0])) + ' ' + str(int(self.pos(c)[2])))['regionId']
                    city = next(row for row in cities if row['region_id'] != current)
                    # Wait for any login card, then use an independent province for this capture.
                    time.sleep(10)
                    offset = c.game.client_log.stat().st_size
                    self.teleport(c, city)
                    self.started(c, offset, 'REGION'); time.sleep(1.1); c.capture('province-A')
                    self.done('province_A')
                    self.started(c, offset, 'TOWN'); time.sleep(1.0); c.capture('town-C')
                    self.done('town_C')
                    before = self.pos(c); c.guard(); c.game.xdo('keydown', 'w'); time.sleep(.35); c.game.xdo('keyup', 'w')
                    self.action('mapped_movement', key='w', seconds=.35)
                    after = self.pos(c)
                    self.check('movement_while_banner', sum((a-b)**2 for a,b in zip(before, after)) > .01,
                               {'before': before, 'after': after}); self.done('mapped_movement')
                    time.sleep(5)
                    count = len(self.starts(c)); self.teleport(c, city); time.sleep(5)
                    self.check('same_town_no_replay', len(self.starts(c)) == count, self.starts(c)); self.done('same_town_no_replay')
                    other = next(row for row in cities if row['region_id'] not in (current, city['region_id']))
                    self.key(c, 'F1')
                    offset = c.game.client_log.stat().st_size
                    self.teleport(c, other); time.sleep(6)
                    self.check('hidden_hud_has_no_started_animation', 'ARRIVAL_UI kind=' not in c.tail(offset), c.tail(offset)[-1500:])
                    self.key(c, 'F1'); self.started(c, offset, 'REGION'); time.sleep(1); c.capture('province-A-resumed')
                    self.done('hud_hidden_waits')
                    self.rc(f'execute in minecraft:the_nether run tp {c.name} 0 180 0')
                    wait(lambda: c.tail(offset), lambda text: 'reason=world_reset' in text, 45, '跨维度清理旧报幕')
                    time.sleep(2); c.capture('dimension-cleared')
                    self.done('dimension_clear'); self.rc('save-all flush')
            self.report['status'] = 'passed'
        except BaseException as error:
            self.report['status'] = 'failed'; self.report['failed'].append(repr(error)); raise
        finally: self.save()


def main():
    p = argparse.ArgumentParser(); p.add_argument('--out', type=Path, required=True)
    p.add_argument('--previous', type=Path, required=True); a = p.parse_args()
    if not str(ROOT).startswith('/content/') or os.environ.get('MYVILLAGE_REMOTE_PROVIDER') != 'colab':
        raise RuntimeError('仅在已授权 Colab 工作区运行')
    Evidence(a.out, a.previous.resolve()).run()


if __name__ == '__main__': main()
