#!/usr/bin/env python3
"""将已完成的七城正式交付汇总成不依赖跨域 fetch 的中文选图页。"""
import argparse
import hashlib
import json
import shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
REGIONS=('beiyuan','nanjiang','lingyue','ximo','donghai','donghuang','moyu')


def render(delivery,base):
    delivery=Path(delivery)
    if not base.startswith('https://assets.ishiki333hhh.win/'):raise ValueError('面向 T3 的资源使用既定 HTTPS 域名')
    base=base.rstrip('/')+'/'
    result=json.loads((delivery/'result.json').read_text())
    if set(result['selected'])!=set(REGIONS) or set(result['completed'])!=set(REGIONS) or result['failed'] or result['unrun']:raise ValueError('必须七城全部完成后再制作正式展示')
    rows=[]
    for region in REGIONS:
        data=json.loads((delivery/region/'preview/plan.json').read_text())
        source=f'blueprints/province_capitals/blueprint_plan/layouts/{region}.json'
        raw=(ROOT/source).read_bytes();layout=json.loads(raw)
        if hashlib.sha256(raw).hexdigest()!=data['source_hashes'][source]:raise ValueError('当前布局与交付输入不同源：'+region)
        if data['checks']['status']!='pass' or data['source_fingerprint']!=result['cities'][region]['source_fingerprint']:
            raise ValueError('蓝图身份或构造状态不符：'+region)
        rows.append({'region':region,'name':result['cities'][region]['name'],'before':layout['expected']['houses_before'],
                     'after':data['checks']['houses'],'services':layout['expected']['services'],'manor':data['manor'],
                     'landmark':data['landmark'],'connected':data['checks']['connected_gardens'],'gardens':data['checks']['gardens'],
                     'sheet':base+region+'/preview/blueprint.svg','interactive':base+region+'/preview/index.html',
                     'pack':base+region+'/minecraft/'+region+'-blueprint-trial.zip','layout':base+'authored/layouts/'+region+'.json'})
    encoded=json.dumps(rows,ensure_ascii=False,separators=(',',':')).replace('<','\\u003c')
    template=(Path(__file__).parent/'seven_manor_review.html').read_text()
    return template.replace('__SUMMARY_DATA__',encoded).replace('https://assets.ishiki333hhh.win/previews/formal-seven-manors-20261031/',base)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--delivery',type=Path,required=True)
    parser.add_argument('--base',required=True);args=parser.parse_args()
    page=render(args.delivery,args.base)
    authored=args.delivery/'authored';(authored/'layouts').mkdir(parents=True,exist_ok=True)
    for region in REGIONS:
        for sub in ('','layouts/'):
            shutil.copy2(ROOT/f'blueprints/province_capitals/blueprint_plan/{sub}{region}.json',authored/sub/(region+'.json'))
    (args.delivery/'review.html').write_text(page)
