#!/usr/bin/env python3
"""Retained Colab: mapped consumption, quality duration, poison boundaries and real restart."""
import argparse,json,sys,time,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from tools.wilderness_roster_evidence import RosterEvidence
from tools.alchemy_quality_evidence import read_data,inventory_point,QualityEvidence

class QingzhangEvidence(RosterEvidence):
    def med(self):return QualityEvidence.med(self)
    def poison(self):return 'minecraft:poison' in self.rc(f'data get entity {self.s.username} active_effects')
    def run(self):
        self.start(reuse=not self.attach)
        if self.cases==['tooltips']:
            self.close_menu();self.tooltips();self.rc('save-all flush');self.stop();return
        self.setup();u=self.s.username
        self.rc('kill @e[type=minecraft:item]');self.rc(f'attribute {u} minecraft:generic.max_health base set 300');self.rc(f'effect give {u} minecraft:instant_health 1 10 true')
        base=self.roster['medicines']['qingzhang']['duration_ticks']
        for quality,factor in enumerate(self.rules['quality']['strength_multipliers'],1):
            expected=round(base*factor)
            self.rc('tick sprint 1300t');time.sleep(1);self.poll(self.med,lambda s:s['effect_ticks']==0 and s['cooldown_ticks']==0,10,'old dose expires')
            self.rc(f'clear {u}');self.rc(f'effect give {u} minecraft:poison 60 0 true')
            self.check('poison_fixture_'+str(quality),self.poison(),self.med())
            before_tick=self.number('time query gametime');self.use_pill('myvillage:pill_qingzhang',quality)
            state=self.med();after_tick=self.number('time query gametime')
            self.check('mapped_duration_quality_'+str(quality),state['effect']=='qingzhang' and state['quality']==quality and expected-(after_tick-before_tick)<=state['effect_ticks']<=expected and self.count('myvillage:pill_qingzhang')==1,{'expected':expected,'elapsed_ticks':after_tick-before_tick,'state':state})
            self.check('clears_existing_poison_'+str(quality),not self.poison(),state)
            self.rc(f'effect give {u} minecraft:poison 60 0 true');self.poll(self.poison,lambda p:not p,3,'sustained poison removal')
            self.check('removes_reapplied_poison_'+str(quality),True,self.med())
            if quality==4:
                self.rc('tick freeze');before=self.med();self.rc('save-all flush');self.stop();self.start(reuse=True);self.rc('tick freeze');after=self.med()
                self.check('quality4_restart_not_clipped_to_baseline',after['quality']==4 and base<after['effect_ticks']<=before['effect_ticks'],{'before':before,'after':after})
                self.rc('tick unfreeze');self.done('restart')
            self.rc(f'tick sprint {expected+10}t');time.sleep(1);self.poll(self.med,lambda s:s['effect_ticks']==0,10,'dose expired')
            self.rc(f'effect give {u} minecraft:poison 60 0 true');time.sleep(.3)
            self.check('poison_returns_after_expiry_'+str(quality),self.poison(),self.med());self.rc(f'effect clear {u} minecraft:poison')
        self.done('four_qualities')
        self.tooltips();self.rc('save-all flush');self.stop()
    def tooltips(self):
        u=self.s.username
        for slot in range(4):self.rc(f'item replace entity {u} hotbar.{slot} with myvillage:pill_qingzhang[myvillage:pill_quality={slot+1}]')
        game=self.s.game;game.key('e');self.poll(game.pointer_grabbed,lambda v:not v,5,'inventory opened')
        book=bool(read_data(self.rc(f'data get entity {u} recipeBook.isGuiOpen')));window=game.xdo('search','--name','Minecraft').split()[0]
        try:
            for slot in range(4):
                x,y=inventory_point(game.size,slot,book);game.xdo('mousemove',str(x),str(y));time.sleep(.5);name=f'quality-{slot+1}-tooltip.png'
                subprocess.run(['import','-silent','-window',window,str(self.out/name)],env=game.env,check=True,timeout=30);self.report['screenshots'].append(name)
        finally:game.xdo('key','Escape')
        self.done('tooltips')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--attach',action='store_true');p.add_argument('--tooltips-only',action='store_true');a=p.parse_args()
    if (a.out/'evidence.json').exists():raise RuntimeError('Retain prior evidence')
    e=QingzhangEvidence(a.out,['tooltips'] if a.tooltips_only else ['four_qualities','restart','tooltips'],attach=a.attach)
    try:e.run();e.report['status']='passed'
    except BaseException as error:e.report['status']='failed';e.report['failures'].append(repr(error));raise
    finally:e.save()
