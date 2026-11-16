"""由材料目录生成境界×稀有度物品；掉落概率另由统一掉落表持有。"""
from __future__ import annotations

import argparse
import io
import json
from pathlib import Path

from PIL import Image, ImageDraw

from tools.language_assets import language_text, load_language

ROOT = Path(__file__).resolve().parents[2]


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def catalogue(root=ROOT):
    data = json.loads((root / 'tools/beast_materials/catalogue.json').read_text())
    if data['schema'] != 1 or data['realm_multiplier'] < 1:
        raise ValueError('材料目录版本或境界倍率错误')
    for key in ('materials', 'realms', 'rarities'):
        ids = [v['id'] for v in data[key]]
        if len(set(ids)) != len(ids) or not ids:
            raise ValueError('材料目录重复或为空：' + key)
    if [v['id'] for v in data['rarities']] != ['common', 'uncommon', 'rare', 'legendary']:
        raise ValueError('材料稀有度顺序错误')
    if data['fallback_realm'] not in [v['id'] for v in data['realms']]:
        raise ValueError('缺少默认境界')
    return data


def variants(data):
    realms = [v['id'] for v in data['realms']]
    for material in data['materials']:
        first = realms.index(material['minimum_realm'])
        for tier, realm in enumerate(data['realms']):
            if tier < first:
                continue
            for rarity in data['rarities']:
                name = '_'.join((material['id'], realm['id'], rarity['id']))
                price = material['base_price'] * data['realm_multiplier'] ** tier * rarity['price_multiplier']
                if not 1 <= price <= 100_000:
                    raise ValueError('回收单价超出交易范围：' + name)
                yield dict(id='yuanxian:' + name, family=material['id'], realm='yuanxian:' + realm['id'],
                           rarity=rarity['id'], price=price, color=int(rarity['color'], 16)), material, realm, rarity


def icon(shape, tint, rarity, tier):
    """32像素原画：完整轮廓、材质纹理、境界纹路与稀有度角标。"""
    im = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(im)
    base = tuple(bytes.fromhex(tint))
    def shade(amount):
        return tuple(max(0, min(255, c + amount)) for c in base) + (255,)
    dark, mid, light = shade(-65), shade(-12), shade(42)
    if shape == 'hide':
        outline = [(8, 3), (12, 5), (20, 5), (24, 3), (28, 7), (25, 12), (26, 21),
                   (29, 26), (24, 29), (19, 26), (13, 26), (8, 29), (3, 26), (6, 21), (7, 12), (4, 7)]
        d.polygon(outline, fill=dark)
        d.polygon([(9, 6), (13, 8), (19, 8), (23, 6), (25, 8), (22, 13), (23, 22),
                   (26, 25), (24, 26), (19, 23), (12, 23), (8, 26), (6, 25), (9, 21), (10, 13), (7, 8)], fill=mid)
        d.line([(12, 10), (11, 17), (12, 22)], fill=light, width=2)
        d.line([(20, 10), (21, 17), (20, 22)], fill=shade(-35))
        for x, y in [(15, 10), (17, 13), (14, 16), (18, 18), (15, 21)]:
            d.line((x, y, x + 1, y + 1), fill=shade(15))
    elif shape == 'bone':
        d.polygon([(5, 3), (9, 3), (12, 6), (12, 10), (23, 21), (27, 20), (30, 23),
                   (30, 27), (27, 30), (23, 30), (21, 27), (10, 16), (6, 16), (3, 13), (3, 9), (5, 7)], fill=dark)
        d.polygon([(6, 5), (9, 5), (10, 8), (9, 11), (24, 25), (26, 23), (28, 24),
                   (28, 27), (26, 28), (24, 27), (11, 13), (7, 14), (5, 12), (5, 10), (7, 8)], fill=shade(30))
        d.line((10, 10, 24, 25), fill=shade(65), width=2)
        d.line((8, 12, 22, 26), fill=mid)
        d.line((16, 15, 18, 19), fill=shade(-30))
    elif shape == 'core':
        d.polygon([(11, 3), (21, 3), (27, 9), (29, 15), (27, 23), (21, 29),
                   (11, 29), (5, 23), (3, 16), (5, 9)], fill=dark)
        d.polygon([(11, 5), (20, 5), (25, 10), (27, 16), (24, 23), (20, 26),
                   (11, 26), (7, 22), (5, 16), (7, 10)], fill=mid)
        d.polygon([(10, 8), (18, 6), (23, 10), (22, 15), (16, 18), (10, 16), (8, 12)], fill=shade(25))
        d.line([(19, 9), (14, 12), (19, 17), (13, 22)], fill=shade(75), width=2)
        d.line([(11, 10), (8, 15), (11, 20)], fill=shade(45))
        d.rectangle((10, 8, 12, 9), fill=shade(90))
    else:
        raise ValueError('未知材料形状：' + shape)
    # 角标不覆盖物品主体；数量随境界增加，颜色取材料稀有度。
    accent = tuple(bytes.fromhex(rarity)) + (255,)
    for i in range(tier + 1):
        x = 2 + i * 3
        d.rectangle((x, 30, x + 1, 31), fill=accent)
    blob = io.BytesIO()
    im.save(blob, format='PNG')
    return blob.getvalue()


def outputs(root=ROOT):
    data = catalogue(root)
    rows = list(variants(data))
    base = root / 'src/main/resources'
    out = {}
    language = base / 'assets/yuanxian/lang/zh_cn.json'
    original = load_language(language.read_text())
    lang = dict(original)
    for variant, material, realm, rarity in rows:
        id = variant['id'].split(':')[1]
        lang['item.yuanxian.' + id] = realm['name'] + '·' + rarity['name'] + material['name']
        out[base / f'assets/yuanxian/models/item/{id}.json'] = encoded({
            'parent': 'minecraft:item/generated', 'textures': {'layer0': 'yuanxian:item/' + id}})
        out[base / f'assets/yuanxian/textures/item/{id}.png'] = icon(
            material['shape'], realm['tint'], rarity['color'], data['realms'].index(realm))
    lang.update({
        'tooltip.yuanxian.beast_material.recycle': '城镇铁匠持续收购：每件 %s 枚下品灵石',
        'tooltip.yuanxian.beast_material.use': '可携带到城镇铁匠处，选择“卖出”批量回收。',
        'screen.yuanxian.commerce.batch_detail_recycling': '总价 %s 枚；持续收购',
    })
    out[language] = language_text(original, lang).encode()
    out[base / 'data/yuanxian/beast_materials/materials.json'] = encoded({
        'schema': data['schema'], 'fallback_realm': 'yuanxian:' + data['fallback_realm'],
        'rarities': [r['id'] for r in data['rarities']], 'variants': [v for v, *_ in rows]})
    for material in data['materials']:
        out[base / f'data/yuanxian/tags/item/{material["id"]}.json'] = encoded({
            'replace': False, 'values': [v['id'] for v, *_ in rows if v['family'] == material['id']]})
    return out


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--check', action='store_true')
    args = p.parse_args()
    stale = []
    for path, blob in outputs().items():
        if args.check:
            if not path.is_file() or path.read_bytes() != blob:
                stale.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(blob)
    if stale:
        print('\n'.join(stale))
        return 1
    print('妖兽材料：' + ('生成结果一致' if args.check else '已生成'))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
