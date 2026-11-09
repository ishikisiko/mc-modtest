import copy
import json
import unittest
from sect_blueprints.generate import ROOT,SectModel,AIR
from sect_blueprints.runtime import metadata


class SectRuntimeMetadataTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec=json.loads((ROOT/'blueprints/sects/cloud_gate.json').read_text())
        cls.model=SectModel(cls.spec).construct()

    def test_compiled_facilities_are_reachable_and_owned_by_the_blueprint(self):
        result=metadata(self.model)
        self.assertEqual(self.spec['runtime']['entry_anchor'],result['entry_anchor'])
        self.assertEqual(self.spec['runtime']['shelves'],result['shelves'])
        self.assertIn(result['steward'],result['courtyard_cells'])
        self.assertEqual(len(result['courtyard_cells']),len({tuple(p) for p in result['courtyard_cells']}))
        self.assertTrue(all(self.model.walkable(tuple(p)) for p in result['courtyard_cells']))

    def test_a_blocked_shelf_reader_is_rejected(self):
        model=copy.deepcopy(self.model)
        p=tuple(self.spec['runtime']['shelves'][0]['reader'])
        model.construction.cell(p,'minecraft:stone','test','fixture')
        with self.assertRaisesRegex(ValueError,'经架或读者位置'):metadata(model)

    def test_linlang_has_a_separate_reachable_counter_and_reader(self):
        result=metadata(self.model)
        self.assertEqual(self.spec['runtime']['linlang'],result['linlang'])
        self.assertNotEqual(result['linlang']['position'],result['linlang']['reader'])
        self.assertEqual('linlang',result['linlang']['building'])

    def test_a_blocked_linlang_reader_is_rejected(self):
        model=copy.deepcopy(self.model)
        p=tuple(self.spec['runtime']['linlang']['reader'])
        model.construction.cell(p,'minecraft:stone','test','fixture')
        with self.assertRaisesRegex(ValueError,'琳琅阁'):metadata(model)

    def test_a_broken_door_threshold_is_rejected(self):
        model=copy.deepcopy(self.model)
        x,y,z=self.spec['entry_connections'][-1]['points'][-1]
        model.construction.cell((x,y-1,z),AIR,'test','fixture')
        with self.assertRaisesRegex(ValueError,'门槛实体'):metadata(model)

    def test_an_authored_indoor_passage_cannot_be_blocked(self):
        model=copy.deepcopy(self.model)
        b=next(b for b in model.buildings if b['id']=='main_hall')
        p=tuple(b['passages'][0])
        model.construction.cell(p,'minecraft:stone','fixture','fixture')
        with self.assertRaisesRegex(ValueError,'室内门洞'):metadata(model)

    def test_full_cube_at_the_last_stair_is_rejected(self):
        model=copy.deepcopy(self.model)
        b=next(b for b in model.buildings if b['id']=='scripture')
        main=next(v for v in b['massing'] if v['id']=='main');meta=main['meta'];opening=meta['stairwell']
        p=(b['origin'][0]+opening['x0'],b['origin'][1]+meta['foundation_h']+meta['story_wall_h'],b['origin'][2]+opening['landing_z'])
        self.assertIn('_stairs[',model.get(p))
        model.construction.cell(p,'minecraft:stone','fixture','fixture')
        with self.assertRaisesRegex(ValueError,'建筑楼层无免跳跃通路'):metadata(model)


if __name__=='__main__':unittest.main()
