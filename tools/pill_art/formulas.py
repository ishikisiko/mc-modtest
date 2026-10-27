"""Alchemy scrolls use the actual recipe output and fixed-quality contract."""
import json
from pathlib import Path
from PIL import Image

from tools import scroll_art
from tools.pill_art.render import pill_icon

ROOT = Path(__file__).resolve().parents[2]


def recipe(kind, root=ROOT):
    rules = json.loads((Path(root) / 'src/main/resources/data/myvillage/alchemy/rules.json').read_text())
    matches = [r for r in rules['recipes'] if r['formula'] == 'myvillage:formula_' + kind]
    if len(matches) != 1:
        raise ValueError('丹方必须唯一对应实际配方: ' + kind)
    return matches[0]


def icon(kind, root=ROOT):
    entry = recipe(kind, root)
    if not entry['output'].startswith('myvillage:pill_'):
        raise ValueError('丹方产物不是已定义的丹药')
    rows = pill_icon(entry['output'].removeprefix('myvillage:pill_'))
    body = Image.new('RGBA', (len(rows[0]),len(rows)))
    body.putdata([pixel for row in rows for pixel in row])
    config = scroll_art.load(root)
    quality = config['quality_levels'][str(entry.get('fixed_quality',0))]
    return scroll_art.compose(body, config, quality, style='alchemy')


def rows(kind, root=ROOT):
    image = icon(kind, root)
    return [[image.getpixel((x,y)) for x in range(image.width)] for y in range(image.height)]
