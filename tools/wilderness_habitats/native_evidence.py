"""固定候选的东荒原生检查。攻击、移动和放置使用映射输入，RCON只准备夹具与读回。

月份边界在自有服务端停机后修改唯一日历long值，保留原文件及哈希，再由正式运行时恢复。
不修改用户存档，不为开发夹具重新编译Java，也不把布置位置称为连续探索。
"""
from pathlib import Path
from contextlib import ExitStack
import argparse,gzip,hashlib,json,math,secrets,shutil,struct,subprocess,sys,time
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.qianji.server import Server as BaseServer
from tools.qianzhen.realm_evidence import Client,wait,one
from tools.alchemy_quality_evidence import read_data
from tools.yunshan.game_evidence import aim
from tools.roads_smoke.run import verify_candidate
from tools.roadweaver_probe.run import write
from tools.combat_capture import procs

CASES=['candidate_identity','natural_world_sites','finite_native_members','native_patrol_and_hunt','mapped_alarm','mapped_clear_and_temporary_loot','monthly_proximity_defer','monthly_recovery','save_restart_and_rollback','construction_preserved','shared_roster_two_clients','chunk_unload_no_duplicate','faction_conflict','low_health_retreat','other_regions_excluded']
class Server(BaseServer):
    def cmd(self,text):return self.command(text)

class Evidence:
    def __init__(self,out,world_ref,jar):
        self.out=out;out.mkdir(parents=True,exist_ok=False);self.jar=jar
        origin=json.loads(world_ref.read_text());self.game=Path(origin['directory']).resolve()
        assert self.game.parent==ROOT/'run-fixed-progression' and self.game.name.startswith('native-')
        self.server=Server(out,self.game,origin['seed']);self.server.startup_timeout=1200
        self.report=dict(kind='east_wilderness_native',status='running',selected=CASES,completed=[],failed=[],unrun=CASES.copy(),checks=[],actions=[],commands=[],screenshots=[],cleanup=[],states=[],world=str(self.game),seed=origin['seed'],owner_visual='not_verified',owner_feel='not_verified',owner_fps='not_verified',continuous_walk='not_verified')
        self.stage='candidate_identity';self.report['source']=json.loads((ROOT/'candidate.json').read_text())['commit'];self.report['collector_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest();self.save()
    def save(self):write(self.out/'report.json',self.report)
    def action(self,kind,**detail):self.report['actions'].append(dict(time=time.monotonic(),kind=kind,**detail));self.save()
    def rc(self,command):
        if command.startswith(('kill ','damage ')):raise AssertionError('战斗验收必须实际攻击')
        reply=self.server.command(command);self.report['commands'].append(dict(command=command,reply=reply));self.save();return reply
    def state(self):
        value=one(self.rc('myvillage habitat'),'HABITAT ');self.report['states'].append(value);self.save();return value
    def check(self,name,passed,detail):
        self.report['checks'].append(dict(name=name,passed=bool(passed),detail=detail));self.save()
        if not passed:raise AssertionError(name+': '+repr(detail))
    def done(self,name):
        if name not in self.report['completed']:self.report['completed'].append(name)
        self.report['unrun']=[x for x in CASES if x not in self.report['completed']];self.save()
    def begin(self,name):self.stage=name;self.report['current']=name;self.save()
    def round(self,site):return self.state()['state']['rounds'].get(site,{})
    def living(self,site):return [m for m in self.state()['living']if m['site']==site]
    def setup(self):
        for command in ['gamerule doMobSpawning true','difficulty normal','gamerule mobGriefing false','weather clear','time set noon']:self.rc(command)
    def prepare_player(self,client):
        for command in [f'op {client.name}',f'gamemode creative {client.name}',f'effect give {client.name} minecraft:resistance infinite 4 true',f'attribute {client.name} minecraft:generic.max_health base set 1024',f'effect give {client.name} minecraft:instant_health 1 10 true',f'item replace entity {client.name} hotbar.0 with myvillage:qingfeng_sword',f'attribute {client.name} minecraft:generic.attack_damage base set 8']:
            self.rc(command)
        client.guard();client.game.xdo('key','1');self.action('mapped_hotbar',player=client.name,slot=1)
    def fixture(self,client,position,target):
        client.close_screen();yaw,pitch=aim(position,target)
        self.rc(f'tp {client.name} '+' '.join(f'{x:.6f}'for x in [*position,yaw,pitch]));self.action('fixture_position',player=client.name,position=position,target=target);time.sleep(.5)
    def visit(self,client,site,distance=76):
        x,y,z=site['x'],site['y'],site['z'];self.rc(f'forceload add {x-64} {z-32} {x+96} {z+32}')
        self.fixture(client,[x+distance+.5,y+2,z+.5],[x+.5,y+1,z+.5])
        wait(lambda:self.state(),lambda s:site['id']in s['loaded_sites'],120,'生态据点区块已加载')
    def populate(self,site):
        wanted=len(next(k for k in json.loads((ROOT/'blueprints/wilderness/habitats.json').read_text())['kinds']if k['id']==site['kind'])['members'])
        return wait(lambda:self.living(site['id']),lambda v:len(v)==wanted,90,'原生有限成员 '+site['id'])
    def hit(self,client,actor):
        self.fixture(client,[actor['x']+2.1,actor['y'],actor['z']],[actor['x'],actor['y']+.75,actor['z']])
        client.guard();client.game.xdo('mousedown','1');time.sleep(.12);client.game.xdo('mouseup','1');self.action('mapped_attack',player=client.name,target=actor['uuid']);time.sleep(.8)
    def calendar(self,month,ticks_per_month):
        path=self.game/'world/data/myvillage_cultivation_calendar.dat';assert self.server.process is None or self.server.process.poll()is not None
        original=path.read_bytes();backup=self.out/f'calendar-before-{len(self.report["actions"])}.dat';backup.write_bytes(original)
        data=bytearray(gzip.decompress(original));key=b'elapsed_calendar_ticks';needle=b'\x04'+struct.pack('>H',len(key))+key
        assert data.count(needle)==1;offset=data.index(needle)+len(needle);before=struct.unpack_from('>q',data,offset)[0];struct.pack_into('>q',data,offset,month*ticks_per_month)
        path.write_bytes(gzip.compress(bytes(data),mtime=0));self.action('stopped_owned_calendar_fixture',before_ticks=before,after_ticks=month*ticks_per_month,backup=str(backup),before_sha256=hashlib.sha256(original).hexdigest(),after_sha256=hashlib.sha256(path.read_bytes()).hexdigest())
    def start_client(self,stack,label):
        self.server.start();self.setup();client=stack.enter_context(Client(self,self.server,'EastWitnessA',label,prebuilt_resources=True,heap='4g'));self.prepare_player(client);return client
    def run(self):
        try:
            self.report['identity']=verify_candidate(self.jar);self.done('candidate_identity')
            with ExitStack()as stack:
                self.begin('natural_world_sites')
                client=self.start_client(stack,'initial');status=self.state();sites=status['sites'];self.sites=sites
                self.check('十二处据点及四类成员',len(sites)==12 and {s['kind']for s in sites}=={'wolf_den','lizard_shelf','spore_grove','bandit_camp'},sites)
                for site in sites:
                    reply=self.rc(f'myvillage worldplan at {site["x"]} {site["z"]}');self.check('据点归属东荒 '+site['id'],'"profileId":"donghuang"'in reply or '"regionId":"donghuang"'in reply,reply)
                self.done('natural_world_sites');wolf=next(s for s in sites if s['id']=='wolf_den_0');camp=next(s for s in sites if s['id']=='bandit_camp_0')
                self.begin('finite_native_members')
                for kind in ('wolf_den','lizard_shelf','spore_grove','bandit_camp'):
                    site=next(s for s in sites if s['kind']==kind);self.visit(client,site);members=self.populate(site)
                    self.check('出生境界 '+kind,all(m['realm'].startswith('myvillage:qi_refining/') and 0<=int(m['realm'].split('/')[-1])<=3 for m in members),members)
                    self.fixture(client,[site['x']+12.5,site['y']+6,site['z']+12.5],[site['x']+.5,site['y']+1,site['z']+.5]);time.sleep(2);client.capture('native-'+kind)
                    if kind=='bandit_camp':
                        previous={m['uuid']:(m['x'],m['z'])for m in members};time.sleep(16);patrol=self.living(site['id'])
                        self.check('劫修巡逻有实际位移',any(m['activity']=='patrol'and math.hypot(m['x']-previous[m['uuid']][0],m['z']-previous[m['uuid']][1])>2 for m in patrol if m['uuid']in previous),patrol)
                self.done('finite_native_members')
                self.begin('native_patrol_and_hunt')
                for actor in self.state()['living']:
                    if actor['site']!=wolf['id']:self.rc(f'data merge entity {actor["uuid"]} {{NoAI:1b}}')
                self.rc('time set midnight');self.visit(client,wolf);before={m['uuid']:(m['x'],m['z'])for m in self.populate(wolf)};time.sleep(18)
                after=self.living(wolf['id']);self.check('夜间巡猎有实际位移',any(m['activity']=='hunt'and math.hypot(m['x']-before[m['uuid']][0],m['z']-before[m['uuid']][1])>2 for m in after),after);self.done('native_patrol_and_hunt')
                self.begin('shared_roster_two_clients')
                second=stack.enter_context(Client(self,self.server,'EastWitnessB','shared',prebuilt_resources=True,heap='4g'));self.prepare_player(second)
                ids={m['id']for m in self.round(wolf['id'])['members']};self.visit(second,wolf,distance=70);time.sleep(3)
                self.check('双玩家共享同一批身份',ids=={m['id']for m in self.round(wolf['id'])['members']},self.round(wolf['id']));self.done('shared_roster_two_clients')
                self.begin('chunk_unload_no_duplicate')
                wounded=self.living(wolf['id'])[-1];self.rc(f'data merge entity {wounded["uuid"]} {{Health:{wounded["max_health"]-1:.1f}f}}')
                self.rc('forceload remove all');spawn=json.loads((self.server.data/'worldplan.json').read_text())['spawn']
                for witness in (client,second):self.fixture(witness,[spawn['x']+.5,spawn['y'],spawn['z']+.5],[spawn['x']+2,spawn['y'],spawn['z']])
                wait(lambda:self.state(),lambda s:wolf['id']not in s['loaded_sites'],150,'原生区块卸载')
                self.check('卸载保留原名单',ids=={m['id']for m in self.round(wolf['id'])['members']},self.round(wolf['id']))
                self.visit(client,wolf);loaded=self.populate(wolf)
                self.check('重新加载没有复制成员或回血',ids=={m['uuid']for m in loaded} and next(m for m in loaded if m['uuid']==wounded['uuid'])['health']<=wounded['max_health']-1,loaded)
                self.done('chunk_unload_no_duplicate')
                self.begin('mapped_alarm')
                self.rc(f'gamemode survival {client.name}');target=self.living(wolf['id'])[0];self.hit(client,target)
                hit_result=next((m for m in self.living(wolf['id'])if m['uuid']==target['uuid']),None)
                self.check('映射攻击改变目标生命',hit_result is None or hit_result['health']<target['health'],{'before':target,'after':hit_result})
                alarm=wait(lambda:self.round(wolf['id']),lambda r:r.get('alarms',0)>0,20,'同群呼援')
                engaged=self.living(wolf['id']);self.check('实际呼援至少两个同伴取得目标',sum(bool(m['target'])for m in engaged)>=2,engaged);self.done('mapped_alarm')
                self.begin('mapped_clear_and_temporary_loot')
                self.rc(f'attribute {client.name} minecraft:generic.attack_damage base set 1000')
                deadline=time.monotonic()+120
                while self.round(wolf['id']).get('members')and any(not m['dead']for m in self.round(wolf['id'])['members']):
                    actors=self.living(wolf['id'])
                    if not actors:raise AssertionError('活成员未加载，不能算击败')
                    self.hit(client,actors[0])
                    if time.monotonic()>deadline:raise TimeoutError('映射清理未完成')
                cleared=self.round(wolf['id']);time.sleep(3);self.check('同月清理后保持空名单',not self.living(wolf['id']) and all(m['dead']for m in self.round(wolf['id'])['members']),cleared)
                drops=self.rc(f'execute positioned {wolf["x"]} {wolf["y"]} {wolf["z"]} as @e[type=minecraft:item,distance=..120] run data get entity @s Item')
                inventory=self.rc(f'data get entity {client.name} Inventory')
                self.check('临时灵石掉落','myvillage:low_grade_spirit_stone'in drops or'myvillage:low_grade_spirit_stone'in inventory,{'ground_items':drops,'player_inventory':inventory});self.done('mapped_clear_and_temporary_loot')
                self.rc(f'gamemode creative {client.name}');self.rc(f'gamemode creative {second.name}');self.fixture(client,[wolf['x']+.5,wolf['y']+1,wolf['z']+.5],[wolf['x'],wolf['y'],wolf['z']]);client.capture('cleared-wolf-den')
                self.rc('save-all flush')
            self.server.stop();self.calendar(1,status['ticks_per_month'])
            with ExitStack()as stack:
                self.begin('monthly_proximity_defer')
                client=self.start_client(stack,'month-one');self.visit(client,wolf,distance=10);time.sleep(3)
                self.check('近距离延后换月恢复',self.round(wolf['id'])['generation']==cleared['generation'],self.round(wolf['id']));self.done('monthly_proximity_defer')
                self.begin('monthly_recovery')
                self.visit(client,wolf);fresh=self.populate(wolf);now=self.round(wolf['id']);self.check('下月仅恢复一轮且使用新身份',now['generation']==cleared['generation']+1 and not ids.intersection({m['id']for m in now['members']}),now);self.done('monthly_recovery')
                survivor=fresh[0];self.rc(f'data merge entity {survivor["uuid"]} {{Health:7.0f}}');preserved={m['uuid']:m for m in self.living(wolf['id'])};self.rc('save-all flush')
            self.server.stop();self.calendar(0,status['ticks_per_month'])
            with ExitStack()as stack:
                self.begin('save_restart_and_rollback')
                client=self.start_client(stack,'rollback');self.visit(client,wolf)
                restored=self.living(wolf['id']);self.check('重启和回拨不补员或回血',{m['uuid']for m in restored}==set(preserved) and next(m for m in restored if m['uuid']==survivor['uuid'])['health']<=7,restored);self.done('save_restart_and_rollback')
                self.begin('construction_preserved')
                self.visit(client,camp);self.populate(camp);block=[camp['x'],camp['y'],camp['z']+2]
                self.rc(f'item replace entity {client.name} hotbar.0 with minecraft:gold_block');self.fixture(client,[block[0]+.5,block[1],block[2]+2.5],[block[0]+.5,block[1]-.5,block[2]+.5]);client.guard();client.game.xdo('key','1');client.right();self.action('mapped_construction',player=client.name,near=block);time.sleep(1)
                construction=self.round(camp['id'])['construction'];self.check('玩家实际放置进入施工记录',bool(construction),construction);self.rc('save-all flush');before_round=self.round(camp['id'])
            self.server.stop();self.calendar(3,status['ticks_per_month'])
            with ExitStack()as stack:
                client=self.start_client(stack,'construction');self.visit(client,camp);self.populate(camp);round_=self.round(camp['id'])
                self.check('跳月只更新一轮并保留施工',round_['generation']==before_round['generation']+1 and round_['construction']==construction,round_)
                for packed in construction:
                    # 保存的坐标从生产JSON读回，方块原值单独在保存前后读取。
                    x=(packed>>38);x=x-(1<<26)if x>=(1<<25)else x;z=(packed<<26>>38)&((1<<26)-1);z=z-(1<<26)if z>=(1<<25)else z;y=packed&4095;y=y-4096 if y>=2048 else y
                    reply=self.rc(f'execute if block {x} {y} {z} minecraft:gold_block');self.check('月度刷新保留玩家方块','Test passed'in reply or 'test passed'in reply.lower(),reply)
                self.done('construction_preserved')
                self.begin('faction_conflict')
                self.visit(client,wolf);wolves=self.living(wolf['id']);self.visit(client,camp);bandits=self.living(camp['id']);a=wolves[0];b=bandits[0]
                for actor in self.state()['living']:self.rc(f'data merge entity {actor["uuid"]} {{NoAI:1b}}')
                for actor in (a,b):self.rc(f'data merge entity {actor["uuid"]} {{NoAI:0b}}')
                self.rc(f'tp {a["uuid"]} {camp["x"]+2}.5 {camp["y"]} {camp["z"]}.5');self.rc(f'tp {b["uuid"]} {camp["x"]+6}.5 {camp["y"]} {camp["z"]}.5')
                self.fixture(client,[camp['x']+22.5,camp['y']+3,camp['z']+16.5],[camp['x']+14,camp['y']+1,camp['z']]);client.guard();client.game.xdo('keydown','w');time.sleep(.4);client.game.xdo('keyup','w');self.action('mapped_move',key='w',seconds=.4)
                conflict=wait(lambda:self.state()['living'],lambda ms:any(m['uuid']==a['uuid']and m['target']==b['uuid']or m['uuid']==b['uuid']and m['target']==a['uuid']for m in ms),30,'不同群体自然选敌');self.check('群体冲突取得真实目标',True,conflict);client.capture('faction-conflict');self.done('faction_conflict')
                self.begin('low_health_retreat')
                self.rc(f'data merge entity {a["uuid"]} {{NoAI:1b}}');self.rc(f'data merge entity {b["uuid"]} {{Health:2.0f}}');time.sleep(3);retreat=next((m for m in self.living(camp['id'])if m['uuid']==b['uuid']),None);self.check('低血量撤退',retreat is not None and retreat['activity']=='retreat'and not retreat['target'],retreat);self.done('low_health_retreat')
                self.begin('other_regions_excluded')
                export=json.loads((self.server.data/'worldplan.json').read_text());north=next(c for c in export['cities']if c['region_id']=='beiyuan');self.fixture(client,[north['x']+64,north.get('y',150)+4,north['z']+64],[north['x']+64,north.get('y',150),north['z']+64]);time.sleep(2)
                self.check('所有据点仍只归属东荒',self.state()['region']=='donghuang'and len(self.state()['sites'])==12,self.state()['sites']);self.done('other_regions_excluded');self.rc('save-all flush')
            self.report['status']='passed'
        except BaseException as error:
            self.report['status']='failed';self.report['failed'].append(dict(case=self.stage,error=repr(error)));self.report['unrun']=[x for x in CASES if x not in self.report['completed']and x!=self.stage];raise
        finally:
            try:self.server.command('forceload remove all')
            except Exception:pass
            self.server.stop();self.save()

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--world-ref',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);a=p.parse_args()
    Evidence(a.out.resolve(),a.world_ref.resolve(),a.jar.resolve()).run()
if __name__=='__main__':main()
