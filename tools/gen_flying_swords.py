#!/usr/bin/env python3
"""飞剑专属模型/材质/配方生成器；--check 不写资源，--preview 输出离线自查图。"""
from pathlib import Path
import argparse, json, struct, zlib
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
COLORS={'jade':'#5aada4','edge':'#b1e4d9','iron':'#293d4a','gold':'#bfa36a','wrap':'#344f60'}

def box(a,b,color):
    return {'from':a,'to':b,'faces':{d:{'texture':'#palette','uv':[i*2+.25,1,i*2+1.75,7]} for d in ('up','down','north','south','west','east') for i in [list(COLORS).index(color)]}}

def model(azure):
    parts=[box([5.8,0,7],[10.2,21,9],'jade' if azure else 'iron'),
           box([5.1,1,7.4],[5.8,21,8.6],'edge'),box([10.2,1,7.4],[10.9,21,8.6],'edge'),
           box([7.7,0,6.6],[8.3,26,9.4],'gold' if azure else 'iron'),
           box([4,-.8,7],[12,.8,9],'iron'),
           box([2.8,-.55,7.2],[4,.55,8.8],'jade'),box([12,-.55,7.2],[13.2,.55,8.8],'jade'),
           box([2,-.3,7.5],[2.8,.3,8.5],'gold'),box([13.2,-.3,7.5],[14,.3,8.5],'gold'),
           box([7,-7,7],[9,-.8,9],'wrap'),box([6.6,-9,6.6],[9.4,-7,9.4],'jade'),
           box([7.6,-12,7.6],[8.4,-9,8.4],'gold')]
    # Finer, monotonic taper avoids the three broad stair blocks of the first draft.
    for step in range(9):
        inset=6.0+step*.225
        parts.append(box([inset,21+step,7.4],[16-inset,22+step,8.6],'edge'))
    if azure:
        # Thin jade cloud feet: short curved steps, kept close to the long main blade.
        for a,b in [([4.8,8,7.2],[5.8,12,8.2]),([4.3,9.5,7.3],[4.8,11.5,8.1]),
                    ([5.2,12,7.4],[6.2,13.4,8]),([4.6,7.5,7.5],[5.3,8,7.9])]:
            parts.append(box(a,b,'jade'))
            parts.append(box([16-b[0],a[1],a[2]],[16-a[0],b[1],b[2]],'jade'))
        parts += [box([6.2,3,6.7],[6.5,19,7.1],'gold'),box([9.5,3,6.7],[9.8,19,7.1],'gold')]
    return {'textures':{'palette':'myvillage:item/flying_sword_palette','particle':'myvillage:item/flying_sword_palette'},'elements':parts,
            'display':{'gui':{'rotation':[20,0,-35],'translation':[0,0,0],'scale':[.48,.48,.48]},
                       'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.32,.32,.32]},
                       'thirdperson_righthand':{'rotation':[0,-90,15],'translation':[0,0,0],'scale':[.55,.55,.55]},
                       'firstperson_righthand':{'rotation':[0,-90,25],'translation':[0,0,0],'scale':[.55,.55,.55]}}}

def outputs():
    result={RES/'assets/myvillage/flying_sword/visuals.json':(ROOT/'tools/flying_sword/visuals.json').read_bytes()}
    for name,azure in [('rideable_flying_sword',False),('azure_cloud_flying_sword',True)]:
        result[RES/f'assets/myvillage/models/item/{name}.json']=(json.dumps(model(azure),indent=2)+'\n').encode()
        key={'I':{'item':'minecraft:iron_ingot'},'D':{'item':'minecraft:diamond'},'S':{'item':'minecraft:stick'}}
        recipe={'type':'minecraft:crafting_shaped','category':'equipment','pattern':['  I',' I ','S  '], 'key':{'I':key['I'],'S':key['S']},'result':{'id':f'myvillage:{name}','count':1}}
        if azure: recipe.update(pattern=[' D ','DID',' D '],key={'D':key['D'],'I':{'item':'myvillage:rideable_flying_sword'}})
        result[RES/f'data/myvillage/recipe/{name}.json']=(json.dumps(recipe,indent=2)+'\n').encode()
    def chunk(kind,data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    pixels=b''.join(bytes.fromhex(c[1:])*4 for c in COLORS.values())+bytes(12*3)
    png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,8,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+pixels)*8))+chunk(b'IEND',b'')
    result[RES/'assets/myvillage/textures/item/flying_sword_palette.png']=png
    return result

def preview(out):
    from PIL import Image, ImageDraw, ImageFont
    import sys
    sys.path.insert(0,str(ROOT))
    from tools.combat_preview.item_model import Assets, ItemModel, Camera, render, transform_quads, affine, rot_xyz
    out.mkdir(parents=True,exist_ok=True)
    font=ImageFont.truetype('/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc',24)
    sheet=Image.new('RGB',(1650,640),'#edf0e9');draw=ImageDraw.Draw(sheet)
    old=Image.open(RES/'assets/myvillage/textures/item/rideable_flying_sword.png').convert('RGBA').resize((440,440),Image.Resampling.NEAREST)
    sheet.paste(old,(55,100),old);draw.text((30,25),'旧版参考：平面占位图',font=font,fill='#253c41')
    assets=Assets([RES],None)
    for i,(name,title) in enumerate([('rideable_flying_sword','基础飞剑：厚刃与剑脊'),('azure_cloud_flying_sword','青霄飞剑：青玉、金纹、承托')],1):
        m=ItemModel(assets,'myvillage:item/'+name)
        if m.messages():raise ValueError(m.messages())
        quads=transform_quads(m.base.quads,affine(rot_xyz(90,0,0)))
        image,_,_=render(quads,Camera((.7,.9,.75)),520,bg=(237,240,233))
        sheet.paste(image,(i*550,75));draw.text((i*550+15,25),title,font=font,fill='#253c41')
    draw.text((30,600),'离线几何自查；游戏站位、声音、手感与多人表现待真实输入验收',font=font,fill='#465c60')
    sheet.save(out/'comparison.png')
    (out/'review.md').write_text('御剑离线自查：旧版是平面占位图；基础剑改为厚刃，青霄增加剑脊、金纹、护手、柄与尾饰、足部侧翼。32px 色板已换算为 0–16 UV；金纹呈金色、握柄为深蓝，承托收为薄玉小翼。使用既有 combat_preview 软件光栅器检查生成后实际模型。剑尖沿本地 +Y，经飞行渲染旋转到世界前向。此图证明几何组成，不证明游戏站位、光照、手感。玩家外观仍 not_verified。\n')

def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--preview',type=Path);a=p.parse_args()
    errors=[]
    for path,data in outputs().items():
        if a.check:
            if not path.exists() or path.read_bytes()!=data:errors.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    if a.preview:preview(a.preview)
    if errors:raise SystemExit('飞剑生成资源漂移：'+', '.join(errors))
    print('飞剑资源一致')
if __name__=='__main__':main()
