"""玩家宅田的独立区域规划；不写入正式城市或玩家存档。"""
import copy, heapq, json, math

from province_capitals.regional.v2.city import Capital
from province_capitals.blueprint_plan.study_surfaces import contains, in_polygon, natural_feature


def overlap(a,b):
    return a[0]<b[2] and a[2]>b[0] and a[1]<b[3] and a[3]>b[1]


def shifted(box,dx,dz):
    return [box[0]+dx,box[1]+dz,box[2]+dx,box[3]+dz]


def expanded(box,n):
    return [box[0]-n,box[1]-n,box[2]+n,box[3]+n]


def add_box(scene,p,s,material,tag,layer='player_land'):
    scene['boxes'].append({'p':list(p),'s':list(s),'m':material,'tag':tag,'layer':layer})


def subtract_box(box,rect):
    """沿半开XZ矩形切开轴对齐预览构件，保留范围外几何。"""
    x,y,z=box['p'];w,h,d=box['s'];a=[x-w/2,z-d/2,x+w/2,z+d/2]
    if not overlap(a,rect):return [box]
    x0,z0,x1,z1=a;rx0,rz0,rx1,rz1=rect;pieces=[]
    candidates=[[x0,z0,min(x1,rx0),z1],[max(x0,rx1),z0,x1,z1],
                [max(x0,rx0),z0,min(x1,rx1),min(z1,rz0)],
                [max(x0,rx0),max(z0,rz1),min(x1,rx1),z1]]
    for xa,za,xb,zb in candidates:
        if xb>xa and zb>za:pieces.append({**box,'p':[(xa+xb)/2,y,(za+zb)/2],'s':[xb-xa,h,zb-za]})
    return pieces


def translate_record(record,delta):
    dx,dy,dz=delta
    for key in ('position','entrance','gate'):
        if key in record:record[key]=[v+d for v,d in zip(record[key],delta)]
    for key in ('path','internal_path'):
        if key in record:record[key]=[[v+d for v,d in zip(p,delta)]for p in record[key]]
    if 'bounds' in record:
        if isinstance(record['bounds'],list):record['bounds']=shifted(record['bounds'],dx,dz)
        else:
            for key in ('min','max'):record['bounds'][key]=[v+d for v,d in zip(record['bounds'][key],delta)]
    if 'y' in record:record['y']+=dy
    for f in record.get('fixtures',[]):
        f['position']=[v+d for v,d in zip(f['position'],delta)]
        for key in ('roots','soil'):
            if key in f.get('planting',{}):
                f['planting'][key]=[[v+d for v,d in zip(point,delta)]for point in f['planting'][key]]


def prepare_city(root,proposal,data,scene,style,rules):
    p=copy.deepcopy(proposal);land=copy.deepcopy(style['player_land']);moves=land['entity_moves'];sx,sz=land['city_shift']
    original_w,_,original_d=data['dimensions'];new_w,_,new_d=land['city_dimensions']
    by_original={b['id']:b for b in p['before']};by_final={b['id']:b for b in p['after']}
    for key,move in moves.items():
        dx,dz=move['to'][0]-move['from'][0],move['to'][1]-move['from'][1]
        if key in by_final:by_final[key]['box']=shifted(by_final[key]['box'],dx,dz)
        for q in p['qianji']:
            if q['id']==key:q['box']=shifted(q['box'],dx,dz)
    deltas={}
    for key,b in by_final.items():
        old=by_original[key]['box'];new=b['box']
        if old!=new:deltas[key]=[new[0]-old[0],0,new[1]-old[1]]
    spec=json.loads((root/'blueprints/province_capitals/blueprint_plan'/(p['region']+'.json')).read_text())
    raw_city=json.loads((root/spec['sources']['city']).read_text());raw_city['dimensions']=land['city_dimensions']
    t=raw_city['terrain']
    for name in ('shore_x_by_z','stream_x_by_z','rift_x_by_z'):
        if name in t:t[name]=[[z+sz,x+sx]for z,x in t[name]]
    if 'center' in t:t['center']=[t['center'][0]+sx,t['center'][1]+sz]
    if 'ravine' in t:
        t['ravine']['center_x']+=sx;t['ravine']['z_range']=[v+sz for v in t['ravine']['z_range']]
    if 'shelves' in t:t['shelves']=[[z+sz,y]for z,y in t['shelves']]
    for patch in raw_city.get('terrain_patches',[]):
        if 'bounds' in patch:patch['bounds']=shifted(patch['bounds'],sx,sz)
    natural=Capital(raw_city).terrain
    terrain=natural[:]
    for z in range(original_d):
        for x in range(original_w):terrain[(z+sz)*new_w+x+sx]=data['terrain'][z*original_w+x]
    def old_ground(x,z):return data['terrain'][min(original_d-1,max(0,int(z)))*original_w+min(original_w-1,max(0,int(x)))]
    for key,delta in deltas.items():
        old,new=by_original[key]['box'],by_final[key]['box']
        oldh=max(old_ground(x,z)for z in range(old[1],old[3])for x in range(old[0],old[2]))
        newh=max(natural[(z+sz)*new_w+x+sx]for z in range(new[1],new[3])for x in range(new[0],new[2]))
        delta[1]=newh-oldh
    retired=set(p['proposed_retired_houses']);removed=retired|{g['id']for g in data['gardens']if g.get('owner')in retired}
    removed|={c for q in proposal['qianji']for c in q['courts']if c not in moves}
    garden_delta={}
    kept=[]
    for g in data['gardens']:
        if g['id']in removed:continue
        if g['access']=='private'and g.get('owner')in deltas:delta=deltas[g['owner']]
        elif g['id']in moves:
            m=moves[g['id']];dx,dz=m['to'][0]-m['from'][0],m['to'][1]-m['from'][1]
            old=g['bounds'];nh=max(natural[(z+sz)*new_w+x+sx]for z in range(old[1]+dz,old[3]+dz+1)for x in range(old[0]+dx,old[2]+dx+1));delta=[dx,nh+1-g['y'],dz]
        else:delta=[0,0,0]
        garden_delta[g['id']]=delta;translate_record(g,delta);translate_record(g,[sx,0,sz]);kept.append(g)
    data['gardens']=kept
    all_deltas={**deltas,**garden_delta}
    transformed=[]
    wall_material=next((b['m']for b in scene['boxes']if b['tag']=='城防'),'stone')
    for box in scene['boxes']:
        tag=box['tag']
        if tag in removed or any(tag.startswith(k+'_')for k in removed):continue
        if land['wall']is not None and tag=='城防':continue
        delta=all_deltas.get(tag)
        if delta is None:
            match=next((k for k in garden_delta if tag.startswith(k+'_')),None)
            delta=garden_delta.get(match,[0,0,0])
        box={**box,'p':[box['p'][0]+delta[0]+sx,box['p'][1]+delta[1],box['p'][2]+delta[2]+sz]}
        transformed.append(box)
    scene['boxes']=transformed
    for mesh in scene['meshes']:
        if mesh.get('tag')not in('heightfield','heightfield_sides'):
            mesh['v']=[[x+sx,y,z+sz]for x,y,z in mesh['v']]
    data['plots']=[g for g in data['plots']if g['id']not in retired]
    for plot in data['plots']:
        b=plot['building'];delta=deltas.get(plot['id'],[0,0,0]);translate_record(b,delta);translate_record(b,[sx,0,sz])
        if plot['id']in by_final:
            box0=shifted(by_final[plot['id']]['box'],sx,sz);plot['cell_runs']=[[z,box0[0],box0[2]-1]for z in range(box0[1],box0[3])]
        else:plot['cell_runs']=[[z+sz,x0+sx,x1+sx]for z,x0,x1 in plot['cell_runs']]
    for r in data['roads']:
        r['points']=[[x+sx,y,z+sz]for x,y,z in r['points']]
    for r in data['building_access']:
        r['points']=[[x+sx,y,z+sz]for x,y,z in r['points']]
    for key in ('paths',):
        for r in data.get(key,[]):r['points']=[[x+sx,y,z+sz]for x,y,z in r['points']]
    data['wall']['points']=land['wall']or[[x+sx,z+sz]for x,z in data['wall']['points']]
    for g in data['wall'].get('gates',[]):g['bounds']=shifted(g['bounds'],sx,sz)
    data['landmark']['bounds']=shifted(data['landmark']['bounds'],sx,sz)
    data['landmark']['origin'][0]+=sx;data['landmark']['origin'][2]+=sz
    for d in data['districts']:d['bounds']=shifted(d['bounds'],sx,sz)
    data['dimensions']=land['city_dimensions'];p['dimensions']=land['city_dimensions']
    for name in ('before','after','qianji','gardens'):
        for item in p[name]:item['box']=shifted(item['box'],sx,sz)
    p['manor']=shifted(p['manor'],sx,sz);p['landmark']=shifted(p['landmark'],sx,sz)
    for name in ('roads_before','roads_after'):
        for r in p[name]:r['points']=[[x+sx,z+sz]for x,z in r['points']]
    p['ground_cells']=[[x+sx,z+sz,k]for x,z,k in p['ground_cells']]
    p['gardens']=[{'id':g['id'],'name':g['name'],'access':g['access'],'owner':g.get('owner'),'box':[g['bounds'][0],g['bounds'][1],g['bounds'][2]+1,g['bounds'][3]+1]}for g in data['gardens']]
    for q in p['qianji']:q['courts']=[g for g in q['courts']if g not in moves]
    for g in style['green_sites']:g['box']=shifted(g['box'],sx,sz)
    for g in style['public_spaces']:g['box']=shifted(g['box'],sx,sz)
    style['coordinate_offset']=[sx,sz]
    b=land['box'];heights=[natural[z*new_w+x]for z in range(b[1],b[3])for x in range(b[0],b[2])]
    target=(min(heights)+max(heights))//2
    assert max(abs(v-target)for v in heights)<=rules['max_earthwork'],('宅田整地超出上限',p['region'])
    pad=rules['public_setback']
    for z in range(max(0,b[1]-pad),min(new_d,b[3]+pad)):
        for x in range(max(0,b[0]-pad),min(new_w,b[2]+pad)):
            dist=max(b[0]-x,0,x-b[2]+1,b[1]-z,z-b[3]+1)
            weight=1-dist/(pad+1);terrain[z*new_w+x]=round(target*weight+terrain[z*new_w+x]*(1-weight))
    data['terrain']=terrain;scene['terrain']=terrain
    land.update(ground_y=target,feet_y=target+1,natural_range=[min(heights),max(heights)],max_earthwork=max(abs(v-target)for v in heights),area=(b[2]-b[0])*(b[3]-b[1]))
    assert land['area']==rules['net_size'][0]*rules['net_size'][1]
    assert all(in_polygon(data['wall']['points'],x+.5,z+.5)for z in range(b[1],b[3])for x in range(b[0],b[2]))
    output=[];cleared=[]
    protected_ids={v['id']for v in data['plots']}|{g['id']for g in data['gardens']}
    for box in scene['boxes']:
        x,y,z=box['p'];w,h,d=box['s'];bounds=[x-w/2,z-d/2,x+w/2,z+d/2]
        if overlap(bounds,b)and y+h/2>target+.01:
            attributed=box['tag']in protected_ids or any(box['tag'].startswith(k+'_')for k in protected_ids)
            if box['layer']not in('roads','road','road_edge','context')and attributed:
                raise ValueError('实体建筑侵入玩家净用地 '+str((p['region'],box['tag'],bounds)))
            cleared.append({'tag':box['tag'],'layer':box['layer']});output.extend(subtract_box(box,b))
        else:output.append(box)
    scene['boxes']=output;land['cleared_context_parts']=len(cleared)
    if land['wall']is not None:
        gate_boxes=[g['bounds']for g in data['wall'].get('gates',[])];points=data['wall']['points'];wallpoints=set()
        for a,c in zip(points,points[1:]):
            n=max(abs(c[0]-a[0]),abs(c[1]-a[1]))
            for i in range(n+1):wallpoints.add((round(a[0]+(c[0]-a[0])*i/max(1,n)),round(a[1]+(c[1]-a[1])*i/max(1,n))))
        for x,z in sorted(wallpoints):
            if not(0<=x<new_w and 0<=z<new_d)or any(contains(g,x,z)for g in gate_boxes):continue
            y=terrain[z*new_w+x]+1;h=rules['wall_height'];ww=rules['wall_width']
            add_box(scene,[x+.5,y+(h-1)/2,z+.5],[ww,h-1,ww],wall_material,'拟调整城墙','walls')
            if (x+z)%2==0:add_box(scene,[x+.5,y+h-.5,z+.5],[ww,1,ww],wall_material,'拟调整城墙','walls')
    data['player_land']=land
    data['districts'].insert(0,{'id':'player_land','name':'玩家宅田用地','bounds':expanded(land['box'],3)})
    style['player_land']=land
    style['player_soil']=rules['player_soil']
    p['player_land']=land
    return p,data,scene


def access_and_context(root,data,scene,proposal,style,rules,ground_rules):
    W,_,D=data['dimensions'];land=data['player_land'];b=land['box'];terrain=data['terrain']
    floor=lambda x,z:terrain[z*W+x]+1
    obstacles=[v['box']for v in proposal['after']]+[expanded(q['box'],2)for q in proposal['qianji']]+[proposal['manor'],proposal['landmark'],b]
    obstacles +=[[g['bounds'][0],g['bounds'][1],g['bounds'][2]+1,g['bounds'][3]+1]for g in data['gardens']]
    blocked=set()
    for box0 in obstacles:
        blocked.update((x,z)for z in range(max(0,box0[1]),min(D,box0[3]))for x in range(max(0,box0[0]),min(W,box0[2])))
    spec=json.loads((root/'blueprints/province_capitals/blueprint_plan'/(proposal['region']+'.json')).read_text());raw=json.loads((root/spec['sources']['city']).read_text());sx,sz=style['coordinate_offset']
    natural=set((x,z)for z in range(D)for x in range(W)if natural_feature(style['reserve'],raw['terrain'],x-sx+.5,z-sz+.5,ground_rules['natural_margin']))
    primary={(x,z):y for road in data['roads']if road['id'].startswith('replan_')for x,y,z in road['points']if 0<=x<W and 0<=z<D}
    valid_cache={}
    radius=rules['road_width']//2
    def valid(x,z):
        key=x,z
        if key not in valid_cache:
            valid_cache[key]=all(0<=xx<W and 0<=zz<D and(xx,zz)not in blocked and((xx,zz)not in natural or(xx,zz)in primary)and in_polygon(data['wall']['points'],xx+.5,zz+.5)for zz in range(z-radius,z+radius+1)for xx in range(x-radius,x+radius+1))
        return valid_cache[key]
    distance={};parent={};end={};queue=[]
    for pt,y in primary.items():
        x,z=pt;gap=max(b[0]-x,0,x-b[2]+1,b[1]-z,0,z-b[3]+1)
        if valid(x,z)and gap>=2*abs(y-land['feet_y'])+1:
            distance[pt]=0;parent[pt]=None;end[pt]=y;heapq.heappush(queue,(0,pt))
    while queue:
        cost,pt=heapq.heappop(queue)
        if cost!=distance[pt]:continue
        x,z=pt
        for n in((x+1,z),(x-1,z),(x,z+1),(x,z-1)):
            if not valid(*n):continue
            new=cost+1+abs(floor(*n)-floor(x,z))*.35
            if new<distance.get(n,float('inf')):distance[n]=new;parent[n]=pt;end[n]=end[pt];heapq.heappush(queue,(new,n))
    gates=[]
    for z in range(b[1]+3,b[3]-3,2):gates.extend([('west',(b[0]-radius-1,z)),('east',(b[2]+radius,z))])
    for x in range(b[0]+3,b[2]-3,2):gates.extend([('north',(x,b[1]-radius-1)),('south',(x,b[3]+radius))])
    choices=[(distance[pt],side,pt)for side,pt in gates if pt in distance]
    assert choices,('玩家用地无法接到公共路',proposal['region'])
    cost,side,start=min(choices);path=[];at=start
    while at is not None:path.append(at);at=parent[at]
    # 入口从净用地外缘接出，公共接路不进入净用地。
    near={'west':(b[0]-1,start[1]),'east':(b[2],start[1]),'north':(start[0],b[1]-1),'south':(start[0],b[3])}[side]
    prefix=[near]
    if near!=start:prefix.append(start)
    path=prefix+path[1:];goal_y=end[start];n=max(1,len(path)-1)
    assert n>=2*abs(goal_y-land['feet_y'])or goal_y==land['feet_y'],('接路长度不足以放阶道',proposal['region'])
    route=[]
    scene['materials']['player_access']={'color':style['plaza']};scene['materials']['player_marker']={'color':rules['player_border']}
    for i,(x,z)in enumerate(path):
        feet=round((land['feet_y']+(goal_y-land['feet_y'])*i/n)*2)/2
        assert not contains(b,x,z)
        route.append([x,feet,z])
        for zz in range(z-radius,z+radius+1):
            for xx in range(x-radius,x+radius+1):
                if contains(b,xx,zz):continue
                terrain[zz*W+xx]=min(terrain[zz*W+xx],math.floor(feet)-1)
                base=terrain[zz*W+xx]+1;height=max(.25,feet-base+.25)
                add_box(scene,[xx+.5,feet-height/2,zz+.5],[1,height,1],'player_access',land['id'],'player_land')
    land['entry']={'side':side,'position':[near[0],land['feet_y'],near[1]],'public_road_goal':[path[-1][0],goal_y,path[-1][1]],'route':route,'width':rules['road_width']}
    for x in range(b[0],b[2]+1,rules['marker_spacing']):
        for z in (b[1]-1,b[3]):
            xx=min(W-1,x);zz=min(D-1,max(0,z));y=floor(xx,zz);h=rules['marker_height'];w=rules['marker_width']
            add_box(scene,[x,y+h/2,z+.5],[w,h,w],'player_marker',land['id'])
    for z in range(b[1],b[3]+1,rules['marker_spacing']):
        for x in (b[0]-1,b[2]):
            xx=min(W-1,max(0,x));zz=min(D-1,z);y=floor(xx,zz);h=rules['marker_height'];w=rules['marker_width']
            add_box(scene,[x+.5,y+h/2,z],[w,h,w],'player_marker',land['id'])
    # 地块边界线在外侧公共退界带上，净用地中不摆放房屋或装饰。
    y=land['feet_y']+.025
    for z in (b[1]-.1,b[3]+.1):add_box(scene,[(b[0]+b[2])/2,y,z],[b[2]-b[0],.05,.08],'player_marker',land['id'])
    for x in (b[0]-.1,b[2]+.1):add_box(scene,[x,y,(b[1]+b[3])/2],[.08,.05,b[3]-b[1]],'player_marker',land['id'])
    # 树院与装饰前坪可调整，避开玩家用地和迁排后的实体。
    occupied=[expanded(v,2)for v in obstacles]+[expanded(b,3)]
    roadcells={(x,z)for road in data['roads']for x,y,z in road['points']if not contains(b,x,z)}|set(path)
    new_sites=[]
    for site in style['green_sites']:
        original=site['box'];target=((original[0]+original[2])/2,(original[1]+original[3])/2);best=None
        sizes=[(original[2]-original[0],original[3]-original[1]),(22,16),(18,14),(12,10)]
        for w,d in sizes:
            for z in range(4,D-d-3,2):
                for x in range(4,W-w-3,2):
                    box0=[x,z,x+w,z+d]
                    if any(overlap(box0,v)for v in occupied):continue
                    if any((xx,zz)in roadcells or(xx,zz)in natural or not in_polygon(data['wall']['points'],xx+.5,zz+.5)for zz in range(z,z+d)for xx in range(x,x+w)):continue
                    ys=[terrain[zz*W+xx]for zz in range(z,z+d)for xx in range(x,x+w)]
                    if max(ys)-min(ys)>ground_rules['green_max_height_range']:continue
                    score=math.dist((x+w/2,z+d/2),target)
                    if best is None or(score,box0)<(best[0],best[1]):best=(score,box0)
            if best:break
        if best:new_sites.append({**site,'box':best[1]});occupied.append(expanded(best[1],1))
    style['green_sites']=new_sites
    spaces=[]
    for space in style['public_spaces']:
        if overlap(space['box'],b):
            court=next((g for g in data['gardens']if g['id']=='hall_square'),None)
            if court:
                a=court['bounds'];spaces.append({**space,'box':expanded([a[0],a[1],a[2]+1,a[3]+1],1)})
        else:spaces.append(space)
    style['public_spaces']=spaces
    return {'player_land':land,'shift':style['coordinate_offset'],'city_dimensions':data['dimensions'],'tree_courts':new_sites}


def finalize_checks(data,scene,proposal):
    # 地面规划会新增树院；最后统一汇总，既包含新院也不依赖缓存中的对象引用。
    data['fixtures']=[copy.deepcopy(f)for g in data['gardens']for f in g.get('fixtures',[])]
    land=data['player_land'];box=land['box'];violations=[]
    for b in scene['boxes']:
        x,y,z=b['p'];w,h,d=b['s']
        if overlap([x-w/2,z-d/2,x+w/2,z+d/2],box)and y+h/2>land['feet_y']+.08:
            violations.append({'tag':b['tag'],'position':b['p'],'size':b['s']})
    assert not violations,('净用地仍有构件',proposal['region'],violations[:3])
    q_outside=[]
    for b in scene['boxes']:
        if not b['tag'].startswith('qianji_'):continue
        x,y,z=b['p'];w,h,d=b['s']
        corners=((x-w/2+.001,z-d/2+.001),(x+w/2-.001,z-d/2+.001),
                 (x-w/2+.001,z+d/2-.001),(x+w/2-.001,z+d/2-.001))
        if any(not in_polygon(data['wall']['points'],xx,zz)for xx,zz in corners):q_outside.append(b['tag'])
    assert not q_outside,('千机阁构件越出新城墙',proposal['region'],sorted(set(q_outside)))
    return {'net_size':land['size'],'net_area':land['area'],'empty_above_ground':True,'inside_city_wall':True,'qianji_inside_city_wall':True,'public_access_xz':'connected','road_height_and_native_walkability':'not_verified','minecraft':'not_run'}


def amend_viewer(viewer):
    viewer=viewer.replace("function reset(){", "function reset(){$('district').value='all';")
    viewer=viewer.replace("$('selectedName').textContent='千机阁入城草案';", "$('selectedName').textContent='七城宅田区域规划';")
    viewer=viewer.replace("建筑使用当前作者输入的实际构件，千机阁保留各栋原尺寸和楼层。迁排住宅按草案移动，街坊空地连成铺地院场，绿地集中在有边界的树院。", "每座城预留一块64×72格净用地，住宅与田地比例由玩家决定。预览同时显示街坊迁排、公共入口和城墙调整。")
    viewer=viewer.replace("...DATA.plots,...DATA.gardens", "...DATA.plots,...DATA.gardens,DATA.player_land")
    anchor="const sourceSelect=select;select=function(id){sourceSelect(id);"
    replacement="""const sourceSelect=select;select=function(id){const land=DATA.player_land;if(id===land.id){selected={...land,kind:'player_land',bounds:land.box.map((v,i)=>i>1?v-1:v),y:land.feet_y};$('selectedName').textContent='玩家宅田预留地';$('selectedText').textContent='净用地 '+land.size.join('×')+' 格，共 '+land.area+' 格；住宅与田地比例由玩家决定。';$('selectedType').textContent=land.mode==='infill'?'位于原城域内':'位于调整城墙后的宅田街坊';$('selectedConnection').textContent='已预留入口和公共接路。初始地面为空地；本页尚未接入正式世界生成。';highlight();return;}sourceSelect(id);"""
    assert anchor in viewer
    viewer=viewer.replace(anchor,replacement)
    viewer=viewer.replace("const m=new THREE.Mesh(g,mats[b.m]);m.userData={layer:b.layer,tag:b.tag};group.add(m);", "const m=new THREE.Mesh(g,mats[b.m]);m.userData={layer:b.layer,tag:b.tag};group.add(m);if(b.tag===DATA.player_land.id)picks.push(m);")
    old="h.object.userData.tags[h.instanceId]"
    viewer=viewer.replace(old,"(h.instanceId===undefined?h.object.userData.tag:h.object.userData.tags[h.instanceId])")
    viewer+="""
function playerFocus(){const b=DATA.player_land.box;focus([b[0]-18,b[1]-18,b[2]+18,b[3]+18]);select(DATA.player_land.id);$('district').value='player_land';}
$('playerfocus').onclick=playerFocus;
if(!new URLSearchParams(location.search).has('view'))playerFocus();
"""
    return viewer
