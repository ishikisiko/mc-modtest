#!/usr/bin/env python3
"""总舵材质、细分构件与碰撞目录的唯一导出入口。"""
from __future__ import annotations
import argparse,gzip,hashlib,io,json,math,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.qianji.components import compile_components,identity,FACINGS
from tools.lord_manors.generate import bank_resources,model as block_model
from tools.language_assets import language_text,load_language
from PIL import Image,ImageDraw,ImageFont

ASSET=Path('src/main/resources/assets/myvillage');DATA=Path('src/main/resources/data/myvillage')

def encoded(v):return (json.dumps(v,ensure_ascii=False,separators=(',',':'))+'\n').encode()
def retarget(v):
    if isinstance(v,str):return v.replace('myvillage:block/lord_manors/qianji_','myvillage:block/qianji/qianji_')
    if isinstance(v,list):return [retarget(x) for x in v]
    if isinstance(v,dict):return {k:retarget(x) for k,x in v.items()}
    return v
class Writer:
    def __init__(self,check):self.check=check;self.expected=set();self.bad=[]
    def put(self,path,b):
        p=ROOT/path;self.expected.add(p)
        if self.check:
            if not p.is_file() or p.read_bytes()!=b:self.bad.append(str(path))
        else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
    def json(self,p,d):self.put(p,encoded(retarget(d)))
    def finish(self):
        owned=[]
        for base in [ASSET/'textures/block/qianji',DATA/'qianji/components']:
            if (ROOT/base).exists():owned += [p for p in (ROOT/base).rglob('*') if p.is_file()]
        for base in [ASSET/'models/block',ASSET/'models/item',ASSET/'blockstates',DATA/'loot_table/blocks']:
            for p in (ROOT/base).glob('qianji_*'):
                owned += [f for f in p.rglob('*') if f.is_file()] if p.is_dir() else [p]
        for p in set(owned)-self.expected:
            if self.check:self.bad.append('未使用的千机阁资源 '+str(p.relative_to(ROOT)))
            else:p.unlink()
        if self.bad:raise ValueError('千机阁生成资源不一致：'+str(self.bad[:20]))

def png(im):
    b=io.BytesIO();im.save(b,format='PNG');return b.getvalue()
def texture(role,palette,cfg,special):
    if role in special:
        info=special[role];im=Image.new('RGBA',(512,128) if info['kind']=='plaque' else (512,320),'#d5c49a');g=ImageDraw.Draw(im)
        if info['kind']=='plaque':
            g.rectangle((0,0,511,127),fill='#293e43');g.rectangle((7,7,504,120),outline='#b9a676',width=4)
            font=ImageFont.truetype(str(ROOT/cfg['font']),min(87,round(440/len(info['text']))));g.text((256,64),info['text'],font=font,fill='#e5c987',anchor='mm')
        else:
            g.rectangle((9,9,502,311),outline='#667b64',width=2)
            f=ImageFont.truetype(str(ROOT/cfg['font']),25);g.text((25,23),'八州舆图' if info['map']=='states' else '秘境勘图',font=f,fill='#425d4c')
            if info['map']=='states':
                path=[(42,246),(95,186),(160,213),(220,147),(275,167),(330,91),(391,137),(464,74)];g.line(path,fill='#6a9697',width=5)
                for i,(x,y) in enumerate(path):g.rectangle((x-18,y-12,x+18,y+14),fill=['#a5ad8f','#92ad9a','#b1ab8c','#9aa99e'][i%4]);g.text((x,y),['西漠','东海','南疆','北原','东荒','灵岳','魔域','中州'][i],font=ImageFont.truetype(str(ROOT/cfg['font']),16),fill='#385a4b',anchor='mm')
            else:
                for x,y,w,h in [(40,86,90,60),(215,76,85,65),(376,95,93,70),(75,220,86,49),(249,207,89,62)]:g.rectangle((x,y,x+w,y+h),fill='#a8b295',outline='#526e60',width=3)
                g.line([(85,116),(257,108),(423,128),(423,247),(295,247),(113,245)],fill='#8a744d',width=3)
        return png(im)
    color,kind=palette[role];rgb=tuple(bytes.fromhex(color[1:]));n=cfg['texture_resolution'];im=Image.new('RGBA',(n,n));pix=im.load()
    for y in range(n):
        for x in range(n):
            f=1+(((x*17+y*31+x*y*7)%19)-9)*.007
            if kind in ('stone','brick','paving') and (y%8==0 or (x+(8 if y//8%2 else 0))%16==0):f*=.78
            elif kind=='tile':f*=.72 if x%8==0 else 1.15 if x%8 in (1,2) else 1;f*=.75 if y%16==0 else 1
            elif kind=='wood':f+=math.sin(x*.62+math.sin(y*.12)*.9)*.19
            elif kind=='planks' and x%8==0:f*=.75
            elif kind=='book' and y%10==0:f*=.7
            elif kind=='metal' and (x==0 or y==0):f*=.83
            alpha=155 if kind=='glass' else 255
            pix[x,y]=tuple(max(0,min(255,round(v*f))) for v in rgb)+(alpha,)
    return png(im)

def definitions(d):
    cfg=d['config'];palette=cfg['palette'];result={}
    names={'stone':'青石','stone_dark':'深青石','carved':'浅石雕饰','paving':'院落石坪','paving_dark':'深色石纹','brick':'灰砖','plaster':'米白灰墙','wood':'木梁','wood_dark':'深木构件','wood_red':'朱褐梁柱','floor':'木楼板','tile':'青灰屋瓦','tile_alt':'浅青屋瓦','tile_dark':'深青脊瓦','tile_edge':'瓦檐收口','bronze':'铜饰','bronze_dark':'青铜饰','glass':'青窗','paper':'纸面','paper_window':'透光窗纸','lamp':'廊灯','grass':'庭院草坪','moss':'地被','soil':'台基填土','rock':'庭石','red':'朱印','ink':'墨色饰面','cloth':'织物','book_blue':'青书函','book_red':'朱书函','book_cream':'素书函'}
    for role in d['roles']:
        kind=palette[role][1] if role in palette else 'special'
        result[role]=dict(id='qianji_'+role,role=role,name='千机阁·'+names.get(role,'题匾' if role.startswith('plaque_') else '舆图'),
            collides=role not in cfg['non_collision_roles'] and role not in d['special'],opaque=role not in cfg['glass_roles'],sound='wood' if role in cfg['wood_roles'] else 'glass' if role=='glass' else 'grass' if kind=='grass' else 'stone',light_level=cfg['light_level'] if role=='lamp' else 0)
    return result

def export(check=False):
    d=compile_components();cfg=d['config'];defs=definitions(d);writer=Writer(check);names=load_language((ROOT/ASSET/'lang/zh_cn.json').read_text());before=dict(names)
    for key in list(names):
        if key.startswith('block.myvillage.qianji_') or key=='itemGroup.myvillage.qianji':del names[key]
    materials=[]
    for role,m in defs.items():
        writer.put(ASSET/f'textures/block/qianji/{m["id"]}.png',texture(role,cfg['palette'],cfg,d['special']))
        if role in d['special']:continue
        materials.append(m);ref='myvillage:block/qianji/'+m['id'];render='minecraft:solid' if m['opaque'] else 'minecraft:translucent'
        writer.json(ASSET/f'models/block/{m["id"]}.json',{'parent':'minecraft:block/cube_all','render_type':render,'textures':{'all':ref}})
        writer.json(ASSET/f'blockstates/{m["id"]}.json',{'variants':{'':{'model':'myvillage:block/'+m['id']}}})
        writer.json(ASSET/f'models/item/{m["id"]}.json',{'parent':'myvillage:block/'+m['id']})
        writer.json(DATA/f'loot_table/blocks/{m["id"]}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'myvillage:'+m['id']}]}]})
        names['block.myvillage.'+m['id']]=m['name']
    banks=[]
    for off in range(0,len(d['profiles']),cfg['bank_size']):
        rows=d['profiles'][off:off+32];rows+=rows[:1]*(32-len(rows));bank={'id':f'qianji_carved_{off//32}','name':'千机阁建筑构件','profiles':[list(map(list,p)) for p in rows]}
        bank_resources(writer,bank,defs,'qianji',names)
        bank['profiles']=[dict(boxes=list(p),collision=[b[:6] for b in p if defs[b[6]]['collides']],occlusion=[b[:6] for b in p if defs[b[6]]['opaque']],
            translucent={defs[role]['id']:[b[:6] for b in p if b[6]==role] for role in sorted({b[6] for b in p if not defs[b[6]]['opaque']})},light_level=max(defs[b[6]]['light_level'] for b in p)) for p in rows]
        banks.append(bank)
    access=json.loads((ROOT/'blueprints/qianji/zhongzhou_headquarters.json').read_text())
    targets=[dict(id=r['id']+'_'+str(i+1),position=l['target']) for r in access['rooms'] for i,l in enumerate(r['levels'])]+access.get('extra_targets',[])
    targets += [dict(id='jianghu_board',position=access['reader']),dict(id='entrance',position=access['paths'][0])]
    layout=dict(size=[168,max(p[1] for p in d['blocks'])+1,184],entry=access['paths'][0],anchor=access['atmosphere_anchor'],targets=targets)
    writer.json(DATA/'qianji/components/catalog.json',dict(schema=1,materials=materials,banks=banks,layout=layout,strength=cfg['strength'],explosion_resistance=cfg['explosion_resistance']))
    writer.json(ASSET/'models/block/qianji_atmosphere_anchor.json',{'parent':'minecraft:block/cube_all','textures':{'all':'myvillage:block/qianji/qianji_paving'}})
    writer.json(ASSET/'blockstates/qianji_atmosphere_anchor.json',{'variants':{f'facing={f}':{'model':'myvillage:block/qianji_atmosphere_anchor','y':i*90} for i,f in enumerate(FACINGS)}})
    writer.json(DATA/'loot_table/blocks/qianji_atmosphere_anchor.json',{'type':'minecraft:block','pools':[]})
    names.update({'block.myvillage.qianji_atmosphere_anchor':'千机阁阵枢','itemGroup.myvillage.qianji':'千机阁建筑'})
    writer.put(ASSET/'lang/zh_cn.json',language_text(before,names).encode())
    writer.finish()
    manifest=dict(identity=d['identity'],size=[168,max(p[1] for p in d['blocks'])+1,184],blocks=[[*p,s] for p,s in sorted(d['blocks'].items())],parts=d['scene']['parts'],levels=d['scene']['levels'],lamps=d['scene']['lamps'],tree_sites=d['scene']['tree_sites'])
    cache=ROOT/'build/generated/qianji/model.json.gz';cache.parent.mkdir(parents=True,exist_ok=True);cache.write_bytes(gzip.compress(encoded(manifest),mtime=0))
    report=dict(files=len(writer.expected),blocks=len(d['blocks']),profiles=len(d['profiles']),banks=len(banks),max_boxes=max(map(len,d['profiles'])),trees=len(d['scene']['tree_sites']),check=check)
    print(json.dumps(report,ensure_ascii=False),flush=True);return report

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');export(p.parse_args().check)
