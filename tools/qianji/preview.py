"""从最终方块模型生成交互三维预览，不另写概念体块。"""
from __future__ import annotations
from collections import defaultdict
import argparse,json,math,shutil,re,zipfile,hashlib
from pathlib import Path
from qianji.model import build,ROOT,AIR
PALETTE={'stone':['#898c89','brick'],'basalt':['#4c5055','brick'],'wall':['#dddcd0','plaster'],'darkwood':['#403832','wood'],'wood':['#6f5138','wood'],'floor':['#7b6046','wood'],'roof':['#404c58','tile'],'grass':['#6d8058','moss'],'water':['#4e8494','water'],'gold':['#bf9d55','metal'],'metal':['#a5744b','metal'],'book':['#697b6c','book'],'jade':['#4c8b85','metal'],'light':['#e4d3a3','plaster']}
def material(state):
    if 'froglight' in state:return 'light'
    if 'gold_block' in state:return 'gold'
    if 'oxidized_copper' in state:return 'jade'
    if 'copper' in state:return 'metal'
    if 'bookshelf' in state:return 'book'
    if 'grass' in state or '_leaves[' in state:return 'grass'
    if state.startswith('minecraft:water['):return 'water'
    if 'calcite' in state:return 'wall'
    if 'deepslate_tile' in state:return 'roof'
    if 'deepslate' in state:return 'basalt'
    if '_log[' in state:return 'darkwood'
    if '_planks' in state:return 'floor'
    if any(s in state for s in ['barrel','_fence[','_slab[','cartography','_sign[']):return 'wood'
    return 'stone'
def boxes(model):
    rows=defaultdict(list);special=[]
    def box(x,y,z,w,h,d,mat,layer):return [x+w/2,y+h/2,z+d/2,w,h,d,mat,layer,0,2]
    for (x,y,z),state in model.blocks.items():
        if state==AIR:continue
        mat=material(state);layer=model.layers[x,y,z]
        if '_stairs[' in state:
            special.append(box(x,y,z,1,.5,1,mat,layer));face=state.split('facing=',1)[1].split(',')[0]
            dx,dz,w,d={'north':(0,0,1,.5),'south':(0,.5,1,.5),'west':(0,0,.5,1),'east':(.5,0,.5,1)}[face]
            special.append(box(x+dx,y+.5,z+dz,w,.5,d,mat,layer))
        elif '_slab[' in state:special.append(box(x,y+(.5 if 'type=top' in state else 0),z,1,.5,1,mat,layer))
        elif '_fence[' in state:special.append(box(x+.35,y,z+.35,.3,1.4,.3,mat,layer))
        elif '_wall_sign[' in state:continue
        else:rows[y,z,mat,layer].append(x)
    runs=defaultdict(list)
    for (y,z,mat,layer),xs in rows.items():
        xs.sort();start=last=xs[0]
        for x in xs[1:]+[10**9]:
            if x==last+1:last=x;continue
            runs[y,start,last-start+1,mat,layer].append(z);start=last=x
    columns=defaultdict(list)
    for (y,x,w,mat,layer),zs in runs.items():
        zs.sort();start=last=zs[0]
        for z in zs[1:]+[10**9]:
            if z==last+1:last=z;continue
            columns[x,start,w,last-start+1,mat,layer].append(y);start=last=z
    result=[]
    for (x,z,w,d,mat,layer),ys in columns.items():
        ys.sort();start=last=ys[0]
        for y in ys[1:]+[10**9]:
            if y==last+1:last=y;continue
            result.append(box(x,start,z,w,last-start+1,d,mat,layer));start=last=y
    return result+special

def scene(ident):
    m,check=build(ident);s=m.spec;core=s['rooms'][-1];x,z,w,d=core['box'];nodes=[]
    picks=['gate','office','hall','archive_w','core'] if s['scale']=='headquarters' else [r['id'] for r in s['rooms']]
    for id in picks:
        r=next(r for r in s['rooms'] if r['id']==id);xx,zz,ww,dd=r['box'];p=[xx+ww/2,r['floor_y'],zz]
        nodes.append(dict(name=r['name'],p=p,label_p=[p[0],r['floor_y']+sum(a['height'] for a in r['levels'])+4,zz+dd/2],role='private' if id=='core' else 'public',text='；'.join(a['use'] for a in r['levels'])))
    hq=s['scale']=='headquarters';features=['总舵三阙门层高24格，前院安排委托总厅、授令厅与分阁联络。','总令大殿首层层高24格，上层只保留跨州议事回廊，中部保持大尺度通高空间。','后中天枢总阁仅设三层，层高分别为24、20、22格，分别容纳八州总图与总档、秘境机要、观天演算与掌阁议事。','万卷档库、玄枢器物库和巡州使院独立成院，屋顶大型天机仪、前院校准仪台和高处金字匾额形成总舵的专属标志。各层布置与地板齐平的铜框地灯，保留中庭和楼梯井。'] if hq else ['沿用此前确认的地块面积，各层高度根据办事、勘图、外勤与议事的用途确定。','任务榜位于公开办事空间；入口、各层楼梯和全部功能目标使用实际方块验证连通。']
    return dict(id=ident,name='中州千机阁总舵' if hq else '千机阁·'+s['name'],group='hall',sect=False,size=[m.w,m.h,m.d],boxes=boxes(m),rooms=m.rooms,nodes=nodes,routes=[dict(kind='main',points=s['paths'])],cameras=[dict(eye=[s['paths'][1][0],s['paths'][1][1]+1.65,s['paths'][1][2]],target=[x+w/2,core['floor_y']+sum(v['height'] for v in core['levels'])*.60,z+d/2])],signs=[dict(text=v['text'],p=[v['position'][0]+.5,v['position'][1]+.5,v['position'][2]-.02],size=[min(7,len(v['text'])*1.1),1.1]) for v in m.signs],tower=dict(name=core['name'],cx=x+w/2,y=core['floor_y'],z=z,w=w,d=d,floors=len(core['levels']),top=m.h),tag='按功能确定层高的完整方块建筑',goal='千机阁办理城镇委托、统筹各州外勤并管理秘境卷宗。',features=features,interior='；'.join(r['name']+'：'+r['use'] for r in m.rooms),stages=['查看完整建筑、院落和主次体量。','去顶查看各层和室内功能分配。','查看后部主楼与各院关系。'],footprint=dict(width=m.w,depth=m.d,area=m.w*m.d,large_width=108,large_depth=118,large_area=12744,fraction=m.w*m.d/12744),scale_label={'compact':'紧凑版示例','large':'大版示例','headquarters':'中州总舵'}[s['scale']],layout_family=s['scale'],checks=check)

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,default=ROOT/'reports/qianji-final-20261112');a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    scenes=[scene(n) for n in ['folded_library','island_octagon','terraced_gallery','town_courts','town_water','town_frontier','zhongzhou_headquarters']]
    base='https://assets.ishiki333hhh.win/previews/'+a.out.name
    (a.out/'scene-data.js').write_text('window.QUEST_STUDY='+json.dumps(dict(palette=PALETTE,scenes=scenes,rules=[]),ensure_ascii=False,separators=(',',':'))+';\n')
    (a.out/'design-index.json').write_text(json.dumps([{k:v for k,v in s.items() if k!='boxes'} for s in scenes],ensure_ascii=False,indent=2)+'\n')
    html=(ROOT/'tools/qianji/template.html').read_text().replace('__BASE__',base)
    html=html.replace('/scene-data.js"','/scene-data.js?v='+hashlib.sha256((a.out/'scene-data.js').read_bytes()).hexdigest()[:12]+'"')
    (a.out/'index.html').write_text(html)
    for n in ['three.min.js','OrbitControls.js']:shutil.copy2(ROOT/'tools/web'/n,a.out/n)
    print(json.dumps([dict(id=s['id'],boxes=len(s['boxes']),size=s['size'],checks=s['checks']) for s in scenes],ensure_ascii=False))
if __name__=='__main__':main()
