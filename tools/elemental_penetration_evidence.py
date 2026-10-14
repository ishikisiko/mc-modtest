#!/usr/bin/env python3
"""Independent penetration attributes: real client, admin fixtures and save/restart.

Run clean stage-1 acceptance first. Only physical/fire have live source mappings;
other elemental attacks are not introduced or claimed by this evidence.
"""
from __future__ import annotations
import argparse
import datetime as dt
import json
from pathlib import Path
import sys
import time

ROOT=Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path: sys.path.insert(0,str(ROOT))
from tools.combat_capture import procs,scene,session as cs
from tools.realm_stats_evidence import close
from tools.realm_armor_evidence import ArmorEvidence,TARGET

CHANNELS=('physical','metal','wood','water','fire','earth')
IDS={channel:'myvillage:'+('resistance_penetration' if channel=='physical' else channel+'_resistance_penetration') for channel in CHANNELS}


class PenetrationEvidence(ArmorEvidence):
    def __init__(self,out):
        super().__init__(out)
        self.report.update(kind='independent_elemental_penetration',
                           metal_wood_water_earth_attack_runtime='not_implemented',
                           method='physical client mapped melee/sword qi; RCON explicit attribute/damage fixtures and readback; direct restart; no DevBridge')

    def start(self,reuse=False):
        super().start(reuse)
        if reuse:self.report['phases'][-1]['phase']='restart_setup'
        self.save()

    def set_pen(self,channel,value):
        self.rc(f'attribute {self.s.username} {IDS[channel]} base set {value}')
        actual=self.number(f'attribute {self.s.username} {IDS[channel]} base get')
        if not close(actual,value):raise RuntimeError(f'{channel} base fixture mismatch: {actual} != {value}')

    def values(self):
        info=self.one(f'myvillage stats info {self.s.username}','STATS_INFO')
        values={channel:info['attributes'][attr] for channel,attr in IDS.items()}
        return info,values

    def target(self,channel='physical',resistance=.5,armor=0,z=3):
        before=self.fixture(armor,8 if armor else 0,realm=None,z=z)
        self.rc(f'attribute {TARGET} myvillage:{channel}_resistance base set {resistance}')
        return before

    def probe(self,name,channel,penetration,*,attacker=True,source=None,armor=0):
        before=self.target(channel,armor=armor)
        source=source or ('minecraft:mob_attack' if channel=='physical' else 'minecraft:in_fire')
        self.rc(f'damage {TARGET} 10 {source}'+(f' by {self.s.username}' if attacker else ''))
        trace,after=self.trace(),self.hp()
        # The armor fixture uses the established scale-one 20/8 result, not normalized damage.
        post=3 if armor==20 else 10
        expected=post*(1-max(0,.5-penetration))
        mitigation=trace.get('mitigation') or {}
        self.check(name,trace.get('type')==channel and close(mitigation.get('penetration'),penetration)
                   and close(trace.get('postVanilla'),post) and close(before-after,expected,.005),
                   {'channel':channel,'selected_penetration':penetration,'expected_loss':expected,
                    'loss':before-after,'trace':trace})

    def mapped(self,qi=False):
        user=self.s.username
        before=self.target(z=6.5 if qi else 3)
        self.rc(f'tp {user} .5 -60 .5 0 0')
        self.rc(f'item replace entity {user} hotbar.0 with myvillage:qingfeng_sword')
        self.s.game.key('1');scene.ensure_cultivation(self.s)
        if qi:
            self.rc(f'myvillage cultivation learn {user} myvillage:jianqi_jue')
            self.rc(f'myvillage cultivation qi fill {user}')
        self.s.game.require_ingame('typed penetration mapped attack')
        if qi:
            self.s.game.xdo('mousedown','3')
            try:time.sleep(.35)
            finally:self.s.game.xdo('mouseup','3')
        else:self.s.game.click(.06)
        time.sleep(2.5)
        trace,after=self.trace(),self.hp()
        requested=(trace.get('outgoing') or {}).get('requestedDamage',0)
        expected=requested*.7
        self.check('mapped_'+('sword_qi' if qi else 'melee')+'_physical_only',requested>0
                   and trace.get('type')=='physical'
                   and close((trace.get('mitigation') or {}).get('penetration'),.2)
                   and close(before-after,expected,.005),{'loss':before-after,'expected':expected,'trace':trace})

    def initial(self):
        started=time.monotonic();user=self.s.username
        for command in (*scene.world_rules(),*scene.player_setup(user,'myvillage:qingfeng_sword'),
                        'gamerule naturalRegeneration false','myvillage world pause',
                        'forceload add -16 -16 32 32',f'myvillage cultivation awaken {user}',
                        f'myvillage cultivation initiate {user}',
                        f'myvillage cultivation setrealm {user} myvillage:golden_core myvillage:golden_late'):
            self.rc(command)
        info,values=self.values()
        self.check('fresh_six_channel_defaults',all(close(v,0) for v in values.values()),values)
        self.probe('fire_default','fire',0)
        self.set_pen('physical',.2)
        self.probe('legacy_value_is_physical','physical',.2)
        self.probe('legacy_value_does_not_penetrate_fire','fire',0)
        self.set_pen('fire',.3)
        self.probe('fire_uses_fire_value','fire',.3)
        self.probe('fire_value_does_not_penetrate_physical','physical',.2)
        for channel in ('metal','wood','water','earth'):
            self.set_pen(channel,.8)
            self.probe(channel+'_does_not_change_fire','fire',.3)
            self.probe(channel+'_does_not_change_physical','physical',.2)
        self.set_pen('fire',.8)
        self.probe('penetration_cannot_make_negative_resistance','fire',.8)
        self.probe('environmental_fire_has_no_attacker_penetration','fire',0,attacker=False)
        self.probe('armor_remains_before_typed_resistance','physical',.2,armor=20)
        for name,source in [('unmapped','minecraft:generic'),('bypassing','minecraft:out_of_world')]:
            before=self.target('physical')
            self.rc(f'damage {TARGET} 10 {source} by {user}')
            trace,after=self.trace(),self.hp()
            self.check(name+'_source_remains_untouched',trace.get('type')=='untouched'
                       and trace.get('mitigation') is None and close(before-after,10),trace)
        maximum=json.loads((ROOT/'src/main/resources/data/myvillage/stats/rules.json').read_text())['limits']['max_penetration']
        self.set_pen('fire',maximum+.1)
        _,values=self.values();self.check('configured_upper_bound',close(values['fire'],maximum),values)
        self.set_pen('fire',-.1)
        _,values=self.values();self.check('lower_bound_zero',close(values['fire'],0),values)
        self.set_pen('fire',.3)
        self.mapped();self.mapped(qi=True)

        # Distinct bases and permanent modifiers must survive native attribute persistence.
        for index,channel in enumerate(CHANNELS,1):
            self.set_pen(channel,index*.1)
            self.rc(f'attribute {user} {IDS[channel]} modifier add myvillage:penetration_fixture_{channel} .01 add_value')
        info,values=self.values()
        expected={channel:(index+1)*.1+.01 for index,channel in enumerate(CHANNELS)}
        self.check('six_independent_base_and_modifier_values',all(close(values[c],expected[c]) for c in CHANNELS),
                   {'values':values,'expected':expected})
        self.report['attributes_before_restart']=info
        self.rc('save-all flush')
        self.report['phases'].append({'phase':'initial_checks','wall_s':round(time.monotonic()-started,3)})
        self.save()

    def restored(self):
        started=time.monotonic()
        info,values=self.values();before=self.report['attributes_before_restart']
        self.check('all_six_attributes_survive_restart',info['entityId']==before['entityId']
                   and all(close(values[c],before['attributes'][IDS[c]]) for c in CHANNELS)
                   and all(info['sources'][attr]==before['sources'][attr] for attr in IDS.values()),
                   {'before':{c:before['attributes'][a] for c,a in IDS.items()},'after':values})
        self.probe('restored_physical_uses_legacy_only','physical',.11)
        self.probe('restored_fire_uses_its_own_value','fire',.51)
        self.report['phases'].append({'phase':'restart_checks','wall_s':round(time.monotonic()-started,3)})
        self.save()


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,default=ROOT/'out/preview/elemental_penetration')
    args=parser.parse_args(argv)
    if not procs.lock_is_free(procs.default_lock_path(ROOT)):raise SystemExit('Heavy-work lock held')
    evidence=PenetrationEvidence(args.out)
    try:
        evidence.start();evidence.initial();evidence.stop();evidence.start(reuse=True);evidence.restored()
    except Exception as exc:
        evidence.report['failures'].append(f'{type(exc).__name__}: {exc}');raise
    finally:
        try:
            if evidence.s is not None or cs.supervisor_alive(cs.read_state()):evidence.stop()
        finally:
            evidence.report['finished_utc']=dt.datetime.now(dt.timezone.utc).isoformat()
            evidence.report['pass']=not evidence.report['failures'] and bool(evidence.report['checks']) and all(c['pass'] for c in evidence.report['checks'])
            evidence.save()
    return 0 if evidence.report['pass'] else 1

if __name__=='__main__':raise SystemExit(main())
