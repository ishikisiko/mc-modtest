"""七州分阁整院落位、门洞和西漠北驿商街的局部回归。"""
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from qianji.enclosure import build_enclosed, check_wall, opening, perimeter, rules, variants
from qianji.model import AIR


class QianjiEnclosureTests(unittest.TestCase):
    def test_whole_branch_fits_each_authored_parcel(self):
        total=0
        for name,site in rules()["towns"].items():
            with self.subTest(region=name):
                model,checks=build_enclosed(name)
                a,b,A,B=site["box"]
                self.assertEqual((A-a,B-b),(model.d,model.w) if site["turn"]%2 else (model.w,model.d))
                self.assertEqual(variants()[name],model.spec["id"])
                self.assertEqual([r["id"] for r in model.rooms],[r["id"] for r in model.spec["rooms"]])
                self.assertEqual(len(list(perimeter(model.w,model.d))),checks["wall_columns"]+checks["gate_columns"])
                self.assertEqual((5,5),(checks["gate_columns"],checks["gate_clear_height"]))
                self.assertEqual(len(model.targets),checks["reachable_targets"])
                self.assertEqual(len(model.rooms),len(model.signs))
                total+=len(model.rooms)
        self.assertEqual(22,total)

    def test_breaks_in_the_side_wall_and_gate_are_rejected(self):
        model,_=build_enclosed("lingyue",validate=False)
        saved=model.blocks[0,2,15]
        model.blocks[0,2,15]=AIR
        with self.assertRaisesRegex(ValueError,"围墙缺口"):
            check_wall(model)
        model.blocks[0,2,15]=saved
        cx,feet,_=opening(model)
        model.blocks[cx,feet+1,0]="minecraft:stone"
        with self.assertRaisesRegex(ValueError,"院门净空不足"):
            check_wall(model)

    def test_market_frontage_keeps_both_old_junctions_and_the_entire_width(self):
        from tools.province_capitals.inner_neighborhoods.qianji import stroke
        site=rules()["towns"]["ximo"];street=site["roads"]["replan_05"]
        self.assertEqual([[194,21],[194,113]],[street[0],street[-1]])
        road=stroke((352,336),street,5)
        self.assertEqual(5,int(road[70,155:173].sum()))
        a,b,A,B=site["box"]
        self.assertFalse(road[b:B,a:A].any())
        link=site["links"][0]
        cross=stroke(road.shape,link["points"],link["width"])
        self.assertTrue((road & cross).any())
        self.assertFalse(cross[b:B,a:A].any())
        self.assertEqual(5,int(cross[60:82,a-1].sum()))


if __name__=="__main__":
    unittest.main()
