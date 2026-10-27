#!/usr/bin/env python3
"""实体放坯／手持锻锤的局部 Colab 证据。

放置、取回、敲击、开面板和拆台全部使用实际客户端鼠标／按键。
命令只准备独立测试世界、材料、历练边界，读取状态及冻结竞争测试的时钟。
热处理和装配继续用原面板。未验证玩家手感、音效偏好、FPS或锻锤等阶。
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import secrets
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from tools.armor_forging_evidence import ArmorForgingEvidence
from tools.cultivation_demo_evidence import status_json
from tools.alchemy_quality_evidence import read_data,SecondClient
from tools.combat_capture import procs,scene,session as cs
from tools.forging_models_evidence import model_load_errors

CASES=['physical_loop','training','lifecycle','two_players']
POS='0 -60 3';TROUGH='1 -60 3'


def blank(recipe,stage=0,blows=0):
 name=['rough','shaped','quenched','tempered','ground'][stage]
 treatment=2 if recipe.startswith('xuantie_') else 0
 return f'myvillage:workpiece_{recipe}_{name}[myvillage:workpiece={{recipe:"{recipe}",stage:{stage},blows:{blows},treatment:{treatment}}},minecraft:custom_name=\'{{"text":"留台试作"}}\']'


def physical_readback(command):
 return bool(re.fullmatch(r'myvillage workshop (?:anvil -?\d+ -?\d+ -?\d+|inspect [A-Za-z0-9_]+)',command)
             or re.fullmatch(r'execute as @e\[type=minecraft:item,nbt=\{Item:\{id:"myvillage:[a-z0-9_]+"\}\}\] run data get entity @s Item',command))


class PhysicalForgingEvidence(ArmorForgingEvidence):
 def verify_source(self):
  super().verify_source();self.report['collector_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
 def __init__(self,out,cases):
  super().__init__(out,cases);self.report.update(kind='physical_forging',method=__doc__);self.save()
 def rc(self,command):
  if physical_readback(command):
   r=self.s.run(command,warn=False)[0];self.report['commands'].append({'command':command,'reply':r});self.save();return r
  return super().rc(command)
 def start(self,reuse=False):
  # Unlike the armor appearance fixture, restart must preserve the player's inventory.
  cs.merge_options(cs.CLIENT_DIR/'options.txt',{'rawMouseInput':'false','maxFps':'30','fov':'0.0',
      'key_key.use':'key.mouse.right','key_key.togglePerspective':'key.keyboard.f5','hideGui':'false'})
  state=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),username=self.username,language='zh_cn',
      reuse_world=reuse,prebuilt_resources=True,lock=str(procs.default_lock_path(ROOT)),
      gradle_jvmargs='-Xmx2g',view_distance=3,world_seed=20261027))
  self.s=cs.Session(state,ui_check=True);self.sessions+=1
  self.report['phases'].append({'phase':'start','timings':state.get('timings'),'session_id':state['session_id'],'reuse':reuse})
  self.attach_window();self.poll(self.s.game.pointer_grabbed,lambda v:v is True,label='actual game pointer')
  self.rc('tick unfreeze')
  if not reuse:
   self.poll(lambda:status_json(self.rc('myvillage qianzhen realm_status '+self.username),'QIANZHEN_REALM_STATUS '),
             lambda s:not s['building'] and not s['waiting'],150,'initial personal realm setup')
   for command in (*scene.world_rules(),'myvillage world pause',f'op {self.username}',f'gamemode survival {self.username}',
       f'execute in minecraft:overworld run tp {self.username} .5 -60 .5 0 0','forceload add -16 -16 16 16',
       'fill -12 -61 -12 12 -61 12 minecraft:smooth_stone',f'clear {self.username}',f'effect clear {self.username}',
       f'effect give {self.username} minecraft:saturation infinite 255 true'):
    self.rc(command)
  self.poll(lambda:self.pos(),lambda p:abs(p[1]+60)<.05,label='fixture settled')
  time.sleep(1);self.poll(self.s.game.pointer_grabbed,lambda v:v is True,90,'dimension screen closed');self.save()
 def anvil(self):return status_json(self.rc('myvillage workshop anvil '+POS),'WORKSHOP_ANVIL ')
 def stored(self):return read_data(self.rc('data get block '+POS+' Items'))
 def inspect(self,user=None):return status_json(self.rc('myvillage workshop inspect '+(user or self.username)),'WORKSHOP ')
 def aim(self,game=None,user=None):
  if user and user!=self.username:self.rc(f'tp {user} 2.5 -60 .5 33.69 20.5')
  else:self.rc(f'tp {self.username} .5 -60 .5 0 20.5')
  time.sleep(.4)
 def open(self):
  self.aim();self.guard();game=self.s.game
  # Let the client send its crouch transition before the subsequent use-on-block packet.
  game.xdo('keydown','Shift_L')
  try:
   time.sleep(.5);game.xdo('click','3')
   self.poll(self.inspect,lambda d:d['open'],label='sneak right-click panel')
  finally:game.xdo('keyup','Shift_L')
  self.action('mapped_sneak_use',keys=['Shift_L'],mouse=3,crouch_lead_seconds=.5)
  self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='panel cursor')
 def heat_and_leave(self):
  self.open();self.operate('heat');self.close_menu()
  self.poll(self.anvil,lambda d:d['heat']>0,label='heat retained after panel close')
 def clean(self):
  if self.inspect()['open']:self.close_menu()
  previous=self.anvil()
  if previous['present']:
   assert previous['input']['count']==0 and previous['output']['count']==0,previous
  self.rc('setblock '+POS+' myvillage:forging_anvil[facing=north]')
  self.rc('setblock '+TROUGH+' myvillage:quenching_trough[facing=north,filled=true]')
  self.rc(f'gamemode survival {self.username}');self.rc(f'clear {self.username}');self.aim()
 def kit(self,item):
  self.rc(f'item replace entity {self.username} hotbar.0 with {item}')
  self.rc(f'item replace entity {self.username} hotbar.1 with myvillage:forging_hammer')
  for slot,(name,count) in enumerate([('coal',32),('flint',16),('iron_nugget',16),('iron_ingot',32),('stick',16)],2):
   self.rc(f'item replace entity {self.username} hotbar.{slot} with minecraft:{name} {count}')
 def put(self,slot='1'):
  self.key(slot);before=self.inspect()['held'];self.right()
  data=self.poll(self.anvil,lambda d:d['input']['id']==before['id'],label='right-click places real item')
  self.check('placement_keeps_world_input',self.s.game.pointer_grabbed() is True and not self.inspect()['open'],data)
  self.check('placement_moves_one_item',data['input']['count']==1 and self.count(before['id'])==0,self.inventory())
 def take(self,expected):
  self.key('9');self.right();self.poll(self.anvil,lambda d:d['input']['count']==0 and d['output']['count']==0,label='empty hand retrieves item')
  self.check('right_click_returns_exactly_one_'+expected,self.count(expected)==1 and self.inspect()['held']['id']==expected,self.inventory())
 def strike(self):
  before=self.anvil()
  if before['heat']<=self.config['strike_ticks']*2:self.heat_and_leave();before=self.anvil()
  self.right()
  after=self.poll(self.anvil,lambda d:d['hits']==before['hits']+1,6,'actual hammer contact')
  self.poll(self.anvil,lambda d:not d['swinging'],6,'hammer cycle ends')
  return before,after
 def physical_loop(self):
  self.clean();self.kit(blank('lingxiao_spear'));self.open();self.click(45,76);self.operate('start');self.close_menu()
  self.check('panel_preparation_creates_one_real_table_piece',self.anvil()['input']['id']=='myvillage:workpiece_forged_pickaxe_rough' and self.count('myvillage:workpiece_forged_pickaxe_rough')==0,self.anvil())
  self.take('myvillage:workpiece_forged_pickaxe_rough')
  self.clean();self.kit(blank('lingxiao_spear'));self.put();initial=self.stored()
  self.key('2');self.right();time.sleep(.3)
  self.check('cold_blank_does_not_advance_or_wear_hammer',self.anvil()['input']['piece']['blows']==0 and self.inspect()['held']['damage']==0,self.anvil())
  self.open();before=self.inventory();self.click(102,112)
  self.check('panel_cannot_pick_up_shared_item',self.stored()==initial and self.inventory()==before,self.stored())
  self.operate('heat');self.close_menu()
  self.check('closing_panel_leaves_the_same_hot_piece',self.stored()==initial and self.anvil()['heat']>0,self.anvil())
  self.start_video('right-click-forging')
  self.key('2');_,after=self.strike()
  self.check('one_right_click_one_hit_and_one_wear',after['input']['piece']['blows']==1 and self.inspect()['held']['damage']==1,after)
  self.capture('first-physical-hit')
  while self.anvil()['input']['piece']['blows']<self.config['forming_strikes']:self.strike()
  self.stop_video();data=self.anvil();damage=self.inspect()['held']['damage']
  self.check('full_forming_uses_handheld_hammer',data['input']['piece']['blows']==self.config['forming_strikes'] and damage==self.config['forming_strikes'],data)
  self.right();time.sleep(.3)
  self.check('extra_hit_after_forming_is_rejected',self.anvil()['input']==data['input'] and self.inspect()['held']['damage']==damage,self.anvil())
  self.capture('formed-on-anvil-no-menu')
  self.take('myvillage:workpiece_lingxiao_spear_shaped');self.put('9')
  self.open();self.operate('heat');self.operate('quench');self.operate('temper');self.operate('grind');self.operate('assemble')
  result=self.inspect()['output'];self.close_menu()
  self.check('other_operations_still_finish_and_leave_output_on_table',result['id']=='myvillage:lingxiao_spear' and self.anvil()['output']==result and self.count(result['id'])==0,self.anvil())
  self.capture('finished-on-anvil-no-menu');self.take(result['id'])
 def training(self):
  for rid in ['forged_pickaxe','xuantie_chestplate']:
   self.clean()
   self.kit('myvillage:'+rid+'[myvillage:temper={experience:6,edge:0,endurance:0,channeling:0,treatment:2},myvillage:forge_mark={trait:"tough",quality:2},minecraft:custom_name=\'{"text":"旧器保留"}\',minecraft:damage=3]')
   self.put();self.open()
   if rid=='forged_pickaxe':self.click(262,75)  # endurance row in the existing panel
   self.operate('heat');self.close_menu();self.key('2')
   before=self.stored()[0]['components'];nuggets=self.count('minecraft:iron_nugget');_,after=self.strike()
   record=after['input']['temper'];raw=self.stored()[0]['components']
   self.check(rid+'_physical_training_spends_exact_cost',record=={'experience':0,'edge':0,'endurance':1,'channeling':0,'treatment':2} and self.count('minecraft:iron_nugget')==nuggets-1,after)
   self.check(rid+'_training_preserves_old_components',all(raw[k]==v for k,v in before.items() if k not in ('myvillage:temper','minecraft:max_damage','minecraft:attribute_modifiers')),raw)
   self.right();time.sleep(.25)
   self.check(rid+'_no_experience_no_extra_hit_or_wear',self.anvil()['input']==after['input'] and self.inspect()['held']['damage']==1 and self.count('minecraft:iron_nugget')==nuggets-1,self.anvil())
   self.take('myvillage:'+rid)
 def lifecycle(self):
  self.clean();self.kit(blank('lingxiao_spear',0,2));self.put();self.key('2');self.strike()
  before=self.stored();inventory=self.inventory();heat=self.anvil()['heat']
  self.rc('save-all flush');self.stop();self.start(reuse=True)
  self.check('anvil_and_inventory_survive_restart_without_duplication',self.stored()==before and self.inventory()==inventory,{'anvil':self.stored(),'inventory':self.inventory()})
  self.check('restart_does_not_reheat_or_replay_a_swing',0<=self.anvil()['heat']<=heat and not self.anvil()['swinging'] and self.anvil()['hits']==0,self.anvil())
  self.capture('persistent-workpiece-after-restart')
  rid='myvillage:workpiece_lingxiao_spear_rough';self.take(rid)
  returned=dict(self.inventory()[8]);returned.pop('Slot');saved=dict(before[0]);saved.pop('Slot')
  self.check('pickup_keeps_complete_saved_stack',returned==saved,returned);self.put('9')
  self.rc(f'gamemode creative {self.username}');self.hold([], .12,mouse=1)
  self.poll(self.anvil,lambda d:not d['present'],label='mapped attack removes anvil')
  replies=self.rc('execute as @e[type=minecraft:item,nbt={Item:{id:"'+rid+'"}}] run data get entity @s Item')
  dropped=[read_data(line) for line in replies.splitlines() if 'entity data:' in line]
  self.check('breaking_anvil_drops_the_saved_piece_exactly_once',len(dropped)==1 and dropped[0]==saved and self.count(rid)==0,dropped)
  self.rc('kill @e[type=minecraft:item]');self.rc(f'gamemode survival {self.username}')
 def two_players(self):
  self.clean();self.kit(blank('lingxiao_spear'));self.put();owner=self.anvil()['input']
  with SecondClient(self,timeout=360) as guest:
   self.guest=guest;self.attach_window(guest.game);u=guest.username
   self.poll(lambda:status_json(self.rc('myvillage qianzhen realm_status '+u),'QIANZHEN_REALM_STATUS '),lambda d:not d['building'] and not d['waiting'],150,'guest opening ready')
   for command in (f'op {u}',f'gamemode survival {u}',f'execute in minecraft:overworld run tp {u} 2.5 -60 .5 33.69 20.5',f'clear {u}',
       f'effect give {u} minecraft:saturation infinite 255 true',f'item replace entity {u} hotbar.1 with myvillage:forging_hammer'):
    self.rc(command)
   time.sleep(1);self.poll(guest.game.pointer_grabbed,lambda v:v is True,60,'guest native input ready')
   self.open();self.key('9',guest.game);self.right(guest.game)
   self.check('menu_occupancy_blocks_other_player_pickup',self.anvil()['input']==owner and self.count(owner['id'],u)==0 and not self.inspect(u)['open'],self.anvil())
   self.key('2',guest.game);self.right(guest.game)
   self.check('menu_occupancy_blocks_other_hammer',self.anvil()['input']==owner and self.inspect(u)['held']['damage']==0,self.anvil())
   self.operate('heat');self.close_menu();self.key('2')
   before=self.anvil()
   self.rc('tick freeze')
   try:
    self.right();self.poll(self.anvil,lambda d:d['swinging'],label='first hammer reserved frozen contact')
    self.right(guest.game)
    self.check('contending_hammers_cannot_commit_before_contact',self.anvil()['hits']==before['hits'],self.anvil())
   finally:self.rc('tick unfreeze')
   data=self.poll(self.anvil,lambda d:d['hits']==before['hits']+1 and not d['swinging'],6,'one contact after contention')
   wear=self.inspect()['held']['damage']+self.inspect(u)['held']['damage']
   self.check('two_hammers_one_piece_one_paid_contact',data['input']['piece']['blows']==1 and wear==1,{'anvil':data,'total_hammer_wear':wear})
   self.rc(f'gamemode spectator {u}');self.rc(f'tp {u} 2.8 -59.3 1.2 40 28');time.sleep(.5)
   self.capture('second-player-sees-real-workpiece-and-held-hammer',guest.game)
   self.rc(f'gamemode survival {u}');self.aim(guest.game,u);self.key('9');self.key('9',guest.game)
   self.rc('tick freeze')
   try:self.right();self.right(guest.game)
   finally:self.rc('tick unfreeze')
   rid=owner['id'];counts=[self.count(rid),self.count(rid,u)];data=self.anvil()
   self.check('two_pickups_return_one_shared_item_total',sum(counts)==1 and data['input']['count']==0 and data['output']['count']==0,{'counts':counts,'anvil':data})
   self.report['multiplayer']='two_native_clients_shared_item_and_hammer_contention_pass';self.save()
  self.guest=None
  logs=[cs.CLIENT_DIR/'logs/latest.log',self.out/'second-client/client.log'];bad=[]
  for log in logs:
   text=log.read_text(errors='replace');bad.extend(model_load_errors(text))
   bad.extend(line for line in text.splitlines() if 'forging_hammer' in line and 'missing texture' in line.lower())
  self.check('no_forging_model_or_texture_load_errors',not bad,bad)

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--cases',default=','.join(CASES));a=p.parse_args()
 cases=a.cases.split(',')
 if not cases or len(set(cases))!=len(cases) or any(c not in CASES for c in cases):p.error('invalid cases')
 PhysicalForgingEvidence(a.out,cases).run()
