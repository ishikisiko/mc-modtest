"""城镇完成面：authored 色板和纹理类型 -> 可注册实心营造方块。"""
from pathlib import Path
import json
import math


ROOT=Path(__file__).resolve().parents[2]
SOURCE=Path('blueprints/lord_manors/town_materials.json')


def load():
    config=json.loads((ROOT/SOURCE).read_text())
    if config['schema']!=1:raise ValueError('城镇材质 schema 不符')
    result=[];seen=set()
    for raw in config['materials']:
        if raw['id'] in seen:raise ValueError('城镇材质ID重复')
        seen.add(raw['id'])
        result.append({'collides':True,'opaque':True,'role':'town_paving',
                       **raw,'texture_rules':config['texture_rules']})
    return result


def texture(material,size):
    from tools.gen_alchemy_assets import png
    spec=material['pattern'];kind=spec['kind'];rules=material['texture_rules']
    base=tuple(bytes.fromhex(material['color']));accent=tuple(bytes.fromhex(spec.get('accent',material['color'])))
    rows=[];tile=rules['stone_span'];joint=rules['joint_width'];grain=rules['grain_strength']
    for y in range(size):
        row=[]
        for x in range(size):
            noise=((x*17+y*31+x*y*7)%19-9)*grain
            shade=noise;blend=0
            if kind=='compacted':
                shade+=rules['sand_ripple']*math.sin(y*.13+math.sin(x*.11))
            elif kind=='worn':
                track=min(abs(x-size*.28),abs(x-size*.72))
                blend=max(0,1-track/rules['wear_width'])*rules['wear_blend']
                shade+=math.sin(y*.21+x*.05)*rules['sand_ripple']
            elif kind in ('setts','flagstone','ashlar'):
                span=tile//2 if kind=='setts' else tile
                offset=span//2 if y//span%2 else 0
                if y%span<joint or (x+offset)%span<joint:shade-=rules['mortar_shade']
                elif y%span==joint or (x+offset)%span==joint:shade+=rules['cut_highlight']
                shade+=((x+offset)//span*3+y//span*5)%7-3
            elif kind=='sand_edge':
                blend=max(0,1-x/rules['sand_edge_width'])*(.74+.15*math.sin(y*.19))
                if y%tile<joint:shade-=rules['mortar_shade']*(1-blend)
            elif kind=='stepping':
                # 同一夯土底上是连续的大踏石，不把小像素石子随机撒满整条巷。
                border=rules['step_inset'];inside=border<=x<size-border and 2<=y<size-2
                bevel=abs(x-size/2)+abs(y-size/2)>size*.84
                blend=.94 if inside and not bevel else 0
                if inside and (x==border or y==2):shade+=rules['cut_highlight']
            elif kind=='inlay':
                band=rules['inlay_offset'];width=rules['inlay_width']
                blend=1 if min(abs(x-band),abs(x-(size-1-band)))<width else 0
                if y%tile<joint:shade-=rules['mortar_shade']
            elif kind=='plaster':
                shade=noise*.45+math.sin(x*.13+y*.1)*rules['plaster_mottle']
            elif kind=='wood':
                shade+=rules['wood_grain']*math.sin(x*.38+math.sin(y*.14))
                if x%rules['wood_board_span']==0:shade-=rules['wood_seam']
            elif kind=='ash':shade+=rules['sand_ripple']*math.sin(x*.24+y*.19)
            else:raise ValueError('未注册城镇纹理类型：'+kind)
            row.append(tuple(max(0,min(255,round(c*(1-blend)+a*blend+shade))) for c,a in zip(base,accent))+(255,))
        rows.append(row)
    return png(rows)
