"""独立城市地面草案；建成街坊默认铺地，种植地只取明确地块。"""
from collections import Counter, deque
import copy, json, math

from province_capitals.regional.v2.common import lerp


def contains(box, x, z):
    return box[0] <= x < box[2] and box[1] <= z < box[3]


def in_polygon(points, x, z):
    inside = False
    for (ax, az), (bx, bz) in zip(points, points[1:] + points[:1]):
        if (az > z) != (bz > z) and x < ax + (z-az)*(bx-ax)/(bz-az):
            inside = not inside
    return inside


def natural_feature(kind, terrain, x, z, margins):
    if kind == 'ravine':
        r = terrain['ravine']
        extra=margins['ravine']
        return r['z_range'][0]-extra <= z <= r['z_range'][1]+extra and abs(x-r['center_x']) <= r['half_width']+extra
    if kind == 'shore':
        return x >= lerp(terrain['shore_x_by_z'], z)-margins['shore']
    if kind == 'oasis':
        extra=margins['oasis']
        return ((x-terrain['center'][0])/(terrain['radius'][0]+extra))**2 + ((z-terrain['center'][1])/(terrain['radius'][1]+extra))**2 < 1
    if kind in ('stream', 'rift'):
        key = 'stream_x_by_z' if kind == 'stream' else 'rift_x_by_z'
        return abs(x-lerp(terrain[key], z)) <= terrain['half_width']+terrain['bank_width']
    return False


def box(scene, position, size, material, tag):
    scene['boxes'].append({'p':position, 's':size, 'm':material, 'tag':tag, 'layer':'studylandscape'})


def apply_surfaces(root, data, scene, proposal, style, rules):
    width, _, depth = data['dimensions']
    ground = lambda x,z: data['terrain'][min(depth-1,max(0,int(z)))*width + min(width-1,max(0,int(x)))]
    spec = json.loads((root/'blueprints/province_capitals/blueprint_plan'/(proposal['region']+'.json')).read_text())
    city = json.loads((root/spec['sources']['city']).read_text())
    building_boxes = [b['box'] for b in proposal['after']] + [b['box'] for b in proposal['qianji']] + [proposal['manor'],proposal['landmark']]
    occupied = {(x,z) for b in building_boxes for z in range(b[1],b[3]) for x in range(b[0],b[2])}
    road = set()
    for r in data['roads']:
        pad = r['width']//2
        for x,y,z in r['points']:
            road.update((xx,zz) for zz in range(max(0,z-pad),min(depth,z+pad+1)) for xx in range(max(0,x-pad),min(width,x+pad+1)))
    green_sites = style['green_sites']
    player=style.get('player_land')
    sx,sz=style.get('coordinate_offset',[0,0])
    path_width = rules['path_width']
    for site in green_sites:
        b=site['box']
        ys=[ground(x,z) for z in range(b[1],b[3])for x in range(b[0],b[2])]
        assert max(ys)-min(ys)<=rules['green_max_height_range'],('树院地形起伏超出草案限制',site['id'],min(ys),max(ys))
        assert all(in_polygon(data['wall']['points'],x+.5,z+.5)for z in range(b[1],b[3])for x in range(b[0],b[2])),('树院越出城内',site['id'])
        for g in data['gardens']:
            a=g['bounds'];a=[a[0],a[1],a[2]+1,a[3]+1]
            assert not (a[0]<b[2] and a[2]>b[0] and a[1]<b[3] and a[3]>b[1]),('树院侵入已有庭院',site['id'],g['id'])
    types, owners = {}, {}
    public_courts = [g for g in proposal['gardens'] if g['access']=='public']
    for z in range(depth):
        for x in range(width):
            point = x,z
            if player and contains(player['box'],x,z):
                assert point not in occupied
                kind,owner='player_land',player['id']
            elif point in occupied:
                kind,owner = 'structure','building'
            elif point in road:
                kind,owner = 'paved','public_street'
            elif not in_polygon(data['wall']['points'],x+.5,z+.5):
                kind,owner = 'natural','outside_wall'
            elif natural_feature(style['reserve'],city['terrain'],x-sx+.5,z-sz+.5,rules['natural_margin']):
                kind,owner = 'natural',style['reserve']
            else:
                kind,owner = 'paved','urban_court'
                for c in style['public_spaces']:
                    if contains(c['box'],x,z):
                        kind,owner='plaza',c['id'];break
                for c in public_courts:
                    if contains(c['box'],x,z):
                        kind,owner = 'plaza',c['id'];break
                for q in proposal['qianji']:
                    a,b,c,d=q['box']
                    margin=rules['forecourt_margin']
                    if contains([a-margin,b-margin,c+margin,d+margin],x,z):
                        kind,owner = 'plaza','qianji_forecourt';break
                for site in green_sites:
                    if contains(site['box'],x,z):
                        assert point not in occupied and point not in road,(proposal['region'],site['id'],point)
                        mid=(site['box'][0]+site['box'][2]-1)//2
                        kind='paved' if abs(x-mid)<=path_width//2 else 'planted'
                        owner=site['id'];break
            types[point]=kind;owners[point]=owner
    # 连续铺地按街道和建筑分出街坊；狭窄剩余空间也有通行用途。
    todo={p for p,k in types.items() if k in ('paved','plaza') and p not in road}
    blocks=[]
    while todo:
        start=min(todo,key=lambda p:(p[1],p[0]));todo.remove(start)
        queue=deque([start]);cells=[]
        while queue:
            p=queue.popleft();cells.append(p)
            for n in ((p[0]+1,p[1]),(p[0]-1,p[1]),(p[0],p[1]+1),(p[0],p[1]-1)):
                if n in todo:todo.remove(n);queue.append(n)
        bid='paved_block_'+str(len(blocks)+1)
        for p in cells:
            if owners[p]=='urban_court':owners[p]=bid
        blocks.append({'id':bid,'use':'paved_alley' if len(cells)<rules['alley_component_max_area'] else 'paved_court','area':len(cells),
                       'bounds':[min(x for x,z in cells),min(z for x,z in cells),max(x for x,z in cells)+1,max(z for x,z in cells)+1]})
    materials={
        'paved':{'color':style['paving'],'pattern':style['texture']},
        'structure':{'color':style['paving'],'pattern':style['texture']},
        'plaza':{'color':style['plaza'],'pattern':'flagstone'},
        'planted':{'color':style['green'],'pattern':'grass'},
        'natural':{'color':style['natural'],'pattern':'natural'},
    }
    for name,value in materials.items():scene['materials']['land_'+name]=value
    if player:scene['materials']['land_player_land']={'color':style['player_soil'],'pattern':'natural'}
    scene['materials'].update({'land_border':{'color':style['border']},'land_leaf':{'color':'#476445'},'land_wood':{'color':'#71593d'}})
    meshes={}
    for z in range(depth):
        x=0
        while x<width:
            kind=types[x,z];y=ground(x,z)+1;end=x+1
            while end<width and types[end,z]==kind and ground(end,z)+1==y:end+=1
            m=meshes.setdefault(kind,{'v':[],'f':[],'uv':[],'m':'land_'+kind,'layer':'planned_ground','tag':'街坊地面_'+kind})
            if kind=='player_land':m['tag']=player['id']
            n=len(m['v']);m['v'].extend([[x,y,z],[x,y,z+1],[end,y,z+1],[end,y,z]])
            m['uv'].extend([[x,z],[x,z+1],[end,z+1],[end,z]])
            m['f'].extend([[n,n+1,n+2],[n,n+2,n+3]])
            x=end
    scene['meshes'].extend(meshes.values())
    # 对照画面保留被用户指出的旧分区，不将旧绿色底面混进新地面。
    legacy_colors={'civic':'#9a9789','forecourt':'#aca99a','yard':'#8d9487','green':'#66865c'}
    for key,color in legacy_colors.items():scene['materials']['legacy_'+key]={'color':color}
    for x,z,key in proposal['ground_cells']:
        if key not in legacy_colors:continue
        m={'v':[],'f':[],'m':'legacy_'+key,'layer':'legacy_ground','tag':'上一版地面'}
        for zz in range(z,min(z+4,depth)):
            for xx in range(x,min(x+4,width)):
                y=ground(xx,zz)+1.015;n=len(m['v'])
                m['v'].extend([[xx,y,zz],[xx,y,zz+1],[xx+1,y,zz+1],[xx+1,y,zz]])
                m['f'].extend([[n,n+1,n+2],[n,n+2,n+3]])
        # 相同旧材料合并，减少WebGL绘制批次。
        target=meshes.setdefault('legacy_'+key,{'v':[],'f':[],'m':'legacy_'+key,'layer':'legacy_ground','tag':'上一版地面'})
        offset=len(target['v']);target['v'].extend(m['v']);target['f'].extend([[v+offset for v in f]for f in m['f']])
    scene['meshes'].extend(m for key,m in meshes.items()if key.startswith('legacy_'))
    # 院场沿既有用途收边，入口一侧完全开敞，不把公共前场改成种植地。
    spaces=[]
    for c in data['gardens']:
        if c['id'] not in style['framed_courts']:continue
        x0,z0,x1,z1=c['bounds'];box0=[x0,z0,x1+1,z1+1];gx,_,gz=c['gate']
        opening=min(('north','south','west','east'),key=lambda k:{'north':abs(gz-z0),'south':abs(gz-z1),'west':abs(gx-x0),'east':abs(gx-x1)}[k])
        spaces.append({'id':c['id'],'name':c['name'],'box':box0,'opening':opening})
    spaces.extend(style['public_spaces'])
    for c in spaces:
        x0,z0,x1,z1=c['box'];h,w=rules['public_border_height'],rules['public_border_width']
        for x in range(x0,x1):
            for z,side in ((z0,'north'),(z1-1,'south')):
                if c['opening']==side or (x,z) in occupied or (x,z) in road:continue
                box(scene,[x+.5,ground(x,z)+1+h/2,z+.5],[1,h,w],'land_border',c['id'])
        for z in range(z0+1,z1-1):
            for x,side in ((x0,'west'),(x1-1,'east')):
                if c['opening']==side or (x,z) in occupied or (x,z) in road:continue
                box(scene,[x+.5,ground(x,z)+1+h/2,z+.5],[w,h,1],'land_border',c['id'])
    for site in green_sites:
        x0,z0,x1,z1=site['box'];mid=(x0+x1-1)//2;tag=site['id']
        for x in range(x0,x1):
            for z in (z0,z1-1):
                if z==z1-1 and abs(x-mid)<=path_width//2:continue
                h,w=rules['border_height'],rules['border_width']
                box(scene,[x+.5,ground(x,z)+1+h/2,z+.5],[1,h,w],'land_border',tag)
        for z in range(z0+1,z1-1):
            for x in (x0,x1-1):
                h,w=rules['border_height'],rules['border_width']
                box(scene,[x+.5,ground(x,z)+1+h/2,z+.5],[w,h,1],'land_border',tag)
        fixtures=[]
        for x in (x0+4,x1-5):
            for z in range(z0+4,z1-3,rules['tree_spacing']):
                y=ground(x,z)+1
                box(scene,[x+.5,y+2,z+.5],[.8,4,.8],'land_wood',tag)
                for dx,dz in ((0,0),(1,0),(-1,0),(0,1),(0,-1)):
                    box(scene,[x+.5+dx,y+4.2,z+.5+dz],[2,1.8,2],'land_leaf',tag)
                box(scene,[x+.5,y+5.4,z+.5],[2,1,2],'land_leaf',tag)
                fixtures.append({'name':'乔木','kind':'tree','position':[x,y,z],'size':[3,6,3]})
        bx,bz=x0+3,z1-3;y=ground(bx,bz)+1
        box(scene,[bx+1.5,y+.55,bz+.5],[3,.4,.8],'land_wood',tag)
        box(scene,[bx+1.5,y+1,bz+.9],[3,.8,.2],'land_wood',tag)
        fixtures.append({'name':'坐凳','kind':'bench','position':[bx,y,bz],'size':[3,1,1]})
        gate=[mid,ground(mid,z1-1)+1,z1-1]
        data['gardens'].append({'id':tag,'name':site['name'],'access':'public','owner':'公共街坊','reason':'集中设置的完整树院，外缘有石边和明确入口；其余街坊空地继续铺地。',
            'bounds':[x0,z0,x1-1,z1-1],'y':gate[1],'gate':gate,'path':[gate],'internal_path':[gate],
            'fixtures':fixtures,'status':'design_preview','kind':'tree_court'})
    counts=Counter(types.values())
    planted=[p for p,k in types.items()if k=='planted']
    assert all(any(contains(s['box'],*p)for s in green_sites)for p in planted)
    assert len(types)==width*depth and all(owners.values())
    data['ground_study']={'green_sites':green_sites,'counts':dict(counts),'paved_blocks':blocks,'default_urban_surface':'paved_court'}
    data['ground_study']['overview_bounds']=style.get('overview_bounds',data['study_bounds'] if 'study_bounds' in data else None)
    return {'status':'design_preview','region':proposal['region'],'coverage_cells':width*depth,'classified_cells':len(types),
            'counts':dict(counts),'planted_cells_outside_named_parcels':0,'unassigned_cells':0,
            'green_sites':green_sites,'paved_blocks':blocks,'framed_public_courts':spaces,'formal_worldgen_modified':False,'owner_acceptance':'pending'}


def amend_viewer(viewer):
    viewer=viewer.replace("'use strict';", "'use strict';let revisedGround=true;")
    viewer=viewer.replace("{v:tv,f:tf,m:'ground',layer:'terrain'}", "{v:tv,f:tf,m:'ground',layer:'original_ground'}")
    old="for(const [k,m]of Object.entries(GEOMETRY.materials))mats[k]=new THREE.MeshStandardMaterial({color:new THREE.Color(m.color).convertSRGBToLinear(),roughness:1});"
    new="""for(const [k,m]of Object.entries(GEOMETRY.materials)){
 const mat=new THREE.MeshStandardMaterial({color:new THREE.Color(m.color).convertSRGBToLinear(),roughness:1,polygonOffset:k.startsWith('land_'),polygonOffsetFactor:1,polygonOffsetUnits:1});
 if(m.pattern){const c=document.createElement('canvas');c.width=c.height=16;const g=c.getContext('2d');let seed=913;for(let y=0;y<16;y++)for(let x=0;x<16;x++){seed=(1664525*seed+1013904223)>>>0;let v=218+(seed>>>24)%26;if((m.pattern==='flagstone'||m.pattern==='sandstone')&&(y===0||x===0))v-=22;if(m.pattern==='cobble'&&(y%8===0||(x+(y<8?0:8))%8===0))v-=20;g.fillStyle='rgb('+v+','+v+','+v+')';g.fillRect(x,y,1,1);}const t=new THREE.CanvasTexture(c);t.wrapS=t.wrapT=THREE.RepeatWrapping;t.magFilter=THREE.NearestFilter;t.minFilter=THREE.NearestFilter;mat.map=t;}mats[k]=mat;}
"""
    assert old in viewer
    viewer=viewer.replace(old,new)
    needle="g.setIndex(b.f.flat());g.computeVertexNormals();"
    viewer=viewer.replace(needle,"if(b.uv)g.setAttribute('uv',new THREE.Float32BufferAttribute(b.uv.flat(),2));"+needle)
    start="if(o.isMesh)o.visible=!hidden.has(knownLayers[o.userData.layer]||'buildings');"
    replacement="""if(o.isMesh){const l=o.userData.layer;if(l==='original_ground'||l==='legacy_ground')o.visible=!revisedGround;else if(l==='planned_ground'||l==='studylandscape')o.visible=revisedGround&&!hidden.has('terrain');else o.visible=!hidden.has(knownLayers[l]||'buildings');}"""
    assert start in viewer
    viewer=viewer.replace(start,replacement)
    anchor="$('citypick').onchange=e=>{location.href=e.target.value+'.html';};"
    extra="""
$('ground_old').onclick=()=>{revisedGround=false;$('ground_old').setAttribute('aria-pressed','true');$('ground_new').setAttribute('aria-pressed','false');updateLayers();};
$('ground_new').onclick=()=>{revisedGround=true;$('ground_old').setAttribute('aria-pressed','false');$('ground_new').setAttribute('aria-pressed','true');updateLayers();};
$('treeview').onclick=()=>{const s=DATA.ground_study.green_sites[0];if(s)focus(s.box);else{const p=DATA.study_bounds;focus(p);$('selectedText').textContent='本城未新增零碎草坪；自然区域按已有水体或裂隙保留，街坊空地以铺地院场组织。';}};
"""
    assert anchor in viewer
    viewer=viewer.replace(anchor,anchor+extra)
    # 设计查看位置与当前选择一致，东荒同时显示北关前场、树院和分阁。
    viewer=viewer.replace("general();changeMode('space');focus(DATA.study_bounds);$('loading').hidden=true;", """
function focusStudy(){const b=DATA.ground_study.overview_bounds||DATA.study_bounds;focus(b);const distance=Math.max(b[2]-b[0],b[3]-b[1])*1.2;camera.position.set(orbit.target.x-distance,orbit.target.y+distance*.95,orbit.target.z+distance);orbit.update();$('district').value='qianji_study';}
general();changeMode('space');focusStudy();$('loading').hidden=true;
""")
    viewer=viewer.replace("$('qfocus').onclick=()=>focus(DATA.study_bounds);", "$('qfocus').onclick=focusStudy;")
    viewer+= """
const requestView=new URLSearchParams(location.search).get('view');if(requestView==='tree')$('treeview').click();else if(requestView==='city')$('reset').click();
if(new URLSearchParams(location.search).get('ground')==='old')$('ground_old').click();
"""
    return viewer
