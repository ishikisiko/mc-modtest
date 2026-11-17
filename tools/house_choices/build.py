"""烘焙住宅候选的离线三维页面，不导出游戏蓝图。"""
from pathlib import Path
import argparse,base64,gzip,hashlib,json,shutil,subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT),str(ROOT/'tools')]
from tools.house_choices.model import build_all,validate
from tools.player_homes.preview import bake_instances

ASTRA_LINK='<t3-thread://v2/981349c3-e6a1-461b-978f-decf7d5f0e8c/thread:delegated-task:command%3Amcp%3A9b0c771d-7f62-4892-87a4-7c821bc33973%3Adelegate-task%3Ahouse-choices-astra-independent-three-20261117>'


def write_astra(out):
    source=ROOT/'tools/house_choices/astra';dest=out/'astra';dest.mkdir()
    page=(source/'viewer.html').read_text()
    note='<p style="margin:0;font-size:12px;line-height:1.6;color:var(--muted-foreground)">Astra max 独立规划。这里使用简化构件与程序像素材质展示布局和体量；三套均未接入游戏。</p>'
    page=page.replace('<div id="astra-houses">','<div id="astra-houses">'+note)
    # URL selection only affects the initial choice; the authored architecture is unchanged.
    navigation="""<script>(()=>{const q=new URLSearchParams(location.search),h=q.get('house'),v=q.get('view');if(h&&/^[0-2]$/.test(h))document.querySelector('[data-house="'+h+'"]').click();if(v&&['overview','person','plan','plot'].includes(v))document.querySelector('[data-view="'+v+'"]').click()})();</script>"""
    page=page.replace('</body>',navigation+'</body>')
    (dest/'index.html').write_text(page);shutil.copy2(source/'design.md',dest/'design.md')
    provenance={'model':'gpt-6-astra','reasoningEffort':'max','independent_context':True,'thread_link':ASTRA_LINK,
        'parent_changes':['补充预览材质说明','增加网址初始方案与视角选择'],'architectural_changes':False,
        'raw_sources':{p.name:hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(source.iterdir())if p.is_file()}}
    (dest/'provenance.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n')

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--out',required=True,type=Path)
    ap.add_argument('--base-url',default='https://assets.ishiki333hhh.win/previews/house-choices-six-20261117/')
    ap.add_argument('--assets-jar',type=Path,default=Path('/home/ubuntu/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'))
    args=ap.parse_args();out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    setup={'choices':[],'libraries':[base64.b64encode(gzip.compress((ROOT/p).read_bytes(),mtime=0)).decode()for p in('tools/web/three.min.js','tools/web/OrbitControls.js')]}
    checks={}
    for site in build_all():
        key=site.c['id'];dest=out/key;dest.mkdir();check=validate(site);print(key,'动线通过',flush=True)
        check['preview']=bake_instances(site,args.assets_jar,dest)
        setup['choices'].append({'info':site.c,'checks':check,'packed':json.loads((dest/'scene.js').read_text().split('=',1)[1].rstrip(';')),
            'atlas':'data:image/png;base64,'+base64.b64encode((dest/'atlas.png').read_bytes()).decode()})
        (dest/'checks.json').write_text(json.dumps(check,ensure_ascii=False,indent=2)+'\n');checks[key]=check
    (out/'setup.js').write_text('window.HOUSE_CHOICES_SETUP='+json.dumps(setup,ensure_ascii=False,separators=(',',':'))+';\n')
    page=(ROOT/'tools/house_choices/viewer.html').read_text().replace('/*POSTER*/',args.base_url+'captures/bamboo/overview.png')
    (out/'index.html').write_text(page)
    (out/'embed.html').write_text(page.replace('src="setup.js"','src="'+args.base_url+'setup.js"'))
    plans=[{k:s.c[k]for k in('id','name','author','description','reason','concern','size','plot_size','plot_origin')}|{'rooms':s.rooms,'roof_specs':s.roofs,'targets':s.targets}for s in build_all()]
    (out/'plans.json').write_text(json.dumps(plans,ensure_ascii=False,indent=2)+'\n')
    write_astra(out)
    (out/'gallery.html').write_text((ROOT/'tools/house_choices/gallery.html').read_text().replace('/*BASE*/',args.base_url))
    manifest={'scope':'仅离线三维住宅设计；无游戏资源、NBT、SCHEM或JAR导出','source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'source_dirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True)),
        'owner_visual_acceptance':'pending','game_integration':False,'minecraft_runtime':'not_run','native_model_choices':list(checks),'astra_choices':['松廊小院','赤陶长屋','石阶露台居'],
        'source_inputs':{p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted((ROOT/'tools/house_choices').rglob('*'))if p.is_file()and'__pycache__'not in p.parts},
        'files':{p.relative_to(out).as_posix():{'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size}for p in sorted(out.rglob('*'))if p.is_file()}}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'choices':len(checks)+3,'native_model_choices':len(checks),'html_bytes':len(page.encode()),'native_model_walk_targets':sum(len(c['walk']['targets'])for c in checks.values())},ensure_ascii=False))

if __name__=='__main__':main()
