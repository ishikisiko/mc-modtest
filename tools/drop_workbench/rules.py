"""写回之前按现有DropRules格式完整校验，错误不得替换原文件。"""
from __future__ import annotations
import json,math,re
MAX_BYTES=1024*1024
SCENES={'normal','habitat','quest_realm','sect_trial','aptitude_trial','battle_ruin'}
class Invalid(ValueError):pass

def decode(raw):
    if len(raw)>MAX_BYTES:raise Invalid('配置超过1 MiB')
    def pairs(rows):
        obj={}
        for k,v in rows:
            if k in obj:raise Invalid('JSON字段重复：'+k)
            obj[k]=v
        return obj
    try:return json.loads(raw.decode('utf-8'),object_pairs_hook=pairs,parse_constant=lambda v:(_ for _ in ()).throw(Invalid('不允许非有限数字')))
    except (UnicodeError,json.JSONDecodeError,RecursionError) as e:raise Invalid('JSON格式错误：'+str(e)) from e

def validate(data,catalogue):
    items={x['id'] for x in catalogue['items']};item_map={x['id']:x for x in catalogue['items']};entities={x['id'] for x in catalogue['entities']};regions={x['id'] for x in catalogue['regions']};realms={x['id']:x for x in catalogue['realms']}
    def obj(v,path,allowed,required=None):
        if not isinstance(v,dict):raise Invalid(path+'应为对象')
        if set(v)-set(allowed):raise Invalid(path+'有未知字段：'+','.join(set(v)-set(allowed)))
        if set(required or [])-set(v):raise Invalid(path+'缺少字段：'+','.join(set(required)-set(v)))
    def array(v,path,limit):
        if not isinstance(v,list) or len(v)>limit:raise Invalid(path+'应为不超过'+str(limit)+'项的数组')
    def number(v,path,low,high,integer=False):
        if isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) or not low<=v<=high or integer and int(v)!=v:raise Invalid(path+f'应为{low}至{high}的'+('整数' if integer else '数值'))
    def text(v,path):
        if not isinstance(v,str):raise Invalid(path+'应为字符串')
    def drops(v,path):
        array(v,path,64)
        for n,entry in enumerate(v):
            p=path+f'[{n}]';obj(entry,p,['item','min','max','chance','looting','factory','quality','rarity','chance_looting','rarity_weights'],['item','min','max','chance'])
            if not isinstance(entry['item'],str) or entry['item'] not in items or entry['item']=='minecraft:air':raise Invalid(p+'物品不存在，请从物品库选择')
            number(entry['min'],p+'最少数量',0,64,True);number(entry['max'],p+'最多数量',entry['min'],64,True);number(entry['chance'],p+'概率',0,1)
            if 'quality' in entry:
                number(entry['quality'],p+'品质',0,4,True)
                if entry['quality'] not in item_map[entry['item']].get('qualities',[]):raise Invalid(p+'该物品不支持此品质')
            if 'rarity' in entry:
                if not isinstance(entry['rarity'],str) or entry['rarity'] not in item_map[entry['item']].get('rarities',[]):raise Invalid(p+'该物品不支持此稀有度')
            if 'quality' in entry and 'rarity' in entry:raise Invalid(p+'品质与原版稀有度不能同时指定')
            if 'looting' in entry:
                l=entry['looting'];obj(l,p+'.looting',['min_per_level','max_per_level','limit'],['min_per_level','max_per_level','limit']);number(l['min_per_level'],p+'每级抢夺最小增量',0,64);number(l['max_per_level'],p+'每级抢夺最大增量',l['min_per_level'],64);number(l['limit'],p+'抢夺上限',1,64,True)
            factory=entry.get('factory','plain')
            if factory not in ['plain','beast_fire','beast_material'] or factory=='beast_fire' and entry['item']!='yuanxian:beast_fire_seed' or factory=='beast_material' and not item_map[entry['item']].get('beast_material'):raise Invalid(p+'特殊物品工厂不匹配')
            if 'chance_looting' in entry:
                l=entry['chance_looting'];obj(l,p+'.chance_looting',['per_level','max_level'],['per_level','max_level']);number(l['per_level'],p+'每级抢夺概率增量',0,1);number(l['max_level'],p+'抢夺概率等级上限',1,255,True)
            if factory=='beast_material':
                if 'quality' in entry or 'rarity' in entry:raise Invalid(p+'妖兽材料使用自身境界与稀有度')
                w=entry.get('rarity_weights');array(w,p+'稀有度权重',4)
                if len(w)!=4:raise Invalid(p+'需要四种材料稀有度权重')
                for weight in w:number(weight,p+'稀有度权重',0,1_000_000,True)
                if sum(w)==0:raise Invalid(p+'总权重必须大于零')
            elif 'rarity_weights' in entry:raise Invalid(p+'稀有度权重仅适用于妖兽材料')
    def policy(v,path,override=False):
        obj(v,path,['id','when','mode','drops'] if override else ['mode','drops'],['mode','drops'])
        if v['mode'] not in ['inherit','append','replace','disabled']:raise Invalid(path+'掉落模式无效')
        drops(v['drops'],path+'.drops')
        if v['mode'] in ['inherit','disabled'] and v['drops']:raise Invalid(path+'继承或禁用模式的掉落列表必须为空')
    def when(v,path):
        obj(v,path,['entities','scenes','regions','min_realm','max_realm','player_kill','fire_captured'])
        for key,allowed,limit in [('entities',entities,512),('scenes',SCENES,6),('regions',regions,64)]:
            if key in v:
                a=v[key];array(a,path+'.'+key,limit)
                if not a or any(not isinstance(i,str) for i in a) or len(set(a))!=len(a) or not set(a)<=allowed:raise Invalid(path+'.'+key+'必须包含有效且不重复的选项')
        ranks={}
        for key in ['min_realm','max_realm']:
            if key in v:
                r=v[key];obj(r,path+'.'+key,['realm','stage'],['realm','stage'])
                if not isinstance(r['realm'],str) or r['realm'] not in realms:raise Invalid(path+'境界不存在')
                number(r['stage'],path+'境界阶段',0,min(8,realms[r['realm']]['stages']-1),True);ranks[key]=realms[r['realm']]['order']*16+r['stage']
        if len(ranks)==2 and ranks['min_realm']>ranks['max_realm']:raise Invalid(path+'最低境界高于最高境界')
        for key in ['player_kill','fire_captured']:
            if key in v and not isinstance(v[key],bool):raise Invalid(path+'.'+key+'应为布尔值')
    obj(data,'统一表',['schema','defaults','entities','overrides','additions'],['schema','defaults','entities','overrides','additions'])
    number(data['schema'],'schema',1,1,True);policy(data['defaults'],'默认规则')
    obj(data['entities'],'生物规则',entities)
    if len(data['entities'])>512:raise Invalid('生物规则最多512项')
    for id,entry in data['entities'].items():policy(entry,id)
    ids=set()
    for key,limit in [('overrides',256),('additions',64)]:
        array(data[key],key,limit)
        for i,entry in enumerate(data[key]):
            p=key+f'[{i}]';obj(entry,p,['id','when','mode','drops'] if key=='overrides' else ['id','when','drops'],['id','when','drops']+(['mode'] if key=='overrides' else []));name=entry['id'];text(name,p+'名称')
            if not re.fullmatch('[a-z0-9_]{1,64}',name) or name in ids:raise Invalid(p+'规则ID只能包含小写英文、数字和下划线，且不可重复')
            ids.add(name);when(entry['when'],p+'.when')
            if key=='overrides':policy(entry,p,True)
            else:drops(entry['drops'],p+'.drops')
    return data
