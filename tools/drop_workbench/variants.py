"""从实际注册输入读取品质适用族，原版稀有度保留原生语义。"""
from __future__ import annotations
import json,re

RARITIES=[{'id':key,'name':name,'color':color} for key,name,color in [
    ('common','普通','#ffffff'),('uncommon','少见','#ffff55'),('rare','稀有','#55ffff'),('epic','史诗','#ff55ff')]]

def attach(root,items,lang):
    kinds={}
    # 装备目录是 EquipmentContent 注册防具的实际输入。
    equipment=json.loads((root/'src/main/resources/data/yuanxian/equipment/catalogue.json').read_text())
    for v in equipment['items']:kinds[v['id']]='equipment'
    source=(root/'src/main/java/com/example/myvillage/item/ModItems.java').read_text()
    registrations=list(re.finditer(r'ITEMS\.registerItem\(\s*"([a-z0-9_]+)"',source))
    fixed=set()
    for i,match in enumerate(registrations):
        body=source[match.end():registrations[i+1].start() if i+1<len(registrations) else len(source)]
        id='yuanxian:'+match.group(1)
        if re.search(r'new\s+(?:AlchemyPillItem|FoundationPillItem)\s*\(',body):kinds[id]='pill'
        elif re.search(r'new\s+CombatWeaponItem\s*\(|new\s+(?:com\.example\.myvillage\.forging\.)?WorkshopTools\.',body):kinds[id]='equipment'
        if re.search(r'new\s+NingyuanFruitItem\s*\(',body):fixed.add(id)
    # 遗迹兵器由专属内容类注册，读取其真实构造类型。
    for path in (root/'src/main/java/com/example/myvillage').rglob('*Content.java'):
        text=path.read_text()
        for m in re.finditer(r'registerItem\(\s*"([a-z0-9_]+)"\s*,\s*\w+\s*->\s*new\s+CombatWeaponItem\b',text):kinds['yuanxian:'+m.group(1)]='equipment'
        for m in re.finditer(r'DeferredItem<CombatWeaponItem>\s+\w+\s*=\s*[\w.]*registerItem\(\s*"([a-z0-9_]+)"',text):kinds['yuanxian:'+m.group(1)]='equipment'
    materials=json.loads((root/'src/main/resources/data/yuanxian/beast_materials/materials.json').read_text())
    material_map={v['id']:v for v in materials['variants']};fixed.update(material_map)
    for v in items:
        kind=kinds.get(v['id'],'');v['quality_kind']=kind
        v['qualities']=list(range(1 if kind=='pill' else 0,5)) if kind else []
        v['rarities']=[r['id'] for r in RARITIES] if not kind and v['id'] not in fixed else []
        if v['id'] in material_map:v['beast_material']=material_map[v['id']]
    colors=['#ffffff','#55ff55','#5555ff','#aa55ff','#ffaa00']
    authored=json.loads((root/'tools/beast_materials/catalogue.json').read_text())
    drops=json.loads((root/'src/main/resources/data/yuanxian/drops/monster_drops.json').read_text())
    weights=next(d['rarity_weights'] for a in drops['additions'] for d in a['drops'] if d.get('factory')=='beast_material')
    return {'qualities':[{'id':q,'name':lang.get('forging.yuanxian.quality.'+str(q),['凡品','下品','中品','上品','极品'][q]),'color':colors[q]} for q in range(5)],'rarities':RARITIES,
            'material_rarities':[{'id':v['id'],'name':v['name'],'color':'#'+v['color']} for v in authored['rarities']], 'material_weights_default':weights}
