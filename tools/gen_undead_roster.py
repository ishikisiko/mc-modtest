"""行尸与骸弓的单一配置导出；所有资源只能从 catalogue 与 beastgen 生成。"""
from __future__ import annotations
import argparse
import copy
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CATALOGUE=ROOT/'tools/undead_roster/catalogue.json'
def catalogue(): return json.loads(CATALOGUE.read_text())
def variants(): return catalogue()['variants']
def names(): return tuple(v['id'] for v in variants())
def entry(name): return next(v for v in variants() if v['id']==name)
def beast_data(v,c):
    moves=[]
    for a in v['moves']:
        m={k:copy.deepcopy(a[k]) for k in ('animation','total_ticks','windup_ticks','turn_lock_tick','damage_multiplier','maximum_targets','use_range','cooldown_ticks','weight','lunge','hit','knockback')}
        m.update(id='myvillage:'+v['id']+'_'+a['animation'],active_ticks=[min(a['strike_ticks']),max(a['strike_ticks'])],immune_ticks=[0,0])
        moves.append(m)
    return dict(schema=1,entity='myvillage:'+v['id'],attributes=v['attributes'],chase=c['chase'],stagger={'animation':'stagger'},moves=moves)
def validate(c):
    ids=[v['id'] for v in c['variants']]
    assert len(ids)==18 and len(set(ids))==18,'必须有两种通用款与八洲各两种'
    regions={'common','ximo','donghai','nanjiang','beiyuan','donghuang','lingyue','moyu','zhongzhou'}
    assert {(v['region'],v['family']) for v in c['variants']}=={(r,f) for r in regions for f in ('corpse','archer')}
    for v in c['variants']:
        assert len(v['moves'])==2
        for m in v['moves']:
            ticks=m['strike_ticks'];assert ticks==sorted(set(ticks)) and m['windup_ticks']<=ticks[0]<=ticks[-1]<m['total_ticks']
            assert 0<=m['turn_lock_tick']<ticks[0] and ticks[-1]+10<m['total_ticks'],'必须有锁向与收招窗口'
        assert v['spawn']['weight']>0 and v['spawn']['biomes']
    return c

def outputs(root=ROOT):
    c=validate(catalogue());res=root/'src/main/resources';out={}
    def add(p,d):out[res/p]=(json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode()
    add('data/myvillage/wilderness/undead.json',c)
    lang=json.loads((res/'assets/myvillage/lang/zh_cn.json').read_text())
    index=json.loads((res/'data/myvillage/beast/index.json').read_text())
    owned={'myvillage:'+v['id'] for v in c['variants']}
    index['beasts']=[i for i in index['beasts'] if i not in owned]+['myvillage:'+v['id'] for v in c['variants']]
    add('data/myvillage/beast/index.json',index)
    guide=['---','title: 行尸与骸弓','---','','# 行尸与骸弓','','行尸有拖步二连抓与扑攫，骸弓有停步蓄射与近身弓扫。观察起手，避开锁向攻击，在收招时反击。','','通用款与八洲特色款按夜间怪物规则低权重出现。特色款只在所属洲域出现；自然出生境界只决定一次，搬迁与重载不重抽。原版僵尸与骷髅继续保留。','','掉落沿用腐肉和骨头，不额外增加材料或配方。','']
    for v in c['variants']:
        n=v['id'];add(f'data/myvillage/beast/{n}.json',beast_data(v,c))
        lang['entity.myvillage.'+n]=v['name'];lang['item.myvillage.'+n+'_spawn_egg']=v['name']+'刷怪蛋'
        add(f'assets/myvillage/models/item/{n}_spawn_egg.json',{'parent':'minecraft:item/template_spawn_egg'})
        add(f'data/myvillage/tags/worldgen/biome/has_{n}.json',{'replace':False,'values':v['spawn']['biomes']})
        add(f'data/myvillage/neoforge/biome_modifier/add_{n}_spawns.json',{'type':'neoforge:add_spawns','biomes':'#myvillage:has_'+n,'spawners':[{'type':'myvillage:'+n,'weight':v['spawn']['weight'],'minCount':v['spawn']['min_count'],'maxCount':v['spawn']['max_count']}]})
        add(f'data/myvillage/loot_table/entities/{n}.json',{'type':'minecraft:entity','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':v['loot'],'functions':[{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':c['runtime']['loot_min'],'max':c['runtime']['loot_max']}},{'function':'minecraft:enchanted_count_increase','enchantment':'minecraft:looting','count':{'type':'minecraft:uniform','min':0,'max':1}}]}]}]})
        guide.extend(['## '+v['name']+'（'+v['region_name']+'）','',v['habitat']+'。',''])
    add('assets/myvillage/lang/zh_cn.json',lang)
    out[root/'guidebook/getting_started/undead_roster.md']=('\n'.join(guide)+'\n').encode()
    return out

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');a=p.parse_args();bad=[]
    for path,data in outputs().items():
        if path.exists() and path.read_bytes()==data:continue
        if a.check:bad.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    if bad:print('\n'.join('STALE '+n for n in bad));return 1
    print('undead roster: '+('up to date' if a.check else 'generated'));return 0
if __name__=='__main__':raise SystemExit(main())
