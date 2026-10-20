#!/usr/bin/env python3
"""Native workshop evidence. All use/hammer/mining actions are mapped input; commands are fixtures/readback."""
import argparse
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import time
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.alchemy_evidence import AlchemyEvidence
from tools.alchemy_quality_evidence import quality_stacks,SecondClient,read_data
from tools.combat_capture import scene,session as cs

CASES=('stages','training','tools','combat','two_players','lifecycle','ui','trough')
PIECE='myvillage:workpiece';TEMPER='myvillage:temper';MARK='myvillage:forge_mark'
POS='0 -60 3'
TROUGH='1 -60 3'
POINTS={'start':(262,117),'heat':(114,135),'strike':(173,135),'quench':(262,117),'temper':(262,117),
        'grind':(262,117),'assemble':(262,117),'infuse':(262,117),'repair':(35,219)}

def point(size,x,y):
    w,h=size;scale=max(1,min(w//320,h//240));return scale*((w//scale-316)//2+x),scale*((h//scale-234)//2+y)

def stack_spec(stack):
    components=stack.get('components',{})
    return stack['id']+('['+','.join(k+'='+json.dumps(v,ensure_ascii=False,separators=(',',':')) for k,v in components.items())+']' if components else '')

class WorkshopEvidence(AlchemyEvidence):
    def __init__(self,out,cases):
        super().__init__(out,'zh_cn');self.cases=cases;self.pulse=None;self.recorders=[]
        self.config=json.loads((ROOT/'src/main/resources/data/myvillage/forging/workshop.json').read_text())
        self.recipes=json.loads((ROOT/'src/main/resources/data/myvillage/forging/rules.json').read_text())['recipes']
        self.report.update(kind='forging_workshop',selected=cases,completed=[],unrun=list(cases),mapped_input=[],
            method='native mouse/key input, operator-only readback and fixtures; isolated cloud world',
            owner_sound='not_verified',owner_feel='not_verified',owner_fps='not_verified',owner_balance='not_verified')
    def check(self,name,passed,detail):
        super().check(name,passed,detail)
        if not passed:raise AssertionError(name)
        return True
    def inspect(self,user=None):return self.one('myvillage workshop inspect '+(user or self.s.username),'WORKSHOP')
    def inventory(self,user=None):return quality_stacks(self.rc('data get entity '+(user or self.s.username)+' Inventory'))
    def count(self,item,user=None):return sum(s['count'] for s in self.inventory(user).values() if s['id']==item)
    def click(self,x,y,shift=False,game=None,wait=.18):
        game=game or self.s.game;px,py=point(game.size,x,y);game.xdo('mousemove',str(px),str(py))
        if shift:game.xdo('keydown','Shift_L')
        try:game.xdo('click','1')
        finally:
            if shift:game.xdo('keyup','Shift_L')
        self.report['mapped_input'].append({'kind':'click','at':[x,y],'shift':shift,'wall':time.monotonic()});time.sleep(wait)
    def open(self,game=None,user=None):
        game=game or self.s.game;user=user or self.s.username
        self.rc(f'tp {user} .5 -60 .5 0 20.5');time.sleep(.4);game.require_ingame('open workshop');game.xdo('click','3')
        self.poll(lambda:self.inspect(user),lambda d:d['open'],12,'mapped workshop open')
    def close_menu(self,game=None):
        game=game or self.s.game;game.xdo('key','Escape');time.sleep(.4);game.require_ingame('close workshop')
    def move_slot(self,slot,game=None):
        if 0<=slot<=8:self.click(152+18*slot,217,True,game)
        elif 9<=slot<=35:self.click(152+18*((slot-9)%9),159+18*((slot-9)//9),True,game)
        else:raise ValueError(slot)
    def insert(self,item,user=None,game=None):
        user=user or self.s.username;rows=self.inventory(user);slot=next(k for k,v in rows.items() if v['id']==item)
        self.move_slot(slot,game);self.poll(lambda:self.inspect(user),lambda d:d['input']['id']==item,6,'insert '+item)
    def operate(self,name,user=None,game=None):
        user=user or self.s.username;before=self.inspect(user);self.click(*POINTS[name],game=game)
        self.poll(lambda:self.inspect(user),lambda d:d.get('action',-1)>=0 or d['input']!=before['input'] or d['output']!=before['output'] or d['heat']>before['heat'] or d['status'] not in (0,13,14),8,'action accepted '+name)
        result=self.poll(lambda:self.inspect(user),lambda d:d.get('action',-1)<0,12,'operation '+name)
        self.check('operation_'+name,result['status'] in (13,14),{'before':before,'after':result});return result
    def give(self,item,n=1,user=None):self.rc(f'give {user or self.s.username} {item} {n}')
    def materials(self,user=None):
        for item,n in [('minecraft:iron_ingot',32),('minecraft:stick',16),('minecraft:diamond',12),('minecraft:coal',24),
                       ('minecraft:water_bucket',1),('minecraft:flint',8),('minecraft:iron_nugget',32),('myvillage:low_grade_spirit_stone',16),
                       ('myvillage:edge_tempering_sand',4),('myvillage:xuantie_ingot',4),('myvillage:chitong_ingot',4)]:self.give(item,n,user)
    def clean(self):
        if self.inspect()['open']:self.close_menu()
        self.rc(f'clear {self.s.username}');self.rc(f'setblock {POS} myvillage:forging_anvil[facing=north]')
        self.rc(f'setblock {TROUGH} myvillage:quenching_trough[facing=north,filled=true]');self.rc('kill @e[type=minecraft:item]');self.s.game.key('1')
    def hold(self,seconds,game=None):
        game=game or self.s.game;px,py=point(game.size,*POINTS['strike']);game.xdo('mousemove',str(px),str(py));game.xdo('mousedown','1')
        self.report['mapped_input'].append({'kind':'hold_hammer','seconds':seconds,'wall':time.monotonic()})
        try:time.sleep(seconds)
        finally:game.xdo('mouseup','1')
        self.poll(self.inspect,lambda d:d.get('action',-1)<0,10,'last swing finishes')
    def form(self):
        for _ in range(8):
            data=self.inspect()
            if data['input']['piece']['blows']==self.config['forming_strikes']:return
            if data['heat']<self.config['strike_ticks']*2:self.operate('heat')
            self.hold(2.6)
        raise AssertionError('forming did not converge')
    def audio_start(self):
        runtime=Path('/tmp/workshop-audio');runtime.mkdir(mode=0o700,exist_ok=True)
        os.environ['XDG_RUNTIME_DIR']=str(runtime);os.environ['PULSE_SERVER']='unix:/tmp/workshop-pulse.sock';os.environ['ALSOFT_DRIVERS']='pulse'
        self.pulse_log=(self.out/'pulse.log').open('w')
        self.pulse=subprocess.Popen(['pulseaudio','--daemonize=no','--exit-idle-time=-1','-n',
            '--load=module-native-protocol-unix socket=/tmp/workshop-pulse.sock auth-anonymous=1',
            '--load=module-null-sink sink_name=forge rate=44100'],stdout=self.pulse_log,stderr=subprocess.STDOUT)
        self.poll(lambda:subprocess.run(['pactl','info'],capture_output=True,text=True).returncode,lambda n:n==0,10,'audio server')
        subprocess.run(['pactl','set-default-sink','forge'],check=True)
        cs.merge_options(ROOT/'run-combat-capture/client/options.txt',{'soundCategory_master':'1.0','soundCategory_blocks':'1.0','soundCategory_music':'0.0','soundCategory_ambient':'0.0'})
    def record_start(self,name,seconds,game=None):
        video=self.out/name
        args=['ffmpeg','-hide_banner','-loglevel','error','-y','-thread_queue_size','512','-f','x11grab','-draw_mouse','0',
            '-video_size','960x540','-framerate','30','-i',(game.display if game else self.s.state['display'])+'.0+0,0',
            '-thread_queue_size','512','-f','pulse','-i','forge.monitor','-t',str(seconds),'-c:v','libx264','-preset','veryfast',
            '-crf','20','-pix_fmt','yuv420p','-c:a','aac','-b:a','160k','-movflags','+faststart',str(video)]
        log=(self.out/(name+'.log')).open('w');process=subprocess.Popen(args,stdout=log,stderr=subprocess.STDOUT);self.recorders.append((process,log));return process
    def stages(self):
        self.clean();self.open();self.click(45,42+2*14+6);before=self.inventory();self.click(*POINTS['start'])
        self.check('missing_materials_atomic',self.inspect()['input']['count']==0 and self.inventory()==before,self.inspect())
        self.close_menu();self.materials();time.sleep(20);self.open();self.click(45,76)
        before=self.inventory();self.operate('start');data=self.inspect()
        self.check('mundane_without_awakening',data['cap']==0 and data['input']['piece']['stage']==0,data)
        self.check('base_costs_only',self.count('minecraft:iron_ingot')==29 and self.count('minecraft:stick')==14 and self.count('myvillage:low_grade_spirit_stone')==16,self.inventory())
        self.capture('01-rough-zh.png');before=self.inspect()['input'];self.click(*POINTS['strike']);time.sleep(.8)
        self.check('cold_strike_has_no_effect',self.inspect()['input']==before,self.inspect())
        self.operate('heat');before=self.inspect();rec=self.record_start('hammer-with-audio.mp4',9)
        time.sleep(.6);self.hold(6.3);after=self.inspect();rec.wait(timeout=20)
        self.check('held_mapped_strikes_advance',after['input']['piece']['blows']>before['input']['piece']['blows']+1,{'before':before,'after':after})
        audio=subprocess.run(['ffmpeg','-hide_banner','-i',str(self.out/'hammer-with-audio.mp4'),'-vn','-af','volumedetect','-f','null','-'],capture_output=True,text=True,check=True)
        (self.out/'audio-levels.txt').write_text(audio.stderr)
        match=re.search(r'max_volume: ([-\d.]+) dB',audio.stderr)
        self.check('captured_non_silent_native_audio',bool(match) and float(match.group(1))>-50,{'max_volume_db':float(match.group(1)) if match else None})
        self.form();self.capture('02-shaped-zh.png');data=self.inspect();self.check('finite_forming',data['input']['piece']['blows']==12 and data['input']['piece']['stage']==1,data)
        if data['heat']==0:self.operate('heat')
        self.operate('quench');self.check('quench_uses_trough_not_inventory_water',not self.trough_filled() and self.count('minecraft:water_bucket')==1,self.inventory());self.capture('03-quenched-zh.png')
        self.operate('temper');self.capture('04-tempered-zh.png');self.operate('grind');self.capture('05-ground-zh.png')
        ground=self.inspect()['input'];self.close_menu();self.check('intermediate_returned_once',self.count(ground['id'])==1,self.inventory())
        self.open();self.insert(ground['id']);self.operate('assemble');final=self.inspect()['output']
        self.check('ordinary_tool_is_usable_unmarked',final['id']=='myvillage:forged_pickaxe' and 'temper' in final and 'mark' not in final and final['max_damage']>0,final)
        self.capture('06-finished-zh.png');self.click(196,112,True);self.close_menu()
    def training(self):
        self.clean();self.materials()
        spec='myvillage:qingfeng_sword[myvillage:temper={experience:16,edge:0,endurance:0,channeling:0,treatment:1},myvillage:forge_mark={trait:"sharp",quality:2},minecraft:damage=100]'
        self.give(spec);self.open();self.insert('myvillage:qingfeng_sword');before=self.inspect()
        self.check('item_experience_budget',before['available']==2,before)
        self.operate('heat');cost_before=self.count('minecraft:iron_nugget');self.operate('strike');first=self.inspect()['input']
        self.check('each_strike_has_exact_cost_and_gain',first['temper']['experience']==10 and first['temper']['edge']==1 and self.count('minecraft:iron_nugget')==cost_before-1,first)
        self.click(260,73);self.operate('strike');second=self.inspect()['input']
        self.check('directions_preserve_previous_investment',second['temper']['edge']==1 and second['temper']['endurance']==1 and second['temper']['experience']==4,second)
        self.check('quality_and_damage_preserved',second['mark']==before['input']['mark'] and second['damage']==100 and second['max_damage']>first['max_damage'],{'before':before,'after':second})
        snapshot=self.inspect()['input'];self.click(*POINTS['strike']);time.sleep(1);self.check('no_experience_no_strike',self.inspect()['input']==snapshot,self.inspect())
        self.capture('07-tempering-zh.png');self.close_menu()
        grown=next(v for v in self.inventory().values() if v['id']=='myvillage:qingfeng_sword')
        modifiers=grown['components']['minecraft:attribute_modifiers']['modifiers']
        edge=[m for m in modifiers if m['id']=='myvillage:tempered_edge']
        self.check('growth_is_one_real_attack_modifier',len(edge)==1 and abs(edge[0]['amount']-(self.config['attack_per_strike']+self.config['treatment_attack']))<1e-6,modifiers)
        # A missing last material must not consume experience or change the existing object.
        self.clean();self.give(spec);self.give('minecraft:coal',1);self.open();self.insert('myvillage:qingfeng_sword');self.operate('heat')
        before=self.inspect()['input'];self.click(*POINTS['strike']);time.sleep(1);self.check('missing_training_material_atomic',self.inspect()['input']==before and self.inspect()['status']==4,self.inspect());self.close_menu()
        self.give('minecraft:coal',1);self.open();self.insert('myvillage:qingfeng_sword');coal_before=self.count('minecraft:coal')
        self.click(*POINTS['heat'],wait=.05);self.close_menu()
        self.check('cancel_unpaid_heating_returns_piece_and_fuel',self.count('minecraft:coal')==coal_before and self.count('myvillage:qingfeng_sword')==1,self.inventory())
        # Infusion is a one-time operation, preserving the temper record and capping quality.
        self.clean();self.materials();self.give('myvillage:forged_axe[myvillage:temper={experience:7,edge:1,endurance:2,channeling:0,treatment:2}]')
        u=self.s.username;self.rc(f'myvillage cultivation awaken {u}');self.rc(f'myvillage cultivation crafting set {u} forging 0')
        self.rc(f'setblock {POS} myvillage:forging_table[facing=north]');self.open();self.insert('myvillage:forged_axe');self.click(285,36);self.operate('infuse');out=self.inspect()['output']
        self.check('infusion_respects_cap_and_keeps_training',out.get('mark')=={'trait':'tough','quality':1} and out['temper']=={'experience':7,'edge':1,'endurance':2,'channeling':0,'treatment':2},out)
        self.capture('08-infused-zh.png');self.close_menu()
        saved=next(v for v in self.inventory().values() if v['id']=='myvillage:forged_axe')
        saved['components']['minecraft:damage']=100
        self.clean();self.give(stack_spec(saved));self.give('minecraft:iron_ingot');self.give('myvillage:low_grade_spirit_stone')
        self.open();self.insert('myvillage:forged_axe');self.operate('repair');self.close_menu()
        repaired=next(v for v in self.inventory().values() if v['id']=='myvillage:forged_axe')
        self.check('repair_keeps_all_non_damage_components',
            {k:v for k,v in repaired['components'].items() if k!='minecraft:damage'}=={k:v for k,v in saved['components'].items() if k!='minecraft:damage'}
            and repaired['components'].get('minecraft:damage',0)<100,{'before':saved,'after':repaired})
    def tool_fixture(self,item,x,block):
        self.clean();u=self.s.username
        self.rc(f'item replace entity {u} hotbar.0 with {item}');self.s.game.key('1')
        self.rc(f'setblock {x} -60 2 {block}');self.rc(f'tp {u} {x+.5} -60 .5 0 29.2');time.sleep(.6)
    def mine(self,x):
        game=self.s.game;game.require_ingame('mine');game.xdo('mousedown','1')
        self.report['mapped_input'].append({'kind':'mine','pos':[x,-60,2]})
        try:self.poll(lambda:self.rc(f'execute if block {x} -60 2 minecraft:air'),lambda s:'Test passed' in s,15,'block mined')
        finally:game.xdo('mouseup','1')
        time.sleep(.3)
    def tools(self):
        for kind,x,block in [('pickaxe',4,'minecraft:iron_ore'),('axe',6,'minecraft:oak_log'),('shovel',8,'minecraft:clay')]:
            self.tool_fixture('myvillage:forged_'+kind,x,block);self.mine(x);state=self.inspect()['held'];expected=next(c['work_experience'] for c in self.config['crafts'] if c['kind']==kind)
            self.check('actual_'+kind+'_earns_experience',state.get('temper',{}).get('experience')==expected,state)
            self.rc(f'setblock {x} -60 2 {block}');self.mine(x);self.check('reworked_position_no_experience_'+kind,self.inspect()['held'].get('temper',{}).get('experience')==expected,self.inspect())
        self.clean();u=self.s.username;self.rc(f'item replace entity {u} hotbar.0 with myvillage:forged_hoe');self.s.game.key('1')
        self.rc('setblock 10 -61 2 minecraft:dirt');self.rc('setblock 10 -60 2 minecraft:air');self.rc(f'tp {u} 10.5 -60 .5 0 39');time.sleep(.5);self.s.game.xdo('click','3')
        self.poll(lambda:self.inspect()['held'],lambda d:d.get('temper',{}).get('experience',0)>0,8,'mapped tilling');self.check('actual_hoe_earns_experience',self.inspect()['held']['temper']['experience']==1,self.inspect())
        # Fresh location, real player placement, then real mining with a forged shovel.
        self.clean();self.rc(f'item replace entity {u} hotbar.0 with minecraft:clay');self.rc(f'item replace entity {u} hotbar.1 with myvillage:forged_shovel')
        self.rc('setblock 12 -61 2 minecraft:stone');self.rc('setblock 12 -60 2 minecraft:air');self.rc(f'tp {u} 12.5 -60 .5 0 39');self.s.game.key('1');time.sleep(.5);self.s.game.xdo('click','3')
        self.poll(lambda:self.rc('execute if block 12 -60 2 minecraft:clay'),lambda s:'Test passed' in s,8,'mapped player placement')
        self.s.game.key('2');self.rc(f'tp {u} 12.5 -60 .5 0 29.2');time.sleep(.4);self.mine(12)
        self.check('placed_block_cannot_mint_experience',self.inspect()['held'].get('temper',{}).get('experience',0)==0,self.inspect())
    def combat(self):
        self.clean();u=self.s.username;self.rc('time set midnight');self.rc('difficulty normal');self.rc(f'item replace entity {u} hotbar.0 with myvillage:qingfeng_sword');self.s.game.key('1')
        self.rc(f'tp {u} 18.5 -60 .5 0 0');self.rc('summon minecraft:zombie 18.5 -60 2.5 {NoAI:1b,PersistenceRequired:1b,Tags:["workshop_target"]}')
        self.poll(lambda:self.rc('data get entity @e[tag=workshop_target,limit=1] Health'),lambda s:'data: ' in s,10,'combat target')
        time.sleep(.6)
        for _ in range(4):
            self.s.game.xdo('click','1');time.sleep(1.1)
            if self.inspect()['held'].get('temper',{}).get('experience',0)>0:break
        held=self.inspect()['held'];self.check('actual_weapon_damage_earns_item_experience',held.get('temper',{}).get('experience',0)>0,held)
        before=held['temper']['experience'];self.rc('kill @e[tag=workshop_target]');self.s.game.xdo('click','1');time.sleep(.8)
        self.check('swinging_at_air_no_experience',self.inspect()['held']['temper']['experience']==before,self.inspect());self.rc('time set 6000')
    def world_capture(self,name,game):
        time.sleep(.45)  # 等待旁观客户端接收更新并绘制，不能用服务器读数替代截图。
        window=game.xdo('search','--name','Minecraft').split()[0]
        subprocess.run(['import','-silent','-window',window,str(self.out/name)],env=game.env,check=True,timeout=30)
    def two_players(self):
        self.clean();self.materials();self.open();self.click(45,76);self.operate('start');owner=self.inspect()['input']
        self.rc(f'tp {self.s.username} -2 -60 3.5')
        with SecondClient(self) as guest:
            user,game=guest.username,guest.game;self.rc(f'gamemode survival {user}');self.materials(user)
            before=self.inventory(user);self.rc(f'tp {user} .5 -60 .5 0 20.5');time.sleep(.5);game.xdo('click','3');time.sleep(.8)
            self.check('occupied_station_rejects_second_operator',not self.inspect(user)['open'] and self.inspect()['input']==owner and self.inventory(user)==before,self.inspect(user))
            self.rc(f'gamemode spectator {user}');self.rc(f'tp {user} 2.8 -59.3 1.2 40 28');game.key('F1');time.sleep(1)
            self.world_capture('world-01-rough.png',game)
            self.operate('heat');self.world_capture('world-02-hot.png',game)
            rec=self.record_start('world-hammer-with-audio.mp4',10,game);time.sleep(.6);before_hits=self.inspect()['hits'];self.hold(6.5);rec.wait(timeout=20)
            self.check('observed_mapped_strikes_commit',self.inspect()['hits']>before_hits+1,self.inspect());self.form();self.world_capture('world-03-shaped.png',game)
            self.operate('heat')
            # 同一槽的取水与淬火互斥；释放后用同一站位再次点击，证明前一次确实瞄准水槽。
            self.rc(f'gamemode survival {user}')
            self.rc(f'item replace entity {user} hotbar.8 with minecraft:bucket')
            self.rc(f'tp {user} 1.5 -60 .5 0 25');game.key('9');time.sleep(.4)
            self.click(*POINTS['quench'],wait=.12)
            self.poll(self.inspect,lambda d:d['action']==3,3,'locked trough quench starts')
            game.xdo('click','3');time.sleep(.25)
            self.report['mapped_input'].append({'kind':'second_player_take_locked_water','wall':time.monotonic()})
            self.check('second_player_cannot_drain_active_trough',self.trough_filled() and self.count('minecraft:bucket',user)==1,self.inventory(user))
            self.close_menu();game.xdo('click','3');time.sleep(.35)
            self.check('same_aim_recovers_water_after_release',not self.trough_filled() and self.count('minecraft:water_bucket',user)==2,self.inventory(user))
            # 注回刚取出的水，操作方继续原器坯。
            game.xdo('click','3');time.sleep(.35)
            self.check('second_player_refills_released_trough',self.trough_filled() and self.count('minecraft:bucket',user)==1,self.inventory(user))
            self.open();self.insert('myvillage:workpiece_forged_pickaxe_shaped');self.operate('heat')
            self.rc(f'tp {self.s.username} -2 -60 3.5')
            self.rc(f'gamemode spectator {user}');self.rc(f'tp {user} 2.8 -59.3 1.2 40 28');time.sleep(.5)
            rec=self.record_start('world-quench-with-audio.mp4',6,game);time.sleep(.4)
            self.click(*POINTS['quench'],wait=.12)
            self.poll(self.inspect,lambda d:d['action']==3,3,'quench starts')
            self.world_capture('world-quench-immersed.png',game)
            self.poll(self.inspect,lambda d:d['action']<0,6,'quench finishes')
            self.check('quench_changes_piece_and_empties_trough',self.inspect()['input']['piece']['stage']==2 and not self.trough_filled(),self.inspect())
            rec.wait(timeout=15);self.world_capture('world-04-quenched.png',game)
            self.operate('temper');self.world_capture('world-05-tempered.png',game)
            self.operate('grind');self.world_capture('world-06-ground.png',game)
            self.operate('assemble');finished=self.inspect()['output'];self.world_capture('world-07-finished.png',game)
            self.close_menu();self.check('physical_display_does_not_duplicate_item',self.count(finished['id'])==1 and self.count(finished['id'],user)==0,self.inventory())
            time.sleep(.5);self.world_capture('world-08-empty.png',game)
            # 六类器坯都经真实槽位输入，命令只准备物品。
            for recipe,kind in [('qingfeng_sword','sword'),('lingxiao_spear','spear'),('forged_axe','axe'),('forged_shovel','shovel'),('forged_hoe','hoe')]:
                item='myvillage:workpiece_'+recipe+'_ground'
                self.give(item+'[myvillage:workpiece={recipe:"'+recipe+'",stage:4,blows:12,treatment:0}]')
                self.open();self.insert(item);self.rc(f'tp {self.s.username} -2 -60 3.5')
                time.sleep(.5);self.world_capture('world-kind-'+kind+'.png',game);self.close_menu()
            self.rc(f'tp {self.s.username} -2 -60 3.5')
            game.key('F1');self.rc(f'gamemode survival {user}');self.open(game,user);self.click(45,90,game=game);self.operate('start',user,game)
            other=self.inspect(user)['input'];self.check('released_station_can_be_claimed',other['piece']['recipe']=='forged_axe',other)
            self.close_menu(game);self.check('next_operator_returns_only_own_piece',self.count(other['id'],user)==1 and self.count(other['id'])==0,self.inventory(user))
        self.report['multiplayer']='two_native_clients_station_occupancy_and_observer_capture_pass'
    def lifecycle(self):
        self.clean();self.materials();self.open();self.click(45,76);self.operate('start');self.operate('heat');self.operate('strike');partial=self.inspect()['input'];self.close_menu()
        self.give('myvillage:forged_axe[myvillage:temper={experience:17,edge:2,endurance:3,channeling:0,treatment:2},myvillage:forge_mark={trait:"tough",quality:2}]');before=self.inventory();self.rc('save-all flush');self.stop();self.language='zh_cn';self.start(reuse=True)
        self.check('partial_workpiece_and_inventory_survive_restart',self.inventory()==before,self.inventory())
        self.open();self.insert(partial['id']);self.check('reopen_retains_work_but_heat_is_cold',self.inspect()['input']==partial and self.inspect()['heat']==0,self.inspect());self.operate('heat');self.operate('strike')
        self.check('resume_continues_existing_progress',self.inspect()['input']['piece']['blows']==partial['piece']['blows']+1,self.inspect())
        self.capture('09-resumed-zh.png');self.close_menu();before=self.inventory();self.rc(f'kill {self.s.username}')
        self.poll(lambda:self.number(f'data get entity {self.s.username} Health'),lambda n:n>0,15,'native respawn health')
        self.poll(self.inventory,lambda v:v==before,15,'keepInventory survives death');self.check('death_keeps_components_once',self.inventory()==before,self.inventory())
        self.open();self.insert(partial['id']);self.rc(f'setblock {POS} minecraft:air')
        self.poll(lambda:self.inspect()['open'],lambda b:not b,10,'block removal closes menu')
        self.check('station_removal_returns_work_once',self.inventory()==before,self.inventory());self.rc(f'setblock {POS} myvillage:forging_anvil[facing=north]')
    def ui(self):
        self.clean();self.materials();self.open();self.click(45,76);self.operate('start')
        game=self.s.game;window=game.xdo('search','--name','Minecraft').split()[0]
        for w,h in [(960,540),(640,480)]:
            game.xdo('windowsize',window,str(w),str(h));game.xdo('windowmove',window,'0','0');game.size=(w,h);time.sleep(.8);self.capture(f'ui-{self.language}-{w}x{h}.png')
            self.check('native_menu_visible_'+str(w),game.pointer_grabbed() is False,{'size':[w,h],'visual_review':'pending'})
        game.xdo('windowsize',window,'960','540');game.size=(960,540);self.close_menu();self.rc(f'tp {self.s.username} 2 -60 .5 28 18');time.sleep(.6);self.capture('workshop-in-world.png')
        self.rc(f'setblock {POS} myvillage:forging_table[facing=north]');self.open();self.click(99,219)
        self.poll(lambda:self.inspect()['open'],lambda opened:not opened,6,'copper sub-menu opens')
        self.check('copper_native_menu_visible',game.pointer_grabbed() is False,self.inspect())
        self.capture('copper-infusion-'+self.language+'.png')
        w,h=game.size;scale=max(1,min(w//320,h//240));px=scale*((w//scale-304)//2+248);py=scale*((h//scale-238)//2+15)
        game.xdo('mousemove',str(px),str(py));game.xdo('click','1')
        self.poll(lambda:self.inspect()['open'],bool,6,'copper back opens workshop');self.check('copper_back_returns_to_workshop',self.inspect()['open'],self.inspect());self.close_menu()
    def trough_filled(self):
        return 'myvillage:quenching_trough' in self.rc(f'execute if block {TROUGH} myvillage:quenching_trough[filled=true] run data get block {TROUGH}')
    def trough_click(self):
        self.rc(f'tp {self.s.username} 1.5 -60 .5 0 25');self.s.game.key('1');time.sleep(.4)
        self.s.game.xdo('click','3');self.report['mapped_input'].append({'kind':'use_trough_bucket','wall':time.monotonic()});time.sleep(.4)
    def trough(self):
        self.clean();self.rc(f'setblock {TROUGH} myvillage:quenching_trough[filled=false]')
        self.give('minecraft:water_bucket');self.trough_click()
        self.check('mapped_bucket_fills_trough_returns_bucket',self.trough_filled() and self.count('minecraft:bucket')==1 and self.count('minecraft:water_bucket')==0,self.inventory())
        self.capture('trough-filled.png');self.trough_click()
        self.check('mapped_bucket_recovers_water',not self.trough_filled() and self.count('minecraft:water_bucket')==1 and self.count('minecraft:bucket')==0,self.inventory())
        self.trough_click();before=self.inventory();self.rc('save-all flush');self.stop();self.start(reuse=True)
        self.check('water_and_bucket_survive_restart',self.trough_filled() and self.inventory()==before,self.inventory())
        self.rc(f'clear {self.s.username}');self.materials()
        item='myvillage:workpiece_forged_pickaxe_shaped'
        self.give(item+'[myvillage:workpiece={recipe:"forged_pickaxe",stage:1,blows:12,treatment:0}]')
        self.open();self.insert(item);self.operate('heat');before=self.inspect()['input']
        self.rc(f'setblock {TROUGH} minecraft:air');time.sleep(.3);self.click(*POINTS['quench'])
        self.check('missing_trough_rejects_quench',self.inspect()['quench_reason']==16 and self.inspect()['action']<0 and self.inspect()['input']==before,self.inspect())
        self.rc(f'setblock {TROUGH} myvillage:quenching_trough[filled=false]');time.sleep(.3);self.click(*POINTS['quench'])
        self.check('dry_trough_rejects_quench',self.inspect()['quench_reason']==16 and self.inspect()['input']==before,self.inspect())
        self.rc(f'setblock {TROUGH} myvillage:quenching_trough[filled=true]');time.sleep(.3)
        self.click(*POINTS['quench']);self.poll(self.inspect,lambda d:d['action']==3,3,'cancelled quench starts');self.close_menu()
        self.check('cancel_returns_one_piece_and_preserves_water',self.count(item)==1 and self.trough_filled(),self.inventory())
        self.open();self.insert(item);self.operate('heat');self.click(*POINTS['quench'])
        self.poll(self.inspect,lambda d:d['action']==3,3,'removed trough quench starts');self.rc(f'setblock {TROUGH} minecraft:air')
        self.poll(self.inspect,lambda d:d['action']<0,5,'removed trough finishes safely')
        self.check('trough_removal_cannot_complete_or_duplicate',self.inspect()['input']==before and self.inspect()['status']==16,self.inspect())
        self.rc(f'setblock {TROUGH} myvillage:quenching_trough[filled=true]');self.operate('heat');self.operate('quench')
        self.check('one_water_charge_one_stage_transition',not self.trough_filled() and self.inspect()['input']['piece']['stage']==2 and self.inspect()['input']['count']==1,self.inspect())
        self.close_menu();self.check('completed_piece_returned_once',self.count('myvillage:workpiece_forged_pickaxe_quenched')==1 and self.count(item)==0,self.inventory())
    def run(self):
        self.audio_start();self.start();u=self.s.username
        for command in (*scene.world_rules(),*scene.player_setup(u,'minecraft:air'),'myvillage world pause','forceload add -16 -16 32 32',f'setblock {POS} myvillage:forging_anvil[facing=north]'):self.rc(command)
        for case in self.cases:
            getattr(self,case)();self.report['completed'].append(case);self.report['unrun'].remove(case);self.save()
        self.report['status']='pass';self.save()
    def shutdown(self):
        for process,log in self.recorders:
            if process.poll() is None:process.terminate();process.wait(timeout=15)
            log.close()
        if self.s:self.stop()
        if self.pulse:
            self.pulse.terminate();self.pulse.wait(timeout=10);self.pulse_log.close()
        self.save()

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,default=ROOT/'out/preview/forging_workshop/runtime');parser.add_argument('--cases',default=','.join(CASES));args=parser.parse_args()
    cases=args.cases.split(',')
    if not cases or len(cases)!=len(set(cases)) or not set(cases)<=set(CASES):raise ValueError('Unknown/duplicate cases')
    evidence=WorkshopEvidence(args.out,cases)
    try:evidence.run()
    except BaseException as error:evidence.report['status']='fail';evidence.report['failures'].append(repr(error));evidence.save();raise
    finally:evidence.shutdown()
if __name__=='__main__':main()
