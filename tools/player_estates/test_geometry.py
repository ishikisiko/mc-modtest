"""与实际入口、楼梯和花槽有关的局部回归。"""
import json
from pathlib import Path
import unittest

from .geometry import Estate,connectivity,walkable_blocks

SOURCE=Path(__file__).resolve().parents[2]/'blueprints/player_estates/gray_courtyard.json'


class EstateGeometryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config=json.loads(SOURCE.read_text())

    def test_every_authored_floor_has_an_entrance_route(self):
        e=Estate(self.config).build();report=connectivity(e)
        self.assertEqual(len(report['targets']),18)
        self.assertEqual(report['failed'],{})

    def test_removed_upper_stair_approach_is_detected(self):
        e=Estate(self.config).build()
        # Removing the second flight's approach reproduces the third-floor
        # isolation found during authoring; the validator must keep rejecting it.
        for x in (53,54):
            for z in (89,90):e.blocks.pop((x,16,z),None)
        self.assertIn('main_3',connectivity(e)['failed'])

    def test_entry_throats_are_clear_of_projecting_shutters(self):
        e=Estate(self.config).build()
        for b in self.config['buildings']:
            x0,z0,x1,z1=b['rect'];x=(x0+x1)//2;f=b['floor']
            for z in (z0-1,z0):
                for y in (f+1,f+2):
                    self.assertNotIn((x,y,z),e.blocks,(b['id'],x,y,z,e.blocks.get((x,y,z))))

    def test_window_plants_have_real_soil(self):
        e=Estate(self.config).build()
        plants=[p for p,s in e.blocks.items() if s.startswith('minecraft:sweet_berry_bush')]
        self.assertGreater(len(plants),50)
        for x,y,z in plants:
            self.assertTrue(e.blocks.get((x,y-1,z),'').startswith('minecraft:grass_block'),(x,y,z))


if __name__=='__main__':unittest.main()
