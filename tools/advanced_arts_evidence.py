#!/usr/bin/env python3
"""Scoped advanced-art integration checks; real mapped keys in an isolated Modal world."""
from pathlib import Path
import json
import sys
import time
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.fates_purple_evidence import PurpleEvidence, ROOT
from tools.combat_capture import scene, session as cs
from tools.alchemy_quality_evidence import SecondClient, read_data


class ArtsEvidence(PurpleEvidence):
    def __init__(self, out):
        super().__init__(out)
        self.arts = json.loads((ROOT/'tools/advanced_arts/arts.json').read_text())['arts']
        selected = ['learning_and_six_casts', 'refusals_and_collision', 'guard_and_cancel', 'multiplayer_and_restart']
        self.report.update(kind='advanced_weapon_arts', selected=selected, completed=[], unrun=selected.copy(),
                           method='mapped right-click manual study and Z/C attacks; RCON fixtures/readbacks and labelled incoming-damage probes',
                           owner_feel='not_verified', owner_sound='not_verified', owner_balance='not_verified')
        self.ready = {}

    def start(self, reuse=False, language='en_us'):
        cs.merge_options(cs.CLIENT_DIR/'options.txt', {
            'key_key.myvillage.art_primary':'key.keyboard.z',
            'key_key.myvillage.art_secondary':'key.keyboard.c'})
        super().start(reuse, language)

    def logs(self, offset=0):
        with (cs.LOG_DIR/'server.log').open('rb') as stream:
            stream.seek(offset)
            return '\n'.join(line for line in stream.read().decode(errors='replace').splitlines() if 'ADVANCED_ART' in line)

    def offset(self):
        return (cs.LOG_DIR/'server.log').stat().st_size

    def learned(self):
        return self.rc(f'data get entity {self.s.username} "neoforge:attachments"."myvillage:cultivation_profile".learned_techniques')

    def weapon(self, art):
        return {'sword':'qingfeng_sword', 'spear':'lingxiao_spear', 'fist':'xuantie_gauntlet'}[art['family']]

    def prepare(self, art):
        self.home()
        self.equip('myvillage:'+self.weapon(art))
        scene.ensure_cultivation(self.s)
        self.rc(f'myvillage cultivation qi fill {self.s.username}')
        self.poll(lambda:self.state()['now'], lambda t:t>=self.ready.get(art['id'], 0),
                  timeout=65, label='authored cooldown elapsed')

    def press_art(self, art):
        mark = self.offset()
        now = self.state()['now']
        self.s.game.key('z' if art['slot']==0 else 'c')
        self.poll(lambda:self.logs(mark), lambda s:f'result=start art={art["id"]}' in s, label='mapped art starts')
        self.ready[art['id']] = now+art['cooldown_ticks']+5
        return mark

    def finish(self, art, mark):
        log = self.poll(lambda:self.logs(mark), lambda s:f'result=stop art={art["id"]}' in s,
                        timeout=20, label='art terminates')
        self.report.setdefault('casts', []).append(log)
        return log

    def cast_art(self, art, target=None, shot=False):
        before = self.hp(target) if target else None
        power = self.power()['current']
        mark = self.press_art(art)
        after = self.power()['current']
        if shot:self.capture(art['id'].split(':')[1]+'.png')
        log = self.finish(art, mark)
        self.check(art['id']+'_completes', 'reason=COMPLETED' in log, log)
        self.check(art['id']+'_one_payment', log.count('result=start')==1 and f'cost={art["qi_cost"]}' in log
                   and power-art['qi_cost'] <= after <= power-art['qi_cost']+2,
                   {'before':power, 'after_start':after, 'cost':art['qi_cost'], 'log':log})
        if target:self.check(art['id']+'_hits', self.hp(target)<before, {'before':before,'after':self.hp(target)})
        return log

    def refused(self, art, label):
        mark=self.offset();before=self.power()['current']
        self.s.game.key('z' if art['slot']==0 else 'c')
        time.sleep(.7)
        self.check(label, 'result=start' not in self.logs(mark) and self.power()['current']>=before,
                   {'log':self.logs(mark),'qi_before':before,'qi_after':self.power()['current']})

    def done(self, case):
        self.report['completed'].append(case);self.report['unrun'].remove(case);self.save()

    def run(self):
        self.start();self.setup();u=self.s.username
        self.rc('fill -12 -61 -12 12 -61 12 minecraft:stone')
        art=self.arts[0]
        self.prepare(art);self.refused(art,'unlearned_refused')
        self.rc(f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_4')
        # Each actual book starts with zero comprehension and is consumed by ordinary study.
        for art in self.arts:
            self.home();self.s.game.key('1')
            reply=self.rc(f'item replace entity {u} hotbar.0 with myvillage:manual_active_xuan[myvillage:technique="{art["id"]}"]')
            self.check(art['id']+'_book_fixture', 'Replaced a slot' in reply, reply)
            time.sleep(.3)
            self.s.game.xdo('click','3')
            self.poll(self.learned, lambda s:art['id'] in s, timeout=90, label='mapped book learning '+art['id'])
            self.check(art['id']+'_book_learned',art['id'] in self.learned(),self.inventory())
            self.prepare(art)
            target=self.dummy('arts_target',(.5,-60,4 if art['family']=='spear' else 2.8))
            self.cast_art(art,target,shot=True)
            self.refused(art,art['id']+'_cooldown_refused')
            self.rc('kill @e[tag=arts_target]')
        self.done('learning_and_six_casts')

        art=self.arts[0];self.prepare(art)
        self.rc(f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_1')
        self.refused(art,'learned_but_low_realm_refused')
        self.rc(f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_4')
        self.rc(f'myvillage cultivation qi set {u} 0');self.refused(art,'insufficient_power_refused')
        self.prepare(art)
        wall=self.dummy('arts_wall',(.5,-60,4))
        ally=self.dummy('arts_ally',(1.5,-60,2.5))
        self.rc('team add arts_allies');self.rc('team modify arts_allies friendlyFire false')
        self.rc(f'team join arts_allies {u}');self.rc('team join arts_allies @e[tag=arts_ally]')
        self.rc('fill -2 -60 3 3 -56 3 minecraft:stone')
        before=[self.hp(wall),self.hp(ally)]
        self.cast_art(art)
        self.check('wall_and_allied_targets_untouched',[self.hp(wall),self.hp(ally)]==before,before)
        self.rc('fill -2 -60 3 3 -56 3 minecraft:air');self.rc('team remove arts_allies')
        self.rc('kill @e[tag=arts_wall]');self.rc('kill @e[tag=arts_ally]')
        self.done('refusals_and_collision')

        guard=self.arts[1];self.prepare(guard);self.heal()
        front=self.dummy('arts_front',(.5,-60,2.8))
        # Slower tick rate exposes the short guard window; damage is a labelled boundary probe.
        self.rc('tick rate 5');mark=self.press_art(guard);hp=self.hp()
        self.rc(f'damage {u} 4 minecraft:mob_attack by {front}')
        loss=hp-self.hp()
        log=self.finish(guard,mark);self.rc('tick rate 20')
        self.check('front_guard_reduces_and_preserves_counter',0<loss<4 and 'reason=COMPLETED' in log,
                   {'health_loss':loss,'log':log,'probe':'RCON incoming damage during mapped guard'})
        self.rc('kill @e[tag=arts_front]')
        self.prepare(art);mark=self.press_art(art);self.s.game.key('2')
        log=self.finish(art,mark)
        self.check('mapped_weapon_swap_cancels','reason=WEAPON_CHANGED' in log,log)
        self.s.game.key('1');time.sleep(.7);self.refused(art,'swap_does_not_reset_cooldown')
        self.done('guard_and_cancel')

        with SecondClient(self) as guest:
            self.rc(f'tp {guest.username} 1.5 -60 2.5 180 0')
            self.rc('team add arts_party');self.rc('team modify arts_party friendlyFire false')
            self.rc(f'team join arts_party {u}');self.rc(f'team join arts_party {guest.username}')
            before=self.hp(guest.username)
            self.prepare(art);target=self.dummy('arts_multiplayer',(.5,-60,2.8))
            self.cast_art(art,target)
            self.check('second_client_allied_player_untouched',self.hp(guest.username)==before,
                       {'before':before,'after':self.hp(guest.username)})
            self.check('two_clients_remain_connected',u in self.rc('list') and guest.username in self.rc('list'),self.rc('list'))
            self.report['multiplayer']='two actual clients; mapped cast and allied-player protection only'
            self.rc('team remove arts_party');self.rc('kill @e[tag=arts_multiplayer]')
        saved=self.learned();self.rc('save-all flush');self.stop();self.start(reuse=True)
        self.check('restart_preserves_six_learned_arts',all(a['id'] in self.learned() for a in self.arts),saved)
        self.prepare(art);self.cast_art(art)
        target=self.dummy('arts_basic',(.5,-60,2.5));self.home();before=self.hp(target)
        self.s.game.click(.06)
        self.poll(lambda:self.hp(target),lambda hp:hp<before,label='basic attack after art')
        self.check('basic_melee_after_art_works',self.hp(target)<before,{'before':before,'after':self.hp(target)})
        self.done('multiplayer_and_restart')


def main():
    e=ArtsEvidence(ROOT/'out/preview/advanced-arts')
    try:e.run()
    except BaseException as error:
        e.report['failures'].append(type(error).__name__+': '+str(error));raise
    finally:
        if e.s:e.stop()
        e.report['passed']=not e.report['failures'] and e.report['completed']==e.report['selected'];e.save()
    return 0 if e.report['passed'] else 1

if __name__=='__main__':raise SystemExit(main())
