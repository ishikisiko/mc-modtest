"""在一个无客户端的 GameTest 测试场中批量检查六套 NPC 住宅。"""
from pathlib import Path
import argparse,json,os,shutil,sys,time,traceback
ROOT=Path(__file__).resolve().parents[2];sys.path[:0]=[str(ROOT),str(ROOT/'tools')]
from qianji.server import Server


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--out',type=Path,required=True);ap.add_argument('--probe',type=Path,required=True)
    ap.add_argument('--ids',nargs='+',help='只检查指定的 NPC 住宅；默认检查六套')
    ap.add_argument('--normal-ticks',action='store_true',help='按普通 tick 速度运行，便于连接客户端观察')
    a=ap.parse_args()
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);game=ROOT/'run-housing-lab'/out.name
    assert not game.exists(),'检查目录已存在'
    (game/'mods').mkdir(parents=True);shutil.copy2(a.probe,game/'mods'/a.probe.name)
    catalog=json.loads((ROOT/'build/generated/housing/resources/data/yuanxian/housing/catalog.json').read_text());homes=[h for h in catalog['houses']if h['role']=='npc'];assert len(homes)==6
    if a.ids:
        assert len(a.ids)==len(set(a.ids)) and set(a.ids)<=set(h['id']for h in homes),'指定的住宅名称无效或重复'
        homes=[h for h in homes if h['id']in a.ids]
    os.environ['MYVILLAGE_HOUSING_LAB_IDS']=','.join(h['id']for h in homes)
    report={'status':'running','selected':[h['id']for h in homes],'completed':[],'failed':[],'unrun':[h['id']for h in homes],
        'runtime':'development_classpath','native_implementation':'正式方块、实体 tick、导航和开门配置','client':'not_started','mapped_player_input':'not_verified',
        'templates':{h['id']:h['sha256']for h in homes},'tick_mode':'normal'if a.normal_ticks else'accelerated_native_ticks'}
    def save():(out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    server=Server(out,game,2026111701360);server.level_type='minecraft\\:flat';server.generate_structures='false';server.readiness_command='list';server.startup_timeout=600;server.init_script='tools/housing_lab/runtime.init.gradle';save()
    try:
        started=time.monotonic();server.start();report['startup_seconds']=round(time.monotonic()-started,3);started=time.monotonic()
        reply=server.command('execute positioned 0 80 0 run test runall');(out/'test-command.txt').write_text(reply)
        if not a.normal_ticks:
            rules=json.loads((ROOT/'src/main/resources/data/yuanxian/world_sim/life_rules.json').read_text());server.command(f'tick sprint {rules["path_timeout_ticks"]*2+80}t')
        deadline=time.monotonic()+240
        while report['unrun']:
            for id in list(report['unrun']):
                path=game/f'housing-gametest-{id}.json'
                if not path.exists():continue
                value=json.loads(path.read_text());(out/path.name).write_text(json.dumps(value,ensure_ascii=False,indent=2))
                if value['status']=='running':continue
                report['unrun'].remove(id)
                if value['status']=='passed':report['completed'].append(id)
                else:report['failed'].append({'id':id,'error':value.get('error','')})
                save()
            if time.monotonic()>deadline:raise TimeoutError('GameTest 尚未结束 '+str(report['unrun']))
            if server.process.poll()is not None:raise RuntimeError('测试服务器提前退出')
            if report['unrun']:time.sleep(1)
        report['execution_seconds']=round(time.monotonic()-started,3);report['status']='passed'if not report['failed']else'failed'
    except BaseException as e:
        report['status']='failed';report['failure']={'error':repr(e),'traceback':traceback.format_exc()};raise
    finally:
        try:server.stop()
        finally:save()
    if report['failed']:raise SystemExit('住宅 GameTest 有失败项')


if __name__=='__main__':main()
