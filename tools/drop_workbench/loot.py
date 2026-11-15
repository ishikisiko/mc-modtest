"""读取原生战利品表，保留抽取、数量和条件的语义，供工作台查看。"""
from __future__ import annotations
import hashlib,json,math

def short(value):return value.split(':')[-1]
def number(v):return str(int(v)) if float(v).is_integer() else f'{v:g}'
def bounds(v):
    if isinstance(v,(int,float)):return float(v),float(v)
    if isinstance(v,dict) and short(v.get('type',''))=='uniform':return float(v['min']),float(v['max'])
    return None
def amount(v):
    b=bounds(v)
    if b:return number(b[0]) if b[0]==b[1] else number(b[0])+'–'+number(b[1])
    return json.dumps(v,ensure_ascii=False,separators=(',',':'))
def percent(p):return f'{p*100:.4f}'.rstrip('0').rstrip('.')+'%'

class LootReader:
    def __init__(self,assets,langs):
        self.assets=assets;self.langs=langs;self.unsupported=set();self.smelting={}
        for n in assets.client.namelist():
            if not n.startswith('data/minecraft/recipe/') or not n.endswith('.json'):continue
            d=json.loads(assets.client.read(n))
            if d.get('type')!='minecraft:smelting':continue
            ing=d.get('ingredient',{});result=d.get('result',{})
            if isinstance(ing,dict) and 'item' in ing:self.smelting[ing['item']]=result.get('id') if isinstance(result,dict) else result
    def label(self,id,kind='item'):
        ns,n=id.split(':',1);lang=self.langs.get(ns,{})
        label=lang.get(f'{kind}.{ns}.{n}') or lang.get(f'block.{ns}.{n}') or id
        if n.startswith('music_disc_'):
            desc=lang.get(f'item.{ns}.{n}.desc')
            if desc:label+=' · '+desc
        return label
    def predicate(self,p):
        parts=[]
        for key,v in p.items():
            if key=='flags':
                names={'is_on_fire':'着火','is_baby':'幼体','is_on_ground':'在地面','is_sneaking':'潜行','is_sprinting':'疾跑','is_swimming':'游泳'}
                parts.extend(names.get(k,k)+('' if x else '：否') for k,x in v.items())
            elif key=='type':
                if v.startswith('#'):
                    choices=self.tag(v[1:],kind='entity_type');parts.append('生物类型：'+'、'.join(self.label(x,'entity') for x in choices) if choices else '生物标签：'+v)
                else:parts.append('生物类型：'+self.label(v,'entity'))
            elif key=='components':
                for k,x in v.items():parts.append({'minecraft:sheep/color':'羊毛颜色'}.get(k,k)+'：'+str(x))
            elif key=='equipment':parts.append('武器带有熔炼掉落附魔' if 'smelts_loot' in json.dumps(v) else '装备条件：'+json.dumps(v,ensure_ascii=False))
            elif key=='type_specific':
                t=short(v.get('type',''))
                if t=='slime':parts.append('史莱姆尺寸：'+self.range_text(v.get('size',{})))
                elif t=='frog':parts.append('青蛙品种：'+{'minecraft:warm':'暖带（白色）','minecraft:cold':'寒带（绿色）','minecraft:temperate':'温带（橙色）'}.get(v.get('variant'),str(v.get('variant'))))
                elif t=='raider':
                    parts.extend({'is_captain':'袭击队长','has_raid':'正在参与袭击'}.get(k,k)+'：'+('是' if x is True else '否' if x is False else str(x)) for k,x in v.items() if k!='type')
                else:parts.append('实体条件：'+json.dumps(v,ensure_ascii=False))
            elif key=='sub_predicate':parts.append('实体子条件：'+json.dumps(v,ensure_ascii=False))
            else:parts.append(key+'：'+json.dumps(v,ensure_ascii=False))
        return '，'.join(parts) or '任意'
    @staticmethod
    def range_text(v):
        if not isinstance(v,dict):return str(v)
        return '、'.join(('至少' if k=='min' else '至多' if k=='max' else k)+str(x) for k,x in v.items())
    def tag(self,id,seen=(),kind='item'):
        if id in seen:return []
        ns,n=id.split(':',1);raw=self.assets.read(f'data/{ns}/tags/{kind}/{n}.json')
        if raw is None:return []
        values=[]
        for v in json.loads(raw).get('values',[]):
            key=v.get('id') if isinstance(v,dict) else v
            values+=self.tag(key[1:],seen+(id,),kind) if key.startswith('#') else [key]
        return list(dict.fromkeys(values))
    def condition(self,c):
        t=short(c['condition'])
        if t=='killed_by_player':return '玩家击杀',1
        if t=='random_chance':return '随机判定 '+percent(c['chance']),c['chance']
        if t=='random_chance_with_enchanted_bonus':
            base=c['unenchanted_chance'];e=c['enchanted_chance'];text='无抢夺 '+percent(base)
            if isinstance(e,dict) and short(e.get('type',''))=='linear':text+='；抢夺 I/II/III：'+'/'.join(percent(e['base']+i*e['per_level_above_first']) for i in range(3))
            else:text+='；附魔概率：'+json.dumps(e,ensure_ascii=False)
            return text,base
        if t=='any_of':return '（'+' 或 '.join(self.condition(x)[0] for x in c['terms'])+'）',None
        if t=='all_of':return ' 且 '.join(self.condition(x)[0] for x in c['terms']),None
        if t=='inverted':return '不满足（'+self.condition(c['term'])[0]+'）',None
        if t=='entity_properties':return {'this':'生物自身','attacker':'击杀者','direct_attacker':'直接攻击者','attacking_player':'击杀玩家'}.get(c['entity'],c['entity'])+'：'+self.predicate(c.get('predicate',{})),None
        if t=='damage_source_properties':
            p=c.get('predicate',{});return '伤害来源：'+'；'.join(('来源生物：'+self.predicate(v) if k=='source_entity' else '直接来源：'+self.predicate(v) if k=='direct_entity' else k+'：'+json.dumps(v,ensure_ascii=False)) for k,v in p.items()),None
        self.unsupported.add('condition:'+t);return '条件 '+t+'：'+json.dumps(c,ensure_ascii=False),None
    def conditions(self,values):
        labels=[];p=1
        for c in values:
            text,chance=self.condition(c);labels.append(text)
            if chance is not None:p*=chance
        return labels,p
    def table(self,id,stack=()):
        ns,n=id.split(':',1);raw=self.assets.read(f'data/{ns}/loot_table/{n}.json')
        if raw is None:return {'table':id,'status':'missing','rows':[],'raw':None,'note':'资源中没有此战利品表，实体可能通过代码处理死亡掉落。'}
        doc=json.loads(raw)
        if id in stack:return {'table':id,'status':'cycle','rows':[],'raw':doc,'note':'检测到循环引用，请查看原始表。'}
        rows=[]
        for i,pool in enumerate(doc.get('pools',[])):
            entries=pool.get('entries',[]);weights=sum(e.get('weight',1) for e in entries);conditional=any(e.get('conditions') or short(e.get('type','')) in {'alternatives','sequence','group'} for e in entries)
            for e in entries:
                self.entry(e,rows,stack+(id,),pool.get('conditions',[]),doc.get('functions',[])+pool.get('functions',[]),pool.get('rolls',1),None if conditional else e.get('weight',1)/weights if weights else 0,i+1)
        return {'table':id,'status':'ok','rows':rows,'raw':doc,'sha256':hashlib.sha256(raw).hexdigest(),'note':'数量与概率按无抢夺计算；条件成立后才参与对应掉落池。'}
    def entry(self,e,rows,stack,conditions,functions,rolls,choice,pool):
        t=short(e['type']);conditions=conditions+e.get('conditions',[]);functions=functions+e.get('functions',[])
        if t=='empty':return
        if t in {'alternatives','sequence','group'}:
            for child in e.get('children',[]):self.entry(child,rows,stack,conditions,functions,rolls,None,pool)
            return
        labels,gates=self.conditions(conditions)
        if t=='loot_table':
            target=e.get('value',e.get('name'))
            if isinstance(target,str):
                nested=self.table(target,stack)
                for row in nested['rows']:
                    r=dict(row);r['conditions']=labels+r['conditions'];r['reference']=target
                    r['chance']=r['chance']*gates*choice if r['chance'] is not None and choice is not None and bounds(rolls)==(1,1) else None
                    rows.append(r)
                if nested['status']!='ok':rows.append({'item':None,'name':'子表：'+str(target),'quantity':'见原始表','chance':None,'conditions':labels,'notes':[nested['note']],'rolls':amount(rolls),'pool':pool,'selection':'子表引用'})
            else:self.unsupported.add('inline_loot_table')
            return
        if t=='tag' and e.get('expand'):
            values=self.tag(e['name'])
            if values:
                for value in values:
                    begin=len(rows);self.entry({'type':'minecraft:item','name':value},rows,stack,conditions,functions,rolls,(choice/len(values)) if choice is not None else None,pool)
                    for row in rows[begin:]:row['notes'].append('从标签 #'+e['name']+' 的 '+str(len(values))+' 个物品中等权抽取')
                return
        if t not in {'item','tag'}:self.unsupported.add('entry:'+t);rows.append({'item':None,'name':'条目 '+t,'quantity':'见原始表','chance':None,'conditions':labels,'notes':[json.dumps(e,ensure_ascii=False)],'rolls':amount(rolls),'pool':pool});return
        id=e['name'];count=1;notes=[]
        for f in functions:
            ft=short(f['function']);fl,_=self.conditions(f.get('conditions',[]));prefix=('；'.join(fl)+'时：') if fl else ''
            if ft=='set_count':
                if not fl and not f.get('add',False):count=f['count']
                else:notes.append(prefix+('增加数量 ' if f.get('add') else '数量 ')+amount(f['count']))
            elif ft=='enchanted_count_increase':notes.append(prefix+'抢夺每级增加 '+amount(f['count'])+' 个'+('，上限 '+str(f['limit'])+' 个' if 'limit' in f else ''))
            elif ft=='furnace_smelt':notes.append(prefix+'改为 '+self.label(self.smelting[id]) if id in self.smelting else prefix+'熔炼后掉落')
            elif ft=='set_potion':notes.append(prefix+'药水：'+self.langs['minecraft'].get('item.minecraft.potion.effect.'+short(f['id']),f['id']))
            elif ft=='set_ominous_bottle_amplifier':
                b=bounds(f['amplifier']);notes.append(prefix+'不祥效果等级：'+(number(b[0]+1)+'–'+number(b[1]+1) if b else amount(f['amplifier'])))
            else:self.unsupported.add('function:'+ft);notes.append(prefix+ft+'：'+json.dumps(f,ensure_ascii=False))
        b=bounds(count);quantity=amount(count);positive=1
        if b:
            lo,hi=math.floor(b[0]),math.floor(b[1]);positive=max(0,hi-max(1,lo)+1)/(hi-lo+1);quantity=amount(max(0,lo)) if lo==hi else str(max(0,lo))+'–'+str(max(0,hi))
        if t=='tag':notes.append('从标签内物品'+('逐项展开后抽取' if e.get('expand') else '整组生成'));choice=None
        chance=gates*choice*positive if choice is not None else None
        rows.append({'item':id if t=='item' else None,'name':self.label(id) if t=='item' else '标签 #'+id,'quantity':quantity,'chance':chance,'pool_chance':gates,'selection':'按满足条件的条目抽取' if choice is None else ('等权或按权重抽取 '+percent(choice) if choice<1 else ''),'conditions':labels,'notes':notes,'rolls':amount(rolls),'pool':pool})

def native_loot(reader,langs,entities):
    parser=LootReader(reader,langs)
    for v in entities.values():
        ns,n=v['id'].split(':',1);v['native_loot']=parser.table(ns+':entities/'+n)
        if v['id']=='minecraft:sheep':
            # Sheep#getDefaultLootTable：未剪毛按颜色选子表，剪毛后回到基础羊肉表。
            loot=parser.table('minecraft:entities/sheep/white');loot['variant_note']='这里显示未剪毛的白色成年绵羊。其他颜色掉落同色羊毛；剪毛后只掉羊肉。'
            for row in loot['rows']:
                if row['item']=='minecraft:white_wool':row['conditions'].append('未剪毛；羊毛颜色为白色')
            v['native_loot']=loot
    return sorted(parser.unsupported)
