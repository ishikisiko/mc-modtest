#!/usr/bin/env python3
"""Local-scope evidence for assembling the approved lotus courts and crystal gates."""
from pathlib import Path
import argparse,json,sys,time
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from tools.gathering_tiers_evidence import TierEvidence,CORE,pos
from tools.alchemy_quality_evidence import SecondClient
from tools.combat_capture import scene

class ArchitectureEvidence(TierEvidence):
    def __init__(self,out):
        super().__init__(out,['legacy_architecture','assembly','visual'])
        self.parts=json.loads((ROOT/'src/main/resources/data/myvillage/gathering/construction.json').read_text())['tiers']
    def put(self,part,tier,block=None):
        p=part['offset'];bid=block or part['block'];suffix='' if bid.startswith('minecraft:') else '[facing='+part['facing']+']'
        self.rc(f'setblock {p[0]} {CORE[1]+p[1]} {CORE[2]+p[2]} {bid}{suffix}')
    def legacy_architecture(self):
        old=(18,-61,3);before=self.inspect(old)
        self.check('old_0740_high_array_keeps_saved_credit',before['tier']=='high' and before['charges']==1983 and before['fuel_grade']=='high_grade' and before['stones']==3,before)
        self.poll(lambda:self.inspect(old),lambda d:not d['formed'],10,'legacy array awaits added architecture')
        self.build_parts(old,'high')
        after=self.poll(lambda:self.inspect(old),lambda d:d['formed'],10,'old core accepts added architecture')
        self.check('adding_architecture_keeps_old_credit_and_queue',all(after[k]==before[k] for k in ['charges','stones','fuel_grade','queued_grade','enabled']),{'before':before,'after':after})
    def assembly(self):
        for tier in ('medium','high'):
            self.fixture(tier);self.activate('medium_grade' if tier=='medium' else 'high_grade');self.benefit(2)
            seen=set()
            for part in self.parts[tier]:
                bid=part['block']
                if bid=='minecraft:air' or bid.endswith('_node') or bid in seen:continue
                seen.add(bid);self.put(part,tier,'minecraft:air')
                self.poll(self.inspect,lambda d:not d['formed'],8,'missing '+bid)
                before=self.energy();self.key('v');time.sleep(1.1);self.key('x');time.sleep(.3)
                self.check('missing_piece_pauses_'+bid,self.energy()==before,self.inspect())
                self.put(part,tier);self.poll(self.inspect,lambda d:d['formed'],8,'restore '+bid)
            # Final repair is done by a real player's mapped block placement.
            last=next(p for p in self.parts[tier] if p['block'].endswith('jade_gathering_step' if tier=='medium' else 'purple_gathering_plinth'))
            self.put(last,tier,'minecraft:air');self.poll(self.inspect,lambda d:not d['formed'],8,'mapped repair fixture')
            dx,dy,dz=last['offset'];x=dx+.5;z=CORE[2]+dz+.5
            self.rc(f'item replace entity {self.s.username} hotbar.0 with {last["block"]} 1')
            self.rc(f'tp {self.s.username} {x if tier=="medium" else x-2} {CORE[1]+1} {z+1.5 if tier=="medium" else z} {180 if tier=="medium" else -90} {47 if tier=="medium" else 39}');time.sleep(.6)
            self.key('1');self.s.game.xdo('click','3');self.report['mapped_input'].append({'repair_piece':last['block']})
            self.poll(self.inspect,lambda d:d['formed'],8,'mapped final masonry repair')
            self.check('mapped_repair_restores_'+tier,self.inspect()['formed'],self.inspect())
            self.center();self.benefit(2)
            self.open();self.click(62,131);self.capture(tier+'-blueprint-layer2.png')
            if tier=='high':self.click(62,131);self.capture('high-blueprint-layer3.png')
            self.close_menu()
            blocked=next(p for p in self.parts[tier] if p['block']=='minecraft:air')
            self.put(blocked,tier,'minecraft:stone')
            self.poll(self.inspect,lambda d:d['issue']==11,8,'architectural clearance')
            self.check('assembly_clearance_'+tier,not self.inspect()['formed'],self.inspect());self.put(blocked,tier)
            self.poll(self.inspect,lambda d:d['formed'],8,'clearance restored')
    def run(self):
        self.start(reuse=True)
        for command in (*scene.world_rules(),*scene.player_setup(self.s.username,'minecraft:air'),'myvillage world pause'):self.rc(command)
        self.legacy_architecture();self.complete('legacy_architecture')
        self.assembly();self.complete('assembly')
        class Guest(SecondClient):username='GatheringGuest'
        with Guest(self) as guest:self.visual(guest);self.complete('visual')
        self.rc('save-all flush');self.stop();self.export();self.report['status']='pass';self.save()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'out/preview/gathering_architecture/runtime');args=p.parse_args()
    e=ArchitectureEvidence(args.out)
    try:e.run()
    except BaseException as error:e.report['status']='fail';e.report['failures'].append(repr(error));e.save();raise
    finally:
        if e.s:e.stop()
        e.save()
if __name__=='__main__':main()
