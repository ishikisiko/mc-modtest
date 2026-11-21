"""使用当前 MC/模组的真实模型与贴图生成工作台三视图，不启动游戏。"""
from __future__ import annotations
import argparse,hashlib,io,json,subprocess
from pathlib import Path
import numpy as np
from PIL import Image
from .catalogue import Assets,ROOT,ASSETS
from ..beastgen import cuboid,anim
from ..beastgen.preview import Mesh,Frame,render

VIEWS=[('quarter','斜前方',(-1,.4,-1.1)),('front','正面',(0,.12,-1)),('back','斜后方',(1,.35,1.1))]

def bake(root,client):
    cache=Path.home()/'.gradle/caches';folder=root/'reports/drop-workbench/bake';folder.mkdir(parents=True,exist_ok=True)
    candidates=list((cache/'neoformruntime/intermediate_results').glob('rename_*output.jar'))
    if not candidates:raise FileNotFoundError('缺少 Minecraft 1.21.1 已映射模型缓存')
    # 精确采用启动器清单的依赖版本，不能把缓存内不同版本的库混用。
    manifest=json.loads((cache/'neoformruntime/artifacts/minecraft_1.21.1_version_manifest.json').read_text());jars=[]
    for lib in manifest['libraries']:
        group,name,version=lib['name'].split(':')[:3];found=list((cache/'modules-2/files-2.1'/group/name/version).glob('*/*.jar'))
        if not found:raise FileNotFoundError('缺少模型烘焙依赖：'+lib['name'])
        jars.append(found[0])
    cp=':'.join(map(str,[sorted(candidates)[0],*jars,client]));source=root/'tools/drop_workbench/BakeVanillaModels.java';out=folder/'vanilla-models.json'
    identity=hashlib.sha256(source.read_bytes()+hashlib.file_digest(sorted(candidates)[0].open('rb'),'sha256').digest()).hexdigest();stamp=folder/'identity'
    if out.exists() and stamp.exists() and stamp.read_text()==identity:return json.loads(out.read_text())
    for command in [['javac','-proc:none','-cp',cp,'-d',str(folder),str(source)],['java','-Djava.awt.headless=true','-cp',str(folder)+':'+cp,'BakeVanillaModels',str(out)]]:
        p=subprocess.run(command,capture_output=True,text=True);(folder/('javac.log' if command[0]=='javac' else 'java.log')).write_text(p.stdout+p.stderr)
        if p.returncode:raise RuntimeError('模型烘焙失败，请查看 '+str(folder))
    stamp.write_text(identity);return json.loads(out.read_text())

def texture(reader,id):
    ns,n=id.split(':',1);raw=reader.read('assets/'+ns+'/'+n)
    if raw is None:raise FileNotFoundError(id)
    return np.asarray(Image.open(io.BytesIO(raw)).convert('RGBA'),np.float32)/255,hashlib.sha256(raw).hexdigest()

def vanilla(reader,layers,spec):
    tex,sha=texture(reader,spec['texture']);vertices=np.asarray(layers[spec['layer']],float);out=[]
    for v in vertices.reshape((-1,4,5)):
        points=np.c_[-v[:,0],1.501-v[:,1],v[:,2]];out.append((points,v[:,3:5]*16,tex,None))
    return out,sha

def model_quads(root,reader,id):
    ns,n=id.split(':',1);base=root/'src/main/resources/assets'/ns
    model=base/'beast'/f'{n}_model.json'
    if not model.exists():model=base/'npc'/f'{n}_model.json'
    doc=json.loads(model.read_text());m=cuboid.model_from_json(doc);inputs=[model]
    texid=f'{ns}:textures/entity/{n}/{n}.png';tex,sha=texture(reader,texid)
    eyes=base/'textures/entity'/n/f'{n}_eyes.png'
    glow=texture(reader,f'{ns}:textures/entity/{n}/{n}_eyes.png')[0] if eyes.exists() else None
    pose=anim.posed_matrices(m,{},look=None)
    quads=[]
    if 'surface_mesh' in doc:
        if model.parent.name == 'npc':
            quads.extend(Mesh(m,tex,glow,scale=m.scale).world(pose))
        sns,sname=doc['surface_mesh'].split(':',1);path=root/'src/main/resources/assets'/sns/sname;m.surface=json.loads(path.read_text());inputs.append(path)
    quads.extend(Mesh(m,tex,glow,scale=m.scale).world(pose))
    return quads,{'model':str(model.relative_to(root/'src/main/resources')),'texture':texid,'texture_sha256':sha,'model_sha256':hashlib.sha256(b''.join(p.read_bytes() for p in inputs)).hexdigest()}

def frame_for(quads,direction,width=384,height=320):
    points=np.concatenate([q[0] for q in quads]);center=(points.min(axis=0)+points.max(axis=0))/2
    probe=Frame(direction,width,height,1,center,ss=2);p=probe.cam.project(points);extent=np.ptp(p[:,:2],axis=0)
    return Frame(direction,width,height,min(width*.81/max(extent[0],.01),height*.81/max(extent[1],.01)),center,ss=2)

def build(root=ROOT,client=None):
    client=client or Path.home()/'.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar';reader=Assets(root,client);layers=bake(root,client)
    source=json.loads((root/'tools/drop_workbench/entity_models.json').read_text());entities=json.loads((root/'reports/drop-workbench/site/catalogue.json').read_text())['entities'];folder=root/'reports/drop-workbench/portraits';(folder/'entities').mkdir(parents=True,exist_ok=True);manifest={}
    extra={'sheep':[('minecraft:sheep#fur','minecraft:textures/entity/sheep/sheep_fur.png')],'stray':[('minecraft:stray#outer','minecraft:textures/entity/skeleton/stray_overlay.png')],'bogged':[('minecraft:bogged#outer','minecraft:textures/entity/skeleton/bogged_overlay.png')],'drowned':[('minecraft:drowned#outer','minecraft:textures/entity/zombie/drowned_outer_layer.png')],'breeze':[('minecraft:breeze_wind#main','minecraft:textures/entity/breeze/breeze_wind.png')]}
    for i,e in enumerate(entities):
        id=e['id'];ns,n=id.split(':',1)
        if ns=='minecraft' or id in source or id=='yuanxian:simple_fox':
            spec=source[id] if id!='yuanxian:simple_fox' else {**source['minecraft:fox'],'texture':'yuanxian:textures/entity/simple_fox/simple_fox.png'}
            quads,sha=vanilla(reader,layers,spec);provenance={**spec,'texture_sha256':sha}
            for layer,tex in extra.get(n,[]):
                q,_=vanilla(reader,layers,{'layer':layer,'texture':tex});quads+=q
        else:quads,provenance=model_quads(root,reader,id)
        # 各视图共用当前真实几何，自动取景仅改变相机和缩放。
        views=[]
        for key,label,direction in VIEWS:
            file=f'{ns}-{n}-{key}.png';im=render(quads,frame_for(quads,direction),bg=(35,48,48),ground=False,cull=False);im.save(folder/'entities'/file,optimize=True)
            views.append({'id':key,'name':label,'url':ASSETS+'entities/'+file})
        note=provenance.get('note','当前模型与贴图的离线3D预览。装备与动作会随游戏状态变化。')
        manifest[id]={'views':views,'kind':'offline_model','note':note,'source':provenance}
        if (i+1)%10==0:print(f'已生成 {i+1}/{len(entities)} 种生物',flush=True)
    (folder/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');print('三视图完成：'+str(len(manifest)*3),flush=True)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--client',type=Path);a=p.parse_args();build(a.root,a.client)
