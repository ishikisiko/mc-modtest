#!/usr/bin/env python3
"""Cloud tag integration: temporary datapack aliases, mapped menus, reload and restart.

Vanilla stand-ins exercise real tag membership, not an installed third-party mod.
All fixtures and tags are confined to this collector's disposable capture world.
"""
import argparse
import json
from pathlib import Path
import sys
import time
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.forging_evidence import ForgingEvidence,MARK,POS
from tools.forging_acquisition_evidence import PROGRESS
from tools.combat_capture import scene,session as cs
from tools.forging.materials import load as materials

COPPER='c:ingots/copper'
TAGS={m['trait']:m['tag'] for m in materials()}
ALIASES={COPPER:['minecraft:brick'],TAGS['sharp']:['minecraft:quartz','minecraft:diamond'],TAGS['channeling']:['minecraft:emerald'],TAGS['tough']:['minecraft:amethyst_shard']}
PROBE='3 -60 3'

class TagEvidence(ForgingEvidence):
    def __init__(self,out):
        super().__init__(out)
        self.pack=cs.SERVER_DIR/cs.LEVEL_NAME/'datapacks/forging_tags_fixture'
        self.report.update(kind='forging_material_tags',selected=['default_tags','alias_infusion','alias_restart','live_tag_reload','tagged_forging','overlap_refusal','empty_tag'],completed=[],
            third_party_mods='not_verified; temporary datapack members exercise the same ItemStack tag API',natural_acquisition='not_selected')
    def tag(self,key,values,replace=False):
        namespace,path=key.split(':');p=self.pack/f'data/{namespace}/tags/item/{path}.json';p.parent.mkdir(parents=True,exist_ok=True)
        data={'replace':replace,'values':values};p.write_text(json.dumps(data)+'\n')
        self.report.setdefault('tag_fixtures',[]).append({'tag':key,**data});self.save()
    def has_tag(self,item,key):
        self.rc(f'item replace block {PROBE} container.0 with {item}')
        return 'passed' in self.rc(f'execute if items block {PROBE} container.0 #{key}')
    def reload_for(self,item,key,expected):
        self.rc('reload');self.poll(lambda:self.has_tag(item,key),lambda b:b==expected,30,'authoritative tag reload')
    def fill_power(self):
        cap=self.profile()['power_cap'];self.rc(f'myvillage cultivation setpower {self.s.username} {cap}')
        if self.profile()['power']!=cap:raise RuntimeError('Power fixture rejected')
        return cap
    def infusion(self):self.open_table();self.click(248,13)
    def insert(self,item):
        slot=next(i for i,s in self.inventory().items() if s['id']==item)
        self.click(78+(slot%9)*18,222 if slot<9 else 164+((slot-9)//9)*18,shift=True)
    def stacks(self,item):return [s for s in self.inventory().values() if s['id']==item]
    def default_tags(self):
        self.check('vanilla_copper_in_common_tag',self.has_tag('minecraft:copper_ingot',COPPER),COPPER)
        self.check('spirit_copper_not_plain_copper',not self.has_tag('myvillage:chitong_ingot',COPPER),COPPER)
        for item,tag in [('minecraft:iron_ingot',TAGS['tough']),('minecraft:copper_ingot',TAGS['channeling']),('minecraft:sand',TAGS['sharp'])]:
            self.check('ordinary_material_not_special_'+item,not self.has_tag(item,tag),tag)
        for m in materials():self.check('own_material_in_tag_'+m['id'],self.has_tag('myvillage:'+m['id'],m['tag']),m['tag'])
        self.check('ore_item_common_classification',self.has_tag('myvillage:xuantie_ore','c:ores'),'c:ores')
        self.rc('setblock 5 -60 3 myvillage:xuantie_ore')
        self.check('ore_block_common_classification','passed' in self.rc('execute if block 5 -60 3 #c:ores'),'c:ores')
        self.report['completed'].append('default_tags')
    def alias_infusion(self):
        u=self.s.username;self.rc(f'clear {u}');self.rc(f'give {u} minecraft:brick 2');self.fill_power()
        self.infusion();self.insert('minecraft:brick');self.click(120,137);self.close_menu()
        self.check('untagged_alias_refused',self.count('minecraft:brick')==2 and self.count('myvillage:chitong_ingot')==0 and not any(PROGRESS in s.get('components',{}) for s in self.stacks('minecraft:brick')),self.inventory())
        self.pack.mkdir(parents=True,exist_ok=True);(self.pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'Temporary forging tag evidence'}}))
        for key,values in ALIASES.items():self.tag(key,values)
        self.reload_for('minecraft:brick',COPPER,True)
        for key,items in ALIASES.items():self.check('datapack_alias_loaded_'+key,self.has_tag(items[0],key),items[0])
        cap=self.fill_power();self.infusion();self.insert('minecraft:brick');self.click(120,137)
        self.poll(lambda:self.profile()['power'],lambda n:n<=cap-4,8,'two alias pulses');self.click(220,137);self.capture('alias-copper-paused.png');self.close_menu()
        stack=self.stacks('minecraft:brick')[0];paid=stack.get('components',{}).get(PROGRESS,0)
        self.check('alternate_item_keeps_paid_progress',stack['count']==2 and 0<paid<6,stack)
        self.check('alternate_item_exact_cost',self.profile()['power']==cap-2*paid,{'paid':paid,'power':self.profile()['power']})
        self.report['completed'].append('alias_infusion')
        saved=self.inventory();self.rc('save-all flush');self.stop();self.start(reuse=True)
        self.check('alternate_item_restart',self.inventory()==saved,self.inventory())
        self.check('alias_datapack_restart',self.has_tag('minecraft:brick',COPPER),COPPER);self.report['completed'].append('alias_restart')
        self.tag(COPPER,[]);self.reload_for('minecraft:brick',COPPER,False)
        before=self.stacks('minecraft:brick');self.infusion();self.insert('minecraft:brick');self.click(120,137);self.close_menu()
        self.check('removed_tag_rejects_without_erasing_progress',self.stacks('minecraft:brick')==before and self.count('myvillage:chitong_ingot')==0,self.inventory())
        self.tag(COPPER,['minecraft:brick']);self.reload_for('minecraft:brick',COPPER,True)
        cap=self.fill_power();self.infusion();self.insert('minecraft:brick');self.click(120,137)
        total=json.loads((ROOT/'src/main/resources/data/myvillage/forging/acquisition.json').read_text())['infusion']['qi_per_ingot'];remaining=(total-paid)*2
        self.poll(lambda:self.profile()['power'],lambda n:n<=cap-remaining,12,'resumed alias completion')
        self.click(208,64,shift=True);self.close_menu()
        self.check('restored_tag_resumes_exact_output',self.count('minecraft:brick')==0 and self.count('myvillage:chitong_ingot')==2,self.inventory())
        self.check('restored_tag_only_remaining_qi',self.profile()['power']==cap-remaining,{'power':self.profile()['power'],'remaining':remaining})
        self.report['completed'].append('live_tag_reload')
    def forge(self,trait,extras,expected,clear=True,label=None):
        u=self.s.username
        if clear:self.rc(f'clear {u}')
        for item,n in self.rules['recipes'][0]['costs'].items():self.rc(f'give {u} {item} {n}')
        for item,n in extras.items():self.rc(f'give {u} {item} {n}')
        before=self.inventory();self.open_table();self.click(50,94+['sharp','channeling','tough'].index(trait)*22);self.capture((label or trait)+'-tag-costs.png');self.click(152,137);self.close_menu()
        after=self.inventory();products=[s for s in after.values() if s['id']=='myvillage:qingfeng_sword' and s.get('components',{}).get(MARK,{}).get('trait')==trait]
        if expected:
            self.check((label or trait)+'_tagged_weapon',len(products)==1,products)
            self.check((label or trait)+'_base_costs_consumed',all(self.count(item)==0 for item in self.rules['recipes'][0]['costs']),after)
            for item,n in extras.items():self.check((label or trait)+'_alias_consumed_'+item,self.count(item)==0,after)
        else:self.check((label or trait)+'_refused_without_consumption',after==before,after)
    def forging_cases(self):
        u=self.s.username
        # The learned-pattern helper uses slot 0; infused output is in the reverse-transfer hotbar slot 8.
        self.learn('qingfeng_sword');self.rc(f'myvillage cultivation crafting set {u} forging 0')
        self.forge('channeling',{},True,clear=False,label='infused_alias_to_weapon')
        self.check('infused_output_used_as_material_tag',self.count('myvillage:chitong_ingot')==1,self.inventory())
        self.forge('sharp',{'minecraft:quartz':1},True,label='overlapping_diamond_and_sand_tag')
        self.forge('tough',{'minecraft:amethyst_shard':1},True)
        self.forge('channeling',{'minecraft:emerald':1},True)
        self.report['completed'].append('tagged_forging')
        self.forge('sharp',{},False,label='same_diamonds_cannot_pay_two_costs');self.report['completed'].append('overlap_refusal')
        self.tag(TAGS['sharp'],[],replace=True);self.reload_for('minecraft:quartz',TAGS['sharp'],False)
        self.check('empty_tag_has_no_default_member',not self.has_tag('myvillage:edge_tempering_sand',TAGS['sharp']),TAGS['sharp'])
        self.forge('sharp',{'myvillage:edge_tempering_sand':1},False,label='empty_tag')
        self.report['completed'].append('empty_tag')
    def run(self):
        self.start();u=self.s.username
        for cmd in (*scene.world_rules(),*scene.player_setup(u,'minecraft:air'),'myvillage world pause','forceload add -16 -16 32 32',f'setblock {POS} myvillage:forging_table',f'setblock {PROBE} minecraft:barrel',f'myvillage cultivation awaken {u}'):
            self.rc(cmd)
        self.default_tags();self.alias_infusion();self.forging_cases();self.report['status']='pass'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'out/preview/forging_tags/runtime');a=p.parse_args();e=TagEvidence(a.out)
    try:e.run()
    except BaseException as error:e.report['status']='fail';e.report['failures'].append(repr(error));e.save();raise
    finally:
        if e.s:e.stop()
        e.save()
if __name__=='__main__':main()
