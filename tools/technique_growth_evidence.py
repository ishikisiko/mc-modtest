#!/usr/bin/env python3
"""Colab-only growth sample: real H clicks, manual use and sword input; RCON is fixture/readback.

Mastery boundary fixtures shorten the run. Qingmu/Jianqi books start unread; the
Yimu book starts near completion to isolate prerequisite and inheritance handling.
This is scoped runtime evidence, not natural acquisition or owner acceptance.
"""
from pathlib import Path
import argparse
import json
import os
import secrets
import sys
import time
import traceback

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.locked_sword_evidence import LockedEvidence
from tools.combat_capture import session as cs, scene, procs
from tools.ink_hub_evidence import bounds, tile_center
from tools.alchemy_quality_evidence import read_data

CASES = ('practice', 'insight', 'related_learning', 'lineage', 'sword', 'save_restart')
BASIC, QING, YI, SWORD = 'basic_breathing', 'qingmu_changchun_gong', 'yimu_shenghua_jue', 'jianqi_jue'


class GrowthEvidence(LockedEvidence):
    def __init__(self, out):
        super().__init__(out, CASES)
        self.report.update(kind='technique_growth_sample', runtime_retained=False, method=__doc__,
                           fixture='new flat world; explicit mastery boundaries; real player actions use mapped input',
                           not_verified=['natural_material_collection', 'manual_crafting_input', 'full_length_yimu_reading',
                                         'owner_appearance', 'owner_feel', 'owner_balance', 'fps', 'multiplayer'])

    def profile(self):
        return read_data(self.rc(f'data get entity {self.s.username} "neoforge:attachments"."myvillage:cultivation_profile"'))

    def progress(self, ident):
        value = self.profile()['learned_techniques'].get('myvillage:' + ident)
        if value is None:
            return None
        return {'mastery_points': value['mastery_points'], 'insight': value.get('insight', ''),
                'insight_spent': value.get('insight_spent', 0), 'growth_revision': value.get('growth_revision', 0)}

    def inventory(self):
        return read_data(self.rc(f'data get entity {self.s.username} Inventory'))

    def stones(self):
        return sum(row['count'] for row in self.inventory() if row.get('id') == 'myvillage:low_grade_spirit_stone')

    def start(self, reuse=False):
        if os.environ.get('MYVILLAGE_CLOUD_PROVIDER') != 'Colab' or not str(ROOT).startswith('/content/'):
            raise RuntimeError('Growth runtime must execute in Colab')
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), language='zh_cn',
                                         prebuilt_resources=True, reuse_world=reuse, view_distance=3,
                                         lock=str(procs.default_lock_path(ROOT)), world_seed=20261026))
        if state.get('phase') != 'ready':
            raise RuntimeError('No ready native session')
        self.s = cs.Session.attach(ui_check=True)
        self.sessions += 1
        self.report['phases'].append({'phase': 'restart' if reuse else 'start', 'timings': state.get('timings', {})})
        self.position(self.s.game)
        self.opened = False
        if reuse:
            return
        u = self.s.username
        for command in (*scene.world_rules(), *scene.player_setup(u, 'minecraft:air'), 'myvillage world pause',
                        'gamerule naturalRegeneration false', 'forceload add -32 -32 32 48'):
            self.rc(command)
        self.poll(lambda: self.rc('execute if loaded 0 -61 0 run time query gametime'), lambda r: 'The time is' in r, label='fixture chunks')
        self.rc('fill -16 -61 -16 16 -61 40 minecraft:stone')
        self.configure(u)
        self.rc(f'myvillage cultivation setroot {u} 4000 6000 0 0 0')
        self.rc(f'clear {u}')
        self.rc(f'give {u} myvillage:low_grade_spirit_stone 64')
        for ident in (QING, YI, SWORD):
            self.rc(f'myvillage cultivation forget {u} myvillage:{ident}')
        self.rc(f'tp {u} .5 -60 .5 0 -10')
        time.sleep(.5)

    def close_h(self):
        if self.opened:
            self.key('Escape')
            self.opened = False
            time.sleep(.25)

    def open_growth(self, ident):
        self.close_h()
        learned = self.profile()['learned_techniques']
        categories = ['core', 'active', 'movement', 'body']
        ordered = sorted([key.split(':')[1] for key in learned if key.split(':')[1] in self.defs],
                         key=lambda key: (categories.index(self.defs[key]['category']), -self.defs[key]['grade'],
                                          self.lang[self.defs[key]['translation_key']], key))
        self.key('h')
        self.opened = True
        time.sleep(.5)
        b = bounds(self.s.game.size)
        self.click(b['left'] + 26, b['top'] + 36 + 2 * 21 + 9, '打开功法页')
        self.click(*tile_center(b, ordered.index(ident)), '选择 ' + ident)
        dock = b['y'] + b['height'] - 58
        self.click(b['x'] + 43, dock + 43, '修习与参悟')
        return b

    def growth_action(self, index, label):
        b = bounds(self.s.game.size)
        cell = (b['width'] - 8) // 3
        dock = b['y'] + b['height'] - 58
        self.click(b['x'] + index * (cell + 4) + cell // 2, dock + 43, label)

    def done(self, case):
        self.report['completed'].append(case)
        self.report['unrun'].remove(case)
        self.save()

    def practice_case(self):
        u = self.s.username
        self.rc(f'myvillage cultivation setmastery {u} myvillage:{BASIC} 40')
        self.open_growth(BASIC)
        self.capture('01_growth_entry')
        before = self.stones()
        self.growth_action(0, '开始修习，随后中断')
        time.sleep(2)
        self.growth_action(0, '收功，中断未完成批次')
        self.check('interrupted_batch_no_cost_or_gain', self.progress(BASIC)['mastery_points'] == 40 and self.stones() == before,
                   {'progress': self.progress(BASIC), 'stones_before': before, 'stones_after': self.stones()})
        self.growth_action(0, '完整修习一个批次')
        self.poll(lambda: self.progress(BASIC), lambda p: p['mastery_points'] == 50, timeout=25, label='real practice batch')
        self.growth_action(0, '收功保留已完成进度')
        self.check('one_batch_one_payment', self.stones() == before - 1, self.inventory())
        self.capture('02_small_success')
        self.done('practice')

    def insight_case(self):
        before = self.stones()
        self.growth_action(1, '参悟调息')
        self.poll(lambda: self.progress(BASIC), lambda p: p['insight'] == 'flow', label='first insight saved')
        self.check('first_insight_spends_personal_points', self.progress(BASIC)['insight_spent'] == 2 and self.stones() == before,
                   self.progress(BASIC))
        self.growth_action(2, '改选守息')
        self.poll(lambda: self.progress(BASIC), lambda p: p['insight'] == 'steady', label='respec saved')
        self.check('respec_one_direction_one_payment', self.stones() == before - 4 and self.progress(BASIC)['insight_spent'] == 2,
                   {'progress': self.progress(BASIC), 'stones': self.stones()})
        self.capture('03_exclusive_insight')
        self.close_h()
        self.rc(f'myvillage cultivation setmastery {self.s.username} myvillage:{BASIC} 170')
        self.open_growth(BASIC)
        before = self.stones()
        self.growth_action(0, '完成最后一个修习批次')
        self.poll(lambda: self.progress(BASIC), lambda p: p['mastery_points'] == 180, timeout=25, label='finite cap')
        time.sleep(11)
        self.check('finite_cap_stops_future_charges', self.stones() == before - 1 and self.progress(BASIC)['mastery_points'] == 180,
                   {'progress': self.progress(BASIC), 'stones': self.stones()})
        self.capture('04_finite_cap')
        self.close_h()
        self.done('insight')

    def book(self, ident, item, points=0):
        self.close_h()
        self.key('1')
        components = f'myvillage:technique="myvillage:{ident}"'
        if points:
            components += f',myvillage:comprehension={points}'
        reply = self.rc(f'item replace entity {self.s.username} hotbar.0 with myvillage:{item}[{components}]')
        self.check('book_fixture_' + ident, 'Replaced a slot' in reply, reply)
        time.sleep(.3)
        self.s.game.xdo('click', '3')
        self.report['inputs'].append({'method': 'mapped right mouse', 'label': '研读 ' + ident, 'initial_comprehension': points})
        self.save()

    def learning_case(self):
        self.book(QING, 'manual_core_huang')
        self.poll(lambda: self.progress(QING), lambda p: p is not None, timeout=30, label='unread Qingmu book learned')
        self.check('manual_consumed_after_learning', not any(row.get('Slot') == 0 and row.get('id') == 'myvillage:manual_core_huang' for row in self.inventory()), self.inventory())
        self.rc(f'myvillage cultivation setmastery {self.s.username} myvillage:{QING} 100')
        self.open_growth(QING)
        self.growth_action(1, '触类旁通后的首次参悟')
        self.poll(lambda: self.progress(QING), lambda p: p['insight'] == 'flow', label='related insight')
        self.check('old_foundation_reduces_first_insight_cost', self.progress(QING)['insight_spent'] == 1, self.progress(QING))
        self.capture('05_related_learning')
        self.close_h()
        self.done('related_learning')

    def lineage_case(self):
        self.book(YI, 'manual_core_xuan', points=11990)
        time.sleep(3)
        self.check('unmastered_predecessor_refuses_learning', self.progress(YI) is None and any(row.get('Slot') == 0 for row in self.inventory()), self.inventory())
        self.rc(f'myvillage cultivation setmastery {self.s.username} myvillage:{QING} 360')
        self.rc(f'myvillage cultivation setstability {self.s.username} 100')
        self.s.game.xdo('click', '3')
        self.report['inputs'].append({'method': 'mapped right mouse', 'label': '前篇圆满后继续研读乙木'})
        self.poll(lambda: self.progress(YI), lambda p: p is not None, timeout=15, label='next chapter learned')
        self.check('chapter_inherits_without_erasing_predecessor', self.progress(YI)['mastery_points'] == 60
                   and self.progress(YI)['insight'] == 'flow' and self.progress(QING)['mastery_points'] == 360,
                   {'previous': self.progress(QING), 'next': self.progress(YI)})
        self.open_growth(YI)
        self.capture('06_next_chapter_inheritance')
        self.close_h()
        self.done('lineage')

    def sword_case(self):
        self.book(SWORD, 'manual_active_huang')
        self.poll(lambda: self.progress(SWORD), lambda p: p is not None, timeout=30, label='unread sword book learned')
        self.rc(f'myvillage cultivation setmastery {self.s.username} myvillage:{SWORD} 80')
        self.open_growth(SWORD)
        self.growth_action(2, '剑气参悟敛气')
        self.poll(lambda: self.progress(SWORD), lambda p: p['insight'] == 'reserve', label='sword insight')
        self.capture('07_sword_growth')
        self.close_h()
        self.rc(f'item replace entity {self.s.username} hotbar.0 with myvillage:qingfeng_sword')
        scene.ensure_cultivation(self.s)
        self.rc(f'tp {self.s.username} .5 -60 .5 0 0')
        self.rc(f'myvillage cultivation qi fill {self.s.username}')
        time.sleep(.5)
        self.s.game.recover_grab()
        def cast(label):
            mark = self.offset()
            self.s.game.xdo('mousedown', '3'); time.sleep(.12); self.s.game.xdo('mouseup', '3')
            self.report['inputs'].append({'method': 'mapped right mouse hold/release', 'label': label, 'seconds': .12})
            self.poll(lambda: self.log(mark), lambda text: 'result=released skill=myvillage:jianqi_jue' in text, label='real sword release')
            time.sleep(3)
            self.report.setdefault('cast_logs', []).append(self.log(mark))
            return self.log(mark)
        cast('空放剑气')
        self.check('empty_cast_no_mastery', self.progress(SWORD)['mastery_points'] == 80, self.progress(SWORD))
        self.rc('summon minecraft:husk .5 -60 8 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Health:1000f,Tags:["growth_target"],attributes:[{id:"minecraft:generic.max_health",base:1000d},{id:"minecraft:generic.knockback_resistance",base:1d}]}')
        self.rc(f'myvillage cultivation qi fill {self.s.username}')
        log = cast('有效命中增长修习')
        self.check('effective_cast_earns_once', self.progress(SWORD)['mastery_points'] == 82 and 'result=hit revision=' in log,
                   {'progress': self.progress(SWORD), 'log': log})
        self.capture('08_sword_hit')
        self.rc('kill @e[tag=growth_target]')
        self.done('sword')

    def restart_case(self):
        before = {ident: self.progress(ident) for ident in (BASIC, QING, YI, SWORD)}
        self.rc('save-all flush')
        self.report['before_restart'] = before
        self.stop()
        self.start(reuse=True)
        after = {ident: self.progress(ident) for ident in (BASIC, QING, YI, SWORD)}
        self.check('all_growth_accounts_survive_real_restart', after == before, {'before': before, 'after': after})
        self.open_growth(YI)
        self.capture('09_restored_growth')
        self.close_h()
        self.done('save_restart')

    def run(self):
        try:
            self.start()
            self.practice_case(); self.insight_case(); self.learning_case(); self.lineage_case(); self.sword_case(); self.restart_case()
        except Exception:
            self.report['failures'].append(traceback.format_exc())
            if self.s:
                try:
                    self.capture('failure')
                except Exception:
                    pass
            self.save()
            raise
        finally:
            if self.s:
                self.stop()
            self.save()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    GrowthEvidence(args.out).run()
