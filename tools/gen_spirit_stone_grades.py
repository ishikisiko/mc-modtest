#!/usr/bin/env python3
"""Pack approved imagegen spirit-stone art; denomination JSON is the authored grade authority."""
import argparse
import io
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'tools/art/spirit_stones'
NAMES = {'medium_grade': '中品灵石', 'high_grade': '上品灵石'}


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def outputs(root=ROOT):
    base = root / 'src/main/resources'
    tiers = json.loads((base / 'data/myvillage/spirit_stones/denominations.json').read_text())['tiers']
    out = {}
    lang_path = base / 'assets/myvillage/lang/zh_cn.json'
    lang = json.loads(lang_path.read_text())
    for tier in tiers[1:]:
        grade = tier['id'].split(':')[1]
        item = tier['item'].split(':')[1]
        # Only resample/pad the approved source; no hand-painted generated output.
        source = Image.open(root / 'tools/art/spirit_stones' / f'{grade}.png').convert('RGBA')
        source = source.crop(source.getchannel('A').getbbox())
        source.thumbnail((56, 58), Image.Resampling.LANCZOS)
        texture = Image.new('RGBA', (64, 64))
        texture.paste(source, ((64-source.width)//2, (64-source.height)//2))
        blob = io.BytesIO()
        texture.save(blob, format='PNG')
        out[base / f'assets/myvillage/textures/item/{item}.png'] = blob.getvalue()
        out[base / f'assets/myvillage/models/item/{item}.json'] = encoded({
            'parent': 'minecraft:item/generated', 'textures': {'layer0': f'myvillage:item/{item}'}})
        out[base / f'data/c/tags/item/spirit_stones/{grade}.json'] = encoded({'replace': False, 'values': [tier['item']]})
        lang[f'item.myvillage.{item}'] = NAMES[grade]
        lang[f'tag.item.c.spirit_stones.{grade}'] = NAMES[grade]
    out[base / 'data/c/tags/item/spirit_stones.json'] = encoded({'replace': False, 'values': ['#'+t['tag'] for t in tiers]})
    lang['tooltip.myvillage.spirit_stone_bag.grade'] = '%s：%s 枚'
    lang['tooltip.myvillage.spirit_stone_bag.exchange'] = '输入 /灵石 查看余额、兑换和拆分用法。'
    out[lang_path] = encoded(lang)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    stale = []
    for path, data in outputs().items():
        if args.check:
            if not path.exists() or path.read_bytes() != data:
                stale.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    if stale:
        print('\n'.join(stale))
        return 1
    print('Spirit stone grades: ' + ('match' if args.check else 'generated'))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
