"""四组传承的原生客户端验收。RCON 只准备夹具、读回和步进；玩家动作经映射键鼠。"""
from __future__ import annotations
import argparse,hashlib,json,math,os,re,secrets,sys,time,traceback
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from tools.elemental_arts_evidence import ElementalEvidence,KEYS
from tools.locked_sword_evidence import LockedEvidence
from tools.alchemy_quality_evidence import SecondClient,read_data
from tools.combat_capture import session as cs,scene,procs
from tools.gen_legacy_encounters import catalogue

CASES=('learning','four_arts','artifacts','normal_ai','acquisition','refusals','two_players','restart')

class Guest(SecondClient):
    username='LegacyObserver'
    def __init__(self,e):
        super().__init__(e,timeout=420);self.directory=ROOT/'run-combat-capture/legacy-guest';self.directory.mkdir(parents=True,exist_ok=True)
    def spawn(self,argv,path,**kw):
        if 'runClient' in argv:
            argv=[*argv,'-x','processResources']
            kw['env']=dict(kw.get('env',os.environ),JAVA_TOOL_OPTIONS='-Xmx2g')
            cs.merge_options(self.directory/'options.txt',{'simulationDistance':'5'})
        return super().spawn(argv,path,**kw)
    def __enter__(self):
        result=super().__enter__()
        self.e.report['phases'][-1]['heap_cap']='2 GiB'
        return result

class LegacyEvidence(ElementalEvidence):
    def __init__(self,out,cases,attach=False):
        super().__init__(out,cases,attach);self.source=catalogue();self.arts={a['id']:a for a in self.source['arts']};self.ids=list(self.arts)
        self.report.update(kind='four_legacy_encounters',method=__doc__,fixture='独立平坦世界；管理员准备境界、材料、目标和位置，动作由真实键鼠触发。',natural_acquisition='新世界遗迹生成另行记录；此采集覆盖物品使用和试炼。')
        self.report['collector_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    def log(self,offset=0):return LockedEvidence.log(self,offset)
    def start(self,reuse=False):
        if os.environ.get('MYVILLAGE_CLOUD_PROVIDER')!='Colab' or not str(ROOT).startswith('/content/'):
            raise RuntimeError('Only run in Colab')
        state=cs.read_state() if self.attach else cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),language='zh_cn',pvp=True,
                reuse_world=reuse,prebuilt_resources=True,view_distance=3,settle=3,server_timeout=600,client_timeout=600,lock=str(procs.default_lock_path(ROOT))))
        if not state or state.get('phase')!='ready':raise RuntimeError('No ready native client')
        self.s=cs.Session.attach(ui_check=True);self.sessions+=1;self.rc('tick unfreeze');self.position(self.s.game)
        self.report['phases'].append({'phase':'restart' if reuse else 'start','timings':state.get('timings',{})})
        if reuse:self.freeze();return
        for cmd in (*scene.world_rules(),*scene.player_setup(self.s.username,'minecraft:air'),'gamerule naturalRegeneration false','myvillage world pause','forceload add -32 -32 64 64'):
            self.rc(cmd)
        self.poll(lambda:self.rc('execute if loaded 0 -61 0 run time query gametime'),lambda r:'The time is' in r,label='fixture chunks')
        self.rc('fill -16 -61 -16 48 -61 48 minecraft:stone');self.configure(self.s.username)
        self.rc(f'item replace entity {self.s.username} hotbar.0 with minecraft:air');scene.ensure_cultivation(self.s);self.freeze()
    def configure(self,user):
        maximum=json.loads((ROOT/'src/main/resources/data/myvillage/stats/rules.json').read_text())['limits']['max_base_health']
        for command in [f'attribute {user} minecraft:generic.max_health base set {maximum}',f'op {user}',f'gamemode survival {user}',
                        f'myvillage cultivation awaken {user}',f'myvillage cultivation initiate {user}',
                        f'myvillage cultivation setrealm {user} myvillage:foundation_establishment myvillage:foundation_early',
                        f'myvillage cultivation setroot {user} 4000 1500 1500 1500 1500',f'myvillage cultivation qi fill {user}',
                        f'effect give {user} minecraft:instant_health 1 20 true',f'effect give {user} minecraft:saturation infinite 255 true',
                        f'item replace entity {user} hotbar.0 with minecraft:air']:
            self.rc(command)
        info=self.one(f'myvillage stats info {user}','STATS_INFO')
        self.check('fixture_player_has_valid_realm_stats',info['realm']=='myvillage:foundation_establishment' and info['factor']>1,info)
    def status(self,user=None):
        text=self.rc(f'myvillage legacy status {user or self.s.username}')
        if 'LEGACY_STATUS ' not in text:raise RuntimeError(text)
        return json.loads(text.split('LEGACY_STATUS ',1)[1])
    def pos(self,user=None):return read_data(self.rc(f'data get entity {user or self.s.username} Pos'))
    def equip(self,id):
        self.rc(f'item replace entity {self.s.username} hotbar.0 with {id}');self.key('1');self.step(3)
    def use(self,game=None):
        game=game or self.s.game;game.require_ingame('使用器物');game.xdo('click','3');self.report['inputs'].append({'method':'mapped right mouse','actor':'guest' if self.guest and game==self.guest.game else 'caster','tick':self.now()});time.sleep(.25);self.save()
    def clean(self):
        self.rc('kill @e[tag=legacy_target]');self.rc('kill @e[tag=locked_fixture]');self.rc('kill @e[tag=legacy_beast]')
        self.rc('kill @e[type=minecraft:item]');self.equip('minecraft:air');self.step(5)
    def fixture_bind(self):
        for id in self.ids:self.rc(f'myvillage cultivation learn {self.s.username} myvillage:{id}')
        self.learned=self.ids[:];self.bind(self.ids)
        self.report['combat_fixture_learning']='管理员已学夹具；独立 learning 场景验证零进度研读。'
    def cast(self,id,finish=True,expected_cost=None):
        self.step(12)  # let the client's bounded pending window expire after a rejected input
        if expected_cost==0:self.rc(f'myvillage cultivation qi fill {self.s.username}')
        mark=self.offset();before=self.power();self.key(self.assigned[id]);
        self.poll(lambda:self.log(mark),lambda s:f'result=start art=myvillage:{id}' in s,label='mapped cast '+id)
        cost=self.arts[id]['qi_cost'] if expected_cost is None else expected_cost
        self.check(id+'_payment',before-self.power()==cost,{'before':before,'after':self.power(),'expected':cost})
        if finish:self.step(14)
        return mark
    def dummy_at(self,x,z,tag='legacy_target',health=1000):
        self.rc('kill @e[tag='+tag+']')
        self.rc('kill @e[type=minecraft:item]')
        self.rc(f'summon minecraft:husk {x} -60 {z} {{NoAI:1b,PersistenceRequired:1b,Silent:1b,Health:{health}f,Tags:["{tag}"],attributes:[{{id:"minecraft:generic.max_health",base:{health}d}},{{id:"minecraft:generic.knockback_resistance",base:1d}}]}}')
        self.step(2);return '@e[tag='+tag+',limit=1]'
    def aim_at(self,point):
        options=dict(line.split(':',1) for line in (cs.CLIENT_DIR/'options.txt').read_text().splitlines() if ':' in line)
        gain=(float(options.get('mouseSensitivity','.5'))*.6+.2)**3*8*.15
        for _ in range(4):
            pos=self.pos();rotation=read_data(self.rc(f'data get entity {self.s.username} Rotation'))
            dx,dz=point[0]-pos[0],point[2]-pos[2]
            yaw=math.degrees(math.atan2(-dx,dz));pitch=-math.degrees(math.atan2(point[1]-pos[1]-1.62,math.hypot(dx,dz)))
            turn=(yaw-rotation[0]+180)%360-180;tilt=pitch-rotation[1]
            if abs(turn)<2 and abs(tilt)<2:return
            mx=max(-900,min(900,round(turn/gain)));my=max(-500,min(500,round(tilt/gain)))
            self.s.game.require_ingame('瞄准试炼守护者');self.s.game.xdo('mousemove_relative','--',str(mx),str(my));time.sleep(.2)
            self.report['inputs'].append({'method':'mapped relative mouse aim','dx':mx,'dy':my,'before':rotation,'target':point});self.save()
    def learning(self):
        self.clean();self.rc('tick unfreeze')
        for id in self.ids:
            self.rc(f'myvillage cultivation forget {self.s.username} myvillage:{id}')
            self.rc(f'item replace entity {self.s.username} hotbar.0 with myvillage:manual_active_xuan[myvillage:technique="myvillage:{id}"]')
            time.sleep(.4);self.use();self.poll(self.learned_text,lambda s:'myvillage:'+id in s,timeout=110,label='zero-progress manual '+id)
            self.check(id+'_normal_study',True,self.learned_text())
        self.capture('manuals-learned');self.freeze();self.learned=self.ids[:];self.bind(self.ids)
    def four_arts(self):
        self.clean();self.fixture_bind();u=self.s.username
        self.ready(240);self.equip('myvillage:guiyan_sword');target=self.dummy_at(.5,7);before=self.hp(target)
        self.cast(self.ids[0]);self.check('sword_outbound_damage',self.hp(target)<before,{'before':before,'after':self.hp(target)})
        self.check('sword_disables_melee',self.status()['weapon_away'],self.status());self.capture('sword-away')
        mark=self.offset();self.s.game.xdo('click','1');self.report['inputs'].append({'method':'mapped left click while sword away'});self.step(3)
        self.check('no_basic_attack_while_sword_away','COMBAT_DEBUG' not in self.log(mark) or 'kind=start' not in self.log(mark),self.log(mark))
        self.s.game.xdo('keydown','d');time.sleep(.45);self.s.game.xdo('keyup','d');self.report['inputs'].append({'method':'mapped strafe','key':'d','seconds':.45})
        position=self.pos();self.check('player_repositions_sword_return',abs(position[0]-.5)>.25,position)
        focus=self.status()['active'][0]['pos'];mid=[(focus[i]+position[i])/2 for i in range(3)];target=self.dummy_at(mid[0],mid[2]);before=self.hp(target)
        self.cast(self.ids[0],expected_cost=0);self.step(18)
        self.check('sword_return_damage',self.hp(target)<before,{'before':before,'after':self.hp(target)})
        self.check('sword_return_releases_weapon',not self.status()['weapon_away'],self.status());self.capture('sword-return')
        self.clean();self.ready(230);origin=self.pos();self.cast(self.ids[1]);after=self.pos()
        self.check('shadow_moves_player',math.dist(origin,after)>2,{'origin':origin,'after':after});self.check('shadow_origin_kept',len(self.status()['active'])==1,self.status())
        self.rc(f'tp {u} {after[0]} {after[1]} {after[2]} 180 0');self.capture('shadow-step')
        self.cast(self.ids[1],expected_cost=0);self.check('shadow_returns_to_origin',math.dist(self.pos(),origin)<.15,{'origin':origin,'after':self.pos()})
        self.clean();self.ready(240);target=self.dummy_at(.5,3);before=self.hp(target)
        for yaw in (-15,0,15):
            self.rc(f'tp {u} .5 -60 .5 {yaw} 35');self.cast(self.ids[2]);self.step(45)
        self.check('three_fire_seeds',sum(s['mode']=='fire' for s in self.status()['active'])==3,self.status());self.capture('three-fire-seeds')
        self.rc(f'myvillage cultivation qi fill {u}');before_power=self.power();self.key(self.assigned[self.ids[2]]);time.sleep(.3)
        after_power=self.power();self.check('fourth_seed_refused_without_payment',after_power==before_power and len(self.status()['active'])==3,{'before':before_power,'after':after_power,'state':self.status()})
        self.s.game.xdo('keydown','Shift_L');time.sleep(.2)
        try:self.cast(self.ids[2],expected_cost=0)
        finally:self.s.game.xdo('keyup','Shift_L')
        self.check('fire_detonation_damage',self.hp(target)<before,{'before':before,'after':self.hp(target)})
        self.check('detonation_clears_only_owned_seeds',not self.status()['active'],self.status());self.capture('fire-detonation')
        self.clean();self.ready(240);self.equip('myvillage:zhenyue_ruler');self.rc(f'tp {u} .5 -60 .5 0 30');target=self.dummy_at(.5,4);before=self.hp(target)
        self.cast(self.ids[3]);self.step(35);self.check('ruler_field_damage',self.hp(target)<before,{'before':before,'after':self.hp(target)})
        effects=self.rc(f'data get entity {target} active_effects');self.check('ruler_applies_slow','slowness' in effects,effects);self.capture('ruler-field')
        self.cast(self.ids[3],expected_cost=0);self.check('ruler_recall_releases_weapon',not self.status()['weapon_away'],self.status());self.clean()
    def artifacts(self):
        self.clean();self.ready(240);u=self.s.username;self.equip('myvillage:zhaoshui_mirror');self.use()
        self.rc('summon minecraft:arrow .5 -58.5 5 {Motion:[0d,0d,-0.7d],Tags:["legacy_target"]}')
        self.step(12);self.check('mirror_captures_incoming_arrow',self.status()['charge']==1,self.status());self.capture('mirror-captured')
        self.step(70);target=self.dummy_at(.5,7);before=self.hp(target);self.use();self.step(10)
        self.check('mirror_release_damage_and_consumption',self.status()['charge']==0 and self.hp(target)<before,{'state':self.status(),'before':before,'after':self.hp(target)})
        self.clean();self.ready(250);self.equip('myvillage:chiyu_gourd')
        self.rc('summon myvillage:ember_toad .5 -60 7 {Tags:["legacy_beast"],PersistenceRequired:1b,LegacyHeat:2}')
        for _ in range(20):
            self.step(20)
            pos=self.rc('data get entity @e[type=myvillage:legacy_projection,limit=1,sort=nearest] Pos')
            if ' has the following entity data:' in pos:break
        ember=read_data(pos);self.check('toad_ai_leaves_collectible_ember',isinstance(ember,list) and len(ember)==3,pos)
        self.rc('kill @e[tag=legacy_beast]');self.rc(f'tp {u} {ember[0]} -60 {ember[2]-3} 0 28');self.step(12);self.use();self.step(10)
        self.check('gourd_collects_real_ai_ember',self.status()['charge']==1,self.status());self.capture('gourd-collected')
        self.step(35);self.rc(f'tp {u} .5 -60 .5 0 0');target=self.dummy_at(.5,6);before=self.hp(target);self.use();self.step(8)
        self.check('gourd_releases_stored_flame',self.status()['charge']==0 and self.hp(target)<before,{'state':self.status(),'before':before,'after':self.hp(target)})
        self.clean()
    def normal_ai(self):
        self.clean();self.rc('myvillage beast debug on');u=self.s.username
        for m in self.source['mobs']:
            self.ready(240);self.equip('minecraft:air');mark=self.offset();self.rc(f'summon myvillage:{m["id"]} .5 -60 7 {{Tags:["legacy_beast"],PersistenceRequired:1b}}')
            for _ in range(22):
                self.step(20)
                if 'start myvillage:'+m['id']+'_' in self.log(mark):break
            log=self.log(mark);self.check(m['id']+'_normal_ai_attack','start myvillage:'+m['id']+'_' in log,log[-8000:]);self.capture(m['id']+'-native')
            if m['id']=='mirror_wraith':
                self.equip('myvillage:qingfeng_sword');self.step(100);self.rc(f'tp {u} .5 -60 .5 180 0')
                mark=self.offset();origin=self.pos();before=self.hp(u)
                self.s.game.xdo('click','1');self.report['inputs'].append({'method':'mapped empty-space sword attack observed by mirror wraith'})
                self.poll(lambda:self.log(mark),lambda s:'LEGACY_ECHO observe' in s,label='wraith observes a real player attack')
                self.s.game.xdo('keydown','d');movement_steps=0
                try:
                    for _ in range(12):
                        self.step(4);movement_steps+=4
                        if math.dist(origin,self.pos())>self.source['runtime']['mirror_echo_radius']+.4:break
                finally:self.s.game.xdo('keyup','d')
                self.report['inputs'].append({'method':'mapped strafe away from remembered position while advancing combat time','server_ticks':movement_steps})
                self.check('player_leaves_echo_position',math.dist(origin,self.pos())>self.source['runtime']['mirror_echo_radius'],{'origin':origin,'after':self.pos()})
                for _ in range(60):
                    self.step(2)
                    if 'LEGACY_ECHO resolve' in self.log(mark):break
                log=self.log(mark);resolved=re.search(r'LEGACY_ECHO resolve entity=\d+ position=\(([^)]+)\) delay=(\d+)',log)
                self.check('wraith_replays_remembered_location',resolved is not None and math.dist(origin,list(map(float,resolved.group(1).split(', '))))<.01 and int(resolved.group(2))>=self.source['runtime']['mirror_delay_ticks'],log[-5000:])
                self.check('leaving_echo_avoids_damage',self.hp(u)==before,{'before':before,'after':self.hp(u)})
                self.capture('mirror-echo-avoided')
            if m['id']=='ember_toad':
                self.rc('data merge entity @e[tag=legacy_beast,limit=1] {NoAI:1b}')
                before=self.hp('@e[tag=legacy_beast,limit=1]');self.rc('damage @e[tag=legacy_beast,limit=1] 2 myvillage:elemental_fire by '+u)
                heat=self.number('data get entity @e[tag=legacy_beast,limit=1] LegacyHeat')
                self.check('toad_fire_absorption_boundary',heat>0 and self.hp('@e[tag=legacy_beast,limit=1]')==before,{'heat':heat,'health':before})
                self.report['boundary_probes']=['火属性吸收的定向 damage 命令仅作边界探针，普通喷火与收余焰来自实际 AI。']
            self.rc('kill @e[tag=legacy_beast]');self.step(25)
        self.rc('myvillage beast debug off')
    def acquisition(self):
        self.clean();u=self.s.username;self.ready(240)
        self.rc('fill -1 -60 -1 2 -60 2 minecraft:water');self.equip('myvillage:watermarked_silk');self.step(3);self.use();self.step(3)
        inventory=self.rc(f'data get entity {u} Inventory');self.check('water_reveals_shadow_manual','myvillage:fenying_bu' in inventory,inventory)
        self.rc(f'clear {u}');self.equip('myvillage:watermarked_silk 2');self.use();self.step(3)
        inventory=self.rc(f'data get entity {u} Inventory');items=read_data(inventory)
        self.check('stacked_silk_consumes_exactly_one',sum(i.get('count',0) for i in items if i.get('id')=='myvillage:watermarked_silk')==1 and sum(i.get('count',0) for i in items if i.get('id')=='myvillage:manual_active_xuan')==1,inventory)
        self.rc('fill -1 -60 -1 2 -60 2 minecraft:air');self.ready(240)
        for slot,item in [('head','diamond_helmet'),('chest','diamond_chestplate'),('legs','diamond_leggings'),('feet','diamond_boots')]:
            self.rc(f'item replace entity {u} armor.{slot} with minecraft:{item}')
        self.report['trial_gear_fixture']='原版钻石防具和青锋剑；击杀由真实转向、前进与攻击输入完成。'
        for kind,mob,item,x in [('sword','wandering_sword','sword_rubbing',12),('mountain','stele_guardian','mountain_rubbing',32)]:
            self.rc(f'kill @e[type=myvillage:{mob}]');self.step(25);self.rc('kill @e[type=minecraft:item]')
            placed=self.rc(f'place template myvillage:legacy_{kind}_ruin {x-7} -61 10')
            block='sword_inscription' if kind=='sword' else 'mountain_stele'
            anchor=self.rc(f'execute if block {x} -60 17 myvillage:{block} run data get block {x} -60 17 Ready')
            self.check(kind+'_template_present',read_data(anchor)==1,{'placement':placed,'actual_anchor':anchor})
            self.rc(f'tp {u} {x+.5} -60 14.5 0 22');self.equip('minecraft:paper');self.use()
            birth=read_data(self.rc(f'data get entity @e[type=myvillage:{mob},limit=1] wilderness_birth'))
            available={p.stem:json.loads(p.read_text()) for p in (ROOT/'src/main/resources/data/myvillage/myvillage/realm').glob('*.json')}
            player_realm=birth['realm'];realm=available.get(player_realm.split(':')[1])
            if realm is None:
                name,realm=max(available.items(),key=lambda v:v[1]['sort_order']);player_realm='myvillage:'+name;stage=realm['stages'][-1]['id']
            else:stage=realm['stages'][birth['stage']]['id']
            self.rc(f'myvillage cultivation setrealm {u} {player_realm} {stage}');self.rc(f'effect give {u} minecraft:instant_health 1 20 true')
            info=self.one(f'myvillage stats info {u}','STATS_INFO');self.check(kind+'_trial_player_realm_applied',info['realm']==player_realm and info['factor']>1,info)
            self.report.setdefault('trial_realm_fixture',[]).append({'trial':kind,'guardian_birth':birth,'player_realm':player_realm,'player_stage':stage,'policy':'match regional guardian when playable; otherwise use the highest implemented player realm'});self.step(40)
            # Fight from an explicit safe fixture position with real mapped sword input.
            self.equip('myvillage:qingfeng_sword')
            guardian=read_data(self.rc(f'data get entity @e[type=myvillage:{mob},limit=1] Pos'))
            self.rc(f'tp {u} {guardian[0]+3} {guardian[1]} {guardian[2]} 90 0');self.step(5)
            for _ in range(30):
                current=self.rc(f'data get entity @e[type=myvillage:{mob},limit=1] Pos')
                if 'No entity was found' in current:
                    # Vanilla selectors omit a dead living entity before its 20-tick removal.
                    # Absence is not victory: wait for the bound death receipt at the real stele.
                    self.step(25);state=self.rc(f'data get block {x} -60 17 Cleared')
                    self.check(kind+'_bound_death_receipt',read_data(state)==1,state);break
                guardian=read_data(current);pos=self.pos()
                height=next(m['physical']['height'] for m in self.source['mobs'] if m['id']==mob)
                self.aim_at([guardian[0],guardian[1]+height*.6,guardian[2]])
                advance=math.hypot(pos[0]-guardian[0],pos[2]-guardian[2])>2.2
                if advance:self.s.game.xdo('keydown','w')
                try:
                    self.s.game.xdo('click','1');self.report['inputs'].append({'method':'mapped trial sword attack and pursuit','trial':kind,'forward':advance});self.step(12)
                finally:self.s.game.xdo('keyup','w')
                state=self.rc(f'data get block {x} -60 17 Cleared')
                if '1b' in state:break
            self.check(kind+'_trial_completed_by_mapped_attacks','1b' in state,state)
            self.equip('minecraft:paper');self.rc(f'tp {u} {x+.5} -60 14.5 0 22');self.use();self.step(4)
            inventory=self.rc(f'data get entity {u} Inventory');self.check(kind+'_paper_rubbing',item in inventory,inventory)
            self.use();self.step(4)
            self.check(kind+'_rubbing_cannot_be_claimed_twice',self.rc(f'data get entity {u} Inventory')==inventory,self.rc(f'data get block {x} -60 17 Claimed'))
            self.capture(kind+'-rubbing');self.rc('kill @e[type=myvillage:'+mob+']')
        for slot in ['head','chest','legs','feet']:self.rc(f'item replace entity {u} armor.{slot} with minecraft:air')
        self.rc(f'myvillage cultivation setrealm {u} myvillage:foundation_establishment myvillage:foundation_early');self.clean()
    def refusals(self):
        self.clean();self.fixture_bind();u=self.s.username;self.ready(250)
        before=self.power();mark=self.offset();self.key(self.assigned[self.ids[0]]);time.sleep(.3)
        self.check('wrong_item_no_payment',self.power()==before and 'result=start art=' not in self.log(mark),self.log(mark))
        self.rc('fill -2 -60 1 2 -56 1 minecraft:stone');self.step(12);before=self.power();self.key(self.assigned[self.ids[1]]);time.sleep(.3)
        self.check('shadow_wall_no_payment',self.power()==before and not self.status()['active'],self.status());self.rc('fill -2 -60 1 2 -56 1 minecraft:air')
        self.ready(230);self.cast(self.ids[1]);self.rc('fill -2 -60 2 2 -56 2 minecraft:stone');self.rc(f'myvillage cultivation qi fill {u}');self.step(12);before=self.power();self.key(self.assigned[self.ids[1]]);time.sleep(.3)
        self.check('shadow_return_cannot_cross_new_wall',self.power()==before and len(self.status()['active'])==1,self.status());self.rc('fill -2 -60 2 2 -56 2 minecraft:air')
        self.cast(self.ids[1],expected_cost=0);self.clean();self.ready(250);self.equip('myvillage:guiyan_sword');self.cast(self.ids[0])
        target=self.dummy_at(.5,2);before=self.hp(target);self.s.game.xdo('click','1');self.report['inputs'].append({'method':'mapped melee attack at an in-range target while sword is deployed'});self.step(26)
        self.check('deployed_weapon_cannot_damage_nearby_target',self.hp(target)==before and self.status()['weapon_away'],{'before':before,'after':self.hp(target),'state':self.status()})
        self.cast(self.ids[0],expected_cost=0);self.step(20);before=self.hp(target);self.s.game.xdo('click','1');self.report['inputs'].append({'method':'mapped melee control attack after sword returns'});self.step(26)
        self.check('returned_weapon_restores_real_melee',self.hp(target)<before and not self.status()['weapon_away'],{'before':before,'after':self.hp(target),'state':self.status()})
        self.clean();self.ready(250);self.equip('myvillage:guiyan_sword');self.cast(self.ids[0]);self.equip('minecraft:air')
        self.check('slot_replacement_cleans_projection',not self.status()['active'],self.status())
        self.ready(230);self.equip('myvillage:guiyan_sword')
        self.poll(lambda:self.rc(f'myvillage cultivation qi {u}'),lambda s:int(re.search(r'recovery_ticks=(\d+)',s).group(1))>=16,label='fresh recovery interval')
        self.rc(f'myvillage cultivation qi set {u} 0');before=self.power();self.key(self.assigned[self.ids[0]])
        self.check('insufficient_qi_no_projection',self.power()==before and not self.status()['active'],self.status());self.rc(f'myvillage cultivation qi fill {u}')
        self.clean()
    def two_players(self):
        self.clean();self.fixture_bind();self.rc('tick unfreeze')
        with Guest(self) as guest:
            self.guest=guest;self.position(guest.game);self.configure(guest.username)
            self.rc(f'tp {guest.username} 3.5 -60 6.5 160 0');self.freeze();self.ready(220);self.equip('myvillage:guiyan_sword');self.cast(self.ids[0])
            owner=self.pos();projection=next(p['pos'] for p in self.status()['active'] if p['mode']=='sword')
            observer=[(owner[0]+projection[0])/2+8,-60,(owner[2]+projection[2])/2]
            self.rc(f'tp {guest.username} {observer[0]} {observer[1]} {observer[2]} 90 4');self.step(4)
            self.report['observer_scene']={'owner':owner,'projection':projection,'observer':observer,'yaw':90,'pitch':4}
            self.capture('observer-sees-sword',guest.game)
            self.check('projection_does_not_lock_other_player',not self.status(guest.username)['weapon_away'],self.status(guest.username))
            self.cast(self.ids[0],expected_cost=0);self.step(20);self.check('projection_clears_for_owner',not self.status()['active'],self.status())
            self.report['multiplayer']='两名真实客户端观察同一飞剑；验证使用者与旁观者状态隔离。';self.rc('tick unfreeze');self.guest=None
        self.freeze();self.clean()
    def restart(self):
        self.clean();self.fixture_bind();self.ready(240);self.equip('myvillage:zhaoshui_mirror');self.use()
        self.rc('summon minecraft:arrow .5 -58.5 5 {Motion:[0d,0d,-0.7d],Tags:["legacy_target"]}');self.step(12)
        self.check('charged_mirror_before_restart',self.status()['charge']==1,self.status())
        self.rc('summon myvillage:ember_toad 8 -60 8 {NoAI:1b,PersistenceRequired:1b,LegacyHeat:2,Tags:["legacy_saved_beast"]}')
        identity=self.rc('data get entity @e[tag=legacy_saved_beast,limit=1] UUID')
        claimed={kind:self.rc(f'data get block {x} -60 17 Claimed') for kind,x in [('sword',12),('mountain',32)]} if 'acquisition' in self.report['completed'] else {}
        self.rc(f'item replace entity {self.s.username} hotbar.1 with myvillage:guiyan_sword');self.key('2');self.step(10)
        self.cast(self.ids[0]);before=self.bindings();learned=self.learned_text()
        self.rc('tick unfreeze');self.stop();self.attach=False;self.start(reuse=True)
        self.check('saved_learning_survives_restart',all('myvillage:'+i in self.learned_text() for i in self.ids),{'before':learned,'after':self.learned_text()})
        self.check('saved_bindings_survive_restart',all('myvillage:'+i in self.bindings() for i in self.ids),{'before':before,'after':self.bindings()})
        self.check('transient_projection_not_restored',not self.status()['active'] and not self.status()['weapon_away'],self.status())
        self.key('1');self.step(3);self.check('mirror_charge_survives_restart',self.status()['charge']==1,self.status())
        self.check('same_toad_heat_survives_restart',self.number('data get entity @e[tag=legacy_saved_beast,limit=1] LegacyHeat')==2 and self.rc('data get entity @e[tag=legacy_saved_beast,limit=1] UUID')==identity,identity)
        for kind,x in [('sword',12),('mountain',32)]:
            if kind in claimed:self.check(kind+'_claimed_state_survives_restart',self.rc(f'data get block {x} -60 17 Claimed')==claimed[kind],claimed[kind])
        self.capture('after-restart')
    def run(self):
        self.start()
        for case in self.cases:
            getattr(self,case)();self.done(case)

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--cases',nargs='+',choices=CASES,default=list(CASES));p.add_argument('--attach',action='store_true');p.add_argument('--keep-game-on-failure',action='store_true');a=p.parse_args()
    e=LegacyEvidence(a.out,a.cases,a.attach)
    try:e.run()
    except BaseException as error:e.report['failures'].append(repr(error));traceback.print_exc()
    finally:
        try:
            if e.s:
                e.rc('tick unfreeze')
                if e.report['failures'] and a.keep_game_on_failure:e.report['game_retained_for_debug']=True
                else:e.stop()
        except BaseException as error:e.report['failures'].append('cleanup: '+repr(error))
        e.report['passed']=not e.report['failures'] and not e.report['unrun'] and all(c['pass'] for c in e.report['checks']);e.save()
    return 0 if e.report['passed'] else 1

if __name__=='__main__':raise SystemExit(main())
