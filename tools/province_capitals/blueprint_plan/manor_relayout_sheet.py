"""从同次最终施工格制作可缩放总平面，不另造示意建筑。"""
from html import escape

from .manor_relayout import AIR,manor_catalog
from .manor_relayout_preview import properties
from province_capitals.regional.v2.preview import SceneBuilder


def svg(planner,data):
    w,_,d=data['dimensions'];pad=18
    catalog=manor_catalog();banks={'myvillage:'+b['id']:b for b in catalog['banks']}
    cubes={'myvillage:'+m['id']:m for m in catalog['materials']}
    palette=SceneBuilder(planner.model);colors=planner.manor_scene['materials'];top={}
    for key in sorted(planner.model.construction.by_tile):
        for (x,y,z),(state,tag,layer) in planner.model.construction.tile(*key).items():
            if state==AIR:continue
            x+=key[0]*16;z+=key[1]*16
            if y<planner.ground(x,z) or y<top.get((x,z),(-1,''))[0]:continue
            base=state.split('[',1)[0]
            if base in cubes:color=colors.get(cubes[base]['role'],'#746662')
            elif base in banks:
                boxes=banks[base]['profiles'][int(properties(state)['shape'])]['boxes']
                color=colors.get(max(boxes,key=lambda b:b[4])[-1],'#746662')
            elif base=='myvillage:demon_pact_pillar':color='#65515e'
            else:color=palette.palette[palette.material(state,layer)]
            top[x,z]=(y,color)
    rows=[f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{-pad} {-pad} {w+pad*2} {d+pad*2+22}" width="1400" role="img" aria-label="{escape(data["name"])}正式总平面">',
          '<style>text{font-family:system-ui,sans-serif;fill:#ede5d1} .label{paint-order:stroke;stroke:#182d2b;stroke-width:1;stroke-linejoin:round;font-size:3.4px;text-anchor:middle} .small{font-size:3px} .grid{stroke:#e2e1c5;stroke-width:.2;opacity:.2}</style>',
          f'<rect x="{-pad}" y="{-pad}" width="{w+pad*2}" height="{d+pad*2+22}" fill="#142c2b"/>',
          f'<text x="0" y="-10" font-size="6">{escape(data["name"])}</text>',
          f'<text x="0" y="-4" class="small">正式总平面 · 一格一方块 · {w} × {d} 格 · 北 ↑</text>']
    for z in range(d):
        x=0
        while x<w:
            def color_at(xx):
                ground=planner.model.terrain[z*w+xx]
                shade=max(0,min(1,(ground-8)/48))
                return top.get((xx,z),(0,f'rgb({round(47+shade*41)},{round(67+shade*49)},{round(57+shade*33)})'))[1]
            color=color_at(x);end=x+1
            while end<w and color_at(end)==color:end+=1
            rows.append(f'<path d="M{x} {z}h{end-x}v1H{x}z" fill="{color}"/>');x=end
    for x in range(0,w+1,16):rows.append(f'<path class="grid" d="M{x} 0V{d}"/><text x="{x}" y="{d+4}" class="small" text-anchor="middle">{x}</text>')
    for z in range(0,d+1,16):rows.append(f'<path class="grid" d="M0 {z}H{w}"/><text x="-2" y="{z+1}" class="small" text-anchor="end">{z}</text>')
    def box(b,color,dashed=False):
        a,z,e,f=b;rows.append(f'<rect x="{a}" y="{z}" width="{e-a+1}" height="{f-z+1}" fill="none" stroke="{color}" stroke-width=".7"'+(' stroke-dasharray="2 1"' if dashed else '')+'/>')
    for g in data['gardens']:box(g['bounds'],'#9ee1ad' if g['access']=='private' else '#e7bd6f',True)
    box(data['manor']['bounds'],'#ffda8f');box(data['landmark']['bounds'],'#a8e5de')
    for p in data['plots']:
        b=p['building'];lo,hi=b['bounds']['min'],b['bounds']['max']
        if b['role']=='house':continue
        rows.append(f'<text x="{(lo[0]+hi[0])/2}" y="{(lo[2]+hi[2])/2}" class="label">{escape(b["name"])}</text>')
    x,_,z=data['landmark']['origin'];rows.append(f'<text x="{x}" y="{z}" class="label">{escape(data["landmark"]["name"])}</text>')
    for access in data['building_access']:
        points=' '.join(f'{x+.5},{z+.5}' for x,_,z in access['points'])
        rows.append(f'<polyline points="{points}" fill="none" stroke="#f5e8b9" stroke-width=".45"/>')
    checks=data['checks'];notes=[f'住宅 {checks["houses"]} · 功能建筑及城主府 {checks["public_buildings"]} · 私院 {checks["private_gardens"]} / 公共庭院 {checks["public_gardens"]}',
        '金框：原尺寸府邸　青框：主地标　绿虚线：私院　黄虚线：公共庭院',
        '方块格投影；构件细节见同源三维。默认世界生成未接入，游戏运行与玩家接受未验证。']
    for i,note in enumerate(notes):rows.append(f'<text x="0" y="{d+12+i*5}" class="small">{escape(note)}</text>')
    rows.append('</svg>');return '\n'.join(rows)
