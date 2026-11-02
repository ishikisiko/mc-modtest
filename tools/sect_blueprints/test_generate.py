"""宗门试点的真实几何与拒绝边界；不启动游戏。"""
import copy
import json
import unittest

from sect_blueprints.generate import AIR, ROOT, SectModel, line


class SectBlueprintTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = json.loads((ROOT/"blueprints/sects/cloud_gate.json").read_text())
        cls.model = SectModel(cls.spec).construct()

    def test_path_is_four_connected_in_both_directions(self):
        for a,b in (([3,5,2],[11,9,15]),([11,9,15],[3,5,2]),([4,2,8],[4,2,8])):
            cells = line(a,b)
            self.assertEqual(tuple(a),cells[0]); self.assertEqual(tuple(b),cells[-1])
            for p,q in zip(cells,cells[1:]):
                self.assertEqual(1,abs(p[0]-q[0])+abs(p[2]-q[2]))
                self.assertLessEqual(abs(p[1]-q[1]),1)

    def test_vertical_or_steep_route_is_rejected(self):
        for a,b in (([1,2,1],[1,4,1]),([1,2,1],[1,9,3])):
            with self.assertRaises(ValueError): line(a,b)

    def test_all_building_approaches_and_anchors_are_connected(self):
        result = self.model.audit()
        self.assertEqual([],result["losses"])
        self.assertEqual("pass",result["status"])
        self.assertEqual(len(self.spec["buildings"]),result["buildings"])

    def test_actual_obstacle_in_bridge_is_detected(self):
        model = copy.deepcopy(self.model)
        route = next(r for r in model.routes if r["id"]=="cross_gorge")
        x,y,z = route["centers"][len(route["centers"])//2]
        model.construction.cell((x,y,z),"minecraft:stone","test_obstacle","fixture")
        self.assertTrue(any(row["kind"]=="blocked_route" and row["position"]==[x,y,z]
                            for row in model.audit()["losses"]))

    def test_disconnected_anchor_is_not_accepted(self):
        model = copy.deepcopy(self.model)
        model.spec = copy.deepcopy(self.spec)
        model.spec["anchors"][1]["position"] = [2,6,2]
        self.assertTrue(any(row["kind"]=="unreachable_anchor" and row["id"]=="disciples"
                            for row in model.audit()["losses"]))

    def test_unplanned_building_demolition_is_rejected(self):
        model = copy.deepcopy(self.model)
        p = next(p for p,(owner,state) in model.protected.items() if owner=="main_hall" and p[1]>95)
        model.construction.cell(p,AIR,"test_demolition","clearance")
        self.assertTrue(any(row["kind"]=="building_overwritten" and row["position"]==list(p)
                            for row in model.audit()["losses"]))

    def test_bridge_fragment_without_bank_connection_is_rejected(self):
        model = copy.deepcopy(self.model)
        p = (137,130,116)
        model.construction.cell(p,"minecraft:stone_bricks","cross_gorge","support")
        self.assertTrue(any(row["kind"]=="unsupported_bridge_cell" and row["position"]==list(p)
                            for row in model.audit()["losses"]))

    def test_source_foundation_cells_have_real_support(self):
        origins = {b["id"]:b["origin"][1] for b in self.model.buildings}
        cells = [p for p,(owner,state) in self.model.protected.items() if p[1]==origins[owner]]
        self.assertTrue(cells)
        for x,y,z in cells:
            self.assertNotEqual(AIR,self.model.get((x,y-1,z)),(x,y,z))

    def test_duplicate_identity_is_rejected(self):
        spec = copy.deepcopy(self.spec); spec["anchors"][1]["id"] = spec["anchors"][0]["id"]
        with self.assertRaisesRegex(ValueError,"重复ID"): SectModel(spec)

    def test_unknown_component_cannot_bypass_registry(self):
        spec = copy.deepcopy(self.spec); spec["buildings"][0]["template"] = "sect_gate_missing"
        with self.assertRaisesRegex(ValueError,"未在宗门模板表注册"): SectModel(spec).construct()


if __name__=="__main__": unittest.main()
