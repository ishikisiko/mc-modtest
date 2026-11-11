"""固定州序的定向原生验收：独立新世界、连续州官道、州界两侧区块与缓存重启。"""
from __future__ import annotations
import argparse,hashlib,json,secrets,shutil,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from roads_smoke.run import Server,verify_candidate
from roadweaver_probe.run import write
from buildgen.nbtread import read_gzipped_nbt


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jar',type=Path,required=True);p.add_argument('--source',required=True)
    p.add_argument('--out',type=Path,required=True);p.add_argument('--seed',type=int,required=True)
    p.add_argument('--restart',action='store_true');p.add_argument('--all-qianji-targets',action='store_true')
    p.add_argument('--continue-probe',type=Path,help='仅继续同一候选已完成新世界验证的自有探针，不接受玩家存档')
    a=p.parse_args();a.out=a.out.resolve();a.out.mkdir(parents=True,exist_ok=False)
    selected=['candidate_identity','fresh_world_and_fixed_order','consecutive_capital_roads','border_chunk_generation_and_readback']
    selected+=['qianji_buildings_and_task_boards']
    if a.restart:selected+=['saved_rules_and_cached_restart']
    result=dict(source=a.source,seed=a.seed,selected=selected,completed=[],failed=[],unrun=selected.copy(),
                full_road_pregeneration='not_selected',player_walk='not_verified',owner_visual_acceptance='not_verified')
    previous=None
    if a.continue_probe:
        old=a.continue_probe.resolve();origin=json.loads((old/'world.json').read_text());prior=json.loads((old/'result.json').read_text())
        game=Path(origin['directory']).resolve()
        assert game.parent==(ROOT/'run-fixed-progression').resolve() and game.name.startswith('native-')
        assert origin['seed']==a.seed and origin['kind']=='fresh isolated development world'
        assert prior['source']==a.source and prior['seed']==a.seed and 'fresh_world_and_fixed_order' in prior['completed']
        previous=json.loads((old/'worldplan.json').read_text());result['continued_from']=str(old)
    else:game=ROOT/'run-fixed-progression'/('native-'+secrets.token_hex(5))
    server=Server(a.out,game,a.seed);stage=selected[0]
    def complete():
        result['completed'].append(stage);result['unrun'].remove(stage);write(a.out/'result.json',result)
    def query(command):
        answer=server.command('myvillage worldplan '+command)
        if 'WORLDPLAN ' not in answer:raise RuntimeError(answer)
        return json.loads(answer.split('WORLDPLAN ',1)[1])
    def load(x,z):
        server.command(f'forceload add {x} {z}');deadline=time.monotonic()+120
        while not server.test(f'if loaded {x} 300 {z}'):
            if time.monotonic()>deadline:raise TimeoutError(f'区块未加载: {x} {z}')
            time.sleep(1)
    write(a.out/'world.json',dict(directory=str(game),seed=a.seed,kind='fresh isolated development world'))
    write(a.out/'result.json',result)
    try:
        identity=verify_candidate(a.jar.resolve());identity['source']=a.source;write(a.out/'identity.json',identity);complete()
        stage=selected[1];started=time.monotonic();server.start();status=server.roads('status')
        assert status['phase']=='ready' and status['mode']=='chunk_generation',status
        query('export');manifest=json.loads((server.data/'worldplan.json').read_text());shutil.copy2(server.data/'worldplan.json',a.out/'worldplan.json')
        if previous is not None:assert manifest==previous,'继续探针的世界规划必须与首次新建时逐项相同'
        order=manifest['rules']['region_progression']['order'];profiles={v['id']:v for v in manifest['rules']['profiles']}
        assert order==['ximo','donghai','nanjiang','beiyuan','donghuang','lingyue','moyu','zhongzhou'],order
        assert manifest['graph']['progression_order']==order and len(manifest['cities'])==8
        assert manifest['spawn']['regionId']==order[0],manifest['spawn']
        tiers={v['id']:v['tier'] for v in manifest['graph']['regions']}
        assert all(tiers[b]>tiers[c] for c,b in zip(order,order[1:])),tiers
        gates=[b for b in manifest['borders'] if b['gate']]
        assert {frozenset([b['a'],b['b']]) for b in gates}=={frozenset(['lingyue','moyu']),frozenset(['moyu','zhongzhou'])}
        for b in manifest['borders']:
            if 'moyu' in (b['a'],b['b']):assert b['passable']==b['gate'],b
        write(a.out/'startup.json',dict(seconds=time.monotonic()-started,status=status,tiers=tiers));complete()
        stage=selected[2];server.roads('export');shutil.copy2(server.data/'roads.json',a.out/'roads.json');roads=json.loads((a.out/'roads.json').read_text())
        pairs={frozenset([c,b]) for c,b in zip(order,order[1:])}
        trunk=[r for r in roads['routes'] if r['from'] in profiles and r['to'] in profiles]
        assert len(trunk)==7 and {frozenset([r['from'],r['to']]) for r in trunk}==pairs
        assert len(roads['routes'])==7+len(manifest['sect_sites']),len(roads['routes']);complete()
        stage=selected[3];samples=[]
        for route in trunk:
            path=route['centerline']
            def at(index):
                q=path[index];return query(f"at {q['x']} {q['z']}")['regionId']
            first,last=at(0),at(len(path)-1)
            assert {first,last}=={route['from'],route['to']},(route['from'],route['to'],first,last)
            lo,hi=0,len(path)-1
            while hi-lo>1:
                mid=(lo+hi)//2;region=at(mid);assert region in (first,last),region
                if region==first:lo=mid
                else:hi=mid
            for index,region in [(lo,first),(hi,last)]:
                q=path[index];load(q['x'],q['z']);samples.append(dict(route=[route['from'],route['to']],region=region,position=q))
        server.roads('audit')
        server.wait(lambda s:s.get('inspection',{}).get('phase') in ('complete','partial'),allow_blocked=True)
        shutil.copy2(server.data/'roads-audit.json',a.out/'border-loaded-audit.json')
        audit=json.loads((a.out/'border-loaded-audit.json').read_text());check=audit['status']['inspection']
        assert check['failure_count']==0 and not audit['failures'],audit['failures'][:20]
        assert check['checked_columns']>=len(samples),check
        write(a.out/'boundary-samples.json',dict(samples=samples,checked_columns=check['checked_columns'],unloaded_chunks=check['unloaded_chunks'],scope='loaded boundary chunks only'))
        complete()
        stage='qianji_buildings_and_task_boards';buildings=[]
        from road_port_sources import CitySource
        for city in manifest['cities']:
            source=CitySource(ROOT/'build/resources/main',city['region_id']);office=source.city['qianji']
            rank=order.index(city['region_id']);assert office['scale']==('compact' if rank<4 else 'large' if rank<7 else 'headquarters')
            points=office['targets'] if a.all_qianji_targets else [v for v in office['targets'] if v['id'] in ('jianghu_board','entrance') or v['id'].startswith(('core_','command_','archive_'))]
            for target in points:
                x,y,z=target['position'];wx,wy,wz=city['x']+x,city['origin_y']+y,city['z']+z;load(wx,wz)
                expected=source.block(x,y-1,z);assert expected not in ('minecraft:air','minecraft:water[level=0]'),(city['region_id'],target)
                assert server.test(f'if block {wx} {wy-1} {wz} {expected}'),(city['region_id'],target,expected)
                for dy in [0,1]:assert server.test(f'if block {wx} {wy+dy} {wz} minecraft:air'),(city['region_id'],target,dy)
            for landmark in office.get('landmarks',[]):
                x,y,z=landmark['position'];wx,wy,wz=city['x']+x,city['origin_y']+y,city['z']+z;load(wx,wz)
                expected=source.block(x,y,z);assert expected!='minecraft:air'
                assert server.test(f'if block {wx} {wy} {wz} {expected}'),landmark
            board=office['board']['position'];wx,wy,wz=city['x']+board[0],city['origin_y']+board[1],city['z']+board[2]
            load(wx,wz);assert server.test(f'if block {wx} {wy} {wz} myvillage:jianghu_board[facing=south]')
            info=query('qianji '+city['region_id'])['qianji'];assert len(info)==1
            assert info[0]['task_reader']==[city['x']+office['board']['reader'][0],city['origin_y']+office['board']['reader'][1],city['z']+office['board']['reader'][2]]
            buildings.append(dict(region=city['region_id'],name=office['name'],scale=office['scale'],checked_targets=len(points),board=[wx,wy,wz],info=info[0]))
        write(a.out/'qianji-readback.json',dict(buildings=buildings,all_function_targets=a.all_qianji_targets));complete()
        if a.restart:
            stage='saved_rules_and_cached_restart';before=status['layout_hash'];server.stop();_,tag=read_gzipped_nbt(str(game/'world/level.dat'))
            gen=tag['Data']['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']
            from worldplan.acceptance import decode_saved_rules
            assert decode_saved_rules(gen['rules'],'myvillage:worldplan_v11')['region_progression']['order']==order
            assert gen['roads']['layout_hash']==before and len(gen['roads']['routes'])==len(roads['routes'])
            server.start();after=server.roads('status');assert after['layout_hash']==before and after['planning_progress']['phase']=='cached',after
            query('export');restored=json.loads((server.data/'worldplan.json').read_text());assert restored==manifest
            write(a.out/'restart.json',dict(status=after,plan_sha256=restored['plan_sha256']));complete()
    except BaseException as error:
        result['failed'].append(dict(case=stage,error=repr(error)));result['unrun']=[v for v in selected if v not in result['completed'] and v!=stage]
        raise
    finally:
        if server.process and server.process.poll() is None:
            try:server.command('forceload remove all')
            except Exception:pass
        server.stop();write(a.out/'result.json',result)

if __name__=='__main__':main()
