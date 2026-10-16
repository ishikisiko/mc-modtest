"""One bounded second-client observation inside an already locked capture session."""
from __future__ import annotations
import json,os,subprocess,time
from pathlib import Path
from . import procs,scene
from .capture import ffmpeg_record_argv
from .session import CLIENT_OPTIONS,REPO,SCREEN,gradle_argv,merge_options,x_display_ready,LOG_DIR
from .sword_qi import DEFAULT_OUT,WEAPON,TARGET,parse_events
from .xgame import Game


def observe(session,out=DEFAULT_OUT/'observer',log=print,timeout=180,tick_rate=10,hold_seconds=1.2):
    out=Path(out);out.mkdir(parents=True,exist_ok=True)
    display=f':{procs.free_display()}'
    game_dir=REPO/'run-combat-capture/observer';game_dir.mkdir(parents=True,exist_ok=True)
    merge_options(game_dir/'options.txt',{**CLIENT_OPTIONS,'lang':'en_us','renderDistance':'4','simulationDistance':'3','maxFps':'30'})
    env=dict(os.environ,DISPLAY=display,JAVA_TOOL_OPTIONS='-Xmx768m')
    handles=[];owned=[];start=time.monotonic();report={'method':'second real Minecraft client on separate Xvfb, same dedicated server; actor uses mapped right mouse','owner_verdict':'not_verified','sound':'not_verified','observer_username':'CaptureWatch','minecraft_heap_cap':'768 MiB via JAVA_TOOL_OPTIONS','observer_max_fps':30,'server_tick_rate':tick_rate,'hold_seconds':hold_seconds}
    try:
        procs.set_child_subreaper()
        xlog=open(out/'xvfb.log','w');handles.append(xlog)
        xvfb=subprocess.Popen(['Xvfb',display,'-screen','0','960x540x24','+extension','GLX','-nolisten','tcp','-noreset'],stdout=xlog,stderr=subprocess.STDOUT)
        owned.append(xvfb.pid)
        deadline=time.monotonic()+15
        while not x_display_ready(display):
            if time.monotonic()>deadline:raise RuntimeError('observer Xvfb did not start')
            time.sleep(.2)
        clog=open(out/'client-process.log','w');handles.append(clog)
        args=gradle_argv('runClient','-Xmx512m',[f'-Pcombat_smoke_server=127.0.0.1:{session.state["server_port"]}',f'-Pcombat_smoke_game_dir={game_dir}', '-Pcombat_smoke_username=CaptureWatch'])
        client=subprocess.Popen(args,cwd=REPO,env=env,stdout=clog,stderr=subprocess.STDOUT);owned.append(client.pid)
        deadline=time.monotonic()+timeout
        while time.monotonic()<deadline:
            if client.poll() is not None:raise RuntimeError(f'observer client exited {client.returncode}')
            if 'CaptureWatch' in session.run('list',warn=False)[0]:break
            time.sleep(2)
        else:raise RuntimeError('observer join deadline exceeded')
        report['joined_s']=round(time.monotonic()-start,3)
        game=Game(display,game_dir/'logs/latest.log',SCREEN,log=log)
        if not game.wait_ingame(30,poll=1):
            if not game.recover_grab():raise RuntimeError('observer is not in game')
        time.sleep(3)
        report['ingame_s']=round(time.monotonic()-start,3)
        u=session.username
        session.run(f'tick rate {tick_rate}','kill @e[tag=sword_qi_target]',f'tp {u} .5 -60 .5 0 0',
                    f'item replace entity {u} hotbar.0 with {WEAPON}',f'myvillage cultivation qi fill {u}',
                    'gamemode spectator CaptureWatch','tp CaptureWatch 5.5 -58 3.5 facing entity '+u+' eyes',
                    'summon minecraft:iron_golem .5 -60 6.5 {NoAI:1b,PersistenceRequired:1b,Tags:["sword_qi_target"]}',warn=False)
        session.game.key('1');scene.ensure_cultivation(session);time.sleep(1)
        before=scene.parse_float_data(session.run(f'data get entity {TARGET} Health',warn=False)[0]);mark=(LOG_DIR/'server.log').stat().st_size
        game.shot(out/'observer_before.png',max_wait=.3)
        video=out/'observer_cast.mp4'
        recorder=subprocess.Popen(ffmpeg_record_argv(display,video,9,fps=30),stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
        try:
            time.sleep(.5);session.game.require_ingame('observer trial actor charge');session.game.xdo('mousedown','3')
            time.sleep(min(.5,hold_seconds));game.shot(out/'observer_charging.png',max_wait=.3)
            time.sleep(max(0,hold_seconds-.5));session.game.xdo('mouseup','3')
            time.sleep(2);rc=recorder.wait(timeout=20)
            if rc:raise RuntimeError(f'observer recording failed {rc}')
        finally:
            session.game.xdo('mouseup','3')
            if recorder.poll() is None:recorder.terminate();recorder.wait(timeout=5)
        after=scene.parse_float_data(session.run(f'data get entity {TARGET} Health',warn=False)[0])
        with (LOG_DIR/'server.log').open('rb') as stream:stream.seek(mark);lines=[l for l in stream.read().decode(errors='replace').splitlines() if 'SWORD_QI ' in l]
        (out/'server_events.log').write_text('\n'.join(lines)+'\n');events=parse_events(lines)
        report.update(target_health_before=before,target_health_after=after,events=events,video=video.name,
                      both_players=session.run('list',warn=False)[0],
                      server_hit_observed=before is not None and after is not None and after<before and any(e.get('result')=='hit' for e in events),
                      observer_visual_verdict='not_verified_until_video_inspection')
    except BaseException as error:
        report['error']=f'{type(error).__name__}: {error}';raise
    finally:
        try:session.run('tick rate 20',warn=False)
        except Exception:pass
        targets=[]
        for pid in owned:
            targets.extend((p,procs.start_time(p),'observer descendant') for p in procs.descendants(pid))
            targets.append((pid,procs.start_time(pid),'observer process'))
        procs.kill_pids(targets,grace=8,log=log)
        for h in handles:h.close()
        report['wall_s']=round(time.monotonic()-start,3)
        (out/'manifest.json').write_text(json.dumps(report,indent=2)+'\n')
    return report
