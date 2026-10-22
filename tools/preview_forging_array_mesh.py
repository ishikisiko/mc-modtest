#!/usr/bin/env python3
"""Read back the generated ding mesh/atlas and render cold/hot offline views.

No sword, particles, invented geometry, bloom or local Minecraft. Fire faces
are omitted when cold. The shared software rasteriser depth-tests glow faces.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0,str(ROOT))


def main(argv=None):
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
    from tools.beastgen.preview import Frame, render

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root",type=Path,default=ROOT)
    parser.add_argument("--out",type=Path,default=ROOT/"out/preview/forging_array/model")
    args = parser.parse_args(argv)
    args.out.mkdir(parents=True,exist_ok=True)
    assets = args.root/"src/main/resources/assets/myvillage"
    mesh_path = assets/"forging_array/formed_mesh.json"
    texture_path = assets/"textures/block/forging_array_formed.png"
    mesh = json.loads(mesh_path.read_text())
    texture = np.asarray(Image.open(texture_path).convert("RGBA"),dtype=np.float32)/255
    # The renderer adds eyes-like glow on top of shaded geometry. Attenuate the
    # base glow tile so their combined value remains an unlit surface, not bloom.
    base_glow = texture.copy()
    base_glow[:,:,:3] *= .20
    emission = texture.copy()
    emission[:,:,:3] *= .80
    views = (("front",(0,.23,-1)),("quarter",(-1,.70,-1.45)),("rear",(1,.62,1.45)))
    font = ImageFont.truetype("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",26)
    sheet = Image.new("RGB",(1800,1170),(28,33,35))
    draw = ImageDraw.Draw(sheet)
    for row,hot in enumerate((False,True)):
        quads = []
        for face in mesh["faces"]:
            if face["g"] == "fire" and not hot:
                continue
            i = face["m"]
            x,y = i%4*4,i//4*4
            uv = [(x,y),(x+4,y),(x+4,y+4),(x,y+4)]
            quads.append((np.asarray(face["p"],float),uv,base_glow if face["glow"] else texture,
                          emission if face["glow"] else None))
        state = "hot" if hot else "cold"
        for column,(name,camera) in enumerate(views):
            im = render(quads,Frame(camera,960,860,143,(0,2.02,0),ss=1),bg=(158,161,157),
                        ground=False,body_rot=180,cull=True)
            im.save(args.out/f"{state}_{name}.png")
            thumb = im.resize((600,537),Image.Resampling.LANCZOS)
            sheet.paste(thumb,(column*600,row*585))
            label = ("运行" if hot else "冷态")+" · "+{"front":"正面","quarter":"前侧","rear":"背面"}[name]
            draw.text((column*600+300,row*585+558),label,font=font,fill=(227,218,191),anchor="mm")
    sheet.save(args.out/"contact_sheet.png")
    manifest = {"mesh_sha256":hashlib.sha256(mesh_path.read_bytes()).hexdigest(),
                "texture_sha256":hashlib.sha256(texture_path.read_bytes()).hexdigest(),
                "bounds":mesh["bounds"],"faces":len(mesh["faces"]),
                "collision_boxes":len(mesh["collision_boxes"]),
                "groups":{g:sum(f["g"]==g for f in mesh["faces"]) for g in ("body","ring","fire")},
                "views":[f"{s}_{n}.png" for s in ("cold","hot") for n,_ in views],
                "source":"generated_mesh_and_atlas","runtime_visual":"not_verified"}
    (args.out/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n")
    (args.out/"index.html").write_text('''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>玄枢炼器鼎 · 精确模型预览</title><style>body{background:#1c2123;color:#e3dabe;font:17px system-ui;max-width:1500px;margin:32px auto;padding:0 20px;line-height:1.8}img{width:100%}a{color:#9bd5c4}</style><h1>玄枢炼器鼎</h1><p>方腹玄铁、青铜双耳兽足、四角导灵阵柱。上排冷态，下排运行；读取正式生成网格与同一张贴图。无静态悬剑；游戏中由渲染器展示真实器物。</p><img src="contact_sheet.png" alt="冷态和运行态的正面、前侧、背面模型"><p>此为离线模型预览。环旋转、真实物品展示、火焰动态、游戏光照和玩家外观验收均为 not_verified。</p><p><a href="manifest.json">生成资源哈希及网格统计</a></p></html>''',encoding="utf-8")
    print("Exact forging-array mesh preview:",args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
