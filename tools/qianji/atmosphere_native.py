"""总舵重设计的定向原生检查：新世界自然生成、碰撞、灯光、阵枢保存重启。"""
from __future__ import annotations
import argparse,json,secrets,shutil,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools'))
from roads_smoke.run import verify_candidate
from qianji.server import Server
from roadweaver_probe.run import write

def main():
    p=argparse.ArgumentParser();p.add_argument('--jar',type=Path,required=True);p.add_argument('--source',required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--seed',type=int,default=42);a=p.parse_args()
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);game=ROOT/'run-fixed-progression'/('native-'+secrets.token_hex(5));server=Server(out,game,a.seed);server.startup_timeout=900
    selected=['candidate_identity','fresh_natural_headquarters','collision_and_light_readback','phase_and_saved_restart'];result=dict(source=a.source,seed=a.seed,selected=selected,completed=[],failed=[],unrun=selected.copy(),owner_visual_acceptance='not_verified',mapped_input='not_selected')
    stage=selected[0]
    def complete():result['completed'].append(stage);result['unrun'].remove(stage);write(out/'result.json',result)
    def query(command):return json.loads(server.command('myvillage worldplan '+command).split('WORLDPLAN ',1)[1])
    def readback():
        answer=server.command('myvillage qianji probe');return json.loads(answer[answer.index('{'):])
    def load(city,q):
        x0,z0,x1,z1=q['bounds'];x0+=city['x'];x1+=city['x']-1;z0+=city['z'];z1+=city['z']-1
        chunks={(x*16+8,z*16+8) for x in range(x0//16,x1//16+1) for z in range(z0//16,z1//16+1)};assert len(chunks)<=256
        server.command(f'forceload add {x0} {z0} {x1} {z1}');deadline=time.monotonic()+600
        while True:
            pending=[p for p in chunks if not server.test(f'if loaded {p[0]} 300 {p[1]}')]
            if not pending:break
            if time.monotonic()>deadline:raise TimeoutError('总舵自然区块未加载 '+str(len(pending)))
            time.sleep(1)
        return len(chunks)
    write(out/'world.json',dict(directory=str(game),seed=a.seed,kind='fresh isolated development world'));write(out/'result.json',result)
    try:
        result['identity']=verify_candidate(a.jar.resolve());complete();stage=selected[1]
        server.start();server.command('gamerule mobGriefing false');query('export');plan=json.loads((server.data/'worldplan.json').read_text());shutil.copy2(server.data/'worldplan.json',out/'worldplan.json')
        city=next(c for c in plan['cities'] if c['region_id']=='zhongzhou');q=json.loads((ROOT/'build/resources/main/data/myvillage/capitals/v4/capital_zhongzhou/city.json').read_text())['qianji']
        cfg=json.loads((ROOT/'blueprints/qianji/zhongzhou_infill.json').read_text());data=json.loads((ROOT/'build/resources/main/data/myvillage/capitals/v4/capital_zhongzhou/city.json').read_text())
        assert q['scale']=='headquarters' and q['infill']['id']==cfg['id']
        assert data['size']==q['source_dimensions']==[1200,320,1440]
        assert q['bounds']==cfg['plot'] and [q['size'][0],q['size'][2]]==cfg['size'] and q['rotation_quarters']==cfg['rotation_quarters']
        assert set(q['infill']['retired_buildings'])==set(cfg['approved_retirements'])
        assert len(q['infill']['retired_buildings'])==73 and q['infill']['maximum_terrain_cut']<=cfg['local_earthwork_limit']
        assert not set(cfg['approved_retirements'])&{row['id']for row in data['buildings']}
        result['infill']=q['infill'];result['city_dimensions']=data['size']
        result['chunks']=load(city,q);complete();stage=selected[2]
        minimum=json.loads((ROOT/'blueprints/qianji/lighting.json').read_text())['minimum_light']
        deadline=time.monotonic()+90
        while True:
            read=readback();write(out/'headquarters-readback.json',read)
            if read['all_standing'] and read['anchor_bound'] and all(t.get('block_light',0)>=minimum for t in read['targets'][:24]):break
            if time.monotonic()>deadline:raise AssertionError(read)
            time.sleep(2)
        assert len(read['targets'])==31;result['standing_targets']=len(read['targets']);result['light_minimum']=minimum;complete();stage=selected[3]
        anchor=' '.join(map(str,read['anchor']));before=server.command('myvillage qianji phase 50');assert '50.0' in before,before
        offset=server.command('data get block '+anchor+' CycleOffset');write(out/'phase-before.json',dict(command=before,offset=offset))
        invalid=server.command('myvillage qianji phase 78');assert '相位超过当前周期' in invalid,invalid
        assert server.command('data get block '+anchor+' CycleOffset')==offset
        server.stop();server.start();query('export');assert json.loads((server.data/'worldplan.json').read_text())==plan
        load(city,q);after=readback();assert after['anchor_bound'] and after['all_standing'];assert server.command('data get block '+anchor+' CycleOffset')==offset
        write(out/'phase-after-restart.json',dict(offset=offset,readback=after,invalid_phase=invalid));complete()
    except BaseException as error:result['failed'].append(dict(case=stage,error=repr(error)));raise
    finally:
        server.stop();result['unrun']=[v for v in selected if v not in result['completed'] and v not in [f['case'] for f in result['failed']]];write(out/'result.json',result)

if __name__=='__main__':main()
