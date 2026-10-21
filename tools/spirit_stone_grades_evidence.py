#!/usr/bin/env python3
"""Focused real-client grade exchange. Chat is pasted and submitted through mapped client input."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.forging_evidence import ForgingEvidence
from tools.combat_capture import scene

STONES = ['myvillage:'+grade+'_grade_spirit_stone' for grade in ('low', 'medium', 'high')]
BAG = 'myvillage:spirit_stone_bag'
LEDGER = 'myvillage:spirit_stone_balance'


class GradeEvidence(ForgingEvidence):
    def __init__(self, out):
        super().__init__(out)
        self.report.update(kind='spirit_stone_grades', provider='Colab',
                           selected=['non_op_exchange', 'split', 'collection', 'failure_atomicity', 'persistence', 'visual'],
                           completed=[], mapped_input=[], method='Mapped chat/keyboard/mouse; RCON fixtures and readback only',
                           owner_appearance='not_verified', owner_feel='not_verified', owner_fps='not_verified', multiplayer='not_verified')

    def balances(self):
        result = [0, 0, 0]
        for stack in self.inventory().values():
            if stack['id'] in STONES:
                result[STONES.index(stack['id'])] += stack['count']
            if stack['id'] == BAG:
                for entry in stack.get('components', {}).get(LEDGER, []):
                    if entry['item']['id'] in STONES:
                        result[STONES.index(entry['item']['id'])] += int(entry['quantity'])
        return result

    def command(self, command):
        game = self.s.game
        game.require_ingame('spirit stone exchange')
        subprocess.run(['xclip', '-selection', 'clipboard'], input=command.encode(),
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=game.env, check=True, timeout=5)
        game.xdo('key', 't'); time.sleep(.4)
        game.xdo('key', 'ctrl+v'); time.sleep(.3)
        game.xdo('key', 'Return'); time.sleep(.8)
        game.require_ingame('submitted exchange')
        self.report['mapped_input'].append({'chat': command, 'method': 'clipboard + T/Ctrl-V/Enter', 'retried': False})
        self.save()

    def expect(self, expected, label):
        actual = self.poll(self.balances, lambda b: b == expected, 8, label)
        self.check(label, actual == expected, actual)

    def run(self):
        self.start(); user = self.s.username
        for cmd in (*scene.world_rules(), *scene.player_setup(user, 'minecraft:air'), 'myvillage world pause',
                    f'tp {user} .5 -60 .5 180 -30', f'clear {user}',
                    f'give {user} '+BAG+'['+LEDGER+'=[{item:{id:"'+STONES[0]+'",count:1},quantity:"10000"}]]',
                    f'give {user} {STONES[0]} 100', f'gamemode survival {user}', f'deop {user}'):
            self.rc(cmd)
        self.expect([10100, 0, 0], 'automatic_low_collection')
        self.poll(lambda: len(self.inventory()), lambda n: n == 1, 8, 'all low stones in bag')
        self.command('/灵石 兑换 中品 101'); self.expect([0, 101, 0], 'non_op_low_to_medium')
        self.command('/灵石 兑换 上品'); self.expect([0, 1, 1], 'non_op_medium_to_high')
        self.report['completed'].append('non_op_exchange')
        self.command('/灵石 拆分 上品'); self.expect([0, 101, 0], 'high_to_medium')
        self.command('/灵石 拆分 中品'); self.expect([100, 100, 0], 'medium_to_low')
        self.report['completed'].append('split')
        self.rc(f'give {user} {STONES[2]} 2'); self.expect([100, 100, 2], 'high_collected')
        self.poll(lambda: len(self.inventory()), lambda n: n == 1, 8, 'all grades in bag')
        self.check('all_three_grades_in_one_ledger', len(next(iter(self.inventory().values()))['components'][LEDGER]) == 3, self.inventory())
        self.report['completed'].append('collection')
        before = self.inventory()
        self.command('/灵石 兑换 中品 2'); self.check('shortage_preserves_inventory', before == self.inventory(), self.inventory())
        self.command('/灵石 兑换 中品 -1'); self.check('negative_quantity_rejected', before == self.inventory(), self.inventory())
        self.rc('save-all flush'); self.stop(); self.start(reuse=True); user = self.s.username
        self.expect([100, 100, 2], 'all_grades_persist_after_restart')
        self.report['completed'].append('persistence')
        self.rc(f'clear {user}')
        for i in range(36):
            slot = f'hotbar.{i}' if i < 9 else f'inventory.{i-9}'
            self.rc(f'item replace entity {user} {slot} with minecraft:stone 64')
        self.rc(f'item replace entity {user} hotbar.0 with {STONES[1]} 2')
        before = self.inventory(); self.command('/灵石 拆分 中品')
        self.check('full_inventory_preserves_original', before == self.inventory(), self.inventory())
        self.rc(f'clear {user}')
        self.rc(f'give {user} {STONES[1]}[minecraft:custom_name=\'{{"text":"纪念灵石"}}\'] 1')
        before = self.inventory(); self.command('/灵石 拆分 中品')
        self.check('named_stone_cannot_be_laundered', before == self.inventory(), self.inventory())
        self.rc(f'clear {user}'); self.rc(f'give {user} {STONES[1]} 1')
        self.command('/灵石 拆分 中品'); self.expect([100, 0, 0], 'loose_split_exact')
        self.check('loose_split_respects_stack_limit', sorted(s['count'] for s in self.inventory().values()) == [36, 64], self.inventory())
        self.report['completed'].append('failure_atomicity')
        self.visual()
        self.report['status'] = 'pass'; self.save()

    def visual(self):
        user = self.s.username
        self.rc(f'clear {user}')
        for i, item in enumerate(STONES):
            self.rc(f'item replace entity {user} hotbar.{i} with {item} 32')
        entries = ','.join('{item:{id:"'+item+'",count:1},quantity:"'+str(n)+'"}' for item, n in zip(STONES, [12345, 123, 12]))
        self.rc(f'item replace entity {user} hotbar.3 with '+BAG+'['+LEDGER+'=['+entries+'],minecraft:custom_data={"myvillage:spirit_bag_auto_collect":false}]')
        self.rc('gamerule sendCommandFeedback true')
        # Clear previous failure-fixture chat through the actual client debug shortcut.
        self.s.game.xdo('keydown', 'F3'); self.s.game.xdo('key', 'd'); self.s.game.xdo('keyup', 'F3'); time.sleep(.4)
        self.s.game.key('e'); time.sleep(1)
        self.capture('grades-inventory.png')
        game = self.s.game; width, height = game.size; scale = max(1, min(width//320, height//240))
        x = scale*((width//scale-176)//2+70); y = scale*((height//scale-166)//2+150)
        game.xdo('mousemove', str(x), str(y)); time.sleep(.6)
        window = game.xdo('search', '--name', 'Minecraft').split()[0]
        subprocess.run(['import', '-silent', '-window', window, str(self.out/'bag-balances.png')], env=game.env, check=True, timeout=30)
        game.xdo('key', 'Escape'); time.sleep(.4)
        offset = self.s.game.client_log.stat().st_size
        self.command('/灵石 余额')
        def feedback():
            with self.s.game.client_log.open('rb') as stream:
                stream.seek(offset); return stream.read().decode('utf-8', 'replace')
        reply = self.poll(feedback, lambda text: all(token in text for token in ('12377', '155', '44')), 8, 'three-grade balance feedback')
        self.check('balance_command_displays_all_three_totals', all(token in reply for token in ('12377', '155', '44')), reply)
        self.s.game.key('t'); time.sleep(.5)
        self.capture('exchange-chat.png'); self.s.game.xdo('key', 'Escape')
        self.report['completed'].append('visual')
        self.report['status'] = 'pass'; self.save()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT/'out/preview/spirit_stone_grades/runtime')
    parser.add_argument('--preview-only', action='store_true')
    args = parser.parse_args()
    evidence = GradeEvidence(args.out)
    try:
        if args.preview_only:
            evidence.report['selected'] = ['visual']
            evidence.start(reuse=True); evidence.visual()
        else:
            evidence.run()
    except BaseException as error:
        evidence.report['status'] = 'fail'; evidence.report['failures'].append(repr(error)); evidence.save(); raise
    finally:
        if evidence.s:
            evidence.stop()
        evidence.save()


if __name__ == '__main__':
    main()
