#!/usr/bin/env python3
"""Selected advanced-fate scenarios using mapped input in a disposable Modal world."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import sys
import time
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from tools.fates_purple_evidence import PurpleEvidence, ROOT
from tools.modal_runner.remote import ADVANCED_FATE_CASES, select_advanced_fate_cases
from tools.combat_capture import session as cs


class AdvancedEvidence(PurpleEvidence):
    def __init__(self,out,cases=None):
        super().__init__(out)
        selected=select_advanced_fate_cases(cases)
        self.report.update(kind='advanced_fates',selected=selected,completed=[],unrun=list(ADVANCED_FATE_CASES),
                           cases={},method='mapped stele, attack and dodge input; commands only prepare/read fixtures and labelled boundary probes')
        self.root_out=out

    def setup(self):
        super().setup()
        self.rc(f'myvillage cultivation learn {self.s.username} myvillage:taxue_wuhen')

    def prepare_case(self):
        self.s.game.xdo('keyup','space','keyup','Alt_L','keyup','w','keyup','a','keyup','s','keyup','d',
                        'mouseup','1','mouseup','3')
        self.rc('tick rate 20')
        self.rc('kill @e[type=!minecraft:player]')
        self.poll(lambda:self.rc('execute if entity @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb]'),
                  lambda value:'passed' not in value,timeout=5,label='previous live fixtures cleared')
        self.home()
        self.rc('fill -12 -60 -12 12 -48 12 minecraft:air')
        self.rc('fill -12 -61 -12 12 -61 12 minecraft:grass_block')
        self.rc(f'gamemode survival {self.s.username}')
        self.rc(f'effect clear {self.s.username}')
        self.rc(f'effect give {self.s.username} minecraft:saturation infinite 255 true')
        self.home();self.heal()

    def run(self):
        from tools import advanced_fates_sword_heart, advanced_fates_wind, advanced_fates_fire
        handlers={'sword_heart':advanced_fates_sword_heart.run,
                  'wind_step':advanced_fates_wind.run,'fire_spirit':advanced_fates_fire.run}
        self.start();self.setup()
        for name in self.report['selected']:
            started=time.monotonic();self.out=self.root_out/name;self.out.mkdir(parents=True,exist_ok=True)
            self.report['unrun'].remove(name)
            try:
                self.prepare_case();handlers[name](self)
                self.report['completed'].append(name)
                self.report['cases'][name]={'result':'pass'}
            except Exception as error:
                self.report['failures'].append(name+': '+repr(error))
                self.report['cases'][name]={'result':'fail','error':repr(error)}
                print('CASE FAILED',name,repr(error),flush=True)
                if self.s is None:raise
                self.s.game.xdo('keyup','space','keyup','Alt_L','mouseup','1','mouseup','3')
                self.s.game.xdo('key','Escape');time.sleep(.3);self.s.game.recover_grab()
            finally:
                self.report['cases'][name]['wall_seconds']=round(time.monotonic()-started,3)
                self.save();self.out=self.root_out;self.save()

        if self.report['completed']==self.report['selected']:
            from tools.alchemy_quality_evidence import read_data
            from tools.combat_capture import scene
            from tools.combat_capture.dodge import dodge_events
            self.prepare_case();self.choose_focus('wind_step')
            self.equip('myvillage:qingfeng_sword');scene.ensure_cultivation(self.s)
            self.poll(self.state,lambda s:not s.get('wind_step',{}).get('air_spent',True)
                      and s['now']>=s.get('wind_step',{}).get('ready_at',0),label='landing before lifecycle flight')
            u=self.s.username
            # Explicit lifecycle fixture: slow falling keeps a real survival player airborne
            # through server/client restart. Ordinary jump behavior is tested in wind_step.run.
            self.rc(f'effect give {u} minecraft:slow_falling 1200 0 true')
            self.rc(f'tp {u} .5 150 .5 180 0')
            self.rc(f'myvillage cultivation qi fill {u}')
            self.s.game.key('Alt_L')
            before=self.poll(self.state,lambda s:s.get('wind_step',{}).get('air_spent',True)
                             and s.get('wind_step',{}).get('ready_at',0)>s['now'],label='mapped lifecycle air charge consumed')
            self.rc('save-all flush');self.stop();self.start(reuse=True)
            after=self.state();position=read_data(self.rc(f'data get entity {u} Pos'))
            self.check('restart_preserves_selected_fates',after['selected']==before['selected'],{'before':before,'after':after})
            for field,keys in [('sword_heart',('cooldown_ready_at',)),('wind_step',('ready_at',)),('fire_spirit',('deadlines',))]:
                a=before.get(field,{});b=after.get(field,{})
                self.check('restart_preserves_'+field+'_deadlines',all(a.get(k)==b.get(k) for k in keys),{'before':a,'after':b})
            self.check('restart_discards_short_opening',after.get('sword_heart',{}).get('opening_expires_at',0)==0,after)
            self.check('airborne_restart_does_not_refill_charge',position[1]>-50
                       and after.get('wind_step',{}).get('air_spent',True),{'pos':position,'state':after})
            log=cs.LOG_DIR/'server.log';offset=log.stat().st_size
            self.s.game.key('Alt_L')
            def decisions():
                with log.open('rb') as stream:
                    stream.seek(offset);return dodge_events(stream.read().decode(errors='replace').splitlines())
            events=self.poll(decisions,lambda rows:any(r['kind']=='rejected' for r in rows),label='no extra air dodge after restart')
            self.check('airborne_restart_repeat_is_rejected',any(r.get('reason')=='AIRBORNE' for r in events),events)
            self.rc(f'effect clear {u} minecraft:slow_falling');self.home()
            self.poll(self.state,lambda s:not s.get('wind_step',{}).get('air_spent',True),label='real supported landing after restart')


def main():
    p=argparse.ArgumentParser();p.add_argument('--cases');p.add_argument('--out',type=Path,default=ROOT/'out/preview/advanced-fates')
    args=p.parse_args();e=AdvancedEvidence(args.out,args.cases)
    try:e.run()
    except BaseException as error:e.report['failures'].append(type(error).__name__+': '+str(error));raise
    finally:
        e.out=e.root_out
        if e.s:e.stop()
        e.report['passed']=not e.report['failures'] and e.report['completed']==e.report['selected'];e.save()
    return 0 if e.report['passed'] else 1

if __name__=='__main__':raise SystemExit(main())
