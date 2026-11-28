"""Adapt the approved cuboids and pixel materials to the native beast skeleton."""
from base64 import b64decode
from functools import lru_cache
from math import ceil
from types import SimpleNamespace

from . import anim, cuboid
from .build import REPO, RESOURCES
from ..gen_qingfeng_sword_model import decode_png_rgba
import json

SOURCE = REPO / 'blueprints/beasts/field_encounters'
NAMES = ('tide_mud_crab', 'hook_jaw_scorpion', 'tide_dragon', 'stele_ape')
SHADOW = dict(tide_mud_crab=.85, hook_jaw_scorpion=.85, tide_dragon=3.6, stele_ape=4.0)


def definition(name):
    return SimpleNamespace(build_model=lambda: model(name), clips=lambda _, server: clips(name, server),
                           paint=transparent, glow=transparent, finalize=finalize, extra_outputs=extra_outputs)


def transparent(_):
    return None


@lru_cache(maxsize=4)
def source(name):
    result=json.loads((SOURCE/f'{name}.json').read_text())
    if result['schema']!=1 or result['id']!=f'yuanxian:{name}':
        raise ValueError(f'原稿身份错误 {name}')
    return result


def coords(vector):
    x,y,z=vector
    return 16*x,-16*y,-16*z


def model(name):
    data=source(name)
    origins={}
    bones=[]
    for part in mesh_bones(name):
        parent=part['parent']
        base=origins[parent] if parent else (0,0,0)
        origins[part['name']]=part['origin']
        x,y,z=coords(a-b for a,b in zip(part['origin'],base))
        bones.append(cuboid.Bone(part['name'],parent,(x,y if parent else y+24,z)))
    look='head' if 'head' in origins else 'root'
    return cuboid.Model(data['id'],bones,{'bone':look,'max_yaw':12 if look=='head' else 0,'max_pitch':9 if look=='head' else 0},SHADOW[name])


def transform(matrix, v):
    x,y,z=v
    return tuple(matrix[i]*x+matrix[i+4]*y+matrix[i+8]*z+matrix[i+12] for i in range(3))


@lru_cache(maxsize=4)
def mesh_bones(name):
    data=source(name)
    parts=[]
    for bone in data['bones']:
        retained=[]
        parts.append({**bone,'boxes':retained})
        for index,cube in enumerate(bone['boxes']):
            matrix=cube['transform']
            vector=data['vertices']
            points=[transform(matrix,vector[k:k+3]) for k in range(0,len(vector),3)]
            if any(abs(a-b)>16 for point in points for a,b in zip(point,bone['origin'])):
                # Keep the approved cube under its animated bone with a nearby mesh pivot.
                parts.append({'name':f"{bone['name']}_mesh_{index}", 'parent':bone['name'],
                              'origin':matrix[12:15], 'boxes':[cube]})
            else:
                retained.append(cube)
    return parts


def finalize(built):
    data=source(built.name)
    slots=4
    width=slots*32
    height=ceil(len(data['materials'])/slots)*32
    blank=(0,0,0,0)
    texture=[[blank]*width for _ in range(height)]
    glow=[[blank]*width for _ in range(height)]
    for i, material in enumerate(data['materials']):
        w,h,rgba=decode_png_rgba(b64decode(material['rgba']))
        if (w,h)!=(32,32):
            raise ValueError('预览贴图的像素尺寸应为 32×32')
        x=(i%slots)*32
        y=(i//slots)*32
        colors=list(zip(rgba[0::4],rgba[1::4],rgba[2::4],rgba[3::4]))
        for j in range(32):
            row=colors[j*32:j*32+32]
            texture[y+j][x:x+32]=row
            if material['light']:
                glow[y+j][x:x+32]=row
    result=[]
    vertices=data['vertices']
    uv=data['uv']
    for bone in mesh_bones(built.name):
        triangles=[]
        for cube in bone['boxes']:
            i=cube['material']
            x=(i%slots)*32
            y=(i//slots)*32
            converted=[]
            for k in range(len(vertices)//3):
                value=transform(cube['transform'],vertices[k*3:k*3+3])
                point=coords(a-b for a,b in zip(value,bone['origin']))
                if any(abs(v)>256 for v in point):
                    raise ValueError(f'分骨坐标超出渲染范围 {built.name} {bone["name"]}')
                u,v=uv[k*2:k*2+2]
                converted.append((*point,(x+.5+31*u)/width,(y+.5+31*(1-v))/height))
            indices=data['indices']
            for cursor in range(0,len(indices),3):
                triangles.append([round(n,7) for k in indices[cursor:cursor+3] for n in converted[k]])
        if triangles:
            result.append({'bone':bone['name'],'triangles':triangles})
    built.texture=texture
    built.glow=glow
    built.surface={'schema':1,'id':data['id'],'parts':result}
    built.model.texture_size=(width,height)
    built.model.surface=built.surface
    built.model_doc=cuboid.model_json(built.model)
    built.model_doc['surface_mesh']=f'yuanxian:beast/{built.name}_surface.json'


def clips(name, server):
    bones={part['name'] for part in source(name)['bones']}

    def pose(values):
        return {bone:{'rotation':vector} for bone,vector in values.items() if bone in bones}

    def clip(key, ticks, frames, loop=False):
        return anim.Clip(key,ticks/20,loop,[anim.Key(t/20,pose(p),'linear') for t,p in frames])

    def gait(key,ticks,angle):
        gait_bones=['leg_left_front','leg_right_back','leg_right_front','leg_left_back',
                    'leg_left','leg_right','arm_left','arm_right','hips']
        stance={key:(angle*(1 if i%2==0 else -1),0,0) for i,key in enumerate(gait_bones)}
        reverse={key:tuple(-v for v in vector) for key,vector in stance.items()}
        zero={bone:(0,0,0) for bone in stance}
        return clip(key,ticks,[(0,stance),(ticks/4,zero),(ticks/2,reverse),(ticks*3/4,zero),(ticks,stance)],True)

    attacks={
        'claw': ({'claw_left':(4,-21,-11),'claw_right':(4,21,11)}, {'claw_left':(-11,13,0),'claw_right':(-11,-13,0)}),
        'sting': ({'stinger':(19,19,4)}, {'stinger':(-49,0,4),'root':(-5,0,0)}),
        'sweep': ({'head':(-10,-27,5),'body':(0,-14,0),'arm_right':(-30,-37,28)},
                  {'head':(5,24,2),'body':(6,16,0),'arm_right':(-75,27,15)}),
        'surge': ({'head':(-17,-19,0),'jaw':(18,0,0),'gill_left':(0,23,-11),'gill_right':(0,-23,11)},
                  {'head':(-4,2,0),'jaw':(22,0,0),'gill_left':(0,34,-11),'gill_right':(0,-34,11)}),
        'reflux': ({'head':(9,18,0),'gill_left':(0,37,-20),'gill_right':(0,-37,20)},
                   {'head':(6,22,0),'gill_left':(0,42,-20),'gill_right':(0,-42,20)}),
        'stele': ({'stele':(-34,0,12),'body':(-18,12,0),'arm_left':(-126,-4,6),'arm_right':(-132,4,-9)},
                  {'stele':(66,-12,-29),'body':(23,0,-6),'arm_left':(-67,18,-1),'arm_right':(-81,-18,2)}),
    }
    idle={'head':(0,0,0),'stinger':(0,0,0)}
    result=[clip('idle',44,[(0,idle),(22,{'head':(2,0,0),'stinger':(4,0,0)}),(44,idle)],True),
            gait('walk',26,13),gait('run',18,24),
            clip('stagger',14,[(0,{'root':(0,0,0)}),(4,{'root':(5,0,-3)}),(14,{'root':(0,0,0)})]),
            clip('hurt',12,[(0,{**idle,'root':(0,0,0)}),(4,{'head':(7,0,0),'root':(2,0,-3)}),(12,{**idle,'root':(0,0,0)})]),
            clip('death',20,[(0,{'root':(0,0,0)}),(20,{'root':(0,0,72)})])]
    for move in server['moves']:
        key=move['animation']
        at=move['active_ticks'][0]
        before,impact=attacks[key]
        ticks=move['total_ticks']
        neutral={bone:(0,0,0) for bone in sorted(before.keys() | impact.keys())}
        result.append(clip(key,ticks,[(0,neutral),(max(1,at-4),neutral|before),(at,neutral|impact),
                                     (min(at+8,ticks-1),neutral|impact),(ticks,neutral)]))
    return result


def extra_outputs(built):
    path=RESOURCES / f'assets/yuanxian/beast/{built.name}_surface.json'
    lines=[json.dumps(p,separators=(',',':')) for p in built.surface['parts']]
    return {path:('{"schema":1,"id":"'+built.model.id+'","parts":[\n'+',\n'.join(lines)+'\n]}\n').encode()}
