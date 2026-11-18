"""将已确认的住宅几何转成实际方块；局部细构件保留 1/16 格形状。"""
from __future__ import annotations

from collections import defaultdict
from functools import lru_cache
import json
import math
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
MATERIALS = {
    '草方块':'grass_block', '泥土':'dirt', '砂砾':'gravel', '泥土径':'dirt_path',
    '去皮云杉木':'stripped_spruce_log', '云杉木板':'spruce_planks', '方解石':'calcite',
    '深板岩瓦':'deepslate_tiles', '石砖':'stone_bricks', '红色陶瓦':'red_terracotta',
    '深色橡木':'dark_oak_planks', '白桦木板':'birch_planks', '平滑砂岩':'smooth_sandstone',
    '氧化的切制铜':'oxidized_cut_copper', '氧化切制铜':'oxidized_cut_copper',
    '凝灰岩':'tuff', '玻璃':'glass', '床褥':'red_wool', '枕头':'white_wool',
    '树叶':'oak_leaves', '白花叶':'azalea_leaves', '耕地':'farmland', '灰色地毯':'gray_wool',
    '木门':'dark_oak_planks', '红砖':'bricks', '熔炉':'furnace', '箱子':'barrel',
    '麦田':'oak_leaves', '深色原木':'stripped_dark_oak_log', '金合欢木':'stripped_acacia_log',
    '书册':'bookshelf', '水':'water',
}


def state(material):
    name = MATERIALS[material]
    props = '[persistent=true,distance=1,waterlogged=false]' if name.endswith('leaves') else '[axis=y]' if name.endswith('_log') else '[facing=up,open=false]'if name=='barrel'else ''
    return 'minecraft:' + name + props


def texture(material):
    name = MATERIALS[material]
    return 'minecraft:block/' + {'grass_block':'grass_block_top','dirt_path':'dirt_path_top',
        'farmland':'farmland','furnace':'furnace_side','barrel':'barrel_side','bookshelf':'bookshelf'}.get(name,name)


@lru_cache(maxsize=None)
def solid(primitives):
    """仅对不同的局部交叠组合求形状；重复屋瓦不重复体素化。"""
    from tools.reference_courtyard.assembly_shapes import merge
    if len(primitives) == 1:
        *b, mat = primitives[0]
        return ((tuple(b[:3]),tuple(b[3:]),mat),)
    cells = {}
    for x0,y0,z0,x1,y1,z1,mat in primitives:
        for x in range(x0,x1):
            for y in range(y0,y1):
                for z in range(z0,z1): cells[x,y,z] = mat
    return tuple((tuple(b['from']),tuple(b['to']),b['material']) for b in merge(cells))


def astra_designs(source):
    module = ROOT / ('tools/complex_houses/astra/geometry.js' if source=='astra_player' else 'tools/house_choices/astra/geometry.js')
    raw = subprocess.check_output(['node','-e','const x=require(process.argv[1]);process.stdout.write(JSON.stringify(x.designs||x));',str(module)])
    return json.loads(raw)


def astra_house(d,details):
    x0,x1,depth = d['bounds']; width=x1-x0
    if 'beds'not in d:
        d={**d,'beds':[{'x':b['x0'],'z':b['z0'],'y':b['y0']-.5}for b in d['boxes']if b['mat']=='床褥']}
    def bed_frame(box):
        return box['mat']in('深色原木','去皮云杉木')and any(
            bed['x']<=box['x0']<box['x1']<=bed['x']+2 and bed['z']<=box['z0']<box['z1']<=bed['z']+2.25
            and bed['y']<=box['y0']<box['y1']<=bed['y']+1 for bed in d['beds'])
    cells=defaultdict(list)
    # 地面下方的两格属于房屋自身台基；宅地其余部分不进入模板。
    blocks={(x,y,z):'minecraft:dirt' for x in range(width) for z in range(depth) for y in (-2,-1)}
    for x in range(width):
        for z in range(depth): blocks[x,-1,z]='minecraft:grass_block'
    for b in d['boxes']:
        if b.get('kind') in ('terrain','plot','compass','door','bed','fixture') or (b.get('kind')=='boundary' and b.get('level')==0) or b['mat'] in ('水','床褥','枕头'):
            continue
        if bed_frame(b):continue
        a=[b['x0']-x0,b['y0'],b['z0']]; c=[b['x1']-x0,b['y1'],b['z1']]
        q=[round(v*16) for v in (*a,*c)]
        if any(q[i+3]<=q[i] for i in range(3)): continue
        for x in range(math.floor(q[0]/16),math.ceil(q[3]/16)):
            for y in range(math.floor(q[1]/16),math.ceil(q[4]/16)):
                for z in range(math.floor(q[2]/16),math.ceil(q[5]/16)):
                    if not (0<=x<width and 0<=z<depth): raise ValueError(('院宅构件超出已确认范围',d['id'],b))
                    local=tuple(max(0,q[i]-p*16) for i,p in enumerate((x,y,z)))+tuple(min(16,q[i+3]-p*16) for i,p in enumerate((x,y,z)))+(b['mat'],)
                    cells[x,y,z].append(local)
    for pos,primitives in cells.items():
        # 后绘制的整格替换前面的细部，避免内部重叠面。
        full=next((i for i in range(len(primitives)-1,-1,-1) if primitives[i][:6]==(0,0,0,16,16,16)),0)
        shape=solid(tuple(primitives[full:]))
        if len(shape)==1 and shape[0][:2]==((0,0,0),(16,16,16)):
            blocks[pos]=state(shape[0][2])
        else:
            key=tuple(shape)
            if key not in details: details[key]=len(details)
            blocks[pos]=('detail',details[key])
    beds=[]
    for bed in d.get('beds',[]):
        bx,bz,by=round(bed['x']-x0),round(bed['z']),math.ceil(bed['y'])
        for dx in (0,1):
            for dz,part in ((0,'foot'),(1,'head')):
                if bed['y']%1:blocks[bx+dx,by-1,bz+dz]='minecraft:spruce_planks'
                blocks[bx+dx,by,bz+dz]=f'minecraft:red_bed[facing=south,part={part},occupied=false]'
            beds.append([bx+dx,by,bz+1])
    for f in d.get('fixtures',[]):
        for dx in range(round(f.get('w',2))):
            blocks[round(f['x']-x0)+dx,round(f['y']),round(f['z'])]='minecraft:barrel[facing=up,open=false]'
    # 水池改用有完整池底与池沿的原版水源；不导出网页的透明色块。
    for b in d['boxes']:
        if b['mat']!='水': continue
        xa,xb=math.ceil(b['x0']-x0),math.floor(b['x1']-x0)
        za,zb=math.ceil(b['z0']),math.floor(b['z1']);wy=math.floor(b['y1'])
        for x in range(xa-1,xb+1):
            for z in range(za-1,zb+1):
                blocks[x,wy-1,z]='minecraft:stone_bricks'
                blocks[x,wy,z]='minecraft:water[level=0]' if xa<=x<xb and za<=z<zb else 'minecraft:stone_bricks'
    assemblies=[];reserved=set()
    shared=json.loads((ROOT/'blueprints/housing/adjustments.json').read_text())['shared_passages']
    for door in d['doors']:
        if any(rule['source_id']==d['id'] and all(door[k]==rule[k] for k in ('axis','fixed','a'))for rule in shared):continue
        if 'side'not in door:
            matches=[b for b in d['boxes']if b.get('kind')=='door'and abs(b['y0']-door['y'])<.001 and
                (door['axis']=='x'and abs(b['x1']-door['a'])<.001 or door['axis']=='z'and abs(b['z1']-door['a'])<.001)]
            if matches:
                leaf=matches[0];door={**door,'side':-1 if leaf['z0'if door['axis']=='x'else'x0']<door['fixed']else 1}
        w=round(door['b']-door['a']);h=round(door['height'])
        if (w,h) not in ((2,3),(3,3),(3,4)):raise ValueError(('未定义的门洞',d['id'],door))
        vi={(3,4):0,(2,3):1,(3,3):2}[w,h]
        a=round(door['a']-(x0 if door['axis']=='x' else 0));b=a+w
        fixed=round(door['fixed']-(x0 if door['axis']=='z' else 0))
        from tools.reference_courtyard.assembly_scene import offset
        for side in (door.get('side',1),-door.get('side',1)):
            if door['axis']=='x':
                facing='south' if side==-1 else 'north'
                root=(b,round(door['y']),fixed) if facing=='south' else (a-1,round(door['y']),fixed)
            else:
                facing='east' if side==-1 else 'west'
                root=(fixed,round(door['y']),a-1) if facing=='east' else (fixed,round(door['y']),b)
            parts=[];turn=['north','east','south','west'].index(facing)
            for y in range(h+1):
                for z in range(3):
                    for x in range(w+2):
                        dx,dy,dz=offset(x,y,z,turn);parts.append([root[0]+dx,root[1]+dy,root[2]+dz])
            if all(tuple(p)not in reserved and 0<=p[0]<width and 0<=p[2]<depth for p in parts):break
        else:raise ValueError(('整门两侧均缺少完整开合区',d['id'],door))
        for part,p in enumerate(parts):
            blocks[tuple(p)]=f'yuanxian:courtyard_double_door[facing={facing},variant={vi},part={part},waterlogged=false,open=false]'
            reserved.add(tuple(p))
        assemblies.append({'root':list(root),'family':'courtyard_double_door','variant':vi,'facing':facing,'parts':parts})
    return {'size':[width,math.ceil(max(b['y1'] for b in d['boxes']))+2,depth],
            'blocks':blocks,'assemblies':assemblies, 'beds':beds,
            'entry':[round(32-x0),0,0], 'rooms':d['rooms'], 'doors':[
                {**v,'a':v['a']-(x0 if v['axis']=='x' else 0),'b':v['b']-(x0 if v['axis']=='x' else 0),
                 'fixed':v['fixed']-(x0 if v['axis']=='z' else 0)} for v in d['doors']]}


def native_house(builder):
    s=builder()
    blocks={(x,y-2,z):b for (x,y,z),b in s.blocks.items()}
    assemblies=[{**a,'root':[a['root'][0],a['root'][1]-2,a['root'][2]],
        'parts':[[p[0],p[1]-2,p[2]] for p in a['parts']]} for a in s.assemblies]
    beds=[list(p) for p,b in blocks.items() if b.startswith('minecraft:') and '_bed[' in b and 'part=head' in b]
    return {'size':s.c['size'],'blocks':blocks,'assemblies':assemblies,'beds':beds,
            'entry':[s.start[0],s.start[1]-2,s.start[2]],'rooms':s.rooms,'doors':[]}
