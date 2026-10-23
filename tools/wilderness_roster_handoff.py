#!/usr/bin/env python3
"""Prepare and export the owned flat acceptance world; never operates on an owner save or VM lifecycle."""
from __future__ import annotations
import argparse,json,math,os,sys,time,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.combat_capture import session as cs
from tools.gen_wilderness_roster import catalogue
from tools.thunder_tiger_evidence import allow_commands_handoff_copy
from tools.wilderness_roster_evidence import RosterEvidence

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    if os.environ.get('MYVILLAGE_CLOUD_PROVIDER')!='Colab' or not str(ROOT).startswith('/content/'):raise RuntimeError('Owned Colab only')
    if (a.out/'evidence.json').exists():raise RuntimeError('Preserve existing handoff evidence')
    e=RosterEvidence(a.out,[],True);e.start();u=e.s.username
    for cmd in ['tick unfreeze','tick rate 20','gamerule doMobSpawning false','gamerule naturalRegeneration true','gamerule keepInventory true','time set day','defaultgamemode creative',f'gamemode creative {u}','difficulty peaceful','kill @e[type=!minecraft:player]','fill -24 -60 -12 24 -52 40 minecraft:air','fill -24 -61 -12 24 -61 40 minecraft:smooth_stone','setworldspawn 0 -60 0 0']:
        e.rc(cmd)
    time.sleep(1);e.rc('difficulty normal')
    def sign(x,z,lines):
        messages=[json.dumps(json.dumps({'text':s,'color':'dark_green'},ensure_ascii=False),ensure_ascii=False) for s in lines]
        e.rc(f'setblock {x} -60 {z} minecraft:oak_sign[rotation=8]'+ '{front_text:{messages:['+','.join(messages)+']}}')
    for i,m in enumerate(catalogue()['mobs']):
        x=(i%3-1)*12;z=12+(i//3)*14
        e.rc(f'fill {x-3} -61 {z-3} {x+3} -61 {z+3} minecraft:polished_andesite')
        e.rc(f'summon myvillage:{m["id"]} {x+.5} -60 {z+.5} {{NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:["roster_gallery"]}}')
        sign(x,z-4,[m['name'],m['role'],m['material'],'静态标本 · 演武请用蛋'])
    e.rc('setblock -16 -60 0 myvillage:spirit_testing_stele');e.rc('setblock -13 -60 0 myvillage:technique_inheritance_stele')
    e.rc('setblock 9 -60 0 myvillage:alchemy_furnace[facing=north]');e.rc('setblock 13 -60 0 myvillage:forging_table');e.rc('setblock 17 -60 0 myvillage:forging_anvil');e.rc('setblock 18 -60 0 myvillage:quenching_trough')
    chests=[(-16,4),(-12,4),(-8,4),(8,4),(12,4),(16,4)]
    for x,z in chests:e.rc(f'setblock {x} -60 {z} minecraft:chest[facing=north]')
    def put(chest,slot,item,count=1):x,z=chests[chest];e.rc(f'item replace block {x} -60 {z} container.{slot} with {item} {count}')
    for i,m in enumerate(catalogue()['mobs']):
        put(0,i,'myvillage:'+m['id']+'_spawn_egg',8);put(1,i,'myvillage:'+m['material_id'],64);put(2,i,'myvillage:formula_'+m['pill_id'],2)
        put(3,i,'myvillage:pill_'+m['pill_id']+'[myvillage:pill_quality=2]',8)
        put(4,i,'myvillage:qingfeng_sword[myvillage:forge_mark={trait:"'+m['trait_id']+'",quality:3}]')
    for i,id in enumerate(['spirit_leaf','dew_blossom','sunroot','blood_berry','cloud_fungus','calm_lotus','low_grade_spirit_stone']):put(1,9+i,'myvillage:'+id,64)
    for i,id in enumerate(['qingfeng_sword','lingxiao_spear','pattern_qingfeng_sword','pattern_lingxiao_spear']):put(5,i,'myvillage:'+id)
    for i,id in enumerate(['iron_helmet','iron_chestplate','iron_leggings','iron_boots','shield','water_bucket','coal','flint']):put(5,9+i,'minecraft:'+id,16 if id in ('coal','flint') else 1)
    sign(0,3,['野外历练验收场','两侧箱子含材料和成品','先看标本，再去空地试招','平坦测试世界 · 不替换旧档'])
    sign(-16,6,['刷怪蛋 / 主材 / 丹方','测灵碑与传承碑在后方','生产按真实界面操作','默认创造模式便于观察'])
    sign(12,6,['丹药 / 专长剑 / 装备','专长互斥，药效不叠加','普通炼器鼎在后方','切生存后检验战斗效果'])
    for i,m in enumerate(catalogue()['mobs']):
        e.check('handoff_specimen_'+m['id'],'Test passed' in e.rc('execute if entity @e[type=myvillage:'+m['id']+',tag=roster_gallery]'),m['id'])
    e.rc('kill @e[type=minecraft:item]');e.rc('kill @e[type=minecraft:experience_orb]');e.rc(f'clear {u}')
    time.sleep(2)
    e.s.game.key('F1')
    try:
        for i,m in enumerate(catalogue()['mobs']):
            x=(i%3-1)*12+.5;z=12+(i//3)*14+.5
            # Front three-quarter camera at a normal, readable distance.
            px=x-2.1;pz=z-3.2;dx=x-px;dz=z-pz;yaw=math.degrees(math.atan2(-dx,dz));pitch=math.degrees(math.atan2(1.62-m['dimensions']['height']*.55,math.hypot(dx,dz)))
            e.rc(f'tp {u} {px} -60 {pz} {yaw} {pitch}');time.sleep(.7);e.capture(m['id']+'-hero.png')
        e.rc(f'tp {u} .5 -55 -6.5 0 24');time.sleep(.7);e.capture('acceptance-yard.png')
    finally:e.s.game.key('F1')
    e.rc(f'tp {u} .5 -60 .5 0 0');e.rc('forceload remove all');e.rc('save-all flush');e.stop()
    world=cs.SERVER_DIR/cs.LEVEL_NAME;target=a.out/'wilderness-roster-acceptance-world.zip'
    instructions='''野外历练 0.84.0 独立验收场

把 ZIP 中的 wilderness-roster-acceptance 文件夹放入 saves，使用本次 myvillage-0.84.0.jar。
这是独立平坦试用世界，默认创造模式、保留物品、关闭随机刷怪；不会替换已有存档。
六只静态标本用于观察模型；左侧箱子是刷怪蛋、主材和丹方，右侧是丹药、六种专长剑及装备。
去空地使用刷怪蛋可试普通 AI。刷怪蛋是基础属性试招；自然出生的地区境界另有测试证据。
新角色可先使用测灵碑、传承碑，按游戏指南开始正常学习、炼丹和炼器。
快速开发验收可在聊天依次执行（世界副本已开启命令）：
/myvillage cultivation awaken @s
/myvillage cultivation initiate @s
/myvillage cultivation setrealm @s myvillage:qi_refining myvillage:qi_refining_4
/myvillage cultivation crafting set @s alchemy 100
/myvillage cultivation crafting set @s forging 100
/myvillage cultivation learn @s myvillage:liuyun_bu
/myvillage cultivation learn @s myvillage:huifeng_jianzhen
/myvillage cultivation learn @s myvillage:linghuo_shu
/myvillage cultivation qi fill @s
/gamemode survival
R 切修炼战斗，左键基础连招、Alt 身法；H 的技能页可分配回风剑阵和灵火术。
可用 /gamemode creative 回到观察状态。
本 ZIP 不包含采集角色玩家数据，不转移其 300 生命夹具或其他属性；手感、平衡、音效与 FPS 由实际试玩判断。
'''
    (a.out/'验收说明.txt').write_text(instructions)
    with zipfile.ZipFile(target,'x',zipfile.ZIP_DEFLATED) as z:
        for path in sorted(world.rglob('*')):
            rel=path.relative_to(world)
            if not path.is_file() or path.is_symlink() or rel.parts[0] in ('playerdata','advancements','stats') or path.name in ('session.lock','level.dat_old'):continue
            dest='wilderness-roster-acceptance/'+str(rel)
            if rel==Path('level.dat'):z.writestr(dest,allow_commands_handoff_copy(path.read_bytes())[0])
            else:z.write(path,dest)
        z.writestr('wilderness-roster-acceptance/验收说明.txt',instructions)
    with zipfile.ZipFile(target) as z:assert z.testzip() is None
    e.report.update(status='passed',world_file=target.name,colab_retained=True,game_stopped_for_consistent_export=True);e.save()
    print(json.dumps({'world':str(target),'bytes':target.stat().st_size,'colab_retained':True}))
if __name__=='__main__':main()
