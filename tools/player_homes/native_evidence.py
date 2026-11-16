"""独立原生服务器：四套NBT回读、整门交互及开合状态重启。"""
import argparse,hashlib,json,shutil,sys,time,traceback
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT),str(ROOT/'tools')]
from tools.qianji.server import Server
from tools.roads_smoke.run import verify_candidate
from tools.buildgen.nbtread import read_gzipped_nbt,state_string


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for arg in('out','jar','probe','blueprints'):ap.add_argument('--'+arg,type=Path,required=True)
    args=ap.parse_args();out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    game=ROOT/'run-player-house-doors'/out.name
    assert not game.exists(),'原生检查必须使用独立新世界'
    (game/'mods').mkdir(parents=True);shutil.copy2(args.probe,game/'mods'/args.probe.name)
    selected=['candidate_identity','four_blueprint_readbacks','door_lifecycle','restart_persistence']
    report={'status':'running','selected':selected,'completed':[],'failed':[],'unrun':selected.copy(),
            'evidence':'server_command_fixture','mapped_player_input':'not_verified','client_visual':'not_verified','multiplayer':'not_verified'}
    def save():(out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    def completed(name):report['completed'].append(name);report['unrun'].remove(name);save()
    server=Server(out,game,2026111701351);server.level_type='minecraft\\:flat';server.generate_structures='false';server.readiness_command='list';server.startup_timeout=1200
    stage=selected[0];save()
    try:
        report['candidate']=verify_candidate(args.jar.resolve());completed(stage)
        expected=[]
        for i,key in enumerate(['compact_court','family_court','workshop_court','moon_courtyard']):
            source=args.blueprints/key/'home.nbt';dest=game/'world/generated/yuanxian/structures'/('door_home_'+key+'.nbt');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
            _,nbt=read_gzipped_nbt(str(source));palette=[state_string(p)for p in nbt['palette']]
            expected.append({'id':key,'origin':[32+i*64,100,32],'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
                             'blocks':[{'pos':b['pos'],'state':palette[b['state']]}for b in nbt['blocks']]})
        (game/'home-probe.json').write_text(json.dumps(expected,separators=(',',':')))
        server.start();stage=selected[1]
        for h in expected:
            x,y,z=h['origin'];server.command(f'forceload add {x} {z} {x+55} {z+49}')
            deadline=time.monotonic()+120
            while not(server.test(f'if loaded {x} {y} {z}')and server.test(f'if loaded {x+55} {y} {z+49}')):
                if time.monotonic()>deadline:raise TimeoutError('模板目标区块未加载 '+h['id'])
                time.sleep(1)
            reply=server.command(f'place template yuanxian:door_home_{h["id"]} {x} {y} {z}')
            if 'Placed'in reply:continue
            raise AssertionError(reply)
        def probe(action):
            reply=server.command('door_probe '+action)
            if 'DOOR_PROBE 'not in reply:raise AssertionError(reply)
            result=json.loads((game/('door-probe-'+action+'.json')).read_text())
            (out/('door-probe-'+action+'.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2))
            assert result['status']=='passed' and all(c['passed']for c in result['checks']),result
        probe('homes');completed(stage)
        stage=selected[2];probe('save');completed(stage)
        stage=selected[3];server.stop();server.start();probe('restore');completed(stage)
        report['status']='passed'
    except BaseException as error:
        report['status']='failed';report['failed'].append({'case':stage,'error':repr(error),'traceback':traceback.format_exc()});raise
    finally:
        try:server.stop()
        finally:save()

if __name__=='__main__':main()
