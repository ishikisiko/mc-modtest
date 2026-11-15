"""源仙入口的独立服务器检查；只调用服务端命令，不声明玩家输入或界面外观验收。"""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,time,traceback
ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT),str(ROOT/'tools')]
from tools.qianji.server import Server as LifecycleServer
from tools.roads_smoke.run import verify_candidate
from tools.roadweaver_probe.run import write
CASES=['metadata','namespace','language_zh_cn','language_en_us','shared_tree','old_root_absent','help_default_and_localized_alias','help_permissions','admin_denied_level_0','admin_denied_level_1','admin_execution_on_both_entries','admin_branches','public_command_execution','parameter_roundtrip','completion_parity']
class Server(LifecycleServer):
    def start(self):
        if not self._released():raise RuntimeError('自有世界或端口尚未释放')
        self.starts+=1;self.game.mkdir(parents=True,exist_ok=True)
        values={'server-ip':'127.0.0.1','server-port':self.sport,'online-mode':'false','enforce-secure-profile':'false','enable-rcon':'true','rcon.port':self.rport,'rcon.password':self.password,'broadcast-rcon-to-ops':'false','enable-query':'false','level-name':'world','level-seed':202611150134,'level-type':'minecraft\\:flat','generator-settings':'{}','generate-structures':'false','gamemode':'creative','difficulty':'peaceful','view-distance':2,'simulation-distance':2,'max-tick-time':180000,'spawn-protection':0,'max-players':2,'pause-when-empty-seconds':-1}
        (self.game/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in values.items())+'\n');(self.game/'eula.txt').write_text('eula=true\n')
        cmd=['./gradlew','-I','tools/roads_smoke/runtime.init.gradle','runAcceptanceServer','-x','processResources','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx4g']
        self.log=(self.out/'server.log').open('w');self.process=subprocess.Popen(cmd,cwd=ROOT,env=dict(os.environ,MYVILLAGE_ROAD_ADAPTER_GAME=str(self.game)),stdout=self.log,stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL,start_new_session=True)
        end=time.monotonic()+420
        while time.monotonic()<end:
            if self.process.poll() is not None:raise RuntimeError('服务器提前退出 '+str(self.process.returncode))
            try:
                if self.command('list'):return
            except (OSError,RuntimeError):pass
            time.sleep(1)
        raise TimeoutError('服务器启动未完成')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--probe',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=False);game=ROOT/'run-yuanxian-name'/out.name;assert not game.exists();(game/'mods').mkdir(parents=True)
    import shutil
    shutil.copyfile(args.probe,game/'mods'/args.probe.name);server=Server(out,game)
    report=dict(kind='yuanxian_brand_commands_native',status='running',selected=CASES,completed=[],failed=[],unrun=CASES.copy(),game=str(game),collector_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),mapped_input='not_verified',client_appearance='not_verified')
    def save():write(out/'report.json',report)
    save()
    try:
        report['candidate']=verify_candidate(args.jar);save();server.start()
        answer=server.command('brand_probe');write(out/'command-result.json',dict(command='brand_probe',response=answer))
        assert 'BRAND_PROBE ' in answer,answer
        probe=json.loads((game/'brand-probe.json').read_text());write(out/'brand-probe.json',probe)
        assert [v['id'] for v in probe['checks']]==CASES
        report['completed']=[v['id'] for v in probe['checks'] if v['passed']];report['failed']=[v for v in probe['checks'] if not v['passed']];report['unrun']=[]
        report['status']='passed' if not report['failed'] else 'failed';save();assert probe['passed'],report['failed']
    except BaseException as error:
        report['status']='failed';report['exception']=dict(error=repr(error),traceback=traceback.format_exc());save();raise
    finally:
        try:server.stop()
        finally:save()
if __name__=='__main__':main()
