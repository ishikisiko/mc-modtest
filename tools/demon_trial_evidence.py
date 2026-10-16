#!/usr/bin/env python3
"""Bounded portable-trial evidence: mapped use/attacks/looting, rejection and two saved lifecycles."""
import argparse
import json
import secrets
import sys
import time
from pathlib import Path
from types import SimpleNamespace
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.combat_ruins_evidence import BattleEvidence, RULES
from tools.alchemy_quality_evidence import read_data, SecondClient
from tools.combat_capture import scene, session as cs, procs
RULES['demon_trial']=json.loads((ROOT/'src/main/resources/data/myvillage/ruins/demon_trial.json').read_text())

class DemonTrialEvidence(BattleEvidence):
    def __init__(self,out):
        super().__init__(out)
        self.report.update(kind='demon_trial',natural_terrain=False,selected=['mapped_summon','rejections','shared_authority','mapped_three_waves','partial_restart','mapped_rewards','empty_cleanup','repeat_new_pearl','looted_restart'],completed=[])
    def start(self,reuse=False):
        t=time.monotonic()
        st=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),reuse_world=reuse,natural_terrain=False,natural_monsters=True,language='zh_cn',lock=str(procs.default_lock_path(ROOT))))
        self.s=cs.Session.attach(ui_check=True);self.sessions+=1
        self.report['phases'].append({'phase':'restart' if reuse else 'initial_setup','wall_s':round(time.monotonic()-t,3),'capture_timings':st.get('timings',{})});self.save()
    def count(self,item,user=None):
        inv=read_data(self.rc(f'data get entity {user or self.s.username} Inventory'))
        return sum(i.get('count',0) for i in inv if i.get('id')==item)
    def core_absent(self,site):return 'passed' in self.rc(f'execute if block {self.coords(site["core"])} minecraft:air')
    def aim_ground(self,x=0,game=None,user=None):
        self.look((x+.5,101,-2.5),(x+.5,100.99,.5),game=game,user=user)
    def trigger(self,site,wave):self.approach_cache(site)
    def run(self):
        self.start();user=self.s.username;pearl='myvillage:demon_lure_pearl'
        for c in scene.world_rules():self.rc(c)
        for c in ['difficulty normal','gamerule doMobSpawning false','forceload add -48 -48 48 48',
                  'fill -40 100 -40 40 100 40 minecraft:stone','fill -16 101 -16 16 110 16 minecraft:air',f'op {user}',f'clear {user}',f'item replace entity {user} hotbar.0 with {pearl} 4']:
            self.rc(c)
        site={'kind':'demon_trial','core':(0,101,0),'turn':0};self.sites={'trial':site}
        self.aim_ground();self.rc('setblock 5 101 5 minecraft:stone');self.click()
        self.must('blocked_floor_preserves_pearl_and_world',self.count(pearl)==4 and self.core_absent(site),'blocked clearance')
        self.rc('setblock 5 101 5 minecraft:air');self.rc('difficulty peaceful');self.click()
        self.must('peaceful_preserves_pearl',self.count(pearl)==4 and self.core_absent(site),'peaceful')
        self.rc('difficulty normal');self.click();self.poll('mapped summon commits',lambda:not self.core_absent(site))
        self.must('one_pearl_one_active_trial',self.count(pearl)==3 and self.state(site)=='ACTIVE',self.data(site))
        self.aim_ground(2);self.click()
        self.must('nearby_retry_does_not_consume',self.count(pearl)==3 and 'passed' in self.rc('execute if block 2 101 0 minecraft:air'),self.data(site))
        self.approach_cache(site);self.click();self.must('active_core_does_not_open',self.s.game.pointer_grabbed() and not self.data(site).get('Items'),self.data(site))
        self.s.game.shot(self.out/'summoned-core.png',max_wait=3)
        self.report['completed']+=['mapped_summon','rejections'];self.save()
        self.setup_player();ids=self.wait_guards(site,0)
        adapter=SimpleNamespace(out=self.out,s=self.s,report=self.report,rc=self.rc,check=self.check,poll=lambda fn,predicate,timeout,name:self.poll(name,lambda:predicate(fn()),timeout))
        with SecondClient(adapter) as guest:
            self.rc(f'op {guest.username}');self.rc(f'clear {guest.username}')
            self.rc(f'item replace entity {guest.username} hotbar.0 with {pearl} 1')
            self.rc(f'effect give {guest.username} minecraft:resistance infinite 4 true')
            self.rc(f'tp {user} 4.5 101 -4.5')
            self.aim_ground(2,guest.game,guest.username);self.click(game=guest.game)
            self.must('second_player_cannot_overlap_or_spend',self.count(pearl,guest.username)==1 and self.guard_ids(site)==ids,self.data(site))
            self.approach_cache(site,guest.game,guest.username);self.click(game=guest.game)
            self.must('second_player_cannot_unlock_or_reset',guest.game.pointer_grabbed() and self.guard_ids(site)==ids,self.data(site))
        self.report['completed'].append('shared_authority')
        self.attack(ids[0]);self.poll('death receipt before save',lambda:any(g.get('Dead') for g in self.data(site)['Guards']))
        self.rc(f'damage {ids[1]} 1 minecraft:generic');health=read_data(self.rc(f'data get entity {ids[1]} Health'));before=self.data(site)
        self.rc('save-all flush');self.stop();self.start(reuse=True);self.setup_player();self.poll('core loaded',lambda:self.loaded(site))
        after=self.data(site)
        self.must('summoned_trial_progress_saved',after['Guards']==before['Guards'] and after['Wave']==before['Wave'],after)
        self.must('summoned_guard_health_saved',read_data(self.rc(f'data get entity {ids[1]} Health'))==health,health)
        self.report['completed'].append('partial_restart')
        self.attack(ids[1]);self.poll('first wave complete',lambda:self.data(site)['Wave']==1)
        self.wave(site,1);self.wave(site,2);self.poll('rewards',lambda:self.state(site)=='REWARDED')
        rewards=RULES['demon_trial']['rewards'];items=self.data(site)['Items']
        self.must('exact_once_rewards',sorted((i['id'],i['count']) for i in items)==sorted((i['id'],i['count']) for i in rewards),items)
        self.report['completed'].append('mapped_three_waves');self.approach_cache(site);self.click()
        self.poll('reward menu',lambda:not self.s.game.pointer_grabbed(),8)
        self.s.game.save_png(self.s.game.grab_raw(),self.out/'rewards.png')
        self.s.game.xdo('keydown','Shift_L')
        try:
            for i in range(len(rewards)):
                self.s.game.xdo('mousemove',str(336+36*i),'156');self.s.game.xdo('click','1');time.sleep(.25)
        finally:self.s.game.xdo('keyup','Shift_L')
        if not self.s.game.pointer_grabbed():self.s.game.xdo('key','Escape')
        self.s.game.require_ingame('reward menu closed')
        for r in rewards:self.must('mapped_pickup_'+r['id'],self.count(r['id'])==r['count'],self.count(r['id']))
        self.poll('empty core removed',lambda:self.core_absent(site));self.report['completed']+=['mapped_rewards','empty_cleanup']
        # A new item must work at the same place after cleanup; this is a new saved encounter.
        self.rc(f'item replace entity {user} hotbar.0 with {pearl} 1');self.aim_ground();self.click()
        self.poll('second trial',lambda:not self.core_absent(site))
        self.must('fresh_trial_requires_new_pearl',self.count(pearl)==0 and self.state(site)=='ACTIVE' and self.data(site)['Wave']==0,self.data(site))
        self.report['completed'].append('repeat_new_pearl');self.rc('save-all flush');self.stop();self.start(reuse=True)
        self.poll('second trial loaded',lambda:self.loaded(site))
        self.must('second_trial_not_previous_rewards',self.state(site)=='ACTIVE' and not self.data(site).get('Items'),self.data(site))
        for r in rewards:self.must('looted_reward_saved_'+r['id'],self.count(r['id'])==r['count'],self.count(r['id']))
        self.report['completed'].append('looted_restart');self.save()

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--out',type=Path,default=ROOT/'out/preview/demon_trial_runtime');args=ap.parse_args();e=DemonTrialEvidence(args.out)
    try:e.run();e.report['status']='pass'
    except BaseException as exc:e.report['status']='fail';e.report['failures'].append(repr(exc));e.save();raise
    finally:
        if e.s:e.stop()
        e.save()
if __name__=='__main__':main()
