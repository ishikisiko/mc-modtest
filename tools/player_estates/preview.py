"""从同一方块集合与 Minecraft 1.21.1 原始模型/贴图烘焙独立预览。"""
from __future__ import annotations

from array import array
import base64
import gzip
import hashlib
from collections import defaultdict
from functools import lru_cache
from io import BytesIO
import json
import math
from pathlib import Path
import zipfile

from PIL import Image

from .geometry import state

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
NORMALS = {'west':(-1,0,0),'east':(1,0,0),'down':(0,-1,0),'up':(0,1,0),'north':(0,0,-1),'south':(0,0,1)}


def parse(s):
    name, _, props = s.partition('[')
    return name, dict(p.split('=') for p in props.rstrip(']').split(',') if p)


def rotate(p, axis, degrees, origin=(8,8,8)):
    a,b,c = [p[i]-origin[i] for i in range(3)]
    co,si=math.cos(math.radians(degrees)),math.sin(math.radians(degrees))
    if axis=='x':out=(a,b*co-c*si,b*si+c*co)
    elif axis=='y':out=(a*co+c*si,b,-a*si+c*co)
    else:out=(a*co-b*si,a*si+b*co,c)
    return tuple(out[i]+origin[i] for i in range(3))


class NativeModels:
    def __init__(self, jar):
        self.jar=zipfile.ZipFile(jar)
        self.textures={}

    @lru_cache(None)
    def read(self, path):
        return json.loads(self.jar.read(path))

    @lru_cache(None)
    def model(self, key):
        if ':' in key:ns,key=key.split(':',1)
        else:ns='minecraft'
        raw=self.read(f'assets/{ns}/models/{key}.json')
        parent=raw.get('parent','')
        data=self.model(parent) if parent and not parent.startswith('builtin/') else {}
        return {**data,**raw,'textures':{**data.get('textures',{}),**raw.get('textures',{})}}

    def condition(self, cond, props):
        if 'OR' in cond:return any(self.condition(c,props) for c in cond['OR'])
        if 'AND' in cond:return all(self.condition(c,props) for c in cond['AND'])
        return all(props.get(k,'false') in str(v).split('|') for k,v in cond.items())

    @lru_cache(None)
    def models(self, s):
        name,props=parse(s);base=name.split(':')[1]
        if base=='water':return [{'model':'block/water_still_preview'}]
        if base.endswith('_bed'):return [{'model':'block/bed_preview'}]
        raw=self.read(f'assets/minecraft/blockstates/{base}.json')
        found=[]
        if 'variants' in raw:
            for key,value in raw['variants'].items():
                if all(props.get(k,{'axis':'y','facing':'north','half':'bottom','type':'bottom','shape':'straight','hanging':'false'}.get(k,v))==v for k,v in (p.split('=') for p in key.split(',') if p)):
                    found.append(value[0] if isinstance(value,list) else value)
                    break
        for part in raw.get('multipart',[]):
            if self.condition(part.get('when',{}),props):
                v=part['apply'];found.append(v[0] if isinstance(v,list) else v)
        if not found:raise ValueError(f'没有原版模型: {s}')
        return found

    def texture(self, key):
        if ':' in key:ns,key=key.split(':',1)
        else:ns='minecraft'
        name=ns+':'+key
        if name not in self.textures:
            image=Image.open(BytesIO(self.jar.read(f'assets/{ns}/textures/{key}.png'))).convert('RGBA')
            if image.height>image.width:image=image.crop((0,0,image.width,image.width))
            self.textures[name]=image
        return name

    @lru_cache(None)
    def faces(self, s):
        name,props=parse(s)
        result=[]
        for entry in self.models(s):
            if entry['model']=='block/water_still_preview':
                m={'textures':{'all':'block/water_still'},'elements':[{'from':[0,0,0],'to':[16,14,16], 'faces':{k:{'texture':'#all','cullface':k} for k in NORMALS}}]}
            elif entry['model']=='block/bed_preview':
                m={'textures':{'all':'block/red_wool'},'elements':[{'from':[0,0,0],'to':[16,9,16], 'faces':{k:{'texture':'#all'} for k in NORMALS}}]}
            else:m=self.model(entry['model'])
            for element in m.get('elements',[]):
                x0,y0,z0=element['from'];x1,y1,z1=element['to']
                corners={
                    'north':[(x1,y1,z0),(x1,y0,z0),(x0,y0,z0),(x0,y1,z0)],
                    'south':[(x0,y1,z1),(x0,y0,z1),(x1,y0,z1),(x1,y1,z1)],
                    'west':[(x0,y1,z0),(x0,y0,z0),(x0,y0,z1),(x0,y1,z1)],
                    'east':[(x1,y1,z1),(x1,y0,z1),(x1,y0,z0),(x1,y1,z0)],
                    'up':[(x0,y1,z0),(x0,y1,z1),(x1,y1,z1),(x1,y1,z0)],
                    'down':[(x0,y0,z1),(x0,y0,z0),(x1,y0,z0),(x1,y0,z1)]}
                for face,spec in element['faces'].items():
                    points=corners[face];normal=NORMALS[face];cull=NORMALS.get(spec.get('cullface'))
                    rotation=element.get('rotation')
                    if rotation:
                        points=[rotate(p,rotation['axis'],rotation['angle'],rotation['origin']) for p in points]
                        normal=rotate(normal,rotation['axis'],rotation['angle'],(0,0,0))
                    for axis in ('x','y'):
                        if entry.get(axis):
                            deg=entry[axis] * (-1 if axis=='y' else 1)
                            points=[rotate(p,axis,deg) for p in points]
                            normal=rotate(normal,axis,deg,(0,0,0))
                            if cull:cull=tuple(round(v) for v in rotate(cull,axis,deg,(0,0,0)))
                    uv=spec.get('uv')
                    if uv is None:
                        uv={'up':[x0,z0,x1,z1],'down':[x0,16-z1,x1,16-z0],
                            'north':[16-x1,16-y1,16-x0,16-y0],'south':[x0,16-y1,x1,16-y0],
                            'west':[z0,16-y1,z1,16-y0],'east':[16-z1,16-y1,16-z0,16-y0]}[face]
                    u0,v0,u1,v1=uv;uvs=[(u0,v0),(u0,v1),(u1,v1),(u1,v0)]
                    turns=spec.get('rotation',0)//90
                    if turns:uvs=uvs[-turns:]+uvs[:-turns]
                    tex=spec['texture']
                    visited=set()
                    while tex.startswith('#'):
                        if tex in visited:raise ValueError(f'循环纹理引用: {tex}')
                        visited.add(tex);tex=m['textures'][tex[1:]]
                    tex=self.texture(tex)
                    tint=(1,1,1)
                    if spec.get('tintindex',-1)>=0 or name.endswith('_leaves'):
                        tint=(.50,.68,.30)
                    if name=='minecraft:water':tint=(.30,.56,.88)
                    transparent=name=='minecraft:water'
                    result.append((points,normal,uvs,tex,tint,cull,transparent))
        if not result:raise ValueError(f'模型没有可见面: {s}')
        return result

    def solid(self,s):
        name,_=parse(s);base=name.split(':')[1]
        if base in ('air','water','wheat','lantern','sweet_berry_bush','glass_pane') or base.endswith(('_stairs','_slab','_fence','_trapdoor','_bed','_leaves')):return False
        return base!='anvil'


def packed(values,code):
    buf=array(code,values)
    import sys
    if sys.byteorder!='little':buf.byteswap()
    return base64.b64encode(buf.tobytes()).decode()


def bake(e,jar,out):
    native=NativeModels(jar)
    for s in set(e.blocks.values()):native.faces(s)
    names=sorted(native.textures);tile=18;cols=8;rows=math.ceil(len(names)/cols)
    atlas=Image.new('RGBA',(cols*tile,rows*tile),(0,0,0,0));loc={}
    for i,key in enumerate(names):
        im=native.textures[key].resize((16,16),Image.Resampling.NEAREST)
        xx=(i%cols)*tile+1;yy=(i//cols)*tile+1
        atlas.paste(im,(xx,yy));loc[key]=(xx,yy)
        # A one-pixel gutter avoids neighbouring sprites at geometry edges.
        atlas.paste(im.crop((0,0,1,16)),(xx-1,yy));atlas.paste(im.crop((15,0,16,16)),(xx+16,yy))
        atlas.paste(im.crop((0,0,16,1)),(xx,yy-1));atlas.paste(im.crop((0,15,16,16)),(xx,yy+16))
    atlas.save(out/'atlas.png')
    buckets=defaultdict(lambda:{'p':[],'n':[],'u':[],'i':[]})
    count=0
    for (x,y,z),s in e.blocks.items():
        layer=e.layers[x,y,z]
        for points,normal,uvs,tex,tint,cull,transparent in native.faces(s):
            if cull:
                other=e.blocks.get((x+cull[0],y+cull[1],z+cull[2]))
                if other and (native.solid(other) or other==s and name_same_transparent(s)):continue
            key=(layer,tint,transparent);b=buckets[key];idx=len(b['p'])//3;tx,ty=loc[tex]
            for point,uv in zip(points,uvs):
                b['p'].extend((x+point[0]/16,y+point[1]/16,z+point[2]/16))
                b['n'].extend(round(v*127) for v in normal)
                b['u'].extend(((tx+uv[0])/atlas.width,1-(ty+uv[1])/atlas.height))
            b['i'].extend((idx,idx+1,idx+2,idx,idx+2,idx+3));count+=1
    meshes=[]
    for (layer,tint,transparent),b in buckets.items():
        meshes.append({'layer':layer,'tint':tint,'transparent':transparent,
                       'positions':packed(b['p'],'f'),'normals':packed(b['n'],'b'),
                       'uv':packed(b['u'],'f'),'indices':packed(b['i'],'I')})
    payload={'size':e.c['size'],'views':e.c['views'],'meshes':meshes,'blocks':len(e.blocks),
             'faces':count,'rooms':e.rooms,'reference':e.c['reference']}
    raw=json.dumps(payload,separators=(',',':'),ensure_ascii=False).encode()
    (out/'scene.json').write_bytes(raw)
    (out/'scene.js').write_text('window.__estatePacked='+json.dumps(base64.b64encode(gzip.compress(raw,mtime=0)).decode())+';')
    return {'meshes':len(meshes),'visible_faces':count,'native_textures':len(names),'native_model_states':len(set(e.blocks.values())),
            'bed_preview':'床使用简化色块，门窗、屋顶、楼梯与建筑主体使用原版模型；不等同于游戏截图。'}


def name_same_transparent(s):
    return s.split('[')[0] in ('minecraft:water','minecraft:glass_pane') or s.split('[')[0].endswith('_leaves')


def write_viewer(e,out,base_url=''):
    template=(HERE/'viewer.html').read_text()
    libraries=[base64.b64encode(gzip.compress((ROOT/name).read_bytes(),mtime=0)).decode() for name in ('tools/web/three.min.js','tools/web/OrbitControls.js')]
    loader='window.__librariesReady=(async()=>{for(const text of '+json.dumps(libraries)+'){const bytes=Uint8Array.from(atob(text),c=>c.charCodeAt(0));const code=await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"))).text();const script=document.createElement("script");script.textContent=code;document.head.append(script)}})();'
    template=template.replace('/*THREE*/',loader).replace('/*ORBIT*/','')
    template=template.replace('const BASE=','(async()=>{await window.__librariesReady;const BASE=')
    template=template.replace('</script></body>','})();</script></body>')
    template=template.replace('/*BASE*/',json.dumps(base_url))
    template=template.replace('/*REV*/',json.dumps(hashlib.sha256((out/'scene.js').read_bytes()).hexdigest()[:16]))
    template=template.replace('/*ATLAS*/',json.dumps('data:image/png;base64,'+base64.b64encode((out/'atlas.png').read_bytes()).decode()))
    poster=base_url+'overview.png'
    if (out/'overview.png').is_file():
        # Embed a compact frame from the actual model, visible before network loading.
        with Image.open(out/'overview.png') as original:
            frame=original.convert('RGB');frame.thumbnail((880,600))
            buffer=BytesIO();frame.save(buffer,format='JPEG',quality=80,optimize=True)
        poster='data:image/jpeg;base64,'+base64.b64encode(buffer.getvalue()).decode()
    template=template.replace('/*POSTER*/',json.dumps(poster))
    if len(template)>512000:raise ValueError('交互预览 HTML 超出前端大小上限')
    (out/'index.html').write_text(template)


def plan_svg(e):
    """A measured, labelled plan, derived from the same authored rectangles."""
    from html import escape
    W,_,D=e.c['size'];s=7;pad=40
    out=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W*s+pad*2}" height="{D*s+pad*2}" viewBox="{-pad} {-pad} {W*s+pad*2} {D*s+pad*2}">',
         '<rect x="-40" y="-40" width="100%" height="100%" fill="#f4f1e7"/>',
         f'<rect width="{W*s}" height="{D*s}" fill="#dce3ce" stroke="#687066" stroke-width="2"/>']
    # Low Z is the entrance, so the front remains at the bottom on the plan.
    def rect(r,color,stroke='#777'):
        a,b,c,d=r;return f'<rect x="{a*s}" y="{(D-d)*s}" width="{(c-a)*s}" height="{(d-b)*s}" fill="{color}" stroke="{stroke}"/>'
    for c in e.c['corridors']:out.append(rect(c['rect'],'#bd9a6d'))
    for b in e.c['buildings']:
        out.append(rect(b['rect'],'#8c9398','#454d55'))
        x0,z0,x1,z1=b['rect'];x=(x0+x1)/2*s;y=(D-(z0+z1)/2)*s
        out.append(f'<text x="{x}" y="{y-3}" text-anchor="middle" font-family="sans-serif" font-size="12" fill="white">{escape(b["name"])}</text>')
        out.append(f'<text x="{x}" y="{y+13}" text-anchor="middle" font-family="sans-serif" font-size="11" fill="white">{b["levels"]} 层</text>')
    cx,cz=e.c['pond']['center'];rx,rz=e.c['pond']['radius']
    out.append(f'<ellipse cx="{cx*s}" cy="{(D-cz)*s}" rx="{rx*s}" ry="{rz*s}" fill="#88b6c3" stroke="#416c7d"/>')
    out.append(rect(e.c['water_garden']['rect'],'#88b6c3'))
    out.append(rect(e.c['crop_garden']['rect'],'#dbc78a'))
    for x,z,h in e.c['trees']:out.append(f'<circle cx="{x*s}" cy="{(D-z)*s}" r="{3*s}" fill="#8dab6d" opacity=".8"/>')
    out.append(f'<text x="{W*s/2}" y="{D*s+25}" text-anchor="middle" font-size="15" font-family="sans-serif">前门入口 · 宽 {W} 格，纵深 {D} 格</text>')
    out.append('<text x="0" y="-15" font-size="14" font-family="sans-serif">灰瓦庭院庄园 · 独立参考蓝图</text>')
    return ''.join(out)+'</svg>'
