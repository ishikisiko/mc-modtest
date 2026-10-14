#!/usr/bin/env python3
"""First-batch fates: mapped H-page input, explicit fixtures, actual effects and restart.

Run clean stage-1 acceptance first. This is developer evidence, never owner feel
or multiplayer acceptance. No DevBridge. Logs and failures remain in the output.
"""
from __future__ import annotations
import argparse
import datetime as dt
import json
import math
import re
from pathlib import Path
import secrets
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:sys.path.insert(0,str(ROOT))
from tools.combat_capture import procs,scene,session as cs
from tools.realm_stats_evidence import Evidence,close

TARGET='@e[tag=fate_fixture,limit=1]'
JADE=bytes((0x6c,0xc5,0xb0))

class FateEvidence(Evidence):
    def __init__(self,out):
        super().__init__(out)
        self.report.update(kind='innate_fates_first_batch',owner_verdict='not_verified',multiplayer='not_verified',
                           method='mapped H-page clicks; RCON fixtures/readback; real client; no DevBridge')
        self.report.pop('natural_encounter_density',None)
    def start(self,reuse=False,language='en_us'):
        started=time.monotonic()
        state=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),reuse_world=reuse,language=language,
                                       lock=str(procs.default_lock_path(ROOT))))
        self.s=cs.Session.attach(ui_check=True);self.sessions+=1
        self.report['phases'].append({'phase':'restart_setup' if reuse else 'initial_setup','language':language,
                                     'wall_s':round(time.monotonic()-started,3),'capture_timings':state.get('timings',{})})
        self.save()
    def state(self):return self.one(f'myvillage cultivation fate status {self.s.username}','FATES')
    def hp(self,target=None):return self.number(f'data get entity {target or self.s.username} Health')
    def maximum(self):return self.number(f'attribute {self.s.username} minecraft:generic.max_health get')
    def heal(self):self.rc(f'effect give {self.s.username} minecraft:instant_health 1 10 true');time.sleep(1.1)
    def reset(self):self.rc(f'myvillage cultivation fate reset {self.s.username}');time.sleep(.3)
    def choose(self,*short_ids):
        self.reset();ids={'myvillage:'+id for id in short_ids}
        for _ in range(80):
            self.rc(f'myvillage cultivation fate roll {self.s.username}');time.sleep(.25);s=self.state()
            if ids<=set(s.get('candidates',[])):
                mask=sum(1<<i for i,id in enumerate(s['candidates']) if id in ids)
                self.rc(f'myvillage cultivation fate confirm {self.s.username} {mask}');time.sleep(.3)
                result=self.state()
                if not result.get('confirmed') or set(result.get('selected',[]))!=ids:raise RuntimeError('Fixture confirmation rejected')
                return result
        raise RuntimeError('Requested fixture not drawn within 80 rounds')
    def click(self,x,y):self.s.game.xdo('mousemove',str(int(x)),str(int(y)),'click','1');time.sleep(.35)
    def bars(self):
        """Read the actual painted active-button accent strips; no game state injection."""
        g=self.s.game;g.xdo('mousemove','0','0');time.sleep(.15);raw=g.grab_raw();w,h=g.size
        result=[]
        for x in range(130,w-20):
            if x>0 and any(raw[(y*w+x-1)*3:(y*w+x-1)*3+3]==JADE for y in range(100,h-65)):
                continue
            start=None
            for y in range(90,h-60):
                yes=raw[(y*w+x)*3:(y*w+x)*3+3]==JADE
                if yes and start is None:start=y
                if not yes and start is not None:
                    if y-start>=24:result.append((x,start,y))
                    start=None
        return result
    def panel(self,width=960,height=540):
        g=self.s.game;g.require_ingame('open H');g.key('h');time.sleep(.5)
        # H-frame math: GUI scale two; fifth nav tab has four 21px predecessors.
        gui_h=height//2;top=(gui_h-min(246,gui_h-8))//2
        self.click(55,2*(top+36+4*21+9));time.sleep(.3)
    def close_panel(self):self.s.game.xdo('key','Escape');time.sleep(.3);self.s.game.require_ingame('close H')
    def ui(self):
        started=time.monotonic();before=self.state();self.panel()
        bars=self.bars()
        if not bars:raise RuntimeError('No active H-page buttons painted')
        first=min(bars,key=lambda b:(b[1],b[0]));self.click(first[0]+40,(first[1]+first[2])/2)
        after=self.state();self.check('mapped_reroll',after.get('rolls',0)==before.get('rolls',0)+1 and len(after.get('candidates',[]))==6,after)
        # Select first three candidates by scrolling through rendered left-column action strips.
        bars=self.bars();base=min(x for x,_,_ in bars);pick_x=base+12
        last=-1;offset=0;picked=0
        for _ in range(80):
            rows=[b for b in self.bars() if abs(b[0]-pick_x)<=2 and b[1]+offset>last+8]
            if rows:
                x,y0,y1=min(rows,key=lambda b:b[1]);self.click(x+40,(y0+y1)/2)
                last=y0+offset;picked+=1
                if picked==3:break
            self.s.game.xdo('mousemove','600','300','click','--repeat','2','--delay','70','5');offset+=56;time.sleep(.2)
        if picked!=3:raise RuntimeError('Could not select three painted candidate buttons')
        self.s.game.xdo('mousemove','600','300','click','--repeat','60','--delay','10','4');time.sleep(.5)
        rows=self.bars();top=min(b[1] for b in rows);confirm=max((b for b in rows if abs(b[1]-top)<3),key=lambda b:b[0])
        self.click(confirm[0]+40,(confirm[1]+confirm[2])/2);self.click(confirm[0]+40,(confirm[1]+confirm[2])/2)
        done=self.state();self.check('mapped_three_choice_confirmation',done.get('confirmed') and done.get('selected')==after['candidates'][:3],done)
        window=self.s.game.xdo('search','--name','Minecraft').split()[0]
        subprocess.run(['import','-silent','-window',window,str(self.out/'mapped-confirmed.png')],env=self.s.game.env,check=True,timeout=30)
        self.close_panel()
        self.rc(f'myvillage cultivation fate roll {self.s.username}');time.sleep(.3)
        unchanged=self.state();self.check('confirmed_reroll_rejected',unchanged.get('rolls')==done.get('rolls') and unchanged.get('selected')==done.get('selected'),unchanged)
        self.report['phases'].append({'phase':'mapped_ui','wall_s':round(time.monotonic()-started,3)});self.save()
    def layouts(self,language):
        started=time.monotonic();g=self.s.game;window=g.xdo('search','--name','Minecraft').split()[0]
        shots=[]
        for w,h in ((960,540),(854,480),(640,480)):
            g.xdo('windowsize',window,str(w),str(h));g.xdo('windowmove',window,'0','0');time.sleep(.7)
            g.recover_grab();self.reset();self.rc(f'myvillage cultivation fate roll {self.s.username}');time.sleep(.3)
            self.panel(w,h)
            for kind in ('draft','confirmed'):
                if kind=='confirmed':
                    self.rc(f'myvillage cultivation fate confirm {self.s.username} 7');time.sleep(.4)
                name=f'{language}_{w//2}x{h//2}_{kind}.png'
                subprocess.run(['import','-silent','-window',window,str(self.out/name)],env=g.env,check=True,timeout=30)
                shots.append({'file':name,'expected_gui':[w//2,h//2],'state':kind})
            self.close_panel()
        g.xdo('windowsize',window,'960','540');time.sleep(.5)
        self.report['phases'].append({'phase':'layouts','language':language,'wall_s':round(time.monotonic()-started,3),'images':shots});self.save()
    def target(self,kind='husk',health=100):
        self.rc('kill @e[tag=fate_fixture]')
        self.rc(f'summon minecraft:{kind} .5 -60 3 {{NoAI:1b,PersistenceRequired:1b,Tags:["fate_fixture"]}}');time.sleep(.3)
        self.rc(f'attribute {TARGET} minecraft:generic.max_health base set {health}')
        self.rc(f'attribute {TARGET} minecraft:generic.armor base set 0')
        self.rc(f'data merge entity {TARGET} {{Health:{health}f}}')
        return self.hp(TARGET)
    def attack(self,source='player_attack',amount=10,by=True):
        self.rc(f'damage {TARGET} {amount} minecraft:{source}'+(f' by {self.s.username}' if by else ''));time.sleep(.15)
    def qi(self):
        # Profile diagnostic is JSON; the command is the established service readback.
        reply=self.rc(f'data get entity {self.s.username} "neoforge:attachments"."myvillage:cultivation_profile".current_spiritual_power')
        m=re.search(r'data:\s*(-?\d+)[Ll]?\s*$',reply)
        if not m:raise RuntimeError('No long qi value: '+reply)
        return int(m.group(1))
    def initial(self):
        user=self.s.username
        for cmd in (*scene.world_rules(),*scene.player_setup(user,'myvillage:qingfeng_sword'),
                    'gamerule naturalRegeneration false','myvillage world pause',f'myvillage cultivation awaken {user}',
                    f'myvillage cultivation initiate {user}',f'myvillage cultivation setrealm {user} myvillage:qi_refining myvillage:qi_refining_1'):
            self.rc(cmd)
        self.reset();self.ui();self.layouts('en_us')
        started=time.monotonic()
        self.reset();base=self.maximum();self.heal();hp=self.hp()
        self.choose('robust','lightfoot','bright');maxhp=self.maximum()
        self.check('robust_health_without_free_heal',close(maxhp,base*1.15,.01) and close(self.hp(),hp,.01),{'baseline':base,'maximum':maxhp,'health':self.hp()})
        self.check('lightfoot_speed',close(self.number(f'attribute {user} minecraft:generic.movement_speed get'),.11,.0001),'expected .11')
        self.rc(f'myvillage cultivation setprogress {user} 0');time.sleep(.3)
        self.check('refresh_does_not_stack',close(self.maximum(),maxhp,.01),self.maximum())
        self.choose('burning_star','robust','bright')
        self.check('health_bonuses_compose',close(self.maximum(),base*.95,.01),self.maximum())
        before=self.target();self.attack();self.check('burning_star_damage',close(before-self.hp(TARGET),13,.01),before-self.hp(TARGET))
        self.choose('fire_affinity','robust','bright')
        before=self.target();self.attack('in_fire');self.check('fire_damage_bonus',close(before-self.hp(TARGET),11.5,.01),before-self.hp(TARGET))
        self.heal();before=self.hp();self.rc(f'damage {user} 4 minecraft:in_fire');time.sleep(.7)
        self.check('environment_fire_half',close(before-self.hp(),2,.01),before-self.hp())
        self.choose('unyielding','lightfoot','bright');self.heal();maximum=self.maximum()
        self.rc(f'damage {user} {maximum*.7} minecraft:generic');time.sleep(.7)
        before=self.hp();self.rc(f'damage {user} 2 minecraft:generic');time.sleep(.7)
        self.check('unyielding_low_health_reduction',close(before-self.hp(),1.6,.01),before-self.hp())
        before=self.hp();self.rc(f'damage {user} 2 minecraft:out_of_world');time.sleep(.7)
        self.check('bypass_unmodified',close(before-self.hp(),2,.01),before-self.hp())
        self.rc(f'myvillage cultivation setrealm {user} myvillage:golden_core myvillage:golden_late')
        self.choose('beast_hunter','robust','bright');self.heal();self.rc(f'myvillage cultivation qi set {user} 0')
        before=self.target();self.attack();self.check('hunter_hostile_bonus',close(before-self.hp(TARGET),11.5,.01),before-self.hp(TARGET))
        before=self.target();attack=self.number(f'attribute {user} minecraft:generic.attack_damage get')
        time.sleep(1.1);self.s.game.click();time.sleep(.5)
        self.check('mapped_hunter_melee',close(before-self.hp(TARGET),attack*1.15,.04),{'loss':before-self.hp(TARGET),'attack':attack})
        self.rc('tick freeze')
        try:
            self.rc(f'myvillage cultivation qi set {user} 0');started_kill=time.monotonic();self.attack(amount=1000);power=self.qi();hunter=self.state()
            cap=json.loads((ROOT/'src/main/resources/data/myvillage/myvillage/realm/golden_core.json').read_text())['stages'][-1]['spiritual_power_cap']
            fraction=json.loads((ROOT/'src/main/resources/data/myvillage/myvillage/innate_fate/beast_hunter.json').read_text())['effects']['hunter_recovery']
            expected=int(cap*fraction)
            self.check('hunter_personal_kill_recovers',expected<=power<=expected+math.ceil(time.monotonic()-started_kill)+1 and hunter.get('hunter_ready_at',0)>hunter.get('now',0),{'power':power,'expected_kill_gain':expected,'passive_regen_allowance':math.ceil(time.monotonic()-started_kill)+1,'state':hunter})
            # Advance beyond the cooldown. ServerTick-based passive qi regen continues during tick freeze;
            # bound its small contribution and separately assert no kill-cooldown update.
            self.rc('tick step 50');time.sleep(3)
            self.rc(f'myvillage cultivation qi set {user} 0');neutral_start=time.monotonic();old_ready=self.state().get('hunter_ready_at');before=self.target('iron_golem');self.attack()
            self.check('hunter_neutral_no_bonus',close(before-self.hp(TARGET),10,.01),before-self.hp(TARGET))
            self.attack(amount=1000);power=self.qi()
            self.check('neutral_kill_no_recovery',power<=math.ceil(time.monotonic()-neutral_start)+1 and self.state().get('hunter_ready_at')==old_ready,{'power':power,'ready_at':self.state().get('hunter_ready_at')})
            self.rc(f'myvillage cultivation qi set {user} 0');summon_start=time.monotonic()
            self.target();self.rc('summon minecraft:wolf 8 -60 8 {NoAI:1b,Tags:["fate_pet"]}')
            self.rc(f'damage {TARGET} 1000 minecraft:magic by @e[tag=fate_pet,limit=1] from {user}')
            power=self.qi();self.check('indirect_summon_kill_no_recovery',power<=math.ceil(time.monotonic()-summon_start)+1 and self.state().get('hunter_ready_at')==old_ready,{'power':power,'ready_at':self.state().get('hunter_ready_at')})
            self.rc('kill @e[tag=fate_pet]')
        finally:self.rc('tick unfreeze')
        self.choose('alchemist_descendant','robust','lightfoot');self.heal();maximum=self.maximum()
        self.rc(f'damage {user} {maximum*.75} minecraft:generic');time.sleep(.4);begun=self.state();before=self.hp();time.sleep(5.3);after=self.hp()
        self.check('alchemist_recovery_actual',begun.get('recovery_ready_at',0)>begun.get('now',0) and after>before and close(after,maximum*.55,.08),{'start':begun,'before':before,'after':after,'max':maximum})
        ready=self.state().get('recovery_ready_at');self.rc(f'damage {user} {maximum*.3} minecraft:generic');time.sleep(.3)
        self.check('alchemist_cooldown_not_retriggered',self.state().get('recovery_ready_at')==ready and self.state().get('healing_ticks',0)==0,self.state())
        before=self.state();self.rc(f'kill {user}');time.sleep(2);after=self.state()
        self.check('death_keeps_choices_and_cooldown',after.get('selected')==before.get('selected') and after.get('recovery_ready_at')==ready and after.get('confirmed'),after)
        self.rc(f'tp {user} .5 -60 .5 0 0');self.heal()
        self.report['before_restart']={'state':self.state(),'maximum':self.maximum(),'health':self.hp()}
        self.rc('save-all flush');self.report['phases'].append({'phase':'actual_effects','wall_s':round(time.monotonic()-started,3)});self.save()
    def restored(self):
        old=self.report['before_restart'];now=self.state()
        keys=('selected','confirmed','rolls','recovery_ready_at','hunter_ready_at')
        self.check('restart_retains_fates_and_timers',all(now.get(k)==old['state'].get(k) for k in keys),{'before':old['state'],'after':now})
        self.check('restart_attributes_do_not_stack',close(self.maximum(),old['maximum'],.01),{'before':old['maximum'],'after':self.maximum()})
        self.layouts('zh_cn')

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'out/preview/innate_fates');args=p.parse_args(argv)
    if not procs.lock_is_free(procs.default_lock_path(ROOT)):raise SystemExit('Heavy-work lock held')
    evidence=FateEvidence(args.out)
    try:
        evidence.start();evidence.initial();evidence.stop();evidence.start(reuse=True,language='zh_cn');evidence.restored()
    except Exception as e:
        evidence.report['failures'].append(f'{type(e).__name__}: {e}');raise
    finally:
        try:
            if evidence.s is not None or cs.supervisor_alive(cs.read_state()):evidence.stop()
        finally:
            evidence.report['finished_utc']=dt.datetime.now(dt.timezone.utc).isoformat()
            evidence.report['pass']=not evidence.report['failures'] and bool(evidence.report['checks']) and all(c['pass'] for c in evidence.report['checks']);evidence.save()
    return 0 if evidence.report['pass'] else 1
if __name__=='__main__':raise SystemExit(main())
