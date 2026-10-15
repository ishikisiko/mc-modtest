#!/usr/bin/env python3
"""First-batch fates: mapped stele awakening, explicit fixtures, actual effects and restart.

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

class FateEvidence(Evidence):
    def __init__(self,out):
        super().__init__(out)
        self.report.update(kind='innate_fates_first_batch',owner_verdict='not_verified',multiplayer='not_verified',
                           method='mapped stele right click and awakening/H-page input; RCON fixtures/readback only; real client; no DevBridge')
        self.report.pop('natural_encounter_density',None)
    def start(self,reuse=False,language='en_us'):
        started=time.monotonic()
        state=cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),reuse_world=reuse,language=language,
                                       lock=str(procs.default_lock_path(ROOT))))
        self.s=cs.Session.attach(ui_check=True);self.sessions+=1
        self.report['phases'].append({'phase':'restart_setup' if reuse else 'initial_setup','language':language,
                                     'wall_s':round(time.monotonic()-started,3),'capture_timings':state.get('timings',{})})
        self.save()
    @staticmethod
    def decoded_state(value):
        # FateState.CODEC omits optional fields equal to these defaults.
        return {'revision':0,'rolls':0,'candidates':[],'locked':'','selected':[],'confirmed':False,
                'recovery_ready_at':0,'healing_ticks':0,'healing_per_tick':0.0,'hunter_ready_at':0,**value}
    def state(self):
        return self.decoded_state(self.one(f'myvillage cultivation fate status {self.s.username}','FATES'))
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
    def panel(self,width=960,height=540):
        g=self.s.game;g.require_ingame('open H');g.key('h');time.sleep(.5)
        # H-frame math: GUI scale two; fifth nav tab has four 21px predecessors.
        gui_h=height//2;top=(gui_h-min(246,gui_h-8))//2
        self.click(55,2*(top+36+4*21+9));time.sleep(.3)
    def close_panel(self):self.s.game.xdo('key','Escape');time.sleep(.3);self.s.game.require_ingame('close H')
    def root(self):
        reply=self.rc(f'data get entity {self.s.username} "neoforge:attachments"."myvillage:cultivation_profile".spiritual_root')
        return reply.split('data:',1)[1].strip() if 'data:' in reply else None
    @staticmethod
    def draft(state):
        return {k:state.get(k) for k in ('revision','rolls','candidates','locked','selected','confirmed')}
    def capture(self,name):
        g=self.s.game;g.xdo('mousemove','0','0');time.sleep(.15)
        window=g.xdo('search','--name','Minecraft').split()[0]
        subprocess.run(['import','-silent','-window',window,str(self.out/name)],env=g.env,check=True,timeout=30)
    def geometry(self):
        """Read actual rendered hit-target diagnostics after a mapped-opened screen."""
        expected=tuple(n//2 for n in self.s.game.size)
        current=None;rects={}
        for line in self.s.game.client_log.read_text(errors='replace').splitlines():
            header=re.search(r'AWAKENING_UI gui=(\d+)x(\d+) step=',line)
            if header:
                current=tuple(map(int,header.groups()));rects={}
            control=re.search(r'AWAKENING_UI control=(\w+) x=(-?\d+) y=(-?\d+) w=(\d+) h=(\d+)',line)
            if control and current==expected:rects[control.group(1)]=tuple(map(int,control.groups()[1:]))
        if current!=expected or not rects:
            raise RuntimeError(f'No current rendered awakening controls for GUI {expected}: {current}')
        return rects
    def press(self,control):
        x,y,w,h=self.geometry()[control];self.click(2*(x+w/2),2*(y+h/2));time.sleep(.2)
    def stele_fixture(self):
        user=self.s.username
        for cmd in (f'item replace entity {user} weapon.mainhand with minecraft:air',
                    f'item replace entity {user} weapon.offhand with minecraft:air',
                    'setblock 0 -60 2 minecraft:stone','setblock 0 -59 2 myvillage:spirit_testing_stele',
                    f'tp {user} .5 -60 .5 0 3.4'):
            self.rc(cmd)
        time.sleep(.4)
    def open_stele(self):
        self.stele_fixture();g=self.s.game;g.require_ingame('right-click testing stele')
        g.xdo('click','3');time.sleep(.7)
        state=self.state()
        if state.get('awakening_session',0)<=0 or g.pointer_grabbed() is not False:
            raise RuntimeError('Mapped right click did not open a live stele session: '+str(state))
        return state
    def leave_stele(self):
        self.s.game.xdo('key','Escape');time.sleep(.4)
        self.s.game.require_ingame('close awakening')
    def valid_slots(self,state):
        from itertools import combinations
        definitions={"myvillage:"+p.stem:json.loads(p.read_text()) for p in (ROOT/'src/main/resources/data/myvillage/myvillage/innate_fate').glob('*.json')}
        for slots in combinations(range(len(state['candidates'])),3):
            values=[definitions[state['candidates'][i]] for i in slots]
            if sum(d['cost'] for d in values)<=state['budget'] and sum(d['tier']>=3 for d in values)<=1 and len({d['group'] for d in values})==3:
                return slots
        raise RuntimeError('Server draft has no legal triple')
    def mapped_confirm(self,state):
        slots=self.valid_slots(state)
        for slot in slots:self.press('card_'+str(slot))
        self.press('confirm');self.capture(f'mapped-permanent-warning-{self.sessions}-{self.s.game.size[0]}x{self.s.game.size[1]}-{state["revision"]}.png');self.press('confirm')
        result=self.state();expected=[state['candidates'][i] for i in slots]
        self.check('mapped_three_choice_confirmation',result.get('confirmed') and result.get('selected')==expected,result)
        return result
    def ui(self):
        started=time.monotonic();self.reset();self.rc(f'myvillage cultivation clearroot {self.s.username}')
        self.check('fresh_fixture_has_no_root',self.root() is None,'administrator reset/clearroot fixture only')
        before=self.state();opened=self.open_stele();root=self.root()
        self.check('mapped_stele_awaken_and_initial_draft',root is not None and len(opened.get('candidates',[]))==6 and opened.get('rolls')==before.get('rolls',0)+1,opened)
        self.capture('mapped-root-reveal.png');self.leave_stele()
        self.check('close_clears_session',self.state().get('awakening_session')==0,self.state())
        resumed=self.open_stele()
        self.check('reopen_resumes_draft_and_root',self.draft(resumed)==self.draft(opened) and self.root()==root,resumed)
        self.press('continue');self.press('card_0');self.press('lock');locked=self.state()
        self.check('mapped_lock',locked.get('locked')==opened['candidates'][0],locked)
        self.press('reroll');rolled=self.state()
        self.check('mapped_reroll_preserves_lock_and_root',rolled.get('rolls')==opened.get('rolls')+1 and rolled.get('locked') in rolled.get('candidates',[]) and self.root()==root,rolled)
        self.press('lock');unlocked=self.state()
        self.check('mapped_unlock',unlocked.get('locked','')=='',unlocked)
        done=self.mapped_confirm(unlocked);self.capture('mapped-confirmed.png');self.leave_stele()
        fixed=self.open_stele();self.press('continue');self.capture('mapped-confirmed-reopen.png');self.leave_stele()
        self.check('confirmed_reopen_keeps_choices_and_root',self.draft(fixed)==self.draft(done) and self.root()==root,fixed)
        self.panel();self.capture('mapped-h-readonly.png')
        # Click the former action area and candidate region; neither may mutate the draft.
        self.click(400,210);self.click(400,300);self.click(700,210)
        readonly=self.state();self.close_panel()
        self.check('h_page_readonly',self.draft(readonly)==self.draft(done) and readonly.get('awakening_session')==0,readonly)
        self.session_rejections(root)
        self.report['phases'].append({'phase':'mapped_stele_ui','wall_s':round(time.monotonic()-started,3)});self.save()
    def session_rejections(self,root):
        user=self.s.username
        for kind in ('out_of_range','removed_block','death','close'):
            self.reset();opened=self.open_stele();self.press('continue');before=self.draft(self.state())
            if kind=='out_of_range':self.rc(f'tp {user} .5 -60 -5 0 0')
            elif kind=='removed_block':self.rc('setblock 0 -59 2 minecraft:air')
            elif kind=='death':self.rc(f'kill {user}')
            else:self.leave_stele()
            time.sleep(2 if kind=='death' else .7)
            # If the screen remains, submit its real mapped reroll; otherwise a mapped self command
            # exercises the same session-gated intent path. No crafted payload or screen-open command.
            if kind=='removed_block' and self.s.game.pointer_grabbed() is False:
                self.press('reroll');self.leave_stele();method='mapped screen reroll'
            else:
                if self.s.game.pointer_grabbed() is False:self.s.game.xdo('key','Escape');time.sleep(.4)
                self.s.game.recover_grab();self.s.game.chat('/myvillage cultivation fate roll');method='mapped self roll command after lifecycle invalidation'
            after=self.state()
            self.check(kind+'_session_rejects',after.get('awakening_session')==0 and self.draft(after)==before and self.root()==root,
                       {'before':before,'after':after,'method':method,'old_token':opened.get('awakening_session')})
        self.stele_fixture()
    def tooltip(self,state,language):
        """Hover the largest localized candidate explanation without changing any selection."""
        translations=json.loads((ROOT/f'src/main/resources/assets/myvillage/lang/{language}.json').read_text())
        def explanation_size(identifier):
            definition=json.loads((ROOT/'src/main/resources/data/myvillage/myvillage/innate_fate'/
                                   (identifier.split(':',1)[1]+'.json')).read_text())
            description=translations.get(definition['description_key'],definition['description_key'])
            effects=definition.get('effects',{})
            return len(description)+sum(len(translations.get('fate.myvillage.effect.'+key,key))+18 for key in effects),len(effects)
        slot=max(range(len(state['candidates'])),key=lambda i:explanation_size(state['candidates'][i]))
        before=self.draft(self.state());root=self.root();g=self.s.game
        x,y,w,h=self.geometry()['card_'+str(slot)]
        g.xdo('mousemove',str(int(2*(x+w/2))),str(int(2*(y+h/2))));time.sleep(1.0)
        gui=[n//2 for n in g.size];name=f'{language}_{gui[0]}x{gui[1]}_tooltip.png'
        # Preserve the hover position: capture() deliberately moves the cursor off controls.
        window=g.xdo('search','--name','Minecraft').split()[0]
        subprocess.run(['import','-silent','-window',window,str(self.out/name)],env=g.env,check=True,timeout=30)
        after=self.state()
        self.check(f'tooltip_readonly_{language}_{gui[0]}x{gui[1]}',self.draft(after)==before and self.root()==root,
                   {'candidate':state['candidates'][slot],'before':before,'after':self.draft(after)})
        return {'file':name,'expected_gui':gui,'state':'draft_tooltip','candidate':state['candidates'][slot],
                'method':'mapped mouse hover over rendered card','owner_verdict':'not_verified'}
    def layouts(self,language):
        started=time.monotonic();g=self.s.game;window=g.xdo('search','--name','Minecraft').split()[0];shots=[];tooltips=[]
        for w,h in ((960,540),(854,480),(640,480)):
            g.xdo('windowsize',window,str(w),str(h));g.xdo('windowmove',window,'0','0');time.sleep(.7)
            g.size=(w,h);g.recover_grab();self.reset();state=self.open_stele()
            for kind in ('root','draft','confirmed'):
                if kind=='draft':self.press('continue')
                elif kind=='confirmed':self.mapped_confirm(state)
                name=f'{language}_{w//2}x{h//2}_{kind}.png';self.capture(name)
                shots.append({'file':name,'expected_gui':[w//2,h//2],'state':kind,'owner_verdict':'not_verified'})
                if kind=='draft':tooltips.append(self.tooltip(state,language))
            self.leave_stele()
        g.xdo('windowsize',window,'960','540');g.size=(960,540);time.sleep(.5)
        self.report['phases'].append({'phase':'awakening_layouts','language':language,'wall_s':round(time.monotonic()-started,3),'images':shots,'tooltips':tooltips});self.save()
    def before_ui_restart(self):
        # Keep a real mapped-opened, confirmed session active at shutdown. It must not persist.
        state=self.open_stele();self.press('continue');self.capture('before-restart-confirmed-session.png')
        self.check('restart_fixture_confirmed_session',state.get('confirmed') and state.get('awakening_session',0)>0,state)
        self.report['before_stele_restart']={'root':self.root(),'draft':self.draft(state),
                                             'active_session':state.get('awakening_session')}
        self.rc('save-all flush');self.save()
    def after_ui_restart(self):
        # This readback must precede the next layout fixture's fate reset.
        before=self.report['before_stele_restart'];state=self.state();root=self.root()
        self.check('restart_keeps_root_and_confirmed_draft',root==before['root'] and self.draft(state)==before['draft'],
                   {'before':before,'after':{'root':root,'draft':self.draft(state)}})
        self.check('restart_discards_ephemeral_session',before['active_session']>0 and state.get('awakening_session')==0,
                   {'before_session':before['active_session'],'after_session':state.get('awakening_session')})
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
                    'gamerule naturalRegeneration false','myvillage world pause'):
            self.rc(cmd)
        self.ui();self.layouts('en_us')
        for cmd in ('setblock 0 -59 2 minecraft:air','setblock 0 -60 2 minecraft:air',f'tp {user} .5 -60 .5 0 0',f'myvillage cultivation initiate {user}',f'myvillage cultivation setrealm {user} myvillage:qi_refining myvillage:qi_refining_1',f'item replace entity {user} weapon.mainhand with myvillage:qingfeng_sword'):
            self.rc(cmd)
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
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'out/preview/innate_fates');p.add_argument('--ui-only',action='store_true',help='mapped stele/session checks, save/restart persistence and bilingual layouts; omit existing effects suite');args=p.parse_args(argv)
    if not procs.lock_is_free(procs.default_lock_path(ROOT)):raise SystemExit('Heavy-work lock held')
    if (args.out/'evidence.json').exists():
        args.out=args.out/('attempt-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
    print('Evidence directory: '+str(args.out),flush=True)
    evidence=FateEvidence(args.out)
    evidence.report['scope']='stele_ui_and_restart' if args.ui_only else 'stele_ui_and_existing_effects_restart'
    if args.ui_only:evidence.report['kind']='stele_awakening_ui_restart'
    try:
        evidence.start()
        if args.ui_only:
            for cmd in (*scene.world_rules(),*scene.player_setup(evidence.s.username,'minecraft:air'),'gamerule naturalRegeneration false','myvillage world pause'):
                evidence.rc(cmd)
            evidence.ui();evidence.layouts('en_us');evidence.before_ui_restart();evidence.stop();evidence.start(reuse=True,language='zh_cn');evidence.after_ui_restart();evidence.layouts('zh_cn')
        else:
            evidence.initial();evidence.stop();evidence.start(reuse=True,language='zh_cn');evidence.restored()
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
