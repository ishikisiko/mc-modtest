"""Export the authored partial-block shapes and shades used in the sect courts."""
from __future__ import annotations

import gzip
import json
from pathlib import Path

from lord_manors.generate import model
from province_capitals.regional.v2.common import write_json


BANK_SIZE = 32


def block_state(index):
    return f'yuanxian:sect_detail_{index // BANK_SIZE:03d}[shape={index % BANK_SIZE}]'


def surface_model(boxes, materials):
    definitions = {key: {'id': key, 'role': key, 'opaque': row['opaque']}
                   for key, row in materials.items()}
    result = model(boxes, definitions, 'sect')
    for component in (result, *result.get('children', {}).values()):
        textures = component['textures'] = dict(component['textures'])
        for key in textures.keys() - {'particle'}:
            textures[key] = materials[key]['side']
        textures['particle'] = next(value for key, value in textures.items() if key != 'particle')
        for element in component.get('elements', ()):
            for face in ('up', 'down'):
                data = element['faces'].get(face)
                if data is None:
                    continue
                key = data['texture'][1:]
                if 'end' in materials[key]:
                    textures[key + '_end'] = materials[key]['end']
                    data['texture'] = '#' + key + '_end'
    return result


def physics(boxes, materials):
    return {
        'boxes': [box[:6] for box in boxes],
        'collision': [box[:6] for box in boxes if materials[box[6]]['collides']],
        'occlusion': [box[:6] for box in boxes if materials[box[6]]['opaque']],
        'translucent': {},
        'light': max(materials[box[6]].get('light', 0) for box in boxes),
    }


def export_court_assets(source: Path, out: Path):
    with gzip.open(source/'court_components.json.gz', 'rt', encoding='utf-8') as stream:
        authored = json.load(stream)
    materials, profiles = authored['materials'], authored['profiles']
    if not profiles or len(profiles) > 16384:
        raise ValueError('宗门雕构目录超出范围')
    assets = out/'assets/yuanxian'
    for path in sorted((source/'court_materials').glob('*.png')):
        target = assets/'textures/block/sect_courts'/path.name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(path.read_bytes())
    banks = []
    for index, boxes in enumerate(profiles):
        bank, shape = divmod(index, BANK_SIZE)
        if shape == 0:
            name = f'sect_detail_{bank:03d}'
            banks.append({'id': name, 'shapes': []})
        if not 1 <= len(boxes) <= 128:
            raise ValueError(f'宗门雕构复杂度无效 {index}')
        banks[-1]['shapes'].append(physics(boxes, materials))
        write_json(assets/f'models/block/sect_courts/{bank:03d}/{shape}.json',
                   surface_model(boxes, materials))
    for bank, row in enumerate(banks):
        variants = {f'shape={shape}': {'model': f'yuanxian:block/sect_courts/{bank:03d}/{shape}'}
                    for shape in range(len(row['shapes']))}
        # Each bank uses the same state property. Pad the last bank with its first valid shape.
        variants.update((f'shape={i}', variants['shape=0']) for i in range(len(variants), BANK_SIZE))
        write_json(assets/f'blockstates/{row["id"]}.json', {'variants': variants})
        row['shapes'].extend([row['shapes'][0]]*(BANK_SIZE-len(row['shapes'])))
    catalog = json.dumps(banks, ensure_ascii=False, separators=(',', ':')).encode()
    (out/'data/yuanxian/sect_blueprints/court_components.json.gz').write_bytes(gzip.compress(catalog, mtime=0))
    lift_models(assets)


def lift_models(assets):
    materials = {
        'body': {'side': 'yuanxian:block/ingot_blocks/qi_moqing_side', 'opaque': True},
        'cap': {'side': 'yuanxian:block/ingot_blocks/zhuji_baiqing_lingyun_side', 'opaque': True},
        'gold': {'side': 'yuanxian:block/ingot_blocks/huashen_dari_side', 'opaque': True},
    }
    shapes = {
        'sect_lift_terminal': [(0,0,0,16,13,16,'body'),(2,12,2,14,16,14,'cap'), (5,15,5,11,16,11,'gold')],
        'sect_lift_gate': [(0,3,6,16,6,10,'body'),(0,12,6,16,15,10,'body'),
                           (1,0,7,4,15,9,'cap'),(12,0,7,15,15,9,'cap')],
    }
    for name, boxes in shapes.items():
        write_json(assets/f'models/block/{name}.json', surface_model(boxes, materials))
    write_json(assets/'models/block/sect_lift_gate_open.json',
               {'parent':'minecraft:block/block','textures':{'particle':materials['body']['side']}})
    rotations = {'north':0,'east':90,'south':180,'west':270}
    write_json(assets/'blockstates/sect_lift_terminal.json',
               {'variants':{'':{'model':'yuanxian:block/sect_lift_terminal'}}})
    write_json(assets/'blockstates/sect_lift_gate.json',{'variants':{
        f'facing={face},open=false':{'model':'yuanxian:block/sect_lift_gate','y':turn}
        for face,turn in rotations.items()
    } | {'open=true':{'model':'yuanxian:block/sect_lift_gate_open'}}})
