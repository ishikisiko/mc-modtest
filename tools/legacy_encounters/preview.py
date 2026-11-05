"""从当前模型和动作生成可旋转的 HTML 验收页，资源统一使用现有 HTTPS 服务。"""
from __future__ import annotations
import argparse,json,math,shutil,hashlib,base64
from pathlib import Path
from tools.gen_legacy_encounters import ROOT,RES,catalogue,item_model
from tools.beastgen.preview import Beast
from tools.beastgen.build import Built
from tools.combat_preview import item_model as items

URL='https://assets.ishiki333hhh.win/previews/four-legacies-20261106'
OUT=ROOT/'reports/four-legacies/html'

def pixel_data(path):
    from PIL import Image
    im=Image.open(path).convert('RGBA')
    return {'width':im.width,'height':im.height,'rgba':base64.b64encode(im.tobytes()).decode()}

def vertices(quads):
    positions=[];uv=[]
    for points,coords,*_ in quads:
        for i in (0,1,2,0,2,3):
            positions.extend(round(float(v),5) for v in points[i]);uv.extend([round(coords[i][0]/16,6),round(1-coords[i][1]/16,6)])
    return positions,uv

def build_assets():
    OUT.mkdir(parents=True,exist_ok=True);(OUT/'models').mkdir(exist_ok=True);(OUT/'images').mkdir(exist_ok=True)
    shutil.copy2(ROOT/'tools/web/three.min.js',OUT/'three.min.js')
    for mob in catalogue()['mobs']:
        name=mob['id'];beast=Beast(name);b=Built(name);pos,uv=vertices(beast.quads());clips={}
        for clip in b.clips:
            if clip.name not in ('idle','walk','run') and clip.name not in {m['animation'] for m in mob['moves']}:continue
            count=max(2,int(math.ceil(clip.length*12)))
            frames=[]
            for i in range(count+1):
                # In-place posing keeps the viewer focused; the native capture shows actual movement.
                q=beast.mesh.world(beast.mats(clip.name,min(clip.length-.00001,i*clip.length/count)))
                frames.append(vertices(q)[0])
            clips[clip.name]={'duration':clip.length,'frames':frames}
        doc={'name':mob['name'],'positions':pos,'uv':uv,'clips':clips,'texture':URL+'/models/'+name+'.png','glow':URL+'/models/'+name+'_eyes.png','height':mob['physical']['height'],
             'pixels':pixel_data(RES/f'assets/myvillage/textures/entity/{name}/{name}.png'),'glow_pixels':pixel_data(RES/f'assets/myvillage/textures/entity/{name}/{name}_eyes.png')}
        (OUT/'models'/f'{name}.json').write_text(json.dumps(doc,separators=(',',':'),ensure_ascii=False))
        for suffix in ('','_eyes'):shutil.copy2(RES/f'assets/myvillage/textures/entity/{name}/{name}{suffix}.png',OUT/'models'/f'{name}{suffix}.png')
        for image in (ROOT/'reports/four-legacies/visuals').glob(name+'*.png'):shutil.copy2(image,OUT/'images'/image.name)
    assets=items.Assets([RES],None)
    for row in catalogue()['items']:
        name=row['id'];obj=item_model(row);resolved=items.resolve_model(assets,obj,'myvillage:item/'+name);quads=[]
        for i,element in enumerate(resolved.elements):
            for q in items.element_quads(assets,resolved,element,i):
                points=q.pos.copy()/16;points[:,0]-=.5;points[:,2]-=.5;quads.append((points,q.uv))
        if resolved.errors:raise ValueError(resolved.errors)
        pos,uv=vertices(quads)
        doc={'name':row['name'],'positions':pos,'uv':uv,'clips':{},'texture':URL+'/models/'+name+'.png','height':1.6,'pixels':pixel_data(RES/f'assets/myvillage/textures/item/{name}.png')}
        (OUT/'models'/f'{name}.json').write_text(json.dumps(doc,separators=(',',':'),ensure_ascii=False))
        shutil.copy2(RES/f'assets/myvillage/textures/item/{name}.png',OUT/'models'/f'{name}.png')
    # A classic same-domain asset script also works inside a sandboxed T3 frame without CORS.
    bundle={p.stem:json.loads(p.read_text()) for p in sorted((OUT/'models').glob('*.json'))}
    (OUT/'models.js').write_text('window.LEGACY_MODELS='+json.dumps(bundle,separators=(',',':'),ensure_ascii=False)+';\n')
    concept=ROOT/'work/entity-art/legacy_encounters/concept.png'
    if concept.exists():shutil.copy2(concept,OUT/'images/concept.png')

def html(summary):
    c=catalogue();data=[]
    acquisition=['在剑痕碑遗迹挑战游剑妖，完成后用纸拓印。拓本、书和墨囊制成秘籍；古剑灵核用于制作归燕剑。',
                 '在沼泽或河岸寻找照影妖。得到水显绢卷后进入水中右键展开，获得分影步秘籍；绢卷也用于制作照水镜。',
                 '吞火蟾出现在恶地和热带草原。余焰丹砂、书和墨囊制成伏火诀秘籍；它喷吐后留下的余焰可用葫芦收取。',
                 '在镇岳碑遗迹触动石碑，击败守碑石灵后用纸拓印。碑拓、书和墨囊制成秘籍；碑灵石心用于制作镇岳尺。']
    for index,(mob,art,item) in enumerate(zip(c['mobs'],c['arts'],c['items'])):
        data.append({'mob':mob,'art':art,'item':item,'acquisition':acquisition[index]})
    template=(ROOT/'tools/legacy_encounters/preview.html').read_text()
    return template.replace('__BASE__',URL).replace('__DATA__',json.dumps(data,ensure_ascii=False)).replace('__SUMMARY__',json.dumps(summary,ensure_ascii=False))

def main():
    p=argparse.ArgumentParser();p.add_argument('--summary',type=Path);p.add_argument('--publish',action='store_true');p.add_argument('--assets',action='store_true');a=p.parse_args()
    if a.assets:build_assets()
    summary=json.loads(a.summary.read_text()) if a.summary else {'version':'0.116.0','status':'implementation','message':'模型和玩法已接入，正在完成云端验证。','checks':[],'native':[],'source':'待最终固定','jar':None}
    OUT.mkdir(parents=True,exist_ok=True);(OUT/'index.html').write_text(html(summary));(OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
    if a.publish:
        target=Path('/home/ubuntu/.local/share/myvillage-assets/www/previews/four-legacies-20261106');shutil.copytree(OUT,target,dirs_exist_ok=True)
        print(URL+'/index.html')
    print(OUT/'index.html')

if __name__=='__main__':main()
