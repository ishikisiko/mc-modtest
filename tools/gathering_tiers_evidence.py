#!/usr/bin/env python3
"""Graded arrays: mapped placement/menus/meditation, saved grade credit and two real clients."""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
import subprocess
import sys
import time
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.gathering_evidence import GatheringEvidence, CORE, pos
from tools.gen_gathering_assets import IDS
from tools.alchemy_quality_evidence import SecondClient, quality_stacks
from tools.combat_capture import scene, session as cs
from tools.golden_core_evidence import parse_profile

CASES=['structure','matrix','switch','legacy_restart','two_players','visual']
FUELS={grade:'myvillage:'+grade+'_spirit_stone' for grade in ('low_grade','medium_grade','high_grade')}


class TierEvidence(GatheringEvidence):
    def __init__(self,out,cases):
        super().__init__(out,cases)
        self.all_rules=json.loads((ROOT/'src/main/resources/data/myvillage/gathering/rules.json').read_text())
        self.tier='low'
        self.report.update(kind='graded_gathering_arrays',selected=cases,completed=[],unrun=list(cases),
                           source_method='Mapped menus, actual V/B/X and physical block placement; RCON fixtures/readback; observer camera for visuals')

    def energy(self,core=CORE):
        data=self.inspect(core)
        return data['charges']+data['stones']*self.all_rules['fuel_charges'].get(data['queued_grade'],0)

    def count(self,item,user=None):
        stacks=quality_stacks(self.rc(f'data get entity {user or self.s.username} Inventory'))
        return sum(stack['count'] for stack in stacks.values() if stack['id']==item)

    def place(self,core=CORE,missing=False,tier=None):
        tier=tier or self.tier;x,y,z=core;r=self.all_rules['tiers'][tier]['radius']
        self.rc(f'fill {x-r} {y} {z-r} {x+r} {y} {z+r} minecraft:stone_bricks')
        self.rc('setblock '+pos(core)+' myvillage:'+IDS[tier]+'_core')
        for i,(dx,dz) in enumerate(((0,-r),(r,0),(0,r),(-r,0))):
            self.rc(f'setblock {x+dx} {y+1} {z+dz} '+('minecraft:air' if missing and i==0 else 'myvillage:'+IDS[tier]+'_node'))

    def fixture(self,tier='low',missing=False):
        self.tier=tier;self.close_menu();self.key('x');self.player()
        self.rc('fill -8 -60 -6 8 -54 12 minecraft:air')
        self.rc('fill -8 -61 -6 8 -61 12 minecraft:grass_block')
        self.rc('kill @e[type=minecraft:item]')
        self.place(missing=missing)
        self.poll(self.inspect,lambda d:d['formed']!=missing and d['tier']==tier,10,'graded structure fixture')

    def activate(self,fuel,stones=2,core=CORE):
        self.rc(f'item replace entity {self.s.username} hotbar.0 with {FUELS[fuel]} {stones}')
        self.open(core);self.click(77,222,True)
        self.poll(lambda:self.inspect(core),lambda d:d['stones']==stones and d['queued_grade']==fuel,5,'graded fuel insertion')
        self.click(172,128)
        self.poll(lambda:self.inspect(core),lambda d:d['enabled'],5,'graded activation')
        self.close_menu()

    def replace_queued(self,fuel,stones=2):
        self.open();self.click(148,53,True)
        self.poll(self.inspect,lambda d:d['stones']==0,5,'take queued fuel out')
        self.close_menu()
        self.rc(f'item replace entity {self.s.username} hotbar.0 with {FUELS[fuel]} {stones}')
        self.open();self.click(77,222,True)
        self.poll(self.inspect,lambda d:d['queued_grade']==fuel and d['stones']==stones,5,'replace queued grade')
        self.close_menu()

    def structure(self):
        for tier,rule in self.all_rules['tiers'].items():
            self.fixture(tier,missing=True);r=rule['radius'];inner=rule['inner_radius'];u=self.s.username
            self.rc(f'item replace entity {u} hotbar.0 with myvillage:{IDS[tier]}_node')
            self.rc(f'tp {u} .5 -60 {3-r-1.5} 0 39');self.key('1');time.sleep(.4)
            self.s.game.xdo('click','3');self.report['mapped_input'].append({'place_final_node':tier})
            self.poll(self.inspect,lambda d:d['formed'],8,'mapped '+tier+' final node')
            self.check('mapped_placement_forms_'+tier,self.inspect()['tier']==tier,self.inspect())
            if tier!='low':
                self.rc(f'setblock 0 -60 {3-r} myvillage:spirit_gathering_node')
                bad=self.poll(self.inspect,lambda d:not d['formed'],5,'wrong-tier node rejected')
                self.check('reject_mixed_nodes_'+tier,bad['issue']==1,bad)
                self.rc(f'setblock 0 -60 {3-r} myvillage:{IDS[tier]}_node')
            self.rc(f'setblock {r} -61 {3+r} minecraft:air')
            bad=self.poll(self.inspect,lambda d:d['issue']==5,5,'outer floor checked')
            self.check('outer_floor_required_'+tier,not bad['formed'],bad)
            self.rc(f'setblock {r} -61 {3+r} minecraft:stone_bricks')
            self.rc(f'setblock {inner} -59 {3+inner} minecraft:stone')
            bad=self.poll(self.inspect,lambda d:d['issue']==6,5,'whole benefit area clear')
            self.check('expanded_clearance_required_'+tier,not bad['formed'],bad)
            self.rc(f'setblock {inner} -59 {3+inner} minecraft:air')
            self.poll(self.inspect,lambda d:d['formed'],5,'restored footprint')

    def matrix(self):
        for tier,rule in self.all_rules['tiers'].items():
            for fuel,bonus in rule['bonuses'].items():
                self.fixture(tier);self.activate(fuel)
                before=self.energy();time.sleep(.7)
                self.check('idle_keeps_energy_'+tier+'_'+fuel,self.energy()==before,self.inspect())
                self.rc(f'give {self.s.username} {FUELS["low_grade"]} 32')
                used=self.benefit(3,key='b');p=self.profile();gain=50+50*bonus//10000
                detail={'state':self.inspect(),'profile':p,'batches':used,'gain':gain}
                self.check('gain_and_energy_'+tier+'_'+fuel,used>=3 and p['progress']==used*gain,detail)
                self.check('personal_spirit_cost_unchanged_'+tier+'_'+fuel,self.count(FUELS['low_grade'])==32-used,detail)
                self.check('active_fuel_and_bonus_'+tier+'_'+fuel,detail['state']['fuel_grade']==fuel and detail['state']['bonus_basis_points']==bonus,detail['state'])
            for fuel in set(FUELS)-set(rule['bonuses']):
                self.fixture(tier)
                self.rc(f'item replace entity {self.s.username} hotbar.0 with {FUELS[fuel]} 1')
                self.open();self.click(77,222,True);time.sleep(.4)
                self.check('reject_slot_'+tier+'_'+fuel,self.inspect()['stones']==0 and self.count(FUELS[fuel])==1,self.inspect())
                self.close_menu()
            self.fixture(tier);fuel=list(rule['bonuses'])[-1];self.activate(fuel)
            inner=rule['inner_radius'];before=self.energy()
            self.center(x=inner+.5,z=3+inner+.5);self.key('v')
            self.poll(self.energy,lambda n:n<before,12,'inside larger benefit corner');self.key('x');time.sleep(.5)
            self.check('benefit_reaches_corner_'+tier,self.energy()<before,self.inspect())
            before=self.energy();progress=self.profile()['progress']
            self.center(x=inner+1.5);self.key('v');time.sleep(3.5);self.key('x');time.sleep(.5)
            self.check('outside_area_no_bonus_'+tier,self.energy()==before and self.profile()['progress']>progress,self.inspect())

    def switch(self):
        self.fixture('high');self.activate('low_grade');self.benefit(2)
        before=self.inspect();self.replace_queued('high_grade',1);after=self.inspect()
        self.check('queue_upgrade_does_not_revalue_credit',after['charges']==before['charges'] and after['fuel_grade']=='low_grade'
                   and after['bonus_basis_points']==3500 and after['queued_bonus_basis_points']==6000,{'before':before,'after':after})
        self.open();self.capture('high-old-credit-new-fuel-menu.png');self.close_menu()
        self.rc(f'myvillage cultivation setprogress {self.s.username} 0')
        low_batches=after['charges'];used=self.benefit(low_batches+2);profile=self.profile();affinity=profile['affinity']
        expected=low_batches*(affinity+affinity*3500//10000)+(used-low_batches)*(affinity+affinity*6000//10000)
        self.check('grade_switch_only_after_old_credit_spent',used>low_batches and profile['progress']==expected
                   and self.inspect()['fuel_grade']=='high_grade',{'batches':used,'old_batches':low_batches,'profile':profile,'expected':expected,'state':self.inspect()})
        before=self.inspect();self.replace_queued('low_grade');after=self.inspect()
        self.check('queue_downgrade_preserves_high_credit',after['charges']==before['charges'] and after['fuel_grade']=='high_grade'
                   and after['bonus_basis_points']==6000 and after['queued_bonus_basis_points']==3500,after)

    def legacy_restart(self):
        self.fixture('high');self.activate('high_grade');self.benefit(2);self.replace_queued('low_grade')
        legacy=(18,-61,3);self.place(legacy,tier='low')
        self.rc('data merge block '+pos(legacy)+' {Version:1,Charges:17,Enabled:1b,FuelGrade:""}')
        before=self.inspect();old=self.inspect(legacy)
        self.check('v1_credit_migrates_as_low',old['charges']==17 and old['fuel_grade']=='low_grade' and old['enabled'],old)
        self.rc('save-all flush');self.stop();self.start(reuse=True)
        after=self.poll(self.inspect,lambda d:d['formed'],15,'high array after restart')
        old_after=self.poll(lambda:self.inspect(legacy),lambda d:d['formed'],15,'legacy array after restart')
        fields=('charges','stones','fuel_grade','queued_grade','enabled','bonus_basis_points')
        self.check('restart_keeps_high_credit_and_low_queue',all(before[k]==after[k] for k in fields) and after['users']==0,{'before':before,'after':after})
        self.check('restart_keeps_migrated_legacy_credit',old_after['charges']==17 and old_after['fuel_grade']=='low_grade' and old_after['users']==0,old_after)
        self.open();self.capture('high-persisted-credit-menu.png');self.close_menu()
        used=self.benefit(2)
        self.check('persisted_high_credit_remains_spendable',used>=2 and self.inspect()['fuel_grade']=='high_grade' and self.inspect()['queued_grade']=='low_grade',self.inspect())

    def two_players(self,guest):
        self.fixture('high');self.activate('high_grade');self.player(guest.username)
        self.center(x=.25,z=3.25);self.center(guest.username,x=3.5,z=6.5)
        before=self.energy();self.key('v');self.key('v',guest.game,guest.username)
        self.poll(self.inspect,lambda d:d['users']==2,20,'two graded beneficiaries')
        self.key('x');self.key('x',guest.game,guest.username);time.sleep(.6)
        profiles=[self.profile(),parse_profile(self.rc('myvillage cultivation info '+guest.username))]
        gains=[p['affinity']+p['affinity']*6000//10000 for p in profiles]
        used=before-self.energy()
        self.check('two_clients_debit_each_positive_batch',all(p['progress']>0 and p['progress']%g==0 for p,g in zip(profiles,gains))
                   and used==sum(p['progress']//g for p,g in zip(profiles,gains)),{'profiles':profiles,'charges_used':used,'gains':gains})

    def capture_game(self,game,name):
        game.xdo('mousemove','0','0');time.sleep(.2)
        window=game.xdo('search','--name','Minecraft').split()[0]
        subprocess.run(['import','-silent','-window',window,str(self.out/name)],env=game.env,check=True,timeout=30)

    def camera(self,guest,position,target):
        dx,dy,dz=target[0]-position[0],target[1]-(position[1]+1.62),target[2]-position[2]
        yaw=math.degrees(math.atan2(-dx,dz));pitch=math.degrees(-math.atan2(dy,math.hypot(dx,dz)))
        self.rc(f'tp {guest.username} '+pos(position)+f' {yaw} {pitch}')
        time.sleep(1.3)

    def visual(self,guest):
        self.close_menu();self.key('x');self.key('x',guest.game,guest.username)
        self.rc('fill -23 -60 -5 25 -53 11 minecraft:air')
        self.rc('fill -23 -61 -5 25 -61 11 minecraft:grass_block')
        self.rc('kill @e[type=minecraft:item]')
        locations={'low':(-16,-61,3),'medium':(0,-61,3),'high':(18,-61,3)}
        for tier,core in locations.items():self.place(core,tier=tier)
        self.rc(f'gamemode spectator {guest.username}');self.key('F1',guest.game,guest.username)
        for tier,core in locations.items():
            self.tier=tier;self.player();fuel=list(self.all_rules['tiers'][tier]['bonuses'])[-1]
            self.activate(fuel,4,core);x,y,z=core
            self.rc(f'tp {self.s.username} {x+.5} {y+1} {z+.5} 0 0');time.sleep(.5);self.key('v')
            self.poll(lambda:self.inspect(core),lambda d:d['running'],15,'running '+tier+' visual')
            self.camera(guest,(x+10.5,y+9,z-12),(x+.5,y+1.1,z+.5))
            self.capture_game(guest.game,tier+'-running.png')
            subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','x11grab','-video_size','960x540',
                            '-framerate','20','-i',guest.game.display+'.0','-t','4','-an','-c:v','libx264','-preset','veryfast',
                            '-crf','22','-pix_fmt','yuv420p',str(self.out/(tier+'-running.mp4'))],check=True,timeout=30)
            radius=self.all_rules['tiers'][tier]['radius']
            self.camera(guest,(x+radius+2.4,y+1.7,z-3),(x+radius+.5,y+1.55,z+.5))
            self.capture_game(guest.game,tier+'-node-detail.png')
            self.check('visual_running_'+tier,self.inspect(core)['running'],self.inspect(core))
            self.key('x');self.open(core);self.capture(tier+'-menu.png');self.close_menu()
        self.camera(guest,(1,-30,-20),(1,-60,3));self.capture_game(guest.game,'three-tiers-overview.png')
        self.key('F1',guest.game,guest.username)
        self.rc('setworldspawn -7 -60 -3 0');self.rc(f'tp {self.s.username} -7 -60 -3 0 15')
        self.rc('setblock -7 -60 -5 minecraft:chest')
        supplies=[('myvillage:'+prefix+'_'+kind,1 if kind=='core' else 4) for prefix in IDS.values() for kind in ('core','node')]
        supplies += [(item,32) for item in FUELS.values()]+[('minecraft:stone_bricks',64),('minecraft:iron_pickaxe',1)]
        for i,(item,count) in enumerate(supplies):self.rc(f'item replace block -7 -60 -5 container.{i} with {item} {count}')
        self.rc('save-all flush')

    def export(self):
        world=cs.SERVER_DIR/cs.LEVEL_NAME;target=self.out/'gathering_tiers_preview_world.zip'
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as archive:
            for file in sorted(world.rglob('*')):
                rel=file.relative_to(world)
                if file.is_file() and not file.is_symlink() and 'session.lock' not in rel.parts:archive.write(file,'三档聚灵阵展示/'+rel.as_posix())
        self.report['world_export']={'file':target.name,'owner_saves_modified':False}

    def complete(self,name):
        self.report['completed'].append(name);self.report['unrun'].remove(name);self.save()

    def run(self):
        self.start()
        for command in (*scene.world_rules(),*scene.player_setup(self.s.username,'minecraft:air'),'myvillage world pause'):
            self.rc(command)
        for name in self.cases:
            if name in ('two_players','visual'):continue
            getattr(self,name)();self.complete(name)
        if any(name in self.cases for name in ('two_players','visual')):
            class Guest(SecondClient):username='GatheringTierGuest'
            with Guest(self) as guest:
                for name in ('two_players','visual'):
                    if name in self.cases:getattr(self,name)(guest);self.complete(name)
        self.rc('save-all flush');self.stop()
        if 'visual' in self.cases:self.export()
        self.report['status']='pass';self.save()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,default=ROOT/'out/preview/gathering_tiers/runtime')
    parser.add_argument('--cases',default='all');args=parser.parse_args()
    requested=CASES if args.cases=='all' else args.cases.split(',')
    if not requested or len(set(requested))!=len(requested) or set(requested)-set(CASES):raise SystemExit('Invalid or duplicate cases')
    evidence=TierEvidence(args.out,[case for case in CASES if case in requested])
    try:evidence.run()
    except BaseException as error:
        evidence.report['status']='fail';evidence.report['failures'].append(repr(error));evidence.save();raise
    finally:
        if evidence.s:evidence.stop()
        evidence.save()


if __name__=='__main__':main()
