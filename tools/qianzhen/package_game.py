#!/usr/bin/env python3
"""按固定源和已完成地点组合原生验收证据；不修改游戏 NBT、SCHEM 或画面。"""
from __future__ import annotations
import argparse
import ast
import copy
import hashlib
import html
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_run(path):
    state = json.loads((path/'state.json').read_text())
    if state.get('termination') != 'confirmed':
        raise ValueError('运行时尚未确认终止: '+path.name)
    folder = path/'artifacts/out/preview/qianzhen-mc-runtime'
    evidence = json.loads((folder/'evidence.json').read_text())
    if evidence['source_commit'] != state['commit']:
        raise ValueError('固定源与原始报告不一致')
    return {'path':path, 'id':path.name, 'state':state, 'folder':folder, 'evidence':evidence}


def source_text(commit, name):
    return subprocess.check_output(['git','show',commit+':'+name],cwd=ROOT,text=True)


def verify_reuse(previous, current):
    a, b = previous['state']['commit'], current['state']['commit']
    protected = ['src','build.gradle','gradle.properties','settings.gradle','tools/combat_capture',
                 'tools/yunshan/game_evidence.py','tools/yunshan/game_export.py',
                 'tools/qianzhen/game_routes.py','tools/qianzhen/build.py','tools/json_to_nbt.py']
    changed = subprocess.check_output(['git','diff','--name-only',a,b,'--',*protected],cwd=ROOT,text=True)
    if changed.strip():
        raise ValueError('不可复用：生产或输入工具已改变: '+changed)
    def movement_methods(commit):
        tree = ast.parse(source_text(commit,'tools/qianzhen/game_evidence.py'))
        wanted = {'travel_to','input_keys','walk_tour','doors','player_pos','setup','start'}
        result = {node.name:ast.dump(node,include_attributes=False) for node in ast.walk(tree)
                  if isinstance(node,ast.FunctionDef) and node.name in wanted}
        result['KEYS'] = next((ast.dump(node,include_attributes=False) for node in tree.body
            if isinstance(node,ast.Assign) and any(isinstance(target,ast.Name) and target.id=='KEYS'
                for target in node.targets)),None)
        return result
    if movement_methods(a) != movement_methods(b):
        raise ValueError('不可复用：行走输入或场景准备方法已改变')


def completed_walk(run, identity, authored):
    row = run['evidence']['buildings'].get(identity,{})
    wanted = set(authored['walk_targets'])
    if set(row.get('mapped_targets',[])) != wanted:
        return False
    if row.get('source_sha256') != sha(ROOT/'blueprints/qianzhen'/(authored['presentation']['role']+'.json')):
        return False
    slug = authored['presentation']['role']
    checks = {r['check']:r['pass'] for r in run['evidence']['checks']}
    if not all(checks.get(slug+'_walk_'+name) for name in wanted|{'return_entry'}):
        return False
    video = run['folder']/slug/'walkthrough.mp4'
    return video.is_file() and video.stat().st_size > 0


def write_page(out, result):
    cards = []
    for row in result['locations']:
        slug = row['id']; stem = row['authored_id'].split(':')[1]+'_game'
        cards.append(f'<article><h2>{html.escape(row["name"])}</h2><img src="{slug}/exterior.png" alt="游戏内外观">'
            f'<p>{len(row["mapped_targets"])} 个检查点和回程已走通 · <a href="{slug}/walkthrough.mp4">完整行走录像</a></p>'
            f'<p><a href="{slug}/{stem}.nbt">原始游戏 NBT</a> · <a href="{slug}/{stem}.schem">SCHEM</a> · '
            f'<a href="{slug}/entry.png">入口</a> · <a href="{slug}/interior.png">室内</a></p></article>')
    facilities = ('<p>设施补验：<a href="facility-recheck/apothecary/alchemy-ui.png">炼丹界面</a> · '
                  '<a href="facility-recheck/initiation_court/testing-ui.png">测灵界面</a> · '
                  '<a href="facility-recheck/evidence.json">右键与传功记录</a></p>') if result.get('facility_recheck') else ''
    page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>阡镇六处 · MC 内验收</title><style>*{box-sizing:border-box}body{margin:0;background:#eee9dd;color:#343f35;font:15px/1.8 system-ui}main{max-width:1200px;margin:auto;padding:24px}.cards{display:grid;grid-template-columns:1fr 1fr;gap:20px}article{padding:16px;background:#faf7ee;border-radius:10px}img{width:100%;display:block}a{color:#3f6c59}h2{font-size:20px}@media(max-width:650px){.cards{grid-template-columns:1fr}}</style><main><h1>阡镇六处 · MC 内验收</h1>
<p>真实 NeoForge 客户端和服务器；独立平坦测试世界，没有改写已有存档。六处完整路线途中不传送、不跳跃，门用实际右键打开。</p>
<p>客栈、药庐的完整路线按相同蓝图和输入代码复用；其他四处复验通过。所有原始来源见 <a href="acceptance.json">组合验收报告</a>。园圃入口导入后踩空的问题已补齐铺地。</p>
<p><a href="qianzhen_showcase_world.zip">独立测试世界</a> · <a href="qianzhen_game_blueprints.zip">游戏蓝图与证据包</a> · <a href="README.txt">导入说明与坐标</a> · <a href="SHA256SUMS.txt">下载哈希</a></p>'''
    if result.get('runtime_jar'):
        page += '<p><a href="'+result['runtime_jar']['file']+'">下载本次使用的 MyVillage jar</a>；仍需安装匹配 NeoForge 和 PAL 依赖。</p>'
    page += facilities+'<div class="cards">'+''.join(cards)+'</div><p>云端软件渲染不代表玩家设备 FPS。NPC、主线触发和自然生成尚未接入；玩家审美、手感、声音及多人未验收。</p></main></html>'
    (out/'index.html').write_text(page)


def package(current, priors, out, facility=None, review=None):
    e = current['evidence']
    if current['state']['status']!='passed' or e['status']!='pass' or e['unrun'] or set(e['selected'])!=set(e['completed']):
        raise ValueError('当前运行未完成选中阶段')
    if out.exists() and any(out.iterdir()):
        raise ValueError('保留已有交付目录；请使用新的输出路径')
    facility_checks = []
    if facility:
        f = facility['evidence']
        if facility['state']['status']!='passed' or f['status']!='pass' or f['unrun']:
            raise ValueError('设施补验未通过')
        verify_reuse(current,facility)
        if not review or review.get('run')!=facility['id'] or review.get('status')!='pass':
            raise ValueError('设施补验需要对应原始图片的人工审阅记录')
        for capture in review['captures']:
            if sha(facility['folder']/capture['path']) != capture['sha256']:
                raise ValueError('人工审阅图片哈希不匹配')
        required_captures = {'apothecary/alchemy-ui.png','initiation_court/testing-ui.png'}
        if not required_captures <= {r['path'] for r in review['captures']}:
            raise ValueError('缺少炼丹和测灵界面的实际图片审阅')
        facility_checks = [copy.deepcopy(r) for r in f['checks'] if '_mapped_' in r['check']]
        if len(facility_checks)!=3 or not all(r['pass'] for r in facility_checks):
            raise ValueError('三项设施检查不完整')
        for check in facility_checks:
            check['source_run'] = facility['id']
            detail = check['detail']
            if 'captures' in detail:
                detail['captures'] = ['facility-recheck/'+name for name in detail['captures']]
            if 'video' in detail:
                detail['video'] = 'facility-recheck/'+detail['video']
    manifest = json.loads((ROOT/'blueprints/qianzhen/manifest.json').read_text())
    records = []
    reuse = {}
    for item in manifest['locations']:
        authored_path = ROOT/'blueprints/qianzhen'/item['source']
        authored = json.loads(authored_path.read_text())
        identity = authored['id']; row = e['buildings'][identity]
        if row['source_sha256'] != sha(authored_path):
            raise ValueError('当前蓝图源与 MC 报告不一致: '+identity)
        if facility and facility['evidence']['buildings'][identity]['source_sha256'] != row['source_sha256']:
            raise ValueError('设施补验蓝图源与当前 MC 报告不一致: '+identity)
        choices = [current,*reversed(priors)]
        walk_run = next((run for run in choices if completed_walk(run,identity,authored)),None)
        if walk_run is None:
            raise ValueError('缺少完整按键路线: '+identity)
        if walk_run is not current:
            verify_reuse(walk_run,current)
            reuse[item['id']] = walk_run
        stem = identity.split(':')[1]+'_game'
        if sha(current['folder']/item['id']/(stem+'.nbt')) != row['game_export']['game_nbt_sha256']:
            raise ValueError('原始游戏 NBT 字节变化')
        for shot in ('exterior','rear','entry','interior'):
            if not (current['folder']/item['id']/(shot+'.png')).is_file():
                raise ValueError('缺少原生画面: '+item['id']+'/'+shot)
        records.append({'id':item['id'],'authored_id':identity,'name':row['name'],'origin':row['origin'],
            'size':row['size'],'source_sha256':row['source_sha256'],'cells_compared':row['game_export']['cells_compared'],
            'non_air_blocks':row['game_export']['non_air_blocks'],'mapped_targets':list(authored['walk_targets']),
            'walk_run':walk_run['id'],'walk_commit':walk_run['state']['commit'],'walk_reused':walk_run is not current,
            'walk_video_sha256':sha(walk_run['folder']/item['id']/'walkthrough.mp4')})
    shutil.copytree(current['folder'],out,dirs_exist_ok=True)
    original = out/'run-evidence';original.mkdir()
    shutil.copy2(current['path']/'state.json',original/(current['id']+'-state.json'))
    shutil.copy2(current['folder']/'evidence.json',original/(current['id']+'-evidence.json'))
    shutil.copy2(current['folder']/'index.html',original/(current['id']+'-index.html'))
    reused_checks = []
    for slug,run in reuse.items():
        for name in ('walkthrough.mp4','walk-complete.png'):
            shutil.copy2(run['folder']/slug/name,out/slug/name)
        shutil.copy2(run['path']/'state.json',original/(run['id']+'-state.json'))
        shutil.copy2(run['folder']/'evidence.json',original/(run['id']+'-evidence.json'))
        reused_checks += [row for row in run['evidence']['checks'] if row['check'].startswith(slug+'_walk_')]
    for item in manifest['locations']:
        shutil.copy2(ROOT/'blueprints/qianzhen'/item['source'],out/item['id']/'authored.json')
    replaced = {r['check'] for r in facility_checks}
    checks = [r for r in e['checks'] if r['check'] not in replaced]+reused_checks+facility_checks
    if facility:
        shutil.copytree(facility['folder'],out/'facility-recheck')
        shutil.copy2(facility['path']/'state.json',original/(facility['id']+'-state.json'))
        shutil.copy2(facility['folder']/'evidence.json',original/(facility['id']+'-evidence.json'))
        (out/'facility-recheck/visual-review.json').write_text(json.dumps(review,ensure_ascii=False,indent=2)+'\n')
    if not all(row['pass'] for row in checks):
        raise ValueError('组合证据包含未通过检查')
    result = {'schema':1,'kind':'qianzhen_native_composed_acceptance','status':'pass',
        'source_commit':e['source_commit'],'mod_version':e['mod_version'],
        'selected':[r['id'] for r in records],'completed':[r['id'] for r in records],'unrun':[],
        'current_run':current['id'],'current_walk_selection':e['walk_locations'],
        'locations':records,'checks':checks,'checks_passed':len(checks),
        'walk_targets':sum(len(r['mapped_targets']) for r in records),'cells_compared':sum(r['cells_compared'] for r in records),
        'reuse_policy':'保留各次原始报告；已完成地点源文件 SHA、生产输入、输入映射/场景准备方法一致才复用',
        'not_verified':['owner_appearance','owner_feel','sound','owner_FPS','multiplayer'],
        'not_implemented':['NPC_story_runtime','main_quest_triggers','natural_generation']}
    if facility:
        result['facility_recheck'] = {'run':facility['id'],'commit':facility['state']['commit'],
            'evidence':'facility-recheck/evidence.json','visual_review':'facility-recheck/visual-review.json',
            'superseded_checks':sorted(replaced),
            'reason':'原炼丹截图早于菜单绘制；原记录不改写，补验用延迟双帧、交互录像和人工图片审阅替代设施结论'}
    jar = current['path']/'artifacts/build/libs'/('myvillage-'+e['mod_version']+'.jar')
    if jar.is_file():
        shutil.copy2(jar,out/jar.name)
        result['runtime_jar'] = {'file':jar.name,'sha256':sha(jar),'source_commit':e['source_commit']}
    (out/'acceptance.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    lines=['阡镇 · 六处地点 MC 内验收',f'Minecraft 1.21.1 / MyVillage {e["mod_version"]}',
           '本次环境：NeoForge 21.1.233；PlayerAnimationLibNeoforge 1.1.4+mc.1.21.1。',
           '测试世界 ZIP 解压后，将 Qianzhen MC Acceptance 文件夹放入对应实例的 saves，再在匹配模组环境打开。',
           f'当前固定源：{e["source_commit"]}',f'当前运行：{current["id"]}',
           '场景是独立平坦测试世界；原有用户存档未修改。',
           'NBT 来自原生结构方块 SAVE，保持原始字节；SCHEM 从该保存转换并保留有类型方块实体。',
           '文件含显式空气；使用独立世界或存档副本导入。最小角为粘贴原点，低 z 为正面。',
           '所有门经实际右键打开。每段录像只有入口外的起点准备，途中不传送、不跳跃；临近折点用 Shift 潜行微调。',
           '录像与照片保持原始采集内容。游戏蓝图包不内嵌 MP4；完整录像可从交付页单独下载。',
           'acceptance.json 是六处组合结论；evidence.json 是当前局部运行，run-evidence/ 保留来源报告和销毁确认。',
           '此场景不含 NPC/主线任务/自然生成接入，不代表玩家审美、声音、FPS 或多人验收。','']
    lines += [f'{r["name"]}：最小角 {r["origin"]}，尺寸 {r["size"]}，步行来源 {r["walk_run"]}'+('（复用）' if r['walk_reused'] else '') for r in records]
    if facility:
        lines += ['', '设施补验来源：'+facility['id']+'，固定源 '+facility['state']['commit'],
                  'facility-recheck/ 保存新菜单双帧、右键录像、服务器传功回读和人工图片审阅。原批次记录及图片保持原样。']
    (out/'README.txt').write_text('\n'.join(lines)+'\n')
    write_page(out,result)
    with zipfile.ZipFile(out/'qianzhen_game_blueprints.zip','w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(out.rglob('*')):
            if p.is_file() and p.suffix in ('.nbt','.schem','.json','.png','.html','.txt') and p.name!='SHA256SUMS.txt':
                z.write(p,p.relative_to(out))
    for name in ('qianzhen_game_blueprints.zip','qianzhen_showcase_world.zip'):
        with zipfile.ZipFile(out/name) as z:
            if z.testzip() is not None:raise ValueError('ZIP CRC 失败: '+name)
    (out/'SHA256SUMS.txt').write_text(''.join(sha(p)+'  '+str(p.relative_to(out))+'\n' for p in sorted(out.rglob('*'))
        if p.is_file() and p.suffix in ('.zip','.jar','.nbt','.schem','.mp4')))
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--current-run',type=Path,required=True)
    parser.add_argument('--prior-run',type=Path,action='append',default=[])
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--facility-run',type=Path)
    parser.add_argument('--visual-review',type=Path)
    args=parser.parse_args()
    result=package(load_run(args.current_run),[load_run(p) for p in args.prior_run],args.out,
                   load_run(args.facility_run) if args.facility_run else None,
                   json.loads(args.visual_review.read_text()) if args.visual_review else None)
    print(json.dumps({k:result[k] for k in ('status','source_commit','walk_targets','cells_compared','checks_passed')},ensure_ascii=False))


if __name__=='__main__':main()
