#!/usr/bin/env python3
"""Authored combat ruins: native jigsaw templates, registered roofs and sealed cache assets."""
from __future__ import annotations
import argparse
import json
import random
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from gen_danxia_ruin import template_bytes
from buildgen.grid import BlockGrid
from buildgen.groups import get_group
from buildgen.massing import Node
from buildgen.modset import load_modset
from buildgen.ops import roof_handler
from buildgen.style import load_style
DATA=ROOT/'src/main/resources/data/myvillage'
ASSETS=ROOT/'src/main/resources/assets/myvillage'
IDS=('duange_ruin','fuyao_ruin')


def blueprint(identity):
    r=json.loads((DATA/f'ruins/{identity}.json').read_text())
    g=BlockGrid();bes={};p=r['palette'];w,h,d=r['size'];sy=r['surface_y']
    group=get_group(identity);style=load_style(group.style_id,{'minecraft'})
    style.material_slots['ROOF_DARK']=['minecraft:deepslate_tile_stairs','minecraft:deepslate_tile_slab','minecraft:dark_oak_planks']
    style.material_slots['GABLE_INFILL']=[p['wall']]
    def put(x,y,z,state):
        assert 0<=x<w and 0<=y<h and 0<=z<d,(identity,x,y,z,state)
        g.set((x,y,z),state,[],20,force=True)
    def box(a,b,state):
        for x in range(a[0],b[0]+1):
            for y in range(a[1],b[1]+1):
                for z in range(a[2],b[2]+1):put(x,y,z,state)
    def sign(pos,lines):
        x,y,z=pos;put(x,y-1,z,p['stone']);put(x,y,z,'minecraft:oak_sign[rotation=8,waterlogged=false]')
        bes[pos]={'id':'minecraft:sign','is_waxed':True,'front_text':{'messages':[json.dumps({'text':s},ensure_ascii=False) for s in lines],'color':'black','has_glowing_text':False}}
    # A filled earth shoulder, with taper only at the border. Clear the actual footprint.
    for x in range(w):
        for z in range(d):
            edge=min(x,z,w-1-x,d-1-z)
            if edge==0 and (x+z)%4!=0:continue
            top=max(0,sy-max(0,3-edge))
            for y in range(top+1):put(x,y,z,'minecraft:grass_block' if y==top else 'minecraft:stone')
            for y in range(top+1,h):put(x,y,z,'minecraft:air')
    for hall in r['halls']:
        assert hall['id'] in group.archetype_roster
        x,y,z=hall['origin'];sx,sh,sz=hall['size']
        node=Node(hall['id'],'hall',tuple(hall['origin']),tuple(hall['size']),meta={'foundation_h':y,'wall_h':sh,'roof':{'ridge_axis':'x','overhang':1,'gable_overhang':1}})
        box((x-1,0,z-1),(x+sx,y-1,z+sz),p['stone'])
        box((x,y,z),(x+sx-1,y+sh-1,z+sz-1),'minecraft:air')
        box((x,y-1,z),(x+sx-1,y-1,z+sz-1),p['floor'])
        for zz in (z,z+sz-1):box((x,y,zz),(x+sx-1,y+sh-1,zz),p['wall'])
        for xx in (x,x+sx-1):box((xx,y,z),(xx,y+sh-1,z+sz-1),p['wall'])
        for xx in range(x,x+sx,4):
            for zz in (z,z+sz-1):box((xx,y,zz),(xx,y+sh-1,zz),p['timber'])
        for zz in range(z+3,z+sz-2,4):
            for xx in (x,x+sx-1):
                box((xx,y+2,zz),(xx,y+3,zz+1),'minecraft:air')
                box((xx,y+2,zz),(xx,y+2,zz+1),'minecraft:dark_oak_fence')
        for zz in (z,z+sz-1):box((x+sx//2-2,y,zz),(x+sx//2+2,y+3,zz),'minecraft:air')
        roof_handler('chinese_overhang_gable')(g,style,random.Random(r['placement']['salt']),node,None)
    if identity=='duange_ruin':
        # Three battle spaces, separated by readable broken cross-walls, never narrow choke corridors.
        box((13,8,6),(19,21,14),'minecraft:air')
        box((11,10,43),(17,22,51),'minecraft:air')
        for x in range(6,38):
            for z in (15,27,42,55):
                if 17<=x<=25 or (z==27 and x>=32):continue
                height=2+(x//4)%2
                box((x,5,z),(x,5+height,z),p['worn'])
                if x%5==0:put(x,6+height,z,'minecraft:stone_brick_slab')
        for x in (5,37):
            for z in range(12,54):
                if 22<=z<=25 or 35<=z<=38:continue
                box((x,5,z),(x,7+(z%7==0),z),p['stone'])
        for z in range(3,44):
            for x in range(18,25):put(x,4,z,p['floor'] if (x*3+z)%7 else p['worn'])
        # Broad stair up to the command platform.
        box((10,4,43),(32,6,55),p['stone'])
        for i in range(3):
            for x in range(18,25):
                put(x,4+i,40+i,'minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
                box((x,5+i,40+i),(x,9,40+i),'minecraft:air')
        # Side structures are collapsed barracks, with long fallen beams and rubble at edges.
        for x,z in [(8,18),(31,20),(8,32)]:
            box((x,5,z),(x+3,5,z+3),p['worn']);box((x,5,z),(x,8,z+3),p['timber'])
            box((x+1,6,z+3),(x+4,6,z+3),'minecraft:dark_oak_log[axis=x]')
        for x,z in [(9,26),(33,38),(15,45),(28,53)]:
            put(x,5 if z<43 else 7,z,p['worn'])
        # Red war standards on surviving posts; no new entity or banner renderer.
        for x in (9,33):
            box((x,5,30),(x,9,30),p['timber']);box((x+1,8,30),(x+3,9,30),'minecraft:red_wool');put(x+3,8,30,'minecraft:air')
        for x,z in [(16,16),(27,26),(17,43),(25,43)]:
            y=5 if z<43 else 7;put(x,y,z,p['stone']);put(x,y+1,z,'minecraft:lantern[hanging=false,waterlogged=false]')
        sign((17,5,5),[r['name'],r['recommended'],'逐段击败三处守军','主台宝箱随之解封'])
    else:
        # Sunken sanctuary: substantial broken roof, visible ribs, intentional east-side collapse.
        box((28,12,23),(37,26,34),'minecraft:air')
        box((14,15,5),(20,26,12),'minecraft:air')
        # Wide stair from surface to the lower nave, with a continuous walking floor.
        for i in range(8):
            z=14+i;fy=11-i
            box((19,fy+1,z),(25,20,z),'minecraft:air')
            for x in range(19,26):put(x,fy,z,'minecraft:deepslate_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
            for x in (18,26):
                box((x,fy,z),(x,fy+1,z),p['stone'])
        box((19,5,22),(25,12,24),'minecraft:air');box((19,4,22),(25,4,24),p['floor'])
        # Pillars are edge cover; the main combat floor remains clear for wolf lunges.
        for x in (10,34):
            for z in (26,32,38):
                box((x,5,z),(x,10 if z!=32 else 7,z),p['stone']);put(x,11 if z!=32 else 8,z,'minecraft:chiseled_deepslate')
                put(x,6,z-1,'minecraft:soul_wall_torch[facing=north]')
        # Copper ring inlaid flush with the floor, leaving a 17-block central fighting width.
        for x in range(15,30):
            for z in range(25,36):
                if x in (15,29) or z in (25,35):put(x,4,z,'minecraft:oxidized_cut_copper')
        for x in range(20,25):
            for z in range(28,33):put(x,4,z,'minecraft:chiseled_deepslate' if (x+z)%2 else 'minecraft:oxidized_copper')
        box((17,5,37),(27,5,40),p['stone'])
        for x in range(19,26):put(x,4,36,'minecraft:deepslate_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
        # A one-block platform can be walked onto with its explicit stair at y=5.
        for x in range(19,26):put(x,5,37,'minecraft:deepslate_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
        for x in (17,27):put(x,6,39,'minecraft:soul_lantern[hanging=false,waterlogged=false]')
        for x,z in [(35,25),(36,28),(34,40),(9,39)]:put(x,5,z,p['worn'])
        sign((17,12,4),[r['name'],r['recommended'],'下殿右击封印宝箱','连续三波·击败解封'])
        sign((18,5,24),['镇妖残阵','右击后稍候开战','可撤退·进度保留','末波有精英妖狼'])
    cx,cy,cz=r['cache'];put(cx,cy,cz,'myvillage:battle_cache[facing=north,lit=false]')
    bes[(cx,cy,cz)]={'id':'myvillage:battle_cache','Kind':identity,'BattleState':'READY','Wave':0}
    # Small-scale wear only decorates the already distinct collapsed architectural masses.
    for pos,cell in list(g.iter_cells()):
        x,y,z=pos
        if cell.state==p['stone'] and (x*13+y*7+z*19)%11==0:put(x,y,z,p['worn'])
    errors=load_modset('vanilla').palette_block_errors(list({c.state for _,c in g.iter_cells()}))
    if errors:raise ValueError(errors)
    data={'size':r['size'],'blocks':[{'pos':list(pos),'state':cell.state} for pos,cell in sorted(g.iter_cells())],'author':'gen_combat_ruins.py'}
    return r,data,bes,[]

MESSAGES={
'zh_cn':{
 'block.myvillage.battle_cache':'遗迹封印宝箱','structure.myvillage.duange_ruin':'断戈营垒','structure.myvillage.fuyao_ruin':'伏妖残殿',
 'entity.myvillage.battle.guard':'%s守卫','entity.myvillage.battle.elite':'%s·精英',
 'message.myvillage.battle.dormant':'这只宝箱没有可用的遗迹封印。',
 'message.myvillage.battle.peaceful':'和平难度下试炼暂停。恢复难度后可继续，消失的守卫不计为击败。',
 'message.myvillage.battle.start':'镇妖阵已启动！稍后妖兽将现身，击败全部三波即可开启宝箱。',
 'message.myvillage.battle.locked':'宝箱仍有封印：请完成第%s/%s处战斗。',
 'message.myvillage.battle.next':'此阵已破。准备迎战第%s波，营垒守军需向前推进。',
 'message.myvillage.battle.clear':'守卫尽灭，遗迹宝箱已经解封！',
 'message.myvillage.battle.progress':'%s · 第%s/%s阵 · 剩余%s名守卫'
},
'en_us':{
 'block.myvillage.battle_cache':'Sealed Ruin Cache','structure.myvillage.duange_ruin':'Duange Garrison','structure.myvillage.fuyao_ruin':'Fuyao Sanctuary',
 'entity.myvillage.battle.guard':'%s Guardian','entity.myvillage.battle.elite':'%s Elite',
 'message.myvillage.battle.dormant':'This cache has no active ruin seal.',
 'message.myvillage.battle.peaceful':'Trials pause in Peaceful. Removed guardians are not defeats; change difficulty to resume.',
 'message.myvillage.battle.start':'The trial has begun! Defeat all three waves to unlock the cache.',
 'message.myvillage.battle.locked':'Sealed: complete encounter %s/%s.',
 'message.myvillage.battle.next':'Encounter cleared. Prepare for wave %s; advance through the garrison to meet its defenders.',
 'message.myvillage.battle.clear':'All guardians defeated. The ruin cache is unlocked!',
 'message.myvillage.battle.progress':'%s · Encounter %s/%s · %s guardians remain'
}}


def outputs():
    files={}
    def emit(path,value):files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    for identity in IDS:
        r,data,bes,entities=blueprint(identity)
        files[DATA/f'structure/{identity}.nbt']=template_bytes(data,bes,entities)
        emit(DATA/f'worldgen/structure/{identity}.json',dict(type='minecraft:jigsaw',biomes=f'#myvillage:has_{identity}',step='surface_structures',terrain_adaptation='beard_thin',spawn_overrides={},start_pool=f'myvillage:{identity}',size=1,start_height={'absolute':-r['surface_y']},project_start_to_heightmap='WORLD_SURFACE_WG',max_distance_from_center=80,use_expansion_hack=False))
        emit(DATA/f'worldgen/template_pool/{identity}.json',{'name':f'myvillage:{identity}','fallback':'minecraft:empty','elements':[{'weight':1,'element':{'element_type':'minecraft:single_pool_element','location':f'myvillage:{identity}','processors':'minecraft:empty','projection':'rigid'}}]})
        emit(DATA/f'worldgen/structure_set/{identity}.json',{'structures':[{'structure':f'myvillage:{identity}','weight':1}],'placement':{'type':'minecraft:random_spread',**r['placement']}})
        emit(DATA/f'tags/worldgen/biome/has_{identity}.json',{'replace':False,'values':r['biomes']})
    for lit in (False,True):
        elements=[]
        # Wood coffer, separate lid, bronze straps, feet and a luminous front seal.
        for a,b,tex in [([1,1,1],[15,10,15],'wood'),([1,10,1],[15,13,15],'wood'),([2,13,2],[14,15,14],'wood'),
                         ([0,0,0],[4,2,4],'metal'),([12,0,0],[16,2,4],'metal'),([0,0,12],[4,2,16],'metal'),([12,0,12],[16,2,16],'metal'),
                         ([3,2,0],[5,13,16],'metal'),([11,2,0],[13,13,16],'metal'),([6,7,0],[10,11,1],'seal')]:
            elements.append({'from':a,'to':b,'faces':{face:{'texture':'#'+tex} for face in ['up','down','north','south','east','west']}})
        emit(ASSETS/f'models/block/battle_cache_{str(lit).lower()}.json',{'textures':{'wood':'minecraft:block/dark_oak_planks','metal':'minecraft:block/oxidized_cut_copper','seal':'minecraft:block/sea_lantern' if lit else 'minecraft:block/redstone_block','particle':'minecraft:block/dark_oak_planks'},'elements':elements})
    emit(ASSETS/'blockstates/battle_cache.json',{'variants':{f'facing={face},lit={str(lit).lower()}':{'model':f'myvillage:block/battle_cache_{str(lit).lower()}','y':turn} for face,turn in [('north',0),('east',90),('south',180),('west',270)] for lit in (False,True)}})
    for lang,entries in MESSAGES.items():
        path=ASSETS/f'lang/{lang}.json';existing=json.loads(path.read_text());existing.update(entries);emit(path,existing)
    return files


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--check',action='store_true');args=ap.parse_args();bad=[]
    for path,content in outputs().items():
        if args.check:
            if not path.exists() or path.read_bytes()!=content:bad.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)
    if bad:raise SystemExit('Combat ruin resource drift: '+', '.join(bad))
    print('Combat ruin resources checked' if args.check else 'Combat ruin resources generated')
if __name__=='__main__':main()
