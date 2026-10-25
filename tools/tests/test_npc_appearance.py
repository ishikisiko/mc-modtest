import json
from pathlib import Path
import tempfile
import unittest

from tools.npcgen import catalog,male_catalog
from tools.npc_appearance_evidence import automatic_look_ok,appearance,expected_ledger_look,egg_item,projection_bounds,withdrawal_count,CASES
from tools.modal_runner.remote import PRESETS


class AppearanceEntryTest(unittest.TestCase):
    def test_withdrawal_keeps_actual_remote_courtyard_cells_loaded_and_requires_server_record(self):
        # 第一轮真实站位在锚点北侧，超出了锚点周围 64 格的加载框。
        bodies={129:{'Pos':[-2840.5,-60.0,1911.5]},154:{'Pos':[-2881.5,-60.0,1913.5]},131:{'Pos':[-2837.5,-60.0,1907.5]}}
        bounds=projection_bounds(bodies)
        self.assertTrue(all(bounds[0]<=b['Pos'][0]<=bounds[2] and bounds[1]<=b['Pos'][2]<=bounds[3] for b in bodies.values()))
        self.assertLess(bounds[1],1928)
        with self.assertRaises(ValueError):projection_bounds({})
        self.assertEqual(0,withdrawal_count('No entity was found',3))
        self.assertEqual(0,withdrawal_count('World sim avatars: sect 4 withdrew 12 avatar(s): no player within 96 blocks',3))
        self.assertEqual(12,withdrawal_count('World sim avatars: sect 3 withdrew 12 avatar(s): no player within 96 blocks',3))

    def test_readback_does_not_accept_legacy_missing_or_unspawned_entities(self):
        for body in ({},{'Look':'m_01_01_a'},{'UUID':[1,2,3,4]},
                     *({'UUID':[1,2,3,4],'Look':look} for look in ('default','f_novice','f_adept','unknown'))):
            self.assertFalse(automatic_look_ok(body),body)
        for v in catalog.runtime_document()['variants']:
            self.assertTrue(automatic_look_ok({'UUID':[1,2,3,4],'Look':v['look']}))

    def test_egg_fixture_never_supplies_the_expected_appearance(self):
        item=egg_item('appearance_egg_0')
        self.assertIn('cultivator_spawn_egg',item)
        self.assertIn('Tags:["appearance_egg_0"]',item)
        self.assertNotIn('Look',item)
        self.assertNotIn('Colours',item)

    def test_ledger_expectation_uses_identity_in_all_realms(self):
        for realm in ('mortal','qi_refining','foundation_establishment','golden_core','nascent_soul'):
            self.assertEqual(catalog.for_identity(17,-12345),expected_ledger_look({'id':17,'birth':-12345,'gender':'f','realm':realm}))
            self.assertEqual(male_catalog.for_identity(17,-12345),expected_ledger_look({'id':17,'birth':-12345,'gender':'m','realm':realm}))
        self.assertNotEqual(appearance({'UUID':[1,2,3,4],'Look':'m_01_01_a'}),appearance({'UUID':[1,2,3,4],'Look':'m_01_01_a','Colours':66}))

    def test_auto_catalogue_is_generator_owned_and_rejects_old_families(self):
        current=catalog.runtime_document()
        self.assertEqual([{'family':'male','weight':1},{'family':'female_novice','weight':2}],current['spawn_families'])
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for relative in (catalog.SOURCE,male_catalog.SOURCE,catalog.APPEARANCE_RULES):
                p=root/relative;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((catalog.ROOT/relative).read_bytes())
            for bad in ([{'family':'default','weight':1},{'family':'female_novice','weight':2}],
                        [{'family':'male','weight':0},{'family':'female_novice','weight':2}],
                        [{'family':'male','weight':True},{'family':'female_novice','weight':2}],
                        [{'family':'male','weight':1},{'family':'male','weight':2}]):
                (root/catalog.APPEARANCE_RULES).write_text(json.dumps({'version':1,'spawn_families':bad}))
                with self.assertRaises(ValueError):catalog.runtime_document(root)

    def test_remote_entry_covers_only_the_targeted_lifecycle(self):
        expected=[list(command) for command in PRESETS['npc-variants-build']]
        index=expected[1].index('jar')
        expected[1][index:index]=['--tests','com.example.myvillage.sim.engine.NpcCombatLedgerTest']
        self.assertEqual(expected+[ ['python3','tools/npc_appearance_evidence.py'] ],PRESETS['npc-appearance-runtime'])
        self.assertNotIn('com.example.myvillage.sim.engine.NpcCombatLedgerTest',PRESETS['npc-variants-build'][1])
        self.assertEqual(('command_spawn','mapped_spawn_egg','explicit_look_compatibility','ledger_projection','ledger_reprojection','save_reload'),CASES)


if __name__=='__main__':unittest.main()
