"""Remote Chapter 1 building-generation evidence, without running or binding the story."""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import secrets
import zipfile

from tools.regiongen.qianzhen_assets import DEFAULT_OUT,RESOURCE
from tools.regiongen.bay_native import SavedWorld,wait_loaded,archive_world,creative_handoff_metadata,sign_component
from tools.regiongen.native_compare import difference
from tools.regiongen.assets import rotated_state
from tools.worldplan.settlement_native import tickets_released
from tools.worldplan import acceptance as support
from buildgen.nbtread import read_gzipped_nbt,state_string


def point(site,pos,size):
    x,y,z=pos
    for _ in range(site['rotation']):x,z=size-1-z,x
    return site['x']+x,site['groundY']+y,site['z']+z


def audit(world,site,manifest):
    saved=SavedWorld(world);size=manifest['size'];margin=manifest['terrain']['margin'];chunks=0
    for z in range(site['z']-margin,site['z']+size+margin,16):
        for x in range(site['x']-margin,site['x']+size+margin,16):
            chunk=saved.chunk(x//16,z//16)
            if not chunk or str(chunk.get('Status','')).split(':')[-1]!='full':raise ValueError(f'Missing FULL chapter chunk {x},{z}')
            chunks+=1
    expected={};per_site={};errors=[];counts=Counter();derived=Counter();signs=0
    source_root=support.ROOT/'build/generated/qianzhen/resources'
    for building in manifest['sites']:
        path=source_root/('data/myvillage/structure/'+building['template'].split(':',1)[1]+'.nbt')
        if hashlib.sha256(path.read_bytes()).hexdigest()!=building['sha256']:raise ValueError('Actual Chapter 1 source identity differs')
        _,tag=read_gzipped_nbt(str(path));palette=[rotated_state(state_string(p),site['rotation']) for p in tag['palette']]
        failed=0
        for block in tag['blocks']:
            local=[building['origin'][i]+block['pos'][i] for i in range(3)];at=point(site,local,size);wanted=palette[block['state']]
            expected[at]=wanted
            why=difference(wanted,saved.block(*at))
            if why:failed+=1
            if block.get('nbt',{}).get('id')=='minecraft:sign':
                signs+=1;chunk=saved.chunk(at[0]//16,at[2]//16)
                entity=next((e for e in chunk.get('block_entities',[]) if (e['x'],e['y'],e['z'])==at),None)
                wanted_text=[sign_component(v) for v in block['nbt']['front_text']['messages']]
                actual_text=[sign_component(v) for v in entity.get('front_text',{}).get('messages',[])] if entity else None
                if wanted_text!=actual_text:
                    failed+=1;counts['sign_text']+=1;errors.append({'site':building['id'],'position':at,'reason':'sign_text','expected':wanted_text,'actual':actual_text})
        per_site[building['id']]={'name':building['display_name'],'cells':len(tag['blocks']),'differences':failed,'passed':failed==0,'source_sha256':building['sha256']}
    for road in manifest['roads']:
        expected[point(site,road['pos'],size)]=rotated_state(road['state'],site['rotation'])
    for z in range(size):
        for x in range(size):expected.setdefault(point(site,[x,0,z],size),manifest['terrain']['surface'])
    cache={}
    for at,wanted in expected.items():
        actual=saved.block(*at);pair=(wanted,actual)
        if pair not in cache:cache[pair]=difference(wanted,actual)
        why=cache[pair]
        if why:
            counts[why]+=1
            if len(errors)<2000:errors.append({'position':at,'expected':wanted,'actual':actual,'reason':why})
        elif actual!=wanted:derived[wanted+' -> '+actual]+=1
    return {'passed':not counts and all(s['passed'] for s in per_site.values()),'chunks':chunks,'compared_cells':len(expected),
            'per_site':per_site,'signs':signs,'differences':dict(counts),'examples':errors,'derived_state_changes':dict(derived)}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--request',type=Path,required=True);parser.add_argument('--seed',type=int,default=20261025);args=parser.parse_args()
    request=json.loads(args.request.read_text())
    if request.get('provider')!='colab' or os.environ.get('MYVILLAGE_REMOTE_PROVIDER')!='colab' or not str(support.ROOT).startswith('/content/'):
        raise SystemExit('This run requires the user-authorized Colab workspace')
    out=args.out;out.mkdir(parents=True,exist_ok=False);support.OUT=out;support.SEED=args.seed
    manifest=json.loads((DEFAULT_OUT/RESOURCE).read_text());name='chapter_buildings_'+str(args.seed)+'_'+secrets.token_hex(4);world=support.GAME/name
    if world.exists():raise ValueError('Expected a fresh independent world')
    report={'status':'running','source_commit':request['commit'],'seed':args.seed,'world':name,
            'selected':['jar_and_six_sources','planned_chapter_sites','partial_save_and_reload','six_site_and_road_comparison','restart_preserves_edit','story_remains_unbound','tickets_released','creative_handoff'],
            'completed':[],'checks':{},'not_verified':['story_gameplay','player_traversal','NPC_navigation','FPS','multi_seed_native']}
    def save():support.write(out/'report.json',report)
    def check(key,passed,details=None):
        report['checks'][key]={'pass':bool(passed),'details':details};save()
        if not passed:raise AssertionError(f'{key}: {details}')
    def stop_check(label):
        cleanup=json.loads((out/(label+'-cleanup.json')).read_text());check(label+'_stop',cleanup['terminated'] and not cleanup['forced_stop'] and cleanup['returncode']==0,cleanup)
    try:
        report['jar']=support.check_jar()
        with zipfile.ZipFile(support.ROOT/'build/libs'/report['jar']['file']) as jar:
            check('jar_region_identity',json.loads(jar.read(RESOURCE))==manifest)
            check('six_original_sources',all(hashlib.sha256(jar.read('data/myvillage/structure/'+b['template'].split(':')[1]+'.nbt')).hexdigest()==b['sha256'] for b in manifest['sites']))
        report['completed'].append('jar_and_six_sources')
        with support.Server(name,'initial') as server:
            info=server.query('qianzhen');site=info.get('qianzhen')
            check('planned_chapter_sites',info['algorithm']==support.DEFAULT_ALGORITHM and site is not None and {b['id'] for b in info['qianzhen_buildings']}==set(manifest['source_templates']),info)
            report.update(plan=info,site=site);report['completed'].append('planned_chapter_sites')
            cx,cz=(site['x']+manifest['size']//2)//16,(site['z']+manifest['size']//2)//16
            server.cmd(f'forceload add {cx*16} {cz*16}');wait_loaded(server,[(cx*16,cz*16)])
            marker=(cx*16+8,site['groundY']+manifest['height']+2,cz*16+8);at=' '.join(map(str,marker))
            check('marker_was_air','Test passed' in server.cmd(f'execute if block {at} minecraft:air'))
            server.cmd(f'setblock {at} minecraft:gold_block');server.cmd(f'forceload remove {cx*16} {cz*16}')
            report['marker_fixture']=marker
        stop_check('initial');support.check_saved(world,info['plan_sha256'])
        with support.Server(name,'restart') as server:
            restored=server.query('qianzhen');check('frozen_plan_survived',restored['plan_sha256']==info['plan_sha256'] and restored['qianzhen']==site)
            report['completed'].append('partial_save_and_reload')
            n=manifest['size'];margin=manifest['terrain']['margin'];min_x=site['x']-margin;min_z=site['z']-margin;width=n+2*margin
            # At most 256 tickets at a time. The larger Chapter scene has a terrain-only outer transition.
            rows=max(1,256//(width//16));batches=[]
            for z in range(min_z,min_z+width,rows*16):
                end=min(z+rows*16,min_z+width);bounds=(min_x,z,min_x+width-1,end-1)
                coords=[(x,zz) for zz in range(z,end,16) for x in range(min_x,min_x+width,16)]
                if len(coords)>256:raise ValueError('Chapter ticket budget')
                server.cmd('forceload add '+' '.join(map(str,bounds)));wait_loaded(server,coords)
                server.cmd('forceload remove '+' '.join(map(str,bounds)))
                check('batch_'+str(len(batches))+'_tickets_empty',tickets_released(server.cmd('forceload query')))
                batches.append({'bounds':bounds,'chunks':len(coords)});report['batches']=batches;save()
            server.cmd(f'forceload add {cx*16} {cz*16}');wait_loaded(server,[(cx*16,cz*16)]);at=' '.join(map(str,marker))
            check('restart_preserves_edit','Test passed' in server.cmd(f'execute if block {at} minecraft:gold_block'))
            report['completed'].append('restart_preserves_edit');server.cmd(f'setblock {at} minecraft:air');server.cmd(f'forceload remove {cx*16} {cz*16}')
            sx,sy,sz=point(site,manifest['entry'],n);server.cmd(f'forceload add {sx} {sz}');wait_loaded(server,[(sx,sz)])
            check('entry_clear','Test passed' in server.cmd(f'execute if block {sx} {sy} {sz} minecraft:air if block {sx} {sy+1} {sz} minecraft:air unless block {sx} {sy-1} {sz} minecraft:air'))
            server.cmd(f'setworldspawn {sx} {sy} {sz} {(180+site["rotation"]*90)%360}');server.cmd('gamerule spawnRadius 0');server.cmd('defaultgamemode creative');report['entry']=[sx,sy,sz]
            server.cmd(f'forceload remove {sx} {sz}');check('tickets_released',tickets_released(server.cmd('forceload query')))
            reply=server.cmd('qianzhen where');check('story_not_deployed','阡镇尚未完成部署' in reply,reply)
        stop_check('restart')
        _,saved_tickets=read_gzipped_nbt(str(world/'data/chunks.dat'));check('saved_tickets_empty',saved_tickets['data']['Forced']==[])
        report['completed'].append('tickets_released')
        deployment=world/'data/myvillage_qianzhen_scene.dat'
        phase=read_gzipped_nbt(str(deployment))[1]['data']['phase'] if deployment.exists() else 'EMPTY'
        check('story_remains_unbound',phase=='EMPTY',{'saved_phase':phase});report['completed'].append('story_remains_unbound')
        comparison=audit(world,site,manifest);support.write(out/'saved-world-comparison.json',comparison)
        check('six_site_and_road_comparison',comparison['passed'],{k:v for k,v in comparison.items() if k not in ('examples','derived_state_changes')})
        report['completed'].append('six_site_and_road_comparison')
        report['handoff_metadata_changes']=creative_handoff_metadata(world/'level.dat');_,level=read_gzipped_nbt(str(world/'level.dat'))
        check('creative_handoff',level['Data']['allowCommands']==1 and level['Data']['GameType']==1);report['completed'].append('creative_handoff')
        report['saved_world']=archive_world(world,out/'saved-world.zip');report['status']='passed'
    except BaseException as error:report['status']='failed';report['error']=repr(error);raise
    finally:
        if report['status']!='passed' and (world/'level.dat').is_file():report['failed_world']=archive_world(world,out/'failed-world.zip')
        report['unrun']=[key for key in report['selected'] if key not in report['completed']];save()
        print(json.dumps({k:v for k,v in report.items() if k!='checks'},ensure_ascii=False),flush=True)


if __name__=='__main__':main()
