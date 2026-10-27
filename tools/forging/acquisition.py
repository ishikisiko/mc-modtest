"""Authored acquisition settings -> mining loot, raw item, worldgen, material UI and deposit textures."""
import json
import random
from pathlib import Path
from tools.gen_alchemy_assets import Canvas, png
ROOT=Path(__file__).resolve().parents[2]

def load(root=ROOT):
    d=json.loads((root/'src/main/resources/data/myvillage/forging/acquisition.json').read_text())
    if set(d)!={'version','infusion','xuantie','sand'} or d['version']!=1:raise ValueError('Acquisition schema')
    ranges={
        'infusion':{'max_batch':(1,64),'qi_per_ingot':(1,10000),'qi_per_pulse':(1,10000),'pulse_ticks':(1,1200)},
        'xuantie':{'vein_size':(1,64),'attempts_per_chunk':(1,32),'min_y':(-64,320),'max_y':(-64,320),'drop_min':(1,64),'drop_max':(1,64)},
        'sand':{'attempts_per_chunk':(1,32),'radius':(1,5),'water_radius':(1,8),'max_water_depth':(0,2),'max_blocks':(1,64),'drop_min':(1,64),'drop_max':(1,64)}}
    for group,fields in ranges.items():
        if set(d[group])!=set(fields)|({'biomes'} if group=='sand' else set()):raise ValueError('Acquisition fields: '+group)
        for key,(lo,hi) in fields.items():
            n=d[group][key]
            if type(n)!=int or not lo<=n<=hi:raise ValueError(group+'.'+key)
    if d['infusion']['qi_per_pulse']>d['infusion']['qi_per_ingot']:raise ValueError('Infusion pulse exceeds total')
    if d['xuantie']['min_y']>d['xuantie']['max_y']:raise ValueError('Ore height range')
    for key in ('xuantie','sand'):
        if d[key]['drop_min']>d[key]['drop_max']:raise ValueError('Drop range')
    if not d['sand']['biomes'] or len(set(d['sand']['biomes']))!=len(d['sand']['biomes']):raise ValueError('Sand biomes')
    return d

LANG={
 'block.myvillage.xuantie_ore':('Dark Iron Ore','玄铁矿'),
 'item.myvillage.raw_xuantie':('Raw Dark Iron','粗玄铁'),
 'block.myvillage.edge_tempering_sand_deposit':('Edge-tempering Sand Deposit','淬锋砂层'),
 'screen.myvillage.infusion.title':('Copper Infusion','赤铜灌灵'),
 'screen.myvillage.infusion.back':('Back to forging','返回炼灵'),
 'screen.myvillage.infusion.start':('Start / Resume','开始 / 继续'),
 'screen.myvillage.infusion.pause':('Pause','暂停'),
 'screen.myvillage.infusion.batch':('Up to %s copper ingots per batch','每批最多 %s 块铜锭'),
 'screen.myvillage.infusion.power':('Qi: %s','灵力：%s'),
 'screen.myvillage.infusion.remaining':('Batch remaining: %s qi','整批还需灵力：%s'),
 'screen.myvillage.infusion.status.0':('Insert copper. Closing keeps paid progress.','放入铜锭；关闭界面保留已灌灵进度。'),
 'screen.myvillage.infusion.status.1':('Infusing your spiritual power...','正在注入自身灵力……'),
 'screen.myvillage.infusion.status.2':('Paused. Paid progress stays on the copper.','已暂停，进度保留在铜锭上。'),
 'screen.myvillage.infusion.status.3':('Insert copper and take any output first.','请放入铜锭，并先取走右槽产物。'),
 'screen.myvillage.infusion.status.4':('Awaken before infusing.','觉醒后才能灌灵。'),
 'screen.myvillage.infusion.status.5':('Not enough qi. Paused; recover and resume.','灵力不足，已暂停；恢复后可继续。'),
 'screen.myvillage.infusion.status.6':('Complete. Take the spirit copper.','灌灵完成，取走右槽赤铜锭。'),
 'tooltip.myvillage.infused_copper':('Infused qi per ingot: %s / %s; resume at a forging ding.','每锭已灌灵：%s / %s；可放回炼器鼎继续。'),
}

def ore_texture():
    c=Canvas()
    for y in range(32):
        for x in range(32):
            n=(x*19+y*7+x*y*3)%15-7
            seam=-12 if y%8==0 or (x+(y//8%2)*9)%16==0 else 0
            c.put(x,y,(max(0,48+n+seam),max(0,51+n+seam),max(0,56+n+seam)))
    for x,y in [(5,5),(20,4),(12,15),(26,19),(4,25),(20,28)]:
        c.poly([(x-3,y),(x,y-2),(x+4,y-1),(x+2,y+3),(x-2,y+3)],(18,27,36))
        c.line(x-2,y,x+2,y-1,(131,154,170));c.line(x-1,y+1,x+1,y+1,(70,89,109));c.put(x+2,y-1,(189,208,218))
    return c.rows

def sand_texture():
    c=Canvas();rng=random.Random(391)
    for y in range(32):
        for x in range(32):
            n=rng.randrange(-13,14)
            c.put(x,y,(142+n,133+n,108+n))
    for x,y in [(4,4),(15,3),(25,7),(8,14),(20,15),(3,25),(15,27),(28,25)]:
        c.poly([(x-2,y),(x,y-2),(x+3,y),(x+1,y+2)],(106,84,48))
        c.line(x-1,y,x+1,y-1,(237,210,143));c.put(x,y-1,(255,240,193));c.put(x+1,y+1,(192,155,90))
    return c.rows

def infusion_texture(stage):
    c=Canvas()
    c.poly([(3,17),(9,10),(24,8),(29,14),(23,23),(7,26),(3,22)],(91,44,35))
    c.poly([(3,17),(23,15),(29,14),(25,22),(7,26),(3,22)],(140,66,42))
    c.poly([(3,17),(9,10),(24,8),(29,14),(23,18),(7,22)],(202,125,76))
    c.line(9,10,23,8,(248,190,113));c.line(7,22,23,18,(91,44,35))
    runes=[(10,15,15,12),(15,12,21,12),(21,12,18,16),(18,16,12,17),(12,17,10,15),(15,13,16,16)]
    for x1,y1,x2,y2 in runes[:stage]:c.line(x1,y1,x2,y2,(255,98,70))
    c.outline((19,27,31));return c.rows

def raw_xuantie_texture():
    """Irregular raw dark-iron chunk with silver inclusions; the mining product."""
    c=Canvas()
    c.poly([(4,23),(5,18),(8,19),(10,14),(13,15),(16,11),(19,13),(22,10),(25,14),(28,13),(28,19),(26,22),(27,25),(22,27),(18,26),(14,28),(9,27),(6,25)],(40,48,62))
    c.poly([(6,18),(9,15),(12,16),(16,12),(19,14),(23,11),(26,15),(24,18),(18,20),(11,20),(7,20)],(84,100,122))
    c.poly([(4,23),(5,18),(7,20),(11,20),(18,20),(24,18),(26,22),(27,25),(22,27),(18,26),(14,28),(9,27),(6,25)],(58,70,88))
    c.poly([(22,18),(26,15),(28,19),(26,22),(27,25),(22,27),(19,22)],(30,36,48))
    for x,y,rx,ry in ((9,17,2,2),(15,16,3,2),(21,15,2,3),(12,23,2,2),(20,22,1,2),(25,20,2,1)):
        c.poly([(x-rx,y),(x-1,y-ry),(x+rx,y-1),(x+1,y+ry)],(104,126,156))
        c.line(x-rx+1,y-1,x+rx-1,y-2,(166,190,220))
        c.put(x,y-ry,(212,232,252))
        c.line(x-rx+1,y+1,x+rx-1,y+1,(64,78,100))
    c.line(6,21,10,19,(118,140,170));c.line(17,18,22,17,(118,140,170));c.line(10,25,14,23,(96,116,144))
    c.line(4,23,5,18,(100,118,144));c.line(5,18,8,19,(126,148,178));c.line(8,19,10,14,(162,188,220))
    c.line(12,17,15,14,(190,214,244));c.put(14,14,(228,242,255))
    c.outline((10,14,22))
    return c.rows


def outputs(root=ROOT):
    d=load(root);out={};base=root/'src/main/resources'
    def put(path,obj):out[base/path]=(json.dumps(obj,ensure_ascii=False,indent=2)+'\n').encode()
    def tag(namespace,path,values):
        rel=f'data/{namespace}/tags/block/{path}.json';p=base/rel
        original=json.loads(p.read_text()) if p.exists() else {'replace':False,'values':[]}
        original['values']=sorted(set(original['values'])|set(values));put(rel,original)
    out[base/'assets/myvillage/textures/item/raw_xuantie.png']=png(raw_xuantie_texture())
    put('assets/myvillage/models/item/raw_xuantie.json',{'parent':'minecraft:item/generated','textures':{'layer0':'myvillage:item/raw_xuantie'}})
    for name,texture,material,settings in [('xuantie_ore',ore_texture(),'raw_xuantie',d['xuantie']),('edge_tempering_sand_deposit',sand_texture(),'edge_tempering_sand',d['sand'])]:
        out[base/f'assets/myvillage/textures/block/{name}.png']=png(texture)
        put(f'assets/myvillage/blockstates/{name}.json',{'variants':{'':{'model':'myvillage:block/'+name}}})
        put(f'assets/myvillage/models/block/{name}.json',{'parent':'minecraft:block/cube_all','textures':{'all':'myvillage:block/'+name}})
        put(f'assets/myvillage/models/item/{name}.json',{'parent':'myvillage:block/'+name})
        silk={'condition':'minecraft:match_tool','predicate':{'predicates':{'minecraft:enchantments':[{'enchantments':'minecraft:silk_touch','levels':{'min':1}}]}}}
        functions=[{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':settings['drop_min'],'max':settings['drop_max']}}]
        if name=='xuantie_ore':functions.append({'function':'minecraft:apply_bonus','enchantment':'minecraft:fortune','formula':'minecraft:ore_drops'})
        functions.append({'function':'minecraft:explosion_decay'})
        put(f'data/myvillage/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:alternatives','children':[
            {'type':'minecraft:item','name':'myvillage:'+name,'conditions':[silk]},
            {'type':'minecraft:item','name':'myvillage:'+material,'functions':functions}]}]}]})
    tag('minecraft','mineable/pickaxe',['myvillage:xuantie_ore'])
    tag('minecraft','needs_iron_tool',['myvillage:xuantie_ore'])
    tag('minecraft','mineable/shovel',['myvillage:edge_tempering_sand_deposit'])
    tag('myvillage','edge_sand_replaceables',['minecraft:sand','minecraft:gravel','minecraft:dirt','minecraft:coarse_dirt','minecraft:grass_block','minecraft:clay','minecraft:stone'])
    for kind in ('item','block'):
        put(f'data/c/tags/{kind}/ores/xuantie.json',{'replace':False,'values':['myvillage:xuantie_ore']})
        put(f'data/c/tags/{kind}/ores.json',{'replace':False,'values':['#c:ores/xuantie','#c:ores/spirit_stone']})
        put(f'data/myvillage/tags/{kind}/deposits/edge_tempering_sand.json',{'replace':False,'values':['myvillage:edge_tempering_sand_deposit']})
    ore=d['xuantie']
    put('data/myvillage/worldgen/configured_feature/xuantie_ore.json',{'type':'minecraft:ore','config':{'size':ore['vein_size'],'discard_chance_on_air_exposure':0.0,'targets':[
        {'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:stone_ore_replaceables'},'state':{'Name':'myvillage:xuantie_ore'}},
        {'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:deepslate_ore_replaceables'},'state':{'Name':'myvillage:xuantie_ore'}}]}})
    put('data/myvillage/worldgen/placed_feature/xuantie_ore.json',{'feature':'myvillage:xuantie_ore','placement':[
        {'type':'minecraft:count','count':ore['attempts_per_chunk']},{'type':'minecraft:in_square'},
        {'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':ore['min_y']},'max_inclusive':{'absolute':ore['max_y']}}},{'type':'minecraft:biome'}]})
    put('data/myvillage/neoforge/biome_modifier/add_xuantie_ore.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':['myvillage:xuantie_ore'],'step':'underground_ores'})
    sand=d['sand']
    put('data/myvillage/worldgen/configured_feature/edge_tempering_sand.json',{'type':'myvillage:edge_tempering_sand','config':{key:sand[key] for key in ('radius','water_radius','max_water_depth','max_blocks')}})
    put('data/myvillage/worldgen/placed_feature/edge_tempering_sand.json',{'feature':'myvillage:edge_tempering_sand','placement':[
        {'type':'minecraft:count','count':sand['attempts_per_chunk']},{'type':'minecraft:in_square'},{'type':'minecraft:heightmap','heightmap':'WORLD_SURFACE_WG'},{'type':'minecraft:biome'}]})
    put('data/myvillage/tags/worldgen/biome/has_edge_tempering_sand.json',{'replace':False,'values':sand['biomes']})
    put('data/myvillage/neoforge/biome_modifier/add_edge_tempering_sand.json',{'type':'neoforge:add_features','biomes':'#myvillage:has_edge_tempering_sand','features':['myvillage:edge_tempering_sand'],'step':'local_modifications'})
    for stage in range(7):out[base/f'assets/myvillage/textures/gui/copper_infusion_{stage}.png']=png(infusion_texture(stage))
    return out
