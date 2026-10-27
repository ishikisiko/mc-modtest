"""Shared scroll support plus an emblem rendered from the actual implement mesh.

Quality affects the support only. Current patterns use the default presentation;
this module does not assign gameplay quality or alter learned recipes.
"""
from __future__ import annotations

import io
import copy
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from tools.combat_preview import item_model as im
from tools.equipment import mesh
from tools.forging.models import Catalogue, ROOT, palette_texture


def load(root=ROOT):
    config = json.loads((Path(root) / 'tools/forging/patterns.json').read_text())
    if config['version'] != 1 or config['texture_size'] % config['grid']:
        raise ValueError('器谱像素网格必须整倍缩放')
    if config['default_quality'] not in config['qualities']:
        raise ValueError('未知器谱边饰')
    return config


def color(value):
    return '#' + value


def support(config, quality=None):
    """Fresh, item-independent scroll. Coordinates are authored on the pixel grid."""
    quality = quality or config['default_quality']
    if quality not in config['qualities']:
        raise ValueError('未知器谱边饰: ' + quality)
    inks = {k: color(v) for k, v in config['palette'].items()}
    trim = {k: color(v) for k, v in config['qualities'][quality].items() if k != 'name'}
    image = Image.new('RGBA', (config['grid'], config['grid']))
    draw = ImageDraw.Draw(image)
    x0, y0, x1, y1 = config['layout']['paper_box']
    draw.rectangle((x0, y0, x1, y1), fill=inks['outline'])
    draw.rectangle((x0+1, y0, x1-1, y1), fill=inks['paper_edge'])
    draw.rectangle((x0+2, y0+1, x1-2, y1-1), fill=inks['paper'])
    draw.line((x0+2, y0+2, x0+2, y1-2), fill=inks['paper_light'])
    draw.line((x1-2, y0+2, x1-2, y1-2), fill=inks['paper_shadow'])
    # Two small corner folds, not fine all-over paper noise.
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
        for x in (4,25):
            draw.rectangle((x,y,x+2,y+3), fill=trim['dark'])
            draw.line((x+1,y,x+1,y+2), fill=trim['base'])
            draw.point((x+1,y), fill=trim['light'])
    sx0, sy0, sx1, sy1 = config['layout']['seal_box']
    draw.rectangle((sx0,sy0,sx1,sy1), fill=inks['seal'])
    draw.point((sx0,sy0+1), fill=inks['seal_ink'])
    return image


class PatternArt:
    def __init__(self, root=ROOT, catalogue=None):
        self.root = Path(root)
        self.config = load(self.root)
        self.catalogue = catalogue or Catalogue(self.root)
        self.textures = {}
        self.emblems = {}
        self.generated_textures = dict(self.catalogue.material_outputs)
        for name, palette in self.catalogue.authored['palette'].items():
            self.generated_textures[f'assets/myvillage/textures/equipment/forging/{name}.png'] = palette_texture(name, palette)

    def texture(self, ref):
        if ref not in self.textures:
            namespace, path = ref.split(':', 1)
            rel = f'assets/{namespace}/textures/{path}.png'
            data = self.generated_textures.get(rel)
            if data is None:
                data = (self.root / 'src/main/resources' / rel).read_bytes()
            self.textures[ref] = np.asarray(Image.open(io.BytesIO(data)).convert('RGBA'), dtype=float) / 255
        return self.textures[ref]

    def emblem(self, recipe):
        if recipe not in self.emblems:
            faces = self.catalogue.sources[recipe]
            view = self.config['views'].get(self.catalogue.kinds[recipe], {})
            if view.get('pair_gap'):
                # Arrange the two existing leg/foot parts as a readable paired emblem.
                # Only this paper illustration moves them; worn/item geometry stays untouched.
                spec = json.loads((self.root / 'equipment' / self.catalogue.config['armor']['set'] / 'equipment.json').read_text())
                item = next(i for i in spec['items'] if i['id'] == 'myvillage:' + recipe)
                bones = {b['name']: b['bone'] for b in item['boxes']}
                faces = copy.deepcopy(faces)
                groups = {bone: [f for f in faces if bones[f['part']] == bone] for bone in ('left_leg','right_leg')}
                centers = {bone: sum(mesh.bounds(fs)[side][0] for side in (0,1))/2 for bone,fs in groups.items()}
                middle = sum(centers.values())/2
                for bone, group in groups.items():
                    shift = view['pair_gap']/2 * (1 if centers[bone] > middle else -1)
                    for face in group:
                        face['points'] = [[x+shift,y,z] for x,y,z in face['points']]
            quads = [im.Quad(f['points'], uv=[[u*16,v*16] for u,v in f['uv']], tex=self.texture(f['texture'])) for f in faces]
            rotation, extent = mesh.gui_profile(self.catalogue.armor(recipe), self.root)
            display = mesh.gui_transform(faces, view.get('rotation',rotation), extent)
            quads = im.transform_quads(quads, im.display_matrix(display))
            camera = im.Camera((0,0,1))
            rendered = im.render(quads, camera, 128, margin=.02, bg=(0,0,0), ss=1, cull=False)[0]
            masks = [im.Quad(q.pos, color=(1,1,1), shade=False) for q in quads]
            alpha = np.asarray(im.render(masks, camera, 128, margin=.02, bg=(0,0,0), ss=1, cull=False)[0])[:,:,0]
            rgba = Image.fromarray(np.dstack((np.asarray(rendered), alpha)))
            bounds = rgba.getbbox()
            if bounds is None:
                raise ValueError('空器物图案: ' + recipe)
            self.emblems[recipe] = rgba.crop(bounds)
        return self.emblems[recipe].copy()

    def icon(self, recipe, quality=None):
        image = support(self.config, quality)
        x0, y0, x1, y1 = self.config['layout']['emblem_box']
        emblem = self.emblem(recipe)
        emblem.thumbnail((x1-x0, y1-y0), Image.Resampling.LANCZOS)
        at = (x0+(x1-x0-emblem.width)//2, y0+(y1-y0-emblem.height)//2)
        image.alpha_composite(emblem, at)
        size = self.config['texture_size']
        return image.resize((size,size), Image.Resampling.NEAREST)


def outputs(root=ROOT, catalogue=None):
    art = PatternArt(root, catalogue)
    base = Path(root) / 'src/main/resources/assets/myvillage'
    rules = json.loads((Path(root) / 'src/main/resources/data/myvillage/forging/rules.json').read_text())
    result = {}
    for recipe in rules['recipes']:
        namespace, item = recipe['pattern'].split(':', 1)
        if namespace != 'myvillage':
            raise ValueError('器谱 namespace 不匹配')
        image = art.icon(recipe['id'])
        out = io.BytesIO(); image.save(out, format='PNG')
        result[base / f'textures/item/{item}.png'] = out.getvalue()
        result[base / f'models/item/{item}.json'] = (json.dumps({
            'parent': 'minecraft:item/generated', 'textures': {'layer0': f'myvillage:item/{item}'}
        }, ensure_ascii=False, indent=2) + '\n').encode()
    return result
