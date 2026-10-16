"""Approved iron/copper open ding: shared authored geometry for assets and collision."""
import json
from pathlib import Path
from tools.gen_alchemy_assets import Canvas, png
ROOT=Path(__file__).resolve().parents[2]
PARTS=('iron','iron_edge','copper','copper_edge','dark','coal','fire','seal')

def config(root=ROOT):
    value=json.loads((root/'tools/forging/ding.json').read_text())
    if set(value)!= {'version','palette','feedback'} or value['version']!=1:raise ValueError('Ding config schema')
    if set(value['palette'])!=set(PARTS):raise ValueError('Ding palette')
    for color in value['palette'].values():
        if len(color)!=3 or any(type(n)!=int or not 0<=n<=255 for n in color):raise ValueError('Ding RGB')
    return value

def uv(part):
    i=PARTS.index(part);x=(i%4)*4;y=(i//4)*4
    return [x,y,x+4,y+4]

def texture(cfg):
    canvas=Canvas(64)
    for y in range(64):
        for x in range(64):
            index=((y//16)*4+x//16)%len(PARTS);part=PARTS[index]
            u,v=x%16,y%16;base=cfg['palette'][part];n=((u*37+v*19+u*v*3)%9)-4
            if part in ('iron','iron_edge'):
                n+=7 if (u+v*2)%23==0 else 0
                if v in (0,15):n-=7
            elif part in ('copper','copper_edge'):
                n+=8 if v in (1,2) else -7 if v==15 else 0
                if u in (2,13) and 4<=v<=11:n-=7
            elif part=='fire':
                base=(255,max(40,177-v*8),max(8,58-v*3));n=0
            elif part=='seal':
                if (u in (3,12) and 3<=v<=12) or (v in (3,12) and 3<=u<=12) or (u==v and 5<=u<=10):base=cfg['palette']['copper_edge']
            canvas.put(x,y,tuple(max(0,min(255,c+n)) for c in base))
    return png(canvas.rows)

def sections(radius):
    """Disjoint strip sections of the same chamfered octagon used by the body."""
    low=8-radius;cut=radius*.42;step=cut/3
    yield low+cut,16-low-cut,low,16-low
    for i in range(3):
        x=low+i*step;inset=cut-i*step
        yield x,x+step,low+inset,16-low-inset
        yield 16-x-step,16-x,low+inset,16-low-inset

def geometry(lit=False):
    elements=[];collision=[]
    def box(name,lo,hi,part='iron',collide=False,glow=False):
        faces={f:{'texture':'#atlas','uv':uv(part)} for f in ('north','south','east','west','up','down')}
        if glow:
            for f in faces.values():f['neoforge_data']={'block_light':15,'sky_light':15,'ambient_occlusion':False}
        element={'name':name,'from':[round(n,5) for n in lo],'to':[round(n,5) for n in hi],'faces':faces}
        if glow:element['shade']=False
        elements.append(element)
        if collide:collision.append([*lo,*hi])
    def solid(name,y0,y1,r,part,collide=False):
        for i,(x0,x1,z0,z1) in enumerate(sections(r)):box(f'{name}_{i}',[x0,y0,z0],[x1,y1,z1],part,collide)
    def ring(name,y0,y1,outer,inner,part):
        # Each strip is split around the square hearth opening; no coplanar overlapping layers.
        for i,(x0,x1,z0,z1) in enumerate(sections(outer)):
            if x1<=8-inner or x0>=8+inner:
                box(f'{name}_{i}',[x0,y0,z0],[x1,y1,z1],part);continue
            mid0,mid1=max(x0,8-inner),min(x1,8+inner)
            if x0<mid0:box(f'{name}_{i}_west',[x0,y0,z0],[mid0,y1,z1],part)
            if x1>mid1:box(f'{name}_{i}_east',[mid1,y0,z0],[x1,y1,z1],part)
            if z0<8-inner:box(f'{name}_{i}_north',[mid0,y0,z0],[mid1,y1,8-inner],part)
            if z1>8+inner:box(f'{name}_{i}_south',[mid0,y0,8+inner],[mid1,y1,z1],part)
    for name,x,z in [('front_left',3,3),('front_right',10,3),('rear',6.5,11)]:
        box(name+'_foot',[x-.3,0,z-.3],[x+3.3,1,z+3.3],'dark',True)
        box(name+'_leg',[x,1,z],[x+3,3.7,z+3],'iron',True)
        box(name+'_anklet',[x-.15,2.5,z-.15],[x+3.15,3.15,z+3.15],'copper')
    solid('belly_base',3.5,4.2,5,'dark')
    solid('base_binding',4.2,4.9,5.5,'copper')
    solid('lower_belly',4.9,6.2,6,'iron')
    ring('belly',6.2,11,6.5,4.5,'iron')
    ring('upper_binding',11,11.6,7,4.5,'copper')
    ring('rim',11.6,12.25,7.5,4.5,'iron_edge')
    ring('rim_binding',12.25,12.65,7.5,4.5,'copper_edge')
    # Simplified load-bearing hull; tiny rivets, straps and ember fragments do not collide.
    for x0,x1,z0,z1 in sections(6.5):collision.append([x0,3.5,z0,x1,12.65,z1])
    solid('hearth',9.5,9.8,4.5,'dark')
    for i,(x,z,w) in enumerate([(5,5,3),(8,8,3),(4,8,2),(9,5,2)]):
        box('ember_'+str(i),[x,9.8,z],[x+w,10.1,z+1.5],'fire' if lit else 'coal',glow=lit)
    for name,x in [('west',0),('east',12)]:
        box(name+'_ear_left',[x,12,6],[x+1.1,16.3,8],'iron',True)
        box(name+'_ear_right',[x+2.9,12,6],[x+4,16.3,8],'iron',True)
        box(name+'_ear_top',[x,16.3,6],[x+4,17.3,8],'copper',True)
        box(name+'_ear_root',[x,12,5.8],[x+4,12.8,8.2],'copper')
    for x in [3,12.35]:box('front_strap_'+str(x),[x,5.1,1.47],[x+.65,11.2,1.66],'copper')
    # Front faces lie in front of the shell (north), avoiding z-fighting with the curved belly.
    box('fire_recess',[5,6.3,1.18],[11,9.7,1.45],'dark')
    box('vent_sill',[5,6.3,1.03],[11,6.75,1.2],'copper')
    box('vent_header',[5,9.25,1.03],[11,9.7,1.2],'copper')
    for i,x in enumerate([5.5,7.4,9.3]):box('fire_window_'+str(i),[x,6.8,1.0],[x+1.2,9.25,1.04],'fire' if lit else 'coal',glow=lit)
    box('front_seal',[7.4,10,1.25],[8.6,10.8,1.46],'seal')
    box('rear_seal',[6.8,8,14.51],[9.2,9.8,14.65],'seal')
    return elements,collision

def outputs(root=ROOT):
    cfg=config(root);base=root/'src/main/resources';files={}
    encoded=lambda v:(json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode()
    files[base/'assets/myvillage/textures/block/forging_ding.png']=texture(cfg)
    for lit in (False,True):
        elements,shape=geometry(lit);suffix='_lit' if lit else ''
        files[base/f'assets/myvillage/models/block/forging_table{suffix}.json']=encoded({'ambientocclusion':True,'textures':{'atlas':'myvillage:block/forging_ding','particle':'myvillage:block/forging_ding'},'elements':elements})
    files[base/'assets/myvillage/blockstates/forging_table.json']=encoded({'variants':{f'facing={d},lit={str(lit).lower()}':{'model':'myvillage:block/forging_table'+('_lit' if lit else ''),'y':angle} for lit in (False,True) for d,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    files[base/'assets/myvillage/models/item/forging_table.json']=encoded({'parent':'myvillage:block/forging_table','display':{
        'gui':{'rotation':[25,225,0],'translation':[0,-.5,0],'scale':[.78,.78,.78]},
        'ground':{'translation':[0,3,0],'scale':[.35,.35,.35]},
        'fixed':{'rotation':[0,180,0],'scale':[.6,.6,.6]},
        'firstperson_righthand':{'rotation':[0,135,0],'translation':[0,2,0],'scale':[.45,.45,.45]},
        'firstperson_lefthand':{'rotation':[0,45,0],'translation':[0,2,0],'scale':[.45,.45,.45]},
        'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.4,.4,.4]}}})
    files[base/'data/myvillage/forging/ding.json']=encoded({'version':1,'collision':shape,'hearth':[8,13,8],'feedback':cfg['feedback']})
    return files

def preview(root=ROOT):
    import numpy as np
    from PIL import Image,ImageDraw,ImageFont
    from tools.combat_preview import item_model as pim
    from tools.beastgen.preview import Frame,render
    out=root/'out/preview/forging_ding';out.mkdir(parents=True,exist_ok=True)
    assets=pim.Assets([root/'src/main/resources'],None)
    for lit in (False,True):
        name='myvillage:block/forging_table'+('_lit' if lit else '')
        model=pim.resolve_model(assets,assets.model_json(name),name)
        if model.errors or model.warnings:raise ValueError(model.errors+model.warnings)
        quads=[((q.pos-np.array([8,0,8]))/16,q.uv,q.tex,None) for i,e in enumerate(model.elements) for q in pim.element_quads(assets,model,e,i)]
        for view,camera in [('front',(0,.35,-1)),('quarter',(-1,.65,-1.3)),('rear',(1,.6,1.3)),('top',(-.6,1.4,-1))]:
            frame=Frame(camera,640,640,420,(0,.54,0),ss=2)
            render(quads,frame,bg=(27,37,37),body_rot=180).save(out/f'{"lit" if lit else "cold"}_{view}.png')
    sheet=Image.new('RGB',(1280,690),(27,37,37));draw=ImageDraw.Draw(sheet)
    font=ImageFont.truetype('/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc',24)
    for i,(name,label) in enumerate([('cold_quarter','暗炉 · 正式模型'),('lit_quarter','亮炉 · 正式模型')]):
        sheet.paste(Image.open(out/(name+'.png')),(i*640,0));draw.text((i*640+320,654),label,font=font,fill=(226,213,185),anchor='mm')
    sheet.save(out/'comparison.png')
    (out/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>炼器鼎 · 正式模型预览</title><style>body{background:#1b2525;color:#e2d5b9;font:16px system-ui;max-width:1280px;margin:24px auto}img{max-width:100%}.grid{display:grid;grid-template-columns:repeat(2,1fr)}</style><h1>玄铁赤铜炼器鼎</h1><p>直接读取正式 Minecraft 模型与贴图的离线预览；光照、粒子与声音以实机为准。</p><img src="comparison.png"><div class="grid">'+''.join('<figure><img src="'+name+'.png"><figcaption>'+name+'</figcaption></figure>' for name in ['cold_front','cold_rear','cold_top','lit_top'])+'</div>')
    print('Ding preview:',out)
