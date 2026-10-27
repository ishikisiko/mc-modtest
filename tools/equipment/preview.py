"""Actual compiled armor geometry rendered with the existing item rasteriser."""
from __future__ import annotations

import io
import json
from pathlib import Path

import numpy as np
from PIL import Image

from .catalogue import ROOT, read
from .export import cube_faces
from ..combat_preview import item_model as im
from ..combat_preview import sheets


def rotation(degrees):
    x,y,z = degrees
    # ModelPart.translateAndRotate uses Quaternionf.rotationZYX.
    return im.axis_rot("z", z) @ im.axis_rot("y", y) @ im.axis_rot("x", x)


def bone_transforms(pose="standing", root=ROOT):
    config = read(root / "equipment/poses.json")
    if pose not in config["poses"]:
        raise ValueError(f"未知姿态 {pose}")
    overrides = config["poses"][pose]["bones"]
    out = {}
    for bone, pivot in config["bones"].items():
        entry = overrides.get(bone, {})
        out[bone] = im.affine(rotation(entry.get("rotation", [0,0,0])), entry.get("position", pivot))
    return out


TO_WORLD = im.affine(np.diag([-1., -1., 1.]), [0,24,0])


def box_quads(box, texture, texture_size, density):
    lo, size = np.array(box["from"]), np.array(box["size"])
    pivot = np.array(box.get("pivot", [0,0,0]))
    transform = im.affine(rotation(box.get("rotation", [0,0,0])), pivot)
    quads = []
    for verts, (u0,v0,u1,v1) in cube_faces(size, box["uv"], density):
        points = [lo + size * np.array(v) - pivot for v in verts]
        uv = [(u1/texture_size,v0/texture_size),(u0/texture_size,v0/texture_size),
              (u0/texture_size,v1/texture_size),(u1/texture_size,v1/texture_size)]
        uv = [(u*16, v*16) for u,v in uv]  # item rasteriser accepts Minecraft's 0..16 UV space
        quads.extend(im.transform_quads([im.Quad(points, uv=uv, tex=texture, tag=box["name"])], transform))
    return quads


def mannequin(profile, transforms):
    skin = (.67,.56,.45)
    cloth = (.25,.27,.29)
    body = [("head",[-4,-8,-4],[4,0,4],skin), ("body",[-4,0,-2],[4,12,2],cloth),
            ("right_leg",[-2,0,-2],[2,12,2],cloth),("left_leg",[-2,0,-2],[2,12,2],cloth)]
    if profile == "slim":
        body += [("right_arm",[-2,-2,-2],[1,10,2],skin),("left_arm",[-1,-2,-2],[2,10,2],skin)]
    else:
        body += [("right_arm",[-3,-2,-2],[1,10,2],skin),("left_arm",[-1,-2,-2],[3,10,2],skin)]
    # Neutral face makes head coverage legible without substituting a character asset.
    body += [("head",[-4.03,-8.03,-4.03],[4.03,-6,4.03],(.16,.14,.13)),
             ("head",[-2.5,-4.6,-4.02],[-1.5,-3.8,-4],(.12,.12,.13)),
             ("head",[1.5,-4.6,-4.02],[2.5,-3.8,-4],(.12,.12,.13))]
    return [q for bone,lo,hi,color in body for q in
            im.transform_quads(im.box_quads(lo,hi,color,bone), TO_WORLD @ transforms[bone])]


def scene(model, pixels, profile="standard", pose="standing", selected=None, body=True, root=ROOT):
    transforms = bone_transforms(pose, root)
    tex = np.array(pixels, dtype=np.float32) / 255.
    quads = mannequin(profile, transforms) if body else []
    for item in model["profiles"][profile]:
        if selected is not None and item["id"] not in selected:
            continue
        for box in item["boxes"]:
            quads += im.transform_quads(box_quads(box,tex,model["texture_size"],model["texel_density"]),
                                       TO_WORLD @ transforms[box["bone"]])
    return quads


def render_icon(model, pixels, item_id):
    quads = scene(model,pixels,selected={item_id},body=False)
    cam = im.Camera((-.45,.25,-1))
    image,_,_ = im.render(quads,cam,64,bg=(0,0,0),ss=2,cull=False)
    masks = [im.Quad(q.pos,color=(1,1,1),shade=False) for q in quads]
    mask,_,_ = im.render(masks,cam,64,bg=(0,0,0),ss=2,cull=False)
    alpha = np.asarray(mask)[:,:,0].astype(float)/255.
    color = np.asarray(image).astype(float)/np.maximum(alpha[:,:,None],1/255.)
    rgba = np.dstack((np.clip(color,0,255).astype(np.uint8), np.rint(alpha*255).astype(np.uint8)))
    out=io.BytesIO();Image.fromarray(rgba).save(out,format="PNG")
    return out.getvalue()


def render_sheet(model,pixels,out,root=ROOT,title="装备"):
    views=[("正面",(0,.05,-1)),("侧面",(-1,.05,0)),("背面",(0,.05,1)),("斜前",(-.7,.25,-1))]
    rows=[]
    for profile,label in (("standard","宽臂"),("slim","细臂")):
        quads=scene(model,pixels,profile=profile,root=root)
        rows.append([sheets.labelled(im.render(quads,im.Camera(camera),320,cull=False,bg=(222,219,209))[0],
                                    [f"{label} · {name}"],320) for name,camera in views])
    for pose in ("walk","crouch","raised"):
        rows.append([sheets.labelled(im.render(scene(model,pixels,profile,pose,root=root),im.Camera(camera),320,
                                               cull=False,bg=(222,219,209))[0],
                                    [f"{read(root/'equipment/poses.json')['poses'][pose]['name']} · {'宽臂' if profile=='standard' else '细臂'}"],320)
                     for profile in ("standard","slim") for camera in ((-.7,.25,-1),(-1,.05,0))])
    sheet=sheets.grid(rows,[f"{title} · 同源模型试衣间", "实际导出几何 / 宽臂与细臂 / 代表性姿态；离线预览，不代替实机验收"])
    out=Path(out);out.parent.mkdir(parents=True,exist_ok=True);sheet.save(out)
    (out.with_suffix('.json')).write_text(json.dumps({"model":model['id'],"profiles":list(model['profiles']),
        "poses":["standing","walk","crouch","raised"],"runtime":"not_verified"},ensure_ascii=False,indent=2)+'\n')
    return out
