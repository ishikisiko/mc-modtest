"""保留逐路逐高度路幅，并显式修正原路线上相交梯段的落脚标高。"""
from __future__ import annotations

from collections import defaultdict
import copy

from province_capitals.regional.v2.common import AIR, line, props


def road_positions(roads, width, depth):
    positions = defaultdict(set)
    for road in roads:
        span = road['width']
        if type(span) is not int or span < 1:
            raise ValueError('道路宽度须为正整数')
        for a,b in zip(road['points'],road['points'][1:]):
            previous = None
            for x,y,z in line(a,b):
                if previous and (max(abs(x-previous[0]),abs(z-previous[2]))!=1 or abs(y-previous[1])>1):
                    raise ValueError('道路中心线不连续或跨级：'+road['id'])
                previous = (x,y,z)
                major_x = abs(b[0]-a[0]) >= abs(b[2]-a[2])
                for cross in range(-(span//2),span-span//2):
                    xx,zz=(x,z+cross) if major_x else (x+cross,z)
                    if 0<=xx<width and 0<=zz<depth:positions[xx,y,zz].add(road['id'])
    return dict(positions)


def _horizontal_trace(points):
    result=[]
    for a,b in zip(points,points[1:]):
        for x,y,z in line(a,b):
            if not result or result[-1]!=(x,z):result.append((x,z))
    return result


def elevation_plan(model,spec):
    overrides=spec.get('road_elevation_overrides',{})
    indexed={r['id']:r for r in model.roads}
    if overrides.keys()-indexed.keys():raise ValueError('道路标高调整引用未知道路')
    original_columns=dict(model.road_columns);records=[]
    for rid,change in overrides.items():
        before=indexed[rid]
        if set(change)-{'points','reason'} or not change.get('reason'):
            raise ValueError('道路标高调整只允许明确路线和设计理由')
        points=change['points']
        if (len(points)<2 or any(len(p)!=3 or any(type(v)is not int for v in p) for p in points)
                or _horizontal_trace(before['points'])!=_horizontal_trace(points)):
            raise ValueError('道路标高修正不能改变原水平中心线或路幅')
        records.append({'road_id':rid,'width':before['width'],'before_points':copy.deepcopy(before['points']),
                        'points':copy.deepcopy(points),'reason':change['reason']})
        before['points']=copy.deepcopy(points)
    model.road_columns={};model.road_goals={};model.make_road_columns()
    # 验证声明本身没有因二维映射而丢掉不同高度；真正施工后仍逐XYZ检查。
    road_positions(model.roads,model.width,model.depth)
    cells={};earth=[];columns=[]
    for (x,z),row in model.road_columns.items():
        old=original_columns[x,z]
        if (row[0],row[2],row[3])==(old[0],old[2],old[3]):continue
        if (x,z) in model.occupied:raise ValueError('道路标高修正侵入建筑足迹')
        y=row[0];delta=y-old[0]
        if abs(delta)>spec['rules']['local_earthwork_limit']:
            raise ValueError('道路标高修正超出局部整地上限')
        ground=model.ground(x,z)
        if delta:earth.append({'column':[x,z],'before_air_y':ground+1,'air_y':ground+1+delta,'owner':row[4]})
        for yy in range(min(ground+1,y-1),y-1):cells[x,yy,z]=model.material('BASE_STONE')
        cells[x,y-1,z]=(props(model.material('PATH_STAIRS'),facing=row[3],half='bottom',shape='straight',waterlogged=False)
                         if row[2] else model.material('GROUND_PATH'))
        for dy in range(spec['rules']['headroom']):cells[x,y+dy,z]=AIR
        columns.append({'column':[x,z],'before_y':old[0],'air_y':y,'road_id':row[4]})
    return {'routes':records,'columns':columns,'earthwork':earth},cells


def apply_elevations(world,model,spec):
    report,cells=elevation_plan(model,spec)
    world.manifest['roads']=copy.deepcopy(model.roads)
    world.source_terrain_before_elevations=world.terrain[:]
    edits=[]
    for p,state in sorted(cells.items()):
        before=world.get(p)
        if before!=state:
            world.set(p,state,'声明道路落脚平台修正')
            edits.append({'position':list(p),'before':before,'after':state})
    for e in report['earthwork']:
        x,z=e['column'];world.terrain[z*world.width+x]=e['air_y']-1
    report['edits']=edits
    return report
