"""Authored forge silhouettes, restrained paper UI and vanilla-backed metal sound events."""
import json
from pathlib import Path
from tools.gen_alchemy_assets import Canvas,png
ROOT=Path(__file__).resolve().parents[2]
STAGES=('rough','shaped','quenched','tempered','ground')
NAMES={'qingfeng_sword':('Qingfeng sword','青锋剑'),'lingxiao_spear':('Lingxiao spear','凌霄枪'),
       'forged_pickaxe':('Forged pickaxe','精铁镐'),'forged_axe':('Forged axe','精铁斧'),
       'forged_shovel':('Forged shovel','精铁铲'),'forged_hoe':('Forged hoe','精铁锄')}
ARMOR_NAMES={'xuantie_helmet':'玄铁盔','xuantie_chestplate':'玄铁甲','xuantie_leggings':'玄铁腿甲','xuantie_boots':'玄铁靴'}
ARMOR_PATTERNS={'helmet':['III','IBI'],'chestplate':['IBI','III','III'],'leggings':['III','IBI','I I'],'boots':['I I','IBI']}
STAGE_NAMES=[('Rough billet','粗坯'),('Shaped blank','成形坯'),('Quenched blank','淬火坯'),('Tempered blank','回火坯'),('Ground blank','精磨坯')]
LANG={
 'workshop.myvillage.status.15':('Take the implement to a forging ding for spirit infusion.','炼灵须移步炼器鼎，器物可随身带走。'),
 'workshop.myvillage.strike_gain.attack':('This strike: +%s attack','此锤收益：攻击 +%s'),
 'workshop.myvillage.strike_gain.speed':('This strike: +%s%% work speed','此锤收益：效率 +%s%%'),
 'workshop.myvillage.strike_gain.durability':('This strike: +%s%% durability','此锤收益：耐久 +%s%%'),
 'workshop.myvillage.strike_gain.qi':('This strike: %s%% less Qi','此锤收益：节灵 %s%%'),
 'block.myvillage.forging_anvil':('Smithing hearth','锻器台'),
 'tag.item.myvillage.forging_fuels':('Coal or charcoal','煤炭或木炭'),
 'workshop.myvillage.title':('Forge','锻器'),
 'workshop.myvillage.seal':('工','工'),
 'workshop.myvillage.patterns':('Patterns','器谱'),
 'workshop.myvillage.resume':('Keep this piece; continue at a smithing hearth or forging ding.','器坯可取走保存，放回锻器台继续。'),
 'workshop.myvillage.formed':('Shaping %s / %s','成形 %s / %s'),
 'workshop.myvillage.experience':('Experience %s · %s strikes ready','历练 %s · 可锻 %s 次'),
 'workshop.myvillage.tempering':('Tempering %s / %s','再锻 %s / %s'),
 'workshop.myvillage.bonus.attack':('Tempered attack +%s','再锻攻击 +%s'),
 'workshop.myvillage.bonus.speed':('Tempered work speed +%s%%','再锻效率 +%s%%'),
 'workshop.myvillage.bonus.durability':('Tempered durability +%s%%','再锻耐久 +%s%%'),
 'workshop.myvillage.bonus.qi':('Tempered Qi saving %s%%','再锻节灵 %s%%'),
 'workshop.myvillage.heat.cold':('Cold · reheat','已冷 · 须回炉'),
 'workshop.myvillage.heat.hot':('Ready for the hammer','火候正好'),
 'workshop.myvillage.heat.none':('Prepare a billet','备料起坯'),
 'workshop.myvillage.directions':('Tempering direction','再锻所向'),
 'workshop.myvillage.traits':('Spirit affinity','炼灵专长'),
 'workshop.myvillage.path.1':('Edge / work','精工'),
 'workshop.myvillage.path.2':('Endurance','耐用'),
 'workshop.myvillage.path.3':('Qi flow','导灵'),
 'workshop.myvillage.path_hint.1':('Each strike improves attack for weapons, work speed for tools.','每锤提高兵器攻击，或工具采掘效率。'),
 'workshop.myvillage.path_hint.2':('Each strike permanently increases maximum durability.','每锤永久提高最大耐久。'),
 'workshop.myvillage.path_hint.3':('Each strike reduces Qi costs. Requires an infused compatible weapon.','每锤降低耗灵；须为可导灵且已经炼灵的兵器。'),
 'workshop.myvillage.treatment':('Heat treatment','热处理取向'),
 'workshop.myvillage.treatment.0':('Balanced','中和'),
 'workshop.myvillage.treatment.1':('Edge','保刃'),
 'workshop.myvillage.treatment.2':('Tough','韧性'),
 'workshop.myvillage.treatment_hint.0':('Half edge/work and half durability benefit. Fixed on quenching.','兼顾刃口与耐用，淬火时定下取向。'),
 'workshop.myvillage.treatment_hint.1':('More attack or work speed. Fixed on quenching.','侧重攻击或采掘效率，淬火时定下取向。'),
 'workshop.myvillage.treatment_hint.2':('More durability. Fixed on quenching.','侧重耐久，淬火时定下取向。'),
 'workshop.myvillage.per_strike':('Next: %s exp + materials','每锤：%s 历练及辅材'),
 'workshop.myvillage.hold':('Hold to hammer; release to stop.','按住连续锻打，松开停止。'),
 'workshop.myvillage.mode.training':('Train','再锻'),
 'workshop.myvillage.cap':('Spirit quality: %s','炼灵上限：%s'),
 'workshop.myvillage.cap_locked':('Awaken to infuse','觉醒后可炼灵'),
 'workshop.myvillage.costs':('Consumes:','消耗：'),
 'workshop.myvillage.no_cost':('No extra materials','无额外耗材'),
 'workshop.myvillage.input':('In','器物'),
 'workshop.myvillage.output':('Out','成器'),
 'workshop.myvillage.take':('Take the finished item','请取走成器'),
 'workshop.myvillage.inventory':('Belongings','行囊'),
 'workshop.myvillage.earned_hint':('Use the item to earn more experience. Placed/reworked blocks do not count.','使用器物积累历练；人工放置或重复采集不计入。'),
 'workshop.myvillage.stage.5':('Finished implement','器身已成'),
}
# New UI text is Chinese; compatibility language files retain their existing entries.
for key,value in {
 'infusion.title':'装备炼灵', 'infusion.equipment':'待炼装备',
 'infusion.page':'专长 %s / %s ›', 'infusion.empty_traits':'放入装备后选择专长',
 'infusion.hint':'放入成品装备，选定专长后炼灵。', 'infusion.working':'正在炼灵…',
 'status.18':'该操作不属于当前工位。',
}.items():LANG['workshop.myvillage.'+key]=(value,value)
for i,names in enumerate(STAGE_NAMES):LANG[f'workshop.myvillage.stage.{i}']=names
for k,v in {'start':('Prepare billet','起坯'),'heat':('Reheat','入火'),'strike':('Hammer','锻打'),'quench':('Quench','淬火'),
            'temper':('Temper','回火'),'grind':('Grind','精磨'),'assemble':('Fit handle','装柄'),'infuse':('Infuse','炼灵'),
            'repair':('Repair','修补'),'idle':('At rest','待作'),'copper':('Spirit copper','赤铜灌灵')}.items():LANG['workshop.myvillage.action.'+k]=v
STATUSES=[('Prepare a billet, or insert an unfinished piece or implement.','可起新坯，也可放入器坯或成品续作。'),
('Take the finished item first.','先取走成器。'),('This operation does not match the current stage.','此工序不适用于当前阶段。'),
('The piece is cold. Reheat before striking or quenching.','器物已冷，请先入火再锻打或淬火。'),('Materials are missing; nothing was spent.','材料不足，本次未扣除。'),
('Use this item to earn experience before tempering it again.','历练不足，携此器历练后再锻。'),('This implement has reached its tempering limit.','此器已达可锻上限。'),
('Awaken before infusing; ordinary smithing is available now.','觉醒后可炼灵；普通锻造不受限制。'),('Read the matching pattern first.','须先研读对应器谱。'),
('Insert a piece, or empty the work slot before preparing a new one.','请放入器物；起新坯前须清空器物槽。'),('The current motion is finishing.','当前工序尚未结束。'),
('This direction or affinity is not available on this implement.','此器不支持该方向或专长。'),('Complete shaping first.','先完成锻打成形。'),
('Strike committed.','此锤已成。'),('Operation complete. Continue, or take the piece away.','此序已毕，可续作或取走保存。')]
for i,v in enumerate(STATUSES):LANG[f'workshop.myvillage.status.{i}']=v
for recipe,names in NAMES.items():
 if recipe.startswith('forged_'):
  LANG['item.myvillage.'+recipe]=names
  LANG['item.myvillage.pattern_'+recipe]=('Pattern: '+names[0],names[1]+'器谱')
 for stage,stage_names in zip(STAGES,STAGE_NAMES):
  LANG[f'item.myvillage.workpiece_{recipe}_{stage}']=(names[0]+' · '+stage_names[0],names[1]+'·'+stage_names[1])


def panel(infusion=False):
    rows=[]
    for y in range(234):
        row=[]
        for x in range(316):
            grain=((x*29+y*17+x*y*3)%11)//4-1
            base=(219,207,178) if 4<x<311 and 4<y<229 else (53,52,44)
            if not infusion and 8<x<82 and 26<y<128:base=(207,191,157)
            if not infusion and 87<x<209 and 26<y<124:base=(193,179,149)
            if 140<x<308 and 146<y<229:base=(47,51,46)
            if x in (3,312) or y in (3,230):base=(139,112,70)
            if (not infusion and x in (85,211) and 27<y<126) or (y==144 and 10<x<306):base=(155,136,100)
            row.append(tuple(max(0,min(255,n+grain)) for n in base)+(255,))
        rows.append(row)
    return rows


def anvil_sprite():
    c=Canvas();c.poly([(2,9),(26,9),(30,11),(25,14),(20,14),(19,21),(25,24),(25,27),(7,27),(7,24),(12,21),(11,14),(5,13)],(40,43,42))
    c.rect(5,9,25,11,(133,141,132));c.line(6,9,24,9,(193,195,170));c.rect(13,14,17,22,(68,76,72));c.rect(8,24,24,25,(81,87,78));return c.rows


def hammer_sprite():
    c=Canvas();c.rect(14,11,17,29,(47,33,24));c.rect(15,12,16,28,(135,86,44));c.rect(7,5,24,13,(38,43,42));c.rect(8,6,22,11,(104,120,115));c.rect(8,6,22,7,(190,196,174));c.rect(10,11,22,12,(65,76,71));c.rect(14,8,17,12,(144,101,56));return c.rows


def outputs(root=ROOT):
    base=root/'src/main/resources';out={};encode=lambda o:(json.dumps(o,ensure_ascii=False,indent=2)+'\n').encode()
    def add(path,obj):out[base/path]=encode(obj)
    add('assets/myvillage/font/forge_detail.json',{'providers':[
        {'type':'ttf','file':'myvillage:book.ttf','size':10.0,'oversample':4.0,'shift':[0.0,0.0],'skip':'回'},
        {'type':'reference','id':'myvillage:panel_body'}]})
    out[base/'assets/myvillage/textures/gui/equipment_infusion_panel.png']=png(panel(infusion=True))
    config=json.loads((base/'data/myvillage/forging/workshop.json').read_text())
    for craft in config['crafts']:
        recipe,kind=craft['recipe'],craft['kind']
        if kind in ARMOR_PATTERNS:
            pattern='pattern_'+recipe
            add(f'assets/myvillage/models/item/{pattern}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/book'}})
            add(f'data/myvillage/recipe/{pattern}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':ARMOR_PATTERNS[kind],
                'key':{'I':{'item':'minecraft:iron_ingot'},'B':{'item':'minecraft:book'}},'result':{'id':'myvillage:'+pattern,'count':1}})
        if recipe.startswith('forged_'):
            pattern='pattern_'+recipe
            add(f'assets/myvillage/models/item/{pattern}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/book'}})
            add(f'data/myvillage/recipe/{pattern}.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:book'},{'item':'minecraft:iron_ingot'}],'result':{'id':'myvillage:'+pattern}})
    for name,rows in [('panel',panel()),('anvil',anvil_sprite()),('hammer',hammer_sprite())]:out[base/f'assets/myvillage/textures/gui/workshop_{name}.png']=png(rows)
    add('data/myvillage/tags/item/forging_fuels.json',{'replace':False,'values':['minecraft:coal','minecraft:charcoal']})
    from tools.forging import anvil
    out.update(anvil.outputs(base))
    from tools.forging.models import Catalogue
    out.update(Catalogue(root).outputs())
    from tools.forging import trough
    out.update(trough.outputs(base))
    add('assets/myvillage/models/item/forging_anvil.json',{'parent':'myvillage:block/forging_anvil','display':{'gui':{'rotation':[25,225,0],'scale':[.65,.65,.65]}}})
    add('data/myvillage/loot_table/blocks/forging_anvil.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'myvillage:forging_anvil'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    add('data/myvillage/recipe/forging_anvil.json',{'type':'minecraft:crafting_shaped','pattern':['III','WWW','SSS'],'key':{'I':{'item':'minecraft:iron_ingot'},'W':{'tag':'minecraft:logs'},'S':{'item':'minecraft:cobblestone'}},'result':{'id':'myvillage:forging_anvil'}})
    tagpath=base/'data/minecraft/tags/block/mineable/pickaxe.json';tag=json.loads(tagpath.read_text());tag['values']=sorted(set(tag['values'])|{'myvillage:forging_anvil','myvillage:forging_table','myvillage:quenching_trough'});out[tagpath]=encode(tag)
    sounds_path=base/'assets/myvillage/sounds.json';sounds=json.loads(sounds_path.read_text())
    events={'strike':[('block.anvil.use',1.55,.65),('block.anvil.use',1.7,.58),('block.anvil.use',1.4,.7)],
            'heat':[('item.firecharge.use',.65,.45)],'quench':[('block.fire.extinguish',.75,.7)],
            'temper':[('block.fire.ambient',.9,.7)],'grind':[('block.grindstone.use',.85,.65)],
            'assemble':[('block.smithing_table.use',.85,.6)],'infuse':[('block.amethyst_block.chime',.75,.6)],'repair':[('block.anvil.use',1.2,.55)]}
    for name,variants in events.items():
        key='forging.'+name;sounds[key]={'subtitle':'subtitles.myvillage.'+key,'sounds':[{'name':'minecraft:'+e,'type':'event','pitch':p,'volume':v} for e,p,v in variants]}
    out[sounds_path]=encode(sounds)
    for lang,i in [('en_us',0),('zh_cn',1)]:
        path=base/f'assets/myvillage/lang/{lang}.json';data=json.loads(path.read_text());data.update({k:v[i] for k,v in LANG.items()})
        for name in events:data['subtitles.myvillage.forging.'+name]=LANG['workshop.myvillage.action.'+name][i]
        out[path]=encode(data)
    path=base/'assets/myvillage/lang/zh_cn.json'
    data=json.loads(out[path]);data['workshop.myvillage.occupied']='此台正有人锻器，请稍候或使用另一座锻器台。';out[path]=encode(data)
    for recipe,name in ARMOR_NAMES.items():
        data['item.myvillage.pattern_'+recipe]=name+'器谱'
        for stage,names in zip(STAGES,STAGE_NAMES):data[f'item.myvillage.workpiece_{recipe}_{stage}']=name+'·'+names[1]
    data.update({'workshop.myvillage.recipe_page':'器谱 %s/%s ›',
        'workshop.myvillage.action.armor.assemble':'铆合衬垫','workshop.myvillage.action.armor.grind':'修整甲片',
        'workshop.myvillage.armor_earned_hint':'穿戴此甲主动攻击敌对生物可积累历练；挨打、背包携带和重复治疗目标不增加额度。'})
    data.update({
        'item.myvillage.forging_hammer':json.loads((root/'equipment/workshop/hammer.json').read_text())['name'],
        'workshop.myvillage.manual.hammer_hint':'手持右键敲击台上热坯；潜行右键打开其余工序面板。',
        'workshop.myvillage.manual.placed':'器物已放上锻台。潜行右键入火，关闭面板后持锻锤右键敲击。',
        'workshop.myvillage.manual.take_first':'台上已有器物，请先空手右键取回。',
        'workshop.myvillage.manual.empty':'手持器坯右键放置；潜行右键可起坯、入火和进行后续工序。',
        'workshop.myvillage.manual.panel_hint':'手持锻锤',
        'workshop.myvillage.status.17':'锻打须关闭面板，手持锻锤右键台上器物。',
        'workshop.myvillage.status.0':'可在此起坯或处理热工序；锻打请关闭面板后手持锻锤右键。',
        'workshop.myvillage.resume':'器坯可取走保存，手持右键放回锻器台。潜行右键打开其余工序面板。',
        'workshop.myvillage.hold':'手持锻锤右键敲击；连按仍受每锤节奏限制。',
        'workshop.myvillage.occupied':'此台正在操作，请稍候或使用另一座锻器台。',
    })
    data.update({
        'block.myvillage.quenching_trough': '淬火水槽',
        'workshop.myvillage.status.16': '须在锻台同层紧邻处摆放有水且空闲的淬火水槽。',
        'workshop.myvillage.trough.empty': '空槽：用水桶注水，随后可在相邻锻器台淬火。',
        'workshop.myvillage.trough.ready': '水已备好，可在相邻锻器台淬火；空桶可取回水。',
        'workshop.myvillage.trough.busy': '正在淬火，请待器坯出水。',
        'workshop.myvillage.trough.cost': '消耗相邻水槽内一桶水；水桶注水时已返还空桶。',
    })
    out[path]=encode(data)
    return out
