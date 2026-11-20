from __future__ import annotations
import copy,json,tempfile,threading,unittest,urllib.error,urllib.request
from pathlib import Path
from tools.drop_workbench.rules import Invalid,decode,validate
from tools.drop_workbench.server import App,Conflict,SOURCE,Store,ORIGIN
ROOT=Path(__file__).resolve().parents[2]
class WorkbenchTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.file=self.root/SOURCE;self.file.parent.mkdir(parents=True);self.original=(ROOT/'src/test/resources/drops/rules_fixture.json').read_bytes();self.file.write_bytes(self.original);self.data=decode(self.original)
        self.catalogue={'items':[{'id':id} for id in ['minecraft:diamond','minecraft:emerald','yuanxian:low_grade_spirit_stone','yuanxian:beast_fire_seed']],'entities':[{'id':id} for id in self.data['entities']],'regions':[{'id':id} for id in ['donghuang','lingyue','beiyuan','donghai','ximo','nanjiang','moyu','zhongzhou']],'realms':json.loads((ROOT/'src/main/resources/data/yuanxian/stats/rules.json').read_text())['realms']}
        from tools.drop_workbench.variants import attach
        self.catalogue['items'] += [{'id':id} for id in ['yuanxian:pill_huiling','yuanxian:pill_zhuji','yuanxian:qingfeng_sword','yuanxian:xuantie_helmet','yuanxian:forged_pickaxe','yuanxian:guiyan_sword','yuanxian:zhenyue_ruler','yuanxian:huashen_dari_helmet']]
        attach(ROOT,self.catalogue['items'],{})
        self.store=Store(self.root,self.catalogue,self.root/'state')
    def tearDown(self):self.tmp.cleanup()
    def change(self):
        data=copy.deepcopy(self.data);data['entities']['yuanxian:redplate_lizard']={'mode':'replace','drops':[{'item':'minecraft:diamond','min':2,'max':4,'chance':.5}]};return data
    def test_default_table_is_valid_and_noop_preserves_bytes(self):
        current=self.store.read();self.assertEqual(self.data,validate(self.data,self.catalogue));self.store.save(self.data,current['revision']);self.assertEqual(self.original,self.file.read_bytes());self.assertEqual([],list((self.root/'state/history').iterdir()))
    def test_real_save_changes_only_unified_file_and_backs_up_original(self):
        untouched=self.root/'other.json';untouched.write_text('keep');current=self.store.read();saved=self.store.save(self.change(),current['revision']);self.assertNotEqual(saved['revision'],current['revision']);self.assertEqual(self.change(),decode(self.file.read_bytes()));backups=list((self.root/'state/history').glob('*.json'));self.assertEqual(1,len(backups));self.assertEqual(self.original,backups[0].read_bytes());self.assertEqual('keep',untouched.read_text());self.assertFalse(list(self.file.parent.glob('.monster-drops-*.tmp')))
    def test_stale_revision_never_overwrites_external_edits(self):
        revision=self.store.read()['revision'];changed=self.original+b'\n';self.file.write_bytes(changed)
        with self.assertRaises(Conflict):self.store.save(self.change(),revision)
        self.assertEqual(changed,self.file.read_bytes())
    def test_invalid_items_probabilities_counts_and_fields_leave_source_untouched(self):
        invalid=[]
        for field,value in [('item','minecraft:missing'),('item',[]),('chance',1.01),('chance',True),('chance',float('nan')),('min',-1),('max',65),('min',3.5)]:
            d=self.change();d['entities']['yuanxian:redplate_lizard']['drops'][0][field]=value;invalid.append(d)
        d=self.change();d['unknown']=True;invalid.append(d)
        d=self.change();d['entities']['yuanxian:redplate_lizard']['mode']='inherit';invalid.append(d)
        for data in invalid:
            with self.assertRaises(Invalid):self.store.save(data,self.store.read()['revision'])
            self.assertEqual(self.original,self.file.read_bytes())
    def test_realm_stage_limits_and_order_match_stats_definitions(self):
        for realm,stage in [('yuanxian:foundation_establishment',3),('yuanxian:mortal',2),('yuanxian:qi_refining',9)]:
            data=self.change();data['overrides'][0]['when']['min_realm']={'realm':realm,'stage':stage}
            with self.assertRaises(Invalid):validate(data,self.catalogue)
        data=self.change();data['overrides'][0]['when'].update(min_realm={'realm':'yuanxian:golden_core','stage':0},max_realm={'realm':'yuanxian:qi_refining','stage':8})
        with self.assertRaises(Invalid):validate(data,self.catalogue)
    def test_duplicate_keys_invalid_utf8_and_nonfinite_values_rejected(self):
        for value in [b'{"schema":1,"schema":1}',b'\xff',b'{"chance":NaN}']:
            with self.assertRaises(Invalid):decode(value)
    def test_special_fire_requires_its_registered_item(self):
        data=self.change();data['entities']['yuanxian:redplate_lizard']['drops'][0]['factory']='beast_fire'
        with self.assertRaises(Invalid):validate(data,self.catalogue)
    def test_looting_quantity_limit_can_be_omitted_or_saved_explicitly(self):
        data = self.change()
        looting = {'min_per_level': 0, 'max_per_level': 1}
        data['entities']['yuanxian:redplate_lizard']['drops'][0]['looting'] = looting
        save = self.store.save(data, self.store.read()['revision'])
        self.assertEqual(data, save['config'])
        looting['limit'] = 4
        save = self.store.save(data, save['revision'])
        self.assertEqual(data, save['config'])
        looting['limit'] = 0
        with self.assertRaises(Invalid):
            self.store.save(data, save['revision'])
        self.assertEqual(save, self.store.read())
    def test_only_one_concurrent_writer_accepts_the_same_revision(self):
        revision=self.store.read()['revision'];results=[]
        def writer(n):
            data=self.change();data['entities']['yuanxian:redplate_lizard']['drops'][0]['min']=n
            try:self.store.save(data,revision);results.append('saved')
            except Conflict:results.append('conflict')
        threads=[threading.Thread(target=writer,args=(i,)) for i in [1,2]]
        for t in threads:t.start()
        for t in threads:t.join()
        self.assertCountEqual(['saved','conflict'],results)
    def test_quality_and_rarity_survive_save_read_and_restore(self):
        for item,value in [('yuanxian:pill_huiling',4),('yuanxian:pill_zhuji',4),('yuanxian:qingfeng_sword',2),('yuanxian:xuantie_helmet',3),('yuanxian:forged_pickaxe',0)]:
            data=self.change();d=data['entities']['yuanxian:redplate_lizard']['drops'][0];d.update(item=item,quality=value)
            saved=self.store.save(data,self.store.read()['revision']);self.assertEqual(value,saved['config']['entities']['yuanxian:redplate_lizard']['drops'][0]['quality']);self.assertEqual(data,decode(self.file.read_bytes()))
        data=self.change();data['entities']['yuanxian:redplate_lizard']['drops'][0]['rarity']='epic';saved=self.store.save(data,self.store.read()['revision']);self.assertEqual('epic',saved['config']['entities']['yuanxian:redplate_lizard']['drops'][0]['rarity'])
    def test_invalid_variants_never_replace_the_file(self):
        for item,field,value in [('yuanxian:pill_huiling','quality',0),('yuanxian:pill_huiling','quality',5),('yuanxian:pill_huiling','quality',1.5),('yuanxian:pill_huiling','quality',True),('yuanxian:pill_huiling','quality',None),('yuanxian:pill_huiling','quality','4'),('minecraft:diamond','quality',4),('yuanxian:qingfeng_sword','rarity','epic'),('minecraft:diamond','rarity','legendary'),('minecraft:diamond','rarity',None),('minecraft:diamond','rarity',4)]:
            data=self.change();d=data['entities']['yuanxian:redplate_lizard']['drops'][0];d.update(item=item);d[field]=value
            with self.assertRaises(Invalid):self.store.save(data,self.store.read()['revision'])
            self.assertEqual(self.original,self.file.read_bytes())
    def test_component_capabilities_use_actual_registered_families(self):
        by={v['id']:v for v in self.catalogue['items']}
        self.assertEqual([1,2,3,4],by['yuanxian:pill_huiling']['qualities']);self.assertEqual([0,1,2,3,4],by['yuanxian:guiyan_sword']['qualities']);self.assertEqual([0,1,2,3,4],by['yuanxian:zhenyue_ruler']['qualities']);self.assertEqual([],by['minecraft:diamond']['qualities']);self.assertEqual(['common','uncommon','rare','epic'],by['yuanxian:huashen_dari_helmet']['rarities'])

class HttpTest(WorkbenchTest):
    def setUp(self):
        super().setUp();self.http=App(('127.0.0.1',0),self.store,b'fixture-password');self.thread=threading.Thread(target=self.http.serve_forever,daemon=True);self.thread.start();self.base='http://127.0.0.1:'+str(self.http.server_port)
    def tearDown(self):self.http.shutdown();self.http.server_close();self.thread.join();super().tearDown()
    def call(self,path,data=None,headers=None):
        request=urllib.request.Request(self.base+path,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json',**(headers or {})})
        try:
            with urllib.request.urlopen(request) as r:return r.status,json.load(r)
        except urllib.error.HTTPError as e:return e.code,json.load(e)
    def test_login_save_conflict_and_history_restore(self):
        self.assertEqual(401,self.call('/api/save',self.change())[0]);self.assertEqual(401,self.call('/api/session',{'password':'bad'})[0]);status,session=self.call('/api/session',{'password':'fixture-password'});self.assertEqual(200,status);headers={'Authorization':'Bearer '+session['token'],'If-Match':self.store.read()['revision']}
        status,saved=self.call('/api/save',self.change(),headers);self.assertEqual(200,status);self.assertEqual(409,self.call('/api/save',self.data,headers)[0]);status,history=self.call('/api/history',headers=headers);self.assertEqual(200,status);headers['If-Match']=saved['revision'];status,restored=self.call('/api/restore',{'id':history['records'][0]['id']},headers);self.assertEqual(200,status);self.assertEqual(self.data,restored['config']);self.assertEqual(self.original,self.file.read_bytes())
    def test_untrusted_origin_and_traversal_rejected(self):
        self.assertEqual(403,self.call('/api/session',{'password':'fixture-password'},{'Origin':'https://example.invalid'})[0]);self.assertEqual(404,self.call('/etc/passwd')[0]);self.assertEqual(404,self.call('/api/../etc/passwd')[0])
    def test_no_password_or_token_in_public_state(self):
        status,data=self.call('/api/state');self.assertEqual(200,status);self.assertNotIn('fixture-password',json.dumps(data));self.assertEqual(SOURCE.as_posix(),data['path']);self.assertFalse(self.call('/api/health')[1]['authenticated'])

class NativeLootAndCategoriesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from tools.drop_workbench.catalogue import Assets
        from tools.drop_workbench.loot import LootReader
        client=Path.home()/'.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'
        if not client.is_file():raise unittest.SkipTest('需要精确版本Minecraft客户端资源缓存')
        cls.assets=Assets(ROOT,client);cls.reader=LootReader(cls.assets,{'minecraft':{},'yuanxian':{}})
    @classmethod
    def tearDownClass(cls):cls.assets.client.close()
    def test_zombie_rare_pool_has_gate_weight_and_zero_quantity(self):
        rows=self.reader.table('minecraft:entities/zombie')['rows'];flesh=next(r for r in rows if r['item']=='minecraft:rotten_flesh');iron=next(r for r in rows if r['item']=='minecraft:iron_ingot')
        self.assertEqual('0–2',flesh['quantity']);self.assertAlmostEqual(2/3,flesh['chance']);self.assertAlmostEqual(.025/3,iron['chance']);self.assertIn('玩家击杀',iron['conditions']);self.assertTrue(any('3.5%/4.5%/5.5%' in v for v in iron['conditions']))
    def test_fire_smelt_and_multi_rolls_are_preserved(self):
        pig=self.reader.table('minecraft:entities/pig')['rows'][0];self.assertTrue(any('着火' in v and 'cooked_porkchop' in v for v in pig['notes']))
        witch=self.reader.table('minecraft:entities/witch')['rows'];self.assertTrue(any(r['rolls']=='1–3' for r in witch));self.assertTrue(any('抢夺每级' in v for r in witch for v in r['notes']))
    def test_magma_cube_conditional_selection_does_not_claim_fixed_chance(self):
        rows=self.reader.table('minecraft:entities/magma_cube')['rows'];self.assertEqual(4,len(rows));self.assertTrue(all(r['chance'] is None for r in rows));self.assertTrue(any('青蛙品种' in v for r in rows for v in r['conditions']))
    def test_creeper_music_disc_tag_expands_actual_choices(self):
        rows=self.reader.table('minecraft:entities/creeper')['rows'];discs=[r for r in rows if r['item'].startswith('minecraft:music_disc_')];self.assertEqual(12,len(discs));self.assertTrue(all(r['conditions'] for r in discs));self.assertAlmostEqual(1,sum(r['chance'] for r in discs))
    def test_sheep_color_table_references_meat_without_losing_count(self):
        rows=self.reader.table('minecraft:entities/sheep/white')['rows'];self.assertEqual({'minecraft:white_wool','minecraft:mutton'},{r['item'] for r in rows});mutton=next(r for r in rows if r['item']=='minecraft:mutton');self.assertEqual('1–2',mutton['quantity']);self.assertEqual(1,mutton['chance']);self.assertEqual('minecraft:entities/sheep',mutton['reference'])
    def test_mod_material_baseline_displays_looting_without_a_quantity_limit(self):
        row=self.reader.table('yuanxian:entities/redplate_lizard')['rows'][0]
        self.assertEqual('yuanxian:redplate_scale',row['item'])
        self.assertEqual('1–2',row['quantity'])
        self.assertEqual(1,row['chance'])
        self.assertEqual(['抢夺每级增加 0–1 个'],row['notes'])
    def test_purpose_category_wins_over_block_or_equipment_word(self):
        from tools.drop_workbench.categories import classify
        ids=['yuanxian:pill_huiling','yuanxian:medium_grade_spirit_stone','yuanxian:spirit_leaf','yuanxian:qingfeng_sword','yuanxian:xuantie_chestplate','yuanxian:workpiece_qingfeng_sword_rough','minecraft:acacia_planks','minecraft:acacia_sign','minecraft:iron_ingot']
        items=[{'id':id,'name':id,'namespace':id.split(':')[0],'is_block':id in {'minecraft:acacia_planks','minecraft:acacia_sign','yuanxian:spirit_leaf'}} for id in ids];categories=classify(ROOT,self.assets,items);by={v['id']:v['category'] for v in items}
        for id in ['yuanxian:medium_grade_spirit_stone','yuanxian:spirit_leaf','yuanxian:workpiece_qingfeng_sword_rough','minecraft:iron_ingot']:self.assertEqual('materials',by[id])
        self.assertEqual('pills',by['yuanxian:pill_huiling']);self.assertEqual('weapons',by['yuanxian:qingfeng_sword']);self.assertEqual('armor',by['yuanxian:xuantie_chestplate']);self.assertEqual('blocks',by['minecraft:acacia_planks']);self.assertEqual('decoration',by['minecraft:acacia_sign']);self.assertEqual(['materials','pills','weapons'],[v['id'] for v in categories[:3]])
    def test_all_native_entity_tables_have_no_silently_unsupported_operators(self):
        from tools.drop_workbench.loot import native_loot
        ids=[]
        for n in self.assets.client.namelist():
            if n.startswith('data/minecraft/loot_table/entities/') and n.endswith('.json'):ids.append('minecraft:'+n[len('data/minecraft/loot_table/'):-5])
        ids += ['yuanxian:entities/'+p.stem for p in (ROOT/'src/main/resources/data/yuanxian/loot_table/entities').glob('*.json')]
        for id in ids:self.reader.table(id)
        self.assertEqual(set(),self.reader.unsupported)

class TechniqueDirectoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from tools.drop_workbench.techniques import build

        cls.directory = build(ROOT)
        cls.entries = {entry['id']: entry for entry in cls.directory['techniques']}

    def technique(self, name):
        return self.entries['yuanxian:' + name]

    def test_all_registered_techniques_are_browsable_under_their_real_category(self):
        source = ROOT / 'src/main/resources/data/yuanxian/yuanxian/technique'
        self.assertEqual({f'yuanxian:{path.stem}' for path in source.glob('*.json')}, self.entries.keys())
        for path in source.glob('*.json'):
            definition = json.loads(path.read_text())
            entry = self.technique(path.stem)
            self.assertEqual(definition['category'], entry['category'])
            self.assertEqual(definition['grade'], entry['grade'])
            self.assertTrue(entry['realm'])
            if definition['category'] == 'body' and 'body' not in definition.get('effects', {}):
                self.assertEqual({'name': '效果待配置', 'stats': []}, entry['effect'])

    def test_details_use_runtime_effects_and_merged_learning_thresholds(self):
        sword = self.technique('taibai_jianjing')
        self.assertEqual('筑基', sword['realm'])
        self.assertIn(('剑类资质', '至少 200 点'), sword['learning'])
        self.assertIn(('金系资质', '至少 200 点'), sword['learning'])
        self.assertIn(('前置功法', '《天罡剑典》已学'), sword['learning'])
        self.assertIn(('锁定距离', '48 格'), sword['effect']['stats'])
        cone = dict(self.technique('canglang_chong')['effect']['stats'])
        self.assertEqual('14 格', cone['作用距离'])
        self.assertNotIn('最远施法距离', cone)
        self.assertIn(('无敌时间', '0.25 秒（5 tick）'), self.technique('liuyun_bu')['effect']['stats'])
        self.assertIn(('前置功法', '《青木长春功》圆满并完成参悟'), self.technique('yimu_shenghua_jue')['learning'])

    def test_major_realms_and_heart_details(self):
        self.assertEqual(['凡人启蒙', '炼气', '筑基', '金丹', '元婴', '化神'],
                         [name for _, name in self.directory['realms']])
        self.assertEqual('凡人启蒙', self.technique('basic_breathing')['realm'])
        sword = self.technique('zhan_yun_jian_zhang')
        self.assertEqual('元婴', sword['realm'])
        self.assertEqual([('攻击', '+24 点')], sword['effect']['base'])
        self.assertEqual([('剑系绝技命中时的伤害基数', '+30 点')], sword['effect']['extra'])
        guard = self.technique('xuan_qiong_shou_jian_jing')['effect']
        self.assertEqual([('生命上限', '+48 点'), ('肉身防御', '+10 点')], guard['base'])
        self.assertEqual([('持剑受到投射物或五行法术时的肉身防御', '+20 点')], guard['extra'])

    def test_frontend_builder_exports_a_separate_directory_with_versioned_assets(self):
        from tools.drop_workbench.catalogue import write_frontend

        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            base = 'https://assets.ishiki333hhh.win/previews/fixture/'
            write_frontend(ROOT, output, base)
            page = (output / 'index.html').read_text()
            for name in ('app.js', 'style.css', 'techniques.js', 'techniques.css'):
                self.assertTrue((output / name).is_file())
                self.assertIn(base + name + '?v=', page)
            directory = json.loads((output / 'techniques.json').read_text())
            self.assertEqual(self.entries.keys(), {entry['id'] for entry in directory['techniques']})


if __name__=='__main__':unittest.main()
