#!/usr/bin/env python3
"""立体炼器资源的局部 Colab 显示核对。实际按键打开背包、切换持物、查看工坊并重载。
命令仅布置独立测试世界与物品、读取状态；不重跑未改变的炼器玩法。截图不是玩家外观或手感验收。
"""
from pathlib import Path
import argparse
import hashlib
import re
import time
import sys
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from tools.armor_forging_evidence import ArmorForgingEvidence
from tools.cultivation_demo_evidence import menu_point
from tools.combat_capture import session as cs

CASES=['inventory_models','held_models','workshop_models','reload_models']
TOOLS=['qingfeng_sword','lingxiao_spear','forged_pickaxe','forged_axe','forged_shovel','forged_hoe']
ARMOR=['xuantie_helmet','xuantie_chestplate','xuantie_leggings','xuantie_boots']
STAGES=['rough','shaped','quenched','tempered','ground']

def spec(rid,stage):
 if stage==5:return 'myvillage:'+rid
 return f'myvillage:workpiece_{rid}_{STAGES[stage]}[myvillage:workpiece={{recipe:"{rid}",stage:{stage},blows:{0 if stage==0 else 12},treatment:{2 if rid in ARMOR else 0}}}]'

def fixture_slot(slot):return 'hotbar.'+str(slot) if slot<9 else 'inventory.'+str(slot-9)

def model_load_errors(text):
 lines=text.splitlines();bad=[]
 for i,line in enumerate(lines):
  if any(s in line.lower() for s in ('unable to load model','exception loading model','failed to load model','using missing texture','missing textures in model','unable to resolve texture','error loading')):
   context='\n'.join(lines[i:i+8])
   if any(s in context for s in ('equipment/','workshop/','xuantie_','forged_','workpiece_','qingfeng','lingxiao')):bad.append(context)
 return bad

class ForgingModelsEvidence(ArmorForgingEvidence):
 def verify_source(self):
  super().verify_source();self.report['collector_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
 def __init__(self,out):
  super().__init__(out,CASES.copy());self.report.update(kind='forging_models',method=__doc__);self.save()
 def inventory_models(self):
  slots=list(range(9,36))+list(range(9))
  for page,recipes in [('weapons-tools',TOOLS),('armor',ARMOR)]:
   self.rc(f'clear {self.username}');expected={}
   for slot,(rid,stage) in zip(slots,[(r,s) for r in recipes for s in range(6)]):
    item=spec(rid,stage);self.rc(f'item replace entity {self.username} {fixture_slot(slot)} with {item}')
    expected[slot]=item.split('[')[0]
   rows=self.inventory();self.check(page+'_registry_items_present',{s:v['id'] for s,v in rows.items()}==expected,rows)
   self.key('e');self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='inventory gallery')
   time.sleep(1);self.capture(page+'-inventory');self.close_menu()
 def held_models(self):
  self.rc(f'clear {self.username}');self.rc(f'tp {self.username} .5 -60 .5 0 0')
  for i,rid in enumerate(TOOLS):self.rc(f'item replace entity {self.username} hotbar.{i} with myvillage:{rid}')
  self.start_video('held-models')
  for i,rid in enumerate(TOOLS):
   self.key(str(i+1));time.sleep(.5)
   self.check('mapped_selection_'+rid,self.inspect()['held']['id']=='myvillage:'+rid,self.inspect()['held'])
   self.capture(rid+'-first');self.key('F5');self.key('F5');time.sleep(.4);self.capture(rid+'-third');self.key('F5')
   self.hold([], .2, mouse=1)
  self.stop_video()
 def workshop_models(self):
  self.rc('setblock 0 -60 3 myvillage:forging_anvil[facing=north]')
  for rid,stage in [('lingxiao_spear',1),('forged_pickaxe',4),('xuantie_chestplate',1)]:
   self.rc(f'clear {self.username}');self.rc(f'item replace entity {self.username} hotbar.0 with {spec(rid,stage)}');self.give('minecraft:coal',4)
   self.open();self.move_slot(0)
   expected='myvillage:workpiece_'+rid+'_'+STAGES[stage]
   data=self.poll(self.inspect,lambda d:d['input']['id']==expected,label='mapped workpiece insertion')
   self.check('workshop_model_'+rid,data['recipe']==rid and data['input']['piece']['stage']==stage,data)
   time.sleep(.5);self.capture(rid+'-workshop')
   if stage==1:self.operate('heat');self.capture(rid+'-heated')
   self.close_menu();self.capture(rid+'-world-bench')
 def reload_models(self):
  before=self.inventory();log=cs.CLIENT_DIR/'logs/latest.log';offset=log.stat().st_size
  self.guard();self.s.game.xdo('keydown','F3')
  try:self.s.game.xdo('key','t')
  finally:self.s.game.xdo('keyup','F3')
  self.action('mapped_resource_reload',keys=['F3','t'])
  def tail():
   with log.open('rb') as f:f.seek(offset);return f.read().decode(errors='replace')
  self.poll(tail,lambda text:'EQUIPMENT_MODELS loaded 8 item/profile models' in text,60,'resource reload complete');time.sleep(3)
  self.poll(self.s.game.pointer_grabbed,lambda v:v is True,label='input after resource reload')
  self.key('e');self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='inventory after reload');self.capture('after-reload');self.close_menu()
  self.check('reload_keeps_inventory',self.inventory()==before,self.inventory())
  bad=model_load_errors(log.read_text(errors='replace'))
  self.check('no_equipment_model_or_texture_load_errors',not bad,bad)
  (self.out/'client-model-log.txt').write_text(log.read_text(errors='replace'))

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();ForgingModelsEvidence(a.out).run()
