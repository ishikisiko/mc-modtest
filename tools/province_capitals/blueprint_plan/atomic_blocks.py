"""把跨分块的完整床、街景组件作为同一个原生模板放置。"""
from __future__ import annotations

from province_capitals.regional.v2.common import DIR,props


def parse(state):
    name,_,raw=state.partition('[')
    return name,dict(v.split('=',1) for v in raw.rstrip(']').split(',') if raw)


def collect(world,rules,furniture):
    registered=rules['atomic_blocks'];families={'myvillage:'+f['id']:f for f in furniture['families']}
    members={};roots=[]
    for tx,tz in sorted(set(world.refs)|world.dirty):
        for (x,y,z),state in world.tile((tx,tz)).items():
            name,values=parse(state)
            if name not in registered:continue
            p=(tx*16+x,y,tz*16+z);members[p]=state
            if (registered[name]=='bed' and values.get('part')=='foot') or (registered[name]=='street_furniture' and values.get('part')=='0'):
                roots.append((p,name,values))
    covered=set();groups=[]
    for (x,y,z),name,values in sorted(roots):
        cells={};kind=registered[name]
        if kind=='bed':
            dx,dz=DIR[values['facing']]
            cells[(x,y,z)]=props(name,**{**values,'part':'foot'})
            cells[(x+dx,y,z+dz)]=props(name,**{**values,'part':'head'})
        elif kind=='street_furniture':
            width,height,depth=families[name]['size'];turn=('north','east','south','west').index(values['facing'])
            for index in range(width*height*depth):
                dx=index%width;dz=(index//width)%depth;dy=index//(width*depth)
                for _ in range(turn):dx,dz=-dz,dx
                cells[x+dx,y+dy,z+dz]=props(name,**{**values,'part':index})
        else:raise ValueError('未知原子组件类型：'+kind)
        for p,state in cells.items():
            if members.get(p)!=state:raise ValueError('原始多方块组件不完整或状态不一致：'+str((p,state,members.get(p))))
        if covered & cells.keys():raise ValueError('多方块组件相互重叠')
        covered.update(cells)
        if len({(p[0]//16,p[2]//16) for p in cells})>1:
            origin=[min(p[i] for p in cells) for i in range(3)]
            size=[max(p[i] for p in cells)-origin[i]+1 for i in range(3)]
            groups.append({'id':f'compound_{len(groups):04}','block':name,'origin':origin,'size':size,'cells':cells})
    if covered!=members.keys():raise ValueError('存在失去根部的多方块组件')
    return groups
