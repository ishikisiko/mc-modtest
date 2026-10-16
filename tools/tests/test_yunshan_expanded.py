"""Three-high entrances, larger usable rooms, and game-save export fidelity."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import json_to_nbt as nbt
from yunshan.build_inn import check_walks,check_roof,check_wall_roof_joins
from yunshan.build_functional import validate_site,validate_services
from yunshan.game_export import read_typed,write_typed,convert_game_save,plain,compare_authored
from tools.yunshan.game_evidence import BuildingEvidence,teleport_command
from tools.modal_runner.remote import commands_for

SOURCE=ROOT/'blueprints/yunshan'


class ExpandedLayoutTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest=SOURCE/'functional_buildings_v2.json'
        cls.data=[json.loads((SOURCE/f).read_text()) for f in json.loads(cls.manifest.read_text())['buildings']]

    def test_three_high_entries_and_all_facilities_are_reachable(self):
        ref=json.loads((SOURCE/'d1_site_reference.json').read_text())
        for d in self.data:
            with self.subTest(building=d['id']):
                size,_,blocks,_=nbt.expand_structure(d);e=d['entrance']
                self.assertGreaterEqual(size[0],19)
                for x in range(e['x_range'][0],e['x_range'][1]+1):
                    self.assertTrue(all(blocks[x,y,e['z']]=='minecraft:air' for y in range(e['floor_y'],e['floor_y']+3)))
                    self.assertNotEqual(blocks[x,e['floor_y']+3,e['z']],'minecraft:air')
                self.assertTrue(all(check_walks(blocks,d['walk_targets'],size).values()))
                for spec in d['roof_checks']:check_roof(blocks,spec,size)
                self.assertGreater(check_wall_roof_joins(blocks,d['wall_roof_joins']),0)
                validate_services(d,blocks)
                self.assertFalse(validate_site(d,ref)['matches_original_lot'])

    def test_sample_buildings_do_not_overlap(self):
        for i,a in enumerate(self.data):
            for b in self.data[i+1:]:
                amin,amax=a['site']['world_min'],a['site']['world_max'];bmin,bmax=b['site']['world_min'],b['site']['world_max']
                self.assertTrue(any(amax[k]<bmin[k] or bmax[k]<amin[k] for k in (0,2)))

    def test_inn_has_two_wide_stair_and_four_real_bed_pairs(self):
        d=next(d for d in self.data if d['presentation']['role']=='inn');_,_,b,_=nbt.expand_structure(d)
        for y,z in enumerate(range(9,15),1):
            for x in (7,8):self.assertIn('spruce_stairs[facing=south',b[x,y,z])
        beds=[s for s in b.values() if s.startswith('minecraft:red_bed')]
        self.assertEqual(sum('part=head' in s for s in beds),4)
        self.assertEqual(sum('part=foot' in s for s in beds),4)

    def test_signs_and_hanging_lamps_have_supports(self):
        offsets={'north':(0,1),'south':(0,-1),'east':(-1,0),'west':(1,0)}
        for d in self.data:
            _,_,b,_=nbt.expand_structure(d)
            for (x,y,z),s in b.items():
                name,props=nbt.parse_block_state(s)
                if name.endswith('_wall_sign'):
                    dx,dz=offsets[props['facing']]
                    self.assertNotEqual(b.get((x+dx,y,z+dz),'minecraft:air'),'minecraft:air',(d['id'],x,y,z))
                if name=='minecraft:lantern' and props.get('hanging')=='true':
                    self.assertNotEqual(b.get((x,y+1,z),'minecraft:air'),'minecraft:air',(d['id'],x,y,z))

    def test_cloud_preset_is_bounded_and_local_game_start_is_refused(self):
        commands=commands_for('yunshan-buildings')
        self.assertEqual(len(commands),4)
        self.assertIn('-x',commands[2]);self.assertIn('generateAllStructures',commands[2])
        self.assertEqual(commands[-1],['python3','tools/yunshan/game_evidence.py'])
        with tempfile.TemporaryDirectory() as tmp:
            e=BuildingEvidence(self.manifest,Path(tmp))
            with patch('tools.yunshan.game_evidence.ROOT',Path('/not-modal')):
                with self.assertRaisesRegex(RuntimeError,'Modal disposable'):e.start()

    def test_teleport_uses_decimals_to_disable_integer_centering(self):
        self.assertEqual(teleport_command('CaptureDev',[32,-60,-8]),
                         'tp CaptureDev 32.000000 -60.000000 -8.000000 0.000000 0.000000')


class ActualSaveExportTests(unittest.TestCase):
    def fixture(self):
        data={'size':[2,2,2],'fill_air':True,'id':'myvillage:typed_fixture','site':{},'blocks':[{'pos':[0,0,0],'state':'minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]'}]}
        root=nbt.structure_json_to_root_nbt(data)
        root.value['blocks'].value[0].value['nbt']=nbt.Compound({
            'id':nbt.String('minecraft:campfire'),'byte':nbt.Byte(1),'short':nbt.Tag(2,123),
            'long':nbt.Tag(4,2**45),'float':nbt.Tag(5,1.25),'double':nbt.Double(2.5),
            'bytes':nbt.Tag(7,b'\0\xff'),'ints':nbt.Tag(11,[1,-2]),'longs':nbt.Tag(12,[2**40,-2**40]),
            'Items':nbt.ListTag(nbt.TAG_COMPOUND,[nbt.Compound({'Slot':nbt.Byte(0),'id':nbt.String('minecraft:stick'),'count':nbt.Int(1)})])})
        return data,root

    def test_all_nbt_types_and_nested_block_entities_survive_game_export(self):
        data,root=self.fixture()
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp);source=p/'minecraft-save.nbt';write_typed(source,'',root)
            self.assertEqual(read_typed(source)[1],root)
            report=convert_game_save(source,p/'export',data)
            self.assertTrue(report['nbt_is_unmodified_game_save'])
            self.assertTrue(report['schem_typed_nbt_roundtrip'])
            _,schem=read_typed(p/'export.schem');entity=schem.value['BlockEntities'].value[0]
            self.assertEqual(entity.value['long'].tag_id,4)
            self.assertEqual(entity.value['long'].value,2**45)
            self.assertEqual(entity.value['Items'].value[0].value['Slot'].tag_id,1)
            self.assertEqual(plain(entity)['Pos'],[0,0,0])

    def test_missing_world_block_is_not_accepted_as_success(self):
        data,root=self.fixture();data['blocks'][0]['state']='minecraft:air'
        with self.assertRaisesRegex(AssertionError,'game block mismatch'):compare_authored(root,data)


if __name__=='__main__':unittest.main()
