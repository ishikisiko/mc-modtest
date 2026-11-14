"""书册只保存连续排列区域，区块模型按位置生成；此算法与 Java 共享整数量化规则。"""
from collections import defaultdict
import json
import math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'blueprints/qianji/bookcases.json'
RULES=json.loads(SOURCE.read_text())
ROLE=RULES['marker_role']


def regions(case):
    """返回作者坐标的书册排列包围盒、朝向及每排底部，不展开单本书。"""
    x,y,z=case['p'];width,height=case['size'];angle=math.radians(case.get('yaw',0));c,s=math.cos(angle),math.sin(angle)
    def point(px,py,pz):return [x+px*c+pz*s,y+py,z-px*s+pz*c]
    normal=(-round(s),-round(c));facing={(0,-1):0,(1,0):1,(0,1):2,(-1,0):3}[normal]
    for row in range(RULES['rows']):
        base=row*height/RULES['rows']+RULES['row_lift']
        corners=[point(xx,yy,zz) for xx in (RULES['start_x'],width-RULES['end_margin'])
                 for yy in (base,base+max(RULES['heights'])/16)
                 for zz in (RULES['front_z'],RULES['front_z']+RULES['depth'])]
        bounds=[round(min(p[i]for p in corners)*16)for i in range(3)]+[round(max(p[i]for p in corners)*16)for i in range(3)]
        yield bounds,facing,round((y+base)*16)


def add_regions(raw,cases):
    for case in cases:
        for bounds,facing,row_y in regions(case):
            for y in range(bounds[1]//16,(bounds[4]+15)//16):
                for z in range(bounds[2]//16,(bounds[5]+15)//16):
                    for x in range(bounds[0]//16,(bounds[3]+15)//16):
                        off=(x*16,y*16,z*16)
                        box=tuple(max(0,bounds[i]-off[i])for i in range(3))+tuple(min(16,bounds[i+3]-off[i])for i in range(3))
                        if all(box[i]<box[i+3]for i in range(3)):
                            raw[x,y+1,z].append(box+(ROLE,facing,row_y-off[1]))


def mix(value):
    value&=0xffffffff;value^=value>>16;value=value*0x7feb352d&0xffffffff
    value^=value>>15;value=value*0x846ca68b&0xffffffff
    return (value^(value>>16))&0xffffffff


def pattern(horizontal,y,row_y):
    return mix((horizontal*73428767)^((y*16+row_y)*438289))%RULES['patterns']


def books(region,seed):
    """局部格内书册；跨 Y 格使用同一排底部和种子，书脊高度连续。"""
    box=region[:6];facing,row_y=region[6:8];axis=0 if facing in(0,2)else 2
    low=box[axis];index=0
    while low<box[axis+3]:
        value=mix(seed+index*0x9e3779b9)
        width=RULES['widths'][value%len(RULES['widths'])]
        height=RULES['heights'][(value>>8)%len(RULES['heights'])]
        role=RULES['book_roles'][(value>>16)%len(RULES['book_roles'])]
        out=list(box);out[axis]=low;out[axis+3]=min(low+width,box[axis+3]);out[1]=max(box[1],row_y);out[4]=min(box[4],row_y+height)
        if out[4]>out[1]:yield tuple(out)+(role,)
        low+=width+RULES['gap'];index+=1


def split_profile(profile):
    return [b for b in profile if b[6]!=ROLE],[list(b[:6])+list(b[7:9])for b in profile if b[6]==ROLE]


def model(profile,definitions,city):
    from tools.lord_manors.generate import model as static_model
    static,rows=split_profile(profile)
    if not rows:return static_model(profile,definitions,city)
    refs={role:'myvillage:block/qianji/qianji_'+role for role in RULES['book_roles']}
    base=static_model(static,definitions,city)if static else dict(parent='minecraft:block/block',ambientocclusion=True,render_type='minecraft:solid',textures={'particle':refs[RULES['book_roles'][0]]},elements=[])
    return dict(parent='minecraft:block/block',ambientocclusion=True,loader='myvillage:procedural_books',
                textures={**base['textures'],**refs},base_model=base,book_regions=rows)
