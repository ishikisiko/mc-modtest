"""在完整月洞水院内增补居住功能，逐格保留原建筑。"""
from collections import deque
from functools import lru_cache
from pathlib import Path
import copy,json,math
from tools.reference_courtyard.assembly_scene import build_scene
from tools.player_estates.geometry import state
from tools.player_estates.preview import parse

ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/'blueprints/player_homes/moon_courtyard_home.json'


def build(config=None):
    c=copy.deepcopy(config or json.loads(INPUT.read_text()))
    kit=json.loads((ROOT/c['source']).read_text());site=build_scene(kit)
    site.original={p:s for p,s in site.blocks.items()if s!='minecraft:air'}
    site.original_assemblies=copy.deepcopy(site.assemblies)
    site.blocks=dict(site.original);site.config=c;site.c={**c,'views':{}};site.rooms=c['rooms']
    site.layers={p:'roof'if s.startswith('yuanxian:courtyard_roof[')else'water'if s.startswith('minecraft:water[')else'architecture'for p,s in site.blocks.items()}
    site.additions={};site.doors=[]
    def add(p,block,layer):
        assert p not in site.original,('改动原建筑',p,site.original.get(p))
        site.put(p,block);site.layers[p]=layer;site.additions[p]=block
    for wall in c['partitions']:
        x0,y0,z0,x1,y1,z1=wall['bounds']
        for x in range(x0,x1):
            for y in range(y0,y1):
                for z in range(z0,z1):
                    p=(x,y,z)
                    if p in site.original:continue
                    key='timber_transom'if y==wall['transom_y']else'plaster'
                    site.basic(p,'wall',key);site.layers[p]='residential';site.additions[p]=site.blocks[p]
    for door in c['doors']:
        root=tuple(door['root']);family=site.families[door['family']]
        v=next(v for v in family['variants']if v['key']==door['variant'])
        from tools.reference_courtyard.assembly_scene import offset,DIRECTIONS
        turn=DIRECTIONS.index(door['facing'])
        footprint=[tuple(root[i]+d for i,d in enumerate(offset(x,y,z,turn)))for y in range(v['size'][1])for z in range(v['size'][2])for x in range(v['size'][0])]
        assert all(p not in site.original for p in footprint),'大门侵入原构件'
        site.place(root,door['family'],door['variant'],door['facing'])
        for p in footprint:site.layers[p]='residential';site.additions[p]=site.blocks[p]
        site.doors.append(root)
    for item in c['furniture']:
        block=state(item['block'],**item.get('properties',{}))
        if item.get('kind')=='bed':
            x,y,z=item['position'];assert item['facing']=='south'
            for part,dz in [('foot',0),('head',1)]:add((x,y,z+dz),state(item['block'],part=part,facing=item['facing'],occupied=False),'furniture')
        elif item.get('kind')=='fill':
            a,b,d,e,f,g=item['bounds']
            for x in range(a,e):
                for y in range(b,f):
                    for z in range(d,g):add((x,y,z),block,'furniture')
        else:add(tuple(item['position']),block,'furniture')
    for item in c['kit_fixtures']:
        p=tuple(item['root']);assert p not in site.original,('灯具改动原建筑',p)
        site.place(p,item['family'],item['variant']);site.layers[p]=item.get('layer','furniture');site.additions[p]=site.blocks[p]
    return site


def check_preservation(site):
    changed=[p for p,s in site.original.items()if site.blocks.get(p)!=s]
    assert not changed,('原版方块被修改',changed[:5])
    assert site.assemblies[:len(site.original_assemblies)]==site.original_assemblies
    for i,assembly in enumerate(site.assemblies):
        for p in assembly['parts']:
            p=tuple(p);assert site.owners[p]==i and site.blocks[p].startswith('yuanxian:'+assembly['family']+'['),('构件不完整',i,p)
    assert site.c['size']==[48,28,40]
    return {'original_cells':len(site.original),'retained_original_cells':len(site.original),'changed_original_cells':0,
            'original_whole_assemblies':len(site.original_assemblies),'retained_whole_assemblies':len(site.original_assemblies),
            'added_cells':len(site.blocks)-len(site.original),'added_whole_assemblies':len(site.assemblies)-len(site.original_assemblies),
            'roof_water_bridge_pavilion_gallery':'unchanged'}


def walking(site,config=None,closed_doors=False):
    c=config or site.config['walk'];catalog=json.loads((ROOT/'src/main/resources/data/yuanxian/courtyard/assemblies.json').read_text())
    families={f['id']:f for f in catalog['families']}
    @lru_cache(None)
    def boxes(s):
        name,p=parse(s)
        if name.startswith('yuanxian:'):
            f=families[name.split(':')[1]];v=f['variants'][int(p.get('variant','0'))]
            part=(v['parts']if closed_doors else v.get('open_parts',v['parts']))[int(p.get('part','0'))];turn=['north','east','south','west'].index(p['facing']);result=[]
            for raw in part['boxes']:
                a,b,d,e,f,g=[n/16 for n in raw]
                for _ in range(turn):a,d,e,g=1-g,a,1-d,e
                result.append((a,b,d,e,f,g))
            return result
        if name in('minecraft:air','minecraft:water'):return []
        if name.endswith('_fence'):return[(.375,0,.375,.625,1.5,.625)]
        if name.endswith('_slab'):return[(0,.5 if p.get('type')=='top'else 0,0,1,1 if p.get('type')in('top','double')else .5,1)]
        if name.endswith('_bed'):return[(0,0,0,1,.5625,1)]
        if name.endswith('_carpet'):return[(0,0,0,1,.0625,1)]
        return[(0,0,0,1,1,1)]
    columns={}
    for (x,y,z),s in site.blocks.items():
        for box in boxes(s):columns.setdefault((x,z),[]).append((s,(x+box[0],y+box[1],z+box[2],x+box[3],y+box[4],z+box[5])))
    half=c['body_half_width'];height=c['body_height'];nodes=set()
    for (x,z),values in columns.items():
        candidates={round(box[4]*16)for _,box in values if box[0]<x+.5+half and box[3]>x+.5-half and box[2]<z+.5+half and box[5]>z+.5-half and c['minimum_feet']<=box[4]<=c['maximum_feet']}
        for yy in candidates:
            y=yy/16;body=(x+.5-half,y+c['feet_epsilon'],z+.5-half,x+.5+half,y+height,z+.5+half)
            if not any(all(min(body[i+3],b[i+3])-max(body[i],b[i])>1e-7 for i in range(3))for _,b in values):nodes.add((x,yy,z))
    def node(p):return(p[0],round(p[1]*16),p[2])
    by_column={}
    for p in nodes:by_column.setdefault((p[0],p[2]),[]).append(p)
    @lru_cache(None)
    def edge_clear(p,q):
        y=max(p[1],q[1])/16
        for i in range(1,c['edge_samples']):
            t=i/c['edge_samples'];x=p[0]+.5+(q[0]-p[0])*t;z=p[2]+.5+(q[2]-p[2])*t
            body=(x-half,y+c['feet_epsilon'],z-half,x+half,y+height,z+half)
            for bx in range(math.floor(body[0]),math.floor(body[3])+1):
                for bz in range(math.floor(body[2]),math.floor(body[5])+1):
                    if any(all(min(body[k+3],b[k+3])-max(body[k],b[k])>1e-7 for k in range(3))for _,b in columns.get((bx,bz),[])):return False
        return True
    start=node(c['start']);assert start in nodes,('桥头站位被阻挡',start)
    seen={start};queue=deque([start]);previous={}
    while queue:
        p=queue.popleft()
        for x,z in ((p[0]-1,p[2]),(p[0]+1,p[2]),(p[0],p[2]-1),(p[0],p[2]+1)):
            for q in by_column.get((x,z),[]):
                if q not in seen and abs(q[1]-p[1])/16<=c['step_height'] and edge_clear(p,q):
                    seen.add(q);previous[q]=p;queue.append(q)
    paths={};missing=[]
    for target in c['targets']:
        end=node(target['position'])
        if end not in seen:missing.append(target['name']);continue
        route=[end]
        while route[-1]!=start:route.append(previous[route[-1]])
        paths[target['name']]=[[p[0]+.5,p[1]/16+.035,p[2]+.5]for p in reversed(route)]
    assert not missing,('房间动线不通',missing)
    def route_chain(names):
        # Each segment is searched from its actual preceding waypoint.
        result=[];begin=start
        for name in names:
            end=node(next(t['position']for t in c['targets']if t['name']==name));q=deque([begin]);parents={begin:None}
            while q and end not in parents:
                p=q.popleft()
                for x,z in ((p[0]-1,p[2]),(p[0]+1,p[2]),(p[0],p[2]-1),(p[0],p[2]+1)):
                    for n in by_column.get((x,z),[]):
                        if n not in parents and abs(n[1]-p[1])/16<=c['step_height'] and edge_clear(p,n):parents[n]=p;q.append(n)
            assert end in parents;part=[];p=end
            while p is not None:part.append([p[0]+.5,p[1]/16+.035,p[2]+.5]);p=parents[p]
            result+=list(reversed(part))[bool(result):];begin=end
        return result
    return {'status':'pass','reachable_stances':len(seen),'targets':c['targets'],'paths':paths,
            'tour':route_chain(c['tour']),'service':route_chain(c['service']),
            'doors':'closed'if closed_doors else'open_collision_checked','swept_edges_checked':edge_clear.cache_info().currsize,'scope':'实际碰撞与脚下支撑的离线格点检查；玩家操作尚未验证'}
