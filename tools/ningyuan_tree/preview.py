"""Offline renders of shipped meshes. Native tree/wood/sign/boat evidence is separate."""
from pathlib import Path
import json
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from tools.combat_preview import item_model as pim
from tools.beastgen.preview import Frame,render
from .assets import ROOT,NAMES,RES

def main():
    out=ROOT/'out/preview/ningyuan_tree';out.mkdir(parents=True,exist_ok=True)
    assets=pim.Assets([ROOT/RES],pim.VANILLA_JAR)
    samples=[('ningyuan_sapling','凝元树苗'),('potted_ningyuan_sapling','盆栽'),
             ('ningyuan_log','凝元原木'),('stripped_ningyuan_log','去皮原木'),
             ('ningyuan_wood','凝元木'),('stripped_ningyuan_wood','去皮凝元木'),
             ('ningyuan_leaves','凝元树叶'),('ningyuan_planks','凝元木板'),
             ('ningyuan_stairs','楼梯'),('ningyuan_slab','台阶'),
             ('ningyuan_fence_inventory','栅栏'),('ningyuan_fence_gate','栅栏门'),
             ('ningyuan_door_bottom_left','花格门 · 下半'),('ningyuan_door_top_left','花格门 · 上半'),
             ('ningyuan_trapdoor_bottom','花格活板门'),('ningyuan_pressure_plate','压力板'),
             ('ningyuan_fruit_0_low','花芽'),('ningyuan_fruit_1_low','生长中'),
             ('ningyuan_fruit_2_low','下品成熟果'),('ningyuan_fruit_2_high','上品成熟果'),
             ('ningyuan_lattice_inventory','嵌玉花格'),('jade_tree_pool_border','青玉树池边石'),
             ('ningyuan_fruit_lantern','凝元果灯'),('ningyuan_fruit_lantern_hanging','悬挂果灯')]
    width,height=1120,1600;bg=(231,227,211)
    sheet=Image.new('RGB',(width,height),bg);d=ImageDraw.Draw(sheet)
    fontpath='/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc'
    font=ImageFont.truetype(fontpath,19);title=ImageFont.truetype(fontpath,30)
    d.text((25,20),'凝元灵木 · 实际模型与贴图',font=title,fill=(42,67,53))
    d.text((25,62),'离线模型检查；整树、告示牌与船以云端实机画面为准',font=font,fill=(93,91,76))
    for i,(name,label) in enumerate(samples):
        ref='myvillage:block/'+name;m=pim.resolve_model(assets,assets.model_json(ref),ref)
        if m.errors:raise ValueError((name,m.errors))
        quads=[((q.pos-np.array([8,0,8]))/16,q.uv,q.tex,None) for j,e in enumerate(m.elements) for q in pim.element_quads(assets,m,e,j)]
        shot=render(quads,Frame((-1,.65,-1.3),260,200,135,(0,.5,0),ss=2),bg=bg,ground=False,body_rot=180)
        shot.save(out/(name+'.png'));x=(i%4)*280+10;y=100+(i//4)*245
        sheet.paste(shot.convert('RGB'),(x,y));d.text((x+130,y+210),label,font=font,fill=(47,65,51),anchor='mt')
    sheet.save(out/'components.png')
    (out/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>凝元灵木</title><style>body{max-width:1120px;margin:24px auto;background:#e7e3d3;color:#2a4335;font:18px sans-serif}img{width:100%}</style><h1>凝元果树 · 模型检查</h1><p>渲染使用实际打包模型和贴图。离线检查不替代游戏中的外观、交互与性能验收。</p><img src="components.png">')
    print(out/'components.png')
if __name__=='__main__':main()
