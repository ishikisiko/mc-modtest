"""按所选作者地图导出独立三维模型；仅写指定预览目录。"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

from .model import ROOT, SOURCE, build, read

HERE = Path(__file__).parent


def write_json(file, data):
    file.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n')


def export(out, ids=None, minecraft_jar=None):
    out=Path(out).resolve();out.mkdir(parents=True,exist_ok=True)
    (out/'textures').mkdir(exist_ok=True)
    catalog=read('catalog');index=[];used_materials=set()
    ids=list(catalog['ids'] if ids is None else ids)
    if not ids or len(set(ids)) != len(ids) or not set(ids) <= set(catalog['ids']):
        raise ValueError('预览地图必须来自作者目录，且不能重复或为空')
    for ident in ids:
        scene=build(ident)
        used_materials.update(scene['materials'][box[6]] for box in scene['boxes'])
        write_json(out/(ident+'.json'),scene)
        (out/(ident+'.data.js')).write_text('window.QUEST_REALM_MAPS=window.QUEST_REALM_MAPS||{};window.QUEST_REALM_MAPS['+json.dumps(ident)+']='+json.dumps(scene,ensure_ascii=False,separators=(',',':'))+';\n')
        index.append(dict(id=ident,name=scene['spec']['name'],subtitle=scene['spec']['subtitle'],
                          checks=scene['checks'],size=scene['spec']['size']))
        print(ident,scene['checks']['boxes'],'个构件',flush=True)
    textures={}
    minecraft_jar=Path(minecraft_jar) if minecraft_jar else Path.home()/'.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'
    for key,material in catalog['palette'].items():
        if key not in used_materials:continue
        if material.get('namespace') == 'minecraft':
            with zipfile.ZipFile(minecraft_jar) as archive:
                data=archive.read('assets/minecraft/textures/block/'+material['file'])
        else:
            source=ROOT/'src/main/resources/assets/myvillage/textures/block'/material['file']
            data=source.read_bytes()
        (out/'textures'/(key+'.png')).write_bytes(data)
        textures[key]=dict(material,sha256=hashlib.sha256(data).hexdigest(),url='textures/'+key+'.png')
    metadata=dict(schema=1,title=catalog['title'],maps=index,palette=textures,rules=catalog['rules'])
    write_json(out/'index.json',metadata)
    local_metadata=dict(metadata,palette={key:dict(value,url='data:image/png;base64,'+base64.b64encode((out/value['url']).read_bytes()).decode('ascii')) for key,value in textures.items()})
    (out/'index.data.js').write_text('window.QUEST_REALM_INDEX='+json.dumps(local_metadata,ensure_ascii=False,separators=(',',':'))+';\n')
    for name in('viewer.js','style.css','index.html'):
        shutil.copyfile(HERE/name,out/name)
    for name in('three.min.js','OrbitControls.js'):
        shutil.copyfile(ROOT/'tools/web'/name,out/name)
    write_json(out/'checks.json',dict(status='passed',scope='authoring_and_preview',maps=index,
              owner_visual='not_verified',minecraft_runtime='not_run',collision_and_walking='not_verified'))
    inputs=[*sorted(SOURCE.glob('*.json')),*sorted(HERE.glob('*.*'))]
    write_json(out/'inputs.json',{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in inputs if p.is_file()})
    return out


def package(out):
    target=out/'quest-realms-redesign.zip'
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED)as z:
        for p in sorted(out.rglob('*')):
            if p.is_file()and p!=target:z.write(p,p.relative_to(out))
    return target


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--zip',action='store_true')
    parser.add_argument('--maps',nargs='+',help='只导出指定作者地图')
    parser.add_argument('--minecraft-jar',type=Path,help='Minecraft 1.21.1客户端jar，仅用于读取预览材质')
    args=parser.parse_args();out=export(args.out,args.maps,args.minecraft_jar)
    if args.zip:print(package(out))


if __name__=='__main__':main()
