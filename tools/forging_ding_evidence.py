#!/usr/bin/env python3
"""Focused appearance/state integration: mapped placement and crafting, plus a saved lit pulse."""
import argparse
import json
import math
from pathlib import Path
import sys
import time
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.forging_evidence import ForgingEvidence,POS
from tools.alchemy_quality_evidence import read_data
from tools.combat_capture import scene

class DingEvidence(ForgingEvidence):
    def __init__(self,out):
        super().__init__(out)
        self.visual=json.loads((ROOT/'src/main/resources/data/myvillage/forging/ding.json').read_text())
        self.report.update(kind='forging_ding_visuals',selected=['mapped_placement','cold_hot','repair_feedback','orientation','pulse_restart'],completed=[],
                           owner_appearance='not_verified',owner_sound='not_verified',owner_fps='not_verified')
    def is_state(self,properties):
        return 'passed' in self.rc(f'execute if block {POS} myvillage:forging_table[{properties}]')
    def cool(self):
        self.poll(lambda:self.is_state('lit=false'),bool,self.visual['feedback']['duration_ticks']/20+6,'ding pulse ends')
    def quarter(self):
        self.rc(f'tp {self.s.username} -1.5 -60 1.5 -45 21.6');time.sleep(.3)
    def render_item(self):
        u=self.s.username;self.rc(f'item replace entity {u} hotbar.7 with myvillage:forging_table 1');self.s.game.key('8');time.sleep(.5)
        self.quarter();self.capture('ding-held.png');self.s.game.key('7')
    def run(self):
        self.start();u=self.s.username
        for cmd in (*scene.world_rules(),*scene.player_setup(u,'minecraft:air'),'myvillage world pause','forceload add -16 -16 32 32',f'setblock {POS} air',f'item replace entity {u} hotbar.0 with myvillage:forging_table 2'):
            self.rc(cmd)
        self.rc(f'tp {u} .5 -60 .5 0 28.5');self.s.game.key('1');time.sleep(.5);self.s.game.xdo('click','3')
        self.poll(lambda:self.is_state('facing=north,lit=false'),bool,8,'mapped ding placement')
        self.check('mapped_placement_consumes_one_and_faces_player',self.count('myvillage:forging_table')==1,self.inventory())
        self.s.game.key('7');self.quarter();self.capture('ding-cold.png');self.render_item()
        self.report['completed'].append('mapped_placement')
        self.learn('qingfeng_sword');self.rc(f'myvillage cultivation awaken {u}');self.rc(f'myvillage cultivation crafting set {u} forging 0')
        self.rc(f'clear {u}');self.open_table();self.click(152,137);self.close_menu()
        self.check('rejected_forge_does_not_light',self.is_state('lit=false'),self.inventory())
        self.prepare_materials(self.rules['recipes'][0],'sharp');self.open_table();self.click(152,137)
        self.check('successful_forge_lights_ding',self.is_state('lit=true'),'server blockstate after mapped forge')
        self.close_menu();self.quarter();self.capture('ding-forged-lit.png')
        self.check('visual_pulse_does_not_delay_output',self.count('myvillage:qingfeng_sword')==1,self.inventory())
        self.cool();self.capture('ding-cooled.png');self.report['completed'].append('cold_hot')
        # A real repair transaction invokes the same presentation hook, with existing saved stack metadata.
        self.rc(f'clear {u}');self.rc(f'item replace entity {u} hotbar.0 with myvillage:qingfeng_sword[minecraft:damage=800]')
        for item,n in self.rules['repair']['costs'].items():self.rc(f'give {u} {item} {n}')
        self.s.game.key('1');self.open_table();self.click(78,222,shift=True);self.click(248,137)
        self.check('successful_repair_lights_ding',self.is_state('lit=true'),'mapped repair');self.close_menu();self.quarter();self.capture('ding-repair-lit.png');self.cool()
        self.check('repair_result_still_returned',self.count('myvillage:qingfeng_sword')==1,self.inventory())
        self.report['completed'].append('repair_feedback')
        # Inspect all state orientations; only the first placement above is mapped-input evidence.
        self.s.game.key('7')
        for direction in ['east','south','west']:
            self.rc(f'setblock {POS} myvillage:forging_table[facing={direction},lit=false]')
            self.open_table();self.check('menu_access_'+direction,not self.s.game.pointer_grabbed(),direction);self.close_menu();self.quarter();self.capture('ding-'+direction+'.png')
        self.report['completed'].append('orientation')
        self.prepare_materials(self.rules['recipes'][0],'tough');self.open_table();self.click(50,138)
        self.rc('tick freeze');self.click(152,137);self.close_menu()
        self.check('saved_fixture_is_actual_successful_pulse',self.is_state('facing=west,lit=true') and self.count('myvillage:qingfeng_sword')==1,self.inventory())
        before=self.inventory();self.rc('save-all flush');self.stop();self.start(reuse=True)
        self.check('facing_survives_restart',self.is_state('facing=west'),'saved west ding')
        self.rc('tick unfreeze');self.cool()
        self.check('no_stuck_fire_or_duplicate_output_after_restart',self.inventory()==before,self.inventory())
        self.quarter();self.capture('ding-restored-cold.png');self.report['completed'].append('pulse_restart')
        self.report['status']='pass'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'out/preview/forging_ding/runtime');a=p.parse_args();e=DingEvidence(a.out)
    try:e.run()
    except BaseException as error:e.report['status']='fail';e.report['failures'].append(repr(error));e.save();raise
    finally:
        if e.s:
            try:e.rc('tick unfreeze')
            finally:e.stop()
        e.save()
if __name__=='__main__':main()
