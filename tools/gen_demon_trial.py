#!/usr/bin/env python3
"""Generate the portable trial's code-native faceted pearl, core and item resources."""
import argparse
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/myvillage'
DATA=ROOT/'src/main/resources/data/myvillage'
MESSAGES={
'zh_cn':{
'item.myvillage.demon_lure_pearl':'引魔珠','block.myvillage.demon_trial_core':'引魔试炼·战利品','structure.myvillage.demon_trial':'引魔试炼',
 'tooltip.myvillage.demon_lure_pearl':'右击地面：需 %s 格见方的平地，上方 %s 格净空。',
 'tooltip.myvillage.demon_lure_pearl.reward':'一次性消耗 · 三波试炼 · 建议炼气一至三层',
 'message.myvillage.demon_trial.permission':'当前无法召唤试炼，请在可建造区域使用。',
 'message.myvillage.demon_trial.ground':'请右击实心地面的上表面。',
 'message.myvillage.demon_trial.space':'需要 %s 格见方的平整实心地面，上方至少 %s 格无障碍。引魔珠未消耗。',
 'message.myvillage.demon_trial.loading':'周围区块尚未加载，请稍候重试。引魔珠未消耗。',
 'message.myvillage.demon_trial.nearby':'距离已有试炼或遗迹宝箱不足 %s 格，请换一片空地。',
 'message.myvillage.demon_trial.occupied':'召唤位置被实体占用，请稍移位置。',
 'message.myvillage.demon_trial.peaceful':'和平难度无法开启试炼；已开启的进度保留，恢复难度可继续。',
 'message.myvillage.demon_trial.start':'引魔珠已化为试炼核心！稍后迎战三波敌人，通关后右击核心领奖。',
 'message.myvillage.demon_trial.dormant':'此核心未被引魔珠激活。',
 'message.myvillage.demon_trial.locked':'试炼尚未完成：第 %s/%s 波。',
 'message.myvillage.demon_trial.next':'本波已击败，准备迎战第 %s 波！',
 'message.myvillage.demon_trial.clear':'引魔试炼完成！右击青色核心领取奖励；取空后核心消散。',
 'message.myvillage.demon_trial.progress':'%s · 第 %s/%s 波 · 剩余 %s 名敌人'
},
'en_us':{
'item.myvillage.demon_lure_pearl':'Demon Lure Pearl','block.myvillage.demon_trial_core':'Demon Trial Rewards','structure.myvillage.demon_trial':'Demon Trial',
 'tooltip.myvillage.demon_lure_pearl':'Use on flat ground: %s blocks across, %s blocks clear above.',
 'tooltip.myvillage.demon_lure_pearl.reward':'Consumed on summon · Three waves · Suggested: Qi Refining 1–3',
 'message.myvillage.demon_trial.permission':'Use in an area where you can build.',
 'message.myvillage.demon_trial.ground':'Use on the top of solid ground.',
 'message.myvillage.demon_trial.space':'Requires a flat solid %s-block square with %s clear blocks above. Pearl not consumed.',
 'message.myvillage.demon_trial.loading':'Nearby chunks are still loading. Try again; pearl not consumed.',
 'message.myvillage.demon_trial.nearby':'Move at least %s blocks from existing trials and ruin caches.',
 'message.myvillage.demon_trial.occupied':'An entity occupies the core position. Try another spot.',
 'message.myvillage.demon_trial.peaceful':'Trials cannot start in Peaceful. Existing progress resumes on other difficulties.',
 'message.myvillage.demon_trial.start':'Trial summoned! Defeat three waves, then use the core to claim the shared rewards.',
 'message.myvillage.demon_trial.dormant':'This core was not activated by a pearl.',
 'message.myvillage.demon_trial.locked':'Trial unfinished: wave %s/%s.',
 'message.myvillage.demon_trial.next':'Wave defeated. Prepare for wave %s!',
 'message.myvillage.demon_trial.clear':'Trial complete! Use the cyan core for rewards. It vanishes when emptied.',
 'message.myvillage.demon_trial.progress':'%s · Wave %s/%s · %s enemies remaining'
}}

def pearl(lit=False,stand=False):
    elements=[]
    def box(a,b,t):elements.append({'from':a,'to':b,'faces':{f:{'texture':'#'+t} for f in ('up','down','north','south','east','west')}})
    # Layered octagonal silhouette: readable as a bead, rather than a cube or potion.
    for y,lo,hi in [(3,5,11),(5,3,13),(7,2,14),(9,2,14),(11,3,13),(13,5,11)]:
        box([lo,y,lo],[hi,y+2,hi],'shell')
    # A jagged red fault line wraps the front; turns cyan after victory.
    for a,b in [([7,4,4.8],[9,6,5.2]),([8,6,2.8],[10,8,3.2]),([6,8,1.8],[9,10,2.2]),([5,10,2.8],[7,12,3.2]),([6,12,4.8],[8,14,5.2])]:
        for _ in range(4):
            box(a,b,'seal')
            # Carry the status fault around every face so the reward state reads from any approach.
            a,b=[16-b[2],a[1],a[0]],[16-a[2],b[1],b[0]]
    # Brass caps distinguish it from a pill; summoned form rests on a small runic base.
    box([6,2,6],[10,3,10],'metal');box([6,15,6],[10,16,10],'metal')
    if stand:box([1,0,1],[15,1,15],'base');box([3,1,3],[13,2,13],'metal')
    return {'ambientocclusion':False,'textures':{'shell':'minecraft:block/crying_obsidian','seal':'minecraft:block/sea_lantern' if lit else 'minecraft:block/redstone_block','metal':'minecraft:block/copper_block','base':'minecraft:block/chiseled_deepslate','particle':'minecraft:block/crying_obsidian'},'elements':elements}

def outputs():
    files={}
    def emit(p,v):files[p]=(json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode()
    for lit in (False,True):emit(ASSETS/f'models/block/demon_trial_core_{str(lit).lower()}.json',pearl(lit,True))
    emit(ASSETS/'blockstates/demon_trial_core.json',{'variants':{f'facing={face},lit={str(lit).lower()}':{'model':f'myvillage:block/demon_trial_core_{str(lit).lower()}','y':turn} for face,turn in [('north',0),('east',90),('south',180),('west',270)] for lit in (False,True)}})
    model=pearl();model['display']={
        'gui':{'rotation':[20,-25,0],'translation':[0,0,0],'scale':[.85,.85,.85]},
        'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.35,.35,.35]},
        'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.7,.7,.7]},
        'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.4,.4,.4]},
        'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.4,.4,.4]},
        'firstperson_righthand':{'rotation':[0,-20,0],'translation':[1,2,0],'scale':[.5,.5,.5]},
        'firstperson_lefthand':{'rotation':[0,20,0],'translation':[1,2,0],'scale':[.5,.5,.5]}}
    emit(ASSETS/'models/item/demon_lure_pearl.json',model)
    emit(DATA/'recipe/demon_lure_pearl.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':[' S ','BEB',' S '],'key':{'S':{'item':'myvillage:low_grade_spirit_stone'},'B':{'item':'minecraft:bone'},'E':{'item':'minecraft:ender_pearl'}},'result':{'id':'myvillage:demon_lure_pearl','count':1}})
    emit(DATA/'advancement/recipes/misc/demon_lure_pearl.json',{'parent':'minecraft:recipes/root','criteria':{'has_pearl':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:ender_pearl']}]}},'has_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'myvillage:demon_lure_pearl'}}},'requirements':[['has_pearl','has_recipe']],'rewards':{'recipes':['myvillage:demon_lure_pearl']}})
    for lang,entries in MESSAGES.items():
        path=ASSETS/f'lang/{lang}.json';existing=json.loads(path.read_text());existing.update(entries);emit(path,existing)
    return files

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--check',action='store_true');args=ap.parse_args()
    bad=[]
    for path,data in outputs().items():
        if args.check:
            if not path.exists() or path.read_bytes()!=data:bad.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    if bad:raise SystemExit('Demon trial resource drift: '+', '.join(bad))
    print('Demon trial resources '+('checked' if args.check else 'generated'))
if __name__=='__main__':main()
