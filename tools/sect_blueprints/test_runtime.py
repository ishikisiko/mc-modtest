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

    def test_a_broken_door_threshold_is_rejected(self):
        model=copy.deepcopy(self.model)
        x,y,z=self.spec['entry_connections'][-1]['points'][-1]
        model.construction.cell((x,y-1,z),AIR,'test','fixture')
        with self.assertRaisesRegex(ValueError,'门槛实体'):metadata(model)


if __name__=='__main__':unittest.main()
