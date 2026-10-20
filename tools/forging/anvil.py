"""实体锻台、台面器坯和锻锤的唯一模型来源。"""
import json
from tools.gen_alchemy_assets import png

KINDS = ('sword', 'spear', 'pickaxe', 'axe', 'shovel', 'hoe')
COLORS = {
    'stone': (48, 52, 51), 'wood': (74, 49, 32), 'iron': (102, 113, 111),
    'edge': (169, 180, 171), 'dark': (27, 30, 29), 'copper': (142, 88, 49),
    'coal': (36, 29, 25), 'fire': (227, 113, 31),
    'rough': (89, 91, 83), 'quench': (61, 79, 91), 'temper': (123, 116, 83),
    'hotmetal': (250, 141, 57),
}


def model(parts):
    return {'ambientocclusion': True,
            'textures': {**{k: f'myvillage:block/workshop_{k}' for k in COLORS},
                         'particle': 'myvillage:block/workshop_stone'},
            'elements': parts}


def box(parts, name, lo, hi, texture):
    parts.append({'name': name, 'from': lo, 'to': hi, 'faces': {
        side: {'texture': '#' + texture, 'uv': [0, 0, 16, 16]}
        for side in ('up', 'down', 'north', 'south', 'east', 'west')}})


def station(lit=False):
    p = []
    def b(name, lo, hi, tex): box(p, name, lo, hi, tex)
    b('足座', [0, 0, 1], [16, 2, 15], 'stone')
    b('炉膛内壁', [2, 2, 3], [14, 9, 14], 'dark')
    b('左炉柱', [1, 2, 1], [4, 10, 15], 'stone')
    b('右炉柱', [12, 2, 1], [15, 10, 15], 'stone')
    b('炉口横梁', [4, 7, 1], [12, 10, 4], 'stone')
    b('炉床', [4, 2, 1], [12, 3, 12], 'coal')
    for x, z, h in [(5, 3, 1), (8, 4, 1.4), (10, 3, .7), (6, 6, .8)]:
        b('炭块', [x, 3, z], [x+1.8, 3+h, z+1.5], 'fire' if lit else 'coal')
    b('深木台沿', [0, 10, 0], [16, 12, 16], 'wood')
    for x in (1, 14):
        b('铜箍', [x, 9.5, -.05], [x+.7, 12.1, 16.05], 'copper')
        b('铆钉', [x-.1, 10.5, -.2], [x+.8, 11.3, 0], 'dark')
    b('砧座', [3, 12, 3], [13, 13, 12], 'dark')
    b('砧腰', [6, 13, 5], [10, 15, 10], 'iron')
    b('砧面', [2, 15, 3], [13, 16, 12], 'iron')
    b('砧面亮边', [2, 15.75, 3], [13, 16, 3.35], 'edge')
    b('砧角', [0, 15, 5], [2, 15.8, 10], 'iron')
    b('砧角尖', [-1, 15.1, 6], [0, 15.65, 9], 'iron')
    b('锻锤支架', [14.8, 12, 7], [16, 20.8, 9], 'wood')
    b('支架底箍', [14.6, 12, 6.8], [16.2, 13, 9.2], 'copper')
    b('锤柄转轴', [15, 19.9, 6.6], [16, 21, 9.4], 'iron')
    b('工具横架', [13.8, 7.5, 3], [16.5, 8.5, 13], 'wood')
    for z in (4, 10):
        b('钳柄甲', [15.9, 3.2, z], [16.45, 10, z+.45], 'iron')
        b('钳柄乙', [15.9, 3.2, z+1.2], [16.45, 10, z+1.65], 'iron')
        b('钳首', [15.8, 9.5, z-.25], [16.5, 11, z+1.9], 'dark')
        b('钳轴', [16.45, 8.2, z+.45], [16.7, 8.8, z+1.2], 'copper')
    return model(p)


def hammer():
    p = []
    box(p, '锤柄', [7.55, -1, 7.55], [8.45, 7, 8.45], 'wood')
    box(p, '柄箍', [7.4, 5, 7.4], [8.6, 6, 8.6], 'copper')
    box(p, '锤头', [5.4, 6, 6.5], [10.6, 9, 9.5], 'iron')
    box(p, '锤击面', [5.3, 6.1, 6.6], [5.6, 8.9, 9.4], 'edge')
    box(p, '柄楔', [7.6, 9, 7.6], [8.4, 9.2, 8.4], 'dark')
    return model(p)


def workpiece(kind, stage, hot=False):
    p = []
    tex = 'hotmetal' if hot else ('rough', 'iron', 'quench', 'temper', 'edge', 'edge')[stage]
    def b(name, lo, hi): box(p, name, lo, hi, tex)
    if stage == 0:
        b('粗坯', [3.5, 0, 5], [12.5, 1.2, 10])
        b('锻痕', [4, 1.2, 6], [10, 1.4, 7])
    elif kind in ('sword', 'spear'):
        b('刃身', [2, 0, 6.4], [12, .65, 9.6])
        b('刃尖', [12, .1, 7.2], [14, .55, 8.8])
        b('脊线', [3, .65, 7.7], [11.5, .85, 8.3])
        if kind == 'spear':
            b('枪套', [.5, 0, 7.3], [3, 1, 8.7])
    elif kind == 'pickaxe':
        b('镐肩', [4, 0, 6], [12, .85, 9])
        b('镐尖左', [2, 0, 8], [5, .65, 10])
        b('镐尖右', [11, 0, 8], [14, .65, 10])
    elif kind == 'axe':
        b('斧眼', [3, 0, 6], [6, 1.3, 9])
        b('斧刃', [6, 0, 4.5], [11, .7, 10.5])
        b('刃沿', [11, .15, 4], [12, .45, 11])
    elif kind == 'shovel':
        b('铲套', [2, 0, 7], [5, 1, 9])
        b('铲面', [5, 0, 5], [11, .6, 11])
        b('铲尖', [11, .1, 6], [13, .45, 10])
    else:
        b('锄眼', [3, 0, 5], [6, 1.3, 8])
        b('锄身', [6, 0, 5], [10, .7, 8])
        b('锄刃', [9, 0, 8], [11, .5, 11])
    if stage == 5:
        if kind in ('pickaxe', 'axe', 'hoe'):
            x = 7.5 if kind == 'pickaxe' else 3.5
            box(p, '装柄', [x, .2, 7.5], [x+1, .9, 16], 'wood')
        else:
            box(p, '装柄', [-1, .2, 7.5], [4, .9, 8.5], 'wood')
    for element in p:
        for face in element['faces'].values():
            face['tintindex'] = 0
    return model(p)


def outputs(base):
    out = {}
    def add(path, obj): out[base/path] = (json.dumps(obj, ensure_ascii=False, indent=2)+'\n').encode()
    for name, color in COLORS.items():
        rows = []
        for y in range(16):
            row = []
            for x in range(16):
                shade = ((x*17+y*31+x*y*3) % 9)-4
                if name == 'wood': shade += -9 if x % 5 == 0 else 0
                if name == 'stone': shade += -10 if y % 6 == 0 else 0
                row.append(tuple(max(0, min(255, c+shade)) for c in color)+(255,))
            rows.append(row)
        out[base/f'assets/myvillage/textures/block/workshop_{name}.png'] = png(rows)
    for lit in (False, True):
        add('assets/myvillage/models/block/forging_anvil'+('_lit' if lit else '')+'.json', station(lit))
    add('assets/myvillage/blockstates/forging_anvil.json', {'variants': {
        f'facing={d},lit={str(lit).lower()}': {'model': 'myvillage:block/forging_anvil'+('_lit' if lit else ''), 'y': angle}
        for lit in (False, True) for d, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
    add('assets/myvillage/models/block/workshop/hammer.json', hammer())
    for kind in KINDS:
        for stage in range(6):
            add(f'assets/myvillage/models/block/workshop/{kind}_{stage}.json', workpiece(kind, stage))
            if stage in (0, 1, 5):
                add(f'assets/myvillage/models/block/workshop/{kind}_{stage}_hot.json', workpiece(kind, stage, hot=True))
    return out
