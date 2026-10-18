#!/usr/bin/env python3
"""Scoped native-client atlas evidence. RCON sets fixtures; all UI actions use mapped physical input."""
from __future__ import annotations
import json,os,secrets,shutil,subprocess,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools'))
from worldplan.acceptance import Server,write
from worldplan.capture import stop
from combat_capture.session import CLIENT_OPTIONS,merge_options
from combat_capture.xgame import Game
OUT=ROOT/'reports/worldplan-atlas'; USER='AtlasViewer'

def wait_log(path,pattern,timeout=90):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        if path.exists() and pattern in path.read_text(errors='replace'):return
        time.sleep(.5)
    raise RuntimeError('Client did not log '+pattern)

def main():
    OUT.mkdir(parents=True,exist_ok=True);display=next(':'+str(n) for n in range(91,120) if not Path('/tmp/.X11-unix/X'+str(n)).exists())
    result={'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'selected':['planned_map','zoom_pan_locate','ordinary_no_plan'],'completed':[],'status':'running','input':'mapped H key and mouse clicks/drags; RCON fixture setup only','owner_aesthetic_verdict':'not_verified','fps':'not_verified','screenshots':[]}
    xvfb=client=None;xlog=(OUT/'xvfb.log').open('w')
    def click(game,x,y):game.xdo('mousemove',str(round(x)),str(round(y)));game.click();time.sleep(.7)
    def shot(game,name):
        path=OUT/(name+'.png');result['screenshots'].append({'file':path.name,'stability':game.shot(path,max_wait=10,settle=.5)})
    try:
        xvfb=subprocess.Popen(['Xvfb',display,'-screen','0','960x540x24','+extension','GLX','+render','-noreset'],stdout=xlog,stderr=subprocess.STDOUT,start_new_session=True)
        deadline=time.monotonic()+20
        while not Path('/tmp/.X11-unix/X'+display[1:]).exists():
            if time.monotonic()>deadline:raise RuntimeError('Xvfb startup timeout')
            time.sleep(.2)
        for planned in (True,False):
            kind='planned' if planned else 'ordinary';token=secrets.token_hex(4);directory=ROOT/'run-atlas-client'/token;directory.mkdir(parents=True)
            merge_options(directory/'options.txt',{**CLIENT_OPTIONS,'lang':'zh_cn','guiScale':'2','renderDistance':'3','simulationDistance':'3','maxFps':'30'})
            clientlog=directory/'logs/latest.log'
            with Server('atlas_'+kind+'_'+token,'atlas-'+kind,planned=planned,view_distance=3) as server:
                if planned: result['plan']=server.query('info')
                env=dict(os.environ,DISPLAY=display,LIBGL_ALWAYS_SOFTWARE='1',LP_NUM_THREADS='2')
                with (OUT/(kind+'-launch.log')).open('w') as log:
                    args=['./gradlew','runClient','-x','generateAllStructures','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx2g',f'-Pcombat_smoke_game_dir={directory.relative_to(ROOT)}',f'-Pcombat_smoke_username={USER}',f'-Pcombat_smoke_server=127.0.0.1:{server.sport}']
                    client=subprocess.Popen(args,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                    try:
                        deadline=time.monotonic()+420
                        while USER not in server.cmd('list'):
                            if client.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Client failed to join '+kind)
                            time.sleep(2)
                        server.cmd('gamemode spectator '+USER);game=Game(display,clientlog)
                        if not(game.wait_ingame(45,poll=.5) or game.recover_grab()):raise RuntimeError('Client has no game focus')
                        time.sleep(3);game.key('h');time.sleep(2)
                        # H is GUI 480x270. Left rail: 天下 (fourth row); dock: sixth button 舆图.
                        click(game,54,240);shot(game,kind+'-entry');click(game,672,462)
                        wait_log(clientlog,'ATLAS_OPEN')
                        if planned:
                            wait_log(clientlog,'ATLAS_READY plan='+result['plan']['plan_sha256']);time.sleep(2)
                            shot(game,'planned-overview');result['completed'].append('planned_map')
                            # Paper fits height: 1440x1024 at 262/1024 GUI scale, rendered at physical scale two.
                            s=262/1024*2;l=(960-1440*s)/2;t=8
                            click(game,l+1201*s,t+904*s)
                            game.xdo('mousemove','350','220');game.xdo('mousedown','1');game.xdo('mousemove','390','255');game.xdo('mouseup','1');time.sleep(1)
                            wait_log(clientlog,'ATLAS_INPUT action=pan');shot(game,'planned-zoom-pan')
                            click(game,l+1160*s,t+789*s);wait_log(clientlog,'ATLAS_INPUT action=locate');shot(game,'planned-locate')
                            click(game,l+1160*s,t+843*s);wait_log(clientlog,'ATLAS_INPUT action=overview');result['completed'].append('zoom_pan_locate')
                        else:
                            time.sleep(4);shot(game,'ordinary-unavailable')
                            text=clientlog.read_text(errors='replace');assert 'ATLAS_READY' not in text
                            result['completed'].append('ordinary_no_plan')
                        game.xdo('key','Escape');time.sleep(.7);shot(game,kind+'-return')
                    finally:
                        result[kind+'_client_cleanup']=stop(client);client=None
                        if clientlog.exists():shutil.copy2(clientlog,OUT/(kind+'-client.log'))
        result['status']='pass'
    except BaseException as error:result['status']='fail';result['error']=repr(error);raise
    finally:
        if client:result['client_cleanup']=stop(client)
        result['display_cleanup']=stop(xvfb);xlog.close();result['unrun']=sorted(set(result['selected'])-set(result['completed']));write(OUT/'capture.json',result)
if __name__=='__main__':main()
