"""Colab原生双客户端：榜单接取、分段真实攻击、实例归属、光照与重启。

RCON只准备位置、玩家修为及持久化边界夹具，并读取服务器事实。
接取、入场、暂离、击败、验核与领奖均通过受焦点保护的真实客户端输入。
"""
from __future__ import annotations
import argparse,gzip,hashlib,json,math,os,re,secrets,subprocess,sys,time
from contextlib import ExitStack,contextmanager
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.worldplan import acceptance as support
from tools.qianzhen.realm_evidence import Client,wait,one
from tools.alchemy_quality_evidence import read_data
from tools.yunshan.game_evidence import aim
from tools.combat_capture import procs

CASES=['board_accept','issuing_region_frozen','party_admission','native_lighting','finite_spawns',
       'mapped_combat','pause_resume','normal_restart','exit_reward_once','beast_solo','foreign_refusal','rescue','reclaim']

@contextmanager
def heavy_lock():
    import fcntl
    fd=procs.acquire_lock(procs.default_lock_path(ROOT),timeout=1)
    try:yield
    finally:fcntl.flock(fd,fcntl.LOCK_UN);os.close(fd)

class Evidence:
    def __init__(self,out):
        self.out=out;out.mkdir(parents=True,exist_ok=False)
        self.report={'kind':'combat_quest_native','status':'running','selected':CASES,'completed':[],'failed':[],
                     'unrun':CASES.copy(),'checks':[],'actions':[],'commands':[],'screenshots':[],'cleanup':[],'states':[]}
        self.report['boundary']='交互夹具，含位置和高境界玩家准备；不代表完整连续步行、数值平衡或玩家手感验收。重启检查使用明确标注的非致死生命夹具。'
        self.save()
    def save(self):support.write(self.out/'report.json',self.report)
    def action(self,kind,**detail):self.report['actions'].append(dict(time=time.monotonic(),kind=kind,**detail));self.save()
    def check(self,name,passed,detail):
        self.report['checks'].append(dict(name=name,passed=bool(passed),detail=detail));self.save()
        if not passed:raise AssertionError(name+': '+repr(detail))
    def done(self,name):
        if name not in self.report['completed']:self.report['completed'].append(name)
        self.report['unrun']=[x for x in CASES if x not in self.report['completed']];self.save()
    def rc(self,command):
        if any(x in command for x in ('expedition enter','expedition leave','expedition finish','expedition claim')):raise AssertionError('必须由真实客户端输入：'+command)
        reply=self.server.cmd(command);self.report['commands'].append(dict(command=command,reply=reply));self.save();return reply
    def state(self,client):return one(self.rc('myvillage quest status '+client.name),'QUEST_STATUS ')
    def map(self,client):
        name=self.state(client)['instance']['map']
        return json.loads(gzip.decompress((ROOT/f'build/resources/main/data/myvillage/expedition/{name}.json.gz').read_bytes()))
    def fixture(self,client,position,target):
        client.close_screen();yaw,pitch=aim(position,target);dim=self.state(client)['dimension']
        self.rc(f'execute in {dim} run tp {client.name} '+' '.join(f'{x:.6f}' for x in [*position,yaw,pitch]));time.sleep(.7)
        self.action('fixture_position',player=client.name,dimension=dim,position=position,target=target)
    def click(self,client,x,y,label):
        client.guard(menu=True);client.game.xdo('mousemove','--sync',str(x),str(y));client.game.xdo('click','1');self.action('menu_click',player=client.name,label=label,x=x,y=y);time.sleep(.6)
    def board(self,client,region):
        result=one(self.rc('questfixture board '+client.name+' '+region),'QUEST_FIXTURE ')
        x,y,z=result['reader'];target=[v+.5 for v in result['board']];self.fixture(client,[x+.5,y,z+.5],target);return result
    def open_board(self,client,board):
        target=[v+.5 for v in board['board']];x,y,z=board['reader'];self.fixture(client,[x+.5,y,z+.5],target);client.right()
        wait(client.game.pointer_grabbed,lambda v:v is False,20,'实际江湖榜菜单')
    def accept_map(self,client,board,index):
        self.open_board(client,board);self.click(client,690,424,'next_page');client.capture('board-second-page-'+client.name+'-'+str(index))
        self.click(client,736,168+index*86,'expedition_row_'+str(index))
        wait(client.game.pointer_grabbed,lambda v:v is True,20,'接取后菜单关闭')
        return wait(lambda:self.state(client),lambda s:s.get('instance',{}).get('state')=='FORMING',20,'榜单已签发')
    def journal(self,client,action):
        client.close_screen();offset=client.game.client_log.stat().st_size;client.guard();client.game.xdo('key','o');self.action('mapped_journal_key',player=client.name,key='o')
        pattern=rf'QUEST_JOURNAL action={action} x=(\d+) y=(\d+) w=(\d+) h=(\d+)'
        match=wait(lambda:re.search(pattern,client.tail(offset)),bool,20,'H行迹远征按钮 '+action)
        x,y,w,h=map(int,match.groups());client.capture('H-expedition-'+action);self.click(client,x+w//2,y+h//2,'H_'+action)
    def enter(self,client,menu=False):
        if menu:self.journal(client,'enter')
        else:client.chat('/expedition enter')
        return wait(lambda:self.state(client),lambda s:s['dimension'].startswith('myvillage:expedition/') and s.get('instance',{}).get('state') in ('RUNNING','GOAL'),180,'实际入场 '+client.name)
    def leave(self,client,menu=False):
        if menu:self.journal(client,'leave')
        else:client.chat('/expedition leave')
        return wait(lambda:self.state(client),lambda s:s['dimension']=='minecraft:overworld',30,'实际暂离 '+client.name)
    def approach(self,client,group):self.rc('questfixture approach '+client.name+' '+group)
    def attack(self,client,guard):
        # 位置准备后保持AI运行，使用实际鼠标触发共享武器执行器。
        s=self.state(client);dim=s['dimension'];uid=guard['id'];pos=read_data(self.rc(f'execute in {dim} run data get entity {uid} Pos'))
        player=[pos[0],pos[1],pos[2]-2.2];self.fixture(client,player,[pos[0],pos[1]+.9,pos[2]])
        client.guard();client.game.xdo('click','1');self.action('mapped_sword_attack',player=client.name,target=uid)
        time.sleep(.8)
    def clear_group(self,client,group):
        self.approach(client,group)
        def remaining():
            s=self.state(client);return [v for v in s['instance']['slots'] if v['group']==group and not v['dead']]
        wait(lambda:self.state(client),lambda s:any(v['group']==group and v['spawned'] for v in s['instance']['slots']),30,'有限敌群出现 '+group)
        deadline=time.monotonic()+120
        while remaining() and time.monotonic()<deadline:
            s=self.state(client);ids={v['id'] for v in remaining()};guards=[g for g in s['guards'] if g['id'] in ids]
            if not guards:time.sleep(.5);continue
            self.attack(client,guards[0])
        self.check('mapped_clear_'+group,not remaining(),self.state(client))
    def finish(self,client,menu=False):
        model=self.map(client);x,y,z=[model['origin'][i]+model['exit'][i] for i in range(3)]
        self.fixture(client,[x+.5,y,z-2.5],[x+.5,y+.5,z+.5])
        if menu:self.journal(client,'finish')
        else:client.chat('/expedition finish')
        return wait(lambda:self.state(client),lambda s:s['member']=='SUCCESS' and s['dimension']=='minecraft:overworld',20,'真实出口验核')
    def claim(self,client,region):
        board=self.board(client,region);self.open_board(client,board);self.click(client,690,424,'next_page');self.click(client,736,168,'claim_expedition')
        client.close_screen();return wait(lambda:self.state(client),lambda s:s.get('receipt',{}).get('claimed') is True,15,'真实领奖')
    def prepare(self,client):
        self.rc('gamemode survival '+client.name)
        self.rc('myvillage cultivation setrealm '+client.name+' myvillage:golden_core myvillage:golden_late')
        self.rc('effect give '+client.name+' minecraft:regeneration 99999 5 true')
        self.rc('item replace entity '+client.name+' hotbar.0 with myvillage:qingfeng_sword');client.guard();client.game.xdo('key','1')
    def snapshot(self,label,**detail):self.report['states'].append(dict(label=label,**detail));self.save()
    def run(self):
        support.OUT=self.out;support.SEED=20261114
        world='quest_runtime_'+secrets.token_hex(4);self.report['world']=world;self.save()
        before=None;run_id=None
        try:
            with heavy_lock():
                with support.Server(world,'first',planned=True,view_distance=3) as self.server:
                    self.rc('difficulty normal');self.rc('gamerule doFireTick true');self.rc('gamerule mobGriefing true')
                    with ExitStack() as stack:
                        a=stack.enter_context(Client(self,self.server,'QuestAlice','first'));b=stack.enter_context(Client(self,self.server,'QuestBob','first'))
                        board=self.board(a,'ximo');self.board(b,'ximo');issued=self.accept_map(a,board,0);run_id=issued['instance']['id'];self.done('board_accept')
                        self.prepare(a);self.prepare(b);a.chat('/expedition invite '+b.name);b.chat('/expedition accept')
                        self.check('two_confirmed_members',len(self.state(a)['instance']['party'])==2,self.state(a)['instance']['party'])
                        self.board(a,'donghai');self.board(b,'donghai');entered=self.enter(a,menu=True);self.enter(b)
                        self.check('issuing_region_does_not_follow_player',entered['instance']['region']=='ximo' and issued['instance']['slots']==entered['instance']['slots'],{'issued':issued,'entered':entered});self.done('issuing_region_frozen');self.done('party_admission')
                        self.check('native_light_and_local_rules',entered['instance']['lightMinimum']>=8 and all(v is False for v in entered['local_rules'].values()),entered);self.done('native_lighting')
                        self.check('overworld_fire_rule_preserved','true' in self.rc('execute in minecraft:overworld run gamerule doFireTick'),self.rc('execute in minecraft:overworld run gamerule doFireTick'))
                        a.capture('bandit-entry');self.approach(a,'gate_clear');self.approach(b,'gate_clear')
                        first=wait(lambda:self.state(a),lambda s:len(s.get('guards',[]))==2,30,'两名门后守敌')
                        self.check('spawned_realms_match_frozen_assignments',all(g['realm']==next(s['realm']+':'+str(s['stage']) for s in first['instance']['slots'] if s['id']==g['id']) for g in first['guards']),first)
                        self.rc('execute in '+first['dimension']+' run summon minecraft:zombie 96 78 64')
                        self.check('extra_mob_refused','No entity was found' in self.rc('execute in '+first['dimension']+' run data get entity @e[type=minecraft:zombie,limit=1]'),self.state(a));self.done('finite_spawns')
                        self.clear_group(a,'gate_clear');a.capture('bandit-cleared-gate')
                        self.approach(a,'cart_clear');second=wait(lambda:self.state(a),lambda s:len(s.get('guards',[]))==3,30,'粮车场守敌')
                        uid=second['guards'][0]['id'];self.rc('execute in '+second['dimension']+' run data merge entity '+uid+' {Health:7.0f}')
                        self.action('nonlethal_health_fixture',guard=uid,health=7)
                        self.leave(a,menu=True);self.leave(b);before=self.state(a);self.snapshot('before_restart',state=before,damaged_guard=uid)
                        self.rc('save-all flush');self.done('pause_resume')
                with support.Server(world,'restart',planned=True,view_distance=3) as self.server:
                    self.rc('difficulty normal')
                    with ExitStack() as stack:
                        a=stack.enter_context(Client(self,self.server,'QuestAlice','restart'));b=stack.enter_context(Client(self,self.server,'QuestBob','restart'))
                        restored=self.state(a);self.check('same_instance_and_frozen_slots_after_restart',restored['instance']['id']==run_id and restored['instance']['slots']==before['instance']['slots'],{'before':before,'after':restored})
                        self.enter(a);self.enter(b);active=wait(lambda:self.state(a),lambda s:len(s.get('guards',[]))==3,25,'恢复三名守敌')
                        guard=next(g for g in active['guards'] if g['id']==uid);old=next(g for g in before['guards'] if g['id']==uid)
                        self.check('guard_health_and_maximum_not_reset',guard['health']==old['health']==7 and guard['max_health']==old['max_health'],{'before':old,'after':guard});self.done('normal_restart')
                        for group in ('cart_clear','court_clear','chief_clear'):self.clear_group(a,group)
                        self.done('mapped_combat');a.capture('bandit-exit')
                        self.finish(a,menu=True);self.finish(b);claimed=self.claim(a,'donghai');self.claim(b,'donghai')
                        inventory=self.rc('data get entity '+a.name+' Inventory');a.chat('/expedition claim');self.check('second_claim_does_not_pay_again',inventory==self.rc('data get entity '+a.name+' Inventory'),self.state(a));self.done('exit_reward_once')
                        wait(lambda:self.state(a),lambda s:'instance' not in s,30,'山寨实例回收');self.done('reclaim')
                        board=self.board(a,'donghai');self.accept_map(a,board,1);beast=self.enter(a)
                        self.check('beast_uses_issuing_beast_range',beast['instance']['region']=='donghai' and all(s['realm']=='myvillage:qi_refining' and 2<=s['stage']<=8 for s in beast['instance']['slots']),beast)
                        self.rc('execute in '+beast['dimension']+' run tp '+b.name+' 104.5 82 24.5')
                        self.check('foreign_player_remains_outside',self.state(b)['dimension']=='minecraft:overworld',self.state(b));self.done('foreign_refusal')
                        self.rc('kill '+a.name);rescued=wait(lambda:self.state(a),lambda s:s.get('receipt',{}).get('rescues')==1,20,'致命伤害救援')
                        self.check('rescue_stays_in_owned_instance',rescued['dimension']==beast['dimension'] and rescued['member']=='ACTIVE',rescued);self.done('rescue')
                        a.capture('beast-entry');
                        for group in ('wolves_clear','guardian_clear','boss_clear'):self.clear_group(a,group)
                        a.capture('beast-cleared-boss');self.finish(a);self.claim(a,'donghai');self.done('beast_solo')
                        self.rc('save-all flush')
            self.report['status']='passed'
        except BaseException as error:
            self.report['status']='failed';self.report['error']=repr(error);self.report['failed'].append({'error':repr(error)});raise
        finally:self.save()

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    if not str(ROOT).startswith('/content/') or os.environ.get('MYVILLAGE_REMOTE_PROVIDER')!='colab':raise RuntimeError('原生游戏检查只允许在授权Colab工作区运行')
    Evidence(a.out).run()

if __name__=='__main__':main()
