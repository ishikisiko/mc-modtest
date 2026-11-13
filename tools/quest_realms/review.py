"""用当前模型的实际渲染图生成审阅页；不生成或修饰截图。"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from .model import read


def export(folder, base):
    folder=Path(folder);manifest=json.loads((folder/'screenshots.json').read_text());maps=[]
    for ident in (ident for ident in read('catalog')['ids'] if ident in manifest):
        spec=read(ident);shots=manifest[ident]
        maps.append(dict(id=ident,name=spec['name'],subtitle=spec['subtitle'],notes=spec['composition'],
                         overview=base+shots['overview'],player=base+shots['player'],detail=base+shots['detail']))
    data=json.dumps(maps,ensure_ascii=False).replace('</','<\\/')
    page='''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>秘境关卡模型审阅</title><style>
*{box-sizing:border-box}html,body{margin:0;background:transparent;color:var(--foreground,#e5e7de);font-family:var(--font-sans,system-ui);line-height:1.6;font-size:14px}button,a{font:inherit}nav{display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:6px;margin-bottom:10px}button{border:1px solid var(--border,#42514b);background:var(--secondary,#293534);color:inherit;border-radius:7px;padding:8px 5px;cursor:pointer;font-size:12px}button[aria-selected="true"]{background:var(--primary,#c4d3ae);color:var(--primary-foreground,#18271d)}.top{display:flex;align-items:baseline;gap:10px;flex-wrap:wrap;margin-bottom:8px}.top strong{font-size:17px}.top span{font-size:12px;color:var(--muted-foreground,#a9b6ae)}a{color:var(--info,#99c6c9);text-decoration:none;font-size:12px}.top a{margin-left:auto}img{width:100%;display:block;border-radius:8px;aspect-ratio:1.6;object-fit:contain}.pair{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-top:10px}figure{margin:0}figcaption{font-size:11px;color:var(--muted-foreground,#a9b6ae);margin-top:4px}ul{margin:10px 0;padding-left:19px;color:var(--muted-foreground,#a9b6ae);font-size:12px}li{margin:4px 0}.footer{font-size:11px;color:var(--muted-foreground,#a9b6ae);margin:9px 0 0}button:focus-visible,a:focus-visible{outline:2px solid var(--ring,#99c6c9);outline-offset:2px}@media(max-width:520px){nav{grid-template-columns:repeat(2,1fr)}.top a{margin-left:0}.pair{gap:7px}}
</style></head><body><nav aria-label="切换地图" id="tabs"></nav><div class="top"><strong id="name"></strong><span id="subtitle"></span><a id="interactive" target="_blank" rel="noopener">打开三维地图 ↗</a></div><figure><img id="overview" alt="地图整体构图"><figcaption>整体构图 · 模型渲染；洞窟全景采用移除岩顶的剖视</figcaption></figure><div class="pair"><figure><img id="player" alt="玩家高度的地图视角"><figcaption>玩家高度的模型视角</figcaption></figure><figure><img id="detail" alt="地图内部或机关附近的视角"><figcaption>内部空间与近处构造</figcaption></figure></div><ul id="notes"></ul><p class="footer">模型外观待确认。玩家视角保留完整屋顶与岩顶；游戏战斗和远征实例尚未接入。<a href="BASEquest-realms-redesign.zip">下载预览包</a> · <a href="BASEbrowser-check.json">查看预览检查</a></p><script id="map-data" type="application/json">MAPDATA</script><script>
(()=>{const maps=JSON.parse(document.getElementById('map-data').textContent);const $=id=>document.getElementById(id);function select(i){const m=maps[i];$('name').textContent=m.name;$('subtitle').textContent=m.subtitle;for(const key of ['overview','player','detail'])$(key).src=m[key];$('interactive').href='BASEindex.html?map='+m.id;$('notes').replaceChildren();for(const t of m.notes){const li=document.createElement('li');li.textContent=t;$('notes').append(li)}for(const b of $('tabs').children)b.setAttribute('aria-selected',Number(b.dataset.index)===i?'true':'false')}maps.forEach((m,i)=>{const b=document.createElement('button');b.dataset.index=i;b.textContent=m.name;b.onclick=()=>select(i);$('tabs').append(b)});select(0)})();
</script></body></html>'''.replace('MAPDATA',data).replace('BASE',base)
    (folder/'review.html').write_text(page)
    return page


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--base',required=True)
    a=p.parse_args()
    if not a.base.startswith('https://assets.ishiki333hhh.win/')or not a.base.endswith('/'):
        p.error('审阅资源使用约定的 HTTPS 域名与目录')
    export(a.out,a.base)


if __name__=='__main__':main()
