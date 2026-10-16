"""Self-contained, state-aware color previews for authored Yunshan buildings."""
from __future__ import annotations
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def model_payload(blocks):
    """Read existing mod model elements without changing their owner assets."""
    models = {}
    for state in set(blocks.values()):
        name = state.split('[')[0]
        if not name.startswith('myvillage:'):
            continue
        model_file = ROOT/'src/main/resources/assets/myvillage/models/block'/f'{name.split(":")[1]}.json'
        model = json.loads(model_file.read_text())
        assert model.get('elements'), f'no model elements: {model_file}'
        models[name] = {'elements': model['elements'], 'textures': model.get('textures',{})}
    return models


def render_viewer(data, blocks, out):
    payload = {'id':data['id'].split(':')[1], 'size':data['size'],
               'blocks':[[*pos,state] for pos,state in blocks.items() if state!='minecraft:air'],
               'models':model_payload(blocks), 'signs':data['signs'],
               'site':data['site'], 'presentation':data['presentation'],
               'services':data.get('service_blocks',[])}
    template = (ROOT/'tools/yunshan/building_viewer.html').read_text()
    html = template.replace('/*DATA*/', json.dumps(payload,ensure_ascii=False))
    html = html.replace('/*THREE*/',(ROOT/'tools/web/three.min.js').read_text())
    html = html.replace('/*ORBIT*/',(ROOT/'tools/web/OrbitControls.js').read_text())
    (out/'index.html').write_text(html)
