#!/usr/bin/env python3
"""已确认组合动效的正式网格、阵纹与云纹；只生成当前使用资源。"""
from __future__ import annotations
import argparse,copy,io,json,math,sys
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont,ImageFilter
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.qianji.generate_assets import encoded
SOURCE=ROOT/'blueprints/qianji/atmosphere.json';ASSET=ROOT/'src/main/resources/assets/myvillage/textures/effect/qianji';DATA=ROOT/'src/main/resources/data/myvillage/qianji'

def rng(seed):
    while True:
        seed=(seed*1664525+1013904223)&0xffffffff;yield seed/4294967296
def point(p,q,t):return [a+(b-a)*t for a,b in zip(p,q)]
def catmull(points,u):
    n=len(points)-1;i=min(n-1,int(u*n));f=u*n-i;p1=points[i];p2=points[i+1];p0=points[i-1] if i else [2*a-b for a,b in zip(p1,p2)];p3=points[i+2] if i+2<len(points) else [2*b-a for a,b in zip(p1,p2)]
    def knot(a,b):return max(1e-5,math.dist(a,b)**.5)
    ts=[0,knot(p0,p1)];ts += [ts[-1]+knot(p1,p2)];ts += [ts[-1]+knot(p2,p3)];t=ts[1]+f*(ts[2]-ts[1])
    a1=point(p0,p1,(t-ts[0])/(ts[1]-ts[0]));a2=point(p1,p2,(t-ts[1])/(ts[2]-ts[1]));a3=point(p2,p3,(t-ts[2])/(ts[3]-ts[2]));b1=point(a1,a2,(t-ts[0])/(ts[2]-ts[0]));b2=point(a2,a3,(t-ts[1])/(ts[3]-ts[1]));return point(b1,b2,(t-ts[1])/(ts[2]-ts[1]))
def route(points,count):
    raw=[catmull(points,i/(count*2)) for i in range(count*2+1)];dist=[0]
    for a,b in zip(raw,raw[1:]):dist.append(dist[-1]+math.dist(a,b))
    out=[];j=0
    for i in range(count+1):
        target=dist[-1]*i/count
        while j+1<len(dist)-1 and dist[j+1]<target:j+=1
        out.append([round(x,5) for x in point(raw[j],raw[j+1],(target-dist[j])/max(1e-9,dist[j+1]-dist[j]))])
    return out
def png(im):b=io.BytesIO();im.save(b,format='PNG');return b.getvalue()

def seal(kind,cfg):
    n=cfg['textures']['seal_resolution'] if kind=='sky' else 512;im=Image.new('RGBA',(n,n));g=ImageDraw.Draw(im);c=n/2
    def arc(r,a,b,width=2):g.arc((c-r,c-r,c+r,c+r),math.degrees(a),math.degrees(b),fill=(255,255,255,255),width=width)
    def path(points,angle=0,width=2):
        ca,sa=math.cos(angle),math.sin(angle);p=[(c+x*ca-y*sa,c+x*sa+y*ca) for x,y in points];g.line(p,fill=(255,255,255,255),width=width,joint='curve')
    if kind=='sky':
        for i in range(8):
            a=i*math.pi/4
            for r,lo,hi,w in [(477,.025,.73,2),(465,.04,.71,1),(410,.08,.66,1),(399,.095,.645,1)]:arc(r,a+lo,a+hi,w)
        for i in range(96):a=i*math.tau/96;path([(0,-475),(0,-(452 if i%12==0 else 459 if i%3==0 else 464))],a,3 if i%12==0 else 1)
        font=ImageFont.truetype(str(ROOT/cfg['textures']['font']),42)
        for i,word in enumerate('乾坎艮震巽离坤兑'):
            a=i*math.pi/4;tile=Image.new('RGBA',(96,96));ImageDraw.Draw(tile).text((48,48),word,font=font,fill='white',anchor='mm');tile=tile.rotate(-i*45,resample=Image.Resampling.BICUBIC,expand=True);x=c+math.sin(a)*434;y=c-math.cos(a)*434;im.alpha_composite(tile,(round(x-tile.width/2),round(y-tile.height/2)))
            path([(-33,-395),(-33,-375),(-47,-375),(-47,-359),(-18,-359),(-18,-378),(18,-378),(18,-359),(47,-359),(47,-375),(33,-375),(33,-395)],a)
            path([(0,-351),(11,-334),(0,-316),(-11,-334),(0,-351)],a)
            for k in range(3):
                path([(-23,-304+k*9),(-5 if k==i%3 else 23,-304+k*9)],a)
                if k==i%3:path([(5,-304+k*9),(23,-304+k*9)],a)
        for r in [269,260]:arc(r,0,math.tau,1)
        for i in range(8):
            a=i*math.pi/4+math.pi/8;b=a+3*math.pi/4;path([(math.cos(a)*259,math.sin(a)*259),(math.cos(b)*259,math.sin(b)*259)],width=1)
        stars=[(-77,-179),(14,-139),(97,-151),(153,-66),(113,26),(29,66),(-41,150),(-141,90),(-172,-20)];path(stars,width=1)
        for x,y in stars:g.ellipse((c+x-4,c+y-4,c+x+4,c+y+4),fill='white')
    else:
        upper=kind=='upper'
        for r in ([238,223] if upper else [239,226,182]):
            for i in range(4):arc(r,i*math.pi/2+.035,(i+1)*math.pi/2-.045,1)
        for i in range(12 if upper else 8):path([(-8,-224),(-8,-205),(-17,-205),(-17,-191),(0,-181),(17,-191),(17,-205),(8,-205),(8,-224)],i*math.tau/(12 if upper else 8),1)
        if not upper:
            for a in [0,math.pi/4]:
                for r in [119,108]:path([(-r,-r),(r,-r),(r,r),(-r,r),(-r,-r)],a,1)
            for i in range(4):path([(0,-180),(0,-137),(31,-106),(31,-59),(0,-28)],i*math.pi/2,1)
            path([(-18,-18),(18,-18),(18,18),(-18,18),(-18,-18)],width=1)
    glow=im.filter(ImageFilter.GaussianBlur(3 if kind=='sky' else 2));glow.putalpha(glow.getchannel('A').point(lambda x:round(x*.65)));glow.alpha_composite(im);return png(glow)

def cloud(seed,size):
    r=rng(seed);grid=[next(r) for _ in range(33*33)]
    def noise(x,y):
        xx,yy=math.floor(x),math.floor(y);fx,fy=x-xx,y-yy
        def at(a,b):return grid[(b%32)*33+(a%32)]
        return(1-fy)*((1-fx)*at(xx,yy)+fx*at(xx+1,yy))+fy*((1-fx)*at(xx,yy+1)+fx*at(xx+1,yy+1))
    def smooth(x):x=max(0,min(1,x));return x*x*(3-2*x)
    w,h=size;im=Image.new('RGBA',(w,h));p=im.load()
    for y in range(h):
        for x in range(w):
            nx=(x-w/2)/(w/2);ny=(y-h/2)/(h/2);n=noise(x/24,y/19)*.6+noise(x/10,y/8)*.27+noise(x/4,y/4)*.13
            lobes=math.exp(-((nx+.38)**2*6+(ny+.04)**2*6))*.65+math.exp(-(nx*nx*4+(ny+.14)**2*7))*.7+math.exp(-((nx-.46)**2*10+(ny-.16)**2*9))*.5
            alpha=max(0,min(1,(n-.27)*1.5))*lobes*smooth((1-abs(nx))*3)*smooth((1-abs(ny))*3);light=.78+noise(x/35,y/28)*.22;p[x,y]=(round(178*light),round(207*light),round(211*light),min(220,round(alpha*255)))
    return png(im)
def spot():
    im=Image.new('RGBA',(64,64));p=im.load()
    for y in range(64):
        for x in range(64):
            t=math.hypot(x-31.5,y-31.5)/32;a=max(0,1-t)**4;p[x,y]=(255,255,255,round(255*a))
    return png(im)

def geometry(cfg):
    m=cfg['mirage'];cells={};faces=[];lines=[];step=m['grid']
    def height(x,z):return m['base_height']+sum(a*math.exp(-((x-px)**2/sx+(z-pz)**2/sz)) for px,pz,a,sx,sz in m['peaks'])+m['ridge'][0]*math.exp(-(z*z/m['ridge'][1]+x*x/m['ridge'][2]))
    def face(points,color,layer=0):faces.append({'p':[[round(v,4) for v in p] for p in points],'c':color,'layer':layer})
    def box(x,y,z,w,h,d,color,layer):
        a=x-w/2;b=y-h/2;c=z-d/2;xx=x+w/2;yy=y+h/2;zz=z+d/2
        for p in [[(a,b,c),(xx,b,c),(xx,yy,c),(a,yy,c)],[(xx,b,zz),(a,b,zz),(a,yy,zz),(xx,yy,zz)],[(a,b,zz),(a,b,c),(a,yy,c),(a,yy,zz)],[(xx,b,c),(xx,b,zz),(xx,yy,zz),(xx,yy,c)],[(a,yy,c),(xx,yy,c),(xx,yy,zz),(a,yy,zz)],[(a,b,zz),(xx,b,zz),(xx,b,c),(a,b,c)]]:face(p,color,layer)
    for iz in range(m['z_cells'][0],m['z_cells'][1]+1):
        for ix in range(m['x_cells'][0],m['x_cells'][1]+1):
            x,z=ix*step,iz*step
            if (x/m['plan_radius'][0])**2+(z/m['plan_radius'][1])**2>1+.08*math.sin(ix*.5)*math.cos(iz*.7):continue
            cells[ix,iz]=(x,z,round(height(x,z)/m['height_step'])*m['height_step'])
    for (ix,iz),(x,z,h) in cells.items():
        s=step/2;light=max(0,min(1,h/34));col=[round(255*n) for n in [.25+light*.25,.49+light*.28,.52+light*.25]]
        face([(x-s,h,z-s),(x-s,h,z+s),(x+s,h,z+s),(x+s,h,z-s)],col)
        for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
            other=cells.get((ix+dx,iz+dz));low=other[2] if other else m['bottom']
            if low>=h:continue
            ps=[(x+dx*s,low,z-s),(x+dx*s,h,z-s),(x+dx*s,h,z+s),(x+dx*s,low,z+s)] if dx else [(x-s,low,z+dz*s),(x-s,h,z+dz*s),(x+s,h,z+dz*s),(x+s,low,z+dz*s)]
            face(ps,[round(n*.77) for n in col])
            if round(h/m['height_step'])%4==0 or not other:lines.append([list(ps[1]),list(ps[2])])
    colors=[[199,181,141],[130,182,169],[66,107,105]]
    for x,z,w,d,levels in m['pavilions']:
        y=height(x,z)
        for k in range(levels):
            ww,dd,yy=w-k*1.3,d-k*.8,y+k*4.3;box(x,yy,z,ww+.8,.35,dd+.7,colors[1],2)
            for xx in [-ww/2+.35,ww/2-.35]:
                for zz in [-dd/2+.35,dd/2-.35]:box(x+xx,yy+1.7,z+zz,.35,3.1,.35,colors[0],1)
            box(x,yy+1.2,z+dd/2-.2,ww-.3,2.3,.3,colors[2],3)
            for j in range(4):box(x,yy+3.2+j*.5,z,ww+2-j*.6,.45,dd+2-j*1.5,colors[1],2)
            box(x,yy+5.2,z,ww*.66,.25,.3,colors[0],1)
    for i in range(32):
        x,y,z=catmull(m['bridge'],i/31);box(x,y,z,1.1,.22,2.6,colors[0],1)
        if i%3==0:
            for s in [-1,1]:box(x,y+.9,z+s*1.15,.16,1.8,.16,colors[1],2)
    return {'faces':faces,'edges':lines,'river':route(m['river'],cfg['render']['route_samples'])}

def export(check=False):
    cfg=json.loads(SOURCE.read_text());expected={}
    for kind in ['sky','ground','upper']:expected[ASSET/f'seal_{kind}.png']=seal(kind,cfg)
    for i,seed in enumerate(cfg['textures']['cloud_seeds']):expected[ASSET/f'cloud_{i}.png']=cloud(seed,cfg['textures']['cloud_size'])
    expected[ASSET/'spot.png']=spot();expected[DATA/'atmosphere.json']=encoded(cfg)
    mesh=geometry(cfg);mesh['routes']=[route(p,cfg['render']['route_samples']) for p in cfg['routes']];mesh['links']=[route(p,cfg['render']['route_samples']) for p in cfg['array_links']];expected[DATA/'atmosphere_mesh.json']=encoded(mesh)
    bad=[]
    for p,b in expected.items():
        if check:
            if not p.exists() or p.read_bytes()!=b:bad.append(str(p.relative_to(ROOT)))
        else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
    if bad:raise ValueError('千机阁动效资源不一致：'+str(bad))
    print(json.dumps({'files':len(expected),'faces':len(mesh['faces']),'edges':len(mesh['edges']),'check':check}),flush=True)
if __name__=='__main__':p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');export(p.parse_args().check)
