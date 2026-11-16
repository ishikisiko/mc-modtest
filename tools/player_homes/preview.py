"""读取现有月洞构件的模型和像素材质，复用原版蓝图烘焙。"""
from functools import lru_cache
from pathlib import Path
import base64,gzip,hashlib,json,math,zipfile
from collections import defaultdict
from PIL import Image

from tools.player_estates.preview import NativeModels,parse,packed

ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent


class HomeModels(NativeModels):
    @lru_cache(None)
    def read(self,path):
        local=ROOT/'src/main/resources'/path
        return json.loads(local.read_text())if local.is_file()else super().read(path)

    @lru_cache(None)
    def models(self,block):
        name,props=parse(block)
        if not name.startswith('yuanxian:'):return super().models(block)
        ns,key=name.split(':',1);raw=self.read(f'assets/{ns}/blockstates/{key}.json')
        for text,value in raw['variants'].items():
            if all(props.get(k)==v for k,v in(p.split('=')for p in text.split(',')if p)):
                return value if isinstance(value,list)else[value]
        raise ValueError('缺少现有构件状态模型 '+block)

    def texture(self,key):
        if not key.startswith('yuanxian:'):return super().texture(key)
        if key not in self.textures:
            local=ROOT/'src/main/resources/assets/yuanxian/textures'/(key.split(':',1)[1]+'.png')
            self.textures[key]=Image.open(local).convert('RGBA')
        return key

    @lru_cache(None)
    def faces(self,block):
        if block.startswith('yuanxian:')and all(not self.model(v['model']).get('elements')for v in self.models(block)):return []
        return super().faces(block)

    @lru_cache(None)
    def solid(self,block):
        name,p=parse(block)
        if name=='yuanxian:courtyard_floor':return int(p['variant'])in(0,1)
        if name=='yuanxian:courtyard_wall':return int(p['variant'])in(0,1)
        if name.startswith('yuanxian:'):return False
        return super().solid(block)


def views_for(site,plot=False):
    W,_,D=site.c['size'];span=max(W,D);center=[W/2,5,D/2]
    return {
        'overview':{'name':'全貌','eye':[-span*.65,span*.82,-span*.72],'target':center},
        'front':{'name':'正面','eye':[W/2,10,-span*.8],'target':[W/2,6,D*.45]},
        'court':{'name':'院内','eye':[W*.35,8,D*.18],'target':[W*.55,6,D*.75],'fov':55},
        'cut':{'name':'去顶看房间','eye':[-span*.1,span*1.1,-span*.1],'target':[W/2,2,D/2],'cut':7},
        'top':{'name':'俯视','eye':[W/2,span*1.45,D/2-.01],'target':[W/2,2,D/2]},
        'plot':{'name':'放进宅田地块','eye':[-40,80,-45],'target':[32,4,36],'plot':True}
    }


def write_viewer(site,out,base_url):
    page=(HERE/'viewer.html').read_text()
    libraries=[base64.b64encode(gzip.compress((ROOT/p).read_bytes(),mtime=0)).decode()for p in('tools/web/three.min.js','tools/web/OrbitControls.js')]
    data={'name':site.c['name'],'description':site.c['description'],'base':base_url,
          'rev':hashlib.sha256((out/'scene.js').read_bytes()).hexdigest()[:16],
          'atlas':'data:image/png;base64,'+base64.b64encode((out/'atlas.png').read_bytes()).decode(),
          'soil':'data:image/png;base64,'+base64.b64encode((out/'plot-soil.png').read_bytes()).decode(),
          'libraries':libraries,'id':site.config['id'],'height':site.c['size'][1],
          'plot':{'size':site.c['plot_size'],'origin':site.config['plot_origin'],'house':[site.c['size'][0],site.c['size'][2]]}}
    page=page.replace('/*SETUP*/',json.dumps(data,ensure_ascii=False,separators=(',',':')))
    page=page.replace('/*SCENE*/',(out/'scene.js').read_text())
    (out/'index.html').write_text(page)


def bake_home(site,jar,out,base_url):
    site.c['views']=views_for(site)
    report=bake_instances(site,jar,out)
    report['model_source']='原版模型与当前月洞水院完整构件模型；床简化显示'
    report.pop('bed_preview',None)
    with zipfile.ZipFile(jar)as archive:(out/'plot-soil.png').write_bytes(archive.read('assets/minecraft/textures/block/dirt.png'))
    write_viewer(site,out,base_url)
    return report


def bake_instances(site,jar,out):
    """完整构件共享同一份网格，保留实际模型的全部顶点和贴图。"""
    native=HomeModels(jar)
    for block in set(site.blocks.values()):native.faces(block)
    names=sorted(native.textures);tile=18;cols=8;rows=math.ceil(len(names)/cols)
    atlas=Image.new('RGBA',(cols*tile,rows*tile),(0,0,0,0));locations={}
    for i,key in enumerate(names):
        im=native.textures[key].resize((16,16),Image.Resampling.NEAREST);x=(i%cols)*tile+1;y=(i//cols)*tile+1
        atlas.paste(im,(x,y));locations[key]=(x,y)
        atlas.paste(im.crop((0,0,1,16)),(x-1,y));atlas.paste(im.crop((15,0,16,16)),(x+16,y))
        atlas.paste(im.crop((0,0,16,1)),(x,y-1));atlas.paste(im.crop((0,15,16,16)),(x,y+16))
    atlas.save(out/'atlas.png');units={};occupied=set()
    for a in site.assemblies:
        root=tuple(a['root']);key=('assembly',a['family'],a['variant'],a['facing']);positions=[tuple(p)for p in a['parts']]
        occupied.update(positions)
        if key not in units:units[key]={'root':root,'parts':positions,'instances':[]}
        units[key]['instances'].append(root)
    for p,block in site.blocks.items():
        if p in occupied:continue
        key=('block',block)
        if key not in units:units[key]={'root':p,'parts':[p],'instances':[]}
        units[key]['instances'].append(p)
    covered=set()
    for unit in units.values():
        root=unit['root']
        for instance in unit['instances']:
            for part in unit['parts']:
                position=tuple(instance[i]+part[i]-root[i]for i in range(3))
                assert position not in covered and site.blocks[position]==site.blocks[part],('显示实例与蓝图不同',position)
                covered.add(position)
    assert covered==set(site.blocks),'显示实例没有覆盖完整蓝图'
    prototypes=[];rendered_faces=0
    for key,unit in units.items():
        root=unit['root'];groups=defaultdict(lambda:{'p':[],'n':[],'u':[],'i':[]})
        for pos in unit['parts']:
            block=site.blocks[pos];layer=site.layers[pos];delta=[pos[i]-root[i]for i in range(3)]
            for points,normal,uvs,texture,tint,cull,transparent in native.faces(block):
                group=groups[layer,tint,transparent];idx=len(group['p'])//3;tx,ty=locations[texture]
                for point,uv in zip(points,uvs):
                    group['p'].extend(delta[i]+point[i]/16 for i in range(3));group['n'].extend(round(v*127)for v in normal)
                    group['u'].extend(((tx+uv[0])/atlas.width,1-(ty+uv[1])/atlas.height))
                group['i'].extend((idx,idx+1,idx+2,idx,idx+2,idx+3));rendered_faces+=len(unit['instances'])
        for (layer,tint,transparent),g in groups.items():
            prototypes.append({'layer':layer,'tint':tint,'transparent':transparent,
                'positions':packed(g['p'],'f'),'normals':packed(g['n'],'b'),'uv':packed(g['u'],'f'),'indices':packed(g['i'],'I'),
                'instances':[list(p)for p in unit['instances']]})
    payload={'size':site.c['size'],'views':site.c['views'],'prototypes':prototypes,'blocks':len(site.blocks),'rooms':site.rooms}
    raw=json.dumps(payload,ensure_ascii=False,separators=(',',':')).encode();(out/'scene.json').write_bytes(raw)
    (out/'scene.js').write_text('window.__estatePacked='+json.dumps(base64.b64encode(gzip.compress(raw,mtime=0)).decode())+';')
    return {'mesh_prototypes':len(prototypes),'whole_units_and_single_cells':sum(len(u['instances'])for u in units.values()),
            'rendered_faces':rendered_faces,'native_textures':len(names),'geometry_simplified':False,'raw_scene_bytes':len(raw),
            'instanced_source_cells':len(covered),'missing_or_duplicate_source_cells':0}


def plan_svg(site,catalog):
    from html import escape
    W,D=catalog['plot_size'];w,_,d=site.c['size'];ox,oz=site.config['plot_origin'];scale=7
    def rect(box,fill,stroke='#6c7568',extra=''):
        a,b,c,e=box;return f'<rect x="{a*scale}" y="{(D-e)*scale}" width="{(c-a)*scale}" height="{(e-b)*scale}" fill="{fill}" stroke="{stroke}" {extra}/>'
    parts=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W*scale+40}" height="{D*scale+70}" viewBox="-20 -20 {W*scale+40} {D*scale+70}">',rect([0,0,W,D],'#c6b28d')]
    parts.append(rect([ox,oz,ox+w,oz+d],'#c8c7bd','#4c5d50'))
    for r in site.rooms:
        a,b,c,e=r['bounds'];parts.append(rect([a+ox,b+oz,c+ox,e+oz],'#777f7a'))
        parts.append(f'<text x="{(ox+(a+c)/2)*scale}" y="{(D-oz-(b+e)/2)*scale}" font-size="11" fill="white" text-anchor="middle">{escape(r["name"])}</text>')
    for b in site.config.get('farm_example',[]):parts.append(rect(b,'#a4ac7b','#637443','stroke-dasharray="4 3"'))
    parts.append(f'<text x="{W*scale/2}" y="{D*scale+24}" text-anchor="middle" font-family="sans-serif" font-size="13">64×72格地块；浅绿虚线仅示意可留给种植的位置</text></svg>')
    return ''.join(parts)
