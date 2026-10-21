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
    def ui_only(self):
        import hashlib,subprocess
        from PIL import Image
        from tools.gathering_evidence import menu_point,MENU
        self.report.update(selected=['beam_orientation','layered_ui'],completed=[],unrun=['beam_orientation','layered_ui'])
        self.start(reuse=True)
        for tier,core in [('medium',(0,-61,3)),('high',(18,-61,3))]:
            if tier=='high':
                piece=next(p for p in self.parts[tier] if p['block'].endswith('golden_gathering_beam'))
                dx,dy,dz=piece['offset'];where=f'{core[0]+dx} {core[1]+dy} {core[2]+dz}'
                self.rc(f'setblock {where} '+piece['block']+'[facing=east]')
                bad=self.poll(lambda:self.inspect(core),lambda d:not d['formed'],8,'misaligned beam')
                self.check('misaligned_beam_refused',bad['issue']==10,bad)
                self.rc(f'setblock {where} '+piece['block']+'[facing='+piece['facing']+']')
                self.poll(lambda:self.inspect(core),lambda d:d['formed'],8,'beam direction restored')
                self.complete('beam_orientation')
            before=self.inspect(core);self.open(core);time.sleep(.7)
            hashes=[]
            for layer in range(1,4 if tier=='high' else 3):
                name=tier+f'-blueprint-layer{layer}.png';self.capture(name)
                a=menu_point(self.s.game.size,14,33,MENU);b=menu_point(self.s.game.size,115,123,MENU)
                hashes.append(hashlib.sha256(Image.open(self.out/name).crop((*a,*b)).tobytes()).hexdigest())
                self.click(62,131)
            self.capture(tier+'-blueprint-wrapped.png')
            wrapped=hashlib.sha256(Image.open(self.out/(tier+'-blueprint-wrapped.png')).crop((*a,*b)).tobytes()).hexdigest()
            self.check('mapped_layers_are_distinct_and_wrap_'+tier,len(set(hashes))==len(hashes) and wrapped==hashes[0],{'grid_hashes':hashes,'wrapped':wrapped})
            px,py=menu_point(self.s.game.size,62,131,MENU);self.s.game.xdo('mousemove',str(px),str(py));time.sleep(1)
            window=self.s.game.xdo('search','--name','Minecraft').split()[0]
            subprocess.run(['import','-silent','-window',window,str(self.out/(tier+'-materials-tooltip.png'))],env=self.s.game.env,check=True,timeout=30)
            self.close_menu();after=self.inspect(core)
            self.check('blueprint_view_does_not_spend_'+tier,all(before[k]==after[k] for k in ['charges','stones','fuel_grade','enabled']),{'before':before,'after':after})
        self.complete('layered_ui');self.stop();self.report['status']='pass';self.save()
    def style_only(self):
        self.report.update(selected=['lamp_placement','visual'],completed=[],unrun=['lamp_placement','visual'])
        self.start(reuse=True)
        for command in (*scene.world_rules(),*scene.player_setup(self.s.username,'minecraft:air'),'myvillage world pause'):self.rc(command)
        self.fixture('high')
        lamp=next(p for p in self.parts['high'] if p['block'].endswith('purple_gathering_lamp'))
        self.put(lamp,'high','minecraft:air');self.poll(self.inspect,lambda d:not d['formed'],8,'lamp removal')
        dx,dy,dz=lamp['offset']
        self.rc(f'item replace entity {self.s.username} hotbar.0 with {lamp["block"]} 1')
        self.rc(f'tp {self.s.username} {dx+.5} {CORE[1]+1} {CORE[2]+dz+.5+1.8} 180 35');time.sleep(.6)
        self.key('1');self.s.game.xdo('click','3');self.report['mapped_input'].append({'place_corner_lamp':lamp['offset']})
        self.poll(self.inspect,lambda d:d['formed'],8,'mapped lamp placement on low plinth')
        self.check('mapped_corner_lamp_fits_low_plinth',self.inspect()['formed'],self.inspect())
        where=f'{dx} {CORE[1]+dy} {CORE[2]+dz}'
        self.rc(f'setblock {where} '+lamp['block']+'[facing=east]')
        bad=self.poll(self.inspect,lambda d:not d['formed'],8,'lamp facing')
        self.check('wrong_corner_lamp_direction_refused',bad['issue']==10,bad)
        self.put(lamp,'high');self.poll(self.inspect,lambda d:d['formed'],8,'lamp facing restored')
        self.complete('lamp_placement')
        class Guest(SecondClient):username='GatheringGuest'
        with Guest(self) as guest:self.visual(guest);self.complete('visual')
        self.rc('save-all flush');self.stop();self.export();self.report['status']='pass';self.save()
    def high_visual_only(self):
        import subprocess
        self.report.update(selected=['high_visual'],completed=[],unrun=['high_visual'])
        self.start(reuse=True);self.player();self.key('x')
        core=(18,-61,3);self.open(core);self.close_menu()
        self.rc(f'tp {self.s.username} 18.5 -60 3.5 0 0');time.sleep(.5);self.key('v')
        self.poll(lambda:self.inspect(core),lambda d:d['formed'] and d['running'],15,'final high running')
        class Guest(SecondClient):username='GatheringGuest'
        with Guest(self) as guest:
            # Restart meditation after the observer's startup so a stage cap cannot end the visual fixture.
            self.key('x');self.rc(f'myvillage cultivation setprogress {self.s.username} 0');self.key('v')
            self.poll(lambda:self.inspect(core),lambda d:d['running'],15,'final high running after observer joins')
            self.rc(f'gamemode spectator {guest.username}');self.key('F1',guest.game,guest.username)
            self.camera(guest,(28.5,-52,-9),(18.5,-59.9,3.5));self.capture_game(guest.game,'high-running.png')
            subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','x11grab','-video_size','960x540',
                '-framerate','20','-i',guest.game.display+'.0','-t','4','-an','-c:v','libx264','-preset','veryfast',
                '-crf','22','-pix_fmt','yuv420p',str(self.out/'high-running.mp4')],check=True,timeout=30)
            self.camera(guest,(25.4,-59.3,0),(23.5,-59.45,3.5));self.capture_game(guest.game,'high-node-detail.png')
            self.check('final_high_geometry_running',self.inspect(core)['running'],self.inspect(core))
            self.key('x');self.open(core);self.capture('high-menu.png');self.close_menu()
            self.camera(guest,(1,-30,-20),(1,-60,3));self.capture_game(guest.game,'three-tiers-overview.png')
            self.key('F1',guest.game,guest.username)
            self.rc(f'tp {self.s.username} -7 -60 -3 0 15')
        self.complete('high_visual');self.rc('save-all flush');self.stop();self.export();self.report['status']='pass';self.save()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'out/preview/gathering_architecture/runtime')
    modes=p.add_mutually_exclusive_group();modes.add_argument('--ui-only',action='store_true');modes.add_argument('--style-only',action='store_true');modes.add_argument('--high-visual-only',action='store_true');args=p.parse_args()
    e=ArchitectureEvidence(args.out)
    try:e.ui_only() if args.ui_only else e.style_only() if args.style_only else e.high_visual_only() if args.high_visual_only else e.run()
    except BaseException as error:e.report['status']='fail';e.report['failures'].append(repr(error));e.save();raise
    finally:
        if e.s:e.stop()
        e.save()
if __name__=='__main__':main()
