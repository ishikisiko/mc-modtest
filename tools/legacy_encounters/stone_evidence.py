"""新石灵表面和镇岳尺的实机展示；姿态夹具与映射技能输入分别记录。"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess
import time
import traceback

from tools.legacy_encounters.evidence import LegacyEvidence

CASES = ('model_views', 'weapon_contexts', 'resource_reload')


class StoneEvidence(LegacyEvidence):
    def __init__(self,out,cases,attach=False):
        super().__init__(out,cases,attach)
        self.report.update(kind='stele_ruler_native_models',method=__doc__,owner_verdict='human_review_pending',
                           natural_acquisition='本轮仅验证模型、贴图、握持、立尺与资源重载。',
                           images=[],videos=[])
        self.report['collector_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
        self.hidden=False

    def hud(self,hidden):
        if self.hidden!=hidden:
            self.key('F1');self.hidden=hidden;time.sleep(.3)

    def camera(self,position,target):
        dx,dy,dz=(target[i]-position[i] for i in range(3))
        yaw=math.degrees(math.atan2(-dx,dz));pitch=-math.degrees(math.atan2(dy-1.62,math.hypot(dx,dz)))
        self.rc(f'tp {self.s.username} {position[0]} {position[1]} {position[2]} {yaw} {pitch}')
        self.step(4);time.sleep(.5)
        self.report.setdefault('cameras',[]).append({'position':position,'target':target})

    def picture(self,name,label):
        self.capture(name)
        self.report['images'].append({'file':name+'.png','label':label});self.save()

    def record(self,name,label,action,seconds):
        path=self.out/(name+'.mp4');log=(self.out/(name+'-ffmpeg.log')).open('w')
        args=['ffmpeg','-hide_banner','-loglevel','error','-y','-f','x11grab','-draw_mouse','0','-video_size','960x540','-framerate','20','-i',self.s.game.display+'.0+0,0','-t',str(seconds),'-c:v','libx264','-preset','ultrafast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(path)]
        self.rc('tick unfreeze');begin=time.monotonic();tick=self.now()
        process=subprocess.Popen(args,stdout=log,stderr=subprocess.STDOUT)
        try:
            time.sleep(.5);action();process.wait(timeout=seconds+20)
            elapsed=time.monotonic()-begin;end=self.now()
        finally:
            if process.poll() is None:process.terminate();process.wait(timeout=10)
            self.freeze();log.close()
        self.check(name+'_video_complete',process.returncode==0 and path.stat().st_size>10000,{'exit_code':process.returncode,'bytes':path.stat().st_size})
        self.report['videos'].append({'file':path.name,'label':label,'wall_seconds':round(elapsed,3),'server_ticks':end-tick,
                                      'method':'真实客户端按实际时间录制；未变速、无音轨，软件渲染不作为 FPS 验收。'})
        self.save()

    def model_views(self):
        self.clean();self.rc(f'gamemode spectator {self.s.username}');self.hud(True)
        self.rc('summon myvillage:stele_guardian .5 -60 8.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:["legacy_beast"],Rotation:[180f,0f]}')
        self.step(15);time.sleep(3)
        for name,position,target,label in [
            ('guardian-front',(.5,-59,1.7),(.5,-58,8.5),'石灵正面'),
            ('guardian-three-quarter',(4.7,-58.3,3.1),(.5,-58,8.5),'石灵斜前方'),
            ('guardian-side',(7.2,-59,8.5),(.5,-58,8.5),'石灵侧面'),
            ('guardian-back',(.5,-59,15.3),(.5,-58,8.5),'石灵背面'),
            ('guardian-detail',(2.8,-57.8,4.8),(.5,-57.2,8.5),'石灵面部和胸前刻纹')]:
            self.camera(position,target);self.picture(name,label)
        self.rc('myvillage beast debug on');self.rc('data merge entity @e[tag=legacy_beast,limit=1] {NoAI:0b}')
        self.camera((5.8,-55.8,-.4),(.5,-57,8.5))
        def action():
            self.rc('myvillage beast move @e[tag=legacy_beast,limit=1] myvillage:stele_guardian_quake')
            time.sleep(4.0)
            self.rc('myvillage beast move @e[tag=legacy_beast,limit=1] myvillage:stele_guardian_slam')
        self.record('guardian-motion','石灵动作展示（管理员触发动作，用于检查骨骼与表面）',action,8)
        self.report['guardian_pose_boundary']='静态图为 NoAI 造型夹具；动作由管理员命令触发，均不作为自然遭遇或玩家战斗验收。'
        self.rc('kill @e[tag=legacy_beast]');self.step(25)
        self.rc('summon myvillage:demon_wolf .5 -60 8.5 {NoAI:1b,PersistenceRequired:1b,Tags:["legacy_beast"],Rotation:[180f,0f]}')
        self.camera((3,-59.4,5),(.5,-59.3,8.5));self.picture('cuboid-regression','原长方体模型路径的实机回归参照')
        self.rc('kill @e[tag=legacy_beast]');self.step(25);self.hud(False)

    def weapon_contexts(self):
        self.configure(self.s.username);self.clean();self.fixture_bind();self.ready(240);self.equip('myvillage:zhenyue_ruler')
        self.rc(f'tp {self.s.username} .5 -60 .5 0 0');self.step(8)
        self.picture('ruler-first-person','镇岳尺第一人称握持')
        self.hud(True);self.key('F5');self.step(4);self.picture('ruler-third-person-back','镇岳尺第三人称背面握持')
        self.key('F5');self.step(4);self.picture('ruler-third-person-front','镇岳尺第三人称正面握持')
        self.key('F5');self.step(4)
        self.rc(f'tp {self.s.username} .5 -60 .5 0 24');self.cast(self.ids[3])
        state=self.status();self.check('ruler_uses_registered_projection',len(state['active'])==1 and state['active'][0]['mode']=='ruler',state)
        center=state['active'][0]['pos'];target=(center[0],center[1]+1.9,center[2])
        # Spectator mode intentionally clears an active legacy projection; creative remains
        # a valid owner while this visual fixture moves the camera around the deployed item.
        self.rc(f'gamemode creative {self.s.username}')
        for name,position,label in [
            ('ruler-deployed-front',(center[0]+3.0,center[1]+.2,center[2]-5.5),'立地镇岳尺的正面与倒角'),
            ('ruler-deployed-side',(center[0]+5,center[1]+.2,center[2]+2),'立地镇岳尺的截面与握柄过渡')]:
            self.camera(position,target);self.picture(name,label)
        # Return to the original survival position before the mapped recall.
        self.rc(f'gamemode survival {self.s.username}');self.rc(f'tp {self.s.username} .5 -60 .5 0 24');self.step(4)
        self.cast(self.ids[3],expected_cost=0)
        self.check('ruler_recall_restores_the_held_weapon',not self.status()['weapon_away'] and not self.status()['active'],self.status())
        self.hud(False);self.picture('ruler-recalled','镇岳尺收回后恢复握持');self.clean()

    def resource_reload(self):
        self.equip('myvillage:zhenyue_ruler');self.rc('tick unfreeze')
        self.s.game.xdo('keydown','F3')
        try:self.key('t')
        finally:self.s.game.xdo('keyup','F3')
        self.report['inputs'].append({'method':'mapped F3+T resource reload'})
        time.sleep(12);self.s.game.require_ingame('资源重载后检查模型');self.freeze()
        self.picture('after-resource-reload','资源重载后的镇岳尺握持')
        self.check('client_survives_resource_reload',True,{'input':'F3+T'})


def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--cases',nargs='+',choices=CASES,default=list(CASES));p.add_argument('--attach',action='store_true');a=p.parse_args()
    e=StoneEvidence(a.out,a.cases,a.attach)
    try:e.run()
    except BaseException as error:e.report['failures'].append(repr(error));traceback.print_exc()
    finally:
        try:
            if e.s:e.rc('tick unfreeze');e.stop()
        except BaseException as error:e.report['failures'].append('cleanup: '+repr(error))
        e.report['passed']=not e.report['failures'] and not e.report['unrun'] and all(c['pass'] for c in e.report['checks']);e.save()
    return 0 if e.report['passed'] else 1


if __name__=='__main__':raise SystemExit(main())
