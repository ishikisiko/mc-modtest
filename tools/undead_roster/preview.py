"""从真实生成模型制作中文地域画廊和动作逐帧预览；复用已有资源服务。"""
from __future__ import annotations
import argparse,json
from pathlib import Path
from tools.gen_undead_roster import catalogue,ROOT
from tools.beastgen.preview import Beast,Frame,render,CAMS

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,default=ROOT/'reports/undead-roster/preview');p.add_argument('--url',default='https://assets.ishiki333hhh.win/undead-roster-20261102/preview');a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    data=catalogue();cards=[]
    for v in data['variants']:
        beast=Beast(v['id']);fr=Frame(CAMS['q_front'][0],260,300,108,(0,1,0),ss=2)
        render(beast.quads(),fr).save(a.out/(v['id']+'.png'))
        frames=[]
        for move in v['moves']:
            name=move['animation'];mframes=[]
            for stage,tick in [('起手',move['turn_lock_tick']),('命中',move['strike_ticks'][0]),('收招',move['strike_ticks'][-1]+6)]:
                filename=v['id']+'_'+name+'_'+str(tick)+'.png'
                # Fixed camera around the actual actor position so the full pounce remains visible.
                forward,lift=beast.place(name,tick/20)
                fr2=Frame(CAMS['q_front'][0],300,300,104,(0,1+lift/2,-forward),ss=2)
                render(beast.quads(name,tick/20),fr2).save(a.out/filename)
                mframes.append({'stage':stage,'tick':tick,'file':filename})
            frames.append({'name':move['name'],'counter':move['counter'],'frames':mframes})
        cards.append({**{k:v[k] for k in ['id','name','region_name','region','family','habitat']},'moves':frames})
    js=json.dumps(cards,ensure_ascii=False)
    html='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
:root{color-scheme:light dark}body{margin:0;color:var(--foreground);font:14px/1.6 var(--font-sans,sans-serif)}button,select{font:inherit;color:var(--foreground);background:var(--secondary);border:1px solid var(--border);border-radius:var(--radius,8px);padding:6px 10px;cursor:pointer}nav{display:flex;gap:8px;flex-wrap:wrap;margin:0 0 12px}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.card{background:var(--card);padding:16px;border-radius:var(--radius,10px)}h3{margin:0}p{margin:6px 0}.muted{color:var(--muted-foreground)}.idle{width:100%;max-height:270px;object-fit:contain;image-rendering:pixelated}.frames{display:flex;gap:4px}.frames figure{margin:0;min-width:0;flex:1}.frames img{width:100%;image-rendering:pixelated}figcaption{font-size:11px;text-align:center}summary{cursor:pointer;margin-top:8px;font-weight:600}.tags{font-size:12px;color:var(--muted-foreground)}@media(max-width:470px){.grid{grid-template-columns:1fr}}
</style><p class="muted">真实生成模型的离线预览 · 18 款 · 起手、命中与收招取同一份攻击配置。外观与实机手感待人工确认。</p><nav><select id="region"><option value="">全部地区</option></select><button id="corpse">行尸</button><button id="archer">骸弓</button><button id="all">两类都看</button></nav><div class="grid" id="cards"></div><script>
const entries=DATA,base=URL;let family='';const select=document.querySelector('#region');for(const r of [...new Set(entries.map(v=>v.region_name))]){const o=document.createElement('option');o.value=r;o.textContent=r;select.append(o)}
function draw(){document.querySelector('#cards').innerHTML=entries.filter(v=>(!family||v.family===family)&&(!select.value||select.value===v.region_name)).map(v=>`<article class="card"><h3>${v.name}</h3><p class="tags">${v.region_name} · ${v.family==='corpse'?'行尸':'骸弓'}</p><img class="idle" src="${base}/${v.id}.png" alt="${v.name}"><p class="muted">${v.habitat}</p>${v.moves.map(m=>`<details><summary>${m.name}</summary><p>${m.counter}</p><div class="frames">${m.frames.map(f=>`<figure><img src="${base}/${f.file}" alt="${m.name}${f.stage}"><figcaption>${f.stage} · 第 ${f.tick} 帧</figcaption></figure>`).join('')}</div></details>`).join('')}</article>`).join('')}
select.onchange=draw;for(const id of ['corpse','archer','all'])document.querySelector('#'+id).onclick=()=>{family=id==='all'?'':id;draw()};draw();</script></html>'''.replace('DATA',js).replace('URL',json.dumps(a.url))
    (a.out/'index.html').write_text(html);(a.out/'manifest.json').write_text(json.dumps(cards,ensure_ascii=False,indent=2)+'\n');print(a.out/'index.html')
if __name__=='__main__':main()
