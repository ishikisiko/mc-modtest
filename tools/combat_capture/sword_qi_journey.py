"""Eligible prospect -> real steward join -> real shelf borrow -> study -> cast.
Only the initial location/root/realm fixture uses administrator preparation.
No admin join, learn or manual grants occur during this journey.
"""
from __future__ import annotations

import argparse
import datetime
import json
import time
from pathlib import Path

from tools import world_sim_entry_evidence as entry
from tools import world_sim_scripture_evidence as scripture
from tools.world_sim_avatar_evidence import Transcript
from . import scene
from .session import LOG_DIR
from .sword_qi import SwordQiCapture, TECHNIQUE, WEAPON, DEFAULT_OUT, parse_power
from .xgame import LogTail


class Journey(scripture.Run):
    def __init__(self, session, out, log=print):
        args=argparse.Namespace(sect=None, distance=140, realize_timeout=600, shelf_distance=3.0,
                                ui_scale=1.0, dialogue_scale=1.0, lock_wait=0, skip_promotion=True)
        super().__init__(args, [])
        self.out=Path(out); self.out.mkdir(parents=True,exist_ok=True)
        self.s=session; self.st=session.state; self.log=log
        if session.username != entry.PLAYER:
            raise ValueError(f'journey reuses entry tooling and requires username {entry.PLAYER}')
        self.t=Transcript(self.out/'commands.txt','Sword qi journey; eligible-prospect fixture then mapped interactions')
        self.slog=LogTail(LOG_DIR/'server.log'); self.clog=LogTail(session.game.client_log)
        self.stage_times=[]

    def shot(self, name, title, screen_open=False):
        time.sleep(.5)
        raw, info=self.s.game.stable_grab(max_wait=4)
        self.s.game.save_png(raw,self.out/f'{name}.png')
        self.facts['shots'].append({'file':f'{name}.png','title':title,'stable':info.get('stable')})

    def measured(self, name, action):
        start=datetime.datetime.now(datetime.timezone.utc).isoformat(); t0=time.monotonic()
        try: return action()
        finally:
            self.stage_times.append({'stage':name,'start_utc':start,
                'end_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'wall_s':round(time.monotonic()-t0,3)})
            self.save()

    def save(self):
        self.t.flush()
        report={'fixture':'Administrator teleport, root and qi_refining_1 qualification; ledger paused for stable NPC identities. No admin join, learn or manual grant.',
                'owner_verdict':'not_verified','stages':self.stage_times,'checks':self.results,'facts':self.facts}
        (self.out/'journey.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')

    def setup_eligible(self):
        self.setup() # existing entry fixture, no join/learn/manual grants
        u=self.s.username
        self.rc(f'clear {u}')
        self.rc(f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_1')
        # Reject previously learned state rather than silently using a cheat to repair it.
        learned=self.learned()
        if TECHNIQUE in learned:
            raise RuntimeError('journey needs an unlearned player; start a fresh capture session or run journey before boundary fixtures')
        self.rc(f'gamemode survival {u}')
        self.rc(f'effect give {u} minecraft:saturation infinite 255 true')
        self.rc(f'effect give {u} minecraft:instant_health 1 10 true')

    def inherit_basic(self):
        if 'myvillage:basic_breathing' in self.learned(): return
        u=self.s.username
        self.s.game.key('9') # empty slot in the isolated eligible-prospect fixture
        self.tp(.5,-60,.5,0,20.5,settle=1)
        reply=self.rc('setblock 0 -60 3 myvillage:technique_inheritance_stele')
        if 'not loaded' in reply:raise RuntimeError('stele fixture chunk not loaded')
        time.sleep(.5)
        self.right_click()
        time.sleep(1)
        inherited='myvillage:basic_breathing' in self.learned()
        self.check('basic_breathing_via_stele',inherited,'mapped right click on inheritance stele; no learn command')
        self.shot('basic_breathing_inherited','Basic Breathing inherited by using the stele')
        if not inherited:raise RuntimeError('inheritance stele did not teach Basic Breathing')

    def learned(self):
        return self.rc(f'data get entity {self.s.username} "neoforge:attachments"."myvillage:cultivation_profile".learned_techniques')

    def join_naturally(self):
        self.meet_steward()
        self.join() # entry.Run.join: mapped steward right-click and JOIN button
        if not any(r.get('check')=='join_ok' and r.get('pass') for r in self.results):
            raise RuntimeError('mapped steward join did not succeed')

    def borrow_manual(self):
        self.shelves_step()
        opened, buttons=self.open_hall('sword_qi')
        if not opened: raise RuntimeError('scripture hall did not open')
        button=next((b for b in buttons if b['technique']=='jianqi_jue' and not b.get('borrowed')),None)
        self.shot('sword_qi_shelf','Public Sword Qi manual in the scripture hall',screen_open=True)
        if button is None: raise RuntimeError(f'no unborrowed Sword Qi entry: {buttons}')
        result=self.borrow(button)
        self.check('sword_qi_borrowed',bool(result and result.get('result')=='ok' and
                                         scripture.tech_path(result.get('technique',''))=='jianqi_jue'),str(result))
        self.close_screen()
        inv=self.inventory(); self.facts['inventory_after_borrow']=inv
        if TECHNIQUE not in inv: raise RuntimeError('borrow did not yield the Sword Qi manual')

    def study(self):
        self.close_screen()
        inv=self.inventory()
        entries=scripture.split_items(inv)
        import re
        item=next((e for e in entries if TECHNIQUE in e and 'manual_active_1' in e),None)
        if item is None:
            # Item grade names are registry-owned; component identity is the authority.
            item=next((e for e in entries if TECHNIQUE in e and 'myvillage:manual_' in e),None)
        match=re.search(r'Slot:\s*(\d+)b',item or '')
        if not match or not 0 <= int(match.group(1)) <= 8: raise RuntimeError('borrowed manual is not in a selectable hotbar slot')
        self.s.game.key(str(int(match.group(1))+1))
        # Look away from shelf before item use, so the same click cannot reopen it.
        gx,gz=self.facts['gate']
        self.tp(gx+.5,-60,gz-140+.5,0,0,settle=1)
        before=self.learned(); self.facts['learned_before_study']=before
        tail=LogTail(self.s.game.client_log)
        self.right_click()
        time.sleep(1)
        tail.poll()
        refused=next((line for line in tail.lines if 'Cannot start studying:' in line or '无法开始研读：' in line),None)
        if refused:raise RuntimeError('study start refused: '+refused)
        self.shot('study_started','Studying the borrowed Sword Qi manual')
        self.rc('tick rate 80')
        t0=time.monotonic(); history=[]
        try:
            deadline=time.monotonic()+180
            while time.monotonic()<deadline:
                learned=self.learned()
                history.append({'elapsed_s':round(time.monotonic()-t0,2),'learned':TECHNIQUE in learned,
                                'item':self.rc(f'data get entity {self.s.username} SelectedItem')})
                if TECHNIQUE in learned:break
                time.sleep(1)
            else:raise RuntimeError('study did not learn Sword Qi within bounded 180 s at tick rate 80')
        finally:
            self.rc('tick rate 20'); self.facts['study_samples']=history
        self.check('learned_through_study',TECHNIQUE not in before and TECHNIQUE in learned,
                   'actual item use and server study completion; tick rate 80 acceleration, no comprehension or learn command')
        self.shot('study_complete','Sword Qi learned by studying the borrowed manual')

    def cast(self):
        # Weapon/target placement is a combat fixture; skill, power and manual remain earned state.
        u=self.s.username
        self.rc(f'tp {u} .5 -60 .5 0 0')
        self.rc(f'item replace entity {u} hotbar.1 with {WEAPON}')
        self.s.game.key('2')
        scene.ensure_cultivation(self.s)
        cap=SwordQiCapture(self.s,self.out/'cast',log=self.log)
        cap.manifest['preparation']='weapon/target fixture; skill earned by mapped shelf borrow and manual study in parent journey'
        cap.manifest['natural_acquisition']='observed_in_parent_journey'
        # Preserve learned state and power: prepare only target and physical scene.
        def prepare(_):
            cap.run('tick rate 10','kill @e[tag=sword_qi_target]', 'fill -3 -60 2 3 -56 4 minecraft:air',
                    'summon minecraft:iron_golem .5 -60 6.5 {NoAI:1b,PersistenceRequired:1b,Tags:["sword_qi_target"]}')
        cap.prepare=prepare
        cap.one({'id':'earned_skill_cast','expect':'hit'})
        self.facts['cast']=cap.manifest
        self.check('earned_skill_cast',cap.manifest['passed'],'mapped right mouse, no learn/fill command after study')
        if not cap.manifest['passed']:raise RuntimeError('earned skill cast failed')

    def recover(self):
        # Ordinary server-time recovery, with no fill or state-writing command.
        samples=[]; t0=time.monotonic(); before=parse_power(self.rc(f'myvillage cultivation qi {self.s.username}'))
        deadline=time.monotonic()+20
        recovered=False
        while time.monotonic()<deadline:
            snapshot=self.rc(f'myvillage cultivation qi {self.s.username}')
            value=parse_power(snapshot)
            samples.append({'wall_s':round(time.monotonic()-t0,3),'snapshot':snapshot})
            if value is not None and before is not None and value>before:
                recovered=True; break
            time.sleep(.5)
        self.facts['passive_recovery']=samples
        self.check('ordinary_recovery_observed',recovered,'live qi increased after earned cast, no fill command')
        if not recovered:raise RuntimeError('ordinary qi recovery not observed within 20 seconds')
        self.shot('recovery','Qi recovering after the earned skill cast')

    def execute(self):
        try:
            for name,action in [('eligible_fixture',self.setup_eligible),('mapped_basic_inheritance',self.inherit_basic),('choose_sect',self.pick_sect),
                                ('gate_fixture',self.realize),('mapped_join',self.join_naturally),
                                ('mapped_borrow',self.borrow_manual),('mapped_study',self.study),('mapped_cast',self.cast),('ordinary_recovery',self.recover)]:
                self.measured(name,action)
        finally:
            self.poll_logs(); self.save()
            (self.out/'server_log.txt').write_text('\n'.join(self.server_lines)+'\n')
            (self.out/'client_log.txt').write_text('\n'.join(self.client_lines)+'\n')
            try:self.rc('tick rate 20')
            except Exception:pass
        return self.facts


def run_journey(session,out=DEFAULT_OUT/'journey',log=print):
    return Journey(session,out,log).execute()
