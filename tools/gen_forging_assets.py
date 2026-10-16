#!/usr/bin/env python3
"""Forging resources: iron/copper ding, existing pattern books and acquisition assets."""
import argparse
import json
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:sys.path.insert(0,str(ROOT))
from tools.forging import ding,materials
LANG={
 'block.myvillage.forging_table':('Forging Ding','炼器鼎'),
 'item.myvillage.pattern_qingfeng_sword':('Pattern: Qingfeng Sword','青锋剑器谱'),
 'item.myvillage.pattern_lingxiao_spear':('Pattern: Lingxiao Spear','凌霄枪器谱'),
 'tooltip.myvillage.forging.pattern_learn':('Use to permanently learn this pattern.','右键使用，永久学会此器谱。'),
 'message.myvillage.forging.formula_learned':('Learned %s','已学会：%s'),
 'message.myvillage.forging.formula_known':('Already learned %s','已经学会：%s'),
 'tooltip.myvillage.forging.mark':('%s · %s','%s · %s'),
 'tooltip.myvillage.forging.sharp':('Forging attack bonus: +%s','炼器攻击加成：+%s'),
 'tooltip.myvillage.forging.channeling':('Sword Qi cost reduction: %s%%','剑气耗灵降低：%s%%'),
 'tooltip.myvillage.forging.tough':('Durability and repair yield: ×%s','耐久与维修效率：×%s'),
}
for i,(en,zh) in enumerate([('Lower','下品'),('Middle','中品'),('Upper','上品'),('Perfect','极品')],1):LANG[f'forging.myvillage.quality.{i}']=(en,zh)
for key,en,zh in [('sharp','Sharp','锋锐'),('channeling','Channeling','导灵'),('tough','Tough','坚韧')]:LANG[f'forging.myvillage.trait.{key}']=(en,zh)
SCREEN={
 'forge':('Forge','炼制'),'repair':('Repair','维修'),'cap':('Quality cap: %s','品质上限：%s'),
 'awaken':('Awaken before forging','觉醒后可炼制'),'costs':('Materials from inventory','从背包消耗以下材料'),
 'slots':('In → Out','维修 / 产物'),'learn_hint':('Use the matching pattern first.','先右键使用对应器谱。'),
 'forge_hint':('Forge one weapon. Quality is rolled within your aptitude cap. Take the output before forging again.','每次炼制一件，随机品质不超过资质上限。取走产物后才能再次炼制。'),
 'repair_hint':('Put a damaged sword or spear in the left slot. Repair preserves quality, name and enchantments. Costs per use:','将受损剑枪放入左槽。维修保留品质、命名与附魔。每次消耗：'),
 'trait.sharp':('Edge-tempering sand increases attack damage. Quality improves the bonus.','淬锋砂提高攻击伤害，品质越高加成越大。'),
 'trait.channeling':('Spirit copper reduces learned Sword Qi costs. Does not grant the skill.','赤铜锭降低已学剑气的耗灵，不会直接授予剑气。'),
 'trait.tough':('Dark iron increases durability and repair yield.','玄铁锭提高最大耐久和单次维修量。'),
 'unsupported':('This weapon has no matching Qi skill yet.','此武器暂时没有对应的耗灵主动技。'),
 'status.0':('Choose a pattern and trait.','选择器谱和专长。'),'status.1':('Pattern not learned.','尚未学会器谱。'),
 'status.2':('Awaken first.','请先觉醒。'),'status.3':('Take the output first.','请先取走产物。'),
 'status.4':('Missing materials.','背包材料不足。'),'status.5':('Insert a damaged weapon.','请放入受损剑枪。'),
 'status.6':('Check repair slot / trait.','检查维修槽或专长。'),'status.7':('Forged. Take the weapon.','炼制完成，请取走。'),
 'status.8':('Repaired. Take the weapon.','维修完成，请取走。')}
LANG.update({'screen.myvillage.forging.'+k:v for k,v in SCREEN.items()})
LANG.update(materials.lang())
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def outputs(root=ROOT):
    files={}
    def add(p,v):files[root/'src/main/resources'/p]=encoded(v)
    add('assets/myvillage/blockstates/forging_table.json',{'variants':{'':{'model':'myvillage:block/forging_table'}}})
    add('assets/myvillage/models/block/forging_table.json',{'parent':'minecraft:block/smithing_table'})
    add('assets/myvillage/models/item/forging_table.json',{'parent':'myvillage:block/forging_table'})
    add('data/myvillage/loot_table/blocks/forging_table.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'myvillage:forging_table'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    def shapeless(id,items):add('data/myvillage/recipe/'+id+'.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':i} for i in items],'result':{'id':'myvillage:'+id,'count':1}})
    shapeless('forging_table',['minecraft:smithing_table','myvillage:low_grade_spirit_stone'])
    for id,material in [('qingfeng_sword','minecraft:quartz'),('lingxiao_spear','minecraft:iron_ingot')]:
        name='pattern_'+id
        add('assets/myvillage/models/item/'+name+'.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/book'}})
        shapeless(name,['minecraft:book',material,'myvillage:low_grade_spirit_stone'])
    for lang,i in [('en_us',0),('zh_cn',1)]:
        path=root/f'src/main/resources/assets/myvillage/lang/{lang}.json'
        current=json.loads(path.read_text());current.update({k:v[i] for k,v in LANG.items()});files[path]=encoded(current)
    path=root/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
    tag=json.loads(path.read_text());tag['values']=sorted(set(tag['values'])|{'myvillage:forging_table'});files[path]=encoded(tag)
    files.update(ding.outputs(root))
    files.update(materials.outputs(root))
    return files
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');p.add_argument('--preview',action='store_true');p.add_argument('--materials-preview',action='store_true');a=p.parse_args();stale=[]
    for path,data in outputs().items():
        if a.check:
            if not path.exists() or path.read_bytes()!=data:stale.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    if stale:print('\n'.join(stale));return 1
    print('Forging assets '+('match' if a.check else 'generated'))
    if a.preview:ding.preview()
    if a.materials_preview:materials.preview()
    return 0
if __name__=='__main__':raise SystemExit(main())
