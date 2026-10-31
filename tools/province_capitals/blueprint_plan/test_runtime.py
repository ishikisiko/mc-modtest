"""默认主城适配：资源身份、真实府邸与迁排后的服务坐标。"""
import json
from pathlib import Path
import tempfile
import unittest

from buildgen.groups import get_group
from buildgen.nbtread import read_gzipped_nbt,state_string
from province_capitals.game.compile import resource_file,life_inputs
from .runtime import export,REGIONS,ROOT


class DefaultManorRuntimeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary=tempfile.TemporaryDirectory();cls.out=Path(cls.temporary.name)
        cls.bundle=export('beiyuan',cls.out);cls.states={}
        for tile in cls.bundle['tiles']:
            _,doc=read_gzipped_nbt(str(resource_file(cls.out,tile['template'],template=True)))
            palette=[state_string(v) for v in doc['palette']]
            for cell in doc['blocks']:
                x,y,z=cell['pos'];cls.states[tile['x']+x,y,tile['z']+z]=palette[cell['state']]

    @classmethod
    def tearDownClass(cls):cls.temporary.cleanup()

    def test_registered_families_are_the_default_seven_and_zhongzhou_is_kept(self):
        rules=json.loads((ROOT/'blueprints/worldplan/rules_v11.json').read_text());cities={c['region_id']:c for c in rules['assets']['cities']}
        self.assertEqual(set(REGIONS)|{'zhongzhou'},set(cities))
        for region in REGIONS:
            group=get_group('province_capital_'+region+'_manor')
            self.assertIn('lord_manor',group.archetype_roster)
            self.assertEqual(group.scale_params['layout_family_id'],cities[region]['layout_family_id'])
            self.assertEqual(group.scale_params['life_layout_id'],cities[region]['life_layout_id'])
        self.assertEqual('province_capitals/zhongzhou_environment_v3',cities['zhongzhou']['layout_family_id'])

    def test_full_manor_uses_runtime_chunk_tiles_and_has_no_stale_trial_paths(self):
        b=self.bundle
        self.assertEqual('capital_game_bundle',b['format']);self.assertEqual('province_capitals/beiyuan_manor',b['layout_family_id'])
        self.assertEqual([76,20,129],b['blueprint']['manor']['size'])
        self.assertEqual(23927,b['blueprint']['manor']['native_architectural_cells'])
        self.assertGreater(sum(s.startswith('myvillage:') for s in self.states.values()),20000)
        self.assertTrue(all(t['template'].startswith('myvillage:province_capitals/v4/capital_beiyuan/') for t in b['tiles']))
        self.assertEqual(55,b['coverage']['houses']);self.assertEqual(8,b['coverage']['retained_services']);self.assertEqual(1,b['coverage']['manors'])

    def test_actual_service_fixtures_follow_the_exported_life_positions(self):
        b=self.bundle;life_inputs(b,*b['size'],192)
        required={'inn_work','inn_rest','inn_social','guest_west','herbalist_work','herbalist_rest','smith_work','smith_rest'}
        self.assertTrue(required<={p['id'] for p in b['life']['places']})
        for place in b['life']['places']:
            if not place['block']:continue
            self.assertEqual(place['block'],self.states[tuple(place['block_pos'])].split('[',1)[0],place['id'])
        entries={p['id']:p for p in b['entries']}
        self.assertEqual([116,12,270],entries['south_gate']['position'])
        self.assertEqual([111,12,144],entries['lord_manor']['position'])


if __name__=='__main__':unittest.main()
