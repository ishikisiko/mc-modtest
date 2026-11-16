"""住宅入口、台阶和完整构件的负例回归。"""
from pathlib import Path
import json,unittest
from tools.player_homes.model import build_home,walk_check

ROOT=Path(__file__).resolve().parents[2]

class HomeGuardsTest(unittest.TestCase):
    def setUp(self):
        self.catalog=json.loads((ROOT/'blueprints/player_homes/catalog.json').read_text())
        self.spec=self.catalog['templates'][0];self.site=build_home(ROOT,self.catalog,self.spec)

    def test_blocked_front_entry_is_rejected(self):
        x=self.spec['front_gates'][0];self.site.put((x,2,0),'minecraft:stone')
        with self.assertRaisesRegex(ValueError,'入口站位'):walk_check(ROOT,self.catalog,self.site)

    def test_removing_main_stairs_does_not_leave_walkable_air(self):
        for p,s in list(self.site.blocks.items()):
            if s.startswith('minecraft:spruce_stairs'):self.site.blocks.pop(p)
        with self.assertRaisesRegex(ValueError,'动线不通'):walk_check(ROOT,self.catalog,self.site)

    def test_window_parts_cannot_be_overwritten_by_partition(self):
        p=next(tuple(a['parts'][1])for a in self.site.assemblies if a['family']=='courtyard_window')
        with self.assertRaisesRegex(AssertionError,'split whole item'):self.site.put(p,'minecraft:stone')

if __name__=='__main__':unittest.main()
