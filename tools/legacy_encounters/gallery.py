"""采集当前资源的实机造型图，并在独立记录中补拍双客户端飞剑。静态夹具只供外观审阅。"""
from __future__ import annotations
import argparse,hashlib,json,math,os,secrets,sys,time,traceback
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from tools.combat_capture import session as cs,procs
from tools.gen_legacy_encounters import catalogue

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    if os.environ.get('MYVILLAGE_CLOUD_PROVIDER')!='Colab' or not str(ROOT).startswith('/content/'):raise RuntimeError('Colab only')
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
    report={'kind':'legacy_native_visual_review','method':__doc__,'status':'running','owner_verdict':'human_review_pending',
            'source_commit':json.loads((ROOT/'source-manifest.json').read_text())['commit'],
            'collector_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'images':[],'failures':[]}
    session=None
    def save():(out/'evidence.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    def command(text):
        result=session.run(text,warn=False)[0];report.setdefault('commands',[]).append({'command':text,'reply':result});save();return result
    try:
        state=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),reuse_world=True,prebuilt_resources=True,
            language='zh_cn',view_distance=3,settle=3,server_timeout=600,client_timeout=600,lock=str(procs.default_lock_path(ROOT))))
        if not state or state.get('phase')!='ready':raise RuntimeError('No ready gallery client')
        session=cs.Session.attach(ui_check=True)
        for text in ['tick unfreeze','myvillage world pause','weather clear','time set noon','gamerule doDaylightCycle false',
                     'gamerule doWeatherCycle false','kill @e[tag=legacy_target]','kill @e[tag=locked_fixture]',
                     *['kill @e[type=myvillage:'+m['id']+']' for m in catalogue()['mobs']],
                     'fill -8 -61 -2 8 -61 16 minecraft:stone','kill @e[type=minecraft:item]','kill @e[type=minecraft:experience_orb]',
                     f'gamemode spectator {session.username}']:
            command(text)
        session.game.recover_grab();session.game.key('F1');report['hud_input']='mapped F1';time.sleep(1)
        for mob in catalogue()['mobs']:
            command('kill @e[tag=legacy_gallery]');time.sleep(1)
            command('kill @e[type=minecraft:item]');command('kill @e[type=minecraft:experience_orb]')
            pitch=math.degrees(math.atan2(1.62-mob['physical']['height']*.5,math.hypot(1.2,2.8)))
            yaw=math.degrees(math.atan2(-1.2,2.8));rotation=yaw+180
            command(f'tp {session.username} -.7 -60 5.2 {yaw} {pitch}')
            command('summon myvillage:'+mob['id']+' .5 -60 8 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:["legacy_gallery"],Rotation:['+str(rotation)+'f,0f]}')
            time.sleep(2)
            file=mob['id']+'-gallery.png';session.game.save_png(session.game.grab_raw(),out/file)
            report['images'].append({'file':file,'group':mob['id'],'label':mob['name']+'的实机模型（静态审阅夹具）'});save()
            if mob['id']=='ember_toad':
                command('data merge entity @e[tag=legacy_gallery,limit=1] {LegacyHeat:3}');time.sleep(2)
                file='ember_toad-heat-gallery.png';session.game.save_png(session.game.grab_raw(),out/file)
                report['images'].append({'file':file,'group':mob['id'],'label':'吞火蟾蓄满火后的发光区域（状态夹具）'});save()
        session.game.key('F1')
        command('kill @e[tag=legacy_gallery]');time.sleep(1)
        command('kill @e[type=minecraft:item]');command('kill @e[type=minecraft:experience_orb]')
        from tools.legacy_encounters.evidence import LegacyEvidence
        observer=LegacyEvidence(out/'observer',['two_players'],attach=True)
        try:
            observer.run()
        except BaseException as error:
            observer.report['failures'].append(repr(error));raise
        finally:
            observer.report['passed']=not observer.report['failures'] and not observer.report['unrun'] and all(c['pass'] for c in observer.report['checks'])
            observer.save()
        if not observer.report['passed']:raise RuntimeError('Observer capture checks failed')
        report['multiplayer_review']={'evidence':'observer/evidence.json','passed':True}
        report['images'].append({'file':'observer/observer-sees-sword.png','group':'wandering_sword','label':'第二名真实客户端从侧面观察飞剑和施法者。'})
        report['status']='captured_for_review'
    except BaseException as error:report['failures'].append(repr(error));report['status']='failed';traceback.print_exc()
    finally:
        if session:cs.stop()
        save()
    return 0 if report['status']=='captured_for_review' else 1

if __name__=='__main__':raise SystemExit(main())
