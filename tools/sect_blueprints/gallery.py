"""同源宗门模型合集；只导出网页，正式游戏资源仍由 runtime.py 负责。"""
import argparse,json,gzip,base64,struct
from pathlib import Path
from sect_blueprints.generate import ROOT,export

def comparison(out,models):
    from sect_blueprints.architecture import profile
    palette={};boxes=[];buildings=[];views=[{'id':'overview','name':'六套主殿对照','eye':[190,94,-75],'target':[90,8,54]}];anchors=[]
    for i,(key,data) in enumerate(models):
        info,_=profile(key);main=next(b for b in data['buildings'] if b['id']=='main_hall');x=17+(i%3)*60;z=12+(i//3)*58
        offset=[x-main['origin'][0],-main['origin'][1],z-main['origin'][2]]
        palette.update({key+'_'+k:v for k,v in data['palette'].items()})
        for b in data['boxes']:
            if b[4]=='main_hall':boxes.append([[b[0][j]+offset[j] for j in range(3)],b[1],key+'_'+b[2],b[3],key])
        boxes.append([[x+main['size'][0]/2,-.3,z+main['size'][2]/2],[main['size'][0]+4,.6,main['size'][2]+4],key+'_stone','ground',key])
        buildings.append({**main,'id':key,'name':info['name'],'origin':[x,0,z]})
        views.append({'id':key,'name':info['name'],'eye':[x+38,25,z-32],'target':[x+main['size'][0]/2,9,z+main['size'][2]/2]})
        anchors.append({'id':key,'name':info['name'],'position':[x+main['size'][0]//2,3,z]})
    w,d=180,120
    payload={'name':'六套宗门主殿形制','status':'approved' if all(data['status']=='approved' for _,data in models) else 'design_review','dimensions':[w,64,d],'palette':palette,'boxes':boxes,
        'terrain':base64.b64encode(struct.pack('<'+'h'*(w*d),*([0]*(w*d)))).decode(),'surfaces':['ground']*(w*d),
        'influence':base64.b64encode(bytes(w*d)).decode(),'buildings':buildings,'views':views,'routes':[],'anchors':anchors,'showLabels':True,
        'checks':{'anchors':6},'design':['相同方块比例对照六套主殿的体量、层数、屋顶和廊柱。','云阙重檐礼殿，松崖低矮山观，听泉白墙群院与赭木园居，照月收分水殿与单檐水榭。','其余单体也采用各自对应语汇；本图只隔离主殿便于比较。']}
    packed={'data':payload,'three':(ROOT/'tools/web/three.min.js').read_text(),'orbit':(ROOT/'tools/web/OrbitControls.js').read_text(),'viewer':(ROOT/'tools/sect_blueprints/viewer.js').read_text()}
    raw=base64.b64encode(gzip.compress(json.dumps(packed,ensure_ascii=False,separators=(',',':')).encode(),mtime=0)).decode()
    dest=out/'architecture';dest.mkdir(exist_ok=True);(dest/'index.html').write_text((ROOT/'tools/sect_blueprints/template.html').read_text().replace('__PAYLOAD__',raw).replace('__NAME__',payload['name']))

def build(out,base_url,catalog_path=None):
    source=catalog_path or ROOT/'blueprints/sects/catalog.json';catalog=json.loads(source.read_text());buttons=[];models=[]
    for row in catalog['layouts']:
        key=row['id'].split(':')[1];path=source.parent/row['source'];spec=json.loads(path.read_text())
        export(path,out/key,preview_only=True)
        if spec.get('architecture_profile'):models.append((key,json.loads(gzip.decompress((out/key/'scene.json.gz').read_bytes()))))
        buttons.append(f'<button data-key="{key}">{spec["name"]}</button>')
    if models:comparison(out,models);buttons.insert(0,'<button data-key="architecture">六套主殿对照</button>')
    html='''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>宗门布局与景观</title><style>
    :root{color:var(--foreground);font-family:var(--font-sans,system-ui)}html,body{margin:0;background:transparent}.tabs{display:flex;flex-wrap:wrap;gap:6px;margin-bottom:10px}button{font:inherit;font-size:12px;border:1px solid var(--border,#435a50);border-radius:var(--radius,8px);padding:8px 10px;background:var(--secondary,#23382d);color:var(--foreground,#dbe8df);cursor:pointer}button.active{background:var(--primary,#8ab196);color:var(--primary-foreground,#14271d)}iframe{border:0;width:100%;height:960px;display:block}p{font-size:12px;color:var(--muted-foreground,#9caf9f);margin:0 0 10px}</style></head><body><div class="tabs">__BUTTONS__</div><p>三种空间骨架，各有紧凑版与较大版。拖动模型旋转；“查看动线”显示内部路线。预览材质按方块分类示意。</p><iframe id="model" title="宗门同源三维模型"></iframe><script>
    const base=__BASE__;function select(key){document.getElementById('model').src=base+'/'+key+'/index.html';document.querySelectorAll('button').forEach(b=>b.classList.toggle('active',b.dataset.key===key));}document.querySelectorAll('button').forEach(b=>b.onclick=()=>select(b.dataset.key));select('__INITIAL__');</script></body></html>'''
    html=html.replace('__INITIAL__','cliff_retreat' if models else 'valley_courts')
    if models:html=html.replace('三种空间骨架，各有紧凑版与较大版。拖动模型旋转；“查看动线”显示内部路线。预览材质按方块分类示意。','六套宗门采用不同建筑形制；松崖、双岸采用修订山体，其余布局保留。可点击“六套主殿对照”比较形体；预览材质按方块分类示意。')
    (out/'index.html').write_text(html.replace('__BUTTONS__',''.join(buttons)).replace('__BASE__',json.dumps(base_url)),encoding='utf-8')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--base-url',required=True);p.add_argument('--catalog',type=Path);a=p.parse_args();build(a.out,a.base_url.rstrip('/'),a.catalog)
