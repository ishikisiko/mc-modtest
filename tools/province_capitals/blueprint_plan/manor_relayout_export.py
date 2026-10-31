"""正式七府整城结构导出；每个分块回读，不执行 Gradle 或 Minecraft。"""
from pathlib import Path
import hashlib,json,zipfile

from .manor_relayout import ROOT,read,rotate_cell
from .nbt_entities import write_tile
from .atomic_blocks import collect
from buildgen.nbtread import read_gzipped_nbt,state_string

def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def write(p,value):p=Path(p);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')

def source_hashes(spec_path,spec):
    files={Path(spec_path).resolve(),*(ROOT/p for p in spec['sources'].values())}
    files.add(ROOT/'src/main/resources/data/myvillage/lord_manors/catalog.json')
    files.add(ROOT/'blueprints/lord_manors/config.json')
    files.add(ROOT/'blueprints/lord_manors/structure_rules.json')
    files.add(ROOT/'blueprints/lord_manors/native_trees.json')
    files.add(ROOT/'tools/lord_manors/generated/native_trees.json')
    files.add(ROOT/'tools/lord_manors/treegen/NativeTreeCapture.java')
    files.update(p for p in (ROOT/'tools/lord_manors').glob('*.py') if not p.name.startswith('test_'))
    files.add(ROOT/'src/main/resources/data/myvillage/decoration/demon_pact_pillar.json')
    files.add(ROOT/'blueprints/province_capitals/regional/v2/building_families.json')
    files.add(ROOT/'blueprints/street_furniture/catalog.json')
    manifest=read(spec['sources']['manor_manifest'])
    files.update(ROOT/'src/main/resources/data/myvillage/structure'/(t['template'].split(':',1)[1]+'.nbt') for t in manifest['tiles'])
    files.update((ROOT/'src/main/resources/assets/myvillage/models/block/demon_pact_pillar').glob('part_*.json'))
    for folder in ('tools/province_capitals/blueprint_plan','tools/province_capitals/regional/v2','tools/province_capitals/environment_design','tools/buildgen'):
        files.update(p for p in (ROOT/folder).rglob('*') if p.suffix in ('.py','.json','.js','.html') and not p.name.startswith('test_'))
    files.update((ROOT/'tools'/name) for name in ('json_to_nbt.py','web/three.min.js','web/OrbitControls.js'))
    from buildgen.groups import get_group
    city=read(spec['sources']['city']);files.add(ROOT/'tools/buildgen/styles'/ (get_group(city['group_id']).style_id+'.json'))
    return {str(p.relative_to(ROOT)):sha(p) for p in sorted(files)}

def check_tile(path,cells):
    _,doc=read_gzipped_nbt(str(path));palette=[state_string(p) for p in doc['palette']]
    restored={tuple(row['pos']):palette[row['state']] for row in doc['blocks']}
    expected={p:v[0] for p,v in cells.items()}
    if restored!=expected or doc.get('entities'):raise ValueError('正式蓝图NBT回读不同：'+str(path))

def pillar_groups(planner):
    rows=[];scene=planner.manor_scene
    if not scene.get('edict_roots'):return rows
    pillar=read('src/main/resources/data/myvillage/decoration/demon_pact_pillar.json');w,_,d=planner.manor_manifest['size'];minimum=planner.manor_manifest['source_min'];q=planner.layout['manor']['rotation_quarters'];sx,_,sz=planner.manor_offset
    for root in scene['edict_roots']:
        cells={}
        for part in pillar['parts']:
            x,y,z=[a+b for a,b in zip(root,part['offset'])];xx,zz=rotate_cell(x-minimum[0],z-minimum[2],w,d,q)
            p=(sx+xx,y+planner.manor_ground_delta,sz+zz);state=planner.get(p)
            if state.split('[',1)[0]!='myvillage:demon_pact_pillar':raise ValueError('正式府邸魔契柱组件不完整')
            cells[p]=state
        origin=[min(p[i] for p in cells) for i in range(3)];size=[max(p[i] for p in cells)-origin[i]+1 for i in range(3)]
        rows.append({'id':'manor_pillar_'+str(len(rows)),'block':'myvillage:demon_pact_pillar','origin':origin,'size':size,'cells':cells})
    return rows

def export(spec_path,base,out,*,preview_out=None):
    from .generate import make_planner,html,preview_scene,digest
    spec_path=Path(spec_path).resolve();out=Path(out);spec=read(str(spec_path.relative_to(ROOT)));region=spec['city_id'].removeprefix('capital_')
    if out.exists():raise ValueError('正式导出使用新输出目录，不能覆盖失败证据')
    out.mkdir(parents=True);identity=source_hashes(spec_path,spec);fingerprint=digest(identity)
    write(out/'input-identity.json',{'source_hashes':identity,'source_fingerprint':fingerprint})
    planner=make_planner(spec,read(spec['sources']['city']),read(spec['sources']['environment']),read(spec['sources']['landmarks']));data=planner.compile()
    if data['checks']['issues']:raise ValueError('正式构造存在断路或覆盖：'+json.dumps(data['checks']['issues'],ensure_ascii=False))
    data['source_hashes']=identity;data['source_fingerprint']=fingerprint
    planner.refs={};planner.dirty=set(planner.model.construction.by_tile)
    groups=collect(planner,planner.native_rules,read(planner.native_rules['furniture_source']))+pillar_groups(planner)
    atomic={p for group in groups for p in group['cells']}
    if len(atomic)!=sum(len(group['cells']) for group in groups):raise ValueError('完整组件组发生重叠')
    pack=out/'datapack';write(pack/'pack.mcmeta',{'pack':{'pack_format':spec['minecraft']['pack_format'],'description':spec['name']}})
    target=pack/f'data/myvillage/structure/blueprint_trial/{region}';target.mkdir(parents=True)
    tiles=[];palette=set();block_count=0
    for tz in range(planner.depth//16):
        for tx in range(planner.width//16):
            cells={}
            for z in range(16):
                for x in range(16):
                    top=planner.ground(tx*16+x,tz*16+z)
                    for y in range(top+1):cells[x,y,z]=(planner.model.material('GROUND_TOP') if y==top else planner.model.material('GROUND_FILL'),'terrain','terrain')
            cells.update(planner.model.construction.tile(tx,tz))
            for p in list(cells):
                if (tx*16+p[0],p[1],tz*16+p[2]) in atomic:del cells[p]
            path=target/f'tile_{tx}_{tz}.nbt';count,colors,entities=write_tile(path,cells,planner.height,{})
            check_tile(path,cells);palette.update(row[0] for row in cells.values());block_count+=count
            tiles.append({'phase':'tiles','x':tx*16,'y':0,'z':tz*16,'size':[16,planner.height,16],'template':f'myvillage:blueprint_trial/{region}/'+path.stem,'sha256':sha(path),'blocks':count})
        print('正式结构',region,'行',tz+1,'/',planner.depth//16,flush=True)
    for group in groups:
        origin=group['origin'];cells={tuple(p[i]-origin[i] for i in range(3)):(state,'','') for p,state in group['cells'].items()}
        path=target/(group['id']+'.nbt');count,_,_=write_tile(path,cells,group['size'][1],{},size=group['size']);check_tile(path,cells)
        block_count+=count;palette.update(row[0] for row in cells.values())
        tiles.append({'phase':'atomic_groups','x':origin[0],'y':origin[1],'z':origin[2],'size':group['size'],'template':f'myvillage:blueprint_trial/{region}/'+path.stem,'sha256':sha(path),'blocks':count})
    errors=planner.model.modset.palette_block_errors(sorted(palette))
    if errors:raise ValueError('正式结构存在未注册方块：'+str(errors))
    if identity!=source_hashes(spec_path,spec):raise ValueError('正式生成期间 authored 输入变动')
    functions=pack/f'data/myvillage/function/blueprint_trial/{region}';functions.mkdir(parents=True)
    origin=spec['minecraft']['origin'];batches=[];step=spec['minecraft']['batch_rows']
    for start in range(0,planner.depth//16,step):
        group=[t for t in tiles if t['phase']=='tiles' and start*16<=t['z']<(start+step)*16]
        batches.append((f'batch_{start:02}',group))
    batches.extend((t['template'].rsplit('/',1)[-1],[t]) for t in tiles if t['phase']=='atomic_groups')
    batch_records=[]
    for name,group in batches:
        lo=[min(t[a] for t in group)+origin[i] for i,a in enumerate(('x','y','z'))];hi=[max(t[a]+t['size'][i]-1 for t in group)+origin[i] for i,a in enumerate(('x','y','z'))]
        commands=[f'forceload add {lo[0]} {lo[2]} {hi[0]} {hi[2]}']
        commands += [f'place template {t["template"]} {origin[0]+t["x"]} {origin[1]+t["y"]} {origin[2]+t["z"]}' for t in group]
        commands += [f'forceload remove {lo[0]} {lo[2]} {hi[0]} {hi[2]}']
        (functions/(name+'.mcfunction')).write_text('\n'.join(commands)+'\n')
        batch_records.append({'function':f'myvillage:blueprint_trial/{region}/'+name,'phase':group[0]['phase'],'templates':[t['template'] for t in group],'tiles':len(group),'bounds':[lo[0],lo[2],hi[0],hi[2]]})
    inventory={'schema':2,'city_id':spec['city_id'],'dimensions':planner.city['dimensions'],'origin':origin,'source_fingerprint':fingerprint,'source_hashes':identity,'tiles':tiles,'batches':batch_records,'blocks':block_count,'block_entities':0,'atomic_groups':len(groups),'runtime':'not_run','npc_binding':'not_performed','owner_acceptance':'not_verified'}
    write(out/'inventory.json',inventory);write(out/'plan.json',data)
    archive=out/f'{region}-blueprint-trial.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for f in sorted(pack.rglob('*')):
            if f.is_file():
                info=zipfile.ZipInfo(str(f.relative_to(pack)),date_time=(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,f.read_bytes())
    if preview_out:
        preview_out=Path(preview_out);preview_out.mkdir(parents=True,exist_ok=True)
        write(preview_out/'plan.json',data);write(preview_out/'checks.json',{'blueprint':data['checks'],'native_export':{'nbt_roundtrip':'pass','palette':'pass','runtime':'not_run'}})
        jobs={k:data[k] for k in ('roads','manor','landmark','gardens','paths','doors','earthwork','fixtures')};jobs['building_jobs']=[p['building'] for p in data['plots']]
        jobs['functional_stands']=planner.model.places;jobs['construction_audit']=planner.audit_result
        write(preview_out/'construction.json',jobs)
        from .manor_relayout_sheet import svg
        (preview_out/'blueprint.svg').write_text(svg(planner,data))
        (preview_out/'index.html').write_text(html(data,preview_scene(planner,data)))
    return inventory
