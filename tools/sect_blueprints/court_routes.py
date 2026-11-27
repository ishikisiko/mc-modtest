"""Walkable approach, three-wide entrance connection and paired lift validation."""
from __future__ import annotations


DIRECTIONS = {(0,-1): 'north', (1,0): 'east', (0,1): 'south', (-1,0): 'west'}
AIR = 'minecraft:air'


def court_probes(spec):
    court = spec['arena']
    center_x, y, center_z = court['center']
    half = court['width'] // 2
    if court['width'] != {'branch':19,'headquarters':27,'main':37}[spec['tier']]:
        raise ValueError('比试台尺寸与宗门等级不符')
    ring = [(x,y,z) for x in range(center_x-half,center_x+half+1)
            for z in range(center_z-half,center_z+half+1)]
    exterior = []
    port = spec['road_port']['position']
    entry = spec['entry_anchor']
    dx, dz = next(delta for delta, direction in DIRECTIONS.items() if direction==spec['road_port']['facing'])
    length = (port[0]-entry[0])*dx+(port[2]-entry[2])*dz
    if length < 0 or [port[0]-length*dx,port[1],port[2]-length*dz] != entry:
        raise ValueError('山门与外路连接口不共线')
    for i in range(length+1):
        for side in (-1,0,1):
            exterior.append((port[0]-i*dx+side*dz,port[1],port[2]-i*dz+side*dx))

    road = court['path']
    width = set()
    for i,(x,y,z) in enumerate(road):
        if i <= court['join']:
            continue
        for adjacent in road[max(0,i-1):i]+road[i+1:i+2]:
            dx,dz = adjacent[0]-x,adjacent[2]-z
            width.update((x+side*dz,y,z+side*dx) for side in (-1,0,1))
    foot = set(ring+exterior+[tuple(p) for k in ('from_entry','path','door_path') for p in court[k]])|width
    required = {tuple((x,y+dy,z)) for x,y,z in foot for dy in (-1,0,1)}
    for lift in court['lifts']:
        for path in lift['approaches']:
            required.update((x,y+dy,z) for x,y,z in path for dy in (-1,0,1))
        gates, shaft = lift_cells(lift)
        required.update(gates)
        required.update(shaft)
        required.update(tuple(lift[k]) for k in ('lower','upper'))
    return foot, required


def lift_cells(lift):
    lower,upper = lift['lower'],lift['upper']
    dx,dz = next(delta for delta,name in DIRECTIONS.items() if name==lift['facing'])
    x,y,z = lower
    height = upper[1]-y
    size = lift['width']
    if size not in (2,3) or not 2<=height<=128 or (upper[0],upper[2])!=(x,z):
        raise ValueError('升降台井道尺寸无效')
    def at(y0,forward,lateral):
        return x+dx*forward-dz*lateral,y0,z+dz*forward+dx*lateral
    gates = [at(dock[1],1,i) for dock in (lower,upper) for i in range(1,size+1)]
    shaft = [at(y0,j,i) for y0 in range(y-1,upper[1]+3)
             for j in range(2,size+2) for i in range(1,size+1)]
    return gates, shaft


def validate_court(spec, foot, actual):
    arena = spec['arena']
    name = spec['id']
    if (arena['from_entry'][0]!=spec['entry_anchor'] or arena['from_entry'][-1]!=arena['path'][0]
        or arena['path'][-1]!=arena['door_path'][0] or arena['door_path'][-1]!=arena['center']):
        raise ValueError(f'山门、比试院支路或门槛断开 {name}')

    def walkable(p):
        x,y,z = p
        feet = actual[x,y,z]
        head = actual[x,y+1,z]
        support = actual[x,y-1,z]
        if feet.startswith('minecraft:ladder['):
            return head==AIR or head.startswith('minecraft:ladder[')
        return feet==AIR and (head==AIR or head.startswith('minecraft:ladder[')) and support not in (AIR,'minecraft:water')

    for p in sorted(foot):
        if not walkable(p):
            raise ValueError(f'比试院或宗门道路缺落脚点或两格净空 {name} {p}')

    def validate_walk(path, stairs):
        for a,b in zip(path,path[1:]):
            dx,dy,dz = (b[i]-a[i] for i in range(3))
            if dx==dz==0 and abs(dy)==1 and any(actual[tuple(p)].startswith('minecraft:ladder[') for p in (a,b)):
                continue
            if abs(dx)+abs(dz)!=1 or abs(dy)>1:
                raise ValueError(f'比试院通路不连续 {name} {a} {b}')
            if dy and stairs:
                x,y,z = b if dy>0 else a
                facing = DIRECTIONS[dx,dz] if dy>0 else DIRECTIONS[-dx,-dz]
                tread = actual[x,y-1,z]
                if not tread.startswith('minecraft:stone_brick_stairs[') or f'facing={facing},half=bottom' not in tread:
                    raise ValueError(f'比试院接坡缺顺向的台阶 {name} {(x,y,z)}')

    validate_walk(arena['from_entry'],False)
    validate_walk(arena['path'][arena['join']:]+arena['door_path'][1:],True)
    for lift in arena['lifts']:
        gates, shaft = lift_cells(lift)
        facing = lift['facing']
        wide = str(lift['width']==3).lower()
        for dock,upper in (('lower','false'),('upper','true')):
            if actual[tuple(lift[dock])]!=f'yuanxian:sect_lift_terminal[facing={facing},upper={upper},wide={wide}]':
                raise ValueError(f'升降台操纵座不符 {name} {dock}')
        if any(actual[p]!=f'yuanxian:sect_lift_gate[facing={facing},open=false]' for p in gates):
            raise ValueError(f'升降台护门缺失 {name}')
        if any(actual[p]!=AIR for p in shaft):
            raise ValueError(f'升降台井道不畅通 {name}')
        for path in lift['approaches']:
            if path[0] not in arena['path'] or not all(walkable(p) for p in path):
                raise ValueError(f'升降台落台没有接上步行道 {name}')
            validate_walk(path,True)
