#!/usr/bin/env python3
"""生成四组传承的玩法数据、秘籍、器物、遗迹和中文说明。"""
from __future__ import annotations
import argparse, copy, json, math, sys
from pathlib import Path
from tools.gen_qingfeng_sword_model import encode_png

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
SOURCE = ROOT / 'tools/legacy_encounters/catalogue.json'

def catalogue():
    return json.loads(SOURCE.read_text())

def dump(v):
    return (json.dumps(v, ensure_ascii=False, indent=2)+'\n').encode()

def validate(c):
    assert c['schema'] == 1
    assert len({m['id'] for m in c['mobs']}) == len(c['mobs']) == 4
    assert {a['mode'] for a in c['arts']} == {'sword','shadow','fire','ruler'}
    for a in c['arts']:
        assert 1 <= a['qi_cost'] <= 100 and 20 <= a['cooldown_ticks'] <= 1200
        assert 1 <= a['range'] <= 24 and 1 <= a['duration_ticks'] <= 600
    for m in c['mobs']:
        for a in m['moves']:
            assert 0 <= a['turn_lock_tick'] < a['windup_ticks'] <= a['active_ticks'][0] <= a['active_ticks'][1] < a['total_ticks']
            assert a['total_ticks']-a['active_ticks'][1] >= 12
    for k,v in c['runtime'].items():
        assert isinstance(v,(int,float)) and math.isfinite(v) and v > 0, k

def texture(palette, kind='stone'):
    colors=[tuple(bytes.fromhex(p)) for p in palette]
    pixels=[]
    for y in range(32):
        row=[]
        for x in range(32):
            k = 0 if (x//4+y//3)%5 else 1
            base=colors[k]
            shade=((x*13+y*7)//9%3-1)*5
            if kind=='mirror' and 7<x<25 and 5<y<25: base=colors[1]
            if kind in ('stone','sword','ruler') and x in (15,16) and y%7 in (1,2,3):base=colors[2]
            row.append(tuple(max(0,min(255,v+shade)) for v in base)+(255,))
        pixels.append(row)
    return encode_png(pixels)

def item_model(item):
    """每件器物有完整连续的主体；体块相交只用于原版 JSON 的轮廓构造。"""
    kind=item['kind'];elements=[]
    def box(lo,hi,uv=(0,0,16,16),rotation=None):
        e={'from':lo,'to':hi,'faces':{face:{'uv':list(uv),'texture':'#all'} for face in ['up','down','north','south','east','west']}}
        if rotation:e['rotation']=rotation
        elements.append(e)
    if kind=='sword':
        # Same grip frame and continuous blade extent as the existing dedicated sword rig.
        box([7.2,0,7.2],[8.8,1.4,8.8]);box([7.55,1.4,7.5],[8.45,7.4,8.5],(0,0,4,16))
        box([7.1,7.4,4.8],[8.9,8.6,11.2],(8,0,16,4))
        box([7.8,8.6,7.1],[8.2,23.5,8.9]);box([7.8,23.5,7.55],[8.2,24.5,8.45])
        from tools.gen_qingfeng_sword_model import build_display
        display=build_display()
    elif kind=='ruler':
        box([7.1,1,6.5],[8.9,25,9.5]);box([7.2,0,7.1],[8.8,4,8.9],(0,0,4,16))
        from tools.gen_qingfeng_sword_model import build_display
        display=build_display()
    elif kind=='mirror':
        box([7,0,7],[9,8,9],(8,0,16,4))
        box([6.9,8,3.5],[9.1,18,12.5],(8,0,16,16))
        box([6.9,9,2.5],[9.1,17,13.5],(8,0,16,16))
        box([6.8,9,4],[9.2,17,12],(3,3,13,13))
        display={'gui':{'rotation':[20,45,0],'translation':[0,-2,0],'scale':[.8,.8,.8]},'firstperson_righthand':{'rotation':[0,-90,15],'translation':[1,1,0],'scale':[.7,.7,.7]},'thirdperson_righthand':{'rotation':[0,-90,0],'translation':[0,1,0],'scale':[.65,.65,.65]}}
    else:
        box([4,1,4],[12,9,12]);box([5,9,5],[11,11,11]);box([5,11,5],[11,16,11]);box([6,16,6],[10,18,10],(8,0,16,4))
        display={'gui':{'rotation':[20,35,0],'translation':[0,-1,0],'scale':[.9,.9,.9]},'firstperson_righthand':{'rotation':[0,-90,15],'translation':[1,1,0],'scale':[.7,.7,.7]},'thirdperson_righthand':{'rotation':[0,-90,0],'translation':[0,2,0],'scale':[.6,.6,.6]}}
    return {'gui_light':'side','textures':{'all':'myvillage:item/'+item['id'],'particle':'myvillage:item/'+item['id']},'elements':elements,'display':display}

def small_icon(kind,palette):
    from PIL import Image,ImageDraw
    image=Image.new('RGBA',(32,32));d=ImageDraw.Draw(image)
    if kind=='silk':
        d.rectangle((6,4,25,28),fill='#b7d2cb',outline='#436f77',width=2)
        for y in (10,16,22):d.line((10,y,21,y-2),fill='#488a96',width=2)
    elif kind=='rubbing':
        d.rectangle((6,3,25,29),fill='#ded3b7',outline='#786e57',width=2)
        for y in range(8,26,5):d.line((11,y,20,y),fill='#30413b',width=2)
    else:
        d.polygon([(16,3),(26,12),(23,25),(12,29),(5,18),(8,8)],fill='#'+palette[0],outline='#'+palette[2])
        d.polygon([(16,8),(21,14),(17,22),(11,19)],fill='#'+palette[1])
    return encode_png([[image.getpixel((x,y)) for x in range(32)] for y in range(32)])

def structure(kind):
    sys.path.insert(0,str(ROOT/'tools'))
    from tools.gen_danxia_ruin import template_bytes
    blocks={};size=[15,7,15]
    for x in range(1,14):
        for z in range(1,14):
            if (x-7)**2+(z-7)**2 <= 45:
                blocks[x,0,z]='minecraft:mossy_stone_bricks' if (x*3+z*7)%7==0 else 'minecraft:stone_bricks'
                for y in range(1,6):blocks[x,y,z]='minecraft:air'
    for x,z in [(3,3),(11,3),(3,11),(11,11)]:
        for y in range(1,3+(x+z)%2):blocks[x,y,z]='minecraft:chiseled_stone_bricks'
    name='sword_inscription' if kind=='sword' else 'mountain_stele'
    blocks[7,1,7]='myvillage:'+name
    if kind=='mountain':blocks[7,2,7]='minecraft:chiseled_stone_bricks'
    data={'size':size,'blocks':[{'pos':list(p),'state':v} for p,v in sorted(blocks.items())],'entities':[]}
    return template_bytes(data,{(7,1,7):{'id':'myvillage:legacy_stele','Ready':True}},[])

def outputs():
    c=catalogue();validate(c);out={}
    def emit(path,obj):out[RES/path]=dump(obj)
    def raw(path,data):out[RES/path]=data
    emit('data/myvillage/legacy/encounters.json',c)
    index=json.loads((RES/'data/myvillage/beast/index.json').read_text())
    # Merge only this author's explicit IDs; other generators retain their inputs.
    for m in c['mobs']:
        rid='myvillage:'+m['id']
        if rid not in index['beasts']:index['beasts'].append(rid)
        moves=[{k:v for k,v in a.items() if k not in ('name','mode')} for a in m['moves']]
        emit(f'data/myvillage/beast/{m["id"]}.json',{'schema':1,'entity':rid,'attributes':m['attributes'],'chase':c['chase'],'stagger':{'animation':'stagger'},'moves':moves})
        emit(f'data/myvillage/loot_table/entities/{m["id"]}.json',{'type':'minecraft:entity','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'myvillage:'+m['material'][0]}]}]})
        emit(f'assets/myvillage/models/item/{m["id"]}_spawn_egg.json',{'parent':'minecraft:item/template_spawn_egg'})
        if m['spawn']=='natural':
            emit(f'data/myvillage/tags/worldgen/biome/has_{m["id"]}.json',{'replace':False,'values':m['biomes']})
            emit(f'data/myvillage/neoforge/biome_modifier/add_{m["id"]}_spawns.json',{'type':'neoforge:add_spawns','biomes':'#myvillage:has_'+m['id'],'spawners':[{'type':rid,'weight':m['spawn_weight'],'minCount':1,'maxCount':1}]})
        mid=m['material'][0]
        emit(f'assets/myvillage/models/item/{mid}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'myvillage:item/'+mid}})
        raw(f'assets/myvillage/textures/item/{mid}.png',small_icon('silk' if mid=='watermarked_silk' else 'core',m['palette']))
    emit('data/myvillage/beast/index.json',index)
    runtime=[]
    for a in c['arts']:
        rid='myvillage:'+a['id'];key='cultivation.technique.myvillage.'+a['id']
        emit(f'data/myvillage/myvillage/technique/{a["id"]}.json',{'translation_key':key,'category':'active','grade':a['grade'],'elements':['myvillage:'+a['element']],'school':'myvillage:legacy','requirements':{'minimum_realm':'myvillage:qi_refining','minimum_stage':'myvillage:qi_refining_4'},'effects':{'active':{'skill':rid,'qi_cost':a['qi_cost'],'slots':1}},'study':{'points':c['runtime']['study_points'],'gates':0,'gate_stability_cost':0}})
        phase={'move':'myvillage:qingfeng_cut','shape':'ring','reach':2,'arc_degrees':360,'width':1,'height':1,'half_height':1,'damage_multiplier':1,'maximum_targets':8,'step_distance':0,'reaction':{'hitstun_ticks':0,'slide_distance':0,'lift':0,'lateral_bias':0},'fx':'ring'}
        # Use the first existing sword move, preserving its PAL and first-person pose ownership.
        phase['move']=json.loads((RES/'data/myvillage/combat/style/basic_sword.json').read_text())['moves'][0]['id']
        runtime.append({'id':rid,'school':'myvillage:legacy','family':'legacy','slot':-1,'qi_cost':a['qi_cost'],'cooldown_ticks':a['cooldown_ticks'],'guard_reduction':0,'guard_arc_degrees':120,'counter_multiplier':1,'requires_ground':False,'interrupt_on_damage':False,'fx_lifetime_ticks':10,'cast_pitch':1,'color':{'sword':[.65,.85,.76],'shadow':[.35,.6,.8],'fire':[1,.42,.15],'ruler':[.65,.7,.45]}[a['mode']],'particle_count':12,'phases':[phase]})
    emit('data/myvillage/combat/legacy_arts.json',{'schema':1,'arts':runtime})
    emit('data/myvillage/myvillage/school/legacy.json',{'translation_key':'cultivation.school.myvillage.legacy','kind':'special','runtime':'spell','element_lean':[]})
    for item in c['items']:
        emit(f'assets/myvillage/models/item/{item["id"]}.json',item_model(item))
        raw(f'assets/myvillage/textures/item/{item["id"]}.png',texture(item['palette'],item['kind']))
    combat=json.loads((RES/'data/myvillage/combat/index.json').read_text())
    for id in ['guiyan_sword','zhenyue_ruler']:
        rid='myvillage:'+id
        if rid not in combat['weapons']:combat['weapons'].append(rid)
        emit(f'data/myvillage/combat/weapon/{id}.json',{'schema':1,'item':rid,'family':'sword','style':'myvillage:basic_sword','first_person_rig':'myvillage:combat/qingfeng_first_person.json','geometry':f'myvillage:combat/{id}_geometry.json'})
        from tools.gen_qingfeng_sword_model import geometry_contract
        geom=geometry_contract();geom['item']=rid
        emit(f'assets/myvillage/combat/{id}_geometry.json',geom)
    emit('data/myvillage/combat/index.json',combat)
    for id in ['sword_rubbing','mountain_rubbing']:
        emit(f'assets/myvillage/models/item/{id}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'myvillage:item/'+id}})
        raw(f'assets/myvillage/textures/item/{id}.png',small_icon('rubbing',c['mobs'][0]['palette']))
    ingredients={'guiyan_sword':['minecraft:iron_ingot','myvillage:sword_spirit_core','myvillage:low_grade_spirit_stone'],
        'zhaoshui_mirror':['minecraft:copper_ingot','minecraft:glass','myvillage:watermarked_silk','myvillage:low_grade_spirit_stone'],
        'chiyu_gourd':['minecraft:clay_ball','minecraft:glass_bottle','myvillage:ember_cinnabar','myvillage:low_grade_spirit_stone'],
        'zhenyue_ruler':['minecraft:stone','minecraft:iron_ingot','myvillage:stele_stone_heart','myvillage:low_grade_spirit_stone']}
    for id,values in ingredients.items():
        emit(f'data/myvillage/recipe/{id}.json',{'type':'minecraft:crafting_shapeless','category':'equipment','ingredients':[{'item':v} for v in values],'result':{'id':'myvillage:'+id,'count':1}})
    for a,material in zip(c['arts'],['sword_rubbing','watermarked_silk','ember_cinnabar','mountain_rubbing']):
        if a['mode']=='shadow':continue # the water-revealed scroll is a real world interaction
        emit(f'data/myvillage/recipe/manual_{a["id"]}.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':'myvillage:'+material},{'item':'minecraft:book'},{'item':'minecraft:ink_sac'}],'result':{'id':'myvillage:manual_active_xuan','count':1,'components':{'myvillage:technique':'myvillage:'+a['id']}}})
    for kind,name in [('sword','sword_inscription'),('mountain','mountain_stele')]:
        raw(f'data/myvillage/structure/legacy_{kind}_ruin.nbt',structure(kind))
        raw(f'assets/myvillage/textures/block/{name}.png',texture(['5a665b','98a28a','d8ce95']))
        emit(f'assets/myvillage/models/block/{name}.json',{'parent':'minecraft:block/cube_all','textures':{'all':'myvillage:block/'+name}})
        emit(f'assets/myvillage/blockstates/{name}.json',{'variants':{'':{'model':'myvillage:block/'+name}}})
        emit(f'assets/myvillage/models/item/{name}.json',{'parent':'myvillage:block/'+name})
        id='legacy_'+kind+'_ruin'
        emit(f'data/myvillage/tags/worldgen/biome/has_{id}.json',{'replace':False,'values':['minecraft:forest','minecraft:plains','minecraft:meadow','minecraft:taiga']})
        emit(f'data/myvillage/worldgen/structure/{id}.json',{'type':'minecraft:jigsaw','biomes':'#myvillage:has_'+id,'step':'surface_structures','spawn_overrides':{},'terrain_adaptation':'beard_thin','start_pool':'myvillage:'+id,'size':1,'start_height':{'absolute':0},'project_start_to_heightmap':'WORLD_SURFACE_WG','max_distance_from_center':32,'use_expansion_hack':False})
        emit(f'data/myvillage/worldgen/template_pool/{id}.json',{'name':'myvillage:'+id,'fallback':'minecraft:empty','elements':[{'weight':1,'element':{'element_type':'minecraft:single_pool_element','location':'myvillage:'+id,'processors':'minecraft:empty','projection':'rigid'}}]})
        emit(f'data/myvillage/worldgen/structure_set/{id}.json',{'structures':[{'structure':'myvillage:'+id,'weight':1}],'placement':{'type':'minecraft:random_spread','spacing':32,'separation':12,'salt':7246081+(kind=='mountain')}})
    lang=json.loads((RES/'assets/myvillage/lang/zh_cn.json').read_text())
    for m in c['mobs']:
        lang['entity.myvillage.'+m['id']]=m['name'];lang['item.myvillage.'+m['id']+'_spawn_egg']=m['name']+'刷怪蛋';lang['item.myvillage.'+m['material'][0]]=m['material'][1]
    for item in c['items']:
        lang['item.myvillage.'+item['id']]=item['name'];lang['tooltip.myvillage.legacy.'+item['id']]=item['description']
    for a in c['arts']:
        lang['cultivation.technique.myvillage.'+a['id']]=a['name'];lang['combat.myvillage.art.'+a['id']+'.description']=a['description']
    lang.update({'cultivation.school.myvillage.legacy':'遗迹传承','entity.myvillage.legacy_projection':'传承灵相','block.myvillage.sword_inscription':'剑痕碑','block.myvillage.mountain_stele':'镇岳碑','item.myvillage.sword_rubbing':'剑痕拓本','item.myvillage.mountain_rubbing':'镇岳碑拓','legacy.myvillage.blocked':'当前位置无法完成这次施法。','legacy.myvillage.no_weapon':'请手持这门功法所需的器物。','legacy.myvillage.deployed':'器物尚在外面，请先召回。','legacy.myvillage.empty':'器物中没有可释放的灵力。','legacy.myvillage.charge':'储存：%s / %s','legacy.myvillage.trial':'石碑已唤醒守护者，完成试炼后用纸拓印。','legacy.myvillage.paper':'完成试炼后，对石碑使用纸可以获得拓本。','legacy.myvillage.claimed':'此处碑文已经拓印。','legacy.myvillage.water':'将绢卷浸入水中，再右键展开。','legacy.myvillage.mirror_ready':'镜面已张开，请迎向来袭的箭矢或小火球。','legacy.myvillage.mirror_caught':'照水镜已截住一道攻击。'})
    emit('assets/myvillage/lang/zh_cn.json',lang)
    commands=['# 给予验收物品，不改变境界和学习状态']+['give @s myvillage:'+i['id'] for i in c['items']]+['myvillage cultivation manual @s myvillage:'+a['id'] for a in c['arts']]+['give @s myvillage:'+m['id']+'_spawn_egg' for m in c['mobs']]
    raw('data/myvillage/function/legacy_encounters_kit.mcfunction',('\n'.join(commands)+'\n').encode())
    guide='---\nnavigation:\n  title: 遗迹传承\n  parent: index.md\n  position: 44\n  icon: myvillage:guiyan_sword\n---\n# 四组遗迹传承\n\n四门功法都需炼气四层，研读后在 H → 技能中分配，按 R 进入修炼战斗模式。再次按相同技能键可召回飞剑、返回残影或收回镇岳尺；潜行施放伏火诀会引爆已有火种。\n\n'
    for m,a,item in zip(c['mobs'],c['arts'],c['items']):
        guide+=f'## {m["name"]}\n\n{m["role"]}\n\n{a["name"]}：{a["description"]}\n\n{item["name"]}：{item["description"]}\n\n'
    guide+='剑痕碑与镇岳碑在主世界新生成的森林、平原、草甸和针叶林小遗迹内出现。完成对应守护者试炼后用纸拓印。照影妖出现在沼泽和河岸，水显绢卷在水中右键可得到分影步秘籍。吞火蟾出现在恶地和热带草原，余焰丹砂配书与墨囊可制成伏火诀秘籍。\n\n四种器物的工作台配方如下。\n\n'+''.join('<Recipe id="myvillage:'+i['id']+'" />\n\n' for i in c['items'])
    out[ROOT/'guidebook/getting_started/legacy_encounters.md']=guide.encode()
    return out

def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args();stale=[]
    for path,data in outputs().items():
        if not path.exists() or path.read_bytes()!=data:
            stale.append(str(path.relative_to(ROOT)))
            if not a.check:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    for s in stale:print(('STALE ' if a.check else 'WROTE ')+s)
    return int(a.check and bool(stale))

if __name__=='__main__':raise SystemExit(main())
