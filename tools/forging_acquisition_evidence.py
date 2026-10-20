#!/usr/bin/env python3
"""Natural deposit readback + mapped mining/infusion/forging, private batches and restart.

Administrator teleports, safe access tunnels, tools, ordinary copper and power are
fixtures. The new materials are only mined or infused, never given. No DevBridge.
"""
from __future__ import annotations
import argparse
import io
import json
import math
from pathlib import Path
import re
import secrets
import sys
import time
import zlib
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.forging_evidence import ForgingEvidence, MARK
from tools.alchemy_quality_evidence import read_data,SecondClient
from tools.golden_core_evidence import parse_profile
from tools.combat_capture import scene,session as cs,procs
from tools.buildgen.nbtread import _read_utf,_read_payload,TAG_COMPOUND

PROGRESS='myvillage:copper_infusion'
ORE='myvillage:xuantie_ore'
SAND='myvillage:edge_tempering_sand_deposit'
DING=(0,121,3)

def section_positions(section,cx,cz,wanted):
    """1.21 paletted block states use padded longs; palette order is not a position."""
    states=section.get('block_states',{});palette=states.get('palette',[])
    selected={i:p['Name'] for i,p in enumerate(palette) if p.get('Name') in wanted}
    if not selected:return []
    bits=max(4,(len(palette)-1).bit_length());per_long=64//bits;mask=(1<<bits)-1
    data=states.get('data',[]);result=[]
    if len(palette)>1 and len(data)!=(4096+per_long-1)//per_long:raise ValueError('Incomplete chunk palette data')
    for i in range(4096):
        index=0 if len(palette)==1 else ((data[i//per_long]&((1<<64)-1))>>((i%per_long)*bits))&mask
        if index in selected:result.append((selected[index],(cx*16+(i&15),section['Y']*16+(i>>8),cz*16+((i>>4)&15))))
    return result

def saved_deposits(region_dir,center,radius=4):
    result={ORE:[],SAND:[]};cx,cz=center[0]//16,center[1]//16
    for x in range(cx-radius,cx+radius+1):
        for z in range(cz-radius,cz+radius+1):
            path=region_dir/f'r.{x//32}.{z//32}.mca'
            if not path.exists():continue
            with path.open('rb') as f:
                f.seek(4*((x%32)+(z%32)*32));h=f.read(4);offset=int.from_bytes(h[:3],'big')
                if not offset:continue
                f.seek(offset*4096);size=int.from_bytes(f.read(4),'big');kind=f.read(1)
                if kind!=b'\x02':raise ValueError('Unexpected region compression')
                data=io.BytesIO(zlib.decompress(f.read(size-1)))
                if data.read(1)!=b'\x0a':raise ValueError('Expected chunk compound')
                _read_utf(data);chunk=_read_payload(data,TAG_COMPOUND)
                for section in chunk.get('sections',[]):
                    for block,pos in section_positions(section,x,z,result):result[block].append(pos)
    return result

class AcquisitionEvidence(ForgingEvidence):
    def __init__(self,out):
        super().__init__(out)
        self.acquisition=json.loads((ROOT/'src/main/resources/data/myvillage/forging/acquisition.json').read_text())
        self.report.update(kind='forging_acquisition',selected=['natural_deposits','mapped_mining','tool_gates','infusion_gates','partial_restart','private_batches','mapped_additive_consumption'],completed=[],
            natural_acquisition='pending',method=__doc__,owner_appearance='not_verified',owner_feel='not_verified',owner_balance='not_verified')
    def start(self,reuse=False):
        t=time.monotonic()
        state=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),reuse_world=reuse,natural_terrain=True,language=self.language,lock=str(procs.default_lock_path(ROOT))))
        self.s=cs.Session.attach(ui_check=True);self.sessions+=1
        self.report['phases'].append({'phase':'restart_setup' if reuse else 'initial_setup','wall_s':round(time.monotonic()-t,3),'capture_timings':state.get('timings',{})});self.save()
    def capture(self,name):
        # A menu capture may move its cursor; a grabbed in-world cursor is the camera.
        if self.s.game.pointer_grabbed():
            self.s.game.shot(self.out/name,max_wait=8,settle=2)
        else:super().capture(name)
    @staticmethod
    def coords(pos):return ' '.join(str(v) for v in pos)
    def look(self,pos,target,user=None,game=None,mode='survival'):
        u=user or self.s.username;g=game or self.s.game
        dx,dy,dz=target[0]-pos[0],target[1]-(pos[1]+1.62),target[2]-pos[2]
        self.rc(f'gamemode {mode} {u}');self.rc(f'tp {u} {self.coords(pos)} {math.degrees(math.atan2(-dx,dz))} {-math.degrees(math.atan2(dy,math.hypot(dx,dz)))}')
        time.sleep(.4);g.require_ingame('mapped acquisition')
    def open_table(self,game=None,user=None):
        g=game or self.s.game;u=user or self.s.username;side=-1.5 if user else .5
        self.look((side,121,.5),(.5,121.7,3.5),u,g);g.xdo('click','3')
        self.poll(g.pointer_grabbed,lambda b:b is False,8,'ding menu')
    def infusion(self,game=None,user=None):
        self.open_table(game,user);self.click(248,13,game=game)
    def block_exists(self,pos,block):return 'passed' in self.rc(f'execute if block {self.coords(pos)} {block}')
    def platform(self):
        for cmd in ['forceload add -16 -16 16 16','fill -5 120 -5 5 120 9 minecraft:stone','fill -5 121 -5 5 124 9 minecraft:air',f'setblock {self.coords(DING)} myvillage:forging_table']:
            self.rc(cmd)
    def natural(self):
        u=self.s.username;found={ORE:[],SAND:[]}
        for ox,oz in [(0,0),(2048,0),(-2048,2048)]:
            reply=self.rc(f'execute positioned {ox} 80 {oz} run locate biome minecraft:river')
            match=re.search(r'\[(-?\d+),\s*(-?\d+),\s*(-?\d+)\]',reply)
            self.check('river_located_'+str(ox),bool(match),reply);x,_,z=map(int,match.groups())
            bounds=(x-64,z-64,x+64,z+64);self.rc('forceload add '+self.coords(bounds));self.rc(f'gamemode spectator {u}');self.rc(f'tp {u} {x} 90 {z}')
            def scan():
                self.rc('save-all flush');return saved_deposits(cs.SERVER_DIR/cs.LEVEL_NAME/'region',(x,z))
            rows=self.poll(scan,lambda r:len(r[ORE])>0,90,'generated natural minerals')
            # A neighboring chunk may still be finishing its decoration when the first ore is flushed.
            if not rows[SAND]:
                try:rows=self.poll(scan,lambda r:len(r[SAND])>0,30,'river sediment')
                except RuntimeError:pass
            for key in found:found[key].extend(rows[key])
            self.report.setdefault('natural_regions',[]).append({'center':[x,z],'counts':{k:len(v) for k,v in rows.items()}});self.save()
            if found[SAND]:break
            self.rc('forceload remove '+self.coords(bounds))
        self.check('natural_ore_generated',bool(found[ORE]),len(found[ORE]))
        self.check('natural_sand_generated',bool(found[SAND]),len(found[SAND]))
        ore=sorted(found[ORE],key=lambda p:abs(p[1]+30))[0]
        sand=next((p for p in found[SAND] if self.block_exists((p[0],p[1]+1,p[2]),'minecraft:air')),found[SAND][0])
        self.report['natural_targets']={'ore':ore,'sand':sand}
        self.check('ore_in_authored_depth',all(self.acquisition['xuantie']['min_y']-9<=p[1]<=self.acquisition['xuantie']['max_y']+9 for p in found[ORE]),{'sample':ore,'count':len(found[ORE])})
        self.look((sand[0]+.5,sand[1]+3,sand[2]-3),(sand[0]+.5,sand[1]+.5,sand[2]+.5),mode='spectator');self.capture('natural-river-sand.png')
        self.report['completed'].append('natural_deposits');return ore,sand
    def mining(self,pos,block,tool,item,label,expect=True):
        u=self.s.username;x,y,z=pos
        # Clear only the approach, never the naturally generated target block.
        self.rc(f'forceload add {x-16} {z-16} {x+16} {z+16}')
        # Transparent temporary walls keep river current from changing the mapped eye ray.
        # The original target is the only unmodified opening in the front wall.
        for wall in [
            (x-2,y,z-4,x-2,y+3,z),(x+2,y,z-4,x+2,y+3,z),
            (x-1,y,z-4,x+1,y+3,z-4),(x-1,y+3,z-3,x+1,y+3,z),
            (x-1,y,z,x-1,y+2,z),(x+1,y,z,x+1,y+2,z),(x,y+1,z,x,y+2,z)]:
            self.rc('fill '+self.coords(wall)+' minecraft:glass')
        self.rc(f'fill {x-1} {y} {z-3} {x+1} {y+2} {z-1} minecraft:air')
        self.rc(f'fill {x-1} {y-1} {z-3} {x+1} {y-1} {z-1} minecraft:stone')
        self.rc(f'setblock {x-1} {y} {z-2} minecraft:torch')
        self.rc(f'item replace entity {u} hotbar.0 with {tool}');self.s.game.key('1')
        self.look((x+.5,y,z-2.5),(x+.5,y+.5,z+.5));self.capture(label+'-before.png');before=self.count(item)
        self.s.game.xdo('mousedown','1')
        try:self.poll(lambda:self.block_exists(pos,block),lambda b:not b,18,'mapped mining '+label)
        except RuntimeError:
            self.capture(label+'-failed.png');self.rc(f'data get entity {u} Pos');self.rc(f'data get entity {u} SelectedItem');raise
        finally:self.s.game.xdo('mouseup','1')
        self.rc(f'tp {u} {x+.5} {y} {z+.5}')
        if expect:
            self.poll(lambda:self.count(item),lambda n:n>before,10,'actual mined pickup '+label)
            self.check(label+'_mapped_pickup',self.count(item)>before,{'before':before,'after':self.count(item)})
        else:
            time.sleep(1);self.check(label+'_no_drop',self.count(item)==before,self.inventory())
    def mining_cases(self,ore,sand):
        self.mining(ore,ORE,'minecraft:iron_pickaxe','myvillage:raw_xuantie','natural-xuantie')
        self.mining(sand,SAND,'minecraft:iron_shovel','myvillage:edge_tempering_sand','natural-sand')
        self.report['completed'].append('mapped_mining');self.report['natural_acquisition']='natural target blocks, mapped extraction, administrative tools and safe access'
        self.platform();pos=(0,121,7)
        self.rc(f'setblock {self.coords(pos)} {ORE}')
        self.mining(pos,ORE,'minecraft:stone_pickaxe','myvillage:raw_xuantie','stone-pick-rejected',False)
        self.rc(f'setblock {self.coords(pos)} {SAND}')
        self.mining(pos,SAND,'minecraft:iron_pickaxe','myvillage:edge_tempering_sand','sand-wrong-tool',False)
        self.report['completed'].append('tool_gates')
    def fill_power(self,user=None):
        u=user or self.s.username
        cap=parse_profile(self.rc(f'myvillage cultivation info {u}'))['power_cap']
        self.rc(f'myvillage cultivation setpower {u} {cap}')
        actual=parse_profile(self.rc(f'myvillage cultivation info {u}'))['power']
        if actual!=cap:raise RuntimeError(f'Power fixture rejected: {actual} != {cap}')
        return cap
    def copper_stacks(self,user=None):return [s for s in self.inventory(user).values() if s['id']=='minecraft:copper_ingot']
    def copper_slot(self,user=None):return next(slot for slot,s in self.inventory(user).items() if s['id']=='minecraft:copper_ingot')
    def insert_copper(self,user=None,game=None):
        slot=self.copper_slot(user)
        self.click(78+(slot%9)*18,222 if slot<9 else 164+((slot-9)//9)*18,shift=True,game=game)
    def infusion_cases(self):
        self.platform();u=self.s.username
        self.rc(f'give {u} minecraft:copper_ingot 4');self.s.game.key('1')
        original=self.copper_stacks();self.infusion();self.insert_copper();self.click(120,137);time.sleep(.5);self.close_menu()
        self.check('unawakened_refused',self.copper_stacks()==original and self.count('myvillage:chitong_ingot')==0,self.inventory())
        self.rc(f'myvillage cultivation awaken {u}')
        self.infusion();self.insert_copper();self.rc(f'myvillage cultivation setpower {u} 0');self.click(120,137);time.sleep(1.2);self.capture('insufficient-qi.png');self.close_menu()
        self.check('insufficient_qi_no_partial_pulse',self.copper_stacks()==original and self.count('myvillage:chitong_ingot')==0,self.copper_stacks())
        cap=self.fill_power();self.infusion();self.insert_copper();self.click(120,137)
        self.poll(lambda:self.profile()['power'],lambda n:n<=cap-8,8,'two paid pulses');self.click(220,137);self.capture('paused-infusion-zh.png');self.close_menu()
        stacks=self.copper_stacks();paid=stacks[0].get('components',{}).get(PROGRESS,0)
        self.check('pause_keeps_paid_batch',len(stacks)==1 and stacks[0]['count']==4 and 0<paid<self.acquisition['infusion']['qi_per_ingot'],stacks)
        self.check('exact_qi_per_ingot',self.profile()['power']==cap-4*paid,{'paid':paid,'power':self.profile()['power']})
        self.report['completed'].append('infusion_gates')
        before=self.inventory();self.rc('save-all flush');self.stop();self.language='en_us';self.start(reuse=True)
        self.check('partial_batch_survives_restart',self.inventory()==before,self.inventory());self.report['completed'].append('partial_restart')
        with SecondClient(self) as guest:
            v=guest.username;g=guest.game
            self.rc(f'op {v}');self.rc(f'myvillage cultivation awaken {v}');self.fill_power(v);self.rc(f'clear {v}')
            self.rc(f'give {v} minecraft:copper_ingot 2')
            owner_before=self.inventory();self.infusion(g,v);self.insert_copper(v,g);self.click(120,137,game=g)
            cap=self.fill_power();self.infusion();self.insert_copper();self.capture('resumed-infusion-en.png');self.click(120,137)
            remaining=4*(self.acquisition['infusion']['qi_per_ingot']-paid)
            self.poll(lambda:self.profile()['power'],lambda n:n<=cap-remaining,12,'owner resumed batch completed')
            self.capture('completed-infusion-en.png');self.click(208,64,shift=True);self.close_menu()
            self.check('owner_exact_batch_output',self.count('myvillage:chitong_ingot')==4 and self.count('minecraft:copper_ingot')==0,self.inventory())
            self.check('resume_pays_only_remaining',self.profile()['power']==cap-remaining,{'power':self.profile()['power'],'remaining':remaining})
            time.sleep(3);self.click(208,64,shift=True,game=g);self.close_menu(g)
            self.check('guest_exact_private_output',self.count('myvillage:chitong_ingot',v)==2 and self.count('minecraft:copper_ingot',v)==0,self.inventory(v))
            self.check('owner_natural_materials_not_shared',all(self.count(item)==sum(s['count'] for s in owner_before.values() if s['id']==item) for item in ['myvillage:raw_xuantie','myvillage:edge_tempering_sand']),self.inventory())
        self.report['completed'].append('private_batches');self.report['multiplayer']='bounded two-client private-batch checks'
    def forge_acquired(self):
        u=self.s.username;self.rc(f'myvillage cultivation crafting set {u} forging 0')
        self.rc(f'give {u} myvillage:pattern_qingfeng_sword')
        pattern_slot=next(slot for slot,stack in self.inventory().items() if stack['id']=='myvillage:pattern_qingfeng_sword')
        if not 0<=pattern_slot<9:raise RuntimeError('Pattern fixture needs a free hotbar slot')
        self.look((.5,121,.5),(.5,130,-4));self.s.game.key(str(pattern_slot+1));self.s.game.xdo('click','3')
        self.poll(lambda:self.learned(),lambda ids:'qingfeng_sword' in ids,8,'mapped pattern use');self.s.game.key('1')
        for trait,material in [('sharp','edge_tempering_sand'),('channeling','chitong_ingot'),('tough','xuantie_ingot')]:
            rid='myvillage:'+material;before=self.count(rid)
            for item,count in self.rules['recipes'][0]['costs'].items():self.rc(f'give {u} {item} {count}')
            self.open_table();self.click(50,94+['sharp','channeling','tough'].index(trait)*22);self.click(152,137);self.capture('acquired-'+trait+'.png');self.close_menu()
            products=[s for s in self.inventory().values() if s['id']=='myvillage:qingfeng_sword' and s.get('components',{}).get(MARK,{}).get('trait')==trait]
            self.check('acquired_material_forges_'+trait,self.count(rid)==before-1 and len(products)==1,{'before':before,'after':self.count(rid),'products':products})
        self.report['completed'].append('mapped_additive_consumption')
    def visual_preview(self):
        self.report['selected']=['staged_visuals'];self.start();u=self.s.username
        for cmd in scene.world_rules():self.rc(cmd)
        self.rc('myvillage world pause');self.platform()
        for cmd in ['fill -4 121 3 -2 121 5 myvillage:xuantie_ore',
                    'setblock -3 122 4 myvillage:xuantie_ore',
                    'fill 2 121 3 4 121 5 myvillage:edge_tempering_sand_deposit']:
            self.rc(cmd)
        for name,pos,target in [
            ('material-display.png',(6,124,-4),(.5,121.7,4)),
            ('xuantie-display.png',(-5,123,.5),(-2.5,121.7,4)),
            ('sand-display.png',(4,123,.5),(3,121.3,4))]:
            self.look(pos,target,mode='spectator');self.s.game.key('F1')
            try:self.capture(name)
            finally:self.s.game.key('F1')
        self.report['completed']=['staged_visuals'];self.report['status']='pass'
    def run(self):
        self.start();u=self.s.username
        for cmd in scene.world_rules():self.rc(cmd)
        for cmd in ['myvillage world pause',f'op {u}',f'clear {u}',f'effect give {u} minecraft:resistance infinite 4 true',f'effect give {u} minecraft:water_breathing infinite 0 true']:
            self.rc(cmd)
        ore,sand=self.natural();self.mining_cases(ore,sand);self.infusion_cases();self.forge_acquired()
        self.report['status']='pass'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--visual-only',action='store_true');p.add_argument('--out',type=Path,default=ROOT/'out/preview/forging_acquisition/runtime');a=p.parse_args();e=AcquisitionEvidence(a.out)
    try:
        if a.visual_only:e.visual_preview()
        else:e.run()
    except BaseException as error:e.report['status']='fail';e.report['failures'].append(repr(error));e.save();raise
    finally:
        if e.s:e.stop()
        e.save()
if __name__=='__main__':main()
