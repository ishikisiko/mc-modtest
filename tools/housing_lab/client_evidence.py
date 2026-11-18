"""使用真实鼠标领取宅地、选房、旋转并提交建造；仅复用自有新世界。"""
from pathlib import Path
import argparse,json,math,os,secrets,shutil,subprocess,sys,time,traceback

ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT),str(ROOT/'tools')]
from qianji.server import Server
from roads_smoke.run import verify_candidate
from combat_capture.session import CLIENT_OPTIONS,merge_options
from combat_capture.xgame import Game
from roadweaver_probe.run import terminate,write

USER='HouseReview'

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--world',type=Path,required=True);ap.add_argument('--out',type=Path,required=True)
    ap.add_argument('--jar',type=Path,required=True);ap.add_argument('--probe',type=Path,required=True);a=ap.parse_args()
    world=a.world.resolve();assert world.parent==ROOT/'run-housing' and (world/'world/level.dat').is_file(),'只复用本轮自有世界'
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
    server=Server(out,world,2026111701361);server.readiness_command='list';server.view_distance=8
    report={'status':'running','selected':['candidate_identity','mapped_steward_menu','mapped_claim','mapped_placement_build','native_house_view'],
            'completed':[],'failed':[],'unrun':[],'owner_visual_acceptance':'not_verified','fps_acceptance':'not_verified','multiplayer':'not_verified'}
    stage='candidate_identity';client=xvfb=None;logs=[]
    def complete():report['completed'].append(stage);write(out/'report.json',report)
    def wait(predicate,timeout,label):
        deadline=time.monotonic()+timeout
        while not predicate():
            if time.monotonic()>deadline:raise TimeoutError(label)
            if client is not None and client.poll()is not None:raise RuntimeError('客户端提前退出')
            time.sleep(.5)
    def pose(eye,target):
        dx,dy,dz=[b-c for c,b in zip(eye,target)];yaw=math.degrees(math.atan2(-dx,dz));pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz)))
        server.command(f'tp {USER} '+' '.join(f'{v:.3f}'for v in [eye[0],eye[1]-1.62,eye[2],yaw,pitch]))
    try:
        report['candidate']=verify_candidate(a.jar.resolve());complete()
        stage='mapped_steward_menu';server.start();server.command('housing_probe info 0')
        site=json.loads((world/'housing-probe-info-0.json').read_text())['sites'][0];report['site']=site
        x,y,z=site['origin'];server.command(f'forceload add {x-8} {z-8} {x+site["width"]+7} {z+site["depth"]+7}')
        wait(lambda:server.test(f'if loaded {x} {y} {z}')and server.test(f'if loaded {x+site["width"]-1} {y} {z+site["depth"]-1}'),180,'宅地尚未加载');server.command('tick sprint 80t');time.sleep(1)
        server.command('housing_probe vacate 0');fixture=json.loads((world/'housing-probe-vacate-0.json').read_text());assert fixture['status']=='passed',fixture
        report['fixture_animals']=fixture.get('fixture_animals',[]);server.command('housing_probe cleanup 0')
        clientdir=ROOT/'run-housing-client'/secrets.token_hex(5);(clientdir/'mods').mkdir(parents=True);shutil.copy2(a.probe,clientdir/'mods'/a.probe.name)
        display=next(':'+str(i)for i in range(91,150)if not Path(f'/tmp/.X11-unix/X{i}').exists())
        log=(out/'xvfb.log').open('w');logs.append(log)
        xvfb=subprocess.Popen(['Xvfb',display,'-screen','0','1280x720x24','+extension','GLX','+render','-noreset'],stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        wait(lambda:Path('/tmp/.X11-unix/X'+display[1:]).exists(),30,'显示器未启动')
        merge_options(clientdir/'options.txt',{**CLIENT_OPTIONS,'lang':'zh_cn','renderDistance':'8','simulationDistance':'3','maxFps':'20','overrideWidth':'1280','overrideHeight':'720','guiScale':'2'})
        env=dict(os.environ,DISPLAY=display,LIBGL_ALWAYS_SOFTWARE='1',LP_NUM_THREADS='2')
        log=(out/'client-launch.log').open('w');logs.append(log)
        args=['./gradlew','-I','tools/qianji/client.init.gradle','runClient','-x','compileJava','-x','processResources','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx3g',
              f'-Pcombat_smoke_game_dir={clientdir.relative_to(ROOT)}',f'-Pcombat_smoke_username={USER}',f'-Pcombat_smoke_server=127.0.0.1:{server.sport}']
        client=subprocess.Popen(args,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        wait(lambda:USER in server.command('list'),900,'客户端未加入')
        screen=Game(display,clientdir/'logs/latest.log',size=(1280,720))
        if not(screen.wait_ingame(90,poll=1)or screen.recover_grab()):raise RuntimeError('客户端尚不能操作')
        window=screen.xdo('getwindowfocus').strip();screen.xdo('windowsize',window,'1280','720');screen.xdo('windowmove',window,'0','0')
        sx,sy,sz=site['steward'];server.command(f'gamemode creative {USER}');pose([sx+2.8,sy+1.62,sz+.5],[sx+.5,sy+1.25,sz+.5]);time.sleep(4)
        screen.require_ingame('宅地执事右键');screen.xdo('click','3');wait(lambda:screen.pointer_grabbed()is False,15,'右键未打开宅地界面')
        screen.shot(out/'housing-unclaimed.png',max_wait=10,settle=.5);report['mapped_inputs']=[{'input':'mouse.right','purpose':'打开宅地执事界面'}];complete()
        def click(x,y,purpose):
            assert screen.pointer_grabbed()is False,'按钮点击前界面已经关闭'
            screen.xdo('mousemove',str(x),str(y));screen.xdo('click','1');report['mapped_inputs'].append({'input':'mouse.left','position':[x,y],'purpose':purpose});time.sleep(1)
        def status():
            server.command('housing_probe status 0');return json.loads((world/'housing-probe-status-0.json').read_text())
        stage='mapped_claim';click(900,620,'领取免费宅地');wait(lambda:status().get('state')=='empty',15,'领取未保存房契');report['claimed']=status();screen.shot(out/'housing-claimed.png',max_wait=10,settle=.5);complete()
        stage='mapped_placement_build';click(230,280,'选择赤陶桥院楼');click(494,576,'旋转九十度');click(498,620,'向右平移一格');screen.shot(out/'housing-placement.png',max_wait=10,settle=.5)
        click(900,620,'提交免费建房');wait(lambda:status().get('state')in('built','blocked'),180,'建房尚未结束');state=status();report['construction']=state
        assert state.get('state')=='built'and state.get('house')=='chitao',state
        screen.shot(out/'housing-built.png',max_wait=10,settle=.5);complete()
        stage='native_house_view';screen.xdo('key','Escape');wait(lambda:screen.pointer_grabbed()is True,15,'界面未关闭')
        server.command(f'gamemode spectator {USER}');screen.key('F1');pose([x+site['width']+35,y+48,z-32],[x+site['width']/2,y+4,z+site['depth']/2]);time.sleep(25)
        stability=screen.shot(out/'housing-native-overview.png',max_wait=20,settle=1);report['native_view']={'file':'housing-native-overview.png','kind':'captured_for_review','stability':stability};complete();report['status']='passed'
    except BaseException as error:
        report['status']='failed';report['failed'].append({'case':stage,'error':repr(error),'traceback':traceback.format_exc()});raise
    finally:
        if client:report['client_cleanup']=terminate(client)
        if xvfb:report['display_cleanup']=terminate(xvfb)
        try:server.stop()
        finally:
            for log in logs:log.close()
            if 'clientdir'in locals()and(clientdir/'logs/latest.log').exists():shutil.copy2(clientdir/'logs/latest.log',out/'native-client.log')
            report['unrun']=[v for v in report['selected']if v not in report['completed']and v not in [f['case']for f in report['failed']]];write(out/'report.json',report)

if __name__=='__main__':main()
