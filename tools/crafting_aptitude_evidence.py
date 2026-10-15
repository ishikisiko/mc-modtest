#!/usr/bin/env python3
"""Mapped awakening/read-only H layouts and crafting-aptitude lifecycle evidence.

Run clean stage1 first. Owns two capture sessions and their heavy-work lock.
Administrator score fixtures are explicit, not natural aptitude distribution or
an implemented pill-quality/forging production loop. No DevBridge.
"""
from __future__ import annotations
import argparse
import html
import json
from pathlib import Path
import secrets
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:sys.path.insert(0,str(ROOT))
from tools.combat_capture import procs,scene,session as cs
from tools.realm_stats_evidence import Evidence


class CraftingEvidence(Evidence):
    def __init__(self,out):
        super().__init__(out)
        self.report.update(kind='crafting_aptitudes',owner_balance='not_verified',
                           owner_appearance='not_verified',quality_production='not_implemented',
                           forging_production='not_implemented',legacy_save_live_migration='not_verified',
                           method='mapped testing-stele right click and H navigation; RCON status/set fixtures; no DevBridge')
        self.report.pop('natural_encounter_density',None)

    def start(self,reuse=False,language='zh_cn'):
        begin=time.monotonic()
        state=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),reuse_world=reuse,language=language,
                                       lock=str(procs.default_lock_path(ROOT))))
        self.s=cs.Session.attach(ui_check=True);self.sessions+=1
        self.report['phases'].append({'phase':'restart_setup' if reuse else 'initial_setup','language':language,
                                     'wall_s':round(time.monotonic()-begin,3),'capture_timings':state.get('timings',{})})
        options=dict(line.split(':',1) for line in (ROOT/'run-combat-capture/client/options.txt').read_text().splitlines() if ':' in line)
        if options.get('key_key.myvillage.open_cultivation_profile')!='key.keyboard.h':
            raise RuntimeError('H capture binding differs; no binding changed')
        self.save()

    def state(self,root='cultivation',command='crafting'):
        value=self.one(f'myvillage {root} {command} {self.s.username}','CRAFTING')
        for key in ('initialized','alchemy','forging','alchemy_quality_cap','forging_quality_cap'):
            if key not in value:raise ValueError('Missing crafting readback: '+key)
        return value

    def poll(self,getter,predicate,timeout,label):
        deadline=time.monotonic()+timeout;last=None
        while time.monotonic()<deadline:
            last=getter()
            if predicate(last):return last
            time.sleep(.25)
        raise RuntimeError(f'Timed out {label}: {last}')

    def capture(self,name):
        g=self.s.game;g.xdo('mousemove','0','0');time.sleep(.2)
        window=g.xdo('search','--name','Minecraft').split()[0]
        subprocess.run(['import','-silent','-window',window,str(self.out/name)],env=g.env,check=True,timeout=30)

    def panel(self):
        g=self.s.game;g.require_ingame('open crafting H page');g.key('h');time.sleep(.5)
        width,height=g.size;gui_height=height//2;top=(gui_height-min(246,gui_height-8))//2
        # Six ordered tabs; Crafting is appended at index5; all tested windows have GUI scale2.
        g.xdo('mousemove','55',str(2*(top+36+5*21+9)),'click','1');time.sleep(.4)
        if g.pointer_grabbed() is not False:raise RuntimeError('H panel did not open')
        tail=g.client_log.read_text(errors='replace')[-20000:]
        if 'CRAFTING_UI page=crafting visible=true' not in tail:raise RuntimeError('Mapped nav did not select crafting page')

    def close_panel(self):
        self.s.game.xdo('key','Escape');time.sleep(.3);self.s.game.require_ingame('close H page')

    def layouts(self,language):
        started=time.monotonic();g=self.s.game;window=g.xdo('search','--name','Minecraft').split()[0]
        before=self.state();images=[]
        for width,height in ((960,540),(854,480),(640,480)):
            g.xdo('windowsize',window,str(width),str(height));g.xdo('windowmove',window,'0','0')
            g.size=(width,height);time.sleep(.7);g.recover_grab()
            self.panel();name=f'{language}_{width//2}x{height//2}_crafting.png';self.capture(name)
            # The page is read-only even at formerly active areas of neighboring pages.
            g.xdo('mousemove',str(width//2),str(height//2),'click','1');time.sleep(.2)
            self.check(f'page_readonly_{language}_{width//2}x{height//2}',self.state()==before,before)
            self.close_panel();images.append(name)
        g.xdo('windowsize',window,'960','540');g.size=(960,540);time.sleep(.4)
        self.report['phases'].append({'phase':'panel_layouts','language':language,'wall_s':round(time.monotonic()-started,3),'images':images});self.save()

    def initial(self):
        user=self.s.username
        for cmd in (*scene.world_rules(),*scene.player_setup(user,'minecraft:air'),
                    'myvillage world pause','forceload add -16 -16 32 32'):
            self.rc(cmd)
        empty=self.state()
        self.check('new_unawakened_is_pending',empty=={'initialized':False,'alchemy':0,'forging':0,'alchemy_quality_cap':0,'forging_quality_cap':0},empty)
        self.panel();self.capture('zh_cn_pending.png');self.close_panel()
        self.rc(f'myvillage cultivation crafting set {user} alchemy 100')
        self.check('uninitialized_admin_set_rejected',self.state()==empty,self.state())
        for cmd in ('setblock 0 -60 2 minecraft:stone','setblock 0 -59 2 myvillage:spirit_testing_stele',
                    f'tp {user} .5 -60 .5 0 3.4'):
            self.rc(cmd)
        time.sleep(.5);self.s.game.xdo('click','3')
        initialized=self.poll(self.state,lambda s:s['initialized'],10,'mapped awakening installs aptitudes')
        self.check('mapped_awakening_generates_both',all(0<=initialized[k]<=100 for k in ('alchemy','forging'))
                   and all(1<=initialized[k]<=4 for k in ('alchemy_quality_cap','forging_quality_cap')),initialized)
        self.capture('zh_cn_mapped_awakening.png');self.close_panel()
        self.report['natural_initial_scores']=initialized
        # Every threshold on both professions; explicit fixtures, not natural rolls.
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/crafting/rules.json').read_text())
        points=sorted({0,100,*[r['minimum'] for r in rules['quality_thresholds']],
                       *[r['minimum']-1 for r in rules['quality_thresholds'] if r['minimum']>0]})
        for profession in ('alchemy','forging'):
            other='forging' if profession=='alchemy' else 'alchemy'
            before=self.state()
            for value in points:
                expected=max(r['quality'] for r in rules['quality_thresholds'] if value>=r['minimum'])
                self.rc(f'myvillage cultivation crafting set {user} {profession} {value}')
                actual=self.state()
                self.check(f'{profession}_quality_boundary_{value}',actual[profession]==value and
                           actual[profession+'_quality_cap']==expected and actual[other]==before[other],actual)
        for prefix in ('cultivation','xiulian'):
            for branch in ('crafting','jiyi'):
                self.check('alias_status_'+prefix+'_'+branch,self.state(prefix,branch)==self.state(),self.state(prefix,branch))
        self.rc(f'myvillage xiulian jiyi shezhi {user} liandan 70')
        self.rc(f'myvillage cultivation jiyi shezhi {user} lianqi 100')
        fixed=self.state();self.check('pinyin_setters_preserve_independent_scores',fixed['alchemy']==70 and fixed['forging']==100,fixed)
        for value in (-1,101):
            self.rc(f'myvillage cultivation crafting set {user} alchemy {value}')
            self.check('invalid_score_'+str(value),self.state()==fixed,self.state())
        self.layouts('zh_cn')
        self.rc(f'kill {user}')
        self.poll(lambda:self.number(f'data get entity {user} Health'),lambda hp:hp>0,10,'automatic respawn')
        self.poll(self.state,lambda s:s==fixed,10,'death preservation')
        time.sleep(1);self.s.game.recover_grab()
        self.check('death_keeps_aptitudes',self.state()==fixed,self.state())
        self.rc(f'myvillage cultivation clearroot {user}')
        self.check('clear_root_keeps_aptitudes',self.state()==fixed,self.state())
        self.rc(f'myvillage cultivation awaken {user}')
        self.check('reawakening_does_not_reroll',self.state()==fixed,self.state())
        self.rc(f'myvillage cultivation reset {user}')
        self.check('profile_reset_keeps_aptitudes',self.state()==fixed,self.state())
        self.rc(f'myvillage cultivation awaken {user}')
        self.report['before_restart']=self.state();self.rc('save-all flush');self.save()

    def restart_checks(self):
        actual=self.state();self.check('restart_keeps_values_and_quality_caps',actual==self.report['before_restart'],actual)
        self.layouts('en_us')

    def finish(self):
        self.report['status']='pass' if not self.report['failures'] and all(r['pass'] for r in self.report['checks']) else 'fail'
        self.save()
        rows=''.join('<li>'+html.escape(('PASS ' if r['pass'] else 'FAIL ')+r['check'])+'</li>' for r in self.report['checks'])
        imgs=''.join(f'<figure><img src="{p.name}"><figcaption>{html.escape(p.stem)}</figcaption></figure>' for p in sorted(self.out.glob('*.png')))
        (self.out/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>技艺资质</title><style>body{background:#19201e;color:#eee;font:16px system-ui;max-width:1080px;margin:30px auto}img{max-width:100%}figure{margin:24px 0}</style>'
            '<h1>炼丹与炼器资质 · 实机验证</h1><p>上限针对同一配方的成品品质；现有丹药尚未分品质，炼器生产尚未实现。玩家外观、平衡与多人验收未确认。</p>'
            '<a href="evidence.json">服务端读数与阶段耗时</a><ul>'+rows+'</ul>'+imgs)


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'out/preview/crafting_aptitudes')
    args=p.parse_args(argv);e=CraftingEvidence(args.out)
    try:
        e.start();e.initial();e.stop();e.start(reuse=True,language='en_us');e.restart_checks()
    except Exception as exc:
        e.report['failures'].append(str(exc));raise
    finally:
        if e.s is not None:
            try:e.stop()
            except Exception as exc:e.report['failures'].append('cleanup: '+str(exc))
        e.finish()
    return 0 if e.report['status']=='pass' else 1

if __name__=='__main__':raise SystemExit(main())
