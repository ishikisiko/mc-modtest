"""补拍游剑妖与真实玩家同框的体型；使用第三人称相机和已验证的资源。"""
from pathlib import Path
import argparse,hashlib,json,time,traceback
from PIL import Image
from tools.legacy_encounters.visual_evidence import VisualEvidence

class SwordScaleEvidence(VisualEvidence):
    def run(self):
        self.start(reuse=True);self.clean();self.rc('kill @e[tag=legacy_saved_beast]');self.step(25);self.ready(20)
        self.rc(f'tp {self.s.username} -1.4 -60 .5 0 -10')
        self.rc('summon myvillage:wandering_sword 1.2 -60 .5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:["legacy_beast"]}')
        self.step(6);self.key('F5');self.key('F1');time.sleep(2)
        # The previous spectator shot was taken before chunks appeared. Require the
        # known stone-floor fixture to be rendered before accepting another shot.
        samples=[]
        for attempt in range(10):
            self.capture('scale-wandering_sword' if attempt==0 else f'scale-wandering_sword-retry-{attempt}')
            name='scale-wandering_sword.png' if attempt==0 else f'scale-wandering_sword-retry-{attempt}.png'
            with Image.open(self.out/name) as im:
                rgb=im.convert('RGB');w,h=rgb.size;pixels=list(rgb.crop((0,h*2//3,w,h)).getdata())
                fraction=sum(abs(r-b)<24 and abs(g-b)<24 for r,g,b in pixels)/len(pixels)
            samples.append({'file':name,'stone_floor_fraction':fraction})
            if fraction>.5:break
            time.sleep(2)
        self.check('native_stone_floor_is_visible',fraction>.5,samples)
        self.report.update(kind='legacy_sword_scale',scale_screenshot=name,scene='真实玩家与 NoAI 游剑妖同框；F5 第三人称相机，HUD 关闭。',source_collector_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
        state=self.status();mob=next(b for b in state['nearby_beasts'] if b['type']=='myvillage:wandering_sword')
        self.check('sword_scale_fixture_height',abs(mob['height']-3.5)<.0001,{'beast':mob,'player_position':self.pos(),'camera':'third person back; pitch -10 degrees'})
        self.key('F1');self.key('F5');self.key('F5');self.done('sword_scale')

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args();e=SwordScaleEvidence(a.out,['sword_scale'])
    try:e.run()
    except BaseException as error:e.report['failures'].append(repr(error));traceback.print_exc()
    finally:
        try:
            if e.s:e.rc('tick unfreeze');e.stop()
        except BaseException as error:e.report['failures'].append('cleanup: '+repr(error))
        e.report['passed']=not e.report['failures'] and not e.report['unrun'] and all(c['pass'] for c in e.report['checks']);e.save()
    return 0 if e.report['passed'] else 1

if __name__=='__main__':raise SystemExit(main())
