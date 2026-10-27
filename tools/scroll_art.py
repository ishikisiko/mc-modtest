"""Reusable paper scroll support and centered illustration composition."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]


def load(root=ROOT):
    config = json.loads((Path(root) / 'tools/scroll_art.json').read_text())
    if config['version'] != 1 or config['texture_size'] % config['grid']:
        raise ValueError('卷轴像素网格必须整倍缩放')
    if config['default_quality'] not in config['qualities']:
        raise ValueError('未知卷轴边饰')
    return config


def color(value):
    return '#' + value


def support(config, quality=None, style='forging'):
    """Fresh, item-independent scroll. Coordinates are authored on the pixel grid."""
    quality = quality or config['default_quality']
    if quality not in config['qualities']:
        raise ValueError('未知卷轴边饰: ' + quality)
    variant = config['styles'][style]
    inks = {k: color(v) for k, v in (config['palette'] | variant.get('palette', {})).items()}
    trim = {k: color(v) for k, v in config['qualities'][quality].items() if k != 'name'}
    if quality == config['default_quality'] and 'neutral_trim' in variant:
        trim = {k: color(v) for k, v in variant['neutral_trim'].items()}
    image = Image.new('RGBA', (config['grid'], config['grid']))
    draw = ImageDraw.Draw(image)
    x0, y0, x1, y1 = config['layout']['paper_box']
    draw.rectangle((x0, y0, x1, y1), fill=inks['outline'])
    draw.rectangle((x0+1, y0, x1-1, y1), fill=inks['paper_edge'])
    draw.rectangle((x0+2, y0+1, x1-2, y1-1), fill=inks['paper'])
    draw.line((x0+2, y0+2, x0+2, y1-2), fill=inks['paper_light'])
    draw.line((x1-2, y0+2, x1-2, y1-2), fill=inks['paper_shadow'])
    # Two small corner folds, not fine all-over paper noise.
    if style == 'forging':
        draw.line([(x0+3,y0+2),(x0+5,y0+2),(x0+5,y0+3)], fill=inks['paper_shadow'])
        draw.line([(x1-3,y0+2),(x1-5,y0+2),(x1-5,y0+3)], fill=inks['paper_shadow'])
    for y in config['layout']['roll_y']:
        draw.rectangle((1,y+1,30,y+2), fill=inks['outline'])
        draw.rectangle((2,y,29,y+3), fill=inks['outline'])
        draw.rectangle((2,y+1,29,y+2), fill=inks['wood'])
        draw.point((2,y+1), fill=inks['wood_light'])
        draw.point((29,y+2), fill=inks['wood_dark'])
        draw.rectangle((5,y,26,y+3), fill=inks['paper_shadow'])
        draw.line((6,y,25,y), fill=inks['paper_light'])
        draw.line((6,y+1,25,y+1), fill=inks['paper'])
        draw.line((6,y+3,25,y+3), fill=inks['paper_edge'])
        if style == 'alchemy':
            draw.rectangle((5,y,26,y+3), fill=inks['wood'])
            draw.line((6,y,25,y), fill=inks['wood_light'])
            draw.line((6,y+3,25,y+3), fill=inks['wood_dark'])
            draw.line((16,y+1,16,y+3), fill=inks['wood_dark'])
            draw.point((17,y+1), fill=inks['wood_light'])
        for x in (4,25):
            draw.rectangle((x,y,x+2,y+3), fill=trim['dark'])
            draw.line((x+1,y,x+1,y+2), fill=trim['base'])
            draw.point((x+1,y), fill=trim['light'])
    if style == 'alchemy':
        # A short linen loop and a tiny herb sprig distinguish the silhouette.
        draw.line([(27,5),(28,7),(29,8),(29,10),(28,9)], fill=inks['wood_dark'])
        draw.line([(28,7),(28,9)], fill=inks['paper_light'])
        draw.line([(24,23),(25,25),(26,23)], fill=inks['seal'])
        draw.point((26,22), fill=inks['seal'])
    else:
        sx0, sy0, sx1, sy1 = config['layout']['seal_box']
        draw.rectangle((sx0,sy0,sx1,sy1), fill=inks['seal'])
        draw.point((sx0,sy0+1), fill=inks['seal_ink'])
    return image


def compose(emblem, config, quality=None, style='forging'):
    image = support(config, quality, style)
    x0,y0,x1,y1 = config['styles'][style].get('emblem_box', config['layout']['emblem_box'])
    emblem = emblem.convert('RGBA')
    bounds = emblem.getbbox()
    if bounds is None:
        raise ValueError('卷轴中央图案为空')
    emblem = emblem.crop(bounds)
    sampling = config['styles'][style].get('resample','lanczos')
    emblem.thumbnail((x1-x0,y1-y0), {'nearest':Image.Resampling.NEAREST, 'lanczos':Image.Resampling.LANCZOS}[sampling])
    image.alpha_composite(emblem, (x0+(x1-x0-emblem.width)//2, y0+(y1-y0-emblem.height)//2))
    size = config['texture_size']
    return image.resize((size,size), Image.Resampling.NEAREST)
