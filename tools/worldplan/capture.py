#!/usr/bin/env python3
"""Real physical-client views of a naturally generated planned world; no DevBridge or fake render."""
from __future__ import annotations
import json
import math
import os
from pathlib import Path
import secrets
import shutil
import signal
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from worldplan.acceptance import Server,write
from combat_capture.session import CLIENT_OPTIONS,merge_options
from combat_capture.xgame import Game
OUT=ROOT/'reports/worldplan/visual'
USER='WorldplanView'

def stop(proc):
    if not proc:return None
    forced=False
    if proc.poll() is None:
        os.killpg(proc.pid,signal.SIGTERM)
        try:proc.wait(timeout=30)
        except subprocess.TimeoutExpired:
            forced=True;os.killpg(proc.pid,signal.SIGKILL);proc.wait(timeout=10)
    return {'pid':proc.pid,'returncode':proc.returncode,'terminated':proc.poll() is not None,'forced_kill':forced}

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    for tool in ['Xvfb','xdotool','import','convert']:
        if not shutil.which(tool):raise RuntimeError('Missing physical-client dependency: '+tool)
    display=next(':'+str(n) for n in range(91,120) if not Path(f'/tmp/.X11-unix/X{n}').exists())
    token=secrets.token_hex(4);clientdir=ROOT/'run-worldplan-client'/token;clientdir.mkdir(parents=True)
    merge_options(clientdir/'options.txt',{**CLIENT_OPTIONS,'lang':'zh_cn','renderDistance':'8','simulationDistance':'4','maxFps':'30'})
    result={'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
            'method':'Native Minecraft 1.21.1 client connected to dedicated planned server; software OpenGL; real screen grabs',
            'status':'running','screenshots':[],'owner_aesthetic_verdict':'pending'}
    xvfb=client=None
    xlog=(OUT/'xvfb.log').open('w');clog=(OUT/'client.log').open('w')
    try:
        xvfb=subprocess.Popen(['Xvfb',display,'-screen','0','960x540x24','+extension','GLX','+render','-noreset'],stdout=xlog,stderr=subprocess.STDOUT,start_new_session=True)
        deadline=time.monotonic()+20
        while not Path('/tmp/.X11-unix/X'+display[1:]).exists():
            if xvfb.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Xvfb startup failed')
            time.sleep(.2)
        with Server('worldplan_visual_'+token,'visual-server',view_distance=8) as server:
            info=server.query('info');result['info']=info
            city=info['city'];cx=city['x']+city['width']/2;cz=city['z']+city['depth']/2;by=city['baseY']
            server.query('pregen_city reverse')
            env=dict(os.environ,DISPLAY=display,LIBGL_ALWAYS_SOFTWARE='1',LP_NUM_THREADS='2')
            args=['./gradlew','runClient','-x','generateAllStructures','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx2g',
                  f'-Pcombat_smoke_game_dir={clientdir.relative_to(ROOT)}',f'-Pcombat_smoke_username={USER}',f'-Pcombat_smoke_server=127.0.0.1:{server.sport}']
            client=subprocess.Popen(args,cwd=ROOT,env=env,stdout=clog,stderr=subprocess.STDOUT,start_new_session=True)
            deadline=time.monotonic()+600
            while USER not in server.cmd('list'):
                if client.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Physical client failed to join; see client.log')
                time.sleep(3)
            server.cmd(f'gamemode spectator {USER}')
            game=Game(display,clientdir/'logs/latest.log')
            if not(game.wait_ingame(60,poll=1) or game.recover_grab()):raise RuntimeError('Client joined without entering the game view')
            time.sleep(4);game.key('F1')
            views=[('city_overview',(cx+80,by+75,cz+100),(cx,by+7,cz)),
                   ('city_street',(cx,by+6,city['z']+106),(cx,by+7,city['z']+15)),
                   ('city_reverse',(cx-70,by+48,cz-80),(cx,by+8,cz))]
            for name,eye,target in views:
                dx,dy,dz=[b-a for a,b in zip(eye,target)]
                yaw=math.degrees(math.atan2(-dx,dz));pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz)))
                server.cmd(f'tp {USER} '+' '.join(f'{v:.3f}' for v in (*eye,yaw,pitch)))
                time.sleep(12)
                path=OUT/(name+'.png');settled=game.shot(path,max_wait=12,settle=1)
                result['screenshots'].append({'file':path.name,'eye':eye,'target':target,'yaw':yaw,'pitch':pitch,'stability':settled,'bytes':path.stat().st_size})
            audit=server.query('audit_city');result['city_audit']=audit
            assert audit['complete'],audit
            result['status']='pass'
            try:server.cmd(f'kick {USER} Capture complete')
            except Exception:pass
            result['client_cleanup']=stop(client);client=None
    except BaseException as error:
        result['status']='fail';result['error']=repr(error);raise
    finally:
        if client:result['client_cleanup']=stop(client)
        result['display_cleanup']=stop(xvfb)
        latest=clientdir/'logs/latest.log'
        if latest.exists():shutil.copy2(latest,OUT/'native-client.log')
        xlog.close();clog.close();write(OUT/'capture.json',result)
        print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
if __name__=='__main__':main()
