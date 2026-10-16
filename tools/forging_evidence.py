#!/usr/bin/env python3
"""Focused real-client forging evidence. Mapped learning/menu/casts; RCON fixtures/readback only."""
import argparse
import json
import math
from pathlib import Path
import sys
import time
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.alchemy_evidence import AlchemyEvidence
from tools.alchemy_quality_evidence import read_data,quality_stacks,SecondClient
from tools.combat_capture import scene,session as cs
from tools.combat_capture.sword_qi import parse_events
from tools.forging.materials import load as material_definitions

POS='0 -60 3'
MARK='myvillage:forge_mark'
KNOWLEDGE='"neoforge:attachments"."myvillage:learned_forge_patterns"'

class ForgingEvidence(AlchemyEvidence):
    def __init__(self,out):
        super().__init__(out,'zh_cn')
        self.rules=json.loads((ROOT/'src/main/resources/data/myvillage/forging/rules.json').read_text())
        self.report.update(kind='forging_prototype',method='mapped pattern use, table menus, repair and sword-qi cast; administrator fixtures/readback; no DevBridge',
            selected=['learning','forging','repair','effects','two_players','persistence'],completed=[],
            owner_appearance='not_verified',owner_feel='not_verified',owner_balance='not_verified',natural_acquisition='not_verified')
    def check(self,name,passed,detail):
        super().check(name,passed,detail)
        if not passed:raise AssertionError(name)
        return True
    def inventory(self,user=None):return quality_stacks(self.rc(f'data get entity {user or self.s.username} Inventory'))
    def count(self,item,user=None):return sum(v['count'] for v in self.inventory(user).values() if v['id']==item)
    def learned(self,user=None):
        reply=self.rc(f'data get entity {user or self.s.username} {KNOWLEDGE}')
        return [] if 'Found no elements' in reply else read_data(reply)
    def click(self,x,y,shift=False,game=None):
        g=game or self.s.game;w,h=g.size;scale=max(1,min(w//320,h//240));left=(w//scale-304)//2;top=(h//scale-238)//2
        g.xdo('mousemove',str(scale*(left+x)),str(scale*(top+y)))
        if shift:g.xdo('keydown','Shift_L')
        try:g.xdo('click','1')
        finally:
            if shift:g.xdo('keyup','Shift_L')
        time.sleep(.3)
    def open_table(self,game=None,user=None):
        g=game or self.s.game;u=user or self.s.username
        self.rc(f'tp {u} .5 -60 .5 0 20.5');time.sleep(.4);g.require_ingame('forging table');g.xdo('click','3')
        self.poll(g.pointer_grabbed,lambda b:b is False,8,'mapped table menu')
    def close_menu(self,game=None):
        g=game or self.s.game;g.xdo('key','Escape');time.sleep(.35);g.require_ingame('close forging menu')
    def learn(self,recipe,user=None,game=None):
        u=user or self.s.username;g=game or self.s.game;item='myvillage:pattern_'+recipe
        self.rc(f'tp {u} .5 -60 .5 180 -60');self.rc(f'item replace entity {u} hotbar.0 with {item} 2');g.key('1');time.sleep(.4)
        before=self.count(item,u);g.xdo('click','3');self.poll(lambda:self.learned(u),lambda ids:recipe in ids,8,'pattern learned')
        self.check('learn_consumes_one_'+recipe,self.count(item,u)==before-1,self.learned(u))
        g.xdo('click','3');time.sleep(.4);self.check('duplicate_pattern_retained_'+recipe,self.count(item,u)==before-1,self.inventory(u))
    def prepare_materials(self,recipe,trait,user=None,multiplier=1):
        u=user or self.s.username;self.rc(f'clear {u}')
        costs=dict(recipe['costs'])
        for item,n in self.rules['traits'][trait]['costs'].items():costs[item]=costs.get(item,0)+n
        tag_items={'#'+m['tag']:'myvillage:'+m['id'] for m in material_definitions()}
        resolved={}
        for reference,n in costs.items():
            item=tag_items[reference] if reference.startswith('#') else reference
            resolved[item]=resolved.get(item,0)+n
        for item,n in resolved.items():self.rc(f'give {u} {item} {n*multiplier}')
        return resolved
    def forge(self,index,trait,cap=1):
        r=self.rules['recipes'][index];u=self.s.username;costs=self.prepare_materials(r,trait,multiplier=2)
        before=self.inventory();self.open_table();self.click(50,40+index*24);self.click(50,94+['sharp','channeling','tough'].index(trait)*22)
        self.click(152,137);time.sleep(.2)
        after=self.inventory()
        self.check('exact_cost_'+r['id']+'_'+trait,all(self.count(item)==n for item,n in costs.items()),after)
        self.click(152,137);self.click(152,137)
        self.check('occupied_output_blocks_repeats_'+trait,self.inventory()==after,after)
        self.capture(r['id']+'-'+trait+'-menu.png')
        self.click(284,104,shift=True);self.close_menu()
        products=[(slot,s) for slot,s in self.inventory().items() if s['id']==r['output']]
        self.check('one_marked_product_'+r['id']+'_'+trait,len(products)==1 and products[0][1]['components'][MARK]['trait']==trait and 1<=products[0][1]['components'][MARK]['quality']<=cap,products)
        return products[0]
    def cast(self,slot,stack):
        u=self.s.username;self.rc(f'tp {u} .5 -60 .5 90 0');self.rc(f'myvillage cultivation learn {u} myvillage:jianqi_jue');self.rc(f'myvillage cultivation qi fill {u}')
        if not 0<=slot<=8:raise RuntimeError('Expected reverse-transfer output in hotbar')
        self.s.game.key(str(slot+1));time.sleep(.4);scene.ensure_cultivation(self.s)
        log=cs.LOG_DIR/'server.log';offset=log.stat().st_size
        self.s.game.xdo('mousedown','3');time.sleep(.4);self.s.game.xdo('mouseup','3')
        def events():
            with log.open('rb') as stream:stream.seek(offset);return parse_events(stream.read().decode('utf-8','replace').splitlines())
        rows=self.poll(events,lambda es:any(e.get('result')=='released' for e in es),8,'mapped forged sword cast')
        released=[r for r in rows if r.get('result')=='released'];self.check('one_cast',len(released)==1,released)
        release=released[0];skill=json.loads((ROOT/'src/main/resources/data/myvillage/combat/skill/jianqi_jue.json').read_text())
        files=list((ROOT/'src/main/resources/data/myvillage').rglob('jianqi_jue.json'))
        definitions=[json.loads(p.read_text()) for p in files];tech=next(d for d in definitions if 'effects' in d)
        base=tech['effects']['active']['qi_cost'];raw=math.ceil(base*(1+(skill['charge_scale']-1)*int(release['charge'])/255))
        q=stack['components'][MARK]['quality'];discount=self.rules['traits']['channeling']['values'][q-1]
        expected=max(1,math.floor(raw*(1-discount)))
        self.check('channeling_server_cost',int(release['cost'])==expected and expected<raw,{'release':release,'raw':raw,'expected':expected})
        time.sleep(2)
    def repair(self,slot,stack):
        u=self.s.username;components=dict(stack['components']);components['minecraft:damage']=900
        components['minecraft:custom_name']=json.dumps({'text':'Old friend'})
        components['minecraft:enchantments']={'levels':{'minecraft:unbreaking':1}}
        spec=stack['id']+'['+','.join(k+'='+json.dumps(v,separators=(',',':')) for k,v in components.items())+']'
        self.rc(f'clear {u}');self.rc(f'item replace entity {u} hotbar.0 with {spec}');self.s.game.key('1')
        damaged=self.inventory()[0]
        for item,n in self.rules['repair']['costs'].items():self.rc(f'give {u} {item} {n}')
        self.open_table();self.click(78,222,shift=True);self.click(248,137);self.capture('repair-completed.png')
        self.close_menu() # Also proves closing returns an unclaimed result.
        rows=[s for s in self.inventory().values() if s['id']==stack['id']]
        self.check('repair_returns_once_on_close',len(rows)==1,rows)
        repaired=rows[0];prior=damaged['components'];actual=repaired['components']
        self.check('repair_preserves_all_other_components',{k:v for k,v in prior.items() if k!='minecraft:damage'}=={k:v for k,v in actual.items() if k!='minecraft:damage'},actual)
        mark=prior[MARK];maximum=prior['minecraft:max_damage']
        multiplier=self.rules['traits']['tough']['values'][mark['quality']-1] if mark['trait']=='tough' else 1
        expected=max(0,900-math.ceil(maximum*self.rules['repair']['fraction']*multiplier))
        self.check('repair_actual_durability',actual.get('minecraft:damage',0)==expected,{'expected':expected,'actual':actual})
        self.check('repair_costs_once',all(self.count(i)==0 for i in self.rules['repair']['costs']),self.inventory())
    def run(self):
        self.start();u=self.s.username
        for c in (*scene.world_rules(),*scene.player_setup(u,'minecraft:air'),'myvillage world pause','forceload add -16 -16 32 32',f'setblock {POS} myvillage:forging_table'):
            self.rc(c)
        self.open_table();self.capture('unlearned.png');self.close_menu()
        for r in self.rules['recipes']:self.learn(r['id'])
        self.report['completed'].append('learning')
        self.rc(f'myvillage cultivation awaken {u}');self.rc(f'myvillage cultivation initiate {u}');self.rc(f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_1');self.rc(f'myvillage cultivation crafting set {u} forging 0')
        # Query actual cap rather than assuming administrator commands succeeded.
        cap=self.one(f'myvillage cultivation crafting {u}','CRAFTING')
        self.check('low_cap_fixture',cap.get('forging_quality_cap')==1,cap)
        self.rc(f'clear {u}');self.rc(f'give {u} minecraft:diamond 1');before=self.inventory();self.open_table();self.click(152,137)
        self.capture('missing-materials.png');self.close_menu();self.check('missing_materials_atomic',self.inventory()==before,self.inventory())
        for trait in ['sharp','channeling','tough']:
            slot,stack=self.forge(0,trait)
            if trait=='sharp':
                self.s.game.key(str(slot+1));time.sleep(.4)
                self.check('sharp_modifier_saved',any(m['id']=='myvillage:forging_sharp' for m in stack['components']['minecraft:attribute_modifiers']['modifiers']),stack)
                self.check('sharp_attribute_equipped',self.number(f'attribute {u} minecraft:generic.attack_damage get')>7,stack)
            elif trait=='channeling':self.cast(slot,stack)
            else:self.repair(slot,stack)
        self.forge(1,'sharp');self.forge(1,'tough')
        self.report['completed']+=['forging','repair','effects']
        with SecondClient(self) as guest:
            g=guest.game;v=guest.username
            self.rc(f'gamemode survival {v}');self.rc(f'tp {u} -2 -60 .5')
            self.prepare_materials(self.rules['recipes'][0],'sharp',user=v)
            self.open_table(g,v);before=self.inventory(v);self.click(152,137,game=g);self.close_menu(g)
            self.check('guest_cannot_borrow_knowledge',self.learned(v)==[] and self.inventory(v)==before,self.inventory(v))
            self.learn('qingfeng_sword',v,g);self.rc(f'myvillage cultivation awaken {v}');self.rc(f'myvillage cultivation crafting set {v} forging 0')
            self.prepare_materials(self.rules['recipes'][0],'sharp',user=v);owner_before=self.inventory()
            self.open_table(g,v);self.click(152,137,game=g);self.close_menu(g)
            self.check('guest_forges_own_weapon',self.count('myvillage:qingfeng_sword',v)==1,self.inventory(v))
            self.check('guest_does_not_consume_owner_inventory',self.inventory()==owner_before,owner_before)
            self.check('knowledge_stays_private',self.learned(v)==['qingfeng_sword'],self.learned(v))
        self.report['completed'].append('two_players');self.report['multiplayer']='bounded_developer_checks_pass'
        self.rc(f'myvillage cultivation crafting set {u} forging 100');self.forge(0,'sharp',cap=4)
        self.persistence()
    def persistence(self):
        before=self.inventory();knowledge=self.learned();self.rc('save-all flush');self.stop();self.language='en_us';self.start(reuse=True)
        self.check('weapon_components_restart',self.inventory()==before,self.inventory());self.check('knowledge_restart',self.learned()==knowledge,self.learned())
        self.open_table();self.capture('english-menu.png');self.close_menu()
        self.rc(f'kill {self.s.username}')
        self.poll(lambda:scene.parse_float_data(self.rc(f'data get entity {self.s.username} Health')),lambda hp:hp is not None and hp>0,15,'server confirms living respawn')
        self.poll(lambda:self.s.game.pointer_grabbed(),bool,15,'respawn input')
        self.poll(lambda:self.learned(),lambda x:x==knowledge,15,'knowledge after death');self.check('knowledge_copy_on_death',self.learned()==knowledge,self.learned())
        self.report['completed'].append('persistence');self.report['status']='pass'
    def lifecycle_only(self):
        self.report['selected']=['learning','forging','persistence']
        self.start();u=self.s.username
        for c in (*scene.world_rules(),*scene.player_setup(u,'minecraft:air'),'myvillage world pause','forceload add -16 -16 32 32',f'setblock {POS} myvillage:forging_table'):
            self.rc(c)
        self.learn('qingfeng_sword');self.report['completed'].append('learning')
        self.rc(f'myvillage cultivation awaken {u}');self.rc(f'myvillage cultivation crafting set {u} forging 100')
        self.forge(0,'sharp',cap=4);self.report['completed'].append('forging')
        self.persistence()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'out/preview/forging_runtime');p.add_argument('--lifecycle-only',action='store_true');a=p.parse_args();e=ForgingEvidence(a.out)
    try:
        if a.lifecycle_only:e.lifecycle_only()
        else:e.run()
    except BaseException as error:e.report['status']='fail';e.report['failures'].append(repr(error));e.save();raise
    finally:
        if e.s:e.stop()
        e.save()
if __name__=='__main__':main()
