"""体型与连续特效的定向实机检查；视频使用真实时间和映射输入。"""
from __future__ import annotations
import argparse, hashlib, json, math, subprocess, time, traceback
from pathlib import Path
from tools.legacy_encounters.evidence import LegacyEvidence, Guest, ROOT, read_data

CASES=('geometry','four_arts','artifacts','normal_ai','refusals','clearance','observer_showcase','restart')

class VisualEvidence(LegacyEvidence):
    def __init__(self,out,cases,attach=False):
        super().__init__(out,cases,attach)
        self.report.update(kind='legacy_visual_revision',collector_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            scope='新体型、攻击与碰撞、连续特效、多人投影和生命周期；自然生成规则未改，本轮不重跑自然生成。')

    def geometry(self):
        self.clean();self.ready(240)
        for index,m in enumerate(self.source['mobs']):
            x=8+index*6
            self.rc(f'summon myvillage:{m["id"]} {x} -60 8 {{NoAI:1b,Tags:["legacy_beast"],PersistenceRequired:1b}}')
        self.step(3);rows=self.status()['nearby_beasts']
        for m in self.source['mobs']:
            row=next(v for v in rows if v['type']=='myvillage:'+m['id'])
            self.check(m['id']+'_actual_dimensions',all(abs(row[k]-m['physical'][k])<.0001 for k in ('width','height','eye_height')),row)
            self.check(m['id']+'_grounded',abs(row['y']+60)<.02,row)
        self.clean();self.step(25)
        for kind,mob,x in [('sword','wandering_sword',12),('mountain','stele_guardian',32)]:
            self.rc(f'place template myvillage:legacy_{kind}_ruin {x-7} -61 10')
            self.rc(f'tp {self.s.username} {x+.5} -60 14.5 0 22');self.equip('minecraft:paper');self.use();self.step(2)
            rows=[b for b in self.status()['nearby_beasts'] if b['type']=='myvillage:'+mob]
            self.check(kind+'_enlarged_guardian_fits_existing_trial',bool(rows),self.status())
            self.rc(f'kill @e[type=myvillage:{mob}]');self.step(25)
        self.report['geometry_fixture']='试炼仅验证真实石碑交互能够生成放大后的守护者；这里的管理员清理不作为战斗完成证据。'
        self.rc('fill 4 -60 10 40 -54 24 minecraft:air');self.clean()

    def clearance(self):
        self.clean();self.fixture_bind();self.ready(240);self.equip('myvillage:zhenyue_ruler')
        u=self.s.username;self.rc(f'tp {u} .5 -60 .5 0 30')
        self.rc('fill -3 -57 1 3 -57 8 minecraft:stone');self.step(15)
        before=self.power();mark=self.offset();self.key(self.assigned[self.ids[3]]);time.sleep(.4)
        self.check('large_ruler_rejects_low_ceiling_without_payment',self.power()==before and not self.status()['active'] and 'result=start art=' not in self.log(mark),self.status())
        self.rc('fill -3 -57 1 3 -57 8 minecraft:air');self.step(14);self.cast(self.ids[3])
        self.check('large_ruler_accepts_open_space',any(a['mode']=='ruler' for a in self.status()['active']),self.status())
        self.cast(self.ids[3],expected_cost=0);self.clean();self.ready(200)
        self.equip('myvillage:zhaoshui_mirror');self.use();self.step(2)
        self.check('mirror_visual_exists_in_active_window',any(v['kind']==11 for v in self.status()['visuals']),self.status())
        self.equip('minecraft:air');self.step(2)
        self.check('mirror_visual_clears_on_slot_change',not any(v['kind']==11 for v in self.status()['visuals']),self.status())
        self.step(240)
        self.check('temporary_effects_expire',not self.status()['visuals'],self.status())

    def camera(self,guest,position,target):
        dx,dy,dz=(target[i]-position[i] for i in range(3))
        yaw=math.degrees(math.atan2(-dx,dz));pitch=-math.degrees(math.atan2(dy-1.62,math.hypot(dx,dz)))
        self.rc(f'tp {guest.username} {position[0]} {position[1]} {position[2]} {yaw} {pitch}')
        self.report.setdefault('camera_fixtures',[]).append({'position':position,'target':target,'yaw':yaw,'pitch':pitch})

    def real_cast(self,ident):
        mark=self.offset();self.key(self.assigned[ident])
        self.poll(lambda:self.log(mark),lambda log:f'result=start art=myvillage:{ident}' in log,timeout=8,label='real-time mapped '+ident)

    def record(self,name,guest,action,seconds=7):
        path=self.out/(name+'.mp4');log=(self.out/(name+'-ffmpeg.log')).open('w')
        command=['ffmpeg','-hide_banner','-loglevel','error','-y','-f','x11grab','-draw_mouse','0','-video_size','960x540','-framerate','20','-i',guest.game.display+'.0+0,0','-t',str(seconds),'-c:v','libx264','-preset','ultrafast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(path)]
        first=len(self.report['inputs']);self.rc('tick unfreeze');begin=time.monotonic();tick=self.now()
        p=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT)
        try:
            time.sleep(.6);action();p.wait(timeout=seconds+20)
            elapsed=time.monotonic()-begin;end=self.now()
        finally:
            if p.poll() is None:p.terminate();p.wait(timeout=10)
            self.freeze();log.close()
        self.check(name+'_video_recorded',p.returncode==0 and path.stat().st_size>10000,{'code':p.returncode,'bytes':path.stat().st_size})
        self.report['videos'].append({'file':path.name,'wall_seconds':round(elapsed,3),'server_ticks':end-tick,'input_start':first,'input_end':len(self.report['inputs']),
                                      'method':'真实客户端映射输入；没有 tick step 或播放变速；无音轨；软件渲染不作为 FPS 验收。'})
        self.save()

    def observer_showcase(self):
        self.clean();self.fixture_bind();self.rc('tick unfreeze')
        with Guest(self) as guest:
            self.guest=guest;self.position(guest.game);self.configure(guest.username)
            self.rc(f'gamemode spectator {guest.username}');self.freeze();guest.game.key('F1')
            u=self.s.username
            for m in self.source['mobs']:
                for heat in ([0,3] if m['id']=='ember_toad' else [0]):
                    self.clean();self.ready(220);self.rc(f'tp {u} -1.7 -60 1 180 0')
                    self.rc(f'summon myvillage:{m["id"]} 1.3 -60 1 {{NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],LegacyHeat:{heat},Tags:["legacy_beast"]}}')
                    self.camera(guest,(5,-57.5,-7),(0,-58.3,1));self.step(3)
                    self.capture('scale-'+m['id']+('-heated' if heat else ''),guest.game)
                    self.rc('kill @e[tag=legacy_beast]');self.step(25)
            self.report['scale_screenshots']='真实客户端同镜头的人物与模型；生物使用 NoAI 造型夹具，玩家高 1.8 格。'
            for idx,name in enumerate(('sword','shadow','fire','ruler')):
                self.clean();self.ready(260);self.camera(guest,(9,-54,5),(0,-58.7,5));self.step(3)
                if idx==0:
                    self.equip('myvillage:guiyan_sword');self.dummy_at(.5,8)
                    def action():
                        self.real_cast(self.ids[0]);time.sleep(.8);self.s.game.xdo('keydown','a');time.sleep(.65);self.s.game.xdo('keyup','a')
                        self.report['inputs'].append({'method':'mapped strafe','key':'a','seconds':.65});time.sleep(.4)
                        self.real_cast(self.ids[0]);time.sleep(.8)
                    self.record(name,guest,action)
                    self.check('observer_has_no_weapon_lock',not self.status(guest.username)['weapon_away'],self.status(guest.username))
                elif idx==1:
                    def action():
                        self.real_cast(self.ids[1]);time.sleep(1.3);self.real_cast(self.ids[1]);time.sleep(.6)
                    self.record(name,guest,action)
                elif idx==2:
                    for yaw in (-25,0,25):
                        self.rc(f'tp {u} .5 -60 .5 {yaw} 24');self.cast(self.ids[2]);self.step(45)
                    self.rc(f'tp {u} .5 -60 .5 0 0');self.rc(f'myvillage cultivation qi fill {u}');self.step(15)
                    def action():
                        time.sleep(.8);self.s.game.xdo('keydown','Shift_L');time.sleep(.2)
                        try:self.real_cast(self.ids[2])
                        finally:self.s.game.xdo('keyup','Shift_L')
                        time.sleep(.8)
                    self.record(name,guest,action)
                else:
                    self.equip('myvillage:zhenyue_ruler');self.rc(f'tp {u} .5 -60 .5 0 25')
                    def action():
                        self.real_cast(self.ids[3]);time.sleep(3);self.real_cast(self.ids[3]);time.sleep(.5)
                    self.record(name,guest,action,8)
                self.check(name+'_cleared_after_real_time_action',not self.status()['weapon_away'],self.status())
            self.clean();self.ready(220);self.equip('myvillage:zhaoshui_mirror');self.camera(guest,(5,-58,4),(.5,-58.7,3));self.step(3)
            def mirror_action():
                self.use();self.rc('summon minecraft:arrow .5 -58.5 5 {Motion:[0d,0d,-0.7d],Tags:["legacy_target"]}')
                self.poll(self.status,lambda s:s['charge']==1,timeout=5,label='normal-time mirror capture');time.sleep(3.3);self.use()
            self.record('mirror',guest,mirror_action,9)
            self.report['multiplayer']='两名真实客户端；旁观画面来自独立客户端，并检查旁观者不会继承施法者的武器离手状态。'
            self.guest=None;self.rc('tick unfreeze')
        self.freeze();self.clean()

    def restart(self):
        super().restart()
        self.check('decorative_effects_not_restored',not self.status()['visuals'],self.status())


def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--cases',nargs='+',choices=CASES,default=list(CASES));p.add_argument('--attach',action='store_true');p.add_argument('--keep-game-on-failure',action='store_true');a=p.parse_args()
    e=VisualEvidence(a.out,a.cases,a.attach)
    try:e.run()
    except BaseException as error:e.report['failures'].append(repr(error));traceback.print_exc()
    finally:
        try:
            if e.s:
                e.rc('tick unfreeze')
                if e.report['failures'] and a.keep_game_on_failure:e.report['game_retained_for_debug']=True
                else:e.stop()
        except BaseException as error:e.report['failures'].append('cleanup: '+repr(error))
        e.report['passed']=not e.report['failures'] and not e.report['unrun'] and all(c['pass'] for c in e.report['checks']);e.save()
    return 0 if e.report['passed'] else 1

if __name__=='__main__':raise SystemExit(main())
