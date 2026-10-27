#!/usr/bin/env python3
"""玄铁甲局部实机证据：真实鼠标锻造、注灵、穿戴、近战历练、再锻和维修。

仅在固定源 Colab 运行；命令只准备独立世界 fixture 和读取状态，不替代菜单操作。
不会释放用户要求保留的 VM。未覆盖玩家手感、声音、FPS、多人和长期平衡。
"""
from pathlib import Path
import argparse
import hashlib
import json
import sys
import time
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.equipment_evidence import EquipmentEvidence
from tools.cultivation_demo_evidence import status_json,menu_point
from tools.forging_workshop_evidence import WorkshopEvidence,POINTS,POS,TROUGH,stack_spec

CASES=('stages','infusion','experience','training_repair','array')
SLOTS=('helmet','chestplate','leggings','boots')

class ArmorForgingEvidence(EquipmentEvidence):
    def __init__(self,out,cases):
        super().__init__(out,cases)
        self.config=json.loads((ROOT/'src/main/resources/data/myvillage/forging/workshop.json').read_text())
        self.report.update(kind='armor_forging',mapped_input=[],owner_feel='not_verified',owner_sound='not_verified',owner_fps='not_verified')
        self.saved={}
    def verify_source(self):
        super().verify_source()
        self.report['collector_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    def rc(self,command):
        if command in ('myvillage workshop inspect '+self.username,'difficulty normal','myvillage forgingarray inspect 0 -59 8'):
            reply=self.s.run(command,warn=False)[0];self.report['commands'].append({'command':command,'reply':reply});self.save();return reply
        return super().rc(command)
    def inspect(self,user=None):return status_json(self.rc('myvillage workshop inspect '+(user or self.username)),'WORKSHOP ')
    def click(self,x,y,shift=False,game=None,wait=.2):
        self.guard(menu=True)
        return WorkshopEvidence.click(self,x,y,shift,game,wait)
    move_slot=WorkshopEvidence.move_slot
    insert=WorkshopEvidence.insert
    operate=WorkshopEvidence.operate
    give=WorkshopEvidence.give
    def open(self):
        self.rc(f'tp {self.username} .5 -60 .5 0 20.5');time.sleep(.6);self.right()
        self.poll(self.inspect,lambda d:d['open'],label='mapped workshop open')
        self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='workshop cursor')
    def material_fixture(self):
        for item,n in [('myvillage:xuantie_ingot',48),('minecraft:leather',12),('minecraft:coal',32),('minecraft:flint',8),
                       ('minecraft:iron_nugget',16),('minecraft:iron_ingot',8),('myvillage:low_grade_spirit_stone',24)]:self.give(item,n)
    def hammer(self):
        self.guard(menu=True)
        WorkshopEvidence.hold(self,2.6)
    def stages(self):
        self.rc(f'setblock {POS} myvillage:forging_anvil[facing=north]')
        # Locked armor pattern cannot spend materials or create a blank.
        self.material_fixture();self.open();self.click(45,135);self.click(45,48)
        self.poll(self.inspect,lambda d:d['recipe']=='xuantie_helmet',label='armor page selected')
        before=self.inventory();self.click(*POINTS['start']);d=self.inspect()
        self.check('unlearned_pattern_rejected',d['start_reason']==8 and not d['knows'] and d['input']['count']==0 and self.inventory()==before,d);self.close_menu()
        self.rc(f'clear {self.username}')
        # Read books while facing empty space; right-clicking the anvil opens its menu first.
        self.rc(f'tp {self.username} .5 -60 .5 180 0');time.sleep(.5)
        for i,slot in enumerate(SLOTS):self.rc(f'item replace entity {self.username} hotbar.{i} with myvillage:pattern_xuantie_{slot}')
        for i in range(4):self.key(str(i+1));self.right()
        self.check('four_patterns_consumed_by_use',all(self.count('myvillage:pattern_xuantie_'+s)==0 for s in SLOTS),self.inventory())
        self.material_fixture();self.start_video('armor-workshop')
        costs={'helmet':(5,1),'chestplate':(8,2),'leggings':(7,2),'boots':(4,1)}
        for index,slot in enumerate(SLOTS):
            self.rc(f'setblock {TROUGH} myvillage:quenching_trough[facing=north,filled=true]')
            self.open();self.click(45,135);self.click(45,48+index*14)
            self.poll(self.inspect,lambda d:d['recipe']=='xuantie_'+slot,label='select '+slot)
            before=(self.count('myvillage:xuantie_ingot'),self.count('minecraft:leather'),self.count('myvillage:low_grade_spirit_stone'))
            self.operate('start')
            after=(self.count('myvillage:xuantie_ingot'),self.count('minecraft:leather'),self.count('myvillage:low_grade_spirit_stone'))
            self.check(slot+'_blank_costs',tuple(a-b for a,b in zip(before,after))==(*costs[slot],0),{'before':before,'after':after})
            for _ in range(8):
                d=self.inspect()
                if d['input']['piece']['blows']==self.config['forming_strikes']:break
                if d['heat']<self.config['strike_ticks']*2:self.operate('heat')
                self.hammer()
            self.check(slot+'_finite_shaping',self.inspect()['input']['piece']['blows']==self.config['forming_strikes'],self.inspect())
            if self.inspect()['heat']==0:self.operate('heat')
            self.capture(slot+'-shaped');self.operate('quench')
            self.check(slot+'_tough_treatment',self.inspect()['input']['piece']['treatment']==2,self.inspect())
            for action in ('temper','grind'):self.operate(action)
            if slot=='chestplate':
                item=self.inspect()['input']['id'];self.close_menu();self.open();self.insert(item)
                self.check('interrupted_piece_resumes',self.inspect()['input']['piece']['stage']==4,self.inspect())
            self.operate('assemble');result=self.inspect()['output']
            self.check(slot+'_finished_armor',result['id']=='myvillage:xuantie_'+slot and result['temper']['treatment']==2 and 'mark' not in result,result)
            self.capture(slot+'-finished');self.click(196,112,True);self.close_menu()
        self.stop_video()
    def infusion(self):
        u=self.username
        self.rc(f'myvillage cultivation awaken {u}');self.rc(f'myvillage cultivation crafting set {u} forging 0')
        self.rc(f'setblock {POS} myvillage:forging_table[facing=north]')
        for slot in SLOTS:
            self.open();self.insert('myvillage:xuantie_'+slot)
            before=self.inspect()['input'];materials=(self.count('myvillage:xuantie_ingot'),self.count('myvillage:low_grade_spirit_stone'))
            self.click(285,36);self.operate('infuse');after=self.inspect()['output']
            self.check(slot+'_infusion_preserves_temper',after.get('mark')=={'trait':'tough','quality':1} and after['temper']==before['temper'],after)
            self.check(slot+'_infusion_costs',(materials[0]-self.count('myvillage:xuantie_ingot'),materials[1]-self.count('myvillage:low_grade_spirit_stone'))==(1,2),self.inventory())
            self.click(196,112,True);self.close_menu()
        self.open();self.insert('myvillage:xuantie_helmet');before=self.inspect()['input'];self.click(285,36);self.click(*POINTS['infuse']);time.sleep(.5)
        self.check('second_infusion_does_not_reroll',self.inspect()['input']==before and self.inspect()['output']['count']==0,self.inspect());self.close_menu()
    def xp(self):
        return {s:v.get('components',{}).get('myvillage:temper',{}).get('experience',0) for s,v in self.inventory().items() if v['id'].startswith('myvillage:xuantie_')}
    def experience(self):
        # Equip the actual four forged results by shift clicking from their inventory slots.
        self.key('e');self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='inventory opened')
        for number,row in list(self.inventory().items()):
            if row['id'] not in {'myvillage:xuantie_'+s for s in SLOTS}:continue
            x=16+18*(number%9);y=150 if number<9 else 92+18*((number-9)//9)
            self.inventory_shift_click(x,y)
        self.close_menu();self.check('forged_set_equipped',len(self.armor_rows())==4,self.armor_rows())
        armor=self.rc(f'attribute {self.username} minecraft:generic.armor get')
        self.check('vanilla_armor_remains_15','15.0' in armor or armor.rstrip().endswith('15'),armor)
        self.give('myvillage:xuantie_helmet')
        self.rc(f'item replace entity {self.username} hotbar.0 with minecraft:diamond_sword');self.key('1')
        self.rc('time set midnight');self.rc('difficulty normal');self.rc(f'tp {self.username} 7.5 -60 .5 0 0');time.sleep(.6)
        self.start_video('armor-combat-experience')
        for index in range(3):
            self.rc('summon minecraft:zombie 7.5 -60 2.2 {NoAI:1b,PersistenceRequired:1b,Tags:["armor_target"]}')
            time.sleep(.5)
            for _ in range(4):self.guard();self.s.game.xdo('click','1');self.action('mapped_melee');time.sleep(1.1)
            self.rc('kill @e[tag=armor_target]')
        self.stop_video();xp=self.xp()
        self.check('only_equipped_armor_earns_melee_experience',all(xp.get(s,0)>=6 for s in (100,101,102,103)) and all(n==0 for s,n in xp.items() if s<100),xp)
        for _ in range(2):self.guard();self.s.game.xdo('click','1');self.action('mapped_air_swing');time.sleep(.5)
        self.rc(f'damage {self.username} 1 minecraft:generic');time.sleep(.5)
        self.check('air_and_passive_damage_award_no_experience',self.xp()==xp,{'before':xp,'after':self.xp()})
        self.rc('summon minecraft:zombie 7.5 -60 2.2 {NoAI:1b,PersistenceRequired:1b,Tags:["armor_target"]}')
        self.rc(f'damage @e[tag=armor_target,limit=1] 2 minecraft:thorns by {self.username}');time.sleep(.4)
        self.check('reflected_thorns_awards_no_experience',self.xp()==xp,{'before':xp,'after':self.xp()})
        self.rc('kill @e[tag=armor_target]')
        self.capture('forged-set-equipped')
        self.key('e');self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='inventory opened')
        self.inventory_shift_click(16,16);self.close_menu()
    def training_repair(self):
        self.open();slot=next(s for s,v in self.inventory().items() if s<36 and v['id']=='myvillage:xuantie_helmet' and v.get('components',{}).get('myvillage:temper',{}).get('experience',0)>=6)
        self.move_slot(slot);self.poll(self.inspect,lambda d:d['input']['id']=='myvillage:xuantie_helmet',label='trained armor inserted');before=self.inspect()['input']
        self.operate('heat');self.operate('strike');after=self.inspect()['input']
        self.check('armor_training_only_endurance',after['temper']['endurance']==1 and after['temper']['edge']==0 and after['temper']['channeling']==0 and after['temper']['experience']==before['temper']['experience']-self.config['training_bands'][0]['experience'] and after['mark']==before['mark'] and after['max_damage']>before['max_damage'],{'before':before,'after':after})
        self.capture('armor-endurance-training');self.close_menu()
        row=next(v for v in self.inventory().values() if v['id']=='myvillage:xuantie_helmet' and v.get('components',{}).get('myvillage:temper',{}).get('endurance')==1)
        row['components']['minecraft:damage']=100
        row['components']['minecraft:custom_name']='{"text":"旧甲"}'
        row['components']['minecraft:enchantments']={'levels':{'minecraft:protection':2}}
        # Damage/name/enchantment fixture; the repair itself is a real menu action.
        self.rc(f'clear {self.username} myvillage:xuantie_helmet');self.give(stack_spec(row))
        # Compare the server's canonical pre-repair components, including its JSON name encoding.
        row=next(v for v in self.inventory().values() if v['id']=='myvillage:xuantie_helmet')
        self.open();self.insert('myvillage:xuantie_helmet');self.operate('repair');self.capture('armor-repaired');self.close_menu()
        repaired=next(v for v in self.inventory().values() if v['id']=='myvillage:xuantie_helmet')
        self.check('repair_preserves_all_non_damage_components',repaired['components'].get('minecraft:damage',0)<100 and {k:v for k,v in repaired['components'].items() if k!='minecraft:damage'}=={k:v for k,v in row['components'].items() if k!='minecraft:damage'},{'before':row,'after':repaired})
    def array(self):
        from tools.forging_array_evidence import world_pos,position,aim_candidates,menu_point as array_point,inventory_point,POINTS as ap
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/forging/array.json').read_text())
        origin=(0,-59,8)
        for cell in rules['cells']:
            pos=position(world_pos(origin,'north',cell))
            self.rc(f"setblock {pos} "+('minecraft:air' if cell['role']=='air' else 'myvillage:forging_array_'+cell['role']+'[facing=north]'))
        self.give('myvillage:xuantie_boots');self.give('myvillage:xuantie_ingot');self.give('myvillage:low_grade_spirit_stone',2)
        self.rc('fill -7 -61 1 7 -61 15 minecraft:smooth_stone')
        self.poll(lambda:status_json(self.rc('myvillage forgingarray inspect 0 -59 8'),'FORGING_ARRAY '),lambda d:d.get('formed') is True,label='array formed')
        mesh=json.loads((ROOT/'src/main/resources/assets/myvillage/forging_array/formed_mesh.json').read_text())
        cell=next(c for c in rules['cells'] if c['role']=='controller')
        attempted=set()
        for ray in aim_candidates(rules,mesh,origin,'north',cell,formed=True):
            key=tuple(round(n,3) for n in (*ray['feet'],ray['yaw'],ray['pitch']))
            if key in attempted:continue
            attempted.add(key)
            self.rc(f'tp {self.username} '+position(ray['feet'])+f" {ray['yaw']:.6f} {ray['pitch']:.6f}")
            time.sleep(.5);self.right();self.action('mapped_array_open',ray=ray);time.sleep(.6)
            if self.s.game.pointer_grabbed() is False:break
            if len(attempted)>=6:raise RuntimeError('Array opening failed on six distinct mesh rays')
        self.poll(self.s.game.pointer_grabbed,lambda v:v is False,label='array menu opened')
        def click(x,y,shift=False):
            self.guard(menu=True);game=self.s.game;px,py=array_point(game.size,x,y);game.xdo('mousemove',str(px),str(py))
            if shift:game.xdo('keydown','Shift_L')
            try:game.xdo('click','1')
            finally:
                if shift:game.xdo('keyup','Shift_L')
            self.action('mapped_array_click',x=x,y=y,shift=shift);time.sleep(.4)
        n=next(s for s,v in self.inventory().items() if s<36 and v['id']=='myvillage:xuantie_boots')
        click(*inventory_point(n),True);click(*ap['tough']);click(*ap['start'])
        from tools.alchemy_quality_evidence import read_data
        def state():return read_data(self.rc('data get block '+position(origin)))
        self.poll(state,lambda d:'Frozen' in d,label='array active')
        self.poll(state,lambda d:'Frozen' not in d,timeout=60,label='array finished')
        self.capture('array-armor-infusion');click(*ap['output'],True);self.close_menu()
        result=next(v for s,v in self.inventory().items() if s<36 and v['id']=='myvillage:xuantie_boots')
        self.check('array_infuses_finished_armor',result.get('components',{}).get('myvillage:forge_mark')=={'trait':'tough','quality':1},result)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    ArmorForgingEvidence(args.out,list(CASES)).run()
