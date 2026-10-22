#!/usr/bin/env python3
"""Colab mapped-input checks of doubled elemental reach, footprints and observer cues.

Commands prepare/read fixtures only. Cooldowns may advance at 200 TPS while frozen;
actual casts/screenshots step at 20 TPS, and video runs at normal 20 TPS.
"""
from pathlib import Path
import argparse,json,os,subprocess,sys,time,traceback
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from tools.elemental_arts_evidence import ElementalEvidence
from tools.alchemy_quality_evidence import SecondClient,read_data
from tools.combat_capture import session as cs
from tools.combat_capture.capture import ffmpeg_record_argv
CASES=('five_school_ranges','cone_and_ward','walls_and_allies','two_client_showcase')

class RangeEvidence(ElementalEvidence):
    def __init__(self,out,cases,attach=False):
        super().__init__(out,cases,attach)
        self.kits={e:{a['role']:a for a in self.source['arts'] if a['element']==e} for e in ('metal','wood','water','fire','earth')}
        self.report.update(kind='elemental_range',method=__doc__,learning='unchanged learning and recipes not rerun',visual_scale=self.source['rules']['visual_scale'])
    def start(self,reuse=False):
        super().start(reuse)
        for cmd in ('gamerule doMobLoot false','forceload add -48 -32 64 80','fill -32 -61 -24 48 -61 72 minecraft:stone'):
            self.rc(cmd)
        self.poll(lambda:self.rc('execute if loaded 0 -61 64 run time query gametime'),lambda r:'The time is' in r,label='extended fixture area loaded')
    def ready(self,ticks=330):
        self.rc('tick rate 200');super().ready(ticks);self.rc('tick rate 20');self.step(12)
    def clean(self):
        for cmd in ('kill @e[tag=elemental_range]','kill @e[type=minecraft:item]','kill @e[type=minecraft:experience_orb]','team remove range_allies'):
            self.rc(cmd)
    def dummy(self,pos='.5 -60 9',armor=0,tag='range_target',knockback=1):
        target=super().dummy(pos,armor,tag,knockback);self.rc(f'tag {target} add elemental_range');return target
    def load(self,element):
        ids=[self.kits[element][r]['id'] for r in ('basic','ward','burst','field')];self.fixture_loadout(ids)
    def finish_spell(self,ident,mark,start):
        a=self.arts[ident];total=a['windup_ticks']+(a['pulses']-1)*a['pulse_interval_ticks']+1+a['recovery_ticks']
        self.step(max(0,start+total+2-self.now()));log=self.log(mark)
        self.check(ident+'_completed','result=stop art=myvillage:'+ident+' reason=COMPLETED' in log,log)
        return log
    def five_school_ranges(self):
        for element,kit in self.kits.items():
            self.clean();self.load(element)
            for role in ('basic','burst','field'):
                a=kit[role]
                if a['shape']=='cone':continue
                self.clean();self.ready();z=.5+a['range']-.6
                center=self.dummy(f'.5 -60 {z}',tag='range_center');outside=self.dummy(f'.5 -60 {.5+a["range"]+2}',tag='range_outer') if role=='basic' or a['shape']=='ray' else self.dummy(f'{.5+a["radius"]+.8} -60 {z}',tag='range_outer')
                side=None
                if a['shape'] in ('burst','field'):side=self.dummy(f'{.5+a["radius"]-.4} -60 {z}',tag='range_side')
                elif a['shape']=='ray':side=self.dummy(f'1.6 -60 {z-1}',tag='range_side')
                before=[self.hp(t) for t in [center,outside]+([side] if side else [])]
                mark,_=self.cast(a['id']);after=[self.hp(t) for t in [center,outside]+([side] if side else [])]
                self.check(a['id']+'_new_reach',after[0]<before[0],{'before':before,'after':after,'range':a['range'],'radius':a['radius']})
                self.check(a['id']+'_outside_untouched',after[1]==before[1],{'before':before,'after':after})
                if side:self.check(a['id']+'_enlarged_width_or_radius',after[2]<before[2],{'before':before,'after':after})
            # Single-target spells also need an explicit outside-range refusal to hit.
            a=kit['basic'];self.clean();self.ready();outside=self.dummy(f'.5 -60 {.5+a["range"]+1}',tag='range_only_outer');before=self.hp(outside)
            self.cast(a['id']);self.check(a['id']+'_no_out_of_range_lock',self.hp(outside)==before,before)
        self.clean()
    def cone_and_ward(self):
        self.load('water');self.clean();self.ready();a=self.kits['water']['burst'];reach=a['radius']*2
        front=self.dummy(f'.5 -60 {.5+reach-.6}',tag='cone_front');outside=self.dummy(f'.5 -60 {.5+reach+1}',tag='cone_out')
        side=self.dummy('12.0 -60 2.0',tag='cone_side')
        back=[self.dummy(f'{.5+(i%4)*.4} -60 {-1-(i//4)*.5}',tag='cone_back_'+str(i)) for i in range(8)]
        targets=[front,outside,side,*back];before=[self.hp(t) for t in targets];self.cast(a['id']);after=[self.hp(t) for t in targets]
        self.check('cone_reaches_fourteen_despite_eight_nearby_rear_targets',after[0]<before[0],{'before':before,'after':after})
        self.check('cone_excludes_outside_sides_and_back',after[1:]==before[1:],{'before':before,'after':after})
        # Put the outside probe behind the caster so the pushed inside mob cannot collide with it.
        self.clean();self.load('fire');self.ready();near=self.dummy('.5 -60 6.1',tag='ward_near',knockback=0);far=self.dummy('.5 -60 -6.3',tag='ward_far',knockback=0)
        before=[read_data(self.rc(f'data get entity {t} Pos')) for t in (near,far)];self.cast(self.kits['fire']['ward']['id']);self.step(8)
        after=[read_data(self.rc(f'data get entity {t} Pos')) for t in (near,far)]
        self.check('fire_ward_six_block_push',after[0][2]>before[0][2]+.1 and abs(after[1][2]-before[1][2])<.01,{'before':before,'after':after});self.clean()
    def walls_and_allies(self):
        self.load('fire');self.clean();self.ready();center=self.dummy('.5 -60 18.5',tag='occlusion_center');wall=self.dummy('-5.5 -60 18.5',tag='occlusion_wall');ally=self.dummy('5.5 -60 18.5',tag='occlusion_ally')
        self.rc('fill -3 -60 14 -3 -55 22 minecraft:stone');self.rc('team add range_allies');self.rc(f'team join range_allies {self.s.username}');self.rc(f'team join range_allies {ally}')
        before=[self.hp(t) for t in (center,wall,ally)];self.cast(self.kits['fire']['field']['id']);after=[self.hp(t) for t in (center,wall,ally)]
        self.check('expanded_field_preserves_walls_and_allies',after[0]<before[0] and after[1:]==before[1:],{'before':before,'after':after})
        self.rc('fill -3 -60 14 -3 -55 22 minecraft:air');self.clean()
    def observe(self,guest,cone=False):
        self.rc(f'gamemode spectator {guest.username}')
        self.rc(f'tp {guest.username} '+('12.5 -51 -8.5 32 30' if cone else '8.5 -54 -2.5 28 25'))
        time.sleep(.3)
    def two_client_showcase(self):
        self.clean();self.rc('tick unfreeze')
        with SecondClient(self) as guest:
            self.guest=guest;self.position(guest.game);self.configure(guest.username);self.freeze();self.load('water');self.ready()
            self.rc(f'tp {guest.username} .5 -60 13.5 180 0');self.step(3);before=self.hp(guest.username);self.cast(self.kits['water']['burst']['id'])
            self.check('second_player_inside_enlarged_cone_hit',self.hp(guest.username)<before,{'before':before,'after':self.hp(guest.username)})
            self.observe(guest);self.key('F1',guest.game)
            for element,kit in self.kits.items():
                self.clean();self.load(element);self.ready();self.rc(f'tp {self.s.username} .5 -60 -8.5 0 0');self.observe(guest)
                self.dummy('.5 -60 12.5',tag='show_center');a=kit['field'];mark,start=self.cast(a['id'],finish=False)
                self.step(a['windup_ticks']);self.capture(element+'_field',guest.game);self.capture(element+'_caster')
                self.finish_spell(a['id'],mark,start)
            self.clean();self.load('water');self.ready();self.observe(guest,True);self.dummy('.5 -60 13.5',tag='show_water')
            a=self.kits['water']['burst'];mark,start=self.cast(a['id'],finish=False);self.step(a['windup_ticks']);self.capture('water_cone',guest.game);self.finish_spell(a['id'],mark,start)
            self.clean();self.ready();self.observe(guest,True);self.dummy('.5 -60 13.5',tag='video_water')
            file=self.out/'water_cone.mp4'
            with (self.out/'video.log').open('w') as log:
                rec=subprocess.Popen(ffmpeg_record_argv(guest.game.display,file,5,fps=20,size=(960,540)),stdout=log,stderr=subprocess.STDOUT)
                time.sleep(.5);self.rc('tick rate 20');self.rc('tick unfreeze');mark=self.offset();self.key(self.assigned[a['id']]);rec.wait(timeout=30);self.freeze()
            self.check('normal_time_cone_video',rec.returncode==0 and file.stat().st_size>10000 and 'result=start art=myvillage:'+a['id'] in self.log(mark),self.log(mark))
            self.report['videos'].append({'file':file.name,'seconds':5,'server_tick_rate':20});self.report['multiplayer']='two actual clients: enlarged cone PvP hit and all five field cues visible to observer'
            self.check('two_clients_connected',self.s.username in self.rc('list') and guest.username in self.rc('list'),self.rc('list'))
            self.rc('tick unfreeze');self.guest=None
        self.freeze()
    def run(self):
        self.start()
        for case in self.cases:getattr(self,case)();self.done(case)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--cases',nargs='+',choices=CASES,default=list(CASES));p.add_argument('--attach',action='store_true');p.add_argument('--keep-game-on-failure',action='store_true');args=p.parse_args()
    e=RangeEvidence(args.out,args.cases,args.attach)
    try:e.run()
    except BaseException as error:e.report['failures'].append(repr(error));traceback.print_exc()
    finally:
        try:
            if e.s:
                e.rc('tick rate 20');e.rc('tick unfreeze')
                if e.report['failures'] and args.keep_game_on_failure:e.report['game_retained_for_debug']=True
                else:e.stop()
            e.report['termination']='confirmed' if cs.read_state().get('phase')=='stopped' else 'not_confirmed'
        except BaseException as error:e.report['failures'].append('cleanup: '+repr(error))
        e.report['passed']=not e.report['failures'] and not e.report['unrun'] and all(c['pass'] for c in e.report['checks']);e.save()
    raise SystemExit(0 if e.report['passed'] else 1)
