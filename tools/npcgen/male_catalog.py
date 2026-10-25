"""男修组合配置与稳定人物选择；正式生成不依赖预览目录。"""
from __future__ import annotations
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = 'tools/npcgen/male_variants.json'
FACTORY = 'tools/npcgen/male.py'


def load(root=ROOT):
    data = json.loads((Path(root) / SOURCE).read_text(encoding='utf-8'))
    if data['version'] != 1 or not data['selection_salt']:
        raise ValueError('无效的男修目录版本或身份盐')
    if set(data['faces']) != set(data['hair']) or set(data['faces']) != {'01','02','03','04','05'}:
        raise ValueError('男修必须保留五张脸与五种发型')
    if set(data['outfits']) != {'A','B','C','legacy'}:
        raise ValueError('男修必须保留四套衣装')
    for face in data['faces'].values():
        if len(face['pixels']) != face['height'] or any(len(row) != face['width'] or set(row)-set('.BLWCDGM') for row in face['pixels']):
            raise ValueError('无效男修脸部画布')
    for hair in data['hair'].values():
        if len(hair['fringe']) != hair['width'] or any(not 0 < n <= hair['height'] for n in hair['fringe']):
            raise ValueError('无效男修刘海')
    expected = {(f,h,o) for f in data['faces'] for h in data['hair'] for o in data['outfits']}
    seen = set()
    for v in data['variants']:
        key = (v['face'],v['hair'],v['outfit'])
        if key not in expected or key in seen or v['look'] != f'm_{key[0]}_{key[1]}_{key[2].lower()}':
            raise ValueError(f'重复或无效的男修组合：{v}')
        seen.add(key)
    if seen != expected:
        raise ValueError('男修组合目录不完整')
    return data


def variants(root=ROOT):
    return load(root)['variants']


def definitions(root=ROOT):
    return tuple('cultivator_'+v['look'] for v in variants(root))


def for_identity(person_id, birth_day):
    from .catalog import select_identity
    data = load()
    return select_identity(data['variants'], data['selection_salt'], person_id, birth_day)
