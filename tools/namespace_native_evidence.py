"""隔离新大陆的命名空间验收；原生注册表、网络、资源绑定与保存重启。"""
from pathlib import Path
import argparse,hashlib,json,secrets,shutil,subprocess,sys,time,traceback
ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT),str(ROOT/'tools')]
from tools.qianji.server import Server as LifecycleServer
from tools.qianzhen.realm_evidence import Client,wait
from tools.roads_smoke.run import verify_candidate
from tools.roadweaver_probe.run import write
CASES=['candidate_identity','new_world_registry_and_resources','command_roots','client_login_and_assets','mapped_profile_network','saved_world_restart']
class Server(LifecycleServer):
 def cmd(self,text):return self.command(text)
class Evidence:
 def __init__(self,out,jar,probe,client):
  self.out=out.resolve();self.out.mkdir(parents=True,exist_ok=False);self.jar=jar.resolve();self.with_client=client
  game=ROOT/'run-namespace'/('native-'+secrets.token_hex(4));assert not game.exists();(game/'mods').mkdir(parents=True);shutil.copyfile(probe,game/'mods'/probe.name)
  self.server=Server(self.out,game,202611150134);self.server.startup_timeout=1800;self.server.view_distance=3
  self.report=dict(status='running',selected=CASES.copy(),completed=[],failed=[],unrun=CASES.copy(),actions=[],commands=[],screenshots=[],cleanup=[],world=str(game),collector_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),owner_appearance='not_verified',owner_feel='not_verified',owner_fps='not_verified');self.stage=CASES[0];self.save()
 def save(self):write(self.out/'report.json',self.report)
 def action(self,kind,**detail):self.report['actions'].append(dict(kind=kind,time=time.monotonic(),**detail));self.save()
 def rc(self,command):
  answer=self.server.command(command);self.report['commands'].append(dict(command=command,response=answer));self.save();return answer
 def done(self):self.report['completed'].append(self.stage);self.report['unrun'].remove(self.stage);self.save()
 def probe(self,name,label):
  answer=self.rc(name);shutil.copyfile(self.server.game/(name.replace('_','-')+'.json'),self.out/(label+'.json'))
  value=json.loads((self.out/(label+'.json')).read_text());assert value['passed'],value;return value
 def run(self):
  try:
   self.report['candidate']=verify_candidate(self.jar);self.done()
   self.stage='new_world_registry_and_resources';self.server.start();before=self.probe('namespace_probe','namespace-initial');self.done()
   self.stage='command_roots';self.probe('brand_probe','brand');self.done()
   if self.with_client:
    self.stage='client_login_and_assets'
    with Client(self,self.server,'YuanxianWitness','resources',prebuilt_resources=True,heap='4g') as client:
     self.rc('op '+client.name);self.rc('gamemode creative '+client.name);self.rc('weather clear');self.rc('time set noon')
     self.rc('item replace entity '+client.name+' hotbar.0 with yuanxian:qingfeng_sword');self.rc('give '+client.name+' yuanxian:low_grade_spirit_stone 8')
     client.close_screen();client.guard();client.game.xdo('key','1');self.action('mapped_hotbar',slot=1);time.sleep(2);client.capture('new-namespace-client');self.done()
     self.stage='mapped_profile_network';client.guard();client.game.xdo('key','h');self.action('mapped_key',key='h');wait(client.game.pointer_grabbed,lambda v:v is False,20,'修炼面板打开');time.sleep(2);client.capture('cultivation-profile');client.close_screen()
     # 正式 V 键经新命名空间的意图载荷到达服务器，正式状态命令读回。
     def state():return json.loads(self.rc('namespace_player_probe '+client.name).split('NAMESPACE_PLAYER ',1)[1])['meditation_state']
     assert state()=='IDLE'
     client.guard();client.game.xdo('key','v');self.action('mapped_key',key='v');wait(state,lambda value:value in ('PREPARING_NORMAL','MEDITATING_NORMAL'),20,'新网络ID接收打坐意图')
     client.capture('meditation-network');client.guard();client.game.xdo('key','x');self.action('mapped_key',key='x');wait(state,lambda value:value=='IDLE',20,'新网络ID接收停止意图');self.done()
    log=(self.out/'resources-YuanxianWitness/client.log').read_text(errors='replace')
    import re
    bad=[line for line in log.splitlines() if re.search(r'missing|unable to load|failed to load|unknown custom packet|disconnect',line,re.I) and ('yuanxian' in line or 'packet' in line.lower())]
    write(self.out/'client-resource-errors.json',dict(errors=bad));assert not bad,bad
   self.stage='saved_world_restart';self.rc('save-all flush');self.server.stop();self.server.start();after=self.probe('namespace_probe','namespace-restart');self.probe('brand_probe','brand-restart');assert before['counts']==after['counts'],(before,after);self.done()
   self.report['status']='passed' if not self.report['unrun'] else 'partial';self.save()
  except BaseException as error:
   self.report['status']='failed';self.report['failed'].append(dict(stage=self.stage,error=repr(error),traceback=traceback.format_exc()));self.save();raise
  finally:
   try:self.server.stop()
   finally:self.save()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--probe',type=Path,required=True);p.add_argument('--client',action='store_true');a=p.parse_args();Evidence(a.out,a.jar,a.probe,a.client).run()
if __name__=='__main__':main()
