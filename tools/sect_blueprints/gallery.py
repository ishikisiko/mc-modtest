"""同源宗门模型合集；只导出网页，正式游戏资源仍由 runtime.py 负责。"""
import argparse,json
from pathlib import Path
from sect_blueprints.generate import ROOT,export

def build(out,base_url):
    catalog=json.loads((ROOT/'blueprints/sects/catalog.json').read_text());buttons=[]
    for row in catalog['layouts']:
        key=row['id'].split(':')[1];source=ROOT/'blueprints/sects'/row['source'];spec=json.loads(source.read_text())
        export(source,out/key,preview_only=True)
        buttons.append(f'<button data-key="{key}">{spec["name"]}</button>')
    html='''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>宗门布局与景观</title><style>
    :root{color:var(--foreground);font-family:var(--font-sans,system-ui)}html,body{margin:0;background:transparent}.tabs{display:flex;flex-wrap:wrap;gap:6px;margin-bottom:10px}button{font:inherit;font-size:12px;border:1px solid var(--border,#435a50);border-radius:var(--radius,8px);padding:8px 10px;background:var(--secondary,#23382d);color:var(--foreground,#dbe8df);cursor:pointer}button.active{background:var(--primary,#8ab196);color:var(--primary-foreground,#14271d)}iframe{border:0;width:100%;height:960px;display:block}p{font-size:12px;color:var(--muted-foreground,#9caf9f);margin:0 0 10px}</style></head><body><div class="tabs">__BUTTONS__</div><p>三种空间骨架，各有紧凑版与较大版。拖动模型旋转；“查看动线”显示内部路线。预览材质按方块分类示意。</p><iframe id="model" title="宗门同源三维模型"></iframe><script>
    const base=__BASE__;function select(key){document.getElementById('model').src=base+'/'+key+'/index.html';document.querySelectorAll('button').forEach(b=>b.classList.toggle('active',b.dataset.key===key));}document.querySelectorAll('button').forEach(b=>b.onclick=()=>select(b.dataset.key));select('valley_courts');</script></body></html>'''
    (out/'index.html').write_text(html.replace('__BUTTONS__',''.join(buttons)).replace('__BASE__',json.dumps(base_url)),encoding='utf-8')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--base-url',required=True);a=p.parse_args();build(a.out,a.base_url.rstrip('/'))
