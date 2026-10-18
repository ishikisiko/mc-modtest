#!/usr/bin/env python3
"""Mapped town-guide, finite chest, Nether-material craft and combat checks on Modal only."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import secrets
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.npc_life_evidence import NpcLifeEvidence
from tools.alchemy_quality_evidence import SecondClient, read_data, quality_stacks
from tools.combat_capture import session as cs, procs, scene
from tools.forging_evidence import ForgingEvidence


class ExplorationEvidence(NpcLifeEvidence):
    def __init__(self, out):
        super().__init__(out)
        self.report.update(kind='vanilla_exploration_v1', selected=['town_guide', 'player_progress', 'guidebook', 'finite_chest',
            'nether_material_craft', 'mapped_combat', 'two_clients', 'restart'], completed=[], unrun=[],
            method='real mapped right-click, crafting, chest transfer, shared sword attack and chat; RCON fixtures/readback; no DevBridge',
            owner_appearance='not_verified', owner_balance='not_verified', owner_feel='not_verified',
            natural_worldgen='separate native-world evidence', cases={})

    def start(self, reuse=False):
        if ROOT != Path('/workspace/repo'):
            raise RuntimeError('Exploration game evidence requires the disposable Modal checkout')
        if not reuse:
            config = cs.SERVER_DIR/'config/myvillage-world_sim-server.toml'
            config.parent.mkdir(parents=True, exist_ok=True)
            config.write_text('[world_sim]\ntier = "large"\n')
        cs.merge_options(cs.CLIENT_DIR/'options.txt', {'key_key.use': 'key.mouse.right', 'key_key.attack': 'key.mouse.left'})
        started = time.monotonic()
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), reuse_world=reuse, language='zh_cn',
            lock=str(procs.default_lock_path(ROOT)), natural_monsters=True))
        self.s = cs.Session.attach(ui_check=True)
        self.sessions += 1
        self.report['phases'].append({'phase': 'restart_setup' if reuse else 'initial_setup',
            'wall_s': round(time.monotonic()-started, 3), 'capture_timings': state.get('timings', {})})
        self.report['source_commit'] = json.loads(Path('/workspace/request.json').read_text())['commit']
        self.report['jar_sha256'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'build/libs').glob('*.jar')}
        self.rc(f'gamemode creative {self.s.username}')

    def events(self, offset):
        path = cs.LOG_DIR/'server.log'
        text = path.read_bytes()[offset:].decode(errors='replace')
        return [dict(re.findall(r'(player|next|tipOnly)=([^\s]+)', line))
                for line in text.splitlines() if 'EXPLORATION_GUIDE ' in line]

    def gui_shot(self, game, name):
        # Game.shot deliberately refuses open screens. These cases explicitly capture the
        # chat/container/GuideME/inventory UI after verifying its mapped opening action.
        raw, info = game.stable_grab(max_wait=3, settle=.2)
        game.save_png(raw, self.out/name)
        self.report.setdefault('ui_captures', []).append({'file': name, **info})

    def mapped_guide(self, person, expected, username=None, game=None):
        username, game = username or self.s.username, game or self.s.game
        # The actual conversation carries no client-provided progress or chosen milestone.
        self.poll(lambda: self.status(person), lambda r: r['pauseTicks'] == 0, 15, 'guide resumes')
        offset = (cs.LOG_DIR/'server.log').stat().st_size
        self.talk(person, username, game)
        rows = self.poll(lambda: self.events(offset), bool, 8, 'server-built guide milestone')
        self.check('mapped_guide_'+username+'_'+expected, rows[-1].get('next') == expected, rows)
        # Re-open chat through mapped input so the actual translated conversation remains visible.
        game.xdo('key', 't'); time.sleep(.4)
        self.gui_shot(game,'guide-'+username+'-'+expected+'.png')
        game.xdo('key', 'Escape'); time.sleep(.3)
        game.require_ingame('guide chat closed')

    def inventory(self):
        return quality_stacks(self.rc(f'data get entity {self.s.username} Inventory'))

    def count_item(self, item):
        return sum(row['count'] for row in self.inventory().values() if row['id'] == item)

    def book(self):
        u,g=self.s.username,self.s.game
        self.rc(f'tp {u} .5 -60 .5 180 -60')
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:cultivation_handbook')
        g.key('1');time.sleep(.3);g.require_ingame('guidebook mapped use');g.xdo('click','3')
        self.poll(g.pointer_grabbed,lambda value:not value,10,'GuideME opens')
        self.gui_shot(g,'guidebook-index.png')
        g.xdo('key','Escape');time.sleep(.4);g.require_ingame('guidebook closed')
        self.check('mapped_guidebook_opened',True,'actual handbook use and GuideME screen; generated chapter packaged separately')
        self.report['completed'].append('guidebook')

    def chest(self):
        u, g = self.s.username, self.s.game
        x, z = self.offset
        p = f'{x} -60 {z+3}'
        self.rc(f'clear {u}')
        self.rc(f'setblock {p} minecraft:chest{{LootTable:"minecraft:chests/simple_dungeon",LootTableSeed:29383L}}')
        self.rc(f'tp {u} {x+.5} -60 {z+.5} 0 20.5')
        time.sleep(.5); g.require_ingame('finite chest'); g.xdo('click','3')
        self.poll(g.pointer_grabbed,lambda value: not value,8,'mapped chest opens')
        contents = read_data(self.rc(f'data get block {p} Items'))
        mod = [row for row in contents if row['id'].startswith('myvillage:')]
        self.check('mapped_chest_has_cultivation_reward',bool(mod),contents)
        self.check('mapped_chest_keeps_vanilla_loot',any(row['id'].startswith('minecraft:') for row in contents),contents)
        self.gui_shot(g,'exploration-chest.png')
        row = mod[0]; slot = row['Slot']; width,height = g.size
        scale=max(1,min(width//320,height//240));left=(width//scale-176)//2;top=(height//scale-168)//2
        g.xdo('mousemove',str(scale*(left+16+18*(slot%9))),str(scale*(top+26+18*(slot//9))))
        g.xdo('keydown','Shift_L');g.xdo('click','1');g.xdo('keyup','Shift_L');time.sleep(.4)
        g.xdo('key','Escape');time.sleep(.3);g.require_ingame('chest closed')
        self.check('mapped_chest_reward_taken',self.count_item(row['id'])==row['count'],self.inventory())
        after=read_data(self.rc(f'data get block {p} Items'))
        self.chest_saved={'pos':p,'items':after,'taken':row}
        self.report['completed'].append('finite_chest')

    def crafting(self):
        u,g=self.s.username,self.s.game
        # The town setup uses creative mode; the real 2x2 inventory grid and item consumption
        # must be exercised in survival, otherwise the screen and pattern cost differ.
        self.rc(f'gamemode survival {u}')
        helper=ForgingEvidence(self.out/'forging-use');helper.s=self.s
        helper.learn('qingfeng_sword')
        self.rc(f'clear {u}')
        for slot,item in enumerate(('minecraft:copper_ingot','minecraft:blaze_powder','myvillage:low_grade_spirit_stone')):
            self.rc(f'item replace entity {u} hotbar.{slot} with {item}')
        self.rc(f'tp {u} .5 -60 .5 180 -60');g.key('e')
        self.poll(g.pointer_grabbed,lambda value:not value,8,'mapped inventory crafting')
        width,height=g.size;scale=max(1,min(width//320,height//240));left=(width//scale-176)//2;top=(height//scale-166)//2
        def click(x,y,shift=False):
            g.xdo('mousemove',str(scale*(left+x)),str(scale*(top+y)))
            if shift:g.xdo('keydown','Shift_L')
            try:g.xdo('click','1')
            finally:
                if shift:g.xdo('keyup','Shift_L')
            time.sleep(.25)
        for slot,(x,y) in enumerate(((106,26),(124,26),(106,44))):
            click(16+18*slot,150);click(x,y)
        self.gui_shot(g,'nether-conversion-ready.png')
        click(154,36,True);g.xdo('key','Escape');time.sleep(.4);g.require_ingame('craft complete')
        self.check('mapped_nether_conversion_exact',self.count_item('myvillage:chitong_ingot')==1
            and all(self.count_item(item)==0 for item in ('minecraft:copper_ingot','minecraft:blaze_powder','myvillage:low_grade_spirit_stone')),self.inventory())
        # The crafted ingot is then consumed by the existing forging transaction.
        self.rc('setblock 0 -60 3 myvillage:forging_table')
        for item,count in [('minecraft:diamond',2),('minecraft:stick',1),('myvillage:low_grade_spirit_stone',2)]:
            self.rc(f'give {u} {item} {count}')
        helper.open_table();helper.click(50,116);helper.click(152,137);helper.click(284,104,shift=True);helper.close_menu()
        weapons=[(slot,row) for slot,row in self.inventory().items() if row['id']=='myvillage:qingfeng_sword']
        self.check('nether_conversion_used_for_channeling',len(weapons)==1
            and weapons[0][1].get('components',{}).get('myvillage:forge_mark',{}).get('trait')=='channeling'
            and self.count_item('myvillage:chitong_ingot')==0,weapons)
        helper.report['status']='pass';helper.save()
        self.report['completed'].append('nether_material_craft')

    def combat(self):
        u,g=self.s.username,self.s.game
        target='@e[tag=exploration_target,limit=1]'
        self.rc(f'gamemode survival {u}');self.rc(f'tp {u} .5 -60 .5 0 0')
        self.rc(f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_1')
        self.rc(f'item replace entity {u} weapon.mainhand with myvillage:qingfeng_sword')
        self.rc('time set midnight')
        # The preceding recipe case used this exact spot as a ding. Leave a genuine two-block
        # spawn space over the buried spawner instead of asking it to spawn inside that ding.
        self.rc('setblock 0 -60 3 minecraft:air')
        # A real vanilla spawner supplies the gameplay birth reason; command summons stay baseline.
        self.rc('setblock 0 -61 3 minecraft:spawner{Delay:1s,MinSpawnDelay:200s,MaxSpawnDelay:200s,SpawnCount:1s,SpawnRange:0s,RequiredPlayerRange:16s,MaxNearbyEntities:1s,SpawnData:{entity:{id:"minecraft:zombie",NoAI:1b,PersistenceRequired:1b,Tags:["exploration_target"]},custom_spawn_rules:{block_light_limit:{min_inclusive:0,max_inclusive:15},sky_light_limit:{min_inclusive:0,max_inclusive:15}}}}')
        self.report['spawner_fixture'] = self.rc('data get block 0 -61 3')
        self.poll(lambda:self.rc(f'data get entity {target} Health'),lambda reply:'data: ' in reply,30,'native spawner birth')
        birth_path='NeoForgeData."myvillage:vanilla_monster_birth"'
        before=read_data(self.rc(f'data get entity {target} {birth_path}'))
        self.rc('setblock 0 -61 3 minecraft:smooth_stone');self.rc(f'data merge entity {target} {{NoGravity:1b,Fire:0s}}')
        self.rc(f'tp {target} .5 -60 3');self.rc('time set midnight')
        health=self.number(f'data get entity {target} Health')
        scene.ensure_cultivation(self.s);g.require_ingame('shared sword hit')
        for _ in range(1):
            g.xdo('mousedown','1');time.sleep(.12);g.xdo('mouseup','1');time.sleep(.65)
        after=self.number(f'data get entity {target} Health')
        self.check('mapped_shared_sword_hurts_assigned_monster',0<after<health,{'before':health,'after':after,'birth':before})
        self.check('combat_keeps_birth',read_data(self.rc(f'data get entity {target} {birth_path}'))==before,before)
        g.shot(self.out/'assigned-monster-combat.png',max_wait=3,settle=.2)
        self.rc(f'kill {target}');self.rc(f'gamemode creative {u}');self.rc('time set day');self.center()
        self.rc(f'myvillage cultivation setrealm {u} myvillage:foundation_establishment myvillage:foundation_early')
        self.report['completed'].append('mapped_combat')

    def run(self):
        self.start();u=self.s.username
        self.setup();x,z=self.offset
        self.rc(f'myvillage life bind showcase yunshan_showcase_v2 {x} -61 {z}')
        rows=self.poll(self.residents,lambda rows:any(r['role']=='guide' for r in rows),20,'automatic guide staffing')
        pid=next(r['personId'] for r in rows if r['role']=='guide');self.ids={'guide':pid}
        self.rc('myvillage world resume')
        self.poll(lambda:self.status(pid),lambda r:r['embodied'],30,'guide real body')
        self.rc(f'deop {u}')
        self.mapped_guide(pid,'awaken');self.report['completed'].append('town_guide')
        for command in (f'myvillage cultivation awaken {u}',f'myvillage cultivation initiate {u}',
            f'myvillage cultivation setrealm {u} myvillage:foundation_establishment myvillage:foundation_early',
            f'advancement grant {u} only minecraft:story/smelt_iron',f'advancement grant {u} only minecraft:story/enter_the_nether'):
            self.rc(command)
        self.mapped_guide(pid,'fortress')
        offset=(cs.LOG_DIR/'server.log').stat().st_size
        self.s.game.type_chat('/exploration guide tips')
        rows=self.poll(lambda:self.events(offset),bool,8,'ordinary-player public tips')
        self.check('non_operator_tips_command',rows[-1].get('next')=='fortress' and rows[-1].get('tipOnly')=='true',rows)
        self.report['completed'].append('player_progress')
        self.book();self.chest();self.crafting();self.combat()
        self.center();self.rc('myvillage world resume')
        self.poll(lambda:self.status(pid),lambda r:r['embodied'],30,'guide before multiplayer')
        with SecondClient(self) as guest:
            self.rc(f'gamemode creative {guest.username}');self.center(guest.username)
            uuid=self.body(pid)['UUID']
            self.mapped_guide(pid,'awaken',guest.username,guest.game)
            self.check('two_clients_share_one_guide',self.count(pid)==1 and self.body(pid)['UUID']==uuid,self.status(pid))
            self.mapped_guide(pid,'fortress')
        self.report['completed'].append('two_clients');self.report['multiplayer']='two actual clients; separate progress, one body'
        self.rc('myvillage world pause');before=self.ledger('before-restart');self.stop()
        self.start(reuse=True);self.center();self.rc('myvillage world resume')
        self.poll(lambda:self.status(pid),lambda r:r['embodied'],35,'guide after restart')
        after=self.ledger('after-restart')
        self.check('mapped_restart_guide_identity',before['life']['guide_assignments']==after['life']['guide_assignments']
            and sum(r['role']=='guide' for r in after['life']['residents'])==1,after['life']['guide_assignments'])
        self.mapped_guide(pid,'fortress')
        saved=self.chest_saved
        self.check('mapped_restart_chest_no_refill',read_data(self.rc(f'data get block {saved["pos"]} Items'))==saved['items'],saved)
        self.report['completed'].append('restart');self.report['unrun']=[];self.report['status']='pass'


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,default=ROOT/'out/preview/exploration-v1');args=parser.parse_args()
    evidence=ExplorationEvidence(args.out)
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
