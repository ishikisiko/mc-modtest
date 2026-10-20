"""淬火水槽资源：深木外槽、石衬、铜箍与可辨认的水面。"""
import json
from tools.forging.anvil import box, model
from tools.gen_alchemy_assets import png


def trough(filled):
    p = []
    def b(name, lo, hi, texture): box(p, name, lo, hi, texture)
    for x in (1, 12):
        b('石足', [x, 0, 2], [x+3, 2, 14], 'stone')
    b('槽底', [0, 2, 2], [16, 4, 14], 'stone')
    b('前槽壁', [0, 4, 2], [16, 10, 4], 'wood')
    b('后槽壁', [0, 4, 12], [16, 10, 14], 'wood')
    b('左槽壁', [0, 4, 4], [2, 10, 12], 'wood')
    b('右槽壁', [14, 4, 4], [16, 10, 12], 'wood')
    b('前石衬', [2, 4, 3.75], [14, 9.6, 4.25], 'stone')
    b('后石衬', [2, 4, 11.75], [14, 9.6, 12.25], 'stone')
    b('前槽沿', [0, 10, 2], [16, 11, 4], 'dark')
    b('后槽沿', [0, 10, 12], [16, 11, 14], 'dark')
    b('左槽沿', [0, 10, 4], [2, 11, 12], 'dark')
    b('右槽沿', [14, 10, 4], [16, 11, 12], 'dark')
    for x in (2.5, 12.5):
        for z in (1.9, 14):
            b('铜箍', [x, 2, z], [x+.7, 10.1, z+.1], 'copper')
            b('铆钉', [x+.15, 7.8, z-.06], [x+.55, 8.25, z+.16], 'iron')
    if filled:
        b('水面', [2.01, 8.5, 4.26], [13.99, 8.55, 11.74], 'water')
    result = model(p)
    result['textures']['water'] = 'myvillage:block/workshop_water'
    return result


def outputs(base):
    out = {}
    def add(path, obj): out[base/path] = (json.dumps(obj, ensure_ascii=False, indent=2)+'\n').encode()
    for filled in (False, True):
        add('assets/myvillage/models/block/quenching_trough'+('_filled' if filled else '')+'.json', trough(filled))
    add('assets/myvillage/blockstates/quenching_trough.json', {'variants': {
        f'facing={direction},filled={str(filled).lower()}': {
            'model': 'myvillage:block/quenching_trough'+('_filled' if filled else ''), 'y': rotation}
        for filled in (False, True) for direction, rotation in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            shade = ((x*7+y*3) % 7)-3
            if (x+2*y) % 13 in (0, 1): shade += 13
            row.append((43+shade, 87+shade, 95+shade, 255))
        rows.append(row)
    out[base/'assets/myvillage/textures/block/workshop_water.png'] = png(rows)
    add('assets/myvillage/models/item/quenching_trough.json', {
        'parent': 'myvillage:block/quenching_trough',
        'display': {'gui': {'rotation': [25, 225, 0], 'scale': [.8, .8, .8]}}})
    add('data/myvillage/recipe/quenching_trough.json', {
        'type': 'minecraft:crafting_shaped', 'pattern': ['I I', 'WWW', 'SSS'],
        'key': {'I': {'item': 'minecraft:iron_ingot'}, 'W': {'tag': 'minecraft:planks'}, 'S': {'item': 'minecraft:cobblestone'}},
        'result': {'id': 'myvillage:quenching_trough'}})
    add('data/myvillage/loot_table/blocks/quenching_trough.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'myvillage:quenching_trough'}],
                                            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    return out
