#!/usr/bin/env python3
"""Publish H-panel icon originals and shared category symbols; never modify generated resources by hand."""
from pathlib import Path
import argparse
import io
import json
import sys
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'tools/cultivation_panel'
ASSETS = ROOT / 'src/main/resources/assets/myvillage'


def png(image):
    out = io.BytesIO()
    image.save(out, format='PNG', optimize=True, compress_level=9)
    return out.getvalue()


def symbol(name, size):
    # Authored compact silhouettes. These encode broad categories only, not technique families.
    scale = 4
    image = Image.new('RGBA', (size * scale, size * scale))
    d = ImageDraw.Draw(image)
    k = size * scale / 72
    ink = (49, 77, 58, 255)
    soft = (91, 116, 85, 230)
    paper = (233, 226, 205, 255)
    def pts(values):
        return [(round(x*k), round(y*k)) for x,y in values]
    def line(values, width=3, color=ink):
        d.line(pts(values), fill=color, width=round(width*k), joint='curve')
    def poly(values, color=ink):
        d.polygon(pts(values), fill=color)
    def ellipse(box, color=ink):
        d.ellipse(tuple(round(v*k) for v in box), fill=color)
    if name in ('core', 'meditation'):
        d.arc(tuple(round(v*k) for v in (9,9,63,63)), 35, 335, fill=ink, width=round(5*k))
        d.arc(tuple(round(v*k) for v in (20,20,52,52)), 190, 470, fill=soft, width=round(4*k))
        ellipse((31,31,41,41))
    elif name in ('active', 'skills'):
        poly([(16,56),(47,16),(60,7),(56,25),(25,63)])
        line([(17,44),(36,58)],4)
        line([(21,59),(12,68)],4)
        line([(27,50),(52,18)],1,paper)
    elif name == 'movement':
        ellipse((9,25,35,46),soft); ellipse((26,14,53,45),soft); ellipse((45,27,65,48),soft)
        poly([(20,36),(57,36),(57,48),(20,48)],soft)
        line([(12,55),(49,55)],4); line([(25,62),(60,62)],3)
    elif name == 'body':
        poly([(23,20),(34,17),(48,51),(37,56)])
        for box in [(15,10,29,25),(26,7,39,22),(33,48,47,62),(43,45,57,59)]: ellipse(box)
    elif name == 'profile':
        ellipse((26,8,46,28)); poly([(22,32),(50,32),(62,61),(36,68),(10,61)],soft)
        line([(25,33),(36,48),(47,33)],2,paper); line([(36,48),(36,62)],2,paper)
    elif name == 'techniques':
        poly([(16,12),(56,12),(56,60),(16,60)],soft)
        line([(12,9),(60,9)],5); line([(12,64),(60,64)],5)
        for y in (24,34,44): line([(25,y),(47,y)],2,paper)
    elif name == 'world':
        poly([(8,17),(25,9),(47,17),(64,10),(64,57),(47,65),(25,57),(8,65)],soft)
        line([(25,13),(25,54)],2,paper); line([(47,21),(47,60)],2,paper)
        line([(14,42),(24,30),(36,43),(46,26),(57,35)],2,paper)
    elif name == 'fates':
        poly([(36,6),(43,28),(65,36),(44,43),(36,66),(28,43),(6,36),(28,28)])
        d.ellipse(tuple(round(v*k) for v in (11,11,61,61)),outline=soft,width=round(2*k))
    elif name in ('crafting','alchemy'):
        poly([(14,29),(59,29),(54,51),(36,60),(19,51)])
        line([(11,24),(62,24)],4); line([(23,17),(36,12),(51,17)],4)
        line([(22,52),(18,65)],4); line([(50,52),(55,65)],4)
        poly([(36,35),(42,44),(36,51),(30,44)],paper)
    elif name == 'forging':
        poly([(40,7),(65,26),(50,44),(25,25)])
        line([(36,35),(13,64)],9)
        line([(9,50),(3,44)],3,soft);line([(19,37),(16,29)],3,soft)
    else:
        poly([(36,7),(64,36),(36,65),(8,36)],soft)
        ellipse((32,21,40,39),paper); ellipse((33,47,39,53),paper)
    return image.resize((size,size), Image.Resampling.LANCZOS)


def outputs():
    style = json.loads((SOURCE/'style.json').read_text())
    size = style['icon_size']; category_size = style['category_size']
    if size not in (128,256) or category_size != 128:
        raise ValueError('Icon export sizes must stay bounded')
    if len(style['grades']) != 5:
        raise ValueError('Five explicit grade palettes are required')
    for grade in style['grades']:
        for value in grade.values():
            if len(value) != 6: raise ValueError('Expected six-digit RGB')
            int(value,16)
    result = {}
    runtime = dict(style)
    runtime['technique_icons'] = {}
    runtime['categories'] = {}
    runtime['navigation'] = {}
    base = ASSETS/'textures/gui/cultivation/panel'
    for ident, name in style['technique_icons'].items():
        if '/' in name or '..' in name or not ident.startswith('myvillage:'):
            raise ValueError('Invalid icon path or technique id')
        original = SOURCE/'source'/f'{name}.png'
        with Image.open(original) as image:
            if image.mode != 'RGBA' or image.getchannel('A').getextrema() != (0,255):
                raise ValueError(f'Icon must preserve real alpha: {original}')
            # Premultiplied-alpha resampling avoids dark fringes around translucent ink.
            resized = image.convert('RGBa').resize((size,size),Image.Resampling.LANCZOS).convert('RGBA')
            result[base/f'{name}.png'] = png(resized)
        runtime['technique_icons'][ident] = f'myvillage:textures/gui/cultivation/panel/{name}.png'
    for group in ('categories','navigation'):
        for name in style[group]:
            stem = ('category_' if group=='categories' else 'nav_')+name
            result[base/f'{stem}.png'] = png(symbol(name,category_size))
            runtime[group][name] = f'myvillage:textures/gui/cultivation/panel/{stem}.png'
    for path in list(result):
        result[Path(str(path)+'.mcmeta')] = b'{"texture":{"blur":true,"clamp":true}}\n'
    result[ASSETS/'cultivation_panel.json'] = (json.dumps(runtime,ensure_ascii=False,indent=2)+'\n').encode()
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    stale=[]
    for path,data in outputs().items():
        if args.check and path.suffix == '.png' and path.exists():
            # PNG compression may differ across the local/cloud zlib packages; pixel ownership must not.
            with Image.open(path) as actual, Image.open(io.BytesIO(data)) as expected:
                if actual.mode == expected.mode and actual.size == expected.size and actual.tobytes() == expected.tobytes():
                    continue
        if not path.exists() or path.read_bytes()!=data:
            stale.append(str(path.relative_to(ROOT)))
            if not args.check:
                path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    print('H panel assets: '+('stale: '+', '.join(stale) if args.check and stale else 'verified' if args.check else 'generated'))
    return int(args.check and bool(stale))

if __name__=='__main__':raise SystemExit(main())
