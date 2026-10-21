#!/usr/bin/env python3
"""Bounded cloud gathering-array scenarios; real keys/mouse, isolated world."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import sys
import time
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.alchemy_evidence import AlchemyEvidence
from tools.alchemy_array_evidence import menu_point
from tools.alchemy_quality_evidence import SecondClient, read_data
from tools.combat_capture import scene, session as cs
from tools.golden_core_evidence import parse_profile
from tools.modal_runner.remote import select_gathering_cases

CORE=(0,-61,3)
MENU=(300,238)


def pos(point): return ' '.join(map(str,point))


def energy(value, charges_per_stone=20):
    if not value.get('core') or any(type(value.get(k)) is not int or value[k]<0 for k in ('stones','charges')):
        raise ValueError('Invalid gathering energy readback')
    return value['stones']*charges_per_stone+value['charges']


class GatheringEvidence(AlchemyEvidence):
    def __init__(self,out,cases):
        super().__init__(out,'zh_cn')
        self.cases=cases
        self.cfg=json.loads((ROOT/'src/main/resources/data/myvillage/gathering/rules.json').read_text())
        self.report.update(kind='spirit_gathering',selected=cases,completed=[],unrun=list(cases),mapped_input=[],
            method='Actual mapped mouse/key interaction; RCON fixtures and readback in disposable cloud world',
            provider=os.environ.get('MYVILLAGE_CLOUD_PROVIDER','unspecified_cloud'),
            owner_appearance='not_verified',owner_balance='not_verified',owner_fps='not_verified',
            broad_multiplayer='not_verified',offline_accrual=False)

    def check(self,name,passed,detail):
        super().check(name,passed,detail)
        if not passed: raise AssertionError(name)
        return True

    def inspect(self,core=CORE): return self.one('myvillage gathering inspect '+pos(core),'GATHERING_ARRAY')
    def energy(self,core=CORE): return energy(self.inspect(core),self.cfg['charges_per_stone'])

    def close_menu(self):
        if self.s.game.pointer_grabbed() is False:
            self.s.game.xdo('key','Escape');time.sleep(.25)
        self.s.game.require_ingame('gathering input')

    def key(self,key,game=None,actor=None):
        game=game or self.s.game
        game.key(key)
        self.report['mapped_input'].append({'key':key,'actor':actor or self.s.username})

    def click(self,x,y,shift=False):
        px,py=menu_point(self.s.game.size,x,y,MENU)
        game=self.s.game;game.xdo('mousemove',str(px),str(py))
        if shift: game.xdo('keydown','Shift_L')
        try: game.xdo('click','1')
        finally:
            if shift: game.xdo('keyup','Shift_L')
        self.report['mapped_input'].append({'gui':[x,y],'shift':shift})
        time.sleep(.35)

    def open(self,core=CORE):
        self.close_menu();x,y,z=core
        self.rc(f'tp {self.s.username} {x+.5} {y+1} {z-1.5} 0 39')
        time.sleep(.4);self.s.game.xdo('click','3')
        self.report['mapped_input'].append({'right_click_core':core})
        self.poll(self.s.game.pointer_grabbed,lambda value:value is False,10,'mapped gathering menu')

    def place(self,core=CORE,missing=False):
        x,y,z=core;r=self.cfg['radius']
        self.rc(f'setblock {x} {y} {z} myvillage:spirit_gathering_core')
        for i,(dx,dz) in enumerate(((0,-r),(r,0),(0,r),(-r,0))):
            self.rc(f'setblock {x+dx} {y+1} {z+dz} '+('minecraft:air' if missing and i==0 else 'myvillage:spirit_gathering_node'))

    def player(self,user=None):
        user=user or self.s.username
        for command in (f'clear {user}',f'gamemode survival {user}',f'myvillage cultivation awaken {user}',
                        f'myvillage cultivation initiate {user}',
                        f'myvillage cultivation setrealm {user} myvillage:qi_refining myvillage:qi_refining_1',
                        f'myvillage cultivation setprogress {user} 0',f'myvillage cultivation setstability {user} 0'):
            self.rc(command)

    def fixture(self,missing=False):
        self.close_menu();self.key('x');self.player()
        for command in ('fill -6 -60 -3 8 -55 11 minecraft:air',
                        'fill -6 -61 -3 8 -61 11 minecraft:stone_bricks','kill @e[type=minecraft:item]'):
            self.rc(command)
        self.place(missing=missing)
        self.poll(self.inspect,lambda value:value['formed']!=missing,10,'fixture pattern state')

    def activate(self,stones=8,core=CORE):
        self.rc(f'item replace entity {self.s.username} hotbar.0 with myvillage:low_grade_spirit_stone {stones}')
        self.open(core);self.click(77,222,True)
        self.poll(lambda:self.inspect(core),lambda value:value['stones']==stones,5,'mapped stone insertion')
        self.click(172,128)
        self.poll(lambda:self.inspect(core),lambda value:value['enabled'],5,'mapped enable')
        self.close_menu()

    def center(self,user=None,x=.5,z=3.5):
        self.rc(f'tp {user or self.s.username} {x} -60 {z} 0 0');time.sleep(.35)

    def benefit(self,batches=4,key='v'):
        self.center();before=self.energy();self.key(key)
        self.poll(self.energy,lambda value:value<=before-batches,20,'paid player batches')
        self.key('x');time.sleep(.8)
        return before-self.energy()

    def structure(self):
        self.fixture()
        for i,(dx,dz) in enumerate(((0,-3),(3,0),(0,3),(-3,0))):
            point=(dx,-60,3+dz);self.rc('setblock '+pos(point)+' minecraft:air')
            check=self.poll(self.inspect,lambda value:not value['formed'],5,'missing node')
            self.check('missing_direction_'+str(i),check['issue']==i+1,check)
            self.rc('setblock '+pos(point)+' myvillage:spirit_gathering_node')
            self.poll(self.inspect,lambda value:value['formed'],5,'repaired node')
        for point,block,expected in (((-2,-61,4),'minecraft:air',5),((1,-59,3),'minecraft:stone',6)):
            self.rc('setblock '+pos(point)+' '+block)
            result=self.poll(self.inspect,lambda value:not value['formed'],5,'foundation or clearance')
            self.check('structure_issue_'+str(expected),result['issue']==expected,result)
            self.rc('setblock '+pos(point)+(' minecraft:stone_bricks' if expected==5 else ' minecraft:air'))
            self.poll(self.inspect,lambda value:value['formed'],5,'repaired foundation or clearance')
        self.rc('setblock 0 -61 9 myvillage:spirit_gathering_core')
        result=self.poll(self.inspect,lambda value:value['issue']==8,5,'shared node rejected')
        self.check('shared_incomplete_core_rejected',not result['formed'],result)

    def mapped(self):
        self.fixture(missing=True)
        self.rc(f'item replace entity {self.s.username} hotbar.0 with myvillage:spirit_gathering_node')
        self.rc(f'tp {self.s.username} .5 -60 -1.5 0 39')
        self.key('1');time.sleep(.35);self.s.game.xdo('click','3')
        self.report['mapped_input'].append({'place_final_node':[0,-60,0]})
        self.poll(self.inspect,lambda value:value['formed'],8,'mapped last node placement')
        self.activate(4);self.open();self.capture('gathering-menu.png');self.close_menu()
        used=self.benefit()
        profile=self.profile();base=profile['affinity'];gain=base+base*self.cfg['bonus_basis_points']//10000
        self.check('normal_gain_matches_exact_energy_debits',profile['progress']==used*gain,{'profile':profile,'batches':used,'gain':gain})
        self.open();self.click(250,128);self.close_menu()
        self.check('mapped_stop_disables',not self.inspect()['enabled'],self.inspect())

    def economy(self):
        self.fixture();self.activate(2)
        before=self.energy();time.sleep(1.5)
        self.check('idle_uses_no_energy',self.energy()==before,self.inspect())
        self.rc(f'give {self.s.username} myvillage:low_grade_spirit_stone 32')
        used=self.benefit(key='b');profile=self.profile()
        gain=50+50*self.cfg['bonus_basis_points']//10000
        self.check('spirit_gain_and_array_cost_agree',profile['progress']==used*gain,{'profile':profile,'batches':used})
        self.rc(f'myvillage cultivation setprogress {self.s.username} 1100')
        self.rc(f'myvillage cultivation setstability {self.s.username} 0')
        self.center();before=self.energy();self.key('v')
        self.poll(self.profile,lambda value:value['stability']>0,10,'solidification')
        self.key('x');time.sleep(.5)
        self.check('solidification_does_not_consume_array_energy',self.energy()==before,self.inspect())
        self.fixture();self.activate(1);self.center();self.key('v')
        self.poll(self.energy,lambda value:value==0,30,'one stone depleted')
        self.key('x');time.sleep(.6);before=self.profile()['progress']
        self.key('v');self.poll(self.profile,lambda value:value['progress']>=before+20,10,'ordinary fallback continues')
        self.key('x');time.sleep(.5);after=self.profile()
        self.check('exhausted_array_falls_back_without_stopping_meditation',
                   self.energy()==0 and (after['progress']-before)%after['affinity']==0,after)
        self.fixture();self.activate(2);self.benefit(2)
        self.rc('setblock 0 -60 0 minecraft:air')
        self.poll(self.inspect,lambda value:not value['formed'],5,'broken active array')
        before=self.energy();self.center();self.key('v');time.sleep(3);self.key('x');time.sleep(.4)
        self.check('broken_array_preserves_credit',self.energy()==before,self.inspect())

    def overlap(self):
        self.fixture();other=(1,-61,3);self.place(other)
        self.poll(lambda:self.inspect(other),lambda value:value['formed'],5,'second overlapping array')
        self.activate(4);self.activate(4,other)
        before=[self.energy(),self.energy(other)]
        self.center(x=1.0);self.key('v')
        self.poll(lambda:sum((self.energy(),self.energy(other))),lambda value:value<=sum(before)-4,15,'overlapping arrays benefit')
        self.key('x');time.sleep(.6)
        debits=[before[0]-self.energy(),before[1]-self.energy(other)]
        profile=self.profile();gain=profile['affinity']*(10000+self.cfg['bonus_basis_points'])//10000
        self.check('overlap_selects_only_one_array',sum(value>0 for value in debits)==1 and profile['progress']==sum(debits)*gain,
                   {'debits':debits,'profile':profile})

    def two_players(self):
        self.fixture();self.activate(8)
        class GatheringGuest(SecondClient): username='GatheringGuest'
        with GatheringGuest(self) as guest:
            self.player(guest.username)
            self.center(x=.25,z=3.25);self.center(guest.username,x=1.5,z=3.5)
            before=self.energy();self.key('v');self.key('v',guest.game,guest.username)
            self.poll(self.inspect,lambda value:value['users']==2,20,'two real players benefit')
            self.key('x');self.key('x',guest.game,guest.username);time.sleep(.8)
            profiles=[self.profile(),parse_profile(self.rc('myvillage cultivation info '+guest.username))]
            used=before-self.energy()
            gains=[p['affinity']*(10000+self.cfg['bonus_basis_points'])//10000 for p in profiles]
            self.check('two_clients_pay_for_each_player_batch',all(p['progress']>0 and p['progress']%g==0 for p,g in zip(profiles,gains))
                       and sum(p['progress']//g for p,g in zip(profiles,gains))==used,{'profiles':profiles,'debits':used})
            self.capture('gathering-two-players.png')

    def restart(self):
        self.fixture();self.activate(4);self.benefit(3)
        before=self.inspect();self.rc('save-all flush');self.stop();self.start(reuse=True)
        after=self.poll(self.inspect,lambda value:value['formed'],15,'same-world restart formation')
        self.check('restart_keeps_stones_charges_and_enabled_without_catchup',
                   all(after[k]==before[k] for k in ('stones','charges','enabled')) and after['users']==0,
                   {'before':before,'after':after})
        used=self.benefit(2)
        self.check('restarted_credit_can_be_used',used>=2,self.inspect())

    def visual(self):
        self.fixture();self.activate(8);self.center();self.key('v')
        self.poll(self.inspect,lambda value:value['running'],12,'running visual')
        # Observer camera position is an administrator fixture; V is the actual player input.
        self.key('F5');time.sleep(.5);self.capture('gathering-running-player.png');self.key('F5');self.key('F5');self.key('x')
        self.rc(f'gamemode creative {self.s.username}')
        self.rc(f'tp {self.s.username} 8 -53 -6 40 33')
        time.sleep(1);self.key('F1');self.capture('gathering-overview.png');self.key('F1')
        self.rc(f'gamemode survival {self.s.username}');self.open();self.capture('gathering-final-menu.png');self.close_menu()

    def export(self):
        world=cs.SERVER_DIR/cs.LEVEL_NAME
        target=self.out/'gathering_preview_world.zip'
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as archive:
            for file in sorted(world.rglob('*')):
                rel=file.relative_to(world)
                if file.is_file() and not file.is_symlink() and 'session.lock' not in rel.parts:
                    archive.write(file,'四方聚灵阵展示/'+rel.as_posix())
        self.report['world_export']={'file':target.name,'owner_saves_modified':False}

    def run(self):
        self.start()
        for command in (*scene.world_rules(),*scene.player_setup(self.s.username,'minecraft:air'),
                        'myvillage world pause','forceload add -32 -32 48 48','tick rate 20'):
            self.rc(command)
        for case in self.cases:
            getattr(self,case)();self.report['completed'].append(case);self.report['unrun'].remove(case);self.save()
        self.close_menu();self.key('x')
        for command in ('setblock -5 -60 1 myvillage:spirit_testing_stele',
                        'setblock -5 -60 3 myvillage:technique_inheritance_stele',
                        'setblock -5 -60 5 minecraft:chest','setworldspawn 0 -60 -2 0'):
            self.rc(command)
        for i,(item,count) in enumerate((('myvillage:low_grade_spirit_stone',64),('myvillage:spirit_gathering_core',1),
                                        ('myvillage:spirit_gathering_node',4),('minecraft:iron_pickaxe',1),('minecraft:stone_bricks',64))):
            self.rc(f'item replace block -5 -60 5 container.{i} with {item} {count}')
        self.rc('save-all flush');self.stop();self.export();self.report['status']='pass';self.save()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,default=ROOT/'out/preview/gathering/runtime')
    parser.add_argument('--cases',default='all');args=parser.parse_args()
    evidence=GatheringEvidence(args.out,select_gathering_cases(args.cases))
    try: evidence.run()
    except BaseException as error:
        evidence.report['status']='fail';evidence.report['failures'].append(repr(error));evidence.save();raise
    finally:
        if evidence.s: evidence.stop()
        evidence.save()


if __name__=='__main__':main()
