"""Authored foundry blocks, hammers, handles, fire seeds and their common rules."""
import argparse
import copy
import io
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[2]
CFG=Path(__file__).with_suffix('.json')

def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def rgb(text):return tuple(bytes.fromhex(text.lstrip('#')))
def texture(color,kind='stone'):
    base=rgb(color);im=Image.new('RGBA',(16,16));px=im.load()
    for y in range(16):
        for x in range(16):
            shade=((x*17+y*29+x*y)%11)-5
            if kind=='wood':shade-=7 if x%5==0 else 0
            if kind=='brick' and (y%6==0 or (x+(8 if y//6%2 else 0))%12==0):shade-=15
            px[x,y]=tuple(max(0,min(255,c+shade)) for c in base)+(255,)
    stream=io.BytesIO();im.save(stream,format='PNG');return stream.getvalue()
def box(parts,name,start,end,tex):
    parts.append({'name':name,'from':start,'to':end,'faces':{side:{'texture':'#'+tex,'uv':[0,0,16,16]} for side in ['north','south','west','east','up','down']}})
def station(kind,lit=False):
    p=[];b=lambda n,a,z,t:box(p,n,a,z,t)
    if kind=='heating_kiln':
        b('厚石底座',[0,0,0],[16,2,16],'stone')
        b('窑背',[1,2,13],[15,14,15],'brick');b('左窑壁',[1,2,1],[3,14,13],'brick');b('右窑壁',[13,2,1],[15,14,13],'brick')
        b('窑顶',[1,12,1],[15,15,15],'brick');b('窑口上沿',[3,11,1],[13,12,3],'stone');b('窑口下沿',[3,2,1],[13,3,3],'stone')
        b('承坯架',[3,7,2],[13,8,13],'bronze');b('炉床',[3,2,3],[13,3,13],'stone')
        b('短烟道',[10,14,10],[14,16,14],'stone');b('烟道暗口',[10.7,16,10.7],[13.3,16.02,13.3],'dark')
        if lit:b('炉火',[3.5,3,3.5],[12.5,5,12.5],'ember')
    else:
        for x in (1,12):
            for z in (1,12):b('台脚',[x,0,z],[x+3,11,z+3],'wood')
        b('木台面',[0,11,0],[16,14,16],'wood');b('抽屉',[3,5,4],[13,9,13],'wood');b('抽屉拉手',[6,6,3.5],[10,7,4],'bronze')
        b('前沿',[0,11,0],[16,11.5,.5],'bronze');b('装配垫',[2,14,3],[11,14.2,13],'jade');b('小台钳',[12,14,6],[15,16,10],'stone')
    return {'ambientocclusion':True,'textures':{k:'myvillage:block/foundry_'+k for k in ['brick','stone','wood','bronze','jade','ember','dark']},'elements':p}
def flame(color):
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im);r,g,b=rgb(color)
    d.polygon([(15,2),(20,12),(25,8),(27,20),(23,27),(16,30),(8,27),(4,19),(9,8),(10,18),(14,13)],fill=(r,g,b,255))
    d.polygon([(16,10),(18,19),(22,16),(22,24),(17,27),(12,24),(12,20)],fill=(min(255,r+45),min(255,g+50),min(255,b+45),255))
    s=io.BytesIO();im.save(s,format='PNG');return s.getvalue()
def outputs(root=ROOT):
    cfg=json.loads(CFG.read_text());base=Path(root)/'src/main/resources';out={}
    def add(path,value):out[base/path]=encoded(value)
    add('data/myvillage/forging/foundry.json',cfg)
    palette=cfg['palette']|{'dark':'#292d2d'}
    for name,color in palette.items():out[base/f'assets/myvillage/textures/block/foundry_{name}.png']=texture(color,name)
    for kind in ['heating_kiln','assembly_table']:
        for lit in [False,True]:add(f'assets/myvillage/models/block/{kind}'+('_lit' if lit else '')+'.json',station(kind,lit))
        variants={}
        for direction,angle in [('north',0),('east',90),('south',180),('west',270)]:
            for lit in [False,True]:
                key=f'facing={direction},lit={str(lit).lower()}'
                variants[key]={'model':f'myvillage:block/{kind}'+('_lit' if lit else ''),'y':angle}
        add(f'assets/myvillage/blockstates/{kind}.json',{'variants':variants})
        add(f'assets/myvillage/models/item/{kind}.json',{'parent':f'myvillage:block/{kind}','display':{'gui':{'rotation':[25,225,0],'scale':[.65,.65,.65]}}})
        add(f'data/myvillage/loot_table/blocks/{kind}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'myvillage:'+kind}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    def shaped(id,pattern,keys):add(f'data/myvillage/recipe/{id}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:{'tag':v[1:]} if v.startswith('#') else {'item':v} for k,v in keys.items()},'result':{'id':'myvillage:'+id,'count':1}})
    shaped('heating_kiln',['BBB','BFB','III'],{'B':'minecraft:bricks','F':'minecraft:furnace','I':'minecraft:iron_ingot'})
    shaped('assembly_table',['III','PPP','P P'],{'I':'minecraft:iron_nugget','P':'#minecraft:planks'})
    from tools.forging import anvil
    from tools.equipment import mesh
    authored=json.loads((Path(root)/'equipment/workshop/hammer.json').read_text())
    for h in cfg['hammers']:
        if h['id']=='forging_hammer':continue
        model=anvil.hammer();model['textures']['iron']='myvillage:block/'+h['id'];model['textures']['edge']='myvillage:block/'+h['id']
        out[base/f'assets/myvillage/textures/block/{h["id"]}.png']=texture(h['color'])
        model['display']=mesh.item_display(mesh.elements_mesh(model),grip=authored['grip'],root=Path(root))
        for ctx in ['firstperson_righthand','firstperson_lefthand']:model['display'][ctx]['translation']=[a+b for a,b in zip(model['display'][ctx]['translation'],authored['first_person_offset'])]
        model['gui_light']='side';add(f'assets/myvillage/models/item/{h["id"]}.json',model)
        recipe=copy.deepcopy(authored['recipe'])
        recipe.setdefault('category','misc')
        recipe['key']['I']={'item':h['material']}
        recipe['result']['id']='myvillage:'+h['id']
        add(f'data/myvillage/recipe/{h["id"]}.json',recipe)
    for h in cfg['handles']:
        parts=[];box(parts,'完整柄身',[6.8,0,6.8],[9.2,15,9.2],'body')
        add(f'assets/myvillage/models/item/{h["id"]}_handle.json',{'textures':{'body':'myvillage:item/'+h['id']+'_handle','particle':'myvillage:item/'+h['id']+'_handle'},'elements':parts,'display':{'gui':{'rotation':[0,0,-35],'scale':[.9,.9,.9]},'ground':{'scale':[.4,.4,.4]},'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,1,0],'scale':[.6,.6,.6]},'firstperson_righthand':{'rotation':[0,0,-25],'translation':[0,1,0],'scale':[.5,.5,.5]}}})
        out[base/f'assets/myvillage/textures/item/{h["id"]}_handle.png']=texture(h['color'],'wood')
        add(f'data/myvillage/recipe/{h["id"]}_handle.json',{'type':'minecraft:crafting_shapeless','category':'equipment','ingredients':[{'item':h['ingredient']},{'item':h['ingredient']}],'result':{'id':'myvillage:'+h['id']+'_handle','count':1}})
    for f in cfg['fires']:
        id=f['id']+'_fire_seed';out[base/f'assets/myvillage/textures/item/{id}.png']=flame(f['color'])
        add(f'assets/myvillage/models/item/{id}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'myvillage:item/'+id}})
    add('assets/myvillage/models/item/fire_capture_bottle.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/glass_bottle'}})
    shaped('fire_capture_bottle',[' A ','GBG',' G '],{'A':'minecraft:amethyst_shard','G':'minecraft:glass','B':'minecraft:glass_bottle'})
    for kind,color in [('earth_fire_vent','#d47335'),('cold_fire_vent','#80abb4')]:
        out[base/f'assets/myvillage/textures/block/{kind}.png']=texture(color,'brick')
        add(f'assets/myvillage/models/block/{kind}.json',{'parent':'minecraft:block/cube_all','textures':{'all':'myvillage:block/'+kind}})
        add(f'assets/myvillage/blockstates/{kind}.json',{'variants':{f'lit={str(lit).lower()}':{'model':'myvillage:block/'+kind} for lit in [False,True]}})
        add(f'assets/myvillage/models/item/{kind}.json',{'parent':'myvillage:block/'+kind})
    for kind,key in [('earth','earth'),('cold','cold')]:
        w=cfg['worldgen'];name=kind+'_fire_source'
        add(f'data/myvillage/worldgen/configured_feature/{name}.json',{'type':'myvillage:'+name,'config':{}})
        add(f'data/myvillage/worldgen/placed_feature/{name}.json',{'feature':'myvillage:'+name,'placement':[
            {'type':'minecraft:count','count':w[key+'_attempts']},{'type':'minecraft:in_square'},
            {'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':w[key+'_min_y']},'max_inclusive':{'absolute':w[key+'_max_y']}}},
            {'type':'minecraft:biome'}]})
        add(f'data/myvillage/neoforge/biome_modifier/{name}.json',{'type':'neoforge:add_features','biomes':'#myvillage:cold_fire_sources' if kind=='cold' else '#minecraft:is_overworld','features':['myvillage:'+name],'step':'underground_ores'})
    add('data/myvillage/tags/worldgen/biome/cold_fire_sources.json',{'replace':False,'values':['minecraft:snowy_plains','minecraft:ice_spikes','minecraft:snowy_taiga','minecraft:grove','minecraft:frozen_peaks','minecraft:jagged_peaks']})
    for tool,ids in [('pickaxe',['heating_kiln','earth_fire_vent','cold_fire_vent']),('axe',['assembly_table'])]:
        path=base/f'data/minecraft/tags/block/mineable/{tool}.json'
        tag=json.loads(path.read_text()) if path.exists() else {'replace':False,'values':[]}
        tag['values']=sorted(set(tag['values'])|{'myvillage:'+id for id in ids});out[path]=encoded(tag)
    return out
def lang():
    c=json.loads(CFG.read_text());d={'block.myvillage.heating_kiln':'加热窑','block.myvillage.assembly_table':'组装台',
        'block.myvillage.earth_fire_vent':'地火泉眼','block.myvillage.cold_fire_vent':'寒焰凝晶','item.myvillage.fire_capture_bottle':'收火瓶',
        'foundry.myvillage.skill':'炼器熟练度：%s 级','foundry.myvillage.progress':'成形进度：%s / %s','foundry.myvillage.manual_bonus':'手工锻造增益：%s%%',
        'foundry.myvillage.handle':'柄材：%s','foundry.myvillage.fire':'辅助火：%s · 成熟度 %s',
        'foundry.myvillage.kiln_hint':'器坯放入后自动加热并保温；潜行右键补充燃料。',
        'foundry.myvillage.assembly_hint':'放入精磨器坯与柄，预览并装配；成品可在此更换柄材。',
        'foundry.myvillage.capture_hint':'对地火泉眼、寒焰凝晶、被雷击激活的避雷针或虚弱烈焰人使用。',
        'foundry.myvillage.ready':'火候已足，可以取出锻打。','foundry.myvillage.no_fuel':'窑中缺少燃料。',
        'foundry.myvillage.transfer':'器坯已转移。','foundry.myvillage.occupied':'目标工位占用，请先取走器物。',
        'foundry.myvillage.cold':'器坯已冷，请先放入加热窑。','foundry.myvillage.finished':'器物已完成，可取出。',
        'foundry.myvillage.captured':'已收取：%s','foundry.myvillage.capture_weak':'先将烈焰人削弱，再尝试收火。',
        'foundry.myvillage.bonus_detail':'伤害 %s%% · 速度 %s%% · 耐久 %s%% · 防护 %s%% · 耗灵 %s%%',
        'foundry.myvillage.preparation':'起坯与自动制作','foundry.myvillage.kiln':'加热窑','foundry.myvillage.assembly':'器物组装'}
    d.update({
        'workshop.myvillage.status.0':'在窑中加热后持锤锻打；面板可进行淬火、精磨或修补。',
        'workshop.myvillage.status.3':'器物已冷，请先送入加热窑。',
        'workshop.myvillage.status.9':'请手持器坯或成品，右键放到锻台上。',
        'workshop.myvillage.status.13':'本次锻打已完成。',
        'workshop.myvillage.status.14':'加工完成，可以继续或取回器物。',
        'workshop.myvillage.manual.placed':'器物已放上锻台。热坯可持锤右键锻打，冷坯先送入加热窑。',
        'workshop.myvillage.manual.empty':'手持器坯右键放置；在普通鼎起坯，在窑中加热。',
    })
    for h in c['hammers']:d['item.myvillage.'+h['id']]=h['name']
    for h in c['handles']:d['item.myvillage.'+h['id']+'_handle']=h['name']
    for f in c['fires']:d['item.myvillage.'+f['id']+'_fire_seed']=f['name']
    return {k:(v,v) for k,v in d.items()}
def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--out',type=Path);a=p.parse_args()
    stale=[]
    for path,data in outputs().items():
        if a.out:path=a.out/path.relative_to(ROOT/'src/main/resources')
        if a.check:
            if not path.exists() or path.read_bytes()!=data:stale.append(str(path))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    if stale:raise SystemExit('\n'.join(stale))
    print('Foundry assets '+('match' if a.check else 'generated'))
if __name__=='__main__':main()
