"""Colab-only private Chapter 1 checks with two real clients.

Entry/exit are typed player commands; dialogue and investigation use real mouse input.
RCON prepares individual interaction positions/angles and reads authoritative state.
This is a set of interaction fixtures, NOT continuous walking or owner acceptance.
"""
from __future__ import annotations

import argparse
from dataclasses import replace
from contextlib import ExitStack
import hashlib
import json
import math
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time
import zipfile

ROOT=Path(os.environ.get('QIANZHEN_PRODUCT_ROOT',Path(__file__).resolve().parents[2])).resolve()
sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.worldplan import acceptance as support
from tools.combat_capture import procs,session as cs
from tools.combat_capture.xgame import Game
from tools.alchemy_quality_evidence import read_data
from tools.qianzhen.story_evidence_helpers import dialogue_buttons,status_object,compile_route
from tools.regiongen.bay_native import SavedWorld,sign_component,archive_world
from tools.regiongen.native_compare import difference
from buildgen.nbtread import read_gzipped_nbt,state_string
from tools.yunshan.game_evidence import aim
from tools.yunshan.game_export import read_typed,write_typed

CASES=['two_private_realms','cross_owner_refusal','independent_chapter','exit_resume',
       'saved_restart','login_owner_guard','death_resume','six_buildings_each','saved_world']
MEDICAL=['accept','commission','inspect_water_sample','inspect_withered_crop','choose_medical',
         'examine_sample','read_record','return_zhou_he','hear_warden','inspect_recut_array',
         'inspect_fresh_cut','inspect_old_array_text','regulate_spring','claim_reward','recognize_contract']


def wait(read,accept,seconds,label):
    until=time.monotonic()+seconds;last=None
    while time.monotonic()<until:
        last=read()
        if accept(last):return last
        time.sleep(.5)
    raise AssertionError(f'{label}: timed out; last={last!r}')


def one(reply,prefix):
    rows=[json.loads(line[len(prefix):]) for line in reply.splitlines() if line.startswith(prefix)]
    if len(rows)!=1 or not isinstance(rows[0],dict):raise ValueError('Missing unique '+prefix+repr(reply))
    return rows[0]


class Client:
    def __init__(self,e,server,name,round_):
        self.e,self.server,self.name=e,server,name
        self.out=e.out/f'{round_}-{name}';self.out.mkdir()
        self.directory=ROOT/'run-combat-capture'/('solo-'+name);self.directory.mkdir(parents=True,exist_ok=True)
        self.processes=[];self.handles=[];self.identities={};self.game=None

    def spawn(self,args,path,**kw):
        log=path.open('w');self.handles.append(log)
        p=subprocess.Popen(args,stdout=log,stderr=subprocess.STDOUT,start_new_session=True,**kw)
        self.processes.append(p);self.identities[p.pid]=procs.start_time(p.pid);return p

    def remember(self):
        for p in self.processes:
            if procs.is_alive(p.pid,self.identities[p.pid]):
                for child in procs.descendants(p.pid):self.identities.setdefault(child,procs.start_time(child))

    def __enter__(self):
        try:
            procs.set_child_subreaper();display=':'+str(procs.free_display())
            self.spawn(['Xvfb',display,'-screen','0','960x540x24','+extension','GLX','-nolisten','tcp','-noreset'],self.out/'xvfb.log')
            wait(lambda:cs.x_display_ready(display),bool,20,'owned X display')
            cs.merge_options(self.directory/'options.txt',{**cs.CLIENT_OPTIONS,'lang':'zh_cn','renderDistance':'3',
                'simulationDistance':'3','maxFps':'30','rawMouseInput':'false','key_key.use':'key.mouse.right'})
            p=self.spawn(cs.gradle_argv('runClient','-Xmx768m',[
                '-Pcombat_smoke_server=127.0.0.1:'+str(self.server.sport),
                '-Pcombat_smoke_game_dir='+str(self.directory),'-Pcombat_smoke_username='+self.name]),
                self.out/'process.log',cwd=ROOT,env=dict(os.environ,DISPLAY=display,JAVA_TOOL_OPTIONS='-Xmx1g'))
            def joined():
                self.remember()
                if p.poll() is not None:raise RuntimeError(f'{self.name} client exited {p.returncode}')
                return self.name in self.server.cmd('list')
            wait(joined,bool,360,'real client joins '+self.name)
            self.game=Game(display,self.directory/'logs/latest.log',(960,540))
            self.window=wait(lambda:self.game.xdo('search','--name','Minecraft').split(),bool,30,'Minecraft window')[0]
            self.game.xdo('windowfocus','--sync',self.window)
            self.game.xdo('mousemove','480','270');time.sleep(2)
            if self.game.pointer_grabbed() is False:
                self.game.xdo('click','2');time.sleep(1)
            wait(self.game.pointer_grabbed,lambda v:v is True,60,'actual in-game pointer')
            self.remember();return self
        except BaseException:
            self.__exit__(*sys.exc_info());raise

    def guard(self,menu=False):
        self.game.xdo('windowfocus','--sync',self.window)
        actual=self.game.xdo('getwindowfocus').strip();pid=int(self.game.xdo('getwindowpid',self.window).strip())
        self.remember();grab=self.game.pointer_grabbed()
        row={'player':self.name,'focus':actual,'window':self.window,'pid':pid,'owned':pid in self.identities,'grabbed':grab,'menu':menu}
        self.e.action('pre_dispatch',**row)
        if actual!=self.window or not row['owned'] or grab is not (not menu):raise AssertionError('Input guard refused '+repr(row))

    def chat(self,text):
        self.close_screen();self.guard();self.game.xdo('key','t');time.sleep(.5);self.guard(menu=True)
        self.game.xdo('type','--delay','45',text);self.game.xdo('key','Return')
        self.e.action('typed_player_command',player=self.name,text=text)
        time.sleep(1);wait(self.game.pointer_grabbed,lambda v:v is True,15,'chat closed')

    def close_screen(self):
        if self.game.pointer_grabbed() is False:
            self.guard(menu=True);self.game.xdo('key','Escape');self.e.action('key',player=self.name,key='Escape')
            time.sleep(.7);wait(self.game.pointer_grabbed,lambda v:v is True,10,'screen closes')

    def right(self):
        self.guard();self.game.xdo('mousedown','3');time.sleep(.1);self.game.xdo('mouseup','3')
        self.e.action('right_mouse',player=self.name)

    def tail(self,offset):
        with self.game.client_log.open('rb') as f:f.seek(offset);return f.read().decode('utf-8','replace')

    def choose(self,option,offset):
        rows=wait(lambda:dialogue_buttons(self.tail(offset),self.game.size,option),bool,20,'physical dialogue '+option)
        if len(rows)!=1:raise AssertionError('Ambiguous option '+repr(rows))
        button=rows[0];self.guard(menu=True)
        self.game.xdo('mousemove','--sync',str(button['x']+button['w']//2),str(button['y']+button['h']//2))
        offset=self.game.client_log.stat().st_size
        self.guard(menu=True);self.game.xdo('click','1')
        self.e.action('dialogue_click',player=self.name,option=option,rectangle=button);time.sleep(.7)
        return offset

    def capture(self,name):
        path=self.e.out/(name+'.png')
        subprocess.run(['import','-silent','-window',self.window,str(path)],env=self.game.env,check=True,timeout=30)
        self.e.report['screenshots'].append(path.name);self.e.save()

    def __exit__(self,*unused):
        self.remember();targets=[(p,s,'solo client owned process') for p,s in self.identities.items()]
        procs.kill_pids(targets,grace=8)
        for handle in self.handles:handle.close()
        log=self.directory/'logs/latest.log'
        if log.exists():shutil.copy2(log,self.out/'client.log')
        if not all(not procs.is_alive(p,s) for p,s,_ in targets):raise RuntimeError('Owned client process survived cleanup')
        self.e.report['cleanup'].append({'client':self.name,'processes':len(targets),'terminated':True});self.e.save()


class Evidence:
    def __init__(self,out,request):
        self.out=out;out.mkdir(parents=True,exist_ok=False);self.request=request
        self.report={'kind':'qianzhen_private_realms','product_commit':request['commit'],
            'collector_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            'selected':CASES,'completed':[],'unrun':CASES.copy(),'checks':[],'status':'running','cleanup':[],
            'screenshots':[],'commands':[],'actions':[],'states':[],
            'method':__doc__,'owner_appearance':'not_verified','owner_fps':'not_verified','continuous_walk':'not_selected'}
        self.scene=json.loads((ROOT/'build/generated/qianzhen/resources/data/myvillage/story/qianzhen_scene.json').read_text())
        self.origin=json.loads((ROOT/'src/main/resources/data/myvillage/story/qianzhen_realm.json').read_text())['origin']
        self.chapter=json.loads((ROOT/'src/main/resources/data/myvillage/story/qianzhen_chapter_1.json').read_text())
        self.actions={a['id']:a for a in self.chapter['actions']};self.expected={row['action']['id']:row for row in compile_route(self.chapter,MEDICAL)};self.save()

    def save(self):support.write(self.out/'report.json',self.report)
    def action(self,kind,**detail):self.report['actions'].append({'time':time.monotonic(),'kind':kind,**detail});self.save()
    def check(self,name,passed,detail):
        self.report['checks'].append({'name':name,'pass':bool(passed),'detail':detail});self.save()
        if not passed:raise AssertionError(name+': '+repr(detail))
    def complete(self,name):self.report['completed'].append(name);self.report['unrun']=[x for x in CASES if x not in self.report['completed']];self.save()
    def rc(self,command):
        if 'qianzhen enter' in command or 'qianzhen leave' in command:raise AssertionError('Entry/exit must be real player input')
        reply=self.server.cmd(command);self.report['commands'].append({'command':command,'reply':reply});self.save();return reply
    def realm(self,name):return one(self.rc('myvillage qianzhen realm_status '+name),'QIANZHEN_REALM_STATUS ')
    def story(self,name):
        state=status_object(self.rc('myvillage story status '+name));self.report['states'].append({'player':name,'state':state});self.save();return state
    def point(self,local):return [self.origin[i]+local[i] for i in range(3)]
    def fixture(self,client,position,target):
        client.close_screen();position=[position[0]+.5,position[1],position[2]+.5];yaw,pitch=aim(position,target)
        dim=self.realm(client.name)['dimension']
        self.rc(f'execute in {dim} run tp {client.name} '+' '.join(f'{x:.6f}' for x in [*position,yaw,pitch]))
        wait(lambda:read_data(self.rc(f'data get entity {client.name} Pos')),lambda p:math.dist(p,position)<.1,10,'interaction fixture position')
        self.action('fixture_interaction_start',player=client.name,dimension=dim,position=position,target=target,yaw=yaw,pitch=pitch)
        time.sleep(.8)
    def step(self,client,action_id):
        before=self.story(client.name);action=self.actions[action_id];role=action.get('role','');target=action.get('target','')
        key=target if action['kind']=='investigate' else role
        station=self.scene['positions'][key];at=self.point(station['approach'])
        if action['kind']=='investigate':
            target_point=[v+.5 for v in self.point(station['pos'])];self.fixture(client,at,target_point);client.right()
        else:
            initial=self.point(station['pos']);self.fixture(client,at,[initial[0]+.5,initial[1]+1.3,initial[2]+.5])
            person=before['npc'][role]['personId'];dim=self.realm(client.name)['dimension']
            query=f'execute in {dim} as @e[type=myvillage:cultivator,nbt={{WorldSimPerson:{person}}}] run data get entity @s Pos'
            bodies=wait(lambda:[read_data(line) for line in self.rc(query).splitlines() if 'data: ' in line],lambda v:len(v)==1,20,'unique private NPC')
            self.fixture(client,at,[bodies[0][0],bodies[0][1]+1.3,bodies[0][2]])
            offset=client.game.client_log.stat().st_size;client.right()
            if action_id=='accept':offset=client.choose('learn',offset)
            client.choose(action_id,offset);client.close_screen()
        after=wait(lambda:self.story(client.name),lambda s:s['revision']==before['revision']+1,15,'physical action '+action_id)
        self.check('mapped_'+client.name+'_'+action_id,after['revision']==before['revision']+1 and after['stage']==self.expected[action_id]['after'],{'before':before['stage'],'after':after['stage'],'expected':self.expected[action_id]['after']})
        return after

    def enter(self,client):
        client.chat('/qianzhen enter')
        return wait(lambda:self.realm(client.name),lambda s:s['phase']=='COMPLETE' and s['current_dimension']==s['dimension'],120,'personal entry '+client.name)

    def audit(self,world,owner):
        saved=SavedWorld(world/'dimensions/myvillage/qianzhen'/owner);errors=[];count=0;signs=0;per_site={};cache={}
        for site in self.scene['sites']:
            path=ROOT/('build/generated/qianzhen/resources/data/myvillage/structure/'+site['template'].split(':')[1]+'.nbt')
            if hashlib.sha256(path.read_bytes()).hexdigest()!=site['sha256']:raise AssertionError('Original template identity changed')
            _,tag=read_gzipped_nbt(str(path));palette=[state_string(p) for p in tag['palette']];failed=0
            for cell in tag['blocks']:
                pos=[self.origin[i]+site['origin'][i]+cell['pos'][i] for i in range(3)];wanted=palette[cell['state']];actual=saved.block(*pos)
                pair=(wanted,actual)
                if pair not in cache:cache[pair]=difference(*pair)
                count+=1
                if cache[pair]:
                    failed+=1
                    if len(errors)<100:errors.append({'site':site['id'],'position':pos,'expected':wanted,'actual':actual,'reason':cache[pair]})
                if cell.get('nbt',{}).get('id')=='minecraft:sign':
                    signs+=1;entities=saved.chunk(pos[0]//16,pos[2]//16).get('block_entities',[])
                    entity=next((e for e in entities if [e['x'],e['y'],e['z']]==pos),None)
                    if entity is None or [sign_component(v) for v in entity.get('front_text',{}).get('messages',[])]!=[sign_component(v) for v in cell['nbt']['front_text']['messages']]:
                        failed+=1;errors.append({'site':site['id'],'position':pos,'reason':'sign_text'})
            per_site[site['id']]={'cells':len(tag['blocks']),'differences':failed}
        road_errors=0
        for cell in self.scene['roads']:
            pos=self.point(cell['pos']);count+=1
            if difference(cell['state'],saved.block(*pos)):
                road_errors+=1
                if len(errors)<100:errors.append({'position':pos,'reason':'road'})
        result={'owner':owner,'cells':count,'signs':signs,'sites':per_site,'road_differences':road_errors,'examples':errors,
                'passed':not road_errors and all(row['differences']==0 for row in per_site.values())}
        support.write(self.out/('saved-geometry-'+owner+'.json'),result);return result

    def run(self):
        support.OUT=self.out;support.SEED=20261025
        name='qianzhen_solo_'+secrets.token_hex(5);world=support.GAME/name;self.report['world']=name
        spawn=self.point(self.scene['positions']['spawn']['pos'])
        marker=' '.join(map(str,[spawn[0]+1,self.origin[1]+self.scene['bounds']['max'][1]+16,spawn[2]+1]))
        self.report['marker_fixture']=marker
        try:
            with support.Server(name,'first',planned=True,view_distance=3) as server:
                self.server=server
                info=server.query('info');self.check('v7_has_no_public_chapter',info['algorithm']=='myvillage:worldplan_v7' and not info.get('qianzhen'),info)
                with ExitStack() as stack:
                    a=stack.enter_context(Client(self,server,'RealmAlice','first'));b=stack.enter_context(Client(self,server,'RealmBob','first'))
                    for client in (a,b):self.rc('gamemode creative '+client.name)
                    self.rc('execute as '+a.name+' at @s run spawnpoint @s ~ ~ ~')
                    spawn_record={field:read_data(self.rc('data get entity '+a.name+' '+field)) for field in ('SpawnDimension','SpawnX','SpawnY','SpawnZ')}
                    ra=self.enter(a);rb=self.enter(b);sa=self.story(a.name);sb=self.story(b.name)
                    ids=lambda s:{row['personId'] for row in s['npc'].values()}
                    durable=lambda s:{k:v for k,v in s.items() if k!='npc'}
                    self.check('distinct_dimensions_and_casts',ra['dimension']!=rb['dimension'] and len(ids(sa))==len(ids(sb))==4
                        and min(ids(sa)|ids(sb))>0 and ids(sa).isdisjoint(ids(sb)),{'a':ra,'b':rb,'cast_a':sa['npc'],'cast_b':sb['npc']})
                    self.complete('two_private_realms');a.capture('alice-private-entry');b.capture('bob-private-entry')
                    self.rc(f'execute in {ra["dimension"]} run tp {b.name} 20.5 64.0 114.5')
                    self.check('cross_owner_teleport_refused',self.realm(b.name)['current_dimension']==rb['dimension'],self.realm(b.name))
                    self.rc(f'execute in myvillage:qianzhen_template run tp {a.name} 0.5 64.0 0.5')
                    self.check('template_teleport_refused',self.realm(a.name)['current_dimension']==ra['dimension'],self.realm(a.name))
                    self.rc('gamemode spectator '+b.name);self.rc('spectate '+a.name+' '+b.name)
                    camera=self.realm(b.name)
                    self.check('foreign_spectator_camera_refused',camera['current_dimension']==rb['dimension'] and camera['camera_self'],camera)
                    self.rc('gamemode creative '+b.name);self.complete('cross_owner_refusal')
                    self.rc(f'execute in {ra["dimension"]} run setblock {marker} minecraft:gold_block')
                    self.check('marker_fixture_written','Test passed' in self.rc(f'execute in {ra["dimension"]} if block {marker} minecraft:gold_block'),marker)
                    self.check('block_state_isolation','Test passed' in self.rc(f'execute in {rb["dimension"]} if block {marker} minecraft:air'),rb['dimension'])
                    for action in MEDICAL:self.step(a,action)
                    a_complete=self.story(a.name);b_fresh=self.story(b.name)
                    self.check('chapter_and_world_facts_are_private',a_complete['completed'] and a_complete['shared']=={'returned':True,'regulated':True}
                        and b_fresh['stage']=='new' and b_fresh['shared']=={'returned':False,'regulated':False} and not b_fresh['receipts'],{'a':a_complete,'b':b_fresh})
                    self.step(b,'accept');b_started=self.story(b.name);self.complete('independent_chapter');a.capture('alice-chapter-complete');b.capture('bob-own-chapter-start')
                    inventory=read_data(self.rc('data get entity '+a.name+' Inventory'))
                    before_exit=self.realm(a.name);a.chat('/qianzhen leave')
                    left=wait(lambda:self.realm(a.name),lambda s:s['current_dimension']==before_exit['outside']['dimension'],20,'return outside')
                    actual=read_data(self.rc('data get entity '+a.name+' Pos'));outside=before_exit['outside']
                    self.check('returns_to_recorded_location',math.dist(actual,[outside['x'],outside['y'],outside['z']])<.15,{'outside':outside,'actual':actual})
                    self.enter(a);self.check('reentry_keeps_progress',durable(self.story(a.name))==durable(a_complete) and ids(self.story(a.name))==ids(a_complete),self.story(a.name));self.complete('exit_resume')
                    self.rc('save-all flush')
            # Deliberately corrupt only the test guest's dimension key, retaining all their task data.
            player_file=world/'playerdata'/(rb['owner']+'.dat');shutil.copy2(player_file,self.out/'bob-before-login-fixture.dat')
            root_name,tag=read_typed(player_file);tag.value['Dimension']=replace(tag.value['Dimension'],value=ra['dimension']);write_typed(player_file,root_name,tag)
            self.report['login_fixture']={'player':rb['owner'],'requested_foreign_dimension':ra['dimension']};self.save()
            with support.Server(name,'restart',planned=True,view_distance=3) as server:
                self.server=server
                with ExitStack() as stack:
                    a=stack.enter_context(Client(self,server,'RealmAlice','restart'));b=stack.enter_context(Client(self,server,'RealmBob','restart'))
                    a_restored=self.story(a.name);b_restored=self.story(b.name)
                    # Epochs advance when bodies re-project; logical identities and all chapter facts must remain exact.
                    self.check('restart_keeps_chapter_and_person_ids',durable(a_restored)==durable(a_complete) and durable(b_restored)==durable(b_started)
                        and ids(a_restored)==ids(a_complete) and ids(b_restored)==ids(b_started),{'a':a_restored,'b':b_restored})
                    self.check('legitimate_login_restores_own_dimension',self.realm(a.name)['current_dimension']==ra['dimension'],self.realm(a.name))
                    self.check('foreign_saved_login_refused',self.realm(b.name)['current_dimension']==rb['outside']['dimension'],self.realm(b.name));self.complete('login_owner_guard')
                    self.enter(b)
                    self.check('saved_block_not_replayed','Test passed' in self.rc(f'execute in {ra["dimension"]} if block {marker} minecraft:gold_block'),ra['dimension'])
                    self.check('other_saved_block_still_independent','Test passed' in self.rc(f'execute in {rb["dimension"]} if block {marker} minecraft:air'),rb['dimension'])
                    self.check('reward_inventory_survives',read_data(self.rc('data get entity '+a.name+' Inventory'))==inventory,inventory);self.complete('saved_restart')
                    self.rc('gamerule keepInventory true');self.rc('gamerule doImmediateRespawn true');self.rc('kill '+a.name)
                    wait(lambda:read_data(self.rc('data get entity '+a.name+' Health')),lambda health:health>0,25,'private death respawn')
                    death=self.story(a.name);self.check('death_keeps_owner_and_progress',self.realm(a.name)['current_dimension']==ra['dimension']
                        and durable(death)==durable(a_complete),death);self.complete('death_resume')
                    self.check('outside_respawn_record_not_overwritten',
                        {field:read_data(self.rc('data get entity '+a.name+' '+field)) for field in spawn_record}==spawn_record,spawn_record)
                    a.capture('alice-after-restart-death');b.capture('bob-after-owner-guard')
                    self.rc(f'execute in {ra["dimension"]} run setblock {marker} minecraft:air')
                    self.check('marker_fixture_removed','Test passed' in self.rc(f'execute in {ra["dimension"]} if block {marker} minecraft:air'),marker)
                    a.chat('/qianzhen leave');b.chat('/qianzhen leave');self.rc('save-all flush')
            cleanups=[json.loads(p.read_text()) for p in self.out.glob('*-cleanup.json')]
            self.check('servers_stopped_normally',len(cleanups)==2 and all(c['terminated'] and not c['forced_stop'] and c['returncode']==0 for c in cleanups),cleanups)
            geometry=[self.audit(world,owner) for owner in (ra['owner'],rb['owner'])]
            self.check('both_saved_realms_match_original_six',all(g['passed'] for g in geometry),geometry);self.complete('six_buildings_each')
            archive=self.out/'saved-world.zip'
            with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
                for p in sorted(world.rglob('*')):
                    if p.is_file() and p.name!='session.lock':z.write(p,p.relative_to(world))
            self.report['saved_world']={'file':archive.name,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()}
            self.complete('saved_world');self.report['status']='passed'
        except BaseException as error:
            self.report.update(status='failed',error=repr(error));raise
        finally:
            if self.report['status']!='passed' and world.exists():
                self.report['failed_saved_world']=archive_world(world,self.out/'failed-world.zip')
            self.save()


def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--request',type=Path,required=True);args=p.parse_args()
    request=json.loads(args.request.read_text())
    if request.get('provider')!='colab' or not str(ROOT).startswith('/content/') or os.environ.get('MYVILLAGE_REMOTE_PROVIDER')!='colab':
        raise RuntimeError('Use the authorized isolated Colab checkout; no local game fallback')
    Evidence(args.out,request).run()


if __name__=='__main__':main()
