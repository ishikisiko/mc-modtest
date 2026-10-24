"""Remote-only V5 settlement generation/restart check; RCON is fixture and readback only."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import secrets

from tools.regiongen.bay_native import (
    SavedWorld, archive_world, audit, creative_handoff_metadata, wait_loaded, world_point,
)
from tools.regiongen.bay_assets import DEFAULT_OUT
from tools.regiongen.build import write_json
from tools.worldplan import acceptance as support
from buildgen.nbtread import read_gzipped_nbt


def piece_for(site):
    return {'ox':site['x'],'oy':site['offsetY'],'oz':site['z'],'rotation':site['rotation'],
            'site_seed':site['siteSeed'],'layout_hash':site['layoutHash']}


def tickets_released(reply):
    # Exact vanilla 1.21.1 overworld reply; do not accept generic successful command text.
    return reply.strip() == 'No force loaded chunks were found in Overworld'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--request',type=Path,required=True)
    parser.add_argument('--seed',type=int,default=20261025)
    args=parser.parse_args()
    request=json.loads(args.request.read_text())
    provider=request.get('provider')
    if provider=='colab':
        if os.environ.get('MYVILLAGE_REMOTE_PROVIDER')!='colab' or not str(support.ROOT).startswith('/content/'):
            raise SystemExit('Colab validation requires the authorized remote workspace')
    elif provider!='modal' or not os.environ.get('MODAL_SANDBOX_ID'):
        raise SystemExit('Native validation must run on the authorized remote provider')
    out=args.out;out.mkdir(parents=True,exist_ok=False)
    support.OUT=out;support.SEED=args.seed
    manifest=json.loads((DEFAULT_OUT/'data/myvillage/regiongen/bay_terraces_v1.json').read_text())
    name='settlements_v5_'+str(args.seed)+'_'+secrets.token_hex(4);world=support.GAME/name
    if world.exists():raise ValueError('Expected an independent new world')
    report={'status':'running','source_commit':request['commit'],'collector_commit':request.get('collector_commit',request['commit']),
            'provider':provider,'seed':args.seed,'world':name,
            'selected':['jar_resources','planned_sites','partial_save','frozen_save','full_settlement','restart_preserves_edit','creative_handoff'],
            'completed':[],'checks':{},'not_verified':['player_traversal','FPS','NPC_navigation','multi_seed_native']}
    def save():write_json(out/'report.json',report)
    def check(key,passed,details=None):
        report['checks'][key]={'pass':bool(passed),'details':details};save()
        if not passed:raise AssertionError(f'{key}: {details}')
    try:
        report['jar']=support.check_jar();report['completed'].append('jar_resources')
        check('jar_resources',True,report['jar'])
        with support.Server(name,'initial') as server:
            info=server.query('settlements');sites=info['settlements']
            check('planned_sites',info['algorithm']=='myvillage:worldplan_v5' and bool(sites),info)
            site=sites[0];piece=piece_for(site);report.update(plan=info,site=site,piece=piece)
            report['completed'].append('planned_sites')
            cx,cz=(site['x']+manifest['size']//2)//16,(site['z']+manifest['size']//2)//16
            server.cmd(f'forceload add {cx*16} {cz*16}');wait_loaded(server,[(cx*16,cz*16)])
            marker=(cx*16+8,piece['oy']+manifest['base_y']+manifest['height']+2,cz*16+8)
            at=' '.join(map(str,marker))
            check('marker_was_air','Test passed' in server.cmd(f'execute if block {at} minecraft:air'))
            server.cmd(f'setblock {at} minecraft:gold_block');server.cmd(f'forceload remove {cx*16} {cz*16}')
            report['marker_fixture']=marker;report['completed'].append('partial_save')
        cleanup=json.loads((out/'initial-cleanup.json').read_text())
        check('initial_stop',cleanup['terminated'] and not cleanup['forced_stop'],cleanup)
        _,tag=read_gzipped_nbt(str(world/'level.dat'));settings=tag['Data']['WorldGenSettings']
        generator=settings['dimensions']['minecraft:overworld']['generator']
        rules=support.decode_saved_rules(generator['rules'],'myvillage:worldplan_v5')
        check('frozen_save',settings['seed']==args.seed and generator['type']=='myvillage:planned_v5'
              and generator['plan_hash']==info['plan_sha256']
              and rules['settlements']['asset']['layout_hash']==manifest['layout_hash'],
              {'seed':settings['seed'],'generator':generator['type'],'plan_sha256':generator['plan_hash']})
        report['completed'].append('frozen_save')
        n=manifest['size'];margin=manifest['worldgen']['margin']
        bounds=(piece['ox']-margin,piece['oz']-margin,piece['ox']+n+margin-1,piece['oz']+n+margin-1)
        coords=[(x,z) for z in range(bounds[1],bounds[3]+1,16) for x in range(bounds[0],bounds[2]+1,16)]
        if len(coords)>256:raise ValueError('Ticket bound exceeded')
        with support.Server(name,'restart') as server:
            after=server.query('settlements')
            check('plan_survived',after['plan_sha256']==info['plan_sha256'] and after['settlements']==sites)
            server.cmd('forceload add '+' '.join(map(str,bounds)));wait_loaded(server,coords)
            at=' '.join(map(str,marker))
            check('restart_preserves_edit','Test passed' in server.cmd(f'execute if block {at} minecraft:gold_block'))
            server.cmd(f'setblock {at} minecraft:air');report['completed'].append('restart_preserves_edit')
            entry=manifest['entry'];spawn=world_point(piece,entry['x'],entry['y'],entry['z'],n);sx,sy,sz=spawn
            check('handoff_spawn_clear','Test passed' in server.cmd(f'execute if block {sx} {sy} {sz} minecraft:air if block {sx} {sy+1} {sz} minecraft:air unless block {sx} {sy-1} {sz} minecraft:air'))
            server.cmd(f'setworldspawn {sx} {sy} {sz} {(180+piece["rotation"]*90)%360}')
            server.cmd('gamerule spawnRadius 0');server.cmd('defaultgamemode creative')
            report['entry']=spawn;server.cmd('forceload remove '+' '.join(map(str,bounds)))
            release=server.cmd('forceload query');report['ticket_release']=release
            check('tickets_released',tickets_released(release),release)
        cleanup=json.loads((out/'restart-cleanup.json').read_text())
        check('restart_stop',cleanup['terminated'] and not cleanup['forced_stop'],cleanup)
        _,tickets=read_gzipped_nbt(str(world/'data/chunks.dat'))
        check('saved_tickets_empty',tickets['data']['Forced']==[],tickets['data'])
        result=audit(world,piece,manifest);write_json(out/'saved-world-comparison.json',result)
        check('full_settlement',result['passed'],{k:v for k,v in result.items() if k not in ('examples','default_or_derived_changes')})
        report['completed'].append('full_settlement')
        # A planned site is emitted through the same slice stage as the main city,
        # so a duplicate random-spread bay start inside its footprint is a failure.
        saved=SavedWorld(world);duplicates=[]
        for x,z in coords:
            start=saved.chunk(x//16,z//16).get('structures',{}).get('starts',{}).get('myvillage:bay_terraces',{})
            if start.get('id')=='myvillage:bay_terraces':duplicates.append([x,z])
        check('no_duplicate_random_settlement',not duplicates,duplicates)
        report['handoff_metadata_changes']=creative_handoff_metadata(world/'level.dat')
        _,tag=read_gzipped_nbt(str(world/'level.dat'))
        check('creative_handoff',tag['Data']['allowCommands']==1 and tag['Data']['GameType']==1)
        report['completed'].append('creative_handoff')
        report['saved_world']=archive_world(world,out/'saved-world.zip');report['status']='passed'
    except BaseException as error:
        report['status']='failed';report['error']=repr(error);raise
    finally:
        if report['status']!='passed' and (world/'level.dat').is_file():
            try:report['failed_world']=archive_world(world,out/'failed-world.zip')
            except Exception as error:report['failed_world_archive_error']=repr(error)
        report['unrun']=[key for key in report['selected'] if key not in report['completed']];save()
        print(json.dumps({k:v for k,v in report.items() if k!='checks'},ensure_ascii=False),flush=True)


if __name__=='__main__':main()
