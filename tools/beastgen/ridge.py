"""将提灯型和抱崖型的 Blockbench 原稿转换到游戏的分骨面网格。"""
from base64 import b64decode
from functools import lru_cache
import json
from pathlib import Path

from . import anim, cuboid
from ..gen_qingfeng_sword_model import decode_png_rgba

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'blueprints/beasts/folded_ridge'


@lru_cache(maxsize=2)
def source(name):
    return json.loads((SOURCE/f'{name}.bbmodel').read_text())


def coordinates(vector):
    x, y, z = vector
    return -x, -y, z


def groups(name):
    def visit(group, parent=None, pivot=(0, 0, 0)):
        offset = coordinates(tuple(a-b for a, b in zip(group['origin'], pivot)))
        yield group, parent, offset
        for child in group['children']:
            if isinstance(child, dict):
                yield from visit(child, group['name'], group['origin'])
    for root in source(name)['outliner']:
        yield from visit(root)


def build_model(name):
    ray = name == 'cliff_ray'
    bones = []
    for group, parent, (x, y, z) in groups(name):
        render_seat = 16.5 if ray else 24
        pivot = (x, y, z) if parent else (x, y+render_seat, z)
        bones.append(cuboid.Bone(group['name'], parent, pivot, coordinates(group['rotation'])))
    look = {'bone': 'head', 'max_yaw': 0 if ray else 16, 'max_pitch': 0 if ray else 12}
    return cuboid.Model('yuanxian:'+name, bones, look, .65 if ray else .60)


def quad(element, face):
    """沿用预览 BoxGeometry 的左上、右上、右下、左下 UV 顺序。"""
    x, y, z = element['from']
    X, Y, Z = element['to']
    return {
        'east':  ((X, Y, Z), (X, Y, z), (X, y, z), (X, y, Z)),
        'west':  ((x, Y, z), (x, Y, Z), (x, y, Z), (x, y, z)),
        'up':    ((x, Y, z), (X, Y, z), (X, Y, Z), (x, Y, Z)),
        'down':  ((x, y, Z), (X, y, Z), (X, y, z), (x, y, z)),
        'south': ((x, Y, Z), (X, Y, Z), (X, y, Z), (x, y, Z)),
        'north': ((X, Y, z), (x, Y, z), (x, y, z), (X, y, z)),
    }[face]


def finalize(built):
    data = source(built.name)
    size = data['resolution']
    w, h = size['width'], size['height']
    uri = data['textures'][0]['source']
    def pixels(blob):
        width, height, raw = decode_png_rgba(blob)
        if (width, height) != (w, h):
            raise ValueError('折山岭模型图集尺寸与 UV 不一致')
        rgba = list(zip(raw[0::4], raw[1::4], raw[2::4], raw[3::4]))
        return [rgba[i:i+w] for i in range(0, w*h, w)]

    built.texture = pixels(b64decode(uri.split(',', 1)[1]))
    glow = SOURCE/f'{built.name}_emissive.png'
    built.glow = pixels(glow.read_bytes()) if glow.is_file() else [[(0, 0, 0, 0)]*w for _ in range(h)]
    elements = {cube['uuid']: cube for cube in data['elements']}
    parts = []
    for group, _, _ in groups(built.name):
        triangles = []
        for uid in group['children']:
            if isinstance(uid, dict):
                continue
            element = elements.pop(uid)
            for face, data in element['faces'].items():
                lo_u, lo_v, hi_u, hi_v = data['uv']
                uv = ((lo_u, lo_v), (hi_u, lo_v), (hi_u, hi_v), (lo_u, hi_v))
                verts = []
                for point, (u, v) in zip(quad(element, face), uv):
                    xyz = coordinates(tuple(a-b for a, b in zip(point, group['origin'])))
                    verts.append([*xyz, u/w, v/h])
                # 左上到右下按 UV 顺时针，反转顶点顺序使法线指向方块外侧。
                for a, b, c in ((0, 2, 1), (0, 3, 2)):
                    triangles.append([round(n, 7) for vertex in (verts[a], verts[b], verts[c]) for n in vertex])
        if triangles:
            parts.append({'bone': group['name'], 'triangles': triangles})
    if elements:
        raise ValueError('折山岭模型有未绑定骨骼的立方体')
    built.surface = {'schema': 1, 'id': built.model.id, 'parts': parts}
    built.model.surface = built.surface
    built.model.texture_size = (w, h)
    built.model_doc = cuboid.model_json(built.model)
    built.model_doc['surface_mesh'] = f'yuanxian:beast/{built.name}_surface.json'


def extra_outputs(built):
    path = ROOT/f'src/main/resources/assets/yuanxian/beast/{built.name}_surface.json'
    parts = [json.dumps(p, separators=(',', ':')) for p in built.surface['parts']]
    return {path: ('{"schema":1,"id":"'+built.model.id+'","parts":[\n'+',\n'.join(parts)+'\n]}\n').encode()}


def paint(_texel):
    return None


glow = paint


def clip(name, ticks, loop, *frames):
    return anim.Clip(name, ticks/20, loop, [anim.Key(t/20, pose, 'linear') for t, pose in frames])


def changes(absolute, rest, *, lift=0):
    pose = {}
    for bone in set(absolute) | set(rest):
        r = tuple(a-b for a, b in zip(absolute.get(bone, (0, 0, 0)), rest.get(bone, (0, 0, 0))))
        if any(r):
            pose[bone] = {'rotation': coordinates(r)}
    if lift:
        pose.setdefault('root', {})['position'] = (0, lift, 0)
    return dict(sorted(pose.items()))


def lantern_clips(model, server):
    poses = json.loads((SOURCE/'poses.json').read_text())['lantern_carrier']
    low, raised = poses['low'], poses['raise']
    move = server['moves'][0]
    hit = move['active_ticks'][0]
    follow = {**raised, 'torso': (-11, 36, -7), 'left_upper_arm': (-40, 5, 17),
              'left_forearm': (-22, 4, -19), 'lantern': (20, 7, -16)}
    impact = {**follow, 'torso': (-11, -12, -5), 'left_upper_arm': (-59, 12, 25)}
    sweep = clip('sweep', move['total_ticks'], False, (0, {}), (hit-3, changes(raised, low)),
                 (hit, changes(impact, low)), (hit+4, changes(follow, low)),
                 (move['total_ticks'], {}))

    def gait(key, ticks, angle):
        def stride(phase):
            return changes({'left_hip': (-angle*phase, 0, 0), 'right_hip': (angle*phase, 0, 0),
                            'left_knee': (angle*max(0, phase), 0, 0),
                            'right_knee': (angle*max(0, -phase), 0, 0)}, {})
        return clip(key, ticks, True, (0, stride(1)), (ticks*.25, {}), (ticks*.5, stride(-1)),
                    (ticks*.75, {}), (ticks, stride(1)))

    return [clip('idle', 40, True, (0, {}), (20, changes({'head': (1.5, 0, 0)}, {})), (40, {})),
            gait('walk', 22, 22), gait('run', 14, 33), sweep,
            clip('stagger', 14, False, (0, {}), (4, changes({'torso': (10, 0, -7)}, {})), (14, {}))]


def ray_clips(model, server):
    poses = json.loads((SOURCE/'poses.json').read_text())['cliff_ray']
    perch, flight = poses['perch'], poses['spread']
    move = server['moves'][0]
    release = json.loads((ROOT/'src/main/resources/data/yuanxian/beast/cliff_ray_perch.json').read_text())['release_frame']
    spread = changes(flight, perch)
    windup = dict(perch, head=(20, 2, 0))
    swoop = dict(flight, root=(9, -3, -2), head=(-3, 0, 0))
    return [clip('idle', 30, True, (0, {}), (30, {})),
            clip('walk', 20, True, (0, {}), (20, {})),
            clip('run', 20, True, (0, {}), (20, {})),
            clip('flight', 28, True, (0, spread), (28, spread)),
            clip('swoop', move['total_ticks'], False, (0, {}), (release, changes(windup, perch)),
                 (move['active_ticks'][0], changes(swoop, perch)),
                 (move['active_ticks'][1], changes(swoop, perch)), (move['total_ticks'], spread)),
            clip('stagger', 14, False, (0, {}), (5, changes(dict(flight, root=(-8, 0, 8)), perch)), (14, spread))]
