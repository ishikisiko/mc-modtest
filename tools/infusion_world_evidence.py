#!/usr/bin/env python3
"""鼎外炼灵局部 Colab 证据。真实点击开炉/取物/拆鼎；命令仅 fixture、读回和竞争时钟控制。
正常速度录像单独记录；冻结竞争及保存探针不作为正常动画/玩家手感证据。
"""
from pathlib import Path
import argparse,hashlib,json,re,sys,time
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from tools.physical_forging_evidence import PhysicalForgingEvidence,POS
from tools.cultivation_demo_evidence import status_json
from tools.alchemy_quality_evidence import read_data,SecondClient
from tools.combat_capture import session as cs
CASES=['visual_flow','interruption','two_players']

class InfusionWorldEvidence(PhysicalForgingEvidence):
 def __init__(self,out,cases):
  super().__init__(out,cases);self.report.update(kind="world_equipment_infusion",method=__doc__);self.save()
 def verify_source(self):
  super().verify_source();self.report['collector_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
 def rc(self,command):
  if re.fullmatch(r'myvillage workshop ding -?\d+ -?\d+ -?\d+',command) or re.fullmatch(r'deop [A-Za-z0-9_]+',command) or command=='execute as @e[type=minecraft:item] run data get entity @s Item':
   r=self.s.run(command,warn=False)[0];self.report['commands'].append({'command':command,'reply':r});self.save();return r
  return super().rc(command)
 def ding(self):return status_json(self.rc('myvillage workshop ding '+POS),'WORKSHOP_DING ')
 def open(self):
  self.aim();self.right();self.poll(self.inspect,lambda d:d['open'],label='normal mapped ding use');self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='ding cursor')
 def setup(self,rid='qingfeng_sword',materials=True):
  if self.inspect()['open']:self.close_menu()
  current=self.ding()
  assert not current.get('occupied',False),current
  self.rc('setblock '+POS+' myvillage:forging_table[facing=north]')
  self.rc(f'gamemode survival {self.username}');self.rc(f'clear {self.username}')
  self.rc(f'myvillage cultivation awaken {self.username}');self.rc(f'myvillage cultivation crafting set {self.username} forging 0')
  self.give('myvillage:'+rid+'[minecraft:custom_name=\'{"text":"留名旧器"}\',minecraft:damage=3,myvillage:temper={experience:14,edge:0,endurance:1,channeling:0,treatment:2}]')
  if materials:
   self.give('myvillage:xuantie_ingot',4);self.give('myvillage:low_grade_spirit_stone',8)
  self.open();self.insert('myvillage:'+rid)
  self.click(182,82 if rid=='qingfeng_sword' else 58)
  return self.inspect()['input']
 def begin(self):
  # Scene viewpoint changes while the GUI is open; the actual start is its mapped button.
  self.rc(f'tp {self.username} .5 -60 .5 0 3')
  self.click(72,132,wait=.04)
  self.poll(self.s.game.pointer_grabbed,lambda v:v is True,label='start automatically closes menu')
  data=self.ding();self.check('start_transfers_workpiece_to_visible_ding',data['occupied'] and not self.inspect()['open'],data)
  return data
 def take(self,rid):
  self.aim();self.right();data=self.poll(self.ding,lambda d:not d['occupied'],label='owner right-click takes finished equipment')
  self.check('one_finished_'+rid+'_returned',self.count('myvillage:'+rid)==1,data)
 def drops(self):
  reply=self.rc('execute as @e[type=minecraft:item] run data get entity @s Item')
  return [read_data(line) for line in reply.splitlines() if 'entity data:' in line]
 def visual_flow(self):
  before=self.setup(materials=False);inventory=self.inventory();self.click(72,132)
  self.check('missing_materials_do_not_start_or_lose_input',not self.ding()['occupied'] and self.inspect()['input']==before and self.inventory()==inventory,self.inspect());self.close_menu()
  for rid in ['qingfeng_sword','xuantie_chestplate']:
   original=self.setup(rid)
   self.start_video(rid+'-world-infusion-normal-speed');data=self.begin()
   self.check('begin_reserves_exact_material_cost',self.count('myvillage:xuantie_ingot')==3 and self.count('myvillage:low_grade_spirit_stone')==6 and data['input']==original and data['output']['count']==0,data)
   for frame in range(3):time.sleep(.38);self.capture(rid+'-phase-'+str(frame))
   finished=self.poll(self.ding,lambda d:not d['running'] and d['output']['count']==1,10,'world job finishes at normal tick rate')
   self.capture(rid+'-finished');self.stop_video()
   self.check('finished_preserves_metadata_and_consumes_escrow',finished['input']['count']==0 and not finished['reserved'] and finished['output']['mark']=={'trait':'tough','quality':1} and finished['output']['temper']==original['temper'] and finished['output']['damage']==original['damage'],finished)
   self.take(rid)
  log=(cs.CLIENT_DIR/'logs/latest.log').read_text(errors='replace')
  errors=[line for line in log.splitlines() if any(s in line.lower() for s in ('failed to load texture','missing texture')) and 'infusion_' in line]
  self.check('infusion_textures_loaded',not errors,errors)
 def interruption(self):
  original=self.setup('forged_pickaxe');self.rc('kill @e[type=minecraft:item]');self.rc('tick freeze')
  try:
   pending=self.begin();raw=read_data(self.rc('data get block '+POS+' Infusion'))
   self.rc(f'gamemode creative {self.username}');self.aim();self.hold([],.12,mouse=1)
   self.poll(self.ding,lambda d:not d['present'],label='mapped attack breaks unfinished ding')
   drops=self.drops();expected=[raw['Input'],*raw['Reserved']]
   actual=[r for r in drops if r['id']!='myvillage:forging_table']
   self.check('unfinished_break_refunds_exact_escrow_not_frozen_result',sorted(actual,key=lambda r:r['id'])==sorted(expected,key=lambda r:r['id']),{'actual':actual,'expected':expected})
  finally:self.rc('tick unfreeze')
  self.rc('kill @e[type=minecraft:item]')
  self.setup('forged_pickaxe');self.begin();self.poll(self.ding,lambda d:d['output']['count']==1,label='finish before persistence check')
  saved=read_data(self.rc('data get block '+POS+' Infusion'));inventory=self.inventory()
  self.rc('save-all flush');self.stop();self.start(reuse=True)
  self.check('finished_ding_survives_restart_exactly_once',read_data(self.rc('data get block '+POS+' Infusion'))==saved and self.inventory()==inventory,self.ding())
  self.take('forged_pickaxe')
 def two_players(self):
  self.rc('setblock '+POS+' myvillage:forging_table[facing=north]')
  with SecondClient(self,timeout=360) as guest:
   self.guest=guest;self.attach_window(guest.game);u=guest.username
   self.poll(lambda:status_json(self.rc('myvillage qianzhen realm_status '+u),'QIANZHEN_REALM_STATUS '),lambda d:not d['building'] and not d['waiting'],150,'guest opening ready')
   for command in (f'op {u}',f'gamemode survival {u}',f'execute in minecraft:overworld run tp {u} 2.5 -60 .5 33.69 20.5',f'clear {u}',f'effect give {u} minecraft:saturation infinite 255 true',f'deop {u}'):
    self.rc(command)
   self.poll(guest.game.pointer_grabbed,lambda v:v is True,60,'guest input ready')
   self.setup('qingfeng_sword');self.rc('tick freeze')
   try:
    pending=self.begin();self.right(guest.game)
    self.check('observer_cannot_open_or_take_running_job',self.ding()==pending and not self.inspect(u)['open'] and self.count('myvillage:qingfeng_sword',u)==0,self.ding())
   finally:self.rc('tick unfreeze')
   self.rc(f'tp {u} 2.5 -60 .5 33.69 3');time.sleep(.35);self.capture('observer-sees-world-infusion',guest.game)
   finished=self.poll(self.ding,lambda d:d['output']['count']==1,label='shared display completes')
   self.aim(guest.game,u);self.right(guest.game)
   self.check('non_operator_observer_cannot_steal_finished_item',self.ding()==finished and self.count('myvillage:qingfeng_sword',u)==0,self.ding())
   self.take('qingfeng_sword');self.check('owner_pickup_clears_shared_display_once',not self.ding()['occupied'] and self.count('myvillage:qingfeng_sword')==1 and self.count('myvillage:qingfeng_sword',u)==0,self.ding())
   self.capture('observer-sees-empty-ding-after-pickup',guest.game)
  self.guest=None;self.report['multiplayer']='two_native_clients_observed_and_owner_only_pickup';self.save()

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--cases',default=','.join(CASES));a=p.parse_args();cases=a.cases.split(',')
 if not cases or len(set(cases))!=len(cases) or any(c not in CASES for c in cases):p.error('invalid cases')
 InfusionWorldEvidence(a.out.resolve(),cases).run()
if __name__=='__main__':main()
