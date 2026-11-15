"""在自有群落验收世界补拍代表画面；使用地表高度和旁观模式避免相机落进岩壁。"""
from pathlib import Path
from contextlib import ExitStack
import argparse,hashlib,json,secrets,tarfile,time
from .native_evidence import Evidence,ROOT,verify_candidate,read_data

class Capture(Evidence):
    def __init__(self,out,world_ref,jar):
        super().__init__(out,world_ref,jar)
        self.report.update(kind='east_wilderness_capture',selected=['candidate_identity','four_representative_views'],unrun=['candidate_identity','four_representative_views'])
        self.report['capture_collector_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
        self.save()
    def done(self,name):
        self.report['completed'].append(name);self.report['unrun']=[x for x in self.report['selected']if x not in self.report['completed']];self.save()
    def run(self):
        try:
            self.report['identity']=verify_candidate(self.jar);self.done('candidate_identity');self.begin('four_representative_views')
            with ExitStack()as stack:
                client=self.start_client(stack,'review');sites=self.state()['sites']
                for kind in('wolf_den','lizard_shelf','spore_grove','bandit_camp'):
                    site=next(s for s in sites if s['kind']==kind);self.rc(f'gamemode creative {client.name}');self.visit(client,site);self.populate(site)
                    self.rc(f'gamemode spectator {client.name}');candidates=[]
                    for dx,dz in[(14,14),(-14,14),(14,-14),(-14,-14)]:
                        x,z=site['x']+dx+.5,site['z']+dz+.5
                        reply=self.rc(f'execute positioned {x} 0 {z} positioned over world_surface run tp {client.name} ~ ~8 ~')
                        self.check('地表相机位置准备','Teleported'in reply and client.name in reply,reply)
                        position=read_data(self.rc(f'data get entity {client.name} Pos'));self.check('相机坐标有效',isinstance(position,list)and len(position)==3,position);candidates.append(position)
                    camera=min(candidates,key=lambda p:p[1]);camera[1]=max(camera[1],site['y']+20)
                    self.fixture(client,camera,[site['x']+.5,site['y']+1.5,site['z']+.5]);time.sleep(3);client.capture('review-'+kind)
                    self.action('captured_representative',habitat_kind=kind,camera=camera,mode='spectator',acceptance='captured_for_review')
                self.rc('save-all flush')
            self.done('four_representative_views');self.report['status']='captured_for_review'
        except BaseException as error:
            self.report['status']='failed';self.report['failed'].append({'case':self.stage,'error':repr(error)});self.report['unrun']=[x for x in self.report['selected']if x not in self.report['completed']and x!=self.stage];raise
        finally:
            try:self.server.command('forceload remove all')
            except Exception:pass
            self.server.stop();self.save()

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--world-ref',type=Path,required=True);p.add_argument('--jar',type=Path,required=True)
    p.add_argument('--baseline-archive',type=Path,required=True);p.add_argument('--baseline-sha256',required=True);a=p.parse_args()
    origin=json.loads(a.world_ref.read_text());original=Path(origin['directory']).resolve()
    assert original.parent==ROOT/'run-fixed-progression' and original.name.startswith('native-')
    assert hashlib.sha256(a.baseline_archive.read_bytes()).hexdigest()==a.baseline_sha256
    game=ROOT/'run-fixed-progression'/('native-'+secrets.token_hex(5));game.mkdir()
    with tarfile.open(a.baseline_archive)as archive:
        assert all(Path(m.name).parts[0]=='world'for m in archive.getmembers())
        archive.extractall(game,filter='data')
    origin.update(directory=str(game),kind='isolated copy of owned fresh development baseline',copied_from=str(a.world_ref.resolve()),baseline_sha256=a.baseline_sha256)
    ref=game/'capture-world.json';ref.write_text(json.dumps(origin,indent=2))
    evidence=Capture(a.out.resolve(),ref,a.jar.resolve());evidence.report['baseline']=origin;evidence.save();evidence.run()
if __name__=='__main__':main()
