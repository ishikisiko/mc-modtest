#!/usr/bin/env python3
"""Existing requested Colab: mapped weapon casts, range/occlusion and two-client visual evidence.

Commands only prepare/read isolated targets and control deterministic boundary time.
Showcase videos run at normal 20 TPS; no command calls an attack or spell action.
"""
from pathlib import Path
import argparse,json,math,os,re,subprocess,sys,time,traceback
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.locked_sword_evidence import LockedEvidence,IDS,KEYS
from tools.combat_capture import session as cs,scene
from tools.combat_capture.capture import ffmpeg_record_argv
from tools.alchemy_quality_evidence import SecondClient,read_data

CASES=('weapon_ranges','locked_ranges','shock_boundaries','army_capacity','wave_and_basic','two_players_and_visuals')
WEAPONS={'sword':'qingfeng_sword','spear':'lingxiao_spear','fist':'xuantie_gauntlet'}

class AmplificationEvidence(LockedEvidence):
    def __init__(self,out,cases,attach=False):
        super().__init__(out,cases,attach)
        self.weapons=json.loads((ROOT/'tools/advanced_arts/arts.json').read_text())['arts']
        self.moves={m['id']:m for p in (ROOT/'src/main/resources/data/myvillage/combat/style').glob('*.json') for m in json.loads(p.read_text())['moves']}
        self.report.update(kind='weapon_art_amplification',videos=[],method=__doc__,
                           learning='administrator learned fixtures; unchanged learning not rerun',owner_audio='not_verified')
    def start(self):
        super().start()
        for art in self.weapons:self.rc(f'myvillage cultivation learn {self.s.username} {art["id"]}')
        self.rc('forceload add -48 -32 48 80')
        self.poll(lambda:self.rc('execute if loaded 0 -61 64 run time query gametime'),lambda s:'The time is' in s,label='extended test area loaded')
        self.rc('fill -32 -61 -24 32 -61 72 minecraft:stone')
    def log(self,offset=0):
        return '\n'.join(s for s in super().log(offset).splitlines() if any(k in s for k in ('ADVANCED_ART ','LOCKED_SWORD ','SWORD_QI ','DODGE ','COMBAT ')))
    def clean(self):
        self.rc('kill @e[tag=amplification_fixture]');self.rc('kill @e[tag=locked_fixture]')
        self.rc('team remove amplification_allies')
        self.rc('fill -3 -60 14 -3 -55 22 minecraft:air')
    def ready(self,weapon='qingfeng_sword'):
        super().ready();self.rc(f'item replace entity {self.s.username} hotbar.0 with myvillage:{weapon}');self.step(12)
    def spawn(self,tag,x,z,y=-60):
        self.rc(f'summon minecraft:husk {x} {y} {z} '+
                '{NoAI:1b,PersistenceRequired:1b,Silent:1b,Health:1000f,Tags:["amplification_fixture","'+tag+'"],attributes:[{id:"minecraft:generic.max_health",base:1000d},{id:"minecraft:generic.armor",base:0d},{id:"minecraft:generic.knockback_resistance",base:1d}]}')
        self.step(2);return '@e[tag='+tag+',limit=1]'
    def art_key(self,art):
        self.step(12);mark=self.offset();before=self.power();start=self.now()
        self.key('z' if art['slot']==0 else 'c')
        log=self.poll(lambda:self.log(mark),lambda s:'result=start art='+art['id'] in s,label='mapped '+art['id'])
        self.check(art['id']+'_one_payment',before-self.power()==art['qi_cost'],{'before':before,'after':self.power(),'log':log})
        return mark,start
    def finish_art(self,art,mark):
        for _ in range(35):
            if 'result=stop art='+art['id'] in self.log(mark):break
            self.step(8)
        log=self.log(mark)
        self.check(art['id']+'_completed','result=stop art='+art['id']+' reason=COMPLETED' in log,log)
        self.report.setdefault('cast_logs',[]).append(log);self.save();return log
    def weapon_ranges(self):
        for art in self.weapons:
            self.clean();self.ready(WEAPONS[art['family']])
            reach=max(p['reach'] for p in art['phases']);step=sum(p['step_distance'] for p in art['phases'])
            inside=self.spawn('range_inside',.5,.5+step+reach-.4)
            outside=self.spawn('range_outside',.5,.5+step+reach+1.5)
            before=[self.hp(inside),self.hp(outside)];mark,start=self.art_key(art)
            self.finish_art(art,mark)
            after=[self.hp(inside),self.hp(outside)]
            self.check(art['id']+'_enlarged_reach',after[0]<before[0],{'before':before,'after':after,'reach':reach,'steps':step})
            self.check(art['id']+'_outside_untouched',after[1]==before[1],{'before':before,'after':after})
        # Enlarged ring must still stop at the same solid collision and friendship policies.
        art=next(a for a in self.weapons if a['id'].endswith('jingang_zhen'))
        self.clean();self.ready('xuantie_gauntlet')
        wall=self.spawn('ring_wall',.5,7);ally=self.spawn('ring_ally',4.5,.5)
        self.rc('fill -2 -60 4 2 -55 4 minecraft:stone');self.rc('team add amplification_allies')
        self.rc(f'team join amplification_allies {self.s.username}');self.rc(f'team join amplification_allies {ally}')
        before=[self.hp(wall),self.hp(ally)];mark,_=self.art_key(art);self.finish_art(art,mark)
        self.check('enlarged_ring_wall_and_ally',[self.hp(wall),self.hp(ally)]==before,before)
        self.rc('fill -2 -60 4 2 -55 4 minecraft:air');self.clean()
    def locked_ranges(self):
        for ident in IDS:
            self.clean();self.ready();reach=self.arts[ident]['lock']['range']
            target=self.spawn('lock_inside',.5,.5+reach-.6);before=self.hp(target)
            mark,start=self.cast(ident);self.finish_cast(ident,mark,start)
            self.check(ident+'_enlarged_lock',self.hp(target)<before,{'range':reach,'before':before,'after':self.hp(target)})
            self.clean();self.ready();target=self.spawn('lock_outside',.5,.5+reach+1)
            before=self.power();mark=self.offset();self.key(KEYS[ident]);time.sleep(.4)
            self.check(ident+'_outside_refused','reason=no_target' in self.log(mark) and self.power()==before,self.log(mark))
        self.clean();self.ready();target=self.spawn('lost_target',.5,18)
        mark,start=self.cast('taibai_jianjing');before=self.hp(target)
        self.rc('fill -3 -60 9 3 -54 9 minecraft:stone');log=self.finish_cast('taibai_jianjing',mark,start)
        self.check('taibai_lost_sight_cancels_entire_impact',self.hp(target)==before and 'result=lost' in log,log)
        self.rc('fill -3 -60 9 3 -54 9 minecraft:air');self.clean()
    def shock_boundaries(self):
        self.clean();self.ready();r=self.arts['taibai_jianjing']['lock']['radius']
        primary=self.spawn('shock_primary',.5,18);inside=self.spawn('shock_inside',.5+r-.4,18)
        outside=self.spawn('shock_outside',.5+r+.8,18);wall=self.spawn('shock_wall',-5.5,18)
        ally=self.spawn('shock_ally',3,15)
        self.rc('fill -3 -60 14 -3 -55 22 minecraft:stone');self.rc('team add amplification_allies')
        self.rc(f'team join amplification_allies {self.s.username}');self.rc(f'team join amplification_allies {ally}')
        targets=[primary,inside,outside,wall,ally];before=[self.hp(t) for t in targets]
        mark,start=self.cast('taibai_jianjing');log=self.finish_cast('taibai_jianjing',mark,start)
        after=[self.hp(t) for t in targets];loss=[a-b for a,b in zip(before,after)]
        self.check('taibai_primary_and_secondary',loss[0]>0 and loss[1]>0,{'loss':loss,'log':log})
        scale=self.arts['taibai_jianjing']['lock']['splash_multiplier']
        self.check('taibai_primary_once_secondary_scale',abs(loss[1]-loss[0]*scale)<.01 and log.count('result=strike art=myvillage:taibai_jianjing')==2,{'loss':loss,'scale':scale,'log':log})
        self.check('taibai_outside_wall_ally_untouched',loss[2:]==[0,0,0],{'loss':loss})
        self.clean()
    def army_capacity(self):
        self.clean();self.ready();primary=self.spawn('army_center',.5,18)
        targets=[primary];r=self.arts['tiangang_jiandian']['lock']['radius']*.85
        for i in range(12):
            a=2*math.pi*i/12;targets.append(self.spawn('army_'+str(i),.5+math.sin(a)*r,18+math.cos(a)*r))
        self.rc(f'tp {self.s.username} .5 -60 .5 0 2.1')
        before=[self.hp(t) for t in targets];mark,start=self.cast('tiangang_jiandian');log=self.finish_cast('tiangang_jiandian',mark,start)
        losses=[a-self.hp(t) for a,t in zip(before,targets)]
        self.check('array_twelve_of_thirteen_targets',sum(v>0 for v in losses)==12,losses)
        pulses={i:len(re.findall(r'pulse='+str(i)+r' target=',log)) for i in range(7)}
        self.check('array_each_pulse_bounded',set(pulses.values())=={12},pulses);self.clean()
    def wave_and_basic(self):
        for charge,near,far in [(0,25,33),(255,40,48)]:
            self.clean();self.ready();inside=self.spawn('wave_inside',.5,near);outside=self.spawn('wave_outside',.5,far)
            before=[self.hp(inside),self.hp(outside)];mark=self.offset()
            self.s.game.xdo('mousedown','3')
            self.poll(lambda:self.log(mark),lambda s:'result=charging skill=myvillage:jianqi_jue' in s,label='wave charging')
            if charge:self.step(20)
            self.s.game.xdo('mouseup','3')
            self.report['inputs'].append({'method':'mapped right mouse press/release','charge':charge,'server_charge_ticks':20 if charge else 0})
            log=self.poll(lambda:self.log(mark),lambda s:'result=released skill=myvillage:jianqi_jue' in s,label='wave release')
            self.check('wave_charge_'+str(charge),'charge='+str(charge)+' ' in log,log)
            self.step(60);after=[self.hp(inside),self.hp(outside)]
            self.check('wave_extended_range_and_boundary_'+str(charge),after[0]<before[0] and after[1]==before[1],{'before':before,'after':after,'log':self.log(mark)})
        self.clean();self.ready();target=self.spawn('basic_target',.5,2.5);before=self.hp(target)
        self.s.game.click(.06);self.report['inputs'].append({'method':'mapped left click','seconds':.06});self.step(35)
        self.check('basic_attack_after_enlarged_arts',self.hp(target)<before,{'before':before,'after':self.hp(target)});self.clean()
    def observer(self,guest,locked=False):
        self.rc(f'gamemode spectator {guest.username}')
        self.rc(f'tp {guest.username} '+('16 -50 -9 31 13' if locked else '13 -50 -12 37 28'))
        time.sleep(.35)
    def visuals(self,guest):
        self.observer(guest);self.key('F1',guest.game)
        for art in self.weapons:
            self.clean();self.ready(WEAPONS[art['family']]);self.observer(guest)
            # A target beyond the earlier stages avoids hit-stop shifting the final-stage capture tick.
            reach=max(p['reach'] for p in art['phases']);distance=sum(p['step_distance'] for p in art['phases'])
            self.spawn('show_target',.5,.5+distance+reach-.4)
            mark,start=self.art_key(art)
            phases=art['phases'];ticks=sum(self.moves[p['move']]['total_ticks'] for p in phases[:-1])+self.moves[phases[-1]['move']]['active_ticks'][0]+1
            self.step(ticks);self.capture(art['id'].split(':')[1]+'_observer',guest.game)
            self.capture(art['id'].split(':')[1]+'_caster');self.finish_art(art,mark)
        for ident in IDS:
            self.clean();self.ready();self.rc(f'tp {self.s.username} .5 -60 -6 0 -8');self.observer(guest,True)
            self.spawn('show_target',.5,18);mark,start=self.cast(ident);lock=self.arts[ident]['lock']
            self.step(max(1,lock['first_strike_ticks']//2));self.capture(ident+'_warning',guest.game)
            self.step(start+lock['first_strike_ticks']-self.now());self.capture(ident+'_impact',guest.game)
            self.capture(ident+'_caster');self.finish_cast(ident,mark,start)
        # Real-time footage is separate from frozen boundary screenshots.
        self.clean()
        for ident in IDS:
            self.ready();self.rc(f'tp {self.s.username} .5 -60 -6 0 -8');self.observer(guest,True);self.spawn('video_target',.5,18)
            file=self.out/(ident+'.mp4');seconds=7 if ident=='tiangang_jiandian' else 5
            with (self.out/(ident+'-ffmpeg.log')).open('w') as log:
                rec=subprocess.Popen(ffmpeg_record_argv(guest.game.display,file,seconds,fps=20,size=(960,540)),stdout=log,stderr=subprocess.STDOUT)
                time.sleep(.5);self.rc('tick rate 20');self.rc('tick unfreeze');mark=self.offset();self.key(KEYS[ident]);rec.wait(timeout=seconds+25);self.freeze()
            self.check(ident+'_normal_time_video',rec.returncode==0 and file.stat().st_size>10000 and 'result=start art=myvillage:'+ident in self.log(mark),{'file':file.name,'bytes':file.stat().st_size,'log':self.log(mark)})
            self.report['videos'].append({'file':file.name,'seconds':seconds,'server_tick_rate':20,'input':'mapped key','view':'second native client'});self.save();self.clean()
        self.key('F1',guest.game)
    def two_players_and_visuals(self):
        self.clean();self.rc('tick unfreeze')
        with SecondClient(self) as guest:
            self.guest=guest;self.position(guest.game);self.configure(guest.username)
            self.rc(f'myvillage cultivation learn {guest.username} myvillage:liuyun_bu')
            self.rc(f'tp {guest.username} 5 -60 12 180 0')
            if scene.parse_combat_mode(self.rc(f'data get entity {guest.username} "neoforge:attachments"'))!='cultivation':self.key('r',guest.game)
            time.sleep(.5);self.freeze()
            for dodge in (False,True):
                self.clean();self.ready();primary=self.spawn('pvp_primary',.5,12)
                self.rc(f'tp {guest.username} 5 -60 12 180 0');self.rc(f'effect give {guest.username} minecraft:instant_health 1 10 true');self.step(10)
                before=self.hp(guest.username);primary_before=self.hp(primary);mark,start=self.cast('taibai_jianjing')
                if dodge:
                    self.step(self.arts['taibai_jianjing']['lock']['first_strike_ticks']-2);self.key('Alt_L',guest.game);self.step(2)
                log=self.finish_cast('taibai_jianjing',mark,start);after=self.hp(guest.username)
                self.check('secondary_player_dodges' if dodge else 'secondary_player_hit',after==before if dodge else after<before,{'before':before,'after':after,'log':log})
                self.check('primary_remains_independent_of_secondary_dodge',self.hp(primary)<primary_before,log)
                if dodge:self.check('secondary_dodge_actual_window','fraction=0.0' in log,log)
            self.visuals(guest)
            self.check('two_clients_still_connected',self.s.username in self.rc('list') and guest.username in self.rc('list'),self.rc('list'))
            self.report['multiplayer']='two actual clients: independent primary/splash damage, splash dodge and observed weapon/locked cues'
            self.rc('tick unfreeze');self.guest=None
        self.freeze()
    def run(self):
        self.start()
        for case in self.cases:
            getattr(self,case)();self.report['completed'].append(case);self.report['unrun'].remove(case);self.save()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--cases',nargs='+',choices=CASES,default=list(CASES));p.add_argument('--attach',action='store_true');p.add_argument('--keep-game-on-failure',action='store_true');args=p.parse_args()
    e=AmplificationEvidence(args.out,args.cases,args.attach)
    try:e.run()
    except BaseException as error:e.report['failures'].append(repr(error));traceback.print_exc()
    finally:
        try:
            if e.s:
                e.rc('tick unfreeze')
                if e.report['failures'] and args.keep_game_on_failure:e.report['game_retained_for_debug']=True
                else:e.stop()
            e.report['termination']='confirmed' if cs.read_state().get('phase')=='stopped' else 'not_confirmed'
        except BaseException as error:e.report['failures'].append('cleanup: '+repr(error))
        e.report['passed']=not e.report['failures'] and not e.report['unrun'] and all(c['pass'] for c in e.report['checks']);e.save()
    raise SystemExit(0 if e.report['passed'] else 1)
