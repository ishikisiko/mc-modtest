"""正式道路的用途、路肩与磨损：只铺既有路幅，配置决定地域差分。"""
from collections import Counter
from functools import lru_cache
import json
from pathlib import Path

from lord_manors.town_materials import load as town_materials

ROOT=Path(__file__).resolve().parents[3]
SOURCE=Path('blueprints/province_capitals/blueprint_plan/road_surfaces.json')


@lru_cache(maxsize=1)
def settings():
    value=json.loads((ROOT/SOURCE).read_text())
    if value['schema']!=1 or len(value['profiles'])!=7:raise ValueError('道路铺装须覆盖七城')
    return value


class RoadSurfaces:
    def __init__(self,region,roads,field,owners):
        self.config=settings();self.profile=self.config['profiles'][region];self.members={}
        self.materials={'myvillage:'+m['id']:m for m in town_materials()}
        self.classes={r['id']:r['surface'] for r in roads}
        priorities=self.config['priority']
        for road in roads:
            category=road['surface']
            if category not in priorities:raise ValueError('未注册道路用途：'+category)
            along=0
            for start,end in zip(road['points'],road['points'][1:]):
                x,_,z=start;ex,_,ez=end
                if (x,z)==(ex,ez):continue
                if x!=ex and z!=ez:raise ValueError('铺装方向须来自正交道路')
                axis='x' if ex!=x else 'z';dx=(ex>x)-(ex<x);dz=(ez>z)-(ez<z)
                length=abs(ex-x)+abs(ez-z)
                for step in range(length+1):
                    for lateral in range(-(road['width']//2),-(road['width']//2)+road['width']):
                        column=(x+dx*step+(lateral if axis=='z' else 0),z+dz*step+(lateral if axis=='x' else 0))
                        if column not in field or road['id'] not in owners[column]:continue
                        member={'road':road['id'],'category':category,'axis':axis,'along':along+step,
                                'lateral':lateral,'width':road['width']}
                        rank=(-priorities[category],abs(lateral),road['id'])
                        if column not in self.members or rank<self.members[column][0]:self.members[column]=(rank,member)
                along+=length
        if set(self.members)!=set(field):raise ValueError('道路铺装遗漏实际路幅')
        for slot,material in self.profile.items():
            if slot!='name' and material.startswith('myvillage:') and material not in self.materials:
                raise ValueError('道路引用未注册自有材质：'+material)

    def oriented(self,material,axis='z',reverse=False):
        if self.materials.get(material,{}).get('directional'):
            face=('west' if reverse else 'east') if axis=='x' else ('south' if reverse else 'north')
            return material+'[facing='+face+']'
        return material

    def state(self,x,z):
        member=self.members[x,z][1];p=self.profile;c=self.config
        kind=member['category'];along=member['along'];side=member['lateral'];width=member['width'];patterns=c['patterns']
        if kind in ('bridge','shore','civic'):material=p[kind]
        elif kind=='lane':
            material=p['lane'] if along%patterns['lane_stone_period']<patterns['lane_stone_length'] else p['lane_base']
        else:
            shoulder=c['widths']['wide_shoulder'] if width>=c['widths']['wide_threshold'] else c['widths']['shoulder']
            left=side+width//2;right=width-1-left
            if width>=c['widths']['shoulder_threshold'] and min(left,right)<shoulder:
                material=p['edge'] if min(left,right)==0 and along%patterns['edge_period']<patterns['edge_length'] else p['shoulder']
            elif kind=='market' and along%patterns['market_stone_period']<patterns['market_stone_length']:
                material=p['market_paving']
            elif side in patterns['wear_laterals'] and along%patterns['wear_period']<patterns['wear_length']:
                material=p['wear']
            else:material=p['main']
        return self.oriented(material,member['axis'],side>0 and material==p['edge'])

    def access(self,points,index):
        x,_,z=points[index]
        other=points[index+1] if index+1<len(points) else points[index-1] if index else points[index]
        axis='x' if other[0]!=x else 'z'
        pattern=self.config['patterns']
        material=self.profile['lane'] if index%pattern['lane_stone_period']<pattern['lane_stone_length'] else self.profile['lane_base']
        return self.oriented(material,axis)

    def summary(self,field,get):
        return {'profile':self.profile['name'],'road_classes':self.classes,
                'actual_surface_counts':dict(sorted(Counter(get((x,y-1,z)) for (x,z),y in field.items()).items()))}
