#!/usr/bin/env python3
"""Three purple fates in an isolated Modal world: mapped selection, cast/use/hit and restart.

RCON creates/readbacks fixtures and applies explicitly labelled damage boundary probes.
No player save is edited. Software-rendered developer evidence is not owner acceptance.
"""
from __future__ import annotations
import argparse
import hashlib
import itertools
import json
import math
from pathlib import Path
import re
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.innate_fates_evidence import FateEvidence, ROOT
from tools.combat_capture import scene, session as cs
from tools.combat_capture.sword_qi import parse_events
from tools.alchemy_quality_evidence import read_data, SecondClient
from tools.realm_stats_evidence import close

from tools.modal_runner.remote import FATE_PURPLE_CASES as CASES, select_fate_purple_cases as selected_cases


def refund_is_once(events, fraction):
    releases = [r for r in events if r.get('result') == 'released']
    refunds = [r for r in events if r.get('result') == 'hit_refund']
    if len(releases) != 1 or len(refunds) != 1:
        return False
    paid = int(releases[0]['cost'])
    refund = refunds[0]
    return (refund['revision'] == releases[0]['revision'] and int(refund['paid']) == paid
            and int(refund['refund']) == math.floor(paid*fraction)
            and int(refund['recovered']) == int(refund['refund']) and refund['success'] == 'true')


class PurpleEvidence(FateEvidence):
    def __init__(self, out, cases=None):
        super().__init__(out)
        selected = selected_cases(cases)
        self.report.update(kind='purple_fates', selected=selected, completed=[], cases={},
                           unrun=list(CASES), owner_appearance='not_verified', owner_fps='not_verified',
                           method='mapped stele confirmation, right-mouse sword qi/pill use, left-mouse melee; RCON fixtures and labelled damage probes; two actual clients',
                           default_fate_rules=True)
        self.definitions = {'myvillage:'+p.stem: json.loads(p.read_text()) for p in
                            (ROOT/'src/main/resources/data/myvillage/myvillage/innate_fate').glob('*.json')}
        self.medicine = json.loads((ROOT/'src/main/resources/data/myvillage/alchemy/rules.json').read_text())

    def check(self, name, passed, detail):
        super().check(name, passed, detail)
        if not passed:
            raise AssertionError(name)

    def poll(self, getter, predicate, timeout=12, label='state'):
        deadline = time.monotonic()+timeout
        latest = None
        while time.monotonic() < deadline:
            latest = getter()
            if predicate(latest):
                return latest
            time.sleep(.2)
        raise TimeoutError(f'{label}: {latest}')

    def start(self, reuse=False, language='en_us'):
        if ROOT != Path('/workspace/repo'):
            raise RuntimeError('Gameplay runs only in the disposable Modal checkout')
        cs.merge_options(cs.CLIENT_DIR/'options.txt', {'key_key.use': 'key.mouse.right'})
        super().start(reuse, language)
        self.report['source_commit'] = json.loads(Path('/workspace/request.json').read_text())['commit']
        self.report['jar_sha256'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                                    for p in (ROOT/'build/libs').glob('*.jar')}

    def setup(self):
        u = self.s.username
        for cmd in (*scene.world_rules(), *scene.player_setup(u, 'minecraft:air'),
                    'gamerule naturalRegeneration false', 'myvillage world pause',
                    f'myvillage cultivation initiate {u}',
                    f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_1',
                    f'myvillage cultivation learn {u} myvillage:jianqi_jue'):
            self.rc(cmd)
        # The inheritance command does not awaken a missing root. Establish it through
        # the actual testing-stele input before the targeted candidate fixture can roll.
        self.open_stele(); self.leave_stele()
        self.check('mapped_root_prepared', self.root() is not None, self.root())
        self.rc(f'myvillage cultivation initiate {u}')
        self.home()

    def home(self):
        self.rc(f'tp {self.s.username} .5 -60 .5 0 0')
        self.rc('setblock 0 -59 2 minecraft:air')
        self.rc('setblock 0 -60 2 minecraft:air')
        self.s.game.require_ingame('purple fate probe')

    def choose_focus(self, focus):
        self.reset()
        wanted = 'myvillage:'+focus
        for _ in range(160):
            self.rc(f'myvillage cultivation fate roll {self.s.username}')
            time.sleep(.22)
            state = self.state()
            if len(state['candidates']) != 6:
                raise RuntimeError('Candidate preparation rejected: '+str(state))
            if wanted not in state['candidates']:
                continue
            index = state['candidates'].index(wanted)
            ordinary = [i for i, v in enumerate(state['candidates']) if self.definitions[v]['tier'] == 0]
            for pair in itertools.combinations(ordinary, 2):
                slots = (index, *pair)
                defs = [self.definitions[state['candidates'][i]] for i in slots]
                if len({d['group'] for d in defs}) != 3 or sum(d['cost'] for d in defs) > state['budget']:
                    continue
                self.open_stele()
                self.press('continue')
                for i in slots:
                    self.press('card_'+str(i))
                self.press('confirm'); self.press('confirm')
                result = self.state()
                self.check('mapped_selection_'+focus, result['confirmed'] and wanted in result['selected'], result)
                self.capture(f'{focus}-selected.png')
                self.leave_stele(); self.home()
                return result
        raise RuntimeError('Focused candidate fixture not drawn in bounded rounds: '+focus)

    def power(self):
        reply = self.rc(f'myvillage cultivation qi {self.s.username}')
        fields = dict(re.findall(r'(current|cap|recovery_ticks)=(\d+)', reply))
        if set(fields) != {'current', 'cap', 'recovery_ticks'}:
            raise ValueError('Missing qi readback: '+reply)
        return {k: int(v) for k, v in fields.items()}

    def own_state(self, field):
        defaults = {'pill_aegis': {'shield': 0, 'shield_expires_at': 0, 'cooldown_ready_at': 0},
                    'tempered_bones': {'damage': 0, 'ready': False}}
        return {**defaults[field], **self.state().get(field, {})}

    def equip(self, item, count=1):
        self.s.game.key('1')
        self.rc(f'item replace entity {self.s.username} hotbar.0 with {item} {count}')
        time.sleep(.3)

    def inventory(self):
        return read_data(self.rc(f'data get entity {self.s.username} Inventory'))

    def held_count(self):
        return sum(s.get('count', 0) for s in self.inventory() if s.get('Slot') == 0)

    def dummy(self, tag='purple_target', pos=(.5, -60, 6), resistance=1):
        self.rc(f'kill @e[tag={tag}]')
        x,y,z = pos
        self.rc(f'summon minecraft:husk {x} {y} {z} '
                '{NoAI:1b,PersistenceRequired:1b,Silent:1b,Health:1000f,Tags:["'+tag+'"],attributes:['
                '{id:"minecraft:generic.max_health",base:1000d},'
                '{id:"minecraft:generic.armor",base:0d},'
                '{id:"minecraft:generic.knockback_resistance",base:'+str(resistance)+'d}]}')
        target = '@e[tag='+tag+',limit=1]'
        self.poll(lambda: self.hp(target), lambda n: n > 0, label='dummy created')
        return target

    def cast(self, label, forged=False, hold=.4):
        self.home()
        spec = 'myvillage:qingfeng_sword'
        if forged:
            spec += '[myvillage:forge_mark={trait:"channeling",quality:4}]'
        self.equip(spec)
        scene.ensure_cultivation(self.s)
        self.rc(f'myvillage cultivation qi fill {self.s.username}')
        log = cs.LOG_DIR/'server.log'; offset = log.stat().st_size
        self.s.game.require_ingame('mapped sword qi')
        self.s.game.xdo('mousedown', '3'); time.sleep(hold); self.s.game.xdo('mouseup', '3')
        def events():
            with log.open('rb') as f:
                f.seek(offset); return parse_events(f.read().decode(errors='replace').splitlines())
        self.poll(events, lambda rows: any(r.get('result') == 'released' for r in rows), label='mapped cast released')
        rows = self.poll(events, lambda rows: any(r.get('result') == 'wave_stop' for r in rows), label='wave finished')
        self.report.setdefault('casts', {})[label] = rows
        self.save()
        time.sleep(.6)
        return rows

    def meridians(self):
        u = self.s.username
        self.reset(); base = self.power()['cap']
        self.rc(f'myvillage cultivation qi set {u} 0')
        before = self.power()
        self.choose_focus('open_meridians')
        after = self.power(); e = self.definitions['myvillage:open_meridians']['effects']
        self.check('effective_cap', after['cap'] == math.floor(base*(1+e['power_cap_bonus'])), {'base':base,'after':after})
        self.check('capacity_does_not_fill', after['current'] < after['cap'], {'before':before,'after':after})
        self.panel(); self.capture('meridians-h-cap.png'); self.close_panel()
        a = self.dummy('purple_a',(.5,-60,6)); b = self.dummy('purple_b',(.5,-60,9))
        health = [self.hp(a), self.hp(b)]
        events = self.cast('multiple_targets')
        self.check('mapped_multitarget_refund_once', refund_is_once(events,e['skill_refund_fraction'])
                   and self.hp(a) < health[0] and self.hp(b) < health[1], events)
        events = self.cast('forged_paid_cost',forged=True,hold=2.4)
        self.check('forged_refund_actual_payment', refund_is_once(events,e['skill_refund_fraction']), events)
        self.rc('kill @e[tag=purple_a]'); self.rc('kill @e[tag=purple_b]')
        events = self.cast('miss')
        self.check('miss_grants_no_refund', not any(r.get('result') == 'hit_refund' for r in events), events)
        self.rc(f'myvillage cultivation qi fill {u}'); self.reset()
        self.check('removing_fate_clamps_cap', self.power()['cap']==base and self.power()['current']<=base, self.power())

    def use_pill(self, quality=1, count=2):
        spec = 'myvillage:pill_yangyuan'+(f'[myvillage:pill_quality={quality}]' if quality>1 else '')
        self.equip(spec,count)
        self.s.game.require_ingame('mapped pill use')
        self.s.game.xdo('mousedown','3')
        try:
            self.poll(self.held_count,lambda n:n==count-1,timeout=6,label='one real pill consumed')
        finally:
            self.s.game.xdo('mouseup','3')
        return self.own_state('pill_aegis')

    def wait_medicine_ready(self):
        # Runtime expiry is observed, never edited; both item and shield cooldowns continue normally.
        def ready():
            state = self.state(); aegis = {**{'cooldown_ready_at':0}, **state.get('pill_aegis',{})}
            medicine = read_data(self.rc(f'data get entity {self.s.username}'))
            current = medicine.get('ForgeData',{}).get('myvillage_medicine',{})
            return state['now'] >= aegis['cooldown_ready_at'] and current.get('cooldown_ticks',0)==0
        self.poll(ready,bool,timeout=38,label='medicine and shield cooldown')

    def pill_aegis(self):
        self.choose_focus('pill_aegis'); u=self.s.username; self.heal()
        maximum=self.maximum(); e=self.definitions['myvillage:pill_aegis']['effects']
        self.rc(f'damage {u} {maximum*.5} minecraft:generic'); time.sleep(.6)
        hp=self.hp(); shield=self.use_pill(quality=2)
        self.check('mapped_pill_grants_independent_shield',close(shield['shield'],maximum*e['pill_shield_fraction'],.01),shield)
        self.capture('pill-aegis-granted.png')
        # Poll the first actual treatment interval, then measure at most one additional tick.
        healed=self.poll(self.hp,lambda n:n>hp+.01,timeout=3,label='quality healing tick')
        expected=maximum*self.medicine['effects']['yangyuan']['heal_fraction']*self.medicine['quality']['strength_multipliers'][1]*(1+e['pill_healing_bonus'])
        self.check('quality_and_fate_healing_once',close(healed-hp,expected,.03),{'gain':healed-hp,'expected':expected})
        self.rc(f'effect give {u} minecraft:absorption 30 1 true')
        absorption=self.number(f'data get entity {u} AbsorptionAmount')
        before=self.own_state('pill_aegis'); self.rc(f'damage {u} 2 minecraft:generic'); time.sleep(.2)
        after=self.own_state('pill_aegis')
        self.check('shield_before_vanilla_absorption',close(before['shield']-after['shield'],2,.02)
                   and close(self.number(f'data get entity {u} AbsorptionAmount'),absorption,.02),{'before':before,'after':after,'absorption':absorption})
        count=self.held_count(); self.s.game.xdo('click','3'); time.sleep(.4)
        self.check('cooldown_rejects_without_consumption',self.held_count()==count and self.own_state('pill_aegis')['cooldown_ready_at']==shield['cooldown_ready_at'],self.own_state('pill_aegis'))
        self.poll(lambda:self.own_state('pill_aegis'),lambda s:s['shield']==0,timeout=10,label='shield expires')
        self.check('expiry_preserves_other_absorption',close(self.number(f'data get entity {u} AbsorptionAmount'),absorption,.02),self.own_state('pill_aegis'))
        self.rc(f'effect clear {u} minecraft:absorption')

    def bones_charge(self):
        u=self.s.username
        self.heal()
        attacker=self.dummy('purple_attacker',(8,-60,0))
        e=self.definitions['myvillage:tempered_bones']['effects']
        amount=self.maximum()*e['bones_threshold']+.01
        self.rc(f'damage {u} {amount} minecraft:mob_attack by {attacker}')
        result=self.poll(lambda:self.own_state('tempered_bones'),lambda s:s['ready'],label='labelled hostile damage probe')
        self.capture('bones-ready.png')
        return result

    def melee(self,target,item='minecraft:iron_sword'):
        self.home(); self.equip(item)
        before=self.hp(target); time.sleep(1.1)
        self.s.game.xdo('click','1')
        self.poll(lambda:self.hp(target),lambda n:n<before,timeout=6,label='mapped melee hits')
        time.sleep(.8)
        return before-self.hp(target)

    def tempered_bones(self):
        self.choose_focus('tempered_bones'); u=self.s.username
        self.heal(); self.rc(f'damage {u} 2 minecraft:generic'); time.sleep(.6)
        self.check('environment_does_not_charge',self.own_state('tempered_bones')['damage']==0,self.own_state('tempered_bones'))
        self.bones_charge()
        target=self.dummy('purple_target',(.5,-60,2.5)); bystander=self.dummy('purple_bystander',(2.5,-60,.5),0)
        baseline_pos=read_data(self.rc(f'data get entity {bystander} Pos'))
        before=self.own_state('tempered_bones'); events=self.cast('bones_qi')
        self.check('sword_qi_does_not_spend_bones',self.own_state('tempered_bones')['ready'],{'state':before,'cast':events})
        # Compare two identical vanilla hits against armor-free stationary targets.
        target=self.dummy('purple_target',(.5,-60,2.5))
        extra=self.melee(target)
        self.check('melee_consumes_ready_once',not self.own_state('tempered_bones')['ready'],self.own_state('tempered_bones'))
        moved=read_data(self.rc(f'data get entity {bystander} Pos'))
        self.check('retaliation_pushes_nearby_enemy',sum((a-b)**2 for a,b in zip(baseline_pos,moved))>.04,{'before':baseline_pos,'after':moved})
        target=self.dummy('purple_target',(.5,-60,2.5)); normal=self.melee(target)
        weapon=self.number(f'attribute {u} minecraft:generic.attack_damage get')
        self.check('one_weapon_base_bonus',close(extra-normal,weapon,.1),{'enhanced':extra,'normal':normal,'weapon':weapon})
        # A second actual player attacks through mapped input; both allocations remain private.
        with SecondClient(self) as guest:
            v,g=guest.username,guest.game
            self.rc(f'gamemode survival {v}')
            self.rc(f'item replace entity {v} weapon.mainhand with minecraft:iron_sword')
            self.heal(); self.home()
            self.rc(f'tp {v} .5 -60 2.5 180 0')
            time.sleep(1.3); before_hp=self.hp()
            g.require_ingame('second player mapped melee'); g.xdo('click','1')
            self.poll(self.hp,lambda n:n<before_hp,timeout=5,label='second player real hit')
            loss=before_hp-self.hp(); charge=self.own_state('tempered_bones')
            self.check('mapped_pvp_health_loss_charges_owner',close(charge['damage'],min(loss,self.maximum()*.3),.1),{'loss':loss,'charge':charge})
            other=self.one(f'myvillage cultivation fate status {v}','FATES')
            self.check('other_player_does_not_inherit_charge',not other.get('tempered_bones',{}).get('ready',False),other)
        self.report['multiplayer']='two_actual_clients_mapped_pvp_and_private_fate_state'

    def lifecycle(self):
        self.choose_focus('pill_aegis'); self.wait_medicine_ready(); u=self.s.username
        self.heal(); self.rc(f'damage {u} {self.maximum()*.5} minecraft:generic'); time.sleep(.6)
        shield=self.use_pill(); selected=self.state()['selected']
        self.rc(f'kill {u}')
        self.poll(self.hp,lambda n:n>0,timeout=8,label='normal respawn')
        after=self.own_state('pill_aegis')
        self.check('death_clears_shield_keeps_cooldown_and_choices',after['shield']==0 and after['cooldown_ready_at']==shield['cooldown_ready_at'] and self.state()['selected']==selected,{'before':shield,'after':after})
        self.home(); self.rc('save-all flush')
        before=self.state()
        self.stop(); self.start(reuse=True)
        after=self.state()
        self.check('restart_preserves_choices_and_cooldown',after['selected']==before['selected'] and after.get('pill_aegis',{}).get('cooldown_ready_at',0)==before['pill_aegis']['cooldown_ready_at'],{'before':before,'after':after})
        self.check('restart_does_not_grant_new_shield',self.own_state('pill_aegis')['shield']==0,self.own_state('pill_aegis'))

    def run(self):
        self.start(); self.setup()
        for name in self.report['selected']:
            self.report['unrun'].remove(name); started=time.monotonic()
            try:
                getattr(self,name)()
                self.report['completed'].append(name)
                self.report['cases'][name]={'result':'pass'}
            except Exception as error:
                self.report['failures'].append(name+': '+str(error))
                self.report['cases'][name]={'result':'fail','error':repr(error)}
                print('CASE FAILED',name,repr(error),flush=True)
                if self.s is None:
                    raise
                self.s.game.xdo('mouseup','3','mouseup','1')
                self.s.game.xdo('key','Escape'); time.sleep(.3); self.s.game.recover_grab()
            finally:
                self.report['cases'][name]['wall_seconds']=round(time.monotonic()-started,3); self.save()


def main():
    p=argparse.ArgumentParser(); p.add_argument('--out',type=Path,default=ROOT/'out/preview/fates-purple');p.add_argument('--cases')
    args=p.parse_args();e=PurpleEvidence(args.out,args.cases)
    try:
        e.run()
    finally:
        if e.s: e.stop()
        e.save()
    return 1 if e.report['failures'] else 0


if __name__=='__main__':
    raise SystemExit(main())
