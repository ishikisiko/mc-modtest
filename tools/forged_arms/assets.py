"""Build finished weapon models, combat definitions and material recipes."""
from __future__ import annotations

from functools import lru_cache
from io import BytesIO
import json
from pathlib import Path

from tools.equipment import mesh
from tools.gen_qingfeng_sword_model import FIST_CENTER_ITEM, OLD_HANDLE_CENTER, derive, mirror
from tools.language_assets import load_language, language_text
from . import flow
from .geometry import PALETTES, TEXTURES, accepted_models, make_texture
from .older_swords import existing_models

ROOT=Path(__file__).resolve().parents[2]
SOURCE=Path(__file__).with_name('catalogue.json')
ASSETS='assets/yuanxian/'
DATA='data/yuanxian/'
MATERIAL_RECIPES={
    'zhuji_baiqing_lingyun_ingot': ('yuanxian:chitong_ingot','minecraft:quartz','#c:spirit_stones/medium_grade'),
    'jindan_jinwu_ingot': ('yuanxian:xuantie_ingot','minecraft:gold_ingot','minecraft:blaze_powder','#c:spirit_stones/high_grade'),
}


def encoded(obj):
    return (json.dumps(obj,ensure_ascii=False,indent=2)+'\n').encode()


@lru_cache(maxsize=1)
def specs():
    values=json.loads(SOURCE.read_text(encoding='utf-8'))
    assert len({s['id'] for s in values})==len(values)==10
    return values


@lru_cache(maxsize=1)
def shapes():
    return accepted_models()|existing_models()


def source(spec):
    model=shapes()[spec['shape']]
    offset=-16 if spec['family']=='spear' else 0
    f=mesh.map_points(model.faces,lambda p:(p[0]+8,p[1]+offset,p[2]+8))
    for part in f:
        part['metal'] = (min(p[1] for p in part['points'])>=spec['guard']+offset-.05
                         and not part['part'].startswith(('缠','握','柄','枪尾','压缠')))
    return f,(8,model.grip_y+offset,8),offset


def display(spec, faces, grip, root=ROOT):
    f=mesh.item_display(faces,grip=grip,root=root)
    if spec['family']=='spear':
        from tools import gen_lingxiao_spear_model as spear
        third=spear.derive('thirdperson_righthand',grip,spear.THIRD_PERSON_SCALE,anchor_target=FIST_CENTER_ITEM)
        first=spear.derive('firstperson_righthand',grip,spear.FIRST_PERSON_SCALE,
            anchor_old=OLD_HANDLE_CENTER,roll=True,tilt=spear.FIRST_PERSON_TILT)
        f.update(thirdperson_righthand=third,thirdperson_lefthand=spear.left_hand(third),
                 firstperson_righthand=first,firstperson_lefthand=spear.left_hand(first))
    else:
        from tools.gen_qingfeng_sword_model import THIRD_PERSON_SCALE
        third=derive('thirdperson_righthand',grip,anchor_target=FIST_CENTER_ITEM,scale=THIRD_PERSON_SCALE)
        first=derive('firstperson_righthand',grip,anchor_old=OLD_HANDLE_CENTER)
        f.update(thirdperson_righthand=third,thirdperson_lefthand=mirror(third),
                 firstperson_righthand=first,firstperson_lefthand=mirror(first))
    return f


def textures():
    result={}
    for family,palette in PALETTES.items():
        for key,(color,style) in palette.items():
            out=BytesIO()
            make_texture(color,style).save(out,'PNG')
            result[ASSETS+f'textures/{TEXTURES}/{family}/{key}.png']=out.getvalue()
    return result


def geometry(spec, faces, grip, offset):
    section=[f for f in faces if f['part'] in {'刃身','一体刃身与浮雕截面','刃身封口'}]
    low,high=mesh.bounds(faces)
    blade_low,blade_high=mesh.bounds(section)
    head_y=blade_low[1]
    peak = {tuple(p) for part in section for p in part['points'] if abs(p[1]-blade_high[1])<1.e-6}
    head_tip = [(min(p[i] for p in peak)+max(p[i] for p in peak))/2 for i in range(3)]
    guard_y=spec['guard']+offset
    butt_y=low[1]+(.85 if spec['family']=='sword' else 1.5)
    handle={'y':[butt_y,guard_y], 'half_width':.76,'half_thickness':.76}
    g={'format':2,'units':'model_pixels','generator':'tools/gen_forged_arms.py',
       'model':'yuanxian:item/'+spec['id'],
       'axes':{'length':'+y','flat_normal':'x','edge':'z','center_x':8,'center_z':8},
       'grip_center':grip,'handle':handle,
       'collar':{'y':[guard_y,head_y], 'half_width':(high[2]-low[2])/2,'half_thickness':(high[0]-low[0])/2},
       'butt':{'y':[low[1],butt_y],'half_width':.85,'half_thickness':.85},
       'head':{'half_width':(blade_high[2]-blade_low[2])/2,'half_thickness':(blade_high[0]-blade_low[0])/2,
               'ridge_half_thickness':(blade_high[0]-blade_low[0])/2,'taper_start_y':head_y+(high[1]-head_y)*.78},
       'head_base':[8,head_y,8],'head_tip':head_tip,
       'edge_axis':[0,0,1],'flat_axis':[1,0,0],'overall_y':[low[1],high[1]]}
    if spec['family']=='spear':
        g.update(off_hand_grip_center=[8,min(guard_y-1.4,grip[1]+12),8],
                 trail={'base':[8,guard_y,8],'tip':g['head_tip']})
    return g


def emit_models(root):
    result=textures()
    for spec in specs():
        rid=spec['id']
        faces,grip,shift=source(spec)
        geo=geometry(spec,faces,grip,shift)
        result[ASSETS+f'combat/{rid}_geometry.json']=encoded(geo)
        family=spec['family']
        rig='lingxiao_spear' if family=='spear' else 'qingfeng'
        result[DATA+f'combat/weapon/{rid}.json']=encoded({
            'schema':1,'item':'yuanxian:'+rid,'family':family,'style':'yuanxian:basic_'+family,
            'first_person_rig':f'yuanxian:combat/{rig}_first_person.json',
            'geometry':f'yuanxian:combat/{rid}_geometry.json'})
        draw=list(faces)
        if spec.get('glow'):
            ref=f'yuanxian:{TEXTURES}/jinwu/flow_{family}'
            draw+=flow.overlays(faces,family,ref,shift)
            png,meta=flow.texture(family)
            target=ASSETS+f'textures/{TEXTURES}/jinwu/flow_{family}.png'
            result[target]=png
            result[target+'.mcmeta']=encoded(meta)
        files,model=mesh.obj_assets(draw,'yuanxian:item/'+rid+'_forged',display(spec,faces,grip,root))
        if spec.get('glow'):
            mtl=ASSETS+f'models/item/{rid}_forged.mtl'
            blocks=files[mtl].decode().split('\n\n')
            blocks=[b.replace('Ka 0 0 0','Ka 1 1 1') if f'map_Kd {ref}' in b else b for b in blocks]
            files[mtl]='\n\n'.join(blocks).encode()
            model.update(emissive_ambient=True,render_type='minecraft:cutout')
        result.update(files)
        result[ASSETS+f'models/item/{rid}.json']=encoded(model)
    return result


def ingredient(ref):
    return {'tag':ref[1:]} if ref.startswith('#') else {'item':ref}


def shapeless(refs,output):
    return {'type':'minecraft:crafting_shapeless','category':'equipment',
            'ingredients':[ingredient(v) for v in refs],'result':{'id':'yuanxian:'+output,'count':1}}


def replace_rows(rows,key,updates):
    result=[updates.get(row[key],row) for row in rows]
    present={row[key] for row in rows}
    return result+[row for name,row in updates.items() if name not in present]


def outputs(root=ROOT):
    base=root/'src/main/resources'
    result=emit_models(root)
    def read(rel):return json.loads((base/rel).read_text(encoding='utf-8'))
    for name,refs in MATERIAL_RECIPES.items():
        result[DATA+f'recipe/{name}_alloy.json']=encoded(shapeless(refs,name))
    recipes,workshop={},{}
    entries=[]
    extra_swords=[]
    for spec in specs():
        rid,material=spec['id'],spec['ingot']
        extra_swords.append('yuanxian:'+rid)
        costs={material:spec['amount'],'#c:spirit_stones/low_grade':2}
        if spec.get('extra'):costs[spec['extra']]=1
        recipes[rid]={'id':rid,'pattern':'yuanxian:pattern_'+rid,'output':'yuanxian:'+rid,'costs':costs,
                     'traits':['sharp','channeling','tough'],'quality_tier':'yuanxian:'+spec['tier'],
                     'infusion_material':material}
        workshop[rid]={'recipe':rid,'kind':spec['family'],'requires_pattern':True,'max_strikes':60,
                       'work_experience':0,'forming_points':spec['points'],'grade':spec['grade']}
        result[DATA+f'recipe/pattern_{rid}.json']=encoded(
            shapeless(('minecraft:book',material,'#c:spirit_stones/low_grade'),'pattern_'+rid))
        if spec.get('new'):
            entries.append({k:spec[k] for k in ('id','family','item_tier','attack')})
    result[DATA+'combat/forged_arms.json']=encoded(entries)
    for rel,field,key,changed in (
        (DATA+'forging/rules.json','recipes','id',recipes),
        (DATA+'forging/workshop.json','crafts','recipe',workshop),
    ):
        doc=read(rel)
        doc[field]=replace_rows(doc[field],key,changed)
        result[rel]=encoded(doc)
    index=DATA+'combat/index.json'
    doc=read(index)
    doc['weapons']=list(dict.fromkeys(doc['weapons']+extra_swords))
    result[index]=encoded(doc)
    tag='data/minecraft/tags/item/swords.json'
    doc=read(tag)
    doc['values']=list(dict.fromkeys(doc['values']+extra_swords))
    result[tag]=encoded(doc)
    melees=['yuanxian:qingfeng_sword',*[('yuanxian:'+s['id']) for s in specs() if s['family']=='sword']]
    result[DATA+'tags/item/melee_swords.json']=encoded({'replace':False,'values':melees})
    stages={'rough':'粗坯','shaped':'成形坯','quenched':'淬火坯','tempered':'回火坯','ground':'精磨坯'}
    for locale in ('zh_cn','en_us'):
        rel=ASSETS+f'lang/{locale}.json'
        original=load_language((base/rel).read_text(encoding='utf8'));lang=dict(original)
        for spec in specs():
            rid,name=spec['id'],spec['name']
            key=f'item.yuanxian.{rid}'
            if locale=='zh_cn' or key not in lang:
                lang[key]=name
            lang[f'item.yuanxian.pattern_{rid}']=name+'器谱'
            for key,label in stages.items():
                lang[f'item.yuanxian.workpiece_{rid}_{key}']=name+'·'+label
        result[rel]=language_text(original,lang).encode()
    return {base/rel:raw for rel,raw in result.items()}
