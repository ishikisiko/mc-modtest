#!/usr/bin/env python3
"""Mapped vanilla crafting -> new forging additives -> actual specialized weapon results."""
import argparse
from pathlib import Path
import sys
import time
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.forging_evidence import ForgingEvidence,MARK,POS
from tools.alchemy_quality_evidence import read_data
from tools.combat_capture import scene
from tools.forging.materials import load

class MaterialEvidence(ForgingEvidence):
    def __init__(self,out):
        super().__init__(out);self.materials=load();self.book=False
        self.report.update(kind='forging_materials',selected=['mapped_crafting','recipe_discovery','mapped_additives','wrong_additive'],completed=[])
    def work_click(self,x,y,button=1,shift=False):
        g=self.s.game;w,h=g.size;scale=max(1,min(w//320,h//240));gw,gh=w//scale,h//scale
        left=177+(gw-176-200)//2 if self.book and gw>=379 else (gw-176)//2;top=(gh-166)//2
        g.xdo('mousemove',str(scale*(left+x)),str(scale*(top+y)))
        if shift:g.xdo('keydown','Shift_L')
        try:g.xdo('click',str(button))
        finally:
            if shift:g.xdo('keyup','Shift_L')
        time.sleep(.15)
    def workbench(self):
        self.rc(f'tp {self.s.username} 2.5 -60 .5 0 20.5');time.sleep(.4);self.s.game.require_ingame('open crafting table');self.s.game.xdo('click','3')
        self.poll(self.s.game.pointer_grabbed,lambda b:b is False,8,'mapped crafting table')
        self.book=bool(read_data(self.rc(f'data get entity {self.s.username} recipeBook.isGuiOpen')))
    def craft(self,m):
        u=self.s.username;rid='myvillage:'+m['id'];before=self.count(rid)
        for slot,(item,count) in enumerate(m['ingredients'].items()):self.rc(f'item replace entity {u} hotbar.{slot} with {item} {count}')
        self.s.game.key('6');self.workbench();grid=0
        for slot,(item,count) in enumerate(m['ingredients'].items()):
            if item=='myvillage:low_grade_spirit_stone':
                self.work_click(132,43,shift=True)
                self.check('no_output_without_spirit_'+m['id'],self.count(rid)==before,self.inventory())
            self.work_click(16+slot*18,150)
            for _ in range(count):
                self.work_click(38+(grid%3)*18,25+(grid//3)*18,button=3);grid+=1
        self.capture(m['id']+'-crafting.png');self.work_click(132,43,shift=True)
        self.poll(lambda:self.count(rid),lambda n:n==before+m['output_count'],8,'mapped material crafting')
        self.work_click(132,43,shift=True);self.close_menu()
        self.check('crafts_once_'+m['id'],self.count(rid)==before+m['output_count'],self.inventory())
        self.check('raw_inputs_consumed_'+m['id'],all(self.count(i)==0 for i in m['ingredients']),self.inventory())
        recipes=read_data(self.rc(f'data get entity {u} recipeBook.recipes'))
        self.check('recipe_discovered_'+m['id'],rid in recipes,rid)
    def forge_material(self,m):
        u=self.s.username;recipe=self.rules['recipes'][0];rid='myvillage:'+m['id'];trait=m['trait']
        for item,n in recipe['costs'].items():self.rc(f'give {u} {item} {n}')
        before={s['id']:self.count(s['id']) for s in self.inventory().values()};weapon_before=self.count(recipe['output'])
        self.open_table();self.click(50,94+['sharp','channeling','tough'].index(trait)*22);self.capture(trait+'-ready.png');self.click(152,137);self.close_menu()
        products=[s for s in self.inventory().values() if s['id']==recipe['output'] and s.get('components',{}).get(MARK,{}).get('trait')==trait]
        self.check('crafted_material_produces_'+trait,len(products)==1 and products[0]['components'][MARK]['quality']==1 and self.count(recipe['output'])==weapon_before+1,products)
        self.check('only_selected_additive_consumed_'+trait,self.count(rid)==before[rid]-1 and all(self.count('myvillage:'+other['id'])==before.get('myvillage:'+other['id'],0) for other in self.materials if other['id']!=m['id']),self.inventory())
    def run(self):
        self.start();u=self.s.username
        for c in (*scene.world_rules(),*scene.player_setup(u,'minecraft:air'),'myvillage world pause','forceload add -16 -16 32 32',f'setblock {POS} myvillage:forging_table','setblock 2 -60 3 minecraft:crafting_table'):
            self.rc(c)
        self.learn('qingfeng_sword');self.rc(f'myvillage cultivation awaken {u}');self.rc(f'myvillage cultivation crafting set {u} forging 0');self.rc(f'clear {u}')
        for m in self.materials:self.craft(m)
        self.report['completed']+=['mapped_crafting','recipe_discovery']
        # The inventory contains only the actually crafted materials; none were supplied by /give.
        self.s.game.key('e');time.sleep(.4);self.capture('crafted-materials-inventory.png');self.close_menu()
        for m in self.materials:self.forge_material(m)
        self.report['completed'].append('mapped_additives')
        self.rc(f'clear {u}')
        for item,n in self.rules['recipes'][0]['costs'].items():self.rc(f'give {u} {item} {n}')
        self.rc(f'give {u} minecraft:quartz 2');before=self.inventory();self.open_table();self.click(152,137);self.capture('raw-quartz-rejected.png');self.close_menu()
        self.check('raw_quartz_no_longer_bypasses_processed_additive',self.inventory()==before,self.inventory())
        self.report['completed'].append('wrong_additive');self.report['status']='pass'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'out/preview/forging_materials/runtime');args=p.parse_args();e=MaterialEvidence(args.out)
    try:e.run()
    except BaseException as error:e.report['status']='fail';e.report['failures'].append(repr(error));e.save();raise
    finally:
        if e.s:e.stop()
        e.save()
if __name__=='__main__':main()
