"""真实客户端的千机阁画面、映射步行和任务榜交互；RCON仅用于场景准备与读回。"""
from __future__ import annotations
import argparse,json,math,os,re,secrets,shutil,subprocess,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools'))
from roads_smoke.run import verify_candidate
from qianji.server import Server
from combat_capture.xgame import Game
from combat_capture.session import CLIENT_OPTIONS,merge_options
from roadweaver_probe.run import terminate,write
USER='QianjiReview'

def placed_point(office,p):
    """把相机或玩家的连续坐标旋转到城市中，保留所在方块内的偏移。"""
    x,y,z=p;w,h,d=office['size'];turn=office['rotation_quarters'];x0,z0,_,_=office['bounds']
    xx,zz=[(x,z),(d-z,x),(w-x,d-z),(z,w-x)][turn]
    return [x0+xx,office['base_y']+y,z0+zz]


def main():
    p=argparse.ArgumentParser();p.add_argument('--world',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--source',required=True);a=p.parse_args()
    a.out=a.out.resolve();a.out.mkdir(parents=True,exist_ok=False);world=json.loads(a.world.read_text());game=Path(world['directory'])
    assert game.resolve().parent==(ROOT/'run-fixed-progression').resolve() and world['kind']=='fresh isolated development world'
    server=Server(a.out,game,world['seed']);server.view_distance=24
    result=dict(source=a.source,selected=['candidate_identity','native_architecture_views','mapped_ground_walk','mapped_task_board_interaction'],completed=[],failed=[],owner_visual_acceptance='not_verified',fps_acceptance='not_verified')
    stage=result['selected'][0];client=xvfb=None;logs=[]
    clientdir=ROOT/'run-qianji-client'/secrets.token_hex(5);clientdir.mkdir(parents=True)
    display=next(':'+str(i) for i in range(91,150) if not Path(f'/tmp/.X11-unix/X{i}').exists())
    def complete():result['completed'].append(stage);write(a.out/'capture.json',result)
    def query(command):return json.loads(server.command('myvillage worldplan '+command).split('WORLDPLAN ',1)[1])
    def pose(eye,target):
        dx,dy,dz=[b-c for c,b in zip(eye,target)];yaw=math.degrees(math.atan2(-dx,dz));pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz)))
        server.command(f'tp {USER} '+ ' '.join(f'{v:.3f}' for v in [eye[0],eye[1]-1.62,eye[2],yaw,pitch]))
    try:
        result['identity']=verify_candidate(a.jar.resolve());complete()
        for tool in ['Xvfb','xdotool','import','convert']:
            if not shutil.which(tool):raise RuntimeError('缺少真实客户端工具：'+tool)
        stage=result['selected'][1];server.start();query('export');plan=json.loads((server.data/'worldplan.json').read_text());city=next(c for c in plan['cities'] if c['region_id']=='zhongzhou')
        data=json.loads((ROOT/'build/resources/main/data/myvillage/capitals/v4/capital_zhongzhou/city.json').read_text());office=data['qianji'];x0,z0,x1,z1=office['bounds'];base=city['origin_y']+office['base_y'];width=x1-x0;depth=z1-z0
        server.command('gamerule spectatorsGenerateChunks true');server.command('gamerule mobGriefing false')
        wx0,wz0,wx1,wz1=city['x']+x0,city['z']+z0,city['x']+x1-1,city['z']+z1-1
        chunks={(cx*16+8,cz*16+8) for cx in range(wx0//16,wx1//16+1) for cz in range(wz0//16,wz1//16+1)}
        assert len(chunks)<=256,'总舵采集的预加载范围超出原版强加载上限'
        server.command(f'forceload add {wx0} {wz0} {wx1} {wz1}');deadline=time.monotonic()+600
        pending=set(chunks)
        while pending:
            pending={p for p in pending if not server.test(f'if loaded {p[0]} 300 {p[1]}')}
            if pending and time.monotonic()>deadline:raise TimeoutError('总舵区块未完整加载：'+str(len(pending)))
            if pending:time.sleep(1)
        result['preloaded_headquarters_chunks']=len(chunks);write(a.out/'capture.json',result)
        cfg=json.loads((ROOT/'blueprints/qianji/zhongzhou_infill.json').read_text())
        def point(x,y,z):
            xx,yy,zz=placed_point(office,[x,y,z]);return [city['x']+xx,city['origin_y']+yy,city['z']+zz]
        def authored(p,part):
            dx,dz=cfg['parts'][part]['shift'];dy=office['infill']['construction']['building_floors'][part]-office['base_y']-cfg['parts'][part]['pads'][0][4]-1
            return point(p[0]+dx,p[1]+dy,p[2]+dz)
        log=(a.out/'xvfb.log').open('w');logs.append(log);xvfb=subprocess.Popen(['Xvfb',display,'-screen','0','1280x720x24','+extension','GLX','+render','-noreset'],stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        deadline=time.monotonic()+30
        while not Path('/tmp/.X11-unix/X'+display[1:]).exists():
            if xvfb.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Xvfb 未启动')
            time.sleep(.2)
        merge_options(clientdir/'options.txt',{**CLIENT_OPTIONS,'lang':'zh_cn','renderDistance':'24','simulationDistance':'3','maxFps':'20','overrideWidth':'1280','overrideHeight':'720','guiScale':'2','fov':'-0.15'})
        env=dict(os.environ,DISPLAY=display,LIBGL_ALWAYS_SOFTWARE='1',LP_NUM_THREADS='2')
        log=(a.out/'client-launch.log').open('w');logs.append(log)
        args=['./gradlew','-I','tools/qianji/client.init.gradle','runClient','-x','compileJava','-x','processResources','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx3g',f'-Pcombat_smoke_game_dir={clientdir.relative_to(ROOT)}',f'-Pcombat_smoke_username={USER}',f'-Pcombat_smoke_server=127.0.0.1:{server.sport}']
        client=subprocess.Popen(args,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        deadline=time.monotonic()+900
        while USER not in server.command('list'):
            if client.poll() is not None or time.monotonic()>deadline:raise RuntimeError('真实客户端未加入服务器')
            time.sleep(3)
        screen=Game(display,clientdir/'logs/latest.log',size=(1280,720))
        if not(screen.wait_ingame(90,poll=1) or screen.recover_grab()):raise RuntimeError('客户端未进入可控制状态')
        window=screen.xdo('getwindowfocus').strip();screen.xdo('windowsize',window,'1280','720');screen.xdo('windowmove',window,'0','0');time.sleep(3)
        server.command(f'gamemode spectator {USER}');screen.key('F1')
        views=[(v['id'],authored(v['eye'],v['part'])if 'part'in v else point(*v['eye']),authored(v['target'],v['part'])if 'part'in v else point(*v['target']))for v in cfg['review_views']]
        result['screenshots']=[]
        for name,eye,target in views:
            pose(eye,target);time.sleep(40 if name=='headquarters-overview' else 18);file=a.out/(name+'.png');stability=screen.shot(file,max_wait=25,settle=1)
            result['screenshots'].append(dict(file=file.name,eye=eye,target=target,stability=stability,kind='captured_for_review'))
        pose(authored([180,130,90],'core'),authored([84,45,153],'core'));time.sleep(20)
        for name,phase in [('quiet',0),('array',20),('mirage',50)]:
            server.command('myvillage qianji phase '+str(phase));time.sleep(2);file=a.out/('atmosphere-'+name+'.png');screen.shot(file,max_wait=5,settle=.2)
            result['screenshots'].append(dict(file=file.name,phase_start=phase,kind='captured_for_review'))
        pose(authored([84,66,132],'core'),authored([84,110,183],'core'));server.command('myvillage qianji phase 50');time.sleep(3)
        screen.shot(a.out/'atmosphere-upper-floor.png',max_wait=5,settle=.2)
        result['screenshots'].append(dict(file='atmosphere-upper-floor.png',phase_start=50,kind='captured_for_review',scope='高层仰视，地面阵枢位于视野外'))
        pose(authored([180,130,90],'core'),authored([84,45,153],'core'));time.sleep(2)
        server.command('myvillage qianji phase 9')
        video=a.out/'atmosphere-array.mp4'
        recorder=subprocess.run(['ffmpeg','-y','-f','x11grab','-framerate','12','-video_size','1280x720','-i',display,'-t','15','-c:v','libx264','-preset','veryfast','-crf','22','-pix_fmt','yuv420p',str(video)],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,timeout=40)
        if recorder.returncode:raise RuntimeError(recorder.stderr.decode(errors='replace')[-2000:])
        result['video']=dict(file=video.name,kind='captured_for_review',phase='array',software_rendering=True)
        complete()
        stage=result['selected'][2];server.command(f'gamemode survival {USER}');eye=authored([24.5,2.6825,37.5],'office');pose(eye,authored([34.5,2.6825,37.5],'office'));time.sleep(8)
        before=server.command(f'data get entity {USER} Pos');screen.require_ingame('办事廊室内步行');screen.xdo('keydown','w');time.sleep(2);screen.xdo('keyup','w');time.sleep(2);after=server.command(f'data get entity {USER} Pos')
        def position(value):return [float(v) for v in re.findall(r'-?\d+\.\d+',value)[-3:]]
        aa,bb=position(before),position(after);distance=math.dist(aa,bb);assert distance>1,(before,after)
        result['mapped_walk']=dict(input='key.keyboard.w',held_seconds=2,before=before,after=after,distance=distance,scope='办事廊室内通道短距离步行');complete()
        stage=result['selected'][3];server.command(f'clear {USER}');board=office['board']['position'];reader=office['board']['reader'];eye=[city['x']+reader[0]+.5,city['origin_y']+reader[1]+1.62,city['z']+reader[2]+.5];target=[city['x']+board[0]+.5,city['origin_y']+board[1]+.5,city['z']+board[2]+.5];pose(eye,target);time.sleep(5)
        screen.require_ingame('任务榜右键交互');screen.xdo('click','3');deadline=time.monotonic()+15
        while screen.pointer_grabbed() is not False and time.monotonic()<deadline:time.sleep(.3)
        assert screen.pointer_grabbed() is False,'映射右键未打开任务界面'
        raw,menu_stability=screen.stable_grab(max_wait=15,settle=1);screen.save_png(raw,a.out/'qianji-task-menu.png');result['task_board_input']=dict(input='key.mouse.right',board=[city['x']+board[0],city['origin_y']+board[1],city['z']+board[2]],menu_opened=True,screenshot='qianji-task-menu.png',stability=menu_stability)
        screen.xdo('key','Escape');assert screen.wait_ingame(15);complete()
    except BaseException as error:
        result['failed'].append(dict(case=stage,error=repr(error)));raise
    finally:
        if client:result['client_cleanup']=terminate(client)
        if xvfb:result['display_cleanup']=terminate(xvfb)
        if server.process and server.process.poll() is None:
            try:server.command('forceload remove all')
            except Exception:pass
        server.stop()
        for f in logs:f.close()
        native=clientdir/'logs/latest.log'
        if native.exists():shutil.copy2(native,a.out/'native-client.log')
        result['unrun']=[v for v in result['selected'] if v not in result['completed'] and v not in [f['case'] for f in result['failed']]];write(a.out/'capture.json',result)
if __name__=='__main__':main()
