#!/usr/bin/env python3
"""锻器／炼灵拆分的局部映射输入证据；两种窗口尺寸，含回火与返回字形。"""
from pathlib import Path
import argparse,hashlib,sys,time
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from tools.physical_forging_evidence import PhysicalForgingEvidence,POS,blank
from tools.cultivation_demo_evidence import menu_point
from tools.combat_capture import session as cs
CASES=['anvil_ui','infusion_ui','copper_return']

class ForgingUiEvidence(PhysicalForgingEvidence):
 def verify_source(self):
  super().verify_source();self.report['collector_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
 def open(self):
  if self.anvil()['present']:return super().open()
  self.aim();self.right()
  self.poll(self.inspect,lambda d:d['open'],label='mapped ding right-click')
  self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='ding cursor')
 def sizes(self,name):
  game=self.s.game;window=game.xdo('search','--name','Minecraft').split()[0]
  game.xdo('mousemove','0','0')
  for w,h in [(960,540),(640,480)]:
   game.xdo('windowsize',window,str(w),str(h));game.size=(w,h);time.sleep(1);self.capture(f'{name}-{w}x{h}')
  game.xdo('windowsize',window,'960','540');game.size=(960,540);time.sleep(.5)
 def anvil_ui(self):
  self.clean();self.kit(blank('forged_pickaxe',2,12));self.put();self.open()
  data=self.inspect();self.check('anvil_rejects_infusion',data['station']=='anvil' and data['infuse_reason']==18,data)
  self.sizes('anvil-temper')
  self.click(262,130);self.poll(self.inspect,lambda d:d['input'].get('piece',{}).get('stage')==3,label='mapped temper advances')
  self.check('temper_consumes_one_fuel',self.count('minecraft:coal')==31,self.inventory())
  self.close_menu();self.take('myvillage:workpiece_forged_pickaxe_tempered')
  self.clean();self.kit('myvillage:forged_pickaxe[myvillage:temper={experience:6,edge:0,endurance:0,channeling:0,treatment:0}]');self.put();self.open()
  self.click(262,82);self.click(114,135);self.poll(self.inspect,lambda d:d['heat']>0,label='mapped reheat');self.close_menu();self.key('2');self.strike()
  self.check('rework_direction_still_controls_real_hammer',self.anvil()['input']['temper']['endurance']==1,self.anvil())
  self.take('myvillage:forged_pickaxe')
 def infusion_ui(self):
  self.clean();self.rc('setblock '+POS+' myvillage:forging_table[facing=north]')
  self.rc(f'clear {self.username}');self.rc(f'myvillage cultivation awaken {self.username}')
  self.rc(f'myvillage cultivation crafting set {self.username} forging 0')
  for item,n in [('myvillage:xuantie_chestplate',1),('myvillage:xuantie_ingot',4),('myvillage:low_grade_spirit_stone',8)]:self.give(item,n)
  self.open();self.insert('myvillage:xuantie_chestplate');data=self.inspect()
  self.check('ding_rejects_forging_actions',data['station']=='infusion' and data['start_reason']==18 and data['quench_reason']==18,data)
  self.sizes('infusion-armor');self.click(182,58);self.click(72,132)
  data=self.poll(self.inspect,lambda d:d['output']['count']==1,label='mapped infusion')
  self.check('dedicated_infusion_pays_exact_cost',data['output'].get('mark')=={'trait':'tough','quality':1} and self.count('myvillage:xuantie_ingot')==3 and self.count('myvillage:low_grade_spirit_stone')==6,data)
  self.click(112,106,True);self.check('new_output_slot_returns_one_item',self.count('myvillage:xuantie_chestplate')==1,self.inventory());self.close_menu()
  self.give('myvillage:qingfeng_sword');self.open();self.insert('myvillage:qingfeng_sword');self.sizes('infusion-weapon');self.close_menu()
 def copper_return(self):
  self.open();self.insert('myvillage:qingfeng_sword');self.click(72,219)
  self.poll(self.inspect,lambda d:not d['open'],label='copper submenu opens');self.sizes('copper-return')
  game=self.s.game;px,py=menu_point(game.size,248,15,width=304,height=238)
  self.guard(menu=True);game.xdo('mousemove',str(px),str(py));game.xdo('click','1');self.action('mapped_copper_back',x=px,y=py)
  data=self.poll(self.inspect,lambda d:d['open'],label='copper returns to infusion')
  self.check('return_targets_infusion_and_returns_private_item_once',data['station']=='infusion' and self.count('myvillage:qingfeng_sword')==1,data)
  self.close_menu()
  log=(cs.CLIENT_DIR/'logs/latest.log').read_text(errors='replace')
  errors=[line for line in log.splitlines() if any(k in line.lower() for k in ('failed to load font','couldn\'t load font','invalid font','failed to load provider','unable to load font'))]
  self.check('fonts_load_without_provider_errors',not errors,errors)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
 ForgingUiEvidence(a.out.resolve(),CASES).run()
if __name__=='__main__':main()
