#!/usr/bin/env python3
"""串行生成所选省城的同源蓝图、预览和原生数据包，保留逐城失败。"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import traceback

ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools'))
from province_capitals.blueprint_plan.export_minecraft import export,write
from province_capitals.blueprint_plan.generate import read,region_id

CATALOG=ROOT/'blueprints/province_capitals/blueprint_plan/catalog.json'


def run(catalog,bases,out,regions=None):
    catalogue=read(catalog);entries=catalogue['cities'];known={c['region'] for c in entries}
    if catalogue['schema']!=1 or len(known)!=len(entries):raise ValueError('城市目录身份重复或schema不符')
    selected=list(regions or [c['region'] for c in entries])
    if len(selected)!=len(set(selected)) or set(selected)-known:raise ValueError('选择了重复或未知城市')
    required={c['region'] for c in entries if c['region'] in selected and read(ROOT/c['input']).get('backend')!='manor_relayout'}
    if required-bases.keys():raise ValueError('所选城市缺基础资源路径：'+str(sorted(required-bases.keys())))
    out=Path(out).resolve()
    if out.exists():raise ValueError('每轮输出须使用新目录，不能覆盖失败证据')
    out.mkdir(parents=True)
    report={'selected':selected,'completed':[],'failed':[],'unrun':selected[:],
            'cities':{},'runtime':'not_run','owner_acceptance':'not_verified',
            'catalog_sha256':hashlib.sha256(Path(catalog).read_bytes()).hexdigest()}
    write(out/'result.json',report)
    by_region={c['region']:c for c in entries}
    for region in selected:
        entry=by_region[region];source=ROOT/entry['input']
        try:
            if region_id(read(source))!=region:raise ValueError('目录与蓝图城市身份不符')
            base=Path(bases[region]) if region in bases else ROOT
            base=base if base.is_absolute() else ROOT/base
            inventory=export(source,base,out/region/'minecraft',preview_out=out/region/'preview')
            report['cities'][region]={'name':entry['name'],'status':'pass','source_fingerprint':inventory['source_fingerprint'],
                                    'tiles':len(inventory['tiles']),'blocks':inventory['blocks'],
                                    'block_entities':inventory['block_entities']}
            report['completed'].append(region);report['unrun'].remove(region)
        except Exception as error:
            (out/(region+'-failure.log')).write_text(traceback.format_exc())
            report['failed'].append(region);report['unrun'].remove(region)
            report['cities'][region]={'name':entry['name'],'status':'failed','error':str(error)}
            write(out/'result.json',report)
            raise
        write(out/'result.json',report)
    formal=all(read(ROOT/by_region[r]['input']).get('backend')=='manor_relayout' for r in selected)
    title='七府正式蓝图' if formal and len(selected)==7 else '省城正式蓝图'
    links=''.join(f'<li><a href="{r}/preview/index.html">{by_region[r]["name"]}完整蓝图与三维</a> · '
                  +(f'<a href="{r}/preview/blueprint.svg">总平面 SVG</a> · ' if formal else '')+
                  f'<a href="{r}/minecraft/{r}-blueprint-trial.zip">原生数据包</a></li>' for r in selected)
    (out/'index.html').write_text('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>'+title+'</title>'
        '<style>body{font:16px/1.8 system-ui;margin:32px;max-width:900px}li{margin:16px 0}</style>'
        '<h1>'+title+'</h1><p>各城蓝图、三维与数据包使用同一份实际方块规划。此页记录离线生成结果；游戏运行尚未验证。</p>'
        '<ul>'+links+'</ul><p>此处为独立试放输出；默认城市使用当前模组的正式资源，运行验收另附报告。</p></html>')
    return report


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--catalog',type=Path,default=CATALOG)
    p.add_argument('--bases',type=Path,help='中州等原建筑适配器需要的基础资源映射；七府正式蓝图直接读取 authored 输入')
    p.add_argument('--out',type=Path,required=True)
    p.add_argument('--regions',nargs='+')
    a=p.parse_args();print(json.dumps(run(a.catalog,read(a.bases) if a.bases else {},a.out,a.regions),ensure_ascii=False))


if __name__=='__main__':main()
