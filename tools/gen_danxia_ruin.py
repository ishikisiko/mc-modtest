#!/usr/bin/env python3
"""Danxia ruined monastery: authored semantic halls, registered roofs, native template.

Owns only the danxia structure/worldgen/core model/messages. The scoped NBT assembly
below attaches the known chest/sign/core payloads; the generic DSL is unchanged.
"""
from __future__ import annotations
import argparse
from collections import OrderedDict
import gzip
import io
import json
from pathlib import Path
import random
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import json_to_nbt as nbt
from buildgen.grid import BlockGrid
from buildgen.groups import get_group
from buildgen.massing import Node
from buildgen.modset import load_modset
from buildgen.ops import roof_handler
from buildgen.style import load_style

DATA = ROOT / 'src/main/resources/data/myvillage'
ASSETS = ROOT / 'src/main/resources/assets/myvillage'
RULES = DATA / 'ruins/danxia.json'


def tag(value):
    if isinstance(value, dict):
        return nbt.Compound(OrderedDict((k, tag(v)) for k, v in value.items()))
    if isinstance(value, list):
        children = [tag(v) for v in value]
        kind = children[0].tag_id if children else nbt.TAG_END
        if any(c.tag_id != kind for c in children):
            raise ValueError('mixed NBT list')
        return nbt.ListTag(kind, children)
    if isinstance(value, bool): return nbt.Byte(int(value))
    if isinstance(value, int): return nbt.Int(value)
    if isinstance(value, float): return nbt.Double(value)
    return nbt.String(str(value))


def blueprint():
    r = json.loads(RULES.read_text())
    group = get_group('danxia_ruin')
    style = load_style(group.style_id, {'minecraft'})
    style.material_slots['ROOF_DARK'] = r['roof_materials']
    style.material_slots['GABLE_INFILL'] = [r['palette']['wall']]
    g = BlockGrid()
    bes = {}
    p = r['palette']
    w, h, d = r['size']
    sy = r['surface_y']
    def put(x, y, z, state):
        assert 0 <= x < w and 0 <= y < h and 0 <= z < d, (x,y,z,state)
        g.set((x,y,z),state,[],20,force=True)
    def box(a,b,state):
        for x in range(a[0],b[0]+1):
            for y in range(a[1],b[1]+1):
                for z in range(a[2],b[2]+1): put(x,y,z,state)
    # A low, irregular earthen shoulder blends into the world; no square slab skirt.
    for x in range(w):
        for z in range(d):
            radius = ((x-20)/20)**2 + ((z-23)/23)**2
            if radius > 1.12: continue
            top = sy if 4 <= x <= 36 and 4 <= z <= 41 else sy - 1 - int(max(0,radius-.65)*5)
            for y in range(top+1): put(x,y,z,p['surface'] if y==top else p['earth'])
            # Clear bounded space only above the occupied mound, not an entire cuboid.
            for y in range(top+1,h): put(x,y,z,'minecraft:air')
    # Process semantic halls independently, retaining posts and traversable thresholds.
    for hall in r['halls']:
        assert hall['id'] in group.archetype_roster
        x,y,z = hall['origin']; sx,wall_h,sz = hall['size']
        node = Node(hall['id'],'hall',tuple(hall['origin']),tuple(hall['size']),
                    meta={'foundation_h':y,'wall_h':wall_h,'roof':{'ridge_axis':'x','overhang':1,'gable_overhang':1}})
        box((x-1,y-1,z-1),(x+sx,y-1,z+sz),p['stone'])
        for xx in range(x,x+sx):
            for zz in range(z,z+sz):
                put(xx,y-1,zz,p['floor'])
                if xx in (x,x+sx-1) or zz in (z,z+sz-1):
                    for yy in range(y,y+wall_h): put(xx,yy,zz,p['wall'])
        for xx in (x,x+sx//2,x+sx-1):
            for zz in (z,z+sz-1): box((xx,y,zz),(xx,y+wall_h-1,zz),p['timber'])
        # Exposed timber rhythm and open lattice windows prevent blank white boxes.
        for xx in range(x+3,x+sx-2,4):
            for zz in (z,z+sz-1):
                box((xx,y+1,zz),(xx+1,y+2,zz),'minecraft:air')
                put(xx,y+1,zz,'minecraft:dark_oak_fence[north=false,south=false,east=true,west=true,waterlogged=false]')
        for zz in range(z+3,z+sz-2,4):
            for xx in (x,x+sx-1):
                box((xx,y+1,zz),(xx,y+2,zz+1),'minecraft:air')
                box((xx,y,zz+2),(xx,y+wall_h-1,zz+2),p['timber'])
        # Door centered in both facades, with supported exposed eaves.
        for zz in (z,z+sz-1):
            box((x+sx//2-1,y,zz),(x+sx//2+1,y+2,zz),'minecraft:air')
        roof_handler(hall['roof'])(g,style,random.Random(72416),node,None)
    # Deliberate collapse shapes: the gate's left gable, main hall's western quarter,
    # and the wing's rear. Ruin silhouette is not random holes in an intact box.
    for a,b in [((13,12,4),(17,22,10)),((12,11,29),(17,24,36)),((28,12,23),(36,23,29))]:
        box(a,b,'minecraft:air')
    for x,z in [(14,31),(16,32),(12,34),(17,35),(30,25),(32,27),(15,6)]:
        put(x,sy+1,z,p['cracked']); put(x+1,sy,z,p['worn'])
    for x in range(8,13): put(x,10,33,'minecraft:dark_oak_log[axis=x]')
    # Boundary survives in fragments, with broad openings and no invisible barriers.
    for x in range(5,36):
        for z in (12,40):
            if 17 <= x <= 23 or (x+z)%11 < 3: continue
            for y in range(10,12+(x%3==0)): put(x,y,z,p['worn'])
    for z in range(13,40):
        if z in range(19,24): continue
        for y in range(10,12): put(5,y,z,p['worn'])
    # Broken stone path from gate to hall, branch to garden and side wing.
    for z in range(3,41):
        for x in range(19,22): put(x,sy,z,p['cracked'] if (x+z)%7==0 else p['floor'])
    for x in range(7,35):
        for z in (15,16): put(x,sy,z,p['floor'])
    # Dry irrigation channel directs attention from the garden toward the descent.
    for z in range(17,31):
        put(15,sy,z,'minecraft:cut_copper_slab[type=bottom,waterlogged=false]')
        put(14,sy,z,p['worn']); put(16,sy,z,p['worn'])
    for plant in r['garden']:
        x,y,z = plant['pos']; put(x,y-1,z,'minecraft:coarse_dirt'); put(x,y,z,'minecraft:dead_bush')
    # A few living perimeter herbs are free to collect without spending the array.
    for x,z,crop in [(7,15,'spirit_leaf'),(12,15,'dew_blossom')]:
        put(x,sy,z,'myvillage:spirit_soil'); put(x,sy+1,z,f'myvillage:{crop}_crop[age=2]')
    # Underground chamber shell and vault. Floor is y=2, headroom y=3..6.
    box((17,1,25),(33,7,39),p['stone'])
    box((18,3,26),(32,6,38),'minecraft:air')
    box((17,2,25),(33,2,39),p['floor'])
    # Two corridors separated by load-bearing stone, open door to the inner archive.
    box((25,3,26),(25,6,38),p['cracked'])
    box((25,3,31),(25,5,33),'minecraft:air')
    # Descending stair enters from the path at z=18, lands at z=25 (y=3).
    for i in range(7):
        z=18+i; floor=8-i
        box((19,floor+1,z),(21,12,z),'minecraft:air')
        for x in range(19,22):
            put(x,floor,z,'minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
    box((19,3,25),(21,5,26),'minecraft:air')
    # Natural-looking side breach gives an alternate route into the chamber.
    # This is a collapsed vertical opening beside the rear hall, with a vine ladder.
    box((32,3,36),(34,14,38),'minecraft:air')
    for y in range(3,14):
        put(35,y,37,p['earth'])
        put(34,y,37,'minecraft:vine[east=true,north=false,south=false,west=false,up=false]')
    for x,y,z in [(18,5,27),(23,5,36),(30,5,36)]:
        put(x,y,z,'minecraft:wall_torch[facing=east]')
        put(x-1,y,z,p['stone'])
    # Ritual source, archive storage, and a working furnace above the old chamber.
    cx,cy,cz=r['core']; put(cx,cy,cz,'myvillage:ruin_core[facing=north,lit=true]')
    bes[(cx,cy,cz)]={'id':'myvillage:ruin_core','Choice':'CHARGED'}
    vx,vy,vz=r['vault']; put(vx,vy,vz,'minecraft:barrel[facing=up,open=false]')
    bes[(vx,vy,vz)]={'id':'minecraft:barrel'}
    put(24,10,36,'myvillage:alchemy_furnace[facing=north,lit=false]')
    put(31,10,18,'minecraft:barrel[facing=up,open=false]')
    bes[(31,10,18)]={'id':'minecraft:barrel','LootTable':'myvillage:chests/danxia_outer'}
    def sign(pos, lines):
        x,y,z=pos
        put(x,y,z,'minecraft:oak_sign[rotation=8,waterlogged=false]')
        put(x,y-1,z,p['stone'])
        bes[pos]={'id':'minecraft:sign','is_waxed':True,'front_text':{'messages':[json.dumps({'text':s},ensure_ascii=False) for s in lines],'color':'black','has_glowing_text':False}}
    sign((18,10,11),['丹霞旧观','荒园尚存余根','丹室深藏残阵','循铜渠下行'])
    sign((22,3,28),['残泉仅余一用','空手右击：药园','潜行右击：内库','再次同击确认'])
    sign((28,3,35),['内库墨迹已散','引残泉可复原','青木长春功','凝神丹方'])
    # Deterministic low-frequency decay only after architectural collapse.
    protected = set(bes) | {tuple(e['pos']) for e in r['garden']}
    for pos, cell in list(g.iter_cells()):
        x,y,z=pos
        if pos in protected: continue
        if cell.state==p['stone'] and (x*13+y*7+z*19)%9==0:
            put(x,y,z,p['worn'])
        if cell.state==p['surface'] and g.state_at((x,y+1,z))=='minecraft:air' and (x*23+z*13)%17==0:
            put(x,y+1,z,'minecraft:short_grass')
    entities=[]
    for index, guardian in enumerate(r['guardians']):
        pos=guardian['pos']
        entities.append({'pos':pos,'blockPos':[int(c) for c in pos],
                         'nbt':{'id':'myvillage:demon_wolf','PersistenceRequired':True,
                                'Tags':['danxia_guardian'], 'NeoForgeData':{'myvillage:ruin_guardian':{'index':index,'pending':True}}}})
    errors=load_modset('vanilla').palette_block_errors(list({c.state for _,c in g.iter_cells()}))
    if errors: raise ValueError(errors)
    blocks=[{'pos':list(pos),'state':cell.state} for pos,cell in sorted(g.iter_cells())]
    return r, {'size':r['size'],'blocks':blocks,'author':'gen_danxia_ruin.py'}, bes, entities


def template_bytes(data, bes, entities):
    root=nbt.structure_json_to_root_nbt(data)
    for entry in root.value['blocks'].value:
        pos=tuple(t.value for t in entry.value['pos'].value)
        if pos in bes: entry.value['nbt']=tag(bes[pos])
    root.value['entities']=nbt.ListTag(nbt.TAG_COMPOUND,[tag(e) for e in entities])
    # Reuse the canonical encoder; fixed gzip metadata keeps regeneration deterministic.
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        path=Path(tmp)/'structure.nbt'; nbt.write_gzipped_nbt(root,str(path))
        raw=gzip.decompress(path.read_bytes())
    return gzip.compress(raw,mtime=0)


MESSAGES={
 'zh_cn': {
  'block.myvillage.ruin_core':'丹霞残阵',
  'message.myvillage.ruin.confirm_garden':'残泉仅余一次：复苏药园，得到十二株成熟灵植与灵土。空手再次右击确认；潜行右击可改选内库。',
  'message.myvillage.ruin.confirm_vault':'残泉仅余一次：复原内库中的《青木长春功》、凝神丹方与灵石。保持潜行，空手再次右击确认；松开潜行可改选药园。',
  'message.myvillage.ruin.spent':'残阵灵气已尽，无法再次激活。',
  'message.myvillage.ruin.unloaded':'灵路远端尚未加载。请先走近药园与内库，再返回残阵。',
  'message.myvillage.ruin.garden_changed':'药园阵位已被改动：请恢复枯株下的砂土，并清空株位。残泉尚未消耗。',
  'message.myvillage.ruin.vault_changed':'内库木桶已被移走或占用。请恢复原位的空木桶。残泉尚未消耗。',
  'message.myvillage.ruin.invalid':'传承数据不可用，残泉尚未消耗。',
  'message.myvillage.ruin.garden':'残泉流向地表，旧药园重新萌发。内库传承随余烬散去。',
  'message.myvillage.ruin.vault':'墨迹在内库木桶中重新凝聚。药园的余根归于沉寂。'
 },
 'en_us': {
  'block.myvillage.ruin_core':'Danxia Residual Array',
  'message.myvillage.ruin.confirm_garden':'One charge remains: restore twelve mature spirit plants and their soil. Right-click again with an empty hand to confirm; sneak-right-click to choose the archive instead.',
  'message.myvillage.ruin.confirm_vault':'One charge remains: restore the Qingmu Changchun manual, Ningshen formula and spirit stones in the archive. Sneak-right-click again with an empty hand to confirm.',
  'message.myvillage.ruin.spent':'The array is exhausted. It cannot be activated again.',
  'message.myvillage.ruin.unloaded':'The far end is not loaded. Visit the garden and archive before returning.',
  'message.myvillage.ruin.garden_changed':'Garden sites were altered. Restore the coarse dirt beneath the withered plants and clear their spaces. No charge spent.',
  'message.myvillage.ruin.vault_changed':'Restore an empty barrel at the original archive position. No charge spent.',
  'message.myvillage.ruin.invalid':'The heritage data is unavailable. No charge spent.',
  'message.myvillage.ruin.garden':'The old garden blooms again. The archive has lost its remaining power.',
  'message.myvillage.ruin.vault':'The archive barrel holds the restored writings. The old garden falls dormant.'
 }
}


def outputs():
    r,data,bes,entities=blueprint()
    files={DATA/'structure/danxia_ruin.nbt':template_bytes(data,bes,entities)}
    def emit(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    emit(DATA/'worldgen/structure/danxia_ruin.json',{
        'type':'minecraft:jigsaw','biomes':'#myvillage:has_danxia_ruin','step':'surface_structures',
        'terrain_adaptation':'beard_thin','spawn_overrides':{},'start_pool':'myvillage:danxia_ruin',
        'size':0,'start_height':{'absolute':-r['surface_y']},'project_start_to_heightmap':'WORLD_SURFACE_WG',
        'max_distance_from_center':64,'use_expansion_hack':False})
    emit(DATA/'worldgen/template_pool/danxia_ruin.json',{
        'name':'myvillage:danxia_ruin','fallback':'minecraft:empty','elements':[{'weight':1,'element':{
            'element_type':'minecraft:single_pool_element','location':'myvillage:danxia_ruin',
            'processors':'minecraft:empty','projection':'rigid'}}]})
    emit(DATA/'worldgen/structure_set/danxia_ruin.json',{
        'structures':[{'structure':'myvillage:danxia_ruin','weight':1}],
        'placement':{'type':'minecraft:random_spread',**r['placement']}})
    emit(DATA/'tags/worldgen/biome/has_danxia_ruin.json',{'replace':False,'values':r['biomes']})
    emit(DATA/'loot_table/chests/danxia_outer.json',{'type':'minecraft:chest','pools':[{'rolls':2,'entries':[
        {'type':'minecraft:item','name':name,'functions':[{'function':'minecraft:set_count','count':count}]}
        for name,count in [('myvillage:low_grade_spirit_stone',3),('myvillage:spirit_leaf',2),('myvillage:dew_blossom',2)]]}]})
    for lit in (False,True):
        elements=[]
        for a,b,texture in [([0,0,0],[16,3,16],'base'),([2,3,2],[14,9,14],'stone'),
                            ([0,9,0],[16,11,16],'base'),([5,11,5],[11,14,11],'core')]:
            elements.append({'from':a,'to':b,'faces':{face:{'texture':'#'+texture} for face in ['up','down','north','south','east','west']}})
        emit(ASSETS/f'models/block/ruin_core_{str(lit).lower()}.json',{'textures':{
            'base':'minecraft:block/oxidized_cut_copper','stone':'minecraft:block/chiseled_stone_bricks',
            'core':'minecraft:block/sea_lantern' if lit else 'minecraft:block/deepslate_tiles',
            'particle':'minecraft:block/chiseled_stone_bricks'},'elements':elements})
    emit(ASSETS/'blockstates/ruin_core.json',{'variants':{
        f'facing={face},lit={str(lit).lower()}':{'model':f'myvillage:block/ruin_core_{str(lit).lower()}','y':turn}
        for face,turn in [('north',0),('east',90),('south',180),('west',270)] for lit in (False,True)}})
    for lang,entries in MESSAGES.items():
        path=ASSETS/f'lang/{lang}.json'; existing=json.loads(path.read_text()); existing.update(entries); emit(path,existing)
    return files


def main():
    ap=argparse.ArgumentParser(description=__doc__); ap.add_argument('--check',action='store_true'); args=ap.parse_args()
    bad=[]
    for path,content in outputs().items():
        if args.check:
            if not path.exists() or path.read_bytes()!=content: bad.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(content)
    if bad: raise SystemExit('Danxia generated resource drift: '+', '.join(bad))
    print('Danxia resources checked' if args.check else 'Danxia resources generated')

if __name__=='__main__': main()
