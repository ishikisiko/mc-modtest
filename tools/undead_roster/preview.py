"""从真实生成模型制作中文地域画廊和动作逐帧预览；复用已有资源服务。"""
from __future__ import annotations
import argparse,json,hashlib
from pathlib import Path
from PIL import Image,ImageDraw
from tools.gen_undead_roster import catalogue,ROOT
from tools.beastgen.preview import Beast,Frame,render,CAMS
from tools.beastgen.undead import appearance
from tools.beastgen.build import paths
from tools.combat_preview.sheets import font

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,default=ROOT/'reports/undead-roster/preview');p.add_argument('--url',default='https://assets.ishiki333hhh.win/undead-polish-20261104/preview');p.add_argument('--stills-only',action='store_true');a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    data=catalogue();cards=[]
    for v in data['variants']:
        beast=Beast(v['id']);fr=Frame(CAMS['q_front'][0],260,300,108,(0,1,0),ss=2)
        render(beast.quads(),fr).save(a.out/(v['id']+'.png'))
        views=[]
        for camera,label,direction in [('front','正面',CAMS['front'][0]),('side','左侧',CAMS['side'][0]),('back','背面',(0,.1,1))]:
            filename=v['id']+'_'+camera+'.png'
            render(beast.quads(),Frame(direction,260,300,108,(0,1,0),ss=2)).save(a.out/filename)
            views.append({'label':label,'file':filename})
        detail=Image.new('RGB',(520,260))
        for i,direction in enumerate([CAMS['front'][0],CAMS['q_front'][0]]):
            detail.paste(render(beast.quads(),Frame(direction,260,260,380,(0,1.78,0),ss=2),ground=False),(i*260,0))
        detail.save(a.out/(v['id']+'_detail.png'))
        frames=[]
        for move in ([] if a.stills_only else v['moves']):
            name=move['animation'];mframes=[]
            for stage,tick in [('起手',move['turn_lock_tick']),('命中',move['strike_ticks'][0]),('收招',move['strike_ticks'][-1]+6)]:
                filename=v['id']+'_'+name+'_'+str(tick)+'.png'
                # Fixed camera around the actual actor position so the full pounce remains visible.
                forward,lift=beast.place(name,tick/20)
                fr2=Frame(CAMS['q_front'][0],300,300,104,(0,1+lift/2,-forward),ss=2)
                render(beast.quads(name,tick/20),fr2).save(a.out/filename)
                mframes.append({'stage':stage,'tick':tick,'file':filename})
            frames.append({'name':move['name'],'counter':move['counter'],'frames':mframes})
        cards.append({**{k:v[k] for k in ['id','name','region_name','region','family','habitat']},'moves':frames,
                      'views':views,'description':appearance()['variants'][v['id']]['description'],
                      'cubes':len(beast.model.cubes()),
                      'model_sha256':hashlib.sha256(paths(v['id'])['model'].read_bytes()).hexdigest(),
                      'texture_sha256':hashlib.sha256(paths(v['id'])['texture'].read_bytes()).hexdigest()})
    sheet=Image.new('RGB',(1560,1032),(224,226,222));draw=ImageDraw.Draw(sheet)
    for row,family in enumerate(['corpse','archer']):
        for i,v in enumerate(c for c in cards if c['family']==family):
            x=(i%3)*260+row*780;y=(i//3)*344
            sheet.paste(Image.open(a.out/(v['id']+'.png')),(x,y))
            draw.text((x+8,y+305),v['name'],font=font(17),fill=(28,35,31))
            draw.text((x+8,y+326),v['region_name']+' · '+str(v['cubes'])+' 个体块',font=font(12),fill=(68,75,70))
    sheet.save(a.out/'contact.png')
    js=json.dumps(cards,ensure_ascii=False)
    html='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
:root{color-scheme:light dark}body{margin:0;color:var(--foreground);font:14px/1.6 var(--font-sans,sans-serif)}button,select{font:inherit;color:var(--foreground);background:var(--secondary);border:1px solid var(--border);border-radius:var(--radius,8px);padding:6px 10px;cursor:pointer}nav{display:flex;gap:8px;flex-wrap:wrap;margin:0 0 12px}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.card{background:var(--card);padding:16px;border-radius:var(--radius,10px)}h3{margin:0}p{margin:6px 0}.muted{color:var(--muted-foreground)}.idle{width:100%;max-height:270px;object-fit:contain;image-rendering:pixelated}.frames{display:flex;gap:4px}.frames figure{margin:0;min-width:0;flex:1}.frames img{width:100%;image-rendering:pixelated}figcaption{font-size:11px;text-align:center}summary{cursor:pointer;margin-top:8px;font-weight:600}.tags{font-size:12px;color:var(--muted-foreground)}@media(max-width:470px){.grid{grid-template-columns:1fr}}
</style><p class="muted">真实生成模型的离线预览 · 18 款 · 起手、命中与收招取同一份攻击配置。外观与实机手感待人工确认。</p><nav><select id="region"><option value="">全部地区</option></select><button id="corpse">行尸</button><button id="archer">骸弓</button><button id="all">两类都看</button></nav><div class="grid" id="cards"></div><script>
const entries=DATA,base=URL;let family='';const select=document.querySelector('#region');for(const r of [...new Set(entries.map(v=>v.region_name))]){const o=document.createElement('option');o.value=r;o.textContent=r;select.append(o)}
function draw(){document.querySelector('#cards').innerHTML=entries.filter(v=>(!family||v.family===family)&&(!select.value||select.value===v.region_name)).map(v=>`<article class="card"><h3>${v.name}</h3><p class="tags">${v.region_name} · ${v.family==='corpse'?'行尸':'骸弓'} · ${v.cubes} 个实际体块</p><img class="idle" src="${base}/${v.id}.png" alt="${v.name}"><p>${v.description}</p><details><summary>查看正面、侧面、背面和头部细节</summary><div class="frames">${v.views.map(f=>`<figure><img src="${base}/${f.file}" alt="${v.name}${f.label}"><figcaption>${f.label}</figcaption></figure>`).join('')}</div><img class="idle" src="${base}/${v.id}_detail.png" alt="${v.name}头部细节"></details><p class="muted">${v.habitat}</p>${v.moves.map(m=>`<details><summary>${m.name}</summary><p>${m.counter}</p><div class="frames">${m.frames.map(f=>`<figure><img src="${base}/${f.file}" alt="${m.name}${f.stage}"><figcaption>${f.stage} · 第 ${f.tick} 帧</figcaption></figure>`).join('')}</div></details>`).join('')}</article>`).join('')}
select.onchange=draw;for(const id of ['corpse','archer','all'])document.querySelector('#'+id).onclick=()=>{family=id==='all'?'':id;draw()};draw();</script></html>'''.replace('DATA',js).replace('URL',json.dumps(a.url))
    (a.out/'index.html').write_text(html);(a.out/'manifest.json').write_text(json.dumps(cards,ensure_ascii=False,indent=2)+'\n');print(a.out/'index.html')
if __name__=='__main__':main()
