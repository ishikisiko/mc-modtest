"""Resource checks independent of the renderer and without per-item Java name lists."""
from __future__ import annotations

import json
from pathlib import Path

from .catalogue import ROOT, load


def validate_resources(root: Path=ROOT):
    errors=[]
    specs,_=load(root)
    resources=root/"src/main/resources"
    path=resources/"data/myvillage/equipment/catalogue.json"
    if not path.is_file():return ["缺少生成的装备 catalogue"]
    generated=json.loads(path.read_text())
    expected={i['id']:i for s in specs if s['kind']=='armor_set' and s.get('enabled',True) for i in s['items']}
    entries=generated.get('items',[])
    actual={i['id']:i for i in entries}
    if len(actual)!=len(entries) or set(actual)!=set(expected):errors.append("装备目录身份不一致或重复")
    names=json.loads((resources/"assets/myvillage/lang/zh_cn.json").read_text())
    for item_id,item in actual.items():
        ns,name=item_id.split(':')
        for ref in (item['model'],item['texture']):
            namespace,rel=ref.split(':')
            if not (resources/'assets'/namespace/rel).is_file():errors.append("缺少装备资源 "+ref)
        for rel in (f'assets/{ns}/models/item/{name}.json',f'assets/{ns}/textures/item/{name}.png'):
            if not (resources/rel).is_file():errors.append("缺少 "+rel)
        if item_id in expected and names.get(f'item.{ns}.{name}')!=expected[item_id]['name']:
            errors.append("装备中文名称不一致 "+item_id)
        if item_id in expected and item['slot']!=expected[item_id]['slot']:
            errors.append("装备槽位不一致 "+item_id)
    for spec in specs:
        if spec['kind']=='weapon_reference':
            for path in (root/spec['generator'],resources/spec['geometry']):
                if not path.is_file():errors.append("缺少既有武器引用 "+str(path.relative_to(root)))
            ns,name=spec['model'].split(':')
            if not (resources/'assets'/ns/'models'/(name+'.json')).is_file():errors.append("缺少武器模型 "+spec['model'])
    return errors
