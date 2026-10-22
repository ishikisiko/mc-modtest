#!/usr/bin/env python3
"""Requested existing Colab: native mapped book use, H assignment and elemental casts.

RCON only prepares/reads fixtures and steps time. It never invokes spell actions.
Each selected case has explicit completion; screenshots, logs and failures are retained.
"""
from pathlib import Path
import argparse,json,os,re,secrets,sys,time,traceback
from contextlib import contextmanager
from types import SimpleNamespace
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.locked_sword_evidence import LockedEvidence
from tools.combat_capture import session as cs,scene,procs
from tools.ink_hub_evidence import bounds,tile_center
from tools.alchemy_quality_evidence import SecondClient,read_data

CASES=('learning','five_kits','weapon_and_resistance','refusals','two_players','restart','old_combat')
KEYS=('z','c','i','j','k','m')

class ElementalEvidence(LockedEvidence):
    def __init__(self,out,cases,attach=False):
        super().__init__(out,cases,attach)
        self.source=json.loads((ROOT/'tools/elemental_arts/arts.json').read_text())
        self.arts={a['id']:a for a in self.source['arts']}
        self.assigned={};self.learned=[]
        self.report.update(kind='elemental_arts',method=__doc__,videos=[],fixture='Administrator-given zero-progress books, later learned-combat fixtures; isolated flat world',
                           natural_acquisition='recipe registration/data verified; natural material collection not observed')
    def log(self,offset=0):
        return "\n".join(line for line in super().log(offset).splitlines() if any(tag in line for tag in ("ELEMENTAL ","ADVANCED_ART ","LOCKED_SWORD ","SWORD_QI ","DODGE ")))
    def capture(self,name,game=None):
        original=name;index=2
        while (self.out/(name+'.png')).exists():
            name=f'{original}-{index}';index+=1
        super().capture(name,game)
    def learned_text(self):return self.rc(f'data get entity {self.s.username} "neoforge:attachments"."myvillage:cultivation_profile".learned_techniques')
    def start(self,reuse=False):
        if os.environ.get('MYVILLAGE_CLOUD_PROVIDER')!='Colab' or not str(ROOT).startswith('/content/'):
            raise RuntimeError('Only run in the requested Colab VM')
        state=cs.read_state() if self.attach else cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),language='zh_cn',pvp=True,reuse_world=reuse,lock=str(procs.default_lock_path(ROOT))))
        if not state or state.get('phase')!='ready':raise RuntimeError('No ready native session')
        self.s=cs.Session.attach(ui_check=True);self.sessions+=1;self.rc('tick unfreeze');self.position(self.s.game)
        self.report['phases'].append({'phase':'restart' if reuse else 'start','timings':state.get('timings',{})})
        if reuse:self.freeze();return
        for cmd in (*scene.world_rules(),*scene.player_setup(self.s.username,'minecraft:air'),'gamerule naturalRegeneration false',
                    'myvillage world pause','forceload add -32 -32 32 48'):
            self.rc(cmd)
        self.poll(lambda:self.rc('execute if loaded 0 -61 0 run time query gametime'),lambda r:'The time is' in r,label='fixture chunk')
        self.rc('fill -16 -61 -16 16 -61 40 minecraft:stone');self.configure(self.s.username)
        self.rc(f'item replace entity {self.s.username} hotbar.0 with minecraft:air')
        self.rc(f'myvillage cultivation setroot {self.s.username} 2000 2000 2000 2000 2000')
        scene.ensure_cultivation(self.s);self.freeze()
    def configure(self,user):
        super().configure(user)
        self.rc(f'attribute {user} minecraft:generic.max_health base set 100')
        self.rc(f'effect give {user} minecraft:instant_health 1 10 true')
    def fixture_loadout(self,ids):
        for ident in self.arts:
            self.rc(f'myvillage cultivation {"learn" if ident in ids else "forget"} {self.s.username} myvillage:{ident}')
        for ident in ('jianqi_jue','qinglian_jiange','gengjin_jianjue'):
            self.rc(f'myvillage cultivation {"learn" if ident in ids else "forget"} {self.s.username} myvillage:{ident}')
        self.rc(f'myvillage cultivation learn {self.s.username} myvillage:liuyun_bu')
        self.learned=list(ids)+['liuyun_bu'];self.bind(ids)
    def bind(self,ids):
        self.step(5);self.rc('tick unfreeze');self.key('h');self.opened=True;time.sleep(.5);b=bounds(self.s.game.size)
        self.click(b['left']+26,b['top']+36+3*21+9,'技能页')
        ordered=sorted(set(self.learned),key=lambda i:(-self.defs[i]['grade'],self.lang[self.defs[i]['translation_key']]))
        columns=6 if b['width']>=350 else 3;rows=6//columns;dock=b['y']+b['height']-(15+rows*24+22)
        self.check('loadout_fits_visible_grid',len(ordered)<=8,ordered)
        for slot,ident in enumerate(ids):
            self.click(*tile_center(b,ordered.index(ident),18),'选择 '+ident)
            self.click(b['x']+(slot%columns+.5)*b['width']/columns,dock+14+slot//columns*24+11,'选择键位 '+str(slot))
            self.click(b['x']+max(66,b['width']//4)//2,dock+15+rows*24+9,'分配 '+ident)
            self.poll(self.bindings,lambda s:re.findall(r'myvillage:[a-z0-9_]+',s)[slot:slot+1]==['myvillage:'+ident],label='saved exact binding')
            self.assigned[ident]=KEYS[slot]
        self.capture('bindings_'+ids[0]);self.s.game.xdo('key','Escape');time.sleep(.25);self.opened=False
        self.s.game.require_ingame('elemental casting');self.freeze()
    def ready(self,ticks=210):
        self.step(ticks);self.rc(f'tp {self.s.username} .5 -60 .5 0 0');self.rc(f'myvillage cultivation qi fill {self.s.username}')
        self.s.game.recover_grab();time.sleep(.15)
    def dummy(self,pos='.5 -60 9',armor=0,tag='locked_fixture',knockback=1):
        self.rc('kill @e[tag='+tag+']')
        self.rc('summon minecraft:husk '+pos+' {NoAI:0b,PersistenceRequired:1b,Silent:1b,Health:1000f,Tags:["'+tag+'"],attributes:[{id:"minecraft:generic.max_health",base:1000d},{id:"minecraft:generic.armor",base:'+str(armor)+'d},{id:"minecraft:generic.knockback_resistance",base:'+str(knockback)+'d},{id:"minecraft:generic.movement_speed",base:0d},{id:"minecraft:generic.follow_range",base:0d},{id:"minecraft:generic.attack_damage",base:0d}]}')
        self.step(2);return '@e[tag='+tag+',limit=1]'
    def cast(self,ident,finish=True,shot=False):
        a=self.arts[ident];self.step(3);mark=self.offset();before=self.power();start=self.now();self.key(self.assigned[ident])
        self.poll(lambda:self.log(mark),lambda s:f'result=start art=myvillage:{ident}' in s,label='mapped elemental cast accepted')
        self.check(ident+'_single_payment',before-self.power()==a['qi_cost'],{'before':before,'after':self.power()})
        if shot:
            self.step(a['windup_ticks']);self.capture(ident+'_impact')
        if finish:
            total=a['windup_ticks']+(a['pulses']-1)*a['pulse_interval_ticks']+1+a['recovery_ticks']
            self.step(max(0,start+total+1-self.now()))
            self.poll(lambda:self.log(mark),lambda s:f'result=stop art=myvillage:{ident}' in s,label='elemental cast finished')
        return mark,start
    def done(self,case):self.report['completed'].append(case);self.report['unrun'].remove(case);self.save()
    def learning(self):
        u=self.s.username
        self.rc('tick unfreeze')
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:manual_active_xuan[myvillage:technique="myvillage:baoyan_shu"]')
        time.sleep(.4);self.s.game.xdo('click','3');time.sleep(1)
        self.report['inputs'].append({'method':'mapped manual use without prerequisite','technique':'baoyan_shu'})
        self.check('missing_prerequisite_not_learned','myvillage:baoyan_shu' not in self.learned_text(),self.learned_text())
        self.rc(f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_1')
        for a in self.source['arts']:
            if a['role']!='basic':continue
            ident=a['id'];self.rc(f'item replace entity {u} hotbar.0 with myvillage:manual_active_huang[myvillage:technique="myvillage:{ident}"]')
            time.sleep(.4);self.s.game.xdo('click','3');self.report['inputs'].append({'method':'mapped right click zero-progress manual','technique':ident})
            self.poll(self.learned_text,lambda s:'myvillage:'+ident in s,timeout=90,label='normal manual study '+ident)
            self.check(ident+'_normal_learning',True,self.learned_text())
        self.freeze();self.rc(f'item replace entity {u} hotbar.0 with minecraft:air')
        for ident in self.arts:
            reply=self.rc(f'recipe give {u} myvillage:manual_{ident}');self.check(ident+'_recipe_registered','Unknown' not in reply and 'Incorrect' not in reply,reply)
        self.rc(f'myvillage cultivation setrealm {u} myvillage:foundation_establishment myvillage:foundation_early')
        self.done('learning')
    def five_kits(self):
        u=self.s.username
        for element in ('metal','wood','water','fire','earth'):
            kit={a['role']:a['id'] for a in self.source['arts'] if a['element']==element}
            ids=[kit[r] for r in ('basic','ward','burst','field')];self.fixture_loadout(ids);self.ready(310)
            target=self.dummy('.5 -60 5' if element=='water' else '.5 -60 9',armor=20 if element=='metal' else 0,knockback=0 if element in ('water','earth') else 1);before=self.hp(target)
            extra=self.dummy('.5 -60 12',tag='elemental_extra') if element=='metal' else None
            extra_before=self.hp(extra) if extra else None
            for n in range(3):
                mark,_=self.cast(kit['basic'],shot=n==0);self.step(15)
                self.check(element+'_charge_'+str(n+1),f'element={element} value={n+1}' in self.log(mark),self.log(mark))
            self.check(element+'_basic_damage',self.hp(target)<before,{'before':before,'after':self.hp(target)})
            if extra:self.check('metal_ray_pierces_second_target',self.hp(extra)<extra_before,{'before':extra_before,'after':self.hp(extra)})
            position_before=read_data(self.rc(f'data get entity {target} Pos'))
            mark,_=self.cast(kit['burst'],shot=True);log=self.log(mark)
            self.check(element+'_burst_consumes_three','charges=3' in log,log)
            if element in ('water','earth'):
                position_after=read_data(self.rc(f'data get entity {target} Pos'));self.check(element+'_burst_push',position_after[2]>position_before[2]+.1,{'before':position_before,'after':position_after})
            if element in ('wood','fire'):
                hits=re.findall(r'harvest=([\d.]+)',log);self.check(element+'_owned_dot_harvest',any(float(x)>0 for x in hits),log)
            if element=='metal':self.check('metal_armor_break',self.number(f'attribute {target} minecraft:generic.armor get')<20,self.rc(f'myvillage stats info {target}'))
            # Gain once again so the wood heal and ward extension are based on real hit accumulation.
            self.step(25);self.cast(kit['basic']);self.step(25)
            self.rc(f'damage {u} 10 minecraft:mob_attack by {target}');self.step(21);before=self.hp(u)
            self.cast(kit['ward'],shot=True);after_ward=self.hp(u)
            if element=='wood':self.check('wood_earned_heal',after_ward>before,{'before':before,'after':after_ward})
            self.rc(f'damage {u} 10 minecraft:mob_attack by {target}');after=self.hp(u)
            self.check(element+'_ward_reduces',0<after_ward-after<10,{'before':after_ward,'after':after})
            self.step(25);mark,_=self.cast(kit['field'],shot=True);log=self.log(mark)
            pulses=re.findall(r'ELEMENTAL .*result=pulse art=myvillage:'+kit['field']+r' pulse=(\d+)',log)
            self.check(element+'_field_five_pulses',pulses==list(map(str,range(5))),log)
            self.capture(element+'_hotbar');self.rc('kill @e[tag=locked_fixture]');self.rc('kill @e[tag=elemental_extra]')
        self.done('five_kits')
    def weapon_and_resistance(self):
        u=self.s.username
        for element in ('metal','wood','water','fire','earth'):
            ident=next(a['id'] for a in self.source['arts'] if a['element']==element and a['role']=='basic')
            self.fixture_loadout([ident]);values=[]
            for item in ('minecraft:air','myvillage:qingfeng_sword','myvillage:lingxiao_spear','myvillage:xuantie_gauntlet'):
                self.rc(f'item replace entity {u} hotbar.0 with {item}');self.ready(30);target=self.dummy();before=self.hp(target)
                self.cast(ident);trace=self.one(f'myvillage stats trace {target}','STATS_TRACE');values.append(before-self.hp(target))
                self.check(element+'_typed_'+item,trace['type']==element,trace)
            self.check(element+'_weapon_independent',max(values)-min(values)<.001,values)
            self.rc(f'item replace entity {u} hotbar.0 with minecraft:air');self.ready(30);target=self.dummy()
            self.rc(f'attribute {target} myvillage:{element}_resistance base set 0.5');before=self.hp(target);self.cast(ident);reduced=before-self.hp(target)
            self.check(element+'_own_resistance',abs(reduced-values[0]*.5)<.02,{'plain':values[0],'resisted':reduced})
            self.rc(f'attribute {u} myvillage:{element}_resistance_penetration base set 0.25');self.ready(30);target=self.dummy()
            self.rc(f'attribute {target} myvillage:{element}_resistance base set 0.5');before=self.hp(target);self.cast(ident);pierced=before-self.hp(target)
            self.check(element+'_own_penetration',abs(pierced-values[0]*.75)<.02,{'plain':values[0],'penetrated':pierced})
            self.rc(f'attribute {u} myvillage:{element}_resistance_penetration base set 0')
        self.rc('kill @e[tag=locked_fixture]');self.done('weapon_and_resistance')
    def refused(self,ident,label):
        mark=self.offset();power=self.power();self.key(self.assigned[ident]);time.sleep(.6)
        self.check(label,'result=start art=' not in self.log(mark) and self.power()==power,self.log(mark))
    def refusals(self):
        u=self.s.username;ids=['jinmang_shu','jingang_zhang','pozhen_jinlun','houshi_zhang'];self.fixture_loadout(ids);self.ready(310)
        self.rc(f'myvillage cultivation qi set {u} 0');self.refused(ids[0],'zero_qi_refused');self.ready(20)
        self.rc(f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_1');self.refused(ids[2],'low_realm_refused')
        self.rc(f'myvillage cultivation setrealm {u} myvillage:foundation_establishment myvillage:foundation_early');self.ready(20)
        target=self.dummy();self.cast(ids[1]);self.refused(ids[3],'wards_share_cooldown');self.refused(ids[1],'own_cooldown')
        self.ready(220);target=self.dummy();self.rc('fill -5 -60 4 5 -55 4 minecraft:stone');before=self.hp(target)
        self.cast(ids[0]);self.check('wall_blocks_elemental_ray',self.hp(target)==before,self.log());self.rc('fill -5 -60 4 5 -55 4 minecraft:air')
        self.rc('team add elemental_allies');self.rc('team modify elemental_allies friendlyFire false');self.rc(f'team join elemental_allies {u}');self.rc('team join elemental_allies @e[tag=locked_fixture]')
        self.ready(25);before=self.hp(target);self.cast(ids[0]);self.check('ally_not_hit',self.hp(target)==before,before);self.rc('team remove elemental_allies')
        self.ready(30);target=self.dummy(knockback=0);before=self.hp(target);mark,start=self.cast(ids[0],finish=False)
        self.step(self.arts[ids[0]]['windup_ticks']-1);self.rc(f'data merge entity {target} {{Motion:[0.2d,0d,0d]}}');self.step(2)
        motion=read_data(self.rc(f'data get entity {target} Motion'))
        self.check('spell_hit_preserves_existing_motion',self.hp(target)<before and motion[0]>.01,{'motion':motion,'log':self.log(mark)})
        self.ready(110);target=self.dummy();before=self.hp(target);mark,_=self.cast(ids[2],finish=False)
        self.rc(f'item replace entity {u} hotbar.0 with minecraft:stick');self.step(30)
        self.check('held_change_cancels',self.hp(target)==before and 'reason=WEAPON_CHANGED' in self.log(mark),self.log(mark));self.refused(ids[2],'switch_does_not_reset_cooldown')
        self.ready(110);self.rc(f'item replace entity {u} hotbar.0 with minecraft:air');before=self.hp(target);mark,_=self.cast(ids[2],finish=False)
        self.key('Alt_L');self.step(30);self.check('dodge_cancels_spell',self.hp(target)==before and 'reason=DODGED' in self.log(mark),self.log(mark))
        self.ready(220);self.rc(f'myvillage cultivation forget {u} myvillage:{ids[0]}');self.refused(ids[0],'forgotten_bound_skill_refused')
        self.rc('kill @e[tag=locked_fixture]');self.done('refusals')
    @contextmanager
    def guest_context(self,guest):
        previous=(self.s,self.learned,self.assigned)
        self.s=SimpleNamespace(username=guest.username,game=guest.game,run=previous[0].run)
        self.learned=[];self.assigned={}
        try:yield
        finally:self.s,self.learned,self.assigned=previous
    def two_players(self):
        ids=['linghuo_shu','baoyan_shu','huiyan_zhang','chiyan_jie'];self.fixture_loadout(ids);self.ready(310);self.rc('tick unfreeze')
        with SecondClient(self) as guest:
            self.guest=guest;self.position(guest.game);self.configure(guest.username)
            self.rc(f'item replace entity {guest.username} hotbar.0 with minecraft:air');self.rc(f'tp {guest.username} .5 -60 9 180 0')
            self.rc(f'myvillage cultivation learn {guest.username} myvillage:liuyun_bu')
            if scene.parse_combat_mode(self.rc(f'data get entity {guest.username} "neoforge:attachments"'))!='cultivation':self.key('r',guest.game)
            time.sleep(.6);self.freeze();self.ready(25);before=self.hp(guest.username)
            mark,_=self.cast(ids[0]);self.check('guest_takes_typed_spell',self.hp(guest.username)<before,self.log(mark));self.capture('guest_observes_fire',guest.game)
            self.step(90);self.rc(f'effect give {guest.username} minecraft:instant_health 1 10 true');self.ready(25);before=self.hp(guest.username)
            mark,start=self.cast(ids[1],finish=False);self.step(8);self.key('Alt_L',guest.game);self.step(30)
            self.check('guest_dodge_prevents_burst',self.hp(guest.username)==before,self.log(mark));self.capture('guest_dodge',guest.game)
            # Two real casters share a victim: a guest cannot harvest the owner's burn.
            self.ready(310)
            with self.guest_context(guest):self.fixture_loadout(['linghuo_shu','baoyan_shu','huiyan_zhang'])
            self.rc('team add elemental_casters');self.rc('team modify elemental_casters friendlyFire false')
            self.rc(f'team join elemental_casters {self.s.username}');self.rc(f'team join elemental_casters {guest.username}')
            self.rc(f'tp {guest.username} .5 -60 .5 0 0');target=self.dummy();self.cast('linghuo_shu')
            with self.guest_context(guest):
                self.assigned={'linghuo_shu':'z','baoyan_shu':'c','huiyan_zhang':'i'}
                mark,_=self.cast('baoyan_shu');log=self.log(mark)
                self.check('guest_does_not_harvest_other_caster',bool(re.search(r'harvest=0\.0 success=true',log)),log)
            mark,_=self.cast('baoyan_shu');log=self.log(mark)
            self.check('owner_still_harvests_own_burn',any(float(x)>0 for x in re.findall(r'harvest=([\d.]+)',log)),log)
            self.cast('huiyan_zhang')
            with self.guest_context(guest):
                self.assigned={'huiyan_zhang':'i'};self.cast('huiyan_zhang')
            self.check('ward_cooldowns_are_per_player',True,'Both mapped wards accepted within one shared-cooldown interval')
            self.rc('team remove elemental_casters');self.rc('kill @e[tag=locked_fixture]')
            self.report['multiplayer']='two_native_clients_cast_visibility_dodge_dot_ownership_and_independent_cooldowns';self.guest=None;self.rc('tick unfreeze')
        self.freeze();self.done('two_players')
    def restart(self):
        ids=['jinmang_shu','shuiren_shu','liushui_zhang'];self.fixture_loadout(ids);self.ready(220)
        speed=self.number(f'attribute {self.s.username} minecraft:generic.movement_speed get')
        self.cast('liushui_zhang')
        self.check('water_ward_speed_active',self.number(f'attribute {self.s.username} minecraft:generic.movement_speed get')>speed,speed)
        before=self.bindings();learned=self.learned_text();self.rc('tick unfreeze');self.stop();self.attach=False;self.start(reuse=True)
        self.check('transient_ward_speed_not_saved',abs(self.number(f'attribute {self.s.username} minecraft:generic.movement_speed get')-speed)<.0001,speed)
        self.check('saved_bindings_restart',all('myvillage:'+i in self.bindings() for i in ids),{'before':before,'after':self.bindings()})
        self.check('saved_learning_restart',all('myvillage:'+i in self.learned_text() for i in ids),{'before':learned,'after':self.learned_text()})
        self.rc(f'item replace entity {self.s.username} hotbar.0 with minecraft:air');scene.ensure_cultivation(self.s);self.ready(20)
        target=self.dummy();before=self.hp(target);self.cast(ids[0]);self.check('cast_after_restart',self.hp(target)<before,before);self.rc('kill @e[tag=locked_fixture]');self.done('restart')
    def old_combat(self):
        ids=['qinglian_jiange','gengjin_jianjue'];self.fixture_loadout(ids)
        self.rc(f'myvillage cultivation learn {self.s.username} myvillage:jianqi_jue')
        self.rc(f'item replace entity {self.s.username} hotbar.0 with myvillage:qingfeng_sword');self.ready(310)
        target=self.dummy('.5 -60 2.8');before=self.hp(target);mark=self.offset();self.key('z');self.step(70)
        self.check('original_advanced_art',self.hp(target)<before and 'result=start art=myvillage:qinglian_jiange' in self.log(mark),self.log(mark))
        self.ready(30);target=self.dummy();before=self.hp(target);mark=self.offset();self.key('c');self.step(35)
        self.check('original_locked_sword',self.hp(target)<before and 'LOCKED_SWORD' in self.log(mark),self.log(mark))
        self.wave();self.done('old_combat')
    def run(self):
        self.start()
        for case in self.cases:getattr(self,case)()

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--cases',nargs='+',choices=CASES,default=list(CASES));p.add_argument('--attach',action='store_true');p.add_argument('--keep-game-on-failure',action='store_true');args=p.parse_args()
    e=ElementalEvidence(args.out,args.cases,args.attach)
    try:e.run()
    except BaseException as error:e.report['failures'].append(repr(error));traceback.print_exc()
    finally:
        try:
            if e.s:
                e.rc('tick unfreeze')
                if e.report['failures'] and args.keep_game_on_failure:e.report['game_retained_for_debug']=True
                else:e.stop()
        except BaseException as error:e.report['failures'].append('cleanup: '+repr(error))
        e.report['passed']=not e.report['failures'] and not e.report['unrun'] and all(c['pass'] for c in e.report['checks']);e.save()
    raise SystemExit(0 if e.report['passed'] else 1)
