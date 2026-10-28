#!/usr/bin/env python3
"""定向原生界面验证：默认世界类型、H 行迹页与 O 快捷入口。"""
from pathlib import Path
import argparse
import json
import os
import secrets
import sys
import time
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
CASES=('journey','fresh_default')


def latest_ui(text):
    rows=[line.split('WORLD_UI_EVIDENCE ',1)[1] for line in text.splitlines() if 'WORLD_UI_EVIDENCE ' in line]
    return json.loads(rows[-1]) if rows else None


def find_button(ui,text,exact=False):
    found={tuple(w[k] for k in ('x','y','width','height')):w for w in ui['widgets']
           if w['active'] and (w['text']==text if exact else text in w['text'])}
    if len(found)!=1:raise ValueError(f'按钮 {text!r} 匹配 {len(found)} 个：'+str([w['text'] for w in ui['widgets']]))
    w=next(iter(found.values()));x=w['x']+w['width']/2;y=w['y']+w['height']/2
    if not 0<=x<ui['width'] or not 0<=y<ui['height']:raise ValueError('按钮超出实际窗口')
    return x,y


def run(out,commit):
    from tools.realm_stats_evidence import Evidence
    from tools.combat_capture import session as cs,procs
    from tools.cultivation_demo_evidence import status_json
    from tools.immortal_equipment_evidence import verify_launch_artifacts
    class Driver(Evidence):
        def __init__(self):
            if out.exists() and any(out.iterdir()):raise ValueError('不能覆盖已有采集')
            super().__init__(out)
            self.report.update(kind='default_world_journey',source_commit=commit,selected=list(CASES),completed=[],unrun=list(CASES),
                inputs=[],screenshots=[],owner_appearance='not_verified',method='mapped keyboard/mouse; read-only widget/selection logging and server status; no client state injection')
        def must(self,name,passed,detail):
            if not self.check(name,passed,detail):raise AssertionError(name)
        def poll(self,fn,predicate,label,timeout=35):
            deadline=time.monotonic()+timeout;value=None
            while time.monotonic()<deadline:
                value=fn()
                if predicate(value):return value
                time.sleep(.25)
            raise TimeoutError(label+': '+str(value)[-500:])
        def ui(self):return latest_ui((cs.CLIENT_DIR/'logs/latest.log').read_text(errors='replace'))
        def screen(self,name,timeout=35):return self.poll(self.ui,lambda v:v and v['screen']==name,name,timeout)
        def key(self,key):
            self.s.game.xdo('key','--delay','100',key)
            self.report['inputs'].append({'kind':'mapped_key','key':key});self.save();time.sleep(.3)
        def click(self,text,exact=False):
            ui=self.ui();x,y=find_button(ui,text,exact)
            x=round(x*self.s.game.size[0]/ui['width']);y=round(y*self.s.game.size[1]/ui['height'])
            self.s.game.xdo('mousemove','--sync',str(x),str(y),'click','1')
            self.report['inputs'].append({'kind':'mapped_click','text':text,'x':x,'y':y,'screen':ui['screen']});self.save();time.sleep(.4)
        def shot(self,name):
            path=self.out/(name+'.png');self.s.game.shot(path,max_wait=.3)
            self.report['screenshots'].append(path.name);self.save()
        def dim(self):return self.rc(f'data get entity {self.s.username} Dimension')
        def start(self):
            os.environ['JAVA_TOOL_OPTIONS']=(os.environ.get('JAVA_TOOL_OPTIONS','')+' -Dmyvillage.uiEvidence=true').strip()
            self.report['launch_files_matching_jar']=verify_launch_artifacts(ROOT)
            state=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),language='zh_cn',prebuilt_resources=True,
                gradle_jvmargs='-Xmx2g',settle=3,lock=str(procs.default_lock_path(ROOT))))
            self.s=cs.Session.attach(ui_check=True);self.sessions+=1
            self.poll(lambda:status_json(self.rc('myvillage qianzhen realm_status '+self.s.username),'QIANZHEN_REALM_STATUS '),
                lambda s:not s['building'] and not s['waiting'],'首次秘境准备完成',150)
            self.poll(self.dim,lambda d:'myvillage:qianzhen/' in d,'已经进入自己的秘境')
            self.poll(self.s.game.pointer_grabbed,lambda v:v is True,'世界画面已获得输入',90)
        def journey(self):
            self.key('h');self.screen('CultivationProfileScreen');self.click('行迹',True)
            self.poll(self.ui,lambda u:any(w['text']=='返回原世界' and w['active'] for w in u['widgets']),'H 内返回按钮')
            self.shot('H行迹_秘境内')
            ui=self.ui();labels={w['text'] for w in ui['widgets']}
            self.must('行迹是H子页','行迹' in labels and '返回原世界' in labels,sorted(labels))
            # O from another hub page switches to journey instead of opening a second standalone screen.
            language=json.loads((ROOT/'src/main/resources/assets/myvillage/lang/zh_cn.json').read_text())
            self.click(language['screen.myvillage.cultivation.tab.profile'],True);self.key('o')
            self.poll(self.ui,lambda u:u['screen']=='CultivationProfileScreen' and any(w['text']=='返回原世界' for w in u['widgets']),'O切回H行迹')
            self.click('返回原世界',True)
            self.poll(self.dim,lambda d:'minecraft:overworld' in d,'未完成任务即可返回')
            self.poll(self.s.game.pointer_grabbed,lambda v:v is True,'返回后游戏输入',90)
            self.key('o');self.screen('CultivationProfileScreen')
            self.poll(self.ui,lambda u:any(w['text']=='进入阡镇' for w in u['widgets']),'主世界进入按钮')
            self.shot('H行迹_主世界')
            self.click('进入阡镇',True);self.poll(self.dim,lambda d:'myvillage:qianzhen/' in d,'重新进入秘境')
            self.poll(self.s.game.pointer_grabbed,lambda v:v is True,'重新进入后游戏输入',90)
            self.key('o');self.screen('CultivationProfileScreen')
            window=self.s.game.xdo('search','--name','Minecraft').split()[0]
            self.s.game.xdo('windowsize',window,'640','480');self.s.game.size=(640,480)
            self.poll(self.ui,lambda u:u['screen']=='CultivationProfileScreen' and u['width']==320,'窄窗口行迹布局')
            ui=self.ui();x,y=find_button(ui,'返回原世界',True)
            self.must('窄窗口返回按钮可见',0<=x<320 and 0<=y<240,{'x':x,'y':y})
            self.shot('H行迹_窄窗口')
            self.key('o');self.poll(self.s.game.pointer_grabbed,lambda v:v is True,'O关闭行迹')
            self.s.game.xdo('windowsize',window,'960','540');self.s.game.size=(960,540);time.sleep(.8)
            self.report['completed'].append('journey');self.report['unrun'].remove('journey');self.save()
        def fresh_default(self):
            self.key('Escape');self.screen('PauseScreen');self.click('断开连接')
            self.screen('JoinMultiplayerScreen');self.key('Escape');self.screen('TitleScreen');self.click('单人游戏')
            ui=self.poll(self.ui,lambda u:u and u['screen'] in ('SelectWorldScreen','CreateWorldScreen'),'选择或新建世界',90)
            if ui['screen']=='SelectWorldScreen':self.click('创建新的世界')
            ui=self.screen('CreateWorldScreen',120)
            self.must('默认选择与实际生成器一致',ui.get('preset')=='myvillage:cultivation' and ui.get('generator','').endswith('PlannedChunkGenerator'),
                      {k:ui.get(k) for k in ('preset','generator')})
            self.click('世界',True);self.shot('新建世界_默认修仙大陆')
            # One or more ordinary user clicks can select vanilla again; switching tabs must not override it.
            self.click('修仙大陆')
            self.poll(self.ui,lambda u:u.get('preset')!='myvillage:cultivation','手动切换预设')
            selected=self.ui()['preset'];generator=self.ui()['generator']
            self.click('游戏',True);self.click('世界',True)
            self.must('手动选择换页后保留',self.ui()['preset']==selected and self.ui()['generator']==generator,{'preset':selected,'generator':generator})
            self.shot('新建世界_手动选择保留')
            self.key('Escape');self.screen('SelectWorldScreen');self.click('创建新的世界');self.screen('CreateWorldScreen',120)
            self.must('再次全新建档仍默认修仙大陆',self.ui().get('preset')=='myvillage:cultivation',self.ui().get('preset'))
            self.key('Escape')
            self.report['completed'].append('fresh_default');self.report['unrun'].remove('fresh_default');self.save()
    e=Driver()
    try:
        e.start();e.journey();e.fresh_default()
    except BaseException as failure:
        e.report['failures'].append(f'{type(failure).__name__}: {failure}');e.save();raise
    finally:
        if e.s is not None:e.stop()
        e.save()


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--plan',action='store_true');p.add_argument('--source-commit');p.add_argument('--out',type=Path,required=True)
    a=p.parse_args()
    if a.plan:print(json.dumps({'selected':CASES,'completed':[],'unrun':CASES}));return
    if not a.source_commit or not Path('/content').exists():p.error('需要固定提交及Colab环境')
    run(a.out,a.source_commit)
if __name__=='__main__':main()
