#!/usr/bin/env python3
"""Generate the MC designs from the five independent authors, never legacy curves."""
import argparse,importlib,json,math,re,subprocess
from pathlib import Path
from mc_ornaments import decorate
ROOT=Path(__file__).resolve().parents[1]

def validate(ds,n):
    assert len(ds)==3
    assert len({d['id'] for d in ds})==3
    for d in ds:
        assert d['level']==n
        assert {c['role'] for c in d['components']} >= {'head','chest','legs','boots'}
        assert all(c['shape'] in {'box','panel'} for c in d['components']),d['id']
        assert all(c.get('bevel',0)==0 for c in d['components']),d['id']
        assert len({c['id'] for c in d['components']})==len(d['components'])
        assert all('pixelArt' in m for m in d['materials'].values())
        assert all(c['material'] in d['materials'] for c in d['components']+d['ingot']['components'])
        text=''.join([d[k] for k in ['description','silhouette','materialStory','progression']]+[p['description']for p in d['parts'].values()]+[d['ingot']['description']])
        assert len(re.findall(r'[\u3400-\u9fff]',text))>=500,(d['id'],'详细设计不足')
        json.dumps(d,allow_nan=False)
    return ds

def document(ds):
    lines=['# '+ds[0]['realm']+'：Minecraft 风格三式','','标准方块人比例、完整硬边衣甲与像素纹样。当前为 HTML 设计交付，未实装游戏。','']
    for d in ds:
        lines+=['## '+d['name'],'',d['tagline'],'',d['description'],'','### 轮廓与前后结构','',d['silhouette'],'','### 材质与灵材锭','',d['materialStory'],'',d['ingot']['name']+'：'+d['ingot']['description'],'','### 四件防具','']
        for p in d['parts'].values():lines+=['**'+p['name']+'**：'+p['description'],'']
        lines+=['### 境界进阶','',d['progression'],'',d['segmentation'],'']
    lines+=['## 验证边界','','来源为 authors/mc_levelN.py、mc_common.py 与 mc_ornaments.py 的同源组合，通过 authors/levelN.py 生成；像素材质由同一数据导出到 HTML 和 PNG。局部技术检查不代替用户外观认可、游戏动作、性能和多人验收。','']
    return '\n'.join(lines)

def main(level=None):
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--engine-check',action='store_true');p.add_argument('--levels',default=str(level) if level else '1,2,3,4,5');a=p.parse_args()
    for n in map(int,a.levels.split(',')):
        ds=validate([decorate(d) for d in importlib.import_module('mc_level'+str(n)).build()],n)
        outputs={ROOT/'levels'/f'level{n}.json':json.dumps(ds,ensure_ascii=False,indent=2,allow_nan=False)+'\n',ROOT/'levels'/f'level{n}-design.md':document(ds)}
        for out,content in outputs.items():
            if a.check:assert out.read_text()==content,str(out)+' 与 MC 作者源不同'
            else:out.write_text(content)
        print(json.dumps({'level':n,'designs':len(ds),'mode':'check' if a.check else 'write'},ensure_ascii=False))
    if a.engine_check:subprocess.run(['node',str(ROOT/'checks/minecraft.js'),'--levels='+a.levels],check=True)

if __name__=='__main__':main()
