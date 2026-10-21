#!/usr/bin/env python3
"""Disposable Colab orchard evidence: actual mapped use/craft/harvest/sign/boat input.

Commands prepare fixtures and read authoritative state. Accelerated random ticks
prove transitions, not natural growth pacing. Never use an owner's world.
"""
from __future__ import annotations
import argparse
import io
import json
import math
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
import zipfile
import zlib

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.realm_stats_evidence import Evidence
from tools.alchemy_evidence import AlchemyEvidence
from tools.alchemy_array_evidence import ArrayEvidence
from tools.alchemy_quality_evidence import read_data
from tools.combat_capture import scene,session as cs,procs
from tools.buildgen.nbtread import _read_utf,_read_payload,TAG_COMPOUND
from tools.forging_acquisition_evidence import section_positions

CASES=['sapling_growth_and_clearance','fruit_cycle','wood_crafting_and_placement','leaf_loot_and_stripping','signs_and_boats','persistence','visuals','natural_generation']
FRUIT=(0,-58,3)
def pos(p):return ' '.join(str(n) for n in p)

class OrchardEvidence(Evidence):
    poll=AlchemyEvidence.poll
    inventory=ArrayEvidence.inventory
    close_menu=ArrayEvidence.close_menu
    click=ArrayEvidence.click

    def __init__(self,out,cases=None):
        super().__init__(out)
        self.cases=cases or CASES
        self.report.update(kind='ningyuan_orchard',provider='Colab',selected=self.cases,completed=[],unrun=list(self.cases),
                           mapped_input=[],status='running',growth_pacing='not_verified',owner_appearance='not_verified',
                           owner_sound='not_verified',owner_fps='not_verified',broad_multiplayer='not_verified',
                           natural_acquisition='not_verified',termination='pending')
        self.rules=json.loads((ROOT/'src/main/resources/data/myvillage/orchard/rules.json').read_text())
        self.natural=False
    def capture(self,name):
        if self.s.game.pointer_grabbed():
            info=self.s.game.shot(self.out/name,max_wait=8,settle=.7)
            self.report.setdefault('screenshots',[]).append({'file':name,**info});self.save()
        else:AlchemyEvidence.capture(self,name)
    def check(self,name,passed,detail):
        result=super().check(name,passed,detail)
        if not result:raise AssertionError(name)
        return result
    def done(self,name):
        self.report['completed'].append(name);self.report['unrun'].remove(name);self.save()
    def start(self,reuse=False,natural=False):
        self.natural=natural
        started=time.monotonic()
        state=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),reuse_world=reuse,language='zh_cn',
                                       lock=str(procs.default_lock_path(ROOT)),natural_terrain=natural,
                                       gradle_jvmargs='-Xmx2g',settle=4))
        self.s=cs.Session.attach(ui_check=True);self.sessions+=1
        self.report['phases'].append({'phase':'restart' if reuse else 'start','wall_s':time.monotonic()-started,'capture_timings':state.get('timings',{})});self.save()
    def count(self,item):return sum(s['count'] for s in self.inventory().values() if s['id']==item)
    def is_block(self,p,block):return 'Test passed' in self.rc(f'execute if block {pos(p)} {block}')
    def held(self,item='minecraft:air',n=1):
        self.close_menu();self.rc(f'item replace entity {self.s.username} hotbar.0 with {item} {n}');self.s.game.key('1');time.sleep(.2)
    def look(self,target,feet=None):
        if feet is None:feet=(target[0],-60,target[2]-2.7)
        dx=target[0]-feet[0];dy=target[1]-(feet[1]+1.62);dz=target[2]-feet[2]
        yaw=math.degrees(math.atan2(-dx,dz));pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz)))
        self.rc(f'tp {self.s.username} {pos(feet)} {yaw} {pitch}');time.sleep(.3)
    def use(self,label='right_click',seconds=None,button=3):
        self.s.game.require_ingame(label)
        if seconds:
            self.s.game.xdo('mousedown',str(button));time.sleep(seconds);self.s.game.xdo('mouseup',str(button))
        else:self.s.game.xdo('click',str(button))
        self.report['mapped_input'].append({'kind':label,'button':button,'seconds':seconds});self.save();time.sleep(.4)
    def collect(self,p):
        self.rc(f'tp {self.s.username} {p[0]+.5} -60 {p[2]+.5}');time.sleep(1)
    def crop_fixture(self,p=FRUIT,age=0,enriched=False):
        x,y,z=p
        for command in [f'setblock {x} {y+1} {z+1} myvillage:ningyuan_log',
                        f'setblock {x} {y+1} {z} myvillage:ningyuan_leaves[persistent=true,distance=1]',
                        f'setblock {pos(p)} myvillage:ningyuan_fruit_cluster[age={age},enriched={str(enriched).lower()}]']:
            self.rc(command)
        self.look((x+.5,y+.65,z+.5))
    def grow_crop(self,p=FRUIT):
        self.held('minecraft:bone_meal',2);self.look((p[0]+.5,p[1]+.65,p[2]+.5))
        self.use('bone_meal_stage_one');self.use('bone_meal_stage_two')
        self.check('mature_after_two_mapped_bonemeals',self.is_block(p,'myvillage:ningyuan_fruit_cluster[age=2]'),self.count('minecraft:bone_meal'))
    def setup(self):
        u=self.s.username
        for command in (*scene.world_rules(),f'gamemode survival {u}',f'clear {u}',
                        'myvillage world pause','gamerule randomTickSpeed 0','gamerule doDaylightCycle false','time set noon',
                        'forceload add -32 -32 48 48',f'effect give {u} minecraft:resistance infinite 4 true',
                        f'effect give {u} minecraft:saturation infinite 1 true'):
            self.rc(command)
    def sapling_growth_and_clearance(self):
        u=self.s.username;p=(-10,-60,3)
        self.held('myvillage:ningyuan_sapling',1);self.look((-9.5,-60,3.5));self.use('plant_sapling_on_dirt')
        self.check('sapling_placed_and_consumed',self.is_block(p,'myvillage:ningyuan_sapling') and self.count('myvillage:ningyuan_sapling')==0,self.inventory())
        self.rc('setblock -10 -58 3 minecraft:stone');self.held('minecraft:bone_meal',32);self.look((-9.5,-59.7,3.5))
        self.use('blocked_tree_bonemeal',seconds=2)
        self.check('blocked_growth_preserves_sapling_and_obstacle',self.is_block(p,'myvillage:ningyuan_sapling') and self.is_block((-10,-58,3),'minecraft:stone'),'full-plan preflight')
        self.rc('setblock -10 -58 3 minecraft:air');self.use('grow_tree_bonemeal',seconds=3)
        self.poll(lambda:self.is_block(p,'myvillage:ningyuan_log'),bool,6,'sapling becomes tree')
        self.check('tree_grew_from_mapped_bonemeal',True,self.count('minecraft:bone_meal'))
        self.rc('setblock -10 -61 14 myvillage:spirit_soil');self.held('myvillage:ningyuan_sapling');self.look((-9.5,-60,14.5));self.use('plant_sapling_on_spirit_soil')
        self.check('spirit_soil_accepts_tree_sapling',self.is_block((-10,-60,14),'myvillage:ningyuan_sapling'),'existing herbs retain their tests')
        self.held('minecraft:bone_meal',32);self.look((-9.5,-59.7,14.5));self.use('grow_tree_on_spirit_soil',seconds=3)
        self.poll(lambda:self.is_block((-10,-60,14),'myvillage:ningyuan_log'),bool,6,'spirit soil tree')
        self.check('tree_preserves_spirit_soil',self.is_block((-10,-61,14),'myvillage:spirit_soil'),'no dirt conversion')
        self.rc(f'clear {u}');self.done('sapling_growth_and_clearance')
    def fruit_cycle(self):
        self.crop_fixture();self.held('myvillage:ningyuan_fertilizer',2);self.use('fertilize_bud')
        self.check('fertilizer_spends_once',self.is_block(FRUIT,'myvillage:ningyuan_fruit_cluster[age=0,enriched=true]') and self.count('myvillage:ningyuan_fertilizer')==1,self.inventory())
        self.use('reject_second_fertilizer');self.check('duplicate_fertilizer_not_consumed',self.count('myvillage:ningyuan_fertilizer')==1,self.inventory())
        self.grow_crop();self.capture('high-fruit.png')
        self.held('myvillage:ningyuan_fertilizer',1);self.use('reject_fertilizer_on_ripe_fruit')
        self.check('ripe_fruit_rejects_fertilizer',self.count('myvillage:ningyuan_fertilizer')==1,self.inventory())
        self.held();self.use('harvest_high_fruit');self.collect(FRUIT)
        self.check('one_high_harvest_resets_enrichment',self.count('myvillage:ningyuan_fruit_high')==self.rules['harvest']['count'] and self.is_block(FRUIT,'myvillage:ningyuan_fruit_cluster[age=0,enriched=false]'),self.inventory())
        self.look((.5,-57.35,3.5));self.use('duplicate_harvest');self.collect(FRUIT)
        self.check('duplicate_harvest_grants_nothing',self.count('myvillage:ningyuan_fruit_high')==self.rules['harvest']['count'],self.inventory())
        self.grow_crop();self.held();self.use('next_cycle_low_harvest');self.collect(FRUIT)
        self.check('next_harvest_is_low_grade',self.count('myvillage:ningyuan_fruit_low')==self.rules['harvest']['count'],self.inventory())
        self.held('minecraft:bone_meal',1);self.look((.5,-57.35,3.5));self.use('grow_before_late_fertilizer')
        self.held('myvillage:ningyuan_fertilizer',1);self.use('reject_fertilizer_during_growth')
        self.check('growing_fruit_rejects_fertilizer',self.count('myvillage:ningyuan_fertilizer')==1 and self.is_block(FRUIT,'myvillage:ningyuan_fruit_cluster[age=1,enriched=false]'),self.inventory())
        self.done('fruit_cycle')
    def wood_crafting_and_placement(self):
        u=self.s.username;self.rc(f'clear {u}');self.held('myvillage:ningyuan_log')
        self.s.game.key('e');time.sleep(.4)
        for x,y,shift,label in [(16,150,False,'take_log'),(106,26,False,'put_log_in_crafting'),(162,36,True,'craft_four_planks')]:
            self.click(x,y,shift=shift,label=label,dimensions=(176,166))
        self.capture('crafting-planks.png');self.close_menu()
        self.check('native_inventory_crafting_produces_four_planks',self.count('myvillage:ningyuan_planks')==4 and self.count('myvillage:ningyuan_log')==0,self.inventory())
        self.held('myvillage:ningyuan_door',2);self.look((5.5,-60,3.5));self.use('place_lattice_door')
        self.check('door_occupies_two_blocks',self.is_block((5,-60,3),'myvillage:ningyuan_door[half=lower]') and self.is_block((5,-59,3),'myvillage:ningyuan_door[half=upper]'),self.inventory())
        self.held();self.look((5.5,-59.5,3.5));self.use('open_door')
        self.check('door_opens_by_hand',self.is_block((5,-60,3),'myvillage:ningyuan_door[open=true]'),'mapped right click')
        self.held('myvillage:ningyuan_slab',2);self.look((8.5,-60,3.5));self.use('place_bottom_slab')
        self.look((8.5,-59.5,3.5));self.use('combine_double_slab')
        self.check('two_slabs_combine_without_extra_cost',self.is_block((8,-60,3),'myvillage:ningyuan_slab[type=double]') and self.count('myvillage:ningyuan_slab')==0,self.inventory())
        self.held('minecraft:netherite_axe');self.look((8.5,-59.6,3.5));self.use('break_double_slab',seconds=.8,button=1);self.collect((8,-60,3))
        self.check('double_slab_drops_two',self.count('myvillage:ningyuan_slab')==2,self.inventory())
        self.rc('setblock 8 -60 6 minecraft:flower_pot');self.held('myvillage:ningyuan_sapling');self.look((8.5,-59.8,6.5));self.use('pot_sapling')
        self.check('sapling_fits_vanilla_flower_pot',self.is_block((8,-60,6),'myvillage:potted_ningyuan_sapling'),self.inventory())
        self.done('wood_crafting_and_placement')
    def leaf_loot_and_stripping(self):
        u=self.s.username;self.rc(f'clear {u}')
        self.rc('setblock 5 -60 9 myvillage:ningyuan_log[axis=x]');self.held('minecraft:iron_axe');self.look((5.5,-59.5,9.5));self.use('strip_log')
        self.check('axe_strips_and_preserves_axis',self.is_block((5,-60,9),'myvillage:stripped_ningyuan_log[axis=x]'),'server blockstate')
        self.rc('setblock 8 -60 9 myvillage:ningyuan_log');self.rc('setblock 8 -59 9 myvillage:ningyuan_leaves[persistent=false,distance=1]')
        self.held('minecraft:shears');self.look((8.5,-58.5,9.5));self.use('shear_leaves',seconds=.5,button=1);self.collect((8,-59,9))
        self.check('shears_return_the_leaf_block',self.count('myvillage:ningyuan_leaves')==1,self.inventory())
        self.rc('setblock 8 -59 9 myvillage:ningyuan_leaves[persistent=false,distance=1]');self.held('minecraft:netherite_axe');self.look((8.5,-59.5,9.5));self.use('chop_support_log',seconds=.8,button=1)
        self.rc('gamerule randomTickSpeed 500')
        self.poll(lambda:self.is_block((8,-59,9),'minecraft:air'),bool,10,'unsupported leaf decay')
        self.rc('gamerule randomTickSpeed 0');self.check('unsupported_leaf_decays',True,'support was chopped using mapped input')
        self.done('leaf_loot_and_stripping')
    def edit_sign(self,label):
        self.poll(lambda:self.s.game.pointer_grabbed(),lambda v:v is False,5,'sign editor')
        self.s.game.xdo('type','--clearmodifiers','--delay','100','0760');self.s.game.xdo('key','Escape');time.sleep(.5)
        self.report['mapped_input'].append({'kind':label,'text':'0760'});self.save()
    def signs_and_boats(self):
        self.held('myvillage:ningyuan_sign');self.look((5.5,-60,14.5));self.use('place_editable_sign');self.edit_sign('type_sign_text')
        data=read_data(self.rc('data get block 5 -60 14'))
        self.check('sign_saves_typed_text','0760' in str(data),data)
        self.rc('setblock 8 -57 14 myvillage:ningyuan_log');self.held('myvillage:ningyuan_hanging_sign');self.look((8.5,-57,14.5),(8.5,-60,12.0));self.use('place_hanging_sign');self.edit_sign('type_hanging_sign_text')
        data=read_data(self.rc('data get block 8 -58 14'))
        self.check('hanging_sign_saves_typed_text','0760' in str(data),data)
        self.rc('fill 14 -61 -4 22 -61 4 minecraft:water');self.held('myvillage:ningyuan_boat');self.look((17.5,-60.1,.5),(17.5,-60,-2.0));self.use('place_wood_boat')
        boat='@e[type=minecraft:boat,sort=nearest,limit=1]'
        data=read_data(self.rc(f'data get entity {boat}'))
        self.check('boat_uses_ningyuan_variant',data.get('Type')=='myvillage:ningyuan',data)
        bpos=data['Pos'];self.held();self.look((bpos[0],bpos[1]+.3,bpos[2]),(bpos[0],-60,bpos[2]-2));self.use('mount_boat')
        reply=self.rc(f'execute as {self.s.username} on vehicle run data get entity @s Type')
        self.check('player_mounts_custom_boat','myvillage:ningyuan' in reply,reply)
        self.s.game.xdo('keydown','w');time.sleep(1);self.s.game.xdo('keyup','w')
        self.report['mapped_input'].append({'kind':'row_boat','key':'w','seconds':1})
        after=read_data(self.rc(f'data get entity {boat}'))
        self.check('mapped_rowing_moves_boat',math.dist(bpos,after['Pos'])>.15,{'before':bpos,'after':after['Pos']})
        self.s.game.xdo('key','Shift_L');time.sleep(.3);self.rc(f'tp {self.s.username} 18.5 -60 -3.5');self.rc('kill @e[type=minecraft:boat]')
        self.held('myvillage:ningyuan_chest_boat');self.look((18.5,-60.1,.5),(18.5,-60,-2));self.use('place_chest_boat')
        chest='@e[type=minecraft:chest_boat,sort=nearest,limit=1]'
        data=read_data(self.rc(f'data get entity {chest}'));bpos=data['Pos']
        self.check('chest_boat_uses_ningyuan_variant',data.get('Type')=='myvillage:ningyuan',data)
        self.held();self.rc(f'item replace entity {self.s.username} hotbar.1 with minecraft:bone_meal 3');self.look((bpos[0],bpos[1]+.4,bpos[2]),(bpos[0],-60,bpos[2]-2))
        self.s.game.xdo('keydown','Shift_L')
        try:self.use('open_chest_boat')
        finally:self.s.game.xdo('keyup','Shift_L')
        self.poll(lambda:self.s.game.pointer_grabbed(),lambda v:v is False,5,'chest boat menu')
        self.click(34,150,shift=True,label='store_in_chest_boat',dimensions=(176,166));self.capture('chest-boat-menu.png');self.close_menu()
        data=read_data(self.rc(f'data get entity {chest}'))
        self.check('chest_boat_stores_native_inventory',any(i['id']=='minecraft:bone_meal' and i['count']==3 for i in data.get('Items',[])),data)
        self.done('signs_and_boats')
    def persistence(self):
        self.crop_fixture();self.held('myvillage:ningyuan_fertilizer');self.use('enrich_before_restart');self.held('minecraft:bone_meal');self.use('grow_before_restart')
        self.check('saved_crop_fixture_is_growing_and_enriched',self.is_block(FRUIT,'myvillage:ningyuan_fruit_cluster[age=1,enriched=true]'),'mapped preparation')
        self.rc('save-all flush');self.stop();self.start(reuse=True)
        self.check('fruit_cycle_survives_restart',self.is_block(FRUIT,'myvillage:ningyuan_fruit_cluster[age=1,enriched=true]'),'saved blockstate')
        for p in [(5,-60,14),(8,-58,14)]:
            data=read_data(self.rc(f'data get block {pos(p)}'));self.check('sign_text_survives_restart_'+str(p),'0760' in str(data),data)
        data=read_data(self.rc('data get entity @e[type=minecraft:chest_boat,limit=1]'))
        self.check('chest_boat_identity_and_inventory_survive_restart',data.get('Type')=='myvillage:ningyuan' and any(i['id']=='minecraft:bone_meal' and i['count']==3 for i in data.get('Items',[])),data)
        self.held('minecraft:bone_meal');self.look((.5,-57.35,3.5));self.use('finish_saved_crop');self.held();self.use('harvest_saved_high_crop');self.collect(FRUIT)
        self.check('resumed_crop_produces_one_high_fruit',self.count('myvillage:ningyuan_fruit_high')==self.rules['harvest']['count'],self.inventory())
        self.done('persistence')
    def visuals(self):
        u=self.s.username
        self.rc(f'gamemode creative {u}');self.rc(f'clear {u}');self.rc('gamerule randomTickSpeed 0')
        self.rc('fill -20 -60 22 0 -50 42 minecraft:air');self.rc('fill -20 -61 22 0 -61 42 minecraft:grass_block')
        self.rc('place feature myvillage:ningyuan_tree -10 -60 32')
        self.rc('fill -15 -60 27 -5 -60 27 myvillage:jade_tree_pool_border');self.rc('fill -15 -60 37 -5 -60 37 myvillage:jade_tree_pool_border')
        self.rc('fill -15 -60 28 -15 -60 36 myvillage:jade_tree_pool_border');self.rc('fill -5 -60 28 -5 -60 36 myvillage:jade_tree_pool_border')
        # Set only the existing fruits' presentation state; tree geometry remains the runtime feature's output.
        count=0
        for x in range(-14,-5):
            for y in range(-58,-51):
                for z in range(28,37):
                    reply=self.s.run(f'execute if block {x} {y} {z} myvillage:ningyuan_fruit_cluster run setblock {x} {y} {z} myvillage:ningyuan_fruit_cluster[age=2,enriched={str(count%4==0).lower()}]',warn=False)[0]
                    if 'Changed the block' in reply:count+=1
        self.report['visual_fixture']={'runtime_tree_origin':[-10,-60,32],'mature_fruit_count':count,'fruit_maturity_was_set_for_presentation':True}
        for x,z in [(-15,27),(-5,27),(-15,37),(-5,37)]:self.rc(f'setblock {x} -59 {z} myvillage:ningyuan_fruit_lantern')
        self.rc('setblock -6 -60 25 myvillage:potted_ningyuan_sapling')
        self.rc('fill -15 -60 24 -11 -60 24 myvillage:ningyuan_lattice')
        self.rc('fill -15 -61 24 -11 -61 24 myvillage:ningyuan_planks')
        self.rc(f'gamemode spectator {u}')
        self.s.game.key('F1');self.look((-9.5,-56,32.5),(.5,-54,19.5));time.sleep(1);self.capture('tree-day.png')
        self.look((-9.5,-55,32.5),(-23.5,-54,22.5));time.sleep(.5);self.capture('tree-rear.png')
        self.rc('time set midnight');self.look((-9.5,-56,32.5),(.5,-54,19.5));time.sleep(.6);self.capture('tree-night.png');self.rc('time set noon')
        self.look((-10,-59,24),(-7.5,-57,18.5));time.sleep(.5);self.capture('decorations.png');self.s.game.key('F1')
        names=json.loads((ROOT/'src/main/resources/data/myvillage/orchard/catalogue.json').read_text())['items']
        self.rc(f'tp {u} -9.5 -60 23.5 0 -12');self.rc(f'gamemode survival {u}')
        for i,name in enumerate(names):self.rc(f'item replace entity {u} inventory.{i} with myvillage:{name}')
        self.s.game.key('e');time.sleep(.5);self.capture('all-items.png');self.close_menu()
        self.rc(f'tp {u} -9.5 -60 23.5 0 -12');self.rc('setworldspawn -10 -60 23');self.rc('save-all flush')
        self.done('visuals')
    def export_world(self):
        world=cs.SERVER_DIR/cs.LEVEL_NAME
        with zipfile.ZipFile(self.out/'ningyuan_orchard_world.zip','w',zipfile.ZIP_DEFLATED) as archive:
            for p in sorted(world.rglob('*')):
                relative=p.relative_to(world)
                if p.is_file() and not p.is_symlink() and not any(part in ('session.lock','stats','advancements') for part in relative.parts):archive.write(p,'凝元果园独立展示/'+relative.as_posix())
        self.report['world_export']={'file':'ningyuan_orchard_world.zip','owner_saves_modified':False};self.save()
    def natural_generation(self):
        if not self.natural:
            self.rc('save-all flush');self.stop()
            if 'visuals' in self.report['completed']:self.export_world()
            self.start(natural=True)
        u=self.s.username
        for c in (*scene.world_rules(),'myvillage world pause',f'gamemode spectator {u}','gamerule randomTickSpeed 0','time set noon'):self.rc(c)
        found={};searched=[]
        for biome,origin in [('minecraft:meadow',(0,0)),('minecraft:forest',(0,0)),('minecraft:forest',(768,768))]:
            reply=self.rc(f'execute positioned {origin[0]} 100 {origin[1]} run locate biome {biome}')
            match=re.search(r'\[(-?\d+),\s*(?:~|-?\d+),\s*(-?\d+)\]',reply)
            if not match:continue
            x,z=map(int,match.groups());searched.append({'biome':biome,'center':[x,z]})
            self.rc('forceload remove all');self.rc(f'forceload add {x-64} {z-64} {x+64} {z+64}');self.rc(f'tp {u} {x+.5} 150 {z+.5}')
            for attempt in range(6):
                time.sleep(3);self.rc('save-all flush');found=scan_natural_tree(cs.SERVER_DIR/cs.LEVEL_NAME/'region',(x,z),6)
                if found.get('myvillage:ningyuan_log'):break
            if found.get('myvillage:ningyuan_log'):break
        self.check('naturally_generated_tree_has_logs_and_leaves',bool(found.get('myvillage:ningyuan_log')) and bool(found.get('myvillage:ningyuan_leaves')),{'searched':searched,'counts':{k:len(v) for k,v in found.items()}})
        root=min(found['myvillage:ningyuan_log'],key=lambda p:p[1]);x,y,z=root
        self.look((x+.5,y+4,z+.5),(x+11.5,y+7,z-12.5));self.s.game.key('F1');time.sleep(.8);self.capture('natural-tree.png');self.s.game.key('F1')
        self.report['natural_generation']={'status':'verified','root':root,'samples':searched,'counts':{k:len(v) for k,v in found.items()},'operator_placed_tree_in_this_world':False}
        self.report['natural_acquisition']='generation_verified; full survival journey and density not_verified'
        self.done('natural_generation')
    def run(self,reuse=False):
        self.start(reuse=reuse,natural=self.cases==['natural_generation'])
        if not reuse:self.setup()
        for name in self.cases:getattr(self,name)()
        self.report['status']='pass';self.save()

def scan_natural_tree(region_dir,center,radius):
    wanted={k:[] for k in ['myvillage:ningyuan_log','myvillage:ningyuan_leaves','myvillage:ningyuan_fruit_cluster']}
    cx,cz=center[0]//16,center[1]//16
    for x in range(cx-radius,cx+radius+1):
        for z in range(cz-radius,cz+radius+1):
            path=region_dir/f'r.{x//32}.{z//32}.mca'
            if not path.is_file():continue
            with path.open('rb') as f:
                f.seek(4*((x%32)+(z%32)*32));header=f.read(4);offset=int.from_bytes(header[:3],'big')
                if not offset:continue
                f.seek(offset*4096);length=int.from_bytes(f.read(4),'big');kind=f.read(1)
                if kind!=b'\x02':raise ValueError(f'Unsupported region compression: {kind}')
                data=io.BytesIO(zlib.decompress(f.read(length-1)))
                if data.read(1)!=b'\x0a':raise ValueError('Expected chunk compound')
                _read_utf(data);chunk=_read_payload(data,TAG_COMPOUND)
                for section in chunk.get('sections',[]):
                    for block,position in section_positions(section,x,z,wanted):wanted[block].append(position)
    return wanted

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'out/preview/ningyuan_tree/runtime');p.add_argument('--cases',default='all');p.add_argument('--reuse',action='store_true');args=p.parse_args()
    cases=CASES if args.cases=='all' else args.cases.split(',')
    if not cases or any(c not in CASES for c in cases):p.error('Unknown orchard case')
    e=OrchardEvidence(args.out,cases)
    try:e.run(args.reuse)
    except BaseException as error:e.report['status']='fail';e.report['failures'].append(repr(error));e.save();raise
    finally:
        try:
            if e.s:e.stop()
            state=cs.read_state() or {}
            if state.get('phase') in cs.PHASES_ACTIVE:cs.stop();state=cs.read_state() or {}
            e.report['termination']='confirmed' if state.get('phase')=='stopped' else 'not_verified'
            if e.report['status']=='pass' and 'visuals' in e.report['completed'] and not (e.out/'ningyuan_orchard_world.zip').exists():e.export_world()
        finally:e.save()
if __name__=='__main__':main()
