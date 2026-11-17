"""月洞水院宅居草案的同源三维与独立蓝图。"""
from pathlib import Path
import argparse,base64,gzip,hashlib,json,subprocess,sys,zipfile
ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT),str(ROOT/'tools')]
from tools.moon_courtyard_home.model import INPUT,build,check_preservation,walking
from tools.player_homes.preview import bake_instances
from tools.player_homes.build import export_home
from tools.buildgen.nbtread import read_gzipped_nbt,state_string
from tools.player_estates.build import canonical

BASE='https://assets.ishiki333hhh.win/previews/moon-courtyard-home-20261117/'
JAR_URL='https://assets.ishiki333hhh.win/previews/player-house-doors-20261117/yuanxian-0.135.1.jar'


def plan(site):
    from html import escape
    W,D=site.config['plot_size'];ox,oz=site.config['plot_origin'];scale=8
    def rect(bounds,fill,stroke='#6c7568',extra=''):
        a,b,c,d=bounds;return f'<rect x="{a*scale}" y="{b*scale}" width="{(c-a)*scale}" height="{(d-b)*scale}" fill="{fill}" stroke="{stroke}" {extra}/>'
    def text(x,z,name,size=12):return f'<text x="{x*scale}" y="{z*scale}" font-size="{size}" text-anchor="middle" fill="#25352c">{escape(name)}</text>'
    chunks=[f'<svg xmlns="http://www.w3.org/2000/svg" width="552" height="664" viewBox="-20 -32 552 664">',rect([0,0,W,D],'#e4ddcd'),text(32,-1.5,'64×72格宅田；区域可调整',15)]
    chunks += [rect([ox,oz,ox+48,oz+40],'#c9c7bd'),rect([ox+4,oz+4,ox+44,oz+17],'#93bcc7'),rect([ox+3,oz+9,ox+35,oz+12],'#b0956c')]
    for r in site.rooms:
        a,b,c,d=r['bounds'];chunks+=[rect([ox+a,oz+b,ox+c,oz+d],r['color']),text(ox+(a+c)/2,oz+(b+d)/2,r['name'],10)]
    for r in site.config['plot_areas']:
        a,b,c,d=r['bounds'];chunks += [rect(r['bounds'],r['color'],extra='stroke-dasharray="5 4"'),text((a+c)/2,(b+d)/2,r['name'],11)]
    chunks += [text(32,75,'原水院48×40格；范围外剩余2688格，未扣通路',12),'</svg>'];return ''.join(chunks)


def preservation_against_template(site):
    path=ROOT/'src/main/resources/data/yuanxian/structure/reference_courtyard.nbt'
    _,nbt=read_gzipped_nbt(str(path));palette=[state_string(s)for s in nbt['palette']]
    actual={tuple(b['pos']):canonical(palette[b['state']])for b in nbt['blocks']if palette[b['state']]!='minecraft:air'}
    assert actual=={p:canonical(s)for p,s in site.original.items()},'原版恢复源与打包模板不同'
    return {'status':'pass','non_air_cells':len(actual),'resource_sha256':hashlib.sha256(path.read_bytes()).hexdigest()}


def fixture_supports(site):
    from tools.player_estates.preview import parse
    catalog=json.loads((ROOT/'src/main/resources/data/yuanxian/courtyard/assemblies.json').read_text())
    families={f['id']:f for f in catalog['families']};result=[]
    for item in site.config['kit_fixtures']:
        if 'support'not in item:continue
        p=tuple(item['support']);root=item['root'];assert p==(root[0],root[1]+(1 if item['attach']=='above'else -1),root[2]),('灯具支承位置不相邻',item['name']);assert p in site.blocks,('缺少灯具支承',item['name'])
        s=site.blocks[p];name,props=parse(s)
        if name.startswith('yuanxian:'):
            v=families[name.split(':')[1]]['variants'][int(props.get('variant','0'))]
            boxes=v['parts'][int(props.get('part','0'))]['boxes'];y=0 if item['attach']=='above' else 16
            assert props['facing']=='north'
            assert any(b[0]<=8<=b[3] and b[2]<=8<=b[5] and (b[1]==y if y==0 else b[4]==y)for b in boxes),('灯链与梁不接触',item['name'])
        else:assert name=='minecraft:barrel'
        result.append({'name':item['name'],'support':list(p),'attach':item['attach'],'contact':'checked'})
    return result


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--out',type=Path,required=True)
    ap.add_argument('--assets-jar',type=Path,default=Path('/home/ubuntu/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'))
    ap.add_argument('--base-url',default=BASE);args=ap.parse_args();out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    site=build();site.c['views']=site.config['views'];checks={'preservation':check_preservation(site),'baseline_template':preservation_against_template(site),'walk':walking(site),'fixture_supports':fixture_supports(site)}
    checks['exports']=export_home(site,out/'blueprint');checks['preview']=bake_instances(site,args.assets_jar,out)
    checks['preview'].update(geometry_simplified=True,architecture_geometry_simplified=False,approximate_blocks=['minecraft:brown_bed'])
    checks.update(owner_visual_acceptance='pending',minecraft_runtime='not_run',formal_worldgen_modified=False)
    (out/'checks.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2)+'\n');(out/'plan.svg').write_text(plan(site))
    libraries=[base64.b64encode(gzip.compress((ROOT/name).read_bytes(),mtime=0)).decode()for name in ['tools/web/three.min.js','tools/web/OrbitControls.js']]
    setup={'name':site.config['name'],'description':site.config['description'],'config':site.config,'checks':checks,'base':args.base_url,'jar_url':JAR_URL,
           'libraries':libraries,'atlas':'data:image/png;base64,'+base64.b64encode((out/'atlas.png').read_bytes()).decode()}
    page=(ROOT/'tools/moon_courtyard_home/viewer.html').read_text().replace('/*SCENE*/',(out/'scene.js').read_text()).replace('/*SETUP*/',json.dumps(setup,ensure_ascii=False,separators=(',',':')))
    (out/'index.html').write_text(page)
    files=[INPUT,*sorted((ROOT/'tools/moon_courtyard_home').glob('*.*')),ROOT/'tools/player_homes/preview.py',ROOT/'tools/player_estates/preview.py',ROOT/'tools/reference_courtyard/assembly_scene.py',ROOT/'tools/reference_courtyard/window_layout.py',ROOT/'tools/reference_courtyard/assemblies.json']
    with zipfile.ZipFile(out/'source.zip','w',zipfile.ZIP_DEFLATED)as archive:
        for p in files:archive.writestr(str(p.relative_to(ROOT)),p.read_bytes())
    manifest={'status':'design_preview','source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'source_dirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True)),
              'scope':'原版月洞水院内部宅居草案；保留全部原建筑，未加入游戏选房或城镇生成','owner_visual_acceptance':'pending','minecraft_runtime':'not_run','checks':checks,
              'archive_scope':'本轮作者输入、预览脚本及主要依赖；复现需同提交项目与原版客户端资源','files':{}}
    for p in sorted(out.rglob('*')):
        if p.is_file():manifest['files'][str(p.relative_to(out))]={'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:v for k,v in checks.items()if k not in ['walk','preview']},ensure_ascii=False))

if __name__=='__main__':main()
