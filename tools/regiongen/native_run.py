"""Colab-only fixture placement and real X11 input acceptance for a river sample.

No player teleport, rotation command, forced jump, or block edits during the tour.
The separate aerial fixture precedes recording. Every run owns a fresh checkout
and world; failures retain their own report and normally saved world.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import time
import traceback

from tools.combat_capture import session as cs, procs, capture
from tools.combat_capture.rcon import connect


def write(path,value):
    temp=path.with_suffix('.tmp');temp.write_text(json.dumps(value,ensure_ascii=False,indent=2));temp.replace(path)


def vector(text,count):
    body=text[text.rfind('[')+1:text.rfind(']')]
    values=[float(v.strip().rstrip('dfb')) for v in body.split(',')]
    if len(values)!=count or not all(math.isfinite(v) for v in values):raise ValueError(f'Invalid vector: {text}')
    return values


def placement_confirmed(response, template_id, position):
    expected='Loaded template \"'+template_id+'\" at '+', '.join(map(str,position))
    return response.strip()==expected


def door_point(node,doors):
    p=[node[0]+.5,node[1],node[2]+.5]
    for door in doors:
        d=door['pos'];state=door['state'];f=re.search(r'facing=(\w+)',state).group(1)
        axis,side={'south':(0,1),'north':(0,-1),'west':(2,1),'east':(2,-1)}[f]
        if 'hinge=right' in state:side=-side
        if node==d or (node[axis]==d[axis] and sum(abs(node[i]-d[i]) for i in (0,2))==1):
            p[axis]-=side*3/32;break
    return p


class Run:
    def __init__(self,pack,out):
        self.pack=pack;self.out=out;out.mkdir(parents=True,exist_ok=False)
        self.m=json.loads((pack/'manifest.json').read_text());self.s=None;self.rec=None;self.r=None
        self.report={'status':'running','source':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
            'plan_sha256':self.m['plan_sha256'],'geometry_sha256':self.m['geometry_sha256'],
            'selected':self.m['selected'],'completed':[],'attempted':[],'failed':[],'unrun':list(self.m['selected']),
            'tour_completed':[],'tiles_completed':0,'started':time.time(),'not_verified':['player_feel','fps','multiplayer','natural_world_integration']}
        self.flush_attempted=False;self.tickets=None;self.opened=[];self.window=None
        self.events=(out/'events.jsonl').open('w',buffering=1)
        self.save()
    def save(self):write(self.out/'report.json',self.report)
    def event(self,kind,**data):self.events.write(json.dumps({'time':time.time(),'kind':kind,**data},ensure_ascii=False)+'\n')
    def begin(self,name):
        self.report['attempted'].append(name);self.report['unrun'].remove(name);self.save()
    def done(self,name):self.report['completed'].append(name);self.save()
    def cmd(self,command):
        value=self.r.cmd(command);self.event('rcon',command=command,response=value);return value
    def position(self):return vector(self.cmd('data get entity '+self.s.username+' Pos'),3)
    def rotation(self):return vector(self.cmd('data get entity '+self.s.username+' Rotation'),2)
    def gate(self):
        st=self.s.state;root=st['procs']['client'];g=self.s.game
        if not procs.is_alive(root['pid'],root['start']):raise RuntimeError('Owned client process exited')
        owned={root['pid'],*procs.descendants(root['pid'])}
        if self.window is None:
            wins=g.xdo('search','--onlyvisible','--name','Minecraft').split()
            choices=[w for w in wins if int(g.xdo('getwindowpid',w).strip()) in owned]
            if len(choices)!=1:raise RuntimeError(f'Expected one owned Minecraft window, got {choices}')
            self.window=choices[0];g.xdo('windowsize',self.window,'1280','720');g.xdo('windowmove',self.window,'0','0');time.sleep(.8)
        if int(g.xdo('getwindowpid',self.window).strip()) not in owned:raise RuntimeError('Window ownership changed')
        if g.xdo('getwindowfocus').strip()!=self.window:g.xdo('windowfocus','--sync',self.window);time.sleep(.3)
        if g.pointer_grabbed() is not True:raise RuntimeError('Owned client lacks in-game pointer grab')
        self.s.assert_alive()
        if self.rec and self.rec.poll() is not None:raise RuntimeError('Video exited before mapped tour completed')
    def look(self,yaw,pitch=0):
        self.gate()
        for _ in range(32):
            r=self.rotation();dy=(yaw-r[0]+180)%360-180;dp=pitch-r[1]
            if abs(dy)<.3 and abs(dp)<.3:
                time.sleep(.2);confirm=self.rotation()
                if abs((yaw-confirm[0]+180)%360-180)<.3 and abs(pitch-confirm[1])<.3:return
                continue
            dx=max(-400,min(400,round(dy/.15)));dz=max(-400,min(400,round(dp/.15)))
            expected=(r[0]+dx*.15,r[1]+dz*.15)
            self.s.game.xdo('mousemove_relative','--',str(dx),str(dz))
            # A delayed readback is not a request to enqueue the same mouse delta again.
            deadline=time.monotonic()+3
            while time.monotonic()<deadline:
                time.sleep(.12);actual=self.rotation()
                if abs((expected[0]-actual[0]+180)%360-180)<.3 and abs(expected[1]-actual[1])<.3:break
            else:raise TimeoutError(f'Mouse input not acknowledged: {expected}, {actual}')
        raise TimeoutError(f'Mouse rotation failed: target={yaw,pitch}, actual={r}')
    def wait_crouch(self,crouched):
        height=1.27 if crouched else 1.62
        command=f'execute as {self.s.username} at @s anchored eyes positioned ^ ^ ^ if entity @s[distance={height-.005:.3f}..{height+.005:.3f}]'
        deadline=time.monotonic()+3
        while time.monotonic()<deadline:
            if 'passed' in self.cmd(command).lower():return
            time.sleep(.12)
        raise TimeoutError('Mapped crouch state was not acknowledged')
    def settle_motion(self):
        time.sleep(.3);last=self.position();deadline=time.monotonic()+2
        while time.monotonic()<deadline:
            time.sleep(.12);now=self.position()
            if max(abs(a-b) for a,b in zip(now,last))<.005:return
            last=now
        raise TimeoutError('Player did not settle after key release')
    def walk(self,target,precise=False):
        deadline=time.monotonic()+35;best=1e9;stale=time.monotonic();tol=.045 if precise else .16
        while time.monotonic()<deadline:
            pos=self.position();dx=target[0]-pos[0];dz=target[2]-pos[2];dist=math.hypot(dx,dz)
            if dist<=tol:
                if abs(target[1]-pos[1])>.65:raise AssertionError(f'Wrong walking elevation: {pos}, expected {target}')
                self.event('arrived',target=target,position=pos,tolerance=tol);return
            if dist<best-.025:best=dist;stale=time.monotonic()
            if time.monotonic()-stale>8:raise TimeoutError(f'Movement stalled: {pos} -> {target}')
            self.look(-math.degrees(math.atan2(dx,dz)),0)
            creep=precise and dist<.8
            duration=min(.3 if creep else .55,max(.025 if creep else .035,(dist-tol*.35)/(1.3 if creep else 4.4)-(.08 if creep else 0)))
            self.gate();self.event('mapped_input',keys=['Shift_L','w'] if creep else ['w'],seconds=duration,position=pos,target=target)
            try:
                if creep:
                    self.s.game.xdo('keydown','Shift_L');self.wait_crouch(True)
                self.s.game.xdo('keydown','w');time.sleep(duration)
            finally:
                self.s.game.xdo('keyup','w')
                if creep:
                    self.settle_motion();self.s.game.xdo('keyup','Shift_L');self.wait_crouch(False)
            if not creep:time.sleep(.12)
        raise TimeoutError(f'Walking timed out: {self.position()} -> {target}')
    def shot(self,name):
        self.gate();time.sleep(.5);self.s.game.save_png(self.s.game.grab_raw(),self.out/(name+'.png'))
        self.event('screenshot',name=name,position=self.position(),rotation=self.rotation())
    def open_door(self,door):
        p=door['pos'];xyz=' '.join(map(str,p));name=door['state'].split('[')[0]
        if 'passed' in self.cmd(f'execute if block {xyz} {name}[open=true]').lower():return
        pos=self.position();dx=p[0]+.5-pos[0];dz=p[2]+.5-pos[2];dy=p[1]+.85-(pos[1]+1.62)
        if math.sqrt(dx*dx+dz*dz+dy*dy)>4.3:raise AssertionError('Door out of actual interaction range')
        self.look(-math.degrees(math.atan2(dx,dz)),-math.degrees(math.atan2(dy,math.hypot(dx,dz))))
        self.gate();self.s.game.xdo('click','3');self.event('mapped_input',button=3,door=p);time.sleep(.35)
        result=self.cmd(f'execute if block {xyz} {name}[open=true]')
        if 'passed' not in result.lower():raise AssertionError(f'Actual right click did not open door: {p}: {result}')
        self.opened.append(p);self.event('door_opened',position=p)
    def start(self):
        for rel,sha in self.m['files'].items():
            if hashlib.sha256((self.pack/rel).read_bytes()).hexdigest()!=sha:raise ValueError('Pack hash mismatch: '+rel)
        if (cs.SERVER_DIR/cs.LEVEL_NAME).exists():raise RuntimeError('Fresh run refuses an existing world')
        cs.merge_options(cs.CLIENT_DIR/'options.txt',{'mouseSensitivity':'0.5','rawMouseInput':'false','bobView':'false','key_key.sneak':'key.keyboard.left.shift','toggleCrouch':'false'})
        state=cs.start(cs.SessionConfig(session_id=self.out.name,lock=os.environ['MC_HEAVY_LOCK'],lock_timeout=10,
            language='zh_cn',view_distance=12,world_seed=20261024,framebuffer_width=1280,framebuffer_height=720))
        self.s=cs.Session.attach();self.s.game.size=(1280,720)
        self.r=connect(state['rcon_port'],state['rcon_password'],retries=1,timeout=120)
        self.report['processes']={k:v for k,v in state['procs'].items()};self.save()
        self.gate()
    def place(self):
        self.begin('complete_placement')
        world=cs.SERVER_DIR/cs.LEVEL_NAME
        dest=world/'datapacks/river_valley_sample';shutil.copytree(self.pack/'datapack',dest)
        for command in ['reload','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doMobSpawning false',
                        'gamerule randomTickSpeed 0','time set noon','weather clear','gamemode spectator '+self.s.username,'tick freeze']:
            self.cmd(command)
        try:
            for z in sorted({t['world_min'][2] for t in self.m['tiles']}):
                self.tickets=(-16,z-16,self.m['size'][0]+15,z+47)
                self.cmd('forceload add '+' '.join(map(str,self.tickets)));time.sleep(2)
                for tile in [t for t in self.m['tiles'] if t['world_min'][2]==z]:
                    xyz=' '.join(map(str,tile['world_min']))
                    result=self.cmd(f"place template {tile['id']} {xyz}")
                    if not placement_confirmed(result,tile['id'],tile['world_min']):raise AssertionError('Template placement failed: '+result)
                    p=tile['probe'];probe=self.cmd('execute if block '+' '.join(map(str,p['pos']))+' '+p['state'])
                    if 'passed' not in probe.lower():raise AssertionError('Template readback failed: '+str(p))
                    self.report['tiles_completed']+=1;self.save()
                self.cmd('forceload remove '+' '.join(map(str,self.tickets)));self.tickets=None
                print('Placed row',z,'tiles',self.report['tiles_completed'],flush=True)
        finally:self.cmd('tick unfreeze')
        # Support across a tile boundary was absent during its first placement.
        # Restore only generator-declared hanging fixtures after all supports exist.
        for attachment in self.m.get('deferred_attachments',[]):
            p=attachment['pos'];support=attachment['support'];self.tickets=(p[0]-16,p[2]-16,p[0]+16,p[2]+16)
            self.cmd('forceload add '+' '.join(map(str,self.tickets)));time.sleep(.2)
            support_reply=self.cmd('execute if block '+' '.join(map(str,support['pos']))+' '+support['state'])
            if 'passed' not in support_reply.lower():raise AssertionError('Deferred attachment support is absent')
            xyz=' '.join(map(str,p));state=attachment['state']
            if 'passed' not in self.cmd(f'execute if block {xyz} {state}').lower():
                if 'passed' not in self.cmd(f'execute if block {xyz} minecraft:air').lower():raise AssertionError('Deferred fixture would overwrite an unexpected block')
                self.cmd(f'setblock {xyz} {state}')
                if 'passed' not in self.cmd(f'execute if block {xyz} {state}').lower():raise AssertionError('Deferred fixture did not survive')
                self.event('deferred_fixture_restored',position=p,state=state)
            self.cmd('forceload remove '+' '.join(map(str,self.tickets)));self.tickets=None
        self.done('complete_placement')
    def photos(self):
        self.begin('native_screenshots')
        for name,p in [('native-overview',(260,195,300,150,40)),('native-village',(267,113,320,140,25)),
                       ('native-courts',(180,137,201,125,22)),('native-relay',(145,151,122,130,28)),
                       ('native-bridge',(290,103,241,180,28))]:
            self.cmd(f'tp {self.s.username} '+' '.join(map(str,p)));time.sleep(6);self.shot(name)
        self.done('native_screenshots')
    def tour(self):
        self.begin('mapped_all_entrances');self.begin('mapped_roads_bridge')
        spawn=self.m['spawn'];self.cmd('tp '+self.s.username+f' {spawn[0]+.5} {spawn[1]} {spawn[2]+.5} 180 0')
        for command in ['gamemode survival '+self.s.username,'effect give '+self.s.username+' minecraft:saturation infinite 0 true',
                        'clear '+self.s.username]:self.cmd(command)
        time.sleep(2);self.gate();self.shot('native-arrival')
        with (self.out/'recording.log').open('w') as log:
            self.rec=subprocess.Popen(capture.ffmpeg_record_argv(self.s.state['display'],self.out/'mapped-tour.mp4',3600,fps=20,size=(1280,720)),stdin=subprocess.PIPE,stdout=log,stderr=log)
        self.report['recording']={'pid':self.rec.pid,'start':procs.start_time(self.rec.pid)};self.save()
        self.event('mapped_tour_start',position=self.position(),auto_jump=False)
        for target in self.m['tour']:
            self.report['current_target']=target['id'];self.save();print('TOUR',target['id'],flush=True)
            for p in target['route']:self.walk([p[0]+.5,p[1],p[2]+.5])
            if 'entry_path' in target:
                doors=target['doors'];path=target['entry_path']
                for p in path:
                    for door in doors:
                        if p==door['pos']:self.open_door(door)
                    self.walk(door_point(p,doors),precise=bool(doors))
                self.shot('inside-'+target['id'])
                for p in path[-2::-1]:self.walk(door_point(p,doors),precise=bool(doors))
            else:self.shot('walk-'+target['id'])
            self.report['tour_completed'].append(target['id']);self.save()
        self.event('mapped_tour_end',position=self.position());self.done('mapped_all_entrances');self.done('mapped_roads_bridge')
    def cleanup(self):
        if self.s:
            for key in ('w','a','s','d','space','Shift_L'):
                try:self.s.game.xdo('keyup',key)
                except Exception:pass
        if self.rec:
            try:self.report['video_exit']=capture.stop_recording(self.rec)
            except Exception as e:self.report['video_error']=repr(e)
        if self.r:
            try:
                if self.tickets:self.cmd('forceload remove '+' '.join(map(str,self.tickets)))
                if not self.flush_attempted:
                    self.flush_attempted=True;self.report['flush_sent_at']=time.time();self.save()
                    self.report['flush_response']=self.cmd('save-all flush');self.save()
            except Exception as e:self.report['save_error']=repr(e)
            self.r.close()
        state=cs.read_state()
        if state and state.get('session_id')==self.out.name:
            cs.stop();state=cs.read_state()
            self.report['cleanup']={'phase':state.get('phase'),'alive':[k for k,v in state.get('procs',{}).items() if procs.is_alive(v['pid'],v['start'])],
                'lock_free':procs.lock_is_free(Path(os.environ['MC_HEAVY_LOCK']))}
        self.report['opened_doors']=self.opened
        self.report['failed']=[x for x in self.report['attempted'] if x not in self.report['completed']]
        self.report['tour_unrun']=[x['id'] for x in self.m['tour'] if x['id'] not in self.report['tour_completed']]
        self.save()
        world=cs.SERVER_DIR/cs.LEVEL_NAME
        if world.exists() and not self.report.get('cleanup',{}).get('alive',['unknown']):
            shutil.make_archive(str(self.out/'saved-world'),'zip',world)
            self.report['saved_world_sha256']=hashlib.sha256((self.out/'saved-world.zip').read_bytes()).hexdigest()
        if cs.LOG_DIR.exists():shutil.copytree(cs.LOG_DIR,self.out/'logs')
        self.report['finished']=time.time();self.save()


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--pack',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    if not Path('/content').is_dir():raise RuntimeError('Native execution requires the explicitly selected Colab VM')
    run=Run(a.pack.resolve(),a.out.resolve())
    def stopped(*_):raise InterruptedError('Controlled native run stop requested')
    signal.signal(signal.SIGTERM,stopped)
    try:run.start();run.place();run.photos();run.tour();run.report['status']='tour_passed_pending_saved_world'
    except BaseException as e:run.report['status']='failed';run.report['error']=repr(e);traceback.print_exc()
    finally:
        try:run.cleanup()
        except BaseException as e:run.report['cleanup_error']=repr(e);run.save();traceback.print_exc()
        run.events.close()
    print(json.dumps(run.report,ensure_ascii=False),flush=True)

if __name__=='__main__':main()
