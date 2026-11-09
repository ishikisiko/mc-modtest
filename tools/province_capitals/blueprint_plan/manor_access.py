"""显式府前宽阶及从街面到真实门洞的碰撞检查。"""
from functools import lru_cache
import math
import re


@lru_cache(maxsize=None)
def collision_boxes(state):
    from .manor_relayout import manor_catalog
    if state in (None, 'minecraft:air') or state.startswith('minecraft:water'):
        return ()
    name = state.split('[', 1)[0]
    properties = dict(re.findall(r'(\w+)=([\w]+)', state))
    if '_carved_' in name:
        banks = {b['id']: b for b in manor_catalog()['banks']}
        bank = banks.get(name.split(':', 1)[1])
        if bank is None:
            raise ValueError('府前碰撞引用未知构件：' + name)
        boxes = [tuple(v / 16 for v in b) for b in bank['profiles'][int(properties['shape'])]['collision']]
        turns = ('north', 'east', 'south', 'west').index(properties.get('facing', 'north'))
        for _ in range(turns):
            boxes = [(1-b[5], b[1], b[0], 1-b[2], b[4], b[3]) for b in boxes]
        return tuple(boxes)
    if name.endswith('_slab'):
        kind = properties.get('type', 'bottom')
        return ((0, .5 if kind == 'top' else 0, 0, 1, .5 if kind == 'bottom' else 1, 1),)
    if name.endswith('_stairs'):
        boxes = [(0, 0, 0, 1, .5, 1), (0, .5, 0, 1, 1, .5)]
        for _ in range(('north', 'east', 'south', 'west').index(properties.get('facing', 'north'))):
            boxes = [(1-b[5], b[1], b[0], 1-b[2], b[4], b[3]) for b in boxes]
        if properties.get('half') == 'top':
            boxes = [(b[0], 1-b[4], b[2], b[3], 1-b[1], b[5]) for b in boxes]
        return tuple(boxes)
    return ((0, 0, 0, 1, 1, 1),)


def standing(get, x, feet2, z, headroom):
    """检查 0.6 格宽的身体及真实脚下碰撞；高度以半格保存。"""
    feet = feet2 / 2
    supported = False
    for y in range(math.floor(feet)-1, math.ceil(feet+headroom)):
        for a,b,c,d,e,f in collision_boxes(get((x,y,z))):
            if d <= .2 or a >= .8 or f <= .2 or c >= .8:
                continue
            low, high = y+b, y+e
            if abs(high-feet) < 1e-6:
                supported = True
            if high > feet+1e-6 and low < feet+headroom-1e-6:
                return False
    return supported


def source_column(planner, x, z):
    from .manor_relayout import rotate_cell
    minimum = planner.manor_manifest['source_min']
    w,_,d = planner.manor_manifest['size']
    m = planner.layout['manor']
    u,v = rotate_cell(x-minimum[0], z-minimum[2], w,d,m['rotation_quarters'])
    return u+m['site'][0],v+m['site'][1]


def build(planner, config):
    """只在声明的接路范围施工，原府邸构件必须完整保留。"""
    from .manor_relayout import AIR, props
    axis = planner.layout['manor']['entry_axis_source_x']
    rows = config['rows']
    native = config['native_rows']
    delta2 = planner.manor_ground_delta * 2
    columns = []
    curbs = []
    centerline = []
    if any(abs(a[0]-b[0]) != 1 or abs(a[1]-b[1]) > 1 for a,b in zip(rows+native, (rows+native)[1:])):
        raise ValueError('府前阶梯行或半格高差不连续')
    for index,(sz,feet2,width) in enumerate(rows+native):
        feet2 += delta2
        cx,cz = source_column(planner,axis,sz)
        centerline.append([cx,feet2,cz])
        for sx in range(axis-width//2, axis-width//2+width):
            x,z = source_column(planner,sx,sz)
            if index < len(rows):
                column = x,z
                if column in planner.building_columns or column in planner.private_owner or planner.is_wet(x,z):
                    raise ValueError('府前宽阶侵入建筑、私院或水体：'+str(column))
                old = planner.road_field.get(column)
                if old is not None and old*2 != feet2:
                    raise ValueError('府前宽阶改变既有街面高度：'+str(column))
                bottom = (feet2-1)//2
                existing = any((x,y,z) in planner.manor_cells for y in range(bottom,math.ceil(feet2/2)+planner.cfg['headroom']))
                if existing:
                    if not standing(planner.get,x,feet2,z,planner.cfg['headroom']):
                        raise ValueError('府前宽阶未接齐原构件碰撞：'+str((x,feet2,z)))
                elif old is None:
                    if abs(feet2/2-(planner.ground(x,z)+1)) > planner.rules['local_earthwork_limit']:
                        raise ValueError('府前宽阶超出局部土方范围：'+str(column))
                    state = props(config['slab'],type='bottom',waterlogged=False) if feet2%2 else config['surface']
                    ground = planner.ground(x,z)
                    if ground >= bottom:
                        planner.set_ground(x,z,bottom-1)
                        ground = bottom-1
                    if ground < bottom-1:
                        planner.box((x,ground+1,z),(x,bottom-1,z),config['foundation'],'府前宽阶','support')
                    planner.set((x,bottom,z),state,'府前宽阶','road')
                    for y in range(bottom+1,bottom+1+planner.cfg['headroom']):
                        planner.set((x,y,z),AIR,'府前宽阶','clearance')
                planner.access_field[column] = math.ceil(feet2/2)
            columns.append([x,feet2,z])
        if index < len(rows):
            for sx in (axis-width//2-1,axis-width//2+width):
                x,z=source_column(planner,sx,sz)
                if (x,z) in planner.road_field:
                    continue
                if any((x,y,z) in planner.manor_cells for y in range(planner.height)):
                    continue
                if (x,z) in planner.building_columns or (x,z) in planner.private_owner or planner.is_wet(x,z):
                    raise ValueError('府前石缘侵入原地块：'+str((x,z)))
                edge_feet=feet2+config['curb_rise2'];bottom=(edge_feet-1)//2
                if abs(edge_feet/2-(planner.ground(x,z)+1)) > planner.rules['local_earthwork_limit']:
                    raise ValueError('府前石缘超出局部土方范围')
                ground=planner.ground(x,z)
                if ground>=bottom:
                    planner.set_ground(x,z,bottom-1);ground=bottom-1
                if ground<bottom-1:
                    planner.box((x,ground+1,z),(x,bottom-1,z),config['foundation'],'府前石缘','support')
                state=props(config['curb_slab'],type='bottom',waterlogged=False) if edge_feet%2 else config['curb_surface']
                planner.set((x,bottom,z),state,'府前石缘','road_edge')
                for y in range(bottom+1,bottom+1+planner.cfg['headroom']):
                    planner.set((x,y,z),AIR,'府前石缘','clearance')
                curbs.append([x,edge_feet,z])
    portal = config['portal_source']
    x,z = source_column(planner,portal[0],portal[2])
    planner.manor_entry = [x,portal[1]+planner.manor_ground_delta,z]
    if centerline[-1] != [x,planner.manor_entry[1]*2,z]:
        raise ValueError('府前路线没有抵达声明的真实门洞')
    road = config['street_id']
    x,feet2,z = centerline[0]
    if road not in planner.road_owners[x,z] or planner.road_field[x,z]*2 != feet2:
        raise ValueError('府前下平台没有接到声明的街道')
    route = {'building_id':'lord_manor','road_id':road,
             'points':[[x,math.ceil(y/2),z] for x,y,z in reversed(centerline)],
             'feet2_points':list(reversed(centerline)), 'surface_columns':columns,
             'curb_columns':curbs,
             'length':len(centerline)-1,'portal':planner.manor_entry,
             'kind':'formal_manor_approach'}
    planner.model.access.append(route)
    planner.manor_approach = route
    audit(planner, route)


def audit(planner, route):
    for x,feet2,z in route['surface_columns']+route['curb_columns']:
        if not standing(planner.get,x,feet2,z,planner.cfg['headroom']):
            raise ValueError('府前整幅路线缺少支撑或碰撞净空：'+str((x,feet2,z)))
    for a,b in zip(route['feet2_points'],route['feet2_points'][1:]):
        if abs(a[0]-b[0])+abs(a[2]-b[2]) != 1 or abs(a[1]-b[1]) > 1:
            raise ValueError('府前通路存在未处理的高坎')
    if route['points'][0] != route['portal']:
        raise ValueError('府前连通检查未覆盖真实门洞')
