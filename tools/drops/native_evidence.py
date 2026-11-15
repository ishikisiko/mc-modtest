"""独立服务器的动态掉落检查。通过死亡回调夹具观察真实事件与落地物品，不声称玩家输入验收。"""
from __future__ import annotations
import argparse,copy,csv,fcntl,hashlib,json,os,re,shutil,subprocess,sys,time,traceback
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT),str(ROOT/'tools')]
from tools.qianji.server import Server as BaseServer
from tools.roads_smoke.run import verify_candidate
from tools.roadweaver_probe.run import write

CASES=['candidate_identity','fresh_config_and_export','bundled_ignores_external_config','external_override_toggle','invalid_settings_keeps_snapshot','external_override_restart','bundled_restart','query_and_inspect','inherit','append','replace','disabled','live_existing_entity','invalid_config_keeps_snapshot','permission_gate','quest_default','sect_default','aptitude_default','scene_override_can_change','region_realm_conditions','looting_and_probability','beast_fire_components','item_variants','invalid_item_variants','captured_fire_suppressed','stale_habitat_rejected','habitat_default','habitat_live_override','restart_preserves_custom','restore_defaults_and_export']
class Server(BaseServer):
    def start(self):
        # Existing lifecycle owns locking, startup, RCON and graceful release. Test-only mod is separate.
        super().start()
        self.command('difficulty normal')

class Evidence:
    def __init__(self,out,jar,probe,seed,world_fixture=None,only=None):
        self.out=out;out.mkdir(parents=True,exist_ok=False)
        self.jar=jar;self.probe=probe
        self.game=ROOT/'run-dynamic-drops'/out.name
        if self.game.exists():raise ValueError('每次初始检查使用独立新世界')
        self.game.mkdir(parents=True);(self.game/'mods').mkdir()
        if world_fixture is not None:
            world_fixture=world_fixture.resolve()
            if not world_fixture.is_relative_to(ROOT/'run-dynamic-drops') or not (world_fixture/'level.dat').is_file():raise ValueError('仅复用本采集器的自有测试世界')
            with (world_fixture/'session.lock').open('r+b') as lock:
                fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
                shutil.copytree(world_fixture,self.game/'world')
        shutil.copyfile(probe,self.game/'mods'/probe.name)
        self.server=Server(out,self.game,seed);self.server.startup_timeout=1800
        self.path=self.game/'config/yuanxian/monster_drops.json'
        self.settings=self.path.with_name('monster_drops_settings.json')
        self.defaults=json.loads((ROOT/'src/main/resources/data/yuanxian/drops/monster_drops.json').read_text())
        self.selected=CASES.copy() if only is None else [v for v in CASES if v=='candidate_identity' or v in only]
        self.report=dict(kind='dynamic_drops_native',status='running',selected=self.selected,completed=[],failed=[],unrun=self.selected.copy(),checks=[],commands=[],world=str(self.game),seed=seed,evidence='accepted_death_callback_fixture_and_native_item_entities',mapped_combat='not_verified',owner_feel='not_verified',owner_visual='not_verified',owner_fps='not_verified',collector_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
        self.current=CASES[0];self.save()
        if world_fixture is not None:self.report['world_fixture']=str(world_fixture);self.save()
    def save(self):write(self.out/'report.json',self.report)
    def rc(self,command):
        value=self.server.command(command);self.report['commands'].append(dict(command=command,response=value));self.save();return value
    def value(self,command,prefix):
        answer=self.rc(command)
        if prefix not in answer:raise AssertionError(answer)
        return json.JSONDecoder().raw_decode(answer.split(prefix,1)[1].lstrip())[0]
    def check(self,name,passed,detail=None):
        self.report['checks'].append(dict(name=name,passed=bool(passed),detail=detail));self.save()
        if not passed:raise AssertionError(name+': '+repr(detail))
    def case(self,name,fn):
        if name not in self.selected:return
        self.current=name;self.report['current']=name;self.save();fn();self.report['completed'].append(name);self.report['unrun']=[v for v in self.selected if v not in self.report['completed']];self.save()
    def status(self):return self.value('yuanxian drops status','DROPS_STATUS ')
    def config(self,data):
        self.path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
        self.settings.write_text(json.dumps({'use_external_config':True})+'\n')
        answer=self.rc('yuanxian drops reload');self.check('reload_accepted','掉落配置已生效' in answer,answer)
    def bundled(self):
        self.settings.write_text(json.dumps({'use_external_config':False})+'\n')
        answer=self.rc('yuanxian drops reload');self.check('bundled_reload_accepted','掉落配置已生效' in answer and self.status()['source']=='bundled',answer)
    def entity_policy(self,mode,drops=None):
        data=copy.deepcopy(self.defaults);data['entities']['yuanxian:stele_guardian']={'mode':mode,'drops':drops or []};return data
    @staticmethod
    def entry(item='minecraft:diamond',count=2,chance=1,**kw):return dict(item=item,min=count,max=count,chance=chance,**kw)
    def spawn(self,entity='yuanxian:stele_guardian',scene='normal',stage=8):
        self.value(f'drop_probe spawn {entity} {scene} {stage}','DROP_PROBE ')
        deadline=time.monotonic()+30
        while time.monotonic()<deadline:
            value=self.value('drop_probe ready','DROP_PROBE ')
            if value.get('target_loaded') and value.get('chunk_entity_ticking'):
                # A restarted probe has no in-memory previous area; saved item entities still exist.
                self.rc('kill @e[type=minecraft:item]')
                cleared=self.value('drop_probe read','DROP_PROBE ')
                self.check('empty_item_fixture_before_death',cleared.get('world_items')=={},cleared)
                return value
            time.sleep(.1)
        raise TimeoutError('夹具区块尚未进入实体运行状态')
    def finish(self,looting=0,captured=False):
        self.value(f'drop_probe finish {looting} {str(captured).lower()}','DROP_PROBE ')
        value=self.value('drop_probe read','DROP_PROBE ')
        self.check('real_event_and_item_entities',value.get('event_items')==value.get('world_items') and 'event_items' in value,value)
        return value
    def kill(self,entity='yuanxian:stele_guardian',scene='normal',stage=8,looting=0,captured=False):self.spawn(entity,scene,stage);return self.finish(looting,captured)
    def items(self,want,**kw):
        value=self.kill(**kw);self.check('expected_items',value['world_items']==want,value);return value
    def run(self):
        try:
            self.case('candidate_identity',lambda:self.identity())
            self.server.start()
            self.rc('kill @e[type=minecraft:item]')
            self.case('fresh_config_and_export',self.initial)
            self.case('bundled_ignores_external_config',self.ignored)
            self.case('external_override_toggle',self.toggle)
            self.case('invalid_settings_keeps_snapshot',self.invalid_settings)
            self.case('external_override_restart',self.external_restart)
            self.case('bundled_restart',self.bundled_restart)
            self.case('query_and_inspect',self.query)
            self.case('inherit',lambda:self.items({'yuanxian:stele_stone_heart':1}))
            self.case('append',lambda:self.mode('append',{'yuanxian:stele_stone_heart':1,'minecraft:diamond':2}))
            self.case('replace',lambda:self.mode('replace',{'minecraft:diamond':2}))
            self.case('disabled',lambda:self.mode('disabled',{}))
            self.case('live_existing_entity',self.live)
            self.case('invalid_config_keeps_snapshot',self.invalid)
            self.case('permission_gate',self.permission)
            for case,scene in [('quest_default','quest_realm'),('sect_default','sect_trial'),('aptitude_default','aptitude_trial')]:
                self.case(case,lambda scene=scene:self.default_scene(scene))
            self.case('scene_override_can_change',self.scene_change)
            self.case('region_realm_conditions',self.conditions)
            self.case('looting_and_probability',self.looting)
            self.case('beast_fire_components',self.fire)
            self.case('item_variants',self.item_variants)
            self.case('invalid_item_variants',self.invalid_item_variants)
            self.case('captured_fire_suppressed',lambda:self.items({},entity='minecraft:blaze',captured=True))
            self.case('stale_habitat_rejected',self.stale)
            self.case('habitat_default',self.habitat)
            self.case('habitat_live_override',self.habitat_change)
            self.case('restart_preserves_custom',self.restart)
            self.case('restore_defaults_and_export',self.restore)
            self.report['status']='passed'
        except BaseException as error:
            self.report['failed'].append(dict(case=self.current,error=repr(error),traceback=traceback.format_exc()));self.report['status']='failed'
            self.report['unrun']=[v for v in self.selected if v not in self.report['completed'] and v!=self.current]
            raise
        finally:
            try:self.server.stop()
            finally:self.save()
    def identity(self):
        self.report['candidate']=verify_candidate(self.jar);self.report['probe_sha256']=hashlib.sha256(self.probe.read_bytes()).hexdigest()
        self.report['source']=json.loads((ROOT/'candidate.json').read_text());self.save()
    def initial(self):
        self.check('first_start_defaults_to_bundled',json.loads(self.settings.read_text())=={'use_external_config':False} and not self.path.exists())
        self.export('defaults');self.check('initial_snapshot',self.status()['revision']==1 and self.status()['source']=='bundled')
    def ignored(self):
        self.path.write_text(json.dumps(self.entity_policy('replace',[self.entry()])))
        original=self.path.read_bytes();self.bundled();self.items({'yuanxian:stele_stone_heart':1})
        self.check('disabled_override_preserved',self.path.read_bytes()==original)
        self.path.write_text('{unused invalid external config');self.bundled();self.items({'yuanxian:stele_stone_heart':1})
    def toggle(self):
        self.spawn();self.config(self.entity_policy('replace',[self.entry()]));self.check('external_source',self.status()['source']=='external')
        value=self.finish();self.check('enable_affects_existing_entity',value['world_items']=={'minecraft:diamond':2},value)
        rows=self.export('external','yuanxian:thornback_badger','yuanxian:thorn_bone')
        replaced=next(row for row in rows if row['实体ID']=='yuanxian:stele_guardian' and row['场景']=='normal')
        entries=json.loads(replaced['配置掉落'])
        self.check('csv_replacement_has_only_configured_drops',replaced['模式']=='replace' and not replaced['原始战利品表ID'] and not replaced['继承战利品表'] and len(entries)==1 and entries[0]['item']=='minecraft:diamond' and entries[0]['min']==entries[0]['max']==2,replaced)
        original=self.path.read_bytes();self.spawn();self.bundled()
        value=self.finish();self.check('disable_affects_existing_entity',value['world_items']=={'yuanxian:stele_stone_heart':1},value)
        self.check('toggle_preserves_custom_file',self.path.read_bytes()==original)
    def invalid_settings(self):
        self.config(self.entity_policy('replace',[self.entry()]));before=self.status()
        for raw in ['{','{"use_external_config":"false"}','{"use_external_config":false,"extra":true}','{"use_external_config":false,"use_external_config":true}']:
            self.settings.write_text(raw);reply=self.rc('yuanxian drops reload');after=self.status()
            self.check('invalid_settings_keep_source_and_snapshot','重载失败' in reply and all(before[k]==after[k] for k in ['revision','sha256','source']),reply)
            self.items({'minecraft:diamond':2})
        self.settings.write_text(json.dumps({'use_external_config':True})+'\n')
    def external_restart(self):
        self.config(self.entity_policy('replace',[self.entry()]));original=self.path.read_bytes();before=self.status();self.server.stop();self.server.start()
        after=self.status();self.check('restart_retains_explicit_override',after['source']=='external' and after['sha256']==before['sha256'] and self.path.read_bytes()==original)
        self.items({'minecraft:diamond':2})
    def bundled_restart(self):
        self.bundled();self.path.write_text('{old unused config');original=self.path.read_bytes();before=self.status();self.server.stop();self.server.start()
        after=self.status();self.check('restart_uses_current_bundle',after['source']=='bundled' and after['sha256']==before['sha256'] and not after['last_error'] and self.path.read_bytes()==original)
        self.items({'yuanxian:stele_stone_heart':1})
    def export(self,label,inherited_entity='yuanxian:stele_guardian',inherited_item='yuanxian:stele_stone_heart'):
        reply=self.rc('yuanxian drops export');self.check('export_command','已导出' in reply,reply)
        result=json.loads(self.path.with_name('monster_drops_effective.json').read_text());status=self.status()
        self.check('export_snapshot_identity',result['revision']==status['revision'] and result['sha256']==status['sha256'] and result['source']==status['source'] and all(row['source']==status['source'] for row in result['rows']))
        self.check('export_all_scenes',len(result['rows'])>=71*6 and len({r['context']['scene'] for r in result['rows']})==6)
        self.check('csv_bom',self.path.with_name('monster_drops_effective.csv').read_bytes().startswith(b'\xef\xbb\xbf'))
        with self.path.with_name('monster_drops_effective.csv').open(encoding='utf-8-sig',newline='') as stream:csv_rows=list(csv.DictReader(stream))
        inherited=next(row for row in csv_rows if row['实体ID']==inherited_entity and row['场景']=='normal')
        self.check('csv_includes_native_drop_expression',inherited['模式'] in ('inherit','append') and inherited_item in inherited['继承战利品表'],inherited)
        for suffix in ['json','csv']:shutil.copyfile(self.path.with_name('monster_drops_effective.'+suffix),self.out/f'{label}-effective.{suffix}')
        return csv_rows
    def query(self):
        born=self.spawn();v=self.value('yuanxian drops inspect '+born['uuid'],'DROPS ')
        self.check('inspect_live_realm',v['context']['realmRank'] is not None and v['effective']['origin']=='entities.yuanxian:stele_guardian',v)
        v=self.value('yuanxian drops show yuanxian:stele_guardian habitat donghuang','DROPS ')
        self.check('show_scene',v['effective']['origin']=='overrides.donghuang_habitat',v)
        self.finish()
    def mode(self,mode,want):
        self.config(self.entity_policy(mode,[] if mode=='disabled' else [self.entry()]));self.items(want)
    def live(self):
        self.spawn();self.config(self.entity_policy('replace',[self.entry('minecraft:emerald',3)]));v=self.finish();self.check('preexisting_mob_new_rule',v['world_items']=={'minecraft:emerald':3},v)
    def invalid(self):
        before=self.status();valid=self.path.read_bytes()
        for raw in ['{','{"schema":1,"schema":1}',json.dumps(self.entity_policy('replace',[self.entry('minecraft:missing_drop_item')]))]:
            self.path.write_text(raw);reply=self.rc('yuanxian drops reload');after=self.status()
            self.check('rejected_keeps_snapshot','重载失败' in reply and before['revision']==after['revision'] and before['sha256']==after['sha256'],reply)
            self.items({'minecraft:emerald':3})
        self.path.write_bytes(valid)
    def permission(self):
        value=self.value('drop_probe deny','DROP_PROBE ');self.check('non_admin_denied',value.get('permission_zero_denied') and value.get('revision_unchanged'),value)
    def default_scene(self,scene):self.config(self.defaults);self.items({},scene=scene)
    def scene_change(self):
        data=copy.deepcopy(self.defaults);data['overrides'][2].update(mode='replace',drops=[self.entry()]);self.config(data);self.items({'minecraft:diamond':2},scene='quest_realm')
    def conditions(self):
        data=self.entity_policy('disabled');born=self.spawn();region=born['spawn_context']['region'];self.finish()
        self.check('real_donghuang_context',region=='donghuang',born)
        data['overrides'].insert(0,dict(id='qi9_in_donghuang',when=dict(entities=['yuanxian:stele_guardian'],scenes=['normal'],regions=[region],min_realm=dict(realm='yuanxian:qi_refining',stage=8),max_realm=dict(realm='yuanxian:qi_refining',stage=8)),mode='replace',drops=[self.entry()]))
        self.config(data);self.items({'minecraft:diamond':2},stage=8);self.items({},stage=7)
        v=self.value('yuanxian drops show yuanxian:stele_guardian normal beiyuan yuanxian:qi_refining 8','DROPS ')
        self.check('other_region_not_matched',v['effective']['origin']=='entities.yuanxian:stele_guardian',v)
    def looting(self):
        entries=[self.entry(count=2,looting=dict(min_per_level=1,max_per_level=1,limit=4)),self.entry('minecraft:emerald',64,0)]
        self.config(self.entity_policy('replace',entries));self.items({'minecraft:diamond':4},looting=3)
    def fire(self):
        data=copy.deepcopy(self.defaults);data['entities']['minecraft:blaze']={'mode':'replace','drops':[]};data['additions'][0]['drops'][0]['chance']=1
        self.config(data);value=self.items({'yuanxian:beast_fire_seed':1},entity='minecraft:blaze')
        detail=value['world_item_details'][0]['components'];fire=detail.get('components',{}).get('yuanxian:fire_seed',{})
        self.check('formal_fire_components',fire.get('kind')=='beast' and fire.get('maturity')==0 and fire.get('dimension')=='minecraft:overworld' and isinstance(fire.get('origin'),int),detail)
    def item_variants(self):
        self.value('drop_probe variants','DROP_PROBE ')
        options=json.loads((self.game/'drop-variant-catalogue.json').read_text())
        expected=json.loads((ROOT/'drop-workbench-catalogue.json').read_text())['items']
        mismatches=[]
        for row in expected:
            actual=options.get(row['id'],{})
            for key in ['qualities','rarities','quality_kind']:
                if actual.get(key)!=row.get(key):mismatches.append({'item':row['id'],'field':key,'expected':row.get(key),'actual':actual.get(key)})
        self.check('all_workbench_variant_capabilities_match_native_registry',not mismatches,{'checked_items':len(expected),'mismatches':mismatches})
        entries=[self.entry('yuanxian:pill_huiling',17,quality=4),self.entry('yuanxian:pill_huiling',1,quality=1),self.entry('yuanxian:pill_zhuji',1,quality=4),self.entry('yuanxian:qingfeng_sword',1,quality=2),self.entry('yuanxian:xuantie_helmet',1,quality=3),self.entry('yuanxian:forged_pickaxe',1,quality=1),self.entry('yuanxian:forged_hoe',1,quality=0),self.entry('minecraft:diamond_sword',1,rarity='rare'),self.entry('yuanxian:huashen_dari_helmet',1,rarity='epic')]
        self.config(self.entity_policy('replace',entries))
        value=self.kill();details=value['world_item_details']
        self.check('all_variant_drop_quantities',value['world_items']=={'yuanxian:pill_huiling':18,'yuanxian:pill_zhuji':1,'yuanxian:qingfeng_sword':1,'yuanxian:xuantie_helmet':1,'yuanxian:forged_pickaxe':1,'yuanxian:forged_hoe':1,'minecraft:diamond_sword':1,'yuanxian:huashen_dari_helmet':1},value)
        def components(row):return row['components'].get('components',{})
        huiling=[r for r in details if r['item']=='yuanxian:pill_huiling']
        self.check('pill_split_stacks_keep_quality',sorted((r['count'],components(r).get('yuanxian:pill_quality',1)) for r in huiling)==[(1,1),(1,4),(16,4)],huiling)
        for id,q in [('yuanxian:pill_zhuji',4),('yuanxian:qingfeng_sword',2),('yuanxian:xuantie_helmet',3),('yuanxian:forged_pickaxe',1)]:
            row=next(r for r in details if r['item']==id);c=components(row)
            self.check('drop_component_'+id,c.get('yuanxian:pill_quality')==q if 'pill_' in id else c.get('yuanxian:forge_mark')=={'trait':'','quality':q},row)
        ordinary=next(r for r in details if r['item']=='yuanxian:forged_hoe')
        self.check('ordinary_tool_is_present_without_forge_mark','yuanxian:forge_mark' not in components(ordinary),ordinary)
        for id,rarity in [('minecraft:diamond_sword','rare'),('yuanxian:huashen_dari_helmet','epic')]:
            row=next(r for r in details if r['item']==id);self.check('drop_native_rarity_'+id,components(row).get('minecraft:rarity')==rarity,row)
        state=self.status();self.check('query_exports_quality_and_rarity',state['source']=='external')
        shown=self.value('yuanxian drops show yuanxian:stele_guardian normal','DROPS ')
        self.check('query_keeps_variant_fields',shown['effective']['policy']['drops'][0]['quality']==4 and shown['effective']['policy']['drops'][-1]['rarity']=='epic',shown['effective'])
    def invalid_item_variants(self):
        before=self.status()
        for item,field,value in [('yuanxian:pill_huiling','quality',0),('yuanxian:pill_huiling','quality',5),('minecraft:diamond','quality',4),('yuanxian:qingfeng_sword','rarity','epic'),('minecraft:diamond_sword','rarity','legendary')]:
            data=self.entity_policy('replace',[self.entry(item,1,**{field:value})]);self.path.write_text(json.dumps(data));answer=self.rc('yuanxian drops reload')
            self.check('invalid_variant_keeps_active_snapshot','重载失败' in answer and self.status()['sha256']==before['sha256'],{'item':item,'field':field,'value':value,'response':answer})
    def stale(self):
        data=copy.deepcopy(self.defaults);data['overrides']=[dict(id='any_habitat',when=dict(scenes=['habitat']),mode='replace',drops=[self.entry()])];self.config(data);self.items({},scene='stale_habitat')
    def habitat(self):
        self.config(self.defaults);v=self.kill('yuanxian:demon_wolf','habitat');items=v['world_items']
        self.check('real_habitat_receipt_and_default',v['death_context']['scene']=='HABITAT' and set(items)=={'yuanxian:low_grade_spirit_stone'} and 1<=items['yuanxian:low_grade_spirit_stone']<=3,v)
    def habitat_change(self):
        self.spawn('yuanxian:demon_wolf','habitat');data=copy.deepcopy(self.defaults);data['overrides'][3]['drops']=[self.entry('yuanxian:low_grade_spirit_stone',7)];self.config(data)
        v=self.finish();self.check('valid_habitat_new_loot',v['world_items']=={'yuanxian:low_grade_spirit_stone':7},v)
    def restart(self):
        raw=self.path.read_bytes();sha=self.status()['sha256'];self.server.stop();self.server.start()
        self.check('restart_keeps_external_file',self.path.read_bytes()==raw and self.status()['sha256']==sha)
        v=self.value('yuanxian drops show yuanxian:demon_wolf habitat donghuang','DROPS ')
        self.check('restart_custom_rule_live',v['effective']['policy']['drops'][0]['min']==7,v)
    def restore(self):
        self.config(self.defaults);self.bundled();self.export('restored-defaults');self.check('restored_configuration',json.loads(self.path.read_text())==self.defaults and self.status()['source']=='bundled')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--probe',type=Path,required=True);parser.add_argument('--seed',type=int,default=202611150133);parser.add_argument('--world-fixture',type=Path);parser.add_argument('--only',nargs='+',choices=CASES)
    args=parser.parse_args();Evidence(args.out.resolve(),args.jar.resolve(),args.probe.resolve(),args.seed,args.world_fixture,args.only).run()
if __name__=='__main__':main()
