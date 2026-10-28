#!/usr/bin/env python3
"""固定源 Colab 打坐动作采集：映射输入、真实客户端、服务器数值读回。"""
from __future__ import annotations
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
import re
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
CASES=('lifecycle','armor','late_observer')


def run(out,commit,cases):
    from tools.combat_capture import procs,scene,session as cs
    from tools.combat_capture.capture import ffmpeg_record_argv
    from tools.combat_capture.xgame import Game
    from tools.realm_stats_evidence import Evidence
    from tools.golden_core_evidence import parse_profile
    from tools.cultivation_demo_evidence import status_json
    from tools.immortal_equipment_evidence import verify_launch_artifacts

    class MotionEvidence(Evidence):
        def __init__(self):
            if out.exists() and any(out.iterdir()):raise ValueError('不能覆盖旧采集')
            super().__init__(out)
            self.report.update(kind='meditation_motion',source_commit=commit,selected=list(cases),completed=[],unrun=list(cases),
                inputs=[],screenshots=[],videos=[],owner_appearance='not_verified',owner_fps='not_verified',
                fixture='隔离平地、开灵/入门/物品和观察位置由管理员准备；开始/停止/移动全部使用映射按键')
        def must(self,name,passed,detail):
            if not self.check(name,passed,detail):raise AssertionError(name)
        def poll(self,fn,predicate,label,timeout=25):
            deadline=time.monotonic()+timeout;last=None
            while time.monotonic()<deadline:
                last=fn()
                if predicate(last):return last
                time.sleep(.2)
            raise TimeoutError(f'{label}: {str(last)[-600:]}')
        def key(self,key,game=None):
            game=game or self.s.game
            game.require_ingame('修炼动作映射输入');game.key(key)
            self.report['inputs'].append({'key':key,'method':'mapped X11 keyboard','display':game.display})
            self.save()
        def move(self):
            def position():
                reply=self.rc(f'data get entity {self.s.username} Pos')
                match=re.search(r'\[([^\[\]]+)\]',reply)
                if not match:raise ValueError('缺少服务器位置读回：'+reply)
                return tuple(float(v.strip().rstrip('dDfF')) for v in match.group(1).split(','))
            before=position();g=self.s.game;g.require_ingame('按住W移动')
            g.xdo('keydown','w')
            try:time.sleep(.7)
            finally:g.xdo('keyup','w')
            self.report['inputs'].append({'key':'w','method':'mapped X11 keydown/keyup','hold_seconds':.7,'display':g.display})
            after=self.poll(position,lambda p:sum((a-b)**2 for a,b in zip(p,before))>.01,'映射W实际位移')
            self.must('服务器位置证明已移动',sum((a-b)**2 for a,b in zip(after,before))>.01,{'before':before,'after':after})
        def profile(self):return parse_profile(self.rc(f'myvillage cultivation info {self.s.username}'))
        def start(self):
            self.report['launch_files_matching_jar']=verify_launch_artifacts(ROOT)
            jar=next((ROOT/'build/libs').glob('*.jar'))
            self.report['jar']={'file':jar.name,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest()}
            self.report['archive']=json.loads((ROOT/'meditation-source.json').read_text())
            self.must('source_identity',self.report['archive']['commit']==commit,self.report['archive'])
            state=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),language='zh_cn',settle=3,
                gradle_jvmargs='-Xmx2g',prebuilt_resources=True,lock=str(procs.default_lock_path(ROOT))))
            self.s=cs.Session.attach(ui_check=True);self.sessions+=1
            self.report['phases'].append({'phase':'setup','timings':state.get('timings',{})})
            u=self.s.username
            self.opening_complete(u)
            for command in (*scene.world_rules(),*scene.player_setup(u,'minecraft:air'),'myvillage world pause',
                    'fill -12 -61 -12 12 -61 12 minecraft:smooth_stone',
                    f'tp {u} .5 -60 .5 0 0',f'myvillage cultivation awaken {u}',f'myvillage cultivation initiate {u}',
                    f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_6'):
                self.rc(command)
            self.poll(self.profile,lambda p:p['root']!='none','开灵读回')
            time.sleep(2)
            self.key('F5');self.key('F5');self.key('F1')
        def opening_complete(self,name):
            self.poll(lambda:status_json(self.rc('myvillage qianzhen realm_status '+name),'QIANZHEN_REALM_STATUS '),
                      lambda s:not s['building'] and not s['waiting'],'首次秘境传送完成',150)
        def shot(self,name,game=None):
            game=game or self.s.game
            path=self.out/(name+'.png');game.shot(path,max_wait=.3)
            self.report['screenshots'].append(path.name);self.save()
        def active(self,key='v',require_model=True):
            log=cs.CLIENT_DIR/'logs/latest.log';mark=len(log.read_text())
            before=self.profile()['progress'];self.key(key)
            self.poll(self.profile,lambda p:p['progress']>before,'修炼增长')
            self.poll(lambda:log.read_text()[mark:],lambda s:'MEDITATION_POSE ' in s and 'active=true' in s,'本次服务器姿态快照')
            if require_model:self.poll(lambda:log.read_text()[mark:],lambda s:'MEDITATION_POSE_RENDERED' in s,'本次人物姿态实际应用')
        def stopped(self):
            time.sleep(.7);before=self.profile()['progress'];time.sleep(1)
            self.must('停止后修为不增长',self.profile()['progress']==before,{'progress':before})
        def record(self,name,seconds,action):
            path=self.out/(name+'.mp4')
            p=subprocess.Popen(ffmpeg_record_argv(self.s.state['display'],path,seconds,fps=24),stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
            try:
                time.sleep(.4);action();_,error=p.communicate(timeout=seconds+20)
                self.must(name+'_录制',p.returncode==0 and path.stat().st_size>1000,error.decode()[-400:])
            finally:
                if p.poll() is None:p.terminate();p.wait(timeout=10)
            self.report['videos'].append(path.name);self.save()
        def lifecycle(self):
            self.shot('站立参考')
            def sequence():
                self.key('v');time.sleep(3);self.shot('盘坐_裸装');time.sleep(2);self.key('x');time.sleep(2.5);self.shot('收功站立')
            before=self.profile()['progress'];self.record('入座_呼吸_收功',10,sequence)
            self.must('映射V启动真实结算',self.profile()['progress']>before,self.profile());self.stopped()
            self.rc(f'give {self.s.username} myvillage:low_grade_spirit_stone 64')
            self.active('b');self.shot('灵石修炼')
            self.move();self.stopped();self.shot('移动打断')
            self.active();self.rc(f'damage {self.s.username} 1 minecraft:generic');self.stopped();self.shot('受击打断')
            time.sleep(5.2);self.active();self.key('x');time.sleep(.4);self.move();self.stopped();self.shot('收功中移动')
            # Re-enter in first person: the same server session drives the camera/hand presentation.
            self.key('F5');self.active(require_model=False);self.shot('第一人称调息');self.key('x');time.sleep(2.2);self.key('F5');self.key('F5')
        def armor(self):
            self.active()
            for set_id in ('minecraft:iron','myvillage:qi_moqing'):
                for slot,suffix in (('head','helmet'),('chest','chestplate'),('legs','leggings'),('feet','boots')):
                    self.rc(f'item replace entity {self.s.username} armor.{slot} with {set_id}_{suffix}')
                time.sleep(1);self.shot('盘坐_'+set_id.replace(':','_'))
            self.key('x');time.sleep(2.2);self.shot('仙衣收功')
            for slot in ('head','chest','legs','feet'):self.rc(f'item replace entity {self.s.username} armor.{slot} with minecraft:air')
        @contextmanager
        def observer(self):
            display=f':{procs.free_display()}';directory=ROOT/'run-combat-capture/meditation-observer';directory.mkdir(parents=True,exist_ok=True)
            cs.merge_options(directory/'options.txt',{**cs.CLIENT_OPTIONS,'lang':'zh_cn','maxFps':'30','renderDistance':'4'})
            handles=[];owned=[]
            try:
                h=open(self.out/'observer-xvfb.log','w');handles.append(h)
                p=subprocess.Popen(['Xvfb',display,'-screen','0','960x540x24','+extension','GLX','-nolisten','tcp','-noreset'],stdout=h,stderr=subprocess.STDOUT);owned.append(p.pid)
                self.poll(lambda:cs.x_display_ready(display),bool,'旁观者显示',15)
                h=open(self.out/'observer-process.log','w');handles.append(h)
                args=cs.gradle_argv('runClient','-Xmx1g',[f'-Pcombat_smoke_server=127.0.0.1:{self.s.state["server_port"]}',
                        f'-Pcombat_smoke_game_dir={directory}','-Pcombat_smoke_username=CaptureWatch','-x','generateAllStructures'])
                p=subprocess.Popen(args,cwd=ROOT,env=dict(os.environ,DISPLAY=display,JAVA_TOOL_OPTIONS='-Xmx1g'),stdout=h,stderr=subprocess.STDOUT);owned.append(p.pid)
                self.poll(lambda:self.rc('list'),lambda s:'CaptureWatch' in s,'旁观者连接',180)
                game=Game(display,directory/'logs/latest.log',cs.SCREEN,log=print)
                if not game.wait_ingame(45,poll=1) and not game.recover_grab():raise RuntimeError('旁观者没有进入世界')
                self.opening_complete('CaptureWatch')
                self.rc('gamemode spectator CaptureWatch')
                self.rc(f'execute as {self.s.username} at @s run tp CaptureWatch 3 -58.7 3 facing .5 -59 .5')
                self.poll(lambda:self.rc(f'execute at {self.s.username} if entity @a[name=CaptureWatch,distance=..10] run time query gametime'),
                          lambda s:'The time is' in s,'旁观者与演员同维度且在可见距离')
                self.poll(game.pointer_grabbed,lambda v:v is True,'旁观者维度加载完成',90)
                time.sleep(2);self.key('F1',game)
                yield game,directory
            finally:
                targets=[]
                for pid in owned:
                    targets.extend((p,procs.start_time(p),'observer descendant')for p in procs.descendants(pid))
                    targets.append((pid,procs.start_time(pid),'observer'))
                procs.kill_pids(targets,grace=8,log=print)
                for h in handles:h.close()
                if (directory/'logs/latest.log').exists():shutil.copy2(directory/'logs/latest.log',self.out/'observer-client.log')
        def late_observer(self):
            # Start before the observer even exists: StartTracking must carry the original start epoch.
            self.rc(f'tp {self.s.username} .5 -60 .5 0 0');time.sleep(1);self.active()
            with self.observer() as (game,directory):
                log=directory/'logs/latest.log'
                text=self.poll(log.read_text,lambda s:'MEDITATION_POSE_RENDERED' in s,'迟加入观察者看见打坐')
                self.must('迟加入姿态快照', 'active=true' in text and 'MEDITATION_POSE_RENDERED' in text,'observer-client.log')
                self.shot('旁观者_正侧面',game)
                self.rc('tp CaptureWatch .5 -59 -3.5 facing .5 -59 .5');time.sleep(.6);self.shot('旁观者_背面',game)
                mark=len(log.read_text());self.key('x')
                self.poll(lambda:log.read_text()[mark:],lambda s:'active=false' in s and 'graceful=true' in s,'旁观者同步收功')
                time.sleep(2.5);self.shot('旁观者_恢复站立',game)
                # Cold second-client startup can fill the current stage. Prepare a fresh growth
                # budget after stopping, without weakening the real-settlement assertion.
                self.rc(f'myvillage cultivation setprogress {self.s.username} 0')
                self.must('旁观者中断场景未满修为',self.profile()['progress']==0,'administrator fixture reset while idle')
                self.active();mark=len(log.read_text());self.move()
                self.poll(lambda:log.read_text()[mark:],lambda s:'active=false' in s and 'graceful=false' in s,'旁观者同步移动中断')
                self.stopped()
                self.report['multiplayer']='developer_two_clients_observed; owner_not_verified'
        def execute(self):
            self.start()
            for case in cases:
                getattr(self,case)();self.report['completed'].append(case);self.report['unrun'].remove(case);self.save()
    evidence=MotionEvidence()
    try:evidence.execute()
    except BaseException as error:
        evidence.report['failures'].append(f'{type(error).__name__}: {error}');evidence.save();raise
    finally:
        if evidence.s is not None:evidence.stop()
        evidence.save()
    return evidence.report


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--plan',action='store_true');p.add_argument('--source-commit');
    p.add_argument('--cases',default=','.join(CASES));p.add_argument('--out',type=Path,default=ROOT/'out/preview/meditation-motion-runtime')
    args=p.parse_args();cases=args.cases.split(',')
    if len(cases)!=len(set(cases)) or not set(cases)<=set(CASES):p.error('未知或重复场景')
    if args.plan:print(json.dumps({'selected':cases,'completed':[],'unrun':cases},ensure_ascii=False));return
    if not args.source_commit or not Path('/content').exists():p.error('需要固定提交与 Colab 环境')
    run(args.out,args.source_commit,cases)
if __name__=='__main__':main()
