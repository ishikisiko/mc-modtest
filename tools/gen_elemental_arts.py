#!/usr/bin/env python3
"""五行战技作者入口。生成秘籍、配方、战斗数据、伤害类型和中文指南；--check 不写入。"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path('tools/elemental_arts/arts.json')
DATA = Path('src/main/resources/data/myvillage')
ELEMENTS = {'metal', 'wood', 'water', 'fire', 'earth'}
ROLES = {'basic', 'ward', 'burst', 'field'}
META = {'name', 'description', 'grade', 'minimum_realm', 'minimum_stage', 'study_points', 'previous'}


def dump(value):
    return json.dumps(value, ensure_ascii=False, indent=2) + '\n'


def validate(doc):
    if doc['schema'] != 1 or {s['element'] for s in doc['schools']} != ELEMENTS:
        raise ValueError('五系定义必须齐全')
    arts = doc['arts']
    if len(arts) != 20 or len({a['id'] for a in arts}) != 20:
        raise ValueError('需要二十个不同战技')
    for element in ELEMENTS:
        if {a['role'] for a in arts if a['element'] == element} != ROLES:
            raise ValueError('每系需要常用、护体、爆发、范围四门')
    for a in arts:
        if a['shape'] not in {'bolt', 'ray', 'self', 'cone', 'burst', 'field'}:
            raise ValueError('未知几何形状')
        if (a['role'] == 'ward') != (a['shape'] == 'self'):
            raise ValueError('护体须为自身施法')
        for key, low, high in [('qi_cost',1,1000),('cooldown_ticks',20,1200),('windup_ticks',1,100),
                              ('pulse_interval_ticks',1,100),('pulses',1,7),('recovery_ticks',1,100),
                              ('maximum_targets',1,12),('grade',1,4),('study_points',1,100000),
                              ('slow_ticks',0,100),('slow_amplifier',0,2),('armor_break_ticks',1,400),
                              ('ward_ticks',0,200),('ward_ticks_per_charge',0,100)]:
            if type(a[key]) is not int or not low <= a[key] <= high:
                raise ValueError(f'{a["id"]}: invalid {key}')
        for key, low, high in [('range',1,32),('radius',.1,6),('acquire_arc_degrees',1,90),
                              ('damage_multiplier',.01,4),('ward_fraction',0,.7),('push',0,.8),
                              ('heal_per_charge',0,1),('speed_bonus',0,.5),('armor_break_fraction',0,.3)]:
            v = a[key]
            if isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) or not low <= v <= high:
                raise ValueError(f'{a["id"]}: invalid {key}')
        if a['previous'] is not None and not any(p['id']==a['previous'] and p['element']==a['element'] and p['grade']<=a['grade'] for p in arts):
            raise ValueError('非法前置功法')
        if a['role'] != 'field' and a['pulses'] != 1:
            raise ValueError('只有范围技有多段时间轴')
    for key,value in doc['rules'].items():
        if isinstance(value,bool) or not isinstance(value,(int,float)) or not math.isfinite(value) or value <= 0:
            raise ValueError('非法规则 '+key)
    if not 1 <= doc['rules']['charge_maximum'] <= 6 or doc['rules']['maximum_dots_per_caster'] > 48:
        raise ValueError('暂态状态上限无效')


def plan(root=ROOT):
    doc=json.loads((root/SOURCE).read_text());validate(doc)
    reserved=json.loads((root/'tools/technique_catalogue/rules.json').read_text())['hand_written_techniques']
    if not {a['id'] for a in doc['arts']} <= set(reserved):
        raise ValueError('先向功法生成器预留 ID')
    schools={s['element']:s for s in doc['schools']}
    out={};runtime=[];lang={}
    guide='''---
navigation:
  title: 五行法修
  parent: index.md
  position: 36
  icon: myvillage:manual_active_huang
---
# 五行法修

金、木、水、火、土各自可单独修习，不要求混修，不绑定武器。完成入门后，制作并研读秘籍，在 H → 技能沿用图标格与六键分配，按 R 切入修炼战斗模式。空手或普通物品均可施法；右键剑气、原武技与身法继续保留。

## 成长与配装

炼气一层：常用攻击和护体；炼气四层：本系爆发；筑基初期：固定区域的多段范围技。先修本系常用攻击，再修护体或爆发；范围技需先学爆发。金丹继续享受已有境界成长；第二专精与金丹独有大招尚未开放。

同系常用攻击每次有效命中最多积累一层势，有限层数、超时消散。爆发与护体在成功扣费后消耗本系积蓄，即使随后被打断也不退款。单系可以独立形成循环；不同元素可以分配到同一技能栏，但全部护体共享冷却且只能保留一个护体。

伤害来自战技基础、境界、根基与对应灵根亲和，不读取手持近战攻击力或武器附魔。五系无最低灵根亲和门槛。施法方向、目标、墙体、友军、伤害与成本都由服务器决定。射线不能穿墙；范围技固定落点、需要持续引导，可以走出，施法者身法取消会结束后续段；护体不是无敌。持续伤害只归施法者自己，木、火的引爆不能收割别人的效果。

换槽或更换手持物中断正在施放的战技；重新选择任意物品仍可施法。退出战斗模式、死亡、离线、切维度清除积蓄、护体及自己留下的持续伤害，冷却不会因这些操作刷新；服务器重启清空暂态冷却。

'''
    for a in doc['arts']:
        ident=a['id'];rid='myvillage:'+ident;e=schools[a['element']]
        spell={k:v for k,v in a.items() if k not in META|{'id','qi_cost','cooldown_ticks'}}
        runtime.append({'id':rid,'school':'myvillage:'+a['element']+'_arts','family':'elemental','slot':-1,
            'qi_cost':a['qi_cost'],'cooldown_ticks':a['cooldown_ticks'],'guard_reduction':0,'guard_arc_degrees':180,
            'counter_multiplier':1,'requires_ground':False,'interrupt_on_damage':False,'fx_lifetime_ticks':12,
            'cast_pitch':1.1,'color':e['color'],'particle_count':24,
            'elemental':spell,'phases':[{'move':'myvillage:basic_fist_02_horizontal_palm','shape':'ring','reach':a['radius'],
                'arc_degrees':360,'width':.5,'height':1,'half_height':1,'damage_multiplier':a['damage_multiplier'],
                'maximum_targets':a['maximum_targets'],'step_distance':0,
                'reaction':{'hitstun_ticks':0,'slide_distance':0,'lift':0,'lateral_bias':0},'fx':'ring'}]})
        technique={'translation_key':'cultivation.technique.myvillage.'+ident,'category':'active','grade':a['grade'],
            'elements':['myvillage:'+a['element']],'school':'myvillage:'+a['element']+'_arts',
            'requirements':{'minimum_realm':'myvillage:'+a['minimum_realm'],'minimum_stage':'myvillage:'+a['minimum_stage']},
            'effects':{'active':{'skill':rid,'qi_cost':a['qi_cost'],'slots':1}},
            'study':{'points':a['study_points'],'gates':0,'gate_stability_cost':0}}
        if a['previous']:technique['lineage']={'previous':'myvillage:'+a['previous']}
        out[DATA/f'myvillage/technique/{ident}.json']=dump(technique)
        grade={1:'huang',2:'xuan',3:'di',4:'tian'}[a['grade']]
        # Different role ingredients prevent ambiguous shapeless recipes for four books of one element.
        role_item={'basic':'minecraft:paper','ward':'minecraft:leather','burst':'minecraft:redstone','field':'minecraft:amethyst_shard'}[a['role']]
        ingredients=['minecraft:book','minecraft:ink_sac','myvillage:spirit_stone_low',e['material'],role_item]
        recipe_id='myvillage:manual_'+ident
        out[DATA/f'recipe/manual_{ident}.json']=dump({'type':'minecraft:crafting_shapeless','category':'misc',
            'ingredients':[{'item':i} for i in ingredients],
            'result':{'id':'myvillage:manual_active_'+grade,'count':1,'components':{'myvillage:technique':rid}}})
        out[DATA/f'advancement/recipes/manual_{ident}.json']=dump({'parent':'minecraft:recipes/root',
            'criteria':{'has_book':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'minecraft:book'}]}},
                'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':recipe_id}}},
            'requirements':[['has_book','has_the_recipe']],'rewards':{'recipes':[recipe_id]}})
        lang[technique['translation_key']]=a['name'];lang['combat.myvillage.art.'+ident+'.description']=a['description']
        guide+=f'## {e["name"]} · {a["name"]}\n\n{a["description"]}\n\n灵力 {a["qi_cost"]}；冷却 {a["cooldown_ticks"]/20:g} 秒。\n\n<Recipe id="{recipe_id}" />\n\n'
    for e in doc['schools']:
        ident=e['element'];out[DATA/f'damage_type/elemental_{ident}.json']=dump({'message_id':'myvillage.elemental_'+ident,'exhaustion':.1,'scaling':'never'})
        lang['death.attack.myvillage.elemental_'+ident]='%1$s 被%2$s的'+e['name']+'击败了'
        lang['combat.myvillage.elemental.'+ident]=e['charge_name']+' %s / %s'
    out[DATA/'combat/elemental_arts.json']=dump({'schema':1,'rules':doc['rules'],'arts':runtime})
    # Fire damage is also recognized by vanilla fire immunity/resistance; the other four use their own typed channels.
    firetag=Path('src/main/resources/data/minecraft/tags/damage_type/is_fire.json')
    tag=json.loads((root/firetag).read_text()) if (root/firetag).exists() else {'replace':False,'values':[]}
    if 'myvillage:elemental_fire' not in tag['values']:tag['values'].append('myvillage:elemental_fire')
    out[firetag]=dump(tag)
    for locale in ['zh_cn','en_us']:
        p=Path(f'src/main/resources/assets/myvillage/lang/{locale}.json');existing=json.loads((root/p).read_text());existing.update(lang);out[p]=dump(existing)
    out[Path('guidebook/getting_started/elemental_arts.md')]=guide
    out[DATA/'function/elemental_arts_books.mcfunction']='# 仅给予秘籍，不改变境界或学习记录\n'+'\n'.join('myvillage cultivation manual @s myvillage:'+a['id'] for a in doc['arts'])+'\n'
    return out


def generate(root=ROOT,check=False):
    changed=[]
    for path,text in plan(root).items():
        p=root/path
        if not p.exists() or p.read_text()!=text:
            changed.append(path)
            if not check:p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
    return changed

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');args=p.parse_args()
    changed=generate(check=args.check)
    for path in changed:print(('STALE ' if args.check else 'WROTE ')+str(path))
    raise SystemExit(int(args.check and bool(changed)))
