#!/usr/bin/env python3
"""构造玩家可选住宅与月洞水院对照，输出独立蓝图及同源三维。"""
from pathlib import Path
import argparse,hashlib,html,json,subprocess,sys,zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.player_homes.model import build_home,build_reference,assembly_check,walk_check
from tools.player_homes.preview import bake_home,plan_svg
from tools.player_estates.build import canonical
from tools.buildgen.nbtread import read_gzipped_nbt,state_string
import voxel_dump_to_structures as exports
import json_to_nbt as nbt

INPUT=ROOT/'blueprints/player_homes/catalog.json'
BASE='https://assets.ishiki333hhh.win/previews/player-house-templates-20261117/'


def export_home(site,out):
    out.mkdir(parents=True,exist_ok=False);W,H,D=site.c['size'];blocks=dict(site.blocks)
    blocks[W-1,H-1,D-1]='minecraft:air';structure=exports.Structure(blocks)
    nbt.write_gzipped_nbt(exports.build_structure_nbt(structure,exports.DEFAULT_DATA_VERSION,'Yuanxian player-home design study'),str(out/'home.nbt'))
    schem,_=exports.build_schem(structure,exports.DEFAULT_DATA_VERSION);exports.write_gzipped_nbt_ext(schem,out/'home.schem','Schematic')
    exports.write_blueprint(exports.build_blueprint(structure,site.config['id'],{'name':site.c['name'],'status':'design_preview'}),out/'home.blueprint.json')
    _,saved=read_gzipped_nbt(str(out/'home.nbt'));palette=[state_string(p)for p in saved['palette']]
    actual={tuple(b['pos']):canonical(palette[b['state']])for b in saved['blocks']if palette[b['state']]!='minecraft:air'}
    expected={p:canonical(s)for p,s in site.blocks.items()}
    assert actual==expected,'NBT逐坐标回读不同'
    _,saved_schem=read_gzipped_nbt(str(out/'home.schem'));palette={v:canonical(k)for k,v in saved_schem['Palette'].items()}
    values=exports.decode_varints(saved_schem['BlockData']);assert len(values)==W*H*D
    for i,value in enumerate(values):
        pos=(i%W,i//(W*D),(i//W)%D)
        assert palette[value]==expected.get(pos,'minecraft:air'),('SCHEM逐坐标回读不同',pos)
    return {'nbt_non_air_cells':len(expected),'schem_volume_cells':len(values),'mismatches':0,
            'nbt_size':saved['size'],'schem_size':[saved_schem['Width'],saved_schem['Height'],saved_schem['Length']],
            'nbt_requires_empty_site':True,'schem_contains_air':True}


def check_plot(catalog,config):
    W,D=catalog['plot_size'];w,_,d=config['size'];x,z=config['plot_origin']
    assert 0<=x and 0<=z and x+w<=W and z+d<=D,('住宅不能放入宅田地块',config['id'])
    for a,b,c,e in config.get('farm_example',[]):
        assert 0<=a<c<=W and 0<=b<e<=D
        assert not(a<x+w and c>x and b<z+d and e>z),('示意种植区域侵入住宅',config['id'])
    return {'plot_size':[W,D],'house_size':[w,d],'plot_origin':[x,z],'outside_house_cells':W*D-w*d,
            'farm_example_only':True,'rotated_plot_geometric_fit':True,'worldedit_rotation':'not_verified'}


def catalog_page(rows,base):
    data=[{'id':r['id'],'name':r['name'],'description':r['description'],'size':r['size'],'plot':r['checks']['plot'],
           'preview_url':base+r['id']+'/index.html','plan_url':base+r['id']+'/plan.svg'}for r in rows]
    payload=json.dumps(data,ensure_ascii=False,separators=(',',':'))
    return '''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>玩家住宅模板选型</title><style>
:root{color-scheme:dark}*{box-sizing:border-box}html,body{margin:0;background:transparent;color:var(--foreground,#e4e6dd);font:14px var(--font-sans,system-ui,sans-serif)}p{line-height:1.7;margin:8px 0}nav,.links{display:flex;flex-wrap:wrap;gap:8px;margin:10px 0}button,a{font:inherit}button{border:1px solid var(--border,#50604c);border-radius:var(--radius,6px);padding:9px 12px;background:var(--secondary,#293b2a);color:inherit;cursor:pointer}button[aria-pressed=true]{background:var(--primary,#728c60);color:var(--primary-foreground,#fff)}a{color:var(--primary,#afc89c)}.muted{color:var(--muted-foreground,#a7b4a1);font-size:12px}.facts{padding:12px 16px;border-radius:var(--radius,8px);background:var(--card,#263328)}iframe{display:block;width:100%;height:1040px;border:0}details{margin:12px 0}summary{cursor:pointer}.old{display:block;width:100%;max-width:1100px;margin:12px 0;border-radius:var(--radius,8px)}
</style></head><body><p>已找回月洞水院，并用同一套白墙、灰瓦、月洞门和回廊构件搭了三种住宅。选择下面的模板查看实际蓝图。</p><nav id="choices"></nav><div class="facts"><b id="selected"></b><p id="description"></p><p class="muted" id="area"></p></div><div class="links"><a id="direct" target="_blank" rel="noopener">单独打开三维预览</a><a id="plan" target="_blank">查看宅田占地</a><a href="'''+base+'''source.zip" download>本轮输入与脚本</a><a href="'''+base+'''manifest.json" target="_blank">产物与检查清单</a></div><iframe id="viewer" title="住宅模板三维预览"></iframe><details><summary>找回的月洞水院：历史游戏画面</summary><p class="muted">这是旧版本展示世界的历史截图，供确认建筑样式。下方可下载的原版水院已按当前构件源重新导出。</p><img class="old" src="'''+base+'''reference/owner-pc-courtyard.png" alt="月洞水院的历史游戏画面"></details><p class="muted">四套均能放入64×72格净用地；地块中的摆放和浅绿种植框仅为示意，房屋、通路和田地均可调整。本轮未加入游戏选房菜单，也未修改任何存档。</p><script>const BASE='''+json.dumps(base)+''',ROWS='''+payload+''';function select(id){const r=ROWS.find(x=>x.id===id);document.querySelector('#selected').textContent=r.name;document.querySelector('#description').textContent=r.description;document.querySelector('#area').textContent='住宅范围 '+r.size[0]+'×'+r.size[2]+'格；64×72格地块中，住宅范围外剩余 '+r.plot.outside_house_cells+'格，尚未扣除通路。';document.querySelector('#direct').href=r.preview_url;document.querySelector('#plan').href=r.plan_url;document.querySelector('#viewer').src=r.preview_url;document.querySelectorAll('[data-id]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.id===id)))}for(const r of ROWS){const b=document.createElement('button');b.textContent=r.name;b.dataset.id=r.id;b.onclick=()=>select(r.id);document.querySelector('#choices').append(b)}select(new URLSearchParams(location.search).get('template')||ROWS[0].id);</script></body></html>'''


def package_source(out):
    files=[INPUT,*sorted((ROOT/'tools/player_homes').glob('*.py')),ROOT/'tools/player_homes/viewer.html',ROOT/'tools/player_homes/README.md',ROOT/'tools/player_homes/capture.mjs',
           ROOT/'tools/reference_courtyard/assemblies.json',ROOT/'tools/reference_courtyard/assembly_scene.py',
           ROOT/'tools/reference_courtyard/window_layout.py',ROOT/'tools/reference_courtyard/assembly_shapes.py',
           ROOT/'tools/reference_courtyard/minecraft.json',ROOT/'tools/player_estates/preview.py']
    with zipfile.ZipFile(out/'source.zip','w',zipfile.ZIP_DEFLATED)as z:
        for p in files:z.writestr(str(p.relative_to(ROOT)),p.read_bytes())


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--out',type=Path,required=True)
    ap.add_argument('--assets-jar',type=Path,default=Path('/home/ubuntu/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'))
    ap.add_argument('--base-url',default=BASE);args=ap.parse_args();out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    catalog=json.loads(INPUT.read_text());specs=[*catalog['templates'],catalog['reference']]
    status={'status':'running','selected':[s['id']for s in specs],'completed':[],'failed':[],'unrun':[s['id']for s in specs]};rows=[]
    def save():(out/'build-status.json').write_text(json.dumps(status,ensure_ascii=False,indent=2)+'\n')
    save()
    for spec in specs:
        key=spec['id']
        try:
            site=build_reference(ROOT,catalog)if key==catalog['reference']['id']else build_home(ROOT,catalog,spec)
            site.c['plot_size']=catalog['plot_size'];checks={'assemblies':assembly_check(site),'plot':check_plot(catalog,spec)}
            checks['walk']=walk_check(ROOT,catalog,site)if key!=catalog['reference']['id']else{'status':'not_retested','scope':'恢复原源，历史游戏检查另有记录'}
            target=out/key;checks['exports']=export_home(site,target)
            checks['preview']=bake_home(site,args.assets_jar,target,args.base_url+key+'/')
            checks.update(game_runtime='not_run',owner_visual_acceptance='pending',formal_worldgen_modified=False)
            (target/'plan.svg').write_text(plan_svg(site,catalog));(target/'checks.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2)+'\n')
            row={**{k:spec[k]for k in('id','name','description','size')},'blocks':len(site.blocks),'checks':checks};rows.append(row)
            status['completed'].append(key);status['unrun'].remove(key);save();print(key,'完成',len(site.blocks),'个方块',flush=True)
        except BaseException as e:
            status['status']='failed';status['failed'].append({'id':key,'error':repr(e)});status['unrun'].remove(key);save();raise
    package_source(out);(out/'index.html').write_text(catalog_page(rows,args.base_url))
    status['status']='design_preview_generated';save()
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    dirty=bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True))
    manifest={'status':'design_preview','source_commit':commit,'source_dirty':dirty,'authored_input_sha256':hashlib.sha256(INPUT.read_bytes()).hexdigest(),
              'scope':'三套新增住宅及恢复的月洞水院；独立选型与蓝图下载','archive_scope':'作者输入与预览脚本；需同提交项目资源及原版客户端资源',
              'formal_worldgen_modified':False,'minecraft':'not_run','templates':rows,'files':{}}
    for p in sorted(out.rglob('*')):
        if p.is_file():manifest['files'][str(p.relative_to(out))]={'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')


if __name__=='__main__':main()
