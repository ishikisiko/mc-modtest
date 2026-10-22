#!/usr/bin/env python3
"""Colab-only visual fixtures; no claim about natural acquisition or gameplay balance."""
from pathlib import Path
import argparse,json,sys,time,traceback
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.locked_sword_evidence import LockedEvidence
from tools.ink_hub_evidence import window_geometry

CASES=('high_health','absorption','poison','wither','low_health','underwater','narrow','mounted','visibility')

class HealthEvidence(LockedEvidence):
    def __init__(self,out):
        super().__init__(out,[])
        self.report.update(kind='player_health_bar',selected=list(CASES),completed=[],unrun=list(CASES))
    def capture_state(self,name):
        user=self.s.username
        self.report.setdefault('health_readbacks',{})[name]={
            'health':self.hp(user),'maximum':self.number(f'attribute {user} minecraft:generic.max_health get'),
            'absorption':self.number(f'data get entity {user} AbsorptionAmount')}
        self.capture(name)
    def complete(self,name):
        self.report['completed'].append(name);self.report['unrun'].remove(name);self.save()
    def resize(self,width,height):
        game=self.s.game;window=game.xdo('search','--name','Minecraft').split()[0]
        game.xdo('windowsize','--sync',window,str(width),str(height));game.xdo('windowmove','--sync',window,'0','0');game.size=(width,height);time.sleep(.7)
        geometry=window_geometry(game.xdo('getwindowgeometry','--shell',window))
        self.check('window_size',geometry==dict(X=0,Y=0,WIDTH=width,HEIGHT=height),geometry)
    def run(self):
        self.start();user=self.s.username
        self.ready()
        self.rc(f'item replace entity {user} armor.chest with minecraft:diamond_chestplate')
        self.capture_state('ordinary_health')
        self.rc(f'attribute {user} minecraft:generic.max_health base set 1000')
        self.rc(f'effect give {user} minecraft:instant_health 1 20 true');self.step(2)
        self.capture_state('high_health');self.complete('high_health')
        self.rc(f'effect give {user} minecraft:absorption infinite 49 true');self.step(2)
        self.capture_state('absorption');self.complete('absorption')
        for name in ('poison','wither'):
            self.rc(f'effect give {user} minecraft:{name} 20 0 true');self.step(2)
            self.capture_state(name);self.complete(name);self.rc(f'effect clear {user} minecraft:{name}')
        self.rc(f'effect clear {user} minecraft:absorption');self.step(2)
        health=self.hp(user);self.rc(f'damage {user} {health*.85} minecraft:generic_kill');self.step(2)
        self.check('low_health_fixture',0<self.hp(user)<health*.26,{'before':health,'after':self.hp(user)})
        self.capture_state('low_health');self.complete('low_health')
        self.rc('fill -3 -60 -3 3 -56 3 minecraft:water');self.rc(f'tp {user} .5 -60 .5 0 0');self.step(10)
        self.capture_state('underwater');self.complete('underwater')
        self.rc('fill -3 -60 -3 3 -56 3 minecraft:air');self.step(2)
        self.resize(640,480);self.capture_state('narrow');self.complete('narrow');self.resize(960,540)
        self.rc('summon minecraft:horse .5 -60 .5 {Tame:1b,NoAI:1b,Tags:["health_hud_mount"]}')
        self.rc(f'ride {user} mount @e[tag=health_hud_mount,limit=1]');self.step(2)
        self.capture_state('mounted');self.complete('mounted');self.rc(f'ride {user} dismount');self.rc('kill @e[tag=health_hud_mount]')
        for mode in ('creative','spectator','survival'):
            self.rc(f'gamemode {mode} {user}');self.step(2);self.capture('mode_'+mode)
        self.key('F1');time.sleep(.3);self.capture('hidden_gui');self.key('F1');time.sleep(.3);self.capture('restored_gui');self.complete('visibility')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);args=p.parse_args()
    e=HealthEvidence(args.out)
    try:e.run()
    except BaseException as error:e.report['failures'].append(repr(error));traceback.print_exc()
    finally:
        try:
            if e.s:e.rc('tick unfreeze');e.stop()
        except BaseException as error:e.report['failures'].append('cleanup: '+repr(error))
        e.report['passed']=not e.report['failures'] and not e.report['unrun'] and all(c['pass'] for c in e.report['checks']);e.save()
    raise SystemExit(0 if e.report['passed'] else 1)
