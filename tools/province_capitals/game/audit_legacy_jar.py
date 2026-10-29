#!/usr/bin/env python3
"""旧 V1–V9 jar 资源审计：模板/地形/frozen 必须逐字节一致。"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path,PurePosixPath
import re
import zipfile

ROOT=Path(__file__).resolve().parents[3]


def digest(data):return hashlib.sha256(data).hexdigest()


def city_diagnostics(before,after):
    a,b=copy.deepcopy(before),copy.deepcopy(after)
    old=a.get('source',{}).get('hashes',{});new=b.get('source',{}).get('hashes',{})
    if old.keys()!=new.keys():return False,[]
    changes=[]
    for path,value in new.items():
        if old[path]==value:continue
        p=PurePosixPath(path)
        if p.is_absolute() or '..' in p.parts or not (ROOT/path).is_file() or digest((ROOT/path).read_bytes())!=value:
            return False,[]
        changes.append({'field':'source.hashes.'+path,'before':old[path],'after':value})
        new[path]=old[path]
    return a==b and bool(changes),changes


def index_diagnostics(before,after,jar,version):
    a,b=copy.deepcopy(before),copy.deepcopy(after);changes=[]
    if len(a.get('cities',[]))!=len(b.get('cities',[])):return False,[]
    for old,new in zip(a['cities'],b['cities']):
        if old['city_id']!=new['city_id']:return False,[]
        manifest_name=f'data/myvillage/capitals/{version}/{new["city_id"]}/city.json'
        raw=jar.read(manifest_name);manifest=json.loads(raw)
        if new.get('raw_city_json_sha256')!=digest(raw):return False,[]
        resources=['data/myvillage/'+manifest['terrain']['resource'].split(':',1)[1]]
        resources += ['data/myvillage/structure/'+t['template'].split(':',1)[1]+'.nbt' for t in manifest['tiles']]
        size=len(raw)+sum(len(jar.read(name)) for name in resources)
        if new['bytes']!=size:return False,[]
        for field in ['raw_city_json_sha256','bytes']:
            if old.get(field)!=new.get(field):
                changes.append({'city_id':new['city_id'],'field':field,'before':old.get(field),'after':new.get(field)})
                if field in old:new[field]=old[field]
                else:new.pop(field,None)
    if after['total_bytes']!=sum(c['bytes'] for c in after['cities']):return False,[]
    b['total_bytes']=a['total_bytes']
    return a==b,changes


def compare(old_path,new_path):
    result={'old_jar_sha256':digest(Path(old_path).read_bytes()),'new_jar_sha256':digest(Path(new_path).read_bytes()),'selected_files':0,'byte_identical':0,'diagnostic_changes':[],'failures':[]}
    with zipfile.ZipFile(old_path) as old,zipfile.ZipFile(new_path) as new:
        names=new.namelist()
        if len(names)!=len(set(names)):result['failures'].append({'kind':'duplicate_jar_entries'})
        selected=[n for n in old.namelist() if n.startswith(('data/myvillage/capitals/','data/myvillage/structure/province_capitals/')) or (n.startswith('data/myvillage/worldplan/') and n.endswith('.json'))]
        for name in selected:
            result['selected_files']+=1
            if name not in names:result['failures'].append({'path':name,'kind':'missing'});continue
            a,b=old.read(name),new.read(name)
            if a==b:result['byte_identical']+=1;continue
            ok=False;changes=[]
            if re.fullmatch(r'data/myvillage/capitals/v[12]/capital_[a-z_]+/city.json',name):
                ok,changes=city_diagnostics(json.loads(a),json.loads(b))
            elif re.fullmatch(r'data/myvillage/capitals/v[12]/index.json',name):
                ok,changes=index_diagnostics(json.loads(a),json.loads(b),new,name.split('/')[3])
            if ok:result['diagnostic_changes'].append({'path':name,'changes':changes})
            else:result['failures'].append({'path':name,'kind':'byte_change','before':digest(a),'after':digest(b)})
    result['passed']=not result['failures']
    return result


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--old',type=Path,required=True);p.add_argument('--new',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    result=compare(a.old,a.new);a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:result[k] for k in ['selected_files','byte_identical','passed']}|{'diagnostic_changes':len(result['diagnostic_changes']),'failures':len(result['failures'])},ensure_ascii=False))
    if not result['passed']:raise SystemExit(1)


if __name__=='__main__':main()
