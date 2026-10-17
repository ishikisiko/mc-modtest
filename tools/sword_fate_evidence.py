#!/usr/bin/env python3
"""Focused sword-fate evidence using real mapped attacks in an isolated Modal world."""
from pathlib import Path
import json
import re
import sys
import time
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.fates_purple_evidence import PurpleEvidence, ROOT
from tools.alchemy_quality_evidence import SecondClient
from tools.combat_capture import scene, session as cs


class SwordEvidence(PurpleEvidence):
    def __init__(self, out):
        super().__init__(out)
        self.report.update(kind='sword_obsession', selected=['sword_obsession'], completed=[], unrun=['sword_obsession'])
        self.style=json.loads((ROOT/'src/main/resources/data/myvillage/combat/style/basic_sword.json').read_text())

    def logs(self, offset=0):
        with (cs.LOG_DIR/'server.log').open('rb') as stream:
            stream.seek(offset)
            return stream.read().decode(errors='replace')

    def offset(self):
        return (cs.LOG_DIR/'server.log').stat().st_size

    def combo(self, target):
        losses=[]; states=[]
        for move in self.style['moves']:
            self.home()
            before=self.hp(target)
            self.s.game.click(.06)
            self.poll(lambda:self.hp(target),lambda hp:hp<before,timeout=4,label=move['id']+' mapped hit')
            # Complete recovery before the next click, inside the authored combo timeout.
            time.sleep((move['total_ticks']-move['active_ticks'][0]+1)/20)
            losses.append(before-self.hp(target));states.append(self.state()['sword_stacks'])
        return losses,states

    def run(self):
        self.start();self.setup();u=self.s.username
        self.equip('myvillage:qingfeng_sword');scene.ensure_cultivation(self.s)
        near=self.dummy('sword_near',(.5,-60,2.5))
        baseline,_=self.combo(near)
        self.report['baseline_damage']=baseline
        self.rc('kill @e[tag=sword_near]')
        self.poll(lambda:self.rc('execute if entity @e[tag=sword_near]'),
                  lambda reply:'passed' not in reply,timeout=4,label='stele sightline cleared')
        self.choose_focus('sword_obsession')
        self.equip('myvillage:qingfeng_sword');scene.ensure_cultivation(self.s)
        far=self.dummy('sword_far',(.5,-60,8))
        near=self.dummy('sword_near',(.5,-60,2.5))
        start=self.offset();far_before=self.hp(far);qi_before=self.power()['current']
        enhanced,stacks=self.combo(near)
        log=self.logs(start);self.report['combo_log']=log
        self.check('mapped_five_move_streak',stacks==[1,2,3,4,5],stacks)
        bonus=self.definitions['myvillage:sword_obsession']['effects']['sword_stack_bonus']
        expected=[baseline[i]*(1+i*bonus) for i in range(4)]
        self.check('damage_uses_prior_stacks_once',all(abs(enhanced[i]-expected[i])<.1 for i in range(4)),
                   {'baseline':baseline,'enhanced':enhanced,'expected_first_four':expected})
        releases=re.findall(r'DERIVED_QI .*result=released wave=(\d+)',log)
        self.check('finisher_launches_one_independent_wave',len(releases)==1 and 'result=hit' in log,log)
        self.check('passive_wave_reaches_distant_target',self.hp(far)<far_before,{'before':far_before,'after':self.hp(far)})
        self.check('derived_hit_does_not_add_stacks_or_refund',self.state()['sword_stacks']==5
                   and 'result=hit_refund' not in log and self.power()['current']>=qi_before,
                   {'stacks':self.state()['sword_stacks'],'log':log})
        self.capture('sword-streak-completed.png')
        self.poll(lambda:self.state()['sword_stacks'],lambda n:n==0,timeout=7,label='online streak timeout')
        self.check('timeout_clears_streak',self.state()['sword_stacks']==0,self.state())
        # A fresh full combo with an actual world collision between melee and distant target.
        self.rc('fill -2 -60 5 3 -56 5 minecraft:stone')
        before=self.hp(far);start=self.offset();self.combo(near);log=self.logs(start)
        self.check('wave_stops_at_wall',self.hp(far)==before and 'reason=WALL' in log,log)
        self.rc('fill -2 -60 5 3 -56 5 minecraft:air')
        self.check('swap_fixture_has_live_streak',self.state()['sword_stacks']==5,self.state())
        self.s.game.key('2');time.sleep(.2)
        self.check('mapped_slot_swap_clears_streak',self.state()['sword_stacks']==0,self.state())
        self.s.game.key('1');time.sleep(.2)
        self.check('returning_to_same_sword_does_not_restore_stacks',self.state()['sword_stacks']==0,self.state())
        with SecondClient(self) as guest:
            self.rc(f'tp {guest.username} 4.5 -60 2.5 90 0')
            guest_before=self.one(f'myvillage cultivation fate status {guest.username}','FATES')
            self.combo(near)
            guest_after=self.one(f'myvillage cultivation fate status {guest.username}','FATES')
            self.check('two_clients_keep_private_sword_streaks',self.state()['sword_stacks']==5
                       and guest_before['sword_stacks']==guest_after['sword_stacks']==0,
                       {'owner':self.state()['sword_stacks'],'guest':guest_after})
            self.report['multiplayer']='two_actual_clients_private_streak_readbacks'
        # Active sword qi uses its normal empty intent; it must not build a melee streak.
        events=self.cast('active_qi_regression',hold=.15)
        self.check('active_skill_still_hits_without_stacking',any(e.get('result')=='hit' for e in events)
                   and self.state()['sword_stacks']==0,events)
        self.combo(near);selected=self.state()['selected']
        self.rc(f'kill {u}');self.poll(self.hp,lambda n:n>0,timeout=8,label='respawn')
        self.check('death_clears_streak_keeps_fate',self.state()['sword_stacks']==0
                   and self.state()['selected']==selected,self.state())
        self.home();self.rc('save-all flush');self.stop();self.start(reuse=True)
        self.check('restart_preserves_selection_without_streak',self.state()['selected']==selected
                   and self.state()['sword_stacks']==0,self.state())
        self.report['completed']=['sword_obsession'];self.report['unrun']=[]


def main():
    e=SwordEvidence(ROOT/'out/preview/sword-fate')
    try:e.run()
    except BaseException as error:
        e.report['failures'].append(type(error).__name__+': '+str(error));raise
    finally:
        if e.s:e.stop()
        e.report['passed']=not e.report['failures'] and e.report['completed']==e.report['selected'];e.save()
    return 0 if e.report['passed'] else 1

if __name__=='__main__':raise SystemExit(main())
