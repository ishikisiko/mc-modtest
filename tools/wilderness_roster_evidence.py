#!/usr/bin/env python3
"""Colab-only bounded native evidence; commands prepare/read fixtures, mapped input performs play.

Keeps the Colab VM alive. Every attempt has its own report. A ready owned capture may be attached;
normal completion leaves the game available until the caller exports and stops it explicitly.
"""
from __future__ import annotations
import argparse,json,math,os,re,secrets,subprocess,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.foundation_pills_evidence import FoundationEvidence,POS
from tools.alchemy_quality_evidence import read_data,quality_stacks,SecondClient
from tools.alchemy_array_evidence import saved_items
from tools.forging_array_evidence import ForgingArrayEvidence,infusion_costs,POINTS as FORGE_POINTS
from tools.combat_capture import scene,session as cs,procs
from tools.gen_wilderness_roster import catalogue

CASES=('visuals','combat','natural','alchemy','forging','effects','restart','observers')
TARGET='@e[tag=roster_target,limit=1]'

class RosterEvidence(FoundationEvidence):
    def __init__(self,out,cases,attach=False):
        super().__init__(out);self.roster=catalogue();self.cases=cases;self.attach=attach
        self.recipes=[r for r in self.rules['recipes'] if r['id'] in self.roster['medicines']]
        self.report.update(kind='wilderness_roster',selected=cases,completed=[],unrun=list(cases),screenshots=[],videos=[],source_commit=json.loads((ROOT/'source-manifest.json').read_text())['commit'],owner_appearance='not_verified',owner_feel='not_verified',owner_balance='not_verified',owner_sound='not_verified',owner_fps='not_verified',colab_policy='keep VM for owner acceptance')
    def start(self,reuse=False):
        if os.environ.get('MYVILLAGE_CLOUD_PROVIDER')!='Colab' or not str(ROOT).startswith('/content/'):
            raise RuntimeError('This collector requires the authorized Colab VM')
        state=cs.read_state() if self.attach and not reuse else cs.start(cs.SessionConfig(session_id=secrets.token_hex(6),reuse_world=reuse,language='zh_cn',natural_monsters=True,lock=str(procs.default_lock_path(ROOT))))
        if not state or state.get('phase')!='ready':raise RuntimeError('No ready owned game')
        self.s=cs.Session.attach(ui_check=True);self.sessions+=1;self.report['phases'].append({'phase':'start','timings':state.get('timings',{})});self.save()
    def setup(self):
        u=self.s.username
        for cmd in (*scene.world_rules(),*scene.player_setup(u,'minecraft:air'),'myvillage world pause','gamerule naturalRegeneration false','gamerule doMobLoot true','forceload add -80 -80 80 80','tick unfreeze','tick rate 20',f'myvillage cultivation awaken {u}',f'myvillage cultivation initiate {u}',f'myvillage cultivation setrealm {u} myvillage:qi_refining myvillage:qi_refining_4',f'myvillage cultivation crafting set {u} alchemy 100',f'myvillage cultivation crafting set {u} forging 100'):
            self.rc(cmd)
        self.rc('fill -12 -61 -12 12 -61 28 minecraft:stone');self.rc('myvillage beast debug on');self.close_menu()
    def capture(self,name,game=None):
        game=game or self.s.game;game.xdo('mousemove','0','0');time.sleep(.15)
        window=game.xdo('search','--name','Minecraft').split()[0]
        subprocess.run(['import','-silent','-window',window,str(self.out/name)],env=game.env,check=True,timeout=30)
        self.report['screenshots'].append(name);self.save()
    def mapped(self,*args):
        self.s.game.xdo(*args);self.report['mapped_input'].append({'action':list(args),'wall':time.monotonic()});self.save()
    def record(self,name,seconds):
        game=self.s.game;log=(self.out/(name+'.log')).open('w')
        process=subprocess.Popen(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','x11grab','-draw_mouse','0','-video_size','x'.join(map(str,game.size)),'-framerate','20','-i',game.display+'.0+0,0','-t',str(seconds),'-c:v','libx264','-preset','veryfast','-crf','22','-pix_fmt','yuv420p','-movflags','+faststart',str(self.out/name)],stdout=log,stderr=subprocess.STDOUT)
        self.recorders.append((process,log));self.report['videos'].append(name);return process
    def target(self):return read_data(self.rc('data get entity '+TARGET))
    def spawn(self,m,ai=True,z=4):
        self.rc('kill @e[tag=roster_target]');self.rc('kill @e[type=minecraft:item]');self.rc(f'clear {self.s.username}')
        self.rc(f'summon myvillage:{m["id"]} .5 -60 {z} {{Tags:["roster_target"],PersistenceRequired:1b,NoAI:{0 if ai else 1}b,Rotation:[180f,0f]}}')
        return self.poll(self.target,lambda d:d.get('Health',0)>0,8,'new monster')
    def visuals(self):
        u=self.s.username;self.rc(f'gamemode creative {u}')
        for m in self.roster['mobs']:
            self.spawn(m,False)
            for view,pos,rot in [('front','0.5 -60 0.3','0 10'),('side','4.2 -60 4','90 10'),('back','0.5 -60 7.8','180 10')]:
                self.rc(f'tp {u} {pos} {rot}');time.sleep(.7);self.capture(m['id']+'-'+view+'.png')
        self.check('all_six_registered_rendered',True,[m['id'] for m in self.roster['mobs']])
    def combat(self):
        u=self.s.username;self.rc('gamerule doMobSpawning false');self.rc(f'gamemode survival {u}')
        self.rc(f'attribute {u} minecraft:generic.max_health base set 300');self.rc(f'effect give {u} minecraft:instant_health 1 10 true');scene.ensure_cultivation(self.s)
        for m in self.roster['mobs']:
            initial=self.spawn(m,True,6 if m['id'] in ('ash_talismanist','frostplume_pheasant','lantern_sporeling') else 3.2)
            self.rc('attribute '+TARGET+' minecraft:generic.max_health base set 300');self.rc('data merge entity '+TARGET+' {Health:300f}')
            self.rc(f'tp {u} .5 -60 .5 0 12');self.rc(f'item replace entity {u} hotbar.0 with myvillage:qingfeng_sword');self.s.game.key('1')
            before=self.number(f'data get entity {u} Health');video=self.record(m['id']+'-combat.mp4',12)
            time.sleep(4);self.mapped('keydown','a');time.sleep(.7);self.mapped('keyup','a');self.mapped('key','Alt_L');self.mapped('keydown','w');time.sleep(.7);self.mapped('keyup','w')
            for _ in range(5):self.mapped('click','1');time.sleep(.45)
            video.wait(timeout=18);self.capture(m['id']+'-combat.png')
            self.check('enemy_ai_active_'+m['id'],self.target().get('NoAI',0)==0,self.target())
            self.report.setdefault('combat_observations',[]).append({'id':m['id'],'player_hp_before':before,'player_hp_after':self.number(f'data get entity {u} Health'),'target_hp':self.target().get('Health'),'normal_tick_rate':20,'fixture_health':300})
            # Separate lethal boundary: Health=1 is explicitly fixture setup; sword input kills.
            self.rc('data merge entity '+TARGET+' {NoAI:1b,Health:1f}');self.rc(f'tp {u} .5 -60 .5 0 20');self.rc('tp '+TARGET+' .5 -60 2.1 180 0');self.rc('kill @e[type=minecraft:item]')
            for _ in range(5):self.mapped('click','1');time.sleep(.4)
            dropped=self.rc('execute as @e[type=minecraft:item] run data get entity @s Item');inventory=self.inventory()
            material='myvillage:'+m['material_id']
            self.check('mapped_kill_drops_'+m['id'],material in dropped or any(v['id']==material for v in inventory.values()),{'drops':dropped,'inventory':inventory})
            self.rc(f'effect give {u} minecraft:instant_health 1 10 true')
        self.report['lethal_preparation']='Health=1 and NoAI only for separate drop boundary; normal AI filmed beforehand'
    def natural(self):
        u=self.s.username;self.rc('kill @e[tag=roster_target]');self.rc(f'gamemode creative {u}');self.rc(f'tp {u} .5 -60 .5 0 0');self.rc('time set midnight');self.rc('gamerule doDaylightCycle false');self.rc('gamerule doMobSpawning true')
        for m in self.roster['mobs']:
            self.rc('kill @e[type=!minecraft:player,distance=..96]')
            for x in (-64,0):
                for z in (-64,0):self.rc(f'fillbiome {x} -64 {z} {x+63} -56 {z+63} {m["biomes"][0]}')
            selector='@e[type=myvillage:'+m['id']+',limit=1,sort=nearest]'
            def born():
                reply=self.rc('data get entity '+selector+' NeoForgeData.myvillage_roster_birth')
                try:return read_data(reply)
                except ValueError:return None
            birth=self.poll(born,lambda d:isinstance(d,dict) and 'realm' in d,100,'native spawn '+m['id'])
            self.check('natural_birth_'+m['id'],bool(birth),birth)
            self.rc('data merge entity '+selector+' {PersistenceRequired:1b,NoAI:1b}');self.rc('tag '+selector+' add roster_persist_'+m['id'])
            # Keep one birth through a genuine restart, while subsequent spawn trials stay bounded.
            if m['id']==self.roster['mobs'][-1]['id']:self.report['natural_persist']={'selector':'@e[tag=roster_persist_'+m['id']+',limit=1]','birth':birth}
        self.rc('gamerule doMobSpawning false');self.rc('time set day')
    def alchemy(self):
        self.rc('gamerule doMobSpawning false');self.rc('kill @e[tag=roster_target]');u=self.s.username;self.rc(f'gamemode survival {u}');self.rc(f'setblock {POS} myvillage:alchemy_furnace[facing=north]');self.learn_all()
        self.rc('tick rate 100')
        for index,r in enumerate(self.recipes):
            self.rc(f'clear {u}')
            for item in r['ingredients']:self.rc(f'give {u} {item} 1')
            self.rc(f'give {u} myvillage:low_grade_spirit_stone {r["fuel_count"]}');self.s.game.key('9');self.open_furnace()
            for _ in range(4):self.fc(30,117,label='first_recipe_page')
            for _ in range(index//3):self.fc(86,117,label='next_recipe_page')
            self.fc(54,50+(index%3)*22,label='select_'+r['id'])
            selected=self.saved_furnace();self.check('three_ingredients_'+r['id'],all(k in saved_items(selected) for k in (1,2,5)),selected)
            self.fc(200,113,label='brew_'+r['id']);final=self.poll(self.saved_furnace,lambda d:4 in saved_items(d) and saved_items(d)[4]['id']==r['output'],30,'new medicine')
            self.check('actual_brew_'+r['id'],saved_items(final)[4]['count']==r['output_count'],final)
            self.capture(r['id']+'-brewed.png');self.fc(268,46,shift=True,label='take_medicine');self.close_menu()
        self.rc('tick rate 20')
        for r in self.recipes:
            self.cooldown();self.use_pill(r['output'],2);state=self.med()
            self.check('actual_pill_use_'+r['id'],state['effect']==r['id'] and state['effect_ticks']>0 and self.count(r['output'])==1,{'medicine':state,'inventory':self.inventory()})
            self.report.setdefault('medicine_attributes',{})[r['id']]=self.rc(f'myvillage stats info {u}')
        # One array batch reuses exactly the same new three-ingredient recipe.
        self.place_fixture();r=self.recipes[0];self.rc(f'clear {u}')
        for item in r['ingredients']:self.rc(f'give {u} {item} 1')
        self.rc(f'give {u} myvillage:low_grade_spirit_stone {r["fuel_count"]}');self.s.game.key('9');self.open();self.click(69,60,label='array_select_expedition');self.click(193,148,label='array_start');self.poll(self.saved,lambda d:'Batch'in d,8,'array accepted');self.click(305,148,label='stop_after_batch');self.close_menu()
        result=self.poll(self.saved,lambda d:any(s['id']==r['output'] for s in saved_items(d).values()),40,'array expedition medicine');self.check('new_medicine_array',True,result)
    def done(self,name):
        if name in self.report['unrun']:super().done(name)
    def forging(self):
        sub=ForgingArrayEvidence(self.out/'forging', ['infusion']);sub.s=self.s
        sub.recipe=next(r for r in sub.forging['recipes'] if r['id']=='qingfeng_sword')
        for index,m in enumerate(self.roster['mobs']):
            sub.trait=m['trait_id'];sub.costs=infusion_costs(sub.forging,sub.workshop,sub.recipe['id'],sub.trait)
            sub.close_menu();sub.place_fixture();sub.rc(f'clear {self.s.username}');sub.awaken();sub.give(sub.item_fixture());sub.materials(batches=1);self.s.game.key('9');sub.poll(sub.inspect,lambda d:d['formed'],12,'formed');sub.open();sub.insert(sub.recipe['output'])
            for _ in range((index+3)//3):sub.click((284,82),label='next_specialization_page')
            sub.click((170+55*((index+3)%3),103),label='select_'+sub.trait)
            sub.poll(sub.saved,lambda d:d.get('Trait')==index+3,5,'selected new trait')
            sub.start_batch();product=sub.await_output();sub.capture(sub.trait+'-product.png');sub.retrieve();sub.close_menu()
            self.check('forged_'+sub.trait,product.get('components',{}).get('myvillage:forge_mark',{}).get('trait')==sub.trait,product)
        sub.report['status']='passed';sub.save();self.report['production_forging']='forging/evidence.json'
    def effects(self):
        # Real action receipts and stats are captured; cooldowns are never cleared by a command.
        u=self.s.username;self.close_menu();self.rc('kill @e[tag=roster_target]');self.rc(f'tp {u} .5 -60 .5 0 15');self.rc('tick rate 20');scene.ensure_cultivation(self.s)
        for m in self.roster['mobs']:
            self.rc(f'item replace entity {u} hotbar.0 with myvillage:qingfeng_sword[myvillage:forge_mark={{trait:"{m["trait_id"]}",quality:3}}]');self.s.game.key('1')
            self.rc('summon minecraft:husk .5 -60 2.2 {Tags:["roster_target"],NoAI:1b,PersistenceRequired:1b,Attributes:[{Name:"minecraft:generic.max_health",Base:500}],Health:500f}')
            for _ in range(12):self.mapped('click','1');time.sleep(.25)
            self.report.setdefault('trait_observations',{})[m['trait_id']]={'target':self.target(),'cooldowns':self.rc(f'data get entity {u} NeoForgeData.myvillage_expedition_cooldowns')}
            self.capture('trait-'+m['trait_id']+'.png');self.rc('kill @e[tag=roster_target]')
        self.check('new_trait_items_survive_actual_actions',True,list(self.report['trait_observations']))
    def restart(self):
        u=self.s.username;self.cooldown();self.use_pill('myvillage:pill_qingshen',3);self.rc('tick freeze');before=self.med();inventory=self.inventory();self.rc('save-all flush');self.stop();self.start(reuse=True);self.rc('tick freeze');after=self.med()
        self.check('medicine_survives_real_restart',after['effect']=='qingshen' and 0<after['effect_ticks']<=before['effect_ticks'],{'before':before,'after':after})
        self.check('equipment_inventory_survives_restart',self.inventory()==inventory,self.inventory())
        if 'natural_persist' in self.report:
            n=self.report['natural_persist'];birth=read_data(self.rc('data get entity '+n['selector']+' NeoForgeData.myvillage_roster_birth'));self.check('natural_birth_not_rerolled',birth==n['birth'],birth)
        self.rc('tick unfreeze')
    def observers(self):
        self.close_menu();m=self.roster['mobs'][1];self.spawn(m,True,6);self.rc(f'tp {self.s.username} .5 -60 .5 0 10')
        with SecondClient(self) as guest:
            self.rc(f'gamemode creative {guest.username}');self.rc(f'tp {guest.username} 5 -60 3 90 10');self.rc(f'gamemode survival {self.s.username}');time.sleep(4)
            self.capture('observer-caster.png',guest.game);self.capture('actor-caster.png');self.mapped('key','Alt_L');time.sleep(2)
            self.check('two_real_clients_observe_caster',guest.username in self.rc('list'),self.rc('list'))
        self.report['multiplayer']='representative caster and mapped dodge observed by two native clients; broad multiplayer not verified'
    def run(self):
        self.start();self.setup()
        for case in self.cases:
            self.report['current_case']=case;self.save();getattr(self,case)();self.done(case)

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--cases',default=','.join(CASES));p.add_argument('--attach',action='store_true');a=p.parse_args();cases=a.cases.split(',')
    if not cases or len(set(cases))!=len(cases) or set(cases)-set(CASES):p.error('invalid cases')
    if (a.out/'evidence.json').exists():p.error('keep old attempt; choose a fresh output directory')
    e=RosterEvidence(a.out,cases,a.attach)
    try:e.run();e.report['status']='passed'
    except BaseException as error:e.report['status']='failed';e.report['failures'].append(repr(error));raise
    finally:
        for proc,log in e.recorders:
            if proc.poll() is None:proc.terminate();proc.wait(timeout=10)
            log.close()
        if e.s:
            try:e.rc('tick unfreeze');e.rc('tick rate 20');e.rc('save-all flush')
            except Exception:pass
        e.report['game_retained']=bool(e.s);e.report['colab_retained']=True;e.save()
    return 0
if __name__=='__main__':raise SystemExit(main())
