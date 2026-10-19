#!/usr/bin/env python3
"""Publish only a completed, terminated Modal courtyard result to the existing preview tree."""
from __future__ import annotations
import argparse
import hashlib
import html
import json
from pathlib import Path
import shutil
import zipfile

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'out/preview/reference-courtyard-mc'


def publish(run):
    state=json.loads((run/'state.json').read_text())
    if state.get('status')!='passed' or state.get('termination')!='confirmed':
        raise ValueError('A passing run with confirmed Sandbox termination is required')
    source=run/'artifacts/out/preview/reference-courtyard-mc';runtime=source/'runtime'
    evidence=json.loads((runtime/'evidence.json').read_text())
    if evidence.get('status')!='pass' or evidence.get('unrun') or not all(c['pass'] for c in evidence['checks']):
        raise ValueError('Incomplete native evidence')
    if evidence['source_commit']!=state['commit']:raise ValueError('Source identity mismatch')
    artifacts={}
    for name in ['courtyard_sample_world.zip','courtyard/reference_courtyard_game.nbt','courtyard/reference_courtyard_game.schem']:
        file=runtime/name
        artifacts[name]={'bytes':file.stat().st_size,'sha256':hashlib.sha256(file.read_bytes()).hexdigest()}
    with zipfile.ZipFile(runtime/'courtyard_sample_world.zip') as world:
        if world.testzip() is not None:raise ValueError('Corrupt exported world')
    version=evidence['mod_version'];jar=run/'artifacts/build/libs'/f'myvillage-{version}.jar'
    with zipfile.ZipFile(jar) as z:
        rows=json.loads(z.read('data/myvillage/courtyard/assemblies.json'))['families']
        if len(rows)!=10:raise ValueError('Unexpected building entry count')
        names=set(z.namelist())
        for f in rows:
            for p in [f'assets/myvillage/models/item/{f["id"]}.json',f'assets/myvillage/blockstates/{f["id"]}.json']:
                if p not in names:raise ValueError('Missing jar resource '+p)
        for p in ['data/myvillage/structure/reference_courtyard.nbt','com/example/myvillage/courtyard/CourtyardAssemblyBlock.class']:
            if p not in names:raise ValueError('Missing jar content '+p)
    target=OUT/'runs'/run.name;target.mkdir(parents=True,exist_ok=True)
    shutil.copytree(runtime,target,dirs_exist_ok=True)
    shutil.copyfile(jar,OUT/jar.name)
    report={'run':run.name,'tested_commit':state['commit'],'mod_version':version,'jar':jar.name,
            'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'families':10,'variants':sum(len(f['variants']) for f in rows),
            'native_checks':len(evidence['checks']),'selected':evidence['selected'],'completed':evidence['completed'],'unrun':evidence['unrun'],
            'scope':evidence.get('scope','full courtyard kit'),'artifacts':artifacts,'termination':state['termination'],'owner_appearance':'not_verified','owner_fps':'not_verified','multiplayer':'not_verified'}
    (OUT/'delivery.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    prefix=f'runs/{run.name}/';shots={
        '月洞门正面':prefix+'courtyard/gate.png','月洞门背面':prefix+'courtyard/gate-back.png',
        '正面接缝斜角':prefix+'courtyard/gate-oblique.png','背面接缝斜角':prefix+'courtyard/gate-back-oblique.png',
        '临水门亭':prefix+'courtyard/reference.png','庭院全貌':prefix+'courtyard/overview.png',
        '连续月洞门':prefix+'courtyard/corridor.png','侧室花窗':prefix+'courtyard/alcove.png',
        '反向回廊':prefix+'courtyard/corridor-reverse.png','窗外侧':prefix+'courtyard/window-front.png',
        '窗内侧':prefix+'courtyard/window-back.png','花窗接缝斜角':prefix+'courtyard/window-oblique.png',
        '整体窗放置':prefix+'courtyard/whole-window-placed.png','款式选择':prefix+'courtyard/variant-selector.png',
        '创造栏与 JEI':prefix+'courtyard/creative-jei.png'}
    shots.update({'屋面正面':prefix+'courtyard/roof-front.png','主屋侧面':prefix+'courtyard/roof-side.png','檐口近景':prefix+'courtyard/roof-detail.png','瓦面第一页':prefix+'courtyard/roof-selector-page1.png','瓦面第二页':prefix+'courtyard/roof-selector-page2.png'})
    shots.update({'门亭起拱正面':prefix+'courtyard/pavilion-front.png','柱梁接点':prefix+'courtyard/joint-pavilion.png','转角梁接点':prefix+'courtyard/joint-corner.png','主厅纵檩支承':prefix+'courtyard/joint-hall.png','圆窗开间居中':prefix+'courtyard/window-centered.png','主厅后窗对称':prefix+'courtyard/hall-windows.png','梁枋款式选择':prefix+'courtyard/beam-selector.png'})
    required=['月洞门正面','月洞门背面','正面接缝斜角','背面接缝斜角'] if 'gate_walks' in evidence['selected'] else ['临水门亭','庭院全貌','连续月洞门','侧室花窗','整体窗放置','款式选择','创造栏与 JEI']
    if 'interior_walks' in evidence['selected']:
        required=['连续月洞门','反向回廊','窗外侧','窗内侧','花窗接缝斜角','侧室花窗','整体窗放置','款式选择']
        shots={label:shots[label] for label in dict.fromkeys(required+list(shots))}
    if 'roof_walks' in evidence['selected']:
        required=['临水门亭','屋面正面','主屋侧面','檐口近景','庭院全貌','连续月洞门','反向回廊','瓦面第一页','瓦面第二页']
        shots={label:shots[label] for label in dict.fromkeys(required+list(shots))}
    if 'joint_walks' in evidence['selected']:
        required=['柱梁接点','转角梁接点','主厅纵檩支承','圆窗开间居中','主厅后窗对称','门亭起拱正面','屋面正面','临水门亭','梁枋款式选择']
        shots={label:shots[label] for label in dict.fromkeys(required+list(shots))}
    for label in required:
        if not (OUT/shots[label]).is_file():raise FileNotFoundError(shots[label])
    shots={label:path for label,path in shots.items() if (OUT/path).is_file()}
    buttons=''.join(f'<button data-src="{path}">{html.escape(label)}</button>' for label,path in shots.items())
    first=next(iter(shots.values()));checks=report['native_checks'];commit=html.escape(state['commit'][:12])
    first_label=html.escape(next(iter(shots)))
    labels={'place_template':'模板放置','gate_walks':'月洞门正反向按键通行','mapped_walks':'按键通行','variant_selector':'款式选择','whole_window_transaction':'整窗放置与拆除','waterlogged_assembly_removal':'含水恢复','creative_jei_grouping':'创造栏与 JEI','game_screenshots':'实机截图','native_structure_save':'游戏 SAVE','restart_readback':'重启读回','world_export':'世界导出'}
    labels['interior_walks']='回廊/侧室正反通行及主屋入口'
    labels.update(roof_walks='楼梯/回廊/主屋通行',roof_selector='屋顶款式翻页',whole_roof_transaction='125 格整顶放置与非根拆除')
    labels.update(joint_walks='修复布局按键通行',beam_selector='长梁款式选择',whole_beam_transaction='五格梁整件放置与非根拆除')
    check_summary='、'.join(labels[k] for k in evidence['selected'])
    page=f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>月洞水院 · Minecraft 实机样板</title>
<style>*{{box-sizing:border-box}}body{{margin:0;background:#ecece3;color:#283c34;font:15px system-ui,sans-serif}}main{{max-width:1120px;margin:auto;padding:22px}}h1{{font-size:27px;margin:4px 0 10px}}p{{line-height:1.8}}.facts,nav,.downloads{{display:flex;gap:9px;flex-wrap:wrap;margin:14px 0}}.facts span{{background:#dce5da;border-radius:5px;padding:8px 12px}}button,.downloads a{{font:inherit;background:#fafaf1;border:1px solid #aebcab;border-radius:6px;color:#315647;padding:9px 12px;cursor:pointer;text-decoration:none}}button.active{{background:#3e6655;color:white}}img{{width:100%;display:block;border-radius:8px}}.note{{font-size:13px;color:#64776c}}code{{background:#dfe5da;padding:5px 8px;border-radius:5px;overflow-wrap:anywhere}}a{{color:#315e4b}}@media(max-width:600px){{main{{padding:12px}}h1{{font-size:22px}}button,.downloads a{{font-size:13px}}}}</style>
<main><h1>月洞水院 · Minecraft 实机样板</h1><p>以整窗、月洞门、梁柱、瓦檐等可复用构件搭建。以下全部为远程 Minecraft 客户端原始截图。</p>
<div class="facts"><span>10 类统一入口</span><span>{report['variants']} 个款式</span><span>3×3 整体窗</span><span>5×5 月洞门</span></div>
<nav>{buttons}</nav><img id="shot" src="{first}" alt="{first_label}"><p id="caption">{first_label}</p>
<div class="downloads"><a href="{jar.name}" download>下载 MyVillage {version}</a><a href="{prefix}courtyard_sample_world.zip" download>独立样板世界</a><a href="{prefix}courtyard/reference_courtyard_game.nbt" download>游戏保存 NBT</a><a href="{prefix}courtyard/reference_courtyard_game.schem" download>SCHEM</a><a href="{prefix}evidence.json">检查记录</a></div>
<p>创造栏和 JEI 每类只列一个入口。手持构件后 <b>Shift＋右键</b> 选择款式；整窗一次放置消耗一个物品，任意部分拆除都整件移除。遇到障碍不留下碎片、不扣物品。</p>
<p>在独立空地使用 <code>/place template myvillage:reference_courtyard ~ ~ ~</code> 放置样板；占地 48×40 格、高度包围盒 28 格。模板包含空气，会覆盖该范围。需要匹配模组及原有依赖；JEI 可选。</p>
<p class="note">本轮 {checks} 项实机检查通过：{check_summary}。未列出的项目未在本轮重跑。固定源 {commit}，云端实例已确认销毁。软件渲染不代表你的电脑 FPS；最终外观、手感、声音和广泛多人仍待玩家验收。SCHEM 默认方向导出，外部编辑器的旋转/镜像未单独验收。</p>
<p class="note"><a href="delivery.json">版本、SHA-256 与交付信息</a> · <a href="../reference-courtyard/">早期独立体素样稿</a></p></main>
<script>for(const b of document.querySelectorAll('nav button'))b.onclick=()=>{{document.querySelector('#shot').src=b.dataset.src;document.querySelector('#shot').alt=b.textContent;document.querySelector('#caption').textContent=b.textContent;document.querySelectorAll('nav button').forEach(t=>t.classList.toggle('active',t===b))}};document.querySelector('nav button').classList.add('active');</script></html>'''
    (OUT/'index.html').write_text(page)
    inline_buttons=''.join(f'<button onclick="show({i})">{html.escape(label)}</button>' for i,label in enumerate(shots))
    inline_images=''.join(f'<img src="{OUT/path}" alt="{html.escape(label)}"'+('' if i==0 else ' hidden')+'>' for i,(label,path) in enumerate(shots.items()))
    handoff='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{margin:0;color:var(--foreground,#303c34);font:14px system-ui,sans-serif}nav{display:flex;gap:6px;flex-wrap:wrap;margin:10px 0}button{font:inherit;border:1px solid var(--border,#bbc7bc);border-radius:6px;padding:7px 10px;cursor:pointer;background:var(--secondary,#e8eee7);color:inherit}button.active{background:#3e6655;color:white}img{display:block;width:100%;border-radius:8px}img[hidden]{display:none}p{line-height:1.6;margin:8px 0}.muted{color:var(--muted-foreground,#637367);font-size:12px}a{color:var(--accent-foreground,#315e4b)}</style>
<p><b>10 类建筑入口 · '''+str(report['variants'])+''' 个款式 · 3×3 整窗 · 5×5 月洞门</b></p><nav>'''+inline_buttons+'</nav><div>'+inline_images+'''</div><p class="muted">原始 Minecraft 实机画面；手持构件 Shift＋右键选款式。最终外观与玩家电脑 FPS 待验收。</p><p><a href="http://43.156.135.198:8765/reference-courtyard-mc/">完整预览与下载</a></p>
<script>function show(i){document.querySelectorAll('img').forEach((e,n)=>e.hidden=n!==i);document.querySelectorAll('button').forEach((e,n)=>e.classList.toggle('active',n===i))}show(0)</script></html>'''
    (OUT/'handoff.html').write_text(handoff)
    print(json.dumps(report,ensure_ascii=False,indent=2))


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--run',type=Path,required=True);args=parser.parse_args();publish(args.run.resolve())


if __name__=='__main__':main()
