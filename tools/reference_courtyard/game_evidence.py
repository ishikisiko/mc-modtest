#!/usr/bin/env python3
"""Real Minecraft proof for the independent courtyard; Modal workspace only."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import sys
import time
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.realm_stats_evidence import Evidence
from tools.yunshan.game_evidence import BuildingEvidence,aim
from tools.yunshan.game_export import read_typed,saved_cells
from tools.combat_capture import session as cs,scene
from tools.alchemy_quality_evidence import read_data
import json_to_nbt as nbt


class CourtyardEvidence(BuildingEvidence):
    def __init__(self,out):
        Evidence.__init__(self,out)
        base=out.parent
        self.spec=json.loads((base/'build-manifest.json').read_text())
        self.d=json.loads((base/'reference_courtyard.structure.json').read_text())
        self.hud_hidden=False
        self.report.update(kind='minecraft_reference_courtyard',selected=['place_template','mapped_walks','variant_selector','whole_window_transaction','waterlogged_assembly_removal','creative_jei_grouping','game_screenshots','native_structure_save','restart_readback','world_export'],completed=[],unrun=[],
            buildings={self.d['id']:{'screenshots':[]}},software_rendering=True,
            owner_appearance='not_verified',owner_fps='not_verified',multiplayer='not_verified')

    def probe(self,pos,state,label):
        world=self.xyz(self.d,pos)
        reply=self.rc('execute if block '+' '.join(str(int(v)) for v in world)+' '+state)
        self.check(label,'passed' in reply,{'position':world,'state':state,'reply':reply})

    def setup(self):
        u=self.s.username
        for cmd in (*scene.world_rules(),f'op {u}',f'clear {u}',f'gamemode creative {u}',
                    f'effect give {u} minecraft:saturation infinite 255 true',
                    'gamerule doFireTick false','gamerule randomTickSpeed 0','myvillage world pause',
                    'forceload add -16 -16 80 64','fill -8 -62 -8 64 -61 48 minecraft:smooth_stone',
                    'setworldspawn 45 -57 2 0','defaultgamemode creative'):
            self.rc(cmd)
        origin=self.d['site']['world_min']
        self.rc(f'place template {self.d["id"]} '+' '.join(map(str,origin)))
        # Actual registry/template readback, not success wording alone.
        rows=self.spec['fixture_cells']
        self.probe(rows[0][:3],rows[0][3],'template_foundation')
        water=next(r for r in rows if r[3].startswith('minecraft:water'))
        wet=next(r for r in rows if 'waterlogged=true' in r[3])
        self.probe(water[:3],water[3],'real_water_source')
        self.probe(wet[:3],wet[3],'waterlogged_micro_piece')
        self.report['completed'].append('place_template');self.save()

    def traverse(self,name,start,yaw,axis,target,minimum_y):
        u=self.s.username;self.set_hud(False);self.rc(f'gamemode survival {u}')
        self.teleport(self.xyz(self.d,start),yaw,0)
        self.s.game.require_ingame('courtyard mapped walk')
        positions=[];begin=time.monotonic();world_target=self.d['site']['world_min'][axis]+target
        increasing=target>start[axis]
        self.s.game.xdo('keydown','w')
        try:
            while time.monotonic()-begin<15:
                p=self.player_pos();positions.append(p)
                if (p[axis]>=world_target if increasing else p[axis]<=world_target):break
                time.sleep(.15)
        finally:self.s.game.xdo('keyup','w')
        p=self.player_pos();crossed=p[axis]>=world_target if increasing else p[axis]<=world_target
        self.s.game.shot(self.out/'courtyard'/f'walk-{name}.png',max_wait=3,settle=.5)
        self.check('mapped_'+name,crossed and p[1]>=self.d['site']['world_min'][1]+minimum_y-.15,
                   {'start':start,'end':p,'target':world_target,'positions':positions,'input':'physical W, autoJump=false; no teleport during traversal'})

    def placement(self):
        u=self.s.username;self.set_hud(False);self.rc(f'gamemode survival {u}')
        identity='courtyard_window'
        self.rc('fill 54 -60 6 60 -59 13 minecraft:smooth_stone')
        self.rc('fill 54 -58 6 60 -54 13 minecraft:air')
        self.rc(f'clear {u}');self.rc(f'item replace entity {u} hotbar.0 with myvillage:{identity} 3')
        pos=[57.5,-58,11.2];target=[57.5,-58.01,8.5]
        yaw,pitch=aim(pos,target);self.teleport(pos,yaw,pitch)
        self.s.game.xdo('key','1');self.s.game.require_ingame('variant selector')
        self.s.game.xdo('keydown','Shift_L');time.sleep(.3);self.s.game.xdo('click','3');self.s.game.xdo('keyup','Shift_L')
        self.poll(self.s.game.pointer_grabbed,lambda p:not p,8,'native variant screen')
        self.s.game.shot(self.out/'courtyard'/'variant-selector.png',max_wait=3,settle=.7)
        width,height=self.s.game.size;scale=max(1,min(width//320,height//240));gw,gh=width//scale,height//scale
        left=(gw-306)//2;top=(gh-111)//2
        self.s.game.xdo('mousemove',str((left+5+100+48)*scale),str((top+31+42)*scale));self.s.game.xdo('click','1')
        self.poll(self.s.game.pointer_grabbed,bool,8,'style selection closes screen')
        chosen=self.poll(lambda:read_data(self.rc(f'data get entity {u} SelectedItem.components."minecraft:custom_model_data"')),lambda n:n==2,8,'server accepted round window')
        self.check('mapped_variant_selection',chosen==2,{'custom_model_data':chosen,'input':'Shift + right mouse, then variant card click'})
        self.report['completed'].append('variant_selector');self.save()
        # An obstacle in a secondary cell must reject the whole placement and consume nothing.
        self.rc('setblock 56 -57 8 minecraft:gold_block')
        self.teleport(pos,yaw,pitch);self.s.game.shot(self.out/'courtyard'/'window-blocked-before.png',max_wait=2,settle=.5)
        self.s.game.xdo('click','3');time.sleep(.6)
        count=read_data(self.rc(f'data get entity {u} Inventory[{{Slot:0b}}].count'))
        checks=[]
        for y in range(-58,-55):
            for x in range(55,58):
                state='minecraft:gold_block' if (x,y)==(56,-57) else 'minecraft:air'
                checks.append('passed' in self.rc(f'execute if block {x} {y} 8 {state}'))
        self.check('blocked_whole_window_no_partial_or_cost',count==3 and all(checks),{'remaining':count,'footprint_unchanged':all(checks)})
        self.rc('setblock 56 -57 8 minecraft:air');time.sleep(.3);self.s.game.xdo('click','3')
        reply=self.poll(lambda:self.rc(f'execute if block 57 -58 8 myvillage:{identity}[variant=1,part=0,facing=south]'),lambda s:'passed' in s,8,'whole window placed')
        count=read_data(self.rc(f'data get entity {u} Inventory[{{Slot:0b}}].count'))
        for i in range(9):
            x,y=57-i%3,-58+i//3
            checks.append('passed' in self.rc(f'execute if block {x} {y} 8 myvillage:{identity}[part={i},variant=1,facing=south]'))
        self.check('one_item_places_nine_cells',count==2 and all(checks),{'remaining':count,'parts':9,'root':reply})
        self.s.game.shot(self.out/'courtyard'/'whole-window-placed.png',max_wait=3,settle=.6)
        # Break an upper, non-root cell with a real attack key. Root-only loot must total one.
        self.rc('kill @e[type=minecraft:item,x=53,y=-61,z=5,dx=10,dy=10,dz=10]')
        self.rc(f'item replace entity {u} hotbar.1 with minecraft:netherite_axe')
        self.rc(f'effect give {u} minecraft:haste 30 10 true');self.s.game.xdo('key','2')
        break_pos=[55.5,-58,11.5];break_target=[55.5,-55.5,8.5];by,bp=aim(break_pos,break_target)
        self.teleport(break_pos,by,bp);self.s.game.shot(self.out/'courtyard'/'window-before-break.png',max_wait=2,settle=.5)
        self.s.game.require_ingame('break upper window cell');self.s.game.xdo('mousedown','1')
        try:self.poll(lambda:self.rc('execute if block 57 -58 8 minecraft:air'),lambda s:'passed' in s,15,'whole window removed from non-root attack')
        finally:self.s.game.xdo('mouseup','1')
        gone=[]
        for y in range(-58,-55):
            for x in range(55,58):gone.append('passed' in self.rc(f'execute if block {x} {y} 8 minecraft:air'))
        selector='@e[type=minecraft:item,x=53,y=-61,z=5,dx=10,dy=10,dz=10,nbt={Item:{id:"myvillage:courtyard_window"}}]'
        self.rc('scoreboard objectives add courtyard_probe dummy');self.rc('scoreboard players set drops courtyard_probe 0')
        self.rc(f'execute as {selector} run scoreboard players add drops courtyard_probe 1')
        score=self.rc('scoreboard players get drops courtyard_probe');match=re.search(r'has (\d+) ',score)
        drop_count=read_data(self.rc('data get entity '+selector[:-1]+',limit=1] Item.count'))
        self.check('break_any_part_drops_exactly_one',all(gone) and match is not None and int(match.group(1))==1 and drop_count==1,{'all_nine_removed':all(gone),'entity_score':score,'item_count':drop_count})
        self.s.game.shot(self.out/'courtyard'/'whole-window-removed.png',max_wait=3,settle=.5)
        self.rc('kill @e[type=minecraft:item,x=53,y=-61,z=5,dx=10,dy=10,dz=10]')
        self.rc(f'gamemode creative {u}');self.s.game.xdo('key','1');self.teleport(pos,yaw,pitch)
        self.s.game.shot(self.out/'courtyard'/'creative-window-before.png',max_wait=2,settle=.5);self.s.game.xdo('click','3')
        self.poll(lambda:self.rc('execute if block 57 -58 8 myvillage:courtyard_window[variant=1]'),lambda s:'passed' in s,8,'creative whole window')
        self.teleport(break_pos,by,bp);self.s.game.shot(self.out/'courtyard'/'creative-window-break.png',max_wait=2,settle=.5);self.s.game.xdo('click','1')
        self.poll(lambda:self.rc('execute if block 57 -58 8 minecraft:air'),lambda s:'passed' in s,8,'creative upper part removes whole')
        self.rc('scoreboard players set drops courtyard_probe 0');self.rc(f'execute as {selector} run scoreboard players add drops courtyard_probe 1')
        score=self.rc('scoreboard players get drops courtyard_probe');match=re.search(r'has (\d+) ',score)
        gone=['passed' in self.rc(f'execute if block {x} {y} 8 minecraft:air') for y in range(-58,-55) for x in range(55,58)]
        self.check('creative_whole_removal_has_no_drop',all(gone) and match is not None and int(match.group(1))==0,{'all_nine_removed':all(gone),'entity_score':score})
        self.report['completed'].append('whole_window_transaction');self.save()

    def waterlogged_removal(self):
        u=self.s.username;self.set_hud(False);self.rc(f'gamemode creative {u}')
        self.rc('fill 54 -60 6 60 -59 13 minecraft:smooth_stone');self.rc('fill 54 -58 6 60 -53 13 minecraft:air')
        self.rc('setblock 57 -59 8 minecraft:water[level=0]')
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:courtyard_column 3');self.s.game.xdo('key','1')
        pos=[57.5,-58,9.7];target=[57.5,-59.01,8.5];yaw,pitch=aim(pos,target)
        self.teleport(pos,yaw,pitch);self.s.game.shot(self.out/'courtyard'/'wet-column-before.png',max_wait=2,settle=.6);self.s.game.xdo('click','3')
        self.poll(lambda:self.rc('execute if block 57 -59 8 myvillage:courtyard_column[waterlogged=true,part=0]'),lambda s:'passed' in s,8,'waterlogged whole column placement')
        target=[57.5,-55.5,8.5];yaw,pitch=aim(pos,target);self.teleport(pos,yaw,pitch)
        self.s.game.shot(self.out/'courtyard'/'wet-column-placed.png',max_wait=2,settle=.6);self.s.game.xdo('click','1')
        reply=self.poll(lambda:self.rc('execute if block 57 -59 8 minecraft:water[level=0]'),lambda s:'passed' in s,8,'water restored after upper-cell removal')
        cleared=['passed' in self.rc(f'execute if block 57 {y} 8 minecraft:air') for y in [-58,-57,-56]]
        self.check('creative_upper_removal_restores_source_water',all(cleared),{'root':reply,'upper_parts_cleared':all(cleared),'input':'physical right click, then physical left click on upper cell'})
        self.rc('fill 54 -60 6 60 -53 13 minecraft:air')
        self.report['completed'].append('waterlogged_assembly_removal');self.save()

    def grouped_inventory(self):
        u=self.s.username;self.set_hud(False);self.rc(f'gamemode creative {u}')
        self.s.game.require_ingame('creative inventory');self.s.game.xdo('key','e')
        self.poll(self.s.game.pointer_grabbed,lambda p:not p,8,'creative inventory opened')
        self.s.game.shot(self.out/'courtyard'/'creative-jei.png',max_wait=3,settle=.8)
        log=cs.CLIENT_DIR/'logs/latest.log'
        text=self.poll(lambda:log.read_text(errors='replace'),lambda s:'COURTYARD_CREATIVE display=10 search=10' in s and 'COURTYARD_JEI entries=10 families=10' in s,20,'ten actual creative and JEI entries')
        self.check('creative_and_jei_have_ten_grouped_entries',True,[l for l in text.splitlines() if 'COURTYARD_CREATIVE' in l or 'COURTYARD_JEI' in l][-6:])
        self.s.game.xdo('key','Escape');self.poll(self.s.game.pointer_grabbed,bool,8,'creative inventory closes')
        self.report['completed'].append('creative_jei_grouping');self.save()

    def screenshots(self):
        for name in ['overview','reference','gate','corridor','alcove']:
            v=self.spec['viewpoints'][name]
            self.photograph(self.d,{'name':name,'pos':v['pos'],'target':v['target']})
        self.report['completed'].append('game_screenshots');self.save()

    def strict_export(self):
        self.save_building(self.d)
        path=self.out/'courtyard'/'reference_courtyard_game.nbt'
        _,root=read_typed(path);size,actual=saved_cells(root)
        expected_size,_,expected,_=nbt.expand_structure(self.d)
        mismatches=[]
        for pos,state in expected.items():
            name,props=nbt.parse_block_state(state);actual_name,actual_props=nbt.parse_block_state(actual[pos])
            if name!=actual_name or dict(props)!=dict(actual_props):
                mismatches.append({'pos':pos,'expected':state,'actual':actual[pos]})
        self.check('strict_game_saved_states',size==expected_size and not mismatches,
                   {'cells':len(expected),'mismatches':mismatches[:20],'compares':'IDs + facing + variant + part + waterlogged + source water levels; includes air'})
        self.report['completed'].append('native_structure_save');self.save()

    def restart_probe(self):
        self.set_hud(False);self.rc('save-all flush');self.stop()
        Evidence.start(self,reuse=True);self.hud_hidden=False
        samples=[]
        rows=self.spec['fixture_cells']
        for predicate in [lambda s:'part=8]' in s,lambda s:'variant=2,' in s,lambda s:s.startswith('minecraft:water'),lambda s:'facing=west' in s]:
            row=next(r for r in rows if predicate(r[3]));samples.append(row)
        for i,row in enumerate(samples):self.probe(row[:3],row[3],f'restart_state_{i}')
        self.report['completed'].append('restart_readback');self.save()

    def package(self):
        world=cs.SERVER_DIR/cs.LEVEL_NAME
        with zipfile.ZipFile(self.out/'courtyard_sample_world.zip','w',zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(world.rglob('*')):
                rel=path.relative_to(world)
                if path.is_file() and not path.is_symlink() and not any(p in ('playerdata','stats','advancements','session.lock') for p in rel.parts):
                    archive.write(path,'Moon Gate Courtyard/'+str(rel))
        logs=[self.out/f'session-{self.sessions}-client.log',self.out/'session-1-client.log']
        errors=[]
        for path in logs:
            if not path.exists():continue
            for line in path.read_text(errors='replace').splitlines():
                if 'courtyard_' in line and any(s in line.lower() for s in ['unable to load','missing texture','exception','error']):errors.append(line)
        self.check('no_courtyard_model_load_errors',not errors,errors[:20])
        self.report['completed'].append('world_export');self.save()

    def run(self):
        if ROOT!=Path('/workspace/repo'):raise RuntimeError('Only the disposable Modal checkout may run this game job')
        (self.out/'courtyard').mkdir(exist_ok=True)
        os.environ['ORG_GRADLE_PROJECT_courtyard_jei_test']='true'
        os.environ['JAVA_TOOL_OPTIONS']=(os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dmyvillage.courtyard.evidence=true').strip()
        cs.merge_options(cs.CLIENT_DIR/'options.txt',{'key_key.sneak':'key.keyboard.left.shift','key_key.inventory':'key.keyboard.e'})
        self.start();self.setup();self.screenshots()
        self.traverse('stairs',[34.5,2.5,10.5],0,2,18.5,5)
        self.traverse('moon_gate',[10.5,3,18.5],0,2,22.2,3)
        self.traverse('gallery',[20,3,28.5],90,0,5,3)
        self.traverse('side_room',[14.5,3,31],0,2,35.5,3)
        self.report['completed'].append('mapped_walks');self.save()
        self.placement();self.waterlogged_removal();self.grouped_inventory();self.strict_export();self.restart_probe()
        self.rc(f'gamemode creative {self.s.username}');self.teleport([45,-57,2])
        self.rc('save-all flush');self.stop();self.package()
        self.report['unrun']=[s for s in self.report['selected'] if s not in self.report['completed']]
        self.check('all_selected_completed',not self.report['unrun'],self.report['completed'])
        self.report['status']='pass';self.save()


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,default=ROOT/'out/preview/reference-courtyard-mc/runtime');args=parser.parse_args()
    e=CourtyardEvidence(args.out)
    try:e.run()
    except BaseException as error:
        e.report['status']='fail';e.report['failures'].append(repr(error));e.report['unrun']=[s for s in e.report['selected'] if s not in e.report['completed']];e.save();raise
    finally:
        if e.s:
            try:e.s.game.xdo('keyup','w')
            finally:e.stop()
        e.save()


if __name__=='__main__':main()
