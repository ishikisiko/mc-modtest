#!/usr/bin/env python3
"""Scoped disposable-cloud evidence: mapped formulas, furnace menus and pill use.

RCON prepares fixtures/readback and accelerates server ticks; it never calls item
use or breakthrough outcomes. Owner balance/feel/FPS remain unverified.
"""
import argparse
import json
from pathlib import Path
import sys
import time
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.alchemy_array_evidence import ArrayEvidence, world_pos, position, outward, saved_items
from tools.alchemy_quality_evidence import quality_stacks,read_data,pill_quality
from tools.combat_capture import scene

POS='0 -60 3'
PROFILE='"neoforge:attachments"."myvillage:cultivation_profile"'
KNOWLEDGE='"neoforge:attachments"."myvillage:learned_formulas"'

class FoundationEvidence(ArrayEvidence):
    def __init__(self,out):
        cases=['formulas','furnace_thresholds_and_five_outputs','batch_restart','array_third_ingredient_port','breakthrough','repair','persistence','quality_visuals']
        super().__init__(out,cases)
        self.report.update(kind='foundation_pills',provider='Colab',tick_acceleration='100 TPS during brewing; tick sprint only advances cooldown/attempt timers',owner_balance='not_verified')
        self.recipes=self.rules['recipes']
    def done(self,name):
        self.report['completed'].append(name);self.report['unrun'].remove(name);self.save()
    def raw_profile(self):return read_data(self.rc(f'data get entity {self.s.username} {PROFILE}'))
    def quality(self):return self.raw_profile().get('foundation_quality',0)
    def known(self):return read_data(self.rc(f'data get entity {self.s.username} {KNOWLEDGE}'))
    def saved_furnace(self):return read_data(self.rc(f'data get block {POS}'))
    def count(self,item):return sum(s['count'] for s in self.inventory().values() if s['id']==item)
    def fc(self,x,y,**kw):return self.click(x,y,dimensions=(304,222),**kw)
    def learn_all(self):
        u=self.s.username
        for r in self.recipes:
            self.rc(f'item replace entity {u} hotbar.0 with {r["formula"]} 2');self.rc(f'tp {u} .5 -60 .5 180 -60')
            self.s.game.key('1');time.sleep(.3);self.s.game.xdo('click','3')
            self.report['mapped_input'].append({'kind':'learn_formula','recipe':r['id']})
            self.poll(self.known,lambda ids:r['id'] in ids,5,'formula learned')
            self.check('one_formula_consumed_'+r['id'],self.count(r['formula'])==1,self.inventory())
        self.done('formulas')
    def materials(self,r,multiple=2):
        u=self.s.username;self.rc(f'clear {u}')
        for item,n in zip(r['ingredients'],r.get('ingredient_counts',[1,1])):self.rc(f'give {u} {item} {n*multiple}')
        self.rc(f'give {u} myvillage:low_grade_spirit_stone {r["fuel_count"]*multiple}')
    def brew(self,index):
        r=self.recipes[index];u=self.s.username
        self.materials(r);self.rc(f'myvillage cultivation crafting set {u} alchemy {r["minimum_aptitude"]-1}')
        self.open_furnace()
        for _ in range(index//3):self.fc(86,117,label='recipe_next_page')
        self.fc(54,50+(index%3)*22,label='select_'+r['id'])
        before=self.saved_furnace();self.check('third_slot_filled_'+r['id'],saved_items(before).get(5,{}).get('count')==r['ingredient_counts'][2],before)
        self.fc(200,113,label='reject_under_aptitude');after=self.saved_furnace()
        self.check('under_aptitude_keeps_materials_'+r['id'],'Batch' not in after and after.get('Items')==before.get('Items'),after)
        self.rc(f'myvillage cultivation crafting set {u} alchemy {r["minimum_aptitude"]}')
        self.fc(200,113,label='start_'+r['id'])
        active=self.poll(self.saved_furnace,lambda d:'Batch' in d,5,'batch accepted')
        self.check('frozen_quality_'+r['id'],active['Batch']['Quality']==r['fixed_quality'],active['Batch'])
        if index==7:
            self.close_menu();self.rc('tick freeze');frozen=self.saved_furnace();self.rc('save-all flush');self.stop();self.start(reuse=True)
            resumed=self.saved_furnace();self.check('batch_survives_restart',resumed.get('Batch')==frozen['Batch'],{'before':frozen,'after':resumed})
            self.rc('tick unfreeze');self.rc('tick rate 100');self.open_furnace();self.done('batch_restart')
        final=self.poll(self.saved_furnace,lambda d:'Batch' not in d and 4 in saved_items(d),45,'furnace finished')
        product=saved_items(final)[4]
        self.check('fixed_product_'+r['id'],product['id']==r['output'] and product['count']==1 and pill_quality(product)==r['fixed_quality'],final)
        self.check('all_three_inputs_spent_'+r['id'],all(slot not in saved_items(final) for slot in [1,2,3,5]),final)
        self.capture(r['id']+'-furnace.png');self.fc(268,46,shift=True,label='take_'+r['id']);self.close_menu()
        self.check('single_output_taken_'+r['id'],self.count(r['output'])==1,self.inventory())
    def array_third_slot(self):
        u=self.s.username;r=self.recipes[7]
        self.place_fixture();self.materials(r,multiple=1)
        self.rc(f'myvillage cultivation crafting set {u} alchemy 85');self.open()
        for _ in range(2):self.click(100,131,label='array_recipe_next_page')
        self.click(69,83,label='array_select_upper_repair');self.click(193,148,label='array_start')
        self.poll(self.saved,lambda d:'Batch' in d,8,'array batch')
        self.click(305,148,label='array_stop_after_current');self.close_menu()
        self.poll(self.saved,lambda d:'Batch' not in d and any(s['id']==r['output'] for s in saved_items(d).values()),40,'array output')
        origin=(0,-59,3);cell=next(c for c in self.layout['cells'] if c['role']=='input');port=world_pos(origin,'north',cell)
        hopper=(port[0]-1,port[1],port[2]);self.rc(f'setblock {position(hopper)} minecraft:hopper[facing=east]')
        self.rc(f'item replace block {position(hopper)} container.0 with myvillage:calm_lotus 4')
        fed=self.poll(self.saved,lambda d:saved_items(d).get(7,{}).get('count')==4,8,'third herb via hopper')
        self.check('input_port_routes_third_herb',saved_items(fed)[7]['id']=='myvillage:calm_lotus',fed)
        for slot,item,n in [(0,r['ingredients'][0],4),(1,r['ingredients'][1],4),(2,'myvillage:low_grade_spirit_stone',r['fuel_count'])]:self.slot(slot,item,n)
        self.open();self.capture('array-three-ingredients.png');self.click(193,148,label='array_start_hopper_fed_batch');self.poll(self.saved,lambda d:'Batch'in d,5,'second array batch');self.click(305,148,label='stop_second_array_batch');self.close_menu()
        final=self.poll(self.saved,lambda d:'Batch'not in d and sum(s['count'] for k,s in saved_items(d).items() if 3<=k<7 and s['id']==r['output'])==2,40,'second array product')
        self.check('array_third_ingredient_consumed_once',7 not in saved_items(final),final);self.done('array_third_ingredient_port')
    def cooldown(self):
        self.rc('tick sprint 650t');time.sleep(1)
        self.poll(self.med,lambda d:d['cooldown_ticks']==0 and d['effect_ticks']==0,10,'medicine interval')
    def use_pill(self,item,q):
        u=self.s.username;spec=item+('[myvillage:pill_quality='+str(q)+']' if q>1 else '')
        self.close_menu();self.rc(f'tp {u} .5 -60 .5 180 -60');self.rc(f'item replace entity {u} hotbar.0 with {spec} 2');self.s.game.key('1');time.sleep(.4)
        self.s.game.xdo('mousedown','3')
        try:time.sleep(2)
        finally:self.s.game.xdo('mouseup','3')
        self.report['mapped_input'].append({'kind':'held_pill_use','item':item,'quality':q,'seconds':2});self.save()
    def setup_qi(self):
        u=self.s.username;self.rc(f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_9');self.rc(f'myvillage cultivation setprogress {u} 1900');self.rc(f'myvillage cultivation setstability {u} 950');self.cooldown()
    def breakthrough(self):
        u=self.s.username;self.rc('tick rate 20');self.setup_qi();self.s.game.key('n');time.sleep(1)
        self.check('normal_advance_cannot_bypass_pill',self.raw_profile()['stage_id']=='myvillage:qi_refining_9',self.raw_profile())
        self.rc(f'myvillage cultivation setprogress {u} 1899');self.use_pill('myvillage:pill_zhuji',1)
        self.check('unready_does_not_consume',self.count('myvillage:pill_zhuji')==2,self.inventory())
        saw_failure=False
        for q in (3,2,1):
            success=False
            for attempt in range(12):
                self.setup_qi();prior=self.raw_profile();hp=self.number(f'data get entity {u} Health');self.use_pill('myvillage:pill_zhuji',q)
                self.check(f'consumed_once_q{q}_attempt{attempt}',self.count('myvillage:pill_zhuji')==1,self.inventory())
                self.rc('tick sprint 410t');time.sleep(1)
                result=self.poll(self.raw_profile,lambda p:p['stage_id']!='myvillage:qi_refining_9' or p['stability']<950,12,'breakthrough resolution')
                if result['stage_id']=='myvillage:foundation_early':
                    self.check(f'root_recorded_q{q}',result.get('foundation_quality')==q and result['cultivation_progress']==0,result)
                    expected=60*(1+[.05,.12,.2][q-1]);maximum=self.number(f'attribute {u} minecraft:generic.max_health get')
                    self.check(f'health_cap_q{q}',abs(maximum-expected)<.001,maximum)
                    self.check(f'no_free_heal_q{q}',abs(self.number(f'data get entity {u} Health')-hp)<.001,self.raw_profile());success=True
                    if q!=1 or saw_failure:break
                else:
                    self.check(f'failed_attempt_q{q}',result['cultivation_progress']==1900 and result['stability']==475 and result.get('foundation_quality',0)==prior.get('foundation_quality',0),result);saw_failure=True
                    if q==1 and success:
                        # Restore the realm of an already observed successful low-grade breakthrough.
                        self.rc(f'myvillage cultivation setrealm {u} myvillage:foundation_establishment myvillage:foundation_early');break
            self.check(f'observed_success_q{q}',success,{'attempts':attempt+1})
        self.check('observed_failed_attempt',saw_failure,{});self.done('breakthrough')
    def repair(self):
        u=self.s.username;self.cooldown();before=self.number(f'data get entity {u} Health');self.use_pill('myvillage:pill_buji',2)
        self.check('middle_repair_consumed_once',self.count('myvillage:pill_buji')==1,self.inventory());self.check('middle_root',self.quality()==2,self.raw_profile())
        self.check('middle_cap',abs(self.number(f'attribute {u} minecraft:generic.max_health get')-67.2)<.001,self.raw_profile())
        self.check('repair_does_not_heal',abs(self.number(f'data get entity {u} Health')-before)<.001,{})
        self.cooldown();self.use_pill('myvillage:pill_buji',2);self.check('same_grade_rejected_without_cost',self.count('myvillage:pill_buji')==2,self.inventory())
        self.rc(f'myvillage cultivation setrealm {u} myvillage:golden_core myvillage:golden_early');self.use_pill('myvillage:pill_buji',3)
        self.check('repair_after_golden_core',self.quality()==3 and self.count('myvillage:pill_buji')==1,self.raw_profile())
        self.check('golden_health_cap',abs(self.number(f'attribute {u} minecraft:generic.max_health get')-144)<.001,self.raw_profile())
        for i in range(3):self.rc(f'myvillage cultivation setstability {u} {100+i}')
        self.check('refresh_does_not_stack',abs(self.number(f'attribute {u} minecraft:generic.max_health get')-144)<.001,self.raw_profile())
        self.cooldown();self.use_pill('myvillage:pill_buji',2);self.check('downgrade_rejected_without_cost',self.count('myvillage:pill_buji')==2,self.inventory())
        self.done('repair')
    def persistence_and_visual(self):
        u=self.s.username;before=self.raw_profile();self.rc('save-all flush');self.stop();self.start(reuse=True)
        after=self.raw_profile();self.check('root_after_restart',after.get('foundation_quality')==3 and after['stage_id']==before['stage_id'],after)
        self.check('attributes_after_restart',abs(self.number(f'attribute {u} minecraft:generic.max_health get')-144)<.001,after)
        self.rc(f'kill {u}');self.poll(lambda:self.number(f'data get entity {u} Health'),lambda hp:hp>0,15,'respawn')
        self.check('root_after_death',self.quality()==3,self.raw_profile());self.done('persistence')
        self.rc(f'clear {u}')
        for i,(item,q) in enumerate([('pill_zhuji',1),('pill_zhuji',2),('pill_zhuji',3),('pill_buji',2),('pill_buji',3),('ningyuan_fruit_low',0),('ningyuan_fruit_high',0)]):
            spec='myvillage:'+item+(f'[myvillage:pill_quality={q}]' if q>1 else '')
            self.rc(f'item replace entity {u} hotbar.{i} with {spec}')
        self.close_menu();self.s.game.key('e');time.sleep(.5);self.capture('green-blue-purple-inventory.png');self.close_menu()
        self.s.game.key('h');time.sleep(.5);self.capture('foundation-profile.png');self.close_menu();self.done('quality_visuals')
    def run(self):
        self.start();u=self.s.username
        for c in (*scene.world_rules(),*scene.player_setup(u,'minecraft:air'),'myvillage world pause','gamerule naturalRegeneration false','forceload add -16 -16 32 32',f'gamemode survival {u}',f'myvillage cultivation awaken {u}',f'myvillage cultivation initiate {u}',f'setblock {POS} myvillage:alchemy_furnace[facing=north]'):
            self.rc(c)
        self.learn_all();self.rc('tick rate 100')
        for i in range(3,8):self.brew(i)
        self.done('furnace_thresholds_and_five_outputs');self.array_third_slot();self.breakthrough();self.repair();self.persistence_and_visual()
        self.report['status']='pass';self.save()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'out/preview/foundation_pills/runtime');args=p.parse_args();e=FoundationEvidence(args.out)
    try:e.run()
    except BaseException as error:e.report['status']='fail';e.report['failures'].append(repr(error));e.save();raise
    finally:
        if e.s:e.stop()
        e.save()
if __name__=='__main__':main()
