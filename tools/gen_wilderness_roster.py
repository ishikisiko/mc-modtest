#!/usr/bin/env python3
"""Export the wilderness roster's data, item art, recipes and Chinese guide; --check is read-only."""
from __future__ import annotations
import argparse
import copy
import json
from pathlib import Path
from tools.gen_qingfeng_sword_model import encode_png
from tools.pill_art.render import pill_icon
from tools.pill_art.formulas import rows as formula_rows

ROOT = Path(__file__).resolve().parents[1]
CATALOGUE = ROOT / 'tools/wilderness_roster/catalogue.json'

def catalogue():
    return json.loads(CATALOGUE.read_text())

def scaled_moves(m):
    moves=copy.deepcopy(m['moves']);scale=m['body_scale']
    for move in moves:
        if move['_mode']=='melee':
            for field in ('forward','height'):
                move['hit'][field]=[round(v*scale,6) for v in move['hit'][field]]
            move['hit']['half_width']=round(move['hit']['half_width']*scale,6)
            move['use_range']=[round(v*scale,6) for v in move['use_range']]
    return [{k:v for k,v in move.items() if not k.startswith('_')} for move in moves]

def icon(kind, palette):
    colors=[tuple(bytes.fromhex(c))+(255,) for c in palette]
    rows=[[(0,0,0,0)]*32 for _ in range(32)]
    def rect(x,y,w,h,c):
        for j in range(max(0,y),min(32,y+h)):
            for i in range(max(0,x),min(32,x+w)): rows[j][i]=c
    dark=tuple(max(0,v//2) for v in colors[0][:3])+(255,)
    if kind in ('talisman_ash','reaver_grit'):
        rect(7,12,19,15,dark);rect(8,14,17,12,colors[0]);rect(9,9,15,6,colors[1]);rect(11,6,11,4,colors[0])
        rect(7,12,19,2,colors[2]);rect(13,19,6,4,colors[2])
        for x,y in [(9,28),(24,27),(27,24)]:rect(x,y,2,2,colors[1])
    elif kind=='thorn_bone':
        for k in range(18):rect(6+k,24-k,5,4,colors[1 if k%5<2 else 0])
        rect(4,24,6,5,colors[2]);rect(23,5,5,5,colors[2]);rect(12,13,3,7,colors[1]);rect(19,8,3,6,colors[1])
    elif kind=='redplate_scale':
        for y in range(5,28):
            half=max(2,11-abs(y-16)//2)
            rect(16-half,y,half*2,1,colors[0 if y<18 else 1])
        rect(11,8,10,2,colors[2]);rect(8,14,16,2,colors[2]);rect(12,20,9,2,colors[2])
    elif kind=='frost_plume':
        for k in range(23):
            x=5+k//2;y=28-k
            rect(x,y,3,1,colors[2]);rect(x+3,y,7 if k<18 else 4,1,colors[1 if k%5<3 else 0])
    else:
        rect(14,3,3,8,colors[0]);rect(10,8,13,4,colors[1]);rect(8,12,17,11,colors[0]);rect(10,23,13,4,colors[1]);rect(12,14,9,10,colors[2]);rect(14,13,4,13,(255,222,132,255))
    return encode_png(rows)

def outputs(root=ROOT):
    c=catalogue();out={};res=root/'src/main/resources'
    def add(path,obj):out[res/path]=(json.dumps(obj,ensure_ascii=False,indent=2)+'\n').encode()
    add('data/myvillage/wilderness/roster.json',c)
    # Existing authored legacy rules remain intact; this generator owns only these new IDs.
    ar=json.loads((res/'data/myvillage/alchemy/rules.json').read_text())
    ids={m['pill_id'] for m in c['mobs']};ar['recipes']=[r for r in ar['recipes'] if r['id'] not in ids]
    fr=json.loads((res/'data/myvillage/forging/rules.json').read_text())
    langs={loc:json.loads((res/f'assets/myvillage/lang/{loc}.json').read_text()) for loc in ('zh_cn','en_us')}
    def name(key,value):
        for lang in langs.values():lang[key]=value
    for m in c['mobs']:
        mid,pill,trait=m['material_id'],m['pill_id'],m['trait_id'];id=m['id']
        if m['moves']:
            moves=scaled_moves(m)
            add(f'data/myvillage/beast/{id}.json',{'schema':1,'entity':'myvillage:'+id,'attributes':m['attributes'],'chase':c['chase'],'stagger':{'animation':'stagger'},'moves':moves})
        for item,label,kind in [(mid,m['material'],mid),('pill_'+pill,m['pill'],'pill'),('formula_'+pill,m['pill']+'方','formula')]:
            name('item.myvillage.'+item,label)
            add('assets/myvillage/models/item/'+item+'.json',{'parent':'minecraft:item/generated','textures':{'layer0':'myvillage:item/'+item}})
            out[res/f'assets/myvillage/textures/item/{item}.png']=encode_png(pill_icon(pill)) if kind=='pill' else encode_png(formula_rows(pill,root)) if kind=='formula' else icon(kind,m['palette'])
        name('entity.myvillage.'+id,m['name']);name('item.myvillage.'+id+'_spawn_egg',m['name']+'刷怪蛋')
        name('tooltip.myvillage.alchemy.pill_'+pill,('立即清除中毒，并在 %2$s 秒内持续清毒。' if c['medicines'][pill].get('quality_duration',False) else m['pillEffect']+' 强度：%s；持续 %s 秒。'))
        name('tooltip.myvillage.wilderness.'+mid,m['material']+'：可炼制'+m['pill']+'，或炼灵为「'+m['trait']+'」。')
        name('forging.myvillage.trait.'+trait,m['trait'])
        name('screen.myvillage.forging.trait.'+trait,m['traitEffect'])
        name('tooltip.myvillage.forging.'+trait,m['traitEffect'])
        add(f'assets/myvillage/models/item/{id}_spawn_egg.json',{'parent':'minecraft:item/template_spawn_egg'})
        add(f'data/myvillage/loot_table/entities/{id}.json',{'type':'minecraft:entity','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'myvillage:'+mid,'functions':[{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':m['drop_count'][0],'max':m['drop_count'][1]}},{'function':'minecraft:enchanted_count_increase','enchantment':'minecraft:looting','count':{'type':'minecraft:uniform','min':0,'max':1},'limit':4}]}]}]})
        add(f'data/myvillage/tags/worldgen/biome/has_{id}.json',{'replace':False,'values':m['biomes']})
        add(f'data/myvillage/neoforge/biome_modifier/add_{id}_spawns.json',{'type':'neoforge:add_spawns','biomes':'#myvillage:has_'+id,'spawners':[{'type':'myvillage:'+id,'weight':m['spawn_weight'],'minCount':m['spawn_group'][0],'maxCount':m['spawn_group'][1]}]})
        formula='formula_'+pill
        add('data/myvillage/recipe/'+formula+'.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:paper'},{'item':'myvillage:'+mid},{'item':m['alchemy_herbs'][0]},{'tag':'c:spirit_stones/low_grade'}],'result':{'id':'myvillage:'+formula,'count':1}})
        add('data/myvillage/advancement/recipes/misc/'+formula+'.json',{'parent':'minecraft:recipes/root','criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['myvillage:'+mid]}]}}},'requirements':[['has_material']],'rewards':{'recipes':['myvillage:'+formula]}})
        ar['recipes'].append({'id':pill,'formula':'myvillage:'+formula,'ingredients':['myvillage:'+mid,*m['alchemy_herbs']],'fuel_count':m['fuel_count'],'duration_ticks':m['brew_ticks'],'output':'myvillage:pill_'+pill,'output_count':m['output_count'],'minimum_aptitude':m['minimum_aptitude']})
        fr['traits'][trait]={'costs':{'myvillage:'+mid:m['trait_cost']},'values':c['traits'][trait]['values']}
        for recipe in fr['recipes']:
            if recipe['id'] in ('qingfeng_sword','lingxiao_spear') and trait not in recipe['traits']:recipe['traits'].append(trait)
    add('data/myvillage/alchemy/rules.json',ar);add('data/myvillage/forging/rules.json',fr)
    index=json.loads((res/'data/myvillage/beast/index.json').read_text())
    # schema-1 index is a list wrapped under beasts.
    entries=index['beasts']
    for m in c['mobs']:
        if m['moves'] and 'myvillage:'+m['id'] not in entries:entries.append('myvillage:'+m['id'])
    add('data/myvillage/beast/index.json',index)
    for loc,lang in langs.items():add(f'assets/myvillage/lang/{loc}.json',lang)
    guide=['---','title: 野外历练与战利品','---','','# 野外历练与战利品','','六种普通敌对生物按群系自然出现。出生境界由当地地区确定，搬迁和重载不重抽。','','主材稳定掉落；每种既能炼丹，也能为青锋剑或凌霄枪选择一种专长。新旧专长互斥，已有炼灵器物不会自动重铸。','','丹方以纸、对应主材、该方第一味药材和下品灵石合成，右键学会；再去丹炉或丹炉阵炼制。战备丹药不能叠加，沿用共享服药间隔。','']
    for m in c['mobs']:
        guide += ['## '+m['name'],'',m['habitat'], '',m['fight']+' 应对：'+m['counter'],'','- 主材：'+m['material'],'- 炼丹：'+m['pill']+'。'+m['pillEffect'],'- 炼器：'+m['trait']+'。'+m['traitEffect'],'']
    out[root/'guidebook/getting_started/wilderness_roster.md']=('\n'.join(guide)+'\n').encode()
    return out

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();bad=[]
    for path,data in outputs().items():
        if not path.exists() or path.read_bytes()!=data:
            if args.check:bad.append(str(path.relative_to(ROOT)))
            else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    if bad:print('\n'.join('STALE '+p for p in bad));return 1
    print('wilderness roster: '+('up to date' if args.check else 'generated'));return 0
if __name__=='__main__':raise SystemExit(main())
