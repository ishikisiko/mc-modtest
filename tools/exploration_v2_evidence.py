#!/usr/bin/env python3
"""Mapped V2 capability checks, not a complete live-AI victory or owner balance verdict."""
import argparse
import json
from pathlib import Path
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.exploration_evidence import ExplorationEvidence
from tools.alchemy_quality_evidence import SecondClient, read_data
from tools.combat_capture import session as cs
from tools.exploration_v2_bosses import run_bosses
from tools.exploration_v2_dragon import run_dragon
from tools.modal_runner.remote import EXPLORATION_V2_CASES, select_exploration_v2_cases

CASES = EXPLORATION_V2_CASES


def select_cases(value=None):
    return select_exploration_v2_cases(value)


class ExplorationV2Evidence(ExplorationEvidence):
    def __init__(self, out, cases=None):
        super().__init__(out)
        self.report.update(kind='vanilla_exploration_v2', selected=select_cases(cases), completed=[], unrun=[],
            cases={}, full_ai_victory='not_verified', owner_balance='not_verified',
            method='mapped movement, combat, bow, qi, chat and chest transfer; RCON setup/readback; no DevBridge')
        self.saved_chest = None
        self.boss_result = None

    def start(self, reuse=False):
        if ROOT != Path('/workspace/repo'):
            raise RuntimeError('Mapped V2 evidence requires the disposable Modal checkout')
        if not reuse:
            # The preceding native fixture opts its separate world into hard gates. The
            # mapped world explicitly uses the shipped default advice mode instead.
            defaults=cs.SERVER_DIR/'defaultconfigs';defaults.mkdir(parents=True,exist_ok=True)
            (defaults/'myvillage-exploration-server.toml').write_text('[portal_gates]\nenabled = false\n')
        super().start(reuse)

    @staticmethod
    def attributes(entity):
        # AttributeMap iteration order is not a persistence contract across JVM boots.
        return sorted(json.dumps(row,sort_keys=True) for row in entity['attributes'])

    def travel(self, dimension, x, y, z, yaw=0, pitch=0):
        self.rc(f'execute in {dimension} run tp {self.s.username} {x} {y} {z} {yaw} {pitch}')
        self.poll(lambda:self.rc(f'execute in {dimension} if loaded {int(x)} {int(y)} {int(z)}'),
                  lambda reply:'passed' in reply,40,'fixture destination loaded')
        time.sleep(1)

    def guide(self, expected, username=None, game=None):
        username,game=username or self.s.username,game or self.s.game
        offset=(cs.LOG_DIR/'server.log').stat().st_size
        game.type_chat('/exploration guide')
        rows=self.poll(lambda:self.events(offset),bool,10,'mapped V2 guide advice')
        self.check('mapped_guide_'+username+'_'+expected,rows[-1].get('next')==expected,rows)
        game.xdo('key','t');time.sleep(.3)
        self.gui_shot(game,'guide-'+username+'-'+expected+'.png')
        game.xdo('key','Escape');time.sleep(.2);game.require_ingame('V2 guide chat closed')

    def progression(self):
        u,g=self.s.username,self.s.game
        self.rc(f'deop {u}')
        self.rc(f'myvillage cultivation awaken {u}');self.rc(f'myvillage cultivation initiate {u}')
        self.rc(f'myvillage cultivation setrealm {u} myvillage:golden_core myvillage:golden_late')
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/exploration/rules.json').read_text())
        final={'dragon','islands','end_city','elytra'}
        for flag,advancement in rules['advancements'].items():
            self.rc(f'advancement {"revoke" if flag in final else "grant"} {u} only {advancement}')
        self.guide('dragon')
        for flag,expected in [('dragon','islands'),('islands','end_city'),('end_city','elytra'),('elytra','complete')]:
            self.rc(f'advancement grant {u} only {rules["advancements"][flag]}')
            self.guide(expected)
        path=cs.CLIENT_DIR/'logs/latest.log';offset=path.stat().st_size
        g.type_chat('/exploration guide side')
        translations=json.loads((ROOT/'src/main/resources/assets/myvillage/lang/zh_cn.json').read_text())
        names=[translations['exploration.myvillage.side.'+route['id']] for route in rules['side_routes']]
        self.poll(lambda:path.read_bytes()[offset:].decode(errors='replace'),
                  lambda text:all(name in text for name in names),10,'three optional side-route messages')
        g.xdo('key','t');time.sleep(.3);self.gui_shot(g,'optional-boss-routes.png');g.xdo('key','Escape')
        self.report['cases']['progression']={'milestones':['dragon','islands','end_city','elytra','complete'],
            'progress_preparation':'administrator advancement flags, not a claim of actual boss kills or natural acquisition',
            'warden_required_for_main_route':False,'ordinary_player':True}

    def portal(self):
        u,g=self.s.username,self.s.game
        self.rc(f'gamemode creative {u}')
        self.rc(f'myvillage cultivation setrealm {u} myvillage:foundation_establishment myvillage:foundation_early')
        self.travel('minecraft:overworld',.5,-60,.5)
        self.rc('fill -3 -61 -3 3 -61 6 minecraft:smooth_stone')
        self.rc('fill -3 -60 -3 3 -57 6 minecraft:air')
        self.rc('setblock 0 -60 3 minecraft:end_portal')
        self.rc(f'item replace entity {u} hotbar.8 with minecraft:emerald 3')
        before=self.inventory()
        g.require_ingame('mapped native end portal approach');g.xdo('keydown','w')
        try:
            self.poll(lambda:self.rc(f'data get entity {u} Dimension'),
                      lambda text:'minecraft:the_end' in text,15,'mapped default-advice End entry below recommendation')
        finally:g.xdo('keyup','w')
        self.poll(g.pointer_grabbed,bool,25,'End transition returns to game');time.sleep(2)
        self.check('default_portal_advice_does_not_lock_entry',self.inventory()==before,
                   {'player_realm':'foundation_early','required_if_enabled':'golden_early','inventory_before':before,'inventory_after':self.inventory()})
        g.shot(self.out/'native-end-portal-arrival.png',max_wait=3,settle=.2)
        self.report['cases']['portal']={'input':'mapped forward into a native end_portal block',
            'default_gate_enabled':False,'inventory_preserved':True,'natural_stronghold_search':'not_verified'}

    def end_chest(self):
        u,g=self.s.username,self.s.game
        self.rc(f'gamemode creative {u}');self.travel('minecraft:the_end',420.5,100,417.5,0,20.5)
        self.rc('execute in minecraft:the_end run fill 416 99 414 424 99 424 minecraft:smooth_stone')
        self.rc('execute in minecraft:the_end run fill 419 100 419 421 102 421 minecraft:air')
        self.rc(f'clear {u}')
        pos='420 100 420'
        self.rc(f'execute in minecraft:the_end run setblock {pos} minecraft:chest{{LootTable:"minecraft:chests/end_city_treasure",LootTableSeed:29383L}}')
        time.sleep(.5);g.require_ingame('mapped finite End-city root table chest');g.xdo('click','3')
        self.poll(g.pointer_grabbed,lambda value:not value,10,'End-city root chest opens')
        items=read_data(self.rc(f'execute in minecraft:the_end run data get block {pos} Items'))
        mod=[row for row in items if row['id'].startswith('myvillage:')]
        self.check('end_city_keeps_vanilla_and_adds_cultivation',bool(mod) and any(row['id'].startswith('minecraft:') for row in items),items)
        self.gui_shot(g,'end-city-chest-before-take.png')
        row=mod[0];slot=row['Slot'];width,height=g.size
        scale=max(1,min(width//320,height//240));left=(width//scale-176)//2;top=(height//scale-168)//2
        g.xdo('mousemove',str(scale*(left+16+18*(slot%9))),str(scale*(top+26+18*(slot//9))))
        g.xdo('keydown','Shift_L');g.xdo('click','1');g.xdo('keyup','Shift_L');time.sleep(.4)
        g.xdo('key','Escape');time.sleep(.3);g.require_ingame('End chest closed')
        self.check('mapped_end_city_reward_taken',self.count_item(row['id'])==row['count'],self.inventory())
        self.saved_chest={'pos':pos,'taken':row,'items':read_data(self.rc(f'execute in minecraft:the_end run data get block {pos} Items'))}
        self.report['cases']['end_chest']={'finite_root_table':'minecraft:chests/end_city_treasure',
            'fixture':'administrator chest in real End, not natural city acquisition','taken':row}

    def two_clients(self):
        u=self.s.username
        self.rc(f'gamemode creative {u}');self.travel('minecraft:overworld',768.5,80,765.5,0,0)
        self.rc('fill 764 79 762 772 79 772 minecraft:smooth_stone')
        self.rc('summon minecraft:elder_guardian 768.5 80 768.5 {NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Tags:["v2_shared_boss"]}')
        target='@e[tag=v2_shared_boss,limit=1]'
        self.poll(lambda:self.rc(f'data get entity {target} Health'),lambda text:'data: ' in text,10,'shared boss present')
        before=read_data(self.rc(f'data get entity {target}'))
        self.rc(f'myvillage cultivation setrealm {u} myvillage:golden_core myvillage:golden_late')
        with SecondClient(self) as guest:
            self.rc(f'gamemode creative {guest.username}')
            self.rc(f'execute in minecraft:overworld run tp {guest.username} 770.5 80 765.5 20 0')
            self.rc(f'deop {guest.username}')
            self.guide('awaken',guest.username,guest.game)
            if 'progression' in self.report['completed']:self.guide('complete')
            after=read_data(self.rc(f'data get entity {target}'))
            self.check('two_real_clients_do_not_rescale_shared_boss',before['UUID']==after['UUID']
                       and before['Health']==after['Health'] and self.attributes(before)==self.attributes(after)
                       and before['NeoForgeData']==after['NeoForgeData'],{'before':before,'after':after})
        self.report['multiplayer']='two actual clients; personal guide progress and unchanged shared boss'

    def restart(self):
        if self.saved_chest is None:self.end_chest()
        boss=None
        if self.boss_result:
            row=self.boss_result['targets'][0]
            self.travel('minecraft:overworld',321.5,80,317.5)
            selector=row['selector']
            boss={'selector':selector,'before':read_data(self.rc(f'data get entity {selector}'))}
        self.travel('minecraft:the_end',420.5,100,417.5,0,20.5)
        self.stop();self.start(reuse=True)
        self.travel('minecraft:the_end',420.5,100,417.5,0,20.5)
        saved=self.saved_chest;g=self.s.game
        g.require_ingame('End chest after real server restart');g.xdo('click','3')
        self.poll(g.pointer_grabbed,lambda value:not value,10,'End chest reopens after real restart')
        after=read_data(self.rc(f'execute in minecraft:the_end run data get block {saved["pos"]} Items'))
        self.check('mapped_end_chest_restart_no_refill',after==saved['items'],{'before':saved['items'],'after':after})
        self.check('mapped_end_reward_restart_preserved',self.count_item(saved['taken']['id'])==saved['taken']['count'],self.inventory())
        self.gui_shot(g,'end-city-chest-after-restart.png');g.xdo('key','Escape');time.sleep(.3)
        if boss:
            self.travel('minecraft:overworld',321.5,80,317.5)
            after=read_data(self.rc(f'data get entity {boss["selector"]}'))
            before=boss['before']
            self.check('mapped_damaged_boss_restart_no_heal',before['UUID']==after['UUID'] and abs(before['Health']-after['Health'])<.001
                       and self.attributes(before)==self.attributes(after) and before['NeoForgeData']==after['NeoForgeData'],{'before':before,'after':after})

    def run(self):
        self.start();self.rc('gamerule doMobSpawning false');self.rc('gamerule doFireTick false')
        for case in self.report['selected']:
            if case=='bosses':
                self.boss_result=run_bosses(self);self.report['cases']['bosses']=self.boss_result
            elif case=='dragon':
                if 'portal' not in self.report['completed']:self.travel('minecraft:the_end',100.5,50,.5)
                self.report['cases']['dragon']=run_dragon(self)
            else:getattr(self,case)()
            self.report['completed'].append(case);self.save()
        self.report['unrun']=[];self.report['status']='pass'


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,default=ROOT/'out/preview/exploration-v2')
    parser.add_argument('--cases');args=parser.parse_args()
    evidence=ExplorationV2Evidence(args.out,args.cases)
    try:evidence.run()
    except Exception as error:
        evidence.report['failures'].append(f'{type(error).__name__}: {error}')
        evidence.report['unrun']=[case for case in evidence.report['selected'] if case not in evidence.report['completed']]
        raise
    finally:
        if evidence.s:evidence.stop()
        evidence.save()
    return 1 if evidence.report['failures'] else 0


if __name__=='__main__':raise SystemExit(main())
