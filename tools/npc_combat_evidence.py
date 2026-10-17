#!/usr/bin/env python3
"""NPC sword/ledger slice in a disposable Modal world, using mapped attacks and real AI.

RCON prepares fixtures, reads state, and freezes labelled timing boundaries. It does not
submit NPC combat intents. Ledger NBT is read only. Owner feel/balance/FPS stay unverified.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.npc_life_evidence import NpcLifeEvidence, read_ledger
from tools.alchemy_quality_evidence import read_data, SecondClient
from tools.combat_capture import scene, session as cs
from tools.combat_capture.capture import ffmpeg_record_argv
from tools.yunshan.game_evidence import aim, teleport_command
from tools.modal_runner.remote import NPC_COMBAT_CASES, select_npc_combat_cases


class NpcCombatEvidence(NpcLifeEvidence):
    def __init__(self, out, cases=None):
        super().__init__(out)
        selected = select_npc_combat_cases(cases)
        self.report.update(kind='npc_sword_combat', selected=selected, completed=[], cases={},
            unrun=selected.copy(), owner_feel='not_verified', owner_balance='not_verified',
            method='mapped left mouse provocation; actual NPC AI and shared executor; command fixtures/readbacks; saved ledger read-only')

    def inspect(self, selector):
        return read_data(self.rc(f'data get entity {selector}'))

    def hp(self, selector=None):
        return self.number(f'data get entity {selector or self.s.username} Health')

    def equip(self):
        u = self.s.username
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:qingfeng_sword')
        self.s.game.key('1')
        scene.ensure_cultivation(self.s)

    def arena(self):
        u = self.s.username
        for command in ('gamerule doMobSpawning false', 'gamerule naturalRegeneration false',
                        'gamerule doDaylightCycle false', 'time set day', 'myvillage world pause',
                        f'tp {u} .5 -60 .5 0 0'):
            self.rc(command)
        self.poll(lambda:self.rc('execute if loaded 0 -60 2'), lambda r:'passed' in r, 30, 'arena loaded')
        self.rc('fill -20 -61 -20 20 -61 20 minecraft:stone')
        self.rc(f'gamemode survival {u}')
        self.rc(f'attribute {u} minecraft:generic.max_health base set 1024')
        self.rc(f'effect give {u} minecraft:instant_health 1 10 true')
        self.rc(f'effect give {u} minecraft:saturation 99999 0 true')
        self.equip()

    def mapped_hit(self, selector, label):
        """Try actually standable approaches; accept only a measured health loss."""
        body = self.inspect(selector)
        x, y, z = body['Pos']
        before = body['Health']
        u = self.s.username
        for dx, dz in ((0, -2), (0, 2), (-2, 0), (2, 0)):
            px, py, pz = math.floor(x+dx), math.floor(y), math.floor(z+dz)
            reply = self.rc(f'execute if block {px} {py} {pz} minecraft:air if block {px} {py+1} {pz} minecraft:air unless block {px} {py-1} {pz} minecraft:air')
            if 'passed' not in reply:
                continue
            position = [px+.5, py, pz+.5]
            yaw, pitch = aim(position, [x, y+1.15, z])
            self.rc(teleport_command(u, position, yaw, pitch))
            self.s.game.require_ingame(label)
            time.sleep(.25)
            self.s.game.xdo('click', '1')
            try:
                after = self.poll(lambda:self.hp(selector), lambda hp:hp < before, 3, label)
                self.check(label, after < before, {'before':before, 'after':after, 'position':position})
                return before, after
            except TimeoutError:
                continue
        raise AssertionError('Mapped sword did not hit '+label)

    def duel(self):
        self.arena()
        target = '@e[tag=npc_duel,limit=1]'
        self.rc('summon myvillage:cultivator .5 -60 3.0 {Tags:["npc_duel"],PersistenceRequired:1b,Health:80f,attributes:[{id:"minecraft:generic.max_health",base:80d}]}')
        self.poll(lambda:self.inspect(target), lambda b:bool(b['HandItems'][0]), 5, 'NPC equipped sword')
        before = self.hp()
        video = subprocess.Popen(ffmpeg_record_argv(self.s.state['display'], self.out/'sword-duel.mp4', 12))
        try:
            self.mapped_hit(target, 'player_mapped_sword_damages_npc')
            samples = []
            end = time.monotonic()+8
            while time.monotonic() < end:
                body = self.inspect(target)
                samples.append({'npc':body, 'player_health':self.hp()})
                time.sleep(.12)
            (self.out/'duel-samples.json').write_text(json.dumps(samples, indent=2))
            moves = {s['npc'].get('NpcCombatView', {}).get('move') for s in samples} - {None}
            self.check('npc_ai_uses_shared_basic_sword_moves', bool(moves) and all(m.startswith('myvillage:basic_sword_') for m in moves), sorted(moves))
            self.check('npc_real_attacks_damage_survival_player', self.hp() < before, {'before':before, 'after':self.hp()})
            self.s.game.shot(self.out/'sword-duel.png', max_wait=2, settle=.1)
            self.rc(f'gamemode creative {self.s.username}')
            self.poll(lambda:self.inspect(target).get('NpcCombatView',{}), lambda v:not v.get('engaged'), 10, 'NPC disengages')
            self.check('invalid_target_clears_action_and_engagement', 'move' not in self.inspect(target).get('NpcCombatView',{}), self.inspect(target))
        finally:
            video.wait(timeout=20)
            self.rc(f'kill {target}')

    def prepare_life(self):
        self.setup()
        self.bind_idempotence()
        self.demo_identity()
        self.equip()
        self.rc(f'gamemode survival {self.s.username}')
        # This labelled observer fixture prevents a high-realm resident killing the observer.
        # The unprotected duel case above establishes real NPC outgoing damage.
        self.rc(f'effect give {self.s.username} minecraft:resistance 99999 4 true')

    def life(self):
        pid = self.ids['innkeeper']
        self.poll(lambda:self.status(pid), lambda r:r['phase']=='working', 100, 'resident reaches actual workplace')
        self.mapped_hit(self.entity(pid), 'mapped_attack_interrupts_resident')
        self.poll(lambda:self.body(pid).get('NpcCombatView',{}), lambda v:v.get('engaged'), 10, 'life combat engaged')
        before = self.status(pid)
        time.sleep(4)
        after = self.status(pid)
        fields = ('progressTicks','completedActivities','coins','herbs')
        self.check('combat_suspends_work_and_rewards', {k:before[k] for k in fields} == {k:after[k] for k in fields}, {'before':before,'after':after})
        saved = self.ledger('combat-live-save')
        profile = next(p['state'] for p in saved['combat']['profiles'] if p['person']==pid)
        self.check('damage_saved_before_withdrawal', profile['health'] < profile['maximumHealth'] and profile['lastAttacker'].startswith('player:'), profile)
        self.s.game.shot(self.out/'resident-self-defence.png', max_wait=2, settle=.1)
        self.rc(f'gamemode creative {self.s.username}')
        self.poll(lambda:self.body(pid).get('NpcCombatView',{}), lambda v:not v.get('engaged'), 12, 'resident disengaged')
        returning = self.status(pid)
        self.check('life_replans_after_combat', returning['revision'] > before['revision'], returning)
        self.poll(lambda:self.status(pid), lambda r:r['phase']=='working', 100, 'resident arrives before work/rest resumes')

    def lifecycle(self):
        pid = self.ids['herbalist']
        self.rc(f'gamemode survival {self.s.username}')
        self.equip()
        self.mapped_hit(self.entity(pid), 'lifecycle_mapped_damage')
        self.rc(f'gamemode creative {self.s.username}')
        self.poll(lambda:self.body(pid).get('NpcCombatView',{}), lambda v:not v.get('engaged'), 12, 'lifecycle disengage')
        before = self.body(pid)
        self.rc('myvillage world pause')
        self.away()
        self.poll(lambda:self.count(pid), lambda count:count==0, 35, 'physical body withdrawn')
        saved = self.ledger('withdrawn')
        profile = next(p['state'] for p in saved['combat']['profiles'] if p['person']==pid)
        self.check('withdrawal_preserves_loss_and_weapon', profile['health'] < profile['maximumHealth'] and 'qingfeng_sword' in profile['weapon'], profile)
        self.center()
        self.rc('myvillage world resume')
        self.poll(lambda:self.count(pid), lambda count:count==1, 35, 'one new projection')
        after = self.body(pid)
        self.check('new_body_keeps_health_loss_and_renews_lease', after['Health'] < profile['maximumHealth']
            and after['UUID'] != before['UUID'] and after['NpcCombatView']['epoch'] > before['NpcCombatView']['epoch'],
            {'before':before,'after':after})
        self.rc('myvillage world pause')
        self.away()
        self.poll(lambda:self.count(pid), lambda count:count==0, 35, 'withdraw before restart snapshot')
        self.ledger('restart-before')
        self.stop()
        stopped = self.ledger('restart-stopped', flush=False)
        self.start(reuse=True)
        loaded = self.ledger('restart-loaded')
        def durable(state):
            return next(p['state'] for p in state['combat']['profiles'] if p['person']==pid)
        self.check('restart_keeps_combat_profile', durable(stopped)==durable(loaded), {'stopped':durable(stopped),'loaded':durable(loaded)})
        self.center(); self.rc('myvillage world resume')
        self.poll(lambda:self.count(pid), lambda count:count==1, 35, 'post-restart body')
        self.check('restart_does_not_refresh_health', self.body(pid)['Health'] < durable(loaded)['maximumHealth'], self.body(pid))

    def death(self):
        pid = self.ids['rogue']
        self.rc(f'gamemode survival {self.s.username}')
        self.rc(f'effect give {self.s.username} minecraft:resistance 99999 4 true')
        self.equip()
        # A low-health fixture lets the actual mapped sword, not a kill command, produce the death.
        self.rc(f'data merge entity {self.entity(pid)} {{Health:1f,NoAI:1b}}')
        body = self.body(pid); x,y,z = body['Pos']
        pos = [x,y,z-2]
        yaw,pitch = aim(pos,[x,y+1.15,z]); self.rc(teleport_command(self.s.username,pos,yaw,pitch))
        self.s.game.require_ingame('mapped lethal NPC sword hit'); time.sleep(.3)
        self.s.game.xdo('click','1')
        state = self.poll(lambda:self.ledger('death-poll'), lambda s:any(p['id']==pid for p in s['tombstones']), 10, 'actual death committed')
        tomb = next(p for p in state['tombstones'] if p['id']==pid)
        self.check('mapped_death_has_player_attribution', tomb['killer_actor'].startswith('player:') and tomb['killer_name']==self.s.username, tomb)
        self.check('death_removes_person_profile_and_resident', all(p['id']!=pid for p in state['persons'])
            and all(p['person']!=pid for p in state['combat']['profiles'])
            and all(p['person_id']!=pid for p in state['life']['residents']), {'person':pid})
        self.away(); time.sleep(2); self.center(); time.sleep(4)
        state = self.ledger('dead-return')
        self.check('dead_person_never_reprojects_or_duplicates_receipt', self.count(pid)==0
            and len([p for p in state['tombstones'] if p['id']==pid])==1, tomb)

    def observers(self):
        pid = self.ids['innkeeper']
        with SecondClient(self) as guest:
            self.rc(f'gamemode creative {guest.username}')
            self.away(guest.username)
            self.rc(f'gamemode survival {self.s.username}')
            self.rc(f'effect give {self.s.username} minecraft:resistance 99999 4 true')
            self.equip(); self.mapped_hit(self.entity(pid),'observer_mapped_provocation')
            self.poll(lambda:self.body(pid).get('NpcCombatView',{}), lambda v:'move' in v, 12, 'NPC active action')
            self.rc('tick freeze')
            try:
                body = self.body(pid); x,y,z = body['Pos']
                position=[x+3,y,z-3]; yaw,pitch=aim(position,[x,y+1.2,z])
                self.rc(teleport_command(guest.username,position,yaw,pitch))
                guest.game.require_ingame('late observer joins current action')
                time.sleep(2)
                guest.game.shot(self.out/'late-observer-action.png',max_wait=3,settle=.2)
                self.s.game.shot(self.out/'first-observer-action.png',max_wait=3,settle=.2)
                after=self.body(pid)
                self.check('two_real_observers_share_one_body_and_frozen_action', self.count(pid)==1
                    and body['UUID']==after['UUID'] and body['NpcCombatView']==after['NpcCombatView'], {'before':body,'after':after})
                self.report['multiplayer']='two_actual_clients; late tracking snapshots and rendered frames retained'
            finally:
                self.rc('tick unfreeze')
                self.rc(f'gamemode creative {self.s.username}')

    def run(self):
        self.start()
        if any(c!='duel' for c in self.report['selected']):
            self.prepare_life()
        for case in self.report['selected']:
            started=time.monotonic()
            if case != 'duel':
                self.center(); self.rc('myvillage world resume')
                living = self.ledger('case-'+case)['persons']
                alive = {p['id'] for p in living}
                for pid in self.ids.values():
                    if pid in alive:
                        self.poll(lambda pid=pid:self.count(pid), lambda n:n==1, 35, 'resident body for '+case)
            try:
                getattr(self,case)()
                self.report['cases'][case]={'result':'pass','wall_seconds':round(time.monotonic()-started,3)}
                self.report['completed'].append(case); self.report['unrun'].remove(case)
            except Exception as error:
                self.report['cases'][case]={'result':'fail','error':repr(error)}
                self.report['failures'].append(case+': '+repr(error)); self.save(); raise
            self.save()


def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--cases',default='all'); args=parser.parse_args()
    evidence=NpcCombatEvidence(ROOT/'out/preview/npc-combat',args.cases)
    try:
        evidence.run()
    finally:
        if evidence.s:
            try: evidence.rc('tick unfreeze'); evidence.stop()
            except Exception as error: evidence.report['failures'].append('cleanup: '+repr(error))
        evidence.save()
    return 1 if evidence.report['failures'] else 0


if __name__=='__main__':
    raise SystemExit(main())
