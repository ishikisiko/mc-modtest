"""新修士外观入口：真实召唤、映射刷怪蛋、命簿投影／撤回／重进。仅限 Modal。"""
from __future__ import annotations
import json
from pathlib import Path
import sys
import time

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.alchemy_quality_evidence import read_data
from tools.combat_capture import procs,scene,session
from tools.combat_capture.beast import camera_pose
from tools.combat_capture.rcon import connect
from tools.npc_life_evidence import read_ledger
from tools.npcgen import catalog,male_catalog
from tools.realm_stats_evidence import Evidence
from tools.yunshan.game_evidence import aim,teleport_command

CASES=('command_spawn','mapped_spawn_egg','explicit_look_compatibility','ledger_projection','ledger_reprojection','save_reload')
OUT=ROOT/'out/preview/npc_appearance_native'
GENERATED={v['look'] for v in catalog.runtime_document()['variants']}


def automatic_look_ok(body):
    return bool(body.get('UUID')) and body.get('Look') in GENERATED


def appearance(body):
    return {'uuid':body.get('UUID'),'look':body.get('Look','default'),'colours':body.get('Colours',-1)}


def expected_ledger_look(person):
    select=catalog.for_identity if person['gender']=='f' else male_catalog.for_identity
    return select(person['id'],person['birth'])


def egg_item(tag):
    # 只设置采集标签与静止姿态；Look／Colours 必须来自实际出生链路。
    return ('myvillage:cultivator_spawn_egg[minecraft:entity_data={id:"myvillage:cultivator",'
            f'NoAI:1b,PersistenceRequired:1b,Tags:["{tag}"]}}]')


class AppearanceEvidence(Evidence):
    def __init__(self):
        super().__init__(OUT)
        self.report.update(kind='npc_appearance_entrypoints',selected=list(CASES),completed=[],unrun=list(CASES),
                           method='实际生成入口；刷怪蛋使用真实鼠标右键；命令只作 fixture／读回／相机',
                           mapped_inputs=[],stills=[],native_visual_acceptance='not_verified')
        self.samples={}
        self.hidden=False

    def check(self,name,passed,detail):
        super().check(name,passed,detail)
        if not passed:raise AssertionError(name)

    def complete(self,name):
        self.report['completed'].append(name);self.report['unrun'].remove(name);self.save()

    def poll(self,getter,predicate,timeout,label):
        end=time.monotonic()+timeout;last=None
        while time.monotonic()<end:
            last=getter()
            if predicate(last):return last
            time.sleep(.4)
        raise TimeoutError(f'{label}: {last}')

    def start(self,reuse=False):
        if ROOT!=Path('/workspace/repo') or not Path('/workspace/request.json').is_file():
            raise RuntimeError('NPC 外观原生验证只能在 Modal 固定源执行')
        self.report['source_commit']=json.loads(Path('/workspace/request.json').read_text())['commit']
        self.save()
        if not reuse:
            config=session.SERVER_DIR/'config/myvillage-world_sim-server.toml'
            config.parent.mkdir(parents=True,exist_ok=True)
            config.write_text('[world_sim]\ntier = "large"\n[avatars]\nauto_realize_gates = false\n')
        session.merge_options(session.CLIENT_DIR/'options.txt',{'key_key.use':'key.mouse.right'})
        state=session.start(session.SessionConfig(session_id='npc-appearance-'+('reload' if reuse else 'initial'),
                            language='zh_cn',reuse_world=reuse,prebuilt_resources=True,settle=5,lock_timeout=60,
                            lock=str(procs.default_lock_path(ROOT))))
        self.s=session.Session.attach(ui_check=True);self.sessions+=1;self.hidden=False
        self.report['phases'].append({'phase':'restart' if reuse else 'initial','timings':state.get('timings',{})})
        for command in (f'op {self.s.username}',f'gamemode creative {self.s.username}',
                        'myvillage world pause',*scene.world_rules(),'tick unfreeze',
                        'forceload add -48 -48 48 48',teleport_command(self.s.username,(.5,-60,.5))):self.rc(command)
        self.save()

    def inspect(self,selector):
        raw=self.rc(f'data get entity {selector}')
        return read_data(raw) if 'data: ' in raw else {}

    def tagged(self,tag):return self.inspect(f'@e[type=myvillage:cultivator,tag={tag},limit=1]')

    def entity(self,pid):return '@e[type=myvillage:cultivator,nbt={WorldSimPerson:'+str(pid)+'},limit=1]'

    def ledger(self,label):
        self.rc('save-all flush')
        data=read_ledger(session.SERVER_DIR/session.LEVEL_NAME/'data/myvillage_world_sim.dat')
        (self.out/(label+'-ledger.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
        return data

    def photo(self,selector,label,view='q_front'):
        self.rc('tick freeze')
        try:
            body=self.inspect(selector);self.check(label+'_exists',bool(body.get('UUID')),appearance(body))
            self.rc(f'gamemode spectator {self.s.username}')
            pose=camera_pose(body['Pos'],body['Rotation'][0],view,2.6,1.3,1.0)
            self.rc(teleport_command(self.s.username,pose[:3],pose[3],pose[4]))
            self.s.game.require_ingame(label)
            if not self.hidden:self.s.game.key('F1');self.hidden=True
            time.sleep(.5)
            shot=self.s.game.shot(self.out/(label+'.png'))
            self.report['stills'].append({'file':label+'.png','appearance':appearance(body),'shot':shot})
        finally:
            if self.hidden:self.s.game.key('F1');self.hidden=False
            self.rc('tick unfreeze');self.rc(f'gamemode creative {self.s.username}')
        self.save()

    def command_spawn(self):
        for i in range(12):
            tag=f'appearance_command_{i}';x=(i%6)*4-10;z=6+(i//6)*4
            self.rc(f'summon myvillage:cultivator {x+.5} -60 {z+.5} {{NoAI:1b,PersistenceRequired:1b,Tags:["{tag}"]}}')
            body=self.poll(lambda:self.tagged(tag),lambda b:bool(b.get('UUID')),5,tag)
            self.check(tag,automatic_look_ok(body),appearance(body));self.samples[tag]=appearance(body)
        self.photo('@e[tag=appearance_command_0,limit=1]','command_spawn')

    def mapped_spawn_egg(self):
        self.rc(teleport_command(self.s.username,(.5,-60,.5)))
        for i in range(6):
            tag=f'appearance_egg_{i}';x=i*4-10
            self.check(tag+'_absent',not self.tagged(tag),{'tag':tag})
            position=(x+.5,-60,-11.5);target=(x+.5,-60,-8.5)
            yaw,pitch=aim(position,target)
            self.rc(teleport_command(self.s.username,position,yaw,pitch))
            self.rc(f'item replace entity {self.s.username} hotbar.0 with {egg_item(tag)} 1')
            self.s.game.require_ingame('mapped spawn egg');self.s.game.key('1');time.sleep(.3)
            self.s.game.xdo('click','3')
            self.report['mapped_inputs'].append({'action':'spawn_egg','method':'xdotool right click','tag':tag,'position':position})
            self.save()
            body=self.poll(lambda:self.tagged(tag),lambda b:bool(b.get('UUID')),8,tag)
            self.check(tag,automatic_look_ok(body),appearance(body));self.samples[tag]=appearance(body)
        self.photo('@e[tag=appearance_egg_0,limit=1]','spawn_egg')

    def explicit_look_compatibility(self):
        for i,look in enumerate(('default','f_novice','f_adept','m_02_04_b','f_novice_02_a_pants')):
            tag=f'appearance_explicit_{i}'
            self.rc(f'summon myvillage:cultivator {i*4-8+.5} -60 16.5 {{NoAI:1b,PersistenceRequired:1b,Tags:["{tag}"],Look:"{look}",Colours:66}}')
            body=self.tagged(tag)
            self.check(tag,bool(body.get('UUID')) and body.get('Look')==look and body.get('Colours')==66,appearance(body))
            self.samples[tag]=appearance(body)

    def projected(self):
        people=[p for p in self.members if not hasattr(self,'ledger_before') or p['id'] in self.ledger_before]
        return {p['id']:b for p in people if (b:=self.inspect(self.entity(p['id']))).get('UUID')}

    def has_required_people(self,bodies):
        return any(p['id'] in bodies and p['gender']=='f' and p['realm'] not in ('mortal','qi_refining') for p in self.members) and any(p['id'] in bodies and p['gender']=='m' for p in self.members)

    def check_projected(self,bodies,label):
        people={p['id']:p for p in self.members}
        for pid,body in bodies.items():
            self.check(label+'_'+str(pid),automatic_look_ok(body) and body['Look']==expected_ledger_look(people[pid])
                       and body.get('WorldSimPerson')==pid and body.get('Colours',-1)>=0,
                       {'person':pid,'gender':people[pid]['gender'],'realm':people[pid]['realm'],'expected':expected_ledger_look(people[pid]),**appearance(body)})

    def ledger_projection(self):
        state=self.ledger('initial')
        candidates=[]
        for sect in state['sects']:
            if sect['state']!='active':continue
            people=[p for p in state['persons'] if p['sect']==sect['id'] and p['status']=='at_sect']
            women=[p for p in people if p['gender']=='f' and p['realm'] not in ('mortal','qi_refining') and p['rank'] in ('elder','sect_master')]
            if women and any(p['gender']=='m' for p in people):
                score=100*sum(p['rank']=='sect_master' for p in women)+len(women)
                candidates.append((score,sect,people))
        self.check('real_ledger_has_high_realm_women',bool(candidates),{'tier':state['tier'],'eligible_sects':len(candidates)})
        _,sect,self.members=max(candidates,key=lambda x:x[0]);self.sect=sect
        x,z=sect['gate_x'],sect['gate_z'];self.gate_position=(x+.5,-59,z+5.5)
        self.report['fixture']={'sect_id':sect['id'],'population_tier':state['tier'],'gate':[x,z],'ledger_modified_by_nbt':False}
        self.rc(teleport_command(self.s.username,(x+.5,-60,z-20.5)))
        self.rc(f'forceload add {x-64} {z-64} {x+64} {z+64}')
        command=f'myvillage world sect {sect["id"]} build'
        with connect(self.s.state['rcon_port'],self.s.state['rcon_password'],retries=1,timeout=180) as r:
            reply=r.cmd(command)
        self.report['commands'].append({'command':command,'reply':reply,'purpose':'admin fixture; original ledger identities'})
        self.rc(teleport_command(self.s.username,self.gate_position))
        bodies=self.poll(self.projected,self.has_required_people,60,'real ledger NPCs appear')
        self.check_projected(bodies,'ledger_initial')
        self.ledger_before={pid:appearance(body) for pid,body in bodies.items()}
        female=next(p['id'] for p in self.members if p['id'] in bodies and p['gender']=='f' and p['realm'] not in ('mortal','qi_refining'))
        male=next(p['id'] for p in self.members if p['id'] in bodies and p['gender']=='m')
        for label,pid in [('high_realm_female',female),('ledger_male',male)]:
            for view in ('front','q_front'):self.photo(self.entity(pid),label+'_'+view,view)
        self.rc(teleport_command(self.s.username,self.gate_position))
        self.report['ledger_before']=self.ledger_before;self.save()

    def ledger_reprojection(self):
        self.rc(teleport_command(self.s.username,(.5,-60,.5)))
        self.poll(self.projected,lambda bodies:not bodies,40,'loaded gate withdraws actual NPCs')
        self.check('ledger_withdrawn',True,{'gate_chunks_forced_loaded':True})
        self.rc(teleport_command(self.s.username,self.gate_position))
        bodies=self.poll(self.projected,lambda b:all(pid in b for pid in self.ledger_before),60,'same ledger people reappear')
        self.check_projected(bodies,'ledger_reprojected')
        for pid,before in self.ledger_before.items():
            after=appearance(bodies[pid])
            self.check('ledger_reprojection_identity_'+str(pid),before['uuid']!=after['uuid'] and before['look']==after['look'] and before['colours']==after['colours'],{'before':before,'after':after})
        self.ledger_before={pid:appearance(bodies[pid]) for pid in self.ledger_before}

    def save_reload(self):
        self.rc('save-all flush');self.stop();self.start(reuse=True)
        for tag,before in self.samples.items():
            body=self.poll(lambda:self.tagged(tag),lambda b:bool(b.get('UUID')),15,tag+' reload')
            self.check(tag+'_reload',before==appearance(body),{'before':before,'after':appearance(body)})
        self.rc(teleport_command(self.s.username,self.gate_position))
        bodies=self.poll(self.projected,lambda b:all(pid in b for pid in self.ledger_before),60,'ledger after restart')
        self.check_projected(bodies,'ledger_restart')
        for pid,before in self.ledger_before.items():
            after=appearance(bodies[pid])
            self.check('ledger_restart_identity_'+str(pid),before['uuid']!=after['uuid'] and before['look']==after['look'] and before['colours']==after['colours'],{'before':before,'after':after})
        saved={p['id']:p for p in self.ledger('final')['persons']}
        self.check('ledger_identity_not_recreated',all(saved[p['id']]==p for p in self.members),{'ids':[p['id'] for p in self.members]})

    def run(self):
        self.start()
        for case in CASES:
            getattr(self,case)();self.complete(case)
        self.report['status']='pass'


def main():
    e=AppearanceEvidence()
    try:e.run()
    except Exception as error:
        e.report['status']='fail';e.report['failures'].append(repr(error));raise
    finally:
        if e.s:e.stop()
        e.save()


if __name__=='__main__':main()
