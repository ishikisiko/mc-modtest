"""较大住宅的空间与外观预览；不生成游戏蓝图。"""
from pathlib import Path
import argparse,base64,gzip,hashlib,json,shutil,subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT),str(ROOT/'tools')]
from tools.complex_houses.model import build_all,check
from tools.player_homes.preview import bake_instances
from tools.complex_houses.preview_models import ComplexModels


def package_astra(source,out):
    dest=out/'astra';shutil.copytree(source,dest,ignore=shutil.ignore_patterns('__pycache__'))
    # Rebuild the child's original page from unchanged authored inputs first.
    subprocess.run([sys.executable,str(dest/'build_preview.py')],check=True,capture_output=True,text=True)
    provenance=json.loads((dest/'provenance.json').read_text());page=(dest/'index.html').read_text()
    assert hashlib.sha256(page.encode()).hexdigest()==provenance['raw_html_sha256'],'Astra 作者输入与原始交付不一致'
    subprocess.run([sys.executable,str(dest/'write_design.py')],check=True,capture_output=True,text=True)
    page=page.replace('<main id="astra-houses">','<main id="astra-houses"><p class="caption">Astra max 在独立上下文中规划的三套候选；本轮仅做外观和空间预览。</p>')
    navigation="""<script>(()=>{const q=new URLSearchParams(location.search),h=q.get('house'),v=q.get('view');if(h&&/^[0-2]$/.test(h))window.__setHouseView(Number(h),['overview','person','gallery','upper','plan','plot'].includes(v)?v:'overview')})();</script>"""
    (dest/'index.html').write_text(page.replace('</body>',navigation+'</body>'))
    provenance['presentation_changes_by_parent']=['增加作者说明','增加网址初始方案与视角选择']
    (dest/'provenance.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n')

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--out',required=True,type=Path)
    ap.add_argument('--base-url',default='https://assets.ishiki333hhh.win/previews/complex-houses-20261117/')
    ap.add_argument('--assets-jar',type=Path,default=Path('/home/ubuntu/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'))
    args=ap.parse_args();out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    setup={'choices':[],'libraries':[base64.b64encode(gzip.compress((ROOT/p).read_bytes(),mtime=0)).decode()for p in('tools/web/three.min.js','tools/web/OrbitControls.js')]}
    checks={};sites=build_all()
    for site in sites:
        key=site.c['id'];dest=out/key;dest.mkdir();result=check(site);print(key,'预览几何与通路检查通过',flush=True)
        result['preview']=bake_instances(site,args.assets_jar,dest,models_factory=ComplexModels)
        result['preview'].update(geometry_simplified=True,architecture_geometry_simplified=False,approximate_geometry=['单格灰瓦的细小表面与底部构架','床'],whole_curve_roofs='完整原模型')
        setup['choices'].append({'info':site.c,'checks':result,'packed':json.loads((dest/'scene.js').read_text().split('=',1)[1].rstrip(';')),
            'atlas':'data:image/png;base64,'+base64.b64encode((dest/'atlas.png').read_bytes()).decode()})
        (dest/'checks.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');checks[key]=result
    (out/'setup.js').write_text('window.HOUSE_CHOICES_SETUP='+json.dumps(setup,ensure_ascii=False,separators=(',',':'))+';\n')
    page=(ROOT/'tools/complex_houses/viewer.html').read_text().replace('/*POSTER*/',args.base_url+'captures/water-courts/overview.png')
    (out/'index.html').write_text(page);(out/'embed.html').write_text(page.replace('src="setup.js"','src="'+args.base_url+'setup.js"'))
    plans=[{k:s.c[k]for k in('id','name','author','description','reason','concern','size','plot_size','plot_origin','views')}|{'rooms':s.rooms,'courts':s.courts,'stairs':s.flights,'roof_specs':s.roofs,'targets':s.targets}for s in sites]
    (out/'plans.json').write_text(json.dumps(plans,ensure_ascii=False,indent=2)+'\n')
    source=ROOT/'tools/complex_houses/astra'
    if source.is_dir():package_astra(source,out)
    if (ROOT/'tools/complex_houses/gallery.html').exists():(out/'gallery.html').write_text((ROOT/'tools/complex_houses/gallery.html').read_text().replace('/*BASE*/',args.base_url))
    manifest={'scope':'只预览较大住宅的外观和空间；无游戏资源、NBT、SCHEM或JAR导出','source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'source_dirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True)),
        'owner_visual_acceptance':'pending','game_integration':False,'minecraft_runtime':'not_run','native_model_choices':list(checks),'astra_included':source.is_dir(),
        'source_inputs':{p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted((ROOT/'tools/complex_houses').rglob('*'))if p.is_file()and'__pycache__'not in p.parts},
        'files':{p.relative_to(out).as_posix():{'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size}for p in sorted(out.rglob('*'))if p.is_file()}}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'native_model_choices':len(checks),'html_bytes':len(page.encode()),'native_model_walk_targets':sum(len(c['walk']['targets'])for c in checks.values())},ensure_ascii=False))

if __name__=='__main__':main()
