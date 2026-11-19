"""导出同一份住宅目录、方块形状、施工单元与生活点。"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from tools.housing.materialize import astra_designs, astra_house, native_house, texture

OUT=ROOT/'build/generated/housing'


def canonical(value):return (json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n').encode()


def build_houses():
    from tools.house_choices.model import BUILDERS as npc
    from tools.complex_houses.model import BUILDERS as player
    sys.path.insert(0,str(ROOT/'tools'))
    from buildgen.groups import get_group
    group=get_group('housing_residences')
    if group.layout_strategy!='authored_housing_templates':raise ValueError('住宅生成族配置错误')
    catalog=json.loads((ROOT/'blueprints/housing/catalog.json').read_text())
    designs={s:astra_designs(s)for s in ('astra_player','astra_npc')}
    details={};houses=[]
    for info in catalog['houses']:
        source=info['source'];i=info['index']
        home=astra_house(designs[source][i],details)if source in designs else native_house((player if source=='native_player' else npc)[i])
        houses.append({**info,**home,'group_id':group.group_id})
    # 内门预留区覆盖之前生成的局部形状时，未使用的形状不进入正式资源。
    used=sorted({b[1]for h in houses for b in h['blocks'].values() if isinstance(b,tuple)})
    shapes=list(details);remap={old:i for i,old in enumerate(used)}
    for home in houses:
        home['blocks']={p:(f'yuanxian:housing_details_{remap[b[1]]//128}[variant={remap[b[1]]%128},facing=north]'if isinstance(b,tuple)else b)for p,b in home['blocks'].items()}
    return houses,[shapes[i]for i in used]


def construction(home):
    blocks=home['blocks'];owners={tuple(p)for a in home['assemblies']for p in a['parts']}
    if sum(len(a['parts'])for a in home['assemblies'])!=len(owners):raise ValueError(('构件交叠',home['id']))
    beds=[]
    from tools.json_to_nbt import parse_block_state
    for head in home['beds']:
        head=tuple(head);name,properties=parse_block_state(blocks[head]);properties=dict(properties)
        if not name.endswith('_bed')or properties.get('part')!='head':raise ValueError(('床头被其他构件覆盖',home['id'],head))
        dx,dz={'north':(0,-1),'south':(0,1),'west':(-1,0),'east':(1,0)}[properties['facing']]
        foot=(head[0]-dx,head[1],head[2]-dz)
        if blocks.get(foot)!=blocks[head].replace('part=head','part=foot'):raise ValueError(('床的两半不完整',home['id'],head,foot))
        if owners&{head,foot}:raise ValueError(('床与整件构件重叠',home['id']))
        owners.update((head,foot));beds.append([list(foot),list(head)])
    units=[]
    # 先完成基础与外壳，再摆放床、灯和流体；整件的所有 part 在同一单元写入。
    def late(s):return any(v in s for v in ('_bed[','water[','lantern[','torch','flower','bamboo['))
    for p,b in sorted(blocks.items(),key=lambda v:(late(v[1]),v[0][1],v[0][2],v[0][0])):
        if p not in owners:units.append([list(p)])
    units.extend(a['parts'] for a in home['assemblies'])
    units.extend(beds)
    palette=sorted(set(blocks.values()));indices={s:i for i,s in enumerate(palette)}
    columns={}
    for x,y,z in blocks:columns[x,z]=max(columns.get((x,z),0),y+1)
    return {'id':home['id'],'name':home['name'],'role':home['role'],'description':home['description'],
        'size':[home['size'][0],max(p[1] for p in blocks)+1,home['size'][2]],'ground_y':0,'entry':home['entry'],
        'palette':palette,'blocks':[[*p,indices[s]]for p,s in sorted(blocks.items())],
        'units':units,'columns':[[x,z,-2,top]for (x,z),top in sorted(columns.items())],
        'beds':home['beds'],'assemblies':home['assemblies']}


def outputs():
    houses,shapes=build_houses();result={};resources=OUT/'resources';assets=resources/'assets/yuanxian';data=resources/'data/yuanxian'
    def put(path,value):result[path]=canonical(value)
    details=[];classes=[]
    from tools.reference_courtyard.materials import model_elements
    for group in range((len(shapes)+127)//128):
        subset=shapes[group*128:(group+1)*128];id=f'housing_details_{group}';variants={};collision=[]
        for vi,shape in enumerate(subset):
            mats=sorted({b[2]for b in shape});keys={m:f'm{i}' for i,m in enumerate(mats)}
            boxes=[{'from':list(a),'to':list(b),'material':keys[m]}for a,b,m in shape]
            model={'parent':'minecraft:block/block','textures':{'particle':texture(mats[0]),**{keys[m]:texture(m)for m in mats}},
                   'elements':model_elements(boxes)}
            if any(m in ('玻璃','树叶','白花叶','麦田')for m in mats):model['render_type']='minecraft:cutout'
            put(assets/f'models/block/housing/{group}_{vi}.json',model)
            for turn,face in enumerate(('north','east','south','west')):
                variants[f'facing={face},variant={vi}']={'model':f'yuanxian:block/housing/{group}_{vi}','y':turn*90}
            collision.append([[*a,*b]for a,b,_ in shape])
        put(assets/f'blockstates/{id}.json',{'variants':variants})
        put(assets/f'models/item/{id}.json',{'parent':f'yuanxian:block/housing/{group}_0','overrides':[
            {'predicate':{'custom_model_data':i+1},'model':f'yuanxian:block/housing/{group}_{i}'} for i in range(len(subset))]})
        put(data/f'loot_table/blocks/{id}.json',{'type':'minecraft:block','pools':[
            {'rolls':1,'conditions':[{'condition':'minecraft:block_state_property','block':'yuanxian:'+id,'properties':{'variant':str(i)}},{'condition':'minecraft:survives_explosion'}],
             'entries':[{'type':'minecraft:item','name':'yuanxian:'+id,'functions':[{'function':'minecraft:set_components','components':{
                 'minecraft:block_state':{'variant':str(i)},'minecraft:custom_model_data':i+1}}]}]} for i in range(len(subset))]})
        details.append({'id':id,'shapes':collision});classes.append((group,len(subset)))
    put(data/'housing/details.json',{'format':1,'groups':details})
    catalog=[]
    for h in houses:
        value=construction(h);raw=canonical(value);path=data/f'housing/houses/{h["id"]}.json';result[path]=raw
        catalog.append({k:value[k]for k in ('id','name','role','description','size','entry')}|{'resource':f'yuanxian:housing/houses/{h["id"]}.json','sha256':hashlib.sha256(raw).hexdigest()})
    put(data/'housing/catalog.json',{'format':1,'houses':catalog})
    from tools.housing.preview import images
    for key,png in images(houses,shapes).items():
        result[assets/f'textures/gui/housing/{key}.png']=png
    lines=['package com.example.myvillage.housing;','// Generated by tools/housing/generate.py.',
        'import com.mojang.serialization.MapCodec;','import net.minecraft.world.level.block.Block;',
        'import net.minecraft.world.level.block.state.BlockBehaviour;','import net.minecraft.world.level.block.state.properties.IntegerProperty;',
        'public final class HousingBlocks {']
    for group,count in classes:
        lines.extend([f'public static final class Detail{group} extends HousingDetailBlock {{',
            f'public static final MapCodec<Detail{group}> CODEC=simpleCodec(Detail{group}::new);',
            f'private static final IntegerProperty VARIANT=IntegerProperty.create("variant",0,{count-1});',
            f'public Detail{group}(BlockBehaviour.Properties p){{super(p);}}',
            f'public int group(){{return {group};}}','public IntegerProperty variants(){return VARIANT;}',
            'protected MapCodec<? extends Block> codec(){return CODEC;}','}'])
    lines+=['public static HousingDetailBlock create(int group,BlockBehaviour.Properties p){return switch(group){']
    for g,_ in classes:lines.append(f'case {g}->new Detail{g}(p);')
    lines+=['default->throw new IllegalArgumentException("Unknown housing detail group "+group);};}','private HousingBlocks(){}','}']
    result[OUT/'java/com/example/myvillage/housing/HousingBlocks.java']=('\n'.join(lines)+'\n').encode()
    from tools.language_assets import load_language,language_text
    lang=ROOT/'src/main/resources/assets/yuanxian/lang/zh_cn.json';original=load_language(lang.read_text());entries=dict(original)
    entries={k:v for k,v in entries.items() if not k.startswith('block.yuanxian.housing_details_')}
    entries.update({f'block.yuanxian.housing_details_{g}':'院宅建筑细部' for g,_ in classes})
    result[lang]=language_text(original,entries).encode()
    report={'houses':[{k:h[k] for k in ('id','name','role','size')}|{'blocks':len(h['blocks']),'assemblies':len(h['assemblies']),'beds':len(h['beds'])}for h in houses],
            'detail_variants':len(shapes),'detail_groups':len(classes),'source':'approved_house_designs','native':'not_verified'}
    put(ROOT/'reports/housing/generation.json',report)
    return result,report


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--check',action='store_true');args=ap.parse_args()
    files,report=outputs();stale=[]
    expected={p for p in files if p.is_relative_to(OUT)}
    for path in sorted(p for p in OUT.rglob('*')if p.is_file()and p not in expected):
        if args.check:stale.append(str(path.relative_to(ROOT)))
        else:path.unlink()
    for path,raw in files.items():
        if args.check:
            if not path.exists()or path.read_bytes()!=raw:stale.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    if stale:raise SystemExit('住宅输出已过期：'+', '.join(stale[:12]))
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
