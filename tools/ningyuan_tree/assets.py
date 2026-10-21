"""Pixel materials, modeled fruits and the complete Minecraft wood family.

vanilla_templates.json contains unmodified 1.21.1 blockstate/model templates.
Only generic Minecraft geometry is reused; all Ningyuan textures are authored here.
"""
from __future__ import annotations
import json
import random
from pathlib import Path
from tools.gen_alchemy_assets import png
from tools.gen_spirit_crops import BLOCK_ITEM_DISPLAY

ROOT=Path(__file__).resolve().parents[2]
RES=Path('src/main/resources')
ASSET=RES/'assets/myvillage'
DATA=RES/'data'
NAMES={
 'ningyuan_log':'凝元原木','ningyuan_wood':'凝元木','stripped_ningyuan_log':'去皮凝元原木',
 'stripped_ningyuan_wood':'去皮凝元木','ningyuan_planks':'凝元木板','ningyuan_stairs':'凝元木楼梯',
 'ningyuan_slab':'凝元木台阶','ningyuan_fence':'凝元木栅栏','ningyuan_fence_gate':'凝元木栅栏门',
 'ningyuan_door':'凝元木花格门','ningyuan_trapdoor':'凝元木花格活板门','ningyuan_button':'凝元木按钮',
 'ningyuan_pressure_plate':'凝元木压力板','ningyuan_sapling':'凝元树苗','ningyuan_leaves':'凝元树叶',
 'ningyuan_fruit_cluster':'凝元挂果枝','potted_ningyuan_sapling':'凝元树苗盆栽',
 'ningyuan_sign':'凝元木告示牌','ningyuan_wall_sign':'壁挂凝元木告示牌',
 'ningyuan_hanging_sign':'悬挂式凝元木告示牌','ningyuan_wall_hanging_sign':'壁挂悬挂式凝元木告示牌',
 'ningyuan_boat':'凝元木船','ningyuan_chest_boat':'凝元木运输船','ningyuan_fertilizer':'培元肥',
 'ningyuan_lattice':'凝元木嵌玉花格','jade_tree_pool_border':'青玉树池边石','ningyuan_fruit_lantern':'凝元果灯',
}
NO_ITEM={'potted_ningyuan_sapling','ningyuan_wall_sign','ningyuan_wall_hanging_sign'}
NON_BLOCK={'ningyuan_boat','ningyuan_chest_boat','ningyuan_fertilizer'}
LOGS=['ningyuan_log','ningyuan_wood','stripped_ningyuan_log','stripped_ningyuan_wood']
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def color(base,n=0,alpha=255):return tuple(max(0,min(255,c+n)) for c in base)+(alpha,)
def noise(x,y):return (x*17+y*31+x*y*7)%13-6

def leaf_texture():
    rng=random.Random(517);leaves=[]
    for y in range(-5,37,6):
        for x in range(-6,38,7):
            leaves.append((x+rng.randrange(-2,3),y+rng.randrange(-2,3),rng.choice((-1,1)),rng.randrange(4)))
    palette=((49,86,58),(62,103,66),(76,120,80),(57,96,65))
    rows=[]
    for y in range(32):
        row=[]
        for x in range(32):
            pixel=(0,0,0,0)
            for cx,cy,sign,tint in leaves:
                dx=x-cx;dy=y-cy
                along=(dx+sign*dy)*.71;across=(dy-sign*dx)*.71
                if abs(along)/6.1+abs(across)/3.8<1:
                    shade=noise(x,y)+(8 if abs(across)<.6 else -6 if across>1 else 0)
                    pixel=color(palette[tint],shade)
            row.append(pixel)
        rows.append(row)
    return rows

def material(kind,size=32):
    if kind=='leaves':return leaf_texture()
    rows=[]
    for y in range(size):
        row=[]
        for x in range(size):
            u,v=x%32,y%32;n=noise(x,y);alpha=255
            if kind=='bark':
                base=(83,77,62);n+=(-20 if (u+v//11)%9 in (0,1) else 8 if (u+v//11)%9==2 else 0)
                if (u*13+v*3)%97<5:base=(98,111,74)
            elif kind in ('end','stripped_end'):
                ring=max(abs(u-15.5),abs(v-15.5));base=(172,148,103)
                n+=-22 if int(ring)%4==0 else 5
                if kind=='end' and ring>12:base=(80,75,61);n-=5
            elif kind=='stripped':
                base=(172,150,108);n+=(u%6-3)*2
            elif kind=='planks':
                base=(161,143,106);n+=-28 if v%8==0 else 9 if v%8==1 else -8 if (u+(v//8%2)*16)%32==0 else 0
                if (u*3+v)%17==0:n-=8
            elif kind=='leaf_blade':
                base=(68,118,76);n+=10 if abs(u-v)<2 else -9 if u>v+10 else 0
            elif kind in ('door_top','door_bottom','trapdoor','lattice'):
                base=(145,124,84)
                border=u<3 or u>28 or v<3 or v>28
                if kind=='door_bottom':
                    base=(158,139,98);n+=-14 if u in (4,27) or v in (5,26) else 0
                    if 10<u<21 and 10<v<21 and abs(u-15.5)+abs(v-15.5)<6:base=(81,130,112)
                    if u>25 and v<5:base=(101,82,48);n=12
                elif not border:
                    alpha=255 if (u+v)%12 in (0,1) or (u-v)%12 in (0,1) else 0
                else:n-=14
                if kind=='lattice' and (u in (2,3,28,29) or v in (2,3,28,29)):base=(73,121,103)
            elif kind=='jade':
                base=(83,137,123);n+=12 if u in (1,2,29,30) or v in (1,2,29,30) else -12 if u in (0,31) or v in (0,31) else 0
                if (u+v*2)%37==0:n+=14
            elif kind=='jade_side':
                base=(68,112,101);n+=15 if v in (0,1,14,15,30,31) else -8 if v in (3,18) else 0
            elif kind=='bronze':
                base=(112,91,51);n+=20 if v in (2,3) else -15 if u%8==0 else 0
            elif kind=='fruit_low':
                base=(103,157,71);n+=20 if u<12 and v<12 else -14 if u>23 else 0
                if (u+2*v)%19 in (0,1):base=(145,180,89)
            elif kind=='fruit_high':
                base=(132,91,163);n+=20 if u<12 and v<12 else -16 if u>24 else 0
                if u in (8,9) and 6<v<25:base=(198,175,107)
            elif kind=='bud':base=(166,190,135);n+=14 if v<12 else -10
            elif kind=='soil':base=(72,57,39)
            else:raise ValueError(kind)
            row.append(color(base,n,alpha))
        rows.append(row)
    return rows

def entity_texture(width,height,hanging=False,boat=False):
    rows=[]
    for y in range(height):
        row=[]
        for x in range(width):
            base=(160,141,101);n=noise(x,y)
            n+=-19 if y%8==0 else 5 if y%8==1 else (x%5-2)
            # Quiet celadon edging and bronze fasteners, also visible on oars and sign chains.
            if y in (0,1,14,15):base=(67,115,102)
            if x%24 in (1,2) and y%16 in (3,4):base=(121,99,54);n=10
            if hanging and x>=32 and y<16:base=(111,93,57)
            row.append(color(base,n))
        rows.append(row)
    return rows

def icon(kind):
    rows=[[(0,0,0,0) for _ in range(32)] for _ in range(32)]
    def rect(x0,y0,x1,y1,c):
        for y in range(y0,y1):
            for x in range(x0,x1):rows[y][x]=color(c,noise(x,y))
    if kind=='fertilizer':
        rect(8,12,24,27,(130,114,77));rect(10,9,22,13,(165,144,95));rect(12,6,20,10,(103,84,48))
        rect(10,16,22,23,(68,119,98));rect(15,15,17,24,(184,169,109));rect(12,18,20,20,(184,169,109))
    elif kind=='sign':
        rect(14,20,18,29,(101,84,55));rect(4,4,28,21,(164,143,98));rect(5,5,27,7,(77,123,106))
    elif kind=='hanging_sign':
        for x in (6,24):rect(x,2,x+2,10,(120,100,58))
        rect(3,9,29,27,(164,143,98));rect(4,10,28,12,(77,123,106))
    else:
        rect(3,14,29,24,(151,130,89));rect(6,24,26,27,(106,87,56));rect(5,11,27,15,(183,160,113))
        rect(7,15,25,21,(72,109,95));rect(1,9,4,24,(177,153,107));rect(28,9,31,24,(177,153,107))
        if kind=='chest_boat':
            rect(11,7,23,19,(135,109,64));rect(11,8,23,10,(81,68,43));rect(16,11,19,15,(185,161,94))
    return rows

def box(a,b,texture,uv=None):
    return {'from':list(a),'to':list(b),'faces':{side:{'texture':'#'+texture,'uv':uv or [0,0,16,16]} for side in ('up','down','north','south','east','west')}}
def model(elements,textures=None):
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','ambientocclusion':True,
            'textures':textures or {k:f'myvillage:block/ningyuan_{k}' for k in ('bark','leaves','leaf_blade','fruit_low','fruit_high','bud','bronze','jade','soil')},
            'elements':elements}
def fruit_model(age,enriched):
    elements=[box((7.6,11,7.6),(8.4,16,8.4),'bark'),
              box((5,12,7),(8,12.4,10),'leaves'),box((8,13,6),(11,13.4,9),'leaves')]
    if age==0:
        elements += [box((6.6,9,6.6),(9.4,11,9.4),'bud'),box((7.3,8.6,7.3),(8.7,9.2,8.7),'bronze' if enriched else 'bud')]
    else:
        w=2 if age==1 else 3;top=11;bottom=7 if age==1 else 4
        tex='fruit_high' if enriched and age==2 else 'fruit_low'
        elements += [box((8-w,bottom+1,8-w),(8+w,top-1,8+w),tex),
                     box((9-w,bottom,9-w),(7+w,top,7+w),tex),
                     box((7,top,7),(9,top+.3,9),'leaves')]
    result=model(elements);result['textures']['particle']='myvillage:block/ningyuan_leaves'
    return result
def sapling_model():
    elements=[box((7.65,0,7.65),(8.35,10.5,8.35),'bark')]
    for y,length,axis in ((3.5,4.3,'x'),(6.7,3.5,'z'),(9.3,2.5,'x')):
        for sign in (-1,1):
            for i,width in enumerate((1.3,2.2,.8)):
                lo,hi=sorted((sign*length*i/3,sign*length*(i+1)/3))
                a=(8+lo,y,8-width/2) if axis=='x' else (8-width/2,y,8+lo)
                b=(8+hi,y+.35,8+width/2) if axis=='x' else (8+width/2,y+.35,8+hi)
                e=box(a,b,'leaf_blade');e['rotation']={'origin':[8,y,8],'axis':'z' if axis=='x' else 'x','angle':sign*(22.5 if axis=='x' else -22.5),'rescale':False};elements.append(e)
    result=model(elements);result['textures']['particle']='myvillage:block/ningyuan_leaves';return result
def potted_model():
    pot=json.loads((Path(__file__).parent/'flower_pot_template.json').read_text());elements=pot['elements']
    for e in sapling_model()['elements']:
        e=dict(e);e['from']=[(v-8)*.65+8 if i!=1 else v*.65+6 for i,v in enumerate(e['from'])]
        e['to']=[(v-8)*.65+8 if i!=1 else v*.65+6 for i,v in enumerate(e['to'])]
        if 'rotation' in e:
            rot=dict(e['rotation']);rot['origin']=[(v-8)*.65+8 if i!=1 else v*.65+6 for i,v in enumerate(rot['origin'])];e['rotation']=rot
        elements.append(e)
    result=model(elements);result['textures'].update({k:'minecraft:'+v for k,v in pot['textures'].items()});return result
def lantern_model(hanging=False):
    elements=[box((5.5,0,5.5),(10.5,1,10.5),'bronze'),box((6,1,6),(10,2,10),'bronze'),
              box((5.5,2,5.5),(10.5,3.5,10.5),'jade'),box((4.5,3.5,4.5),(11.5,8.5,11.5),'jade'),
              box((5.5,8.5,5.5),(10.5,10,10.5),'jade'),box((5,10,5),(11,11,11),'bronze'),
              box((6,11,6),(10,12,10),'jade'),box((7,12,7),(9,12.5,9),'bronze')]
    for side in range(4):
        for y0,y1,edge in ((2,3.5,5),(3.5,8.5,4),(8.5,10,5)):
            a,b=((edge,y0,7.6),(edge+.6,y1,8.4)) if side==0 else ((15.4-edge,y0,7.6),(16-edge,y1,8.4)) if side==1 else ((7.6,y0,edge),(8.4,y1,edge+.6)) if side==2 else ((7.6,y0,15.4-edge),(8.4,y1,16-edge))
            elements.append(box(a,b,'bronze'))
    if hanging:elements.extend([box((7.4,12.5,7.4),(8.6,16,8.6),'bronze')])
    else:elements.extend([box((6.8,12.5,7.5),(7.5,15,8.5),'bronze'),box((8.5,12.5,7.5),(9.2,15,8.5),'bronze'),box((6.8,14.5,7.5),(9.2,15.2,8.5),'bronze')])
    result=model(elements);result['textures']['particle']='myvillage:block/ningyuan_bronze';return result

def outputs():
    rules=json.loads((ROOT/RES/'data/myvillage/orchard/rules.json').read_text());out={}
    def put(path,value):out[path]=encoded(value)
    def rename(s):
        return s.replace('minecraft:block/stripped_oak','myvillage:block/stripped_ningyuan').replace('minecraft:block/potted_oak','myvillage:block/potted_ningyuan').replace('minecraft:block/oak','myvillage:block/ningyuan').replace('minecraft:block/iron_bars','myvillage:block/ningyuan_lattice').replace('"block/iron_bars"','"myvillage:block/ningyuan_lattice"')
    templates=json.loads((Path(__file__).parent/'vanilla_templates.json').read_text())
    for path,obj in templates.items():
        target=path.replace('assets/minecraft/','assets/myvillage/').replace('oak','ningyuan').replace('iron_bars','ningyuan_lattice')
        obj=json.loads(rename(json.dumps(obj)))
        if '/models/' in path:
            obj['render_type']='minecraft:cutout' if any(k in path for k in ('door','sapling','leaves','iron_bars')) else 'minecraft:solid'
        put(RES/target,obj)
    textures={'ningyuan_log':'bark','ningyuan_log_top':'end','stripped_ningyuan_log':'stripped','stripped_ningyuan_log_top':'stripped_end',
              'ningyuan_planks':'planks','ningyuan_leaves':'leaves','ningyuan_door_top':'door_top','ningyuan_door_bottom':'door_bottom',
              'ningyuan_trapdoor':'trapdoor','ningyuan_lattice':'lattice','ningyuan_lattice_top':'lattice'}
    textures.update({f'ningyuan_{k}':k for k in ('bark','leaf_blade','fruit_low','fruit_high','bud','bronze','jade','jade_side','soil')})
    for name,kind in textures.items():out[ASSET/f'textures/block/{name}.png']=png(material(kind))
    # Untinted, cutout leaves preserve the agreed palette in every biome.
    put(ASSET/'models/block/ningyuan_leaves.json',{'parent':'minecraft:block/cube_all','render_type':'minecraft:cutout_mipped','textures':{'all':'myvillage:block/ningyuan_leaves'}})
    put(ASSET/'models/block/ningyuan_sapling.json',sapling_model());put(ASSET/'models/block/potted_ningyuan_sapling.json',potted_model())
    lattice_textures={'wood':'myvillage:block/ningyuan_planks','jade':'myvillage:block/ningyuan_jade','pattern':'myvillage:block/ningyuan_lattice','particle':'myvillage:block/ningyuan_planks'}
    put(ASSET/'models/block/ningyuan_lattice_post.json',model([box((7,0,7),(9,16,9),'wood'),box((6.9,0,6.9),(9.1,1,9.1),'jade'),box((6.9,15,6.9),(9.1,16,9.1),'jade')],lattice_textures))
    put(ASSET/'models/block/ningyuan_lattice_inventory.json',model([box((0,0,7),(16,16,9),'pattern')],lattice_textures))
    variants={};bounds=[]
    for age in range(3):
        for enriched in (False,True):
            suffix=f'{age}_{"high" if enriched else "low"}';m=fruit_model(age,enriched)
            put(ASSET/f'models/block/ningyuan_fruit_{suffix}.json',m)
            variants[f'age={age},enriched={str(enriched).lower()}']={'model':f'myvillage:block/ningyuan_fruit_{suffix}'}
        bounds.append([min(e['from'][i] for e in m['elements']) for i in range(3)]+[max(e['to'][i] for e in m['elements']) for i in range(3)])
    put(ASSET/'blockstates/ningyuan_fruit_cluster.json',{'variants':variants})
    for hanging in (False,True):
        put(ASSET/f'models/block/ningyuan_fruit_lantern{"_hanging" if hanging else ""}.json',lantern_model(hanging))
    put(ASSET/'blockstates/ningyuan_fruit_lantern.json',{'variants':{f'hanging={str(h).lower()}':{'model':'myvillage:block/ningyuan_fruit_lantern'+('_hanging' if h else '')} for h in (False,True)}})
    for name,parent in [('jade_tree_pool_border','slab'),('jade_tree_pool_border_top','slab_top'),('jade_tree_pool_border_double','cube_bottom_top')]:
        put(ASSET/f'models/block/{name}.json',{'parent':'minecraft:block/'+parent,'textures':{'bottom':'myvillage:block/ningyuan_jade_side','top':'myvillage:block/ningyuan_jade','side':'myvillage:block/ningyuan_jade_side'}})
    put(ASSET/'blockstates/jade_tree_pool_border.json',{'variants':{f'type={k}':{'model':'myvillage:block/jade_tree_pool_border'+suffix} for k,suffix in [('bottom',''),('top','_top'),('double','_double')]}})
    # Item models use the same meshes as the world, plus standard block display transforms.
    special={'ningyuan_fence':'ningyuan_fence_inventory','ningyuan_button':'ningyuan_button_inventory','ningyuan_trapdoor':'ningyuan_trapdoor_bottom','ningyuan_door':None,
             'ningyuan_fruit_cluster':'ningyuan_fruit_2_low','ningyuan_lattice':'ningyuan_lattice_inventory',
             'ningyuan_sign':None,'ningyuan_hanging_sign':None,'ningyuan_boat':None,'ningyuan_chest_boat':None,'ningyuan_fertilizer':None}
    for name in NAMES.keys()-NO_ITEM:
        if name in special and special[name] is None:
            texture=f'myvillage:item/{name}'
            if name=='ningyuan_door':
                # A single readable door sprite; the placed door remains a two-block lattice model.
                top=material('door_top');bottom=material('door_bottom');rows=[]
                for y in range(32):
                    rows.append([(0,0,0,0)]*8+[ (top if y<16 else bottom)[(y%16)*2][x*2] for x in range(16)]+[(0,0,0,0)]*8)
            else:rows=icon(name.removeprefix('ningyuan_'))
            out[ASSET/f'textures/item/{name}.png']=png(rows)
            put(ASSET/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':texture}})
        else:
            put(ASSET/f'models/item/{name}.json',{'parent':'myvillage:block/'+special.get(name,name),'display':BLOCK_ITEM_DISPLAY})
    for kind,w,h in [('signs',64,32),('signs/hanging',64,32),('boat',128,64),('chest_boat',128,128)]:
        out[ASSET/f'textures/entity/{kind}/ningyuan.png']=png(entity_texture(w,h,hanging='hanging' in kind,boat='boat' in kind))
    put(DATA/'myvillage/orchard/geometry.json',{'fruit_bounds':bounds,'lantern_shapes':{str(h).lower():[e['from']+e['to'] for e in lantern_model(h)['elements']] for h in (False,True)}})
    put(DATA/'myvillage/orchard/catalogue.json',{'blocks':sorted(NAMES.keys()-NON_BLOCK),'items':sorted(NAMES.keys()-NO_ITEM),'no_item_blocks':sorted(NO_ITEM),'owner':'tools/gen_ningyuan_tree.py'})
    loot_and_recipes(put,rules)
    tags(put)
    put(DATA/'myvillage/worldgen/configured_feature/ningyuan_tree.json',{'type':'myvillage:ningyuan_tree','config':{}})
    put(DATA/'myvillage/worldgen/placed_feature/ningyuan_tree.json',{'feature':'myvillage:ningyuan_tree','placement':[
        {'type':'minecraft:rarity_filter','chance':rules['worldgen']['rarity']},{'type':'minecraft:in_square'},
        {'type':'minecraft:surface_water_depth_filter','max_water_depth':0},{'type':'minecraft:heightmap','heightmap':'OCEAN_FLOOR'},
        {'type':'minecraft:biome'}]})
    put(DATA/'myvillage/neoforge/biome_modifier/ningyuan_tree.json',{'type':'neoforge:add_features','biomes':rules['worldgen']['biomes'],'features':'myvillage:ningyuan_tree','step':'vegetal_decoration'})
    zhpath=ASSET/'lang/zh_cn.json';zh=json.loads((ROOT/zhpath).read_text())
    for name,label in NAMES.items():zh[('item' if name in NON_BLOCK else 'block')+'.myvillage.'+name]=label
    zh.update({'tooltip.myvillage.ningyuan_sapling':'泥土上可生长，灵土加快自然长成；需要树冠净空。',
               'tooltip.myvillage.ningyuan_fruit_cluster':'放在连接原木的凝元树叶下；成熟后右键采摘，枝条保留。',
               'tooltip.myvillage.ningyuan_fertilizer':'右键未施肥的花芽，本轮结上品果；采完后恢复普通花芽。'})
    put(zhpath,zh)
    return out

def loot_and_recipes(put,rules):
    def item(name):return {'item':name if ':' in name else 'myvillage:'+name}
    def entry(name,conditions=None,functions=None):
        row={'type':'minecraft:item','name':name if ':' in name else 'myvillage:'+name}
        if conditions:row['conditions']=conditions
        if functions:row['functions']=functions
        return row
    def loot(name,pools):put(DATA/f'myvillage/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':pools})
    def pool(entries,conditions=None):return {'rolls':1,'entries':entries,'conditions':conditions or [{'condition':'minecraft:survives_explosion'}]}
    def state(name,properties):return {'condition':'minecraft:block_state_property','block':'myvillage:'+name,'properties':properties}
    def count(n,conditions=None):
        obj={'function':'minecraft:set_count','count':n}
        if conditions:obj['conditions']=conditions
        return obj
    for name in NAMES.keys()-NON_BLOCK-{'ningyuan_leaves','ningyuan_fruit_cluster','potted_ningyuan_sapling'}:
        drop={'ningyuan_wall_sign':'ningyuan_sign','ningyuan_wall_hanging_sign':'ningyuan_hanging_sign'}.get(name,name)
        conditions=[state(name,{'half':'lower'})] if name=='ningyuan_door' else None
        functions=[count(2,[state(name,{'type':'double'})])] if name in ('ningyuan_slab','jade_tree_pool_border') else None
        loot(name,[pool([entry(drop,conditions,functions)])])
    loot('potted_ningyuan_sapling',[pool([entry('minecraft:flower_pot')]),pool([entry('ningyuan_sapling')])])
    silk={'condition':'minecraft:match_tool','predicate':{'predicates':{'minecraft:enchantments':[{'enchantments':'minecraft:silk_touch','levels':{'min':1}}]}}}
    shears={'condition':'minecraft:match_tool','predicate':{'items':'minecraft:shears'}}
    keep={'condition':'minecraft:any_of','terms':[silk,shears]}
    loot('ningyuan_leaves',[pool([{'type':'minecraft:alternatives','children':[
        entry('ningyuan_leaves',[keep]),entry('ningyuan_sapling',[{'condition':'minecraft:table_bonus','enchantment':'minecraft:fortune','chances':[.05,.0625,.083333336,.1]}])]}]),
        pool([entry('minecraft:stick',[{'condition':'minecraft:inverted','term':keep},{'condition':'minecraft:table_bonus','enchantment':'minecraft:fortune','chances':[.02,.022222223,.025,.033333335,.1]}],[count({'type':'minecraft:uniform','min':1,'max':2})])])])
    loot('ningyuan_fruit_cluster',[pool([entry(rules['harvest'][key],[state('ningyuan_fruit_cluster',{'age':'2','enriched':str(enriched).lower()})],[count(rules['harvest']['count'])])]) for enriched,key in [(False,'low_item'),(True,'high_item')]])
    def recipe(name,body,unlock='myvillage:ningyuan_sapling'):
        put(DATA/f'myvillage/recipe/{name}.json',body)
        put(DATA/f'myvillage/advancement/recipes/ningyuan/{name}.json',{'parent':'minecraft:recipes/root',
            'criteria':{'has_ingredient':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':unlock}]}},'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'myvillage:'+name}}},
            'requirements':[['has_ingredient','has_the_recipe']],'rewards':{'recipes':['myvillage:'+name]}})
    def shaped(name,pattern,key,n=1,result=None):recipe(name,{'type':'minecraft:crafting_shaped','category':'building','pattern':pattern,'key':{k:(v if isinstance(v,dict) else item(v)) for k,v in key.items()},'result':{'id':'myvillage:'+(result or name),'count':n}})
    def shapeless(name,ingredients,n=1,result=None,unlock='myvillage:ningyuan_sapling'):recipe(name,{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[i if isinstance(i,dict) else item(i) for i in ingredients],'result':{'id':'myvillage:'+(result or name),'count':n}},unlock)
    shapeless('ningyuan_planks',[{'tag':'myvillage:ningyuan_logs'}],4)
    shaped('ningyuan_wood',['LL','LL'],{'L':'ningyuan_log'},3)
    shaped('stripped_ningyuan_wood',['LL','LL'],{'L':'stripped_ningyuan_log'},3)
    p='ningyuan_planks';s='minecraft:stick'
    shaped('ningyuan_stairs',['P  ','PP ','PPP'],{'P':p},4);shaped('ningyuan_slab',['PPP'],{'P':p},6)
    shaped('ningyuan_fence',['PSP','PSP'],{'P':p,'S':s},3);shaped('ningyuan_fence_gate',['SPS','SPS'],{'P':p,'S':s})
    shaped('ningyuan_door',['PP','PP','PP'],{'P':p},3);shaped('ningyuan_trapdoor',['PPP','PPP'],{'P':p},2)
    shaped('ningyuan_pressure_plate',['PP'],{'P':p});shapeless('ningyuan_button',[p])
    shaped('ningyuan_sign',['PPP','PPP',' S '],{'P':p,'S':s},3)
    shaped('ningyuan_hanging_sign',['C C','PPP','PPP'],{'P':'stripped_ningyuan_log','C':'minecraft:chain'},6)
    shaped('ningyuan_boat',['P P','PPP'],{'P':p});shapeless('ningyuan_chest_boat',['ningyuan_boat','minecraft:chest'])
    shapeless('ningyuan_sapling',['ningyuan_fruit_low','minecraft:oak_sapling',{'tag':'c:spirit_stones/low_grade'}],unlock='myvillage:ningyuan_fruit_low')
    shaped('ningyuan_fruit_cluster',['SS',' F'],{'S':s,'F':'ningyuan_fruit_low'})
    shapeless('ningyuan_fertilizer',rules['fertilizer']['ingredients'],rules['fertilizer']['count'],unlock='myvillage:ningyuan_fruit_low')
    shaped('ningyuan_lattice',['SPS','PLP','SPS'],{'S':s,'P':p,'L':{'tag':'c:spirit_stones/low_grade'}},8)
    shaped('jade_tree_pool_border',['SSS','CLC'],{'S':'minecraft:stone_bricks','C':'minecraft:copper_ingot','L':{'tag':'c:spirit_stones/low_grade'}},6)
    shaped('ningyuan_fruit_lantern',[' C ','FLF',' C '],{'C':'minecraft:copper_ingot','F':p,'L':{'tag':'c:spirit_stones/low_grade'}})
    for log in LOGS:
        recipe(log+'_charcoal',{'type':'minecraft:smelting','category':'misc','ingredient':item(log),'result':{'id':'minecraft:charcoal'},'experience':.15,'cookingtime':200})

def tags(put):
    values={
      'myvillage:ningyuan_logs':LOGS,
      'minecraft:logs_that_burn':['#myvillage:ningyuan_logs'], 'minecraft:logs':['#myvillage:ningyuan_logs'],
      'minecraft:planks':['ningyuan_planks'],'minecraft:wooden_stairs':['ningyuan_stairs'],'minecraft:wooden_slabs':['ningyuan_slab'],
      'minecraft:wooden_fences':['ningyuan_fence'],'minecraft:fence_gates':['ningyuan_fence_gate'],
      'minecraft:wooden_doors':['ningyuan_door'],'minecraft:wooden_trapdoors':['ningyuan_trapdoor'],
      'minecraft:wooden_buttons':['ningyuan_button'],'minecraft:wooden_pressure_plates':['ningyuan_pressure_plate'],
      'minecraft:leaves':['ningyuan_leaves'],'minecraft:saplings':['ningyuan_sapling'],
      'minecraft:standing_signs':['ningyuan_sign'],'minecraft:wall_signs':['ningyuan_wall_sign'],
      'minecraft:ceiling_hanging_signs':['ningyuan_hanging_sign'],'minecraft:wall_hanging_signs':['ningyuan_wall_hanging_sign'],
      'minecraft:all_signs':['ningyuan_sign','ningyuan_wall_sign','ningyuan_hanging_sign','ningyuan_wall_hanging_sign'],
      'minecraft:mineable/axe':LOGS+['ningyuan_planks','ningyuan_stairs','ningyuan_slab','ningyuan_fence','ningyuan_fence_gate','ningyuan_door','ningyuan_trapdoor','ningyuan_button','ningyuan_pressure_plate','ningyuan_sign','ningyuan_wall_sign','ningyuan_hanging_sign','ningyuan_wall_hanging_sign','ningyuan_lattice'],
      'minecraft:mineable/pickaxe':['jade_tree_pool_border','ningyuan_fruit_lantern'],
      'minecraft:flower_pots':['potted_ningyuan_sapling'], 'minecraft:slabs':['jade_tree_pool_border'],
      'minecraft:bee_growables':['ningyuan_fruit_cluster'],
      'minecraft:replaceable_by_trees':['ningyuan_sapling','ningyuan_leaves','ningyuan_fruit_cluster'],
    }
    item_only={'minecraft:boats':['ningyuan_boat'],'minecraft:chest_boats':['ningyuan_chest_boat'],
               'minecraft:signs':['ningyuan_sign'],'minecraft:hanging_signs':['ningyuan_hanging_sign']}
    block_only={'minecraft:standing_signs','minecraft:wall_signs','minecraft:ceiling_hanging_signs','minecraft:wall_hanging_signs','minecraft:all_signs','minecraft:flower_pots','minecraft:bee_growables','minecraft:replaceable_by_trees','minecraft:mineable/axe','minecraft:mineable/pickaxe'}
    for kind,entries in [('block',values),('item',{**{k:v for k,v in values.items() if k not in block_only},**item_only})]:
        for key,ids in entries.items():
            namespace,path=key.split(':');target=DATA/f'{namespace}/tags/{kind}/{path}.json'
            previous=json.loads((ROOT/target).read_text()) if (ROOT/target).exists() else {'replace':False,'values':[]}
            additions=[s if ':' in s or s.startswith('#') else 'myvillage:'+s for s in ids]
            previous['values']=list(dict.fromkeys(previous['values']+additions));put(target,previous)
