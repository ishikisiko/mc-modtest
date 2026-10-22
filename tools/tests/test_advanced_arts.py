from __future__ import annotations
import copy
import json
from pathlib import Path
import shutil
import tempfile
import unittest
from tools import gen_advanced_arts as gen
from tools.modal_runner import remote

ROOT=Path(__file__).resolve().parents[2]

class AdvancedArtsDataTests(unittest.TestCase):
    def setUp(self):self.doc=gen.read(ROOT/gen.SOURCE)

    def test_six_arts_two_per_weapon_and_generator_is_current(self):
        gen.validate(ROOT,self.doc)
        self.assertEqual(6,len(self.doc['arts']))
        self.assertEqual([],gen.generate(ROOT,check=True))

    def test_survival_recipes_bind_exact_book_and_do_not_copy_progress(self):
        output=gen.plan(ROOT)
        seen=set()
        for art in self.doc['arts']:
            rid=art['id'].split(':')[1]
            recipe=json.loads(output[gen.DATA/f'recipe/manual_{rid}.json'])
            self.assertEqual('myvillage:manual_active_xuan',recipe['result']['id'])
            self.assertEqual({'myvillage:technique':art['id']},recipe['result']['components'])
            self.assertEqual(1,recipe['result']['count'])
            self.assertIn({'tag':'c:spirit_stones/low_grade'},recipe['ingredients'])
            self.assertFalse(any('manual' in ingredient.get('item','') for ingredient in recipe['ingredients']))
            signature=json.dumps(sorted(json.dumps(i,sort_keys=True) for i in recipe['ingredients']))
            self.assertNotIn(signature,seen);seen.add(signature)
            advancement=json.loads(output[gen.DATA/f'advancement/recipes/manual_{rid}.json'])
            self.assertEqual([f'myvillage:manual_{rid}'],advancement['rewards']['recipes'])

    def test_invalid_fields_are_rejected(self):
        mutations=[('qi_cost',-1),('slot',2),('slot',True),('cooldown_ticks',0),('guard_reduction',float('nan')),
                   ('counter_multiplier',10),('fx_lifetime_ticks',999),('requires_ground','true'),('particle_count',10000)]
        for key,value in mutations:
            with self.subTest(key=key):
                bad=copy.deepcopy(self.doc);bad['arts'][0][key]=value
                with self.assertRaises(ValueError):gen.validate(ROOT,bad)

    def test_unknown_fields_and_duplicate_slots_rejected(self):
        bad=copy.deepcopy(self.doc);bad['arts'][0]['damage_from_client']=42
        with self.assertRaises(ValueError):gen.validate(ROOT,bad)
        bad=copy.deepcopy(self.doc);bad['arts'][1]['slot']=0
        with self.assertRaises(ValueError):gen.validate(ROOT,bad)
        bad=copy.deepcopy(self.doc);bad['arts'][1]['id']=bad['arts'][0]['id']
        with self.assertRaises(ValueError):gen.validate(ROOT,bad)

    def test_move_family_and_shape_boundaries(self):
        for key,value in [('move','myvillage:basic_spear_01_mid_thrust'),('step_distance',1.61),('reach',float('inf')),
                          ('maximum_targets',13),('shape','sphere'),('fx','infinite_particles')]:
            with self.subTest(key=key):
                bad=copy.deepcopy(self.doc);bad['arts'][0]['phases'][0][key]=value
                with self.assertRaises(ValueError):gen.validate(ROOT,bad)

    def test_registered_learning_and_manual_fields(self):
        output=gen.plan(ROOT)
        for art in self.doc['arts']:
            rid=art['id'].split(':')[1]
            manual=json.loads(output[gen.DATA/f'myvillage/technique/{rid}.json'])
            self.assertEqual(art['qi_cost'],manual['effects']['active']['qi_cost'])
            self.assertEqual(art['id'],manual['effects']['active']['skill'])
            self.assertEqual(art['minimum_stage'],manual['requirements']['minimum_stage'])
            self.assertEqual(600,manual['study']['points'])
            for lang in ('zh_cn','en_us'):
                self.assertIn(manual['translation_key'],json.loads(output[gen.ASSETS/f'lang/{lang}.json']))
        function=output[gen.DATA/'function/advanced_arts_books.mcfunction']
        self.assertEqual(6,function.count('myvillage cultivation manual @s '))
        self.assertNotIn(' learn ',function);self.assertNotIn('setrealm',function)

    def test_output_is_reproducible_and_check_never_repairs_drift(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            for rel in [gen.DATA/'combat',gen.DATA/'myvillage/realm',gen.DATA/'myvillage/school',gen.ASSETS/'lang',Path('tools/technique_catalogue'),Path('tools/advanced_arts')]:
                shutil.copytree(ROOT/rel,root/rel,dirs_exist_ok=True)
            gen.generate(root)
            self.assertEqual([],gen.generate(root,check=True))
            target=gen.DATA/'myvillage/technique/qinglian_jiange.json'
            (root/target).write_text('{}\n')
            self.assertIn(target,gen.generate(root,check=True))
            self.assertEqual('{}\n',(root/target).read_text())
            self.assertIn(target,gen.generate(root))
            self.assertEqual([],gen.generate(root,check=True))

    def test_no_runtime_changes_to_baseline_index_or_skill(self):
        index=gen.read(ROOT/gen.DATA/'combat/index.json')
        self.assertEqual(['myvillage:jianqi_jue', 'myvillage:sword_obsession_wave',
                          'myvillage:innate_sword_heart_wave'],index['skills'])
        self.assertEqual({'schema','styles','weapons','skills'},set(index))
        self.assertEqual(3,len(index['styles']))

    def test_all_manual_ids_reserved_from_old_catalogue(self):
        from tools.technique_catalogue import generator
        sources=generator.load_sources(ROOT/'tools/technique_catalogue')
        existing_plan=generator.plan(ROOT,ROOT/'tools/technique_catalogue')
        ours=set(gen.plan(ROOT))
        self.assertFalse(set(existing_plan.deletions)&ours)
        self.assertFalse(set(existing_plan.files)&{p for p in ours if '/technique/' in str(p)})
        self.assertEqual([],generator.check(ROOT,ROOT/'tools/technique_catalogue'))

    def test_server_ownership_and_damage_call_are_single_routed(self):
        base=ROOT/'src/main/java/com/example/myvillage'
        service=(base/'combat/session/CombatArtActions.java').read_text()
        self.assertEqual(1,service.count('CultivationService.spendSpiritualPower('))
        self.assertIn('TechniqueRequirementEvaluator.evaluate(',service)
        self.assertIn('e.held==player.getMainHandItem()',service)
        self.assertIn('CombatDamageService.apply(',service)
        self.assertNotIn('.hurt(',service)
        self.assertNotIn('teleportTo(',service)
        self.assertNotIn('setBlock(',service)
        self.assertIn('e.guardedSource==source',service)
        resolver=(base/'combat/runtime/ArtHitResolver.java').read_text()
        self.assertIn('CombatHitResolver.legalTarget(',resolver)
        self.assertIn('ClipContext.Block.COLLIDER',resolver)
        removal=service.split('static void remove(UUID player)',1)[1].split('static void clear()',1)[0]
        self.assertNotIn('COOLDOWNS',removal)
        for path in (base/'combat/art').glob('*.java'):
            self.assertNotIn('net.minecraft.client.',path.read_text())

    def test_focused_modal_preset_does_not_start_unrelated_gameplay(self):
        commands=remote.commands_for('advanced-arts-build')
        gradle=next(c for c in commands if c[0]=='./gradlew')
        self.assertIn('com.example.myvillage.combat.art.*',gradle)
        self.assertIn('jar',gradle)
        self.assertFalse(any('runClient' in ' '.join(c) or 'runServer' in ' '.join(c) or 'chunky' in ' '.join(c) for c in commands))

if __name__=='__main__':unittest.main()
