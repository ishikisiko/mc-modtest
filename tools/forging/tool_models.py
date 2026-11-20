"""Closed, thin tool profiles shared by the item, workpiece and workshop views."""
from __future__ import annotations

import io
import json
import math
from pathlib import Path

from PIL import Image
from tools.equipment import mesh

from .mesh_profiles import cross

PREFIX = 'yuanxian:equipment/forging/'
KINDS = {'pickaxe': '镐', 'axe': '斧', 'shovel': '铲', 'hoe': '锄'}
STAGE_MIX = {'formed': .44, 'quenched': .52, 'tempered': .46}


def added_tools(root):
    """The roster used by registration, workshop recipe assets and model generation."""
    root = Path(root)
    sets = json.loads((root / 'src/main/resources/data/yuanxian/forging/tool_sets.json').read_text())
    for material in sets:
        for kind, label in KINDS.items():
            yield f"{material['prefix']}_{kind}", kind, material['name'] + label, material


def turn(a, b, c):
    return (b[0]-a[0])*(c[1]-a[1]) - (b[1]-a[1])*(c[0]-a[0])


def triangulate(polygon):
    """Ear clipping keeps concave silhouettes and their front-face UV projection intact."""
    indices = list(range(len(polygon)))
    while len(indices) > 3:
        for n, j in enumerate(indices):
            i, k = indices[n-1], indices[(n+1) % len(indices)]
            a, b, c = (polygon[q] for q in (i, j, k))
            if turn(a, b, c) <= 1.e-9:
                continue
            if any(min(turn(a, b, polygon[q]), turn(b, c, polygon[q]), turn(c, a, polygon[q])) >= -1.e-9
                   for q in indices if q not in (i, j, k)):
                continue
            yield i, j, k
            indices.pop(n)
            break
        else:
            raise ValueError('无法封闭炼器工具轮廓')
    yield tuple(indices)


def profile_faces(polygon, x, depth, part, texture, side, uv, edge_uv, metal):
    polygon = [tuple(p) for p in polygon]
    area2 = sum(a[0]*b[1]-a[1]*b[0] for a,b in zip(polygon,polygon[1:]+polygon[:1]))
    if area2 < 0:
        polygon.reverse()
    triangles = list(triangulate(polygon))
    rings = [[(x+sign*depth/2,y,z) for z,y in polygon] for sign in (-1,1)]
    slot = 'metal' if metal else 'wood'

    # NeoForge's OBJ loader bakes quadrilaterals. Duplicating the fourth vertex preserves each cap triangle.
    for ring, sign in zip(rings, (-1, 1)):
        for a,b,c in triangles:
            ids = (a,b,c,c) if sign < 0 else (a,c,b,b)
            points = [ring[j] for j in ids]
            if math.hypot(*cross(*points[:3])) <= 1.e-8:
                raise ValueError(f'炼器工具轮廓含退化面：{part}')
            yield dict(part=part, points=points, uv=[uv(*polygon[j]) for j in ids],
                       texture=texture, slot=slot+'_face', metal=metal)
    low, high = rings
    for i in range(len(polygon)):
        j = (i+1) % len(polygon)
        points = [low[i],high[i],high[j],low[j]]
        yield dict(part=part, points=points, uv=edge_uv(points), texture=side, slot=slot+'_edge', metal=metal)


def encode(image):
    out = io.BytesIO()
    image.save(out, 'PNG')
    return out.getvalue()


def asset_path(ref):
    namespace, name = ref.split(':', 1)
    return f'assets/{namespace}/textures/{name}.png'


def retired_tool_meshes(root):
    """Obsolete per-item OBJs and MTLs are owned by the forging generator."""
    variants = [(f'forged_{kind}',kind) for kind in KINDS]
    variants.extend((rid,rid) for rid,_,_,_ in added_tools(root))
    directory = Path(root)/'src/main/resources/assets/yuanxian/models/equipment/forging'
    for rid, key in variants:
        names = [rid, *(f'workpiece_{rid}_{state}' for state in ('rough','shaped','quenched','tempered','ground'))]
        names.extend(f'bench_{key}_{n}' for n in range(6))
        names.extend(f'bench_{key}_{n}_hot' for n in (0,1,5))
        yield from (directory/f'{name}.{extension}' for name in names for extension in ('obj','mtl'))


class ToolModels:
    def __init__(self, root, profiles, stages, palette_texture):
        self.settings = profiles
        self.stages = stages
        self.textures = {}
        self.sizes = {}
        self.models = {}
        shapes = {row['kind']: row for row in profiles['tools']}
        for shape in shapes.values():
            self.models[shape['id']] = (shape, 'iron', shape['name'], shape['kind'])
        for rid, kind, name, material in added_tools(root):
            self.models[rid] = (shapes[kind], material['key'], name, rid)

        surfaces = {
            state: Image.open(io.BytesIO(palette_texture(state, profiles['palette'][state]))).convert('RGB')
            for state in ('rough', *STAGE_MIX)
        }
        for material in {row[1] for row in self.models.values()}:
            sides = {}
            for part in ('metal_edge', 'wood_edge'):
                sides[part] = self.load(root, material, part)
            rough = Image.blend(surfaces['rough'], sides['metal_edge'].resize(surfaces['rough'].size), .58)
            self.record(self.ref(material,'rough'), rough)
            for state, alpha in STAGE_MIX.items():
                self.record(self.ref(material,f'metal_edge_{state}'),
                            Image.blend(sides['metal_edge'], surfaces[state].resize(sides['metal_edge'].size), alpha))
            for kind in KINDS:
                head = self.load(root, material, kind+'_head')
                self.load(root, material, kind+'_wood')
                for state, alpha in STAGE_MIX.items():
                    # Tint the entire plane in a single pass, preserving the inked rim and keeping UV continuous.
                    tint = surfaces[state].resize(head.size,Image.Resampling.NEAREST)
                    self.record(self.ref(material,f'{kind}_{state}'), Image.blend(head,tint,alpha))

    @staticmethod
    def ref(material, name):
        return f'{PREFIX}tools/{material}/{name}'

    def load(self, root, material, name):
        path = root / 'equipment/workshop/tool_textures' / material / (name+'.png')
        data = path.read_bytes()
        image = Image.open(io.BytesIO(data)).convert('RGB')
        self.record(self.ref(material,name), image, data)
        return image

    def record(self, ref, image, data=None):
        self.sizes[ref] = image.size
        self.textures[asset_path(ref)] = encode(image) if data is None else data

    def projection(self, outline, part, ref, rough):
        z0 = min(z for z,y in outline)
        y1 = max(y for z,y in outline)
        width,height = self.sizes[ref]
        if rough:
            z1 = max(z for z,y in outline)
            y0 = min(y for z,y in outline)
            return lambda z,y: ((z-z0)/(z1-z0), (y1-y)/(y1-y0))
        spec = self.settings['projection']
        scale = spec[f'{part}_texels_per_unit']
        pad = spec['padding']
        return lambda z,y: (((z-z0)*scale+pad)/width, ((y1-y)*scale+pad)/height)

    def faces(self, rid, stage, hot=False):
        shape,material,_,_ = self.models[rid]
        kind = shape['kind']
        x = shape['grip'][0]
        rough = stage == 0
        outline = shape['rough'] if rough else shape['head']
        depth = shape['head_depth'] + (self.settings['rough_extra_depth'] if rough else 0)
        surface = self.stages[stage]['surface'] if stage < 5 else None
        head = self.ref(material,'rough' if rough else kind+'_'+(surface or 'head'))
        edge = self.ref(material,'rough' if rough else 'metal_edge'+('_'+surface if surface else ''))
        face_uv = self.projection(outline,'head',head,rough)
        if hot:
            head = edge = PREFIX+'hot'
        square = lambda points: [(0,0),(0,1),(1,1),(1,0)]
        result = list(profile_faces(outline,x,depth,'连续薄刃',head,edge,face_uv,square,True))
        if stage == 5:
            wood = self.ref(material,kind+'_wood')
            handle_uv = self.projection(shape['handle'],'handle',wood,False)
            height = self.settings['projection']['handle_edge_height']
            edge_uv = lambda points: [(u,(height-p[1])/height) for u,p in zip((0,1,1,0),points)]
            result.extend(profile_faces(shape['handle'],x,self.settings['handle_depth'],'连续握柄',
                                        wood,self.ref(material,'wood_edge'),handle_uv,edge_uv,False))
        return result

    def export(self, faces, rid, stage, bench=False, hot=False, display=None):
        shape, material, _, _ = self.models[rid]
        kind = shape['kind']
        form = 'rough' if stage == 0 else 'finished' if stage == 5 else 'head'
        geometry = f'equipment/forging/tool_geometry/{kind}_{form}'+('_bench' if bench else '')
        mtls = 'equipment/forging/tool_materials/'
        default = mtls + ('iron_rough' if stage == 0 else f"forged_{kind}_{'finished' if stage == 5 else 'ground'}")
        if hot and stage < 5:
            material_key = mtls+'hot'
        elif stage == 0:
            material_key = mtls+material+'_rough'
        else:
            state = 'finished' if stage == 5 else self.stages[stage]['id']
            material_key = mtls+f'{rid}_{state}'+('_hot' if hot else '')
        slots = {}
        for face in faces:
            previous = slots.setdefault(face['slot'], face['texture'])
            if previous != face['texture']:
                raise ValueError(f'同一工具面槽出现多张贴图：{rid}')
        assets, model = mesh.obj_assets([dict(face,texture=face['slot']) for face in faces], 'yuanxian:'+geometry, display)
        name = f'assets/yuanxian/models/{geometry}.obj'
        obj = assets[name].replace(f'mtllib yuanxian:models/{geometry}.mtl'.encode(),
                                   f'mtllib yuanxian:models/{default}.mtl'.encode(), 1)
        mtl = '\n'.join(f'newmtl m{i}\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd {texture}\n'
                        for i,texture in enumerate(slots.values())).encode()
        model.update(mtl_override=f'yuanxian:models/{material_key}.mtl',textures={'particle':faces[0]['texture']})
        return {name:obj, f'assets/yuanxian/models/{material_key}.mtl':mtl}, model
