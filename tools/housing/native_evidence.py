"""独立新世界检查正式院宅、领取收费、施工续建和 NPC 实际通行。"""
from __future__ import annotations
import argparse,json,shutil,sys,time,traceback
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path[:0]=[str(ROOT),str(ROOT/'tools')]
from qianji.server import Server
from roads_smoke.run import verify_candidate


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for key in ('out','jar','probe'):ap.add_argument('--'+key,type=Path,required=True)
    ap.add_argument('--seeds',nargs='+',type=int,default=[2026111701360,2026111701361])
    ap.add_argument('--checks',choices=['all','player','npc'],default='all')
    ap.add_argument('--reuse-world',type=Path,help='复用本工具创建的隔离世界；仅用于输入未变的局部复验')
    ap.add_argument('--resume-claimed-fixture',action='store_true',help='从夹具已领取且尚未建房的宅地继续；不重复领取检查')
    ap.add_argument('--player-start-index',type=int,choices=range(7),default=0,help='仅复验尚未完成的后续宅地；大于零时复用隔离世界')
    ap.add_argument('--require-staffed-homes',action='store_true',help='在已准备本地适任散修的隔离世界中，要求各城抽检院宅均有住户')
    a=ap.parse_args()
    if a.reuse_world and len(a.seeds)!=1:ap.error('复用世界须明确指定一个种子')
    if a.resume_claimed_fixture and(not a.reuse_world or a.checks!='player'):ap.error('继续已领取夹具须指定隔离世界和 player 检查')
    if a.player_start_index and(not a.reuse_world or a.checks!='player'or a.resume_claimed_fixture):ap.error('后续宅地复验须指定隔离世界及 player 检查，不能同时重跑首块宅地收费')
    if a.require_staffed_homes and (not a.reuse_world or a.checks!='npc'):ap.error('指定院宅必有住户须复用准备好的隔离世界及 npc 检查')
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
    case_prefix='new_world'if a.checks=='all'else a.checks
    if a.player_start_index:case_prefix+=f'_from_{a.player_start_index}'
    selected=['candidate_identity']+[f'{case_prefix}_{s}'for s in a.seeds]
    report={'status':'running','selected':selected,'completed':[],'failed':[],'unrun':selected.copy(),
        'evidence':'server_command_fixture','mapped_player_input':'not_verified','owner_appearance':'not_verified','multiplayer':'not_verified',
        'checks':a.checks,'world_origin':'reused_isolated'if a.reuse_world else'fresh_isolated','resume_claimed_fixture':a.resume_claimed_fixture,'player_start_index':a.player_start_index,
        'require_staffed_homes':a.require_staffed_homes}
    def save():(out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    def complete(stage):report['completed'].append(stage);report['unrun'].remove(stage);save()
    save();stage=selected[0]
    try:
        report['candidate']=verify_candidate(a.jar.resolve());complete(stage)
        for number,seed in enumerate(a.seeds):
            stage=f'{case_prefix}_{seed}';case=out/str(seed);case.mkdir();game=a.reuse_world.resolve()if a.reuse_world else ROOT/'run-housing'/f'{out.name}-{seed}'
            if a.reuse_world:
                assert game.parent==(ROOT/'run-housing').resolve()and(game/'world/level.dat').is_file(),'只能复用本工具的隔离世界'
                from buildgen.nbtread import read_gzipped_nbt
                _,data=read_gzipped_nbt(str(game/'world/level.dat'));assert data['Data']['WorldGenSettings']['seed']==seed,'复用世界种子不符'
            else:assert not game.exists(),'需要独立新世界'
            (game/'mods').mkdir(parents=True,exist_ok=bool(a.reuse_world));shutil.copy2(a.probe,game/'mods'/a.probe.name)
            server=Server(case,game,seed);server.startup_timeout=1200;server.readiness_command='list'
            def command(action,index=0,npc=False,allow_running=False):
                prefix='npc_housing_probe'if npc else'housing_probe';reply=server.command(f'{prefix} {action} {index}')
                path=game/f'{"npc-housing-probe"if npc else"housing-probe"}-{action}-{index}.json'
                if not path.exists():raise AssertionError(reply)
                result=json.loads(path.read_text());(case/path.name).write_text(json.dumps(result,ensure_ascii=False,indent=2))
                assert result['status']=='passed'or allow_running and result['status']=='running',result
                assert all(c['passed']for c in result.get('checks',[])),result
                return result
            def loaded(box,y):
                x,z,e,f=box;server.command(f'forceload add {x} {z} {e-1} {f-1}')
                deadline=time.monotonic()+180
                while not all(server.test(f'if loaded {xx} {y} {zz}')for xx,zz in ((x,z),(e-1,f-1))):
                    if time.monotonic()>deadline:raise TimeoutError('目标区块未加载')
                    time.sleep(1)
                # FULL 与实体 tick 是两个阶段，执事还需要一次服务器事件周期。
                server.command('tick sprint 40t');time.sleep(1)
            def settled(i,building=False):
                deadline=time.monotonic()+180
                while True:
                    state=command('status',i)
                    if building and state.get('state')=='built':return state
                    if not building and not state['pending']:return state
                    if state.get('state')=='blocked':raise AssertionError(state)
                    if building and not state['pending']and state.get('state')=='empty':raise AssertionError(state)
                    if time.monotonic()>deadline:raise TimeoutError(state)
                    time.sleep(.5)
            try:
                server.start()
                if a.resume_claimed_fixture:command('cleanup')
                info=command('info');plots=info['sites']
                if number==0 and a.checks!='npc':
                    if a.player_start_index==0:
                        first=plots[0];x,y,z=first['origin'];loaded([x-4,z-4,x+first['width']+4,z+first['depth']+4],y)
                        if a.resume_claimed_fixture:command('resume')
                        else:command('fees')
                        command('vacate');command('blocked');settled(0);command('blocked_check');command('unpaid');settled(0);command('unpaid_check');command('begin')
                        deadline=time.monotonic()+30
                        while True:
                            state=command('status')
                            if state.get('state')=='building'and state.get('cursor',0)>0:break
                            if not state['pending']and state.get('state')!='building':raise AssertionError(state)
                            if time.monotonic()>deadline:raise TimeoutError('尚未进入可保存的施工进度')
                            time.sleep(.1)
                        server.command('tick freeze');saved=command('status');assert saved['state']=='building'and saved['cursor']>0,saved
                        (case/'restart-before.json').write_text(json.dumps(saved,indent=2));command('offline');server.stop();server.start();command('restore');server.command('tick unfreeze');settled(0,True);command('readback');command('alter');time.sleep(2);command('alter_check')
                        command('cleanup')
                    for i,plot in enumerate(plots):
                        if i<max(1,a.player_start_index):continue
                        server.command('forceload remove all');x,y,z=plot['origin'];loaded([x-4,z-4,x+plot['width']+4,z+plot['depth']+4],y)
                        command('vacate',i);command('begin',i);settled(i,True);command('readback',i)
                    command('coverage');command('cleanup')
                for i in range(7)if a.checks!='player'else():
                    server.command('forceload remove all');description=command('info',i,npc=True);outside=description['outside'];inside=description['inside'];ox,oy,oz=outside;ix,iy,iz=inside
                    # 包含整套住宅及其路口，不能用传送把 NPC 直接放到终点。
                    box=description['housing']['bounds'];base_x,_,base_z=description['origin']
                    loaded([base_x+box[0]-8,base_z+box[1]-8,base_x+box[2]+8,base_z+box[3]+8],oy)
                    server.command('tick sprint 200t');time.sleep(2);command('staffed_audit'if a.require_staffed_homes else'audit',i,npc=True)
                    if number==0:
                        command('walk',i,npc=True,allow_running=True);path=game/f'npc-housing-probe-walk-{i}.json';deadline=time.monotonic()+180
                        while True:
                            value=json.loads(path.read_text())
                            if value['status']!='running':break
                            if time.monotonic()>deadline:raise TimeoutError(value)
                            server.command('tick sprint 100t');time.sleep(.5)
                        (case/path.name).write_text(json.dumps(value,ensure_ascii=False,indent=2));assert value['status']=='passed',value
                    server.command('forceload remove all')
                    west,north,east,south=description['enclosure']
                    loaded([base_x+west,base_z+north,base_x+east,base_z+south],oy)
                    command('enclosure',i,npc=True)
                complete(stage)
            finally:
                try:
                    if server.process is not None and server.process.poll()is None:
                        try:command('cleanup')
                        except Exception as error:
                            report.setdefault('cleanup_failures',[]).append({'case':stage,'error':repr(error)});save()
                finally:server.stop()
        if report.get('cleanup_failures'):raise RuntimeError('测试夹具清理失败 '+str(report['cleanup_failures']))
        report['status']='passed'
    except BaseException as e:
        report['status']='failed';report['failed'].append({'case':stage,'error':repr(e),'traceback':traceback.format_exc()})
        if stage in report['unrun']:report['unrun'].remove(stage)
        raise
    finally:save()


if __name__=='__main__':main()
