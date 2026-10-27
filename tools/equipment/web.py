"""Self-contained WebGL fitting room, built from the same baked armor/weapon geometry."""
from __future__ import annotations

import base64
import gzip
import html
import json
from pathlib import Path

import numpy as np

from .catalogue import ROOT, read
from .geometry import encode_png
from .preview import TO_WORLD, bone_transforms, box_quads, mannequin
from ..combat_preview import item_model as im


def data_uri(pixels):
    return "data:image/png;base64,"+base64.b64encode(encode_png(pixels)).decode()


def triangles(quads):
    out=[]
    for q in quads:
        normal=np.cross(q.pos[1]-q.pos[0],q.pos[2]-q.pos[0])
        normal/=max(np.linalg.norm(normal),1e-9)
        for i in (0,1,2,0,2,3):
            uv=[v/16 for v in q.uv[i]] if q.uv is not None else [-1,-1]
            out.extend([*q.pos[i],*normal,*uv,*(q.color if q.color is not None else [1,1,1])])
    return [round(float(v),6) for v in out]


def payload(spec,model,pixels,root,weapon_spec=None):
    textures=[data_uri(pixels)]
    profiles={}
    for profile,pieces in model['profiles'].items():
        batches=[]
        for piece in pieces:
            groups={}
            for box in piece['boxes']:
                groups.setdefault(box['bone'],[]).extend(box_quads(box,None,model['texture_size'],model['texel_density']))
            for bone,quads in groups.items():
                batches.append({'item':piece['id'],'bone':bone,'texture':0,'vertices':triangles(quads)})
        identity={bone:np.eye(4) for bone in read(root/'equipment/poses.json')['bones']}
        groups={}
        for q in mannequin(profile,identity):
            groups.setdefault(q.tag,[]).extend(im.transform_quads([q],np.linalg.inv(TO_WORLD)))
        for bone,quads in groups.items():
            batches.append({'item':'body','bone':bone,'texture':0,'vertices':triangles(quads)})
        # Reuse the existing weapon's exact third-person display and hand transform.
        if weapon_spec is not None:
            weapon=im.ItemModel(im.Assets([root/'src/main/resources'],None),weapon_spec['model'])
            variant=weapon.variant('thirdperson_righthand')
            if variant.res.errors:raise ValueError('配持武器资源无效：'+';'.join(variant.res.errors))
            transform=im.affine(np.eye(3)*16) @ im.hand_item_matrix() @ im.display_matrix(variant.display('thirdperson_righthand'))
            if profile=='slim':transform=im.affine(t=[.5,0,0]) @ transform
            grouped={}
            for q in im.transform_quads(variant.quads,transform):
                if q.tex is None:raise ValueError('配持武器贴图缺失')
                uri=data_uri(np.rint(q.tex*255).astype(np.uint8).tolist())
                if uri not in textures:textures.append(uri)
                ti=textures.index(uri)
                grouped.setdefault(ti,[]).append(q)
            for ti,quads in grouped.items():
                batches.append({'item':'weapon','bone':'right_arm','texture':ti,'vertices':triangles(quads)})
        profiles[profile]=batches
    config=read(root/'equipment/poses.json')
    poses={pose:{bone:(TO_WORLD @ matrix).T.flatten().tolist() for bone,matrix in bone_transforms(pose,root).items()}
           for pose in config['poses']}
    return {'profiles':profiles,'poses':poses,'textures':textures,'weapon_name':weapon_spec['name'] if weapon_spec else None,
            'pose_names':{key:value['name'] for key,value in config['poses'].items()},
            'items':[{k:i[k] for k in ('id','name')} for i in spec['items']]}


def write_preview(spec,model,pixels,path,root=ROOT,weapon_spec=None):
    template=(Path(__file__).parent/'viewer.html').read_text()
    encoded=json.dumps(payload(spec,model,pixels,root,weapon_spec),ensure_ascii=False,separators=(',',':')).encode('utf-8')
    # Lossless compression keeps the complete model in one portable page and within T3's
    # inline HTML size limit. No remote script/library or geometry quantisation is needed.
    compressed=base64.b64encode(gzip.compress(encoded,mtime=0)).decode('ascii')
    document=template.replace('__EQUIPMENT_DATA_GZIP__',compressed).replace('__EQUIPMENT_NAME__',html.escape(spec['name']))
    Path(path).write_text(document,encoding='utf-8')
    return path
