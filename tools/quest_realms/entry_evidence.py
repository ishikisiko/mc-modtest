"""双客户端入口占位回归。复用本工具的已完成测试世界，不修改其历史记录。"""
from __future__ import annotations
import argparse,json,os,re
from contextlib import ExitStack
from pathlib import Path
from tools.quest_realms.native_evidence import Evidence,heavy_lock,ROOT
from tools.worldplan import acceptance as support
from tools.qianzhen.realm_evidence import Client,wait
from tools.alchemy_quality_evidence import read_data

CASES=['separate_party_entry','occupied_resume_position','separate_rescue_position','abandon_reclaim']

class EntryEvidence(Evidence):
    def __init__(self,out,previous):
        old=json.loads(previous.read_text());world=old.get('world','')
        if old.get('kind')!='combat_quest_native' or old.get('status')!='passed' or old.get('failed') or old.get('unrun') or not re.fullmatch(r'quest_runtime_[a-f0-9]{8}',world):
            raise RuntimeError('只复用已完整通过的自建原生测试世界')
        super().__init__(out)
        self.report.update(kind='combat_quest_entry_native',selected=CASES,completed=[],unrun=CASES.copy(),world=world,
                           previous={'report':str(previous),'product_commit':old['product_commit'],'jar':old['jar']})
        self.save()
    def done(self,name):
        self.report['completed'].append(name);self.report['unrun']=[x for x in CASES if x not in self.report['completed']];self.save()
    def separate(self,a,b,label):
        positions={c.name:read_data(self.rc('data get entity '+c.name+' Pos')) for c in (a,b)}
        x,y=positions[a.name],positions[b.name]
        clear=abs(x[0]-y[0])>=.6 or abs(x[2]-y[2])>=.6 or abs(x[1]-y[1])>=1.8
        self.check(label,clear,positions);self.snapshot(label,positions=positions)
    def run(self):
        support.OUT=self.out;support.SEED=20261114
        try:
            with heavy_lock(),support.Server(self.report['world'],'entry',planned=True,view_distance=3,prebuilt_resources=True,startup_timeout=1200) as self.server:
                self.rc('difficulty normal')
                with ExitStack() as stack:
                    a=stack.enter_context(Client(self,self.server,'QuestAlice','entry',prebuilt_resources=True,heap='4g'))
                    b=stack.enter_context(Client(self,self.server,'QuestBob','entry',prebuilt_resources=True,heap='4g'))
                    for c in (a,b):
                        saved=wait(lambda:self.state(c),lambda s:'instance' not in s,30,'前轮已回收')
                        self.check('previous_reward_claimed_'+c.name,saved.get('receipt',{}).get('claimed') is True,saved)
                    board=self.board(a,'ximo');self.board(b,'ximo',park=True);self.accept_map(a,board,0)
                    self.prepare(a);self.prepare(b);a.chat('/expedition invite '+b.name);b.chat('/expedition accept')
                    self.check('confirmed_two_members',len(self.state(a)['instance']['party'])==2,self.state(a))
                    self.enter(a,menu=True);self.enter(b);self.separate(a,b,'party_entry_body_boxes_separate');self.done('separate_party_entry')
                    self.rc('questfixture park '+b.name);a.capture('bandit-final-entry')
                    self.leave(a,menu=True);saved=self.state(a)['receipt']['inside']
                    self.fixture(b,[saved[k] for k in ('x','y','z')],[saved['x'],saved['y']+1,saved['z']+8])
                    self.snapshot('occupied_saved_entry',place=saved,occupant=b.name)
                    self.enter(a,menu=True);self.separate(a,b,'occupied_saved_position_avoided');self.done('occupied_resume_position')
                    self.rc('questfixture camp '+b.name);self.rc('kill '+a.name)
                    rescued=wait(lambda:self.state(a),lambda s:s.get('receipt',{}).get('rescues')==1,20,'救援保持本轮')
                    self.check('rescue_keeps_active_member',rescued['member']=='ACTIVE' and rescued['dimension'].startswith('myvillage:expedition/'),rescued)
                    self.separate(a,b,'rescue_body_boxes_separate');self.done('separate_rescue_position');a.capture('bandit-final-rescue')
                    for c in (a,b):
                        c.chat('/expedition abandon')
                        wait(lambda:self.state(c),lambda s:s['dimension']=='minecraft:overworld' and s['member']=='ABANDONED',30,'真实放弃并返回')
                    wait(lambda:self.state(a),lambda s:'instance' not in s,30,'入口回归实例回收');self.done('abandon_reclaim');self.rc('save-all flush')
            self.report['status']='passed'
        except BaseException as error:
            self.report['status']='failed';self.report['error']=repr(error);self.report['failed'].append({'error':repr(error)});raise
        finally:self.save()

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--previous',type=Path,required=True);a=p.parse_args()
    if not str(ROOT).startswith('/content/') or os.environ.get('MYVILLAGE_REMOTE_PROVIDER')!='colab':raise RuntimeError('仅允许在授权Colab工作区运行')
    EntryEvidence(a.out,a.previous).run()

if __name__=='__main__':main()
