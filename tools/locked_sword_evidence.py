#!/usr/bin/env python3
"""Colab high-RAM only: real mapped H bindings, casts and two-client dodge decisions.

RCON prepares isolated fixtures and controls server time; it never invokes a cast
or dodge. The VM is deliberately retained; only owned Minecraft processes stop.
"""
from pathlib import Path
import argparse,json,os,re,secrets,sys,time,traceback
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.realm_stats_evidence import Evidence
from tools.combat_capture import session as cs,scene,procs
from tools.alchemy_quality_evidence import SecondClient,read_data
from tools.ink_hub_evidence import bounds,tile_center,window_geometry

CASES=('bindings_and_three_casts','refusals_and_lifecycle','two_client_dodge','sword_qi_regression')
IDS=('gengjin_jianjue','tiangang_jiandian','taibai_jianjing')
KEYS=dict(zip(IDS,('i','j','k')))

class LockedEvidence(Evidence):
    def __init__(self,out,cases,attach=False):
        if out.exists() and any(out.iterdir()):raise ValueError('Retain previous evidence: '+str(out))
        super().__init__(out);self.language='zh_cn';self.cases=cases;self.opened=False;self.guest=None;self.attach=attach
        self.arts={a['id'].split(':')[1]:a for a in json.loads((ROOT/'src/main/resources/data/myvillage/combat/locked_sword_arts.json').read_text())['arts']}
        self.defs={p.stem:json.loads(p.read_text()) for p in (ROOT/'src/main/resources/data/myvillage/myvillage/technique').glob('*.json')}
        self.lang=json.loads((ROOT/'src/main/resources/assets/myvillage/lang/zh_cn.json').read_text())
        self.report.update(kind='locked_sword_arts',provider='Colab',runtime_retained=True,selected=list(cases),completed=[],unrun=list(cases),
                           inputs=[],screenshots=[],owner_appearance='not_verified',owner_feel='not_verified',owner_balance='not_verified',owner_fps='not_verified',
                           method=__doc__,source_commit=json.loads((ROOT/'source-manifest.json').read_text())['commit'])
    def check(self,name,passed,detail):
        super().check(name,passed,detail)
        if not passed:raise AssertionError(name)
    def poll(self,getter,predicate,timeout=15,label='condition'):
        deadline=time.monotonic()+timeout;last=None
        while time.monotonic()<deadline:
            last=getter()
            if predicate(last):return last
            time.sleep(.12)
        raise RuntimeError(f'Timed out {label}: {last}')
    def now(self):return int(self.number('time query gametime'))
    def step(self,ticks):
        if ticks<=0:return
        before=self.now();self.rc(f'tick step {ticks}')
        after=self.poll(self.now,lambda n:n>=before+ticks,timeout=ticks/10+20,label='server ticks advance')
        self.check('bounded_tick_step',after==before+ticks,{'before':before,'after':after,'ticks':ticks})
    def freeze(self):self.rc('tick freeze')
    def log(self,offset=0):
        with (cs.LOG_DIR/'server.log').open('rb') as f:f.seek(offset);return f.read().decode(errors='replace')
    def offset(self):return (cs.LOG_DIR/'server.log').stat().st_size
    def hp(self,target):return self.number(f'data get entity {target} Health')
    def power(self):return int(re.search(r'current=(\d+)',self.rc(f'myvillage cultivation qi {self.s.username}')).group(1))
    def key(self,key,game=None):
        game=game or self.s.game;game.key(key)
        self.report['inputs'].append({'method':'mapped X11 key','actor':'guest' if self.guest and game==self.guest.game else 'caster','key':key,'server_tick':self.now()});self.save()
    def capture(self,name,game=None):
        game=game or self.s.game;time.sleep(.15);game.save_png(game.grab_raw(),self.out/(name+'.png'))
        self.report['screenshots'].append({'file':name+'.png','server_tick':self.now()});self.save()
    def position(self,game):
        window=game.xdo('search','--name','Minecraft').split()[0]
        game.xdo('windowsize','--sync',window,'960','540');game.xdo('windowmove','--sync',window,'0','0')
        self.check('window_geometry',window_geometry(game.xdo('getwindowgeometry','--shell',window))==dict(X=0,Y=0,WIDTH=960,HEIGHT=540),window)
        game.size=(960,540)
    def start(self):
        if os.environ.get('MYVILLAGE_CLOUD_PROVIDER')!='Colab' or not str(ROOT).startswith('/content/'):
            raise RuntimeError('This evidence must run in the requested Colab VM')
        state=cs.read_state() if self.attach else cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),language='zh_cn',pvp=True,lock=str(procs.default_lock_path(ROOT))))
        if not state or state.get('phase')!='ready':raise RuntimeError('No owned ready session to attach')
        self.s=cs.Session.attach(ui_check=True);self.sessions+=1
        self.rc('tick unfreeze')
        self.report['phases'].append({'phase':'initial_setup','timings':state.get('timings',{})})
        self.position(self.s.game)
        for cmd in (*scene.world_rules(),*scene.player_setup(self.s.username,'myvillage:qingfeng_sword'),
                    'gamerule naturalRegeneration false','myvillage world pause','forceload add -32 -32 32 48'):
            self.rc(cmd)
        self.poll(lambda:self.rc('execute if loaded 0 -61 0 run time query gametime'),lambda r:'The time is' in r,label='fixture chunk')
        self.rc('fill -16 -61 -16 16 -61 40 minecraft:stone');self.configure(self.s.username)
        self.learned=[*IDS,'liuyun_bu','jianqi_jue']
        for ident in self.learned:self.rc(f'myvillage cultivation learn {self.s.username} myvillage:{ident}')
        scene.ensure_cultivation(self.s);self.bind_all();self.freeze()
    def configure(self,user):
        for cmd in (f'op {user}',f'gamemode survival {user}',f'myvillage cultivation awaken {user}',f'myvillage cultivation initiate {user}',
                    f'myvillage cultivation setrealm {user} myvillage:foundation_establishment myvillage:foundation_early',
                    f'myvillage cultivation setroot {user} 4000 1500 1500 1500 1500',f'myvillage cultivation qi fill {user}',
                    f'attribute {user} minecraft:generic.max_health base set {20 if user==self.s.username else 200}',f'effect give {user} minecraft:instant_health 1 10 true',
                    f'effect give {user} minecraft:saturation infinite 255 true',f'item replace entity {user} hotbar.0 with myvillage:qingfeng_sword'):
            self.rc(cmd)
    def click(self,x,y,label):
        scale=bounds(self.s.game.size)['scale'];self.s.game.xdo('mousemove',str(round(x*scale)),str(round(y*scale)),'click','1')
        self.report['inputs'].append({'method':'mapped H mouse click','x':x,'y':y,'label':label});time.sleep(.35)
    def bindings(self):return self.rc(f'execute as {self.s.username} run myvillage combat bindings')
    def bind_all(self):
        self.key('h');self.opened=True;time.sleep(.5);b=bounds(self.s.game.size)
        self.click(b['left']+26,b['top']+36+3*21+9,'技能页')
        ordered=sorted(self.learned,key=lambda i:(-self.defs[i]['grade'],self.lang[self.defs[i]['translation_key']]))
        columns=6 if b['width']>=350 else 3;rows=6//columns;dock=b['y']+b['height']-(15+rows*24+22)
        for slot,ident in enumerate(IDS,2):
            self.click(*tile_center(b,ordered.index(ident),18),'选择 '+ident)
            self.click(b['x']+(slot%columns+.5)*b['width']/columns,dock+14+slot//columns*24+11,'选择键位 '+str(slot))
            self.click(b['x']+max(66,b['width']//4)//2,dock+15+rows*24+9,'分配 '+ident)
            self.poll(self.bindings,lambda s:re.findall(r'myvillage:[a-z0-9_]+',s)[slot:slot+1]==['myvillage:'+ident],label='binding saved in exact slot')
        self.capture('three_sword_bindings');self.s.game.xdo('key','Escape');time.sleep(.3);self.opened=False
        self.s.game.require_ingame('bound skill tests');self.check('three_server_bindings',all('myvillage:'+i in self.bindings() for i in IDS),self.bindings())
    def ready(self):
        self.step(250)
        self.rc(f'tp {self.s.username} .5 -60 .5 0 0');self.rc(f'myvillage cultivation qi fill {self.s.username}')
        self.s.game.recover_grab();time.sleep(.2)
    def dummy(self,pos='.5 -60 9',armor=0):
        self.rc('kill @e[tag=locked_fixture]')
        self.rc('summon minecraft:husk '+pos+' {NoAI:1b,PersistenceRequired:1b,Silent:1b,Health:1000f,Tags:["locked_fixture"],attributes:[{id:"minecraft:generic.max_health",base:1000d},{id:"minecraft:generic.armor",base:'+str(armor)+'d},{id:"minecraft:generic.knockback_resistance",base:1d}]}')
        self.step(2);return '@e[tag=locked_fixture,limit=1]'
    def cast(self,ident):
        self.step(20)  # also expire the client's eight-tick pending lock and allow its time snapshot to catch up
        mark=self.offset();before=self.power();self.key(KEYS[ident]);log=self.poll(lambda:self.log(mark),lambda s:f'result=start art=myvillage:{ident}' in s,label='mapped cast accepted')
        match=re.search(r'LOCKED_SWORD player=\S+ t=(\d+) result=locked art=myvillage:'+ident,log)
        if not match:raise RuntimeError('Missing lock receipt')
        self.check(ident+'_single_payment',before-self.power()==self.defs[ident]['effects']['active']['qi_cost'],{'before':before,'after':self.power()})
        return mark,int(match.group(1))
    def finish_cast(self,ident,mark,start):
        lock=self.arts[ident]['lock'];end=lock['first_strike_ticks']+(lock['strikes']-1)*lock['strike_interval_ticks']+1+lock['recovery_ticks']
        self.step(max(0,start+end+1-self.now()))
        log=self.poll(lambda:self.log(mark),lambda s:f'result=stop art=myvillage:{ident}' in s,label='cast finished')
        self.report.setdefault('cast_logs',[]).append(log);self.save();return log
    def three_casts(self):
        for ident in IDS:
            self.ready();target=self.dummy(armor=20);before=self.hp(target);mark,start=self.cast(ident)
            lock=self.arts[ident]['lock'];self.step(max(1,lock['first_strike_ticks']//2));self.capture(ident+'_warning')
            self.step(start+lock['first_strike_ticks']-self.now());self.capture(ident+'_impact')
            if ident=='gengjin_jianjue':
                armor=self.number(f'attribute {target} minecraft:generic.armor get')
                self.check('gengjin_armor_break',abs(armor-15)<.01,armor)
            log=self.finish_cast(ident,mark,start)
            hits=[line for line in log.splitlines() if 'LOCKED_SWORD' in line and 'result=strike' in line]
            self.check(ident+'_authored_pulses',len(hits)==lock['strikes'],hits)
            self.check(ident+'_damages',self.hp(target)<before,{'before':before,'after':self.hp(target)})
            if ident=='gengjin_jianjue':
                self.step(lock['armor_break_ticks']);armor=self.number(f'attribute {target} minecraft:generic.armor get')
                self.check('armor_break_expires',abs(armor-20)<.01,armor)
        self.rc('kill @e[tag=locked_fixture]')
    def refusals(self):
        self.ready();self.rc('kill @e[tag=locked_fixture]');mark=self.offset();before=self.power();self.key('i');time.sleep(.5)
        self.check('no_target_no_payment','reason=no_target' in self.log(mark) and 'result=start art=' not in self.log(mark) and self.power()==before,self.log(mark))
        target=self.dummy();self.rc('fill -5 -60 4 5 -55 4 minecraft:stone');self.step(10);mark=self.offset();self.key('i');time.sleep(.4)
        self.check('wall_blocks_acquisition','reason=no_target' in self.log(mark) and 'result=start art=' not in self.log(mark),self.log(mark))
        self.rc('fill -5 -60 4 5 -55 4 minecraft:air')
        self.rc(f'myvillage cultivation qi set {self.s.username} 0');self.step(10);mark=self.offset();before=self.power();self.key('i');time.sleep(.5)
        after=self.power()
        self.check('insufficient_power_refused','reason=power' in self.log(mark) and 'result=start art=' not in self.log(mark) and before<self.defs['gengjin_jianjue']['effects']['active']['qi_cost'] and after>=before,{'before':before,'after':after,'log':self.log(mark)})
        self.rc(f'myvillage cultivation qi fill {self.s.username}')
        self.rc(f'myvillage cultivation setrealm {self.s.username} myvillage:qi_refining myvillage:qi_refining_4')
        self.step(10);mark=self.offset();before=self.power();self.key('k');time.sleep(.5)
        self.check('current_realm_rechecked','reason=unlearned' in self.log(mark) and 'result=start art=' not in self.log(mark) and self.power()==before,self.log(mark))
        self.rc(f'myvillage cultivation setrealm {self.s.username} myvillage:foundation_establishment myvillage:foundation_early')
        self.rc(f'myvillage cultivation qi fill {self.s.username}')
        self.rc('team add locked_allies');self.rc(f'team join locked_allies {self.s.username}');self.rc(f'team join locked_allies {target}')
        self.step(10);mark=self.offset();before=self.power();self.key('i');time.sleep(.5)
        self.check('allies_not_acquired','reason=no_target' in self.log(mark) and 'result=start art=' not in self.log(mark) and self.power()==before,self.log(mark));self.rc('team remove locked_allies')
        mark,start=self.cast('gengjin_jianjue');self.rc('fill -5 -60 4 5 -55 4 minecraft:stone');before=self.hp(target)
        log=self.finish_cast('gengjin_jianjue',mark,start)
        self.check('wall_breaks_existing_lock','result=lost' in log and self.hp(target)==before,log)
        self.rc('fill -5 -60 4 5 -55 4 minecraft:air');self.ready();target=self.dummy()
        mark,start=self.cast('taibai_jianjing');before=self.hp(target);self.key('Alt_L');self.step(40)
        log=self.log(mark);self.check('caster_dodge_cancels_without_hit','reason=DODGED' in log and self.hp(target)==before,log)
        mark=self.offset();before=self.power();self.key('k');time.sleep(.4)
        self.check('cancel_does_not_reset_cooldown','reason=cooldown' in self.log(mark) and 'result=start art=' not in self.log(mark) and self.power()==before,self.log(mark))
        self.ready();mark,start=self.cast('gengjin_jianjue');before=self.hp(target)
        self.rc(f'item replace entity {self.s.username} hotbar.0 with minecraft:stick');self.step(25)
        self.check('weapon_switch_cancels',self.hp(target)==before and 'reason=WEAPON_CHANGED' in self.log(mark),self.log(mark))
        self.rc(f'item replace entity {self.s.username} hotbar.0 with myvillage:qingfeng_sword');self.ready()
        mark,start=self.cast('taibai_jianjing');self.rc(f'tp {target} .5 -60 35');before=self.hp(target)
        log=self.finish_cast('taibai_jianjing',mark,start)
        self.check('range_breaks_lock','result=lost' in log and self.hp(target)==before,log)
        self.rc('kill @e[tag=locked_fixture]')
    def pair_ready(self,guest):
        self.ready();self.rc(f'tp {guest.username} .5 -60 9 180 0');self.rc(f'effect give {guest.username} minecraft:instant_health 1 10 true');time.sleep(.35)
    def defend_case(self,guest,ident,kind):
        self.pair_ready(guest);before=self.hp(guest.username);mark,start=self.cast(ident);lock=self.arts[ident]['lock']
        if kind=='full':
            self.step(lock['first_strike_ticks']-2);self.key('Alt_L',guest.game)
        elif kind=='partial':
            inv=self.defs['liuyun_bu']['effects']['movement']['invulnerable_ticks'];self.step(lock['first_strike_ticks']-inv-2);self.key('Alt_L',guest.game)
        elif kind=='early':self.key('Alt_L',guest.game)
        elif kind=='walk':
            guest.game.xdo('keydown','d');time.sleep(.35);guest.game.xdo('keyup','d')
            self.report['inputs'].append({'method':'mapped guest strafe','key':'d','seconds':.35})
        self.step(max(0,start+lock['first_strike_ticks']-self.now()));self.capture(ident+'_'+kind,guest.game)
        log=self.finish_cast(ident,mark,start);after=self.hp(guest.username)
        fraction='0.0' if kind=='full' else '.5' if kind=='partial' else '1.0'
        if kind=='full':self.check(ident+'_full_dodge',after==before and 'fraction=0.0' in log,{'before':before,'after':after,'log':log})
        elif kind=='partial':self.check(ident+'_partial_dodge',after<before and 'fraction=0.5' in log,{'before':before,'after':after,'log':log})
        else:self.check(ident+'_'+kind+'_still_hits',after<before and 'fraction=1.0' in log,{'before':before,'after':after,'log':log})
        return before-after
    def pvp(self):
        self.rc('tick unfreeze');self.rc('kill @e[tag=locked_fixture]')
        with SecondClient(self) as guest:
            self.guest=guest;self.position(guest.game);self.configure(guest.username)
            self.rc(f'myvillage cultivation learn {guest.username} myvillage:liuyun_bu');self.rc(f'tp {guest.username} .5 -60 9 180 0')
            if scene.parse_combat_mode(self.rc(f'data get entity {guest.username} "neoforge:attachments"'))!='cultivation':
                self.key('r',guest.game)
            time.sleep(.7)
            self.check('guest_cultivation_mode','cultivation' in self.rc(f'data get entity {guest.username} "neoforge:attachments"."myvillage:combat_preference"'),guest.username)
            self.freeze()
            plain=self.defend_case(guest,'gengjin_jianjue','walk')
            self.defend_case(guest,'gengjin_jianjue','full')
            partial=self.defend_case(guest,'gengjin_jianjue','partial')
            self.check('partial_less_than_unmitigated',0<partial<plain,{'plain':plain,'partial':partial})
            self.defend_case(guest,'taibai_jianjing','early');self.defend_case(guest,'taibai_jianjing','full')
            self.pair_ready(guest);before=self.hp(guest.username);mark,start=self.cast('tiangang_jiandian')
            self.step(18);self.key('Alt_L',guest.game);self.step(2);self.capture('tiangang_first_pulse_evaded',guest.game)
            log=self.finish_cast('tiangang_jiandian',mark,start)
            self.check('array_independent_pulses','fraction=0.0' in log and 'fraction=1.0' in log and self.hp(guest.username)<before,log)
            self.pair_ready(guest);mark,start=self.cast('tiangang_jiandian')
            guest.game.xdo('keydown','d');time.sleep(.8);self.key('Alt_L',guest.game);time.sleep(.6);guest.game.xdo('keyup','d');self.report['inputs'].append({'method':'mapped guest uses directional dodge to leave fixed array','key':'d + Alt_L'})
            position=read_data(self.rc(f'data get entity {guest.username} Pos'));before=self.hp(guest.username)
            self.check('guest_left_array',abs(position[0]-.5)>self.arts['tiangang_jiandian']['lock']['radius'],position)
            log=self.finish_cast('tiangang_jiandian',mark,start)
            self.check('array_does_not_chase',self.hp(guest.username)==before,log)
            self.report['multiplayer']='two_native_clients_scoped_passed';self.rc('tick unfreeze');self.guest=None
        self.freeze()
    def wave(self):
        self.ready();target=self.dummy('.5 -60 15');before=self.hp(target);mark=self.offset()
        self.s.game.xdo('mousedown','3');time.sleep(.18);self.s.game.xdo('mouseup','3');self.report['inputs'].append({'method':'mapped right mouse hold/release','seconds':.18})
        self.poll(lambda:self.log(mark),lambda s:'result=released skill=myvillage:jianqi_jue' in s,label='original sword qi released')
        self.step(35)
        self.check('original_sword_qi_hits',self.hp(target)<before,self.log(mark));self.capture('original_sword_qi_preserved')
    def run(self):
        self.start()
        methods=dict(zip(CASES,(self.three_casts,self.refusals,self.pvp,self.wave)))
        for case in self.cases:
            methods[case]();self.report['completed'].append(case);self.report['unrun'].remove(case);self.save()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--cases',nargs='+',choices=CASES,default=list(CASES))
    p.add_argument('--attach',action='store_true');p.add_argument('--keep-game-on-failure',action='store_true');args=p.parse_args()
    e=LockedEvidence(args.out,args.cases,args.attach)
    try:e.run()
    except BaseException as error:
        e.report['failures'].append(repr(error));traceback.print_exc()
    finally:
        try:
            if e.s:
                e.rc('tick unfreeze')
                if e.report['failures'] and args.keep_game_on_failure:e.report['game_retained_for_debug']=True
                else:e.stop()
        except BaseException as error:e.report['failures'].append('cleanup: '+repr(error))
        e.report['passed']=not e.report['failures'] and not e.report['unrun'] and all(c['pass'] for c in e.report['checks']);e.save()
    raise SystemExit(0 if e.report['passed'] else 1)
